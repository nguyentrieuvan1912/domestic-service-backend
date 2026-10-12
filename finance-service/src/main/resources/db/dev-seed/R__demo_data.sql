INSERT INTO orders(id,booking_id,order_code,subtotal,add_on_amount,discount_amount,total_amount,payment_method,status) VALUES
(1,1,'DEMO-ORDER-001',180000,20000,0,200000,'BANK_TRANSFER_QR','FINALIZED'),
(2,2,'DEMO-ORDER-002',180000,0,30000,150000,'BANK_TRANSFER_QR','CANCELLED') ON CONFLICT DO NOTHING;
INSERT INTO order_details(id,order_id,item_type,reference_id,item_name,quantity,unit_price,total_price) VALUES
(1,1,'PACKAGE',1,'Demo 2 hours',1,180000,180000),
(2,1,'ADD_ON',1,'Demo extra cleaning',1,20000,20000) ON CONFLICT DO NOTHING;
INSERT INTO payments(id,booking_id,customer_id,order_id,payment_code,amount,payment_method,provider,transaction_code,status,paid_at) VALUES
(1,1,2,1,'DEMO-PAY-001',200000,'BANK_TRANSFER_QR','DEV_STUB','DEMO-TXN-001','PAID','2026-09-30T04:00:00Z'),
(2,2,2,2,'DEMO-PAY-002',150000,'BANK_TRANSFER_QR','DEV_STUB','DEMO-TXN-002','REFUNDED','2026-10-01T04:30:00Z') ON CONFLICT DO NOTHING;
INSERT INTO refunds(id,payment_id,amount,reason,status,transaction_code,completed_at)
VALUES (1,2,150000,'Demo cancelled booking','PROCESSED','DEMO-REFUND-001','2026-10-01T06:00:00Z') ON CONFLICT DO NOTHING;
INSERT INTO invoices(id,order_id,booking_id,customer_id,invoice_number,customer_snapshot,service_snapshot,subtotal,total_amount,status)
VALUES (1,1,1,2,'DEMO-INV-001','{"demo":true,"name":"Customer Demo","address":"DEMO ADDRESS"}','{"demo":true,"serviceName":"Don dep nha theo gio"}',200000,200000,'PAID') ON CONFLICT DO NOTHING;
INSERT INTO invoice_items(id,invoice_id,name,quantity,unit_price,total_price) VALUES
(1,1,'Demo 2 hours',1,180000,180000),
(2,1,'Demo extra cleaning',1,20000,20000) ON CONFLICT DO NOTHING;
INSERT INTO staff_balances(id,staff_id,available_balance,total_earned,withdrawn_amount)
VALUES (1,3,105000,140000,40000) ON CONFLICT DO NOTHING;
INSERT INTO staff_incomes(id,staff_id,booking_id,balance_id,working_hours,service_amount,platform_fee,staff_amount,status,income_date)
VALUES (1,3,1,1,2,200000,60000,140000,'SETTLED','2026-10-01') ON CONFLICT DO NOTHING;
INSERT INTO rewards(id,staff_id,reward_type,period_start,period_end,revenue,amount,reason,status)
VALUES (1,3,'DEMO_BONUS','2026-10-01','2026-10-01',200000,10000,'Synthetic demo reward','APPLIED') ON CONFLICT DO NOTHING;
INSERT INTO penalties(id,staff_id,booking_id,penalty_type,amount,reason,status,approved_by)
VALUES (1,3,1,'DEMO_ADJUSTMENT',5000,'Synthetic demo penalty','APPLIED',1) ON CONFLICT DO NOTHING;
INSERT INTO staff_bank_accounts(id,staff_id,bank_code,account_number,account_holder,status,is_default)
VALUES (1,3,'DEMO_BANK','DEMO-ACCOUNT-0001','STAFF DEMO','VERIFIED',true) ON CONFLICT DO NOTHING;
INSERT INTO withdrawals(id,staff_id,balance_id,bank_account_id,bank_account_snapshot,amount,status,approved_by,approved_at,paid_at,transfer_reference)
VALUES (1,3,1,1,'{"demo":true,"bankCode":"DEMO_BANK","accountNumber":"DEMO-ACCOUNT-0001","accountHolder":"STAFF DEMO"}',40000,'PAID',1,'2026-10-03T01:00:00Z','2026-10-03T02:00:00Z','DEMO-PAYOUT-001') ON CONFLICT DO NOTHING;
INSERT INTO wallet_transactions(id,balance_id,direction,bucket,transaction_type,amount,reference_id,idempotency_key) VALUES
(1,1,'CREDIT','AVAILABLE','EARNING',140000,1,'demo-earning-001'),
(2,1,'CREDIT','AVAILABLE','REWARD',10000,1,'demo-reward-001'),
(3,1,'DEBIT','AVAILABLE','PENALTY',5000,1,'demo-penalty-001'),
(4,1,'DEBIT','AVAILABLE','WITHDRAWAL',40000,1,'demo-withdrawal-001') ON CONFLICT DO NOTHING;
INSERT INTO payment_webhook_events(id,provider,provider_event_id,payment_id,payload,status)
VALUES (1,'DEV_STUB','DEMO-EVENT-001',1,'{"demo":true,"description":"Synthetic rejected webhook example; no actual bank event"}','REJECTED') ON CONFLICT DO NOTHING;

-- Explicit fixture IDs must also advance identity sequences on a freshly cloned database.
DO $$
DECLARE col RECORD; sequence_name TEXT; next_id BIGINT; sequence_next BIGINT;
BEGIN
    FOR col IN SELECT table_name FROM information_schema.columns
        WHERE table_schema = 'public' AND column_name = 'id' AND is_identity = 'YES'
    LOOP
        sequence_name := pg_get_serial_sequence('public.' || quote_ident(col.table_name), 'id');
        EXECUTE format('SELECT coalesce(max(id), 0) + 1 FROM public.%I', col.table_name) INTO next_id;
        EXECUTE format('SELECT last_value + CASE WHEN is_called THEN 1 ELSE 0 END FROM %s', sequence_name)
            INTO sequence_next;
        PERFORM setval(sequence_name::regclass, greatest(next_id, sequence_next), false);
    END LOOP;
END;
$$;
