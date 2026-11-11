package com.mgb.keyboard.emoji;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * v0.8.0 (E1) generator: Unicode "emoji-test.txt" -> the small Jabra emoji data file (format in EmojiDb). Pure Java, no Android.
 * Run it again when a new Unicode version comes out (download emoji-test.txt from https://unicode.org/Public/emoji/latest/emoji-test.txt):
 *   sh tools/build_emoji.sh emoji-test.txt        (compiles emoji/EmojiTone+EmojiDb+EmojiBuild, writes app/src/main/assets/emoji/emoji_v1.txt)
 * What it keeps: only "fully-qualified" lines, group "Component" dropped. Skin tone variants are NOT stored: a base emoji gets flag "t"
 * only when ALL 5 uniform-tone variants exist in the input and EmojiTone.apply() reproduces them exactly (so code-made variants are
 * always real Unicode emoji). Mixed-tone couples / handshakes are not kept (the tone picker offers one tone for the whole emoji).
 * It also writes the modifier-base list (#mods) the app needs to make variants. Problems that do not stop the build are returned as warnings.
 * It lives in the app source tree so a later step (E6 "Update emoji list") can run the same code on the phone.
 */
public final class EmojiBuild {
    private EmojiBuild() { }

    /** Result of build(). */
    public static final class Result {
        public String text;            // the data file content
        public String unicodeVersion;  // from "# Version:" (else the highest E-number seen)
        public int count;              // emoji lines written
        public int groups;
        public int toneable;           // base emoji with flag t
        public int toneVariantsSkipped;
        public int notFullyQualifiedSkipped;
        public final List<String> warnings = new ArrayList<String>();
    }

    private static final class Ent {
        int[] cps; String emoji; String ver; String name; String group; String sub;
    }

    /** Build the data file text from the content of emoji-test.txt. Throws IllegalArgumentException("line N: ...") on a broken input. */
    public static Result build(String src) {
        if (src == null) throw new IllegalArgumentException("no input");
        if (src.length() > 0 && src.charAt(0) == '﻿') src = src.substring(1);
        Result r = new Result();
        String[] lines = src.split("\n", -1);
        String group = null, sub = "", headerVer = null;
        double maxVer = 0;
        String maxVerS = null;
        List<Ent> all = new ArrayList<Ent>();
        int ln = 0;
        for (String raw : lines) {
            ln++;
            String t = raw.trim();
            if (t.length() == 0) continue;
            if (t.charAt(0) == '#') {
                if (t.startsWith("# group:")) { group = t.substring(8).trim(); sub = ""; }
                else if (t.startsWith("# subgroup:")) sub = t.substring(11).trim();
                else if (t.startsWith("# Version:")) headerVer = firstNumber(t.substring(10));
                continue;
            }
            int semi = t.indexOf(';');
            int hash = semi < 0 ? -1 : t.indexOf('#', semi + 1);
            if (semi < 0 || hash < 0) throw new IllegalArgumentException("line " + ln + ": expected 'code points ; status # emoji Ex.y name'");
            String status = t.substring(semi + 1, hash).trim();
            int[] cps = hexList(t.substring(0, semi), ln);
            if (!status.equals("fully-qualified")) { r.notFullyQualifiedSkipped++; continue; }
            if (group == null) throw new IllegalArgumentException("line " + ln + ": entry before any '# group:' line");
            String comment = t.substring(hash + 1).trim();
            int sp1 = comment.indexOf(' ');
            int sp2 = sp1 < 0 ? -1 : comment.indexOf(' ', sp1 + 1);
            if (sp1 < 0 || sp2 < 0 || comment.charAt(sp1 + 1) != 'E') throw new IllegalArgumentException("line " + ln + ": bad comment, expected '# emoji Ex.y name'");
            String shown = comment.substring(0, sp1);
            String ver = comment.substring(sp1 + 2, sp2);
            if (firstNumber(ver) == null) throw new IllegalArgumentException("line " + ln + ": bad version " + ver);
            Ent e = new Ent();
            e.cps = cps;
            e.emoji = new String(cps, 0, cps.length);
            e.ver = ver;
            e.name = comment.substring(sp2 + 1).trim().replace('\t', ' ');
            e.group = group;
            e.sub = sub;
            if (e.name.length() == 0) throw new IllegalArgumentException("line " + ln + ": empty name");
            if (!shown.equals(e.emoji)) r.warnings.add("line " + ln + ": comment emoji differs from the code points (" + e.name + ")");
            try { double d = Double.parseDouble(ver); if (d >= maxVer) { maxVer = d; maxVerS = ver; } } catch (NumberFormatException x) { /* ignore */ }
            all.add(e);
        }
        r.unicodeVersion = headerVer != null ? headerVer : (maxVerS != null ? maxVerS : "0");

        // 1) modifier bases = the code point right before a tone modifier
        TreeSet<Integer> modSet = new TreeSet<Integer>();
        for (Ent e : all) {
            for (int i = 1; i < e.cps.length; i++) {
                if (EmojiTone.isTone(e.cps[i])) {
                    if (EmojiTone.isTone(e.cps[i - 1]) || e.cps[i - 1] == 0xFE0F) r.warnings.add("odd tone position in: " + e.name);
                    else modSet.add(e.cps[i - 1]);
                }
            }
        }
        int[] mods = new int[modSet.size()];
        int mi = 0;
        for (int v : modSet) mods[mi++] = v;

        // 2) split into base entries and tone variants
        Set<String> toneStrings = new HashSet<String>();
        List<Ent> bases = new ArrayList<Ent>();
        List<Ent> uniformVariants = new ArrayList<Ent>();
        for (Ent e : all) {
            if (hasTone(e.cps)) {
                r.toneVariantsSkipped++;
                toneStrings.add(e.emoji);
                if (uniformTone(e.cps)) uniformVariants.add(e);
            } else bases.add(e);
        }

        // 3) a base is toneable only if all 5 generated variants really exist
        Set<String> matched = new HashSet<String>();
        Set<String> toneable = new HashSet<String>();
        for (Ent b : bases) {
            if (!hasModBase(b.cps, modSet)) continue;
            int found = 0;
            String[] gen = new String[EmojiTone.COUNT];
            for (int t = 0; t < EmojiTone.COUNT; t++) {
                gen[t] = EmojiTone.apply(b.emoji, t, mods);
                if (toneStrings.contains(gen[t])) found++;
            }
            if (found == EmojiTone.COUNT) {
                toneable.add(b.emoji);
                for (String s : gen) matched.add(s);
            } else if (found > 0) {
                r.warnings.add("only " + found + " of 5 tone variants reproduced for: " + b.name + " (left without tones)");
                for (String s : gen) if (toneStrings.contains(s)) matched.add(s);   // already reported, no second warning per variant
            }
        }
        for (Ent v : uniformVariants) if (!matched.contains(v.emoji)) r.warnings.add("tone variant not made by any base (rule gap?): " + v.name);

        // 4) write
        StringBuilder sb = new StringBuilder(all.size() * 40);
        StringBuilder body = new StringBuilder(all.size() * 40);
        String curG = null, curS = null;
        int groups = 0, count = 0;
        Set<String> seen = new HashSet<String>();
        for (Ent b : bases) {
            if (!seen.add(b.emoji)) { r.warnings.add("duplicate emoji skipped: " + b.name); continue; }
            if (!b.group.equals(curG)) {
                if (b.group.length() == 0) throw new IllegalArgumentException("empty group name");
                body.append("@G\t").append(b.group).append('\n');
                curG = b.group; curS = null; groups++;
            }
            if (!b.sub.equals(curS)) {
                if (b.sub.length() > 0) body.append("@S\t").append(b.sub).append('\n');
                curS = b.sub;
            }
            body.append(b.emoji).append('\t').append(b.name).append('\t').append(toneable.contains(b.emoji) ? "t" : "-").append('\t').append(b.ver).append('\n');
            count++;
        }
        if (count == 0) throw new IllegalArgumentException("no fully-qualified emoji found in the input");
        // a group name used twice (not contiguous in the input) would be refused by EmojiDb: make sure it cannot happen
        Set<String> gs = new HashSet<String>();
        for (String l : body.toString().split("\n")) if (l.startsWith("@G\t") && !gs.add(l)) throw new IllegalArgumentException("group not contiguous in the input: " + l.substring(3));
        sb.append(EmojiDb.MAGIC).append(' ').append(EmojiDb.FORMAT).append('\n');
        sb.append("#unicode ").append(r.unicodeVersion).append('\n');
        sb.append("#count ").append(count).append('\n');
        sb.append("#mods");
        for (int m : mods) sb.append(' ').append(String.format("%X", m));
        sb.append('\n');
        sb.append(body);
        r.text = sb.toString();
        r.count = count;
        r.groups = groups;
        r.toneable = toneable.size();
        // self check: our own reader must accept what we wrote
        EmojiDb chk = EmojiDb.parse(r.text);
        if (chk.size() != count) throw new IllegalArgumentException("self check failed: size");
        return r;
    }

    private static String firstNumber(String s) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+(\\.\\d+)*").matcher(s);
        return m.find() ? m.group() : null;
    }

    private static int[] hexList(String s, int ln) {
        String[] p = s.trim().split(" +");
        if (p.length == 0 || p[0].length() == 0) throw new IllegalArgumentException("line " + ln + ": no code points");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            try { r[i] = Integer.parseInt(p[i], 16); } catch (NumberFormatException e) { throw new IllegalArgumentException("line " + ln + ": bad code point " + p[i]); }
            if (r[i] < 0 || r[i] > 0x10FFFF || (r[i] >= 0xD800 && r[i] <= 0xDFFF)) throw new IllegalArgumentException("line " + ln + ": code point out of range " + p[i]);
        }
        return r;
    }

    private static boolean hasTone(int[] c) { for (int v : c) if (EmojiTone.isTone(v)) return true; return false; }

    /** All tone modifiers in the sequence are the same one. */
    private static boolean uniformTone(int[] c) {
        int first = -1;
        for (int v : c) if (EmojiTone.isTone(v)) { if (first < 0) first = v; else if (v != first) return false; }
        return first >= 0;
    }

    private static boolean hasModBase(int[] c, Set<Integer> mods) { for (int v : c) if (mods.contains(v)) return true; return false; }

    /** java.io only (java.nio.file needs Android API 26, the app has minSdk 24). */
    private static byte[] readAll(File f) throws java.io.IOException {
        InputStream in = new FileInputStream(f);
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } finally { in.close(); }
    }

    /** CLI: java -cp <classes> com.mgb.keyboard.emoji.EmojiBuild <emoji-test.txt> <output file>  (tools/build_emoji.sh does it all) */
    public static void main(String[] a) throws Exception {
        if (a.length != 2) {
            System.out.println("usage: EmojiBuild <emoji-test.txt> <output file, e.g. app/src/main/assets/emoji/emoji_v1.txt>   (or: sh tools/build_emoji.sh <emoji-test.txt>)");
            System.exit(2);
            return;
        }
        String src;
        try { src = new String(readAll(new File(a[0])), StandardCharsets.UTF_8); }
        catch (java.io.IOException e) { System.out.println("ERROR: cannot read " + a[0] + " (" + e.getMessage() + ")"); System.exit(1); return; }
        Result r;
        try { r = build(src); } catch (IllegalArgumentException e) { System.out.println("ERROR: " + e.getMessage()); System.exit(1); return; }
        File out = new File(a[1]);
        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        byte[] bytes = r.text.getBytes(StandardCharsets.UTF_8);
        OutputStream os = new FileOutputStream(out);
        try { os.write(bytes); } finally { os.close(); }
        System.out.println("Unicode " + r.unicodeVersion + ": " + r.count + " emoji in " + r.groups + " groups, " + r.toneable + " with skin tones ("
            + r.toneVariantsSkipped + " tone variants + " + r.notFullyQualifiedSkipped + " not-fully-qualified lines left out), file " + bytes.length + " bytes -> " + out.getPath());
        for (String w : r.warnings) System.out.println("WARNING: " + w);
        if (r.count < 1000) System.out.println("WARNING: fewer than 1000 emoji - is this the full emoji-test.txt?");
    }
}
