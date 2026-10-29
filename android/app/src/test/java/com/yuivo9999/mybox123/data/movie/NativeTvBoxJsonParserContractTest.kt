package com.yuivo9999.mybox123.data.movie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NativeTvBoxJsonParserContractTest {
    @Test
    fun acceptsLegacyTopLevelArrayAndCommonVodArrayKeys() {
        val fixtures = listOf(
            "[{\"id\":\"a\",\"name\":\"Array item\"}]",
            "{\"movies\":[{\"id\":\"a\",\"name\":\"Movies item\"}]}",
            "{\"vod\":[{\"id\":\"a\",\"name\":\"Vod item\"}]}",
            "{\"list\":[{\"id\":\"a\",\"name\":\"List item\"}]}",
            "{\"data\":[{\"id\":\"a\",\"name\":\"Data item\"}]}",
            "{\"result\":[{\"id\":\"a\",\"name\":\"Result item\"}]}",
        )

        fixtures.forEachIndexed { index, fixture ->
            val parsed = NativeTvBoxJsonParser.parse(fixture, "fixture-source")
            assertEquals("fixture $index item count", 1, parsed.size)
            assertEquals("fixture $index source item id", "a", parsed.single().sourceItemId)
            assertEquals("fixture $index title", listOf("Array item", "Movies item", "Vod item", "List item", "Data item", "Result item")[index], parsed.single().title)
        }
    }

    @Test
    fun acceptsNestedDataListAndResultListContainers() {
        val fixtures = listOf(
            "{\"data\":{\"list\":[{\"vod_id\":\"nested-data\",\"vod_name\":\"Nested data\"}]}}",
            "{\"result\":{\"list\":[{\"id\":\"nested-result\",\"title\":\"Nested result\"}]}}",
        )
        val expected = listOf("nested-data" to "Nested data", "nested-result" to "Nested result")

        fixtures.forEachIndexed { index, fixture ->
            val parsed = NativeTvBoxJsonParser.parse(fixture, "fixture-source")
            assertEquals("fixture $index item count", 1, parsed.size)
            assertEquals(expected[index].first, parsed.single().sourceItemId)
            assertEquals(expected[index].second, parsed.single().title)
        }
    }


    @Test
    fun keepsEpisodeNumbersLocalToEachPlaybackRouteAndPreservesRouteMetadata() {
        val fixture = """
            {
              "vod_id": "movie-1",
              "vod_name": "Route fixture",
              "vod_play_from": "Primary__DOLLAR____DOLLAR____DOLLAR__Backup",
              "vod_play_url": "第1集__DOLLAR__https://media.example/a.m3u8#第2集__DOLLAR__https://media.example/b.m3u8__DOLLAR____DOLLAR____DOLLAR__第1集__DOLLAR__https://backup.example/a.m3u8#第2集__DOLLAR__https://backup.example/b.m3u8"
            }
        """.trimIndent().replace("__DOLLAR__", "${'$'}")

        val item = NativeTvBoxJsonParser.parse(fixture, "fixture-source").single().toJson()
        val episodes = item.getJSONArray("episodes")
        assertEquals(4, episodes.length())
        assertEquals(listOf(1, 2, 1, 2), (0 until episodes.length()).map { episodes.getJSONObject(it).getInt("episodeNumber") })
        assertEquals(listOf("第1集", "第2集", "第1集", "第2集"), (0 until episodes.length()).map { episodes.getJSONObject(it).getString("title") })

        val firstCandidate = episodes.getJSONObject(0).getJSONArray("playbackCandidates").getJSONObject(0)
        val backupCandidate = episodes.getJSONObject(2).getJSONArray("playbackCandidates").getJSONObject(0)
        assertEquals("https://media.example/a.m3u8", firstCandidate.getString("mediaUrl"))
        assertEquals("Primary", firstCandidate.getString("label"))
        assertEquals(0, firstCandidate.getJSONObject("metadata").getInt("tvboxRouteIndex"))
        assertEquals("Backup", backupCandidate.getString("label"))
        assertEquals(1, backupCandidate.getJSONObject("metadata").getInt("tvboxRouteIndex"))
    }

    @Test
    fun normalizesCommonSingleVodFieldsAndRetainsUnknownSourceFields() {
        val fixture = """
            {
              "id": "movie-2",
              "name": "  Sample title  ",
              "type_id": 7,
              "type_name": "Drama",
              "vod_pic": "https://media.example/poster.jpg",
              "vod_pic_slide": "https://media.example/backdrop.jpg",
              "vod_content": "<p>Story&nbsp;line</p>",
              "vod_year": "2025",
              "vod_area": "Canada",
              "vod_director": "Director",
              "vod_actor": "Actor A,Actor B",
              "vod_hits": "42",
              "vod_remarks": "更新至第8集",
              "vod_score": "8.1",
              "customSourceField": "preserve-me"
            }
        """.trimIndent()

        val item = NativeTvBoxJsonParser.parse(fixture, "fixture-source").single().toJson()
        assertEquals("Sample title", item.getString("title"))
        assertEquals("7", item.getString("sourceCategoryId"))
        assertEquals("Drama", item.getString("sourceCategoryName"))
        assertEquals("https://media.example/poster.jpg", item.getString("poster"))
        assertEquals("https://media.example/backdrop.jpg", item.getString("backdrop"))
        assertEquals("Story line", item.getString("description"))
        assertEquals("2025", item.getString("year"))
        assertEquals("Canada", item.getString("region"))
        assertEquals("Director", item.getString("director"))
        assertEquals(listOf("Actor A", "Actor B"), (0 until item.getJSONArray("actors").length()).map { item.getJSONArray("actors").getString(it) })
        assertEquals(42.0, item.getDouble("popularity"), 0.0)
        assertEquals("更新至第8集", item.getString("updateInfo"))
        assertEquals("8.1", item.getString("rating"))
        assertEquals("preserve-me", item.getString("customSourceField"))
    }


    @Test
    fun preservesExplicitEpisodeUrlProtocolAndHeaders() {
        val fixture = """
            {
              "id": "movie-3",
              "episodes": [
                {
                  "title": "Episode via url",
                  "url": "https://media.example/episode.m3u8",
                  "protocol": "hls",
                  "headers": {"Referer": "https://episode.example/", "X-Episode": "episode-value"},
                  "quality": "1080p"
                },
                {
                  "title": "Episode via mediaUrl",
                  "mediaUrl": "https://media.example/episode2.mpd",
                  "protocol": "dash",
                  "headers": {"Authorization": "Bearer episode-token"}
                }
              ]
            }
        """.trimIndent()
        val sourceMetadata = org.json.JSONObject().put("headers", org.json.JSONObject().put("X-Source", "source-value"))
        val item = NativeTvBoxJsonParser.parse(fixture, "fixture-source", sourceMetadata).single().toJson()
        val episodes = item.getJSONArray("episodes")
        assertEquals(2, episodes.length())

        val first = episodes.getJSONObject(0).getJSONArray("playbackCandidates").getJSONObject(0)
        assertEquals("https://media.example/episode.m3u8", first.getString("mediaUrl"))
        assertEquals("hls", first.getString("protocol"))
        assertEquals("1080p", first.getString("quality"))
        assertEquals("https://episode.example/", first.getJSONObject("headers").getString("Referer"))
        assertEquals("episode-value", first.getJSONObject("headers").getString("X-Episode"))
        assertEquals("source-value", first.getJSONObject("headers").getString("X-Source"))

        val second = episodes.getJSONObject(1).getJSONArray("playbackCandidates").getJSONObject(0)
        assertEquals("https://media.example/episode2.mpd", second.getString("mediaUrl"))
        assertEquals("dash", second.getString("protocol"))
        assertEquals("Bearer episode-token", second.getJSONObject("headers").getString("Authorization"))
    }

    @Test
    fun preservesExplicitPlaybackCandidateUrlProtocolAndHeaders() {
        val fixture = """
            {
              "id": "movie-4",
              "eps": [
                {
                  "title": "Candidate fixture",
                  "playbackCandidates": [
                    {"url": "https://media.example/candidate.m3u8", "protocol": "hls", "headers": {"Referer": "https://candidate.example/"}},
                    {"mediaUrl": "https://media.example/candidate.mp4", "protocol": "mp4", "headers": {"X-Token": "candidate-token"}}
                  ]
                }
              ]
            }
        """.trimIndent()
        val item = NativeTvBoxJsonParser.parse(fixture, "fixture-source").single().toJson()
        val candidates = item.getJSONArray("episodes").getJSONObject(0).getJSONArray("playbackCandidates")
        assertEquals(2, candidates.length())
        assertEquals("https://media.example/candidate.m3u8", candidates.getJSONObject(0).getString("mediaUrl"))
        assertEquals("hls", candidates.getJSONObject(0).getString("protocol"))
        assertEquals("https://candidate.example/", candidates.getJSONObject(0).getJSONObject("headers").getString("Referer"))
        assertEquals("https://media.example/candidate.mp4", candidates.getJSONObject(1).getString("mediaUrl"))
        assertEquals("mp4", candidates.getJSONObject(1).getString("protocol"))
        assertEquals("candidate-token", candidates.getJSONObject(1).getJSONObject("headers").getString("X-Token"))
    }

    @Test
    fun rejectsValidJsonThatDoesNotContainRecognizedVodArray() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeTvBoxJsonParser.parse("{\"data\":{\"items\":[{\"id\":\"x\"}]}}", "fixture-source")
        }
    }
}
