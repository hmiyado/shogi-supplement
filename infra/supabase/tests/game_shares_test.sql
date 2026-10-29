begin;
create extension if not exists pgtap with schema extensions;
select plan(16);
select ok((select public from storage.buckets where id='game-shares'),'public static bucket');
select ok(not has_table_privilege('anon','public.game_share_objects','select'),'anonymous cannot enumerate mappings');
select ok(not has_table_privilege('authenticated','public.game_share_objects','select'),'owners cannot enumerate mappings');
select ok(not has_function_privilege('authenticated','public.reserve_game_share(text,uuid,text)','execute'),'reservation is server only');
select ok(not has_function_privilege('anon','public.finish_game_share(text,uuid)','execute'),'publication is server only');
insert into auth.users(id) values('50000000-0000-0000-0000-000000000001');
select ok(not public.reserve_game_share('MissingSource1234567890','50000000-0000-0000-0000-000000000001','absent'),'missing source cannot publish');
insert into public.uploaded_games_v2(user_id,content_hash,moves_usi)
values('50000000-0000-0000-0000-000000000001','v2-source','[]');
select ok(public.reserve_game_share('SourceV2AbCdEfGhIjKlMn0','50000000-0000-0000-0000-000000000001','v2-source'),'reserve synchronized v2 game');
delete from public.uploaded_games_v2 where content_hash='v2-source';
select is((select count(*)::int from public.game_share_deletions where object_id='SourceV2AbCdEfGhIjKlMn0'),1,'source deletion enqueues storage deletion');
select ok(not public.finish_game_share('SourceV2AbCdEfGhIjKlMn0','50000000-0000-0000-0000-000000000001'),'deleted source cannot finish publishing');
insert into public.uploaded_games(user_id,content_hash,moves_usi)
values('50000000-0000-0000-0000-000000000001','legacy-source','[]');
select ok(public.reserve_game_share('LegacyAbCdEfGhIjKlMnOp0','50000000-0000-0000-0000-000000000001','legacy-source'),'legacy source reservation remains supported');
insert into public.uploaded_games(user_id,content_hash,moves_usi)
values('50000000-0000-0000-0000-000000000001',repeat('f',64),'[]');
select ok(public.reserve_game_share('LogicalDeleteAbCdEfGhIj0','50000000-0000-0000-0000-000000000001',repeat('f',64)),'legacy game can publish before logical deletion');
set local role authenticated;
select set_config('request.jwt.claims','{"sub":"50000000-0000-0000-0000-000000000001","role":"authenticated"}',true);
select ok(public.delete_analysis_generation(repeat('f',64),null,'50000000-0000-0000-0000-000000000004','50000000-0000-0000-0000-000000000001'),'application deletion RPC accepts legacy game');
reset role;
select is((select count(*)::int from public.game_share_objects where id='LogicalDeleteAbCdEfGhIj0'),0,'application deletion removes mapping for retained legacy row');
select ok(not public.reserve_game_share('DeletedLegacyAbCdEfGhI0','50000000-0000-0000-0000-000000000001',repeat('f',64)),'hidden legacy data cannot be shared after application deletion');
insert into public.game_share_objects(id,owner_id,content_hash,source_table)
values('AbCdEfGhIjKlMnOpQrStUv','50000000-0000-0000-0000-000000000001','test','uploaded_games_v2');
delete from auth.users where id='50000000-0000-0000-0000-000000000001';
select is((select count(*)::int from public.game_share_objects where owner_id='50000000-0000-0000-0000-000000000001'),0,'account cascade removes mappings');
select is((select count(*)::int from public.game_share_deletions where object_id='AbCdEfGhIjKlMnOpQrStUv'),1,'durable tombstone survives account deletion');
select * from finish();
rollback;
