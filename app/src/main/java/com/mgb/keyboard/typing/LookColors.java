package com.mgb.keyboard.typing;

/** v0.1.5 Jabra look colours (pure Java, no Android). ARGB ints. */
public final class LookColors {
    private LookColors() { }

    /** Function-key fill per theme index (0 Classic, 1 Blue, 2 Mint, 3 Sand, 4 Dark). Unknown index -> Classic value. */
    public static int funcFill(int themeIndex) {
        switch (themeIndex) {
            case 1: return 0xFFB5CFE8;
            case 2: return 0xFFB4DCC8;
            case 3: return 0xFFE0D2B8;
            case 4: return 0xFF2B2D31;
            default: return 0xFFC3C9D0;
        }
    }

    /** Darker colour for the pressed state: each RGB channel * (1 - f), alpha kept. f is clamped to 0..1. */
    public static int darken(int argb, float f) {
        float k = 1f - (f < 0f ? 0f : (f > 1f ? 1f : f));
        int a = (argb >>> 24) & 0xFF;
        int r = Math.round(((argb >> 16) & 0xFF) * k);
        int g = Math.round(((argb >> 8) & 0xFF) * k);
        int b = Math.round((argb & 0xFF) * k);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
