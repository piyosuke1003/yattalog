package jp.personal.yattalog

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.*
import androidx.savedstate.*
import java.util.Calendar
import java.util.concurrent.Executors

internal class FormDraft {
    var title by mutableStateOf("")
    var anchor by mutableLongStateOf(now())
    var minutes by mutableIntStateOf(15)
    var startsHere by mutableStateOf(false)
    var message by mutableStateOf("")
    var saving by mutableStateOf(false)
    val start: Long get() = if (startsHere) anchor else anchor - minutes * 60_000L
    val end: Long get() = start + minutes * 60_000L

    fun step(direction: Int) {
        val minute = Calendar.getInstance().apply { timeInMillis = anchor }.get(Calendar.MINUTE)
        val rest = minute % 5
        val change = if (direction > 0) 5 - rest else if (rest == 0) -5 else -rest
        anchor += change * 60_000L
    }

    companion object {
        fun now(): Long = Calendar.getInstance().apply {
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

// A service window has no Activity view tree. Give each mounted composition
// a lifecycle ending exactly when that host releases it.
private class FormOwner : SavedStateRegistryOwner {
    private val events = LifecycleRegistry(this)
    private val state = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = events
    override val savedStateRegistry: SavedStateRegistry get() = state.savedStateRegistry
    init {
        state.performAttach()
        state.performRestore(null)
        events.currentState = Lifecycle.State.RESUMED
    }
    fun close() { events.currentState = Lifecycle.State.DESTROYED }
}

internal class LogForm private constructor(context: Context) {
    @JvmField var activity: MainActivity? = null
    private val writer = CalendarWriter(context)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val draft = FormDraft()
    private var view: ComposeView? = null
    private var owner: FormOwner? = null
    private var floating = false

    fun attach(target: Context, host: FrameLayout, compact: Boolean) {
        if (view?.parent === host) return
        unmount()
        activity = target as? MainActivity
        floating = compact
        val next = ComposeView(target)
        if (compact) {
            val tree = FormOwner()
            owner = tree
            host.setViewTreeLifecycleOwner(tree)
            host.setViewTreeSavedStateRegistryOwner(tree)
        }
        next.setContent {
            EntryForm(draft, compact,
                fold = { if (compact) OverlayService.foldPanel() else activity?.beginFloating() },
                close = { OverlayService.exitToApp() },
                save = ::save)
        }
        view = next
        host.addView(next, FrameLayout.LayoutParams(-1, -1))
    }

    private fun unmount() {
        view?.let {
            it.disposeComposition()
            (it.parent as? FrameLayout)?.removeView(it)
        }
        owner?.close()
        view = null
        owner = null
        activity = null
        floating = false
    }
    fun releaseActivity(old: MainActivity) { if (activity === old) unmount() }
    fun releaseOverlay() { if (floating) unmount() }

    private fun save() {
        val title = draft.title.trim()
        if (draft.saving || title.isEmpty()) return
        val start = draft.start
        val end = draft.end
        draft.saving = true
        draft.message = ""
        val action = Runnable {
            worker.execute {
                try {
                    writer.insert(title, start, end)
                    main.post { result(true, "記録しました") }
                } catch (e: Exception) {
                    val message = (e as? IllegalArgumentException)?.message
                        ?: "保存できませんでした。カレンダーの同期と権限を確認してください。"
                    main.post { result(false, message) }
                }
            }
        }
        activity?.withCalendar(action) ?: action.run()
    }

    fun result(ok: Boolean, message: String) {
        draft.saving = false
        draft.message = message
        if (ok) {
            draft.title = ""
            draft.anchor = FormDraft.now()
            if (floating) OverlayService.foldPanel()
        }
    }
    companion object {
        private var instance: LogForm? = null
        @JvmStatic fun get(context: Context): LogForm =
            instance ?: LogForm(context.applicationContext).also { instance = it }
    }
}
