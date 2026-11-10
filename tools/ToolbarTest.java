import com.mgb.keyboard.typing.ToolbarOrder;
import java.util.Arrays;
import java.util.List;

/** v0.2.1 toolbar order tests (computer only).  javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/typing/*.java tools/ToolbarTest.java && java -cp out ToolbarTest */
public class ToolbarTest {
    static int ok = 0, bad = 0;
    static void t(String name, boolean cond) { if (cond) ok++; else { bad++; System.out.println("FAIL: " + name); } }
    static String s(List<String> l) { return ToolbarOrder.serialize(l); }
    static final String DEF = "fmt,clip,mode,all,cut,copy,paste,plain";

    public static void main(String[] a) {
        t("null -> default", s(ToolbarOrder.parse(null)).equals(DEF));
        t("empty -> default", s(ToolbarOrder.parse("")).equals(DEF));
        t("blank -> default", s(ToolbarOrder.parse("   ")).equals(DEF));
        t("garbage -> default", s(ToolbarOrder.parse("x,y,,;;")).equals(DEF));
        t("full custom kept", s(ToolbarOrder.parse("plain,paste,copy,cut,all,mode,clip,fmt")).equals("plain,paste,copy,cut,all,mode,clip,fmt"));
        t("unknown dropped", s(ToolbarOrder.parse("copy,zzz,fmt")).startsWith("copy,fmt,"));
        t("missing appended (all 8 present)", ToolbarOrder.parse("copy,fmt").size() == 8);
        t("missing appended in default order", s(ToolbarOrder.parse("copy,fmt")).equals("copy,fmt,clip,mode,all,cut,paste,plain"));
        t("duplicates dropped", s(ToolbarOrder.parse("copy,copy,fmt,copy")).equals("copy,fmt,clip,mode,all,cut,paste,plain"));
        t("spaces trimmed", s(ToolbarOrder.parse(" copy , fmt ")).startsWith("copy,fmt,"));
        t("round trip", s(ToolbarOrder.parse(s(ToolbarOrder.parse("paste,fmt")))).equals(s(ToolbarOrder.parse("paste,fmt"))));

        List<String> d = ToolbarOrder.parse("");
        t("moveFirst", s(ToolbarOrder.moveFirst(d, "paste")).equals("paste,fmt,clip,mode,all,cut,copy,plain"));
        t("moveFirst already first", s(ToolbarOrder.moveFirst(d, "fmt")).equals(DEF));
        t("moveFirst unknown = same", s(ToolbarOrder.moveFirst(d, "nope")).equals(DEF));
        t("up", s(ToolbarOrder.up(d, "clip")).equals("clip,fmt,mode,all,cut,copy,paste,plain"));
        t("up on first = same", s(ToolbarOrder.up(d, "fmt")).equals(DEF));
        t("down", s(ToolbarOrder.down(d, "fmt")).equals("clip,fmt,mode,all,cut,copy,paste,plain"));
        t("down on last = same", s(ToolbarOrder.down(d, "plain")).equals(DEF));
        t("original list not changed", s(d).equals(DEF));

        String h = ToolbarOrder.setHidden("", "cut", true);
        t("hide", h.equals("cut") && ToolbarOrder.isHidden(h, "cut"));
        h = ToolbarOrder.setHidden(h, "cut", true);
        t("hide twice = once", h.equals("cut"));
        h = ToolbarOrder.setHidden(h, "plain", true);
        t("hide second", h.equals("cut,plain"));
        t("hide unknown ignored", ToolbarOrder.setHidden(h, "zzz", true).equals("cut,plain"));
        t("visible drops hidden", s(ToolbarOrder.visible("", h)).equals("fmt,clip,mode,all,copy,paste"));
        t("visible keeps custom order", s(ToolbarOrder.visible("paste,fmt", h)).equals("paste,fmt,clip,mode,all,copy"));
        h = ToolbarOrder.setHidden(h, "cut", false);
        t("show", h.equals("plain") && !ToolbarOrder.isHidden(h, "cut"));
        t("hidden garbage", ToolbarOrder.parseHidden("a,b,,copy,copy").equals(Arrays.asList("copy")));
        t("hidden null", ToolbarOrder.parseHidden(null).isEmpty());
        t("all hidden -> visible empty", ToolbarOrder.visible("", DEF).isEmpty());
        t("known", ToolbarOrder.known("fmt") && !ToolbarOrder.known("x") && !ToolbarOrder.known(null));

        System.out.println("ToolbarTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
