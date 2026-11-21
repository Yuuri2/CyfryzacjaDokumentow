#!/usr/bin/env bash
# Test uploadu: dla każdego folderu w Materiały/Materiały_dc (poza "Materiały do zadania dla Uczestników")
# wysyła maksymalnie 1 plik każdego typu (pdf, doc, docx, ods, xlsx, jpg, jpeg, png).
# Użycie: ./scripts/upload-tests.sh [URL]   (domyślnie http://localhost:8080/upload)
set -u
URL="${1:-http://localhost:8080/upload}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/Materiały/Materiały_dc"
EXTS=(pdf doc docx ods xlsx jpg jpeg png)
SKIP="Materiały do zadania dla Uczestników"
ok=0; fail=0

for dir in "$ROOT"/*/; do
  name="$(basename "$dir")"
  [ "$name" = "$SKIP" ] && continue
  echo "== $name"
  for ext in "${EXTS[@]}"; do
    # pierwszy plik danego typu (bez wersji "wizualizacja formularza po cyfryzacji")
    file="$(find "$dir" -maxdepth 1 -type f -iname "*.$ext" ! -iname '*wizualizacj*' | sort | head -n 1)"
    [ -z "$file" ] && { printf '   %-5s brak pliku\n' "$ext"; continue; }
    code="$(curl -s -o /dev/null -w '%{http_code}' -X PUT -F "file=@$file" "$URL")"
    if [[ "$code" =~ ^2 ]]; then ok=$((ok+1)); else fail=$((fail+1)); fi
    printf '   %-5s %s  %s\n' "$ext" "$code" "$(basename "$file")"
  done
done

echo; echo "Podsumowanie: OK=$ok, BŁĘDY=$fail"
[ "$fail" -eq 0 ]
