#!/usr/bin/env python3
"""Old and current PostgREST contracts against real migrations in disposable containers."""
import base64
import hashlib
import hmac
import json
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import unittest
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
OWNER = '10000000-0000-0000-0000-000000000001'
OTHER = '10000000-0000-0000-0000-000000000002'


def docker(*args, sql=None):
    result = subprocess.run(['docker', *args], input=sql, text=True, capture_output=True, timeout=90)
    if result.returncode:
        raise RuntimeError(result.stderr)
    return (result.stdout + (result.stderr if args[0] == 'logs' else '')).strip()


class SyncCompatibilityApiTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.prefix = 'shogi-sync-test-' + secrets.token_hex(4)
        cls.db = cls.prefix + '-db'
        cls.api = cls.prefix + '-api'
        cls.temp = tempfile.TemporaryDirectory(prefix=cls.prefix)
        cls.addClassCleanup(cls.temp.cleanup)
        docker('network', 'create', cls.prefix)
        cls.addClassCleanup(lambda: docker('network', 'rm', cls.prefix))
        docker('run', '-d', '--name', cls.db, '--network', cls.prefix, '--user', 'postgres',
               '--entrypoint', 'sh', 'public.ecr.aws/supabase/postgres:17.6.1.141', '-c',
               "initdb -D /tmp/testdb -A trust --no-locale >/tmp/init.log && "
               "printf 'host postgres authenticator samenet trust\\n' >> /tmp/testdb/pg_hba.conf && "
               "exec postgres -D /tmp/testdb -c listen_addresses=* -c shared_preload_libraries=pg_cron -c cron.database_name=postgres")
        cls.addClassCleanup(lambda: docker('rm', '-fv', cls.db))
        for _ in range(60):
            ready = subprocess.run(['docker', 'exec', cls.db, 'pg_isready', '-U', 'postgres'], capture_output=True)
            if ready.returncode == 0:
                break
            time.sleep(0.25)
        else:
            raise RuntimeError('Disposable PostgreSQL did not start')
        cls.sql("""
CREATE ROLE authenticated;
CREATE ROLE anon;
CREATE ROLE service_role;
CREATE ROLE authenticator NOINHERIT LOGIN;
GRANT authenticated, anon TO authenticator;
CREATE SCHEMA auth;
CREATE TABLE auth.users(id uuid PRIMARY KEY);
CREATE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS
$$ SELECT (nullif(current_setting('request.jwt.claims',true),'')::jsonb->>'sub')::uuid $$;
""")
        cls.sql(f"GRANT USAGE ON SCHEMA public,auth TO authenticated,anon; INSERT INTO auth.users VALUES ('{OWNER}'),('{OTHER}');")
        migrations = sorted((ROOT / 'infra/supabase/migrations').glob('*.sql'))
        cls.pending = [p for p in migrations if p.name >= '20260919090000']
        for migration in migrations:
            if migration not in cls.pending:
                cls.sql(migration.read_text())
        cls.secret = secrets.token_bytes(32)
        config = Path(cls.temp.name) / 'postgrest.conf'
        config.write_text(f'db-uri = "postgres://authenticator@{cls.db}:5432/postgres"\ndb-schemas = "public"\ndb-anon-role = "anon"\njwt-secret = "{cls.secret.hex()}"\n')
        cls.addClassCleanup(lambda: docker('rm', '-fv', cls.api))
        docker('run', '-d', '--name', cls.api, '--network', cls.prefix, '-p', '127.0.0.1::3000',
               '-v', f'{config}:/etc/postgrest.conf:ro', 'public.ecr.aws/supabase/postgrest:v14.5', 'postgrest', '/etc/postgrest.conf')
        port = docker('port', cls.api, '3000/tcp').rsplit(':', 1)[1]
        cls.base = 'http://127.0.0.1:' + port
        for _ in range(80):
            try:
                if cls.request('GET', '/uploaded_games')[0] == 200:
                    break
            except (URLError, ConnectionError):
                pass
            time.sleep(0.25)
        else:
            raise RuntimeError('Disposable PostgREST did not start: ' + str(cls.request('GET', '/uploaded_games')) + '\n' + docker('logs', cls.api).replace(cls.secret.hex(), '[redacted]'))
        # Keep actual pre-migration data, then apply the pending migrations without rebuilding the API.
        cls.preexisting = 'a' * 64
        status, _ = cls.request('POST', '/uploaded_games', cls.game(cls.preexisting))
        if status != 201:
            raise AssertionError(f'Pre-migration insert failed: {status}')
        grants_query = "SELECT table_name || ':' || privilege_type FROM information_schema.role_table_grants WHERE grantee='authenticated' AND table_schema='public' AND table_name IN ('uploaded_games','drill_problems','drill_attempts') ORDER BY 1;"
        legacy_grants = cls.sql(grants_query)
        for migration in cls.pending:
            cls.sql(migration.read_text())
        if cls.sql(grants_query) != legacy_grants:
            raise AssertionError('Migration changed old client permissions')
        cls.sql("NOTIFY pgrst, 'reload schema';")
        for _ in range(80):
            if cls.request('GET', '/uploaded_games_current')[0] == 200:
                break
            time.sleep(0.25)
        else:
            raise RuntimeError('PostgREST schema cache was not refreshed')

    @classmethod
    def sql(cls, statement):
        return docker('exec', '-i', cls.db, 'psql', '-U', 'postgres', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', sql=statement)

    @classmethod
    def request(cls, method, path, body=None, owner=OWNER, prefer=None):
        headers = {'Content-Type': 'application/json'}
        if owner:
            encode = lambda value: base64.urlsafe_b64encode(json.dumps(value).encode()).rstrip(b'=')
            message = encode({'alg': 'HS256', 'typ': 'JWT'}) + b'.' + encode({'role': 'authenticated', 'sub': owner, 'exp': int(time.time()) + 300})
            signature = base64.urlsafe_b64encode(hmac.new(cls.secret.hex().encode(), message, hashlib.sha256).digest()).rstrip(b'=')
            headers['Authorization'] = 'Bearer ' + (message + b'.' + signature).decode()
        if prefer:
            headers['Prefer'] = prefer
        request = Request(cls.base + path, data=None if body is None else json.dumps(body).encode(), method=method, headers=headers)
        try:
            response = urlopen(request, timeout=10)
        except HTTPError as error:
            response = error
        with response:
            payload = response.read()
            return response.status, json.loads(payload) if payload else None

    @staticmethod
    def game(key):
        return {'user_id': OWNER, 'content_hash': key, 'moves_usi': ['7g7f'], 'private_enc': 'old-ciphertext', 'analysis_json': []}

    @staticmethod
    def problem(key):
        return {'user_id': OWNER, 'content_hash': key, 'ply': 1, 'side': 'sente', 'sfen_before': 'startpos', 'move_usi': '7g7f', 'best_usi': '2g2f', 'loss_wp': 0.3, 'category': 'test', 'verdict': 'TARGET', 'note': '', 'problem_type': 'test', 'priority': 1}

    def test_old_api_after_migration_and_new_snapshot_isolation(self):
        key = self.preexisting
        self.assertEqual(1, len(self.request('GET', f'/uploaded_games_current?content_hash=eq.{key}')[1]))
        self.assertEqual(409, self.request('POST', '/uploaded_games', self.game(key))[0])
        problem_path = '/drill_problems?on_conflict=user_id,content_hash,ply'
        for _ in range(2):
            self.assertEqual(201, self.request('POST', problem_path, [self.problem(key)], prefer='resolution=ignore-duplicates')[0])
        problem_id = self.request('GET', f'/drill_problems?content_hash=eq.{key}')[1][0]['id']
        answer = {'user_id': OWNER, 'problem_id': problem_id, 'client_attempt_id': '20000000-0000-0000-0000-000000000001', 'user_move_usi': '7g7f', 'is_correct': False, 'attempted_at': '2026-01-01T00:00:00Z'}
        for _ in range(2):
            self.assertEqual(201, self.request('POST', '/drill_attempts?on_conflict=user_id,client_attempt_id', answer, prefer='resolution=ignore-duplicates')[0])
        generation = '30000000-0000-0000-0000-000000000001'
        payload = {'p_content_hash': key, 'p_generation': generation, 'p_expected_generation': None, 'p_game': {**self.game(key), 'private_enc': 'new-ciphertext'}, 'p_problems': [self.problem(key)]}
        status, result = self.request('POST', '/rpc/replace_analysis_generation', payload)
        self.assertEqual((200, 'applied'), (status, result.get('status')))
        self.assertEqual('already_applied', self.request('POST', '/rpc/replace_analysis_generation', payload)[1]['status'])
        self.assertEqual('new-ciphertext', self.request('GET', f'/uploaded_games_current?content_hash=eq.{key}')[1][0]['private_enc'])
        self.assertEqual('old-ciphertext', self.request('GET', f'/uploaded_games?content_hash=eq.{key}')[1][0]['private_enc'])
        self.assertEqual(1, len(self.request('GET', '/drill_attempts')[1]))
        self.assertEqual([], self.request('GET', '/drill_attempts_v2')[1])
        self.assertEqual(204, self.request('DELETE', f'/uploaded_games?content_hash=eq.{key}')[0])
        self.assertEqual([], self.request('GET', '/drill_attempts')[1])
        self.assertEqual(generation, self.request('GET', f'/uploaded_games_current?content_hash=eq.{key}')[1][0]['analysis_generation'])
        self.assertEqual(201, self.request('POST', '/uploaded_games', self.game(key))[0])
        deleted = {'p_content_hash': key, 'p_expected_generation': generation, 'p_delete_generation': '30000000-0000-0000-0000-000000000002', 'p_expected_user_id': OWNER}
        self.assertEqual((200, True), self.request('POST', '/rpc/delete_analysis_generation', deleted))
        self.assertEqual([], self.request('GET', f'/uploaded_games_current?content_hash=eq.{key}')[1])
        self.assertEqual(1, len(self.request('GET', f'/uploaded_games?content_hash=eq.{key}')[1]))

    def test_study_copy_and_owner_permissions(self):
        key = 'b' * 64
        self.assertEqual(201, self.request('POST', '/uploaded_games', self.game(key))[0])
        payload = {'p_content_hash': key, 'p_expected_private_enc': 'old-ciphertext', 'p_private_enc': 'new-study'}
        self.assertEqual((200, False), self.request('POST', '/rpc/update_study_private_enc', payload, owner=OTHER))
        self.assertEqual((200, True), self.request('POST', '/rpc/update_study_private_enc', payload))
        self.assertEqual((200, False), self.request('POST', '/rpc/update_study_private_enc', payload))
        self.assertEqual('old-ciphertext', self.request('GET', f'/uploaded_games?content_hash=eq.{key}')[1][0]['private_enc'])
        self.assertEqual('new-study', self.request('GET', f'/uploaded_games_current?content_hash=eq.{key}')[1][0]['private_enc'])
        self.assertEqual([], self.request('GET', f'/uploaded_games_current?content_hash=eq.{key}', owner=OTHER)[1])
        self.assertEqual(403, self.request('POST', '/uploaded_games_v2', self.game(key))[0])
        self.assertEqual(403, self.request('PATCH', f'/uploaded_games?content_hash=eq.{key}', {'private_enc': 'bad'})[0])
        self.assertEqual(401, self.request('GET', '/uploaded_games_current', owner=None)[0])


if __name__ == '__main__':
    unittest.main()
