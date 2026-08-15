-- 引き継ぎコードの再生成に必要な update。作成時は「アカウント削除→作り直し」で割り切って
-- いたが、漏れたコードを無効化する手段としては代償が大きすぎる。
--
-- 復号鍵の導出元は端末に残したまま認証用のシークレットだけを引き直すため、この行が持つ
-- ハッシュを差し替えられれば足りる。行の追加・削除は従来どおり増やさない。
create policy "user_transfer_secrets_update_own"
  on public.user_transfer_secrets for update
  to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

grant update on public.user_transfer_secrets to authenticated;
