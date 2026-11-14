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
import org.kostja.reci.data.Decks
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.kostja.reci.data.Telemetry
import org.kostja.reci.data.Word
import org.kostja.reci.data.t

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
        Toast.makeText(ctx, t("Нет приложения, чтобы открыть ссылку"), Toast.LENGTH_LONG).show()
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
            t("Приложение раз в день отправляет анонимную статистику: сколько карточек показано, какие ") +
                t("разделы и настройки выбраны. Отключить: Настройки → «Анонимная статистика»."),
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

// Opens "Куда пойти учиться?"; sits next to the flag at the bottom of a card.
@Composable
fun SchoolsButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_school), t("Куда пойти учиться"), tint = LocalPalette.current.faint, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun ReportFlag(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_flag), t("Сообщить об ошибке"), tint = LocalPalette.current.faint, modifier = Modifier.size(22.dp))
    }
}

private val WORD_PROBLEMS get() = listOf("translation" to t("Перевод"), "stress" to t("Ударение"), "example" to t("Пример"), "forms" to t("Род или формы"), "other" to t("Другое"))
private val ARTICLE_PROBLEMS get() = listOf("text" to t("Ошибка в тексте"), "other" to t("Другое"))

@Composable
private fun sentToast(): (Boolean) -> Unit {
    val ctx = LocalContext.current
    return { sent ->
        Toast.makeText(ctx, if (sent) t("Спасибо! Сообщение отправлено") else t("Сообщение уйдёт, когда появится интернет"), Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        placeholder = { Text(t("Почта для ответа, если нужен ответ")) },
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
        title = { Text(t("Что не так с карточкой?")) },
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
                    placeholder = { Text(t("Что именно не так")) },
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
                error = when {
                    text.isBlank() -> t("Напишите, что не так")
                    mail.isNotEmpty() && !Telemetry.isEmail(mail) -> t("Проверьте адрес почты")
                    else -> ""
                }
                if (error.isNotEmpty()) return@TextButton
                val card = JSONObject()
                    .put("key", w.key.take(120)).put("word", w.cyr.take(120)).put("problem", problem)
                    .put("latin", Prefs.latinFirst).put("stress", Prefs.accentMode).put("deck", Decks.current.id)
                Telemetry.sendFeedback("card", text.trim(), card, mail, Env.of(ctx), toast)
                onDismiss()
            }) { Text(t("Отправить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
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
        title = { Text(t("Написать разработчику")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(t("Идея, ошибка, вопрос — что угодно."), color = LocalPalette.current.secondary)
                OutlinedTextField(
                    text, { if (it.length <= 4000) text = it },
                    placeholder = { Text(t("Сообщение")) },
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
                    text.isBlank() -> t("Напишите сообщение")
                    mail.isNotEmpty() && !Telemetry.isEmail(mail) -> t("Проверьте адрес почты")
                    else -> ""
                }
                if (error.isNotEmpty()) return@TextButton
                Telemetry.sendFeedback("app", text.trim(), null, mail, Env.of(ctx), toast)
                onDismiss()
            }) { Text(t("Отправить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
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
        title = { Text(t("Приложение закрылось с ошибкой")) },
        text = {
            Text(
                t("В прошлый раз приложение неожиданно закрылось. Отправить разработчику отчёт, чтобы он мог ") +
                    t("это исправить? В отчёте место в коде, где случилась ошибка, модель телефона, версия Android ") +
                    t("и настройки приложения. Слова и прогресс не отправляются.")
            )
        },
        confirmButton = {
            TextButton(onClick = {
                Telemetry.sendFeedback("crash", r, null, "", Env.of(ctx), toast)
                close()
            }) { Text(t("Отправить")) }
        },
        dismissButton = { TextButton(onClick = close) { Text(t("Не отправлять")) } },
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
        title = { Text(t("Анонимная статистика")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    t("Раз в день уходит: случайный номер этой установки, платформа, версии приложения и системы, ") +
                        t("язык, сколько карточек показано и ответов дано, сколько слов выучено, выбранные разделы ") +
                        t("и настройки, включена ли карточка на экране блокировки, какие экраны открывали.\n\n") +
                        t("Не уходит: слова и ответы по отдельным словам, имя, почта, точное местоположение. Адрес IP сервер не сохраняет."),
                    color = p.secondary,
                )
                Text(if (last != null) t("Последний пакет, %s", last.optString("day")) else t("Пока ничего не отправлялось"), color = p.text)
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
                        Text(t("Номер этой установки, для статистики"), fontSize = 13.sp, color = p.secondary)
                        Text(Telemetry.id, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = p.text,
                             modifier = Modifier.testTag("installId"))
                        Text(t("Номер для сообщений разработчику"), fontSize = 13.sp, color = p.secondary,
                             modifier = Modifier.padding(top = 6.dp))
                        Text(Telemetry.feedbackId, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = p.text,
                             modifier = Modifier.testTag("feedbackId"))
                    }
                }
                Text(
                    t("Политика конфиденциальности"),
                    color = p.accent,
                    modifier = Modifier.clickable { openLink(ctx, Telemetry.PRIVACY) },
                )
                Text(
                    t("Удалить мои данные с сервера"),
                    fontSize = 13.sp,
                    color = p.secondary,
                    modifier = Modifier.clickable { forgetting = true },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Закрыть")) } },
    )
    if (forgetting) ForgetDialog { forgetting = false }
}

@Composable
private fun ForgetDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Удалить данные с сервера?")) },
        text = {
            Text(
                t("С сервера разработчика удалятся статистика и сообщения, отправленные с этого телефона. Прогресс и ") +
                    t("настройки на телефоне останутся. Приложение получит новые номера и дальше будет отправлять ") +
                    t("статистику уже с ними, если она включена.")
            )
        },
        confirmButton = {
            TextButton(onClick = {
                Telemetry.forget { ok ->
                    Toast.makeText(
                        ctx,
                        if (ok) t("Данные удалены с сервера") else t("Не удалось связаться с сервером. Попробуйте позже"),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                onDismiss()
            }) { Text(t("Удалить")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Отмена")) } },
    )
}

private const val PLAY = "https://play.google.com/store/apps/details?id=org.kostja.reci"
private const val GITHUB = "https://github.com/finist0/reci"
val SHARE_TEXT: String
    get() = Decks.shareText +
        "\nAndroid: $PLAY\n" + t("iPhone и компьютер:") + " https://finist0.github.io/reci/web/"

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
        title = { Text(t("Помочь автору")) },
        text = {
            Column {
                Text(t("Приложение бесплатное. Вот чем можно помочь:"), color = p.secondary)
                HelpRow(t("Поделиться с другом"), t("Ссылки на Android, iPhone и компьютер")) {
                    Telemetry.count("opened", "share")
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, SHARE_TEXT)
                    ctx.startActivity(Intent.createChooser(send, t("Поделиться")))
                }
                HelpRow(t("Оценить приложение"), t("В Google Play")) {
                    Telemetry.count("opened", "rate")
                    // The Play Store app where it is installed, the web page elsewhere.
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.kostja.reci")))
                    } catch (e: ActivityNotFoundException) {
                        openLink(ctx, PLAY)
                    }
                }
                HelpRow(t("Поставить звезду на GitHub"), t("Там лежит код приложения")) {
                    Telemetry.count("opened", "star")
                    openLink(ctx, GITHUB)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Закрыть")) } },
    )
}
