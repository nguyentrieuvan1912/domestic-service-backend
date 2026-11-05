CREATE FUNCTION guard_discipline_action_update() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF ROW(NEW.staff_id,NEW.booking_id,NEW.kind,NEW.violation_code,NEW.violation_number,NEW.amount,NEW.reason,NEW.source,NEW.request_key,NEW.created_by,NEW.created_at,NEW.evidence)
 IS DISTINCT FROM ROW(OLD.staff_id,OLD.booking_id,OLD.kind,OLD.violation_code,OLD.violation_number,OLD.amount,OLD.reason,OLD.source,OLD.request_key,OLD.created_by,OLD.created_at,OLD.evidence)
 OR OLD.status <> 'PENDING_FUNDS' OR NEW.status <> 'APPLIED' OR NEW.ledger_id IS NULL OR NEW.adjustment_id IS NULL OR NEW.applied_at IS NULL
 THEN RAISE EXCEPTION 'Discipline evidence and applied results are immutable'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER guard_discipline_action_update BEFORE UPDATE ON staff_discipline_actions FOR EACH ROW EXECUTE FUNCTION guard_discipline_action_update();
