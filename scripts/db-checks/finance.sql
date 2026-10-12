BEGIN;
DO $$
DECLARE ledger_total BIGINT; cached_balance BIGINT;
BEGIN
    SELECT sum(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount END) INTO ledger_total
      FROM wallet_transactions WHERE balance_id=1 AND bucket='AVAILABLE';
    SELECT available_balance INTO cached_balance FROM staff_balances WHERE id=1;
    IF ledger_total IS DISTINCT FROM cached_balance THEN
        RAISE EXCEPTION 'TEST FAILED: demo wallet cache differs from ledger';
    END IF;
    BEGIN
        INSERT INTO refunds(payment_id,amount,reason,status)
        VALUES (2,1,'Test excessive refund','PENDING');
        RAISE EXCEPTION 'TEST FAILED: excess refund was allowed';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM <> 'Total refunds exceed the collected payment amount' THEN RAISE; END IF;
    END;
    BEGIN
        UPDATE wallet_transactions SET amount=1 WHERE id=1;
        RAISE EXCEPTION 'TEST FAILED: mutation of ledger was allowed';
    EXCEPTION WHEN raise_exception THEN
        IF SQLERRM <> 'Wallet ledger is append-only; create a reversal entry instead' THEN RAISE; END IF;
    END;
    BEGIN
        INSERT INTO withdrawals(staff_id,balance_id,bank_account_id,bank_account_snapshot,amount)
        VALUES (2,1,
                1,'{}',1);
        RAISE EXCEPTION 'TEST FAILED: another staff wallet/bank was allowed';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
END;
$$;
ROLLBACK;
