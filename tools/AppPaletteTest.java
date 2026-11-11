import com.mgb.keyboard.typing.AppPalette;

/** v0.10.9 (D1) AppPalette tests (computer only). */
public class AppPaletteTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }
    static void c(String name, int fg, int bg, double min) {
        double r = AppPalette.contrast(fg, bg);
        t(name + " (" + String.format("%.2f", r) + " >= " + min + ")", r >= min);
    }

    public static void main(String[] a) {
        // mode pick
        t("light mode = light", !AppPalette.pick(1, false) && !AppPalette.pick(1, true));
        t("dark mode = dark", AppPalette.pick(2, false) && AppPalette.pick(2, true));
        t("auto follows phone dark", AppPalette.pick(0, true));
        t("auto follows phone light", !AppPalette.pick(0, false));
        t("bad mode 3 = light", !AppPalette.pick(3, true));
        t("bad mode -1 = light", !AppPalette.pick(-1, true));
        t("bad mode 99 = light", !AppPalette.pick(99, true));
        t("normalize", AppPalette.normalize(0) == 0 && AppPalette.normalize(1) == 1 && AppPalette.normalize(2) == 2 && AppPalette.normalize(7) == 1);
        t("default pref value 1 = today's look (light)", !AppPalette.pick(1, true));
        t("next cycle", AppPalette.next(1) == 2 && AppPalette.next(2) == 0 && AppPalette.next(0) == 1);
        t("next bad", AppPalette.next(42) == 2);
        t("names", AppPalette.name(1).equals("Light") && AppPalette.name(2).equals("Dark") && AppPalette.name(0).startsWith("Auto") && AppPalette.name(9).equals("Light"));
        // dark table = handoff values
        t("dark bg", AppPalette.bg(true) == 0xFF121316);
        t("dark card", AppPalette.card(true) == 0xFF1E1F22);
        t("dark text", AppPalette.text(true) == 0xFFF2F2F2);
        t("dark dim", AppPalette.dim(true) == 0xFFB8B8B8);
        t("dark warn", AppPalette.warn(true) == 0xFFFF8A65);
        t("accent = caps lock blue", AppPalette.accent() == 0xFF2271B1);
        t("light warn unchanged", AppPalette.warn(false) == 0xFFD84315);
        // contrast helper sanity
        t("contrast black/white 21", Math.abs(AppPalette.contrast(0xFF000000, 0xFFFFFFFF) - 21.0) < 0.01);
        t("contrast same = 1", Math.abs(AppPalette.contrast(0xFF123456, 0xFF123456) - 1.0) < 1e-9);
        t("contrast symmetric", Math.abs(AppPalette.contrast(0xFF2271B1, 0xFFFFFFFF) - AppPalette.contrast(0xFFFFFFFF, 0xFF2271B1)) < 1e-9);
        t("contrast ignores alpha byte", Math.abs(AppPalette.contrast(0x00F2F2F2, 0xFF121316) - AppPalette.contrast(0xFFF2F2F2, 0xFF121316)) < 1e-9);
        // every text / background pair, both modes
        for (int d = 0; d < 2; d++) {
            boolean dark = d == 1; String m = dark ? "dark " : "light ";
            c(m + "text on bg", AppPalette.text(dark), AppPalette.bg(dark), AppPalette.MIN_CONTRAST);
            c(m + "text on card", AppPalette.text(dark), AppPalette.card(dark), AppPalette.MIN_CONTRAST);
            c(m + "dim on bg", AppPalette.dim(dark), AppPalette.bg(dark), AppPalette.MIN_CONTRAST);
            c(m + "dim on card", AppPalette.dim(dark), AppPalette.card(dark), AppPalette.MIN_CONTRAST);
            c(m + "ok on bg", AppPalette.ok(dark), AppPalette.bg(dark), AppPalette.MIN_CONTRAST);
            c(m + "ok on card", AppPalette.ok(dark), AppPalette.card(dark), AppPalette.MIN_CONTRAST);
            c(m + "onAccent on accent", AppPalette.onAccent(), AppPalette.accent(), AppPalette.MIN_CONTRAST);
        }
        c("dark warn on bg", AppPalette.warn(true), AppPalette.bg(true), AppPalette.MIN_CONTRAST);
        c("dark warn on card", AppPalette.warn(true), AppPalette.card(true), AppPalette.MIN_CONTRAST);
        // KNOWN: the existing warning orange #D84315 on white is 4.44:1 (kept on purpose: design rule "warning orange #D84315", light must stay as today)
        c("light warn on bg (existing colour, 4.4 floor)", AppPalette.warn(false), AppPalette.bg(false), 4.4);
        // old light help colour was worse: proves the change is an improvement
        t("old help #888888 on white was below 4.5", AppPalette.contrast(0xFF888888, 0xFFFFFFFF) < 4.5);
        t("new light dim better than old", AppPalette.contrast(AppPalette.dim(false), 0xFFFFFFFF) > AppPalette.contrast(0xFF888888, 0xFFFFFFFF));
        // dark surfaces are dark, light surfaces are light
        t("dark bg luminance low", AppPalette.luminance(AppPalette.bg(true)) < 0.02);
        t("dark card lighter than bg", AppPalette.luminance(AppPalette.card(true)) > AppPalette.luminance(AppPalette.bg(true)));
        t("light bg luminance 1", Math.abs(AppPalette.luminance(AppPalette.bg(false)) - 1.0) < 1e-9);
        System.out.println("AppPaletteTest: " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
