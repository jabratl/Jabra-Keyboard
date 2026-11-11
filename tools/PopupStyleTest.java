import com.mgb.keyboard.typing.PopupStyle;

public class PopupStyleTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object got, Object want) {
        boolean same = (got == null) ? want == null : got.equals(want);
        if (same) ok++; else { bad++; System.out.println("FAIL " + n + ": got " + got + " want " + want); }
    }
    static void t(String n, boolean c) { eq(n, c, true); }
    public static void main(String[] a) {
        float d = 2f;   // xhdpi
        // width / height
        eq("w key 40dp", PopupStyle.width(80, d), 108);          // 80 + 28
        eq("w min 44dp", PopupStyle.width(10, d), 88);           // 10+28=38 < 88
        eq("w d1", PopupStyle.width(40, 1f), 54);
        eq("h key 48dp", PopupStyle.height(96, d), 130);         // 96*1.35 = 129.6
        eq("h min 56dp", PopupStyle.height(20, d), 112);
        eq("h d1", PopupStyle.height(48, 1f), 65);               // 64.8
        t("h taller than key", PopupStyle.height(96, d) > 96);
        t("w wider than key", PopupStyle.width(80, d) > 80);
        eq("radius 14dp d2", PopupStyle.radiusPx(d), 28f);
        eq("radius 14dp d3", PopupStyle.radiusPx(3f), 42f);
        // text size
        eq("text min", PopupStyle.textSp(40, d), 24f);           // 20dp*0.6=12 -> 24
        t("text mid", Math.abs(PopupStyle.textSp(96, d) - 28.8f) < 0.01f);   // 48dp*0.6
        eq("text max", PopupStyle.textSp(300, d), 32f);
        eq("text d0 safe", PopupStyle.textSp(100, 0f), 24f);
        // lighten
        eq("lighten 0", PopupStyle.lighten(0xFF102030, 0f), 0xFF102030);
        eq("lighten 1", PopupStyle.lighten(0xFF102030, 1f), 0xFFFFFFFF);
        eq("lighten white stays", PopupStyle.lighten(0xFFFFFFFF, 0.1f), 0xFFFFFFFF);
        eq("lighten black .1", PopupStyle.lighten(0xFF000000, 0.1f), 0xFF1A1A1A);   // 25.5 -> 26
        eq("lighten forces opaque", PopupStyle.lighten(0x00000000, 0.1f) >>> 24, 0xFF);
        eq("lighten clamp neg", PopupStyle.lighten(0xFF102030, -5f), 0xFF102030);
        eq("lighten clamp big", PopupStyle.lighten(0xFF102030, 9f), 0xFFFFFFFF);
        // fill: Classic = white, themed = key lighter
        eq("fill classic white", PopupStyle.fill(null), 0xFFFFFFFF);
        eq("fill dark theme", PopupStyle.fill(Integer.valueOf(0xFF3A3C42)), PopupStyle.lighten(0xFF3A3C42, 0.1f));
        t("fill dark theme lighter", PopupStyle.lum(PopupStyle.fill(Integer.valueOf(0xFF3A3C42))) > PopupStyle.lum(0xFF3A3C42));
        eq("fill opaque", PopupStyle.fill(Integer.valueOf(0x403A3C42)) >>> 24, 0xFF);
        eq("fill white key", PopupStyle.fill(Integer.valueOf(0xFFFFFFFF)), 0xFFFFFFFF);
        // text colour
        eq("text classic dark", PopupStyle.text(null, PopupStyle.fill(null)), 0xFF222222);
        eq("text on dark fill, theme text ok", PopupStyle.text(Integer.valueOf(0xFFF2F2F2), PopupStyle.fill(Integer.valueOf(0xFF3A3C42))), 0xFFF2F2F2);
        eq("text null on dark = light", PopupStyle.text(null, PopupStyle.fill(Integer.valueOf(0xFF3A3C42))), 0xFFF2F2F2);
        eq("text null on light = dark", PopupStyle.text(null, 0xFFFFFFFF), 0xFF222222);
        eq("text unreadable theme text replaced", PopupStyle.text(Integer.valueOf(0xFF3A3C42), 0xFF404040), 0xFFF2F2F2);
        eq("text blue theme (1B3A57 on white)", PopupStyle.text(Integer.valueOf(0xFF1B3A57), PopupStyle.fill(Integer.valueOf(0xFFFFFFFF))), 0xFF1B3A57);
        eq("text alpha forced opaque", PopupStyle.text(Integer.valueOf(0x001B3A57), 0xFFFFFFFF) >>> 24, 0xFF);
        // stroke
        eq("stroke classic grey", PopupStyle.stroke(null, 0xFFFFFFFF), 0xFF888888);
        t("stroke themed darker", PopupStyle.lum(PopupStyle.stroke(Integer.valueOf(0xFF3A3C42), 0xFF4A4C52)) < PopupStyle.lum(0xFF4A4C52));
        // lum
        eq("lum black", PopupStyle.lum(0xFF000000), 0f);
        t("lum white ~1", Math.abs(PopupStyle.lum(0xFFFFFFFF) - 1f) < 0.001f);
        // position: q / p / edge clamp, above the key
        int sw = 720, kw = 72;
        int w = PopupStyle.width(kw, d), h = PopupStyle.height(96, d);
        eq("x q (left edge) clamp", PopupStyle.x(2, kw, w, sw), 0);
        eq("x p (right edge) clamp", PopupStyle.x(sw - kw - 2, kw, w, sw), sw - w);
        eq("x middle centred", PopupStyle.x(300, kw, w, sw), 300 + kw / 2 - w / 2);
        for (int left = 0; left <= sw - kw; left += 8) {
            int x = PopupStyle.x(left, kw, w, sw);
            if (x < 0 || x + w > sw) { bad++; System.out.println("FAIL x off screen at " + left); } else ok++;
        }
        eq("y above key", PopupStyle.y(1000, h, d), 1000 - h - 8);
        eq("y never negative", PopupStyle.y(50, h, d), 0);
        t("bubble bottom above key top", PopupStyle.y(1000, h, d) + h <= 1000);
        // release rule (fast typing)
        Object A = new Object(), B = new Object();
        t("release: nobody owns -> hide", PopupStyle.releaseHides(null, A));
        t("release: owner lifts -> hide", PopupStyle.releaseHides(A, A));
        t("release: other key lifts -> keep", !PopupStyle.releaseHides(B, A));
        t("release: null key, owner set -> keep", !PopupStyle.releaseHides(B, null));
        t("release: both null -> hide", PopupStyle.releaseHides(null, null));
        System.out.println("PopupStyleTest ok=" + ok + " bad=" + bad);
        if (bad > 0) System.exit(1);
    }
}
