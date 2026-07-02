---
name: quarkus-qubit
description: "Type-safe, lambda-based queries for Quarkus Panache entities — translated to JPA Criteria Queries at build time with zero runtime overhead."
guide: https://docs.quarkiverse.io/quarkus-qubit/dev/
---

# Quarkus Qubit

Qubit adds a JINQ-inspired fluent query API to Quarkus Panache. Write queries as Java lambdas — Qubit analyzes bytecode at build time and generates JPA Criteria Queries.

```xml
<dependency>
    <groupId>io.quarkiverse.qubit</groupId>
    <artifactId>quarkus-qubit</artifactId>
</dependency>
```

Key imports:

```java
import io.quarkiverse.qubit.*;
import static io.quarkiverse.qubit.Subqueries.subquery;
import jakarta.persistence.criteria.Nulls;
```

## Getting Started

```java
@Entity
public class Person extends PanacheEntity {
    public String name;
    public int age;
    public boolean active;
}
```

**QubitRepository (recommended)** — CDI bean, testable:

```java
@ApplicationScoped
public class PersonRepository implements QubitRepository<Person, Long> {
}

@Inject PersonRepository repo;
List<Person> adults = repo.where((Person p) -> p.age >= 18).toList();
```

**QubitEntity (ActiveRecord)** — static methods, less boilerplate (extend `QubitEntity` instead of `PanacheEntity`):

```java
List<Person> adults = Person.where((Person p) -> p.age >= 18).toList();
```

**Always use explicit lambda parameter types** — `(Person p) ->`, not `p ->`. Required for ActiveRecord pattern with `var`; recommended everywhere for consistency.

## Querying

```java
// Multiple where() calls combine with AND
repo.where((Person p) -> p.active && p.age >= 18).toList();
repo.where((Person p) -> p.active).where((Person p) -> p.age >= 18).toList();  // equivalent

// Field projection
repo.select((Person p) -> p.name).toList();

// DTO projection — constructor parameter order must match field order
repo.where((Person p) -> p.active)
    .select((Person p) -> new PersonDTO(p.name, p.age)).toList();

// Expression projections — arithmetic, string concat
repo.select((Person p) -> p.salary * 1.1).toList();
repo.select((Person p) -> p.firstName + " " + p.lastName).toList();

// Ternary → SQL CASE WHEN
repo.select((Person p) -> p.active ? p.salary : 0.0).toList();

// Sorting — last sortedBy() is primary, thenSortedBy() is secondary
repo.sortedBy((Person p) -> p.lastName)
    .thenSortedBy((Person p) -> p.firstName).toList();
repo.sortedDescendingBy((Person p) -> p.salary).toList();
repo.sortedBy((Person p) -> p.name, Nulls.LAST).toList();  // null precedence

// Pagination
repo.where((Person p) -> p.active).skip(20).limit(10).toList();

// Terminal operations
long count    = repo.where((Person p) -> p.active).count();
boolean any   = repo.where((Person p) -> p.active).exists();
Person single = repo.where((Person p) -> p.id == 42L).getSingleResult();
Optional<Person> first = repo.where((Person p) -> p.active).findFirst();
repo.select((Person p) -> p.city).distinct().toList();

// Aggregations — return ScalarResult<T>, call getSingleResult() or findFirst()
Double avgSalary = repo.avg((Person p) -> p.salary).getSingleResult();
Integer maxAge   = repo.max((Person p) -> p.age).getSingleResult();
Long totalDays   = repo.sumLong((Person p) -> p.daysWorked).getSingleResult();
```

## Supported Expressions

Inside lambdas, these operations translate to JPA Criteria API equivalents:

| Category | Operations |
|----------|-----------|
| Comparison | `==`, `!=`, `<`, `<=`, `>`, `>=` |
| Logical | `&&`, `\|\|`, `!` |
| Arithmetic | `+`, `-`, `*`, `/`, `%` |
| String | `startsWith()`, `endsWith()`, `contains()`, `toLowerCase()`, `toUpperCase()`, `trim()`, `length()`, `substring()`, `replace()`, `indexOf()` |
| Math | `Math.abs()`, `Math.sqrt()`, `Math.ceil()`, `Math.floor()`, `Math.exp()`, `Math.log()`, `Math.pow()`, `Math.round()`, `QubitMath.round(value, decimals)`, `Integer.signum()`, `Long.signum()` |
| Temporal comparison | `isAfter()`, `isBefore()`, `isEqual()` on LocalDate, LocalDateTime, LocalTime |
| Temporal extract | `getYear()`, `getMonth()`, `getDayOfMonth()`, `getHour()`, `getMinute()`, `getSecond()`, `Qubit.quarter(dateField)`, `Qubit.week(dateField)` |
| Null | `== null`, `!= null` |
| Collections | `List.contains(field)` → SQL `IN` |
| LIKE | `Qubit.like(field, "%pattern%")`, `Qubit.notLike(field, pattern)` |
| String extract | `Qubit.left(field, n)`, `Qubit.right(field, n)` |
| Type cast | `Qubit.cast(field, TargetType.class)`, `Integer.parseInt(field)`, `Long.parseLong(field)` |
| BigDecimal | `.compareTo()` for ordering comparisons |
| Ternary | `condition ? trueExpr : falseExpr` → SQL `CASE WHEN` |

## Relationship Navigation

Navigate `@ManyToOne` relationships by chaining field access — Qubit generates JPA path expressions:

```java
// Two-level: entity → related entity field
repo.where((Person p) -> p.department.name.equals("Engineering")).toList();

// Three-level: entity → related → related field
phoneRepo.where((Phone ph) -> ph.owner.department.name.equals("Engineering")).toList();

// Projection through relationships
phoneRepo.select((Phone ph) -> ph.owner.department.name).toList();

// All supported operations work on navigated fields
phoneRepo.where((Phone ph) -> ph.owner.department.budget >= 300000).toList();
phoneRepo.where((Phone ph) -> ph.owner.department.name.startsWith("Human")).toList();
```

For `@OneToMany` / `@ManyToMany` traversal, use `join()` or `leftJoin()` instead.

## Joins

Join via collection relationships on entities:

```java
// Inner join — excludes entities without matching related entities
repo.join((Person p) -> p.phones)
    .where((Person p, Phone ph) -> ph.type.equals("mobile"))
    .select((Person p, Phone ph) -> new PersonPhoneDTO(p.name, ph.number))
    .toList();

// Left outer join — includes all source entities
repo.leftJoin((Person p) -> p.orders)
    .where((Person p, Order o) -> o.total > 100)
    .toList();

// on() vs where() — on() preserves null rows in left joins, where() filters them
repo.leftJoin((Person p) -> p.orders)
    .on((Person p, Order o) -> o.status.equals("ACTIVE"))  // keeps persons with no orders
    .toList();

// Project one side
repo.join((Person p) -> p.phones).selectSource().toList();   // source entities only
repo.join((Person p) -> p.phones).selectJoined().toList();   // joined entities only
```

## Grouping & Aggregation

```java
// Group by with count
repo.groupBy((Person p) -> p.department)
    .select((Group<Person, Department> g) -> new DeptCount(g.key(), g.count()))
    .toList();

// HAVING clause
repo.groupBy((Person p) -> p.department)
    .having((Group<Person, Department> g) -> g.count() > 5)
    .selectKey()
    .toList();

// Multiple aggregations in one query
repo.groupBy((Person p) -> p.department)
    .select((Group<Person, Department> g) -> new DeptStats(
        g.key(),
        g.count(),
        g.avg((Person p) -> p.salary),
        g.min((Person p) -> p.salary),
        g.max((Person p) -> p.salary)))
    .toList();

// Group aggregation methods: key(), count(), countDistinct(), avg(), sumInteger(),
// sumLong(), sumDouble(), min(), max()
```

## Subqueries

Use `Subqueries.subquery(EntityClass.class)` inside `where()` clauses. The aggregation method takes a field selector directly — there is no `select()` on SubqueryBuilder.

```java
// Scalar aggregation — persons earning above average salary
repo.where((Person p) -> p.salary > subquery(Person.class)
        .avg((Person s) -> s.salary))
    .toList();

// Filtered subquery — above average salary within a department
repo.where((Person p) -> p.salary > subquery(Person.class)
        .where((Person s) -> s.department.name.equals("Sales"))
        .avg((Person s) -> s.salary))
    .toList();

// Multiple where() calls chain with AND
repo.where((Person p) -> p.salary > subquery(Person.class)
        .where((Person s) -> s.active)
        .where((Person s) -> s.department.name.equals("Sales"))
        .avg((Person s) -> s.salary))
    .toList();

// Cross-entity subquery — departments with budget above total salary
deptRepo.where((Department d) -> d.budget > subquery(Person.class)
        .sum((Person p) -> p.salary))
    .toList();
```

SubqueryBuilder methods:

| Method | SQL |
|--------|-----|
| `.avg(selector)` | `SELECT AVG(field)` |
| `.sum(selector)` | `SELECT SUM(field)` |
| `.min(selector)` | `SELECT MIN(field)` |
| `.max(selector)` | `SELECT MAX(field)` |
| `.count()` / `.count(predicate)` | `SELECT COUNT(*)` |
| `.exists(predicate)` | `EXISTS (SELECT 1 ... WHERE predicate)` |
| `.notExists(predicate)` | `NOT EXISTS (...)` |
| `.in(field, selector)` / `.in(field, selector, predicate)` | `field IN (SELECT ...)` |
| `.notIn(field, selector)` / `.notIn(field, selector, predicate)` | `field NOT IN (SELECT ...)` |

All methods accept an optional `.where(predicate)` chain before the terminal.

## Inheritance

For JPA entity inheritance hierarchies (`@Inheritance`), use `instanceof` pattern matching or explicit casts:

```java
// Type filtering — find all dogs
animalRepo.where((Animal a) -> a instanceof Dog).toList();

// Pattern matching with subclass field access
animalRepo.where((Animal a) -> a instanceof Dog d && d.breed.equals("Labrador")).toList();

// Explicit cast (alternative syntax)
animalRepo.where((Animal a) -> a instanceof Dog && ((Dog) a).breed.equals("Labrador")).toList();

// Combine parent + subclass fields
animalRepo.where((Animal a) -> a instanceof Dog d && d.trained && a.weight > 20).toList();
```

Both forms generate JPA `cb.treat(root, Subclass.class)`.

## Migration from Panache

| Before (Panache HQL) | After (Qubit) |
|-----------------------|---------------|
| `Person.find("age >= ?1", 18).list()` | `Person.where((Person p) -> p.age >= 18).toList()` |
| `Person.find("name like ?1", "%john%").list()` | `Person.where((Person p) -> p.name.contains("john")).toList()` |
| `repo.find("active", true).count()` | `repo.where((Person p) -> p.active).count()` |
| `Person.find("salary > ?1 and active", Sort.by("name"), sal)` | `Person.where((Person p) -> p.salary > sal && p.active).sortedBy((Person p) -> p.name).toList()` |

Panache methods still work alongside Qubit.

## Configuration

All properties are build-time only:

```properties
quarkus.qubit.scanning.include-packages=com.example.model
quarkus.qubit.scanning.exclude-packages=com.example.internal
quarkus.qubit.logging.log-generated-classes=true
quarkus.qubit.failOnAnalysisError=true
```

## Testing

Standard `@QuarkusTest` with `@Inject` repository. Lambda queries behave identically in test and production — no special test configuration needed.

## Lambda Constraints

Qubit analyzes lambda bytecode at build time. Lambdas **must** contain only operations that map to JPA Criteria API. The following are **not supported**:

- **Implicit lambda types** — always use `(Person p) ->`, not `p ->`; required for ActiveRecord, recommended everywhere
- **Method references** — `Person::getName` does not work; use `(Person p) -> p.name`
- **External method calls** — only field access and the supported operations listed above; unsupported JDK methods cause build failure
- **Loops, try/catch, complex control flow** — lambdas must be single-expression
- **Mutable captured variables** — captured values must be effectively final
- **Side effects** — lambdas are analyzed, never executed at runtime
- **Getter methods** — use direct field access (`p.name`), not `p.getName()` (unless mapped as a JPA attribute)
- **Switch expressions** — not supported in lambdas

Unsupported patterns fail the build with a descriptive error (when `failOnAnalysisError=true`).

## Common Pitfalls

- **`.equals()` on non-String types** — use `==` for primitives and enums; `.equals()` is only recognized for String fields
- **Missing `@Entity`** — entities must be annotated or build-time analysis cannot find them
- **BigDecimal comparison** — `>`, `<` operators don't work on BigDecimal; use `.compareTo(other) > 0`
- **`on()` vs `where()` in left joins** — `where()` filters out null rows (acts like inner join); use `on()` to preserve them
- **Inherited Panache `count()` vs Qubit** — `Person.count()` counts all; `Person.where((Person p) -> p.active).count()` counts filtered
- **DTO constructors** — `select((Person p) -> new DTO(...))` requires a matching constructor; field order must match constructor parameter order
- **Relationship navigation** — use path expressions (`p.department.name`) for `@ManyToOne`; use `join()` / `leftJoin()` for `@OneToMany` and `@ManyToMany`
- **`select()` changes stream type** — `repo.select((Person p) -> p.name)` returns `QubitStream<String>`; subsequent chained operations apply to the projected type
- **No `select()` on SubqueryBuilder** — use `.avg((Person s) -> s.salary)` directly, not `.select(...).avg()`
