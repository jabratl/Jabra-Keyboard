import com.mgb.keyboard.layout.*;
import java.util.*;

/** v0.17.19 (L3): own bottom row per language (layers bot_av / bot_pb): ops, codec round trip, old layouts, danda-dot ids, warnings. */
public class LayoutBottomTest {
    static int ok = 0, bad = 0;
    static void eq(String n, Object a, Object b) { if (a == null ? b == null : a.equals(b)) ok++; else { bad++; System.out.println("FAIL " + n + ": " + a + " != " + b); } }
    static void tr(String n, boolean c) { eq(n, c, true); }

    static KeyDef find(LayoutDef m, String layer, String id) {
        LayerDef l = m.layer(layer);
        if (l == null) return null;
        for (RowDef r : l.rows) for (KeyDef k : r.keys) if (k.id.equals(id)) return k;
        return null;
    }

    public static void main(String[] a) {
        LayoutDef d = LayoutDefaults.build();
        eq("default en refs bot", d.botRef("en"), "bot");
        eq("default av refs bot", d.botRef("av"), "bot");
        eq("default pb refs bot", d.botRef("pb"), "bot");
        tr("default has no bot_av", d.layer("bot_av") == null);
        tr("default has no bot_pb", d.layer("bot_pb") == null);
        eq("default valid", LayoutOps.valid(d), null);
        eq("default max layers", LayoutDef.MAX_LAYERS, 10);

        // ---- separate Probhat ----
        LayoutDef s = LayoutOps.separateBottom(d, "pb");
        tr("separate pb changes the model", s != d);
        eq("separate pb valid", LayoutOps.valid(s), null);
        tr("bot_pb layer exists", s.layer("bot_pb") != null);
        eq("pb refs bot_pb", s.botRef("pb"), "bot_pb");
        eq("av still refs bot", s.botRef("av"), "bot");
        eq("en still refs bot", s.botRef("en"), "bot");
        tr("original untouched", d.layer("bot_pb") == null && d.botRef("pb").equals("bot"));
        tr("copy keys have suffixed ids", find(s, "bot_pb", "b_period_pb") != null && find(s, "bot_pb", "b_space_pb") != null);
        tr("original ids kept in bot", find(s, "bot", "b_period") != null);
        eq("copy has same key count", s.layer("bot_pb").rows.get(0).keys.size(), s.layer("bot").rows.get(0).keys.size());
        eq("effective rows of pb use the copy", s.effectiveRows("pb").get(s.effectiveRows("pb").size() - 1).keys.get(0).id.endsWith("_pb"), true);
        eq("effective rows of av use the shared one", s.effectiveRows("av").get(s.effectiveRows("av").size() - 1).keys.get(0).id.endsWith("_pb"), false);
        tr("no fatal after separate", LayoutOps.fatal(s).isEmpty());
        tr("separate twice refused (same instance)", LayoutOps.separateBottom(s, "pb") == s);
        tr("separate en refused", LayoutOps.separateBottom(d, "en") == d);
        tr("separate unknown refused", LayoutOps.separateBottom(d, "zz") == d);
        tr("separate null safe", LayoutOps.separateBottom(null, "pb") == null);

        // ---- separate Avro too, edit the copy only ----
        LayoutDef s2 = LayoutOps.separateBottom(s, "av");
        eq("both own valid", LayoutOps.valid(s2), null);
        eq("av refs bot_av", s2.botRef("av"), "bot_av");
        tr("both copies + shared = 3 bottom layers", s2.layer("bot") != null && s2.layer("bot_av") != null && s2.layer("bot_pb") != null);
        LayoutDef e = LayoutOps.setOut(s2, "b_period_pb", 0, "\u0964");
        tr("edit of copy accepted", e != s2);
        eq("pb copy changed", find(e, "bot_pb", "b_period_pb").out[0], "\u0964");
        eq("shared dot unchanged", find(e, "bot", "b_period").out[0], find(s2, "bot", "b_period").out[0]);
        eq("avro dot unchanged", find(e, "bot_av", "b_period_av").out[0], find(s2, "bot_av", "b_period_av").out[0]);

        // ---- share again ----
        LayoutDef back = LayoutOps.shareBottom(e, "pb");
        tr("share changes the model", back != e);
        eq("share valid", LayoutOps.valid(back), null);
        tr("bot_pb gone", back.layer("bot_pb") == null);
        eq("pb refs bot again", back.botRef("pb"), "bot");
        tr("bot_av kept", back.layer("bot_av") != null);
        tr("share without copy refused", LayoutOps.shareBottom(back, "pb") == back);
        tr("share en refused", LayoutOps.shareBottom(back, "en") == back);
        LayoutDef back2 = LayoutOps.shareBottom(back, "av");
        eq("all shared equals the default", back2.sig(), d.sig());

        // ---- layer limit ----
        LayoutDef full = d.copy();
        while (full.layers.size() < LayoutDef.MAX_LAYERS) full.layers.add(new LayerDef("x" + full.layers.size()));
        tr("limit refuses separate", LayoutOps.separateBottom(full, "pb") == full);

        // ---- codec ----
        String t = LayoutCodec.encode(s2);
        tr("encode ok", t != null);
        LayoutDef r = LayoutCodec.decode(t);
        tr("decode ok", r != null);
        if (r != null) {
            eq("round trip sig", r.sig(), s2.sig());
            eq("round trip av ref", r.botRef("av"), "bot_av");
            eq("round trip pb ref", r.botRef("pb"), "bot_pb");
        }
        LayoutDef oldR = LayoutCodec.decode(LayoutCodec.encode(d));
        tr("old layout (no own rows) still decodes", oldR != null && oldR.botRef("pb").equals("bot") && oldR.layer("bot_pb") == null);
        // a ref to a missing own layer is dropped by the decoder (row dropped, never a crash)
        LayoutDef broken = s.copy();
        broken.layers.remove(broken.layer("bot_pb"));
        String bt = LayoutCodec.encode(broken);
        LayoutDef bd = bt == null ? null : LayoutCodec.decode(bt);
        tr("dangling own ref never throws", true);

        // ---- check ----
        tr("no HIGH warning with own rows", noHigh(LayoutCheck.check(s2)));
        tr("no W_DUP between a copy and the shared row", !hasCode(LayoutCheck.check(s2), "W_DUP"));

        // ---- danda-dot rule knows the suffix ----
        eq("mapDot copy pb", LayoutRoute.mapDot("b_period_pb", ".", true, true), "\u0964");
        eq("mapDot copy av long", LayoutRoute.mapDot("b_period_av", "?", true, true), ".");
        eq("mapDot copy english", LayoutRoute.mapDot("b_period_pb", ".", false, true), ".");
        eq("mapDot user text untouched", LayoutRoute.mapDot("b_period_pb", "x", true, true), "x");
        eq("mapDot other key", LayoutRoute.mapDot("b_comma_pb", ".", true, true), ".");
        eq("dotLabel copy", LayoutRoute.dotLabel("b_period_pb", ".", true, true, true), "\u0964");
        eq("qmLabel copy", LayoutRoute.qmLabel("b_qm_av", "?", true), "\u0964");
        eq("mapQm base id", LayoutRoute.mapQm("b_qm", "?", true), "\u0964");

        // ---- text helpers ----
        tr("isShareId", LayoutText.isShareId("nr") && LayoutText.isShareId("bot") && LayoutText.isShareId("bot_av") && LayoutText.isShareId("bot_pb") && !LayoutText.isShareId("en") && !LayoutText.isShareId(null));
        eq("botLayerOf", LayoutText.botLayerOf("av") + LayoutText.botLayerOf("pb") + LayoutText.botLayerOf("en"), "bot_avbot_pbbot");

        System.out.println("LayoutBottomTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }

    static boolean hasCode(List<LayoutCheck.Warn> w, String code) { for (LayoutCheck.Warn x : w) if (code.equals(x.code)) return true; return false; }
    static boolean noHigh(List<LayoutCheck.Warn> w) { for (LayoutCheck.Warn x : w) if (x.severity >= LayoutCheck.HIGH) return false; return true; }
}
