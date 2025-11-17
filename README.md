# Quarkus Qusaq - Lambda-based Panache Query Extension

A Quarkus extension that enables type-safe, lambda-based queries on Panache entities with build-time transformation to JPA Criteria Queries.

## Overview

**Qusaq** (Query Using SAfe Queries) transforms lambda expressions into optimized JPA Criteria Queries at build time, providing:

- **Type-safe queries**: Write queries in plain Java using lambda expressions with explicit types
- **Build-time transformation**: Lambda expressions are analyzed and transformed during compilation
- **Zero runtime overhead**: Transformed Criteria Queries are cached and executed directly
- **IDE support**: Full code completion and refactoring support
- **No string-based queries**: Eliminate typos and runtime query errors

## Features

- ✅ **Field access and getter methods**: Query using direct field access or getters
- ✅ **All comparison operators**: `==`, `!=`, `<`, `<=`, `>`, `>=`
- ✅ **Logical operators**: `&&`, `||`, `!`
- ✅ **Arithmetic operations**: `+`, `-`, `*`, `/`, `%` in predicates
- ✅ **String methods**: `startsWith()`, `endsWith()`, `contains()`
- ✅ **Null checks**: `== null`, `!= null`
- ✅ **Count and exists queries**: Optimized counting and existence checks
- ✅ **Multiple entity types**: Works with any Panache entity
- ✅ **Complex nested conditions**: Combine multiple predicates with parentheses
- ✅ **Bytecode enhancement**: Static methods injected into entity classes (application code only)

## Installation

Add the extension to your Quarkus project:

```xml
<dependency>
    <groupId>io.quarkus.extension</groupId>
    <artifactId>quarkus-qusaq</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## Quick Start

### 1. Define Your Entity

```java
@Entity
public class Person extends PanacheEntity {
    public String firstName;
    public String lastName;
    public int age;
    public boolean active;
    public Double salary;
}
```

### 2. Write Type-Safe Queries

```java
@Path("/persons")
public class PersonResource {

    @GET
    @Path("/adults")
    public List<Person> getAdults() {
        // Explicit type parameter required due to Java type erasure
        return Person.findWhere((Person p) -> p.age >= 18);
    }

    @GET
    @Path("/active")
    public List<Person> getActive() {
        return Person.findWhere((Person p) -> p.active && p.age > 25);
    }
}
```

> **Why Explicit Types?** Java's type erasure requires explicit lambda parameter types for the compiler to resolve field access. This is the same pattern used by Java Streams and other functional APIs.

### 3. Count and Existence Checks

```java
@GET
@Path("/stats")
public Stats getStats() {
    // Explicit type parameters required
    long activeCount = Person.countWhere((Person p) -> p.active);
    boolean hasAdults = Person.exists((Person p) -> p.age >= 18);

    return new Stats(activeCount, hasAdults);
}
```

## API Summary

All code (application and tests) uses the same syntax with explicit type parameters:

```java
// List query
Person.findWhere((Person p) -> p.age >= 18)

// Count query
Person.countWhere((Person p) -> p.active)

// Existence check
Person.exists((Person p) -> p.email != null)
```

## Usage Examples

### Complex Queries

```java
@GET
@Path("/search")
public List<Person> search(@QueryParam("minAge") int minAge) {
    return Person.findWhere((Person p) ->
        (p.age > minAge && p.active) ||
        (p.salary > 80000 && p.firstName.startsWith("J"))
    );
}
```

### String Operations

```java
List<Person> johns = Person.findWhere((Person p) -> p.firstName.startsWith("John"));
List<Person> smiths = Person.findWhere((Person p) -> p.lastName.endsWith("Smith"));
List<Person> gmail = Person.findWhere((Person p) -> p.email.contains("@gmail.com"));
```

### Null Checks

```java
List<Person> withEmail = Person.findWhere((Person p) -> p.email != null);
List<Person> withoutEmail = Person.findWhere((Person p) -> p.email == null);
```

### Arithmetic Operations

```java
List<Person> retiring = Person.findWhere((Person p) -> p.age + 5 >= 65);
List<Person> doubleAge = Person.findWhere((Person p) -> p.age * 2 > 60);
```

## How It Works

### Build-Time Transformation

1. **Lambda Detection**: During compilation, Qusaq scans bytecode for `invokedynamic` instructions
2. **AST Analysis**: Lambda expressions are analyzed to build an Abstract Syntax Tree
3. **Query Generation**: AST is transformed into JPA Criteria Query using Gizmo
4. **Bytecode Enhancement**: Static methods are injected into entity classes (application code only)
5. **Registry Storage**: Generated executors are stored in `QueryExecutorRegistry`
6. **Runtime Execution**: Queries execute with zero overhead using cached executors

### Architecture

```
User Code:
  Person.findWhere((Person p) -> p.age >= 18)
         ↓
  [Bytecode Enhancement - injects static method at build time]
         ↓
  Person.findWhere(QuerySpec spec)
         ↓
  QusaqOperations.findWhere(Person.class, spec)
         ↓
  QueryExecutorRegistry.executeListQuery(callSiteId, Person.class)
         ↓
  Generated Criteria Query Executor (built at compile time)
```

## API Reference

### Static Methods (Injected via Bytecode Enhancement)

Available on all entities extending `QusaqEntity` or `PanacheEntity`:

```java
// Injected by bytecode enhancement at build time
public static List<Person> findWhere(QuerySpec<Person, Boolean> spec)
public static long countWhere(QuerySpec<Person, Boolean> spec)
public static boolean exists(QuerySpec<Person, Boolean> spec)
```

**Usage with explicit type parameter:**
```java
Person.findWhere((Person p) -> p.age >= 18)
Person.countWhere((Person p) -> p.active)
Person.exists((Person p) -> p.email != null)
```

## Supported Operations

### Comparison Operators

| Operator | Example | JPA Criteria Method |
|----------|---------|---------------------|
| `==` | `p.age == 30` | `cb.equal()` |
| `!=` | `p.age != 30` | `cb.not(cb.equal())` |
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

### Arithmetic Operators

| Operator | Example | JPA Criteria Method |
|----------|---------|---------------------|
| `+` | `p.age + 5` | `cb.sum()` |
| `-` | `p.age - 5` | `cb.diff()` |
| `*` | `p.age * 2` | `cb.prod()` |
| `/` | `p.age / 2` | `cb.quot()` |
| `%` | `p.age % 2` | `cb.mod()` |

## Testing

The extension includes a comprehensive test suite covering all query combinations:

```bash
mvn clean test
```

Test categories:
- Equality and inequality tests
- Comparison operator tests
- Logical operator combinations
- String method tests
- Null checks
- Count and exists queries
- Arithmetic operations
- Complex nested conditions

## Performance

- **Build time**: Lambda analysis adds minimal overhead during compilation
- **Runtime**: Zero overhead - pre-compiled Criteria Queries execute directly
- **Caching**: Query cache uses `ConcurrentHashMap` for thread-safe, high-performance lookups
- **Memory**: Only transformed queries are cached, not lambda instances

## Limitations

- **Explicit type parameters**: Required due to Java type erasure
- **Join queries**: Not yet supported (planned for future release)
- **Subqueries**: Not yet supported (planned for future release)
- **Aggregations**: Limited to count (sum, avg, min, max planned)
- **Collections**: `IN`, `MEMBER OF` not yet supported

## Requirements

- Java 17 or higher
- Quarkus 3.17.0 or higher
- Hibernate ORM with Panache

## Building from Source

```bash
git clone https://github.com/your-org/quarkus-qusaq.git
cd quarkus-qusaq
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
