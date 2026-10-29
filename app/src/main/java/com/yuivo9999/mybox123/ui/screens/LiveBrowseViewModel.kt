package com.yuivo9999.mybox123.ui.screens

import com.yuivo9999.mybox123.MyBoxApplication
import com.yuivo9999.mybox123.data.source.NativeContentSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.json.JSONObject

/** Native live source/channel browser and stream-resolution coordinator. */
class LiveBrowseViewModel(private val application: MyBoxApplication) : ViewModel() {
    private val _uiState = MutableStateFlow(LiveBrowseUiState())
    val uiState: StateFlow<LiveBrowseUiState> = _uiState.asStateFlow()
    private var sourceRequest = 0L
    private var channelRequest = 0L
    private var playbackRequest = 0L
    private var pendingFavoriteTarget: FavoriteChannelTarget? = null

    fun loadSources(force: Boolean = false) {
        if (_uiState.value.sourcesLoading || (_uiState.value.sourcesLoaded && !force)) return
        val request = ++sourceRequest
        channelRequest++
        viewModelScope.launch {
            _uiState.update { it.copy(sourcesLoading = true, sourceError = null, message = null) }
            try {
                val services = application.awaitServices()
                val result = withContext(Dispatchers.IO) {
                    val all = services.contentSourceRepository.getAll()
                    val settings = services.userDataRepository.getSettings()
                    all.filter { it.sourceType == "live" }.sortedWith(compareBy<NativeContentSource> { it.order }.thenBy { it.name }) to
                        settings.optString("defaultLiveSource").takeIf { it.isNotBlank() && it != "null" }
                }
                if (request != sourceRequest) return@launch
                val builtinSource = NativeContentSource.fromJson(
                    JSONObject()
                        .put("sourceId", "builtin_live")
                        .put("name", "内置央卫视")
                        .put("sourceType", "live")
                        .put("enabled", true)
                        .put("order", -1),
                    -1
                )
                val sources = listOf(builtinSource) + result.first
                val selected = sources.firstOrNull { it.sourceId == _uiState.value.selectedSourceId }
                    ?: sources.firstOrNull { it.sourceId == result.second && it.enabled }
                    ?: builtinSource
                _uiState.update {
                    it.copy(sourcesLoading = false, sourcesLoaded = true, sources = sources,
                        selectedSourceId = selected.sourceId, channels = emptyList(), selectedCategory = ALL_CATEGORIES,
                        sourceError = null, channelError = null)
                }
                refreshChannels()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == sourceRequest) _uiState.update {
                    it.copy(sourcesLoading = false, sourcesLoaded = false, sourceError = error.message ?: "LIVE_SOURCES_LOAD_FAILED")
                }
            }
        }
    }

    fun selectSource(sourceId: String) {
        if (_uiState.value.selectedSourceId == sourceId) return
        channelRequest++
        _uiState.update { it.copy(selectedSourceId = sourceId, channels = emptyList(), selectedCategory = ALL_CATEGORIES,
            channelError = null, message = null) }
        refreshChannels()
    }


    /** Returns to a saved channel in browse-only mode; it never resolves or launches a stream. */
    fun openFavoriteChannel(rawJson: String) {
        val favorite = try { JSONObject(rawJson) } catch (_: Exception) {
            _uiState.update { it.copy(navigationMessage = "无法解析这条直播收藏；收藏记录未被修改。") }
            return
        }
        val sourceId = favorite.optString("sourceId").trim()
        val targetId = favorite.optString("targetId").takeIf { it.isNotBlank() && it != "null" }.orEmpty()
        val sourceItemId = favorite.optString("sourceItemId").trim()
        val name = favorite.optString("title").ifBlank { favorite.optString("name") }.trim()
        if (sourceId.isBlank() || (targetId.isBlank() && sourceItemId.isBlank() && name.isBlank())) {
            _uiState.update { it.copy(navigationMessage = "该旧直播收藏缺少来源或频道定位信息，无法准确定位；收藏记录未被修改。") }
            return
        }
        val source = _uiState.value.sources.firstOrNull { it.sourceId == sourceId }
        if (source == null) {
            _uiState.update { it.copy(navigationMessage = "收藏对应的直播源不存在。请先在“我的 - 内容源”检查该源；收藏记录未被修改。") }
            return
        }
        if (!source.enabled) {
            _uiState.update { it.copy(selectedSourceId = sourceId, navigationMessage = "收藏对应的直播源已停用。请先启用该源；未加载频道，也未启动播放。") }
            return
        }
        pendingFavoriteTarget = FavoriteChannelTarget(targetId, sourceItemId, name)
        channelRequest++
        _uiState.update { it.copy(selectedSourceId = sourceId, channels = emptyList(), selectedCategory = ALL_CATEGORIES,
            keyword = "", channelError = null, navigationMessage = null, message = null) }
        refreshChannels()
    }

    fun refreshChannels() {
        val state = _uiState.value
        val source = state.sources.firstOrNull { it.sourceId == state.selectedSourceId } ?: run {
            _uiState.update { it.copy(channelError = "请先选择直播源。") }
            return
        }
        if (!source.enabled) {
            _uiState.update { it.copy(channelError = "当前直播源已停用，请先到“我的 - 内容源”启用。", channels = emptyList()) }
            return
        }
        val request = ++channelRequest
        viewModelScope.launch {
            _uiState.update { it.copy(channelsLoading = true, channelError = null, message = null) }
            try {
                val channels = if (source.sourceId == "builtin_live") {
                    com.yuivo9999.mybox123.data.movie.StaticCatalogData.liveChannels.map { org.json.JSONObject(it.toString()) }
                } else {
                    application.awaitServices().liveDescriptorService.loadChannels(source, forceRefresh = true)
                }
                if (request != channelRequest) return@launch
                val categories = channels.map { it.optString("category").trim().ifBlank { "未分类" } }.distinct()
                val oldCategory = _uiState.value.selectedCategory
                val target = pendingFavoriteTarget
                val matched = target?.let { wanted -> channels.firstOrNull { channel ->
                    val channelId = channel.optString("sourceChannelId").ifBlank {
                        "${channel.optString("sourceId").ifBlank { source.sourceId }}:${channel.optString("sourceItemId").ifBlank { channel.optString("name") }}"
                    }
                    (wanted.targetId.isNotBlank() && (channelId == wanted.targetId || channel.optString("sourceItemId") == wanted.targetId)) ||
                        (wanted.sourceItemId.isNotBlank() && channel.optString("sourceItemId") == wanted.sourceItemId) ||
                        (wanted.name.isNotBlank() && channel.optString("name").equals(wanted.name, ignoreCase = true))
                } }
                if (target != null) pendingFavoriteTarget = null
                _uiState.update {
                    it.copy(channelsLoading = false, channels = channels,
                        selectedCategory = matched?.optString("category")?.trim()?.ifBlank { "未分类" }
                            ?: if (oldCategory == ALL_CATEGORIES || oldCategory in categories) oldCategory else ALL_CATEGORIES,
                        keyword = if (target != null) matched?.optString("name")?.takeIf { it.isNotBlank() }
                            ?: target.name.ifBlank { target.sourceItemId } else it.keyword,
                        channelError = null,
                        navigationMessage = if (target != null && matched == null) "已打开收藏对应的直播源，但未找到匹配频道。可在下方搜索框调整关键词；收藏记录未被修改。" else null,
                        message = if (target != null && matched != null) "已定位到收藏频道“${matched.optString("name")}”。可点“播放”解析线路并启动原生播放器。"
                            else if (channels.isEmpty()) "源已读取，但没有解析到频道。" else "已读取 ${channels.size} 个频道。")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == channelRequest) _uiState.update {
                    it.copy(channelsLoading = false, channelError = friendlyLiveError(error.message ?: "LIVE_CHANNELS_LOAD_FAILED"), channels = emptyList())
                }
            }
        }
    }

    /** Resolves the selected channel's stream descriptors, then presents a native player route. */
    fun openPlayback(channel: JSONObject) {
        val request = ++playbackRequest
        val sourceId = channel.optString("sourceId").ifBlank { _uiState.value.selectedSourceId.orEmpty() }
        val source = _uiState.value.sources.firstOrNull { it.sourceId == sourceId }
        if (source == null) {
            _uiState.update { it.copy(playbackError = "找不到该频道所属的直播源。", playbackChannel = null, playbackStreams = emptyList()) }
            return
        }
        if (!source.enabled) {
            _uiState.update { it.copy(playbackError = "该直播源已停用，请先启用。", playbackChannel = null, playbackStreams = emptyList()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(playbackLoading = true, playbackError = null, playbackChannel = JSONObject(channel.toString()), playbackStreams = emptyList(), playbackSelectedStreamId = null) }
            try {
                val streams = if (source.sourceId == "builtin_live") {
                    val rawStreams = channel.optJSONArray("streams")
                    buildList {
                        if (rawStreams != null) {
                            for (i in 0 until rawStreams.length()) {
                                rawStreams.optJSONObject(i)?.let { add(it) }
                            }
                        }
                    }
                } else {
                    application.awaitServices().liveDescriptorService.loadStreams(source, channel, forceRefresh = true)
                }
                if (request != playbackRequest) return@launch
                val playable = streams.filter { stream ->
                    val url = stream.optString("url").ifBlank { stream.optString("playUrl") }.trim()
                    url.startsWith("http://", true) || url.startsWith("https://", true)
                }
                if (playable.isEmpty()) {
                    val unsupported = streams.firstOrNull()?.optString("protocol").orEmpty()
                    _uiState.update { it.copy(playbackLoading = false, playbackStreams = streams,
                        playbackError = if (streams.isEmpty()) "该频道没有可用线路。" else "当前原生播放器仅支持 HTTP/HTTPS 线路；${unsupported.ifBlank { "此协议" }}暂不支持。") }
                } else {
                    val first = playable.first()
                    _uiState.update { it.copy(playbackLoading = false, playbackStreams = playable,
                        playbackSelectedStreamId = first.optString("streamId"), playbackError = null,
                        message = "已解析 ${playable.size} 条可用 HTTP/HTTPS 线路。") }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == playbackRequest) _uiState.update { it.copy(playbackLoading = false, playbackError = friendlyLiveError(error.message ?: "LIVE_STREAM_RESOLVE_FAILED"), playbackStreams = emptyList()) }
            }
        }
    }

    fun selectPlaybackStream(streamId: String) {
        if (_uiState.value.playbackStreams.none { it.optString("streamId") == streamId }) return
        _uiState.update { it.copy(playbackSelectedStreamId = streamId, playbackError = null) }
    }

    fun closePlayback() {
        playbackRequest++
        _uiState.update { it.copy(playbackLoading = false, playbackError = null, playbackChannel = null, playbackStreams = emptyList(), playbackSelectedStreamId = null) }
    }

    fun updateKeyword(value: String) = _uiState.update { it.copy(keyword = value) }
    fun selectCategory(category: String) = _uiState.update { it.copy(selectedCategory = category) }

    fun addFavorite(channel: JSONObject) {
        val sourceId = channel.optString("sourceId").ifBlank { _uiState.value.selectedSourceId.orEmpty() }
        val stableChannelId = channel.optString("sourceChannelId").ifBlank {
            "$sourceId:${channel.optString("sourceItemId").ifBlank { channel.optString("name") }}"
        }
        if (stableChannelId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(mutatingFavoriteId = stableChannelId, message = null, channelError = null) }
            try {
                withContext(Dispatchers.IO) {
                    val repository = application.awaitServices().userDataRepository
                    val existing = repository.getFavoriteRecords()
                    if (existing.none { it.targetType.equals("channel", true) && it.targetId?.toString() == stableChannelId }) {
                        val now = System.currentTimeMillis()
                        val record = JSONObject()
                            .put("favoriteId", "favorite:channel:$stableChannelId")
                            .put("targetType", "channel")
                            .put("targetId", stableChannelId)
                            .put("kind", "live")
                            .put("title", channel.optString("name").ifBlank { "未命名频道" })
                            .put("name", channel.optString("name").ifBlank { "未命名频道" })
                            .put("sourceId", sourceId)
                            .put("sourceItemId", channel.optString("sourceItemId"))
                            .put("sourceName", _uiState.value.sources.firstOrNull { it.sourceId == sourceId }?.name.orEmpty())
                            .put("createdAt", now)
                            .put("lastAccessedAt", now)
                        repository.saveFavoriteRecords(existing + com.yuivo9999.mybox123.data.storage.FavoriteRecord.fromJson(record))
                    }
                }
                _uiState.update { it.copy(mutatingFavoriteId = null, favoriteIds = it.favoriteIds + stableChannelId, message = "频道已加入收藏（若已存在则保持不变）。") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _uiState.update { it.copy(mutatingFavoriteId = null, channelError = error.message ?: "LIVE_FAVORITE_SAVE_FAILED") }
            }
        }
    }

    fun isFavorite(channel: JSONObject): Boolean {
        val sourceId = channel.optString("sourceId").ifBlank { _uiState.value.selectedSourceId.orEmpty() }
        val id = channel.optString("sourceChannelId").ifBlank { "$sourceId:${channel.optString("sourceItemId").ifBlank { channel.optString("name") }}" }
        return _uiState.value.favoriteIds.contains(id)
    }

    fun loadFavoriteIds() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    application.awaitServices().userDataRepository.getFavoriteRecords()
                        .filter { it.targetType.equals("channel", true) || it.toJson().optString("kind").equals("live", true) }
                        .mapNotNull { it.targetId?.toString() }.toSet()
                }
            }.onSuccess { ids -> _uiState.update { it.copy(favoriteIds = ids) } }
        }
    }

    companion object { const val ALL_CATEGORIES = "全部分组" }
}

private data class FavoriteChannelTarget(
    val targetId: String,
    val sourceItemId: String,
    val name: String,
)

data class LiveBrowseUiState(
    val sourcesLoading: Boolean = false,
    val sourcesLoaded: Boolean = false,
    val sources: List<NativeContentSource> = emptyList(),
    val selectedSourceId: String? = null,
    val sourceError: String? = null,
    val channelsLoading: Boolean = false,
    val channels: List<JSONObject> = emptyList(),
    val selectedCategory: String = LiveBrowseViewModel.ALL_CATEGORIES,
    val keyword: String = "",
    val channelError: String? = null,
    val message: String? = null,
    val mutatingFavoriteId: String? = null,
    val favoriteIds: Set<String> = emptySet(),
    val navigationMessage: String? = null,
    val playbackLoading: Boolean = false,
    val playbackChannel: JSONObject? = null,
    val playbackStreams: List<JSONObject> = emptyList(),
    val playbackSelectedStreamId: String? = null,
    val playbackError: String? = null,
)

private fun friendlyLiveError(raw: String): String = when {
    raw.contains("SOURCE_NOT_FOUND") -> "直播源不存在或已被删除。"
    raw.contains("SOURCE_TYPE_NOT_LIVE") -> "所选来源不是直播源。"
    raw.contains("SOURCE_DISABLED") -> "直播源已停用，请先启用。"
    raw.contains("LIVE_SOURCE_IS_EPG_NOT_CHANNELS") -> "当前地址是节目单（EPG），不是频道列表。"
    raw.contains("LIVE_SOURCE_INPUT_TOO_LARGE") -> "直播源内容超过允许的读取大小。"
    raw.contains("LIVE_SOURCE_CHANGED_DURING_LOAD") -> "直播源在读取期间发生变化，请重新加载。"
    raw.contains("CLEARTEXT") -> "系统安全策略阻止了该 HTTP 地址。"
    else -> raw.take(180)
}
