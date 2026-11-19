package com.mgb.keyboard

import android.os.SystemClock
import android.view.View
import android.widget.Button
import com.mgb.keyboard.typing.ClipPop
import com.mgb.keyboard.typing.ReplaceEngine
import com.mgb.keyboard.typing.ReplaceOpts

/**
 * v0.42.1 (M1 of PLAN_RP_MINIMAL): the slim "Options" button + its floating menu for the Find & Replace panel (look of docs/Replace_Tool_Minimal_Mock.html).
 * Only used while pref rp_slim is ON (default OFF). Rows come from the pure model typing/ReplaceOpts.java; the window is ClipPopover (same as the clipboard menu).
 * Tick rows keep the menu open: the state changes, the panel refreshes (new Options button), and the menu is shown again on the new button (no fade = no blink).
 * Any problem returns null / calls onFail: the panel then uses the OLD option chips. Delete this file + the lines marked "v0.42.1" = back to v0.42.0.
 * NOT tested on a phone (Kotlin not compiled here).
 */
object ReplaceSlim {
    private var reopenAt = 0L   // uptime of the last tick; 0 = nothing pending

    fun on(p: JabraPrefs): Boolean = try { p.bool("rp_slim", false) } catch (_: Throwable) { false }

    /** True once after a tick: the freshly built panel shows the menu again. */
    private fun takeReopen(): Boolean { val r = reopenAt > 0L && SystemClock.uptimeMillis() - reopenAt < 1500L; reopenAt = 0L; return r }   // a stale flag (panel did not rebuild) never re-opens later

    /** The panel is built without a pending re-show: close a menu that may still be open from the previous view. */
    fun closeIfIdle() { if (reopenAt <= 0L) { try { ClipPopover.dismiss() } catch (_: Throwable) { } } }

    /**
     * The Options button. onChange = the panel's optChanged() (save + refresh); onNever = open the never-change page; onFail = the menu could not open (use the old chips).
     * Returns null on any problem (the caller then builds the old chip).
     */
    fun optionsButton(
        ctx: android.content.Context, p: JabraPrefs, host: JabraHost, opt: ReplaceEngine.Opt, protectCount: Int,
        onChange: () -> Unit, onNever: () -> Unit, onFail: () -> Unit
    ): Button? {
        return try {
            val b = UiKit.chip(ctx, p, ReplaceOpts.buttonLabel(opt, protectCount), false, 34, { v -> host.feedback(v) }) { btn ->
                try {
                    if (ClipPopover.isOpen()) { ClipPopover.dismiss(); return@chip }
                    if (!ClipPop.reopenGuard(SystemClock.uptimeMillis(), ClipPopover.lastDismiss())) return@chip   // the tap that just closed it must not re-open it
                    if (!show(btn, p, host, opt, protectCount, true, onChange, onNever)) onFail()
                } catch (_: Throwable) { onFail() }
            }
            if (takeReopen()) b.post { try { show(b, p, host, opt, protectCount, false, onChange, onNever) } catch (_: Throwable) { } }
            b
        } catch (_: Throwable) { null }
    }

    private fun show(a: View, p: JabraPrefs, host: JabraHost, opt: ReplaceEngine.Opt, protectCount: Int, animate: Boolean, onChange: () -> Unit, onNever: () -> Unit): Boolean {
        return ClipPopover.show(a, p, host, ReplaceOpts.items(opt, protectCount), false, animate, ReplaceOpts.WIDTH_DP) { picked ->
            if (picked.id == ReplaceOpts.NEVER) onNever()
            else if (ReplaceOpts.toggle(opt, picked.id)) { reopenAt = SystemClock.uptimeMillis(); onChange() }
        }
    }
}
