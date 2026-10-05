package org.fossify.gallery.svg

import android.content.Context
import android.graphics.drawable.PictureDrawable
import android.os.StatFs

import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory
import com.bumptech.glide.module.AppGlideModule
import com.caverock.androidsvg.SVG

import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.GLIDE_CACHE_MAX_FREE_SPACE_RATIO
import org.fossify.gallery.helpers.MB_IN_BYTES
import java.io.InputStream

@GlideModule
class SvgModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.register(SVG::class.java, PictureDrawable::class.java, SvgDrawableTranscoder()).append(InputStream::class.java, SVG::class.java, SvgDecoder())
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        val requestedSize = context.config.glideDiskCacheSizeMB * MB_IN_BYTES
        builder.setDiskCache(
            ExternalPreferredCacheDiskCacheFactory(context, requestedSize.coerceAtMost(getFreeSpaceQuota(context)))
        )
    }

    // keep the cache from filling up the storage, cap it at a quarter of the free space on the volume hosting it
    private fun getFreeSpaceQuota(context: Context): Long {
        return try {
            val dir = context.getExternalCacheDir() ?: context.cacheDir
            (StatFs(dir.absolutePath).availableBytes * GLIDE_CACHE_MAX_FREE_SPACE_RATIO).toLong()
        } catch (ignored: Exception) {
            Long.MAX_VALUE
        }
    }

    override fun isManifestParsingEnabled() = false
}
