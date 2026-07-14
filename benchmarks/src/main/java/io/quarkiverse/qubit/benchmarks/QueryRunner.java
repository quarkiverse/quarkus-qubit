package io.quarkiverse.qubit.benchmarks;

import java.util.List;

import io.quarkiverse.qubit.benchmarks.model.Person;
import io.quarkiverse.qubit.benchmarks.model.Phone;

/**
 * Contains all Qubit lambda call sites for build-time scanning.
 * Qubit's InvokeDynamicScanner processes these at build time to generate query executors.
 * Benchmark test classes delegate to these methods.
 */
public final class QueryRunner {

    private QueryRunner() {
    }

    public static List<Person> simpleWhere(int minAge) {
        return Person.where((Person p) -> p.age > minAge).toList();
    }

    public static List<String> projection() {
        return Person.select((Person p) -> p.firstName).toList();
    }

    public static List<Person> joinQuery() {
        return Person.join((Person p) -> p.phones)
                .where((Person p, Phone ph) -> ph.type.equals("mobile"))
                .toList();
    }

    public static List<?> groupByQuery() {
        return Person.groupBy((Person p) -> p.department).selectKey().toList();
    }

    public static Integer aggregation() {
        return Person.min((Person p) -> p.age).getSingleResult();
    }

    public static long countQuery(boolean active) {
        return Person.where((Person p) -> p.active == active).count();
    }
}
