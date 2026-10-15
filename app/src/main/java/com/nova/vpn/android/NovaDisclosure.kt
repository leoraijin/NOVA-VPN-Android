package com.nova.vpn.android

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.nova.vpn.android.data.ImportedProfile

@Composable
internal fun NovaDisclosure(expanded: Boolean) {
    val angle by animateFloatAsState(if (expanded) 90f else 0f, label = "disclosure")
    Icon(Icons.Rounded.ChevronRight, if (expanded) "Свернуть" else "Развернуть",
        Modifier.size(20.dp).rotate(angle), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun ProfileActionsMenu(profile: ImportedProfile, busy: Boolean,
    onWatch: (ImportedProfile) -> Unit, onDelete: (ImportedProfile) -> Unit) {
    var open by remember(profile.id) { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, "Действия с профилем ${profile.name}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(profile.server) }, onClick = {}, enabled = false)
            DropdownMenuItem(text = { Text(if (busy) "Передача…" else "Передать на часы") },
                leadingIcon = { Icon(Icons.Rounded.Watch, null) }, enabled = !busy,
                onClick = { open = false; onWatch(profile) })
            DropdownMenuItem(text = { Text("Удалить профиль", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { open = false; onDelete(profile) })
        }
    }
}
