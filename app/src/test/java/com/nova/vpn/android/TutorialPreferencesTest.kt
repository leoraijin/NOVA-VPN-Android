package com.nova.vpn.android

import com.nova.vpn.android.data.AppPreferencesStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TutorialPreferencesTest {
    @Test fun updateCooldownSurvivesStoreRecreationWithoutChangingPreferences() {
        val context = RuntimeEnvironment.getApplication()
        val store = AppPreferencesStore(context)
        assertTrue(store.automaticUpdateCheckDue(1000))
        store.markUpdateCheck(1000)
        val reopened = AppPreferencesStore(context)
        assertFalse(reopened.automaticUpdateCheckDue(1001))
        assertTrue(reopened.automaticUpdateCheckDue(1000 + 6 * 60 * 60 * 1000L))
        assertTrue(reopened.updateChecksEnabled())
        assertFalse(reopened.autoConnectEnabled())
    }

    @Test fun freshInstallationOffersTutorialUntilExplicitlyDismissed() {
        val context = RuntimeEnvironment.getApplication()
        assertFalse(AppPreferencesStore(context).tutorialDismissed())
        assertFalse(AppPreferencesStore(context).tutorialDismissed())
        AppPreferencesStore(context).dismissTutorial()
        assertTrue(AppPreferencesStore(context).tutorialDismissed())
    }

    @Test fun dismissingTutorialDoesNotChangeExistingUserPreferences() {
        val context = RuntimeEnvironment.getApplication()
        val store = AppPreferencesStore(context)
        store.setAutoConnectEnabled(true)
        store.setSelectedDesign("WINDOWS_DARK")
        store.setUpdateChecksEnabled(false)
        store.dismissTutorial()
        store.dismissTutorial()
        val reopened = AppPreferencesStore(context)
        assertTrue(reopened.tutorialDismissed())
        assertTrue(reopened.autoConnectEnabled())
        assertFalse(reopened.updateChecksEnabled())
        assertEquals("WINDOWS_DARK", reopened.selectedDesign())
    }

    @Test fun completedOrInvalidSavedStepDoesNotOpenAnotherTutorialPage() {
        assertNull(TutorialStep.at(-1))
        assertNull(TutorialStep.at(TutorialStep.entries.size))
        assertNull(TutorialStep.at(Int.MAX_VALUE))
        assertEquals(TutorialStep.Connect, TutorialStep.at(5))
    }
}
