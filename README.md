# Quarkus Qubit - Lambda-based Panache Query Extension

A Quarkus extension that enables type-safe, lambda-based queries on Panache entities with build-time transformation to JPA Criteria Queries.

## Overview

**Qubit** (Query Using SAfe Queries) transforms lambda expressions into optimized JPA Criteria Queries at build time, providing:

- **Type-safe queries**: Write queries in plain Java using lambda expressions with explicit types
- **Build-time transformation**: Lambda expressions are analyzed and transformed during compilation
- **Zero runtime overhead**: Transformed Criteria Queries are cached and executed directly
- **Fluent API**: JINQ-inspired method chaining for composing complex queries
- **IDE support**: Full code completion and refactoring support
- **No string-based queries**: Eliminate typos and runtime query errors

## Features

### Query Capabilities
- **Filtering**: Filter entities with complex predicates using `where()`
- **Projection**: Select specific fields, expressions, or DTOs using `select()`
- **Sorting**: Order results ascending or descending using `sortedBy()` / `sortedDescendingBy()`
- **Pagination**: Limit and skip results using `limit()` and `skip()`
- **Distinct**: Remove duplicates using `distinct()`
- **Aggregation**: Compute min, max, avg, sum, count using aggregation methods

### Expression Support
- **All comparison operators**: `==`, `!=`, `<`, `<=`, `>`, `>=`
- **Logical operators**: `&&`, `||`, `!`
- **Arithmetic operations**: `+`, `-`, `*`, `/`, `%` in predicates and projections
- **String methods**: `startsWith()`, `endsWith()`, `contains()`, `toLowerCase()`, `toUpperCase()`, `concat()`, `trim()`, `length()`
- **Null checks**: `== null`, `!= null`
- **Temporal types**: LocalDate, LocalTime, LocalDateTime comparisons with `isAfter()`, `isBefore()`
- **BigDecimal**: Full `compareTo()` support for precision arithmetic

## Installation

Add the extension to your Quarkus project:

```xml
<dependency>
    <groupId>io.quarkus.extension</groupId>
    <artifactId>quarkus-qubit</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

### Option 1: ActiveRecord Pattern (QubitEntity)

```java
@Entity
public class Person extends QubitEntity {
    public String firstName;
    public String lastName;
    public int age;
    public boolean active;
    public Double salary;
}

// Simple filtering
List<Person> adults = Person.where((Person p) -> p.age >= 18).toList();

// Complex composition
List<String> topCities = Person.where((Person p) -> p.active && p.salary > 100000)
                               .select((Person p) -> p.city)
                               .distinct()
                               .sortedBy((String city) -> city)
                               .limit(10)
                               .toList();
```

### Option 2: Repository Pattern (QubitRepository) - Recommended

```java
@ApplicationScoped
public class PersonRepository implements QubitRepository<Person, Long> {
}

@Path("/persons")
public class PersonResource {

    @Inject
    PersonRepository personRepository;

    @GET
    @Path("/adults")
    public List<Person> getAdults() {
        return personRepository.where((Person p) -> p.age >= 18).toList();
    }

    @GET
    @Path("/search")
    public List<PersonDTO> search(@QueryParam("minSalary") double minSalary) {
        return personRepository
            .where((Person p) -> p.active && p.salary > minSalary)
            .select((Person p) -> new PersonDTO(p.firstName, p.lastName))
            .sortedDescendingBy((PersonDTO dto) -> dto.lastName())
            .limit(100)
            .toList();
    }
}
```

## Fluent API Reference

### Entry Points

| Method | Description |
|--------|-------------|
| `where(p -> predicate)` | Filter entities matching the predicate |
| `select(p -> projection)` | Project to fields, expressions, or DTOs |
| `sortedBy(p -> key)` | Sort ascending by the key |
| `sortedDescendingBy(p -> key)` | Sort descending by the key |

### Intermediate Operations

| Method | Description |
|--------|-------------|
| `where(p -> predicate)` | Add additional filter (AND with previous) |
| `select(p -> projection)` | Transform elements |
| `sortedBy(p -> key)` | Add sort order (JINQ semantics: last wins as primary) |
| `sortedDescendingBy(p -> key)` | Add descending sort order |
| `limit(n)` | Limit results to n items |
| `skip(n)` | Skip first n results |
| `distinct()` | Remove duplicates |

### Terminal Operations

| Method | Description |
|--------|-------------|
| `toList()` | Execute and return all results as List |
| `getSingleResult()` | Execute expecting exactly one result |
| `findFirst()` | Execute and return Optional of first result |
| `count()` | Count matching entities |
| `exists()` | Check if any entity matches |

### Aggregation Operations

| Method | Description |
|--------|-------------|
| `min(p -> field)` | Find minimum value |
| `max(p -> field)` | Find maximum value |
| `avg(p -> field)` | Calculate average (returns Double) |
| `sumInteger(p -> field)` | Sum Integer values (returns Long) |
| `sumLong(p -> field)` | Sum Long values (returns Long) |
| `sumDouble(p -> field)` | Sum Double values (returns Double) |

## Usage Examples

### Filtering

```java
// Simple predicate
List<Person> adults = personRepository.where((Person p) -> p.age >= 18).toList();

// Multiple conditions (AND)
List<Person> activeAdults = personRepository
    .where((Person p) -> p.age >= 18)
    .where((Person p) -> p.active)
    .toList();

// Complex logical expressions
List<Person> results = personRepository.where((Person p) ->
    (p.age >= 25 && p.age <= 35) || (p.salary > 100000 && p.active)
).toList();
```

### Projection

```java
// Single field projection
List<String> names = personRepository.select((Person p) -> p.firstName).toList();

// Expression projection
List<String> fullNames = personRepository
    .select((Person p) -> p.firstName + " " + p.lastName)
    .toList();

// DTO projection
List<PersonDTO> dtos = personRepository
    .select((Person p) -> new PersonDTO(p.firstName, p.lastName, p.age))
    .toList();

// Combined filtering and projection
List<String> activeCities = personRepository
    .where((Person p) -> p.active)
    .select((Person p) -> p.city)
    .distinct()
    .toList();
```

### Sorting

```java
// Single sort
List<Person> byAge = personRepository.sortedBy((Person p) -> p.age).toList();

// Descending sort
List<Person> highestSalary = personRepository
    .sortedDescendingBy((Person p) -> p.salary)
    .limit(10)
    .toList();

// Multi-level sort (JINQ semantics: last call is primary)
List<Person> sorted = personRepository
    .sortedBy((Person p) -> p.firstName)    // Secondary
    .sortedBy((Person p) -> p.lastName)     // Primary
    .toList();
// SQL: ORDER BY lastName ASC, firstName ASC
```

### Pagination

```java
// Top 10
List<Person> top10 = personRepository
    .sortedDescendingBy((Person p) -> p.salary)
    .limit(10)
    .toList();

// Page 3 (skip 20, take 10)
List<Person> page3 = personRepository
    .sortedBy((Person p) -> p.id)
    .skip(20)
    .limit(10)
    .toList();
```

### Aggregation

```java
// Count
long activeCount = personRepository.where((Person p) -> p.active).count();

// Exists
boolean hasAdults = personRepository.where((Person p) -> p.age >= 18).exists();

// Min/Max
Integer minAge = personRepository.min((Person p) -> p.age).getSingleResult();
Double maxSalary = personRepository.max((Person p) -> p.salary).getSingleResult();

// Average
Double avgAge = personRepository.avg((Person p) -> p.age).getSingleResult();

// Sum
Long totalAge = personRepository.sumInteger((Person p) -> p.age).getSingleResult();
Double totalSalary = personRepository.sumDouble((Person p) -> p.salary).getSingleResult();

// Conditional aggregation
Double avgActiveSalary = personRepository
    .where((Person p) -> p.active)
    .avg((Person p) -> p.salary)
    .getSingleResult();
```

### String Operations

```java
List<Person> johns = personRepository.where((Person p) -> p.firstName.startsWith("John")).toList();
List<Person> smiths = personRepository.where((Person p) -> p.lastName.endsWith("Smith")).toList();
List<Person> gmail = personRepository.where((Person p) -> p.email.contains("@gmail.com")).toList();
List<Person> upper = personRepository.where((Person p) -> p.firstName.toLowerCase().equals("john")).toList();
```

### Null Checks

```java
List<Person> withEmail = personRepository.where((Person p) -> p.email != null).toList();
List<Person> withoutEmail = personRepository.where((Person p) -> p.email == null).toList();
```

### Temporal Types

```java
// Date comparisons
List<Person> bornAfter1990 = personRepository
    .where((Person p) -> p.birthDate.isAfter(LocalDate.of(1990, 1, 1)))
    .toList();

// DateTime comparisons
List<Person> recentlyCreated = personRepository
    .where((Person p) -> p.createdAt.isAfter(LocalDateTime.now().minusDays(7)))
    .toList();
```

### BigDecimal Comparisons

```java
// Product price queries
List<Product> expensive = productRepository
    .where((Product p) -> p.price.compareTo(new BigDecimal("1000.00")) > 0)
    .toList();
```

### Arithmetic in Predicates

```java
List<Person> retiring = personRepository.where((Person p) -> p.age + 5 >= 65).toList();
List<Person> doubleAge = personRepository.where((Person p) -> p.age * 2 > 60).toList();
```

## How It Works

### Build-Time Transformation

1. **Lambda Detection**: During compilation, Qubit scans bytecode for `invokedynamic` instructions
2. **AST Analysis**: Lambda expressions are analyzed to build an Abstract Syntax Tree
3. **Query Generation**: AST is transformed into JPA Criteria Query using Gizmo bytecode generation
4. **Entity/Repository Enhancement**: Static methods are injected at build time
5. **Registry Storage**: Generated executors are stored in `QueryExecutorRegistry`
6. **Runtime Execution**: Queries execute with zero overhead using cached executors

### Architecture

```
User Code:
  personRepository.where((Person p) -> p.age >= 18).toList()
         |
  [Bytecode Enhancement - generates query executor at build time]
         |
  QubitStreamImpl.where(QuerySpec spec)
         |
  QueryExecutorRegistry.executeListQuery(callSiteId, Person.class)
         |
  Generated Criteria Query Executor (built at compile time)
```

## Supported Operations

### Comparison Operators

| Operator | Example | JPA Criteria Method |
|----------|---------|---------------------|
| `==` | `p.age == 30` | `cb.equal()` |
| `!=` | `p.age != 30` | `cb.notEqual()` |
| `<` | `p.age < 30` | `cb.lt()` |
| `<=` | `p.age <= 30` | `cb.le()` |
| `>` | `p.age > 30` | `cb.gt()` |
| `>=` | `p.age >= 30` | `cb.ge()` |

### Logical Operators

| Operator | Example | JPA Criteria Method |
|----------|---------|---------------------|
| `&&` | `p.age > 25 && p.active` | `cb.and()` |
| `\|\|` | `p.age < 26 \|\| p.age > 40` | `cb.or()` |
| `!` | `!p.active` | `cb.not()` |

### String Methods

| Method | Example | JPA Criteria Method |
|--------|---------|---------------------|
| `startsWith()` | `p.name.startsWith("J")` | `cb.like(expr, "J%")` |
| `endsWith()` | `p.email.endsWith(".com")` | `cb.like(expr, "%.com")` |
| `contains()` | `p.email.contains("john")` | `cb.like(expr, "%john%")` |
| `toLowerCase()` | `p.name.toLowerCase()` | `cb.lower()` |
| `toUpperCase()` | `p.name.toUpperCase()` | `cb.upper()` |
| `trim()` | `p.name.trim()` | `cb.trim()` |
| `length()` | `p.name.length() > 5` | `cb.length()` |
| `concat()` | `p.first.concat(p.last)` | `cb.concat()` |

### Arithmetic Operators

| Operator | Example | JPA Criteria Method |
|----------|---------|---------------------|
| `+` | `p.age + 5` | `cb.sum()` |
| `-` | `p.age - 5` | `cb.diff()` |
| `*` | `p.age * 2` | `cb.prod()` |
| `/` | `p.age / 2` | `cb.quot()` |
| `%` | `p.age % 2` | `cb.mod()` |

### Aggregation Functions

| Function | Example | JPA Criteria Method |
|----------|---------|---------------------|
| `min()` | `Person.min(p -> p.age)` | `cb.min()` |
| `max()` | `Person.max(p -> p.age)` | `cb.max()` |
| `avg()` | `Person.avg(p -> p.salary)` | `cb.avg()` |
| `sumInteger()` | `Person.sumInteger(p -> p.age)` | `cb.sum()` |
| `sumLong()` | `Person.sumLong(p -> p.employeeId)` | `cb.sum()` |
| `sumDouble()` | `Person.sumDouble(p -> p.salary)` | `cb.sum()` |

## Testing

The extension includes a comprehensive test suite with 850+ tests covering all query combinations:

```bash
mvn clean test
```

Test categories:
- Basic queries (equality, comparison, null checks)
- Logical operators (AND, OR, NOT, complex expressions)
- String operations (startsWith, endsWith, contains, case conversion)
- Arithmetic operations (in predicates and projections)
- Temporal types (LocalDate, LocalTime, LocalDateTime)
- BigDecimal comparisons
- Fluent API (where, select, sortedBy, limit, skip, distinct)
- Projections (field, expression, DTO)
- Aggregation (min, max, avg, sum, count, exists)
- Pagination and sorting
- Repository pattern and ActiveRecord pattern parity

## Performance

- **Build time**: Lambda analysis adds minimal overhead during compilation
- **Runtime**: Zero overhead - pre-compiled Criteria Queries execute directly
- **Caching**: Query cache uses `ConcurrentHashMap` for thread-safe, high-performance lookups
- **Memory**: Only transformed queries are cached, not lambda instances
- **Optimization**: findFirst() automatically adds LIMIT 1

## Limitations

- **Explicit type parameters**: Required due to Java type erasure
- **Join queries**: Not yet supported (planned for future release)
- **Subqueries**: Not yet supported (planned for future release)
- **Relationship navigation**: Queries like `p.address.city` not yet supported
- **Collections**: `IN`, `MEMBER OF` not yet supported
- **Grouping**: GROUP BY not yet supported (planned for future release)

## Requirements

- Java 17 or higher
- Quarkus 3.17.0 or higher
- Hibernate ORM with Panache

## Building from Source

```bash
git clone https://github.com/your-org/quarkus-qubit.git
cd quarkus-qubit
mvn clean install
```

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

Apache License 2.0

## Credits

Built with:
- [Quarkus](https://quarkus.io/) - Supersonic Subatomic Java
- [Hibernate ORM Panache](https://quarkus.io/guides/hibernate-orm-panache) - Simplified persistence
- [Gizmo](https://github.com/quarkusio/gizmo) - Bytecode generation
- [ASM](https://asm.ow2.io/) - Bytecode analysis and manipulation
