package com.mgb.keyboard.typing;

/**
 * v0.16.15 (E1) pure state machine: "hold the first clip about 1.5 s = select mode starts, then every other clip = one tap".
 * No Android, no clock: the caller passes the time (ms) and the finger position in px.
 * Not wired into any screen yet (E2 does that); nothing here changes the old long-click.
 *
 * One gesture = down() ... up() / cancel(). Results:
 *   START_SELECT = enter select mode with this clip selected (returned ONCE per gesture, by tick() or by a late up()).
 *   TAP_PASTE    = short tap outside select mode (paste the clip).
 *   TAP_TOGGLE   = tap inside select mode (tick / untick this clip; a long hold in select mode is still ONE toggle, never a restart).
 *   NONE         = nothing to do (moved too far, scrolled, cancelled, or the hold already started).
 */
public final class HoldSelect {
    public enum State { IDLE, HOLDING, STARTED, CANCELLED }
    public enum Result { NONE, START_SELECT, TAP_PASTE, TAP_TOGGLE }

    public static final int DEFAULT_MS = 1500;
    public static final int MIN_MS = 600;
    public static final int MAX_MS = 2500;
    public static final float DEFAULT_SLOP = 24f;

    /** 0 / negative (unset or broken) = DEFAULT_MS; otherwise limited to MIN_MS..MAX_MS. */
    public static int sanitize(int ms) {
        if (ms <= 0) return DEFAULT_MS;
        return Math.max(MIN_MS, Math.min(MAX_MS, ms));
    }

    /** true only for a value inside MIN_MS..MAX_MS (E2 uses the old long-click when the pref is not valid). */
    public static boolean isValid(int ms) { return ms >= MIN_MS && ms <= MAX_MS; }

    private final int holdMs;
    private final float slop;
    private State state = State.IDLE;
    private boolean selectMode;
    private long downAt;
    private float downX, downY;

    public HoldSelect() { this(DEFAULT_MS, DEFAULT_SLOP); }

    public HoldSelect(int holdMs, float slopPx) {
        this.holdMs = sanitize(holdMs);
        this.slop = (Float.isNaN(slopPx) || slopPx < 0f) ? DEFAULT_SLOP : Math.min(slopPx, 400f);
    }

    public int holdMs() { return holdMs; }
    public State state() { return state; }

    /** Finger down. A new down always starts a fresh gesture. selectMode = the panel is in select mode now. */
    public Result down(long now, float x, float y, boolean selectMode) {
        this.state = State.HOLDING;
        this.selectMode = selectMode;
        this.downAt = now;
        this.downX = x;
        this.downY = y;
        return Result.NONE;
    }

    /** Finger moved: further than the slop = this is a scroll / drag, the gesture is cancelled (no paste, no select). */
    public Result move(long now, float x, float y) {
        if (state != State.HOLDING) return Result.NONE;
        float dx = x - downX, dy = y - downY;
        if (Math.sqrt((double) dx * dx + (double) dy * dy) > slop) state = State.CANCELLED;
        return Result.NONE;
    }

    /** The list scrolled (parent took over the touch). */
    public Result scroll() {
        if (state == State.HOLDING) state = State.CANCELLED;
        return Result.NONE;
    }

    /** Timer fired (Handler.postDelayed). Starts select mode once when the hold time is reached. Never in select mode. */
    public Result tick(long now) {
        if (state != State.HOLDING || selectMode) return Result.NONE;
        if (elapsed(now) >= holdMs) { state = State.STARTED; return Result.START_SELECT; }
        return Result.NONE;
    }

    /** Finger up. */
    public Result up(long now) {
        State s = state;
        state = State.IDLE;
        if (s != State.HOLDING) return Result.NONE;   // STARTED: the hold already did its job, no paste; CANCELLED / IDLE: nothing
        if (selectMode) return Result.TAP_TOGGLE;
        if (elapsed(now) >= holdMs) return Result.START_SELECT;   // timer was late, the finger waited long enough
        return Result.TAP_PASTE;
    }

    /** ACTION_CANCEL or the view was rebuilt. */
    public Result cancel() {
        state = State.IDLE;
        return Result.NONE;
    }

    private long elapsed(long now) { long d = now - downAt; return d < 0 ? 0 : d; }
}
