import com.mgb.keyboard.hist.HistDays;
import com.mgb.keyboard.hist.HistLog;
import java.util.List;
import java.util.TimeZone;

/** v0.21.0 (H2) HistDays + HistLog.restore tests (computer only). */
public class HistDaysTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }

    static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    static final TimeZone BD = TimeZone.getTimeZone("GMT+06:00");
    static final long H = 3600000L, D = 86400000L;
    // 2026-11-16 12:00 UTC
    static final long NOW = 1794830400000L;

    static HistLog.Entry one(String text, String app, long time) {
        HistLog l = new HistLog();
        l.type(text, app, time);
        l.closeRun();
        return l.entries().get(0);
    }

    public static void main(String[] a) {
        t("now is 2026-11-16 12:00 UTC", HistDays.timeText(NOW, UTC).equals("12:00") && HistDays.exportName(NOW, UTC).equals("jabra_typing_history_2026-11-16_1200.txt"));
        t("day today", HistDays.dayLabel(NOW - 3 * H, NOW, UTC).equals("Today"));
        t("day yesterday", HistDays.dayLabel(NOW - 13 * H, NOW, UTC).equals("Yesterday"));
        t("day date", HistDays.dayLabel(NOW - 5 * D, NOW, UTC).equals("Wed 11 Nov 2026"));
        t("midnight edge UTC", HistDays.dayLabel(NOW - 12 * H, NOW, UTC).equals("Today") && HistDays.dayLabel(NOW - 12 * H - 1, NOW, UTC).equals("Yesterday"));
        // 18:30 UTC = 00:30 next day in Bangladesh (+6)
        long t1830 = NOW + 6 * H + 30 * 60000L;
        t("time zone moves the day", HistDays.dayNumber(t1830, UTC) != HistDays.dayNumber(t1830, BD) && HistDays.timeText(t1830, BD).equals("00:30"));
        t("before 1970 does not crash", HistDays.dayNumber(-1L, UTC) == -1L && HistDays.dayNumber(0L, UTC) == 0L);

        t("clean pkg", HistDays.cleanPackage("com.whatsapp").equals("Whatsapp") && HistDays.cleanPackage("com.google.android.apps.messaging").equals("Messaging"));
        t("clean pkg odd", HistDays.cleanPackage("").equals("Unknown app") && HistDays.cleanPackage(null).equals("Unknown app") && HistDays.cleanPackage("termux").equals("Termux") && HistDays.cleanPackage("a.b.my_app").equals("My app") && HistDays.cleanPackage("x.").equals("X."));
        HistDays.Labeler lb = p -> p.equals("org.telegram.messenger") ? "Telegram" : null;
        t("app name label", HistDays.appName("org.telegram.messenger", lb).equals("Telegram"));
        t("app name fallback", HistDays.appName("com.whatsapp", lb).equals("Whatsapp") && HistDays.appName("com.whatsapp", null).equals("Whatsapp"));
        HistDays.Labeler boom = p -> { throw new IllegalStateException("x"); };
        t("labeler that throws", HistDays.appName("com.whatsapp", boom).equals("Whatsapp"));

        HistLog.Entry e1 = one("Ami Bhalo Achi", "com.whatsapp", NOW - 2 * H);
        t("match empty", HistDays.matches(e1, "", null) && HistDays.matches(e1, null, null) && HistDays.matches(e1, "   ", null));
        t("match text ci", HistDays.matches(e1, "bhalo", null) && HistDays.matches(e1, "BHALO achi", null) && !HistDays.matches(e1, "bhalo kharap", null));
        t("match package", HistDays.matches(e1, "whats", null) && HistDays.matches(e1, "com.whatsapp", null));
        t("match label", HistDays.matches(one("hi", "org.telegram.messenger", NOW), "telegram", lb));
        t("match text + app", HistDays.matches(e1, "achi whatsapp", null));
        t("match null entry", !HistDays.matches(null, "x", null));
        t("match bangla", HistDays.matches(one("\u0986\u09AE\u09BF \u09AD\u09BE\u09B2\u09CB", "x.y", NOW), "\u09AD\u09BE\u09B2\u09CB", null));

        // grouping
        HistLog l = new HistLog();
        l.type("old one", "a.app", NOW - 5 * D); l.closeRun();
        l.type("yesterday one", "a.app", NOW - 13 * H); l.closeRun();
        l.type("today one", "b.app", NOW - 3 * H); l.closeRun();
        l.type("today two", "b.app", NOW - 1 * H); l.closeRun();
        List<HistLog.Entry> all = l.entries();
        List<HistDays.Day> days = HistDays.group(all, NOW, UTC, "", null);
        t("3 days", days.size() == 3);
        t("order newest first", days.get(0).label.equals("Today") && days.get(1).label.equals("Yesterday") && days.get(2).label.equals("Wed 11 Nov 2026"));
        t("entry order newest first", days.get(0).items.get(0).text.equals("today two") && days.get(0).items.get(1).text.equals("today one"));
        t("count", HistDays.count(days) == 4 && HistDays.count(null) == 0);
        List<HistDays.Day> f = HistDays.group(all, NOW, UTC, "today", null);
        t("filter keeps only matching days", f.size() == 1 && f.get(0).items.size() == 2);
        t("filter none", HistDays.group(all, NOW, UTC, "zzz", null).isEmpty());
        t("group null / empty", HistDays.group(null, NOW, UTC, "", null).isEmpty() && HistDays.group(new HistLog().entries(), NOW, UTC, "", null).isEmpty());

        // texts
        t("summary", HistDays.summaryLine(0, 0, "").equals("No entries yet") && HistDays.summaryLine(5, 5, "").equals("5 entries") && HistDays.summaryLine(1, 1, null).equals("1 entry") && HistDays.summaryLine(2, 9, "x").equals("2 of 9 entries"));
        t("empty off", HistDays.emptyTitle(false, 0, "").equals("Typing history is OFF") && HistDays.emptyHelp(false, 0, "").contains("Data & Backup"));
        t("empty on", HistDays.emptyTitle(true, 0, "").equals("Nothing saved yet") && !HistDays.emptyHelp(true, 0, "").isEmpty());
        t("empty search", HistDays.emptyTitle(true, 3, " abc ").contains("abc") && HistDays.emptyTitle(false, 3, "abc").contains("Nothing found"));
        t("toasts", HistDays.copiedToast().contains("clipboard history") && !HistDays.deletedToast().isEmpty() && HistDays.undoLabel().equals("Undo") && !HistDays.restoredToast().isEmpty());
        t("open label", HistDays.openLabel(0).equals("Open typing history") && HistDays.openLabel(3).equals("Open typing history (3)"));
        t("hint", HistDays.hintLine().contains("Tap") && HistDays.hintLine().contains("Hold"));

        // export
        HistLog x = new HistLog();
        x.type("line one\nline two", "com.whatsapp", NOW - 13 * H); x.closeRun();
        x.type("second", "org.telegram.messenger", NOW - 1 * H); x.closeRun();
        String ex = HistDays.exportText(x.entries(), NOW, UTC, "", lb);
        t("export header", ex.startsWith("Jabra Keyboard typing history\nExported 2026-11-16 12:00\n2 entries\n\n"));
        t("export oldest first", ex.indexOf("== Yesterday ==") > 0 && ex.indexOf("== Yesterday ==") < ex.indexOf("== Today =="));
        t("export row", ex.contains("23:00  Whatsapp\n  line one\n  line two\n") && ex.contains("11:00  Telegram\n  second\n"));
        t("export filter", HistDays.exportText(x.entries(), NOW, UTC, "second", lb).contains("1 entry") && !HistDays.exportText(x.entries(), NOW, UTC, "second", lb).contains("line one"));
        t("export empty", HistDays.exportText(null, NOW, UTC, "", null).contains("0 entries"));

        // HistLog.restore (Undo)
        HistLog r = new HistLog();
        r.type("first", "a.app", 1000L); r.closeRun();
        r.type("second", "a.app", 2000L); r.closeRun();
        r.type("third", "a.app", 3000L); r.closeRun();
        HistLog.Entry mid = r.entries().get(1);
        t("remove then restore", r.remove(mid.id) && r.size() == 2);
        r.markClean();
        t("restore ok", r.restore(mid) && r.size() == 3 && r.isDirty());
        List<HistLog.Entry> back = r.entries();
        t("restore keeps time order", back.get(0).text.equals("first") && back.get(1).text.equals("second") && back.get(2).text.equals("third") && back.get(1).id == mid.id);
        t("restore twice refused", !r.restore(mid) && r.size() == 3);
        t("restore null / blank refused", !r.restore(null));
        HistLog r2 = new HistLog();
        r2.type("old", "a.app", 10L); r2.closeRun();
        HistLog.Entry oldE = r2.entries().get(0);
        r2.remove(oldE.id);
        r2.type("new", "a.app", 99999L); r2.closeRun();
        t("restore at front", r2.restore(oldE) && r2.entries().get(0).text.equals("old") && r2.entries().get(1).text.equals("new"));
        HistLog r3 = HistLog.parseOrNull(r.serialize());
        t("restored entry survives save / load", r3 != null && r3.size() == 3 && r3.entries().get(1).text.equals("second"));

        System.out.println("HistDaysTest: " + ok + " PASS / " + bad + " FAIL");
        if (bad > 0) System.exit(1);
    }
}
