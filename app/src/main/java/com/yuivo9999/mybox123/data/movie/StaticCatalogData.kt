package com.yuivo9999.mybox123.data.movie

import org.json.JSONArray
import org.json.JSONObject

/**
 * Curated static cinema and live dataset matching homeData.js from the frontend.
 * Provides instant cold-start content and reliable fallback when online sources are loading.
 */
object StaticCatalogData {

    val categories = listOf(
        NativeMovieCategory(id = "movie", name = "电影", rawJson = "{\"id\":\"movie\",\"name\":\"电影\"}"),
        NativeMovieCategory(id = "series", name = "电视剧", rawJson = "{\"id\":\"series\",\"name\":\"电视剧\"}"),
        NativeMovieCategory(id = "anime", name = "动漫", rawJson = "{\"id\":\"anime\",\"name\":\"动漫\"}"),
        NativeMovieCategory(id = "variety", name = "综艺", rawJson = "{\"id\":\"variety\",\"name\":\"综艺\"}"),
        NativeMovieCategory(id = "short_drama", name = "短剧", rawJson = "{\"id\":\"short_drama\",\"name\":\"短剧\"}"),
        NativeMovieCategory(id = "all", name = "全部", rawJson = "{\"id\":\"all\",\"name\":\"全部\"}")
    )

    val movies: List<NativeMovieItem> by lazy {
        listOf(
            createMovie(
                contentId = "static_movie_001",
                title = "流浪地球2",
                category = "电影",
                sourceCategoryName = "科幻片",
                year = "2023",
                region = "中国大陆",
                rating = "8.3",
                updateInfo = "4K超清国语",
                poster = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=1200&q=80",
                description = "太阳即将毁灭，人类在地球表面建造出巨大的推进器，寻找新的家园，开启漫长的流浪地球时代。",
                director = "郭帆",
                actors = listOf("吴京", "刘德华", "李雪健", "沙溢", "宁理"),
                episodes = listOf(
                    EpisodeDef(
                        title = "正片 4K",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                        candidates = listOf(
                            CandidateDef("官方4K专线", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"),
                            CandidateDef("极速HLS线路", "https://bitmovin-a.akamaihd.net/content/MI201109210084_1/m3u8s/f08e80da-bf1d-4e3d-8899-f0f6155f6efa.m3u8")
                        )
                    )
                )
            ),
            createMovie(
                contentId = "static_movie_002",
                title = "封神第一部：朝歌风云",
                category = "电影",
                sourceCategoryName = "动作片",
                year = "2023",
                region = "中国大陆",
                rating = "7.8",
                updateInfo = "4K臻彩画质",
                poster = "https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=1200&q=80",
                description = "商王殷寿勾结狐妖妲己，暴虐无道，引发天谴。昆仑仙人姜子牙携封神榜下山，寻找天下共主。",
                director = "乌尔善",
                actors = listOf("费翔", "李雪健", "黄渤", "于适", "陈牧驰", "娜然"),
                episodes = listOf(
                    EpisodeDef(
                        title = "正片 4K",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                        candidates = listOf(
                            CandidateDef("东方神话4K", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"),
                            CandidateDef("HLS高码率", "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8")
                        )
                    )
                )
            ),
            createMovie(
                contentId = "static_movie_003",
                title = "星际穿越",
                category = "电影",
                sourceCategoryName = "科幻片",
                year = "2014",
                region = "欧美",
                rating = "9.4",
                updateInfo = "IMAX 4K重置版",
                poster = "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?auto=format&fit=crop&w=1200&q=80",
                description = "世界末日来临，一组探险家利用新发现的虫洞，超越人类太空旅行的极限，为人类寻找希望。",
                director = "克里斯托弗·诺兰",
                actors = listOf("马修·麦康纳", "安妮·海瑟薇", "杰西卡·查斯坦"),
                episodes = listOf(
                    EpisodeDef(
                        title = "正片 IMAX",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4",
                        candidates = listOf(
                            CandidateDef("IMAX 极速专线", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4")
                        )
                    )
                )
            ),
            createMovie(
                contentId = "static_movie_004",
                title = "奥本海默",
                category = "电影",
                sourceCategoryName = "剧情片",
                year = "2023",
                region = "欧美",
                rating = "8.8",
                updateInfo = "4K杜比视界",
                poster = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?auto=format&fit=crop&w=1200&q=80",
                description = "克里斯托弗·诺兰执导的历史传记片，讲述美国物理学家罗伯特·奥本海默研制原子弹的历史过程。",
                director = "克里斯托弗·诺兰",
                actors = listOf("基里安·墨菲", "艾米莉·布朗特", "马特·达蒙", "小罗伯特·唐尼"),
                episodes = listOf(
                    EpisodeDef(
                        title = "正片 4K",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                        candidates = listOf(
                            CandidateDef("4K原画线路", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4")
                        )
                    )
                )
            ),
            createMovie(
                contentId = "static_movie_005",
                title = "沙丘2",
                category = "电影",
                sourceCategoryName = "科幻片",
                year = "2024",
                region = "欧美",
                rating = "8.4",
                updateInfo = "4K杜比全景声",
                poster = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=1200&q=80",
                description = "保罗·厄崔迪携手契妮和弗雷曼人，为向毁灭其家族的阴谋者复仇而战。",
                director = "丹尼斯·维伦纽瓦",
                actors = listOf("提莫西·查拉梅", "赞达亚", "丽贝卡·弗格森", "哈维尔·巴登"),
                episodes = listOf(
                    EpisodeDef(
                        title = "正片 4K",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                        candidates = listOf(
                            CandidateDef("杜比视界4K", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4")
                        )
                    )
                )
            ),
            createMovie(
                contentId = "static_movie_006",
                title = "热辣滚烫",
                category = "电影",
                sourceCategoryName = "喜剧片",
                year = "2024",
                region = "中国大陆",
                rating = "7.8",
                updateInfo = "4K超清国语",
                poster = "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=1200&q=80",
                description = "宅家多年的杜乐莹结识拳击教练昊坤，开启突破自我、找回人生节奏的励志篇章。",
                director = "贾玲",
                actors = listOf("贾玲", "雷佳音", "张小斐", "杨紫", "沙溢"),
                episodes = listOf(
                    EpisodeDef(
                        title = "正片 4K",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                        candidates = listOf(
                            CandidateDef("励志欢乐专线", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4")
                        )
                    )
                )
            ),
            createMovie(
                contentId = "static_movie_007",
                title = "飞驰人生2",
                category = "电影",
                sourceCategoryName = "喜剧片",
                year = "2024",
                region = "中国大陆",
                rating = "7.7",
                updateInfo = "4K超清国语",
                poster = "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?auto=format&fit=crop&w=1200&q=80",
                description = "昔日冠军车手张驰重出江湖，组建草台班子笑料百出，再次奔赴最后一届巴音布鲁克拉力赛。",
                director = "韩寒",
                actors = listOf("沈腾", "范丞丞", "尹正", "张本煜", "孙艺洲"),
                episodes = listOf(
                    EpisodeDef(
                        title = "正片 4K",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                        candidates = listOf(
                            CandidateDef("赛车疾速4K", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
                        )
                    )
                )
            ),
            createMovie(
                contentId = "static_movie_010",
                title = "长安三万里",
                category = "动漫",
                sourceCategoryName = "国创动画",
                year = "2023",
                region = "中国大陆",
                rating = "8.3",
                updateInfo = "4K盛唐画卷",
                poster = "https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=1200&q=80",
                description = "安史之乱爆发，高适回忆与李白的往昔岁月，展现大唐璀璨群星与壮美诗篇。",
                director = "谢君伟, 邹靖",
                actors = listOf("杨天翔", "凌振赫", "吴俊全", "宣晓鸣"),
                episodes = listOf(
                    EpisodeDef(
                        title = "正片 4K",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        candidates = listOf(
                            CandidateDef("盛唐国风4K", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                        )
                    )
                )
            ),
            // TV Series
            createMovie(
                contentId = "static_series_001",
                title = "庆余年 第二季",
                category = "电视剧",
                sourceCategoryName = "古装悬疑",
                year = "2024",
                region = "中国大陆",
                rating = "8.2",
                updateInfo = "更新至36集全",
                poster = "https://images.unsplash.com/photo-1514533450685-4493e01d1fdc?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=1200&q=80",
                description = "范闲率领使团回京，面临二皇子与长公主的步步紧逼，在京都风云中展开智谋较量。",
                director = "孙皓",
                actors = listOf("张若昀", "李沁", "陈道明", "吴刚", "郭麒麟"),
                episodes = (1..6).map { idx ->
                    EpisodeDef(
                        title = "第 $idx 集",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4",
                        candidates = listOf(
                            CandidateDef("极速超清", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4")
                        )
                    )
                }
            ),
            createMovie(
                contentId = "static_series_002",
                title = "三体",
                category = "电视剧",
                sourceCategoryName = "科幻巨制",
                year = "2023",
                region = "中国大陆",
                rating = "8.7",
                updateInfo = "30集全",
                poster = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?auto=format&fit=crop&w=1200&q=80",
                description = "地球基础科学出现异常扰动，纳米物理学家汪淼与刑警史强联手调查，揭开地外文明的神秘面纱。",
                director = "杨磊",
                actors = listOf("张鲁一", "于和伟", "陈瑾", "王子文", "李小冉"),
                episodes = (1..6).map { idx ->
                    EpisodeDef(
                        title = "第 $idx 集",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                        candidates = listOf(
                            CandidateDef("原画4K", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4")
                        )
                    )
                }
            ),
            createMovie(
                contentId = "static_series_004",
                title = "狂飙",
                category = "电视剧",
                sourceCategoryName = "扫黑刑侦",
                year = "2023",
                region = "中国大陆",
                rating = "8.5",
                updateInfo = "39集全",
                poster = "https://images.unsplash.com/photo-1485846234645-a62644f84728?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=1200&q=80",
                description = "刑警安欣与鱼贩高启强跨越二十年的正邪较量，生动展现扫黑除恶常态化的坚定斗争。",
                director = "徐纪周",
                actors = listOf("张译", "张颂文", "李一桐", "张志坚", "吴刚"),
                episodes = (1..6).map { idx ->
                    EpisodeDef(
                        title = "第 $idx 集",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4",
                        candidates = listOf(
                            CandidateDef("高清主线", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4")
                        )
                    )
                }
            ),
            // Anime
            createMovie(
                contentId = "static_anime_001",
                title = "凡人修仙传",
                category = "动漫",
                sourceCategoryName = "玄幻修真",
                year = "2023",
                region = "中国大陆",
                rating = "8.9",
                updateInfo = "每周更新",
                poster = "https://images.unsplash.com/photo-1578632767115-351597cf2477?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1534447677768-be436bb09401?auto=format&fit=crop&w=1200&q=80",
                description = "一个普通的山村穷小子韩立，偶然之下跨入江湖，踏上艰险漫长的求仙问道之路。",
                director = "王裕仁",
                actors = listOf("钱文青", "杨天翔", "佟心竹"),
                episodes = (1..6).map { idx ->
                    EpisodeDef(
                        title = "第 $idx 集",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        candidates = listOf(
                            CandidateDef("4K国创专线", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                        )
                    )
                }
            ),
            // Variety
            createMovie(
                contentId = "static_variety_001",
                title = "种地吧 第二季",
                category = "综艺",
                sourceCategoryName = "真人秀",
                year = "2024",
                region = "中国大陆",
                rating = "8.6",
                updateInfo = "更新至最新期",
                poster = "https://images.unsplash.com/photo-1500937386664-56d1dfef3854?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1506703719100-a0f3a48c0f86?auto=format&fit=crop&w=1200&q=80",
                description = "十位年轻少年在百亩农田中踏实劳作、躬耕助农，记录真实的现代农业成长历程。",
                director = "杨长岭",
                actors = listOf("陈少熙", "何浩楠", "蒋敦豪", "李耕耘", "李昊"),
                episodes = (1..4).map { idx ->
                    EpisodeDef(
                        title = "第 $idx 期",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4",
                        candidates = listOf(
                            CandidateDef("官方超清", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4")
                        )
                    )
                }
            ),
            // Short drama
            createMovie(
                contentId = "static_drama_001",
                title = "无双龙王殿",
                category = "短剧",
                sourceCategoryName = "都市爽剧",
                year = "2024",
                region = "中国大陆",
                rating = "7.5",
                updateInfo = "全80集",
                poster = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?auto=format&fit=crop&w=600&q=80",
                backdrop = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?auto=format&fit=crop&w=1200&q=80",
                description = "龙王归隐都市化身平民，受尽欺凌后王者归来，惩奸除恶，重临巅峰！",
                director = "张驰",
                actors = listOf("李易", "张雅"),
                episodes = (1..6).map { idx ->
                    EpisodeDef(
                        title = "第 $idx 集",
                        url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                        candidates = listOf(
                            CandidateDef("极速直链", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4")
                        )
                    )
                }
            )
        )
    }

    val liveChannels: List<JSONObject> by lazy {
        listOf(
            createChannel(
                channelId = "live_cctv1",
                name = "CCTV-1 综合",
                category = "央视",
                logo = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?auto=format&fit=crop&w=300&q=80",
                currentProgram = "新闻联播 / 焦点访谈",
                streams = listOf(
                    StreamDef("超清电信源", "http://39.134.115.163:8080/PLTV/88888910/224/3221225632/index.m3u8"),
                    StreamDef("备用流畅源", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                )
            ),
            createChannel(
                channelId = "live_cctv13",
                name = "CCTV-13 新闻",
                category = "央视",
                logo = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?auto=format&fit=crop&w=300&q=80",
                currentProgram = "共同关注 / 东方时空",
                streams = listOf(
                    StreamDef("超清移动源", "http://39.134.115.163:8080/PLTV/88888910/224/3221225632/index.m3u8")
                )
            ),
            createChannel(
                channelId = "live_cctv5",
                name = "CCTV-5 体育",
                category = "央视",
                logo = "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?auto=format&fit=crop&w=300&q=80",
                currentProgram = "体育世界 / 国际赛事直播",
                streams = listOf(
                    StreamDef("体育专用超清", "http://39.134.115.163:8080/PLTV/88888910/224/3221225632/index.m3u8")
                )
            ),
            createChannel(
                channelId = "live_cctv6",
                name = "CCTV-6 电影",
                category = "央视",
                logo = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=300&q=80",
                currentProgram = "佳片有约 / 黄金档影院",
                streams = listOf(
                    StreamDef("院线电影超清", "http://39.134.115.163:8080/PLTV/88888910/224/3221225632/index.m3u8")
                )
            ),
            createChannel(
                channelId = "live_hunan",
                name = "湖南卫视",
                category = "卫视",
                logo = "https://images.unsplash.com/photo-1598899134739-24c46f58b8c0?auto=format&fit=crop&w=300&q=80",
                currentProgram = "金鹰独播剧场 / 芒果精选",
                streams = listOf(
                    StreamDef("湖南高清专线", "http://39.134.115.163:8080/PLTV/88888910/224/3221225632/index.m3u8")
                )
            ),
            createChannel(
                channelId = "live_zhejiang",
                name = "浙江卫视",
                category = "卫视",
                logo = "https://images.unsplash.com/photo-1598899134739-24c46f58b8c0?auto=format&fit=crop&w=300&q=80",
                currentProgram = "中国好声音 / 奔跑吧精选",
                streams = listOf(
                    StreamDef("浙江高清专线", "http://39.134.115.163:8080/PLTV/88888910/224/3221225632/index.m3u8")
                )
            )
        )
    }

    private data class CandidateDef(val label: String, val mediaUrl: String)
    private data class EpisodeDef(val title: String, val url: String, val candidates: List<CandidateDef> = emptyList())
    private data class StreamDef(val label: String, val mediaUrl: String)

    private fun createMovie(
        contentId: String,
        title: String,
        category: String,
        sourceCategoryName: String,
        year: String,
        region: String,
        rating: String,
        updateInfo: String,
        poster: String,
        backdrop: String,
        description: String,
        director: String,
        actors: List<String>,
        episodes: List<EpisodeDef>
    ): NativeMovieItem {
        val json = JSONObject()
            .put("contentId", contentId)
            .put("sourceId", "builtin")
            .put("sourceItemId", contentId)
            .put("title", title)
            .put("category", category)
            .put("sourceCategoryName", sourceCategoryName)
            .put("year", year)
            .put("region", region)
            .put("rating", rating)
            .put("updateInfo", updateInfo)
            .put("poster", poster)
            .put("backdrop", backdrop)
            .put("description", description)
            .put("director", director)
            .put("actors", JSONArray(actors))
            .put("episodes", JSONArray().apply {
                episodes.forEachIndexed { index, ep ->
                    put(JSONObject()
                        .put("episodeId", "$contentId:ep${index + 1}")
                        .put("title", ep.title)
                        .put("name", ep.title)
                        .put("episodeNumber", index + 1)
                        .put("url", ep.url)
                        .put("playUrl", ep.url)
                        .put("candidates", JSONArray().apply {
                            ep.candidates.forEach { c ->
                                put(JSONObject().put("label", c.label).put("mediaUrl", c.mediaUrl))
                            }
                        })
                    )
                }
            })

        return NativeMovieItem.fromJson(json)
    }

    private fun createChannel(
        channelId: String,
        name: String,
        category: String,
        logo: String,
        currentProgram: String,
        streams: List<StreamDef>
    ): JSONObject {
        return JSONObject()
            .put("channelId", channelId)
            .put("name", name)
            .put("category", category)
            .put("logo", logo)
            .put("currentProgram", currentProgram)
            .put("streams", JSONArray().apply {
                streams.forEachIndexed { index, s ->
                    put(JSONObject()
                        .put("streamId", "stream_${index + 1}")
                        .put("name", s.label)
                        .put("url", s.mediaUrl)
                        .put("playUrl", s.mediaUrl)
                    )
                }
            })
    }
}
