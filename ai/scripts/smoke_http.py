"""Check running HTTP service; failed runs overwrite previous successful evidence."""
from pathlib import Path
from datetime import datetime, timezone
import argparse
import copy
import json
import httpx

ROOT = Path(__file__).resolve().parents[1]


def run_checks(client, root=ROOT):
    root = Path(root)
    report = {'started_at': datetime.now(timezone.utc).isoformat(), 'base_url': str(client.base_url),
              'scope': 'Python HTTP service; not team Java/frontend application',
              'success': False, 'completed': False, 'cases': []}
    destination = root/'reports/http_smoke_results.json'
    destination.parent.mkdir(parents=True, exist_ok=True)

    def save():
        destination.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding='utf-8')

    def record(name, response, expected):
        try:
            body = response.json()
        except ValueError:
            body = {'raw_text': response.text[:2000]}
        report['cases'].append({'name': name, 'http_status': response.status_code, 'expected_status': expected, 'body': body})
        save()
        print(name, response.status_code, flush=True)
        if response.status_code != expected:
            raise RuntimeError(f'{name}: HTTP {response.status_code}, expected {expected}')
        return body

    save()
    try:
        health = record('model_readiness', client.get('/health'), 200)
        if health.get('audio_model_ready') is not True:
            raise RuntimeError('real model is not ready')
        boat = record('audio_real_boat', client.post('/v1/audio/analyze',
            files={'file': ('boat.wav', (root/'data/audio/boat_river_germany.wav').read_bytes(), 'audio/wav')},
            data={'station_id': 'delta-demo-01', 'recorded_at': '2026-05-10T09:00:00Z'}), 200)
        if not any(event['type'] == 'boat_suspected' for event in boat['events']):
            raise RuntimeError('expected demonstration boat event was not returned')
        shot = record('audio_gunshot_effect', client.post('/v1/audio/analyze',
            files={'file': ('gunshot.wav', (root/'data/audio/gunshot_effect.wav').read_bytes(), 'audio/wav')},
            data={'station_id': 'delta-demo-01', 'recorded_at': '2026-05-10T09:00:00Z'}), 200)
        if not any(event['type'] == 'gunshot_suspected' for event in shot['events']):
            raise RuntimeError('expected demonstration gunshot event was not returned')
        record('audio_corrupt_rejected', client.post('/v1/audio/analyze',
            files={'file': ('bad.wav', b'not wav', 'audio/wav')},
            data={'station_id': 'delta-demo-01', 'recorded_at': '2026-05-10T09:00:00Z'}), 422)
        forecast = json.loads((root/'examples/forecast_request.json').read_text(encoding='utf-8'))
        record('forecast_demo', client.post('/v1/spawning/forecast', json=forecast), 200)
        bad = copy.deepcopy(forecast)
        bad['issued_at'] = '2026-05-10T09:00:00'
        record('forecast_timezone_rejected', client.post('/v1/spawning/forecast', json=bad), 422)
        record('iot_batch', client.post('/v1/iot/validate', json=json.loads((root/'examples/iot_batch.json').read_text())), 200)
        image_files = {key: (f'{key}.png', (root/f'data/images/synthetic_{key}.png').read_bytes(), 'image/png') for key in ('before', 'after')}
        metadata = {'station_id': 'delta-demo-01', 'before_at': '2026-05-09T09:00:00Z',
                    'after_at': '2026-05-10T09:00:00Z', 'aligned': 'true', 'is_demo': 'true'}
        record('vision_synthetic_change', client.post('/v1/images/compare', files=image_files, data=metadata), 200)
        metadata['aligned'] = 'false'
        record('vision_unaligned_rejected', client.post('/v1/images/compare', files=image_files, data=metadata), 422)
        schema = client.get('/openapi.json')
        schema.raise_for_status()
        (root/'docs').mkdir(exist_ok=True)
        (root/'docs/openapi.json').write_text(json.dumps(schema.json(), indent=2, ensure_ascii=False), encoding='utf-8')
        report['success'] = report['completed'] = True
    except Exception as error:
        report['error'] = f'{type(error).__name__}: {error}'
    finally:
        report['finished_at'] = datetime.now(timezone.utc).isoformat()
        save()
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://127.0.0.1:8001')
    args = parser.parse_args()
    with httpx.Client(base_url=args.base_url, timeout=60) as client:
        report = run_checks(client)
    print('HTTP checks passed' if report['success'] else 'HTTP checks failed: '+report.get('error', 'see report'))
    return 0 if report['success'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
