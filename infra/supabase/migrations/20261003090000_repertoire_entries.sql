CREATE TABLE public.repertoire_entries (
    user_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    id text NOT NULL CHECK (length(id) BETWEEN 1 AND 128),
    kind text NOT NULL CHECK (kind IN ('line', 'labels')),
    version text NOT NULL CHECK (version ~ '^[0-9a-f]{64}$'),
    payload_enc text NOT NULL CHECK (length(payload_enc) BETWEEN 1 AND 1048576),
    PRIMARY KEY (user_id, id)
);
ALTER TABLE public.repertoire_entries ENABLE ROW LEVEL SECURITY;
CREATE POLICY repertoire_owner_read ON public.repertoire_entries FOR SELECT
TO authenticated USING (user_id = (SELECT auth.uid()));
GRANT SELECT ON public.repertoire_entries TO authenticated;

CREATE FUNCTION public.put_repertoire_entry(
    p_owner uuid, p_id text, p_kind text, p_expected_version text,
    p_version text, p_payload_enc text
) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
    stored_version text;
BEGIN
    IF auth.uid() IS NULL OR auth.uid() IS DISTINCT FROM p_owner THEN
        RAISE EXCEPTION 'unauthorized' USING ERRCODE = '42501';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtextextended(p_owner::text || ':' || p_id, 0));
    SELECT version INTO stored_version FROM public.repertoire_entries
    WHERE user_id = p_owner AND id = p_id;
    IF stored_version = p_version THEN RETURN true; END IF;
    IF stored_version IS DISTINCT FROM p_expected_version THEN RETURN false; END IF;
    INSERT INTO public.repertoire_entries(user_id, id, kind, version, payload_enc)
    VALUES (p_owner, p_id, p_kind, p_version, p_payload_enc)
    ON CONFLICT(user_id, id) DO UPDATE SET kind = EXCLUDED.kind,
        version = EXCLUDED.version, payload_enc = EXCLUDED.payload_enc;
    RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION public.put_repertoire_entry(uuid, text, text, text, text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION public.put_repertoire_entry(uuid, text, text, text, text, text) TO authenticated;
