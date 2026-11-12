import unittest
from ecoshield.forecast import forecast_conditions
from ecoshield.iot import validate_batch


class TimeOrderingTests(unittest.TestCase):
    def test_future_fractional_timestamp_cannot_hide_before_whole_second(self):
        payload = {'station_id': 's1', 'species': 'cyprinus_carpio',
                   'issued_at': '2026-05-10T09:00:00.250000Z', 'horizon_hours': 24,
                   'data_source': 'demo_synthetic', 'history': [
                       {'timestamp': '2026-05-08T09:00:00Z', 'temperature_c': 15},
                       {'timestamp': '2026-05-10T09:00:00Z', 'temperature_c': 16},
                       {'timestamp': '2026-05-10T09:00:00.500000Z', 'temperature_c': 17}]}
        with self.assertRaisesRegex(ValueError, 'no later than'):
            forecast_conditions(payload)

    def test_iot_fractional_seconds_are_chronologically_sorted(self):
        result = validate_batch({'station_id': 's1', 'data_source': 'demo_synthetic', 'measurements': [
            {'timestamp': '2026-05-10T09:00:00.500000Z', 'temperature_c': 17},
            {'timestamp': '2026-05-10T09:00:00Z', 'temperature_c': 16}]})
        self.assertEqual(result['measurements'][0]['temperature_c'], 16)
