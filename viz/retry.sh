#!/usr/bin/env bash
# Delad retry-hjälpare för hämtnings- och byggstegen. Sourcas, körs inte:
#
#   . "$(dirname "$0")/retry.sh"
#   retry ./mill Elmix.scala transform
#
# DuckDB-native flaky-kraschar slumpvis i CI: icke-deterministiskt, utan
# stacktrace, drabbar fetch/transform/pca oberoende av varandra och oftast vid
# teardown efter att arbetet redan är klart. Alla tre stegen är idempotenta -
# fetch är inkrementell, transform och pca skriver om sina marts - så en
# omkörning är säker.
#
# Låg här i en egen fil för att båda skripten hade var sin ordagranna kopia och
# kommentarerna ovanför dem redan hunnit glida isär. Två kopior av samma
# flaky-policy driver garanterat vidare första gången någon justerar antalet
# försök eller pausen.

RETRY_FORSOK="${RETRY_FORSOK:-4}"
RETRY_PAUS="${RETRY_PAUS:-5}"

retry() {
  local a
  for a in $(seq 1 "$RETRY_FORSOK"); do
    "$@" && return 0
    # Ingen paus efter sista försöket - det finns inget mer att vänta på.
    if [ "$a" -lt "$RETRY_FORSOK" ]; then
      echo "  retry $a/$RETRY_FORSOK (exit ≠0, flaky DuckDB-native): $*" >&2
      sleep "$RETRY_PAUS"
    else
      echo "  gav upp efter $RETRY_FORSOK försök: $*" >&2
    fi
  done
  return 1
}
