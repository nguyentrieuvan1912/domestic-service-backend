-- No test data is retained; no external delivery is attempted.
BEGIN;
DO $$
DECLARE v_event_id BIGINT; v_notification_id BIGINT; v_device_id BIGINT; v_other_device_id BIGINT;
BEGIN
    INSERT INTO notification_events(source_service,event_key,event_type)
    VALUES ('schema-check',gen_random_uuid()::text,'BOOKING_CREATED') RETURNING id INTO v_event_id;
    BEGIN
        INSERT INTO notification_events(source_service,event_key,event_type)
        SELECT source_service,event_key,event_type FROM notification_events WHERE id=v_event_id;
        RAISE EXCEPTION 'TEST FAILED: duplicate incoming event was allowed';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    INSERT INTO notifications(user_id,event_id,notification_type,title,content)
    VALUES (1,v_event_id,'BOOKING_CREATED','Schema check','Persistence check') RETURNING id INTO v_notification_id;
    IF NOT EXISTS (SELECT 1 FROM notifications WHERE id=v_notification_id AND user_id=1 AND read_at IS NULL) THEN
        RAISE EXCEPTION 'TEST FAILED: unread notification was not persisted';
    END IF;
    BEGIN
        INSERT INTO notifications(user_id,event_id,notification_type,title,content)
        VALUES (1,v_event_id,'BOOKING_CREATED','Duplicate','Duplicate');
        RAISE EXCEPTION 'TEST FAILED: event generated duplicate inbox item for the same user';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    UPDATE notifications SET read_at=CURRENT_TIMESTAMP WHERE id=v_notification_id;
    IF NOT EXISTS (SELECT 1 FROM notifications WHERE id=v_notification_id AND read_at IS NOT NULL) THEN
        RAISE EXCEPTION 'TEST FAILED: read state was not persisted';
    END IF;
    INSERT INTO notification_devices(user_id,device_key,platform,provider,push_token)
    VALUES (1,gen_random_uuid()::text,'ANDROID','EXPO',gen_random_uuid()::text) RETURNING id INTO v_device_id;
    INSERT INTO notification_devices(user_id,device_key,platform,provider,push_token)
    VALUES (2,gen_random_uuid()::text,'IOS','EXPO',gen_random_uuid()::text) RETURNING id INTO v_other_device_id;
    INSERT INTO notification_deliveries(notification_id,user_id,device_id,channel)
    VALUES (v_notification_id,1,v_device_id,'PUSH');
    BEGIN
        INSERT INTO notification_deliveries(notification_id,user_id,device_id,channel)
        VALUES (v_notification_id,1,v_other_device_id,'PUSH');
        RAISE EXCEPTION 'TEST FAILED: delivery to another user device was allowed';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO notification_deliveries(notification_id,user_id,device_id,channel)
        VALUES (v_notification_id,2,v_other_device_id,'PUSH');
        RAISE EXCEPTION 'TEST FAILED: delivery owner differed from notification owner';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    INSERT INTO notification_deliveries(notification_id,user_id,channel)
    VALUES (v_notification_id,1,'IN_APP');
    BEGIN
        INSERT INTO notification_deliveries(notification_id,user_id,channel)
        VALUES (v_notification_id,1,'IN_APP');
        RAISE EXCEPTION 'TEST FAILED: duplicate in-app delivery was allowed';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        UPDATE notification_deliveries SET status='SENT' WHERE notification_id=v_notification_id;
        RAISE EXCEPTION 'TEST FAILED: sent status without timestamp was allowed';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE notification_events SET status='PROCESSED' WHERE id=v_event_id;
        RAISE EXCEPTION 'TEST FAILED: processed status without timestamp was allowed';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
END;
$$;
ROLLBACK;
