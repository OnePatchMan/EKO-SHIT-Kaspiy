"""One synthetic sensor batch sent to the validation endpoint; no real hardware."""
import argparse
from datetime import datetime, timezone
import json
from urllib.request import Request, urlopen

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--base-url', default='http://127.0.0.1:8001')
args = parser.parse_args()
payload = {'station_id': 'delta-demo-01', 'data_source': 'demo_synthetic',
           'level_datum': 'synthetic local gauge zero', 'measurements': [{
               'timestamp': datetime.now(timezone.utc).isoformat(), 'temperature_c': 17.2,
               'water_level_m': 2.2, 'dissolved_oxygen_mg_l': 7.1, 'inundated': True}]}
request = Request(args.base_url.rstrip('/')+'/v1/iot/validate', data=json.dumps(payload).encode(),
                  headers={'Content-Type': 'application/json'}, method='POST')
with urlopen(request, timeout=20) as response:
    print(response.read().decode())
