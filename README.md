# IPTV Peru + Arequipa + Deportes

Playlist M3U personalizada generada automaticamente a partir de las listas publicas de [iptv-org/iptv](https://github.com/iptv-org/iptv).

## Que incluye

- Canales nacionales peruanos seleccionados.
- Canales regionales de Arequipa; se excluyen los regionales de otras zonas del Peru.
- Deportes de Peru, Latinoamerica y Espana.
- Deduplicacion por `tvg-id` y nombre normalizado.
- Prioridad para la mejor resolucion disponible.
- Preferencia por HTTPS en empates.
- Exclusion de entradas marcadas como `Geo-blocked`, `Not 24/7` u `Offline`.
- Orden prioritario para canales habituales como America TV, Latina, ATV, Panamericana, TV Peru, L1, L1 Max y Movistar Deportes.
- En deportes latinoamericanos se priorizan marcas conocidas cuando estan disponibles en la fuente.

## Enlace para usar en tu reproductor IPTV

Una vez que subas este repositorio a GitHub como repositorio **publico**, reemplaza `TU_USUARIO` y `TU_REPOSITORIO`:

```text
https://raw.githubusercontent.com/TU_USUARIO/TU_REPOSITORIO/main/playlist.m3u
```

Ese enlace puede pegarse directamente en reproductores compatibles con playlists M3U, por ejemplo VLC, TiviMate, OTT Navigator, Kodi u otros.

## Actualizacion automatica

El workflow `.github/workflows/update-playlist.yml` se ejecuta al subir o modificar el generador, y despues vuelve a ejecutarse todos los dias. Actualiza `playlist.m3u` solo cuando cambia el contenido.

Por eso, al subir por primera vez el repositorio, GitHub Actions deberia generar la playlist automaticamente. Tambien puedes ejecutarlo manualmente:

1. Abre la pestana **Actions** del repositorio.
2. Selecciona **Actualizar playlist IPTV**.
3. Pulsa **Run workflow**.

## Generar localmente

No requiere paquetes externos; usa solamente la biblioteca estandar de Python.

```bash
python generate_playlist.py
```

Esto actualiza:

```text
playlist.m3u
```

## Estructura

```text
.
├── .github/
│   └── workflows/
│       └── update-playlist.yml
├── .gitignore
├── generate_playlist.py
├── playlist.m3u
└── README.md
```

## Fuentes

El generador consulta estas playlists mantenidas por iptv-org:

- Peru: `https://iptv-org.github.io/iptv/countries/pe.m3u`
- Arequipa: `https://iptv-org.github.io/iptv/subdivisions/pe-are.m3u`
- Deportes: `https://iptv-org.github.io/iptv/categories/sports.m3u`

## Nota

La disponibilidad de cada stream depende de su proveedor original y puede cambiar. Este repositorio no aloja video; solamente genera una playlist a partir de URLs publicadas por la fuente indicada. Usa las emisiones de acuerdo con las condiciones aplicables en tu ubicacion.

## Si GitHub Actions no puede hacer push

El workflow solicita `contents: write`. Si una politica de tu cuenta u organizacion lo bloquea, revisa **Settings > Actions > General > Workflow permissions** y habilita permisos de lectura y escritura para workflows.
