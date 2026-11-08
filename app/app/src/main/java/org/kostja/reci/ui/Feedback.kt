package org.kostja.reci.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import org.kostja.reci.R
import org.kostja.reci.data.Crash
import org.kostja.reci.data.Env
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.kostja.reci.data.Telemetry
import org.kostja.reci.data.Word

// Sends the ping and the waiting messages when it is time; cheap to call on every start.
fun sendStats() {
    Telemetry.sendPing({ Repo.allProgress().values.count { it.state == State.KNOWN } })
    Telemetry.flush()
}

// A phone without a browser has nothing to open a link with.
fun openLink(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Нет приложения, чтобы открыть ссылку", Toast.LENGTH_LONG).show()
    }
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
fun ReportFlag(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_flag), "Сообщить об ошибке", tint = LocalPalette.current.faint, modifier = Modifier.size(22.dp))
    }
}

private val WORD_PROBLEMS = listOf("translation" to "Перевод", "stress" to "Ударение", "example" to "Пример", "forms" to "Род или формы", "other" to "Другое")
private val ARTICLE_PROBLEMS = listOf("text" to "Ошибка в тексте", "other" to "Другое")

@Composable
private fun sentToast(): (Boolean) -> Unit {
    val ctx = LocalContext.current
    return { sent ->
        Toast.makeText(ctx, if (sent) "Спасибо! Сообщение отправлено" else "Сообщение уйдёт, когда появится интернет", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        placeholder = { Text("Почта для ответа, если нужен ответ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth().testTag("email"),
    )
}

@Composable
fun ReportDialog(w: Word, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val toast = sentToast()
    var problem by remember { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(Telemetry.email) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Что не так с карточкой?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Prefs.primary(w.cyr), color = p.secondary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((k, t) in if (Repo.isArticle(w)) ARTICLE_PROBLEMS else WORD_PROBLEMS) {
                        SelectChip(t, problem == k) { problem = k }
                    }
                }
                OutlinedTextField(
                    text, { if (it.length <= 2000) text = it },
                    placeholder = { Text("Что именно не так, если хотите") },
                    minLines = 4,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth().testTag("comment"),
                )
                EmailField(email) { email = it }
                if (error.isNotEmpty()) Text(error, color = MarkDontKnow, fontSize = 14.sp)
            }
        },
        confirmButton = {
            TextButton(enabled = problem != null, onClick = {
                val mail = email.trim()
                if (mail.isNotEmpty() && !Telemetry.isEmail(mail)) {
                    error = "Проверьте адрес почты"
                    return@TextButton
                }
                val card = JSONObject()
                    .put("key", w.key.take(120)).put("word", w.cyr.take(120)).put("problem", problem)
                    .put("latin", Prefs.latinFirst).put("stress", Prefs.accentMode)
                Telemetry.sendFeedback("card", text.trim(), card, mail, Env.of(ctx), toast)
                onDismiss()
            }) { Text("Отправить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun WriteDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val toast = sentToast()
    var text by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(Telemetry.email) }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Написать разработчику") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Идея, ошибка, вопрос — что угодно.", color = LocalPalette.current.secondary)
                OutlinedTextField(
                    text, { if (it.length <= 4000) text = it },
                    placeholder = { Text("Сообщение") },
                    minLines = 6,
                    maxLines = 12,
                    modifier = Modifier.fillMaxWidth().testTag("message"),
                )
                EmailField(email) { email = it }
                if (error.isNotEmpty()) Text(error, color = MarkDontKnow, fontSize = 14.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val mail = email.trim()
                error = when {
                    text.isBlank() -> "Напишите сообщение"
                    mail.isNotEmpty() && !Telemetry.isEmail(mail) -> "Проверьте адрес почты"
                    else -> ""
                }
                if (error.isNotEmpty()) return@TextButton
                Telemetry.sendFeedback("app", text.trim(), null, mail, Env.of(ctx), toast)
                onDismiss()
            }) { Text("Отправить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

// Offers the report of the last crash once, at the next start of the app.
@Composable
fun CrashDialog() {
    val ctx = LocalContext.current
    val toast = sentToast()
    var report by remember { mutableStateOf(Crash.pending) }
    val r = report ?: return
    val close = {
        Crash.clear()
        report = null
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Приложение закрылось с ошибкой") },
        text = {
            Text(
                "В прошлый раз приложение неожиданно закрылось. Отправить разработчику отчёт, чтобы он мог " +
                    "это исправить? В отчёте место в коде, где случилась ошибка, модель телефона, версия Android " +
                    "и настройки приложения. Слова и прогресс не отправляются."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                Telemetry.sendFeedback("crash", r, null, "", Env.of(ctx), toast)
                close()
            }) { Text("Отправить") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Не отправлять") } },
    )
}

@Composable
fun WhatSentDialog(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val last = Telemetry.last
    var forgetting by remember { mutableStateOf(false) }
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
                // Quoted when asking about a message or the deletion of the data.
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Номер этой установки, для статистики", fontSize = 13.sp, color = p.secondary)
                        Text(Telemetry.id, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = p.text,
                             modifier = Modifier.testTag("installId"))
                        Text("Номер для сообщений разработчику", fontSize = 13.sp, color = p.secondary,
                             modifier = Modifier.padding(top = 6.dp))
                        Text(Telemetry.feedbackId, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = p.text,
                             modifier = Modifier.testTag("feedbackId"))
                    }
                }
                Text(
                    "Политика конфиденциальности",
                    color = p.accent,
                    modifier = Modifier.clickable { openLink(ctx, Telemetry.PRIVACY) },
                )
                Text(
                    "Удалить мои данные с сервера",
                    fontSize = 13.sp,
                    color = p.secondary,
                    modifier = Modifier.clickable { forgetting = true },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
    if (forgetting) ForgetDialog { forgetting = false }
}

@Composable
private fun ForgetDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить данные с сервера?") },
        text = {
            Text(
                "С сервера разработчика удалятся статистика и сообщения, отправленные с этого телефона. Прогресс и " +
                    "настройки на телефоне останутся. Приложение получит новые номера и дальше будет отправлять " +
                    "статистику уже с ними, если она включена."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                Telemetry.forget { ok ->
                    Toast.makeText(
                        ctx,
                        if (ok) "Данные удалены с сервера" else "Не удалось связаться с сервером. Попробуйте позже",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                onDismiss()
            }) { Text("Удалить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private const val PLAY = "https://play.google.com/store/apps/details?id=org.kostja.reci"
private const val GITHUB = "https://github.com/finist0/reci"
const val SHARE_TEXT = "НЕРодная речь — сербские слова для тех, кто говорит по-русски: карточки с ударениями, " +
    "примерами и грамматикой. Бесплатно и без рекламы.\nAndroid: $PLAY\niPhone и компьютер: https://finist0.github.io/reci/web/"

@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    @Composable
    fun HelpRow(title: String, summary: String, onClick: () -> Unit) {
        Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
            Text(title, fontSize = 16.sp, color = p.text)
            Text(summary, fontSize = 13.sp, color = p.secondary)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Помочь автору") },
        text = {
            Column {
                Text("Приложение бесплатное и без рекламы. Вот чем можно помочь:", color = p.secondary)
                HelpRow("Поделиться с другом", "Ссылки на Android, iPhone и компьютер") {
                    Telemetry.count("opened", "share")
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, SHARE_TEXT)
                    ctx.startActivity(Intent.createChooser(send, "Поделиться"))
                }
                HelpRow("Оценить приложение", "В Google Play") {
                    Telemetry.count("opened", "rate")
                    // The Play Store app where it is installed, the web page elsewhere.
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.kostja.reci")))
                    } catch (e: ActivityNotFoundException) {
                        openLink(ctx, PLAY)
                    }
                }
                HelpRow("Поставить звезду на GitHub", "Там лежит код приложения") {
                    Telemetry.count("opened", "star")
                    openLink(ctx, GITHUB)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
