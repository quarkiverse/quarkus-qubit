# Quarkus Qubit

A Quarkus extension that enables type-safe, lambda-based queries on Panache entities with build-time transformation to JPA Criteria Queries.

## Overview

**Qubit** (QUery Bytecode Into Typed-queries) transforms lambda expressions into JPA Criteria Queries at build time:

- **Type-safe**: Full IDE support with compile-time verification
- **Zero overhead**: Queries are pre-compiled, not interpreted at runtime
- **Fluent API**: JINQ-inspired method chaining for readable queries

## Installation

**Maven**
```xml
<dependency>
    <groupId>io.quarkiverse.qubit</groupId>
    <artifactId>quarkus-qubit</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**Gradle**
```groovy
implementation 'io.quarkiverse.qubit:quarkus-qubit:1.0.0-SNAPSHOT'
```

## Quick Start

### Repository Pattern (Recommended)

```java
@ApplicationScoped
public class PersonRepository implements QubitRepository<Person, Long> {
}

@Inject PersonRepository personRepository;

// Filtering
List<Person> adults = personRepository.where((Person p) -> p.age >= 18).toList();

// Projection to DTO
List<PersonDTO> dtos = personRepository
    .where((Person p) -> p.active)
    .select((Person p) -> new PersonDTO(p.firstName, p.lastName))
    .toList();

// Aggregation
Double avgSalary = personRepository.avg((Person p) -> p.salary).getSingleResult();
```

### ActiveRecord Pattern

```java
@Entity
public class Person extends QubitEntity {
    public String firstName;
    public int age;
    public boolean active;
}

List<Person> adults = Person.where((Person p) -> p.age >= 18).toList();
```

## Features

### Query Operations

| Operation | Example |
|-----------|---------|
| Filter | `.where((Person p) -> p.age >= 18)` |
| Project | `.select((Person p) -> p.firstName)` |
| Sort | `.sortedBy((Person p) -> p.lastName)` |
| Paginate | `.skip(10).limit(20)` |
| Distinct | `.distinct()` |

### Terminal Operations

| Method | Description |
|--------|-------------|
| `toList()` | Returns all results as a list |
| `getSingleResult()` | Returns exactly one result |
| `findFirst()` | Returns an Optional for the first result |
| `count()` | Returns the count of matching entities |
| `exists()` | Returns true if any results match |

### Aggregations

Compute aggregate values across entities. Use `sumLong()` and `sumDouble()` for other numeric types.

```java
personRepository.min((Person p) -> p.age).getSingleResult();
personRepository.max((Person p) -> p.salary).getSingleResult();
personRepository.avg((Person p) -> p.salary).getSingleResult();
personRepository.sumInteger((Person p) -> p.age).getSingleResult();
```

### Join Queries

Join related entities with access to both sides in predicates and projections.

```java
// Inner join - excludes persons without phones
personRepository.join((Person p) -> p.phones)
    .where((Person p, Phone ph) -> ph.type.equals("mobile"))
    .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
    .toList();

// Left join - includes persons without phones (ph may be null)
personRepository.leftJoin((Person p) -> p.phones)
    .where((Person p, Phone ph) -> ph == null || ph.type.equals("mobile"))
    .toList();
```

### GROUP BY

Group entities and compute aggregates per group. The `Group` interface provides `key()`, `count()`, `avg()`, `min()`, `max()`, and `sum*()` methods.

```java
Person.groupBy((Person p) -> p.department.name)
    .having((Group<Person, String> g) -> g.count() > 5)
    .select((Group<Person, String> g) -> new DeptStats(
        g.key(), g.count(), g.avg((Person p) -> p.salary)
    ))
    .toList();
```

### Subqueries

Use subqueries for scalar comparisons, existence checks, and membership tests. Supports `avg()`, `sum()`, `min()`, `max()`, `count()`, `exists()`, `notExists()`, `in()`, and `notIn()`.

```java
import static io.quarkiverse.qubit.Subqueries.*;

// Scalar comparison
Person.where((Person p) -> p.salary > subquery(Person.class).avg(q -> q.salary)).toList();

// EXISTS
Person.where((Person p) -> subquery(Phone.class).exists(ph -> ph.owner.id.equals(p.id))).toList();

// IN
Person.where((Person p) -> subquery(Department.class)
    .in(p.department.id, d -> d.id, d -> d.budget > 100000)
).toList();
```

### IN Clause

```java
List<String> names = List.of("John", "Jane", "Alice");
personRepository.where((Person p) -> names.contains(p.firstName)).toList();
```

### Supported Expressions

| Category | Examples |
|----------|----------|
| Comparison | `==`, `!=`, `<`, `<=`, `>`, `>=` |
| Logical | `&&`, `\|\|`, `!` |
| Arithmetic | `+`, `-`, `*`, `/`, `%` |
| String | `startsWith()`, `endsWith()`, `contains()`, `toLowerCase()`, `toUpperCase()`, `trim()`, `length()`, `substring()` |
| Null | `== null`, `!= null` |
| Temporal | `isAfter()`, `isBefore()`, `isEqual()` for LocalDate, LocalDateTime, LocalTime |
| BigDecimal | `compareTo()` |

## Requirements

- Java 21+
- Quarkus 3.29+
- Hibernate ORM with Panache
- GraalVM 25+ (optional, for native compilation)

## Building from Source

```bash
git clone https://github.com/quarkiverse/quarkus-qubit.git
cd quarkus-qubit
./mvnw clean install
```

## License

Apache License 2.0
