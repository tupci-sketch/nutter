"""Reads .nitro bundles: a count, then (name, zlib data) entries."""
import json, struct, zlib

def read(path):
    data = open(path, 'rb').read()
    n = struct.unpack('>H', data[:2])[0]
    pos, files = 2, {}
    for _ in range(n):
        ln = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
        name = data[pos:pos+ln].decode(); pos += ln
        size = struct.unpack('>I', data[pos:pos+4])[0]; pos += 4
        files[name] = zlib.decompress(data[pos:pos+size]); pos += size
    return files

def info(path):
    files = read(path)
    js = next((json.loads(v) for k, v in files.items() if k.endswith('.json')), {})
    return js
