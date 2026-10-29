package com.yuivo9999.mybox123.data.movie

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class NativeTvBoxJsonParserTest {
    @Test
    fun parsesNestedDataListAndTvBoxEpisodeRoutes() {
        val payload = """
            {
              "total": 1,
              "data": {"list": [{
                "vod_id": "movie-7",
                "vod_name": "测试影片",
                "type_id": 3,
                "type_name": "动作片",
                "vod_pic": "https://example.com/poster.jpg",
                "vod_play_from": "线路甲",
                "vod_play_url": "第1集${'$'}https://example.com/ep1.m3u8#第2集${'$'}https://example.com/ep2.m3u8"
              }]}
            }
        """.trimIndent()

        val items = NativeTvBoxJsonParser.parse(payload, "source-1", JSONObject().put("referer", "https://example.com/"))

        assertEquals(1, items.size)
        assertEquals("movie-7", items.single().sourceItemId)
        assertEquals("测试影片", items.single().title)
        assertEquals("动作片", items.single().category)
        assertEquals(2, items.single().episodes.size)
        assertEquals("https://example.com/ep1.m3u8", items.single().episodes[0].optJSONArray("playbackCandidates")?.optJSONObject(0)?.optString("mediaUrl"))
        assertEquals("https://example.com/", items.single().episodes[0].optJSONArray("playbackCandidates")?.optJSONObject(0)?.optString("referer"))
    }

    @Test
    fun rejectsNonVodJsonAndMalformedPayloads() {
        val nonVod = runCatching { NativeTvBoxJsonParser.parse("{\"message\":\"ok\"}", "source-1") }.exceptionOrNull()
        val malformed = runCatching { NativeTvBoxJsonParser.parse("{broken", "source-1") }.exceptionOrNull()
        assertTrue(nonVod?.message?.contains("TVBOX_EXT_JSON_NOT_VOD_DATA") == true)
        assertTrue(malformed?.message?.contains("TVBOX_EXT_JSON_INVALID") == true)
    }
}
