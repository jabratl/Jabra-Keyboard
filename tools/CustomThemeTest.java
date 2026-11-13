import com.mgb.keyboard.typing.CustomTheme;
import com.mgb.keyboard.typing.CustomThemeForm;

/** v0.4.8 CustomTheme tests. javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/CustomThemeTest.java && java -cp out CustomThemeTest */
public class CustomThemeTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] x) {
        t("hex 6", CustomTheme.hex("#1E1F22") == 0xFF1E1F22 && CustomTheme.hex("1e1f22") == 0xFF1E1F22);
        t("hex 3", CustomTheme.hex("#fa0") == 0xFFFFAA00);
        t("hex bad", CustomTheme.hex("#12345") == null && CustomTheme.hex("#GGGGGG") == null && CustomTheme.hex("") == null && CustomTheme.hex(null) == null && CustomTheme.hex("#1234567") == null);
        t("hex trims", CustomTheme.hex("  #FFFFFF ") == 0xFFFFFFFF);
        t("toHex", CustomTheme.toHex(0xFF0A0B0C).equals("#0A0B0C") && CustomTheme.toHex(0x00FFFFFF).equals("#FFFFFF"));
        t("clamp", CustomTheme.clamp(5, 0, 3) == 3 && CustomTheme.clamp(-1, 0, 3) == 0 && CustomTheme.clamp(2, 0, 3) == 2);

        CustomTheme d = CustomTheme.defaults();
        t("defaults", d.round == 45 && d.alpha == 100 && !d.hasBg2 && (d.bg >>> 24) == 0xFF);
        t("radius dp", d.radiusDp() > 8.99f && d.radiusDp() < 9.01f);
        CustomTheme r0 = new CustomTheme(); r0.round = 0; CustomTheme r1 = new CustomTheme(); r1.round = 100;
        t("radius range", r0.radiusDp() == 0f && r1.radiusDp() > 19.99f);
        CustomTheme a = new CustomTheme(); a.alpha = 50;
        t("alpha applied", (a.withKeyAlpha(0xFF112233) >>> 24) == 128 && (a.withKeyAlpha(0xFF112233) & 0xFFFFFF) == 0x112233);
        t("alpha 100 opaque", (d.withKeyAlpha(0xFF112233) >>> 24) == 0xFF);

        // json round trip
        CustomTheme c = new CustomTheme();
        c.bg = 0xFF000000; c.hasBg2 = true; c.bg2 = 0xFF1B0B3A; c.key = 0xFF141B3D; c.text = 0xFF00FFC8; c.func = 0xFF2A1458; c.round = 80; c.alpha = 70;
        String js = c.toJson();
        CustomTheme p = CustomTheme.parse(js);
        t("round trip", p != null && p.toJson().equals(js) && p.bg == c.bg && p.hasBg2 && p.bg2 == c.bg2 && p.key == c.key && p.text == c.text && p.func == c.func && p.round == 80 && p.alpha == 70);
        CustomTheme n2 = CustomTheme.parse(CustomTheme.defaults().toJson());
        t("no bg2 round trip", n2 != null && !n2.hasBg2 && n2.toJson().equals(CustomTheme.defaults().toJson()));
        t("json has ver", js.startsWith("{\"ver\":1,"));

        // parse safety
        t("garbage = null", CustomTheme.parse("hello") == null && CustomTheme.parse("") == null && CustomTheme.parse(null) == null && CustomTheme.parse("{}") == null);
        t("no ver = null", CustomTheme.parse("{\"bg\":\"#000000\"}") == null);
        t("ver 2 = null", CustomTheme.parse("{\"ver\":2,\"bg\":\"#000000\"}") == null);
        CustomTheme tol = CustomTheme.parse("{\"ver\":1,\"bg\":\"#102030\",\"key\":\"nope\",\"round\":999,\"alpha\":1,\"extra\":5}");
        t("tolerant: bad colour = default", tol != null && tol.bg == 0xFF102030 && tol.key == CustomTheme.defaults().key);
        t("tolerant: clamps", tol != null && tol.round == 100 && tol.alpha == 40);
        CustomTheme ver1only = CustomTheme.parse("{\"ver\":1}");
        t("ver only = defaults", ver1only != null && ver1only.toJson().equals(CustomTheme.defaults().toJson()));

        // presets
        t("4 presets", CustomTheme.PRESET_NAMES.length == 4);
        t("amoled is black", CustomTheme.preset(0).bg == 0xFF000000 && CustomTheme.preset(0).text == 0xFFFFFFFF);
        t("cyber has gradient", CustomTheme.preset(2).hasBg2);
        t("presets parse back", CustomTheme.parse(CustomTheme.preset(1).toJson()) != null && CustomTheme.parse(CustomTheme.preset(3).toJson()) != null);
        t("unknown preset = defaults", CustomTheme.preset(99).toJson().equals(CustomTheme.defaults().toJson()));
        boolean allOpaque = true; for (int i = 0; i < 4; i++) { CustomTheme q = CustomTheme.preset(i); allOpaque &= (q.bg >>> 24) == 0xFF && (q.key >>> 24) == 0xFF && (q.text >>> 24) == 0xFF && (q.func >>> 24) == 0xFF; }
        t("presets opaque", allOpaque);

        // v0.17.45 (P2) key outline
        t("border default 0", CustomTheme.defaults().border == 0 && !CustomTheme.defaults().toJson().contains("border"));
        CustomTheme bt = CustomTheme.defaults(); bt.border = 4;
        CustomTheme bp = CustomTheme.parse(bt.toJson());
        t("border round trip", bp != null && bp.border == 4 && bp.toJson().equals(bt.toJson()));
        t("border clamp high", CustomTheme.parse("{\"ver\":1,\"border\":99}").border == 6);
        t("border clamp low", CustomTheme.parse("{\"ver\":1,\"border\":-3}") == null || CustomTheme.parse("{\"ver\":1,\"border\":-3}").border == 0);
        t("old json no border = 0", CustomTheme.parse("{\"ver\":1,\"round\":50}").border == 0);
        t("borderDp", Math.abs(bt.borderDp() - 2.0f) < 0.001f);
        t("border colour 50% of text", (bt.borderColor() >>> 24) == 0x80 && (bt.borderColor() & 0xFFFFFF) == (bt.text & 0xFFFFFF));
        String[] bf = CustomThemeForm.fields(CustomTheme.defaults());
        t("form build border", CustomThemeForm.build(bf, 45, 100, 3).border == 3 && CustomThemeForm.build(bf, 45, 100).border == 0 && CustomThemeForm.build(bf, 45, 100, 50).border == 6);

        System.out.println("CustomThemeTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
