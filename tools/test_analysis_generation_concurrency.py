#!/usr/bin/env python3
"""Exercise the real migration in a disposable, Unix-socket-only PostgreSQL cluster."""
import concurrent.futures
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
PG_BIN = Path(os.environ.get('PG_BIN', '/opt/homebrew/opt/postgresql@17/bin'))
OWNER = '10000000-0000-0000-0000-000000000001'
HASH = 'a' * 64
GAME = '{"user_id":"10000000-0000-0000-0000-000000000001","moves_usi":["7g7f"],"private_enc":"original","analysis_json":[]}'
PROBLEMS = '[{"ply":1,"side":"sente","sfen_before":"startpos","move_usi":"7g7f","best_usi":"2g2f","loss_wp":0.3,"category":"test","verdict":"TARGET","note":"","problem_type":"test","priority":1}]'


class AnalysisGenerationConcurrencyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.directory = Path(tempfile.mkdtemp(prefix='analysis-generation-pg-'))
        cls.data = cls.directory / 'data'
        subprocess.run([PG_BIN / 'initdb', '-D', cls.data, '-A', 'trust', '--no-locale'],
                       check=True, capture_output=True, text=True)
        subprocess.run([PG_BIN / 'pg_ctl', '-D', cls.data, '-l', cls.directory / 'postgres.log',
                        '-o', f'-k {cls.directory} -p 55490 -h ""', 'start'],
                       check=True, capture_output=True, text=True)
        cls.addClassCleanup(cls.stop)
        # Include the exact migration through the sequential fixture, preserving its assertions.
        fixture = (ROOT / 'tools/tests/analysis_generation.sql').read_text()
        fixture = fixture.replace('ROLLBACK;', 'COMMIT;').replace('\\ir ../../', '\\i ' + str(ROOT) + '/')
        cls.sql(fixture)

    @classmethod
    def stop(cls):
        subprocess.run([PG_BIN / 'pg_ctl', '-D', cls.data, 'stop', '-m', 'fast'],
                       check=True, capture_output=True, text=True)
        print(f'Isolated PostgreSQL stopped; evidence retained at {cls.directory}')

    @classmethod
    def sql(cls, statement, owner=False):
        if owner:
            statement = f"SET ROLE authenticated; SET request.jwt.claim.sub = '{OWNER}';\n" + statement
        result = subprocess.run([PG_BIN / 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
                                 '-h', cls.directory, '-p', '55490', '-d', 'postgres'],
                                input=statement, text=True, capture_output=True, timeout=30)
        if result.returncode:
            raise AssertionError(result.stderr)
        return result.stdout.strip()

    @classmethod
    def replace(cls, generation, expected, problems=PROBLEMS):
        return json.loads(cls.sql(
            f"SELECT public.replace_analysis_generation('{HASH}','{generation}','{expected}',"
            f"'{GAME}'::jsonb,'{problems}'::jsonb);", owner=True))

    def test_legacy_and_current_copies_are_independent(self):
        key = 'c' * 64
        generation = 'a0000000-0000-0000-0000-000000000001'
        legacy_insert = f"INSERT INTO public.uploaded_games(user_id,content_hash,moves_usi,private_enc) VALUES ('{OWNER}','{key}','[]','legacy');"
        self.sql(legacy_insert, True)
        problem_insert = f"""INSERT INTO public.drill_problems(user_id,content_hash,ply,side,sfen_before,move_usi,best_usi,loss_wp,category,verdict,note,problem_type,priority)
            SELECT '{OWNER}','{key}',p.ply,p.side,p.sfen_before,p.move_usi,p.best_usi,p.loss_wp,p.category,p.verdict,p.note,p.problem_type,p.priority
            FROM jsonb_populate_recordset(NULL::public.drill_problems,'{PROBLEMS}') p
            ON CONFLICT(user_id,content_hash,ply) DO NOTHING;"""
        self.sql(problem_insert, True)
        attempt_insert = f"""INSERT INTO public.drill_attempts(user_id,problem_id,client_attempt_id,user_move_usi,is_correct,attempted_at)
            SELECT '{OWNER}',id,'a0000000-0000-0000-0000-000000000002','7g7f',false,now()
            FROM public.drill_problems WHERE content_hash='{key}'
            ON CONFLICT(user_id,client_attempt_id) DO NOTHING;"""
        self.sql(attempt_insert, True)
        self.assertEqual('legacy', self.sql(f"SELECT private_enc FROM public.uploaded_games_current WHERE content_hash='{key}';", True))
        result = json.loads(self.sql(f"SELECT public.replace_analysis_generation('{key}','{generation}',NULL,'{GAME}','{PROBLEMS}');", True))
        self.assertEqual('applied', result['status'])
        self.sql(problem_insert + attempt_insert, True)
        self.assertEqual('1', self.sql(f"SELECT count(*) FROM public.drill_attempts a JOIN public.drill_problems p ON a.problem_id=p.id WHERE p.content_hash='{key}';", True))
        self.assertEqual('0', self.sql(f"SELECT count(*) FROM public.drill_attempts_v2 a JOIN public.drill_problems_v2 p ON a.problem_id=p.id WHERE p.content_hash='{key}';", True))
        self.assertEqual('original', self.sql(f"SELECT private_enc FROM public.uploaded_games_current WHERE content_hash='{key}';", True))
        self.sql(f"DELETE FROM public.uploaded_games WHERE content_hash='{key}';", True)
        self.assertEqual(generation, self.sql(f"SELECT analysis_generation FROM public.uploaded_games_current WHERE content_hash='{key}';", True))
        self.sql(legacy_insert + problem_insert + attempt_insert, True)
        deleted = 'a0000000-0000-0000-0000-000000000003'
        self.assertEqual('t', self.sql(f"SELECT public.delete_analysis_generation('{key}','{generation}','{deleted}','{OWNER}');", True))
        self.assertEqual('1', self.sql(f"SELECT count(*) FROM public.uploaded_games WHERE content_hash='{key}';", True))
        self.assertEqual('0', self.sql(f"SELECT count(*) FROM public.uploaded_games_current WHERE content_hash='{key}';", True))
        self.sql(f"DELETE FROM public.uploaded_games WHERE content_hash='{key}';" + legacy_insert, True)
        self.assertEqual('0', self.sql(f"SELECT count(*) FROM public.uploaded_games_current WHERE content_hash='{key}';", True))

    def test_study_copy_cas_and_permissions(self):
        fixture = (ROOT / 'tools/tests/study_private_enc.sql').read_text()
        self.sql(fixture)

    def test_new_tables_keep_limits_and_account_cascades(self):
        self.sql("""
BEGIN;
INSERT INTO auth.users VALUES ('b0000000-0000-0000-0000-000000000001');
INSERT INTO public.uploaded_games_v2(user_id,content_hash,moves_usi)
SELECT 'b0000000-0000-0000-0000-000000000001', lpad(to_hex(n),64,'0'), '[]'::jsonb FROM generate_series(1,50) n;
DO $$ DECLARE rejected boolean := false; BEGIN
    BEGIN
        INSERT INTO public.uploaded_games_v2(user_id,content_hash,moves_usi)
        VALUES ('b0000000-0000-0000-0000-000000000001',repeat('f',64),'[]');
    EXCEPTION WHEN raise_exception THEN rejected := true; END;
    IF NOT rejected THEN RAISE EXCEPTION 'game daily limit bypassed'; END IF;
END $$;
INSERT INTO public.drill_problems_v2(user_id,content_hash,ply,side,sfen_before,move_usi,loss_wp,category,verdict,note,problem_type,priority)
SELECT 'b0000000-0000-0000-0000-000000000001',lpad('1',64,'0'),n,'sente','startpos','7g7f',0.3,'test','TARGET','','test',1 FROM generate_series(1,500) n;
DO $$ DECLARE rejected boolean := false; BEGIN
    BEGIN
        INSERT INTO public.drill_problems_v2(user_id,content_hash,ply,side,sfen_before,move_usi,loss_wp,category,verdict,note,problem_type,priority)
        VALUES ('b0000000-0000-0000-0000-000000000001',lpad('1',64,'0'),501,'sente','startpos','7g7f',0.3,'test','TARGET','','test',1);
    EXCEPTION WHEN raise_exception THEN rejected := true; END;
    IF NOT rejected THEN RAISE EXCEPTION 'problem daily limit bypassed'; END IF;
END $$;
INSERT INTO public.drill_attempts_v2(user_id,problem_id,client_attempt_id,user_move_usi,is_correct,attempted_at)
SELECT 'b0000000-0000-0000-0000-000000000001',id,gen_random_uuid(),'7g7f',true,now() FROM public.drill_problems_v2 WHERE user_id='b0000000-0000-0000-0000-000000000001';
DO $$ DECLARE rejected boolean := false; BEGIN
    BEGIN
        INSERT INTO public.drill_attempts_v2(user_id,problem_id,client_attempt_id,user_move_usi,is_correct,attempted_at)
        SELECT 'b0000000-0000-0000-0000-000000000001',id,gen_random_uuid(),'7g7f',true,now() FROM public.drill_problems_v2 WHERE user_id='b0000000-0000-0000-0000-000000000001' LIMIT 1;
    EXCEPTION WHEN raise_exception THEN rejected := true; END;
    IF NOT rejected THEN RAISE EXCEPTION 'answer daily limit bypassed'; END IF;
END $$;
INSERT INTO public.analysis_generation_receipts VALUES ('b0000000-0000-0000-0000-000000000001',repeat('a',64),gen_random_uuid(),'x',true);
INSERT INTO public.deleted_analysis_generations VALUES ('b0000000-0000-0000-0000-000000000001',repeat('a',64),gen_random_uuid());
DELETE FROM auth.users WHERE id='b0000000-0000-0000-0000-000000000001';
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM public.uploaded_games_v2 WHERE user_id='b0000000-0000-0000-0000-000000000001')
       OR EXISTS (SELECT 1 FROM public.drill_problems_v2 WHERE user_id='b0000000-0000-0000-0000-000000000001')
       OR EXISTS (SELECT 1 FROM public.drill_attempts_v2 WHERE user_id='b0000000-0000-0000-0000-000000000001')
       OR EXISTS (SELECT 1 FROM public.analysis_generation_receipts WHERE user_id='b0000000-0000-0000-0000-000000000001')
       OR EXISTS (SELECT 1 FROM public.deleted_analysis_generations WHERE user_id='b0000000-0000-0000-0000-000000000001')
    THEN RAISE EXCEPTION 'account deletion left data'; END IF;
END $$;
ROLLBACK;
""")

    def test_parallel_replacement_replay_and_delete(self):
        old = '20000000-0000-0000-0000-000000000003'
        candidates = [f'40000000-0000-0000-0000-{i:012d}' for i in range(8)]
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(lambda generation: self.replace(generation, old), candidates))
        self.assertEqual(1, sum(r['status'] == 'applied' for r in results))
        self.assertEqual(7, sum(r['status'] == 'conflict' for r in results))
        winner = next(r['generation'] for r in results if r['status'] == 'applied')
        generation = '50000000-0000-0000-0000-000000000001'
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(lambda _: self.replace(generation, winner), range(8)))
        self.assertEqual(1, sum(r['status'] == 'applied' for r in results))
        self.assertEqual(7, sum(r['status'] == 'already_applied' for r in results))
        answer = """jsonb_build_object('user_id','10000000-0000-0000-0000-000000000001','client_attempt_id','60000000-0000-0000-0000-000000000001',
            'user_move_usi','2g2f','is_correct',true,'attempted_at',now())"""
        self.assertEqual('t', self.sql(f"SELECT public.record_generation_attempt('{HASH}', '{generation}', 1, {answer});", True))
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(lambda _: self.replace(generation, winner), range(8)))
        self.assertTrue(all(r['status'] == 'already_applied' for r in results))
        self.assertEqual('1', self.sql('SELECT count(*) FROM public.drill_attempts_v2;', True))
        # A recorded generation cannot recreate a deleted game, even when its initial reply was lost.
        deleted = '70000000-0000-0000-0000-000000000001'
        self.assertEqual('t', self.sql(f"SELECT public.delete_analysis_generation('{HASH}','{generation}','{deleted}','{OWNER}');", True))
        self.assertEqual('superseded', self.replace(generation, winner)['status'])
        self.assertEqual('0', self.sql('SELECT count(*) FROM public.uploaded_games_v2;', True))
        self.assertEqual('0', self.sql('SELECT count(*) FROM public.drill_attempts_v2;', True))
        self.assertEqual('t', self.sql(f"SELECT public.delete_analysis_generation('{HASH}','{generation}','{deleted}','{OWNER}');", True))
        # An unsent generation prepared before deletion cannot silently recreate the game.
        self.assertEqual('conflict', self.replace('80000000-0000-0000-0000-000000000003', generation)['status'])
        self.assertEqual('conflict', json.loads(self.sql(
            f"SELECT public.replace_analysis_generation('{HASH}','80000000-0000-0000-0000-000000000001',NULL,'{GAME}','[]');", True))['status'])
        # Explicit recreation is possible only with the observed deletion generation.
        self.assertEqual('applied', self.replace('80000000-0000-0000-0000-000000000002', deleted)['status'])

    def test_legacy_delete_holds_off_initial_upload(self):
        legacy_hash = 'b' * 64
        self.sql(f"INSERT INTO public.uploaded_games(user_id,content_hash,moves_usi) VALUES ('{OWNER}','{legacy_hash}','[]');")
        delete_generation = '90000000-0000-0000-0000-000000000001'
        command = [PG_BIN / 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
                   '-h', self.directory, '-p', '55490', '-d', 'postgres']
        with subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, text=True) as deleting:
            deleting.stdin.write(f"""BEGIN;
SET ROLE authenticated;
SET request.jwt.claim.sub = '{OWNER}';
SELECT public.delete_analysis_generation('{legacy_hash}',NULL,'{delete_generation}','{OWNER}');
SELECT pg_sleep(1);
COMMIT;
""")
            deleting.stdin.flush()
            self.assertEqual('t', deleting.stdout.readline().strip())
            # The deletion transaction is now live and still holding the shared lock.
            result = json.loads(self.sql(
                f"SELECT public.replace_analysis_generation('{legacy_hash}',"
                f"'90000000-0000-0000-0000-000000000002',NULL,'{GAME}','[]');", True))
            _, error = deleting.communicate(timeout=5)
            self.assertEqual(0, deleting.returncode, error)
        self.assertEqual('conflict', result['status'])
        self.assertEqual(delete_generation, result['generation'])
        self.assertEqual('1', self.sql(f"SELECT count(*) FROM public.uploaded_games WHERE content_hash='{legacy_hash}';"))
        self.assertEqual('0', self.sql(f"SELECT count(*) FROM public.uploaded_games_current WHERE content_hash='{legacy_hash}';", True))

    def test_z_concurrent_old_and_new_uploads_do_not_mix_problems(self):
        key = 'e' * 64
        def send_old(_):
            self.sql(f"""INSERT INTO public.uploaded_games(user_id,content_hash,moves_usi)
                VALUES ('{OWNER}','{key}','[]') ON CONFLICT(user_id,content_hash) DO NOTHING;
                INSERT INTO public.drill_problems(user_id,content_hash,ply,side,sfen_before,move_usi,best_usi,loss_wp,category,verdict,note,problem_type,priority)
                VALUES ('{OWNER}','{key}',1,'sente','startpos','7g7f','7g7f',0.3,'old','TARGET','','old',1)
                ON CONFLICT(user_id,content_hash,ply) DO NOTHING;""", True)
        def send_new(index):
            return json.loads(self.sql(f"SELECT public.replace_analysis_generation('{key}','c0000000-0000-0000-0000-{index:012d}',NULL,'{GAME}','{PROBLEMS}');", True))
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            old = [pool.submit(send_old, i) for i in range(4)]
            new = [pool.submit(send_new, i) for i in range(4)]
            for result in old:
                result.result()
            results = [result.result() for result in new]
        self.assertEqual(1, sum(result['status'] == 'applied' for result in results))
        self.assertEqual(3, sum(result['status'] == 'conflict' for result in results))
        self.assertEqual('7g7f', self.sql(f"SELECT best_usi FROM public.drill_problems WHERE content_hash='{key}';", True))
        self.assertEqual('2g2f', self.sql(f"SELECT best_usi FROM public.drill_problems_v2 WHERE content_hash='{key}';", True))


if __name__ == '__main__':
    unittest.main()
