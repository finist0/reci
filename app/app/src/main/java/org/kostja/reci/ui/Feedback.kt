package org.kostja.reci.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.kostja.reci.data.Telemetry
import org.kostja.reci.data.Word

// Sends the ping when it is time; cheap to call on every start.
fun sendStats() {
    Telemetry.sendPing({ Repo.allProgress().values.count { it.state == State.KNOWN } })
}

fun countAnswer(w: Word, a: State, learnedNow: Boolean) {
    val what = when {
        Repo.isArticle(w) -> if (a == State.KNOWN) "hide" else "later"
        learnedNow -> "long"
        a == State.KNOWN -> "know"
        else -> "dontknow"
    }
    Telemetry.count("answers", what)
}

@Composable
fun StatsNotice() {
    val p = LocalPalette.current
    LaunchedEffect(Unit) {
        Telemetry.markNoticeShown()
        sendStats()
    }
    Row(
        Modifier
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(p.highlight)
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp)
            .testTag("statsNotice"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Приложение раз в день отправляет анонимную статистику: сколько карточек показано, какие " +
                "разделы и настройки выбраны. Отключить: Настройки → «Анонимная статистика».",
            fontSize = 13.sp,
            color = p.text,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.size(40.dp).clip(CircleShape).clickable { Telemetry.closeNotice() },
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", fontSize = 15.sp, color = p.secondary)
        }
    }
}

@Composable
fun WhatSentDialog(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val last = Telemetry.last
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Анонимная статистика") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Раз в день уходит: случайный номер этой установки, платформа, версии приложения и системы, " +
                        "язык, сколько карточек показано и ответов дано, сколько слов выучено, выбранные разделы " +
                        "и настройки, включена ли карточка на экране блокировки, какие экраны открывали.\n\n" +
                        "Не уходит: слова и ответы по отдельным словам, имя, почта, точное местоположение. Адрес IP сервер не сохраняет.",
                    color = p.secondary,
                )
                Text(if (last != null) "Последний пакет, ${last.optString("day")}" else "Пока ничего не отправлялось", color = p.text)
                if (last != null) {
                    Text(
                        last.toString(1),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(p.panel).padding(10.dp).testTag("packet"),
                    )
                }
                Text(
                    "Политика конфиденциальности",
                    color = p.accent,
                    modifier = Modifier.clickable {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Telemetry.PRIVACY)))
                    },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
