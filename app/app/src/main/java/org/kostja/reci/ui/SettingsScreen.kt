package org.kostja.reci.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kostja.reci.R
import org.kostja.reci.data.Backup
import org.kostja.reci.data.Decks
import org.kostja.reci.data.I18n
import org.kostja.reci.data.Labels
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.Speaker
import org.kostja.reci.data.State
import org.kostja.reci.data.Telemetry
import org.kostja.reci.lock.LockActivity
import org.kostja.reci.lock.LockService
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.kostja.reci.data.t
import org.kostja.reci.data.plural

private fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
    PackageManager.PERMISSION_GRANTED

// After two refusals Android stops asking, so the row leads to the app's system page instead.
internal fun afterCallsRequest(activity: Activity, granted: Boolean) {
    if (granted || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_PHONE_STATE)) return
    activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
}

// The values of Prefs.uiLang in the order of the interface language choice.
private val uiLangs = listOf("ru", "sr", "en")

private fun ignoresBattery(ctx: Context) =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

@Composable
fun SettingsScreen(openCategories: () -> Unit, openSchools: () -> Unit, back: () -> Unit, lockGuide: Boolean = false) {
    Prefs.version.intValue
    Repo.version.intValue
    val p = LocalPalette.current
    val ctx = LocalContext.current
    // System permission state can change while the user is away in system settings.
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose {}
    }
    tick
    val overlay = Settings.canDrawOverlays(ctx)
    val notify = canNotify(ctx)
    val battery = ignoresBattery(ctx)
    val calls = LockActivity.canReadCalls(ctx)
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val callsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        tick++
        (ctx as? Activity)?.let { afterCallsRequest(it, granted) }
    }
    val scope = rememberCoroutineScope()
    val toast = { text: String -> Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = Backup.export()
                    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }.isSuccess
            }
            toast(if (ok) t("Прогресс сохранён.") else t("Не удалось сохранить файл."))
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching {
                    Backup.import(ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() })
                }.getOrNull()
            }
            toast(
                if (n == null) t("Это не файл с прогрессом.")
                else t("Загружен прогресс по %d %s.", n, plural(n, "слову", "словам", "словам"))
            )
        }
    }
    var confirmReset by remember { mutableStateOf(false) }
    var writing by remember { mutableStateOf(false) }
    var whatSent by remember { mutableStateOf(false) }
    var helping by remember { mutableStateOf(false) }
    var confirmArticles by remember { mutableStateOf<String?>(null) }
    var showPermissions by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Speaker.connect() }

    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding()) {
        Header(t("Настройки"), back)
        val scroll = rememberScrollState()
        // The lock screen section's place, for the steps of the lock screen offer. The settings
        // scroll there once, not again on the way back from a screen opened from them.
        var lockAt by remember { mutableIntStateOf(-1) }
        var guided by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(lockAt) {
            if (lockGuide && !guided && lockAt >= 0) {
                guided = true
                scroll.animateScrollTo(lockAt)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(scroll)) {
            ProgressCard()

            GroupTitle(t("Язык"))
            Panel {
                val deck = Decks.current
                MenuChoice(t("Учу"), "target", Decks.targets, deck.target, Decks::name) {
                    Repo.switchDeck(Decks.pick(it, deck.native).id)
                }
                Line()
                MenuChoice(t("Перевод на"), "native", Decks.natives(deck.target), deck.native, Decks::name) {
                    Repo.switchDeck(Decks.pick(deck.target, it).id)
                }
                Line()
                // Each language is named in itself, so that a user who cannot read the
                // interface finds their own.
                val uiNames = listOf("Русский", "Српски", "English")
                MenuChoice(t("Язык интерфейса"), "uilang", uiLangs, Prefs.uiLang, { uiNames[uiLangs.indexOf(it).coerceAtLeast(0)] }) {
                    Prefs.uiLang = it
                }
            }

            GroupTitle(t("Учёба"))
            Panel {
                val cats = Prefs.categories
                NavRow(
                    t("Что учим"),
                    (if (cats.size == 1) Labels.category(cats.first()) else t("Разделов: %d", cats.size)) + " · " + Prefs.order.label,
                    openCategories,
                )
                Line()
                Choice(t("Карточки"), listOf(t("Обучение"), t("Перевод"), t("Слово")), Prefs.cardMode) { Prefs.cardMode = it }
                Text(
                    when (Prefs.cardMode) {
                        1 -> t("Видно только перевод: вспомните слово и откройте его кнопкой или ответом.")
                        2 -> t("Видно только слово: перевод открывается по нажатию.")
                        else -> t("Слово учится по шагам: сначала показано целиком, после «Знаю» ") +
                            t("надо вспомнить его по переводу, после трёх «Знаю» подряд оно выучено. ") +
                            t("«Не знаю» вернёт слово через пять карточек. Долгое нажатие на «Знаю» — слово уже знакомо.")
                    },
                    fontSize = 13.sp,
                    color = p.secondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                )
                Line()
                Choice(t("Слов в работе одновременно"), listOf("10", "15", "20", "30"), listOf(10, 15, 20, 30).indexOf(Prefs.activeSize).coerceAtLeast(0)) {
                    Prefs.activeSize = listOf(10, 15, 20, 30)[it]
                }
                Line()
                Toggle(t("Кнопки «Не знаю / Знаю»"), null, Prefs.useMarks) { Prefs.useMarks = it }

                Line()
                Toggle(
                    t("Проверять выученные слова"),
                    t("Через неделю, потом через 3\u00A0недели, 2\u00A0месяца, полгода и год"),
                    Prefs.reviewKnown,
                ) { Prefs.reviewKnown = it }
                if (Decks.current.target == "sr") {
                    Line()
                    NavRow(t("Куда пойти учиться?"), t("Школы сербского языка"), openSchools)
                }
            }

            Column(Modifier.onGloballyPositioned { if (lockAt < 0) lockAt = it.positionInParent().y.toInt() }) {
                if (lockGuide) LockGuide(Prefs.lockEnabled && overlay)
                GroupTitle(t("Экран блокировки"))
            }
            Panel {
                Toggle(t("Карточка при включении экрана"), null, Prefs.lockEnabled, highlight = lockGuide && !Prefs.lockEnabled) {
                    Prefs.lockEnabled = it
                    if (it && !notify && Build.VERSION.SDK_INT >= 33) {
                        notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    LockService.sync(ctx)
                }
                if (Prefs.lockEnabled) {
                    Line()
                    Permission(t("Поверх других окон"), t("Без этого Android не покажет карточку"), overlay, highlight = lockGuide && !overlay) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        )
                    }
                    if (!overlay) RestrictedHint()
                    Line()
                    Permission(t("Уведомления"), t("Для слов на повторение в шторке"), notify) {
                        if (Build.VERSION.SDK_INT >= 33) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    Line()
                    Permission(t("Без ограничений батареи"), t("Нужно, только если карточки пропадают"), battery) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        )
                    }
                    if (!battery) BatteryHint()
                    if (Prefs.skipDuringCalls) {
                        Line()
                        Permission(t("Звонки"), t("Надёжнее понять, идёт ли звонок"), calls) {
                            callsLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                        }
                        if (!calls) CallsHint()
                    }
                }
                Line()
                Pause()
                Line()
                Toggle(t("Не показывать во время звонков"), null, Prefs.skipDuringCalls) { Prefs.skipDuringCalls = it }
                Line()
                Toggle(t("Новое слово при каждом включении"), t("Иначе слово меняется по кнопке «Готово»"), Prefs.newWordEachTime) {
                    Prefs.newWordEachTime = it
                }
                Line()
                Toggle(t("Слова на повторение в шторке"), t("Показывает слова с «Не знаю»"), Prefs.reviewNotification) {
                    Prefs.reviewNotification = it
                    LockService.refresh(ctx)
                }
                Line()
                NavRow(t("Зачем эти разрешения"), null) { showPermissions = true }
            }

            GroupTitle(t("Оформление"))
            Panel {
                Themes()
                Line()
                if (Decks.hasSerbian) {
                    Choice(t("Алфавит"), listOf(t("Кириллица"), "Latinica"), if (Prefs.latinFirst) 1 else 0) { Prefs.latinFirst = it == 1 }
                    Line()
                }
                if (Decks.current.target == "sr") {
                    Toggle(t("Подпись другим алфавитом"), null, Prefs.showBothScripts) { Prefs.showBothScripts = it }
                    Line()
                    Choice(t("Ударения"), Decks.accentSamples, Prefs.accentMode) { Prefs.accentMode = it }
                } else {
                    // One kind of mark only: "with" is mode 1, "without" is mode 2.
                    Choice(t("Ударения"), Decks.accentSamples, if (Prefs.accentMode == 2) 1 else 0) { Prefs.accentMode = it + 1 }
                }
                Line()
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Row {
                        Text(
                            t("Размер шрифта: %d", Prefs.wordSize),
                            fontSize = 16.sp,
                            color = p.text,
                            modifier = Modifier.weight(1f).alignByBaseline(),
                        )
                        Text(
                            Decks.sample,
                            fontFamily = Serif,
                            fontSize = (Prefs.wordSize * 0.6f).sp,
                            color = p.word,
                            modifier = Modifier.alignByBaseline(),
                        )
                    }
                    Slider(
                        value = Prefs.wordSize.toFloat(),
                        onValueChange = { Prefs.wordSize = it.toInt() },
                        valueRange = 28f..72f,
                        colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.panel),
                    )
                }
            }

            GroupTitle(t("Звук"))
            Panel {
                Toggle(t("Произносить слово сразу"), null, Prefs.autoSpeak) { Prefs.autoSpeak = it }
                Line()
                val voice = Speaker.voice.value?.getDisplayName(Locale.forLanguageTag(I18n.lang))
                NavRow(
                    t("Голос"),
                    when {
                        voice != null -> voice.replaceFirstChar { it.uppercase() }
                        Speaker.failed.value -> Decks.noVoice
                        else -> t("Проверяю…")
                    },
                ) { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            }

            GroupTitle(t("Данные"))
            Panel {
                NavRow(t("Сохранить прогресс в файл"), t("Чтобы перенести его на другое устройство")) {
                    exportLauncher.launch("reci-progress-${LocalDate.now()}.json")
                }
                Line()
                NavRow(t("Загрузить прогресс из файла"), t("Заменит прогресс на этом устройстве")) {
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }
                Line()
                for ((pos, title) in listOf("compare" to t("Снова показывать разборы"), "note" to t("Снова показывать заметки"))) {
                    val hidden = Repo.hiddenArticles(pos)
                    NavRow(
                        title,
                        if (hidden == 0) t("Скрытых нет") else t("Скрыто: %d", hidden),
                    ) { if (hidden > 0) confirmArticles = pos }
                    Line()
                }
                Text(
                    t("Сбросить прогресс"),
                    fontSize = 16.sp,
                    color = MarkDontKnow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmReset = true }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                )
            }

            GroupTitle(t("Обратная связь"))
            Panel {
                Column {
                    val waiting = Telemetry.waiting
                    NavRow(t("Написать разработчику"), if (waiting > 0) t("Ждут отправки: %d", waiting) else t("Идея, ошибка или вопрос")) {
                        writing = true
                    }
                    Line()
                    Toggle(
                        t("Анонимная статистика"),
                        t("Раз в день: сколько карточек показано и ответов дано, сколько слов выучено, разделы и настройки"),
                        Telemetry.enabled,
                    ) { Telemetry.enabled = it }
                    Line()
                    val last = Telemetry.last
                    NavRow(t("Что отправляется"), if (last != null) t("Последний раз: %s", last.optString("day")) else t("Ещё ничего не отправлялось")) {
                        whatSent = true
                    }
                    Line()
                    NavRow(t("Помочь автору"), t("Поделиться, оценить, поставить звезду")) { helping = true }
                }
            }

            GroupTitle(t("О приложении"))
            Panel {
                val version = remember {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName.orEmpty()
                }
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 18.dp)) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = p.titleRed)) { append("НЕ") }
                            append("РОДНАЯ РЕЧЬ")
                        },
                        fontFamily = Display,
                        fontSize = 26.sp,
                        color = p.word,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    Text(t("Версия %s", version), fontSize = 14.sp, color = p.secondary)
                    Text(t("Автор: Константин Хоренко"), fontSize = 14.sp, color = p.secondary)
                    Text(
                        t("Лицензии и источники"),
                        fontSize = 14.sp,
                        color = p.accent,
                        modifier = Modifier.padding(top = 8.dp, bottom = 18.dp).clickable { openLink(ctx, Telemetry.LICENSES) },
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    confirmArticles?.let { pos ->
        val what = if (pos == "compare") t("разборы похожих слов") else t("заметки")
        AlertDialog(
            onDismissRequest = { confirmArticles = null },
            title = { Text(t("Снова показывать %s?", what)) },
            text = { Text(t("Скрытые кнопкой «Не показывать» станут новыми и снова будут приходить на карточках.")) },
            confirmButton = { TextButton(onClick = { Repo.showArticlesAgain(pos); confirmArticles = null }) { Text(t("Показывать")) } },
            dismissButton = { TextButton(onClick = { confirmArticles = null }) { Text(t("Отмена")) } },
        )
    }

    if (writing) WriteDialog { writing = false }
    if (whatSent) WhatSentDialog { whatSent = false }
    if (helping) HelpDialog { helping = false }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(t("Сбросить прогресс?")) },
            text = { Text(t("Все слова снова станут новыми, избранное очистится.")) },
            confirmButton = { TextButton(onClick = { Repo.resetAll(); confirmReset = false }) { Text(t("Сбросить")) } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(t("Отмена")) } },
        )
    }

    if (showPermissions) {
        AlertDialog(
            onDismissRequest = { showPermissions = false },
            title = { Text(t("Зачем эти разрешения")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermissionNote(
                        t("Поверх других окон"),
                        t("Чтобы показать карточку на экране блокировки, когда включается экран. ") +
                            t("Без него карточка не появится."),
                    )
                    PermissionNote(
                        t("Уведомления"),
                        t("Android требует, чтобы у службы, которая ждёт включения экрана, было уведомление. ") +
                            t("В нём же показываются слова на повторение. Если запретить, карточки всё равно ") +
                            t("будут, но слов в шторке не будет."),
                    )
                    PermissionNote(
                        t("Без ограничений батареи"),
                        t("Некоторые телефоны останавливают фоновые службы, и карточки перестают появляться. ") +
                            t("Тогда это поможет. Пока экран выключен, приложение ничего не делает и батарею не тратит."),
                    )
                    PermissionNote(
                        t("Звонки"),
                        t("Необязательно. Без него звонок определяется по режиму звука, и мессенджер, который ") +
                            t("после своего звонка забыл вернуть режим «звонит», выключает карточки. С ним о ") +
                            t("телефонных звонках сообщает Android, а забытый режим «звонит» не мешает. Звонить ") +
                            t("приложение не будет, номеров не видит."),
                    )
                    PermissionNote(
                        t("Чего приложение не просит"),
                        t("Контакты, микрофон, местоположение."),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPermissions = false }) { Text(t("Понятно")) } },
        )
    }
}

@Composable
private fun CallsHint() {
    val p = LocalPalette.current
    Text(
        t("Без этого разрешения звонок определяется по режиму звука. Некоторые мессенджеры после своего ") +
            t("звонка забывают вернуть режим «звонит», и карточки перестают появляться. С разрешением о ") +
            t("телефонных звонках сообщает сам Android, и забытый режим «звонит» не мешает. Разговор в ") +
            t("мессенджере по-прежнему прячет карточку. Android назовёт разрешение «звонки», но звонить ") +
            t("приложение не будет и номеров не видит."),
        fontSize = 12.sp,
        color = p.secondary,
        lineHeight = 17.sp,
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
    )
}

@Composable
private fun BatteryHint() {
    val p = LocalPalette.current
    val steps = listOf(
        "App battery usage.",
        "Allow background usage → Unrestricted.",
        t("Вернитесь сюда: вместо «выдать» будет «есть»."),
    )
    Column(
        Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(t("Откроется страница приложения в настройках Android. Там:"), fontSize = 12.sp, color = p.secondary, lineHeight = 17.sp)
        steps.forEachIndexed { i, step ->
            Row {
                Text("${i + 1}.", fontSize = 12.sp, color = p.accent, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, modifier = Modifier.width(18.dp))
                Text(step, fontSize = 12.sp, color = p.text, lineHeight = 17.sp)
            }
        }
        Text(
            t("На старых версиях Android и на некоторых телефонах путь другой: Battery → Battery optimization → ") +
                t("All apps → НЕРодная речь → Don't optimize."),
            fontSize = 12.sp,
            color = p.secondary,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun RestrictedHint() {
    val p = LocalPalette.current
    val steps = listOf(
        t("Settings → Apps → НЕРодная речь."),
        t("⋮ справа вверху → Allow restricted settings."),
        t("Подтвердите PIN-кодом или отпечатком."),
        t("Вернитесь сюда и снова нажмите «выдать»."),
    )
    Column(
        Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            t("Если переключатель не включается, Android ограничил настройки. Так бывает у приложений, ") +
                t("установленных не из Google Play. Снимите ограничение:"),
            fontSize = 12.sp,
            color = p.secondary,
            lineHeight = 17.sp,
        )
        steps.forEachIndexed { i, step ->
            Row {
                Text("${i + 1}.", fontSize = 12.sp, color = p.accent, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, modifier = Modifier.width(18.dp))
                Text(step, fontSize = 12.sp, color = p.text, lineHeight = 17.sp)
            }
        }
        Text(
            t("Если пункта Allow restricted settings в меню нет, сначала попробуйте включить разрешение, ") +
                t("а потом откройте меню ещё раз."),
            fontSize = 12.sp,
            color = p.secondary,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PermissionNote(title: String, text: String) {
    Column {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(text, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun ProgressCard() {
    val p = LocalPalette.current
    val all = Repo.words
    val counts = State.entries.associateWith { s -> all.count { Repo.progress(it).state == s } }
    val known = counts[State.KNOWN]!!
    Column(
        Modifier
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(p.card)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$known", fontFamily = Serif, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, color = p.word)
            Spacer(Modifier.width(8.dp))
            Text(t("из %d слов знаю", all.size), fontSize = 15.sp, color = p.secondary, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(p.panel)) {
            if (all.isNotEmpty()) Box(Modifier.fillMaxWidth(known.toFloat() / all.size).fillMaxHeight().background(p.known))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Count(t("не знаю"), counts[State.DONT_KNOW]!! + counts[State.UNSURE]!!, MarkDontKnow)
            Count(t("сегодня"), Prefs.todayCount, p.accent)
        }
    }
}

@Composable
private fun Count(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text("$value $label", fontSize = 14.sp, color = LocalPalette.current.text)
    }
}

// The step of the lock screen offer to take next.
private fun Modifier.highlighted(on: Boolean, title: String, p: Palette): Modifier {
    if (!on) return this
    val shape = RoundedCornerShape(14.dp)
    return testTag("highlighted:$title").padding(4.dp).clip(shape).background(p.highlight).border(2.dp, p.accent, shape)
}

// Over the lock screen section when the offer opened the settings: what is left to do.
@Composable
private fun LockGuide(ready: Boolean) {
    val p = LocalPalette.current
    Text(
        if (ready) t("Готово! Заблокируйте телефон и включите экран — там будет карточка.")
        else t("Включите подсвеченный пункт — и карточка готова."),
        fontSize = 14.sp,
        color = p.text,
        lineHeight = 19.sp,
        modifier = Modifier
            .testTag("lockGuide")
            .padding(start = 14.dp, end = 14.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(p.highlight)
            .padding(14.dp),
    )
}

@Composable
private fun Line() = HorizontalDivider(Modifier.padding(start = 18.dp), color = LocalPalette.current.divider)

@Composable
private fun Toggle(title: String, summary: String?, value: Boolean, highlight: Boolean = false, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .highlighted(highlight, title, p)
            .clickable { onChange(!value) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = p.text)
            if (summary != null) Text(summary, fontSize = 13.sp, color = p.secondary, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = value,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = p.accent, uncheckedTrackColor = p.panel),
        )
    }
}

@Composable
private fun NavRow(title: String, summary: String?, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = p.text)
            if (summary != null) Text(summary, fontSize = 13.sp, color = p.secondary)
        }
        Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = p.faint, modifier = Modifier.size(18.dp))
    }
}

// A choice that grows with the languages: the row shows the value, a tap opens the options.
// Each option is tagged "<tag>:<value>" for the tests.
@Composable
private fun MenuChoice(
    title: String,
    tag: String,
    values: List<String>,
    value: String,
    label: (String) -> String,
    onSelect: (String) -> Unit,
) {
    val p = LocalPalette.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag(tag)
                .clickable { open = true }
                .padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 16.sp, color = p.text, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(label(value), fontSize = 16.sp, color = p.secondary)
            Icon(painterResource(R.drawable.ic_expand_more), null, tint = p.faint, modifier = Modifier.size(22.dp))
        }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            values.forEach { v ->
                val on = v == value
                DropdownMenuItem(
                    text = {
                        Text(
                            label(v),
                            fontSize = 16.sp,
                            color = if (on) p.accent else p.text,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(v)
                    },
                    modifier = Modifier.testTag("$tag:$v"),
                )
            }
        }
    }
}

@Composable
private fun Choice(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(title, fontSize = 16.sp, color = p.text)
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(p.panel)
                .padding(3.dp)
        ) {
            options.forEachIndexed { i, o ->
                val on = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(11.dp))
                        .background(if (on) p.card else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        o,
                        fontSize = 15.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (on) p.accent else p.secondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun Themes() {
    val p = LocalPalette.current
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(t("Тема"), fontSize = 16.sp, color = p.text, modifier = Modifier.padding(horizontal = 18.dp))
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Palettes.forEachIndexed { i, pal ->
                val on = Prefs.theme == i
                Column(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { Prefs.theme = i }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(pal.bg)
                            .border(if (on) 3.dp else 1.dp, if (on) p.accent else p.divider, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(pal.accent))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(pal.name, fontSize = 12.sp, color = if (on) p.accent else p.secondary)
                }
            }
        }
    }
}

@Composable
private fun Permission(title: String, hint: String, granted: Boolean, highlight: Boolean = false, onGrant: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .highlighted(highlight, title, p)
            .clickable(enabled = !granted, onClick = onGrant)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = p.text)
            Text(hint, fontSize = 12.sp, color = p.secondary)
        }
        Text(
            if (granted) t("есть") else t("выдать"),
            color = if (granted) p.known else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .testTag("permission:$title")
                .clip(RoundedCornerShape(50))
                .background(if (granted) p.known.copy(alpha = 0.12f) else p.accent)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun Pause() {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    val endOfDay = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
    }.timeInMillis
    val until = Prefs.pausedUntil
    val paused = until > now
    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(t("Пауза"), fontSize = 16.sp, color = p.text)
        Text(
            if (paused) t("Карточки не показываются до ") + SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru")).format(Date(until))
            else t("Не показывать карточки:"),
            fontSize = 13.sp,
            color = p.secondary,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (paused) {
                SelectChip(t("Снять паузу"), true) { Prefs.pausedUntil = 0 }
            } else {
                SelectChip(t("1 час"), false) { Prefs.pausedUntil = now + 60 * 60_000L }
                SelectChip(t("3 часа"), false) { Prefs.pausedUntil = now + 3 * 60 * 60_000L }
                SelectChip(t("До конца дня"), false) { Prefs.pausedUntil = endOfDay }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Switching off stops the service as well, unlike a timed pause.
        SelectChip(t("Отключить совсем"), false) {
            Prefs.lockEnabled = false
            LockService.sync(ctx)
        }
    }
}
