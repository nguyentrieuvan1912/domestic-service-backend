package com.domesticservice.booking.service;

import com.domesticservice.booking.service.StaffDispatchSolver.Candidate;
import com.domesticservice.booking.service.StaffDispatchSolver.Job;
import com.domesticservice.common.web.ServiceHttpClient;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class StaffDispatchService {
    private static final int BATCH_LIMIT = 20;
    private static final int HORIZON_DAYS = 7;
    private static final String SCORE_VERSION = "1";
    private static final Map<String, Integer> WEIGHTS = Map.of("skill", 35, "rating", 25, "reliability", 25, "workload", 15);
    private final JdbcTemplate db;
    private final BookingOperationsService ops;
    private final BookingWorkflowService workflow;
    private final StaffDispatchSolver solver;
    private final ObjectMapper json;
    private final TransactionTemplate snapshotTransaction;

    public StaffDispatchService(JdbcTemplate db, BookingOperationsService ops, BookingWorkflowService workflow,
                                StaffDispatchSolver solver, ObjectMapper json, PlatformTransactionManager manager) {
        this.db = db;
        this.ops = ops;
        this.workflow = workflow;
        this.solver = solver;
        this.json = json;
        snapshotTransaction = new TransactionTemplate(manager);
        snapshotTransaction.setReadOnly(true);
        snapshotTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    public record Run(long id, Instant createdAt, JsonNode result) {}
    private record Batch(List<Job> jobs, boolean truncated) {}
    private record Metrics(Double rating, long completed, long missed, double workloadHours) {}

    Run runAutomatically() {
        var profiles = new HashMap<Long, Map<String, Object>>();
        for (var profile : ops.identityClient().list("/api/v1/identity/staff-directory", null)) {
            if (Boolean.TRUE.equals(profile.get("isOnline")) && "AVAILABLE".equals(profile.get("status"))) {
                profiles.put(ServiceHttpClient.userId(profile), profile);
            }
        }
        Instant snapshotAt = Instant.now();
        var batch = Objects.requireNonNull(snapshotTransaction.execute(status -> collect(profiles, snapshotAt)));
        if (batch.jobs().isEmpty()) return null;
        var result = solver.solve(batch.jobs());
        JsonNode report = json.valueToTree(Map.of("snapshotAt", snapshotAt, "horizonDays", HORIZON_DAYS,
            "truncated", batch.truncated(), "scoreVersion", SCORE_VERSION, "weights", WEIGHTS,
            "jobs", batch.jobs(), "solver", result));
        return db.queryForObject("INSERT INTO staff_dispatch_runs(result) VALUES (CAST(? AS jsonb)) RETURNING id,created_at",
            (rs, row) -> new Run(rs.getLong(1), rs.getTimestamp(2).toInstant(), report), report.toString());
    }

    private Batch collect(Map<Long, Map<String, Object>> profiles, Instant now) {
        var rows = db.queryForList("""
            SELECT b.*, b.required_staff_count - (SELECT count(*) FROM booking_assignments a
                WHERE a.booking_id=b.id AND a.status IN ('ACCEPTED','COMPLETED')) AS remaining
            FROM bookings b
            WHERE b.pricing_confirmed AND b.status IN ('PENDING','MATCHING','STAFF_ASSIGNED')
              AND (b.payment_method='CASH' OR b.payment_status='PAID') AND b.starts_at>? AND b.starts_at<=?
              AND (b.booking_mode='MODE_B' OR (b.staff_wait_state='WAITING' AND b.staff_response_deadline>now()))
              AND b.required_staff_count > (SELECT count(*) FROM booking_assignments a
                WHERE a.booking_id=b.id AND a.status IN ('ACCEPTED','COMPLETED'))
            ORDER BY b.starts_at,b.id LIMIT ?
            """, Timestamp.from(now), Timestamp.from(now.plus(HORIZON_DAYS, ChronoUnit.DAYS)), BATCH_LIMIT + 1);
        var metrics = new HashMap<Long, Metrics>();
        var jobs = rows.stream().limit(BATCH_LIMIT).map(row -> job(row, profiles, metrics, now)).toList();
        return new Batch(jobs, rows.size() > BATCH_LIMIT);
    }

    private Job job(Map<String, Object> row, Map<Long, Map<String, Object>> profiles, Map<Long, Metrics> metrics, Instant now) {
        long id = ((Number) row.get("id")).longValue();
        Timestamp start = (Timestamp) row.get("starts_at"), end = (Timestamp) row.get("ends_at");
        String mode = row.get("booking_mode").toString();
        var candidates = new ArrayList<Candidate>();
        if (!profiles.isEmpty()) {
            var required = workflow.requiredServices(row);
            String placeholders = String.join(",", Collections.nCopies(required.size(), "?"));
            var args = new ArrayList<Object>(required);
            Collections.addAll(args, start, end, required.size());
            var skills = db.queryForList("""
                SELECT staff_id, min(CASE skill_level WHEN 'EXPERT' THEN 100 WHEN 'ADVANCED' THEN 75
                    WHEN 'INTERMEDIATE' THEN 50 ELSE 25 END) AS skill
                FROM staff_service_capabilities WHERE service_id IN (%s) AND status='APPROVED'
                  AND approved_at<=now() AND (valid_from IS NULL OR valid_from<=?) AND (valid_until IS NULL OR valid_until>=?)
                GROUP BY staff_id HAVING count(DISTINCT service_id)=? ORDER BY staff_id
                """.formatted(placeholders), args.toArray());
            var blocked = db.queryForList("""
                SELECT staff_id FROM booking_assignments WHERE booking_id=? AND status IN ('ACCEPTED','COMPLETED','REJECTED')
                UNION SELECT staff_id FROM staff_reservations WHERE booking_id IS DISTINCT FROM ?
                  AND status IN ('HELD','CONFIRMED','IN_PROGRESS') AND starts_at<? AND ends_at>?
                """, Long.class, id, id, Timestamp.from(end.toInstant().plusSeconds(StaffDispatchSolver.BUFFER_SECONDS)), start);
            var excluded = new HashSet<>(blocked);
            for (var skill : skills) {
                long staff = ((Number) skill.get("staff_id")).longValue();
                var profile = profiles.get(staff);
                if (profile == null || excluded.contains(staff) || !workflow.eligibility(staff, row, profile).isEmpty()) continue;
                var data = metrics.computeIfAbsent(staff, key -> metrics(key, now));
                var scores = new LinkedHashMap<String, Integer>();
                scores.put("skill", ((Number) skill.get("skill")).intValue());
                if (data.rating() != null) scores.put("rating", (int) Math.round(data.rating() * 20));
                if (data.completed() + data.missed() > 0) scores.put("reliability",
                    (int) Math.round(100.0 * data.completed() / (data.completed() + data.missed())));
                scores.put("workload", (int) Math.round(100 * (1 - Math.min(1, data.workloadHours() / 40))));
                int totalWeight = scores.keySet().stream().mapToInt(WEIGHTS::get).sum();
                int weightedScore = scores.entrySet().stream().mapToInt(item -> item.getValue() * WEIGHTS.get(item.getKey())).sum();
                candidates.add(new Candidate(staff, Objects.toString(profile.get("fullName"), "Nhân viên"),
                    (int) Math.round(100.0 * weightedScore / totalWeight), scores));
            }
        }
        return new Job(id, row.get("booking_code").toString(), mode, start.toInstant(), end.toInstant(),
            ((Number) row.get("remaining")).intValue(), candidates);
    }

    private Metrics metrics(long staff, Instant now) {
        Timestamp since = Timestamp.from(now.minus(90, ChronoUnit.DAYS));
        Timestamp snapshot = Timestamp.from(now);
        return db.queryForObject("""
            SELECT (SELECT avg(rating) FROM reviews WHERE staff_id=? AND moderation_status IN ('PUBLISHED','RESOLVED')) AS rating,
              (SELECT count(*) FROM staff_execution_events WHERE staff_id=? AND status='COMPLETED'
                AND occurred_at>=? AND occurred_at<=?) AS completed,
              (SELECT count(*) FROM forgotten_job_remediations WHERE staff_id=? AND occurred_at>=? AND occurred_at<=?) AS missed,
              (SELECT COALESCE(sum(EXTRACT(EPOCH FROM LEAST(b.ends_at, ?) - GREATEST(b.starts_at, ?)))/3600,0)
                FROM booking_assignments a JOIN bookings b ON b.id=a.booking_id
                WHERE a.staff_id=? AND a.status='ACCEPTED' AND b.status IN ('MATCHING','CONFIRMED','IN_PROGRESS')
                  AND b.starts_at<? AND b.ends_at>?) AS workload
            """, (rs, row) -> {
                var rating = rs.getBigDecimal("rating");
                return new Metrics(rating == null ? null : rating.doubleValue(), rs.getLong("completed"),
                    rs.getLong("missed"), rs.getDouble("workload"));
            }, staff, staff, since, snapshot, staff, since, snapshot,
            Timestamp.from(now.plus(HORIZON_DAYS, ChronoUnit.DAYS)), snapshot, staff,
            Timestamp.from(now.plus(HORIZON_DAYS, ChronoUnit.DAYS)), snapshot);
    }
}
