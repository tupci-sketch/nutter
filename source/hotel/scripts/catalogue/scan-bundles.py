"""Indexes furniture artwork bundles: logic type, size, height, states.

    scan-bundles.py <bundled/furniture dir> <out.json>
"""
import json, os, sys
from concurrent.futures import ProcessPoolExecutor
sys.path.insert(0, os.path.dirname(__file__))
import nitro
DIR = sys.argv[1] if len(sys.argv) > 1 else "/srv/habnut/nitro/bundled/furniture"

def one(fn):
    try:
        files = nitro.read(os.path.join(DIR, fn))
        js = next(json.loads(v) for k, v in files.items() if k.endswith('.json'))
        vis = js.get('visualizations') or [{}]
        v64 = next((v for v in vis if v.get('size') == 64), vis[0])
        anims = [int(k) for k in (v64.get('animations') or {}) if k.lstrip('-').isdigit()]
        states = sorted(a for a in anims if 0 <= a < 100)
        model = (js.get('logic') or {}).get('model') or {}
        dims = model.get('dimensions') or {}
        return fn[:-6], {
            'logic': js.get('logicType'), 'vis': js.get('visualizationType'),
            'x': dims.get('x'), 'y': dims.get('y'), 'z': dims.get('z'),
            'dirs': model.get('directions') or [], 'states': len(states) if states else 1,
            'colors': len((v64.get('colors') or {})), 'particles': bool((js.get('logic') or {}).get('particleSystems')),
            'sound': (js.get('logic') or {}).get('soundSample'),
        }
    except Exception as e:
        return fn[:-6], {'error': str(e)[:100]}

if __name__ == '__main__':
    fns = [f for f in os.listdir(DIR) if f.endswith('.nitro')]
    with ProcessPoolExecutor(2) as ex:
        out = dict(ex.map(one, fns, chunksize=200))
    json.dump(out, open(sys.argv[2] if len(sys.argv) > 2 else "bundles.json", "w"))
    errs = [k for k, v in out.items() if 'error' in v]
    from collections import Counter
    print(len(out), 'bundles,', len(errs), 'unreadable', errs[:5])
    print(Counter(v.get('logic') for v in out.values()).most_common(40))
