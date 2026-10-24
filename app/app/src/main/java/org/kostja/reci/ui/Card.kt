package org.kostja.reci.ui

import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.kostja.reci.R
import org.kostja.reci.data.Labels
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.State
import org.kostja.reci.data.Word
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CardScreen(
    onNext: () -> Unit,
    // Set on the lock screen: closes the card, while onNext only flips to the next word.
    onDone: (() -> Unit)? = null,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onCategories: () -> Unit,
    onGrammar: (String?) -> Unit,
) {
    Repo.version.intValue
    Prefs.version.intValue
    val p = LocalPalette.current
    val w = Repo.current()

    // Fixed for one showing of a card: answering moves the word to its next stage, but the
    // card on screen keeps the layout it was shown with, and only the first answer counts.
    val showId = Prefs.cardCount
    val recall = remember(w?.key, showId) { w != null && Repo.asksRecall(w) }
    val stage = remember(w?.key, showId) { w?.let { stageLabel(it) } }
    var revealed by rememberSaveable(w?.key, showId) { mutableStateOf(false) }
    var answered by remember(w?.key, showId) { mutableStateOf<State?>(null) }
    val onAnswer = { a: State, learnedNow: Boolean ->
        if (w != null && answered == null) {
            if (learnedNow) Repo.markLearned(w) else Repo.answer(w, a)
            answered = a
        }
        revealed = true
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .systemBarsPadding()
    ) {
        TopBar(onSearch, { onGrammar(null) }, onSettings, onCategories)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .shadow(3.dp, RoundedCornerShape(28.dp), clip = false)
                .clip(RoundedCornerShape(28.dp))
                .background(p.card)
        ) {
            if (w == null) {
                if (Repo.isLoaded) {
                    if (Repo.exhausted.value) Empty(onCategories) else LaunchedEffect(Unit) { Repo.step(1) }
                }
                return@Box
            }
            // The lock card is created and resumed while the screen is off; count the word
            // once the screen is actually on, and once only across activity recreation.
            val ctx = LocalContext.current
            var counted by rememberSaveable(w.key, showId) { mutableStateOf(false) }
            LifecycleResumeEffect(w.key, showId) {
                if (!counted && ctx.getSystemService(PowerManager::class.java).isInteractive) {
                    counted = true
                    Repo.markShown(w)
                }
                onPauseOrDispose {}
            }
            key(w.key, showId) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 20.dp)
                ) {
                    if (stage != null) {
                        Text(stage, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = p.accent)
                        Spacer(Modifier.height(6.dp))
                    }
                    WordBody(
                        w,
                        slide = Prefs.cardMode == 2 && !Repo.isArticle(w),
                        onGrammar = onGrammar,
                        hideWord = recall && !revealed,
                        onReveal = { revealed = true },
                    )
                }
            }
            Star(w, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp))
        }
        if (Prefs.useMarks && w != null) {
            MarksRow(answered, Modifier.padding(horizontal = 14.dp, vertical = 4.dp), onAnswer)
        }
        BottomNav(onPrev = { Repo.step(-1) }, onNext = onNext, onDone = onDone)
    }
}

// What the user is asked to do with this card, shown above it.
private fun stageLabel(w: Word): String? {
    if (Repo.isArticle(w)) return null
    val pr = Repo.progress(w)
    return when {
        Repo.isDue(pr) -> "Проверка: вспомните слово"
        pr.phase == Repo.RECALL -> "Вспомните слово · «Знаю» ${pr.streak} из 3"
        pr.phase == Repo.LEARNED -> "Выучено"
        pr.streak > 0 -> "Знакомство · «Знаю» ${pr.streak} из 2"
        else -> "Новое слово"
    }
}

@Composable
private fun Empty(onCategories: () -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Здесь пока пусто", fontFamily = Serif, fontSize = 24.sp, color = p.word)
        Spacer(Modifier.height(8.dp))
        Text("В выбранных разделах не осталось слов", fontSize = 15.sp, color = p.secondary)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onCategories,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = Color.White),
        ) { Text("Выбрать слова") }
    }
}

private val BAR_HEIGHT = 44.dp

@Composable
private fun TopBar(onSearch: () -> Unit, onGrammar: () -> Unit, onSettings: () -> Unit, onCategories: () -> Unit) {
    val p = LocalPalette.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // The clock ticks only while the card is visible, so it costs nothing with the screen off.
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(1000L * (60 - (now / 1000) % 60))
            }
        }
    }
    val formats = remember {
        val ru = Locale.forLanguageTag("ru")
        listOf("HH:mm", "EEE, d MMM").map { SimpleDateFormat(it, ru) }
    }
    val date = Date(now)

    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(formats[0].format(date), fontSize = 32.sp, fontWeight = FontWeight.Medium, color = p.text, lineHeight = 34.sp)
            Text(formats[1].format(date), fontSize = 13.sp, color = p.secondary)
        }
        // The font's internal leading puts the clock digits a little below the box top.
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DeckChip(onCategories, Modifier.height(BAR_HEIGHT).widthIn(max = 150.dp))
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier
                    .height(BAR_HEIGHT)
                    .clip(RoundedCornerShape(50))
                    .background(p.card)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarIcon(R.drawable.ic_search, "Поиск", onSearch)
                BarIcon(R.drawable.ic_book, "Грамматика", onGrammar)
                BarIcon(R.drawable.ic_settings, "Настройки", onSettings)
            }
        }
    }
}

@Composable
private fun BarIcon(icon: Int, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = LocalPalette.current.secondary, modifier = Modifier.size(21.dp))
    }
}

@Composable
private fun DeckChip(onCategories: () -> Unit, modifier: Modifier) {
    val p = LocalPalette.current
    val cats = Prefs.categories
    val first = cats.minOrNull()
    val title = when {
        first == null -> "—"
        first.startsWith("L") -> Labels.category(first).substringBefore(' ')
        else -> Labels.category(first)
    } + if (cats.size > 1) " +${cats.size - 1}" else ""
    // Known words out of all words in the chosen sections, the marked ones included.
    val (known, total) = remember(Repo.version.intValue, cats) {
        var k = 0
        var t = 0
        for (w in Repo.words) {
            if (cats.none { Repo.inCategory(w, it) }) continue
            t++
            if (Repo.progress(w).state == State.KNOWN) k++
        }
        k to t
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(p.card)
            .clickable(onClick = onCategories)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = p.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (Repo.isLoaded) Text("  $known/$total", fontSize = 13.sp, color = p.secondary, maxLines = 1)
        Icon(painterResource(R.drawable.ic_expand_more), null, tint = p.accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun Star(w: Word, modifier: Modifier) {
    val p = LocalPalette.current
    val fav = Repo.progress(w).fav
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { Repo.toggleFav(w) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (fav) R.drawable.ic_star else R.drawable.ic_star_border),
            "Избранное",
            tint = if (fav) MarkUnsure else p.faint,
            modifier = Modifier.size(24.dp),
        )
    }
}

// Long-press on «Знаю» marks a word the user already knows as learned at once.
@Composable
fun MarksRow(answered: State?, modifier: Modifier = Modifier, onAnswer: (State, Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(p.card)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Mark("Не знаю", answered == State.DONT_KNOW, MarkDontKnow, Modifier.weight(1f)) { onAnswer(State.DONT_KNOW, false) }
        Mark(
            "Знаю",
            answered == State.KNOWN,
            p.accent,
            Modifier.weight(1f),
            onLongClick = { onAnswer(State.KNOWN, true) },
        ) { onAnswer(State.KNOWN, false) }
    }
}

@Composable
private fun Mark(
    label: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) color else Color.Transparent)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (selected) Color.White else color))
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else LocalPalette.current.text,
        )
    }
}

@Composable
private fun BottomNav(onPrev: () -> Unit, onNext: () -> Unit, onDone: (() -> Unit)?) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavCircle(R.drawable.ic_chevron_left, "Предыдущее слово", onPrev)
        Row(
            Modifier
                .weight(1f)
                .height(54.dp)
                .clip(RoundedCornerShape(27.dp))
                .background(p.accent)
                .clickable(onClick = onDone ?: onNext),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onDone != null) {
                Icon(painterResource(R.drawable.ic_check), null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Готово", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Дальше", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_arrow_forward), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        if (onDone != null) NavCircle(R.drawable.ic_chevron_right, "Следующее слово", onNext)
    }
}

@Composable
private fun NavCircle(icon: Int, description: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(p.card)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), description, tint = p.text, modifier = Modifier.size(28.dp))
    }
}
