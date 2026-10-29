package com.yuivo9999.mybox123.app

/** Root-level destinations that mirror the existing MyBox information architecture. */
enum class AppDestination(
    val route: String,
    val label: String,
    val description: String,
) {
    HOME(
        route = "home",
        label = "首页",
        description = "内容入口、继续观看与推荐内容",
    ),
    LIVE(
        route = "live",
        label = "直播",
        description = "直播源、频道分组、频道浏览与原生播放",
    ),
    MOVIES(
        route = "movies",
        label = "影视",
        description = "影视分类、搜索、详情与剧集播放",
    ),
    MOVIE_PLAYBACK(
        route = "movie_playback",
        label = "影视播放",
        description = "影视剧集播放页面（原生 Media3 播放器）",
    ),
    FAVORITES(
        route = "favorites",
        label = "收藏",
        description = "收藏内容与播放记录",
    ),
    PROFILE(
        route = "profile",
        label = "我的",
        description = "内容源、仓库、外观与应用设置",
    );

    companion object {
        fun fromRoute(route: String?): AppDestination =
            entries.firstOrNull { it.route == route } ?: HOME
    }
}
