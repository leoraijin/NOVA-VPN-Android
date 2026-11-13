package com.nova.vpn.android

import androidx.core.content.FileProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import android.content.pm.PackageManager
import org.xmlpull.v1.XmlPullParser
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class RoutingShareProviderTest {
    @Test fun manifestGrantsOnlyTheDedicatedCacheDirectory() {
        val context=RuntimeEnvironment.getApplication()
        val provider=context.packageManager.resolveContentProvider("${context.packageName}.routing-shares",PackageManager.GET_META_DATA)!!
        assertFalse(provider.exported);assertTrue(provider.grantUriPermissions)
        val parser=provider.loadXmlMetaData(context.packageManager,"android.support.FILE_PROVIDER_PATHS")!!
        val paths=mutableListOf<Pair<String,String>>()
        parser.use {
            while(it.next()!=XmlPullParser.END_DOCUMENT) {
                if(it.eventType==XmlPullParser.START_TAG && it.name!="paths") paths+=it.name to it.getAttributeValue(null,"path")
            }
        }
        assertEquals(listOf("cache-path" to "routing-shares/"),paths)
    }
    @Test fun onlyDedicatedShareDirectoryIsExposed() {
        // AndroidX FileProvider compares canonical paths with '/' separators.
        // Host Windows paths cannot exercise that Android-only path traversal check.
        assumeTrue("FileProvider round-trip needs Unix host paths or a device",File.separatorChar=='/')
        val context=RuntimeEnvironment.getApplication()
        val authority="${context.packageName}.routing-shares"
        val folder=File(context.cacheDir,"routing-shares").apply { mkdirs() }
        val packet=File(folder,"fixture.nova").apply { writeText("ENCRYPTED_FIXTURE") }
        try {
            val uri=FileProvider.getUriForFile(context,authority,packet)
            assertEquals("content",uri.scheme)
            assertEquals("ENCRYPTED_FIXTURE",context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() })
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context,authority,File(context.filesDir,"profiles.vault"))
            }
            assertThrows(IllegalArgumentException::class.java) {
                FileProvider.getUriForFile(context,authority,File(context.cacheDir,"unrelated-cache"))
            }
        } finally { packet.delete() }
    }
}
