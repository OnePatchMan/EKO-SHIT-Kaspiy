import copy
import unittest
from ecoshield.forecast import forecast_conditions


def request():
    return {
        'station_id': 'delta-demo-01', 'species': 'cyprinus_carpio',
        'issued_at': '2026-05-10T12:00:00+03:00', 'horizon_hours': 72,
        'data_source': 'demo_synthetic', 'level_datum': 'demo local gauge zero',
        'history': [
            {'timestamp': '2026-05-08T09:00:00Z', 'temperature_c': 15.0, 'water_level_m': 2.0, 'inundated': True},
            {'timestamp': '2026-05-09T09:00:00Z', 'temperature_c': 16.0, 'water_level_m': 2.1, 'inundated': True},
            {'timestamp': '2026-05-10T09:00:00Z', 'temperature_c': 17.0, 'water_level_m': 2.2, 'inundated': True},
        ],
    }


class ForecastTests(unittest.TestCase):
    def test_future_dates_and_demo_provenance(self):
        result = forecast_conditions(request())
        self.assertEqual(len(result['trajectory']), 3)
        self.assertEqual(result['trajectory'][0]['timestamp'], '2026-05-11T09:00:00Z')
        self.assertEqual(result['trajectory'][-1]['timestamp'], '2026-05-13T09:00:00Z')
        self.assertTrue(result['is_demo'])
        self.assertIsNone(result['probability'])
        self.assertEqual(result['validation_status'], 'not_validated_on_spawning_observations')
        self.assertAlmostEqual(result['trajectory'][0]['temperature_c'], 18.0, places=2)

    def test_missing_inundation_never_confirms_conditions(self):
        payload = request()
        for row in payload['history']:
            row.pop('inundated')
        result = forecast_conditions(payload)
        self.assertIn('inundation', result['missing_data'])
        self.assertTrue(all(row['conditions'] == 'insufficient_data' for row in result['trajectory']))

    def test_cold_scenario_changes_future_conditions(self):
        payload = request()
        payload['scenario'] = [
            {'timestamp': '2026-05-11T09:00:00Z', 'temperature_c': 8.0, 'water_level_m': 2.1, 'inundated': True},
            {'timestamp': '2026-05-12T09:00:00Z', 'temperature_c': 9.0, 'water_level_m': 2.1, 'inundated': True},
            {'timestamp': '2026-05-13T09:00:00Z', 'temperature_c': 10.0, 'water_level_m': 2.1, 'inundated': True},
        ]
        result = forecast_conditions(payload)
        self.assertEqual(result['hydrology_method'], 'provided_scenario')
        self.assertTrue(all(row['conditions'] == 'temperature_trigger_not_met' for row in result['trajectory']))

    def test_invalid_or_future_history_rejected(self):
        for mutate in [
            lambda p: p.update(issued_at='2026-05-10T12:00:00'),
            lambda p: p['history'][-1].update(timestamp='2026-05-11T09:00:00Z'),
            lambda p: p['history'][-1].update(temperature_c=float('nan')),
            lambda p: p.update(horizon_hours=96),
            lambda p: p.update(level_datum=None),
        ]:
            payload = copy.deepcopy(request())
            mutate(payload)
            with self.subTest(payload=payload), self.assertRaises(ValueError):
                forecast_conditions(payload)
