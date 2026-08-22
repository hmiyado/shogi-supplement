-- 棋譜削除機能（アプリ側）でユーザーが自分のアップロード済み棋譜を削除できるようにする。
-- RLSポリシー"own rows"（20260726120000）は for all で既にDELETEをUSING句でカバーしているため、
-- 新しいポリシーは不要。20260726120000時点では削除機能自体が無かったためGRANTで塞いでいたDELETEを、
-- ここで解禁する。
grant delete on public.uploaded_games to authenticated;
