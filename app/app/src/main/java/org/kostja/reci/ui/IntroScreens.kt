package org.kostja.reci.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import org.kostja.reci.data.Decks
import org.kostja.reci.data.Intro
import org.kostja.reci.data.Prefs
import org.kostja.reci.data.Repo
import org.kostja.reci.data.t

// A word to show each language learned by, so that Serbian is not taken for Russian.
private val samples = mapOf("sr" to "ку́ћа · kuća", "ru" to "доро́га")

// The first start: which language to learn and the language of the translations, preset by the
// phone's language.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PairScreen(onStart: (deck: String) -> Unit) {
    val p = LocalPalette.current
    val deck = Decks.current
    var target by rememberSaveable { mutableStateOf(deck.target) }
    var native by rememberSaveable { mutableStateOf(deck.native) }
    // Another choice would race the cards of the pair chosen while they load.
    val loading = Intro.loading.value
    Column(Modifier.fillMaxSize().background(p.bg).systemBarsPadding().testTag("pair").alpha(if (loading) 0.6f else 1f)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "НЕРодная речь".uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = p.accent,
            )
            Spacer(Modifier.height(6.dp))
            Text(t("Что будем учить?"), fontFamily = Serif, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = p.word, lineHeight = 36.sp)
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Decks.targets.forEach { code ->
                    LanguageOption(code, samples[code], code == target, !loading) {
                        target = code
                        native = Decks.pick(code, native).native
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(t("Перевод на"), fontFamily = Serif, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = p.word)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Decks.natives(target).forEach { code ->
                    val on = code == native
                    Text(
                        Decks.name(code),
                        fontSize = 16.sp,
                        color = if (on) p.accent else p.text,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier
                            .testTag("native:$code")
                            .clip(RoundedCornerShape(50))
                            .background(p.card)
                            .border(2.dp, if (on) p.accent else Color.Transparent, RoundedCornerShape(50))
                            .clickable(enabled = !loading) { native = code }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                t("Выбрано по языку телефона. Всё можно поменять потом в настройках."),
                fontSize = 14.sp,
                color = p.secondary,
                lineHeight = 19.sp,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(p.card).padding(16.dp),
            )
            Spacer(Modifier.height(16.dp))
        }
        PrimaryButton(t("Начать"), Modifier.padding(18.dp), enabled = !loading) { onStart(Decks.pick(target, native).id) }
    }
}

@Composable
private fun LanguageOption(code: String, sample: String?, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .testTag("target:$code")
            .clip(RoundedCornerShape(20.dp))
            .background(if (on) p.highlight else p.card)
            .border(2.dp, if (on) p.accent else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(Decks.name(code), fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = p.word)
            if (sample != null) Text(sample, fontFamily = Serif, fontSize = 15.sp, color = p.secondary)
        }
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .border(2.dp, if (on) p.accent else p.faint, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(if (on) p.accent else Color.Transparent)
        )
    }
}

@Composable
private fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .background(p.accent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LaterButton(onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(t("Позже"), color = LocalPalette.current.word, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

// The offer to put the card on the lock screen, over the main screen. "Yes" tells the two steps
// before it opens the settings; "Later" and Back put it off.
@Composable
fun LockOffer(onLater: () -> Unit, onSetUp: () -> Unit) {
    val p = LocalPalette.current
    var steps by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onLater)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("lockOffer")
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(p.bg)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 16.dp),
        ) {
            if (!steps) OfferText() else StepsText()
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                LaterButton(onLater, Modifier.weight(1f))
                if (!steps) {
                    PrimaryButton(t("Да, настроить"), Modifier.weight(1.4f)) { steps = true }
                } else {
                    PrimaryButton(t("Открыть настройки"), Modifier.weight(1.6f), onClick = onSetUp)
                }
            }
        }
    }
}

@Composable
private fun OfferText() {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        MiniLockScreen()
        Spacer(Modifier.width(16.dp))
        Column {
            Text(t("Главное в приложении").uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = p.accent)
            Spacer(Modifier.height(6.dp))
            Text(t("Слово на экране блокировки"), fontFamily = Serif, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = p.word, lineHeight = 27.sp)
        }
    }
    Spacer(Modifier.height(14.dp))
    listOf(
        t("Берёте телефон — и видите слово, ещё до разблокировки."),
        t("«Знаю» или «Не знаю» — карточка уходит, дальше всё как обычно."),
        t("Не нужно выкраивать время: слова набираются сами."),
    ).forEach { line ->
        Row(Modifier.padding(top = 4.dp)) {
            Text("+", color = p.accent, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.width(20.dp))
            Text(line, color = p.text, fontSize = 15.sp, lineHeight = 20.sp)
        }
    }
    Spacer(Modifier.height(14.dp))
    Text(t("Настроить? Это один переключатель и одно разрешение."), color = p.word, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    Text(t("Можно и потом: Настройки → Экран блокировки."), color = p.secondary, fontSize = 13.sp)
}

@Composable
private fun StepsText() {
    val p = LocalPalette.current
    Text(t("Два шага в настройках"), fontFamily = Serif, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = p.word)
    Spacer(Modifier.height(6.dp))
    Text(t("Сейчас откроем настройки, нужные пункты будут подсвечены."), color = p.secondary, fontSize = 15.sp, lineHeight = 20.sp)
    listOf(
        t("Включите «Карточка при включении экрана».") to
            t("Если Android спросит про уведомления: в них слова на повторение, карточка работает и без них."),
        t("Нажмите «Поверх других окон» и включите переключатель у НЕРодной речи.") to
            t("Без этого Android не покажет карточку. Потом вернитесь в приложение кнопкой «Назад»."),
    ).forEachIndexed { i, (step, note) ->
        Row(Modifier.padding(top = 14.dp)) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(p.accent), contentAlignment = Alignment.Center) {
                Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(step, color = p.text, fontSize = 16.sp, lineHeight = 21.sp)
                Text(note, color = p.secondary, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}

// A small phone with the card on screen on its lock screen.
@Composable
private fun MiniLockScreen() {
    val p = LocalPalette.current
    Repo.version.intValue
    val w = Repo.current()
    Column(
        Modifier
            .width(120.dp)
            .height(210.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF2A2A28))
            .padding(4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF2C3E2A), Color(0xFF172414))))
            .padding(horizontal = 7.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.card).padding(8.dp)) {
            Text(
                w?.let { Prefs.headword(it) } ?: Decks.sample,
                fontFamily = Serif,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = p.word,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            w?.meanings?.firstOrNull()?.let {
                Text(Prefs.native(it), fontSize = 10.sp, color = p.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(p.card).padding(vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(MarkDontKnow to t("Не знаю"), p.known to t("Знаю")).forEach { (color, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(3.dp))
                    Text(label, fontSize = 8.sp, color = p.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}
