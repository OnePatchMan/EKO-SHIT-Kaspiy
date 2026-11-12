"""Real YAMNet inference from locally cached official weights."""
from pathlib import Path
import csv
import hashlib
import importlib
import json
import os
import sys
from threading import Lock
from time import perf_counter
import numpy as np
from .audio import decode_audio, summarize_scores

ROOT = Path(__file__).resolve().parents[1]


class YamnetAnalyzer:
    def __init__(self, root=ROOT):
        root = Path(root)
        weights = root / 'models/yamnet.h5'
        vendor = root / 'vendor/yamnet'
        if not weights.is_file() or not (vendor / 'yamnet.py').is_file():
            raise FileNotFoundError('Run python scripts/setup_assets.py to obtain official YAMNet assets')
        digest = hashlib.sha256(weights.read_bytes()).hexdigest()
        manifest_path = root / 'data/model_manifest.json'
        if manifest_path.exists():
            manifest = json.loads(manifest_path.read_text(encoding='utf-8'))
            expected = next(item['sha256'] for item in manifest['files'] if Path(item['path']).name == 'yamnet.h5')
            if digest != expected:
                raise ValueError('YAMNet weights checksum mismatch')
        os.environ.setdefault('TF_CPP_MIN_LOG_LEVEL', '2')
        sys.path.insert(0, str(vendor))
        params = importlib.import_module('params')
        yamnet = importlib.import_module('yamnet')
        self.model = yamnet.yamnet_frames_model(params.Params())
        self.model.load_weights(str(weights))
        with (vendor / 'yamnet_class_map.csv').open(encoding='utf-8', newline='') as stream:
            self.names = [row['display_name'] for row in csv.DictReader(stream)]
        if len(self.names) != 521:
            raise ValueError('YAMNet class map must contain 521 names')
        self.version = f'google-yamnet-1-h5-{digest[:12]}'
        self.lock = Lock()
        self.model(np.zeros(16000, dtype=np.float32))  # warm-up, not a validation recording

    def analyze(self, data):
        started = perf_counter()
        audio = decode_audio(data)
        with self.lock:
            scores, _, _ = self.model(audio.waveform)
            result = summarize_scores(scores.numpy(), self.names, audio.duration_seconds)
        result.update({'model_version': self.version, 'duration_seconds': round(audio.duration_seconds, 4),
                       'model_sample_rate': 16000, 'original_sample_rate': audio.original_sample_rate,
                       'original_channels': audio.original_channels, 'frame_count': int(scores.shape[0]),
                       'latency_ms': round((perf_counter() - started) * 1000, 2),
                       'sensor_domain': 'air_audio_or_sound_effect; hydrophone performance unvalidated'})
        return result
