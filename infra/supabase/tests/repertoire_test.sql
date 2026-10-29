begin;
create extension if not exists pgtap with schema extensions;
select plan(8);
insert into auth.users(id, instance_id, aud, role) values
('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated'),
('40000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated');
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"40000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select ok(public.put_repertoire_entry('40000000-0000-0000-0000-000000000001', 'one', 'line', null, repeat('a',64), 'encrypted'), 'insert own entry');
select ok(public.put_repertoire_entry('40000000-0000-0000-0000-000000000001', 'one', 'line', null, repeat('a',64), 'retry'), 'same revision retry succeeds');
select is((select payload_enc from public.repertoire_entries where id = 'one'), 'encrypted', 'retry does not replace payload');
select ok(not public.put_repertoire_entry('40000000-0000-0000-0000-000000000001', 'one', 'line', null, repeat('b',64), 'stale'), 'stale writer rejected');
select ok(public.put_repertoire_entry('40000000-0000-0000-0000-000000000001', 'one', 'line', repeat('a',64), repeat('b',64), 'new'), 'matching base updates');
select set_config('request.jwt.claims', '{"sub":"40000000-0000-0000-0000-000000000002","role":"authenticated"}', true);
select is((select count(*)::int from public.repertoire_entries where id = 'one'), 0, 'other owner cannot read');
select throws_ok($$select public.put_repertoire_entry('40000000-0000-0000-0000-000000000001', 'one', 'line', repeat('b',64), repeat('c',64), 'attack')$$, '42501', 'unauthorized', 'owner spoof denied');
reset role;
delete from auth.users where id = '40000000-0000-0000-0000-000000000001';
select is((select count(*)::int from public.repertoire_entries where id = 'one'), 0, 'account deletion removes entries');
select * from finish();
rollback;
