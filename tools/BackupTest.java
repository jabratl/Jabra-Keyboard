import com.mgb.keyboard.typing.BackupCodec;
import java.util.LinkedHashMap;
import java.util.Map;

/** v0.4.0 backup codec tests (computer only).  javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/BackupTest.java && java -cp out BackupTest */
public class BackupTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }

    static Map<String, Object> prefs() {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("bangla", Boolean.TRUE);
        p.put("theme", Integer.valueOf(7));
        p.put("big", Long.valueOf(1234567890123L));
        p.put("ratio", Float.valueOf(1.5f));
        p.put("clips_v1", "1,%E0%A6%A4\n0,hello \"q\" \\ \t end");
        p.put("fmt_cur", "{\"a\":\"বাংলা 😀\"}");
        return p;
    }

    public static void main(String[] a) {
        Map<String, String> files = new LinkedHashMap<String, String>();
        files.put("vault_v1.json", "{\"ver\":1,\"x\":\"বাংলা\"}");
        String b = BackupCodec.build(prefs(), files, "0.4.0", 1700000000000L);

        t("valid OK", BackupCodec.validate(b) == BackupCodec.OK);
        Map<String, Object> p = BackupCodec.prefs(b);
        t("6 prefs", p.size() == 6);
        t("bool", Boolean.TRUE.equals(p.get("bangla")));
        t("int", Integer.valueOf(7).equals(p.get("theme")));
        t("long", Long.valueOf(1234567890123L).equals(p.get("big")));
        t("float", Float.valueOf(1.5f).equals(p.get("ratio")));
        t("clips string round trip", prefs().get("clips_v1").equals(p.get("clips_v1")));
        t("bangla + emoji json string", prefs().get("fmt_cur").equals(p.get("fmt_cur")));
        t("file round trip", files.get("vault_v1.json").equals(BackupCodec.files(b).get("vault_v1.json")));
        long[] s = BackupCodec.summary(b);
        t("summary counts", s[0] == 6 && s[1] == 1 && s[2] == 1700000000000L);
        t("app version", "0.4.0".equals(BackupCodec.appVersion(b)));

        // empty backup is valid
        String e = BackupCodec.build(new LinkedHashMap<String, Object>(), new LinkedHashMap<String, String>(), "0.4.0", 1L);
        t("empty valid", BackupCodec.validate(e) == BackupCodec.OK);
        t("null maps valid", BackupCodec.validate(BackupCodec.build(null, null, null, 0L)) == BackupCodec.OK);

        // damage
        t("bad json", BackupCodec.validate("not json") == BackupCodec.BAD_JSON);
        t("null text", BackupCodec.validate(null) == BackupCodec.BAD_JSON);
        t("empty text", BackupCodec.validate("") == BackupCodec.BAD_JSON);
        t("truncated", BackupCodec.validate(b.substring(0, b.length() / 2)) == BackupCodec.BAD_JSON);
        t("array root", BackupCodec.validate("[1,2]") == BackupCodec.BAD_JSON);
        t("trailing junk", BackupCodec.validate(b + "x") == BackupCodec.BAD_JSON);
        t("wrong version", BackupCodec.validate(b.replace("\"ver\":1", "\"ver\":2")) == BackupCodec.BAD_VER);
        t("missing version", BackupCodec.validate("{\"prefs\":{},\"files\":{}}") == BackupCodec.BAD_VER);
        t("edited pref -> hash", BackupCodec.validate(b.replace("\"v\":7", "\"v\":8")) == BackupCodec.BAD_HASH);
        t("edited file -> hash", BackupCodec.validate(b.replace("vault_v1.json", "vault_v2.json")) == BackupCodec.BAD_HASH);
        t("hash tampered", BackupCodec.validate(b.replaceFirst("\"sha256\":\"[0-9a-f]", "\"sha256\":\"z")) == BackupCodec.BAD_HASH);
        t("bad type tag", BackupCodec.validate(b.replace("\"t\":\"b\"", "\"t\":\"q\"")) == BackupCodec.BAD_JSON);

        // too big
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < BackupCodec.MAX_BYTES + 10; i++) big.append('a');
        t("too big", BackupCodec.validate(big.toString()) == BackupCodec.TOO_BIG);

        // unreadable helpers never throw
        t("summary of junk", BackupCodec.summary("zzz")[0] == -1);
        t("prefs of junk empty", BackupCodec.prefs("zzz").isEmpty());
        t("files of junk empty", BackupCodec.files("zzz").isEmpty());

        // unsupported value types skipped
        Map<String, Object> w = new LinkedHashMap<String, Object>();
        w.put("set", new java.util.HashSet<String>()); w.put("ok", "x");
        t("unsupported skipped", BackupCodec.prefs(BackupCodec.build(w, null, "x", 0)).size() == 1);

        // control chars
        Map<String, Object> c = new LinkedHashMap<String, Object>();
        c.put("k", "a\u0001b\u001fc");
        String cb = BackupCodec.build(c, null, "x", 0);
        t("control chars valid", BackupCodec.validate(cb) == BackupCodec.OK && "a\u0001b\u001fc".equals(BackupCodec.prefs(cb).get("k")));

        // whitespace tolerant (pretty printed copy still valid)
        t("pretty printed ok", BackupCodec.validate(b.replace(",\"", ",\n \"")) == BackupCodec.OK);

        System.out.println("BackupTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
