import com.mgb.keyboard.typing.ProfileCodec;
import com.mgb.keyboard.typing.ProfileCodec.Profile;
import java.util.*;

/** v0.5.1 profile engine tests (computer only).  javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/ProfileTest.java && java -cp out ProfileTest */
public class ProfileTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }

    public static void main(String[] a) {
        // whitelist
        t("allowed theme", ProfileCodec.allowed("theme") && ProfileCodec.allowed("xrow_chars") && ProfileCodec.allowed("haptic_lv"));
        t("ct_json NOT allowed", !ProfileCodec.allowed("ct_json") && !ProfileCodec.allowed("clips_v1") && !ProfileCodec.allowed("fmt_cur") && !ProfileCodec.allowed(null));
        t("68 keys", ProfileCodec.KEYS.length == 68);   // v0.17.32: +clip_btn_pct; v0.17.18: +danda_key; v0.17.5: +kb_custom; v0.17.2: +sp_wpct, sp_hpct, sp_anim; v0.17.1: +key_qm; v0.16.16: +clip_hold; v0.16.9: +panel_ui; v0.16.7: +nr_hpct, nr_mode, nr_chars;   // v0.16.6: +tb_hpct, tb_ipct; v0.16.3: +bn_map   // v0.9.9: +pop_fast; v0.10.1: +sg_on
        Set<String> seen = new HashSet<String>(Arrays.asList(ProfileCodec.KEYS));
        t("keys unique", seen.size() == ProfileCodec.KEYS.length);

        // clean
        Map<String, Object> raw = new LinkedHashMap<String, Object>();
        raw.put("theme", 3); raw.put("ct_json", "{}"); raw.put("pc_mode", "yes"); raw.put("kh_pct_p", null);
        raw.put("onehand", 7); raw.put("bn_layout", "zzz"); raw.put("tb_order", "fmt,clip"); raw.put("look", 1.5); raw.put("numrow", true);
        Map<String, Object> c = ProfileCodec.clean(raw);
        t("clean keeps valid", c.get("theme").equals(3) && c.get("tb_order").equals("fmt,clip") && c.get("numrow").equals(true));
        t("clean drops unknown / bad type / range", !c.containsKey("ct_json") && !c.containsKey("pc_mode") && !c.containsKey("onehand") && !c.containsKey("bn_layout") && !c.containsKey("look"));
        t("clean keeps null = remove", c.containsKey("kh_pct_p") && c.get("kh_pct_p") == null);
        t("clean null map", ProfileCodec.clean(null).isEmpty());
        Map<String, Object> r2 = new HashMap<String, Object>();
        r2.put("theme", 21); t("theme 21 dropped", ProfileCodec.clean(r2).isEmpty());
        r2.clear(); r2.put("theme", 20); t("theme 20 ok (custom slot)", ProfileCodec.clean(r2).size() == 1);
        r2.clear(); r2.put("kh_pct_p", 5000); t("big int dropped", ProfileCodec.clean(r2).isEmpty());
        // v0.17.2 (K2): space bar prefs
        r2.clear(); r2.put("sp_wpct", 150); r2.put("sp_hpct", 120); r2.put("sp_anim", false); t("sp prefs ok", ProfileCodec.clean(r2).size() == 3);
        r2.clear(); r2.put("sp_wpct", 59); t("sp_wpct 59 dropped", ProfileCodec.clean(r2).isEmpty());
        r2.clear(); r2.put("sp_wpct", 201); t("sp_wpct 201 dropped", ProfileCodec.clean(r2).isEmpty());
        r2.clear(); r2.put("sp_hpct", 79); t("sp_hpct 79 dropped", ProfileCodec.clean(r2).isEmpty());
        r2.clear(); r2.put("sp_hpct", 161); t("sp_hpct 161 dropped", ProfileCodec.clean(r2).isEmpty());
        r2.clear(); r2.put("sp_anim", 1); t("sp_anim wrong type dropped", ProfileCodec.clean(r2).isEmpty());
        r2.clear(); r2.put("kb_custom", 1); t("kb_custom 1 ok", ProfileCodec.clean(r2).size() == 1);   // v0.17.5
        r2.clear(); r2.put("kb_custom", 0); t("kb_custom 0 ok", ProfileCodec.clean(r2).size() == 1);
        r2.clear(); r2.put("kb_custom", 2); t("kb_custom 2 dropped", ProfileCodec.clean(r2).isEmpty());
        r2.clear(); r2.put("kb_custom", -1); t("kb_custom -1 dropped", ProfileCodec.clean(r2).isEmpty());
        r2.clear(); r2.put("kb_custom", true); t("kb_custom wrong type dropped", ProfileCodec.clean(r2).isEmpty());
        r2.clear(); r2.put("sp_wpct", 60); r2.put("sp_hpct", 160); t("sp range edges ok", ProfileCodec.clean(r2).size() == 2);
        r2.clear(); r2.put("kh_pct_p", Long.valueOf(90)); t("Long accepted as int", ProfileCodec.clean(r2).get("kh_pct_p").equals(90));
        StringBuilder big = new StringBuilder(); for (int i = 0; i < 700; i++) big.append('x');
        r2.clear(); r2.put("tb_order", big.toString()); t("long string dropped", ProfileCodec.clean(r2).isEmpty());
        r2.clear(); r2.put("xrow_chars", "12345678901234567890123456789012345678901"); t("xrow > 40 dropped", ProfileCodec.clean(r2).isEmpty());

        // names
        t("name trim + spaces", ProfileCodec.cleanName("  My   work\n ").equals("My work"));
        t("name max 24", ProfileCodec.cleanName("abcdefghijklmnopqrstuvwxyz0123").length() == 24);
        t("name null", ProfileCodec.cleanName(null).isEmpty());
        t("name Bangla kept", ProfileCodec.cleanName("লেখা মোড").equals("লেখা মোড"));

        // capture
        Map<String, Object> all = new HashMap<String, Object>();
        all.put("theme", 5); all.put("pc_mode", true); all.put("ct_json", "SECRET"); all.put("clips_v1", "x"); all.put("tb_order", "paste,fmt"); all.put("look", "wrongtype");
        Profile p = ProfileCodec.capture("Mine", all);
        t("capture name", p.name.equals("Mine"));
        t("capture all whitelisted keys present", p.values.size() == ProfileCodec.KEYS.length);
        t("capture values", p.values.get("theme").equals(5) && p.values.get("pc_mode").equals(true) && p.values.get("tb_order").equals("paste,fmt"));
        t("capture unset = null", p.values.containsKey("kh_pct_p") && p.values.get("kh_pct_p") == null);
        t("capture wrong type = null", p.values.get("look") == null);
        t("capture never has ct_json / clips", !p.values.containsKey("ct_json") && !p.values.containsKey("clips_v1"));
        t("capture null map", ProfileCodec.capture("x", null).values.size() == ProfileCodec.KEYS.length);

        // list ops
        List<Profile> l = new ArrayList<Profile>();
        l = ProfileCodec.put(l, p);
        t("put", l != null && l.size() == 1);
        List<Profile> l2 = ProfileCodec.put(l, ProfileCodec.capture("mine", all));
        t("put same name (case) replaces", l2.size() == 1 && l2.get(0).name.equals("mine"));
        t("put empty name refused", ProfileCodec.put(l, new Profile("", new LinkedHashMap<String, Object>())) == null);
        List<Profile> full = new ArrayList<Profile>();
        for (int i = 0; i < ProfileCodec.MAX_PROFILES; i++) full = ProfileCodec.put(full, ProfileCodec.capture("p" + i, all));
        t("20 allowed", full != null && full.size() == 20);
        t("21st refused", ProfileCodec.put(full, ProfileCodec.capture("extra", all)) == null);
        t("21st same name replaces", ProfileCodec.put(full, ProfileCodec.capture("p3", all)).size() == 20);
        t("remove", ProfileCodec.remove(full, 0).size() == 19 && ProfileCodec.remove(full, 99).size() == 20 && ProfileCodec.remove(full, -1).size() == 20);
        t("rename", ProfileCodec.rename(l, 0, "Work").get(0).name.equals("Work"));
        t("rename empty refused", ProfileCodec.rename(l, 0, "  ") == null);
        t("rename to other's name refused", ProfileCodec.rename(full, 0, "P1") == null);
        t("rename to own name (case change) ok", ProfileCodec.rename(full, 1, "P1") != null);
        t("rename bad index", ProfileCodec.rename(l, 5, "x") == null);
        t("indexOf", ProfileCodec.indexOf(full, "P7") == 7 && ProfileCodec.indexOf(full, "zz") == -1 && ProfileCodec.indexOf(null, "a") == -1);

        // json round trip
        List<Profile> two = new ArrayList<Profile>();
        two = ProfileCodec.put(two, ProfileCodec.capture("A \"q\" \\ \u00e9 লেখা", all));
        two = ProfileCodec.put(two, ProfileCodec.starters().get(1));
        String js = ProfileCodec.toJson(two);
        List<Profile> back = ProfileCodec.parse(js);
        t("round trip count", back != null && back.size() == 2);
        t("round trip name with quote / backslash / Bangla", back.get(0).name.equals("A \"q\" \\ \u00e9 লেখা"));
        t("round trip values", back.get(0).values.equals(two.get(0).values) && back.get(1).values.equals(two.get(1).values));
        t("round trip json stable", ProfileCodec.toJson(back).equals(js));
        t("empty list json", ProfileCodec.parse(ProfileCodec.toJson(new ArrayList<Profile>())).isEmpty());

        // parse: damaged / newer / hostile
        t("parse null", ProfileCodec.parse(null) == null);
        t("parse garbage", ProfileCodec.parse("not json") == null && ProfileCodec.parse("") == null && ProfileCodec.parse("{") == null);
        t("parse truncated", ProfileCodec.parse(js.substring(0, js.length() / 2)) == null);
        t("parse trailing junk", ProfileCodec.parse(js + "x") == null);
        t("parse newer ver = null", ProfileCodec.parse("{\"ver\":2,\"profiles\":[]}") == null);
        t("parse no ver = null", ProfileCodec.parse("{\"profiles\":[]}") == null);
        t("parse array root = null", ProfileCodec.parse("[]") == null);
        List<Profile> h = ProfileCodec.parse("{\"ver\":1,\"profiles\":[{\"name\":\"X\",\"values\":{\"theme\":2,\"ct_json\":\"evil\",\"pc_mode\":5,\"look\":null}},{\"name\":\"\",\"values\":{}},{\"name\":\"X\",\"values\":{}},7,{\"name\":\"Y\"}]}");
        t("hostile: only first X kept", h != null && h.size() == 1 && h.get(0).name.equals("X"));
        t("hostile: values cleaned", h.get(0).values.size() == 2 && h.get(0).values.get("theme").equals(2) && h.get(0).values.containsKey("look"));
        StringBuilder deep = new StringBuilder(); for (int i = 0; i < 30; i++) deep.append('['); 
        t("deep nesting refused", ProfileCodec.parse(deep.toString()) == null);
        t("escape \\u0041", ProfileCodec.parse("{\"ver\":1,\"profiles\":[{\"name\":\"\\u0041b\",\"values\":{}}]}").get(0).name.equals("Ab"));

        // starters
        List<Profile> st = ProfileCodec.starters();
        t("6 starters", st.size() == 6);
        t("starter names", st.get(0).name.equals("Normal") && st.get(1).name.equals("Novel Writing") && st.get(2).name.equals("PC") && st.get(3).name.equals("Compact") && st.get(4).name.equals("One-Handed") && st.get(5).name.equals("Large"));
        t("starters unique", new HashSet<String>(Arrays.asList(st.get(0).name, st.get(1).name, st.get(2).name, st.get(3).name, st.get(4).name, st.get(5).name)).size() == 6);
        t("Normal resets size keys", st.get(0).values.containsKey("kh_pct_p") && st.get(0).values.get("kh_pct_p") == null && st.get(0).values.get("pc_mode").equals(false) && st.get(0).values.get("onehand").equals(0));
        t("PC sets pc_mode", st.get(2).values.get("pc_mode").equals(true));
        t("Compact 80", st.get(3).values.get("kh_pct_p").equals(80));
        t("One-Handed right", st.get(4).values.get("onehand").equals(2));
        t("Large 120", st.get(5).values.get("kh_pct_p").equals(120));
        boolean noTheme = true;
        for (Profile s : st) if (s.values.containsKey("theme") || s.values.containsKey("look") || s.values.containsKey("bn_layout")) noTheme = false;
        t("starters never touch theme / look / language", noTheme);
        boolean allClean = true;
        for (Profile s : st) if (!ProfileCodec.clean(s.values).equals(s.values)) allClean = false;
        t("starters pass their own whitelist (no value dropped)", allClean);
        t("describe", ProfileCodec.describe(st.get(3)).equals("6 set / 3 default") || ProfileCodec.describe(st.get(3)).contains("set"));

        System.out.println(ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
