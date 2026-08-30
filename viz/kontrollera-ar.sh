#!/usr/bin/env bash
# Validerar ett årsargument innan det når hämtnings- eller byggsteget.
#
#   ./viz/kontrollera-ar.sh          # tomt = innevarande år, OK
#   ./viz/kontrollera-ar.sh 2026     # OK
#   ./viz/kontrollera-ar.sh "2026; rm -rf /"   # avvisas
#
# Finns för att `ar` är en fritextinput i workflow_dispatch. Den går numera via
# miljön och citeras i skalet, men ett skräpvärde skulle ändå nå skripten och
# tolkas där: "2026 2025" blir två argument, ett ledande bindestreck blir en
# flagga, och ett tomt-men-inte-riktigt värde ger en obegriplig hämtning. Bättre
# att avvisa högljutt i workflowen än att låta mill tolka det.
set -euo pipefail

AR="${1:-}"

# Tomt är giltigt och betyder "innevarande år" - skripten defaultar själva.
[ -z "$AR" ] && exit 0

case "$AR" in
  [12][0-9][0-9][0-9]) ;;
  *)
    echo "FEL: ogiltigt år: '$AR' (förväntar fyra siffror, eller tomt för innevarande)" >&2
    exit 1
    ;;
esac

# Rimlighetsgräns: projektet har data från 2016 och kan inte hämta framtiden.
if [ "$AR" -lt 2016 ] || [ "$AR" -gt "$(( $(date -u +%Y) + 1 ))" ]; then
  echo "FEL: året $AR ligger utanför intervallet 2016..$(( $(date -u +%Y) + 1 ))" >&2
  exit 1
fi
