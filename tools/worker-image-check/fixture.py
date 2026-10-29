import base64
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import threading
import time
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlsplit
from urllib.request import Request, urlopen

import jwt
from cryptography.hazmat.primitives.asymmetric import rsa
import shogi


key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
public = key.public_key().public_numbers()
def number(value):
    return base64.urlsafe_b64encode(value.to_bytes((value.bit_length()+7)//8, 'big')).rstrip(b'=').decode()

jwks = {'keys': [{'kty': 'RSA', 'kid': 'fixture', 'alg': 'RS256', 'use': 'sig',
                  'n': number(public.n), 'e': number(public.e)}]}
rows = []
calls = []
lock = threading.Lock()


class Fixture(BaseHTTPRequestHandler):
    def log_message(self, *_):
        pass

    def reply(self, status, body=None, headers=None):
        content = json.dumps(body).encode() if body is not None else b''
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(content)))
        for name, value in (headers or {}).items():
            self.send_header(name, value)
        self.end_headers()
        if self.command != 'HEAD':
            self.wfile.write(content)

    def handle_request(self):
        url = urlsplit(self.path)
        params = parse_qs(url.query)
        calls.append((self.command, url.path))
        if url.path == '/auth/v1/.well-known/jwks.json':
            return self.reply(200, jwks)
        if self.headers.get('apikey') != 'fixture-service-key' or self.headers.get('Authorization') != 'Bearer fixture-service-key':
            return self.reply(401, {'error': 'fixture credentials required'})
        if url.path in ('/rest/v1/user_bans', '/rest/v1/quota_limits') and self.command == 'GET':
            return self.reply(200, [])
        if url.path != '/rest/v1/analysis_jobs':
            return self.reply(404, {'error': 'unexpected fixture path'})
        def matching(row):
            for field in ('id', 'user_id', 'moves_hash'):
                if field in params and params[field][0] != 'eq.'+row[field]:
                    return False
            mode = params.get('moves_usi->>mode')
            return not mode or mode[0] == 'eq.'+row['moves_usi']['mode']
        with lock:
            if self.command == 'GET':
                return self.reply(200, [row for row in rows if matching(row)])
            if self.command == 'HEAD':
                count = sum(matching(row) and row['status'] != 'error' for row in rows)
                return self.reply(200, headers={'Content-Range': f'*/{count}'})
            body = json.loads(self.rfile.read(int(self.headers['Content-Length'])))
            if self.command == 'POST':
                created = []
                for item in body:
                    if any(r['user_id'] == item['user_id'] and r['moves_hash'] == item['moves_hash'] for r in rows):
                        continue
                    row = dict(item, id=f'fixture-{len(rows)}', created_at=datetime.now(timezone.utc).isoformat(),
                               result_json=None, engine_meta=None, error=None)
                    rows.append(row)
                    created.append(row)
                return self.reply(201, created)
            if self.command == 'PATCH':
                for row in rows:
                    if matching(row):
                        row.update(body)
                return self.reply(204)
        self.reply(405)

    do_GET = do_HEAD = do_POST = do_PATCH = handle_request


server = ThreadingHTTPServer(('0.0.0.0', 8081), Fixture)
threading.Thread(target=server.serve_forever, daemon=True).start()
token = jwt.encode({'sub': 'fixture-user', 'iss': 'http://fixture:8081/auth/v1',
                    'aud': 'authenticated', 'exp': int(time.time())+600}, key,
                   algorithm='RS256', headers={'kid': 'fixture'})
for _ in range(90):
    try:
        with urlopen('http://worker:8080/health', timeout=2) as response:
            assert response.status == 200
            break
    except (URLError, TimeoutError):
        time.sleep(1)
else:
    raise RuntimeError('Worker did not become healthy')


def request(body, bearer=token):
    started = time.monotonic()
    req = Request('http://worker:8080/v1/analyses', data=json.dumps(body).encode(),
                  headers={'Content-Type': 'application/json', 'Authorization': 'Bearer '+bearer})
    with urlopen(req, timeout=90) as response:
        assert response.status == 200
        lines = [json.loads(line) for line in response.read().decode().splitlines()]
    result = lines[-1]
    assert 'result' in result and 'engine_meta' in result, result
    return result, time.monotonic()-started


try:
    request({'sfen': shogi.STARTING_SFEN}, 'invalid')
    raise AssertionError('Invalid JWT was accepted')
except HTTPError as error:
    assert error.code == 401

payload = {'sfen': shogi.STARTING_SFEN, 'moves': ['7g7f', '3c3d'], 'multi_pv': 3, 'purpose': 'study'}
study, study_seconds = request(payload)
assert study['engine_meta']['fv_scale'] == 40
assert study['engine_meta']['eval_sha256'] == 'f8ee839ae8c08537036f23345dd5ed0416958b22425476fc60177942903219b5'
cached, cached_seconds = request(payload)
assert cached == study and len(rows) == 1
old, old_seconds = request({k:v for k,v in payload.items() if k != 'purpose'})
drill, drill_seconds = request(dict(payload, purpose='drill'))
assert old['engine_meta']['fv_scale'] == drill['engine_meta']['fv_scale'] == 20
assert drill['engine_meta']['multi_pv'] == 2
assert old['engine_meta']['eval_sha256'] == drill['engine_meta']['eval_sha256'] == '1141d275bceec911156801f27303dc9ff5beb24f4f59144cc069306c59e80782'
assert len(rows) == 3 and len({r['moves_hash'] for r in rows}) == 3
assert all(row['status'] == 'done' and row['engine_meta'] for row in rows)
assert rows[0]['moves_usi']['purpose'] == 'study'
assert rows[0]['engine_meta'] == study['engine_meta'] and rows[0]['result_json'] == study['result']
for result, count in ((study, 3), (old, 3), (drill, 2)):
    assert len(result['result'][0]) == count
    for pv in result['result'][0]:
        board = shogi.Board()
        for move in payload['moves']:
            board.push_usi(move)
        for move in pv['pv']:
            parsed = shogi.Move.from_usi(move)
            assert board.is_legal(parsed)
            board.push(parsed)
report = {'study_seconds': study_seconds, 'cache_seconds': cached_seconds,
          'legacy_seconds': old_seconds, 'drill_seconds': drill_seconds, 'saved_jobs': len(rows),
          'study_meta': study['engine_meta'], 'legacy_meta': old['engine_meta'], 'drill_meta': drill['engine_meta'],
          'invalid_jwt_rejected': True, 'pv_legality': True,
          'scope': 'Production worker entrypoint with isolated JWKS/PostgREST fixture; not a real Supabase database'}
print('WORKER_IMAGE_RESULT='+json.dumps(report), flush=True)
server.shutdown()
