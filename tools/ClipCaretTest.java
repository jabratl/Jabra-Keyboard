import com.mgb.keyboard.typing.ClipCaret;

/** v0.17.56 (C1): caret logic of the clipboard Edit screen. Prints FAIL lines; exit 1 on any fail. */
public class ClipCaretTest {
    static int fails = 0, checks = 0;
    static void eq(String what, Object a, Object b) {
        checks++;
        if (a == null ? b != null : !a.equals(b)) { fails++; System.out.println("FAIL " + what + ": got " + a + " want " + b); }
    }
    public static void main(String[] x) {
        eq("clamp lo", ClipCaret.clamp("abc", -5), 0);
        eq("clamp hi", ClipCaret.clamp("abc", 9), 3);
        eq("clamp null", ClipCaret.clamp(null, 4), 0);
        eq("ins mid", ClipCaret.insert("abcd", 2, "XY"), "abXYcd");
        eq("ins start", ClipCaret.insert("abcd", 0, "X"), "Xabcd");
        eq("ins end", ClipCaret.insert("abcd", 4, "X"), "abcdX");
        eq("ins over", ClipCaret.insert("ab", 99, "X"), "abX");
        eq("ins null", ClipCaret.insert(null, 0, "X"), "X");
        eq("afterIns", ClipCaret.afterInsert("abcd", 2, "XY"), 4);
        eq("bs mid", ClipCaret.backspace("abcd", 2), "acd");
        eq("bs start", ClipCaret.backspace("abcd", 0), "abcd");
        eq("bs end", ClipCaret.backspace("abcd", 4), "abc");
        eq("afterBs", ClipCaret.afterBackspace("abcd", 2), 1);
        eq("afterBs0", ClipCaret.afterBackspace("abcd", 0), 0);
        String emo = "a\uD83D\uDE00b";   // a + emoji (2 units) + b
        eq("bs emoji", ClipCaret.backspace(emo, 3), "ab");
        eq("afterBs emoji", ClipCaret.afterBackspace(emo, 3), 1);
        eq("left emoji", ClipCaret.left(emo, 3), 1);
        eq("right emoji", ClipCaret.right(emo, 1), 3);
        eq("left 0", ClipCaret.left("abc", 0), 0);
        eq("right end", ClipCaret.right("abc", 3), 3);
        eq("left", ClipCaret.left("abc", 2), 1);
        eq("right", ClipCaret.right("abc", 1), 2);
        String m = "ab\ncde\n\nf";   // 0..: a0 b1 \n2 c3 d4 e5 \n6 \n7 f8
        eq("ls0", ClipCaret.lineStart(m, 1), 0);
        eq("ls1", ClipCaret.lineStart(m, 4), 3);
        eq("ls at nl", ClipCaret.lineStart(m, 3), 3);
        eq("ls empty line", ClipCaret.lineStart(m, 7), 7);
        eq("le0", ClipCaret.lineEnd(m, 0), 2);
        eq("le1", ClipCaret.lineEnd(m, 4), 6);
        eq("le last", ClipCaret.lineEnd(m, 8), 9);
        eq("up first line", ClipCaret.up(m, 1), 0);
        eq("up keep col", ClipCaret.up(m, 5), 2);   // col 2 in "cde" -> line "ab" has len 2 -> pos 2
        eq("up to empty", ClipCaret.up(m, 8), 7);
        eq("down keep col", ClipCaret.down(m, 1), 4);
        eq("down short", ClipCaret.down(m, 5), 7);   // next line is empty
        eq("down last", ClipCaret.down(m, 8), 9);
        eq("disp none nl", ClipCaret.display("abc", 1), "a\u258Fbc");
        eq("disp nl", ClipCaret.display("a\nb", 2), "a \u23CE \u258Fb");
        eq("disp end", ClipCaret.display("ab", 2), "ab\u258F");
        eq("disp empty", ClipCaret.display("", 0), "\u258F");
        eq("disp null", ClipCaret.display(null, 3), "\u258F");
        // round trip: tap at each gap of the displayed text -> same position
        String t = "ab\ncd";
        for (int old = 0; old <= t.length(); old++) {
            for (int want = 0; want <= t.length(); want++) {
                String d = ClipCaret.display(t, old);
                // offset of the gap 'want' in display (before the caret mark is considered)
                int off = t.substring(0, want).replace("\n", ClipCaret.NL_MARK).length();
                if (off >= t.substring(0, old).replace("\n", ClipCaret.NL_MARK).length() && want != old) off += 1;
                eq("tap old=" + old + " want=" + want, ClipCaret.posFromDisplay(t, old, off), want);
            }
        }
        eq("tap far", ClipCaret.posFromDisplay("abc", 0, 999), 3);
        eq("tap neg", ClipCaret.posFromDisplay("abc", 3, -4), 0);
        eq("tap empty", ClipCaret.posFromDisplay("", 0, 3), 0);
        eq("tap inside nl mark start", ClipCaret.posFromDisplay("a\nb", 3, 2), 1);
        eq("tap inside nl mark end", ClipCaret.posFromDisplay("a\nb", 3, 3), 2);
        System.out.println("ClipCaretTest: " + checks + " checks, " + fails + " fails");
        if (fails > 0) System.exit(1);
    }
}
