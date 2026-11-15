package com.mgb.keyboard.hist;

/** v0.23.0 (H3) texts for "exclude this app" on the typing history page (pure Java, tested by tools/HistPart3Test.java). */
public final class HistExcl {
    private HistExcl() { }

    public static String menuTitle(String appName) { return appName == null || appName.isEmpty() ? "This entry" : appName; }
    public static String deleteItem() { return "Delete this entry"; }
    public static String excludeItem(String appName) { return "Do not save typing in " + (appName == null || appName.isEmpty() ? "this app" : appName); }
    public static String cancel() { return "Cancel"; }

    public static String askTitle(String appName) { return (appName == null || appName.isEmpty() ? "This app" : appName) + " is excluded"; }

    /** n = entries of that app already saved. */
    public static String askBody(int n) {
        if (n <= 0) return "Nothing new from this app is saved from now on. You can allow it again at the bottom of this page.";
        return "Nothing new from this app is saved from now on. " + n + (n == 1 ? " entry from it is" : " entries from it are")
            + " still stored. Delete " + (n == 1 ? "it" : "them") + " now?";
    }

    public static String deleteThem(int n) { return n == 1 ? "Delete 1 entry" : "Delete " + n + " entries"; }
    public static String keepThem() { return "Keep them"; }
    public static String doneToast(int removed) { return removed <= 0 ? "App excluded" : "App excluded, " + removed + (removed == 1 ? " entry deleted" : " entries deleted"); }
    public static String allowedToast() { return "App is allowed again"; }
    public static String allowLabel() { return "Allow again"; }

    public static String sectionTitle(int n) { return "Excluded apps (" + n + ")"; }
    public static String sectionHelp() { return "Typing in these apps is not saved. Entries already saved stay until you delete them or the keep time ends."; }
    public static String hintLine() { return "Tap an entry to copy it. Hold an entry for more: delete it, or stop saving that app."; }
}
