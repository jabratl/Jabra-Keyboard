import com.mgb.keyboard.typing.KeyDesc;

/** v0.10.20 (W7) tests for spoken key names. */
public class KeyDescTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static boolean eq(String a, String b) { return a == null ? b == null : a.equals(b); }
    static final String[] ICONS = { "clip", "all", "cut", "copy", "paste", "plain", "search", "close", "pin", "pinned", "gear", "shift", "back", "enter", "space", "fmt", "globe", "mic", "emoji", "mode_phone", "mode_pc", "numpad", "sym", "theme", "onehand", "size", "pad", "vault", "tt", "profiles" };

    public static void main(String[] a) {
        t("shift", eq(KeyDesc.describe("⇧"), "Shift"));
        t("backspace", eq(KeyDesc.describe("⌫"), "Backspace"));
        t("enter", eq(KeyDesc.describe("⏎"), "Enter"));
        t("space", eq(KeyDesc.describe("space"), "Space"));
        t("?123", eq(KeyDesc.describe("?123"), "Numbers and symbols"));
        t("ABC", eq(KeyDesc.describe("ABC"), "Letters"));
        t("<<", eq(KeyDesc.describe("«"), "Back"));
        t("emoji", eq(KeyDesc.describe("☺"), "Emoji"));
        t("globe", eq(KeyDesc.describe("🌐"), "Next keyboard"));
        t("mic", eq(KeyDesc.describe("🎤"), "Voice typing"));
        t("comma", eq(KeyDesc.describe(","), "Comma"));
        t("period", eq(KeyDesc.describe("."), "Period"));
        t("undo / redo", eq(KeyDesc.describe("↶"), "Undo") && eq(KeyDesc.describe("↷"), "Redo"));
        t("plus / minus", eq(KeyDesc.describe("+"), "Plus") && eq(KeyDesc.describe("−"), "Minus") && eq(KeyDesc.describe("-"), "Minus"));
        t("close x", eq(KeyDesc.describe("×"), "Close"));
        t("arrows", eq(KeyDesc.describe("←"), "Left") && eq(KeyDesc.describe("→"), "Right") && eq(KeyDesc.describe("↑"), "Up") && eq(KeyDesc.describe("↓"), "Down"));
        t("toolbar short labels", eq(KeyDesc.describe("Th"), "Theme") && eq(KeyDesc.describe("1H"), "One-hand mode") && eq(KeyDesc.describe("Sz"), "Size") && eq(KeyDesc.describe("Pad"), "Cursor pad") && eq(KeyDesc.describe("Vlt"), "Text Vault") && eq(KeyDesc.describe("Tt"), "Text tools") && eq(KeyDesc.describe("Pf"), "Profiles"));
        t("Ren / Del", eq(KeyDesc.describe("Ren"), "Rename") && eq(KeyDesc.describe("Del"), "Delete"));
        t("back prefix", eq(KeyDesc.describe("< More settings"), "Back, More settings"));
        t("back prefix themes", eq(KeyDesc.describe("< Themes"), "Back, Themes"));
        t("app button", eq(KeyDesc.describe("> App"), "Use this profile for this app"));
        t("tick prefix", eq(KeyDesc.describe("✓ Bg"), "Selected, Bg"));
        t("dot prefix ready", eq(KeyDesc.describe("● Compact  (ready)"), "Compact, ready-made"));
        t("undo prefix", eq(KeyDesc.describe("↶ Undo apply"), "Undo, Undo apply"));
        // letters / digits / words: null (screen reader speaks them; labels change)
        for (char c = 'a'; c <= 'z'; c++) t("letter " + c + " null", KeyDesc.describe(String.valueOf(c)) == null && KeyDesc.describe(String.valueOf(Character.toUpperCase(c))) == null);
        for (char c = '0'; c <= '9'; c++) t("digit " + c + " null", KeyDesc.describe(String.valueOf(c)) == null);
        t("Bangla letter null", KeyDesc.describe("ক") == null && KeyDesc.describe("১") == null);
        t("relabelled danda / question not in map", KeyDesc.describe("।") == null && KeyDesc.describe("?") == null);
        t("language key labels null", KeyDesc.describe("EN") == null && KeyDesc.describe("বাং") == null);
        t("plain word null", KeyDesc.describe("Clipboard") == null && KeyDesc.describe("Save") == null);
        t("null / empty / blank", KeyDesc.describe(null) == null && KeyDesc.describe("") == null && KeyDesc.describe("   ") == null);
        t("trim", eq(KeyDesc.describe("  space "), "Space"));
        t("lonely '< ' prefix no crash", KeyDesc.describe("< ") == null || KeyDesc.describe("< ") != null);
        // icons
        for (String i : ICONS) {
            String s = KeyDesc.iconName(i);
            t("icon " + i + " has a spoken name", s != null && s.length() > 0 && !s.equals(i) || i.equals("search") || i.equals("close") || i.equals("space") || i.equals("emoji") || i.equals("theme") || i.equals("size") || i.equals("profiles"));
            t("icon " + i + " not empty", s.length() > 0);
        }
        t("icon shift", eq(KeyDesc.iconName("shift"), "Shift"));
        t("icon back = Backspace", eq(KeyDesc.iconName("back"), "Backspace"));
        t("icon mic", eq(KeyDesc.iconName("mic"), "Voice typing"));
        t("icon gear", eq(KeyDesc.iconName("gear"), "Settings"));
        t("icon unknown = itself", eq(KeyDesc.iconName("zzz"), "zzz"));
        t("icon null = empty", eq(KeyDesc.iconName(null), ""));
        // space
        t("space plain", eq(KeyDesc.spaceDesc("space"), "Space") && eq(KeyDesc.spaceDesc("Space"), "Space") && eq(KeyDesc.spaceDesc(""), "Space") && eq(KeyDesc.spaceDesc(null), "Space"));
        t("space with language", eq(KeyDesc.spaceDesc("English"), "Space, English") && eq(KeyDesc.spaceDesc(" Avro "), "Space, Avro"));
        System.out.println("KeyDescTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
