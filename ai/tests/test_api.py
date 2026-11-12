import unittest
from fastapi.testclient import TestClient
from ecoshield.api import create_app
from test_forecast import request
from test_audio import wav_bytes


class HttpTests(unittest.TestCase):
    def setUp(self):
        self.client = TestClient(create_app(analyzer=None))

    def test_health_reports_model_unavailable(self):
        response = self.client.get('/health')
        self.assertEqual(response.status_code, 200)
        self.assertFalse(response.json()['audio_model_ready'])

    def test_real_model_missing_never_returns_fake_classification(self):
        response = self.client.post('/v1/audio/analyze', files={'file': ('test.wav', wav_bytes(), 'audio/wav')},
                                    data={'station_id': 'delta-demo-01', 'recorded_at': '2026-05-10T09:00:00Z'})
        self.assertEqual(response.status_code, 503)

    def test_scenario_and_station_correlation(self):
        response = self.client.post('/v1/spawning/forecast', json=request())
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()['station_id'], 'delta-demo-01')
        self.assertIsNone(response.json()['probability'])

    def test_invalid_forecast_is_client_error(self):
        payload = request()
        payload['history'] = []
        response = self.client.post('/v1/spawning/forecast', json=payload)
        self.assertEqual(response.status_code, 422)
        self.assertIn('detail', response.json())

    def test_iot_validation_does_not_claim_storage(self):
        response = self.client.post('/v1/iot/validate', json={'station_id': 's1', 'data_source': 'demo_synthetic',
            'measurements': [{'timestamp': '2026-05-10T09:00:00Z', 'temperature_c': 17}]})
        self.assertEqual(response.status_code, 200)
        self.assertFalse(response.json()['stored'])

    def test_openapi_exposes_all_four_technology_modules(self):
        paths = self.client.get('/openapi.json').json()['paths']
        for route in ['/v1/audio/analyze', '/v1/spawning/forecast', '/v1/images/compare', '/v1/iot/validate']:
            self.assertIn(route, paths)
