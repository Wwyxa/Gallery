package org.fossify.gallery

import com.github.ajalt.reprint.core.Reprint
import com.squareup.picasso.Downloader
import com.squareup.picasso.Picasso
import okhttp3.Request
import okhttp3.Response
import org.fossify.commons.FossifyApp
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.WAS_INTERNAL_GLIDE_CACHE_CLEARED
import java.io.File

class App : FossifyApp() {

    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        Reprint.initialize(this)
        Picasso.setSingletonInstance(Picasso.Builder(this).downloader(object : Downloader {
            override fun load(request: Request) = Response.Builder().build()

            override fun shutdown() {}
        }).build())
        cleanupLegacyGlideCache()
    }

    // the thumbnail cache moved to the external storage, drop the one older versions left in the internal storage
    private fun cleanupLegacyGlideCache() {
        ensureBackgroundThread {
            if (!config.wasInternalGlideCacheCleared) {
                File(cacheDir, "image_manager_disk_cache").deleteRecursively()
                config.wasInternalGlideCacheCleared = true
            }
        }
    }
}
