package com.yuivo9999.mybox123.domain.movie

import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeMovieRequestUrlPolicyTest {
    @Test
    fun preservesExistingQueryValuesAndAddsOnlyMissingParameters() {
        val url = NativeMovieRequestUrlPolicy.build(
            endpoint = "https://example.test/api.php/provide/vod?pg=9&wd=keep&t=2&limit=7&token=abc",
            categoriesOnly = false,
            categoryId = "99",
            itemId = "movie-4",
            page = 3,
            pageSize = 24,
            keyword = "new",
        )
        val parsed = HttpUrl.parse(url)!!
        assertEquals("detail", parsed.queryParameter("ac"))
        assertEquals("9", parsed.queryParameter("pg"))
        assertEquals("keep", parsed.queryParameter("wd"))
        assertEquals("2", parsed.queryParameter("t"))
        assertEquals("7", parsed.queryParameter("limit"))
        assertEquals("abc", parsed.queryParameter("token"))
        assertEquals("movie-4", parsed.queryParameter("ids"))
    }

    @Test
    fun categoriesModeSetsListActionAndDoesNotReplaceExistingLimit() {
        val url = NativeMovieRequestUrlPolicy.build(
            endpoint = "https://example.test/api.php/provide/vod?limit=12",
            categoriesOnly = true,
        )
        val parsed = HttpUrl.parse(url)!!
        assertEquals("list", parsed.queryParameter("ac"))
        assertEquals("12", parsed.queryParameter("limit"))
    }

    @Test
    fun explicitDetailIdOverridesConfiguredIdsButPreservesOtherQueryParameters() {
        val url = NativeMovieRequestUrlPolicy.build(
            endpoint = "https://example.test/api.php/provide/vod?ac=list&ids=stale&token=keep&pg=8",
            categoriesOnly = false,
            itemId = "requested-item",
            page = 2,
        )
        val parsed = HttpUrl.parse(url)!!
        assertEquals("detail", parsed.queryParameter("ac"))
        assertEquals("requested-item", parsed.queryParameter("ids"))
        assertEquals("keep", parsed.queryParameter("token"))
        assertEquals("8", parsed.queryParameter("pg"))
    }

    @Test
    fun configuredIdsRemainWhenNoDetailIdWasRequested() {
        val url = NativeMovieRequestUrlPolicy.build(
            endpoint = "https://example.test/api.php/provide/vod?ids=configured&token=keep",
            categoriesOnly = false,
        )
        val parsed = HttpUrl.parse(url)!!
        assertEquals("configured", parsed.queryParameter("ids"))
        assertEquals("keep", parsed.queryParameter("token"))
    }

    @Test
    fun staticJsonResourcesDoNotReceiveVodQueryParameters() {
        val endpoint = "https://example.test/catalog.json?token=abc"
        assertEquals(endpoint, NativeMovieRequestUrlPolicy.build(endpoint, categoriesOnly = false, page = 3, forceStaticJson = true))
        assertNull(HttpUrl.parse("not a url"))
        assertTrue(NativeMovieRequestUrlPolicy.build("https://example.test/catalog.json", categoriesOnly = false).contains("catalog.json"))
    }
}
