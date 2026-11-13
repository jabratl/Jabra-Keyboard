import com.mgb.keyboard.layout.*;

/** v0.17.6 (K5a.2) routing decisions of the custom keyboard. */
public class LayoutRouteTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }

    public static void main(String[] a) {
        // layer for the language
        t("english", LayoutRoute.layerFor(false, false, false).equals("en"));
        t("english + probhat flag", LayoutRoute.layerFor(false, false, true).equals("en"));
        t("avro", LayoutRoute.layerFor(true, false, false).equals("av"));
        t("probhat", LayoutRoute.layerFor(true, false, true).equals("pb"));
        t("latinOnly avro = en", LayoutRoute.layerFor(true, true, false).equals("en"));
        t("latinOnly probhat = en", LayoutRoute.layerFor(true, true, true).equals("en"));
        t("latinOnly english = en", LayoutRoute.layerFor(false, true, false).equals("en"));
        // av buffer
        t("a", LayoutRoute.avBuffer("a"));
        t("Z", LayoutRoute.avBuffer("Z"));
        t("digit no", !LayoutRoute.avBuffer("1"));
        t("two letters no", !LayoutRoute.avBuffer("ab"));
        t("empty no", !LayoutRoute.avBuffer(""));
        t("null no", !LayoutRoute.avBuffer(null));
        t("bangla letter no", !LayoutRoute.avBuffer("\u0995"));
        t("comma no", !LayoutRoute.avBuffer(","));
        t("emoji no", !LayoutRoute.avBuffer("\uD83D\uDE00"));
        // ? / danda rules
        t("qm bangla = danda", LayoutRoute.mapQm("b_qm", "?", true).equals("\u0964"));
        t("qm english = ?", LayoutRoute.mapQm("b_qm", "?", false).equals("?"));
        t("period long-press bangla = danda", LayoutRoute.mapQm("b_period", "?", true).equals("\u0964"));
        t("period long-press english = ?", LayoutRoute.mapQm("b_period", "?", false).equals("?"));
        t("other key keeps ?", LayoutRoute.mapQm("en_q", "?", true).equals("?"));
        t("other text kept on qm key", LayoutRoute.mapQm("b_qm", "!", true).equals("!"));
        t("null text", LayoutRoute.mapQm("b_qm", null, true).equals(""));
        t("null id", LayoutRoute.mapQm(null, "?", true).equals("?"));
        t("qm label bangla", LayoutRoute.qmLabel("b_qm", "?", true).equals("\u0964"));
        t("qm label english", LayoutRoute.qmLabel("b_qm", "?", false).equals("?"));
        t("label other key", LayoutRoute.qmLabel("b_period", ".", true).equals("."));
        t("label null", LayoutRoute.qmLabel("x", null, true).equals(""));
        // digits
        t("digit 0", LayoutRoute.isDigit("0"));
        t("digit 9", LayoutRoute.isDigit("9"));
        t("not digit a", !LayoutRoute.isDigit("a"));
        t("not digit 12", !LayoutRoute.isDigit("12"));
        t("not digit null", !LayoutRoute.isDigit(null));
        // rebuild rule
        t("old unedited = rebuild", LayoutRoute.needsRebuild(0, false));
        t("old edited = keep", !LayoutRoute.needsRebuild(0, true));
        t("current = keep", !LayoutRoute.needsRebuild(LayoutRoute.BUILT_VERSION, false));
        t("newer = keep", !LayoutRoute.needsRebuild(99, false));
        // defaults: ids the renderer relies on exist, and the language rules hit the real keys
        LayoutDef d = LayoutDefaults.build();
        LayoutDef q = qmDefaults();
        t("default bottom has b_period", find(d, "bot", "b_period") != null);
        t("b_period long-press is ?", find(d, "bot", "b_period") != null && find(d, "bot", "b_period").lp.equals("?"));
        t("qm model has b_qm", find(q, "bot", "b_qm") != null && find(q, "bot", "b_qm").out[0].equals("?"));
        t("comma long-press = @emoji", find(d, "bot", "b_comma").lp.equals("@emoji"));
        for (String l : new String[] {"en", "av", "pb"}) {
            LayerDef ly = d.layer(l);
            boolean nr = false, bot = false;
            for (RowDef r : ly.rows) { if (r.share.equals("nr")) nr = true; if (r.share.equals("bot")) bot = true; }
            t(l + " refs nr + bot", nr && bot);
            t(l + " has own rows", ly.keyCount() > 10);
        }
        t("number row layer exists", d.layer("nr") != null && d.layer("nr").rows.get(0).keys.size() == 10);
        // mode key index: sym + lang first
        RowDef bot = d.layer("bot").rows.get(0);
        t("mode key index = 2 (sym, lang)", LayoutRoute.modeKeyIndex(bot) == 2);
        t("mode key index null", LayoutRoute.modeKeyIndex(null) == 0);
        RowDef noLang = bot.copy();
        noLang.keys.remove(1);
        t("mode key index = 1 (sym only)", LayoutRoute.modeKeyIndex(noLang) == 1);
        RowDef first = new RowDef();
        first.keys.add(KeyDef.ch("x", "a", 1f));
        t("mode key index 0 when first is a char", LayoutRoute.modeKeyIndex(first) == 0);
        System.out.println("LayoutRouteTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }

    static LayoutDef qmDefaults() {
        LayoutDefaults.Options o = new LayoutDefaults.Options();
        o.qmShown = true;
        return LayoutDefaults.build(o);
    }

    static KeyDef find(LayoutDef m, String layer, String id) {
        LayerDef l = m.layer(layer);
        if (l == null) return null;
        for (RowDef r : l.rows) for (KeyDef k : r.keys) if (k.id.equals(id)) return k;
        return null;
    }
}
