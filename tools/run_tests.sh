#!/usr/bin/env bash
# v0.17.3 (K0): compile app/**/*.java + tools/*Test.java and run EVERY *Test class.
# Used by .github/workflows/build.yml (step "Java tests") and by chats. Exit 0 = all green, 1 = something red.
# Rollback: delete the workflow step "Java tests" (or put an empty file SKIP_TESTS.txt in the repo root).
set -u
cd "$(dirname "$0")/.."
OUT="${TMPDIR:-/tmp}/jabra_cls_$$"
rm -rf "$OUT"; mkdir -p "$OUT"
if command -v javac >/dev/null 2>&1; then JAVAC=(javac); else JAVAC=(java --add-modules jdk.compiler -m jdk.compiler/com.sun.tools.javac.Main); fi
if ! "${JAVAC[@]}" -encoding UTF-8 -nowarn -sourcepath app/src/main/java:tools -d "$OUT" $(find app -name '*.java') tools/*Test.java tools/WordBuild.java > "$OUT/compile.log" 2>&1; then
  echo "TESTS: COMPILE ERROR (a test or a pure-Java file does not compile):"
  grep -E 'error:' "$OUT/compile.log" | head -n 25
  rm -rf "$OUT"; exit 1
fi
PASS=0; FAILN=0; FAILED=""
for F in tools/*Test.java; do
  N=$(basename "$F" .java)
  R=$(java -Dfile.encoding=UTF-8 -cp "$OUT" "$N" 2>&1); RC=$?
  if [ "$RC" -eq 0 ] && ! echo "$R" | grep -q '^FAIL'; then PASS=$((PASS+1))
  else FAILN=$((FAILN+1)); FAILED="$FAILED $N"; echo "--- $N (exit $RC) ---"; echo "$R" | grep -E 'FAIL|Exception|Error' | head -n 8; fi
done
echo "Java tests: $PASS/$((PASS+FAILN)) green"
rm -rf "$OUT"
[ "$FAILN" -eq 0 ] || { echo "RED:$FAILED"; exit 1; }
