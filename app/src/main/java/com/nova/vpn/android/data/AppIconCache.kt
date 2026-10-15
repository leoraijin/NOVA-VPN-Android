package com.nova.vpn.android.data

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Visible rows only; bounded process cache survives lazy-row disposal. Never retains Context. */
internal object AppIconCache {
    private val icons = LruCache<String, ImageBitmap>(64)
    private val loading = Mutex()
    fun clear() = icons.evictAll()
    fun trim() = icons.trimToSize(16)
    fun peek(packageName: String): ImageBitmap? = icons.get(packageName)
    suspend fun load(manager: PackageManager, packageName: String): ImageBitmap? =
        withContext(Dispatchers.IO) {
            loading.withLock {
                ensureActive()
                icons.get(packageName) ?: runCatching {
                    val drawable = manager.getApplicationIcon(packageName)
                    val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                    drawable.setBounds(0, 0, 96, 96)
                    drawable.draw(Canvas(bitmap))
                    bitmap.asImageBitmap().also { icons.put(packageName, it) }
                }.getOrNull()
            }
        }
}
