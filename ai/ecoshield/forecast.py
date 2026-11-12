"""Transparent demonstration of future conditions, not a biological probability."""
from datetime import timedelta
import numpy as np
from .validation import identifier, iso, number, observation, source, timestamp

ALGORITHM_VERSION = 'carp-scenario-0.1.0'
TEMPERATURE_TRIGGER_C = 17.0


def forecast_conditions(payload):
    if not isinstance(payload, dict):
        raise ValueError('forecast request must be a JSON object')
    allowed = {'station_id', 'species', 'issued_at', 'horizon_hours', 'data_source', 'level_datum', 'history', 'scenario'}
    if set(payload) - allowed:
        raise ValueError(f'unknown forecast fields: {sorted(set(payload) - allowed)}')
    station = identifier(payload.get('station_id'))
    if payload.get('species') != 'cyprinus_carpio':
        raise ValueError('this demonstration supports species=cyprinus_carpio only')
    issued = timestamp(payload.get('issued_at'))
    horizon = payload.get('horizon_hours', 72)
    if isinstance(horizon, bool) or not isinstance(horizon, int) or horizon not in (24, 48, 72):
        raise ValueError('horizon_hours must be 24, 48 or 72')
    try:
        valid_to = issued + timedelta(hours=horizon)
    except OverflowError as error:
        raise ValueError('forecast horizon is outside the supported date range') from error
    provenance = source(payload.get('data_source'))
    raw_history = payload.get('history')
    if not isinstance(raw_history, list) or not 3 <= len(raw_history) <= 1000:
        raise ValueError('history requires 3 to 1000 observations')
    history = sorted((observation(row) for row in raw_history), key=lambda row: timestamp(row['timestamp']))
    times = [timestamp(row['timestamp']) for row in history]
    if len(set(times)) != len(times) or times[-1] > issued:
        raise ValueError('history must have unique timestamps no later than issued_at')
    if (times[-1] - times[0]).total_seconds() < 48 * 3600:
        raise ValueError('history must span at least 48 hours')
    if issued - times[-1] > timedelta(hours=24):
        raise ValueError('last observation is older than 24 hours; refresh data before projection')
    if any(row['temperature_c'] is None for row in history):
        raise ValueError('temperature_c is required in every forecast history row')
    level_datum = payload.get('level_datum')
    scenario_input = payload.get('scenario')
    if scenario_input is not None and (not isinstance(scenario_input, list) or len(scenario_input) != horizon // 24):
        raise ValueError('scenario requires one observation per forecast day')
    scenario = [observation(row) for row in scenario_input] if scenario_input is not None else None
    if any(row['water_level_m'] is not None for row in history + (scenario or [])):
        level_datum = identifier(level_datum, 'level_datum')
    warnings = [
        'Scenario conditions are not a validated forecast of actual spawning.',
        'The 17 C trigger is illustrative; population, habitat and biological observations are required.',
    ]
    missing = []
    last = history[-1]
    trajectory = []
    for day in range(1, horizon // 24 + 1):
        future = issued + timedelta(days=day)
        if scenario is not None:
            row = scenario[day - 1].copy()
            if timestamp(row['timestamp']) != future or row['temperature_c'] is None:
                raise ValueError('scenario timestamps must equal issued_at + 24, 48, 72 hours and require temperature_c')
        else:
            row = {'timestamp': iso(future), 'inundated': last['inundated']}
            for name, max_change in [('temperature_c', 2.0), ('water_level_m', 0.5)]:
                available = [(t, r[name]) for t, r in zip(times, history) if r[name] is not None and (times[-1] - t).total_seconds() <= 7 * 86400]
                if not available:
                    row[name] = None
                    continue
                x = np.array([(t - times[-1]).total_seconds() / 86400 for t, _ in available])
                values = np.array([v for _, v in available])
                slope = float(np.polyfit(x, values, 1)[0]) if len(available) >= 3 and np.ptp(x) >= 1 else 0.0
                slope = float(np.clip(slope, -max_change, max_change))
                projected = values[-1] + slope * (future - available[-1][0]).total_seconds() / 86400
                row[name] = round(float(np.clip(projected, -2, 45)) if name == 'temperature_c' else projected, 3)
            row['persistence_temperature_c'] = last['temperature_c']
        if row.get('inundated') is None:
            condition = 'insufficient_data'
            if 'inundation' not in missing:
                missing.append('inundation')
        elif not row['inundated']:
            condition = 'floodplain_not_inundated'
        elif row['temperature_c'] < TEMPERATURE_TRIGGER_C:
            condition = 'temperature_trigger_not_met'
        else:
            condition = 'demo_trigger_met_requires_inspector_review'
        row['conditions'] = condition
        row['temperature_trigger_met'] = row['temperature_c'] >= TEMPERATURE_TRIGGER_C
        trajectory.append(row)
    if scenario is None:
        warnings.append('Linear trends are capped at 2 C/day and 0.5 m/day; inundation is assumed unchanged. These are demonstration assumptions, not calibrated physics.')
    if all(row.get('water_level_m') is None for row in trajectory):
        missing.append('water_level_m')
    return {
        'station_id': station, 'species': 'cyprinus_carpio', 'issued_at': iso(issued),
        'valid_from': iso(issued), 'valid_to': iso(valid_to),
        'mode': 'scenario_conditions_forecast', 'is_demo': True, 'data_source': provenance,
        'validation_status': 'not_validated_on_spawning_observations', 'probability': None,
        'algorithm_version': ALGORITHM_VERSION, 'horizon_hours': horizon, 'level_datum': level_datum,
        'hydrology_method': 'provided_scenario' if scenario is not None else 'capped_linear_projection',
        'temperature_trigger_c': TEMPERATURE_TRIGGER_C, 'trajectory': trajectory,
        'missing_data': missing, 'warnings': warnings,
        'factors': ['water temperature relative to demonstration trigger', 'known or assumed floodplain inundation', 'water level trend when available'],
    }
