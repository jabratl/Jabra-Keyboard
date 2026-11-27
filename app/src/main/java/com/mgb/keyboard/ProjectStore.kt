package com.mgb.keyboard

import com.mgb.keyboard.typing.FixNames
import com.mgb.keyboard.typing.NovelBook
import com.mgb.keyboard.typing.ReplaceEngine
import com.mgb.keyboard.typing.ReplacePairList
import com.mgb.keyboard.typing.ReplacePaste
import com.mgb.keyboard.typing.ReplaceProtect
import com.mgb.keyboard.typing.ReplaceRules

/**
 * v0.63.0 (PROJECT 2): everything a Project is, read and written straight from the prefs (no keyboard panel needed).
 * The main-app Project page (ProjectPage.kt) uses this. The keyboard's Replace panel keeps its own copy of the same rules
 * and reads the SAME prefs when it opens (ReplacePanel.reset() > loadProtect()), so both always show the same projects.
 * A project = the old novel: registry rp_novels (one name per line), active project rp_novel_cur, its terms live in the
 * "My pairs" sets of rp_sets (see NovelBook.mySetName), its Keep as is names in pref NovelBook.neverKey(name), Whole words = rp_proj_word.
 * Every function is in try / catch and returns a plain one-line message (or a value); nothing here throws.
 */
object ProjectStore {
    const val K_REG = "rp_novels"
    const val K_CUR = "rp_novel_cur"
    const val K_WORD = "rp_proj_word"

    class Info(val name: String, val terms: Int, val kept: Int, val active: Boolean)

    fun names(p: JabraPrefs): List<String> = try { NovelBook.parse(p.str(K_REG, "")) } catch (_: Throwable) { ArrayList<String>() }

    /** The active project (a name that is in the list), or "" = none. */
    fun current(p: JabraPrefs): String = try {
        val c = p.str(K_CUR, "")
        if (c.isNotEmpty() && NovelBook.has(names(p), c)) c else ""
    } catch (_: Throwable) { "" }

    fun setCurrent(p: JabraPrefs, name: String) { try { p.setStr(K_CUR, name) } catch (_: Throwable) { } }

    fun wholeWord(p: JabraPrefs): Boolean = try { p.bool(K_WORD, true) } catch (_: Throwable) { true }
    fun setWholeWord(p: JabraPrefs, on: Boolean) { try { p.setBool(K_WORD, on) } catch (_: Throwable) { } }

    private fun mineSets(p: JabraPrefs): LinkedHashMap<String, ReplaceStore.Def> {
        val all = ReplaceStore.load(p)
        val out = LinkedHashMap<String, ReplaceStore.Def>()
        for ((k, v) in all) if (NovelBook.isMine(k)) out[k] = v
        return out
    }

    fun rows(p: JabraPrefs, project: String): List<FixNames.Row> = try {
        val m = mineSets(p)
        FixNames.rows(project, ArrayList<String>(m.keys), m.values.map { it.rules })
    } catch (_: Throwable) { ArrayList<FixNames.Row>() }

    fun keep(p: JabraPrefs, project: String): List<String> = try {
        ReplaceProtect.parse(p.str(NovelBook.neverKey(project), ""))
    } catch (_: Throwable) { ArrayList<String>() }

    fun infos(p: JabraPrefs): List<Info> = try {
        val cur = current(p)
        val m = mineSets(p)
        val ns = ArrayList<String>(m.keys)
        val tx = m.values.map { it.rules }
        names(p).map { n -> Info(n, FixNames.rows(n, ns, tx).size, keep(p, n).size, n.equals(cur, true)) }
    } catch (_: Throwable) { ArrayList<Info>() }

    /** New empty project, made active. A name that exists = that project is selected. Returns a one-line message. */
    fun create(p: JabraPrefs, name: String): String {
        return try {
            val clean = name.trim()
            if (clean.isEmpty()) return "Type a project name."
            val reg = NovelBook.parse(p.str(K_REG, ""))
            if (NovelBook.has(reg, clean)) {
                setCurrent(p, reg.first { it.equals(clean, true) })
                return "That project already exists. It is active now."
            }
            if (!NovelBook.add(reg, clean)) return "Project limit reached (" + NovelBook.MAX_NOVELS + ")."
            p.setStr(K_REG, NovelBook.text(reg))
            if (!NovelBook.has(NovelBook.parse(p.str(K_REG, "")), clean)) return "Could not save the project. Storage write failed."
            setCurrent(p, clean)
            "Created " + clean + "."
        } catch (_: Throwable) { "Could not create the project." }
    }

    /** Deletes a project with its terms and its Keep as is names. Other projects stay. */
    fun delete(p: JabraPrefs, name: String): String {
        return try {
            if (name.isEmpty()) return "Nothing to delete."
            val all = ReplaceStore.load(p)
            all.keys.removeAll { NovelBook.belongs(it, name) }
            ReplaceStore.save(p, all)
            val reg = NovelBook.parse(p.str(K_REG, ""))
            reg.removeAll { it.equals(name, true) }
            p.setStr(K_REG, NovelBook.text(reg))
            p.setStr(NovelBook.neverKey(name), "")
            if (current(p).isEmpty() || p.str(K_CUR, "").equals(name, true)) setCurrent(p, reg.firstOrNull() ?: "")
            "Deleted " + name + "."
        } catch (_: Throwable) { "Could not delete." }
    }

    /** Saves ONE term to the project (its My pairs set). Same wrong forms = updated in place. Returns "Saved..." / "Updated..." or a problem text. */
    private fun addPair(p: JabraPrefs, project: String, forms: List<String>, to: String): String {
        try {
            if (project.isEmpty()) return "Pick a project first."
            if (forms.isEmpty()) return "Type the wrong forms first."
            if (to.isEmpty()) return "Type the right form."
            val m = ReplacePairList.make(forms.joinToString("; "), to, ReplaceEngine.MODE_TEXT, false, false)
            val r = m.rule ?: return m.error ?: "Could not add this term"
            val line = ReplacePairList.text(listOf(r)).trim()
            val key = line.substringBefore("=>").trim()
            val all = ReplaceStore.load(p)
            val mine = all.keys.filter { NovelBook.belongs(it, project) && NovelBook.isMine(it) }
            var target: String? = null
            for (n in mine) {
                val d = all[n] ?: continue
                if (d.rules.split("\n").any { l -> val t = l.trim(); t.isNotEmpty() && t[0] != '#' && t.substringBefore("=>").trim() == key }) { target = n; break }
            }
            if (target == null) for (n in mine) {
                val d = all[n] ?: continue
                if (ReplaceRules.parse(d.rules).active() < NovelBook.RULES_PER_SET) { target = n; break }
            }
            var updated = false
            val tg = target
            if (tg != null) {
                val d = all[tg] ?: return "Could not save"
                val mg = FixNames.mergeWithTopic(d.rules, line, NovelBook.MY_TOPIC, NovelBook.RULES_PER_SET)
                all[tg] = ReplaceStore.Def(mg.text, d.matchCase, false)
                updated = mg.updated > 0
            } else {
                if (NovelBook.setsOf(all.keys, project).size >= NovelBook.MAX_SETS_PER_NOVEL || all.size >= ReplaceStore.MAX_ALL) return "No room for a new set: delete old terms first."
                var num = mine.size + 1
                var nm = NovelBook.mySetName(project, num)
                while (all.containsKey(nm)) { num++; nm = NovelBook.mySetName(project, num) }
                val mg = FixNames.mergeWithTopic("# " + nm + " (pairs saved by me)\n", line, NovelBook.MY_TOPIC, NovelBook.RULES_PER_SET)
                all[nm] = ReplaceStore.Def(mg.text, false, false)
            }
            ReplaceStore.save(p, all)
            return (if (updated) "Updated, saved to " else "Saved to ") + project + "."
        } catch (_: Throwable) { return "Could not save" }
    }

    /** One typed line  "gaam; geme; gome => game". [editFind] = Rule.find of the term being edited ("" = a new term). */
    fun addLine(p: JabraPrefs, project: String, line: String, editFind: String): String {
        return try {
            val l = line.trim()
            if (l.isEmpty()) return "Type the term first, like: gaam; geme => game"
            if (project.isEmpty()) return "Pick a project first."
            val m = ReplacePairList.make(l, "", ReplaceEngine.MODE_TEXT, false, false)
            val r = m.rule ?: return m.error ?: "Use: wrong; wrong => right"
            val vs = ReplaceRules.variants(r.find)
            val forms: List<String> = if (vs.isEmpty()) listOf(r.find) else vs
            if (editFind.isNotEmpty() && editFind != r.find) deleteTerm(p, project, editFind)
            val sv = addPair(p, project, forms, r.with)
            if (sv.startsWith("Saved") || sv.startsWith("Updated")) (if (editFind.isNotEmpty()) "Term saved." else "Term added.") else sv
        } catch (_: Throwable) { "Could not add" }
    }

    /** Removes the term whose rule.find is [find]. Comments and other terms stay. */
    fun deleteTerm(p: JabraPrefs, project: String, find: String): String {
        return try {
            val all = ReplaceStore.load(p)
            var hit = 0
            for (n in all.keys.filter { NovelBook.belongs(it, project) && NovelBook.isMine(it) }) {
                val d = all[n] ?: continue
                val kept = ArrayList<String>()
                var changed = false
                for (l in d.rules.split("\n")) {
                    val t = l.trim()
                    var drop = false
                    if (t.isNotEmpty() && t[0] != '#') {
                        val pr = ReplaceRules.parse(l).rules.firstOrNull { it.kind == ReplaceRules.K_TEXT }
                        if (pr != null && pr.find == find) drop = true
                    }
                    if (drop) { changed = true; hit++ } else kept.add(l)
                }
                if (changed) all[n] = ReplaceStore.Def(kept.joinToString("\n"), d.matchCase, false)
            }
            if (hit > 0) ReplaceStore.save(p, all)
            if (hit > 0) "Term deleted." else "Term not found."
        } catch (_: Throwable) { "Could not delete" }
    }

    /** Many lines (one term per line, same forms the keyboard's Paste list reads) into the project. */
    fun pasteLines(p: JabraPrefs, project: String, text: String): String {
        return try {
            if (project.isEmpty()) return "Pick a project first."
            val res = ReplacePaste.parse(text, ArrayList<ReplaceRules.Rule>(), 200)
            if (res.changed() == 0) return res.summary()
            var ok = 0
            var skipped = 0
            for (r in res.rules) {
                if (r.kind != ReplaceRules.K_TEXT || r.with.isEmpty()) { skipped++; continue }
                val vs = ReplaceRules.variants(r.find)
                val forms: List<String> = if (vs.isEmpty()) listOf(r.find) else vs
                val sv = addPair(p, project, forms, r.with)
                if (sv.startsWith("Saved") || sv.startsWith("Updated")) ok++ else skipped++
            }
            "Added " + ok + " to " + project + (if (skipped > 0) " \u00B7 " + skipped + " skipped" else "")
        } catch (_: Throwable) { "Could not paste" }
    }

    /** Keep as is: one name or several lines. */
    fun addKeep(p: JabraPrefs, project: String, text: String): String {
        return try {
            val n = text.trim()
            if (project.isEmpty()) return "Pick a project first."
            if (n.isEmpty()) return "Type the name first."
            val l = ArrayList<String>(ReplaceProtect.parse(p.str(NovelBook.neverKey(project), "")))
            val c = ReplaceProtect.addAll(l, n)
            p.setStr(NovelBook.neverKey(project), ReplaceProtect.text(l))
            if (c[0] > 0) "Kept as is: " + c[0] + " added."
            else if (c[1] > 0) "Already kept."
            else if (c[2] > 0) "The list is full (" + ReplaceProtect.MAX_TERMS + ")."
            else "Nothing to add."
        } catch (_: Throwable) { "Could not add" }
    }

    fun removeKeep(p: JabraPrefs, project: String, term: String) {
        try {
            val l = ArrayList<String>(ReplaceProtect.parse(p.str(NovelBook.neverKey(project), "")))
            l.remove(term)
            p.setStr(NovelBook.neverKey(project), ReplaceProtect.text(l))
        } catch (_: Throwable) { }
    }

    fun clearKeep(p: JabraPrefs, project: String) { try { p.setStr(NovelBook.neverKey(project), "") } catch (_: Throwable) { } }
}
