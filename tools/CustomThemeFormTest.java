import com.mgb.keyboard.typing.CustomTheme;
import com.mgb.keyboard.typing.CustomThemeForm;
import java.util.List;

/** Run: javac -d out app/src/main/java/com/mgb/keyboard/typing/{CustomTheme,LookColors,CustomThemeForm}.java tools/CustomThemeFormTest.java && java -cp out CustomThemeFormTest */
public class CustomThemeFormTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (java.util.Objects.equals(a, b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    static String[] F(String... s) { return s; }
    public static void main(String[] x) {
        String[] good = F("#112233", "", "FFFFFF", "#000", "abcdef");
        eq("fieldOk bg", CustomThemeForm.fieldOk(0, "#112233"), true);
        eq("fieldOk bg no hash", CustomThemeForm.fieldOk(0, "112233"), true);
        eq("fieldOk short", CustomThemeForm.fieldOk(0, "#abc"), true);
        eq("fieldOk lower", CustomThemeForm.fieldOk(2, "ffeedd"), true);
        eq("fieldOk spaces", CustomThemeForm.fieldOk(3, "  #123456 "), true);
        eq("fieldOk bad letters", CustomThemeForm.fieldOk(0, "#12345G"), false);
        eq("fieldOk bad length", CustomThemeForm.fieldOk(0, "#12345"), false);
        eq("fieldOk empty bg", CustomThemeForm.fieldOk(0, ""), false);
        eq("fieldOk null bg", CustomThemeForm.fieldOk(0, null), false);
        eq("fieldOk empty bg2 ok", CustomThemeForm.fieldOk(1, ""), true);
        eq("fieldOk null bg2 ok", CustomThemeForm.fieldOk(1, null), true);
        eq("fieldOk blank bg2 ok", CustomThemeForm.fieldOk(1, "   "), true);
        eq("fieldOk empty key", CustomThemeForm.fieldOk(2, ""), false);
        eq("fieldOk empty func", CustomThemeForm.fieldOk(4, ""), false);
        eq("fieldOk bad index", CustomThemeForm.fieldOk(5, "#111111"), false);
        eq("fieldOk neg index", CustomThemeForm.fieldOk(-1, "#111111"), false);
        eq("allOk good", CustomThemeForm.allOk(good), true);
        eq("allOk null", CustomThemeForm.allOk(null), false);
        eq("allOk short array", CustomThemeForm.allOk(F("#111111", "")), false);
        eq("check length", CustomThemeForm.check(null).length, 5);
        eq("check flags", java.util.Arrays.toString(CustomThemeForm.check(F("#111111", "zz", "", "#222222", "#333333"))), "[true, false, false, true, true]");
        CustomTheme c = CustomThemeForm.build(good, 45, 100);
        eq("build ok", c != null, true);
        eq("build bg", c.bg, 0xFF112233);
        eq("build no bg2", c.hasBg2, false);
        eq("build key", c.key, 0xFFFFFFFF);
        eq("build text short hex", c.text, 0xFF000000);
        eq("build func", c.func, 0xFFABCDEF);
        eq("build round", c.round, 45);
        CustomTheme g = CustomThemeForm.build(F("#112233", "#445566", "#FFFFFF", "#000000", "#CCCCCC"), 10, 80);
        eq("build bg2", g.hasBg2, true);
        eq("build bg2 value", g.bg2, 0xFF445566);
        eq("build alpha", g.alpha, 80);
        eq("build invalid = null", CustomThemeForm.build(F("#112233", "", "nope", "#000000", "#CCCCCC"), 45, 100), null);
        eq("build null fields", CustomThemeForm.build(null, 45, 100), null);
        eq("round clamp high", CustomThemeForm.build(good, 999, 100).round, 100);
        eq("round clamp low", CustomThemeForm.build(good, -5, 100).round, 0);
        eq("alpha clamp low", CustomThemeForm.build(good, 45, 1).alpha, 40);
        eq("alpha clamp high", CustomThemeForm.build(good, 45, 500).alpha, 100);
        // round trip with the saved JSON shape
        CustomTheme back = CustomTheme.parse(g.toJson());
        eq("json round trip bg", back.bg, g.bg);
        eq("json round trip bg2", back.hasBg2, true);
        eq("json round trip alpha", back.alpha, 80);
        eq("json has ver 1", g.toJson().startsWith("{\"ver\":1,"), true);
        String[] f = CustomThemeForm.fields(g);
        eq("fields bg2", f[1], "#445566");
        eq("fields no bg2 empty", CustomThemeForm.fields(c)[1], "");
        eq("fields null = defaults", CustomThemeForm.fields(null)[0], CustomTheme.toHex(CustomTheme.defaults().bg));
        eq("fields -> build same json", CustomThemeForm.build(CustomThemeForm.fields(g), g.round, g.alpha).toJson(), g.toJson());
        // contrast warnings
        eq("warn none (black on white)", CustomThemeForm.warnings(CustomThemeForm.build(F("#FFFFFF", "", "#FFFFFF", "#000000", "#EEEEEE"), 45, 100)).size(), 0);
        List<String> w = CustomThemeForm.warnings(CustomThemeForm.build(F("#FFFFFF", "", "#FFFFFF", "#EEEEEE", "#FFFFFF"), 45, 100));
        eq("warn both", w.size(), 2);
        eq("warn text mentions keys", w.get(0).contains("keys"), true);
        eq("warn null", CustomThemeForm.warnings(null).size(), 0);
        eq("names 5", CustomThemeForm.NAMES.length, 5);
        System.out.println("CustomThemeFormTest " + ok + " ok, " + bad + " fail");
        if (bad > 0) System.exit(1);
    }
}
