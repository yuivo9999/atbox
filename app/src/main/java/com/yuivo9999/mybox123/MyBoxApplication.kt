package com.yuivo9999.mybox123

import android.app.Application
import com.yuivo9999.mybox123.core.network.NativeHttpClient
import com.yuivo9999.mybox123.data.source.NativeContentSourceRepository
import com.yuivo9999.mybox123.data.storage.NativeUserDataRepository
import com.yuivo9999.mybox123.domain.movie.NativeMovieSourceService
import com.yuivo9999.mybox123.domain.source.NativeLiveDescriptorService
import com.yuivo9999.mybox123.domain.source.NativeSourceManagementService

class AppServices(
    val userDataRepository: NativeUserDataRepository,
    val contentSourceRepository: NativeContentSourceRepository,
    val sourceManagementService: NativeSourceManagementService,
    val movieSourceService: NativeMovieSourceService,
    val liveDescriptorService: NativeLiveDescriptorService,
    val httpClient: NativeHttpClient,
    val storage: NativeUserDataRepository,
)

class MyBoxApplication : Application() {
    private val services by lazy {
        val userRepo = NativeUserDataRepository(this)
        val sourceRepo = NativeContentSourceRepository(this)
        AppServices(
            userDataRepository = userRepo,
            contentSourceRepository = sourceRepo,
            sourceManagementService = NativeSourceManagementService(sourceRepo),
            movieSourceService = NativeMovieSourceService(),
            liveDescriptorService = NativeLiveDescriptorService(),
            httpClient = NativeHttpClient(),
            storage = userRepo,
        )
    }

    suspend fun awaitServices(): AppServices = services
}
