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
        // v0.17.18 (L1): danda-dot rule
        final String D = "\u0964";
        t("default ON", LayoutRoute.DANDA_DEF);
        t("rule needs ? key hidden", LayoutRoute.dandaDot(true, false) && !LayoutRoute.dandaDot(true, true) && !LayoutRoute.dandaDot(false, false));
        t("classic tap bangla = danda", LayoutRoute.dotTap(true, true).equals(D));
        t("classic tap english = dot", LayoutRoute.dotTap(true, false).equals("."));
        t("classic tap rule off = dot", LayoutRoute.dotTap(false, true).equals("."));
        t("classic long bangla rule on = dot", LayoutRoute.dotLong(true, true).equals("."));
        t("classic long bangla rule off = danda (old)", LayoutRoute.dotLong(false, true).equals(D));
        t("classic long english = ? (old)", LayoutRoute.dotLong(true, false).equals("?") && LayoutRoute.dotLong(false, false).equals("?"));
        t("classic label", LayoutRoute.dotLabel(true, true).equals(D) && LayoutRoute.dotLabel(true, false).equals(".") && LayoutRoute.dotLabel(false, true).equals("."));
        t("custom tap bangla", LayoutRoute.mapDot("b_period", ".", true, true).equals(D));
        t("custom long bangla", LayoutRoute.mapDot("b_period", "?", true, true).equals("."));
        t("custom english unchanged", LayoutRoute.mapDot("b_period", ".", false, true).equals(".") && LayoutRoute.mapDot("b_period", "?", false, true).equals("?"));
        t("custom rule off = old mapQm", LayoutRoute.mapDot("b_period", "?", true, false).equals(D) && LayoutRoute.mapDot("b_period", ".", true, false).equals("."));
        t("custom user text untouched", LayoutRoute.mapDot("b_period", ";", true, true).equals(";") && LayoutRoute.mapDot("b_period", D, true, true).equals(D));
        t("custom other key untouched", LayoutRoute.mapDot("b_comma", ".", true, true).equals(".") && LayoutRoute.mapDot("b_qm", "?", true, true).equals(D));
        t("custom null", LayoutRoute.mapDot("b_period", null, true, true).equals(""));
        t("custom label", LayoutRoute.dotLabel("b_period", ".", true, true, true).equals(D) && LayoutRoute.dotLabel("b_period", ".", true, false, true).equals(".")
                && LayoutRoute.dotLabel("b_qm", "?", true, true, true).equals(D) && LayoutRoute.dotLabel("b_comma", ",", true, true, true).equals(","));
        // factory default layout really has the keys the rule looks for (b_period "." with long-press "?")
        { LayoutDef dm = LayoutDefaults.build(); KeyDef pk = find(dm, "bot", "b_period");
          t("default b_period is '.' + lp '?'", pk != null && pk.out[0].equals(".") && pk.lp.equals("?")); }
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
