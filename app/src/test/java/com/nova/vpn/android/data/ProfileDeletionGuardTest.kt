package com.nova.vpn.android.data
import com.nova.vpn.android.AppliedConfiguration
import com.nova.vpn.android.RoutePolicyIdentity
import org.junit.Assert.*
import org.junit.Test

class ProfileDeletionGuardTest {
    private val settings=AppRoutingSettings(multiServer=MultiServerSettings(1,"fr",listOf(
        MultiServerRule(packageName="example.chat",target=ServerRouteTarget(ServerRouteKind.PROFILE,"us")))))
    private val routes=RoutingPresets("active",listOf(RoutingPreset("active","Active",settings)))
    private fun refused(id:String,document:RoutingPresets=routes,applied:AppliedConfiguration?=null) {
        assertThrows(ProfileDeletionGuard.InUse::class.java) {ProfileDeletionGuard.requireAllowed(id,document,applied)}
    }
    @Test fun defaultAppAndInactivePresetReferencesCannotBeRemoved() {
        refused("fr");refused("us")
        val inactive=routes.copy(activeId="ordinary",presets=routes.presets+RoutingPreset("ordinary","Ordinary",AppRoutingSettings()))
        refused("us",inactive)
        ProfileDeletionGuard.requireAllowed("unrelated",inactive,null)
    }
    @Test fun pendingNewSettingsAndSelectionAlsoRetainProfiles() {
        val ordinary=RoutingPresets("active",listOf(RoutingPreset("active","Ordinary",AppRoutingSettings())))
        refused("us",ordinary.stage("active","request","session",settings,RoutingSelectionChange("selected-pending")))
        refused("selected-pending",ordinary.stage("active","request","session",settings,RoutingSelectionChange("selected-pending")))
    }
    @Test fun disabledModeHistoryRemainsProtectedUntilExplicitlyForgotten() {
        val enabled=RoutingModeTransitions.enable(AppRoutingSettings(),"single","fr",emptyList(),false)
        val disabled=RoutingModeTransitions.disable(enabled).settings
        val document=RoutingPresets("active",listOf(RoutingPreset("active","Disabled",disabled)))
        refused("fr",document)
        val forgotten=document.withoutServerAssignments()
        ProfileDeletionGuard.requireAllowed("fr",forgotten,null)
    }
    @Test fun oldAppliedMultiSnapshotProtectsLiveExitEvenAfterSavedRoutesChange() {
        val ordinary=RoutingPresets("active",listOf(RoutingPreset("active","Ordinary",AppRoutingSettings())))
        val applied=AppliedConfiguration("old","fr","active",RoutePolicyIdentity.capture(settings))
        refused("us",ordinary,applied)
        ProfileDeletionGuard.requireAllowed("us",ordinary,null)
        // Existing ordinary selected-profile deletion flow is unchanged.
        ProfileDeletionGuard.requireAllowed("fr",ordinary,AppliedConfiguration("legacy","fr","active"))
    }
}
