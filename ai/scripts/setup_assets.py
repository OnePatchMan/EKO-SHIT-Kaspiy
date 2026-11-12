"""Fetch only small official model assets and selected CC0 demo previews.

No account, API key, or entire FSD50K download is needed. Files are cached.
"""
from pathlib import Path
from urllib.request import Request, urlopen
import hashlib
import io
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
REVISION = '19f287775ae950e22a9b5fb82727e72cff2e3664'
MODEL_BASE = f'https://raw.githubusercontent.com/tensorflow/models/{REVISION}'
SAMPLES = [
    ('boat_thailand', 'jonny4c', 521034, 'boat', 'field recording on motorboat; iPhone 7; Thailand'),
    ('boat_river_germany', 'Garuda1982', 529873, 'boat', 'motorboat on river field recording; Sony PCM D100 and Sennheiser MK66/K6; Germany; added as an independent control after first four results, thresholds unchanged'),
    ('river_sweden', 'kentspublicdomain', 325182, 'background', 'field recording of fast-flowing creek; Roland R-26; Sweden'),
    ('car_engine', 'XiiiSamples', 384107, 'engine_not_boat', 'car engine field recording; Zoom H1; Finland'),
    ('gunshot_effect', 'SignatureSoundsOrg', 876004, 'gunshot', 'authored gunshot SFX; not verified field firearm recording'),
]


def fetch(url, path, limit=25_000_000):
    path = Path(path)
    if path.exists():
        return path.read_bytes()
    request = Request(url, headers={'User-Agent': 'EcoShieldHackathon/0.1 (research demonstration)'})
    with urlopen(request, timeout=45) as response:
        data = response.read(limit + 1)
    if len(data) > limit:
        raise ValueError(f'download exceeds {limit} bytes: {url}')
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return data


def main():
    manifest = {'source_revision': REVISION, 'files': [], 'samples': []}
    targets = [
        (f'{MODEL_BASE}/research/audioset/yamnet/{name}', ROOT / 'vendor' / 'yamnet' / name)
        for name in ('yamnet.py', 'params.py', 'features.py', 'yamnet_class_map.csv', 'README.md')
    ]
    targets += [
        (f'{MODEL_BASE}/LICENSE', ROOT / 'vendor' / 'yamnet' / 'LICENSE'),
        ('https://storage.googleapis.com/audioset/yamnet.h5', ROOT / 'models' / 'yamnet.h5'),
    ]
    for url, path in targets:
        data = fetch(url, path)
        manifest['files'].append({'path': path.relative_to(ROOT).as_posix(), 'url': url, 'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)})
        print('asset', path.name, len(data), flush=True)
    (ROOT / 'data').mkdir(exist_ok=True)
    (ROOT / 'data' / 'model_manifest.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    if '--model-only' in sys.argv:
        return
    for name, author, sound_id, label, description in SAMPLES:
        page_url = f'https://freesound.org/people/{author}/sounds/{sound_id}/'
        page = fetch(page_url, ROOT / 'data' / 'source_pages' / f'{sound_id}.html', limit=2_000_000).decode('utf-8')
        if 'creativecommons.org/publicdomain/zero/1.0' not in page:
            raise ValueError(f'CC0 license could not be verified for {page_url}')
        previews = re.findall(r'https://cdn\.freesound\.org/previews/[^\s"<>]+-hq\.mp3', page)
        if not previews:
            raise ValueError(f'public preview not found for {page_url}')
        preview_url = previews[0].replace('&amp;', '&')
        compressed = fetch(preview_url, ROOT / 'data' / 'previews' / f'{sound_id}.mp3', limit=5_000_000)
        if '--download-only' in sys.argv:
            print('preview downloaded', name, len(compressed), flush=True)
            continue
        import soundfile as sf
        values, rate = sf.read(io.BytesIO(compressed), dtype='float32', always_2d=True)
        values = values[: min(len(values), 15 * rate)]
        if len(values) < rate:
            import numpy as np
            values = np.pad(values, ((0, rate-len(values)), (0, 0)))
        destination = ROOT / 'data' / 'audio' / f'{name}.wav'
        destination.parent.mkdir(parents=True, exist_ok=True)
        sf.write(destination, values, rate, subtype='PCM_16')
        item = {'file': destination.relative_to(ROOT).as_posix(), 'source_url': page_url, 'preview_url': preview_url,
                'author': author, 'license': 'CC0-1.0', 'original_id': str(sound_id), 'label': label,
                'sensor_type': 'air_microphone_or_sfx', 'group_id': str(sound_id), 'split': 'demo_control',
                'is_demo': True, 'is_caspian_field_data': False, 'description': description,
                'processing': 'public compressed preview decoded to PCM16 WAV; first up to 15 s',
                'sample_rate': rate, 'duration_seconds': round(len(values)/rate, 4),
                'sha256': hashlib.sha256(destination.read_bytes()).hexdigest()}
        manifest['samples'].append(item)
        print('sample', name, item['duration_seconds'], flush=True)
    if '--download-only' in sys.argv:
        return
    (ROOT / 'data' / 'manifest.json').write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding='utf-8')
    print('Saved data/manifest.json', flush=True)


if __name__ == '__main__':
    main()
