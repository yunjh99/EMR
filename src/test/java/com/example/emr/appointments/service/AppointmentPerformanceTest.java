package com.example.emr.appointments.service;

import com.example.emr.appointments.dto.AppointmentCreateRequest;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("mysql-performance")
@EnabledIfEnvironmentVariable(named = "APPOINTMENT_BENCHMARK_MODE", matches = "UNIQUE")
class AppointmentPerformanceTest extends AppointmentMysqlTestSupport {

    @Test
    void warmupAndMeasureSeparateScenarios() throws Exception {
        String mode = System.getenv("APPOINTMENT_BENCHMARK_MODE");
        var pool = dataSource.unwrap(HikariDataSource.class);
        String catalog;
        int isolation;
        String version;
        try (var c = dataSource.getConnection()) {
            catalog = c.getCatalog(); isolation = c.getTransactionIsolation();
            version = c.getMetaData().getDatabaseProductVersion();
        }
        assertThat(catalog).startsWith(mode.equals("UNIQUE") ? "emr_bench_unique_" : "emr_bench_lock_");
        long uniqueParts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE()
                AND table_name='appointments' AND index_name='uk_active_doctor_slot' AND non_unique=0
                """, Long.class);
        assertThat(uniqueParts).as("Only the selected protection strategy is active").isEqualTo(mode.equals("UNIQUE") ? 2 : 0);
        assertThat(pool.getMaximumPoolSize()).isEqualTo(20);
        Function<AppointmentCreateRequest, Long> create = service::createAppointment;
        String key = mode + "-" + isolation;
        Path directory = Path.of("build/reports/appointment-benchmark");
        Files.createDirectories(directory);
        Path csv = directory.resolve(key + ".csv");
        Path raw = directory.resolve(key + "-attempts.csv");
        Files.writeString(csv, "mode,isolation,scenario,phase,round,requests,success,duplicate,other,stored,active,elapsedMs,rps,successP50,successP95,successP99,duplicateP50,duplicateP95,duplicateP99,globalLockMs,globalLockWaits,maxPoolPending,maxPoolActive,maxDbWaiters,dbMetricStatus\n");
        Files.writeString(raw, "scenario,phase,round,request,outcome,elapsedMs,errorType\n");
        String ddl = jdbc.queryForMap("SHOW CREATE TABLE appointments").toString();
        Files.writeString(directory.resolve(key + "-environment.txt"), """
                mode=%s schema=%s MySQL=%s isolation=%d
                protection=%s
                threads=100 requests=100 poolMax=20 poolMin=%d connectionTimeoutMs=%d
                warmupRounds=2 measuredRounds=5 scenarioOrder=rotatedPerRound
                sqlLogging=false audit=enabled_REQUIRES_NEW expectedAuditEntries=100_per_batch
                Java=%s OS=%s processors=%d maxHeapBytes=%d
                rowLockTimeMetric=GLOBAL_delta_ms_may_include_other_server_work
                rowWaitersMetric=distinct_requesting_transactions_per_schema_sample_10ms_not_wait_duration
                poolPendingMetric=Hikari_sample_10ms_not_DB_row_wait
                monitorConnection=one_extra_independent_connection_outside_Hikari_pool
                latencies=service_proxy_including_authorization_transaction_and_audit
                wallTime=gate_release_to_all_returns_excludes_fixtures_queries_cleanup_and_monitor_setup
                percentile=nearest_rank_per_batch_NaN_when_no_requests_in_category
                legacy=removed_historical_comparison_implementation
                %s
                """.formatted(mode, catalog, version, isolation,
                mode.equals("UNIQUE") ? "generated_column_UNIQUE_only_no_Doctor_lock" : "Doctor_row_PESSIMISTIC_WRITE_only_no_slot_UNIQUE",
                pool.getMinimumIdle(), pool.getConnectionTimeout(), System.getProperty("java.version"),
                System.getProperty("os.name"), Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory(), ddl));
        fixtures(100);
        List<String> violations = new ArrayList<>();
        for (int round = -2; round < 5; round++) {
            String phase = round < 0 ? "WARMUP" : "MEASURE";
            List<String> scenarios = new ArrayList<>(List.of("A", "B", "C"));
            Collections.rotate(scenarios, Math.floorMod(round, 3));
            for (String scenario : scenarios) {
                clearBookings();
                var requests = requests(scenario);
                Sample sample = execute(requests, create, true);
                Files.writeString(csv, String.format(Locale.ROOT,
                        "%s,%d,%s,%s,%d,100,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d,%d,%d,%s%n",
                        mode, isolation, scenario, phase, round, sample.count("SUCCESS"), sample.count("DUPLICATE"), sample.count("ERROR"),
                        sample.stored(), sample.active(), sample.elapsedMs(), 100_000.0 / sample.elapsedMs(),
                        sample.percentile("SUCCESS", .50), sample.percentile("SUCCESS", .95), sample.percentile("SUCCESS", .99),
                        sample.percentile("DUPLICATE", .50), sample.percentile("DUPLICATE", .95), sample.percentile("DUPLICATE", .99),
                        sample.globalLockMs(), sample.globalLockWaits(), sample.maxPoolPending(), sample.maxPoolActive(),
                        sample.maxDbWaiters(), sample.dbMetricStatus()), StandardOpenOption.APPEND);
                StringBuilder attempts = new StringBuilder();
                for (int i = 0; i < sample.attempts().size(); i++) {
                    Attempt a = sample.attempts().get(i);
                    attempts.append(String.format(Locale.ROOT, "%s,%s,%d,%d,%s,%.6f,%s%n",
                            scenario, phase, round, i, a.outcome(), a.nanos()/1_000_000.0, a.errorType()));
                }
                Files.writeString(raw, attempts, StandardOpenOption.APPEND);
                System.out.println("BENCHMARK " + key + " " + scenario + " " + phase + " " + round + " " + sample.summary());
                // Preserve failed rounds and finish the experiment; never count DB errors as rejections.
                if (sample.count("ERROR") != 0 || sample.count("SUCCESS") != (scenario.equals("A") ? 1 : 100)
                        || sample.count("DUPLICATE") != (scenario.equals("A") ? 99 : 0))
                    violations.add(scenario + "/" + phase + "/" + round + " " + sample.summary());
                try { assertSlots(requests, scenario.equals("A") ? 1 : 100); }
                catch (AssertionError error) { violations.add(scenario + "/" + phase + "/" + round + " slot verification failed: " + error.getMessage()); }
            }
        }
        assertThat(violations).as("All rounds recorded; correctness failures are not hidden").isEmpty();
    }
}
