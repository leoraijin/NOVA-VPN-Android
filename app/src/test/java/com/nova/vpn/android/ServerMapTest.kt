package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ServerMapTest {
    private fun data() = ServerMapData.load(RuntimeEnvironment.getApplication())

    @Test fun bundledWorldContainsActualCountryOutlinesAndCities() {
        val data = data()
        assertTrue(data.countries.size >= 170)
        for(code in listOf("NL", "DE", "US", "RU", "FI", "PL")) {
            val country = data.countries.first { it.code == code }
            assertTrue(country.rings.isNotEmpty())
            assertTrue(country.rings.all { it.size >= 4 })
        }
        val location = data.resolve("🇳🇱 Netherlands · Amsterdam #1")!!
        assertEquals("Амстердам", location.city)
        assertEquals(MapPoint.fromGeo(4.914694, 52.351915).x, location.point.x, .01)
    }

    @Test fun flagsAndRussianNamesResolveWithoutUsingServerHost() {
        assertEquals("Хельсинки", data().resolve("🇫🇮 Finland Helsinki")?.city)
        assertEquals("Варшава", data().resolve("Польша · Варшава")?.city)
        assertEquals("Вена", data().resolve("🇦🇹 Austria - Vienna")?.city)
        assertNotNull(data().resolve("🇺🇸 USA #2"))
        assertNull(data().resolve("Мой профиль 123"))
        assertNull(data().resolve("profile-nl1.example.com"))
    }

    @Test fun countryOnlyDoesNotInventCityAndConflictingCityCannotMoveFlag() {
        val country = data().resolve("🇩🇪 Germany")!!
        assertNull(country.city)
        val conflict = data().resolve("🇩🇪 Amsterdam")!!
        assertEquals("Германия", conflict.country)
        assertNull(conflict.city)
    }

    @Test fun cameraZoomPreservesGestureFocusAndPanMovesMapUnderFinger() {
        val camera = ServerMapCamera(.54, .34, 3f)
        val zoomed = camera.transform(0f,0f,2f,230f,150f,400f,400f)
        val beforeX = camera.x + (230 - 200) / (400 * camera.zoom)
        val afterX = zoomed.x + (230 - 200) / (400 * zoomed.zoom)
        assertEquals(beforeX, afterX, .000001)
        assertTrue(camera.transform(30f,0f,1f,200f,200f,400f,400f).x < camera.x)
        assertEquals(24f, camera.transform(0f,0f,100f,200f,200f,400f,400f).zoom)
        assertEquals(1f, camera.transform(0f,0f,.001f,200f,200f,400f,400f).zoom)
    }
}
