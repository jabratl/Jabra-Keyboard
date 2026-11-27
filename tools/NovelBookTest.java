import com.mgb.keyboard.typing.GlossaryImport;
import com.mgb.keyboard.typing.NovelBook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** v0.55.0 (NV1) topics from the plugin, one glossary per novel, sets and Never change keys of other novels stay apart. */
public class NovelBookTest {
    static int ok = 0, bad = 0;
    static void eq(String name, Object a, Object b) {
        if (String.valueOf(a).equals(String.valueOf(b))) ok++; else { bad++; System.out.println("FAIL " + name + ": got [" + a + "] want [" + b + "]"); }
    }
    static final String S = " \u00B7 ";

    public static void main(String[] x) {
        String t = "## jabra-glossary v2 | Cultivation Saga | newest\n"
                 + "## wrong forms | characters\nLin Fen => Lin Feng\nLi Wu => Li Wuji\n"
                 + "## wrong forms | Places\nBlue Peek => Blue Peak\n"
                 + "## raw to English | Characters\n\u6797\u98ce => Lin Feng\n"
                 + "## raw to English | Skills\n\u5251\u6c14 => Sword Qi\n"
                 + "## never change\nLin Fen Pavilion\n";
        GlossaryImport.Plan p = GlossaryImport.parse(t, 40);
        eq("novel", p.novel, "Cultivation Saga");
        eq("prefix", p.prefix, "G Cultivation Saga");
        eq("4 sets", p.setNames.size(), 4);
        eq("name1", p.setNames.get(0), "G Cultivation Saga" + S + "Characters" + S + "wrong");
        eq("name2", p.setNames.get(1), "G Cultivation Saga" + S + "Places" + S + "wrong");
        eq("name3", p.setNames.get(2), "G Cultivation Saga" + S + "Characters" + S + "raw");
        eq("name4", p.setNames.get(3), "G Cultivation Saga" + S + "Skills" + S + "raw");
        eq("wrong n", p.wrong, 3); eq("raw n", p.raw, 2); eq("never n", p.never.size(), 1);
        eq("case flag wrong", p.setCase.get(0), false); eq("case flag raw", p.setCase.get(2), true);
        eq("name length fits 64", p.setNames.get(3).length() <= 64, true);

        // old format (no type) keeps the old names and shows as Terms
        GlossaryImport.Plan o = GlossaryImport.parse("## jabra-glossary v1 | Novel A | x\n## wrong forms\nA => B\n## raw to English\nC => D\n", 40);
        eq("legacy name", o.setNames.get(0), "G Novel A" + S + "wrong");
        eq("legacy topic", GlossaryImport.topicOf(o.setNames.get(0)), "Terms");
        eq("legacy kind raw", GlossaryImport.kindOf(o.setNames.get(1)), "raw");

        // helpers
        String n = "G Cultivation Saga" + S + "Places" + S + "raw 2";
        eq("novelOf", GlossaryImport.novelOf(n), "Cultivation Saga");
        eq("topicOf", GlossaryImport.topicOf(n), "Places");
        eq("kindOf numbered", GlossaryImport.kindOf(n), "raw");
        eq("novelOf user set", GlossaryImport.novelOf("Pairs 1"), "");
        eq("novelOf untitled", GlossaryImport.novelOf("G" + S + "wrong"), "");
        eq("title with dot sep", GlossaryImport.parse("## jabra-glossary v2 | A" + S + "B | c\n## wrong forms\nx => y\n", 5).novel, "A - B");

        // chunking per topic: 120 rules of one topic = 3 sets
        StringBuilder sb = new StringBuilder("## jabra-glossary v2 | Big | c\n## raw to English | Characters\n");
        for (int i = 0; i < 120; i++) sb.append("r").append(i).append(" => R").append(i).append("\n");
        GlossaryImport.Plan big = GlossaryImport.parse(sb.toString(), 40);
        eq("3 parts", big.setNames.size(), 3);
        eq("part name", big.setNames.get(2), "G Big" + S + "Characters" + S + "raw 3");
        eq("cap", GlossaryImport.parse(sb.toString(), 2).droppedSets, 1);

        // registry
        List<String> reg = NovelBook.parse("Cultivation Saga\n\n cultivation saga \nSpace Station Z\n");
        eq("registry dedup", reg.size(), 2);
        eq("registry text", NovelBook.text(reg), "Cultivation Saga\nSpace Station Z");
        List<String> names = new ArrayList<String>(p.setNames);
        names.add("G Old Novel" + S + "wrong"); names.add("Pairs 1");
        List<String> all = NovelBook.all(reg, names);
        eq("legacy novel found", all.contains("Old Novel"), true);
        eq("all size", all.size(), 3);
        eq("add new", NovelBook.add(reg, "Third"), true); eq("add repeat", NovelBook.add(reg, "third"), true); eq("size after", reg.size(), 3);
        eq("add empty", NovelBook.add(reg, "  "), false);
        eq("never keys differ", NovelBook.neverKey("A").equals(NovelBook.neverKey("B")), false);
        eq("never key case", NovelBook.neverKey("Lin"), NovelBook.neverKey(" lin "));

        // per novel sets / topics / visibility
        List<String> two = new ArrayList<String>(names);
        two.addAll(GlossaryImport.parse("## jabra-glossary v2 | Space Station Z | c\n## wrong forms | Characters\nCmdr Voss => Commander Voss\n", 40).setNames);
        eq("sets of saga", NovelBook.setsOf(two, "Cultivation Saga").size(), 4);
        eq("sets of station", NovelBook.setsOf(two, "space station z").size(), 1);
        Map<String, Integer> tp = NovelBook.topics(two, "Cultivation Saga");
        eq("topics", tp.toString(), "{Characters=2, Places=1, Skills=1}");
        eq("user count", NovelBook.userCount(two), 1);
        String saga = p.setNames.get(1), station = NovelBook.setsOf(two, "Space Station Z").get(0);
        eq("vis own all", NovelBook.visible(saga, false, "Cultivation Saga", NovelBook.ALL), true);
        eq("vis own topic yes", NovelBook.visible(saga, false, "Cultivation Saga", "Places"), true);
        eq("vis own topic no", NovelBook.visible(saga, false, "Cultivation Saga", "Skills"), false);
        eq("vis other novel", NovelBook.visible(station, false, "Cultivation Saga", NovelBook.ALL), false);
        eq("vis mine not in All", NovelBook.visible("Pairs 1", false, "Cultivation Saga", NovelBook.ALL), false);
        eq("vis mine chip", NovelBook.visible("Pairs 1", false, "Cultivation Saga", NovelBook.MINE), true);
        eq("vis mine in topic", NovelBook.visible("Pairs 1", false, "Cultivation Saga", "Places"), false);
        eq("vis ready chip", NovelBook.visible("Quotes", true, "Cultivation Saga", NovelBook.READY), true);
        eq("vis ready in topic", NovelBook.visible("Quotes", true, "Cultivation Saga", "Places"), false);
        eq("vis ready not in All", NovelBook.visible("Quotes", true, "Cultivation Saga", NovelBook.ALL), false);
        eq("title wrong", NovelBook.title(p.setNames.get(0), false), "Fix wrong names");
        eq("title raw topic", NovelBook.title(p.setNames.get(3), true), "Skills \u00B7 Raw to English");
        eq("title part", NovelBook.title("G Big" + S + "Characters" + S + "raw 3", true), "Characters \u00B7 Raw to English #3");
        eq("title legacy", NovelBook.title("G Novel A" + S + "wrong", true), "Terms \u00B7 Fix wrong names");
        eq("title user set", NovelBook.title("Pairs 1", true), "Pairs 1");
        eq("no novel = all", NovelBook.visible(station, false, "", NovelBook.ALL), true);
        eq("row sub", NovelBook.rowSub(6, 12), "6 sets \u00B7 12 never change");
        eq("row sub 1", NovelBook.rowSub(1, 0), "1 set \u00B7 0 never change");

        // v0.56.0: ";" = wrong forms, My pairs
        com.mgb.keyboard.typing.ReplacePairList.Made a1 = com.mgb.keyboard.typing.ReplacePairList.make("Lin Fen; Lin Fong ;Lin Fan;", "Lin Feng", 0, false, false);
        eq("semi ok", a1.error, null);
        eq("semi find", a1.rule.find, "Lin Fen | Lin Fong | Lin Fan");
        eq("semi with", a1.rule.with, "Lin Feng");
        eq("semi dup", com.mgb.keyboard.typing.ReplacePairList.make("A;a;A", "B", 0, false, false).rule.find, "A | a");
        eq("one piece = text", com.mgb.keyboard.typing.ReplacePairList.make("&amp;", "&", 0, false, false).rule.find, "&amp;");
        eq("lone semicolon", com.mgb.keyboard.typing.ReplacePairList.make(";", ",", 0, false, false).rule.find, ";");
        eq("list switch + semi", com.mgb.keyboard.typing.ReplacePairList.make("a|b;c", "x", 0, false, true).rule.find, "a | b | c");
        eq("regex untouched", com.mgb.keyboard.typing.ReplacePairList.make("a;b", "x", 2, false, false).rule.find, "a;b");
        eq("my name", NovelBook.mySetName("Saga", 1), "G Saga" + S + "My pairs" + S + "wrong");
        eq("my name 2", NovelBook.mySetName("Saga", 2), "G Saga" + S + "My pairs" + S + "wrong 2");
        eq("my topic", GlossaryImport.topicOf(NovelBook.mySetName("Saga", 2)), "My pairs");
        eq("my novel", GlossaryImport.novelOf(NovelBook.mySetName("Saga", 2)), "Saga");
        eq("is mine", NovelBook.isMine(NovelBook.mySetName("Saga", 1)), true);
        eq("not mine", NovelBook.isMine(saga), false);
        eq("my title", NovelBook.title(NovelBook.mySetName("Saga", 1), true), "My pairs");
        eq("my title 2", NovelBook.title(NovelBook.mySetName("Saga", 2), false), "My pairs #2");
        NovelBook.Merged mg = NovelBook.merge("# head\nA | B => C\n", Arrays.asList("A | B => D", "X => Y", "bad line", "  "), 50);
        eq("merge updated", mg.updated, 1); eq("merge added", mg.added, 1);
        eq("merge text", mg.text, "# head\nA | B => D\nX => Y\n");
        NovelBook.Merged ov = NovelBook.merge("a => b\nc => d\n", Arrays.asList("e => f", "g => h"), 3);
        eq("overflow n", ov.overflow.size(), 1); eq("overflow line", ov.overflow.get(0), "g => h"); eq("overflow added", ov.added, 1);
        eq("merge empty existing", NovelBook.merge(null, Arrays.asList("p => q"), 50).text, "p => q\n");

        System.out.println("NovelBookTest: " + ok + " ok, " + bad + " bad");
        if (bad > 0) System.exit(1);
    }
}
