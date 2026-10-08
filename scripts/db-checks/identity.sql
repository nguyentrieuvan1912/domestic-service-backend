BEGIN;
DO $$
BEGIN
    BEGIN
        INSERT INTO addresses(customer_id,receiver_name,receiver_phone,province,ward,detail_address,is_default)
        VALUES (2,'Test','DEMO','Demo','Demo','Demo',true);
        RAISE EXCEPTION 'TEST FAILED: second default address was allowed';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
END;
$$;
ROLLBACK;
