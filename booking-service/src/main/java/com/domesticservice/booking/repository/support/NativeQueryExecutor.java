package com.domesticservice.booking.repository.support;

import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.hibernate.query.NativeQuery;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.dao.*;
import org.springframework.jdbc.core.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedCaseInsensitiveMap;

/**
 * Native persistence operations for existing PostgreSQL workflows. Entity CRUD uses
 * the adjacent Spring Data JPA repositories. Both paths participate in the same
 * JpaTransactionManager transaction. JDBC is used for wall-clock schedules, advisory locks, ResultSet DTO mappers and
 * generated-key inserts, preserving the existing API's JDBC value representation.
 */
@Repository
public class NativeQueryExecutor {

    private final EntityManager entityManager;

    private final JdbcTemplate jdbc;

    public NativeQueryExecutor(EntityManager entityManager, JdbcTemplate jdbc) {
        this.entityManager = entityManager;
        this.jdbc = jdbc;
    }

    private NativeQuery<?> query(String sql, Object... args) {
        var query = entityManager.createNativeQuery(sql).unwrap(NativeQuery.class);
        for (int i = 0; i < args.length; i++) {
            Object value = args[i];
            if (value instanceof Timestamp time)
                value = time.toInstant().atOffset(ZoneOffset.UTC);
            else if (value instanceof java.sql.Date date)
                value = date.toLocalDate();
            else if (value instanceof java.sql.Time time)
                value = time.toLocalTime();
            query.setParameter(i + 1, value);
        }
        return query;
    }

    private Object jdbcValue(Object value) {
        if (value instanceof OffsetDateTime time)
            return Timestamp.from(time.toInstant());
        if (value instanceof Instant time)
            return Timestamp.from(time);
        if (value instanceof LocalDate date)
            return java.sql.Date.valueOf(date);
        if (value instanceof LocalDateTime time)
            return Timestamp.valueOf(time);
        if (value instanceof LocalTime time)
            return java.sql.Time.valueOf(time);
        return value;
    }

    private boolean wallClockQuery(String sql, Object... args) {
        // SQL TIME is a wall-clock time, unlike UTC instants. Preserve the existing
        // JDBC policy representation for schedules instead of shifting it by a zone.
        return sql.toLowerCase(Locale.ROOT).contains("staff_availabilities") || Arrays.stream(args).anyMatch(value -> value instanceof java.sql.Time || value instanceof LocalTime);
    }

    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        if (wallClockQuery(sql, args))
            return jdbc.queryForList(sql, args);
        return query(sql, args).setTupleTransformer((tuple, aliases) -> {
            Map<String, Object> row = new LinkedCaseInsensitiveMap<>();
            for (int i = 0; i < tuple.length; i++) row.put(aliases[i], jdbcValue(tuple[i]));
            return row;
        }).getResultList();
    }

    public <T> List<T> queryForList(String sql, Class<T> type, Object... args) {
        if (java.util.Date.class.isAssignableFrom(type))
            return jdbc.queryForList(sql, type, args);
        return queryForList(sql, args).stream().map(row -> {
            if (row.size() != 1)
                throw new IncorrectResultSizeDataAccessException(1, row.size());
            return convert(row.values().iterator().next(), type);
        }).toList();
    }

    private <T> T convert(Object value, Class<T> type) {
        if (value == null)
            return null;
        return DefaultConversionService.getSharedInstance().convert(value, type);
    }

    private <T> T single(List<T> values) {
        if (values.isEmpty())
            throw new EmptyResultDataAccessException(1);
        if (values.size() != 1)
            throw new IncorrectResultSizeDataAccessException(1, values.size());
        return values.getFirst();
    }

    public Map<String, Object> queryForMap(String sql, Object... args) {
        return single(queryForList(sql, args));
    }

    public <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return single(queryForList(sql, type, args));
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        return jdbc.query(sql, mapper, args);
    }

    public <T> T queryForObject(String sql, RowMapper<T> mapper, Object... args) {
        return jdbc.queryForObject(sql, mapper, args);
    }

    @Transactional
    public int update(String sql, Object... args) {
        int count = wallClockQuery(sql, args) ? jdbc.update(sql, args) : query(sql, args).executeUpdate();
        entityManager.clear();
        return count;
    }

    @Transactional
    public void execute(String sql) {
        jdbc.execute(sql);
    }

    @Transactional
    public long insert(String sql, Object... values) {
        entityManager.flush();
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, new String[] { "id" });
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        entityManager.clear();
        return Objects.requireNonNull(key.getKey()).longValue();
    }
}
