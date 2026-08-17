#!/usr/bin/env python3
"""
IPTV curado: canales nacionales del Peru + Arequipa + deportes.

Fuentes oficiales de iptv-org:
  Peru:
    https://iptv-org.github.io/iptv/countries/pe.m3u
  Arequipa:
    https://iptv-org.github.io/iptv/subdivisions/pe-are.m3u
  Deportes:
    https://iptv-org.github.io/iptv/categories/sports.m3u

Criterios:
- Mantiene los canales nacionales peruanos seleccionados.
- Elimina los canales regionales del resto del Peru.
- Mantiene automaticamente los canales clasificados como Arequipa.
- Mantiene deportes de Peru, Latinoamerica y Espana.
- Elimina duplicados por tvg-id; si falta, usa nombre normalizado.
- Conserva la mejor señal disponible.
- Excluye [Geo-blocked], [Not 24/7] y [Offline].
- Prioriza los canales peruanos mas comunes.
- En Deportes Latinoamerica prioriza marcas conocidas como TyC Sports,
  ESPN, TNT Sports, FOX Sports, DSports, Claro Sports, Win Sports,
  GolTV, Tigo Sports, SporTV y Premiere cuando esten disponibles.

Orden inicial aproximado:
  America TV
  Latina
  ATV
  Panamericana TV
  TV Peru
  L1
  L1 Max
  Movistar Deportes
  Canal N
  ATV+
  Global TV
  La Tele
  Gol Peru
  Ovacion TV
  ...resto de nacionales...
  ...Arequipa...
  ...deportes Latinoamerica...
  ...deportes Espana...

Uso:
    python generate_playlist.py

Salida:
    playlist.m3u
"""

from __future__ import annotations

import ipaddress
import re
import unicodedata
import urllib.parse
import urllib.request
from dataclasses import dataclass, field

PERU_URL = "https://iptv-org.github.io/iptv/countries/pe.m3u"
AREQUIPA_URL = "https://iptv-org.github.io/iptv/subdivisions/pe-are.m3u"
SPORTS_URL = "https://iptv-org.github.io/iptv/categories/sports.m3u"
OUTPUT = "playlist.m3u"

# ---------------------------------------------------------------------------
# CANALES NACIONALES DEL PERU
# ---------------------------------------------------------------------------
# No incluimos regionales de otras zonas. Arequipa se obtiene aparte desde
# AREQUIPA_URL, por lo que no hay que mantener sus IDs manualmente.
NATIONAL_TV_IDS = {
    "americatelevision.pe",
    "latina.pe",
    "atv.pe",
    "panamericanatv.pe",
    "tvperu.pe",
    "tvperunoticias.pe",
    "canalipe.pe",
    "canaln.pe",
    "atvplus.pe",
    "globaltv.pe",
    "latele.pe",
    "latinanoticias247.pe",
    "latinaclasicos.pe",
    "latinasclasicos.pe",
}

PERU_SPORTS_IDS = {
    "l1.pe",
    "l1max.pe",
    "movistardeportes.pe",
    "golperu.pe",
    "ovaciontv.pe",
}

# Prioridad manual para que los canales mas comunes aparezcan primero.
# Los IDs no incluidos aqui se ordenan despues alfabeticamente.
COMMON_PRIORITY = [
    "americatelevision.pe",
    "latina.pe",
    "atv.pe",
    "panamericanatv.pe",
    "tvperu.pe",
    "l1.pe",
    "l1max.pe",
    "movistardeportes.pe",
    "canaln.pe",
    "atvplus.pe",
    "globaltv.pe",
    "latele.pe",
    "golperu.pe",
    "ovaciontv.pe",
    "tvperunoticias.pe",
    "canalipe.pe",
    "latinanoticias247.pe",
    "latinaclasicos.pe",
    "latinasclasicos.pe",
]

PRIORITY_RANK = {
    channel_id: position
    for position, channel_id in enumerate(COMMON_PRIORITY)
}

# Prioridad para Deportes Latinoamerica.
# Se evalua primero por tvg-id y luego por nombre visible normalizado.
# No fuerza la inclusion de ningun canal: solo cambia el orden SI el canal
# existe en la playlist Sports descargada en ese momento.
LATAM_SPORTS_PRIORITY_IDS = [
    "tycsports.ar",
    "tycsportslatinamerica.ar",
    "espndeportes.us",
    "clarosports.mx",
    "clarosportschile.cl",
    "winsports.co",
]

LATAM_SPORTS_PRIORITY_TERMS = [
    # Marcas deportivas mas conocidas / utiles para futbol.
    "tycsports",
    "espndeportes",
    "espn",
    "tntsports",
    "foxsports",
    "dsports",
    "directvsports",
    "clarosports",
    "winsports",
    "goltv",
    "tigosports",
    "tntsportsbrasil",
    "sportv",
    "premiere",
    "bandsports",

    # Despues, otras senales deportivas reconocibles.
    "canaldelfutbol",
    "cdf",
    "telefe deportes",
    "deportv",
    "argentinagobdeportes",
]

LATAM_SPORTS_ID_RANK = {
    channel_id: position
    for position, channel_id in enumerate(LATAM_SPORTS_PRIORITY_IDS)
}

LATAM_COUNTRIES = {
    "ar",  # Argentina
    "bo",  # Bolivia
    "br",  # Brasil
    "cl",  # Chile
    "co",  # Colombia
    "cr",  # Costa Rica
    "cu",  # Cuba
    "do",  # Republica Dominicana
    "ec",  # Ecuador
    "gt",  # Guatemala
    "hn",  # Honduras
    "mx",  # Mexico
    "ni",  # Nicaragua
    "pa",  # Panama
    "py",  # Paraguay
    "pr",  # Puerto Rico
    "sv",  # El Salvador
    "uy",  # Uruguay
    "ve",  # Venezuela
}

BLOCKED_MARKERS = (
    "[geo-blocked]",
    "[not 24/7]",
    "[offline]",
)


@dataclass
class Entry:
    extinf: str
    extra_lines: list[str]
    url: str
    origins: set[str] = field(default_factory=set)

    @property
    def tvg_id(self) -> str:
        match = re.search(r'tvg-id="([^"]*)"', self.extinf, re.I)
        return match.group(1).strip() if match else ""

    @property
    def base_tvg_id(self) -> str:
        return self.tvg_id.split("@", 1)[0].lower()

    @property
    def display_name(self) -> str:
        return self.extinf.split(",", 1)[1].strip() if "," in self.extinf else ""

    @property
    def country(self) -> str:
        base = self.tvg_id.split("@", 1)[0]
        match = re.search(r"\.([a-z]{2})$", base, re.I)
        return match.group(1).lower() if match else ""


def download_text(url: str) -> str:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 IPTV-Personalizer/3.0",
            "Accept": "*/*",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as response:
        return response.read().decode("utf-8-sig", errors="replace")


def parse_m3u(text: str, origin: str) -> list[Entry]:
    lines = [line.strip() for line in text.splitlines()]
    entries: list[Entry] = []
    i = 0

    while i < len(lines):
        if not lines[i].startswith("#EXTINF:"):
            i += 1
            continue

        extinf = lines[i]
        i += 1
        extra: list[str] = []

        while (
            i < len(lines)
            and lines[i].startswith("#")
            and not lines[i].startswith("#EXTINF:")
        ):
            extra.append(lines[i])
            i += 1

        if i < len(lines) and lines[i] and not lines[i].startswith("#"):
            entries.append(
                Entry(
                    extinf=extinf,
                    extra_lines=extra,
                    url=lines[i],
                    origins={origin},
                )
            )
            i += 1

    return entries


def normalize_text(value: str) -> str:
    value = unicodedata.normalize("NFKD", value)
    value = "".join(c for c in value if not unicodedata.combining(c))
    value = value.lower()

    value = re.sub(r"\[[^\]]+\]", " ", value)
    value = re.sub(
        r"\((?:2160p|1440p|1080p|1080i|720p|576p|576i|540p|480p|480i|360p|240p|"
        r"4k|uhd|fhd|hd|sd)\)",
        " ",
        value,
        flags=re.I,
    )
    value = re.sub(r"\b(?:4k|uhd|fhd|hd|sd)\b", " ", value)
    value = re.sub(r"[^a-z0-9]+", "", value)
    return value


def canonical_key(entry: Entry) -> str:
    if entry.base_tvg_id:
        return "id:" + entry.base_tvg_id

    name = normalize_text(entry.display_name)
    if name:
        return "name:" + entry.country + ":" + name

    return "url:" + entry.url.strip().lower()


def is_blocked_or_unstable(entry: Entry) -> bool:
    text = (entry.extinf + " " + " ".join(entry.extra_lines)).lower()
    return any(marker in text for marker in BLOCKED_MARKERS)


def resolution_score(text: str) -> int:
    text = text.lower()

    scores = {
        "2160p": 600,
        "4k": 600,
        "uhd": 590,
        "1440p": 520,
        "1080p": 500,
        "1080i": 460,
        "fhd": 450,
        "720p": 400,
        "hd": 380,
        "576p": 320,
        "576i": 300,
        "540p": 280,
        "480p": 250,
        "480i": 230,
        "sd": 200,
        "360p": 150,
        "240p": 100,
    }

    best = 0
    for token, score in scores.items():
        if token in text:
            best = max(best, score)
    return best


def is_ip_host(url: str) -> bool:
    try:
        host = urllib.parse.urlsplit(url).hostname
        if not host:
            return False
        ipaddress.ip_address(host)
        return True
    except ValueError:
        return False


def quality_score(entry: Entry) -> int:
    text = " ".join([entry.extinf, *entry.extra_lines, entry.url]).lower()
    score = resolution_score(text)

    if re.search(r"@hd(?:$|\")", entry.tvg_id, re.I):
        score += 25

    if entry.url.lower().startswith("https://"):
        score += 20

    if not is_ip_host(entry.url):
        score += 5

    return score


def clean_visible_name(name: str) -> str:
    name = re.sub(
        r"\s*\((?:2160p|1440p|1080p|1080i|720p|576p|576i|540p|480p|480i|360p|240p|"
        r"4k|uhd|fhd|hd|sd)\)",
        "",
        name,
        flags=re.I,
    )
    name = re.sub(
        r"\s+\[(?:Geo-blocked|Not 24/7|Offline)\]",
        "",
        name,
        flags=re.I,
    )
    return re.sub(r"\s{2,}", " ", name).strip()


def replace_group_title(extinf: str, group: str) -> str:
    if re.search(r'group-title="[^"]*"', extinf, re.I):
        return re.sub(
            r'group-title="[^"]*"',
            f'group-title="{group}"',
            extinf,
            count=1,
            flags=re.I,
        )

    comma = extinf.find(",")
    if comma == -1:
        return extinf + f' group-title="{group}"'

    return extinf[:comma] + f' group-title="{group}"' + extinf[comma:]


def clean_extinf(extinf: str, group: str) -> str:
    extinf = replace_group_title(extinf, group)

    if "," in extinf:
        metadata, name = extinf.split(",", 1)
        return metadata + "," + clean_visible_name(name)

    return extinf


def sports_region(entry: Entry) -> str | None:
    if entry.base_tvg_id in PERU_SPORTS_IDS or entry.country == "pe":
        return "Deportes Peru"

    if entry.country == "es":
        return "Deportes Espana"

    if entry.country in LATAM_COUNTRIES:
        return "Deportes Latinoamerica"

    return None


def choose_best(entries: list[Entry]) -> list[Entry]:
    best: dict[str, Entry] = {}

    for entry in entries:
        key = canonical_key(entry)

        if key not in best:
            best[key] = entry
            continue

        current = best[key]
        origins = current.origins | entry.origins

        if quality_score(entry) > quality_score(current):
            entry.origins = origins
            best[key] = entry
        else:
            current.origins = origins

    return list(best.values())


def group_of(entry: Entry) -> str:
    # Los deportes peruanos tienen prioridad sobre la procedencia geografica.
    if entry.base_tvg_id in PERU_SPORTS_IDS:
        return "Deportes Peru"

    # Si viene de la playlist especifica de Arequipa, se mantiene como local.
    if "arequipa" in entry.origins:
        return "TV Arequipa"

    if entry.base_tvg_id in NATIONAL_TV_IDS:
        return "TV Peru"

    region = sports_region(entry)
    if region:
        return region

    return "TV Peru"


def peru_priority_key(entry: Entry) -> tuple[int, str]:
    rank = PRIORITY_RANK.get(entry.base_tvg_id, 10_000)
    return rank, normalize_text(clean_visible_name(entry.display_name))


def alphabetical_key(entry: Entry) -> str:
    return normalize_text(clean_visible_name(entry.display_name))


def latam_sports_priority_key(entry: Entry) -> tuple[int, int, str]:
    """
    Ordena Deportes Latinoamerica de forma mas util:

    1. IDs conocidos y confirmados.
    2. Marcas deportivas conocidas detectadas por nombre.
    3. Resto de canales en orden alfabetico.

    Si una marca no esta presente en iptv-org, simplemente no afecta nada.
    """
    name = normalize_text(clean_visible_name(entry.display_name))
    channel_id = entry.base_tvg_id

    if channel_id in LATAM_SPORTS_ID_RANK:
        return (0, LATAM_SPORTS_ID_RANK[channel_id], name)

    for position, term in enumerate(LATAM_SPORTS_PRIORITY_TERMS):
        normalized_term = normalize_text(term)
        if normalized_term and normalized_term in name:
            return (1, position, name)

    return (2, 10_000, name)


def main() -> None:
    print("Descargando canales del Peru...")
    peru_all = parse_m3u(download_text(PERU_URL), "peru")

    print("Descargando canales de Arequipa...")
    arequipa_all = parse_m3u(download_text(AREQUIPA_URL), "arequipa")

    print("Descargando categoria Deportes...")
    sports_all = parse_m3u(download_text(SPORTS_URL), "sports")

    peru_clean = [e for e in peru_all if not is_blocked_or_unstable(e)]
    arequipa_clean = [
        e for e in arequipa_all
        if not is_blocked_or_unstable(e)
    ]
    sports_clean = [e for e in sports_all if not is_blocked_or_unstable(e)]

    # Solo TV nacional de la lista Peru.
    national_tv = [
        e for e in peru_clean
        if e.base_tvg_id in NATIONAL_TV_IDS
    ]

    # Deportes peruanos tomados tambien desde PE por si no aparecen
    # temporalmente dentro de la categoria Sports.
    peru_sports_from_pe = [
        e for e in peru_clean
        if e.base_tvg_id in PERU_SPORTS_IDS
    ]

    sports_selected = [
        e for e in sports_clean
        if sports_region(e) is not None
    ]

    # Arequipa entra desde su propia playlist. Asi quedan fuera los canales
    # regionales de Junin, Lima, Moquegua, San Martin, etc.
    combined = choose_best(
        national_tv
        + peru_sports_from_pe
        + arequipa_clean
        + sports_selected
    )

    groups: dict[str, list[Entry]] = {
        "TV Peru": [],
        "Deportes Peru": [],
        "TV Arequipa": [],
        "Deportes Latinoamerica": [],
        "Deportes Espana": [],
    }

    for entry in combined:
        group = group_of(entry)
        if group in groups:
            groups[group].append(entry)

    # Nacionales y deportes peruanos: prioridad manual por popularidad.
    groups["TV Peru"].sort(key=peru_priority_key)
    groups["Deportes Peru"].sort(key=peru_priority_key)

    # Arequipa y deportes extranjeros: alfabetico.
    groups["TV Arequipa"].sort(key=alphabetical_key)

    # En Latinoamerica primero aparecen TyC Sports, ESPN, TNT Sports,
    # FOX Sports, DSports, Claro Sports, Win Sports, GolTV, Tigo Sports,
    # SporTV, Premiere, BandSports, etc. si estan disponibles.
    groups["Deportes Latinoamerica"].sort(key=latam_sports_priority_key)

    groups["Deportes Espana"].sort(key=alphabetical_key)

    # Para que al abrir la lista primero aparezcan los canales peruanos
    # mas habituales, mezclamos TV Peru + Deportes Peru por COMMON_PRIORITY.
    peru_featured = groups["TV Peru"] + groups["Deportes Peru"]
    peru_featured.sort(key=peru_priority_key)

    output_lines = ["#EXTM3U"]
    emitted: set[str] = set()

    def emit(entry: Entry) -> None:
        key = canonical_key(entry)
        if key in emitted:
            return

        emitted.add(key)
        group = group_of(entry)
        output_lines.append(clean_extinf(entry.extinf, group))
        output_lines.extend(entry.extra_lines)
        output_lines.append(entry.url)

    # 1) America, Latina, ATV, Panamericana, TV Peru, L1, L1 Max, etc.
    for entry in peru_featured:
        emit(entry)

    # 2) Solo regionales de Arequipa.
    for entry in groups["TV Arequipa"]:
        emit(entry)

    # 3) Deportes del resto de Latinoamerica.
    for entry in groups["Deportes Latinoamerica"]:
        emit(entry)

    # 4) Deportes de Espana.
    for entry in groups["Deportes Espana"]:
        emit(entry)

    with open(OUTPUT, "w", encoding="utf-8", newline="\n") as file:
        file.write("\n".join(output_lines) + "\n")

    print()
    print("Lista generada correctamente.")
    print(f"Archivo: {OUTPUT}")
    print()
    print(f"TV Peru:                  {len(groups['TV Peru'])}")
    print(f"Deportes Peru:            {len(groups['Deportes Peru'])}")
    print(f"TV Arequipa:              {len(groups['TV Arequipa'])}")
    print(f"Deportes Latinoamerica:   {len(groups['Deportes Latinoamerica'])}")
    print(f"Deportes Espana:          {len(groups['Deportes Espana'])}")
    print(f"TOTAL UNICO:              {len(emitted)}")
    print()
    print("Se excluyeron canales regionales fuera de Arequipa.")
    print("Tambien se excluyeron Geo-blocked, Not 24/7 y Offline.")


if __name__ == "__main__":
    main()
