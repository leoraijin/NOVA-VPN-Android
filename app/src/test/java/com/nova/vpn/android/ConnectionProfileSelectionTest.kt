package com.nova.vpn.android

import org.junit.Assert.*
import org.junit.Test

class ConnectionProfileSelectionTest {
    @Test fun idleMultiShowsDefaultInsteadOfLegacySelection() {
        assertEquals("fr", ConnectionProfileSelection.id(VpnUiState(), "us", "fr"))
    }
    @Test fun idleLegacyRetainsManualSelection() {
        assertEquals("us", ConnectionProfileSelection.id(VpnUiState(), "us", null))
        assertNull(ConnectionProfileSelection.id(VpnUiState(), "", null))
    }
    @Test fun connectedShowsAcknowledgedProfileEvenWhileAnotherDraftIsSelected() {
        val state=VpnUiState(connected=true, appliedConfiguration=AppliedConfiguration("old", "fr"))
        assertEquals("fr", ConnectionProfileSelection.id(state, "us", "jp"))
    }
    @Test fun recoveryKeepsActiveIdentityAndDoesNotBorrowDraft() {
        val state=VpnUiState(connecting=true, recoveryStatus="recovering",
            appliedConfiguration=AppliedConfiguration("old", "fr"))
        assertEquals("fr", ConnectionProfileSelection.id(state, "us", "jp"))
        assertNull(ConnectionProfileSelection.id(state.copy(appliedConfiguration=null), "us", "jp"))
    }
    @Test fun unacknowledgedRunningTunnelHasNoInventedName() {
        assertNull(ConnectionProfileSelection.id(VpnUiState(connected=true), "us", "fr"))
        assertNull(ConnectionProfileSelection.id(VpnUiState(connected=true,
            appliedConfiguration=AppliedConfiguration("old")), "us", "fr"))
    }
    @Test fun initialConnectionUsesDesiredDefaultUntilAcknowledged() {
        assertEquals("fr", ConnectionProfileSelection.id(VpnUiState(connecting=true), "us", "fr"))
        assertEquals("us", ConnectionProfileSelection.id(VpnUiState(connecting=true), "us", null))
    }
    @Test fun missingActiveRecordDoesNotTellUserToAddAnotherServer() {
        val running=VpnUiState(connected=true)
        assertEquals("Активный сервер отсутствует в списке", ConnectionProfileSelection.name(running, null, "Добавить сервер"))
        assertEquals("Добавить сервер", ConnectionProfileSelection.name(VpnUiState(), null, "Добавить сервер"))
    }
    @Test fun presentationUsesPersonalNameWithoutChangingProfile() {
        val profile=com.nova.vpn.android.data.ImportedProfile(id="fr",name="Provider FR",protocol="socks",
            server="127.0.0.1",port=1,outboundJson="{}",
            userSettings=com.nova.vpn.android.data.ProfileUserSettings(personalName="France"))
        assertEquals("France", ConnectionProfileSelection.name(VpnUiState(connected=true), profile, "Add"))
        assertEquals("Provider FR", profile.name)
    }
}
