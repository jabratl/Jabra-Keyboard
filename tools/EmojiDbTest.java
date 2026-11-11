import com.mgb.keyboard.emoji.EmojiBuild;
import com.mgb.keyboard.emoji.EmojiDb;
import com.mgb.keyboard.emoji.EmojiTone;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * v0.8.0 (E1) tests for EmojiBuild + EmojiDb + EmojiTone. Run from the repo root (reads tools/testdata/emoji-test-sample.txt):
 * javac -encoding UTF-8 -d out app/src/main/java/com/mgb/keyboard/emoji/*.java tools/EmojiDbTest.java ; java -Dfile.encoding=UTF-8 -cp out EmojiDbTest
 * The sample file is hand made in the real emoji-test.txt layout (NOT real Unicode data). The real file could not be tested here.
 * Expected strings below are written as explicit code points, NOT made with the code under test.
 */
public class EmojiDbTest {
    static int ok = 0, bad = 0;
    static void check(boolean c, String m) { if (c) ok++; else { bad++; System.out.println("FAIL: " + m); } }
    static String u(int... cps) { return new String(cps, 0, cps.length); }

    static int find(EmojiDb db, String e) { for (int i = 0; i < db.size(); i++) if (db.emoji(i).equals(e)) return i; return -1; }

    public static void main(String[] a) throws Exception {
        File f = new File("tools/testdata/emoji-test-sample.txt");
        check(f.exists(), "sample file exists (run from repo root)");
        String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);

        // ---------- build ----------
        EmojiBuild.Result r = EmojiBuild.build(src);
        check(r.unicodeVersion.equals("99.0"), "version " + r.unicodeVersion);
        check(r.warnings.isEmpty(), "no warnings on the sample: " + r.warnings);
        check(r.groups == 9, "9 groups (Component dropped) " + r.groups);
        check(r.count == 25, "base emoji count " + r.count);
        check(r.toneable == 7, "toneable bases " + r.toneable);
        check(r.toneVariantsSkipped == 37, "tone variants skipped " + r.toneVariantsSkipped);
        check(r.text.startsWith("#JABRA-EMOJI 1\n#unicode 99.0\n#count 25\n#mods "), "header");
        check(r.text.indexOf('\r') < 0 && r.text.endsWith("\n"), "LF only, final newline");
        check(r.text.length() < 4000, "small file");

        EmojiDb db = EmojiDb.parse(r.text);
        check(db.size() == 25, "size");
        check(db.unicodeVersion().equals("99.0"), "db version");
        check(db.groupCount() == 9, "db groups");
        String[] gnames = { "Smileys & Emotion", "People & Body", "Animals & Nature", "Food & Drink", "Travel & Places", "Activities", "Objects", "Symbols", "Flags" };
        for (int g = 0; g < gnames.length; g++) check(db.groupName(g).equals(gnames[g]), "group name " + g + " " + db.groupName(g));
        int cover = 0, prevEnd = 0;
        for (int g = 0; g < db.groupCount(); g++) {
            check(db.groupStart(g) == prevEnd, "group " + g + " contiguous");
            check(db.groupEnd(g) > db.groupStart(g), "group " + g + " not empty");
            for (int i = db.groupStart(g); i < db.groupEnd(g); i++) { check(db.group(i) == g, "group of item " + i); cover++; }
            prevEnd = db.groupEnd(g);
        }
        check(cover == db.size(), "groups cover all items");
        check(db.groupName(-1).equals("") && db.groupName(99).equals("") && db.groupStart(99) == 0 && db.groupEnd(99) == 0, "bad group index is safe");

        // ---------- what is in / out ----------
        int grin = find(db, u(0x1F600));
        check(grin == 0 && db.name(grin).equals("grinning face") && db.version(grin).equals("1.0") && db.subgroup(grin).equals("face-smiling"), "first item");
        check(find(db, u(0x2764, 0xFE0F)) >= 0, "red heart fully-qualified in");
        check(find(db, u(0x2764)) < 0 && find(db, u(0x263A)) < 0 && find(db, u(0x261D)) < 0 && find(db, u(0x1F3CB)) < 0, "unqualified out");
        check(find(db, u(0x1F3C3, 0x200D, 0x2642)) < 0, "minimally-qualified out");
        check(find(db, u(0x0023, 0x20E3)) < 0 && find(db, u(0x0023, 0xFE0F, 0x20E3)) >= 0, "keycap: only the fully-qualified one");
        check(find(db, u(0x002A, 0xFE0F, 0x20E3)) >= 0, "keycap * kept");
        check(r.text.contains("\n#\uFE0F\u20E3\tkeycap: #\t-\t0.6\n"), "keycap line really starts with # in the file (reader must not call it a comment)");
        for (int t = 0; t < 5; t++) check(find(db, u(0x1F3FB + t)) < 0, "tone swatch (component) out " + t);
        check(find(db, u(0x1F9B0)) < 0, "hair component out");
        for (int i = 0; i < db.size(); i++) {
            String e = db.emoji(i);
            boolean hasTone = false;
            for (int k = 0; k < e.length(); k++) { int cp = e.codePointAt(k); if (EmojiTone.isTone(cp)) hasTone = true; }
            check(!hasTone, "no tone variant stored: " + db.name(i));
            check(db.name(i).indexOf("skin tone") < 0, "no tone name stored: " + db.name(i));
        }
        check(find(db, u(0x1FAF1, 0x1F3FB, 0x200D, 0x1FAF2, 0x1F3FC)) < 0, "mixed-tone handshake not stored");
        int bd = find(db, u(0x1F1E7, 0x1F1E9));
        check(bd >= 0 && db.name(bd).equals("flag: Bangladesh") && db.groupName(db.group(bd)).equals("Flags") && db.version(bd).equals("2.0"), "flag Bangladesh");
        int eng = find(db, u(0x1F3F4, 0xE0067, 0xE0062, 0xE0065, 0xE006E, 0xE0067, 0xE007F));
        check(eng >= 0 && db.emoji(eng).codePointCount(0, db.emoji(eng).length()) == 7 && db.subgroup(eng).equals("subdivision-flag"), "tag flag England kept whole");
        int fam = find(db, u(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467));
        check(fam >= 0 && !db.toneable(fam), "family has no tones");
        int dog = find(db, u(0x1F415, 0x200D, 0x1F9BA));
        check(dog >= 0 && db.version(dog).equals("12.0") && !db.toneable(dog), "service dog (zwj, no tone)");

        // ---------- skin tones (expected strings written by hand) ----------
        int wave = find(db, u(0x1F44B));
        check(wave >= 0 && db.toneable(wave), "wave toneable");
        check(db.tone(wave, 2).equals(u(0x1F44B, 0x1F3FD)), "wave tone 2");
        int up = find(db, u(0x261D, 0xFE0F));
        check(up >= 0 && db.toneable(up), "index up toneable");
        check(db.tone(up, 0).equals(u(0x261D, 0x1F3FB)), "index up: FE0F replaced by the tone");
        int lift = find(db, u(0x1F3CB, 0xFE0F));
        check(db.toneable(lift) && db.tone(lift, 4).equals(u(0x1F3CB, 0x1F3FF)), "lifting weights tone 4");
        int run = find(db, u(0x1F3C3));
        check(db.toneable(run) && db.tone(run, 1).equals(u(0x1F3C3, 0x1F3FC)), "runner");
        int manrun = find(db, u(0x1F3C3, 0x200D, 0x2642, 0xFE0F));
        check(db.toneable(manrun) && db.tone(manrun, 1).equals(u(0x1F3C3, 0x1F3FC, 0x200D, 0x2642, 0xFE0F)), "man running keeps the male sign + FE0F");
        int couple = find(db, u(0x1F469, 0x200D, 0x2764, 0xFE0F, 0x200D, 0x1F468));
        check(db.toneable(couple) && db.tone(couple, 3).equals(u(0x1F469, 0x1F3FE, 0x200D, 0x2764, 0xFE0F, 0x200D, 0x1F468, 0x1F3FE)), "couple: tone on BOTH people, heart untouched");
        int shake = find(db, u(0x1F91D));
        check(db.toneable(shake) && db.tone(shake, 0).equals(u(0x1F91D, 0x1F3FB)), "handshake");
        int[] noTone = { grin, bd, eng, fam, dog, find(db, u(0x1F436)), find(db, u(0x2764, 0xFE0F)) };
        for (int i : noTone) { check(!db.toneable(i), "not toneable " + db.name(i)); check(db.tone(i, 2).equals(db.emoji(i)), "tone() on non-toneable = base " + db.name(i)); }
        check(db.tone(wave, -1).equals(u(0x1F44B)) && db.tone(wave, 5).equals(u(0x1F44B)), "bad tone index = base");
        check(db.tone(u(0x1F44D), 0).equals(u(0x1F44D)), "tone(String) of an emoji without a modifier base in the list = unchanged");
        check(db.tone(u(0x1F44B), 4).equals(u(0x1F44B, 0x1F3FF)), "tone(String) works for any base");
        check(EmojiTone.swatch(0).equals(u(0x1F3FB)) && EmojiTone.swatch(4).equals(u(0x1F3FF)) && EmojiTone.swatch(5).equals("") && EmojiTone.swatch(-1).equals(""), "swatch");
        check(EmojiTone.apply(null, 0, new int[] { 1 }) == null && EmojiTone.apply("x", 0, null).equals("x") && EmojiTone.apply("x", 0, new int[0]).equals("x"), "apply null-safe");

        // v0.8.0.1: people holding hands (handshake between two ZWJ gets NO tone, both people do)
        int[] m3 = { 0x1F91D, 0x1F9D1 };
        check(EmojiTone.apply(u(0x1F9D1, 0x200D, 0x1F91D, 0x200D, 0x1F9D1), 2, m3).equals(u(0x1F9D1, 0x1F3FD, 0x200D, 0x1F91D, 0x200D, 0x1F9D1, 0x1F3FD)), "people holding hands: tone on both people, not on the handshake");
        check(EmojiTone.apply(u(0x1F91D), 2, m3).equals(u(0x1F91D, 0x1F3FD)), "lone handshake still gets the tone");
        check(EmojiTone.apply(u(0x1F9D1, 0x200D, 0x1F91D), 0, m3).equals(u(0x1F9D1, 0x1F3FB, 0x200D, 0x1F91D, 0x1F3FB)), "handshake at the END of a zwj sequence still gets the tone");
        // every uniform tone variant of the sample is reproduced (the build already warns otherwise; check by hand too)
        int uniformSeen = 0;
        for (String line : src.split("\n")) {
            if (line.startsWith("#") || line.trim().isEmpty()) continue;
            String[] p = line.split(";");
            String[] cpS = p[0].trim().split(" +");
            int tc = 0; Set<String> ts = new HashSet<String>();
            for (String s : cpS) { int v = Integer.parseInt(s, 16); if (EmojiTone.isTone(v)) { tc++; ts.add(s); } }
            if (tc == 0 || ts.size() != 1 || !line.contains("fully-qualified") || line.contains("handshake: light skin tone, medium-light")) continue;
            int[] cps = new int[cpS.length]; for (int i = 0; i < cps.length; i++) cps[i] = Integer.parseInt(cpS[i], 16);
            String variant = u(cps);
            int tone = -1;
            for (int v : cps) if (EmojiTone.isTone(v)) tone = v - EmojiTone.FIRST;
            boolean made = false;
            for (int i = 0; i < db.size(); i++) if (db.toneable(i) && db.tone(i, tone).equals(variant)) made = true;
            check(made, "variant reproduced: " + line.substring(line.indexOf('#')));
            uniformSeen++;
        }
        check(uniformSeen == 35, "35 uniform variants checked, got " + uniformSeen);

        // ---------- build robustness ----------
        EmojiBuild.Result crlf = EmojiBuild.build(src.replace("\n", "\r\n"));
        check(crlf.text.equals(r.text), "CRLF input gives the same file");
        EmojiBuild.Result bom = EmojiBuild.build("﻿" + src);
        check(bom.text.equals(r.text), "BOM input gives the same file");
        String noVer = src.replace("# Version: 99.0", "#");
        check(EmojiBuild.build(noVer).unicodeVersion.equals("14.0"), "no Version line: highest E-number used (14.0)");
        String badLine = src.replace("1F603", "1F603 oops");
        try { EmojiBuild.build(badLine); check(false, "bad code point must throw"); } catch (IllegalArgumentException e) { check(e.getMessage().startsWith("line "), "error names the line: " + e.getMessage()); }
        try { EmojiBuild.build("# group: X\nnonsense line\n"); check(false, "no ; must throw"); } catch (IllegalArgumentException e) { check(e.getMessage().startsWith("line 2"), "no-semicolon error: " + e.getMessage()); }
        try { EmojiBuild.build(""); check(false, "empty must throw"); } catch (IllegalArgumentException e) { check(true, "empty input refused"); }
        try { EmojiBuild.build(null); check(false, "null must throw"); } catch (IllegalArgumentException e) { check(true, "null input refused"); }
        try { EmojiBuild.build("1F600 ; fully-qualified # 😀 E1.0 x\n"); check(false, "entry before group must throw"); } catch (IllegalArgumentException e) { check(e.getMessage().contains("group"), "entry before group refused"); }
        try { EmojiBuild.build("# group: G\n1F600 ; fully-qualified # 😀 X1.0 x\n"); check(false, "bad comment must throw"); } catch (IllegalArgumentException e) { check(true, "bad comment refused"); }
        try { EmojiBuild.build("# group: G\nD800 ; fully-qualified # x E1.0 x\n"); check(false, "surrogate cp must throw"); } catch (IllegalArgumentException e) { check(true, "surrogate code point refused"); }
        try { EmojiBuild.build("# group: G\n1F600 ; unqualified # 😀 E1.0 x\n"); check(false, "only unqualified must throw"); } catch (IllegalArgumentException e) { check(e.getMessage().contains("no fully-qualified"), "nothing usable refused"); }
        // partial tone set -> not toneable + warning
        String partial = "# group: G\n# subgroup: s\n1F44B ; fully-qualified # 👋 E0.6 waving hand\n"
            + "1F44B 1F3FB ; fully-qualified # 👋🏻 E1.0 waving hand: light skin tone\n"
            + "1F44B 1F3FC ; fully-qualified # 👋🏼 E1.0 waving hand: medium-light skin tone\n";
        EmojiBuild.Result pr = EmojiBuild.build(partial);
        check(pr.toneable == 0 && pr.warnings.size() == 1 && pr.warnings.get(0).contains("only 2 of 5"), "partial tones left off + warning: " + pr.warnings);
        check(!EmojiDb.parse(pr.text).toneable(0), "partial: flag off");
        // a variant that no base makes -> warning
        String orphan = "# group: G\n1F600 ; fully-qualified # 😀 E1.0 grin\n1F44B 1F3FB ; fully-qualified # 👋🏻 E1.0 waving hand: light skin tone\n";
        EmojiBuild.Result orr = EmojiBuild.build(orphan);
        check(orr.warnings.size() == 1 && orr.warnings.get(0).contains("not made by any base"), "orphan variant warns: " + orr.warnings);
        // comment emoji that disagrees with the code points -> warning only
        EmojiBuild.Result mm = EmojiBuild.build("# group: G\n1F600 ; fully-qualified # 😃 E1.0 grin\n");
        check(mm.warnings.size() == 1 && mm.count == 1, "emoji/comment mismatch warns");
        // duplicate fully-qualified line -> skipped with a warning
        EmojiBuild.Result dup = EmojiBuild.build("# group: G\n1F600 ; fully-qualified # 😀 E1.0 grin\n1F600 ; fully-qualified # 😀 E1.0 grin\n");
        check(dup.count == 1 && dup.warnings.size() == 1, "duplicate skipped with warning");
        // group that comes back later -> refused (the reader needs contiguous groups)
        try { EmojiBuild.build("# group: A\n1F600 ; fully-qualified # 😀 E1.0 a\n# group: B\n1F601 ; fully-qualified # 😁 E1.0 b\n# group: A\n1F602 ; fully-qualified # 😂 E1.0 c\n"); check(false, "non contiguous group must throw"); } catch (IllegalArgumentException e) { check(e.getMessage().contains("contiguous"), "non-contiguous group refused"); }
        // no subgroup lines at all is fine
        EmojiDb nosub = EmojiDb.parse(EmojiBuild.build("# group: G\n1F600 ; fully-qualified # 😀 E1.0 grin\n").text);
        check(nosub.size() == 1 && nosub.subgroup(0).equals(""), "no subgroup ok");

        // ---------- reader robustness ----------
        check(EmojiDb.parseOrNull((String) null) == null, "null text");
        check(EmojiDb.parseOrNull((java.io.InputStream) null) == null, "null stream");
        check(EmojiDb.parseOrNull("") == null, "empty text");
        check(EmojiDb.parseOrNull("hello") == null, "garbage text");
        check(EmojiDb.parseOrNull("#JABRA-EMOJI 2\n@G\tx\n😀\tgrin\t-\t1.0\n") == null, "newer format refused");
        String good = r.text;
        check(EmojiDb.parseOrNull(good) != null, "good text parses");
        check(EmojiDb.parseOrNull(good.replace("#count 25", "#count 26")) == null, "count mismatch refused");
        check(EmojiDb.parseOrNull(good.replace("#count 25", "#count x")) == null, "bad count refused");
        check(EmojiDb.parseOrNull(good.replace("#mods ", "#mods zz ")) == null, "bad mods refused");
        check(EmojiDb.parseOrNull(good.replaceFirst("@G\t[^\n]*\n", "")) == null, "emoji before group refused");
        check(EmojiDb.parseOrNull(good.replace("\t-\t1.0", "\tx\t1.0")) == null, "bad flags refused");
        check(EmojiDb.parseOrNull(good.replace("@G\tFlags", "@G\tObjects")) == null, "repeated group refused");
        check(EmojiDb.parseOrNull(good + "x\ty\n") == null, "short emoji line refused");
        check(EmojiDb.parseOrNull(good + "@X\ty\n") == null, "unknown header refused");
        check(EmojiDb.parseOrNull(good + "#some future comment\n") != null, "unknown # line ignored");
        check(EmojiDb.parseOrNull(good.replace("\n", "\r\n")) != null, "CRLF file parses");
        check(EmojiDb.parseOrNull("﻿" + good) != null, "BOM file parses");
        check(EmojiDb.parseOrNull(new ByteArrayInputStream(good.getBytes(StandardCharsets.UTF_8))) != null, "stream parses");
        check(EmojiDb.parseOrNull(new ByteArrayInputStream(new byte[] { (byte) 0xFF, (byte) 0xFE, 0, 1 })) == null, "binary stream refused");
        // never throws on any cut-off file; a cut file is refused (count) or, if the cut is exactly at the end, accepted
        int cutNull = 0;
        for (int cut = 0; cut < good.length(); cut += 3) {
            EmojiDb d = EmojiDb.parseOrNull(good.substring(0, cut));
            if (d == null) cutNull++; else check(d.size() == 25, "cut file that parses must be complete, cut=" + cut);
        }
        check(cutNull > good.length() / 3 - 5, "cut-off files are refused (" + cutNull + ")");
        Random rnd = new Random(7);
        for (int k = 0; k < 300; k++) {
            char[] c = good.toCharArray();
            for (int j = 0; j < 5; j++) c[rnd.nextInt(c.length)] = (char) rnd.nextInt(0x3000);
            EmojiDb.parseOrNull(new String(c));
        }
        check(true, "random corruption never throws");

        // ---------- size / speed (real list = ~4000 lines) ----------
        StringBuilder big = new StringBuilder("# Version: 99.0\n");
        int base = 0x20000;
        for (int g = 0; g < 9; g++) {
            big.append("# group: G").append(g).append("\n# subgroup: s").append(g).append("\n");
            for (int k = 0; k < 440; k++) {
                int cp = base++;
                String e = u(cp);
                big.append(String.format("%X", cp)).append(" ; fully-qualified # ").append(e).append(" E1.0 name ").append(cp).append("\n");
            }
        }
        long t0 = System.nanoTime();
        EmojiBuild.Result br = EmojiBuild.build(big.toString());
        long t1 = System.nanoTime();
        EmojiDb bdb = EmojiDb.parse(br.text);
        long t2 = System.nanoTime();
        check(br.count == 3960 && bdb.size() == 3960 && bdb.groupCount() == 9, "big list " + br.count);
        check((t1 - t0) / 1000000 < 3000 && (t2 - t1) / 1000000 < 1000, "big build " + (t1 - t0) / 1000000 + " ms, parse " + (t2 - t1) / 1000000 + " ms");
        System.out.println("info: 3960 emoji build " + (t1 - t0) / 1000000 + " ms, parse " + (t2 - t1) / 1000000 + " ms, file " + br.text.length() + " chars");

        System.out.println("EmojiDbTest: " + ok + " ok, " + bad + " failed");
        if (bad > 0) System.exit(1);
    }
}
