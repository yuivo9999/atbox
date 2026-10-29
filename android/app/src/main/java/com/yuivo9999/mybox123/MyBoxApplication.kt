package com.yuivo9999.mybox123

import android.app.Application
import android.util.Log
import com.yuivo9999.mybox123.core.network.NativeHttpClient
import com.yuivo9999.mybox123.data.source.NativeContentSourceRepository
import com.yuivo9999.mybox123.domain.movie.NativeMovieSourceService
import com.yuivo9999.mybox123.data.storage.LegacyMigrationResult
import com.yuivo9999.mybox123.data.storage.LegacyUserDataMigrator
import com.yuivo9999.mybox123.data.storage.NativeUserDataDatabase
import com.yuivo9999.mybox123.data.storage.NativeUserDataRepository
import com.yuivo9999.mybox123.domain.source.NativeSourceManagementService
import com.yuivo9999.mybox123.domain.source.NativeSourceRegistryService
import com.yuivo9999.mybox123.domain.source.NativeLiveCacheStore
import com.yuivo9999.mybox123.domain.source.NativeLiveDescriptorService
import com.yuivo9999.mybox123.integration.tvbox.NativeTvBoxRuntime
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Application-scoped composition root. Opening SQLite and importing the legacy preference snapshot
 * are kept off the main thread so startup cannot be blocked by user-data size or storage latency.
 */
class MyBoxApplication : Application() {
    private val serviceExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "MyBoxNativeServices").apply { isDaemon = true }
    }
    private val servicesReady = CompletableFuture<NativeAppServices>()
    @Volatile private var tvBoxRuntimeInstance: NativeTvBoxRuntime? = null

    override fun onCreate() {
        super.onCreate()
        serviceExecutor.execute {
            try {
                val database = NativeUserDataDatabase(applicationContext)
                val userDataRepository = NativeUserDataRepository(database)
                val migrationResult = LegacyUserDataMigrator(applicationContext, database).migrate()
                if (!migrationResult.completed) {
                    Log.e(TAG, "Legacy user data migration is blocked: ${migrationResult.errorCode}")
                } else if (migrationResult.invalidKnownKeys.isNotEmpty()) {
                    Log.w(TAG, "Some legacy values were backed up but have invalid JSON shapes: ${migrationResult.invalidKnownKeys}")
                }
                val sourceRepository = NativeContentSourceRepository(userDataRepository)
                val httpClient = NativeHttpClient()
                val liveCacheStore = NativeLiveCacheStore(userDataRepository)
                val sourceRegistryService = NativeSourceRegistryService(userDataRepository, liveCacheStore)
                sourceRegistryService.sync(sourceRepository.getAll())
                servicesReady.complete(
                    NativeAppServices(
                        userDataRepository = userDataRepository,
                        contentSourceRepository = sourceRepository,
                        httpClient = httpClient,
                        sourceRegistryService = sourceRegistryService,
                        liveCacheStore = liveCacheStore,
                        liveDescriptorService = NativeLiveDescriptorService(
                            httpClient,
                            liveCacheStore,
                            currentSourceLookup = { sourceId -> sourceRepository.getAll().firstOrNull { it.sourceId == sourceId } },
                            sourceRegistryService = sourceRegistryService,
                        ),
                        sourceManagementService = NativeSourceManagementService(sourceRepository, httpClient, userDataRepository, sourceRegistryService),
                        movieSourceService = NativeMovieSourceService(sourceRepository, httpClient),
                        legacyMigrationResult = migrationResult,
                    ),
                )
            } catch (error: Exception) {
                Log.e(TAG, "Native service initialization failed", error)
                servicesReady.completeExceptionally(error)
            }
        }
    }

    /** Await initialization from a coroutine without blocking the caller's thread. */
    suspend fun awaitServices(): NativeAppServices = suspendCancellableCoroutine { continuation ->
        servicesReady.whenComplete { services, failure ->
            if (!continuation.isActive) return@whenComplete
            if (failure != null) {
                continuation.resumeWithException(failure.cause ?: failure)
            } else if (services != null) {
                continuation.resume(services)
            } else {
                continuation.resumeWithException(IllegalStateException("NATIVE_SERVICES_UNAVAILABLE"))
            }
        }
    }

    @Synchronized
    fun tvBoxRuntime(): NativeTvBoxRuntime =
        tvBoxRuntimeInstance ?: NativeTvBoxRuntime(applicationContext).also { tvBoxRuntimeInstance = it }

    override fun onTerminate() {
        synchronized(this) {
            tvBoxRuntimeInstance?.close()
            tvBoxRuntimeInstance = null
        }
        serviceExecutor.shutdown()
        super.onTerminate()
    }

    companion object {
        private const val TAG = "MyBoxApplication"
    }
}

/** Native services become available together only after the migration snapshot has been processed. */
data class NativeAppServices(
    val userDataRepository: NativeUserDataRepository,
    val contentSourceRepository: NativeContentSourceRepository,
    val httpClient: NativeHttpClient,
    val sourceRegistryService: NativeSourceRegistryService,
    val liveCacheStore: NativeLiveCacheStore,
    val liveDescriptorService: NativeLiveDescriptorService,
    val sourceManagementService: NativeSourceManagementService,
    val movieSourceService: NativeMovieSourceService,
    val legacyMigrationResult: LegacyMigrationResult,
)
