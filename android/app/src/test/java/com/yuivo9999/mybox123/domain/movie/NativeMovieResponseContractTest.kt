package com.yuivo9999.mybox123.domain.movie

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class NativeMovieResponseContractTest {
    @Test
    fun stripsBomAndWhitespaceFromJsonBody() {
        assertEquals("{\"list\":[]}", NativeMovieResponseContract.normalizeBody("\uFEFF  {\"list\":[]}  ", emptyMap(), 100))
    }

    @Test
    fun rejectsEmptyHtmlAndOversizedBodiesWithStableCodes() {
        assertFailure("MOVIE_SOURCE_EMPTY_RESPONSE") {
            NativeMovieResponseContract.normalizeBody(" \n", emptyMap(), 100)
        }
        assertFailure("MOVIE_SOURCE_HTML_RESPONSE") {
            NativeMovieResponseContract.normalizeBody("<html>blocked</html>", mapOf("Content-Type" to "text/html"), 100)
        }
        assertFailure("MOVIE_SOURCE_RESPONSE_TOO_LARGE") {
            NativeMovieResponseContract.normalizeBody("12345", emptyMap(), 4)
        }
    }

    @Test
    fun rejectsInvalidJsonAndTrailingGarbage() {
        assertFailure("MOVIE_SOURCE_JSON_INVALID") { NativeMovieResponseContract.validateJsonSyntax("{not-json}") }
        assertFailure("MOVIE_SOURCE_JSON_INVALID") { NativeMovieResponseContract.validateJsonSyntax("{} trailing") }
    }

    @Test
    fun acceptsJsonArrayAndObjectEnvelopes() {
        NativeMovieResponseContract.validateJsonSyntax("[]")
        NativeMovieResponseContract.validateJsonSyntax("{\"list\":[]}")
    }

    @Test
    fun mapsTransportFailuresToStableDomainCodes() {
        assertEquals("MOVIE_SOURCE_RESPONSE_TOO_LARGE", NativeMovieResponseContract.requestFailureCode("RESPONSE_TOO_LARGE", 0))
        assertEquals("MOVIE_SOURCE_TIMEOUT", NativeMovieResponseContract.requestFailureCode("TIMEOUT", 0))
        assertEquals("MOVIE_SOURCE_REQUEST_FAILED:HTTP_503", NativeMovieResponseContract.requestFailureCode(null, 503))
    }

    private fun assertFailure(expectedMessage: String, action: () -> Unit) {
        try {
            action()
            fail("Expected $expectedMessage")
        } catch (error: Exception) {
            assertEquals(expectedMessage, error.message)
        }
    }
}
