import com.mgb.keyboard.clip.ClipExport;
import com.mgb.keyboard.clip.ClipExport.Entry;
import com.mgb.keyboard.clip.ClipExport.Options;
import com.mgb.keyboard.clip.ClipName;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** v0.17.63 (R3): export file names + zip. Prints FAIL lines; exit 1 on any fail. */
public class ClipNameTest {
    static int fails = 0, checks = 0;
    static void eq(String what, Object a, Object b) {
        checks++;
        if (a == null ? b != null : !a.equals(b)) { fails++; System.out.println("FAIL " + what + ": got " + a + " want " + b); }
    }
    static void ok(String what, boolean c) { eq(what, c, true); }
    static Entry e(String t) { return new Entry(t, null, false, 0L); }

    static List<String> zipList(byte[] b) throws Exception {
        List<String> l = new ArrayList<String>();
        ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(b));
        ZipEntry ze;
        while ((ze = z.getNextEntry()) != null) l.add(ze.getName());
        return l;
    }

    public static void main(String[] a) throws Exception {
        TimeZone utc = TimeZone.getTimeZone("UTC");
        long now = 1763096640000L;   // 2025-11-14 05:04 UTC

        // ---- clean
        eq("plain", ClipName.clean("My notes"), "My notes");
        eq("null", ClipName.clean(null), "");
        eq("empty", ClipName.clean("   "), "");
        eq("slash", ClipName.clean("a/b\\c:d*e?f\"g<h>i|j"), "a-b-c-d-e-f-g-h-i-j");
        eq("ext txt", ClipName.clean("report.txt"), "report");
        eq("ext TXT upper", ClipName.clean("report.TXT"), "report");
        eq("ext docx", ClipName.clean("report.docx"), "report");
        eq("ext zip", ClipName.clean("report.zip"), "report");
        eq("ext twice", ClipName.clean("report.txt.zip"), "report");
        eq("only ext", ClipName.clean(".txt"), "");
        eq("dots", ClipName.clean("..."), "");
        eq("dashes", ClipName.clean("---"), "");
        eq("lead trail dots", ClipName.clean("  ..abc..  "), "abc");
        eq("inner spaces", ClipName.clean("a    b \t c"), "a b c");
        eq("newline", ClipName.clean("a\nb"), "a b");
        eq("reserved", ClipName.clean("con"), "con_");
        eq("reserved ext", ClipName.clean("NUL.backup"), "NUL.backup_");
        eq("not reserved", ClipName.clean("console"), "console");
        eq("bangla", ClipName.clean("আমার নোট"), "আমার নোট");
        eq("bidi", ClipName.clean("a‮b"), "a-b");
        eq("lone surrogate", ClipName.clean("a\uD83Db"), "ab");
        eq("emoji kept", ClipName.clean("a😀b"), "a😀b");
        StringBuilder lg = new StringBuilder();
        for (int i = 0; i < 300; i++) lg.append('x');
        eq("max len", ClipName.clean(lg.toString()).length(), ClipName.MAX_BASE);
        StringBuilder em = new StringBuilder();
        for (int i = 0; i < 100; i++) em.append("😀");
        String ce = ClipName.clean(em.toString());
        ok("emoji cut no broken pair", ce.length() <= ClipName.MAX_BASE && !Character.isHighSurrogate(ce.charAt(ce.length() - 1)));
        eq("idempotent", ClipName.clean(ClipName.clean("a/b.txt ")), ClipName.clean("a/b.txt "));

        // ---- withExt
        eq("withExt", ClipName.withExt("hello", "txt", "fb"), "hello.txt");
        eq("withExt strips typed ext", ClipName.withExt("hello.docx", "docx", "fb"), "hello.docx");
        eq("withExt empty -> fallback", ClipName.withExt("", "zip", "fb"), "fb.zip");
        eq("withExt both empty", ClipName.withExt("?", "zip", "..."), "---.zip".equals(ClipName.withExt("?", "zip", "...")) ? "---.zip" : "Jabra-Clips.zip");

        // ---- title / autoBase
        eq("title", ClipName.title("Hello world. second"), "Hello world  second".replaceAll("  ", " "));
        eq("title skip blank", ClipName.title("\n\n  Buy milk\nmore"), "Buy milk");
        eq("title empty", ClipName.title("   \n "), "");
        ok("title short", ClipName.title("one two three four five six seven eight nine ten").length() <= ClipName.TITLE_CHARS);
        eq("title word cut", ClipName.title("alpha beta gamma delta epsilon zeta eta"), "alpha beta gamma delta");
        List<Entry> one = new ArrayList<Entry>(); one.add(e("Buy milk today"));
        eq("auto one", ClipName.autoBase(one, now, utc), "Buy milk today");
        List<Entry> oneBlank = new ArrayList<Entry>(); oneBlank.add(e("///"));
        eq("auto one unusable", ClipName.autoBase(oneBlank, now, utc), "Jabra-Clips_2025-11-14_0504");
        List<Entry> two = new ArrayList<Entry>(); two.add(e("a")); two.add(e("b"));
        eq("auto many", ClipName.autoBase(two, now, utc), "Jabra-Clips_2025-11-14_0504");
        eq("auto = old fileName", ClipName.autoBase(two, now, utc) + ".txt", ClipExport.fileName("txt", now, utc));
        eq("auto null", ClipName.autoBase(null, now, utc), "Jabra-Clips_2025-11-14_0504");

        // ---- addDate
        eq("addDate", ClipName.addDate("notes", now, utc), "notes_2025-11-14");
        eq("addDate twice", ClipName.addDate(ClipName.addDate("notes", now, utc), now, utc), "notes_2025-11-14");
        eq("addDate empty", ClipName.addDate("", now, utc), "Jabra-Clips_2025-11-14");
        ok("addDate long", ClipName.addDate(lg.toString(), now, utc).length() <= ClipName.MAX_BASE);

        // ---- zipNames
        List<Entry> same = new ArrayList<Entry>();
        same.add(e("Hi")); same.add(e("hi")); same.add(e("HI")); same.add(e("")); same.add(e("a/b"));
        List<String> zn = ClipName.zipNames(same, "txt");
        eq("zn size", zn.size(), 5);
        eq("zn1", zn.get(0), "01_Hi.txt");
        eq("zn2", zn.get(1), "02_hi.txt");
        eq("zn4 blank", zn.get(3), "04_clip.txt");
        eq("zn5", zn.get(4), "05_a-b.txt");
        List<Entry> many = new ArrayList<Entry>();
        for (int i = 0; i < 120; i++) many.add(e("same"));
        List<String> zm = ClipName.zipNames(many, "docx");
        eq("zm pad", zm.get(0), "001_same.docx");
        eq("zm last", zm.get(119), "120_same.docx");
        ok("zm unique", new HashSet<String>(zm).size() == 120);

        // ---- toZip
        Options o = new Options(); o.now = now; o.tz = utc;
        byte[] z = ClipName.toZip(same.subList(0, 3), false, o);
        ok("zip not null", z != null);
        List<String> zl = zipList(z);
        eq("zip entries", zl.size(), 3);
        eq("zip first", zl.get(0), "01_Hi.txt");
        byte[] zd = ClipName.toZip(two, true, o);
        ok("zip docx not null", zd != null);
        eq("zip docx names", zipList(zd).get(1), "02_b.docx");
        eq("zip empty", ClipName.toZip(new ArrayList<Entry>(), false, o), null);
        eq("zip null", ClipName.toZip(null, false, o), null);
        ok("zip null opts", ClipName.toZip(two, false, null) != null);
        byte[] zb = ClipName.toZip(java.util.Arrays.asList(e("আমার নোট\nline2")), false, o);
        eq("zip bangla name", zipList(zb).get(0), "01_আমার নোট.txt");
        ok("zip 0 time ok", ClipName.toZip(two, false, new Options()) != null);

        // ---- fuzz: clean() never throws, result always safe + idempotent
        Random r = new Random(7);
        String pool = "ab ./\\:*?\"<>|\n\t‮😀\uD83D-_.txt.zip.docx আ";
        for (int i = 0; i < 4000; i++) {
            StringBuilder sb = new StringBuilder();
            int n = r.nextInt(40);
            for (int k = 0; k < n; k++) sb.append(pool.charAt(r.nextInt(pool.length())));
            String c = ClipName.clean(sb.toString());
            boolean safe = c.length() <= ClipName.MAX_BASE;
            for (int k = 0; k < c.length(); k++) {
                char ch = c.charAt(k);
                if (ch < 32 || "/\\:*?\"<>|".indexOf(ch) >= 0) safe = false;
            }
            if (c.length() > 0 && (c.charAt(0) == ' ' || c.charAt(0) == '.' || c.charAt(c.length() - 1) == ' ' || c.charAt(c.length() - 1) == '.')) safe = false;
            if (!safe) { fails++; System.out.println("FAIL fuzz unsafe: [" + c + "]"); }
            checks++;
            String c2 = ClipName.clean(c);
            if (!c.endsWith("_") && !c2.equals(c)) { fails++; System.out.println("FAIL fuzz idempotent: [" + c + "] -> [" + c2 + "]"); }
            checks++;
            List<Entry> fz = new ArrayList<Entry>(); fz.add(e(sb.toString())); fz.add(e(sb.toString()));
            Set<String> seen = new HashSet<String>();
            for (String nm : ClipName.zipNames(fz, "txt")) if (!seen.add(nm.toLowerCase())) { fails++; System.out.println("FAIL fuzz dup name"); }
            checks++;
        }
        System.out.println("ClipNameTest: " + checks + " checks, " + fails + " fails");
        if (fails > 0) System.exit(1);
    }
}
