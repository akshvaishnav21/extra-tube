package com.extratube

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class — entry point for Hilt dependency injection.
 *
 * @HiltAndroidApp triggers Hilt's code generation and sets up the application-level
 * dependency container. Must be registered in AndroidManifest.xml via android:name.
 *
 * NewPipe.init() is called via the Hilt AppModule's initNewPipe() provider,
 * which runs before any extractor call thanks to the @Singleton sentinel pattern.
 */
@HiltAndroidApp
class ExtraTubeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupStrictMode()
    }

    /**
     * StrictMode catches IO-on-main-thread violations in debug builds.
     * penaltyDeath() crashes immediately on violation — do not hide violations.
     * This ensures zero IO-on-main violations ship in production.
     */
    private fun setupStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyLog()
                    .penaltyDeath()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
