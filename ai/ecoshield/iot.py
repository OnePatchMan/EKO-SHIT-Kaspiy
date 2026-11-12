"""Validate sensor batches; storage belongs to the Java backend."""
from .validation import identifier, observation, source, timestamp


def validate_batch(payload):
    if not isinstance(payload, dict) or set(payload) - {'station_id', 'data_source', 'level_datum', 'measurements'}:
        raise ValueError('invalid IoT batch fields')
    station = identifier(payload.get('station_id'))
    provenance = source(payload.get('data_source'))
    rows = payload.get('measurements')
    if not isinstance(rows, list) or not 1 <= len(rows) <= 1000:
        raise ValueError('measurements requires 1 to 1000 records')
    normalized = [observation(row) for row in rows]
    times = [row['timestamp'] for row in normalized]
    if len(set(times)) != len(times):
        raise ValueError('duplicate observation timestamps')
    datum = payload.get('level_datum')
    if any(row['water_level_m'] is not None for row in normalized):
        datum = identifier(datum, 'level_datum')
    return {'station_id': station, 'data_source': provenance, 'is_demo': provenance == 'demo_synthetic',
            'measurements': sorted(normalized, key=lambda row: timestamp(row['timestamp'])), 'level_datum': datum,
            'stored': False, 'count': len(normalized)}
