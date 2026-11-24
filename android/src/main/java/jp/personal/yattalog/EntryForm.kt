package jp.personal.yattalog

import android.widget.CalendarView
import android.widget.NumberPicker
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.viewinterop.AndroidView
import java.text.SimpleDateFormat
import java.util.*

private val paper = Color(0xfffaf8f6)
private val ink = Color(0xff302d30)
private val accent = Color(0xff74465b)
private val soft = Color(0xfff0e7eb)
private val durations = listOf(1, 2, 3, 4) + (5..1440 step 5)
private fun stamp(time: Long, pattern: String) =
    SimpleDateFormat(pattern, Locale.JAPAN).format(Date(time))

@Composable
private fun Label(text: String, size: Int = 14, color: Color = ink) {
    BasicText(text, style = TextStyle(color = color, fontSize = size.sp))
}

@Composable
private fun Action(text: String, name: String, onClick: () -> Unit, selected: Boolean = false) {
    Box(Modifier.clip(RoundedCornerShape(28.dp)).background(if (selected) soft else Color.Transparent)
        .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = name }
        .heightIn(min = 44.dp).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Label(text) }
}

@Composable
internal fun EntryForm(draft: FormDraft, compact: Boolean, fold: () -> Unit, close: () -> Unit, save: () -> Unit) {
    var pickingDate by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(paper)) {
        Column(Modifier.fillMaxSize().padding(bottom = 66.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = if (compact) 18.dp else 26.dp, vertical = if (compact) 12.dp else 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 400.dp).fillMaxWidth()) {
                Label("やったこと")
                Spacer(Modifier.height(8.dp))
                BasicTextField(draft.title, { if (it.length <= 200) { draft.title = it; draft.message = "" } },
                    Modifier.fillMaxWidth().semantics { contentDescription = "やったこと" },
                    enabled = !draft.saving, singleLine = true, textStyle = TextStyle(color = ink, fontSize = 20.sp),
                    decorationBox = { input -> Box { if (draft.title.isEmpty()) Label("部屋を掃除した", 20, Color.DarkGray); input() } })
                Spacer(Modifier.height(7.dp))
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(Color(0xffc9bfc4)))
            }
            Spacer(Modifier.height(10.dp))
            Action(stamp(draft.anchor, "yyyy年M月d日(E)") + " ⌄", "日付を選ぶ", { pickingDate = !pickingDate })
            if (pickingDate) AndroidView(factory = { ctx ->
                CalendarView(ctx).apply {
                    date = draft.anchor
                    setOnDateChangeListener { _, y, m, d ->
                        draft.anchor = Calendar.getInstance().apply {
                            timeInMillis = draft.anchor
                            set(y, m, d)
                        }.timeInMillis
                        pickingDate = false
                    }
                }
            }, modifier = Modifier.fillMaxWidth().height(308.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Action("−", "5分戻す", { draft.step(-1) })
                Box(Modifier.pointerInput(draft) {
                    var travel = 0f
                    val threshold = 18.dp.toPx()
                    detectVerticalDragGestures(onDragStart = { travel = 0f }) { change, dy ->
                        change.consume()
                        travel += dy
                        while (kotlin.math.abs(travel) >= threshold) {
                            val direction = if (travel < 0) 1 else -1
                            draft.step(direction)
                            travel += threshold * direction
                        }
                    }
                }) { Label(stamp(draft.anchor, "HH:mm"), if (compact) 36 else 46) }
                Action("＋", "5分進める", { draft.step(1) })
            }
            Row {
                Action("始めた時間", "始めた時間", { draft.startsHere = true }, draft.startsHere)
                Action("終わった時間", "終わった時間", { draft.startsHere = false }, !draft.startsHere)
            }
            Row(Modifier.widthIn(max = 400.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Label("所要時間")
                AndroidView(factory = { ctx -> NumberPicker(ctx).apply {
                    minValue = 0
                    maxValue = durations.lastIndex
                    wrapSelectorWheel = false
                    displayedValues = durations.map { "${it}分" }.toTypedArray()
                    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
                    contentDescription = "所要時間"
                    setOnValueChangedListener { _, _, index -> draft.minutes = durations[index] }
                } }, update = { it.value = durations.indexOf(draft.minutes) }, modifier = Modifier.width(116.dp).height(106.dp))
            }
            Row(Modifier.widthIn(max = 400.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                val pattern = if (stamp(draft.start, "yyyyMMdd") == stamp(draft.end, "yyyyMMdd")) "HH:mm" else "M/d HH:mm"
                Label(stamp(draft.start, pattern) + " ～ " + stamp(draft.end, pattern), 13)
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(accent)
                    .clickable(enabled = !draft.saving && draft.title.isNotBlank(), role = Role.Button, onClick = save)
                    .padding(vertical = 12.dp, horizontal = 18.dp)) { Label("記録する", 15, Color.White) }
            }
            if (draft.message.isNotEmpty()) Box(Modifier.fillMaxWidth().padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite }) { Label(draft.message, 13) }
        }
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (compact) Action("×", "常駐を終了", close, true) else Spacer(Modifier.width(1.dp))
            Box(Modifier.size(if (compact) 48.dp else 56.dp).clip(CircleShape).background(if (compact) soft else accent)
                .clickable(role = Role.Button, onClick = fold).semantics { contentDescription = "待機タイルへ" },
                contentAlignment = Alignment.Center) {
                if (compact) Label("−", 22) else Image(painterResource(R.drawable.round_logo), null, Modifier.fillMaxSize())
            }
        }
    }
}
