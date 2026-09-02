#!/usr/bin/env python3
"""Optimal ladd- och urladdningsprofil med perfekt förutsägelse.

Sidans grundmodell tar dygnets H dyraste mot dygnets H billigaste timmar och
antar en cykel per dygn. Det är optimalt givet EN cykel inom kalenderdygnet -
men det är inte optimum. Ett dygn med två pristoppar bär mer än en cykel, ett
platt dygn bär ingen alls, och de billiga timmarna ligger ofta kring midnatt så
affären spänner över dygnsgränsen. Modulen räknar vad en aktör med facit i hand
hade fått, som en övre gräns mot heuristikens undre.

Dynamisk programmering över diskretiserad laddningsnivå, ett steg per prispunkt,
hela året i följd utan dygnsgränser.

Det inre maximumet är ett glidande fönster, inte en loop över alla åtgärder.
Skrivet naivt blir det O(nivåer x åtgärder) per tidssteg, vilket för korta
varaktigheter - där effekten räcker för att fylla batteriet på en timme - blir
tusentals operationer per steg och timmar för hela nätet av zoner, år och
varaktigheter. Omskrivningen nedan använder att vinsten är LINJÄR i antalet
steg, så maximum över källtillstånd är ett fönstermaximum som en monoton kö
löser i linjär tid.

Konvention som på resten av sidan OCH i referensfiguren: verkningsgraden ligger
på urladdningssidan - att höja nivån med x kostar pris * x, att urladda x ger
pris * eta * x. En cykel över dod*H ger då (p_hog*eta - p_lag)*dod*H, exakt
sidans uttryck. Lade man i stället eta på laddsidan blev varje tal 1/eta större,
alltså 14 % för hög, och den optimala linjen hade legat systematiskt över
heuristiken av ren konventionsskillnad i stället för av bättre drift.
"""
from collections import deque

NIVAER = 40


def _fonstermax(varden, m, framat):
    """Fönstermaximum med monoton kö.

    framat=True  -> for varje i, max over j i [i-m, i]
    framat=False -> for varje i, max over j i [i, i+m]
    Returnerar listan av (varde, argmax).
    """
    n = len(varden)
    ut = [None] * n
    kö = deque()
    intervall = range(n) if framat else range(n - 1, -1, -1)
    for i in intervall:
        if varden[i] is not None:
            while kö and varden[kö[-1]] <= varden[i]:
                kö.pop()
            kö.append(i)
        while kö and (i - kö[0] > m if framat else kö[0] - i > m):
            kö.popleft()
        ut[i] = (varden[kö[0]], kö[0]) if kö else (None, None)
    return ut


def optimal(poster, effekt_mw, kapacitet_mwh, eta=0.88, dod=0.90):
    """Maximal intäkt över serien.

    poster: [(unix_sekunder, pris_eur_per_mwh), ...] i tidsordning.
    Returnerar (intäkt_eur, levererad_mwh).
    """
    if len(poster) < 2:
        return 0.0, 0.0
    anvandbar = kapacitet_mwh * dod
    if anvandbar <= 0:
        return 0.0, 0.0
    steg = anvandbar / NIVAER
    tider = [t for t, _ in poster]
    langder = [b - a for a, b in zip(tider, tider[1:])]
    langder.append(langder[-1])

    NEG = None
    vinst = [NEG] * (NIVAER + 1)
    vinst[0] = 0.0
    lev = [0.0] * (NIVAER + 1)

    for i, (_, p) in enumerate(poster):
        dt = langder[i] / 3600.0
        m = max(1, min(NIVAER, int(round(effekt_mw * dt / steg))))
        # Ladda: varde(s2) = max_{s i [s2-m, s2]} (vinst[s] + p*s*steg) - p*s2*steg
        gl = [None if v is None else v + p * s * steg for s, v in enumerate(vinst)]
        # Urladda: varde(s2) = max_{s i [s2, s2+m]} (vinst[s] + p*eta*s*steg) - p*eta*s2*steg
        gu = [None if v is None else v + p * eta * s * steg for s, v in enumerate(vinst)]
        fl = _fonstermax(gl, m, True)
        fu = _fonstermax(gu, m, False)

        ny = [NEG] * (NIVAER + 1)
        nylev = [0.0] * (NIVAER + 1)
        for s2 in range(NIVAER + 1):
            bast, kalla = None, None
            v, k = fl[s2]
            if v is not None:
                kand = v - p * s2 * steg
                if bast is None or kand > bast:
                    bast, kalla = kand, k
            v, k = fu[s2]
            if v is not None:
                kand = v - p * eta * s2 * steg
                if bast is None or kand > bast:
                    bast, kalla = kand, k
            if bast is not None:
                ny[s2] = bast
                # Cellgenomsattning, inte levererad energi efter forluster: det ar
                # den cellerna aldras av, och det ar cykelantalet sidan visar.
                nylev[s2] = lev[kalla] + (max(0, kalla - s2) * steg)
        vinst, lev = ny, nylev

    bast = max((s for s in range(NIVAER + 1) if vinst[s] is not None),
               key=lambda s: vinst[s], default=None)
    if bast is None:
        return 0.0, 0.0
    return vinst[bast], lev[bast]
