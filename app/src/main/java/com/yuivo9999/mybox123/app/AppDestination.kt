package com.yuivo9999.mybox123.app

/** Root-level destinations that mirror the original MyBox / TVBox architecture. */
enum class AppDestination(
    val route: String,
    val label: String,
    val description: String,
    val isNavTab: Boolean = true,
) {
    HOME(
        route = "home",
        label = "首页",
        description = "内容入口、焦点推荐与内容专区",
    ),
    MOVIES(
        route = "movies",
        label = "影视",
        description = "影视分类、筛选搜索、详情与剧集列表",
    ),
    LIVE(
        route = "live",
        label = "直播",
        description = "央卫视直播、频道分组与原生线路播放",
    ),
    FAVORITES(
        route = "favorites",
        label = "收藏",
        description = "影视与 Live 收藏",
    ),
    PROFILE(
        route = "profile",
        label = "我的",
        description = "内容源、多仓管理、播放与外观设置",
    ),
    MOVIE_PLAYBACK(
        route = "movie_playback",
        label = "影视播放",
        description = "影视剧集播放页面（原生 Media3 播放器）",
        isNavTab = false,
    );

    companion object {
        val tabEntries = listOf(HOME, MOVIES, LIVE, FAVORITES, PROFILE)

        fun fromRoute(route: String?): AppDestination =
            entries.firstOrNull { it.route == route } ?: HOME
    }
}
