import com.mgb.keyboard.typing.HealthCheck;
import com.mgb.keyboard.typing.HealthCheck.Item;
import com.mgb.keyboard.typing.Migrate;
import com.mgb.keyboard.clip.ClipList;
import java.util.*;

/** v0.5.3 health check + migration tests (computer only).
 *  javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java app/src/main/java/com/mgb/keyboard/clip/*.java app/src/main/java/com/mgb/keyboard/vault/*.java tools/HealthTest.java && java -cp out HealthTest */
public class HealthTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }

    static Item get(List<Item> l, String id) { for (Item i : l) if (i.id.equals(id)) return i; return null; }

    static HealthCheck.Input base() {
        HealthCheck.Input in = new HealthCheck.Input();
        in.clips = "1,hello\n0,%E0%A6%A4"; in.schemaV = Migrate.CURRENT; in.backups = 2; in.now = 1000;
        return in;
    }

    public static void main(String[] a) {
        // ---- JSON validator ----
        t("json blank", HealthCheck.isJson("") && HealthCheck.isJson(null) && HealthCheck.isJson("  "));
        t("json obj", HealthCheck.isJson("{\"a\":[1,2.5,-3e2,true,false,null,\"x\\n\\u0041\"],\"b\":{}}"));
        t("json arr", HealthCheck.isJson("[]") && HealthCheck.isJson("[{\"a\":1}]"));
        t("json bangla", HealthCheck.isJson("{\"a\":\"বাংলা 😀\"}"));
        t("json cut", !HealthCheck.isJson("{\"a\":1"));
        t("json trailing comma", !HealthCheck.isJson("{\"a\":1,}") && !HealthCheck.isJson("[1,]"));
        t("json junk after", !HealthCheck.isJson("{} x"));
        t("json single quote", !HealthCheck.isJson("{'a':1}"));
        t("json bad escape", !HealthCheck.isJson("\"\\q\"") && !HealthCheck.isJson("\"\\u12G4\""));
        t("json bad num", !HealthCheck.isJson("[1.]") && !HealthCheck.isJson("[-]") && !HealthCheck.isJson("[1e]"));
        t("json deep", !HealthCheck.isJson("[".repeat(100) + "]".repeat(100)));
        t("json word", !HealthCheck.isJson("hello"));

        // ---- all fine ----
        List<Item> r = HealthCheck.run(base());
        t("all ok", HealthCheck.worst(r) == HealthCheck.OK);
        t("9 lines", r.size() == 9);
        t("label OK", get(r, "clips").label().startsWith("OK"));

        // ---- clips ----
        HealthCheck.Input in = base(); in.clips = "1,hello\nXX\n\n0,%ZZ\n0,fine";
        r = HealthCheck.run(in);
        t("clips warn", get(r, "clips").level == HealthCheck.WARN && "clips".equals(get(r, "clips").fix));
        t("clips count 2 bad", get(r, "clips").text.contains("2 unreadable"));
        String fixed = HealthCheck.repairClips(in.clips);
        t("repair keeps good", ClipList.parse(fixed).size() == 2 && fixed.startsWith("1,hello"));
        t("repair second run same", HealthCheck.repairClips(fixed).equals(fixed));
        in.clips = fixed; t("after repair ok", get(HealthCheck.run(in), "clips").level == HealthCheck.OK);
        t("repair empty", HealthCheck.repairClips("").equals("") && HealthCheck.repairClips(null).equals(""));
        t("countLines", HealthCheck.countLines("a\n\n b \n") == 2 && HealthCheck.countLines(null) == 0);

        // ---- formatter ----
        in = base(); in.fmtCur = "{\"a\":1"; r = HealthCheck.run(in);
        t("fmt warn", get(r, "fmt").level == HealthCheck.WARN && get(r, "fmt").text.contains("current"));
        in = base(); in.fmtPresets = "[1,]"; t("fmt presets warn", get(HealthCheck.run(in), "fmt").text.contains("presets"));
        in = base(); in.fmtCur = "{\"a\":1}"; in.fmtPresets = "[]"; t("fmt ok", get(HealthCheck.run(in), "fmt").level == HealthCheck.OK);

        // ---- vault ----
        in = base(); in.vault = "{\"ver\":1,\"folders\":[],\"entries\":[]}"; t("vault ok", get(HealthCheck.run(in), "vault").level == HealthCheck.OK);
        in.vault = "{not json"; r = HealthCheck.run(in);
        t("vault bad", get(r, "vault").level == HealthCheck.BAD && "vault".equals(get(r, "vault").fix));
        in.vault = "{\"ver\":99,\"entries\":[]}"; t("vault newer = bad", get(HealthCheck.run(in), "vault").level == HealthCheck.BAD);
        in.vault = null; t("vault none ok", get(HealthCheck.run(in), "vault").level == HealthCheck.OK);

        // ---- profiles ----
        in = base(); in.profiles = "{\"ver\":1,\"profiles\":[]}"; t("profiles ok", get(HealthCheck.run(in), "profiles").level == HealthCheck.OK);
        in.profiles = "xx"; r = HealthCheck.run(in);
        t("profiles bad", get(r, "profiles").level == HealthCheck.BAD && "profiles".equals(get(r, "profiles").fix));
        in.profiles = "{\"ver\":2,\"profiles\":[]}"; t("profiles newer = bad", get(HealthCheck.run(in), "profiles").level == HealthCheck.BAD);
        in.profiles = null; t("profiles none ok", get(HealthCheck.run(in), "profiles").level == HealthCheck.OK);

        // ---- theme ----
        in = base(); in.ctJson = "garbage"; t("ct bad = warn", get(HealthCheck.run(in), "theme").level == HealthCheck.WARN);
        in = base(); in.theme = 20; t("theme 20 no ct = warn", get(HealthCheck.run(in), "theme").level == HealthCheck.WARN);
        in = base(); in.theme = 20; in.ctJson = "{\"ver\":1,\"bg\":\"#112233\"}"; t("theme 20 + ct ok", get(HealthCheck.run(in), "theme").level == HealthCheck.OK);
        in = base(); in.ctJson = "{\"ver\":2}"; t("ct wrong ver = warn", get(HealthCheck.run(in), "theme").level == HealthCheck.WARN);

        // ---- backups / storage / schema ----
        in = base(); in.backups = 0; t("no backups warn", get(HealthCheck.run(in), "backups").level == HealthCheck.WARN);
        in = base(); in.backups = 4; in.badBackups = 1; t("bad backup warn", get(HealthCheck.run(in), "backups").text.contains("1 of 4"));
        in = base(); in.storageBytes = 31L * 1024 * 1024; t("storage warn", get(HealthCheck.run(in), "storage").level == HealthCheck.WARN);
        in = base(); in.storageBytes = 2048; t("storage ok", get(HealthCheck.run(in), "storage").text.contains("2 KB"));
        in = base(); in.schemaV = 1; t("schema old warn", get(HealthCheck.run(in), "schema").level == HealthCheck.WARN);
        in = base(); in.schemaV = 9; t("schema newer warn", get(HealthCheck.run(in), "schema").text.contains("newer"));

        // ---- crash guard ----
        in = base(); in.crashPanel = "vault"; in.crashN = 2; in.crashT = 500; in.now = 1000;
        r = HealthCheck.run(in);
        t("crash active bad", get(r, "crash").level == HealthCheck.BAD && "crash".equals(get(r, "crash").fix));
        in.now = 500 + HealthCheck.BLOCK_MS + 1; t("crash expired warn", get(HealthCheck.run(in), "crash").level == HealthCheck.WARN);
        in = base(); in.crashPanel = "vault"; in.crashN = 1; t("crash once ok", get(HealthCheck.run(in), "crash").level == HealthCheck.OK);
        t("nextCount same", HealthCheck.nextCount("vault", 1, "vault") == 2);
        t("nextCount other", HealthCheck.nextCount("vault", 5, "tt") == 1);
        t("nextCount first", HealthCheck.nextCount("", 0, "tt") == 1);
        t("nextCount cap", HealthCheck.nextCount("tt", 99, "tt") == 99);
        t("blocked yes", HealthCheck.blocked("vault", 2, 1000, "vault", 2000));
        t("blocked one crash no", !HealthCheck.blocked("vault", 1, 1000, "vault", 2000));
        t("blocked other panel no", !HealthCheck.blocked("vault", 3, 1000, "tt", 2000));
        t("blocked expired no", !HealthCheck.blocked("vault", 3, 1000, "vault", 1000 + HealthCheck.BLOCK_MS));
        t("blocked clock back no", !HealthCheck.blocked("vault", 3, 5000, "vault", 1000));
        t("blocked null name no", !HealthCheck.blocked("vault", 3, 1000, null, 2000));

        // ---- migration ----
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("theme", 7); p.put("haptic", Boolean.TRUE); p.put("tb_order", "a,b"); p.put("unknown_key", "keep");
        Migrate.Result m = Migrate.plan(p, 1);
        t("good prefs: no change", !m.changes() && m.to == Migrate.CURRENT);
        p.put("theme", 99); p.put("haptic", "yes"); p.put("kh_pct_p", Long.valueOf(80)); p.put("onehand", 5); p.put("bn_layout", "zzz"); p.put("tb_hidden", Integer.valueOf(3));
        p.put("key_gap", -1); p.put("lp_delay", 5000);
        m = Migrate.plan(p, 1);
        t("bad theme removed", m.remove.contains("theme"));
        t("bad bool type removed", m.remove.contains("haptic"));
        t("long int removed", m.remove.contains("kh_pct_p"));
        t("onehand range removed", m.remove.contains("onehand"));
        t("bn_layout removed", m.remove.contains("bn_layout"));
        t("string type removed", m.remove.contains("tb_hidden"));
        t("good keys kept", !m.remove.contains("tb_order") && !m.remove.contains("key_gap") && !m.remove.contains("unknown_key"));
        t("big lp_delay kept (no range rule)", !m.remove.contains("lp_delay"));
        t("notes per key", m.notes.size() == m.remove.size() && m.notes.size() == 6);
        // idempotent: apply removals, plan again = nothing
        for (String k : m.remove) p.remove(k);
        t("second plan from 1 empty", !Migrate.plan(p, 1).changes());
        t("already current", !Migrate.plan(p, 2).changes() && Migrate.plan(p, 2).to == 2);
        Map<String, Object> bp = new LinkedHashMap<String, Object>(); bp.put("theme", 99);
        t("current schema untouched", !Migrate.plan(bp, 2).changes());
        Migrate.Result nw = Migrate.plan(bp, 7);
        t("newer schema untouched", !nw.changes() && nw.to == 7);
        t("garbage from = 1", Migrate.plan(bp, 0).changes() && Migrate.plan(bp, -5).from == 1);
        t("null prefs safe", !Migrate.plan(null, 1).changes());

        // ---- backup age ----
        long nowT = 0;
        try { nowT = new java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US).parse("20261111_1200").getTime(); } catch (Exception e) { }
        t("days same day", HealthCheck.daysSince("20261111_0800", nowT) == 0);
        t("days 3", HealthCheck.daysSince("20261108_1200", nowT) == 3);
        t("days bad stamp", HealthCheck.daysSince("", nowT) == -1 && HealthCheck.daysSince(null, nowT) == -1 && HealthCheck.daysSince("2026-11-08 12:00", nowT) == -1 && HealthCheck.daysSince("20261399_9999", nowT) == -1);
        t("days future = 0", HealthCheck.daysSince("20271111_1200", nowT) == 0);
        t("note auto on = empty", HealthCheck.backupNote(true, "", nowT).isEmpty() && HealthCheck.backupNote(true, "20200101_0000", nowT).isEmpty());
        t("note none", HealthCheck.backupNote(false, "", nowT).startsWith("No backup yet"));
        t("note fresh", HealthCheck.backupNote(false, "20261110_1200", nowT).isEmpty());
        t("note 6 days ok, 7 stale", HealthCheck.backupNote(false, "20261105_1200", nowT).isEmpty() && HealthCheck.backupNote(false, "20261104_1200", nowT).contains("7 days"));
        t("ago", HealthCheck.ago(-1).equals("") && HealthCheck.ago(0).equals("today") && HealthCheck.ago(1).equals("yesterday") && HealthCheck.ago(5).equals("5 days ago"));
        System.out.println("HealthTest " + ok + "/" + (ok + bad) + (bad == 0 ? " OK" : " FAILED"));
        if (bad != 0) System.exit(1);
    }
}
