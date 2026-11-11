package org.kostja.reci.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.kostja.reci.data.WhatsNew

@Composable
fun WhatsNewDialog() {
    val p = LocalPalette.current
    val entries = WhatsNew.pending.value
    if (entries.isEmpty()) return
    val one = entries.size == 1
    AlertDialog(
        onDismissRequest = WhatsNew::close,
        modifier = Modifier.testTag("whatsNew"),
        title = { Text(if (one) "Что нового в версии ${entries[0].version}" else "Что нового") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (e in entries) {
                    if (!one) Text("Версия ${e.version}", color = p.text, fontWeight = FontWeight.SemiBold)
                    for (item in e.items) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", color = p.accent)
                            Text(item, color = p.text)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = WhatsNew::close) { Text("Понятно") } },
    )
}
