import com.mgb.keyboard.typing.PanelKeep;

public class PanelKeepTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    public static void main(String[] a) {
        t("restart + panel open = keep", PanelKeep.keepPanel(true, true, false));
        t("new field (not restart) = old", !PanelKeep.keepPanel(false, true, false));
        t("letters on screen = nothing to keep", !PanelKeep.keepPanel(true, false, false));
        t("auto number pad = old behaviour", !PanelKeep.keepPanel(true, true, true));
        t("new field, letters", !PanelKeep.keepPanel(false, false, false));
        t("new field, auto number pad", !PanelKeep.keepPanel(false, true, true));
        t("restart, letters, auto number pad", !PanelKeep.keepPanel(true, false, true));
        t("new field, letters, auto number pad", !PanelKeep.keepPanel(false, false, true));
        t("row reset: panel open = keep", PanelKeep.keepForRowReset(true, false));
        t("row reset: letters = old", !PanelKeep.keepForRowReset(false, false));
        t("row reset: auto number pad = old", !PanelKeep.keepForRowReset(true, true));
        t("row reset: letters + auto numpad flag = old", !PanelKeep.keepForRowReset(false, true));
        System.out.println("PanelKeepTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
