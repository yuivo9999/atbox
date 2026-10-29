package com.yuivo9999.mybox123.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppDestinationTest {
    @Test
    fun knownRoutesResolveToTheirDestination() {
        AppDestination.entries.forEach { destination ->
            assertEquals(destination, AppDestination.fromRoute(destination.route))
        }
    }

    @Test
    fun unknownOrMissingRouteFallsBackToHome() {
        assertEquals(AppDestination.HOME, AppDestination.fromRoute(null))
        assertEquals(AppDestination.HOME, AppDestination.fromRoute("not-a-route"))
    }
}
