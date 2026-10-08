DO $$
DECLARE col RECORD; sequence_name TEXT; max_id BIGINT; sequence_next BIGINT;
BEGIN
    FOR col IN SELECT table_name, is_identity FROM information_schema.columns
        WHERE table_schema='public' AND column_name='id'
          AND table_name NOT IN ('service_schema_metadata','flyway_schema_history')
    LOOP
        IF col.table_name IN ('admins','customers','staffs') THEN
            IF col.is_identity='YES' THEN RAISE EXCEPTION 'Inherited profile must share users.id'; END IF;
        ELSE
            IF col.is_identity <> 'YES' THEN RAISE EXCEPTION 'Identity missing on %.id', col.table_name; END IF;
            sequence_name := pg_get_serial_sequence('public.' || quote_ident(col.table_name), 'id');
            EXECUTE format('SELECT coalesce(max(id),0) FROM public.%I',col.table_name) INTO max_id;
            EXECUTE format('SELECT last_value + CASE WHEN is_called THEN 1 ELSE 0 END FROM %s',sequence_name)
                INTO sequence_next;
            IF sequence_next <= max_id THEN
                RAISE EXCEPTION 'Sequence % would collide with existing IDs',sequence_name;
            END IF;
        END IF;
    END LOOP;
END;
$$;
