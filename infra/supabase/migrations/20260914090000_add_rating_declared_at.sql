-- 取込時点でユーザーが申告していた棋力設定の保存日時。
-- NULLは旧データまたは未申告、時刻はUTCのtimestamptzとして保持する。
ALTER TABLE public.uploaded_games
  ADD COLUMN rating_declared_at timestamptz;
