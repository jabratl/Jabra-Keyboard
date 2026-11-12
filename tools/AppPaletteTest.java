import com.mgb.keyboard.typing.AppPalette;

/** v0.10.9 (D1) AppPalette tests (computer only). */
public class AppPaletteTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }

    public static void main(String[] a) {
        // mode logic
        t("sanitize bad -> light", AppPalette.sanitize(7) == AppPalette.LIGHT && AppPalette.sanitize(-1) == AppPalette.LIGHT);
        t("light ignores phone dark", !AppPalette.isDark(AppPalette.LIGHT, true));
        t("dark ignores phone light", AppPalette.isDark(AppPalette.DARK, false));
        t("auto follows phone dark", AppPalette.isDark(AppPalette.AUTO, true));
        t("auto follows phone light", !AppPalette.isDark(AppPalette.AUTO, false));
        t("bad mode = light", !AppPalette.isDark(99, true));
        t("next cycle", AppPalette.next(AppPalette.LIGHT) == AppPalette.DARK && AppPalette.next(AppPalette.DARK) == AppPalette.AUTO
                && AppPalette.next(AppPalette.AUTO) == AppPalette.LIGHT);
        t("next bad -> dark (from light)", AppPalette.next(42) == AppPalette.DARK);
        t("names", "Light".equals(AppPalette.name(1)) && "Dark".equals(AppPalette.name(2)) && AppPalette.name(0).startsWith("Auto") && "Light".equals(AppPalette.name(9)));
        // contrast helper
        t("contrast black/white = 21", Math.abs(AppPalette.contrast(0xFF000000, 0xFFFFFFFF) - 21.0) < 0.01);
        t("contrast same = 1", Math.abs(AppPalette.contrast(0xFF123456, 0xFF123456) - 1.0) < 0.001);
        t("contrast symmetric", Math.abs(AppPalette.contrast(0xFF336699, 0xFFFFFFFF) - AppPalette.contrast(0xFFFFFFFF, 0xFF336699)) < 1e-9);
        // every text colour on bg and card, both modes
        for (int i = 0; i < 2; i++) {
            boolean d = i == 1;
            String m = d ? "dark " : "light ";
            int[] surfaces = { AppPalette.bg(d), AppPalette.card(d) };
            String[] sn = { "bg", "card" };
            for (int s = 0; s < 2; s++) {
                t(m + "text on " + sn[s] + " >= 4.5", AppPalette.contrast(AppPalette.text(d), surfaces[s]) >= 4.5);
                t(m + "dim on " + sn[s] + " >= 4.5", AppPalette.contrast(AppPalette.dim(d), surfaces[s]) >= 4.5);
                t(m + "ok on " + sn[s] + " >= 4.5", AppPalette.contrast(AppPalette.ok(d), surfaces[s]) >= 4.5);
                t(m + "accent on " + sn[s] + " >= 4.5", AppPalette.contrast(AppPalette.accent(d), surfaces[s]) >= 4.5);
                // light warning orange darkened #D84315 -> #BF360C (4.44 -> 5.6 on white)
                t(m + "warn on " + sn[s] + " >= 4.4", AppPalette.contrast(AppPalette.warn(d), surfaces[s]) >= 4.4);
            }
            t(m + "card differs from bg", AppPalette.card(d) != AppPalette.bg(d));
        }
        t("light bg white", AppPalette.bg(false) == 0xFFFFFFFF);
        t("dark bg near black", AppPalette.bg(true) == 0xFF121316);
        t("opaque colours", (AppPalette.text(true) >>> 24) == 0xFF && (AppPalette.warn(false) >>> 24) == 0xFF);
        System.out.println("AppPaletteTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
