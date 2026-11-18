package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.28.0 (R3b) HELP TEXTS of the Find & Replace panel (the "?" button). PURE JAVA, no Android, never throws.
 * Three pages: Modes (Text / Word / Regex), Examples (5 live examples: Korean, Japanese, Chinese, Russian, Arabic), Sets and Recent pairs.
 * Every example is RUN by tools/ReplaceHelpTest.java with ReplaceEngine, so the text on the screen can never lie about the result.
 * (Not the same as HelpPlan: HelpPlan = the grey help line under a row of the settings screen.)
 */
public final class ReplaceHelp {
    private ReplaceHelp() { }

    public static final int P_MODES = 0, P_EXAMPLES = 1, P_SETS = 2;
    public static final String[] PAGES = { "Modes", "Examples", "Sets" };

    /** One example: the panel can load it into Find / With (tap), the test runs it. */
    public static final class Example {
        public final String lang, what, find, with, input, expected;
        public final int mode;
        public final boolean matchCase;
        Example(String lang, String what, int mode, boolean matchCase, String find, String with, String input, String expected) {
            this.lang = lang; this.what = what; this.mode = mode; this.matchCase = matchCase;
            this.find = find; this.with = with; this.input = input; this.expected = expected;
        }
    }

    private static final Example[] EX = {
        new Example("Korean", "price: put the won sign in front (Regex)", ReplaceEngine.MODE_REGEX, false,
            "(\\d+)원", "₩$1", "커피 4500원, 빵 3000원", "커피 ₩4500, 빵 ₩3000"),
        new Example("Japanese", "western comma to Japanese comma (Text)", ReplaceEngine.MODE_TEXT, false,
            ",", "、", "今日は,晴れです,", "今日は、晴れです、"),
        new Example("Chinese", "western comma to Chinese comma (Text)", ReplaceEngine.MODE_TEXT, false,
            ",", "，", "你好,世界,我是小明", "你好，世界，我是小明"),
        new Example("Russian", "whole word only, any case (Word)", ReplaceEngine.MODE_WORD, false,
            "кот", "пёс", "Кот сидит. Котёнок спит. кот ушёл", "пёс сидит. Котёнок спит. пёс ушёл"),
        new Example("Arabic", "swap two words (Regex, $2 $1)", ReplaceEngine.MODE_REGEX, false,
            "(\\S+) (\\S+)", "$2 $1", "مرحبا بالعالم", "بالعالم مرحبا"),
    };

    public static int exampleCount() { return EX.length; }
    public static List<Example> examples() { List<Example> l = new ArrayList<Example>(); for (Example e : EX) l.add(e); return l; }
    public static Example example(int i) { return (i < 0 || i >= EX.length) ? null : EX[i]; }

    public static int sanitizePage(int p) { return (p < 0 || p >= PAGES.length) ? P_MODES : p; }

    /** Page 0. */
    public static String modesText() {
        return "TEXT: Find is plain text. Every copy is replaced. Letters like . * ( ) mean nothing special.\n\n"
            + "WORD: like Text, but only whole words. \"cat\" does not touch \"category\". Works with every script (Korean, Japanese, Chinese, Russian, Arabic).\n\n"
            + "REGEX: Find is a pattern. In With: $1 $2 = the groups, $0 = everything found, $$ = a dollar sign, \\n = new line, \\t = tab.\n"
            + "Also \\U \\L \\E and \\u \\l change the case of what follows.\n\n"
            + "Aa = match case. Whole field = 2 taps and Undo (the last 3 Replace taps can be undone, one after the other). "
            + "Select text first to work on the selection only. Nothing is read in password fields.";
    }

    /** Page 1, one example as text: "Korean - what\nFind: ...\nWith: ...\nBefore: ...\nAfter: ...". */
    public static String exampleText(Example e) {
        if (e == null) return "";
        return e.lang + " - " + e.what + "\nFind: " + e.find + "\nWith: " + e.with + "\nBefore: " + e.input + "\nAfter: " + e.expected;
    }

    /** Page 1 header line. */
    public static String examplesIntro() { return "Tap an example to load its Find and With. Mode is set too."; }

    /** Page 2: the ready-made sets (names from ReplaceSets, so the list is never out of date) + Recent pairs. */
    public static String setsText() {
        StringBuilder b = new StringBuilder();
        b.append("SETS = a list of rules run one after the other (one rule per line: find => with, re: for regex, del: removes lines, # = comment). ");
        b.append("Edit them in the app: Tools & Panels > Find & Replace sets. In the panel tap Sets and pick one.\n\nReady-made sets:\n");
        try {
            for (ReplaceSets.Def d : ReplaceSets.all()) b.append("- ").append(d.name).append('\n');
        } catch (RuntimeException ex) { /* list stays short */ }
        b.append("\nVARIANTS: one line, many wrong spellings: Xiaoyan | Xiao Yan | Siao Yan => Xiao Yan (separate with space-bar-space). Works with word: and keep:.\n");
        b.append("PAIR LIST: type Find and With, tap +Pair (top bar), type the next one ... then Replace all runs them all. Tap a pair to edit it, x removes it. Check shows duplicates, chains (a => b, then b => c) and short words; Whole word fixes the short ones. Save as Set keeps the list.\n");
        b.append("\nRECENT PAIRS: ").append(RecentPairs.switchHelp());
        return b.toString();
    }

    public static String pageText(int page) {
        switch (sanitizePage(page)) {
            case P_EXAMPLES: return examplesIntro();
            case P_SETS: return setsText();
            default: return modesText();
        }
    }
}
