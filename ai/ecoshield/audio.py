"""Decode bounded WAV files and preserve short events in YAMNet frame scores."""
from dataclasses import dataclass
import io
import math
import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

MAX_BYTES = 10 * 1024 * 1024
POLICY_VERSION = 'audio-policy-0.1.0'
TARGETS = {
    'boat_suspected': (['Motorboat, speedboat', 'Boat, Water vehicle'], 0.30, 2),
    'gunshot_suspected': (['Gunshot, gunfire', 'Machine gun'], 0.35, 1),
    'engine_detected': (['Engine', 'Medium engine (mid frequency)', 'Heavy engine (low frequency)', 'Light engine (high frequency)'], 0.40, 2),
}


@dataclass
class DecodedAudio:
    waveform: np.ndarray
    duration_seconds: float
    original_sample_rate: int
    original_channels: int


def decode_audio(data):
    if not data or len(data) > MAX_BYTES:
        raise ValueError('audio must contain a WAV file no larger than 10 MiB')
    try:
        with sf.SoundFile(io.BytesIO(data)) as f:
            if f.format not in ('WAV', 'WAVEX') or f.channels not in (1, 2) or not 8000 <= f.samplerate <= 192000:
                raise ValueError('expected mono/stereo WAV, 8–192 kHz')
            duration = f.frames / f.samplerate
            if not 1 <= duration <= 60:
                raise ValueError('audio duration must be 1 to 60 seconds')
            waveform = f.read(dtype='float32', always_2d=True)
            original_rate, channels = f.samplerate, f.channels
    except (RuntimeError, sf.LibsndfileError) as error:
        raise ValueError('cannot decode WAV audio') from error
    if not np.isfinite(waveform).all() or np.max(np.abs(waveform)) > 1.001:
        raise ValueError('audio amplitudes must be finite and in [-1, 1]')
    waveform = waveform.mean(axis=1)
    if original_rate != 16000:
        divisor = math.gcd(original_rate, 16000)
        waveform = resample_poly(waveform, 16000 // divisor, original_rate // divisor)
    waveform = np.clip(waveform, -1, 1).astype(np.float32)
    return DecodedAudio(waveform, duration, original_rate, channels)


def summarize_scores(scores, names, duration):
    scores = np.asarray(scores)
    if scores.ndim != 2 or scores.shape[1] != len(names) or not np.isfinite(scores).all():
        raise ValueError('invalid model score tensor')
    events, target_scores = [], {}
    uncertain = False
    for kind, (labels, threshold, minimum_windows) in TARGETS.items():
        indices = [names.index(name) for name in labels if name in names]
        if not indices:
            continue
        values = scores[:, indices].max(axis=1)
        target_scores[kind] = round(float(values.max()), 6)
        uncertain |= bool(values.max() >= threshold / 2)
        selected = np.flatnonzero(values >= threshold)
        for run in np.split(selected, np.where(np.diff(selected) > 1)[0] + 1):
            if len(run) < minimum_windows:
                continue
            start = float(run[0]) * .48
            end = min(float(duration), float(run[-1]) * .48 + .975)
            if start >= duration:
                continue
            events.append({'type': kind, 'start_seconds': round(start, 3), 'end_seconds': round(end, 3),
                           'score': round(float(values[run].max()), 6), 'threshold': threshold,
                           'review_recommended': kind != 'engine_detected'})
    top = np.argsort(scores.mean(axis=0))[-5:][::-1]
    return {
        'status': 'events_detected' if events else ('uncertain' if uncertain else 'no_target_event'),
        'events': sorted(events, key=lambda event: event['start_seconds']), 'target_scores': target_scores,
        'top_classes': [{'class': names[i], 'mean_score': round(float(scores[:, i].mean()), 6)} for i in top],
        'score_is_calibrated_probability': False, 'policy_version': POLICY_VERSION,
        'thresholds_calibrated': False,
        'warnings': ['Sound does not establish poaching. Initial thresholds are illustrative and uncalibrated.',
                     'No target event does not prove absence of a boat or gunshot.'],
    }
