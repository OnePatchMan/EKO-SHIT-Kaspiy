"""Shared validation without silent unit or timezone conversion."""
from datetime import datetime, timezone
import math


def timestamp(value):
    if not isinstance(value, str):
        raise ValueError('timestamp must be an ISO 8601 string with timezone')
    try:
        result = datetime.fromisoformat(value.replace('Z', '+00:00'))
    except ValueError as error:
        raise ValueError('invalid ISO 8601 timestamp') from error
    if result.tzinfo is None or result.utcoffset() is None:
        raise ValueError('timestamp requires a timezone')
    try:
        return result.astimezone(timezone.utc)
    except (OverflowError, ValueError) as error:
        raise ValueError('timestamp is outside the supported UTC date range') from error


def iso(value):
    return value.astimezone(timezone.utc).isoformat().replace('+00:00', 'Z')


def number(value, name, minimum, maximum):
    if isinstance(value, bool) or not isinstance(value, (float, int)):
        raise ValueError(f'{name} must be a finite number')
    try:
        finite = math.isfinite(value)
    except OverflowError as error:
        raise ValueError(f'{name} is outside the supported numeric range') from error
    if not finite:
        raise ValueError(f'{name} must be a finite number')
    if not minimum <= value <= maximum:
        raise ValueError(f'{name} must be between {minimum} and {maximum}')
    return float(value)


def identifier(value, name='station_id'):
    if not isinstance(value, str) or not value.strip() or len(value) > 120:
        raise ValueError(f'{name} must be a nonempty string of at most 120 characters')
    return value.strip()


def source(value):
    if value not in ('demo_synthetic', 'open_observations', 'organizer_observations'):
        raise ValueError('data_source must explicitly identify demo_synthetic, open_observations or organizer_observations')
    return value


def observation(row):
    if not isinstance(row, dict):
        raise ValueError('measurement must be an object')
    allowed = {'timestamp', 'temperature_c', 'water_level_m', 'inundated',
               'dissolved_oxygen_mg_l', 'salinity_psu', 'discharge_m3_s'}
    if set(row) - allowed:
        raise ValueError(f'unknown measurement fields: {sorted(set(row) - allowed)}')
    result = {'timestamp': iso(timestamp(row.get('timestamp')))}
    limits = {'temperature_c': (-2, 45), 'water_level_m': (-100, 10000),
              'dissolved_oxygen_mg_l': (0, 30), 'salinity_psu': (0, 60),
              'discharge_m3_s': (0, 1000000)}
    for name, (lo, hi) in limits.items():
        result[name] = None if row.get(name) is None else number(row[name], name, lo, hi)
    if row.get('inundated') is not None and not isinstance(row['inundated'], bool):
        raise ValueError('inundated must be boolean or null')
    result['inundated'] = row.get('inundated')
    if all(result[name] is None for name in limits):
        raise ValueError('measurement requires at least one numeric water observation')
    return result
