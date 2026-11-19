import com.mgb.keyboard.typing.ReplaceCompose;
import com.mgb.keyboard.typing.ReplaceEdits;
import com.mgb.keyboard.typing.ReplaceEngine;
import com.mgb.keyboard.typing.ReplaceRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** v0.42.5: a pair list (rule set) must give ONE edit list on the original text (so the field is changed match by match and keeps its format). */
public class ReplaceComposeTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL " + n); } }

    static ReplaceRules.RunResult run(String text, String rules) {
        return ReplaceRules.run(text, ReplaceRules.parse(rules).rules, new ReplaceEngine.Opt());
    }

    /** edits are sorted, not overlapping, each found really is the old text at that place, apply gives the new text. */
    static boolean sane(String orig, String out, List<ReplaceEngine.Hit> e) {
        int pos = 0;
        for (ReplaceEngine.Hit h : e) {
            if (h.start < pos || h.end < h.start || h.end > orig.length()) return false;
            if (!orig.substring(h.start, h.end).equals(h.found)) return false;
            if (h.found.equals(h.repl)) return false;
            pos = h.end;
        }
        return ReplaceEdits.apply(orig, e).equals(out);
    }

    public static void main(String[] a) {
        // ---- the bug itself: a pair list gave NO edits (= whole-text rewrite = format lost)
        String text = "cat and dog and cat";
        ReplaceRules.RunResult rr = run(text, "cat => lion\ndog => wolf");
        t("pair list out", rr.out.equals("lion and wolf and lion"));
        t("pair list has edits", rr.edits.size() == 3);
        t("pair list edits sane", sane(text, rr.out, rr.edits));
        t("text between the matches is not an edit", rr.edits.get(0).end == 3 && rr.edits.get(1).start == 8);

        // ---- chained rules: cat > dog, dog > bird  =>  ONE edit cat > bird
        rr = run("a cat b", "cat => dog\ndog => bird");
        t("chain out", rr.out.equals("a bird b"));
        t("chain = one edit", rr.edits.size() == 1 && rr.edits.get(0).found.equals("cat") && rr.edits.get(0).repl.equals("bird"));

        // ---- net no-op
        rr = run("a cat b", "cat => dog\ndog => cat");
        t("no-op out", rr.out.equals("a cat b"));
        t("no-op = no edits", rr.edits.isEmpty());

        // ---- second rule inside text made by the first
        rr = run("x ab y", "ab => 12345\n34 => ZZ");
        t("inside out", rr.out.equals("x 12ZZ5 y"));
        t("inside = one edit", rr.edits.size() == 1 && sane("x ab y", rr.out, rr.edits));

        // ---- second rule overlaps the end of first-step text and the text after it
        rr = run("one two three", "one => 1\n1 two => 12");
        t("overlap out", rr.out.equals("12 three"));
        t("overlap sane", sane("one two three", rr.out, rr.edits));

        // ---- deletes, line delete, regex
        String doc = "keep 1\nbad line\nkeep 2\nx9 y\n";
        rr = run(doc, "del: bad\nre: [0-9] => #\nkeep => K");
        t("del+re out", rr.out.equals("K #\nK #\nx# y\n"));
        t("del+re sane", sane(doc, rr.out, rr.edits));

        // ---- empty: nothing changes = no edits
        rr = run("abc", "zzz => y");
        t("no match no edits", rr.edits.isEmpty() && rr.out.equals("abc"));

        // ---- a broken rule is skipped, the others still give edits
        rr = run("a b a", "re: ( => x\na => A");
        t("bad rule skipped", rr.err != null && rr.out.equals("A b A") && sane("a b a", rr.out, rr.edits));

        // ---- compose() directly
        List<ReplaceEngine.Hit> e1 = ReplaceEngine.replaceAll("aXbXc", "X", "--", new ReplaceEngine.Opt()).edits;
        List<ReplaceEngine.Hit> e2 = ReplaceEngine.replaceAll("a--b--c", "-", "", new ReplaceEngine.Opt()).edits;
        List<ReplaceEngine.Hit> c = ReplaceCompose.compose("aXbXc", e1, e2);
        t("compose direct", c != null && ReplaceEdits.apply("aXbXc", c).equals("abc") && c.size() == 2);
        t("compose empty second", ReplaceCompose.compose("abc", e1, new ArrayList<ReplaceEngine.Hit>()).size() == e1.size());
        t("compose null", ReplaceCompose.compose(null, e1, e2) == null);

        // ---- inverse of the composed list undoes the run
        rr = run(text, "cat => lion\ndog => wolf");
        t("inverse undoes", ReplaceEdits.apply(rr.out, ReplaceEdits.inverse(rr.edits)).equals(text));

        // ---- FUZZ: random texts + random rule lists; the composed list must ALWAYS turn the old text into the new one
        Random rnd = new Random(42);
        String[] words = { "a", "b", "ab", "ba", "abc", "x", "xy", "yx", "\n", " ", "cat", "dog", "\u0985", "\u0985\u09BE", "" };
        int fuzzOk = 0, fuzzBad = 0;
        for (int it = 0; it < 4000; it++) {
            StringBuilder tx = new StringBuilder();
            int len = rnd.nextInt(14);
            for (int k = 0; k < len; k++) tx.append(words[rnd.nextInt(words.length - 1)]);
            StringBuilder rules = new StringBuilder();
            int nr = 1 + rnd.nextInt(5);
            for (int k = 0; k < nr; k++) {
                String f = words[rnd.nextInt(words.length - 2) ];
                if (f.trim().isEmpty() || f.equals("\n")) f = "a";
                String w = words[rnd.nextInt(words.length)];
                if (w.contains("\n")) w = "";
                if (rnd.nextInt(6) == 0) rules.append("del: ").append(f).append('\n');
                else rules.append(f).append(" => ").append(w).append('\n');
            }
            String src = tx.toString();
            ReplaceRules.RunResult r = run(src, rules.toString());
            boolean good = r.edits.isEmpty() || (sane(src, r.out, r.edits) && ReplaceEdits.apply(r.out, ReplaceEdits.inverse(r.edits)).equals(src));
            // edits empty is allowed only when the text really did not change (we never give an empty list for a changed text without a reason)
            if (r.edits.isEmpty() && !r.out.equals(src)) good = false;
            if (good) fuzzOk++; else { fuzzBad++; if (fuzzBad < 4) System.out.println("fuzz fail: [" + src + "] rules[" + rules + "] out[" + r.out + "] edits " + r.edits.size()); }
        }
        t("fuzz 4000 runs", fuzzBad == 0);

        // ---- coalesce: too many edits -> fewer bigger edits, same result, far text untouched
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 50; i++) big.append("cat ").append("................................").append(i % 2 == 0 ? " " : "\n");
        String bigT = big.toString();
        ReplaceEngine.Result rb = ReplaceEngine.replaceAll(bigT, "cat", "lion", new ReplaceEngine.Opt());
        List<ReplaceEngine.Hit> co = ReplaceEdits.coalesce(bigT, rb.edits, 10);
        t("coalesce size", co != null && co.size() == 10);
        t("coalesce result", co != null && ReplaceEdits.apply(bigT, co).equals(rb.out));
        t("coalesce sane", co != null && ReplaceEdits.matches(bigT, rb.out, co));
        t("coalesce short list unchanged", ReplaceEdits.coalesce(bigT, rb.edits, 100) == rb.edits);
        t("coalesce to one", ReplaceEdits.coalesce(bigT, rb.edits, 1).size() == 1);
        t("coalesce inverse", co != null && ReplaceEdits.apply(rb.out, ReplaceEdits.inverse(co)).equals(bigT));
        t("coalesce null", ReplaceEdits.coalesce(null, rb.edits, 5) == null && ReplaceEdits.coalesce(bigT, rb.edits, 0) == null);

        System.out.println("ReplaceComposeTest " + ok + "/" + (ok + bad));
        if (bad > 0) System.exit(1);
    }
}
