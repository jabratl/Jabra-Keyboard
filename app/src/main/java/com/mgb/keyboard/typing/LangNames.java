package com.mgb.keyboard.typing;

/**
 * v0.16.2 (plan X, step X2): the ONE place with the names the user SEES for the two Bangla keyboards.
 *   AVRO    (pref value "avro",    phonetic)     -> shown as "Bangলা"
 *   PROBHAT (pref value "probhat", fixed layout) -> shown as "বাংলা"
 * Only DISPLAY text lives here. Pref values, storage keys (bn_layout, lang_avro_on, lang_probhat_on) never change.
 * Pure Java, no Android. Every method returns a non-empty text and never throws.
 */
public final class LangNames {
    private LangNames() { }

    /** "Bangলা" = "Bang" + LA + AA-kar. */
    public static final String AVRO = "Bang\u09B2\u09BE";
    /** "বাংলা" = BA AA-kar ANUSVARA LA AA-kar. */
    public static final String PROBHAT = "\u09AC\u09BE\u0982\u09B2\u09BE";

    /** Name of a Bangla layout: probhat = false -> "Bangলা", true -> "বাংলা". */
    public static String name(boolean probhat) { return probhat ? PROBHAT : AVRO; }

    /** Text on the language key while Bangla is on (English shows "EN", decided by the caller). */
    public static String keyLabel(boolean probhat) { return name(probhat); }

    /** Short name in the live bubble while swiping on the space key (English = "English", decided by the caller). */
    public static String bubbleName(boolean probhat) { return name(probhat); }

    /** Text for the app Typing summary line (before ", space swipe on / off"). */
    public static String summary(boolean probhat) { return name(probhat); }

    /** Setting row choices for "Bangla layout": phonetic first, fixed second (same order as the pref values avro, probhat). */
    public static String[] layoutChoices() { return new String[] { AVRO + " (phonetic)", PROBHAT + " (fixed)" }; }

    /** "Language key tap" choices (index = pref lang_tap 0..2). */
    public static String[] tapNames() {
        return new String[] { "EN <-> Bangla", "Cycle EN > " + AVRO + " > " + PROBHAT, "Cycle EN > " + PROBHAT + " > " + AVRO };
    }

    /** "Bangla first" choices (index = pref lang_fav 0..2). */
    public static String[] favNames() { return new String[] { "Last used", AVRO, PROBHAT }; }
}
