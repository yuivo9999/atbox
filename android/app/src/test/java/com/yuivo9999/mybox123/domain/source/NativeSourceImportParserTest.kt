package com.yuivo9999.mybox123.domain.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.json.JSONObject
import com.yuivo9999.mybox123.core.network.NativeHttpClient
import com.yuivo9999.mybox123.data.source.NativeContentSource
import kotlinx.coroutines.runBlocking

class NativeSourceImportParserTest {
    @Test
    fun importReconciliationFindsIdentityDedupRemovalsAndTypeReusedIds() {
        fun source(id: String, type: String, ref: String) = NativeContentSource.fromJson(
            JSONObject().put("sourceId", id).put("sourceType", type).put("sourceRef", ref),
        )

        val prior = listOf(
            source("old-movie-id", "movie", "https://example.test/api"),
            source("reused-id", "movie", "https://example.test/old"),
            source("still-present", "live", "https://example.test/live.txt"),
        )
        val persisted = listOf(
            source("new-movie-id", "movie", "https://example.test/api"),
            source("reused-id", "live", "https://example.test/new-live.txt"),
            source("still-present", "live", "https://example.test/live.txt"),
        )

        val removed = NativeSourceImportReconciliationPolicy.removedSources(prior, persisted)
        assertEquals(listOf("old-movie-id", "reused-id"), removed.map { it.sourceId })
        assertEquals(listOf("movie", "movie"), removed.map { it.sourceType })
    }

    @Test
    fun importReconciliationKeepsReferencesWhoseIdAndTypeStillExist() {
        val prior = listOf(
            NativeContentSource.fromJson(JSONObject().put("sourceId", "stable").put("sourceType", "movie")),
        )
        val persisted = listOf(
            NativeContentSource.fromJson(JSONObject().put("sourceId", "stable").put("sourceType", "movie").put("name", "Updated")),
        )
        assertTrue(NativeSourceImportReconciliationPolicy.removedSources(prior, persisted).isEmpty())
    }

    @Test
    fun nativeLiveFormatDetectionPrefersExplicitFormatThenContentTypeThenBody() {
        assertEquals("txt", detectNativeLiveFormat("txt", "m3u", "application/vnd.apple.mpegurl", "#EXTM3U\n"))
        assertEquals("m3u", detectNativeLiveFormat(null, "json", "application/vnd.apple.mpegurl; charset=utf-8", "#EXTM3U\n"))
        assertEquals("xmltv", detectNativeLiveFormat(null, "json", "application/xml", "<tv><programme start=\"x\"/></tv>"))
        assertEquals("json", detectNativeLiveFormat(null, "txt", "application/json", "{\"channels\":[]}"))
        assertEquals("m3u", detectNativeLiveFormat(null, "txt", "text/plain", "#EXTM3U\n"))
        assertEquals("txt", detectNativeLiveFormat(null, "txt", "text/plain", "#genre#新闻\n频道,http://example.test/live"))
        assertEquals("tv1", detectNativeLiveFormat("tv1", "txt", null, "频道,#genre#新闻"))
    }
    @Test
    fun tvboxCategoriesStringAndIjkProfilesMatchLegacyShape() {
        val imported = NativeSourceImportParser.parseText(
            """{
                "sites": [{"key":"site-a", "name":"示例", "api":"https://example.test/api.php/provide/vod/", "type":"1", "categories":"电影,剧集#综艺"}],
                "ijk": [
                    {"group":"硬解", "options":[
                        {"category":"1", "name":"codec", "value":"h264"},
                        {"category":9, "name":"ignored", "value":"x"},
                        {"category":2, "name":"empty", "value":""}
                    ]},
                    {"group":"无效", "options":[{"category":1,"name":"missing-value"}]}
                ]
            }""".trimIndent(),
            "tvbox.json",
        )
        val source = imported.single()
        assertEquals("http-vod", source.optString("tvboxAdapterKind"))
        assertEquals(1, source.optInt("tvboxType"))
        val categories = source.optJSONArray("categories")!!
        assertEquals(listOf("电影", "剧集", "综艺"), (0 until categories.length()).map { categories.getString(it) })
        val profiles = source.optJSONObject("tvboxIJKProfiles")!!
        assertNotNull(profiles.optJSONArray("硬解"))
        assertEquals(1, profiles.optJSONArray("硬解")!!.length())
        assertNull(profiles.optJSONArray("无效"))
    }

    @Test
    fun absentOrInvalidTvboxTypeIsPreservedAsNullInsteadOfInventingZero() {
        val imported = NativeSourceImportParser.parseText(
            """{"sites":[{"key":"unknown-type","api":"https://example.test/custom"},{"key":"bad-type","type":"n/a","api":"https://example.test/custom2"}]}""",
            "tvbox.json",
        )
        assertEquals(2, imported.size)
        assertTrue(imported.all { it.isNull("tvboxType") })
    }

    @Test
    fun legacyTvboxExtShapeAndClassificationArePreserved() {
        val imported = NativeSourceImportParser.parseText(
            """{"sites":[
                {"key":"object-ext","name":"普通源","api":"https://example.test/a","ext":{"note":"直播脚本字样不应改变分类"}},
                {"key":"array-ext","api":"https://example.test/b","ext":["https://example.test/live.js"]},
                {"key":"inline-ext","api":"https://example.test/c","ext":"{\"movies\":[]}"},
                {"key":"remote-ext","api":"https://example.test/d","ext":"https://example.test/rule.json?x=1"}
            ]}""",
            "tvbox.json",
        )
        assertEquals(4, imported.size)
        assertTrue(imported.all { it.optString("sourceType") == "movie" })
        assertEquals("config-object", imported[0].optString("tvboxExtFormat"))
        assertEquals("unknown", imported[1].optString("tvboxExtFormat"))
        assertEquals("inline-json", imported[2].optString("tvboxExtFormat"))
        assertEquals("remote-json", imported[3].optString("tvboxExtFormat"))
    }

    @Test
    fun emptyCategoriesStringIsOmittedButEmptyArrayIsPreserved() {
        val imported = NativeSourceImportParser.parseText(
            """{"sites":[
                {"key":"blank","api":"https://example.test/a","categories":"  "},
                {"key":"empty-array","api":"https://example.test/b","categories":[]},
                {"key":"delimited-empty","api":"https://example.test/c","categories":" , # | "}
            ]}""",
            "tvbox.json",
        )
        assertFalse(imported[0].has("categories"))
        assertTrue(imported[1].has("categories"))
        assertEquals(0, imported[1].optJSONArray("categories")!!.length())
        assertTrue(imported[2].has("categories"))
        assertEquals(0, imported[2].optJSONArray("categories")!!.length())
    }

    @Test
    fun tvboxTypeMatchesLegacyNumberCoercionForNullEmptyAndNumericStrings() {
        val imported = NativeSourceImportParser.parseText(
            """{"sites":[
                {"key":"missing","api":"https://example.test/a"},
                {"key":"null","api":"https://example.test/b","type":null},
                {"key":"empty","api":"https://example.test/c","type":""},
                {"key":"numeric-string","api":"https://example.test/api.php/provide/vod/","type":"1"},
                {"key":"fraction-string","api":"https://example.test/d","type":"1.5"}
            ]}""",
            "tvbox.json",
        )
        assertTrue(imported[0].isNull("tvboxType"))
        assertEquals(0.0, imported[1].optDouble("tvboxType"), 0.0)
        assertEquals(0.0, imported[2].optDouble("tvboxType"), 0.0)
        assertEquals("http-vod", imported[3].optString("tvboxAdapterKind"))
        assertEquals(1.0, imported[3].optDouble("tvboxType"), 0.0)
        assertEquals("unknown", imported[4].optString("tvboxAdapterKind"))
        assertEquals(1.5, imported[4].optDouble("tvboxType"), 0.0)
    }

    @Test
    fun fractionalTvboxTypeDoesNotQualifyAsDirectVod() {
        val imported = NativeSourceImportParser.parseText(
            """{"sites":[{"key":"fractional","type":1.5,"api":"https://example.test/api.php/provide/vod/"}]}""",
            "tvbox.json",
        )
        assertEquals("unknown", imported.single().optString("tvboxAdapterKind"))
    }

    @Test
    fun tvboxLiveReferencesReceiveNormalizedGlobalParseAndIjkSettings() {
        val imported = NativeSourceImportParser.parseText(
            """{"lives":[{"name":"新闻","url":"https://example.test/live.txt"}],"parses":[{"name":"p","url":"https://example.test/parse"}],"ijk":[{"group":"G","options":[{"category":1,"name":"n","value":"v"}]}]}""",
            "tvbox.json",
        )
        val live = imported.single()
        assertEquals("tv1", live.optString("liveMode"))
        assertEquals("direct-live", live.optString("sourceCapability"))
        assertEquals("live-reference", live.optString("adapterType"))
        assertEquals("p", live.optJSONObject("tvboxParseConfig")!!.optJSONArray("parses")!!.getJSONObject(0).optString("name"))
        assertFalse(live.optJSONObject("tvboxIJKProfiles")!!.optJSONArray("G")!!.isNull(0))
    }
    @Test
    fun cspDrpyJarAndExecutableExtKeepExplicitNonHttpAdapterKinds() {
        val imported = NativeSourceImportParser.parseText(
            """{"sites":[
                {"key":"drpy_js_demo","name":"DRPY","api":"https://example.test/drpy2.min.js"},
                {"key":"csp_demo","name":"CSP","api":"csp_XYZ"},
                {"key":"jar_demo","name":"JAR","api":"https://example.test/api","jar":"https://example.test/spider.jar"},
                {"key":"script_ext","name":"EXT JS","api":"https://example.test/api2","ext":"https://example.test/rule.js"}
            ]}""",
            "tvbox.json",
        )
        assertEquals(listOf("drpy-js", "csp", "jar", "ext"), imported.map { it.optString("tvboxAdapterKind") })
        assertEquals(listOf("tvbox-drpy-js", "tvbox-csp", "tvbox-jar", "tvbox-ext"), imported.map { it.optString("sourceCapability") })
        assertEquals("javascript", imported.last().optString("tvboxExtFormat"))
        assertTrue(imported.all { it.optString("adapterType") == "tvbox-extension" })
    }

    @Test
    fun tvboxExtArraysArePreservedButNotMisclassifiedAsExecutableScripts() {
        val imported = NativeSourceImportParser.parseText(
            """{"sites":[
                {"key":"array-ext","api":"https://example.test/api","ext":["one","two"]},
                {"key":"config-ext","api":"https://example.test/api2","ext":{"headers":{"x":"y"}}}
            ]}""",
            "tvbox.json",
        )
        assertEquals(2, imported.size)
        assertEquals("unknown", imported[0].optString("tvboxExtFormat"))
        assertEquals(2, imported[0].optJSONArray("tvboxExt")!!.length())
        assertEquals("config-object", imported[1].optString("tvboxExtFormat"))
    }

    @Test
    fun proxyLiveRefsDecodeOnlyExtParameterAndDeduplicateUrls() {
        val imported = NativeSourceImportParser.parseText(
            """{"lives":[{"name":"Proxy live","urls":[
                "proxy://?ext=https%3A%2F%2Fexample.test%2Flive.txt%3Ftoken%3Da%2Bb&type=0",
                "https://example.test/live.txt?token=a%2Bb",
                "proxy://?type=0&ext=https%3A%2F%2Fexample.test%2Fother.m3u"
            ]}]}""",
            "tvbox.json",
        )
        assertEquals(2, imported.size)
        assertTrue(imported.any { it.optString("sourceRef") == "https://example.test/live.txt?token=a+b" })
        assertTrue(imported.any { it.optString("sourceRef") == "https://example.test/other.m3u" })
        assertTrue(imported.all { it.optString("sourceCapability") == "direct-live" })
    }

    @Test
    fun proxyExtParameterDoesNotAbsorbFollowingOptionsAndDuplicateUrlsKeepSingleName() {
        val imported = NativeSourceImportParser.parseText(
            """{"lives":[{"name":"Single live","urls":[
                "proxy://?ext=https%3A%2F%2Fexample.test%2Flive.txt%3Ftoken%3Da%2Bb&type=0",
                "https://example.test/live.txt?token=a+b",
                "https://example.test/live.txt?token=a+b"
            ]}]}""",
            "tvbox.json",
        )
        assertEquals(1, imported.size)
        assertEquals("Single live", imported.single().optString("name"))
        assertEquals("https://example.test/live.txt?token=a+b", imported.single().optString("sourceRef"))
    }

    @Test
    fun tvboxLiveProxyWithoutHttpExtIsIgnored() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NativeSourceImportParser.parseText(
                """{"lives":[{"name":"invalid","url":"proxy://?ext=file%3A%2F%2F%2Fetc%2Fhosts"}]}""",
                "tvbox.json",
            )
        }
        assertEquals("SOURCE_IMPORT_TVBOX_EMPTY", error.message)
    }

    @Test
    fun tv1MetadataGroupsDuplicateChannelsAndKeepsLineIndexesForLazyResolution() {
        val channels = NativeLiveDescriptorParser.parseTv1Metadata(
            """﻿央视,#genre#
CCTV1,http://one.test/live.m3u8${'$'}主线#http://backup.test/live.m3u8${'$'}备用
CCTV1，http://third.test/live.m3u8
// comment
[卫视]
湖南卫视 http://hunan.test/live.m3u8""",
        )
        assertEquals(2, channels.size)
        assertEquals("央视", channels[0].optString("category"))
        assertEquals(2, channels[0].optJSONObject("deferredRef")!!.optJSONArray("lineIndices")!!.length())
        assertEquals(3, channels[0].optInt("estimatedStreamCount"))
        assertEquals("卫视", channels[1].optString("category"))
        val streams = NativeLiveDescriptorParser.parseTv1LineStreams("CCTV1,http://one.test/live.m3u8${'$'}主线#http://backup.test/live.m3u8${'$'}备用")
        assertEquals(2, streams.size)
        assertEquals("主线", streams[0].optString("label"))
        assertEquals("备用", streams[1].optString("label"))
    }

    @Test
    fun m3uParserMapsTvgMetadataAndSkipsOrphanUrls() {
        val channels = NativeLiveDescriptorParser.parseM3u(
            """#EXTM3U
https://orphan.test/no-meta.m3u8
#EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV-1" tvg-logo="https://img.test/logo.png" group-title="央视" tvg-quality="HD",中央电视台一套
https://stream.test/cctv1.m3u8
#EXTINF:-1 group-title="卫视",湖南卫视
https://stream.test/hunan.m3u8""",
        )
        assertEquals(2, channels.size)
        assertEquals("cctv1", channels[0].optString("sourceItemId"))
        assertEquals("中央电视台一套", channels[0].optString("name"))
        assertEquals("https://img.test/logo.png", channels[0].optString("logo"))
        assertEquals("央视", channels[0].optString("category"))
        assertEquals("HD", channels[0].optJSONObject("stream")!!.optString("quality"))
        assertEquals("卫视", channels[1].optString("category"))
    }

    @Test
    fun jsonLiveParserMatchesLegacyEnvelopeAndFieldAliases() {
        val channels = NativeLiveDescriptorParser.parseJsonLive(
            """{"data":[{"id":"c1","title":"频道一","icon":"https://img.test/a.png","group":"新闻","urls":["https://a.test/live.m3u8",{"mediaUrl":"https://b.test/live.m3u8","name":"备用"}],"epg":[{"title":"节目"}]},null]}""",
        )
        assertEquals(1, channels.size)
        val channel = channels.single()
        assertEquals("c1", channel.optString("sourceItemId"))
        assertEquals("频道一", channel.optString("name"))
        assertEquals("https://img.test/a.png", channel.optString("logo"))
        assertEquals("新闻", channel.optString("category"))
        assertEquals(2, channel.optJSONArray("streams")!!.length())
        assertEquals("https://b.test/live.m3u8", channel.optJSONArray("streams")!!.getJSONObject(1).optString("url"))
        assertEquals(1, channel.optJSONArray("epg")!!.length())
    }

    @Test
    fun xmlLiveParserMapsChannelAndStreamAliases() {
        val channels = NativeLiveDescriptorParser.parseXmlLive(
            """<root><channel id="c1" display-name="新闻" group="综合"><icon src="https://img.test/logo.png"/><stream url="https://a.test/live.m3u8" label="主线" quality="HD" priority="2"/><source>https://b.test/live.m3u8</source></channel></root>""",
        )
        assertEquals(1, channels.size)
        val channel = channels.single()
        assertEquals("c1", channel.optString("sourceItemId"))
        assertEquals("新闻", channel.optString("name"))
        assertEquals("综合", channel.optString("category"))
        assertEquals(2, channel.optJSONArray("streams")!!.length())
        assertEquals("主线", channel.optJSONArray("streams")!!.getJSONObject(0).optString("label"))
        assertEquals("https://img.test/logo.png", channel.optString("logo"))
    }

    @Test
    fun xmltvParserMapsProgrammesAndRejectsDoctypePayloads() {
        val programmes = NativeLiveDescriptorParser.parseXmlEpg(
            """<?xml version="1.0"?><tv><programme id="p1" channel="c1" category="News" start="20261017090000 +0000" stop="20261017100000 +0000"><title>新闻</title><desc>早间新闻</desc></programme></tv>""",
        )
        assertEquals(1, programmes.size)
        assertEquals("c1", programmes.single().optString("channelRef"))
        assertEquals("新闻", programmes.single().optString("title"))
        assertEquals("早间新闻", programmes.single().optString("description"))
        assertEquals("News", programmes.single().optString("category"))
        assertEquals("20261017090000 +0000", programmes.single().optString("startAt"))
        assertEquals("20261017100000 +0000", programmes.single().optString("endAt"))
        val error = assertThrows(IllegalArgumentException::class.java) {
            NativeLiveDescriptorParser.parseXmlEpg("<!DOCTYPE tv [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><tv><programme><title>&x;</title></programme></tv>")
        }
        assertEquals("LIVE_EPG_XML_INVALID", error.message)
    }

    @Test
    fun liveCacheStoreInvalidatesOnlyChangedDisabledOrRemovedSourceOwners() {
        var persisted = JSONObject()
        val store = NativeLiveCacheStore({ JSONObject(persisted.toString()) }, { persisted = JSONObject(it.toString()) }, Unit)
        store.put("a", "channels", "all", JSONObject().put("count", 3))
        store.put("b", "epg", "today", JSONObject().put("count", 9))
        assertEquals(3, store.get("a", "channels", "all")!!.optInt("count"))
        val previous = org.json.JSONArray()
            .put(JSONObject().put("sourceId", "a").put("enabled", true).put("descriptorFingerprint", "old"))
            .put(JSONObject().put("sourceId", "b").put("enabled", true).put("descriptorFingerprint", "same"))
        val next = org.json.JSONArray()
            .put(JSONObject().put("sourceId", "a").put("enabled", true).put("descriptorFingerprint", "new"))
            .put(JSONObject().put("sourceId", "b").put("enabled", true).put("descriptorFingerprint", "same"))
            .put(JSONObject().put("sourceId", "c").put("enabled", true).put("descriptorFingerprint", "new"))
        assertEquals(listOf("a"), store.reconcile(previous, next))
        assertNull(store.get("a", "channels", "all"))
        assertEquals(9, store.get("b", "epg", "today")!!.optInt("count"))
        assertEquals(listOf("b"), store.reconcile(next, org.json.JSONArray().put(JSONObject().put("sourceId", "b").put("enabled", false).put("descriptorFingerprint", "same"))))
        assertNull(store.get("b", "epg", "today"))
    }

    @Test
    fun liveCacheGenerationRejectsLateWritesAfterDescriptorInvalidation() {
        var persisted = JSONObject()
        val store = NativeLiveCacheStore({ JSONObject(persisted.toString()) }, { persisted = JSONObject(it.toString()) }, Unit)
        val previous = org.json.JSONArray().put(JSONObject()
            .put("sourceId", "source-a").put("enabled", true).put("descriptorFingerprint", "old"))
        val next = org.json.JSONArray().put(JSONObject()
            .put("sourceId", "source-a").put("enabled", true).put("descriptorFingerprint", "new"))
        val inFlightGeneration = store.generation()

        // No prior cache entry exists, but registry invalidation must still fence an in-flight load.
        assertTrue(store.reconcile(previous, next).isEmpty())
        assertTrue(store.generation() > inFlightGeneration)
        assertFalse(store.putIfGeneration("source-a", "channels", "old-config", JSONObject().put("count", 1), inFlightGeneration))
        assertNull(store.get("source-a", "channels", "old-config"))
        assertTrue(store.putIfGeneration("source-a", "channels", "new-config", JSONObject().put("count", 2), store.generation()))
        assertEquals(2, store.get("source-a", "channels", "new-config")!!.optInt("count"))
    }

    @Test
    fun sourceMutationInvalidationClearsCachesAndAdvancesGenerationEvenWhenEmpty() {
        var persisted = JSONObject()
        val store = NativeLiveCacheStore({ JSONObject(persisted.toString()) }, { persisted = JSONObject(it.toString()) }, Unit)
        store.put("source-a", "channels", "all", JSONObject().put("count", 2))
        val before = store.generation()
        assertEquals(1, store.invalidateAllForSourceMutation())
        assertTrue(store.generation() > before)
        assertNull(store.get("source-a", "channels", "all"))
        val emptyGeneration = store.generation()
        assertEquals(0, store.invalidateAllForSourceMutation())
        assertTrue(store.generation() > emptyGeneration)
    }

    @Test
    fun liveCacheGenerationChangesWhenSourceIsRemovedEvenWithoutCachedOwner() {
        val store = NativeLiveCacheStore({ JSONObject() }, { }, Unit)
        val previous = org.json.JSONArray().put(JSONObject()
            .put("sourceId", "source-a").put("enabled", true).put("descriptorFingerprint", "same"))
        val before = store.generation()
        assertTrue(store.reconcile(previous, org.json.JSONArray()).isEmpty())
        assertTrue(store.generation() > before)
    }

    @Test
    fun liveCacheStoreExpiresEntriesByKindAndPersistsExpiryMetadata() {
        var now = 1_800_000_000_000L
        var persisted = JSONObject()
        val store = NativeLiveCacheStore(
            { JSONObject(persisted.toString()) },
            { persisted = JSONObject(it.toString()) },
            Unit,
        ) { now }
        store.put("source", "streams", "channel-1", JSONObject().put("count", 2))
        assertEquals(now + NativeLiveCacheStore.STREAMS_TTL_MS,
            persisted.getJSONObject("source").getJSONObject("streams").getJSONObject("channel-1").getLong("expiresAt"))
        now += NativeLiveCacheStore.STREAMS_TTL_MS
        assertNull(store.get("source", "streams", "channel-1"))
        assertEquals(0, store.summary().getInt("sourceCount"))
    }

    @Test
    fun liveDescriptorServiceLoadsLocalJsonAndReadsBackCachedChannelsAndStreams() = runBlocking {
        var persisted = JSONObject()
        val cache = NativeLiveCacheStore({ JSONObject(persisted.toString()) }, { persisted = JSONObject(it.toString()) }, Unit)
        val raw = JSONObject()
            .put("sourceId", "local-live")
            .put("name", "本地直播")
            .put("sourceType", "live")
            .put("sourceRef", "")
            .put("enabled", true)
            .put("localFormat", "json")
            .put("localContent", """[{"id":"c1","title":"测试频道","url":"https://example.test/live.m3u8"}]""")
        val source = NativeContentSource.fromJson(raw)
        val service = NativeLiveDescriptorService(NativeHttpClient(), cache)

        val first = service.loadChannels(source)
        val second = service.loadChannels(source)
        assertEquals(1, first.size)
        assertEquals("测试频道", first.single().optString("name"))
        assertEquals(first.single().toString(), second.single().toString())
        val streams = service.loadStreams(source, first.single())
        assertEquals(1, streams.size)
        assertEquals("https://example.test/live.m3u8", streams.single().optString("url"))
        assertEquals(1, service.loadStreams(source, first.single()).size)
    }


    @Test
    fun jsonLiveParserPreservesLegacyStreamMetadataFieldsAndAliases() {
        val channels = NativeLiveDescriptorParser.parseJsonLive(
            """{"channels":[{"id":"c1","title":"News","streams":["https://one.test/live.m3u8",{"mediaUrl":"https://two.test/live.m3u8","name":"Backup","priority":2,"protocol":"hls","headers":{"X-Test":"yes"},"cookies":"a=b","referer":"https://ref.test/","userAgent":"MyBoxTest","expiresAt":"2030-01-01T00:00:00Z"}]}]}"""
        )
        val streams = channels.single().getJSONArray("streams")
        assertEquals(2, streams.length())
        val first = streams.getJSONObject(0)
        assertEquals("", first.optString("label"))
        assertTrue(first.has("headers"))
        val backup = streams.getJSONObject(1)
        assertEquals("https://two.test/live.m3u8", backup.optString("url"))
        assertEquals("Backup", backup.optString("label"))
        assertEquals(2, backup.optInt("priority"))
        assertEquals("yes", backup.getJSONObject("headers").optString("X-Test"))
        assertEquals("a=b", backup.optString("cookies"))
        assertEquals("https://ref.test/", backup.optString("referer"))
        assertEquals("MyBoxTest", backup.optString("userAgent"))
        assertEquals("2030-01-01T00:00:00Z", backup.optString("expiresAt"))
    }

    @Test
    fun liveProtocolInferenceMatchesLegacyStreamHintsAndPreservesExplicitProtocols() {
        assertEquals("hls", NativeLiveDescriptorParser.inferLiveProtocolFromUrl("https://cdn.test/live.m3u8?token=x"))
        assertEquals("flv", NativeLiveDescriptorParser.inferLiveProtocolFromUrl("https://cdn.test/live.flv"))
        assertEquals("ts", NativeLiveDescriptorParser.inferLiveProtocolFromUrl("https://cdn.test/live.ts"))
        assertEquals("mp4", NativeLiveDescriptorParser.inferLiveProtocolFromUrl("https://cdn.test/movie.mp4"))
        assertEquals("dash", NativeLiveDescriptorParser.inferLiveProtocolFromUrl("https://cdn.test/manifest.mpd"))
        assertEquals("rtmp", NativeLiveDescriptorParser.inferLiveProtocolFromUrl("rtmps://cdn.test/live"))
        assertEquals("rtsp", NativeLiveDescriptorParser.inferLiveProtocolFromUrl("rtsps://cdn.test/live"))
        assertEquals("auto", NativeLiveDescriptorParser.inferLiveProtocolFromUrl("https://cdn.test/dynamic.php?id=1"))
        assertEquals("custom-proto", NativeLiveDescriptorParser.normalizeLiveProtocol("custom-proto", "https://cdn.test/live.m3u8"))
        assertEquals("hls", NativeLiveDescriptorParser.normalizeLiveProtocol("http", "https://cdn.test/live.m3u8"))
    }

    @Test
    fun epgRangeFilteringUsesLegacyOverlapSemanticsAndTimeAliases() {
        val programmes = listOf(
            JSONObject().put("programId", "before").put("startAt", "20261017080000 +0000").put("endAt", "20261017090000 +0000"),
            JSONObject().put("programId", "overlap").put("startTime", "20261017090000 +0000").put("endTime", "20261017100000 +0000"),
            JSONObject().put("programId", "after").put("startAt", "20261017110000 +0000").put("endAt", "20261017120000 +0000"),
        )
        val result = NativeLiveDescriptorParser.filterEpgByRange(programmes, "20261017090000 +0000", "20261017110000 +0000")
        assertEquals(listOf("before", "overlap", "after"), result.map { it.optString("programId") })
        assertEquals(listOf("overlap", "after"), NativeLiveDescriptorParser.filterEpgByRange(programmes, "20261017090001 +0000", null).map { it.optString("programId") })
        assertEquals(listOf("before", "overlap"), NativeLiveDescriptorParser.filterEpgByRange(programmes, null, "20261017100000 +0000").map { it.optString("programId") })
    }

    @Test
    fun tv1LineStreamsMatchLegacyAllowedUrlFormsAndIgnoreInvalidCandidates() {
        val streams = NativeLiveDescriptorParser.parseTv1LineStreams(
            "频道,https://ok.test/live.m3u8${'$'}主线#javascript:alert(1)#file:///etc/passwd#rtsp://ok.test/live"
        )
        assertEquals(2, streams.size)
        assertEquals("主线", streams[0].optString("label"))
        assertEquals("rtsp://ok.test/live", streams[1].optString("url"))
    }

    @Test
    fun nativeLiveDescriptorParsesLocalM3uAndXmlChannelSources() = runBlocking {
        var persisted = JSONObject()
        val cache = NativeLiveCacheStore({ JSONObject(persisted.toString()) }, { persisted = JSONObject(it.toString()) }, Unit)
        val service = NativeLiveDescriptorService(NativeHttpClient(), cache)
        val m3u = NativeContentSource.fromJson(JSONObject()
            .put("sourceId", "local-m3u").put("name", "M3U").put("sourceType", "live")
            .put("enabled", true).put("localFormat", "m3u")
            .put("localContent", "#EXTM3U\n#EXTINF:-1 tvg-id=\"one\" group-title=\"News\",Channel One\nhttps://example.test/one.m3u8"))
        val m3uChannels = service.loadChannels(m3u)
        assertEquals(1, m3uChannels.size)
        assertEquals("Channel One", m3uChannels.single().optString("name"))
        assertEquals("https://example.test/one.m3u8", m3uChannels.single().optJSONObject("stream")!!.optString("url"))
        val m3uStreams = service.loadStreams(m3u, m3uChannels.single())
        assertEquals("hls", m3uStreams.single().optString("protocol"))
        assertEquals(0.0, m3uStreams.single().optDouble("priority"), 0.0)

        val xml = NativeContentSource.fromJson(JSONObject()
            .put("sourceId", "local-xml").put("name", "XML").put("sourceType", "live")
            .put("enabled", true).put("localFormat", "xml")
            .put("localContent", "<channels><channel id=\"news\" display-name=\"News\"><stream url=\"https://example.test/news.m3u8\" label=\"Main\"/></channel></channels>"))
        val xmlChannels = service.loadChannels(xml)
        assertEquals(1, xmlChannels.size)
        assertEquals("News", xmlChannels.single().optString("name"))
        assertEquals("https://example.test/news.m3u8", xmlChannels.single().getJSONArray("streams").getJSONObject(0).optString("url"))
    }

    @Test
    fun xmlLiveAdapterAttachesXmltvProgrammesByChannelReferenceLikeLegacy() = runBlocking {
        var persisted = JSONObject()
        val cache = NativeLiveCacheStore({ JSONObject(persisted.toString()) }, { persisted = JSONObject(it.toString()) }, Unit)
        val service = NativeLiveDescriptorService(NativeHttpClient(), cache)
        val source = NativeContentSource.fromJson(JSONObject()
            .put("sourceId", "xml-with-epg")
            .put("name", "XML with EPG")
            .put("sourceType", "live")
            .put("enabled", true)
            .put("localFormat", "xml")
            .put("localContent", """<tv>
                <channel id="news"><display-name>News</display-name><stream url="https://example.test/news.m3u8"/></channel>
                <programme channel="news" start="20261017080000 +0000" stop="20261017090000 +0000"><title>Morning News</title><desc>Daily briefing</desc><category>News</category></programme>
                <programme channel="other" start="20261017090000 +0000" stop="20261017100000 +0000"><title>Other channel</title></programme>
            </tv>"""))

        val channels = service.loadChannels(source)
        assertEquals(1, channels.size)
        val epg = channels.single().getJSONArray("epg")
        assertEquals(1, epg.length())
        assertEquals("news", epg.getJSONObject(0).optString("channelRef"))
        assertEquals("Morning News", epg.getJSONObject(0).optString("title"))
        assertEquals("Daily briefing", epg.getJSONObject(0).optString("description"))
    }

    @Test
    fun liveDescriptorServiceRejectsDisabledWrongTypeAndEpgAsChannelSources() = runBlocking {
        var persisted = JSONObject()
        val cache = NativeLiveCacheStore({ JSONObject(persisted.toString()) }, { persisted = JSONObject(it.toString()) }, Unit)
        val service = NativeLiveDescriptorService(NativeHttpClient(), cache)
        val disabled = NativeContentSource.fromJson(JSONObject().put("sourceId", "disabled").put("sourceType", "live").put("enabled", false).put("localFormat", "json").put("localContent", "[]"))
        val disabledError = assertThrows(IllegalArgumentException::class.java) { runBlocking { service.loadChannels(disabled) } }
        assertEquals("SOURCE_DISABLED", disabledError.message)
        val vod = NativeContentSource.fromJson(JSONObject().put("sourceId", "vod").put("sourceType", "vod").put("enabled", true).put("localFormat", "json").put("localContent", "[]"))
        val typeError = assertThrows(IllegalArgumentException::class.java) { runBlocking { service.loadChannels(vod) } }
        assertEquals("SOURCE_TYPE_NOT_LIVE", typeError.message)
        val epg = NativeContentSource.fromJson(JSONObject().put("sourceId", "epg").put("sourceType", "live").put("enabled", true).put("localFormat", "xmltv").put("localContent", "<tv/>"))
        val epgError = assertThrows(IllegalArgumentException::class.java) { runBlocking { service.loadChannels(epg) } }
        assertEquals("LIVE_SOURCE_IS_EPG_NOT_CHANNELS", epgError.message)
    }


    @Test
    fun liveCacheGenerationFenceProtectsReadsAsWellAsWrites() {
        var persisted = JSONObject()
        val store = NativeLiveCacheStore({ JSONObject(persisted.toString()) }, { persisted = JSONObject(it.toString()) }, Unit)
        store.put("source-a", "epg", "today", JSONObject().put("items", org.json.JSONArray().put(JSONObject().put("title", "old"))))
        val captured = store.generation()
        val previous = org.json.JSONArray().put(JSONObject()
            .put("sourceId", "source-a").put("enabled", true).put("descriptorFingerprint", "old"))
        val next = org.json.JSONArray().put(JSONObject()
            .put("sourceId", "source-a").put("enabled", true).put("descriptorFingerprint", "new"))
        store.reconcile(previous, next)
        assertNull(store.getIfGeneration("source-a", "epg", "today", captured))
        assertNull(store.getIfGeneration("source-a", "epg", "today", store.generation()))
    }

    @Test
    fun liveDescriptorCacheKeysAndReturnedIdsKeepDuplicateChannelIdsSeparate() = runBlocking {
        var persisted = JSONObject()
        val cache = NativeLiveCacheStore({ JSONObject(persisted.toString()) }, { persisted = JSONObject(it.toString()) }, Unit)
        val source = NativeContentSource.fromJson(JSONObject()
            .put("sourceId", "same-source")
            .put("sourceType", "live")
            .put("enabled", true)
            .put("localFormat", "json")
            .put("localContent", """[{"id":"dup","title":"Same","url":"https://one.test/live.m3u8"},{"id":"dup","title":"Same","url":"https://two.test/live.m3u8"}]"""))
        val service = NativeLiveDescriptorService(NativeHttpClient(), cache)
        val channels = service.loadChannels(source)
        assertEquals(2, channels.size)
        assertEquals("same-source", channels[0].optString("sourceId"))
        assertTrue(channels[0].optString("sourceChannelId") != channels[1].optString("sourceChannelId"))
        val firstStreams = service.loadStreams(source, channels[0])
        val secondStreams = service.loadStreams(source, channels[1])
        assertEquals("https://one.test/live.m3u8", firstStreams.single().optString("url"))
        assertEquals("https://two.test/live.m3u8", secondStreams.single().optString("url"))
        assertEquals(channels[0].optString("sourceChannelId"), firstStreams.single().optString("sourceChannelId"))
        assertEquals(channels[1].optString("sourceChannelId"), secondStreams.single().optString("sourceChannelId"))

        val foreignChannel = JSONObject(channels[0].toString()).put("sourceId", "other-source")
        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.loadStreams(source, foreignChannel) }
        }
        assertEquals("LIVE_CHANNEL_SOURCE_MISMATCH", error.message)
    }


    @Test
    fun liveSourceDefinitionPolicyRejectsDeletedDisabledAndStaleDescriptors() {
        val original = NativeContentSource.fromJson(JSONObject()
            .put("sourceId", "live-a").put("sourceType", "live").put("enabled", true)
            .put("localFormat", "json").put("localContent", "[]"))
        assertEquals(original, NativeLiveSourceDefinitionPolicy.requireCurrent(original, original))

        val changed = NativeContentSource.fromJson(original.toJson().put("localContent", "[{\"id\":\"changed\"}]"))
        val stale = assertThrows(IllegalArgumentException::class.java) {
            NativeLiveSourceDefinitionPolicy.requireCurrent(original, changed)
        }
        assertEquals("LIVE_SOURCE_DEFINITION_STALE", stale.message)

        val disabled = NativeContentSource.fromJson(original.toJson().put("enabled", false))
        val disabledError = assertThrows(IllegalArgumentException::class.java) {
            NativeLiveSourceDefinitionPolicy.requireCurrent(original, disabled)
        }
        assertEquals("SOURCE_DISABLED", disabledError.message)

        val deletedError = assertThrows(IllegalArgumentException::class.java) {
            NativeLiveSourceDefinitionPolicy.requireCurrent(original, null)
        }
        assertEquals("SOURCE_NOT_FOUND", deletedError.message)
    }

    @Test
    fun liveDescriptorServiceChecksRepositoryBackedCurrentDefinitionBeforeLoading() = runBlocking {
        var persisted = JSONObject()
        val cache = NativeLiveCacheStore({ JSONObject(persisted.toString()) }, { persisted = JSONObject(it.toString()) }, Unit)
        val requested = NativeContentSource.fromJson(JSONObject()
            .put("sourceId", "mutable-live").put("sourceType", "live").put("enabled", true)
            .put("localFormat", "json").put("localContent", "[{\"id\":\"old\",\"title\":\"Old\",\"url\":\"https://example.test/old.m3u8\"}]"))
        var current: NativeContentSource? = requested
        val service = NativeLiveDescriptorService(NativeHttpClient(), cache, currentSourceLookup = { current })

        val oldChannels = service.loadChannels(requested)
        assertEquals(1, oldChannels.size)
        current = NativeContentSource.fromJson(requested.toJson().put("localContent", "[{\"id\":\"new\",\"title\":\"New\",\"url\":\"https://example.test/new.m3u8\"}]"))
        val staleError = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.loadChannels(requested) }
        }
        assertEquals("LIVE_SOURCE_DEFINITION_STALE", staleError.message)

        val currentDefinition = current!!
        val staleChannelError = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.loadStreams(currentDefinition, oldChannels.single()) }
        }
        assertEquals("LIVE_CHANNEL_SOURCE_STALE", staleChannelError.message)

        current = null
        val missingError = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { service.loadChannels(requested) }
        }
        assertEquals("SOURCE_NOT_FOUND", missingError.message)
    }

    @Test
    fun liveDescriptorParserRejectsOversizedInput() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            NativeLiveDescriptorParser.parseM3u("x".repeat(NativeLiveDescriptorParser.MAX_INPUT_CHARS + 1))
        }
        assertEquals("LIVE_SOURCE_INPUT_TOO_LARGE", error.message)
    }

    @Test
    fun sourceRuntimeStatusDoesNotMislabelTvboxLiveProvidersAsDescriptorSources() {
        val provider = NativeContentSource.fromJson(JSONObject()
            .put("sourceId", "provider")
            .put("name", "TVBox live provider")
            .put("sourceType", "live")
            .put("sourceRef", "https://example.test/live.txt")
            .put("enabled", true)
            .put("localFormat", "txt")
            .put("tvboxLiveProvider", true)
            .put("sourceCapability", "tvbox-live-provider")
            .put("adapterType", "tvbox-live-extension"))
        assertEquals("unsupported-tvbox-live-provider", NativeSourceRuntimeStatusPolicy.liveStatus(provider))

        val direct = NativeContentSource.fromJson(JSONObject()
            .put("sourceId", "direct")
            .put("name", "Direct TXT")
            .put("sourceType", "live")
            .put("sourceRef", "local://direct")
            .put("enabled", true)
            .put("localFormat", "txt")
            .put("liveMode", "tv1"))
        assertEquals("descriptor-service-available-runtime-pending", NativeSourceRuntimeStatusPolicy.liveStatus(direct))
    }

    @Test
    fun sourceRuntimeStatusKeepsUnsupportedTvboxExtensionsExplicit() {
        val drpyLive = NativeContentSource.fromJson(JSONObject()
            .put("sourceId", "drpy-live")
            .put("name", "DRPY live")
            .put("sourceType", "live")
            .put("sourceRef", "https://example.test/live.js")
            .put("enabled", true)
            .put("sourceCapability", "tvbox-drpy-js")
            .put("tvboxAdapterKind", "drpy-js"))
        assertEquals("unsupported-tvbox-live-extension", NativeSourceRuntimeStatusPolicy.liveStatus(drpyLive))
    }

}
