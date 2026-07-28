-- analysis-worker専用テーブル（クライアント不可視）。
-- Why not RLSポリシーを作る: service_roleはRLSをバイパスするためワーカーからは読み書きでき、
-- anon/authenticatedからは行が一切見えない。アプリはワーカー経由でしかここへ触れないので、
-- 行単位のアクセス制御を組む相手がいない。

create table analysis_jobs (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users(id) on delete cascade,
  moves_hash    text not null,                   -- USI手列のSHA-256（冪等キー）
  -- Why not NOT NULL: TTLスイープが7日経過後にNULL化する対象。
  moves_usi     jsonb,
  status        text not null default 'running', -- running|done|error
  result_json   jsonb,                           -- 局面ごとのMultiPV結果（PvInfo互換。TTL対象）
  engine_meta   jsonb,                           -- engine_rev/eval_sha256/解析条件（TTL対象外）
  error         text,
  created_at    timestamptz not null default now(),
  finished_at   timestamptz,
  unique (user_id, moves_hash)
);
alter table analysis_jobs enable row level security;

-- Why not unique(user_id, moves_hash)のインデックスで足りるか: あれはmoves_hash完全一致に
-- しか効かない。クォータ判定は当日分をcreated_atの範囲で数えるため別途必要。
create index analysis_jobs_user_id_created_at_idx
  on analysis_jobs (user_id, created_at);

create table user_bans (
  user_id    uuid primary key references auth.users(id) on delete cascade,
  reason     text,
  created_at timestamptz not null default now()
);
alter table user_bans enable row level security;

create table quota_limits (
  user_id     uuid primary key references auth.users(id) on delete cascade,
  daily_limit int not null default 30
);
alter table quota_limits enable row level security;

-- TTL: finished_atから7日経過したジョブの棋譜本体と解析結果をNULL化する。
-- Why not 行ごと削除する: クォータ集計と計測に使い続けるため。消すのは棋譜だけでよい。
create extension if not exists pg_cron;

-- security definer + search_path固定: スケジュール実行者の権限と検索パスに依存させず、
-- 対象テーブルを常にpublicスキーマで解決させる。
create or replace function analysis_jobs_ttl_sweep()
returns void
language sql
security definer
set search_path = ''
as $$
  update public.analysis_jobs
  set moves_usi = null,
      result_json = null
  where finished_at < now() - interval '7 days'
    and (moves_usi is not null or result_json is not null);
$$;

select cron.schedule(
  'analysis_jobs_ttl_sweep',
  '0 3 * * *',
  $$select public.analysis_jobs_ttl_sweep();$$
);

-- ── uploaded_games（データ提供・v2構造化形式）─────────────────────────────
-- KIF原文（kif_text）は保存しない。平文は構造化フィールドのみで、
-- 対局者名等の秘匿情報はクライアントが暗号化したprivate_encに載る
-- （形式はPrivateEncCodec、鍵導出はTransferSecretKeysが正）。
create table public.uploaded_games (
  id                  uuid primary key default gen_random_uuid(),
  user_id             uuid not null references auth.users(id) on delete cascade,
  content_hash        text not null,
  moves_usi           jsonb not null,
  -- 1手ごとの消費秒（null要素=テンポ不明局面）
  move_times          jsonb,
  -- ホワイトリスト済みヘッダのみ（KifuDecomposer.HEADER_WHITELIST）
  headers             jsonb,
  -- 終局理由（投了・時間切れ等）
  result              text,
  -- 出典サービスの正規化値のみ（KifuSource.wireValue。対局URL等はprivate_enc側）
  source_place        text,
  -- アップロードしたユーザーが指した側（sente/gote。null=未申告）。
  -- 対局者名は平文に持たないため、この列が無いと申告レートをどちらの側の
  -- 成績と対応付けるか判別できない
  side                text,
  -- version(1B)+nonce(12B)+AES-256-GCM暗号文のBase64。
  -- Why not bytea: PostgRESTのbytea往復（hex表現）の実環境検証が済むまで、
  -- クライアント側だけで完結するtext+Base64を使う。数百バイト/局なので冗長化は無視できる
  private_enc         text,
  -- ユーザーの申告棋力（サービス名・サービス上のraw値・ルール）。較正データの中核軸
  rating_service      text,
  rating_raw          integer,
  rating_rule         text,
  -- KIF記載の段級。headersの先手段級/後手段級をside基準でユーザー側/相手側に
  -- 割り付けた検索用の複製（headersが正本）
  user_rank           text,
  opponent_rank       text,
  -- 対局開始日時（分丸め済み・JSTとして解釈）。headersの開始日時の検索用複製
  started_at          timestamptz,
  -- 時間設定（headersの持ち時間・秒読みの検索用複製。時間設定は棋力統計の主要な交絡軸）
  time_control        text,
  byoyomi             text,
  -- 解析からの推定棋力（申告のrating_rawとは別物）
  estimated_rating    integer,
  rating_sample_moves integer,
  move_count          integer,
  coef_version        text,
  analysis_json       jsonb,
  created_at          timestamptz not null default now(),
  unique (user_id, content_hash),
  -- 行サイズの安全弁。アップロードはRLS越しの直接insertで、ワーカーのような
  -- サーバー側検証を通らないため、異常な巨大行はDB制約で弾く
  -- （正常値は moves_usi ~1KB・analysis_json 数KB・private_enc ~1KB）
  constraint uploaded_games_size_limits check (
    pg_column_size(moves_usi) <= 51200
    and (move_times is null or pg_column_size(move_times) <= 51200)
    and (headers is null or pg_column_size(headers) <= 10240)
    and (analysis_json is null or pg_column_size(analysis_json) <= 262144)
    and (private_enc is null or length(private_enc) <= 65536)
  )
);
alter table public.uploaded_games enable row level security;

-- 日次insert上限の照会用（トリガーが毎insertで数えるため）
create index uploaded_games_user_created_idx
  on public.uploaded_games (user_id, created_at);

create policy "own rows"
  on public.uploaded_games for all
  to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

-- 1ユーザーあたりの日次アップロード上限（JST日界・50行/日）。
-- アップロード経路はApp Check・ワーカークォータの守備範囲外（Supabase直）で、
-- RLSは他人のデータを守るがストレージの埋め尽くしは防げないため、DB側で塞ぐ。
-- 上限値は解析クォータ（30局/日）に再送・重複ぶんの余裕を足した値。
create or replace function public.uploaded_games_daily_limit()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if (
    select count(*)
    from public.uploaded_games
    where user_id = new.user_id
      and created_at >= (date_trunc('day', now() at time zone 'Asia/Tokyo') at time zone 'Asia/Tokyo')
  ) >= 50 then
    raise exception 'daily upload limit reached';
  end if;
  return new;
end;
$$;

create trigger uploaded_games_daily_limit
  before insert on public.uploaded_games
  for each row execute function public.uploaded_games_daily_limit();

-- ── アカウント削除RPC ────────────────────────────────────────────────────
-- 本人のauth.users行を削除する（uploaded_games等はon delete cascadeで消える）。
-- security definer: authenticatedロールはauth.usersを直接削除できないため。
create or replace function public.delete_user()
returns void
language sql
security definer
set search_path = ''
as $$
  delete from auth.users where id = auth.uid();
$$;

-- ── 引き継ぎコード（K_authハッシュのみ保管）──────────────────────────────
-- サーバーはK_authそのものではなくSHA-256ハッシュのみを保存する。user_id単位で1行。
-- Why not service_role専用（ポリシー無し）: この行は初回起動時にアプリ自身が
-- 登録する経路が必要なため、本人の行に限定したinsert/selectを許可する。
-- ローテーション（update）は提供しない（ベータはアカウント削除→作り直しで割り切る）。
create table public.user_transfer_secrets (
  user_id       uuid primary key references auth.users(id) on delete cascade,
  key_auth_hash text not null,
  created_at    timestamptz not null default now()
);
alter table public.user_transfer_secrets enable row level security;

create policy "user_transfer_secrets_insert_own"
  on public.user_transfer_secrets for insert
  to authenticated
  with check (auth.uid() = user_id);

create policy "user_transfer_secrets_select_own"
  on public.user_transfer_secrets for select
  to authenticated
  using (auth.uid() = user_id);

-- ── 権限（明示GRANT）─────────────────────────────────────────────────────
-- Supabaseの新しい既定（publicスキーマのhardening）では、テーブルのDML権限が
-- anon/authenticated/service_roleへ自動付与されない。一方、旧既定の環境では
-- default privilegesで全権限が付く。どちらの環境でも同じ結果になるよう、
-- いったん全revokeしてから必要な最小権限だけを明示的に付与する。
revoke all on public.uploaded_games, public.user_transfer_secrets,
  public.analysis_jobs, public.user_bans, public.quota_limits
  from anon, authenticated, service_role;

-- アプリ（authenticated・RLS越し）: アップロードと自分の行の参照のみ。
-- UPDATE/DELETEは付与しない（変更・削除を提供する機能が無く、行の消去は
-- delete_userのcascadeで行われる。upsert不可の前提もこの権限が固定する）
grant select, insert on public.uploaded_games to authenticated;
grant select, insert on public.user_transfer_secrets to authenticated;

-- ワーカー（service_role・RLSバイパス）: ジョブの読み書きとBAN/クォータ参照
grant select, insert, update on public.analysis_jobs to service_role;
grant select on public.user_bans to service_role;
grant select on public.quota_limits to service_role;

-- 関数: 実行権を必要な相手だけに絞る。
-- Why not `from public`だけ: default privilegesが各ロールへ明示付与する環境では
-- PUBLICからのrevokeでは剥がれないため、ロールも列挙して両方の環境で同じ結果にする
revoke execute on function public.delete_user()
  from public, anon, authenticated, service_role;
grant execute on function public.delete_user() to authenticated;
revoke execute on function public.analysis_jobs_ttl_sweep()
  from public, anon, authenticated, service_role;
revoke execute on function public.uploaded_games_daily_limit()
  from public, anon, authenticated, service_role;
