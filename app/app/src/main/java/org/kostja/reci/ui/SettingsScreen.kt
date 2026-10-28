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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import org.kostja.reci.data.Labels
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.Speaker
import org.kostja.reci.data.State
import org.kostja.reci.lock.LockActivity
import org.kostja.reci.lock.LockService
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun canNotify(ctx: Context) = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
    PackageManager.PERMISSION_GRANTED

// After two refusals Android stops asking, so the row leads to the app's system page instead.
internal fun afterCallsRequest(activity: Activity, granted: Boolean) {
    if (granted || ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.READ_PHONE_STATE)) return
    activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")))
}

private fun ignoresBattery(ctx: Context) =
    ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

@Composable
fun SettingsScreen(openCategories: () -> Unit, back: () -> Unit) {
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
            toast(if (ok) "Прогресс сохранён." else "Не удалось сохранить файл.")
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
                if (n == null) "Это не файл с прогрессом."
                else "Загружен прогресс по $n ${plural(n, "слову", "словам", "словам")}."
            )
        }
    }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmArticles by remember { mutableStateOf<String?>(null) }
    var showPermissions by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { Speaker.connect() }

    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding()) {
        Header("Настройки", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ProgressCard()

            GroupTitle("Учёба")
            Panel {
                val cats = Prefs.categories
                NavRow(
                    "Что учим",
                    (if (cats.size == 1) Labels.category(cats.first()) else "Разделов: ${cats.size}") + " · " + Prefs.order.label,
                    openCategories,
                )
                Line()
                Choice("Карточки", listOf("Обучение", "Перевод", "Слово"), Prefs.cardMode) { Prefs.cardMode = it }
                Text(
                    when (Prefs.cardMode) {
                        1 -> "Видно только перевод: вспомните слово и откройте его кнопкой или ответом."
                        2 -> "Видно только слово: перевод открывается по нажатию."
                        else -> "Слово учится по шагам: сначала показано целиком, после «Знаю» " +
                            "надо вспомнить его по переводу, после трёх «Знаю» подряд оно выучено. " +
                            "«Не знаю» вернёт слово через пять карточек. Долгое нажатие на «Знаю» — слово уже знакомо."
                    },
                    fontSize = 13.sp,
                    color = p.secondary,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                )
                Line()
                Choice("Слов в работе одновременно", listOf("10", "15", "20", "30"), listOf(10, 15, 20, 30).indexOf(Prefs.activeSize).coerceAtLeast(0)) {
                    Prefs.activeSize = listOf(10, 15, 20, 30)[it]
                }
                Line()
                Toggle("Кнопки «Не знаю / Знаю»", null, Prefs.useMarks) { Prefs.useMarks = it }

                Line()
                Toggle(
                    "Проверять выученные слова",
                    "Через неделю, потом через 3 недели, 2 месяца, полгода и год",
                    Prefs.reviewKnown,
                ) { Prefs.reviewKnown = it }
            }

            GroupTitle("Экран блокировки")
            Panel {
                Toggle("Карточка при включении экрана", null, Prefs.lockEnabled) {
                    Prefs.lockEnabled = it
                    if (it && !notify && Build.VERSION.SDK_INT >= 33) {
                        notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    LockService.sync(ctx)
                }
                if (Prefs.lockEnabled) {
                    Line()
                    Permission("Поверх других окон", "Без этого Android не покажет карточку", overlay) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        )
                    }
                    if (!overlay) RestrictedHint()
                    Line()
                    Permission("Уведомления", "Для слов на повторение в шторке", notify) {
                        if (Build.VERSION.SDK_INT >= 33) notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    Line()
                    Permission("Без ограничений батареи", "Нужно, только если карточки пропадают", battery) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        )
                    }
                    if (!battery) BatteryHint()
                    if (Prefs.skipDuringCalls) {
                        Line()
                        Permission("Звонки", "Надёжнее понять, идёт ли звонок", calls) {
                            callsLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                        }
                        if (!calls) CallsHint()
                    }
                }
                Line()
                Pause()
                Line()
                Toggle("Не показывать во время звонков", null, Prefs.skipDuringCalls) { Prefs.skipDuringCalls = it }
                Line()
                Toggle("Новое слово при каждом включении", "Иначе слово меняется по кнопке «Готово»", Prefs.newWordEachTime) {
                    Prefs.newWordEachTime = it
                }
                Line()
                Toggle("Слова на повторение в шторке", "Показывает слова с «Не знаю»", Prefs.reviewNotification) {
                    Prefs.reviewNotification = it
                    LockService.refresh(ctx)
                }
                Line()
                NavRow("Зачем эти разрешения", null) { showPermissions = true }
            }

            GroupTitle("Оформление")
            Panel {
                Themes()
                Line()
                Choice("Алфавит", listOf("Кириллица", "Latinica"), if (Prefs.latinFirst) 1 else 0) { Prefs.latinFirst = it == 1 }
                Line()
                Toggle("Подпись другим алфавитом", null, Prefs.showBothScripts) { Prefs.showBothScripts = it }
                Line()
                Choice("Ударения", listOf("ку̏ћа", "ку́ћа", "кућа"), Prefs.accentMode) { Prefs.accentMode = it }
                Line()
                Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                    Row {
                        Text(
                            "Размер шрифта: ${Prefs.wordSize}",
                            fontSize = 16.sp,
                            color = p.text,
                            modifier = Modifier.weight(1f).alignByBaseline(),
                        )
                        Text(
                            "реч",
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

            GroupTitle("Звук")
            Panel {
                Toggle("Произносить слово сразу", null, Prefs.autoSpeak) { Prefs.autoSpeak = it }
                Line()
                val voice = Speaker.voice.value
                NavRow(
                    "Голос",
                    when {
                        voice != null -> voice.replaceFirstChar { it.uppercase() }
                        Speaker.failed.value -> "Нет сербского, хорватского или боснийского голоса"
                        else -> "Проверяю…"
                    },
                ) { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
            }

            GroupTitle("Данные")
            Panel {
                NavRow("Сохранить прогресс в файл", "Чтобы перенести его на другое устройство") {
                    exportLauncher.launch("reci-progress-${LocalDate.now()}.json")
                }
                Line()
                NavRow("Загрузить прогресс из файла", "Заменит прогресс на этом устройстве") {
                    importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }
                Line()
                for ((pos, title) in listOf("compare" to "Снова показывать разборы", "note" to "Снова показывать заметки")) {
                    val hidden = Repo.hiddenArticles(pos)
                    NavRow(
                        title,
                        if (hidden == 0) "Скрытых нет" else "Скрыто: $hidden",
                    ) { if (hidden > 0) confirmArticles = pos }
                    Line()
                }
                Text(
                    "Сбросить прогресс",
                    fontSize = 16.sp,
                    color = MarkDontKnow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { confirmReset = true }
                        .padding(horizontal = 18.dp, vertical = 15.dp),
                )
            }

            GroupTitle("О приложении")
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
                    Text("Версия $version", fontSize = 14.sp, color = p.secondary)
                    Text("Автор: Константин Хоренко", fontSize = 14.sp, color = p.secondary)
                    Text("Лицензия: GNU GPL v3 или новее", fontSize = 14.sp, color = p.secondary)
                }
                Text(
                    "Частотность слов: FrequencyWords (OpenSubtitles), CC BY-SA 4.0.\n" +
                        "Ударения: Викисловарь (kaikki.org), CC BY-SA.\n" +
                        "Часть карточек и подсказок составлена по материалам из открытых источников.\n" +
                        "Шрифты Literata и Ruslan Display: SIL Open Font License.\n" +
                        "Иконки: Material Icons, Apache 2.0.",
                    fontSize = 13.sp,
                    color = p.secondary,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(18.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    confirmArticles?.let { pos ->
        val what = if (pos == "compare") "разборы похожих слов" else "заметки"
        AlertDialog(
            onDismissRequest = { confirmArticles = null },
            title = { Text("Снова показывать $what?") },
            text = { Text("Скрытые кнопкой «Не показывать» станут новыми и снова будут приходить на карточках.") },
            confirmButton = { TextButton(onClick = { Repo.showArticlesAgain(pos); confirmArticles = null }) { Text("Показывать") } },
            dismissButton = { TextButton(onClick = { confirmArticles = null }) { Text("Отмена") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить прогресс?") },
            text = { Text("Все слова снова станут новыми, избранное очистится.") },
            confirmButton = { TextButton(onClick = { Repo.resetAll(); confirmReset = false }) { Text("Сбросить") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } },
        )
    }

    if (showPermissions) {
        AlertDialog(
            onDismissRequest = { showPermissions = false },
            title = { Text("Зачем эти разрешения") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PermissionNote(
                        "Поверх других окон",
                        "Чтобы показать карточку на экране блокировки, когда включается экран. " +
                            "Без него карточка не появится.",
                    )
                    PermissionNote(
                        "Уведомления",
                        "Android требует, чтобы у службы, которая ждёт включения экрана, было уведомление. " +
                            "В нём же показываются слова на повторение. Если запретить, карточки всё равно " +
                            "будут, но слов в шторке не будет.",
                    )
                    PermissionNote(
                        "Без ограничений батареи",
                        "Некоторые телефоны останавливают фоновые службы, и карточки перестают появляться. " +
                            "Тогда это поможет. Пока экран выключен, приложение ничего не делает и батарею не тратит.",
                    )
                    PermissionNote(
                        "Звонки",
                        "Необязательно. Без него звонок определяется по режиму звука, и мессенджер, который " +
                            "после своего звонка забыл вернуть режим «звонит», выключает карточки. С ним о " +
                            "телефонных звонках сообщает Android, а забытый режим «звонит» не мешает. Звонить " +
                            "приложение не будет, номеров не видит.",
                    )
                    PermissionNote(
                        "Чего приложение не просит",
                        "Контакты, микрофон, местоположение.",
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPermissions = false }) { Text("Понятно") } },
        )
    }
}

@Composable
private fun CallsHint() {
    val p = LocalPalette.current
    Text(
        "Без этого разрешения звонок определяется по режиму звука. Некоторые мессенджеры после своего " +
            "звонка забывают вернуть режим «звонит», и карточки перестают появляться. С разрешением о " +
            "телефонных звонках сообщает сам Android, и забытый режим «звонит» не мешает. Разговор в " +
            "мессенджере по-прежнему прячет карточку. Android назовёт разрешение «звонки», но звонить " +
            "приложение не будет и номеров не видит.",
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
        "Вернитесь сюда: вместо «выдать» будет «есть».",
    )
    Column(
        Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Откроется страница приложения в настройках Android. Там:", fontSize = 12.sp, color = p.secondary, lineHeight = 17.sp)
        steps.forEachIndexed { i, step ->
            Row {
                Text("${i + 1}.", fontSize = 12.sp, color = p.accent, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, modifier = Modifier.width(18.dp))
                Text(step, fontSize = 12.sp, color = p.text, lineHeight = 17.sp)
            }
        }
        Text(
            "На старых версиях Android и на некоторых телефонах путь другой: Battery → Battery optimization → " +
                "All apps → НЕРодная речь → Don't optimize.",
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
        "Settings → Apps → НЕРодная речь.",
        "⋮ справа вверху → Allow restricted settings.",
        "Подтвердите PIN-кодом или отпечатком.",
        "Вернитесь сюда и снова нажмите «выдать».",
    )
    Column(
        Modifier.padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Если переключатель не включается, Android ограничил настройки. Так бывает у приложений, " +
                "установленных не из Google Play. Снимите ограничение:",
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
            "Если пункта Allow restricted settings в меню нет, сначала попробуйте включить разрешение, " +
                "а потом откройте меню ещё раз.",
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
            Text("из ${all.size} слов знаю", fontSize = 15.sp, color = p.secondary, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(p.panel)) {
            if (all.isNotEmpty()) Box(Modifier.fillMaxWidth(known.toFloat() / all.size).fillMaxHeight().background(p.known))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Count("не знаю", counts[State.DONT_KNOW]!! + counts[State.UNSURE]!!, MarkDontKnow)
            Count("сегодня", Prefs.todayCount, p.accent)
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

@Composable
private fun Line() = HorizontalDivider(Modifier.padding(start = 18.dp), color = LocalPalette.current.divider)

@Composable
private fun Toggle(title: String, summary: String?, value: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
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
        Text("Тема", fontSize = 16.sp, color = p.text, modifier = Modifier.padding(horizontal = 18.dp))
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
private fun Permission(title: String, hint: String, granted: Boolean, onGrant: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted, onClick = onGrant)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = p.text)
            Text(hint, fontSize = 12.sp, color = p.secondary)
        }
        Text(
            if (granted) "есть" else "выдать",
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
        Text("Пауза", fontSize = 16.sp, color = p.text)
        Text(
            if (paused) "Карточки не показываются до " + SimpleDateFormat("HH:mm", Locale.forLanguageTag("ru")).format(Date(until))
            else "Не показывать карточки:",
            fontSize = 13.sp,
            color = p.secondary,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (paused) {
                SelectChip("Снять паузу", true) { Prefs.pausedUntil = 0 }
            } else {
                SelectChip("1 час", false) { Prefs.pausedUntil = now + 60 * 60_000L }
                SelectChip("3 часа", false) { Prefs.pausedUntil = now + 3 * 60 * 60_000L }
                SelectChip("До конца дня", false) { Prefs.pausedUntil = endOfDay }
            }
        }
        Spacer(Modifier.height(8.dp))
        // Switching off stops the service as well, unlike a timed pause.
        SelectChip("Отключить совсем", false) {
            Prefs.lockEnabled = false
            LockService.sync(ctx)
        }
    }
}
