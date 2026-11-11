#!/bin/sh
# v0.8.0 (E1): make the emoji data file from Unicode's emoji-test.txt. Run from the repo root.
#   sh tools/build_emoji.sh emoji-test.txt                      -> app/src/main/assets/emoji/emoji_v1.txt
#   sh tools/build_emoji.sh emoji-test.txt some/other/out.txt
# Get the input: https://unicode.org/Public/emoji/latest/emoji-test.txt (save as emoji-test.txt). Needs a JDK (11+).
IN="$1"
OUT="${2:-app/src/main/assets/emoji/emoji_v1.txt}"
[ -n "$IN" ] || { echo "usage: sh tools/build_emoji.sh <emoji-test.txt> [output file]"; exit 2; }
TMP=$(mktemp -d) || exit 2
SRC=app/src/main/java/com/mgb/keyboard/emoji
if command -v javac >/dev/null 2>&1; then
  javac -encoding UTF-8 -d "$TMP" $SRC/EmojiTone.java $SRC/EmojiDb.java $SRC/EmojiBuild.java || { rm -rf "$TMP"; exit 1; }
else
  java --add-modules jdk.compiler -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$TMP" $SRC/EmojiTone.java $SRC/EmojiDb.java $SRC/EmojiBuild.java || { rm -rf "$TMP"; exit 1; }
fi
java -Dfile.encoding=UTF-8 -cp "$TMP" com.mgb.keyboard.emoji.EmojiBuild "$IN" "$OUT"
RC=$?
rm -rf "$TMP"
exit $RC
