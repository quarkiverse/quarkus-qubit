---
name: quarkus-qubit
description: "Type-safe, lambda-based queries for Quarkus Panache entities — translated to JPA Criteria Queries at build time with zero runtime overhead."
guide: https://docs.quarkiverse.io/quarkus-qubit/dev/
---

# Quarkus Qubit

Qubit adds a JINQ-inspired fluent query API to Quarkus Panache. Write queries as Java lambdas — Qubit analyzes the bytecode at build time and generates JPA Criteria Queries. No reflection, no runtime parsing, no overhead.

```xml
<dependency>
    <groupId>io.quarkiverse.qubit</groupId>
    <artifactId>quarkus-qubit</artifactId>
</dependency>
```

## Getting Started

**QubitRepository (recommended)** — inject a CDI bean, testable, familiar to Spring developers:

```java
@Entity
public class Person extends PanacheEntity {
    public String name;
    public int age;
    public boolean active;
}

@ApplicationScoped
public class PersonRepository implements QubitRepository<Person, Long> {
}

// Inject and use
@Inject PersonRepository repo;
List<Person> adults = repo.where(p -> p.age >= 18).toList();
```

**QubitEntity (ActiveRecord)** — static methods on entities, less boilerplate:

```java
@Entity
public class Person extends QubitEntity {
    public String name;
    public int age;
}

List<Person> adults = Person.where(p -> p.age >= 18).toList();
```

## Querying

```java
// Filtering — multiple where() calls combine with AND
repo.where(p -> p.active && p.age >= 18).toList();

// Projection — field access or DTO construction
repo.select(p -> p.name).toList();
repo.where(p -> p.active).select(p -> new PersonDTO(p.name, p.age)).toList();

// Sorting — last sortedBy() is primary, thenSortedBy() is secondary
repo.sortedBy(p -> p.lastName).thenSortedBy(p -> p.firstName).toList();
repo.sortedDescendingBy(p -> p.salary).toList();

// Null precedence (JPA 3.2)
repo.sortedBy(p -> p.name, Nulls.LAST).toList();

// Pagination
repo.where(p -> p.active).skip(20).limit(10).toList();

// Terminal operations
long count    = repo.where(p -> p.active).count();
boolean any   = repo.where(p -> p.active).exists();
Person single = repo.where(p -> p.id == 42L).getSingleResult();
Optional<Person> first = repo.where(p -> p.active).findFirst();

// Distinct
repo.select(p -> p.city).distinct().toList();

// Aggregations — return ScalarResult with getSingleResult() or findFirst()
Double avgSalary = repo.avg(p -> p.salary).getSingleResult();
Integer maxAge   = repo.max(p -> p.age).getSingleResult();
Integer minAge   = repo.min(p -> p.age).getSingleResult();
Long totalDays   = repo.sumLong(p -> p.daysWorked).getSingleResult();
```

## Supported Expressions

Inside lambdas, these operations are translated to JPA Criteria API equivalents:

| Category | Operations |
|----------|-----------|
| Comparison | `==`, `!=`, `<`, `<=`, `>`, `>=` |
| Logical | `&&`, `\|\|`, `!` |
| Arithmetic | `+`, `-`, `*`, `/`, `%` |
| String | `startsWith()`, `endsWith()`, `contains()`, `toLowerCase()`, `toUpperCase()`, `trim()`, `length()`, `substring()`, `replace()`, `indexOf()` |
| Math | `Math.abs()`, `Math.sqrt()`, `Math.ceil()`, `Math.floor()`, `QubitMath.round(value, decimals)` |
| Temporal | `isAfter()`, `isBefore()`, `isEqual()` on LocalDate, LocalDateTime, LocalTime |
| Null | `== null`, `!= null` |
| Collections | `List.contains(field)` → SQL `IN` |
| LIKE | `Qubit.like(field, "%pattern%")`, `Qubit.notLike(field, pattern)` |
| String extract | `Qubit.left(field, n)`, `Qubit.right(field, n)` |
| Date extract | `Qubit.quarter(dateField)`, `Qubit.week(dateField)` |
| Type cast | `Qubit.cast(field, TargetType.class)` |
| BigDecimal | `.compareTo()` for ordering comparisons |

## Joins

Join via collection relationships on entities:

```java
// Inner join — excludes entities without matching related entities
repo.join(p -> p.phones)
    .where((p, ph) -> ph.type.equals("mobile"))
    .select((p, ph) -> new PersonPhoneDTO(p.name, ph.number))
    .toList();

// Left outer join — includes all source entities
repo.leftJoin(p -> p.orders)
    .where((p, o) -> o.total > 100)
    .toList();

// on() vs where() — on() preserves null rows in left joins, where() filters them
repo.leftJoin(p -> p.orders)
    .on((p, o) -> o.status.equals("ACTIVE"))  // keeps persons with no orders
    .toList();

// Project one side
repo.join(p -> p.phones).selectSource().toList();   // source entities only
repo.join(p -> p.phones).selectJoined().toList();   // joined entities only
```

## Grouping & Aggregation

```java
// Group by with count
repo.groupBy(p -> p.department)
    .select(g -> new DeptCount(g.key(), g.count()))
    .toList();

// HAVING clause
repo.groupBy(p -> p.department)
    .having(g -> g.count() > 5)
    .selectKey()
    .toList();

// Multiple aggregations in one query
repo.groupBy(p -> p.department)
    .select(g -> new DeptStats(
        g.key(),
        g.count(),
        g.avg(p -> p.salary),
        g.min(p -> p.salary),
        g.max(p -> p.salary)))
    .toList();

// Group aggregation methods: key(), count(), countDistinct(), avg(), sumInteger(),
// sumLong(), sumDouble(), min(), max()
```

## Subqueries

```java
// Scalar subquery in a where() clause
repo.where(p -> p.salary > Subqueries.subquery(Person.class)
        .where(s -> s.department.equals("SALES"))
        .select(s -> s.salary)
        .avg())
    .toList();
```

## Migration from Panache

| Before (Panache HQL) | After (Qubit) |
|-----------------------|---------------|
| `Person.find("age >= ?1", 18).list()` | `Person.where(p -> p.age >= 18).toList()` |
| `Person.find("name like ?1", "%john%").list()` | `Person.where(p -> p.name.contains("john")).toList()` |
| `repo.find("active", true).count()` | `repo.where(p -> p.active).count()` |
| `Person.find("salary > ?1 and active", Sort.by("name"), sal)` | `Person.where(p -> p.salary > sal && p.active).sortedBy(p -> p.name).toList()` |

Panache methods (`findAll()`, `count()`, `deleteAll()`, etc.) still work — Qubit adds to Panache, it does not replace it.

## Configuration

All properties are build-time only (no runtime configuration):

```properties
# Restrict scanning to specific packages (comma-separated)
quarkus.qubit.scanning.include-packages=com.example.model
quarkus.qubit.scanning.exclude-packages=com.example.internal

# Log generated query executor classes during build
quarkus.qubit.logging.log-generated-classes=true

# Fail build on unsupported lambda patterns (default: true)
quarkus.qubit.failOnAnalysisError=true
```

## Testing

Standard `@QuarkusTest` patterns apply. Lambda queries behave identically in test and production:

```java
@QuarkusTest
class PersonRepositoryTest {
    @Inject PersonRepository repo;

    @Test
    void findsAdults() {
        List<Person> adults = repo.where(p -> p.age >= 18).toList();
        assertThat(adults).allMatch(p -> p.age >= 18);
    }
}
```

## Lambda Constraints

Qubit analyzes lambda bytecode at build time. Lambdas **must** contain only operations that map to JPA Criteria API. The following are **not supported**:

- **Method references** — `Person::getName` does not work; use `p -> p.name`
- **External method calls** — only field access and the supported operations listed above
- **Loops, try/catch, complex control flow** — lambdas must be single-expression
- **Mutable captured variables** — captured values must be effectively final
- **Side effects** — lambdas are analyzed, never executed at runtime
- **Getter methods** — use direct field access (`p.name`), not `p.getName()` (unless mapped as a JPA attribute)
- **Unsupported JDK methods** — only the string, math, and temporal methods listed above are recognized

If a lambda contains unsupported patterns, the build fails with a descriptive error (when `failOnAnalysisError=true`).

## Common Pitfalls

- **`.equals()` on non-String types** — use `==` for primitives and enums; `.equals()` is only recognized for String fields
- **Missing `@Entity`** — entities must be annotated or build-time analysis cannot find them
- **BigDecimal comparison** — `>`, `<` operators don't work on BigDecimal; use `.compareTo(other) > 0`
- **`on()` vs `where()` in left joins** — `where()` filters out null rows (acts like inner join); use `on()` to preserve them
- **Inherited Panache `count()` vs Qubit** — `Person.count()` counts all; `Person.where(p -> p.active).count()` counts filtered
- **DTO constructors** — `select(p -> new DTO(...))` requires a matching constructor; field order must match constructor parameter order
