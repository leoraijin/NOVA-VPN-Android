package com.nova.vpn.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nova.vpn.android.data.ImportedProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ExitHealthCard(checks: Map<String,ExitCheckState>,profiles: List<ImportedProfile>) {
    val byId=remember(profiles){profiles.associateBy { it.id }}
    val wanted=checks.keys
    val hashes by produceState<Map<String,String>>(emptyMap(),profiles,wanted) {
        value=withContext(Dispatchers.Default){profiles.filter { it.id in wanted }.associate { it.id to RouteExitBindings.profileHash(it) }}
    }
    SoftCard {
        Text("Выходы действующего подключения",style=MaterialTheme.typography.titleMedium)
        Text("HTTPS-проверка каждого активного конфига повторяется раз в минуту. Она не переключает серверы.",style=MaterialTheme.typography.bodySmall)
        checks.toSortedMap().forEach { (id,check)->
            val profile=byId[id]
            Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(profile?.displayName?:"Недоступный сервер · ${id.takeLast(6)}")
                Text(when(check.status) {
                    ExitCheckStatus.QUEUED->"Ожидает проверки"
                    ExitCheckStatus.CHECKING->"Проверяется"
                    ExitCheckStatus.VERIFIED->"HTTPS подтверждён"+(check.responseMillis?.let { " · $it мс" }?:"")
                    ExitCheckStatus.FAILED->"HTTPS не подтверждён; другие выходы проверяются отдельно"
                    ExitCheckStatus.UNAVAILABLE->"Нет доступного конфига — назначения на него блокируются"
                    ExitCheckStatus.INCOMPATIBLE->"Конфиг несовместим — назначения на него заблокированы"
                    ExitCheckStatus.UNSUPPORTED->"Проверка этого типа не поддерживается"
                },style=MaterialTheme.typography.bodySmall)
                if(check.profileSha256!=null&&profile?.retiredFromSubscription==true)
                    Text("Сервер удалён из подписки; результат относится к активному снимку.",style=MaterialTheme.typography.bodySmall)
                else if(check.profileSha256!=null&&hashes[id]!=null&&hashes[id]!=check.profileSha256)
                    Text("Настройки профиля изменены; результат относится к активному снимку.",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
