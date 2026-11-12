import com.mgb.keyboard.typing.LookPresets;
import com.mgb.keyboard.typing.LookColors;
import com.mgb.keyboard.typing.ProfileCodec;
import java.util.*;

/** v0.10.17 (D3) tests: look presets + contrast helpers + contrast table of the 20 themes. */
public class LookPresetsTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static boolean near(double a, double b, double e) { return Math.abs(a - b) <= e; }

    // JabraThemes.ALL (index: bg, key (-1 = Classic system key = white), text, bg2 (-1 none)); kept in sync BY HAND with ThemeEmojiExtras.kt
    static final int[][] TH = {
        {0xDDDDDD, -1, 0x222222, -1}, {0xD6E6F5, 0xFFFFFF, 0x1B3A57, -1}, {0xD8EFE3, 0xFFFFFF, 0x1E4D3A, -1}, {0xEFE6D6, 0xFFFAF0, 0x4A3B22, -1},
        {0x1E1F22, 0x3A3C42, 0xF2F2F2, -1}, {0xE8DFCB, 0xF7F1E3, 0x2B2620, -1}, {0x5C7066, 0x2F3E38, 0xEAF2EE, -1}, {0xF3F0E8, 0xFFFFFF, 0x2A2A2A, -1},
        {0x0B0B0D, 0x222226, 0xFFFFFF, -1}, {0x1A1A1A, 0x2E2E2E, 0xE6E6E6, -1}, {0x0F1626, 0x1E2A44, 0xE8EEFF, -1}, {0xC9D6EE, 0xF4F7FF, 0x1F2F55, -1},
        {0x1C6BC0, 0x3779BC, 0xFFFFFF, -1}, {0x0E2A24, 0x1B4036, 0xDDF3EA, -1}, {0xBFEEE3, 0xEAFBF7, 0x0F4A40, -1}, {0xF6D3DE, 0xFFF1F5, 0x5A1F35, -1},
        {0xB9BDC4, 0xE6E8EC, 0x23262B, -1}, {0x2A2340, 0x3C3460, 0xEDE8FF, -1}, {0x3B1F6B, 0x5B3A9A, 0xF3EBFF, 0x9944BE}, {0xFF9A7B, 0xFFF1EC, 0x3D151C, 0xE8587A}
    };

    public static void main(String[] a) {
        // ---- contrast helper
        t("black/white 21", near(LookColors.contrast(0xFF000000, 0xFFFFFFFF), 21.0, 0.01));
        t("same colour 1", near(LookColors.contrast(0xFF336699, 0xFF336699), 1.0, 0.0001));
        t("order does not matter", near(LookColors.contrast(0xFF123456, 0xFFABCDEF), LookColors.contrast(0xFFABCDEF, 0xFF123456), 1e-9));
        t("alpha ignored", near(LookColors.contrast(0x80000000, 0xFFFFFFFF), 21.0, 0.01));
        t("luminance white 1", near(LookColors.luminance(0xFFFFFFFF), 1.0, 1e-6));
        t("luminance black 0", near(LookColors.luminance(0xFF000000), 0.0, 1e-9));
        t("grey 767676 ~4.54", near(LookColors.contrast(0xFF767676, 0xFFFFFFFF), 4.54, 0.02));
        t("lowContrast grey on white (777777 -> 4.48)", LookColors.lowContrast(0xFF777777, 0xFFFFFFFF));
        t("lowContrast 767676 on white ok", !LookColors.lowContrast(0xFF767676, 0xFFFFFFFF));
        t("lowContrast white on light", LookColors.lowContrast(0xFFFFFFFF, 0xFFEEEEEE));
        t("minOnBg uses worst of two", near(LookColors.minOnBg(0xFF000000, 0xFFFFFFFF, true, 0xFF000000), 1.0, 0.0001));
        t("minOnBg no bg2", near(LookColors.minOnBg(0xFF000000, 0xFFFFFFFF, false, 0xFF000000), 21.0, 0.01));
        t("MIN_RATIO 4.5", LookColors.MIN_RATIO == 4.5);

        // ---- noteColor: Classic EXACTLY as before; every theme >= 4.5 on bg and bg2
        t("Classic note stays 555555", LookColors.noteColor(0xFFDDDDDD, false, 0, false, 0) == 0xFF555555);
        t("Dark note stays B8B8B8", LookColors.noteColor(0xFF1E1F22, false, 0, true, 0xFFF2F2F2) == 0xFFB8B8B8);
        t("Gloss Black note stays B8B8B8", LookColors.noteColor(0xFF0B0B0D, false, 0, true, 0xFFFFFFFF) == 0xFFB8B8B8);
        t("Blue note stays 555555", LookColors.noteColor(0xFFD6E6F5, false, 0, true, 0xFF1B3A57) == 0xFF555555);
        t("Silver Grey note -> text colour", LookColors.noteColor(0xFFB9BDC4, false, 0, true, 0xFF23262B) == 0xFF23262B);
        t("Ocean note -> white", LookColors.noteColor(0xFF1C6BC0, false, 0, true, 0xFFFFFFFF) == 0xFFFFFFFF);
        t("no text colour, mid grey -> white or black", (LookColors.noteColor(0xFF808080, false, 0, false, 0) & 0xFFFFFF) == 0xFFFFFF || (LookColors.noteColor(0xFF808080, false, 0, false, 0) & 0xFFFFFF) == 0);
        int worstNote = 0;
        StringBuilder table = new StringBuilder();
        for (int i = 0; i < TH.length; i++) {
            int bg = 0xFF000000 | TH[i][0];
            boolean b2 = TH[i][3] >= 0; int bg2 = 0xFF000000 | Math.max(TH[i][3], 0);
            int key = 0xFF000000 | (TH[i][1] < 0 ? 0xFFFFFF : TH[i][1]);
            int tx = 0xFF000000 | TH[i][2];
            int note = LookColors.noteColor(bg, b2, bg2, i != 0, tx);
            int fn = LookColors.funcFill(i);
            t("theme " + i + " key/text >= 4.5", LookColors.contrast(key, tx) >= 4.5);
            t("theme " + i + " func/text >= 4.5", LookColors.contrast(0xFF000000 | fn, tx) >= 4.5);
            t("theme " + i + " bg/text >= 4.5", LookColors.minOnBg(tx, bg, b2, bg2) >= 4.5);
            t("theme " + i + " bg/note >= 4.5", LookColors.minOnBg(note, bg, b2, bg2) >= 4.5);
            table.append(String.format(Locale.US, "%2d key/text %5.2f func/text %5.2f bg/text %5.2f bg/note %5.2f%n", i, LookColors.contrast(key, tx), LookColors.contrast(0xFF000000 | fn, tx), LookColors.minOnBg(tx, bg, b2, bg2), LookColors.minOnBg(note, bg, b2, bg2)));
        }
        if (a.length > 0) System.out.print(table);

        // ---- presets
        t("4 presets", LookPresets.count() == 4 && LookPresets.NAMES.length == 4);
        t("names", LookPresets.name(0).equals("AMOLED") && LookPresets.name(1).equals("Reading") && LookPresets.name(2).equals("Professional") && LookPresets.name(3).equals("Cyber"));
        t("bad name index", LookPresets.name(-1).isEmpty() && LookPresets.name(4).isEmpty());
        t("bad index values empty", LookPresets.values(9).isEmpty() && LookPresets.values(-1).isEmpty());
        t("bad index profile null", LookPresets.profile(4) == null && LookPresets.profile(-1) == null);
        t("used keys all whitelisted", allWhitelisted(LookPresets.USED_KEYS));
        Set<Integer> themes = new HashSet<Integer>();
        for (int i = 0; i < 4; i++) {
            Map<String, Object> v = LookPresets.values(i);
            t("preset " + i + " validate", LookPresets.validate(v));
            t("preset " + i + " keys whitelisted", allWhitelisted(v.keySet().toArray(new String[0])));
            t("preset " + i + " only used keys", new HashSet<String>(Arrays.asList(LookPresets.USED_KEYS)).equals(v.keySet()));
            int th = (Integer) v.get("theme");
            t("preset " + i + " theme 0..19", th >= 0 && th <= 19);
            themes.add(th);
            t("preset " + i + " look = Jabra", ((Integer) v.get("look")) == 1);
            t("preset " + i + " no null values", !v.containsValue(null));
            t("preset " + i + " deterministic", v.equals(LookPresets.values(i)));
            t("preset " + i + " fresh map each call", LookPresets.values(i) != LookPresets.values(i));
            ProfileCodec.Profile p = LookPresets.profile(i);
            t("preset " + i + " profile name", p != null && p.name.equals(LookPresets.name(i)));
            t("preset " + i + " clean keeps all 5", p != null && p.values.size() == 5);
            t("preset " + i + " type of key_gap", ProfileCodec.typeOf("key_gap") == 'i' && v.get("key_gap") instanceof Integer);
            t("preset " + i + " type of letter_bold", v.get("letter_bold") instanceof Boolean);
            int th2 = th;
            t("preset " + i + " theme text readable", LookColors.contrast(0xFF000000 | (TH[th2][1] < 0 ? 0xFFFFFF : TH[th2][1]), 0xFF000000 | TH[th2][2]) >= 4.5);
            // round trip through the profile file text
            List<ProfileCodec.Profile> one = new ArrayList<ProfileCodec.Profile>(); one.add(p);
            List<ProfileCodec.Profile> back = ProfileCodec.parse(ProfileCodec.toJson(one));
            t("preset " + i + " file round trip", back != null && back.size() == 1 && back.get(0).values.equals(p.values));
        }
        t("4 different themes", themes.size() == 4);
        t("AMOLED = Gloss Black (8)", (Integer) LookPresets.values(0).get("theme") == 8);
        t("Cyber is bold", Boolean.TRUE.equals(LookPresets.values(3).get("letter_bold")));
        t("presets 0..2 not bold", Boolean.FALSE.equals(LookPresets.values(0).get("letter_bold")) && Boolean.FALSE.equals(LookPresets.values(1).get("letter_bold")) && Boolean.FALSE.equals(LookPresets.values(2).get("letter_bold")));
        // validate rejects bad maps
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        t("validate null / empty", !LookPresets.validate(null) && !LookPresets.validate(m));
        m.put("theme", 20); t("validate theme 20 (custom slot) rejected", !LookPresets.validate(m));
        m.put("theme", 5); t("validate theme 5 ok", LookPresets.validate(m));
        m.put("theme", -1); t("validate theme -1 rejected", !LookPresets.validate(m));
        m.put("theme", 5); m.put("ct_json", "x"); t("validate foreign key rejected", !LookPresets.validate(m));
        m.remove("ct_json"); m.put("look", "x"); t("validate wrong type rejected", !LookPresets.validate(m));
        m.clear(); m.put("look", 1); t("validate without theme rejected", !LookPresets.validate(m));
        // presets never carry custom theme colours / clips / vault
        for (int i = 0; i < 4; i++) t("preset " + i + " has no ct_json", !LookPresets.values(i).containsKey("ct_json"));

        System.out.println("LookPresetsTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
    static boolean allWhitelisted(String[] ks) { for (String k : ks) if (!ProfileCodec.allowed(k)) return false; return true; }
}
