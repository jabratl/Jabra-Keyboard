import com.mgb.keyboard.vault.VaultList;
import com.mgb.keyboard.vault.VaultList.Entry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * v0.4.4 + v0.4.5 Vault tests (computer only).
 * javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/vault/*.java tools/VaultTest.java && java -cp out VaultTest
 */
public class VaultTest {
    static int ok = 0, bad = 0;
    static void t(String n, boolean c) { if (c) ok++; else { bad++; System.out.println("FAIL: " + n); } }
    static List<Integer> ids(Entry... es) { List<Integer> l = new ArrayList<Integer>(); for (Entry e : es) l.add(e.id); return l; }
    static String titles(List<Entry> l) { StringBuilder s = new StringBuilder(); for (Entry e : l) s.append(e.title).append('|'); return s.toString(); }

    public static void main(String[] a) {
        // ---------------- v0.4.4: engine ----------------
        VaultList v = new VaultList();
        t("empty", v.size() == 0 && v.folders().isEmpty());
        t("add ok", v.add(VaultList.SNIPPET, "", "Greeting", "Hello {name}!", 100) == VaultList.OK && v.size() == 1);
        int id1 = v.lastAddedId();
        t("id starts at 1", id1 == 1);
        t("empty text refused", v.add(VaultList.SNIPPET, "", "x", "   ", 1) == VaultList.EMPTY && v.add(VaultList.SNIPPET, "", "x", null, 1) == VaultList.EMPTY);
        t("bad kind refused", v.add("zzz", "", "x", "abc", 1) == VaultList.BAD_KIND && v.add(null, "", "x", "abc", 1) == VaultList.BAD_KIND);
        StringBuilder big = new StringBuilder(); for (int i = 0; i < VaultList.MAX_LEN + 1; i++) big.append('x');
        t("too long refused", v.add(VaultList.NOTE, "", "x", big.toString(), 1) == VaultList.TOO_LONG);
        t("max length ok", v.add(VaultList.NOTE, "", "big", big.substring(1), 1) == VaultList.OK);
        t("auto title = first line", v.add(VaultList.SNIPPET, "", "", "  First line here\nsecond", 5) == VaultList.OK && v.find(v.lastAddedId()).title.equals("First line here"));
        t("auto title cut at 40", VaultList.autoTitle("0123456789012345678901234567890123456789ABCDEF").length() == 40);
        t("title cut at 60", v.add(VaultList.SNIPPET, "", "0123456789012345678901234567890123456789012345678901234567890123456789", "t", 1) == VaultList.OK && v.find(v.lastAddedId()).title.length() == 60);
        t("newest on top", v.view(null, null, "", VaultList.NEWEST).get(0).id == v.lastAddedId());
        t("unknown folder = unfiled", v.add(VaultList.SNIPPET, "Nope", "q", "q", 1) == VaultList.OK && v.find(v.lastAddedId()).folder.equals(""));

        t("hasText", v.hasText("Hello {name}!") && !v.hasText("hello {name}!") && !v.hasText(null));

        // folders
        VaultList f = new VaultList();
        t("folder add", f.addFolder("Prompts") == VaultList.OK && f.folders().size() == 1);
        t("folder empty refused", f.addFolder("  ") == VaultList.EMPTY && f.addFolder(null) == VaultList.EMPTY);
        t("folder duplicate (case)", f.addFolder("prompts") == VaultList.EXISTS);
        t("folder name too long", f.addFolder("0123456789012345678901234567890") == VaultList.TOO_LONG);
        t("folder bangla", f.addFolder("চরিত্র") == VaultList.OK && f.hasFolder("চরিত্র"));
        for (int i = 0; i < 40; i++) f.addFolder("F" + i);
        t("folder limit 30", f.folders().size() == VaultList.MAX_FOLDERS && f.addFolder("one more") == VaultList.FULL);

        VaultList g = new VaultList();
        g.addFolder("A"); g.addFolder("B");
        g.add(VaultList.SNIPPET, "A", "a1", "alpha one", 1); Entry a1 = g.find(g.lastAddedId());
        g.add(VaultList.TEMPLATE, "A", "a2", "alpha two {x}", 2); Entry a2 = g.find(g.lastAddedId());
        g.add(VaultList.SNIPPET, "B", "b1", "beta one", 3); Entry b1 = g.find(g.lastAddedId());
        g.add(VaultList.SNIPPET, "", "u1", "loose one", 4); Entry u1 = g.find(g.lastAddedId());
        t("view folder A", g.view(null, "A", "", 0).size() == 2 && titles(g.view(null, "A", "", 0)).equals("a2|a1|"));
        t("view unfiled", g.view(null, "", "", 0).size() == 1 && g.view(null, "", "", 0).get(0) == u1);
        t("view all", g.view(null, null, "", 0).size() == 4);
        t("search words (all must match)", g.view(null, null, "ALPHA two", 0).size() == 1 && g.view(null, null, "one", 0).size() == 3);
        t("search folder name", g.view(null, null, "b", 0).size() >= 1 && g.view(null, null, "zzz", 0).isEmpty());
        t("sort A-Z", titles(g.view(null, null, "", VaultList.AZ)).equals("a1|a2|b1|u1|"));
        g.noteUse(b1.id, 50); g.noteUse(b1.id, 60); g.noteUse(a1.id, 70);
        t("count + used", b1.count == 2 && b1.used == 60 && a1.count == 1);
        t("sort used", titles(g.view(null, null, "", VaultList.USED)).startsWith("b1|a1|"));
        t("move to folder", g.move(ids(u1, a2), "B") == 2 && u1.folder.equals("B") && a2.folder.equals("B"));
        t("move to unfiled", g.move(ids(u1), "") == 1 && u1.folder.equals(""));
        t("move to unknown folder = nothing", g.move(ids(u1), "ZZZ") == 0 && u1.folder.equals(""));
        t("edit keeps id + position", g.edit(a1.id, VaultList.SNIPPET, "B", "A one", "alpha 1") == VaultList.OK && a1.title.equals("A one") && a1.folder.equals("B") && a1.id == g.find(a1.id).id);
        t("edit empty refused", g.edit(a1.id, VaultList.SNIPPET, "", "t", "  ") == VaultList.EMPTY && a1.text.equals("alpha 1"));
        t("edit unknown id", g.edit(999, VaultList.SNIPPET, "", "t", "x") == VaultList.NOT_FOUND);
        t("remove folder keeps entries", g.removeFolder("B") == 3 && g.countIn("B") == 0 && !g.hasFolder("B"));
        t("entries survive folder delete", g.size() == 4 && g.find(b1.id) != null && g.find(b1.id).folder.equals(""));
        t("remove unknown folder", g.removeFolder("nope") == -1);
        t("remove one", g.remove(u1.id) && g.size() == 3 && !g.remove(u1.id));
        t("removeAll", g.removeAll(ids(a1, a2, b1, a1)) == 3 && g.size() == 0);
        VaultList p = new VaultList();
        t("preset folders", p.addPresetFolders() == 4 && p.folders().equals(Arrays.asList(VaultList.NOVEL_FOLDERS)) && p.addPresetFolders() == 0);

        // entry limit
        VaultList lim = new VaultList();
        for (int i = 0; i < VaultList.MAX_ENTRIES; i++) lim.add(VaultList.SNIPPET, "", "e" + i, "t" + i, i);
        t("limit 500", lim.size() == 500 && lim.add(VaultList.SNIPPET, "", "x", "x", 1) == VaultList.FULL);

        // JSON
        VaultList j = new VaultList();
        j.addFolder("Prompts \"quoted\""); j.addFolder("চরিত্র");
        j.add(VaultList.TEMPLATE, "Prompts \"quoted\"", "Tr \\ prompt", "Translate {from} -> {to}:\n\"q\"\ttab\u0001", 123456789012L);
        j.add(VaultList.SNIPPET, "চরিত্র", "আমি", "আমি তুমি সে\nনতুন লাইন", 5);
        j.noteUse(j.lastAddedId(), 77);
        String js = j.toJson();
        t("json has ver", js.startsWith("{\"ver\":1,"));
        VaultList r = VaultList.parse(js);
        t("round trip parses", r != null && r.size() == 2 && r.folders().size() == 2);
        t("round trip same json", r != null && r.toJson().equals(js));
        t("round trip bangla", r != null && r.view(null, "চরিত্র", "", 0).get(0).text.equals("আমি তুমি সে\nনতুন লাইন") && r.view(null, "চরিত্র", "", 0).get(0).count == 1);
        t("round trip specials", r != null && r.find(1).text.equals("Translate {from} -> {to}:\n\"q\"\ttab\u0001") && r.find(1).title.equals("Tr \\ prompt") && r.find(1).made == 123456789012L);
        t("ids continue after load", r != null && r.add(VaultList.NOTE, "", "n", "n", 1) == VaultList.OK && r.lastAddedId() == 3);
        t("parse garbage = null", VaultList.parse("hello") == null && VaultList.parse("") == null && VaultList.parse(null) == null && VaultList.parse("{\"ver\":1,") == null);
        t("parse newer ver = null", VaultList.parse("{\"ver\":2,\"entries\":[]}") == null);
        t("parse no ver = null", VaultList.parse("{\"entries\":[]}") == null);
        t("parse empty vault", VaultList.parse("{\"ver\":1,\"next\":1,\"folders\":[],\"entries\":[]}") != null);
        VaultList tol = VaultList.parse("{\"ver\":1,\"entries\":[{\"id\":5,\"kind\":\"weird\",\"text\":\"keep me\"},{\"id\":5,\"text\":\"dup id\"},{\"id\":0,\"text\":\"zero id\"},{\"id\":6,\"text\":\"  \"},{\"id\":7,\"kind\":\"term\",\"title\":\"T\",\"text\":\"M\",\"folder\":\"gone\"}],\"extra\":1}");
        t("tolerant parse", tol != null && tol.size() == 2 && tol.find(5).kind.equals("snippet") && tol.find(5).title.equals("keep me") && tol.find(7).folder.equals(""));
        t("next id after tolerant", tol != null && tol.add(VaultList.NOTE, "", "x", "x", 1) == VaultList.OK && tol.lastAddedId() == 8);

        // ---------------- v0.4.5: Glossary + Notes ----------------
        VaultList gl = new VaultList();
        t("term needs a title", gl.add(VaultList.TERM, "", "", "meaning only", 1) == VaultList.EMPTY && gl.size() == 0);
        t("term ok", gl.add(VaultList.TERM, "", "Jabra", "a personal keyboard", 1) == VaultList.OK);
        Entry term = gl.find(gl.lastAddedId());
        t("tap pastes the term", VaultList.pasteText(term).equals("Jabra"));
        t("meaning = text", VaultList.meaning(term).equals("a personal keyboard"));
        gl.add(VaultList.NOTE, "", "", "Remember: chapter 5 ends here\nmore", 2);
        Entry note = gl.find(gl.lastAddedId());
        t("note auto title", note.title.equals("Remember: chapter 5 ends here"));
        t("note pastes its text", VaultList.pasteText(note).equals(note.text) && VaultList.meaning(note).equals(""));
        gl.add(VaultList.SNIPPET, "", "s", "snip", 3); gl.add(VaultList.TEMPLATE, "", "tp", "tpl {x}", 4);
        t("kind chips: Snippets = snippet + template", gl.view(VaultList.SNIPPET, null, "", 0).size() == 2);
        t("kind chips: Notes", gl.view(VaultList.NOTE, null, "", 0).size() == 1);
        t("kind chips: Glossary", gl.view(VaultList.TERM, null, "", 0).size() == 1 && gl.view(VaultList.TERM, null, "", 0).get(0) == term);
        t("kind + search together", gl.view(VaultList.TERM, null, "keyboard", 0).size() == 1 && gl.view(VaultList.NOTE, null, "keyboard", 0).isEmpty());
        t("counts per kind", gl.countKind(null) == 4 && gl.countKind(VaultList.SNIPPET) == 2 && gl.countKind(VaultList.TERM) == 1);
        t("edit to term needs title", gl.edit(note.id, VaultList.TERM, "", "", "text") == VaultList.EMPTY && note.kind.equals(VaultList.NOTE));
        t("edit kind change keeps id", gl.edit(note.id, VaultList.TERM, "", "Chapter", "ends here") == VaultList.OK && note.kind.equals(VaultList.TERM) && gl.find(note.id) == note);
        t("kind cycle", VaultList.nextKind("snippet").equals("template") && VaultList.nextKind("term").equals("snippet") && VaultList.nextKind("???").equals("snippet"));
        t("kind labels", VaultList.kindLabel("note").equals("Note") && VaultList.kindLabel("term").equals("Term"));
        VaultList gr = VaultList.parse(gl.toJson());
        t("glossary round trip", gr != null && gr.countKind(VaultList.TERM) == 2 && gr.toJson().equals(gl.toJson()));
        t("glossary bangla term", gl.add(VaultList.TERM, "", "বাংলা", "Bangla language", 9) == VaultList.OK && VaultList.pasteText(gl.find(gl.lastAddedId())).equals("বাংলা"));

        System.out.println("VaultTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
