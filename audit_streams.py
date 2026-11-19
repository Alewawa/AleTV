#!/usr/bin/env python3
"""Auditoria de enlaces HLS publicados en playlist.m3u.

No cambia la playlist. Comprueba desde GitHub Actions, no desde el Fire TV:
1. Respuesta HTTP y manifiesto HLS (si es .m3u8).
2. Una variante si el manifiesto es master.
3. Un segmento pequeno por Range cuando sea posible.

Genera audit/iptv_audit.csv, audit/iptv_audit.json y audit/iptv_audit.md.
Solo usa la libreria estandar de Python.
"""
from __future__ import annotations

import argparse
import concurrent.futures
import csv
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

UA = 'Mozilla/5.0 (Linux; Android 11; Fire TV) AppleWebKit/537.36 AleTV-Audit/1.0'
EXPECTED_PERU_IDS = {
    'americatelevision.pe': 'América TV',
    'latina.pe': 'Latina',
    'atv.pe': 'ATV',
    'panamericanatv.pe': 'Panamericana TV',
    'tvperu.pe': 'TV Perú',
    'tvperunoticias.pe': 'TV Perú Noticias',
    'canalipe.pe': 'Canal IPe',
    'canaln.pe': 'Canal N',
    'atvplus.pe': 'ATV+',
    'globaltv.pe': 'Global TV',
    'latele.pe': 'La Tele',
    'l1.pe': 'L1',
    'l1max.pe': 'L1 Max',
    'movistardeportes.pe': 'Movistar Deportes',
}

@dataclass
class Channel:
    name: str
    group: str
    tvg_id: str
    url: str
    ua: str = ''
    referer: str = ''
    origin: str = ''


def attrs(s: str) -> dict[str, str]:
    return dict(re.findall(r'([\w-]+)="([^"]*)"', s))


def parse_playlist(text: str) -> list[Channel]:
    channels: list[Channel] = []
    extinf = None
    extra: dict[str, str] = {}
    for raw in text.replace('\r', '').split('\n'):
        line = raw.strip()
        if not line:
            continue
        if line.startswith('#EXTINF:'):
            extinf, extra = line, {}
        elif extinf and line.lower().startswith('#extvlcopt:'):
            val = line[len('#EXTVLCOPT:'):]
            key, _, value = val.partition('=')
            extra[key.lower()] = value
        elif extinf and line.lower().startswith('#exthttp:'):
            try:
                data = json.loads(line[len('#EXTHTTP:'):])
                if isinstance(data, dict):
                    for k,v in data.items():
                        extra[k.lower()] = str(v)
            except ValueError:
                pass
        elif line.startswith('#'):
            continue
        elif extinf:
            meta = attrs(extinf)
            name = extinf.split(',',1)[-1].strip()
            channels.append(Channel(name, meta.get('group-title', 'Otros'), meta.get('tvg-id',''),
                                    line, extra.get('http-user-agent',extra.get('user-agent','')),
                                    extra.get('http-referrer',extra.get('referer','')),
                                    extra.get('http-origin',extra.get('origin',''))))
            extinf = None
            extra = {}
    return channels


def headers_for(c: Channel, ranged: bool = False) -> dict[str, str]:
    out = {'User-Agent': c.ua or UA, 'Accept': '*/*', 'Connection': 'close'}
    if c.referer:
        out['Referer'] = c.referer
    if c.origin:
        out['Origin'] = c.origin
    if ranged:
        out['Range'] = 'bytes=0-1023'
    return out


def fetch(url: str, c: Channel, timeout: float, size: int = 65536, ranged: bool = False):
    req = urllib.request.Request(url, headers=headers_for(c, ranged))
    with urllib.request.urlopen(req, timeout=timeout) as r:
        status = getattr(r,'status',200)
        ct = r.headers.get('Content-Type','')
        return status, r.geturl(), ct, r.read(size)


def first_next_url(body: str, base: str, master: bool) -> Optional[str]:
    lines = [x.strip() for x in body.splitlines() if x.strip()]
    if master:
        for i, line in enumerate(lines):
            if line.startswith('#EXT-X-STREAM-INF:'):
                for follower in lines[i+1:]:
                    if not follower.startswith('#'):
                        return urllib.parse.urljoin(base, follower)
        for line in lines:
            if line.startswith('#EXT-X-MEDIA:') and 'URI=' in line:
                m = re.search(r'URI="([^"]+)"', line)
                if m:
                    return urllib.parse.urljoin(base, m.group(1))
        return None
    for line in lines:
        if not line.startswith('#'):
            return urllib.parse.urljoin(base, line)
    return None


def check(c: Channel, timeout: float) -> dict:
    start = time.monotonic()
    result = {'name':c.name, 'group':c.group, 'tvg_id':c.tvg_id, 'url':c.url,
              'status':'', 'manifest_http':'', 'segment_http':'', 'detail':'', 'elapsed_s':''}
    try:
        code, final, ct, data = fetch(c.url, c, timeout)
        result['manifest_http'] = code
        text = data.decode('utf-8-sig','replace')
        if '#EXTM3U' not in text[:4096].upper():
            result['status'] = 'NO_HLS_MANIFEST'
            result['detail'] = f'HTTP {code}; Content-Type={ct}; no contiene #EXTM3U'
            return result
        if '#EXT-X-' not in text.upper():
            result['status'] = 'M3U_NO_HLS'
            result['detail'] = 'Texto M3U, pero no se reconoce como manifiesto HLS'
            return result
        manifest_url = final
        if '#EXT-X-STREAM-INF:' in text or '#EXT-X-MEDIA:' in text:
            child = first_next_url(text, final, True)
            if not child:
                result['status'] = 'MASTER_NO_VARIANT'
                result['detail'] = 'Master HLS sin variante reconocible'
                return result
            code2, manifest_url, ct2, data2 = fetch(child, c, timeout)
            text = data2.decode('utf-8-sig','replace')
            if '#EXTM3U' not in text[:4096].upper():
                result['status'] = 'VARIANT_INVALID'
                result['detail'] = f'La variante responde HTTP {code2}, sin manifiesto HLS'
                return result
        segment = first_next_url(text, manifest_url, False)
        if not segment:
            result['status'] = 'MANIFEST_ONLY'
            result['detail'] = 'Manifiesto valido; no se encontro segmento (puede ser evento sin emisión)'
            return result
        try:
            sc, _, sct, b = fetch(segment, c, timeout, size=1024, ranged=True)
            result['segment_http'] = sc
            if b:
                result['status'] = 'SEGMENT_OK_FROM_GITHUB'
                result['detail'] = f'HTTP {sc}, se recibieron {len(b)} bytes; NO valida decodificacion ni reproduccion en Fire TV'
            else:
                result['status'] = 'SEGMENT_EMPTY'
                result['detail'] = f'HTTP {sc}, segmento vacio'
        except Exception as exc:
            result['status'] = 'MANIFEST_OK_SEGMENT_FAIL'
            result['detail'] = f'Segmento no accesible desde GitHub: {type(exc).__name__}: {str(exc)[:160]}'
    except urllib.error.HTTPError as exc:
        result['manifest_http'] = exc.code
        result['status'] = 'HTTP_ERROR'
        result['detail'] = f'HTTP {exc.code}'
    except Exception as exc:
        result['status'] = 'NETWORK_ERROR'
        result['detail'] = f'{type(exc).__name__}: {str(exc)[:180]}'
    finally:
        result['elapsed_s'] = round(time.monotonic() - start,2)
    return result


def write_report(results: list[dict], channels: list[Channel], folder: Path, time_taken: str):
    folder.mkdir(parents=True, exist_ok=True)
    fields = ['name','group','tvg_id','url','status','manifest_http','segment_http','detail','elapsed_s']
    with (folder/'iptv_audit.csv').open('w', encoding='utf-8-sig', newline='') as f:
        w = csv.DictWriter(f, fieldnames=fields)
        w.writeheader()
        w.writerows(results)
    (folder/'iptv_audit.json').write_text(json.dumps({'checked_at':time_taken, 'results':results},ensure_ascii=False,indent=2),encoding='utf-8')
    counts = Counter(r['status'] for r in results)
    ids = {c.tvg_id.split('@')[0].lower() for c in channels}
    missing = [(v,k) for k,v in EXPECTED_PERU_IDS.items() if k not in ids]
    md = ['# Auditoría IPTV de AleTV', '', f'**Fecha UTC:** {time_taken}',
          f'**Canales examinados:** {len(results)}',
          '', 'Las pruebas se ejecutan **desde GitHub Actions**, no desde tu Fire TV.',
          'Un segmento accesible desde GitHub no garantiza imagen/sonido en un dispositivo.',
          '', '## Resumen', '']
    for k, n in sorted(counts.items()):
        md.append(f'- {k}: {n}')
    md.extend(['', '## Canales peruanos importantes ausentes de la playlist', ''])
    md.extend(f'- {name} (`{key}`)' for name,key in missing)
    if not missing:
        md.append('Ninguno de los identificadores esperados falta.')
    md.extend(['', '## Resultados por canal', '', '| Canal | Grupo | Resultado | HTTP | Detalle |', '|---|---|---|---|---|'])
    for r in results:
        short = r['detail'].replace('|','/').replace('\n',' ')[:110]
        md.append(f"| {r['name'].replace('|','/')} | {r['group'].replace('|','/')} | {r['status']} | {r['manifest_http']} / {r['segment_http']} | {short} |")
    md.extend(['', '## Limitaciones', '',
               '- No comprueba si la emisión es el canal correcto.',
               '- No verifica audio, códecs ni DRM.',
               '- Puede haber restricciones geográficas, IP/ISP o tokens temporales.',
               '- No se modifica `playlist.m3u`.'])
    (folder/'iptv_audit.md').write_text('\n'.join(md)+'\n',encoding='utf-8')


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--playlist', default='playlist.m3u')
    p.add_argument('--output', default='audit')
    p.add_argument('--timeout',type=float,default=8.0)
    p.add_argument('--workers',type=int,default=8)
    p.add_argument('--all', action='store_true', help='Incluye deportes internacionales; por defecto solo Peru y Arequipa')
    args = p.parse_args()
    playlist = Path(args.playlist).read_text(encoding='utf-8-sig')
    all_channels = parse_playlist(playlist)
    if not all_channels:
        sys.exit('No se detectaron canales en playlist.m3u')
    channels = all_channels if args.all else [c for c in all_channels if c.group in ('TV Peru','Deportes Peru','TV Arequipa')]
    print(f'Auditoria de {len(channels)} canales (total playlist: {len(all_channels)})',flush=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=max(1,min(args.workers,12))) as pool:
        results = list(pool.map(lambda c: check(c,args.timeout),channels))
    checked = datetime.now(timezone.utc).strftime('%Y-%m-%d %H:%M:%S UTC')
    write_report(results, all_channels, Path(args.output), checked)
    for r in results:
        print(f"{r['status']:<30} {r['name'][:33]:<33} {r['detail'][:90]}",flush=True)
    print('Archivos creados: audit/iptv_audit.md, .csv, .json')


if __name__ == '__main__':
    main()
