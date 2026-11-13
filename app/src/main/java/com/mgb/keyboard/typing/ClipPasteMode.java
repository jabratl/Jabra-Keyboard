package com.mgb.keyboard.typing;

/**
 * v0.17.40 (M7): plain or with format? Pure Java, no Android (tools/ClipPasteModeTest.java).
 * Pref clip_fmt_tap (bool, default true) = a tap on the card body keeps the format. The small "Aa" tag does the OTHER choice.
 * A clip without a stored format is always pasted plain (and shows no tag).
 */
public final class ClipPasteMode {
    private ClipPasteMode() { }

    public static final int PLAIN = 0, FORMAT = 1;

    public static int decide(boolean keepFormatOnTap, boolean badgeTapped, boolean hasFmt) {
        if (!hasFmt) return PLAIN;
        boolean wantFormat = badgeTapped ? !keepFormatOnTap : keepFormatOnTap;
        return wantFormat ? FORMAT : PLAIN;
    }
}
