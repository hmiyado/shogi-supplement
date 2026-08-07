-- POST /v1/transfer（server/worker）がkey_auth_hashでuser_transfer_secretsを引くために必要。
-- service_roleへのgrantが漏れていた（RLSはBYPASSRLS属性で回避されるが、テーブルGRANT
-- 自体は別物で回避されない。従来はauthenticated=書き込み元のクライアントのみgrant済みだった）。
grant select on public.user_transfer_secrets to service_role;
