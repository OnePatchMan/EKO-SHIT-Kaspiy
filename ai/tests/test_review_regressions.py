import json
from pathlib import Path
import tempfile
import unittest
import httpx
from fastapi.testclient import TestClient
from ecoshield.api import create_app


class ValidationBoundaryTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(create_app(analyzer=None), raise_server_exceptions=False)

    def test_huge_integer_returns_422(self):
        payload = {'station_id': 's1', 'data_source': 'demo_synthetic', 'measurements': [
            {'timestamp': '2026-05-10T09:00:00Z', 'temperature_c': 10**400}]}
        self.assertEqual(self.client.post('/v1/iot/validate', json=payload).status_code, 422)

    def test_timezone_overflow_returns_422(self):
        for stamp in ['0001-01-01T00:00:00+03:00', '9999-12-31T23:59:59-03:00']:
            payload = {'station_id': 's1', 'data_source': 'demo_synthetic', 'measurements': [
                {'timestamp': stamp, 'temperature_c': 17}]}
            self.assertEqual(self.client.post('/v1/iot/validate', json=payload).status_code, 422)

    def test_forecast_horizon_date_overflow_returns_422(self):
        payload = {'station_id': 's1', 'species': 'cyprinus_carpio', 'issued_at': '9999-12-31T09:00:00Z',
                   'horizon_hours': 24, 'data_source': 'demo_synthetic', 'history': [
                       {'timestamp': f'9999-12-{day}T09:00:00Z', 'temperature_c': 17} for day in (29, 30, 31)]}
        self.assertEqual(self.client.post('/v1/spawning/forecast', json=payload).status_code, 422)

    def test_valid_early_history_does_not_overflow_week_lookback(self):
        payload = {'station_id': 's1', 'species': 'cyprinus_carpio', 'issued_at': '0001-01-03T09:00:00Z',
                   'horizon_hours': 24, 'data_source': 'demo_synthetic', 'history': [
                       {'timestamp': f'0001-01-0{day}T09:00:00Z', 'temperature_c': 17} for day in (1, 2, 3)]}
        self.assertEqual(self.client.post('/v1/spawning/forecast', json=payload).status_code, 200)


class EvidenceFailureTests(unittest.TestCase):
    def test_audio_error_replaces_stale_success_and_keeps_other_controls(self):
        from scripts.evaluate import run_evaluation
        class FailingAnalyzer:
            def analyze(self, data):
                if data == b'bad':
                    raise ValueError('deliberately corrupt control')
                return {'events': [], 'status': 'no_target_event', 'target_scores': {}, 'latency_ms': 1}
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as folder:
            root = Path(folder)
            (root/'data').mkdir()
            (root/'data/a.wav').write_bytes(b'bad')
            (root/'data/b.wav').write_bytes(b'good')
            (root/'data/manifest.json').write_text(json.dumps({'samples': [
                {'file': 'data/a.wav', 'label': 'background'}, {'file': 'data/b.wav', 'label': 'background'}]}))
            report = run_evaluation(root, analyzer_factory=FailingAnalyzer)
            self.assertFalse(report['success'])
            self.assertTrue(report['completed'])
            self.assertEqual(len(report['records']), 2)
            self.assertIn('error', report['records'][0])
            saved = json.loads((root/'reports/audio_control_results.json').read_text())
            self.assertFalse(saved['success'])

    def test_http_status_failure_replaces_previous_success(self):
        from scripts.smoke_http import run_checks
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as folder:
            root = Path(folder)
            (root/'reports').mkdir()
            path = root/'reports/http_smoke_results.json'
            path.write_text('{"success": true, "completed": true}')
            transport = httpx.MockTransport(lambda request: httpx.Response(500, json={'detail': 'test failure'}))
            with httpx.Client(base_url='http://test', transport=transport) as client:
                self.assertFalse(run_checks(client, root)['success'])
            report = json.loads(path.read_text())
            self.assertFalse(report['success'])
            self.assertFalse(report['completed'])
            self.assertEqual(report['cases'][0]['http_status'], 500)

    def test_http_connection_failure_is_recorded(self):
        from scripts.smoke_http import run_checks
        def fail(request):
            raise httpx.ConnectError('deliberately offline')
        with tempfile.TemporaryDirectory(dir=Path(__file__).parent) as folder:
            root = Path(folder)
            with httpx.Client(base_url='http://test', transport=httpx.MockTransport(fail)) as client:
                report = run_checks(client, root)
            self.assertFalse(report['success'])
            self.assertIn('ConnectError', report['error'])
            self.assertTrue((root/'reports/http_smoke_results.json').exists())
