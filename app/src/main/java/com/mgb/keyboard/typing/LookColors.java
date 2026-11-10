package com.mgb.keyboard.typing;

/** v0.1.5 Jabra look colours (pure Java, no Android). ARGB ints. */
public final class LookColors {
    private LookColors() { }

    /** Function-key fill per theme index (0 Classic, 1 Blue, 2 Mint, 3 Sand, 4 Dark; v0.2.4: 5..17, v0.2.6: 18..19). Unknown index -> Classic value. */
    public static int funcFill(int themeIndex) {
        switch (themeIndex) {
            case 1: return 0xFFB5CFE8;
            case 2: return 0xFFB4DCC8;
            case 3: return 0xFFE0D2B8;
            case 4: return 0xFF2B2D31;
            case 5: return 0xFFD2C6AB;   // v0.2.4 new themes 5..17 (same order as JabraThemes.ALL)
            case 6: return 0xFF24302B;
            case 7: return 0xFFE2DDD0;
            case 8: return 0xFF16161A;
            case 9: return 0xFF242424;
            case 10: return 0xFF16213A;
            case 11: return 0xFFAEBFE0;
            case 12: return 0xFF1556A0;
            case 13: return 0xFF14352C;
            case 14: return 0xFF9FDCCD;
            case 15: return 0xFFE9B3C5;
            case 16: return 0xFFA0A5AE;
            case 17: return 0xFF332B52;
            case 18: return 0xFF4A2C80;   // v0.2.6 gradient themes 18..19
            case 19: return 0xFFFFC9BA;
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
