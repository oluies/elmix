# Kickoff-prompt för Claude Code

Klistra in nedanstående i `claude` i projektroten.

---

Det här är ett Mill single-file Scala-script (Elmix.scala med
`//| mvnDeps`-huvud, kräver Mill 1.1+, se CLAUDE.md) som är skrivet men
aldrig kompilerat - det togs fram i en miljö utan åtkomst till Maven
Central. Genomför en provkörning i fyra steg och stanna och rapportera
efter varje:

Steg 1 - Kompilering. Kontrollera `mill --version` (behöver 1.1+ för
script-stöd; uppgradera annars). Kör `./mill Elmix.scala:compile`. Fixa
kompileringsfel minimalt - ändra inte arkitektur, schemakontrakt eller
SQL-filerna. Bumpa duckdb_jdbc i YAML-huvudet till senaste stabila om
versionen saknas i Central. Verifiera att mainargs-dispatchen fungerar:
`./mill Elmix.scala badcommand` ska ge felutskrift, inte krasch i
argumentparsningen - justera @main-signaturen vid behov.

Steg 2 - Röktest utan nätverk. Kör röktestet enligt CLAUDE.md
(seed_testdata.sql + `./mill Elmix.scala transform`). Om duckdb CLI
saknas, installera den. Verifiera förväntat utfall: sex marts i
data/marts/, capture_rate < 1 för Vind, mart_changepoints icke-tom.

Steg 3 - Liten skarp hämtning. Endast om miljövariabeln ENTSOE_API_KEY är
satt: kör `./mill Elmix.scala fetch --start 2024 --end 2024 --data prices`
och inspektera en zonfil med duckdb: ~8784 rader per zon för 2024, priser
i rimligt spann, ts i TIMESTAMPTZ. Om svaret är tomt för alla zoner:
misstänk EIC-koderna i Zones, verifiera mot ENTSO-E:s dokumentation och
rätta. Gör INTE hela 2016-2026-hämtningen.

Steg 4 - Rapport. Sammanfatta: vad som ändrades och varför, vad som är
verifierat, vad som återstår (A85-parsningen och Neighbours-kartan testas
bara vid imbalance-/flows-hämtning). Lämna skriptet i kompilerande skick.

Begränsningar: rör inte transform.sql (verifierad separat), behåll
2-sekunderspausen mellan API-anrop, inga nya beroenden utöver vad som
krävs för att kompilera - requests/mainargs/os-lib är redan bundlade i
Mill-scripts.
