import io
import unittest
import numpy as np
from PIL import Image
from ecoshield.vision import compare_images
from ecoshield.iot import validate_batch


def png(array):
    out = io.BytesIO()
    Image.fromarray(array).save(out, format='PNG')
    return out.getvalue()


class VisionTests(unittest.TestCase):
    def test_identical_pair_has_no_change(self):
        image = png(np.full((100, 100, 3), 100, dtype=np.uint8))
        result = compare_images(image, image, aligned=True)
        self.assertEqual(result['changed_fraction'], 0)
        self.assertEqual(result['candidate_regions'], [])
        self.assertFalse(result['detects_illegal_nets_or_spills'])

    def test_local_change_produces_candidate_region(self):
        before = np.full((100, 100, 3), 100, dtype=np.uint8)
        after = before.copy()
        after[20:40, 30:50] = 230
        result = compare_images(png(before), png(after), aligned=True)
        self.assertGreater(result['changed_fraction'], 0)
        self.assertGreater(len(result['candidate_regions']), 0)

    def test_unaligned_and_mismatched_pair_rejected(self):
        a = png(np.zeros((100, 100, 3), dtype=np.uint8))
        b = png(np.zeros((101, 100, 3), dtype=np.uint8))
        with self.assertRaises(ValueError):
            compare_images(a, a, aligned=False)
        with self.assertRaises(ValueError):
            compare_images(a, b, aligned=True)


class IotTests(unittest.TestCase):
    def test_units_and_provenance_preserved(self):
        batch = {'station_id': 'demo-01', 'data_source': 'demo_synthetic', 'measurements': [
            {'timestamp': '2026-05-10T09:00:00Z', 'temperature_c': 17, 'dissolved_oxygen_mg_l': 6.5}
        ]}
        result = validate_batch(batch)
        self.assertEqual(result['measurements'][0]['temperature_c'], 17)
        self.assertTrue(result['is_demo'])
        self.assertFalse(result['stored'])

    def test_invalid_oxygen_and_naive_time_rejected(self):
        for row in [
            {'timestamp': '2026-05-10T09:00:00Z', 'temperature_c': 17, 'dissolved_oxygen_mg_l': -1},
            {'timestamp': '2026-05-10T09:00:00', 'temperature_c': 17},
        ]:
            with self.assertRaises(ValueError):
                validate_batch({'station_id': 's1', 'data_source': 'demo_synthetic', 'measurements': [row]})
