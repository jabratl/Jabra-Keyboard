package com.mgb.keyboard.typing;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.25.0 (R2) READY-MADE rule sets (read only, in code, never saved). Languages: Korean, Japanese, Chinese, Russian, Arabic / Persian + general clean-up.
 * Text = the same one-line syntax as ReplaceRules. matchCase = the flag of the set. PURE JAVA; ReplaceSetsTest checks that every rule compiles.
 */
public final class ReplaceSets {
    private ReplaceSets() { }

    public static final class Def {
        public final String name, text;
        public final boolean matchCase;
        Def(String name, boolean matchCase, String text) { this.name = name; this.matchCase = matchCase; this.text = text; }
        public ReplaceEngine.Opt opt() {
            ReplaceEngine.Opt o = new ReplaceEngine.Opt();
            o.mode = ReplaceEngine.MODE_TEXT; o.matchCase = matchCase; o.escapes = false;
            return o;
        }
    }

    private static final List<Def> ALL = new ArrayList<Def>();
    static {
        ALL.add(new Def("Clean text", false,
            "# double spaces, space before punctuation, trailing spaces, 3+ blank lines\n" +
            "re: (\\S)[ ]{2,}(\\S) => $1 $2\n" +
            "re: [ ]+([,.;:!?]) => $1\n" +
            "re: [ \\t]+$ =>\n" +
            "re: \\n{3,} => \\n\\n\n"));
        ALL.add(new Def("Quotes and dashes", false,
            "“ => \"\n" +
            "” => \"\n" +
            "‘ => '\n" +
            "’ => '\n" +
            "— => -\n" +
            "– => -\n" +
            "… => ...\n"));
        ALL.add(new Def("Korean clean-up", false,
            "# spaces before marks, one space after a mark before Hangul, long ㅋㅋㅋ / ㅎㅎㅎ / ㅠㅠㅠ shortened\n" +
            "re: [ ]+([,.!?]) => $1\n" +
            "re: ([,.!?])(\\p{IsHangul}) => $1 $2\n" +
            "re: ㅋ{4,} => ㅋㅋㅋ\n" +
            "re: ㅎ{4,} => ㅎㅎㅎ\n" +
            "re: ㅠ{4,} => ㅠㅠ\n" +
            "re: (\\S)\\u3000+(\\S) => $1 $2\n"));
        ALL.add(new Def("Japanese punctuation", false,
            "# . , ! ? after Japanese letters -> 。 、 ！ ？ ; spaces between Japanese letters removed\n" +
            "re: (?<=[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}])\\. => 。\n" +
            "re: (?<=[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]), => 、\n" +
            "re: (?<=[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}])! => ！\n" +
            "re: (?<=[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}])\\? => ？\n" +
            "re: (?<=[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}])[ ]+(?=[\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]) =>\n"));
        ALL.add(new Def("Chinese punctuation", false,
            "# , . ! ? : ; after Chinese letters -> full-width marks ; spaces between Chinese letters removed\n" +
            "re: (?<=\\p{IsHan}), => ，\n" +
            "re: (?<=\\p{IsHan})\\. => 。\n" +
            "re: (?<=\\p{IsHan})! => ！\n" +
            "re: (?<=\\p{IsHan})\\? => ？\n" +
            "re: (?<=\\p{IsHan}): => ：\n" +
            "re: (?<=\\p{IsHan}); => ；\n" +
            "re: (?<=\\p{IsHan})[ ]+(?=\\p{IsHan}) =>\n"));
        ALL.add(new Def("Full-width to half-width", false,
            "# full-width digits and marks (CN / JP / KR) -> normal ones\n" +
            "０ => 0\n" +
            "１ => 1\n" +
            "２ => 2\n" +
            "３ => 3\n" +
            "４ => 4\n" +
            "５ => 5\n" +
            "６ => 6\n" +
            "７ => 7\n" +
            "８ => 8\n" +
            "９ => 9\n" +
            "！ => !\n" +
            "？ => ?\n" +
            "， => ,\n" +
            "； => ;\n" +
            "： => :\n" +
            "（ => (\n" +
            "） => )\n" +
            "　 =>\n"));
        ALL.add(new Def("Russian typography", true,
            "# yo -> ye, spaced hyphen -> long dash, straight quotes -> guillemets\n" +
            "ё => е\n" +
            "Ё => Е\n" +
            "re: (\\S) - (\\S) => $1 — $2\n" +
            "re: \"([^\"\\n]*)\" => «$1»\n"));
        ALL.add(new Def("Russian quotes to straight", false,
            "« => \"\n" +
            "» => \"\n" +
            "„ => \"\n" +
            "“ => \"\n"));
        ALL.add(new Def("Arabic digits to English", false,
            "٠ => 0\n" +
            "١ => 1\n" +
            "٢ => 2\n" +
            "٣ => 3\n" +
            "٤ => 4\n" +
            "٥ => 5\n" +
            "٦ => 6\n" +
            "٧ => 7\n" +
            "٨ => 8\n" +
            "٩ => 9\n"));
        ALL.add(new Def("Persian digits to English", false,
            "۰ => 0\n" +
            "۱ => 1\n" +
            "۲ => 2\n" +
            "۳ => 3\n" +
            "۴ => 4\n" +
            "۵ => 5\n" +
            "۶ => 6\n" +
            "۷ => 7\n" +
            "۸ => 8\n" +
            "۹ => 9\n"));
        ALL.add(new Def("English digits to Arabic", false,
            "0 => ٠\n" +
            "1 => ١\n" +
            "2 => ٢\n" +
            "3 => ٣\n" +
            "4 => ٤\n" +
            "5 => ٥\n" +
            "6 => ٦\n" +
            "7 => ٧\n" +
            "8 => ٨\n" +
            "9 => ٩\n"));
        ALL.add(new Def("English digits to Persian", false,
            "0 => ۰\n" +
            "1 => ۱\n" +
            "2 => ۲\n" +
            "3 => ۳\n" +
            "4 => ۴\n" +
            "5 => ۵\n" +
            "6 => ۶\n" +
            "7 => ۷\n" +
            "8 => ۸\n" +
            "9 => ۹\n"));
        ALL.add(new Def("Arabic clean-up", false,
            "# tatweel, harakat (short vowel marks), Persian ک ی -> Arabic ك ي\n" +
            "ـ =>\n" +
            "re: [\\u064B-\\u065F\\u0670] =>\n" +
            "ک => ك\n" +
            "ی => ي\n" +
            "ى => ي\n"));
        ALL.add(new Def("Arabic punctuation", false,
            "# , ; ? after Arabic letters -> ، ؛ ؟\n" +
            "re: (?<=\\p{IsArabic}), => ،\n" +
            "re: (?<=\\p{IsArabic}); => ؛\n" +
            "re: (?<=\\p{IsArabic})\\? => ؟\n" +
            "re: [ ]+([،؛؟]) => $1\n"));
    }

    public static List<Def> all() { return new ArrayList<Def>(ALL); }

    public static Def get(String name) {
        for (Def d : ALL) if (d.name.equals(name)) return d;
        return null;
    }
}
