-- 強制アップデートの検証用「-dev行」（android-dev/ios-dev）を追加する。
-- min_build判定の動作確認に本番行（android/ios）を動かすと実ユーザーへ影響するため、
-- Debugビルド専用の行を別途用意し、判定ロジック自体は本番と共通のまま検証できるようにする。

alter table public.app_policy
  drop constraint app_policy_platform_check;

alter table public.app_policy
  add constraint app_policy_platform_check
  check (platform in ('android', 'ios', 'common', 'android-dev', 'ios-dev'));

-- 初期値: 本番のandroid/ios行の初期投入（20260803120000）と同じ方針でminBuild=1
-- （誰もブロックしない）。ストアURL・messageは検証用途のため空のまま。
insert into public.app_policy (platform, min_build, store_url, message)
values
  ('android-dev', 1, '', null),
  ('ios-dev', 1, '', null)
on conflict (platform) do nothing;
