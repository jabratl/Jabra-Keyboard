import com.mgb.keyboard.typing.ReplaceEdits;
import com.mgb.keyboard.typing.ReplaceEngine;

public class ReplaceEditsTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + n); } }

    static void roundTrip(String name, String text, String find, String with, ReplaceEngine.Opt o) {
        ReplaceEngine.Result r = ReplaceEngine.replaceAll(text, find, with, o);
        t(name + " edits == changed", r.edits.size() == r.changed);
        t(name + " apply == out", ReplaceEdits.apply(text, r.edits).equals(r.out));
        t(name + " matches", r.changed == 0 || ReplaceEdits.matches(text, r.out, r.edits));
        t(name + " inverse undoes", ReplaceEdits.apply(r.out, ReplaceEdits.inverse(r.edits)).equals(text));
    }

    public static void main(String[] a) {
        ReplaceEngine.Opt o = new ReplaceEngine.Opt();
        roundTrip("text", "one two one three one", "one", "1", o);
        roundTrip("longer", "ab ab ab", "ab", "xyz!", o);
        roundTrip("delete", "a-b-c", "-", "", o);
        ReplaceEngine.Opt rx = new ReplaceEngine.Opt(); rx.mode = ReplaceEngine.MODE_REGEX;
        roundTrip("regex", "a1 b22 c333", "[0-9]+", "#", rx);
        roundTrip("regex groups", "x=1,y=2", "(\\w)=(\\d)", "$2:$1", rx);
        roundTrip("multiline", "l1\nl2\nl3", "l", "L", o);
        roundTrip("korean", "\uC548\uB155 \uC548\uB155", "\uC548", "\uC548!", o);
        ReplaceEngine.Result one = ReplaceEngine.replaceNth("aaa", "a", "b", o, 2);
        t("nth = one edit at 1", one.edits.size() == 1 && one.edits.get(0).start == 1 && ReplaceEdits.apply("aaa", one.edits).equals("aba"));
        ReplaceEngine.Result none = ReplaceEngine.replaceAll("abc", "z", "y", o);
        t("no match = no edits", none.edits.isEmpty() && !ReplaceEdits.matches("abc", "abc", none.edits));
        ReplaceEngine.Result err = ReplaceEngine.replaceAll("abc", "(", "y", rx);
        t("error = no edits", err.err != null && err.edits.isEmpty());
        t("matches rejects wrong after", !ReplaceEdits.matches("one one", "1 one", ReplaceEngine.replaceAll("one one", "one", "1", o).edits));
        ReplaceEngine.Result sh = ReplaceEngine.replaceAll("one two", "two", "2", o);
        t("shift", ReplaceEdits.apply("XX one two", ReplaceEdits.shift(sh.edits, 3)).equals("XX one 2"));
        System.out.println("ReplaceEditsTest " + ok + "/" + (ok + bad));
        if (bad > 0) System.exit(1);
    }
}
