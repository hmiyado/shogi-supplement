-- 旧クライアントのテーブル・権限・外部キーを変えず、新方式の保存先を分離する。
CREATE TABLE public.uploaded_games_v2 (LIKE public.uploaded_games INCLUDING ALL);
ALTER TABLE public.uploaded_games_v2 ADD FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
CREATE TABLE public.drill_problems_v2 (LIKE public.drill_problems INCLUDING ALL);
ALTER TABLE public.drill_problems_v2 ADD FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.drill_problems_v2 ADD FOREIGN KEY (user_id, content_hash)
    REFERENCES public.uploaded_games_v2(user_id, content_hash) ON DELETE CASCADE;
CREATE TABLE public.drill_attempts_v2 (LIKE public.drill_attempts INCLUDING ALL);
ALTER TABLE public.drill_attempts_v2 ADD FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;
ALTER TABLE public.drill_attempts_v2 ADD FOREIGN KEY (problem_id) REFERENCES public.drill_problems_v2(id) ON DELETE CASCADE;

ALTER TABLE public.uploaded_games_v2 ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.drill_problems_v2 ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.drill_attempts_v2 ENABLE ROW LEVEL SECURITY;
CREATE POLICY own_rows ON public.uploaded_games_v2 FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY own_rows ON public.drill_problems_v2 FOR SELECT TO authenticated USING (user_id = auth.uid());
CREATE POLICY own_rows ON public.drill_attempts_v2 FOR SELECT TO authenticated USING (user_id = auth.uid());
REVOKE ALL ON public.uploaded_games_v2, public.drill_problems_v2, public.drill_attempts_v2 FROM PUBLIC, anon, authenticated;
GRANT SELECT ON public.uploaded_games_v2, public.drill_problems_v2, public.drill_attempts_v2 TO authenticated;

-- LIKEはトリガーを複製しない。新版にも旧版と同じ日次上限を設ける。
CREATE FUNCTION public.sync_v2_daily_limit() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    row_count bigint;
    row_limit integer;
BEGIN
    IF TG_TABLE_NAME = 'uploaded_games_v2' THEN
        IF EXISTS (SELECT 1 FROM public.uploaded_games_v2 WHERE user_id = NEW.user_id AND content_hash = NEW.content_hash) THEN RETURN NEW; END IF;
        SELECT count(*) INTO row_count FROM public.uploaded_games_v2 WHERE user_id = NEW.user_id
            AND created_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Tokyo') AT TIME ZONE 'Asia/Tokyo');
        row_limit := 50;
    ELSIF TG_TABLE_NAME = 'drill_problems_v2' THEN
        IF EXISTS (SELECT 1 FROM public.drill_problems_v2 WHERE user_id = NEW.user_id AND content_hash = NEW.content_hash AND ply = NEW.ply) THEN RETURN NEW; END IF;
        SELECT count(*) INTO row_count FROM public.drill_problems_v2 WHERE user_id = NEW.user_id
            AND created_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Tokyo') AT TIME ZONE 'Asia/Tokyo');
        row_limit := 500;
    ELSE
        IF EXISTS (SELECT 1 FROM public.drill_attempts_v2 WHERE user_id = NEW.user_id AND client_attempt_id = NEW.client_attempt_id) THEN RETURN NEW; END IF;
        SELECT count(*) INTO row_count FROM public.drill_attempts_v2 WHERE user_id = NEW.user_id
            AND created_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Tokyo') AT TIME ZONE 'Asia/Tokyo');
        row_limit := 500;
    END IF;
    IF row_count >= row_limit THEN RAISE EXCEPTION 'daily sync limit reached'; END IF;
    RETURN NEW;
END;
$$;
REVOKE ALL ON FUNCTION public.sync_v2_daily_limit() FROM PUBLIC, anon, authenticated;
CREATE TRIGGER sync_daily_limit BEFORE INSERT ON public.uploaded_games_v2 FOR EACH ROW EXECUTE FUNCTION public.sync_v2_daily_limit();
CREATE TRIGGER sync_daily_limit BEFORE INSERT ON public.drill_problems_v2 FOR EACH ROW EXECUTE FUNCTION public.sync_v2_daily_limit();
CREATE TRIGGER sync_daily_limit BEFORE INSERT ON public.drill_attempts_v2 FOR EACH ROW EXECUTE FUNCTION public.sync_v2_daily_limit();

ALTER TABLE public.uploaded_games_v2 ADD COLUMN analysis_generation uuid;

CREATE TABLE public.analysis_generation_receipts (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content_hash text NOT NULL,
    generation uuid NOT NULL,
    fingerprint bytea NOT NULL,
    initial_private_written boolean NOT NULL,
    PRIMARY KEY (user_id, content_hash, generation)
);
ALTER TABLE public.analysis_generation_receipts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.analysis_generation_receipts FROM PUBLIC, anon, authenticated;

-- 棋譜本体を残さず、削除前に作られた未適用要求を識別する。
CREATE TABLE public.deleted_analysis_generations (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    content_hash text NOT NULL,
    generation uuid NOT NULL,
    PRIMARY KEY (user_id, content_hash)
);
ALTER TABLE public.deleted_analysis_generations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.deleted_analysis_generations FROM PUBLIC, anon, authenticated;
