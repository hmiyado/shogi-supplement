CREATE EXTENSION IF NOT EXISTS pgtap WITH SCHEMA extensions;
SELECT plan(13);
INSERT INTO auth.users(id, instance_id, aud, role) VALUES
('50000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000000','authenticated','authenticated'),
('50000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000000','authenticated','authenticated');
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claims','{"sub":"50000000-0000-0000-0000-000000000001","role":"authenticated"}',true);
SELECT lives_ok($$INSERT INTO public.uploaded_games(user_id,content_hash,moves_usi,private_enc)
VALUES ('50000000-0000-0000-0000-000000000001',repeat('d',64),'["7g7f"]','old-cipher')$$,'legacy upload remains writable');
SELECT is((SELECT count(*)::int FROM public.uploaded_games_current WHERE content_hash=repeat('d',64)),1,'current restore view includes legacy upload');
SELECT ok(public.update_study_private_enc(repeat('d',64),'old-cipher','new-cipher'),'study CAS accepts the current cipher');
SELECT ok(NOT public.update_study_private_enc(repeat('d',64),'old-cipher','stale-cipher'),'study CAS rejects stale cipher');
SELECT is((SELECT private_enc FROM public.uploaded_games WHERE content_hash=repeat('d',64)),'old-cipher','legacy encrypted payload remains untouched');
SELECT is((SELECT private_enc FROM public.uploaded_games_current WHERE content_hash=repeat('d',64)),'new-cipher','restore reads updated encrypted payload');
SELECT is(public.replace_analysis_generation(repeat('e',64),'60000000-0000-0000-0000-000000000001',null,
'{"user_id":"50000000-0000-0000-0000-000000000001","moves_usi":["7g7f"],"private_enc":"generation-cipher"}'::jsonb,'[]'::jsonb)->>'status','applied','current analysis upload RPC works');
SELECT is(public.get_analysis_sync_state(repeat('e',64))->>'generation','60000000-0000-0000-0000-000000000001','generation can be restored');
SELECT is((SELECT private_enc FROM public.uploaded_games_current WHERE content_hash=repeat('e',64)),'generation-cipher','current generation encrypted payload is preserved');
SELECT ok(public.delete_analysis_generation(repeat('e',64),'60000000-0000-0000-0000-000000000001','60000000-0000-0000-0000-000000000002','50000000-0000-0000-0000-000000000001'),'current delete RPC works');
SELECT is(public.get_analysis_sync_state(repeat('e',64))->>'deleted','true','current delete tombstone remains readable');
SELECT set_config('request.jwt.claims','{"sub":"50000000-0000-0000-0000-000000000002","role":"authenticated"}',true);
SELECT is((SELECT count(*)::int FROM public.uploaded_games_current WHERE content_hash=repeat('d',64)),0,'another account cannot restore owner data');
SELECT ok(NOT public.update_study_private_enc(repeat('d',64),'new-cipher','foreign-cipher'),'another account cannot edit the study');
SELECT * FROM finish();
