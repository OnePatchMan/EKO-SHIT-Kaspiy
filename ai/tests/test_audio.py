import io
import unittest
import wave
import numpy as np
from ecoshield.audio import decode_audio, summarize_scores


def wav_bytes(rate=48000, seconds=2, channels=2):
    data = (np.sin(np.arange(rate * seconds) * 2 * np.pi * 300 / rate) * 1000).astype('<i2')
    data = np.repeat(data[:, None], channels, axis=1)
    out = io.BytesIO()
    with wave.open(out, 'wb') as f:
        f.setnchannels(channels)
        f.setsampwidth(2)
        f.setframerate(rate)
        f.writeframes(data.tobytes())
    return out.getvalue()


class AudioTests(unittest.TestCase):
    def test_real_resampling_preserves_duration(self):
        audio = decode_audio(wav_bytes())
        self.assertEqual(audio.waveform.shape, (32000,))
        self.assertEqual(audio.waveform.dtype, np.float32)
        self.assertEqual(audio.duration_seconds, 2)
        self.assertEqual(audio.original_sample_rate, 48000)
        self.assertLessEqual(float(abs(audio.waveform).max()), 1)

    def test_invalid_and_overlong_audio_rejected(self):
        for data in [b'not a wav', wav_bytes(rate=8000, seconds=61), b'']:
            with self.subTest(length=len(data)), self.assertRaises(ValueError):
                decode_audio(data)

    def test_single_gunshot_window_is_retained(self):
        scores = np.zeros((100, 3))
        scores[40, 1] = .9
        result = summarize_scores(scores, ['Motorboat, speedboat', 'Gunshot, gunfire', 'Engine'], 49)
        shots = [event for event in result['events'] if event['type'] == 'gunshot_suspected']
        self.assertEqual(len(shots), 1)
        self.assertAlmostEqual(shots[0]['start_seconds'], 19.2)

    def test_generic_engine_does_not_imply_boat(self):
        scores = np.array([[.01, .01, .9], [.01, .01, .9], [.01, .01, .9]])
        result = summarize_scores(scores, ['Motorboat, speedboat', 'Gunshot, gunfire', 'Engine'], 2)
        self.assertIn('engine_detected', [event['type'] for event in result['events']])
        self.assertNotIn('boat_suspected', [event['type'] for event in result['events']])

    def test_silence_is_no_target_without_asserting_no_boat(self):
        result = summarize_scores(np.zeros((3, 2)), ['Motorboat, speedboat', 'Gunshot, gunfire'], 2)
        self.assertEqual(result['status'], 'no_target_event')
        self.assertEqual(result['events'], [])
        self.assertFalse(result['score_is_calibrated_probability'])
