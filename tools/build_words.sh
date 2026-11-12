#!/bin/sh
# v0.10.22 (W8): make assets/words/en.txt or bn.txt from a frequency list. Run from the repo root. Needs a JDK (11+).
#   sh tools/build_words.sh en en_50k.txt      -> app/src/main/assets/words/en.txt
#   sh tools/build_words.sh bn bn_50k.txt      -> app/src/main/assets/words/bn.txt
# The OLD file is read as the "starter words" (kept on top). Never edit the generated file by hand.
# Word lists: FrequencyWords (hermitdave), content CC-BY-SA-4.0 (see docs/LICENSES.txt).
LANG="$1"; IN="$2"
[ -n "$LANG" ] && [ -n "$IN" ] || { echo "usage: sh tools/build_words.sh <en|bn> <frequency file>"; exit 2; }
OUT="app/src/main/assets/words/$LANG.txt"
TMP=$(mktemp -d) || exit 2
cp "$OUT" "$TMP/starter.txt" 2>/dev/null || : > "$TMP/starter.txt"
javac -encoding UTF-8 -d "$TMP" tools/WordBuild.java || { rm -rf "$TMP"; exit 1; }
java -Dfile.encoding=UTF-8 -cp "$TMP" WordBuild "$LANG" "$IN" "$TMP/starter.txt" "$OUT" ${3:-30000} ${4:-409600}
R=$?
rm -rf "$TMP"
exit $R
