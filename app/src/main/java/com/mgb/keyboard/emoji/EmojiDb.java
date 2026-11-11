package com.mgb.keyboard.emoji;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v0.8.0 (E1) reader of the Jabra emoji data file (pure Java, no Android; tested by tools/EmojiDbTest.java).
 * The file is made from Unicode emoji-test.txt by EmojiBuild (never edit it by hand). Plain UTF-8 text, LF lines:
 *   #JABRA-EMOJI 1            magic + format (an unknown format number = file refused, old behaviour stays)
 *   #unicode 17.0             Unicode version of the list
 *   #count 1938               number of emoji lines (must match, so a cut-off file is refused)
 *   #mods 261D 26F9 ...       code points that accept a skin tone (hex)
 *   @G&lt;TAB&gt;Smileys &amp; Emotion  group (tab order = file order, groups are contiguous)
 *   @S&lt;TAB&gt;face-smiling       subgroup of the current group
 *   &lt;emoji&gt;&lt;TAB&gt;&lt;name&gt;&lt;TAB&gt;&lt;flags&gt;&lt;TAB&gt;&lt;version&gt;   flags: "t" = has skin tones (variants are made in code, EmojiTone), "-" = none; version e.g. 13.1
 * Lines starting with another # (and no TAB) are ignored; the keycap emoji # starts with '#' too, so a line with a TAB is always an emoji line. ANY other problem = parseOrNull returns null = caller keeps the old emoji panel.
 */
public final class EmojiDb {
    public static final int FORMAT = 1;
    public static final String MAGIC = "#JABRA-EMOJI";
    /** Safety cap when reading a stream (the real file is ~100 KB). */
    public static final int MAX_BYTES = 4 * 1024 * 1024;

    private final String unicode;
    private final String[] emoji;
    private final String[] name;
    private final String[] ver;
    private final boolean[] tone;
    private final short[] grp;
    private final short[] sub;
    private final String[] groupNames;
    private final int[] groupStart;       // length groups + 1
    private final String[] subNames;
    private final int[] mods;             // sorted

    private EmojiDb(String unicode, String[] emoji, String[] name, String[] ver, boolean[] tone, short[] grp, short[] sub,
                    String[] groupNames, int[] groupStart, String[] subNames, int[] mods) {
        this.unicode = unicode; this.emoji = emoji; this.name = name; this.ver = ver; this.tone = tone;
        this.grp = grp; this.sub = sub; this.groupNames = groupNames; this.groupStart = groupStart;
        this.subNames = subNames; this.mods = mods;
    }

    // ---- queries (all index based, no allocation except tone()) ----
    public String unicodeVersion() { return unicode; }
    public int size() { return emoji.length; }
    public int groupCount() { return groupNames.length; }
    public String groupName(int g) { return g >= 0 && g < groupNames.length ? groupNames[g] : ""; }
    /** First item index of group g. */
    public int groupStart(int g) { return g >= 0 && g < groupNames.length ? groupStart[g] : 0; }
    /** End (exclusive) item index of group g; items of one group are contiguous. */
    public int groupEnd(int g) { return g >= 0 && g < groupNames.length ? groupStart[g + 1] : 0; }
    public String emoji(int i) { return emoji[i]; }
    public String name(int i) { return name[i]; }
    /** Unicode emoji version the emoji came in (e.g. "13.1"); E3 can use it to hide very new emoji. */
    public String version(int i) { return ver[i]; }
    public boolean toneable(int i) { return tone[i]; }
    public int group(int i) { return grp[i]; }
    public String subgroup(int i) { return subNames[sub[i]]; }

    /** Skin tone variant (tone 0..4) of item i; the base emoji itself when it has no tones or tone is bad. */
    public String tone(int i, int t) {
        if (!tone[i]) return emoji[i];
        return EmojiTone.apply(emoji[i], t, mods);
    }

    /** Same rule for any base string (e.g. a "recent" entry). Returns base unchanged when it has no modifier-base code point. */
    public String tone(String base, int t) { return EmojiTone.apply(base, t, mods); }

    // ---- parsing ----
    /** Parse or return null on ANY problem (never throws). */
    public static EmojiDb parseOrNull(String text) {
        try { return parse(text); } catch (Throwable e) { return null; }
    }

    /** Reads the whole stream (max MAX_BYTES, UTF-8) and parses it; null on any problem. The stream is NOT closed here. */
    public static EmojiDb parseOrNull(InputStream in) {
        try {
            if (in == null) return null;
            ByteArrayOutputStream bo = new ByteArrayOutputStream(131072);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                bo.write(buf, 0, n);
                if (bo.size() > MAX_BYTES) return null;
            }
            return parse(new String(bo.toByteArray(), StandardCharsets.UTF_8));
        } catch (Throwable e) { return null; }
    }

    /** Strict parse; throws IllegalArgumentException("line N: ...") (used by tests and the tool for a clear message). */
    public static EmojiDb parse(String text) {
        if (text == null) throw new IllegalArgumentException("null text");
        if (text.length() > 0 && text.charAt(0) == '﻿') text = text.substring(1);
        String[] lines = text.split("\n", -1);
        int ln = 0;
        boolean magic = false;
        String unicode = "";
        int declared = -1;
        int[] mods = new int[0];
        List<String> gNames = new ArrayList<String>();
        List<Integer> gStarts = new ArrayList<Integer>();
        List<String> sNames = new ArrayList<String>();
        List<String> em = new ArrayList<String>(), nm = new ArrayList<String>(), vr = new ArrayList<String>();
        List<Boolean> tn = new ArrayList<Boolean>();
        List<Short> gi = new ArrayList<Short>(), si = new ArrayList<Short>();
        Set<String> seen = new HashSet<String>();
        int curG = -1, curS = -1;
        for (String raw : lines) {
            ln++;
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            if (line.length() == 0) continue;
            if (!magic) {
                if (!line.equals(MAGIC + " " + FORMAT)) throw new IllegalArgumentException("line " + ln + ": not a Jabra emoji file (format " + FORMAT + ")");
                magic = true;
                continue;
            }
            char c0 = line.charAt(0);
            if (c0 == '#' && line.indexOf('\t') < 0) {   // a keycap emoji (#) also starts with '#', but only emoji lines contain a TAB
                if (line.startsWith("#unicode ")) unicode = line.substring(9).trim();
                else if (line.startsWith("#count ")) {
                    try { declared = Integer.parseInt(line.substring(7).trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("line " + ln + ": bad #count"); }
                } else if (line.startsWith("#mods")) {
                    String[] p = line.substring(5).trim().split(" +");
                    List<Integer> l = new ArrayList<Integer>();
                    for (String s : p) {
                        if (s.length() == 0) continue;
                        int v;
                        try { v = Integer.parseInt(s, 16); } catch (NumberFormatException e) { throw new IllegalArgumentException("line " + ln + ": bad #mods"); }
                        if (v < 0 || v > 0x10FFFF) throw new IllegalArgumentException("line " + ln + ": bad #mods value");
                        l.add(v);
                    }
                    mods = new int[l.size()];
                    for (int i = 0; i < mods.length; i++) mods[i] = l.get(i);
                    java.util.Arrays.sort(mods);
                }
                continue;
            }
            if (c0 == '@') {
                String[] p = line.split("\t", -1);
                if (p.length != 2 || p[1].length() == 0) throw new IllegalArgumentException("line " + ln + ": bad header line");
                if (p[0].equals("@G")) {
                    if (gNames.contains(p[1])) throw new IllegalArgumentException("line " + ln + ": group repeated: " + p[1]);
                    gNames.add(p[1]);
                    gStarts.add(em.size());
                    curG = gNames.size() - 1;
                    curS = -1;
                } else if (p[0].equals("@S")) {
                    if (curG < 0) throw new IllegalArgumentException("line " + ln + ": subgroup before group");
                    sNames.add(p[1]);
                    curS = sNames.size() - 1;
                } else throw new IllegalArgumentException("line " + ln + ": unknown header " + p[0]);
                continue;
            }
            // emoji line
            if (curG < 0) throw new IllegalArgumentException("line " + ln + ": emoji before group");
            if (curS < 0) { sNames.add(""); curS = sNames.size() - 1; }   // subgroup is optional
            String[] p = line.split("\t", -1);
            if (p.length != 4) throw new IllegalArgumentException("line " + ln + ": need 4 columns, got " + p.length);
            if (p[0].length() == 0 || p[1].length() == 0 || p[3].length() == 0) throw new IllegalArgumentException("line " + ln + ": empty column");
            if (p[0].indexOf(' ') >= 0) throw new IllegalArgumentException("line " + ln + ": space inside emoji");
            if (!(p[2].equals("t") || p[2].equals("-"))) throw new IllegalArgumentException("line " + ln + ": bad flags");
            if (!seen.add(p[0])) throw new IllegalArgumentException("line " + ln + ": duplicate emoji");
            if (gNames.size() > Short.MAX_VALUE || sNames.size() > Short.MAX_VALUE) throw new IllegalArgumentException("too many groups");
            em.add(p[0]); nm.add(p[1]); tn.add(p[2].equals("t")); vr.add(p[3]);
            gi.add((short) curG); si.add((short) curS);
        }
        if (!magic) throw new IllegalArgumentException("empty file");
        if (em.isEmpty()) throw new IllegalArgumentException("no emoji");
        if (declared >= 0 && declared != em.size()) throw new IllegalArgumentException("#count " + declared + " but " + em.size() + " emoji (file cut off?)");
        // drop groups that stayed empty (cannot happen from EmojiBuild, but keep ranges valid)
        int g = gNames.size();
        int[] starts = new int[g + 1];
        for (int i = 0; i < g; i++) starts[i] = gStarts.get(i);
        starts[g] = em.size();
        for (int i = 0; i < g; i++) if (starts[i] >= starts[i + 1]) throw new IllegalArgumentException("empty group " + gNames.get(i));
        int n = em.size();
        String[] ea = em.toArray(new String[n]), na = nm.toArray(new String[n]), va = vr.toArray(new String[n]);
        boolean[] ta = new boolean[n];
        short[] ga = new short[n], sa = new short[n];
        for (int i = 0; i < n; i++) { ta[i] = tn.get(i); ga[i] = gi.get(i); sa[i] = si.get(i); }
        return new EmojiDb(unicode, ea, na, va, ta, ga, sa, gNames.toArray(new String[g]), starts, sNames.toArray(new String[sNames.size()]), mods);
    }
}
