-- Exercise persistence and ownership without leaving test accounts/posts behind.
BEGIN;
DO $$
DECLARE
    owner_id BIGINT;
    post_id BIGINT;
BEGIN
    INSERT INTO users(user_name,password_hash,full_name,phone,role,status)
    VALUES ('schema-check-' || gen_random_uuid(), '!SCHEMA_CHECK_LOGIN_DISABLED!',
            'Schema check', left(gen_random_uuid()::text, 30), 'CUSTOMER', 'INACTIVE')
    RETURNING id INTO owner_id;
    INSERT INTO customers(id) VALUES (owner_id);
    INSERT INTO community_posts(user_id,content,media_urls)
    VALUES (owner_id, 'Database persistence check', '["https://example.invalid/media.png"]')
    RETURNING id INTO post_id;
    IF NOT EXISTS (SELECT 1 FROM community_posts
                   WHERE id=post_id AND user_id=owner_id
                     AND media_urls->>0='https://example.invalid/media.png') THEN
        RAISE EXCEPTION 'TEST FAILED: post was not persisted with its owner/media';
    END IF;
    BEGIN
        INSERT INTO community_posts(user_id,content) VALUES (NULL, 'Missing owner');
        RAISE EXCEPTION 'TEST FAILED: null owner was allowed';
    EXCEPTION WHEN not_null_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO community_posts(user_id,content) VALUES (-1, 'Invalid owner');
        RAISE EXCEPTION 'TEST FAILED: nonexistent owner was allowed';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO community_posts(user_id,content) VALUES (owner_id, '   ');
        RAISE EXCEPTION 'TEST FAILED: empty content was allowed';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO community_posts(user_id,content,media_urls) VALUES (owner_id, 'Invalid media', '{}');
        RAISE EXCEPTION 'TEST FAILED: non-array media was allowed';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
END;
$$;
ROLLBACK;
