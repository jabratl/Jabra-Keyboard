import com.mgb.keyboard.typing.*;
import java.util.*;

/** v0.10.19 (W6.2) tests for the auto-profile decisions. */
public class AppProfileApplyTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static Map<String, Object> m(Object... kv) { Map<String, Object> r = new LinkedHashMap<String, Object>(); for (int i = 0; i + 1 < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]); return r; }

    public static void main(String[] a) {
        AppProfileMap map = AppProfileMap.empty().put("com.chat", "Compact").put("com.notes", "Novel Writing");
        // pick
        t("pick mapped", "Compact".equals(AppProfileApply.pick(map, "com.chat", "")));
        t("pick mapped, last null", "Compact".equals(AppProfileApply.pick(map, "com.chat", null)));
        t("pick same as last = null", AppProfileApply.pick(map, "com.chat", "Compact") == null);
        t("pick same as last, other case = null", AppProfileApply.pick(map, "com.chat", "compact") == null);
        t("pick different last = apply", "Compact".equals(AppProfileApply.pick(map, "com.chat", "Novel Writing")));
        t("pick unmapped app = null", AppProfileApply.pick(map, "com.other", "") == null);
        t("pick null / empty package = null", AppProfileApply.pick(map, null, "") == null && AppProfileApply.pick(map, "", "") == null);
        t("pick null map = null", AppProfileApply.pick(null, "com.chat", "") == null);
        t("pick empty map = null", AppProfileApply.pick(AppProfileMap.empty(), "com.chat", "") == null);
        AppProfileMap withOther = map.withOther("Normal");
        t("pick uses other for unknown app", "Normal".equals(AppProfileApply.pick(withOther, "com.zzz", "")));
        t("pick own beats other", "Compact".equals(AppProfileApply.pick(withOther, "com.chat", "")));
        t("pick other same as last = null", AppProfileApply.pick(withOther, "com.zzz", "Normal") == null);

        // find
        List<ProfileCodec.Profile> ready = ProfileCodec.starters();
        List<ProfileCodec.Profile> saved = new ArrayList<ProfileCodec.Profile>();
        saved.add(new ProfileCodec.Profile("Mine", ProfileCodec.clean(m("theme", 4))));
        saved.add(new ProfileCodec.Profile("Compact", ProfileCodec.clean(m("theme", 9))));
        t("find saved", AppProfileApply.find("Mine", saved, ready).values.get("theme").equals(4));
        t("find ready", AppProfileApply.find("Novel Writing", saved, ready) != null);
        t("find case-insensitive", AppProfileApply.find("mine", saved, ready) != null && AppProfileApply.find("NOVEL WRITING", saved, ready) != null);
        t("saved wins over ready of same name", AppProfileApply.find("Compact", saved, ready).values.get("theme") != null);
        t("find missing = null", AppProfileApply.find("Nope", saved, ready) == null);
        t("find null / empty name", AppProfileApply.find(null, saved, ready) == null && AppProfileApply.find("", saved, ready) == null);
        t("find with null saved (damaged file) still finds ready", AppProfileApply.find("Large", null, ready) != null);
        t("find with both null", AppProfileApply.find("Large", null, null) == null);

        // differs
        t("differs: same int", !AppProfileApply.differs(m("theme", 4), m("theme", 4)));
        t("differs: other int", AppProfileApply.differs(m("theme", 4), m("theme", 5)));
        t("differs: Long vs Integer same value", !AppProfileApply.differs(m("theme", 4), m("theme", 4L)));
        t("differs: key not set now, value wanted", AppProfileApply.differs(m("theme", 4), m()));
        t("differs: null all", AppProfileApply.differs(m("theme", 4), null));
        t("differs: null wanted, key not set = same", !AppProfileApply.differs(m("kh_pct_p", null), m()));
        t("differs: null wanted, key set = change", AppProfileApply.differs(m("kh_pct_p", null), m("kh_pct_p", 80)));
        t("differs: boolean same", !AppProfileApply.differs(m("pc_mode", false), m("pc_mode", false)));
        t("differs: boolean other", AppProfileApply.differs(m("pc_mode", true), m("pc_mode", false)));
        t("differs: string same", !AppProfileApply.differs(m("tb_order", "a,b"), m("tb_order", "a,b")));
        t("differs: string other", AppProfileApply.differs(m("tb_order", "a,b"), m("tb_order", "b,a")));
        t("differs: one of many differs", AppProfileApply.differs(m("theme", 4, "look", 1, "pc_mode", false), m("theme", 4, "look", 0, "pc_mode", false)));
        t("differs: all same many", !AppProfileApply.differs(m("theme", 4, "look", 1, "pc_mode", false), m("theme", 4, "look", 1, "pc_mode", false, "extra", 7)));
        t("differs: empty values", !AppProfileApply.differs(m(), m("theme", 1)));
        t("differs: null values", !AppProfileApply.differs(null, m("theme", 1)));
        t("differs: boolean vs int type mismatch = differs", AppProfileApply.differs(m("pc_mode", true), m("pc_mode", 1)));

        // all ready profiles: applying twice = second time differs() is false (idempotent, so no flicker on the second visit)
        for (ProfileCodec.Profile p : ready) {
            Map<String, Object> prefs = new HashMap<String, Object>();
            for (Map.Entry<String, Object> e : p.values.entrySet()) if (e.getValue() != null) prefs.put(e.getKey(), e.getValue()); else prefs.remove(e.getKey());
            t("ready '" + p.name + "' idempotent", !AppProfileApply.differs(p.values, prefs));
        }
        // look presets as profiles too
        for (int i = 0; i < LookPresets.count(); i++) {
            ProfileCodec.Profile p = LookPresets.profile(i);
            Map<String, Object> prefs = new HashMap<String, Object>(p.values);
            t("preset '" + p.name + "' idempotent", !AppProfileApply.differs(p.values, prefs));
        }
        System.out.println("AppProfileApplyTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
