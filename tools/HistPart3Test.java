import com.mgb.keyboard.hist.HistExcl;
import com.mgb.keyboard.hist.HistLog;
import com.mgb.keyboard.hist.HistPause;
import com.mgb.keyboard.hist.HistPolicy;
import com.mgb.keyboard.hist.HistUi;
import java.util.Arrays;

/** v0.23.0 (H3) tests: excluded-app list helpers, HistLog.removeApp / countApp, timed pause (computer only). */
public class HistPart3Test {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static final String OWN = "com.mgb.keyboard";

    public static void main(String[] a) {
        // ---- list helpers
        t("parse empty", HistPolicy.parseApps("").isEmpty() && HistPolicy.parseApps(null).isEmpty());
        t("parse mixed separators + dupes", HistPolicy.parseApps("a.b, c.d;e.f\ng.h a.b").equals(Arrays.asList("a.b", "c.d", "e.f", "g.h")));
        t("add to empty", HistPolicy.addApp("", "com.bank", OWN).equals("com.bank"));
        t("add second", HistPolicy.addApp("com.bank", "com.chat", OWN).equals("com.bank,com.chat"));
        t("add twice = once", HistPolicy.addApp("com.bank,com.chat", "com.bank", OWN).equals("com.bank,com.chat"));
        t("add own app ignored", HistPolicy.addApp("com.bank", OWN, OWN).equals("com.bank"));
        t("add blank / null ignored", HistPolicy.addApp("com.bank", "  ", OWN).equals("com.bank") && HistPolicy.addApp("com.bank", null, OWN).equals("com.bank"));
        t("add with space inside ignored", HistPolicy.addApp("com.bank", "a b", OWN).equals("com.bank"));
        t("add normalises old text", HistPolicy.addApp("a.b; c.d", "e.f", OWN).equals("a.b,c.d,e.f"));
        t("remove", HistPolicy.removeApp("a.b,c.d,e.f", "c.d").equals("a.b,e.f"));
        t("remove last = empty", HistPolicy.removeApp("a.b", "a.b").equals(""));
        t("remove unknown = same", HistPolicy.removeApp("a.b,c.d", "x.y").equals("a.b,c.d"));
        t("remove null", HistPolicy.removeApp("a.b", null).equals("a.b"));
        t("added app is listed", HistPolicy.listed(HistPolicy.addApp("", "com.bank", OWN), "com.bank"));
        t("removed app not listed", !HistPolicy.listed(HistPolicy.removeApp("com.bank", "com.bank"), "com.bank"));
        t("decide uses new list", HistPolicy.decide(true, false, true, true, 1, 0, "com.bank", OWN, HistPolicy.addApp("", "com.bank", OWN)) == HistPolicy.APP_OFF);
        t("prefix is not a match", !HistPolicy.listed("com.bank.pro", "com.bank"));

        // ---- HistLog.removeApp / countApp
        HistLog l = new HistLog();
        long t0 = 1000000L;
        l.type("hello", "com.chat", t0); l.closeRun();
        l.type("secret", "com.bank", t0 + 10); l.closeRun();
        l.type("more", "com.chat", t0 + 20); l.closeRun();
        l.type("open run", "com.bank", t0 + 30);
        t("countApp chat", l.countApp("com.chat") == 2);
        t("countApp bank incl open run", l.countApp("com.bank") == 2);
        t("countApp unknown / null / empty", l.countApp("x") == 0 && l.countApp(null) == 0 && l.countApp("") == 0);
        l.markClean();
        int r = l.removeApp("com.bank");
        t("removeApp count", r == 2);
        t("removeApp leaves other app", l.size() == 2 && l.entries().size() == 2 && l.entries().get(0).app.equals("com.chat"));
        t("removeApp marks dirty", l.isDirty());
        l.markClean();
        t("removeApp none = 0, not dirty", l.removeApp("com.bank") == 0 && !l.isDirty());
        t("removeApp null / empty", l.removeApp(null) == 0 && l.removeApp("") == 0);
        l.type("after", "com.chat", t0 + 100);
        t("typing continues after removeApp", l.entries().size() == 3);
        // chars are counted right after removing (clear still works, size ok)
        t("clear after removeApp", l.clear() == 3 && l.size() == 0);

        // ---- timed pause
        long now = 5_000_000L;
        t("not paused", !HistPause.paused(false, 0, now));
        t("flag paused", HistPause.paused(true, 0, now));
        t("timed paused", HistPause.paused(false, now + 1000, now));
        t("timed over = resumed by itself", !HistPause.paused(false, now - 1, now) && !HistPause.paused(false, now, now));
        t("timed() only without flag", HistPause.timed(false, now + 5, now) && !HistPause.timed(true, now + 5, now) && !HistPause.timed(false, 0, now));
        t("tap while saving = pause 1 hour (default)", HistPause.tapUntil(false, 0, now, HistPause.DEF_LEVEL) == now + HistPause.HOUR_MS);
        t("tap picks 15 min / 1 day", HistPause.tapUntil(false, 0, now, 0) == now + 15 * 60000L && HistPause.tapUntil(false, 0, now, 2) == now + 86400000L);
        t("bad level = default", HistPause.level(-1) == 1 && HistPause.level(9) == 1 && HistPause.ms(7) == HistPause.HOUR_MS);
        t("names", HistPause.name(0).equals("15 min") && HistPause.name(1).equals("1 hour") && HistPause.name(2).equals("1 day") && HistPause.NAMES.length == HistPause.DURS.length);
        t("tap while timed paused = resume", HistPause.tapUntil(false, now + 1000, now, 2) == 0L);
        t("tap while flag paused = resume", HistPause.tapUntil(true, 0, now, 2) == 0L);
        t("tap after expiry = pause again", HistPause.tapUntil(false, now - 5, now, 1) == now + HistPause.HOUR_MS);
        t("left 42 min", HistPause.left(now + 42 * 60000L, now).equals("42 min left"));
        t("left rounds up", HistPause.left(now + 60001L, now).equals("2 min left") && HistPause.left(now + 1L, now).equals("1 min left"));
        t("left hours", HistPause.left(now + 80 * 60000L, now).equals("1 h 20 min left") && HistPause.left(now + 120 * 60000L, now).equals("2 h left"));
        t("left days", HistPause.left(now + 86400000L, now).equals("1 day left") && HistPause.left(now + 27 * 3600000L, now).equals("1 day 3 h left") && HistPause.left(now + 50 * 3600000L, now).startsWith("2 days"));
        t("left never negative", HistPause.left(now - 5, now).equals("0 min left"));
        t("line none", HistPause.line(false, 0, now).isEmpty());
        t("line flag", HistPause.line(true, 0, now).startsWith("Paused until"));
        t("line timed", HistPause.line(false, now + 30 * 60000L, now).contains("30 min left"));
        t("line timed 1 day", HistPause.line(false, now + 86400000L, now).contains("1 day left"));
        t("keyLabel 3 states", !HistPause.keyLabel(true, false).equals(HistPause.keyLabel(true, true)) && !HistPause.keyLabel(false, false).equals(HistPause.keyLabel(true, false)));
        t("tip off", HistPause.tip(false, false, 0, now, HistPolicy.OFF).contains("OFF"));
        t("tip paused flag", HistPause.tip(true, true, 0, now, HistPolicy.PAUSED).contains("resume"));
        t("tip paused timed", HistPause.tip(true, false, now + 120000L, now, HistPolicy.PAUSED).contains("2 min left"));
        t("tip saving", HistPause.tip(true, false, 0, now, HistPolicy.OK).contains("saving"));
        t("pick title", HistPause.pickTitle().contains("Pause"));
        t("tip password reason", HistPause.tip(true, false, 0, now, HistPolicy.PASSWORD).startsWith("Password field"));
        t("tip app excluded reason", HistPause.tip(true, false, 0, now, HistPolicy.APP_OFF).startsWith("This app is excluded"));

        // ---- texts
        t("menu title falls back", HistExcl.menuTitle("").equals("This entry") && HistExcl.menuTitle("Chat").equals("Chat"));
        t("exclude item has the app name", HistExcl.excludeItem("Chat").contains("Chat") && HistExcl.excludeItem(null).contains("this app"));
        t("ask body 0 / 1 / many", !HistExcl.askBody(0).contains("Delete") && HistExcl.askBody(1).contains("1 entry") && HistExcl.askBody(5).contains("5 entries"));
        t("delete label", HistExcl.deleteThem(1).equals("Delete 1 entry") && HistExcl.deleteThem(3).equals("Delete 3 entries"));
        t("done toast", HistExcl.doneToast(0).equals("App excluded") && HistExcl.doneToast(2).contains("2 entries") && HistExcl.doneToast(1).contains("1 entry deleted"));
        t("section title", HistExcl.sectionTitle(3).contains("3"));
        t("hint keeps Tap and Hold", HistExcl.hintLine().contains("Tap") && HistExcl.hintLine().contains("Hold"));

        // ---- HistUi
        t("hero titles", HistUi.heroTitle(false, false).contains("OFF") && HistUi.heroTitle(true, false).contains("ON") && HistUi.heroTitle(true, true).contains("paused"));
        t("hero state", HistUi.state(false, true) == 0 && HistUi.state(true, false) == 1 && HistUi.state(true, true) == 2);
        t("hero sub on", HistUi.heroSub(true, 2, 5).equals("5 entries \u00B7 kept 7 days") && HistUi.heroSub(true, 0, 1).startsWith("1 entry "));
        t("hero sub off", HistUi.heroSub(false, 2, 0).contains("Nothing is saved") && HistUi.heroSub(false, 2, 3).contains("3 entries still stored"));
        t("pause labels", HistUi.pauseNow("1 day").equals("Pause for 1 day") && !HistUi.resumeNow().isEmpty());
        t("badge", HistUi.badge("whatsapp").equals("W") && HistUi.badge("").equals("?") && HistUi.badge(null).equals("?") && HistUi.badge("  chat").equals("C"));

        System.out.println("HistPart3Test: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
