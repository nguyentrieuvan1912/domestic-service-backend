package com.domesticservice.booking.service;

import com.google.ortools.Loader;
import com.google.ortools.sat.*;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class StaffDispatchSolver {
    static final int MAX_SCORE = 10_000;
    static final long BUFFER_SECONDS = 3600;

    record Candidate(long staffId, String name, int score, Map<String, Integer> components) {}
    record Job(long id, String code, String mode, Instant start, Instant end, int remaining, List<Candidate> candidates) {}
    record Plan(int covered, long score, Map<Long, List<Candidate>> teams) {}
    record Result(String status, boolean fallback, Plan proposal, Plan baseline) {}
    private record Edge(Job job, Candidate candidate, BoolVar selected) {}

    Result solve(List<Job> jobs) {
        Plan baseline = baseline(jobs);
        if (jobs.isEmpty()) return new Result("NO_JOBS", false, baseline, baseline);
        if (jobs.stream().noneMatch(job -> job.candidates().size() >= job.remaining())) {
            return new Result("NO_FULL_TEAM", false, baseline, baseline);
        }
        try {
            Loader.loadNativeLibraries();
        } catch (LinkageError | RuntimeException unavailable) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("Staff dispatch solver could not load", unavailable);
            return new Result("SOLVER_UNAVAILABLE", true, baseline, baseline);
        }
        return optimize(jobs, baseline);
    }

    private Result optimize(List<Job> jobs, Plan baseline) {
        var model = new CpModel();
        var objective = LinearExpr.newBuilder();
        long coverageWeight = 1 + (long) MAX_SCORE * jobs.stream().mapToLong(Job::remaining).sum();
        var byStaff = new HashMap<Long, List<IntervalVar>>();
        var edges = new ArrayList<Edge>();
        for (var job : jobs) {
            if (job.candidates().size() < job.remaining()) continue;
            var covered = model.newBoolVar("covered_" + job.id());
            var team = LinearExpr.newBuilder();
            team.addTerm(covered, -job.remaining());
            objective.addTerm(covered, coverageWeight);
            for (var candidate : job.candidates()) {
                var selected = model.newBoolVar("staff_" + candidate.staffId() + "_job_" + job.id());
                team.add(selected);
                objective.addTerm(selected, candidate.score());
                var interval = model.newOptionalFixedInterval(micros(job.start()),
                    micros(job.end().plusSeconds(BUFFER_SECONDS)) - micros(job.start()), selected, selected.getName());
                byStaff.computeIfAbsent(candidate.staffId(), key -> new ArrayList<>()).add(interval);
                edges.add(new Edge(job, candidate, selected));
            }
            model.addEquality(team, 0);
        }
        for (var intervals : byStaff.values()) model.addNoOverlap(intervals);
        model.maximize(objective);
        var solver = new CpSolver();
        solver.getParameters().setMaxTimeInSeconds(2).setNumSearchWorkers(1);
        var status = solver.solve(model);
        if (status != CpSolverStatus.OPTIMAL && status != CpSolverStatus.FEASIBLE) {
            return new Result(status.name(), true, baseline, baseline);
        }
        var teams = new LinkedHashMap<Long, List<Candidate>>();
        for (var edge : edges) {
            if (solver.booleanValue(edge.selected())) {
                teams.computeIfAbsent(edge.job().id(), key -> new ArrayList<>()).add(edge.candidate());
            }
        }
        Plan proposal = plan(teams);
        boolean fallback = proposal.covered() < baseline.covered()
            || (proposal.covered() == baseline.covered() && proposal.score() < baseline.score());
        return new Result(status.name(), fallback, fallback ? baseline : proposal, baseline);
    }

    private static Plan baseline(List<Job> jobs) {
        var teams = new LinkedHashMap<Long, List<Candidate>>();
        var allocations = new HashMap<Long, List<Job>>();
        for (var job : jobs) {
            var team = job.candidates().stream()
                .filter(candidate -> allocations.getOrDefault(candidate.staffId(), List.of()).stream().noneMatch(other -> overlaps(job, other)))
                .sorted(Comparator.comparingInt(Candidate::score).reversed().thenComparingLong(Candidate::staffId))
                .limit(job.remaining()).toList();
            if (team.size() != job.remaining()) continue;
            teams.put(job.id(), team);
            for (var candidate : team) allocations.computeIfAbsent(candidate.staffId(), key -> new ArrayList<>()).add(job);
        }
        return plan(teams);
    }

    private static Plan plan(Map<Long, List<Candidate>> teams) {
        return new Plan(teams.size(), teams.values().stream().flatMap(Collection::stream).mapToLong(Candidate::score).sum(), teams);
    }

    private static boolean overlaps(Job a, Job b) {
        return a.start().isBefore(b.end().plusSeconds(BUFFER_SECONDS)) && b.start().isBefore(a.end().plusSeconds(BUFFER_SECONDS));
    }

    private static long micros(Instant instant) {
        return instant.getEpochSecond() * 1_000_000 + instant.getNano() / 1000;
    }
}
