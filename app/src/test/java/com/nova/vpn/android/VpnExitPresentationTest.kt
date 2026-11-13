package com.nova.vpn.android
import org.junit.Assert.*
import org.junit.Test
class VpnExitPresentationTest {
    private fun state() = VpnUiState(connected=true, sessionRevision=3,
        appliedConfiguration=AppliedConfiguration("request", "fr"),
        exitNames=mapOf("fr" to "France", "us" to "USA"),
        exitChecks=linkedMapOf("us" to ExitCheckState(ExitCheckStatus.FAILED, "hash"),
            "fr" to ExitCheckState(ExitCheckStatus.VERIFIED, "hash", 42)))
    @Test fun rolesUseActivePrimaryNotMapOrder() {
        val lines=state().exitLines("other")
        assertEquals("Основной VPN · France", lines[0].title)
        assertTrue(lines[0].status.startsWith("Работает"))
        assertEquals("VPN-2 · USA", lines[1].title)
        assertTrue(lines[1].failed)
    }
    @Test fun recoveryNeverShowsOldSuccess() {
        assertTrue(state().copy(recoveryStatus="waiting_network").exitLines("x").all { it.status=="Ожидаем сеть" })
    }
    @Test fun independentPeriodicCheckCanRecover() {
        val state=state()
        val checking=ExitHealthReducer.checking(state,3,"us","hash")
        assertEquals(ExitCheckStatus.CHECKING,checking.exitChecks["us"]?.status)
        assertEquals(ExitCheckStatus.VERIFIED,checking.exitChecks["fr"]?.status)
        val recovered=ExitHealthReducer.result(checking,3,"us","hash",true,5)
        assertTrue(recovered.trafficVerified)
        assertEquals(checking,ExitHealthReducer.result(checking,2,"us","hash",true,5))
    }
    @Test fun untestedTunnelIsNotReportedWorking() {
        assertEquals("Трафик ещё не проверен",VpnUiState(connected=true).exitLines("test").single().status)
    }
}
