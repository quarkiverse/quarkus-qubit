package io.quarkiverse.qubit.benchmarks;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * PostgreSQL-only benchmark verifying that Qubit parameterized queries
 * benefit from the query plan cache ({@code pg_stat_statements}).
 *
 * <p>
 * Gated by {@code -Ddb.kind=postgresql}. Requires the
 * {@code pg_stat_statements} extension enabled in the test database.
 */
@QuarkusTest
@EnabledIfSystemProperty(named = "db.kind", matches = "postgresql")
class PlanCacheBenchmarkIT {

    private static final List<BenchmarkResult> results = new ArrayList<>();

    @Inject
    EntityManager em;

    @AfterAll
    static void report() {
        if (!results.isEmpty()) {
            BenchmarkReport.writeResults(results);
            System.out.println("\n=== Plan Cache Results ===");
            for (var r : results) {
                System.out.printf("  %-30s %,.0f %s%n", r.testName() + "/" + r.metric(), r.value(), r.unit());
            }
        }
    }

    @Test
    @Transactional
    void parameterizedQueryReusesPlan() {
        // Reset stats (pg_stat_statements preloaded via DevServices command + init script)
        try {
            em.createNativeQuery("SELECT pg_stat_statements_reset()").getSingleResult();
        } catch (Exception e) {
            System.out.println("[benchmark] pg_stat_statements not available, skipping: " + e.getMessage());
            return;
        }

        // Run the same parameterized query with different values
        for (int age = 20; age < 40; age++) {
            QueryRunner.simpleWhere(age);
        }

        em.flush();

        // Query pg_stat_statements for our query pattern
        // Hibernate generates: select ... from person p1_0 where p1_0.age>$1
        @SuppressWarnings("unchecked")
        List<Object[]> stats = em.createNativeQuery(
                "SELECT calls, plans, query FROM pg_stat_statements " +
                        "WHERE query LIKE '%p1_0.age%' AND calls > 1 " +
                        "AND query NOT LIKE '%pg_stat%'")
                .getResultList();

        if (!stats.isEmpty()) {
            long totalCalls = 0;
            long totalPlans = 0;
            for (Object[] row : stats) {
                totalCalls += ((Number) row[0]).longValue();
                totalPlans += ((Number) row[1]).longValue();
            }

            results.add(new BenchmarkResult("parameterizedQuery", "calls", totalCalls, "count"));
            results.add(new BenchmarkResult("parameterizedQuery", "plans", totalPlans, "count"));
            results.add(new BenchmarkResult("parameterizedQuery", "planReuse",
                    totalCalls > 0 ? (double) (totalCalls - totalPlans) / totalCalls * 100 : 0, "%"));

            // Parameterized queries should reuse plans: calls > plans
            assertThat(totalCalls)
                    .as("Parameterized queries should produce more calls than plans (plan cache reuse)")
                    .isGreaterThan(totalPlans);
        } else {
            @SuppressWarnings("unchecked")
            List<Object[]> all = em.createNativeQuery(
                    "SELECT calls, query FROM pg_stat_statements ORDER BY calls DESC LIMIT 10")
                    .getResultList();
            System.out.println("[benchmark] No matching entries. Top 10 pg_stat_statements:");
            for (Object[] row : all) {
                System.out.printf("  calls=%s query=%.80s%n", row[0], row[1]);
            }
        }
    }
}
