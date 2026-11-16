package com.domesticservice.booking.service;

import com.domesticservice.booking.service.StaffDispatchSolver.Candidate;
import com.domesticservice.booking.service.StaffDispatchSolver.Job;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StaffDispatchSolverTest {
    @Test
    void coversMoreJobsThanGreedyAndRespectsCompleteTeamsAndBuffer() {
        Instant start = Instant.parse("2027-01-01T02:00:00Z");
        var high = new Candidate(1, "A", 9900, Map.of());
        var low = new Candidate(2, "B", 2000, Map.of());
        var backup = new Candidate(3, "C", 1000, Map.of());
        var jobs = List.of(
            new Job(1, "1", "MODE_B", start, start.plusSeconds(3600), 2, List.of(high, low, backup)),
            new Job(2, "2", "MODE_A", start.plusSeconds(3600), start.plusSeconds(7200), 1, List.of(high)),
            new Job(3, "3", "MODE_B", start.plusSeconds(10_800), start.plusSeconds(14_400), 1, List.of(high)),
            new Job(4, "4", "MODE_B", start.plusSeconds(14_400), start.plusSeconds(18_000), 2, List.of(low)));

        var result = new StaffDispatchSolver().solve(jobs);
        assertEquals("OPTIMAL", result.status());
        assertFalse(result.fallback());
        assertEquals(2, result.baseline().covered());
        assertEquals(3, result.proposal().covered());
        assertEquals(List.of(low, backup), result.proposal().teams().get(1L));
        assertEquals(List.of(high), result.proposal().teams().get(2L));
        assertEquals(List.of(high), result.proposal().teams().get(3L));
        assertFalse(result.proposal().teams().containsKey(4L));
    }
}
