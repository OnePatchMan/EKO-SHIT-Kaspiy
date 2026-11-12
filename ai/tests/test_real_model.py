"""Opt-in integration checks; run with ECOSHIELD_REAL_MODEL=1 after asset setup."""
import os
from pathlib import Path
import unittest
import numpy as np


@unittest.skipUnless(os.environ.get('ECOSHIELD_REAL_MODEL') == '1', 'requires downloaded official weights')
class RealModelTests(unittest.TestCase):
    def test_official_weights_produce_521_finite_scores_and_real_audio_contract(self):
        from ecoshield.model import YamnetAnalyzer
        analyzer = YamnetAnalyzer()
        scores, _, _ = analyzer.model(np.zeros(16000, dtype=np.float32))
        self.assertEqual(scores.shape[1], 521)
        self.assertTrue(np.isfinite(scores.numpy()).all())
        sample = Path(__file__).resolve().parents[1] / 'data/audio/boat_thailand.wav'
        result = analyzer.analyze(sample.read_bytes())
        self.assertIn('Motorboat, speedboat', analyzer.names)
        self.assertFalse(result['score_is_calibrated_probability'])
        self.assertGreater(result['duration_seconds'], 0)
