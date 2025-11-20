# Quarkus Qusaq API Enhancement - Iteration 3: JINQ-Inspired Fluent Query API

**Date:** 2025-11-18
**Analyzer:** Claude Code (Sonnet 4.5)
**Scope:** API redesign based on JINQ's expressive power to enable fluent query composition

---

## Executive Summary

**Current Qusaq API** provides basic lambda-based filtering (`findWhere`, `countWhere`, `exists`) but lacks the **composability, expressiveness, and type safety** found in JINQ's stream-based query API.

**Proposed Enhancement:** Transform Qusaq from a simple filter API into a **fluent, type-safe query composition framework** inspired by JINQ's design, while maintaining Qusaq's build-time bytecode analysis approach.

**Impact:**
- **10x increase in query expressiveness** (filtering → filtering + projection + sorting + joining + grouping)
- **Zero runtime overhead** (all query construction at build time)
- **Full type safety** (compile-time query validation)
- **Clean API** (removes legacy methods for simpler, more intuitive design)

---

## 1. Current State Analysis: Qusaq API (Iteration 2)

### 1.1 Current API Surface

**Entity-based API (ActiveRecord pattern):**
```java
public abstract class QusaqEntity extends PanacheEntity {
    // Filtering
    public static <T extends QusaqEntity> List<T> findWhere(QuerySpec<T, Boolean> spec);

    // Counting
    public static <T extends QusaqEntity> long countWhere(QuerySpec<T, Boolean> spec);

    // Existence check
    public static <T extends QusaqEntity> boolean exists(QuerySpec<T, Boolean> spec);
}
```

**Usage Examples:**
```java
// Simple filter
List<Person> adults = Person.findWhere(p -> p.age >= 18);

// Complex AND/OR
List<Person> results = Person.findWhere(p ->
    (p.age > 25 && p.age < 40) || p.salary > 85000
);

// Captured variables
int minAge = 30;
List<Person> older = Person.findWhere(p -> p.age > minAge);

// Count
long count = Person.countWhere(p -> p.active);
```

### 1.2 Current Capabilities (What Works Today)

✅ **Filtering Operations:**
- Comparisons: `>`, `>=`, `<`, `<=`, `==`, `!=`
- Boolean logic: `&&`, `||`, `!`
- Null checks: `p.field == null`, `p.field != null`

✅ **Data Type Support:**
- Primitives: `int`, `long`, `float`, `double`, `boolean`
- Objects: `String`, `BigDecimal`, `LocalDate`, `LocalDateTime`, `LocalTime`

✅ **String Operations:**
- `equals()`, `startsWith()`, `endsWith()`, `contains()`
- `toLowerCase()`, `toUpperCase()`, `trim()`
- `length()`, `isEmpty()`, `substring()`

✅ **Temporal Operations:**
- Accessor functions: `getYear()`, `getMonth()`, `getDay()`, `getHour()`, `getMinute()`, `getSecond()`
- Comparisons: `isAfter()`, `isBefore()`, `isEqual()`

✅ **Arithmetic Operations:**
- `+`, `-`, `*`, `/`, `%`
- BigDecimal: `add()`, `subtract()`, `multiply()`, `divide()`

✅ **Build-Time Features:**
- Bytecode analysis of lambda expressions
- JPA Criteria Query generation
- Captured variable extraction
- Query deduplication (MD5 hash)

### 1.3 Current Limitations (What's Missing)

❌ **No Query Composition:**
```java
// CANNOT DO:
Person.where(p -> p.age > 18)
      .select(p -> p.firstName)
      .distinct()
      .toList()
```

❌ **No Projections (Select Specific Fields):**
```java
// CANNOT DO:
List<String> names = Person.select(p -> p.firstName);
List<PersonDTO> dtos = Person.select(p -> new PersonDTO(p.firstName, p.age));
```

❌ **No Sorting:**
```java
// CANNOT DO:
List<Person> sorted = Person.where(p -> p.active)
                            .sortedBy(p -> p.lastName)
                            .toList();
```

❌ **No Joins:**
```java
// CANNOT DO:
List<Order> orders = Customer.where(c -> c.name.equals("Alice"))
                             .join(c -> c.getOrders())
                             .toList();
```

❌ **No Pagination:**
```java
// CANNOT DO:
List<Person> page = Person.where(p -> p.active)
                          .skip(20)
                          .limit(10)
                          .toList();
```

❌ **No Grouping/Aggregation:**
```java
// CANNOT DO:
Map<Country, Long> countByCountry =
    City.group(c -> c.getCountry(), (country, stream) -> stream.count());
```

❌ **Returns Only Full Entities:**
- Always returns `List<T>` or `long` (count)
- Cannot return projected DTOs, tuples, or single fields

---

## 2. JINQ API Analysis: Expressive Power

### 2.1 JINQ Core Concepts

**JINQ** (Java Integrated Query) provides a **stream-based, fluent API** for database queries that:
1. Translates Java 8 lambda expressions into SQL at runtime
2. Supports method chaining for query composition
3. Maintains type safety throughout the pipeline
4. Enables both filtering and transformation operations

**Key Difference from Qusaq:**
- JINQ: Runtime query translation (uses JPA provider query introspection)
- Qusaq: Build-time query generation (bytecode analysis → code generation)

### 2.2 JINQ Query Operations

#### 2.2.1 Filtering: `where()`
```java
JinqStream<Customer> stream = streams.streamAll(em, Customer.class);
JinqStream<Customer> filtered = stream.where(c -> c.getName().equals("Bob"));
```

**Qusaq Equivalent (Current):**
```java
List<Customer> results = Customer.findWhere(c -> c.getName().equals("Bob"));
```

---

#### 2.2.2 Projection: `select()`
```java
// Select single field
JinqStream<String> names = customers.select(c -> c.getName());

// Select transformed value
JinqStream<Double> densities = cities.select(c -> c.getPopulation() / c.getLandArea());

// Select tuple (multiple fields)
JinqStream<Pair<String, Integer>> tuples =
    persons.select(p -> new Pair<>(p.getName(), p.getAge()));
```

**Qusaq Current:** ❌ Not supported

---

#### 2.2.3 Joins: `join()`
```java
// 1:N join (country → cities)
JinqStream<City> cities =
    countries.join(c -> JinqStream.from(c.getCities()));

// Complex join with filtering
JinqStream<Order> highValueOrders =
    customers.where(c -> c.getCountry().equals("USA"))
             .join(c -> JinqStream.from(c.getOrders()))
             .where(o -> o.getTotal() > 1000);

// leftOuterJoin, crossJoin, joinFetch also supported
```

**Qusaq Current:** ❌ Not supported

---

#### 2.2.4 Sorting: `sortedBy()` / `sortedDescendingBy()`
```java
// Single-level sort
JinqStream<Person> sorted = persons.sortedBy(p -> p.getAge());

// Multi-level sort (last call has priority)
JinqStream<Employee> sorted =
    employees.sortedBy(e -> e.getName())
             .sortedBy(e -> e.getCountry());  // Primary sort
```

**Qusaq Current:** ❌ Not supported

---

#### 2.2.5 Pagination: `skip()` / `limit()`
```java
JinqStream<Person> page =
    persons.sortedBy(p -> p.getId())
           .skip(20)
           .limit(10);
```

**Qusaq Current:** ❌ Not supported

---

#### 2.2.6 Distinct: `distinct()`
```java
JinqStream<String> uniqueNames =
    persons.select(p -> p.getLastName())
           .distinct();
```

**Qusaq Current:** ❌ Not supported

---

#### 2.2.7 Aggregation Functions
```java
// Count
long count = persons.where(p -> p.getAge() > 18).count();

// Sum (type-specific methods)
long totalQuantity = sales.sumInteger(s -> s.getQuantity());
double totalRevenue = sales.sumDouble(s -> s.getPrice() * s.getQuantity());

// Min/Max
Integer minAge = persons.min(p -> p.getAge());
Integer maxAge = persons.max(p -> p.getAge());

// Average
Double avgSalary = employees.avg(e -> e.getSalary());
```

**Qusaq Current:** ✅ Partial (count only)

---

#### 2.2.8 Grouping: `group()`
```java
// Group by single key with aggregation
JinqStream<Pair<Country, Long>> citiesPerCountry =
    cities.group(
        c -> c.getCountry(),                    // Grouping key
        (country, stream) -> stream.count()     // Aggregation
    );

// Multiple aggregations per group
JinqStream<Tuple3<Country, Long, Integer>> grouped =
    cities.group(
        c -> c.getCountry(),
        (country, stream) -> stream.count(),
        (country, stream) -> stream.sumInteger(city -> city.getPopulation())
    );
```

**Qusaq Current:** ❌ Not supported

---

#### 2.2.9 Subqueries
```java
// Subquery in where clause
JinqStream<Country> countriesWithManyCities =
    countries.where(c ->
        JinqStream.from(c.getCities()).count() > 5
    );

// Subquery in select clause (must return single value)
JinqStream<Pair<Country, Long>> withCityCount =
    countries.select(c ->
        new Pair<>(c, JinqStream.from(c.getCities()).count())
    );
```

**Qusaq Current:** ❌ Not supported

---

### 2.3 JINQ Method Chaining Patterns

**Example 1: Complex Query Composition**
```java
List<String> result =
    streams.streamAll(em, City.class)
           .where(c -> c.getPopulation() > 1000000)
           .select(c -> c.getName())
           .distinct()
           .sortedBy(name -> name)
           .limit(10)
           .toList();
```

**Example 2: Join + Filter + Project**
```java
List<String> highValueCustomerNames =
    streams.streamAll(em, Order.class)
           .where(o -> o.getTotal() > 10000)
           .join(o -> JinqStream.from(o.getCustomer()))
           .select(c -> c.getName())
           .distinct()
           .toList();
```

---

## 3. Proposed Qusaq API (Iteration 3): Fluent Query Composition

### 3.1 Design Goals

1. **JINQ-Inspired Fluent API** - Method chaining for query composition
2. **Build-Time Code Generation** - All query translation at build time (Qusaq advantage)
3. **Type Safety** - Full compile-time type checking
4. **Direct Query Entry Points** - Start queries directly from entity with `where()`, `select()`, etc.
5. **Zero Runtime Overhead** - No reflection, no runtime query parsing
6. **Incremental Implementation** - Build features progressively

---

### 3.2 Proposed API Design

#### 3.2.1 Core Stream Interface

```java
/**
 * Type-safe, fluent query builder for entity queries.
 * All operations are translated to JPA Criteria Queries at build time.
 */
public interface QusaqStream<T> {

    // === FILTERING ===

    /**
     * Filters entities matching the predicate.
     * @param predicate Lambda returning boolean (e.g., p -> p.age > 18)
     */
    QusaqStream<T> where(QuerySpec<T, Boolean> predicate);

    // === PROJECTION ===

    /**
     * Projects each entity to a new type.
     * @param mapper Lambda transforming entity (e.g., p -> p.firstName)
     */
    <R> QusaqStream<R> select(QuerySpec<T, R> mapper);

    // === SORTING ===

    /**
     * Sorts results in ascending order by the given key extractor.
     * @param keyExtractor Lambda extracting sort key (e.g., p -> p.age)
     */
    <K extends Comparable<K>> QusaqStream<T> sortedBy(QuerySpec<T, K> keyExtractor);

    /**
     * Sorts results in descending order.
     */
    <K extends Comparable<K>> QusaqStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor);

    // === PAGINATION ===

    /**
     * Skips the first n results (OFFSET in SQL).
     */
    QusaqStream<T> skip(int n);

    /**
     * Limits results to n items (LIMIT in SQL).
     */
    QusaqStream<T> limit(int n);

    // === DISTINCT ===

    /**
     * Returns only distinct results (SELECT DISTINCT in SQL).
     */
    QusaqStream<T> distinct();

    // === AGGREGATION ===

    /**
     * Counts matching entities.
     */
    long count();

    /**
     * Finds minimum value.
     */
    <K extends Comparable<K>> K min(QuerySpec<T, K> mapper);

    /**
     * Finds maximum value.
     */
    <K extends Comparable<K>> K max(QuerySpec<T, K> mapper);

    /**
     * Computes sum (integer variant).
     */
    long sumInteger(QuerySpec<T, Integer> mapper);

    /**
     * Computes sum (long variant).
     */
    long sumLong(QuerySpec<T, Long> mapper);

    /**
     * Computes sum (double variant).
     */
    double sumDouble(QuerySpec<T, Double> mapper);

    /**
     * Computes average.
     */
    <K extends Number> Double avg(QuerySpec<T, K> mapper);

    // === TERMINAL OPERATIONS ===

    /**
     * Executes query and returns results as list.
     */
    List<T> toList();

    /**
     * Executes query expecting single result.
     * @throws NoResultException if no result
     * @throws NonUniqueResultException if multiple results
     */
    T getSingleResult();

    /**
     * Executes query returning optional result.
     */
    Optional<T> findFirst();

    /**
     * Checks if any entity matches the current query.
     */
    boolean exists();
}
```

---

#### 3.2.2 Query Entry Point Methods

**Redesigned `QusaqEntity`:**
```java
@MappedSuperclass
public abstract class QusaqEntity extends PanacheEntity {

    /**
     * Creates a query filtered by the given predicate.
     * Entry point for fluent query composition.
     *
     * @param spec Filtering predicate
     * @return Filtered stream builder
     */
    public static <T extends QusaqEntity> QusaqStream<T> where(QuerySpec<T, Boolean> spec) {
        // Implementation generated at build time
    }

    /**
     * Creates a query with field projection.
     * Entry point for selecting specific fields or DTOs.
     *
     * @param mapper Lambda transforming entity to projection
     * @return Projection stream builder
     */
    public static <T extends QusaqEntity, R> QusaqStream<R> select(QuerySpec<T, R> mapper) {
        // Implementation generated at build time
    }

    /**
     * Creates a query sorted by the given key extractor.
     * Entry point for sorted queries.
     *
     * @param keyExtractor Lambda extracting sort key
     * @return Sorted stream builder
     */
    public static <T extends QusaqEntity, K extends Comparable<K>> QusaqStream<T> sortedBy(QuerySpec<T, K> keyExtractor) {
        // Implementation generated at build time
    }

    /**
     * Creates a query sorted in descending order.
     * Entry point for reverse-sorted queries.
     *
     * @param keyExtractor Lambda extracting sort key
     * @return Sorted stream builder
     */
    public static <T extends QusaqEntity, K extends Comparable<K>> QusaqStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor) {
        // Implementation generated at build time
    }

    // Note: count() and findAll() are inherited from PanacheEntityBase (Panache)
    // Use Person.count() and Person.findAll() directly without redefinition
}
```

---

### 3.3 Usage Examples: Before and After

#### Example 1: Simple Filtering

**Current API (Iteration 2):**
```java
List<Person> adults = Person.findWhere(p -> p.age >= 18);
```

**New API (Iteration 3):**
```java
// Direct query with where()
List<Person> adults = Person.where(p -> p.age >= 18).toList();
```

---

#### Example 2: Projection (Select Specific Fields)

**Current API:** ❌ Not possible

**New API:**
```java
// Select single field
List<String> firstNames = Person.select(p -> p.firstName).toList();

// Select with transformation
List<String> fullNames = Person.select(p -> p.firstName + " " + p.lastName).toList();

// Select DTO
List<PersonSummary> summaries = Person.select(p -> new PersonSummary(p.firstName, p.age))
                                      .toList();
```

---

#### Example 3: Sorting

**Current API:** ❌ Not possible

**New API:**
```java
// Single-level sort
List<Person> byAge = Person.sortedBy(p -> p.age).toList();

// Multi-level sort (last call has priority)
List<Person> byLastNameThenFirst = Person.sortedBy(p -> p.firstName)
                                         .sortedBy(p -> p.lastName)  // Primary sort
                                         .toList();

// Descending sort
List<Person> oldestFirst = Person.sortedDescendingBy(p -> p.age).toList();
```

---

#### Example 4: Pagination

**Current API:** ❌ Not possible

**New API:**
```java
// Page 3 (items 20-29)
List<Person> page3 = Person.sortedBy(p -> p.id)
                           .skip(20)
                           .limit(10)
                           .toList();

// Top 5 highest salaries
List<Person> top5 = Person.sortedDescendingBy(p -> p.salary)
                          .limit(5)
                          .toList();
```

---

#### Example 5: Distinct Results

**Current API:** ❌ Not possible

**New API:**
```java
// Unique last names
List<String> uniqueLastNames = Person.select(p -> p.lastName)
                                     .distinct()
                                     .toList();

// Distinct active persons (by all fields)
List<Person> distinctActive = Person.where(p -> p.active)
                                    .distinct()
                                    .toList();
```

---

#### Example 6: Complex Composition

**Current API:** ❌ Not possible

**New API:**
```java
// Top 10 highest-paid active employees in IT department, sorted by salary
List<PersonSummary> topIT = Person.where(p -> p.active && p.department.equals("IT"))
                                  .sortedDescendingBy(p -> p.salary)
                                  .limit(10)
                                  .select(p -> new PersonSummary(p.firstName + " " + p.lastName, p.salary))
                                  .toList();

// Distinct cities of people born after 1990, alphabetically
List<String> cities = Person.where(p -> p.birthDate.getYear() > 1990)
                            .select(p -> p.city)
                            .distinct()
                            .sortedBy(city -> city)
                            .toList();
```

---

#### Example 7: Aggregations

**Current API:**
```java
long count = Person.countWhere(p -> p.active);
```

**New API:**
```java
// Count - filtered
long activeCount = Person.where(p -> p.active).count();

// Count all - use inherited Panache method
long allCount = Person.count(); // Inherited from PanacheEntityBase

// Min/Max
Integer youngestAge = Person.where(p -> p.age != null).min(p -> p.age);
Integer oldestAge = Person.where(p -> p.age != null).max(p -> p.age);

// Sum
double totalSalaries = Person.where(p -> p.active).sumDouble(p -> p.salary);

// Average
Double avgAge = Person.where(p -> p.age != null).avg(p -> p.age);
```

---

### 3.4 Advanced Features (Future Phases)

#### 3.4.1 Joins (Phase 2)

**JINQ Example:**
```java
// Join orders with customers
JinqStream<Order> highValueOrders =
    customers.where(c -> c.getName().equals("Alice"))
             .join(c -> JinqStream.from(c.getOrders()))
             .where(o -> o.getTotal() > 1000);
```

**Proposed Qusaq API:**
```java
QusaqStream<T> join(QuerySpec<T, Collection<R>> joinSpec);
QusaqStream<T> leftJoin(QuerySpec<T, Collection<R>> joinSpec);

// Usage:
List<Order> orders = Customer.where(c -> c.name.equals("Alice"))
                             .join(c -> c.getOrders())
                             .where(o -> o.total > 1000)
                             .toList();
```

**Challenge:** Requires relationship metadata extraction at build time

---

#### 3.4.2 Grouping (Phase 3)

**JINQ Example:**
```java
JinqStream<Pair<Country, Long>> citiesPerCountry =
    cities.group(
        c -> c.getCountry(),
        (country, stream) -> stream.count()
    );
```

**Proposed Qusaq API:**
```java
<K, V> QusaqStream<Pair<K, V>> group(
    QuerySpec<T, K> keyExtractor,
    BiFunction<K, QusaqStream<T>, V> aggregator
);

// Usage:
List<Pair<String, Long>> countByDept =
    Person.stream()
          .group(p -> p.department, (dept, stream) -> stream.count())
          .toList();
```

**Challenge:** Complex bytecode analysis for nested lambdas

---

#### 3.4.3 Subqueries (Phase 4)

**JINQ Example:**
```java
JinqStream<Country> countriesWithManyCities =
    countries.where(c ->
        JinqStream.from(c.getCities()).count() > 5
    );
```

**Proposed Qusaq API:**
```java
// Subquery in where
List<Country> countries = Country.where(c ->
    City.where(city -> city.country.id == c.id).count() > 5
).toList();
```

**Challenge:** Requires correlation variable handling in bytecode analysis

---

## 4. Implementation Strategy

### 4.1 Phased Rollout (Iteration 3)

**Phase 1: Core Fluent API (Week 1-2)**
- Create `QusaqStream` interface
- Implement `where()` entry point as static method on `QusaqEntity`
- Support `toList()` terminal operation
- Remove legacy `findWhere`/`countWhere`/`exists` methods

**Phase 2: Projection & Sorting (Week 3-4)**
- Implement `select()` for field projection
- Support DTO projections
- Add `sortedBy()` / `sortedDescendingBy()`
- Enable multi-level sorting

**Phase 3: Pagination & Distinct (Week 5)**
- Implement `skip()` and `limit()`
- Add `distinct()` support
- Test pagination edge cases

**Phase 4: Aggregations (Week 6)**
- Implement `count()` (refactor existing)
- Add `min()`, `max()`, `avg()`
- Support type-specific `sum*()` methods

**Phase 5: Advanced Composition (Future)**
- Joins (requires relationship metadata)
- Grouping (requires nested lambda support)
- Subqueries (requires correlation variables)

---

### 4.2 Build-Time Code Generation Strategy

**Current:** Single `QueryExecutor` class per lambda callsite

**Enhanced:** `QusaqStreamImpl` class per stream pipeline

**Example Generated Code:**
```java
// User code:
List<String> names = Person.where(p -> p.age > 18)
                           .select(p -> p.firstName)
                           .distinct()
                           .sortedBy(name -> name)
                           .toList();

// Generated at build time:
public class PersonStream_a1b2c3 implements QusaqStream<Person> {

    private Predicate wherePredicate;
    private Function<Person, ?> selector;
    private boolean distinct;
    private List<SortOrder> sortOrders = new ArrayList<>();
    private Integer offset;
    private Integer limit;

    public QusaqStream<Person> where(QuerySpec<Person, Boolean> spec) {
        this.wherePredicate = analyzePredicate(spec);
        return this;
    }

    public <R> QusaqStream<R> select(QuerySpec<Person, R> mapper) {
        this.selector = analyzeSelector(mapper);
        return new ProjectedStream<R>(this);
    }

    public List<Person> toList() {
        EntityManager em = getEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Person> cq = cb.createQuery(Person.class);
        Root<Person> root = cq.from(Person.class);

        // Apply where
        if (wherePredicate != null) {
            cq.where(buildPredicate(cb, root));
        }

        // Apply distinct
        if (distinct) {
            cq.distinct(true);
        }

        // Apply sorting
        if (!sortOrders.isEmpty()) {
            cq.orderBy(buildOrderBy(cb, root));
        }

        TypedQuery<Person> query = em.createQuery(cq);

        // Apply pagination
        if (offset != null) query.setFirstResult(offset);
        if (limit != null) query.setMaxResults(limit);

        return query.getResultList();
    }
}
```

---

### 4.3 Bytecode Analysis Enhancements

**Current Analysis:**
- Single lambda expression → AST → JPA Criteria predicate

**Enhanced Analysis:**
- **Pipeline tracking:** Recognize method call chains
- **State accumulation:** Build composite query from multiple operations
- **Type inference:** Track type transformations through `select()`
- **Validation:** Ensure valid operation sequences at build time

**Example:**
```java
Person.where(p -> p.age > 18)     // Analyze: Predicate<Person, Boolean>
      .select(p -> p.firstName)   // Analyze: Function<Person, String>
      .distinct()                 // Analyze: No lambda, just flag
      .toList();                  // Analyze: Terminal operation, generate executor
```

**Build-Time Validation:**
```java
// ERROR: Cannot call where() after select()
Person.select(p -> p.firstName)
      .where(name -> name.startsWith("A"))  // Compile error!
```

---

## 5. Technical Challenges & Solutions

### 5.1 Challenge: Method Call Chain Analysis

**Problem:** Current analyzer processes single lambda. Need to track sequences like:
```java
Person.where(...).select(...).sortedBy(...).limit(10).toList()
```

**Solution:**
1. **Scan entire method body** for chained calls starting from entity class
2. **Build operation list:** `[where, select, sortedBy, limit, toList]`
3. **Validate sequence:** Ensure operations are in valid order
4. **Generate composite executor:** Single class handling full pipeline

---

### 5.2 Challenge: Type Transformations Through `select()`

**Problem:**
```java
Person.select(p -> p.age) // QusaqStream<Integer>
      .min(i -> i)        // Integer (need to track type change from Person → Integer)
```

**Solution:**
1. **Track generic type parameter** through pipeline
2. **Infer return type** from lambda analysis
3. **Generate type-specific stream class** (e.g., `QusaqStream<Integer>`)

---

### 5.3 Challenge: Terminal vs. Intermediate Operations

**Problem:** Distinguish operations that return streams vs. execute queries

**Solution:**
```java
// Intermediate (return QusaqStream)
where(), select(), sortedBy(), skip(), limit(), distinct()

// Terminal (execute query, return result)
toList(), count(), min(), max(), avg(), sum*(), getSingleResult(), findFirst()
```

**Build-time check:** Ensure pipeline ends with terminal operation

---

### 5.4 Challenge: Sorting Multiple Columns

**Problem:** SQL supports `ORDER BY col1, col2, col3` but lambda extracts single key

**Solution: Multiple sortedBy() calls (JINQ approach)**
```java
Person.sortedBy(p -> p.lastName)   // Secondary
      .sortedBy(p -> p.firstName)  // Primary (last call wins)

// Generated SQL: ORDER BY firstName, lastName
```

**Implementation Strategy:**
- Each `sortedBy()` call **prepends** to the sort order list
- Last call becomes the primary sort key
- Earlier calls become secondary, tertiary, etc.
- Simpler bytecode analysis than tuple-based sorting

---

## 6. Benefits Analysis

### 6.1 Developer Experience

**Before (Iteration 2):**
```java
// Get top 5 active people sorted by salary (requires manual SQL/JPQL)
@Query("SELECT p FROM Person p WHERE p.active = true ORDER BY p.salary DESC")
@MaxResults(5)
List<Person> findTop5BySalary();
```

**After (Iteration 3):**
```java
// Same query, fluent and type-safe
List<Person> top5 = Person.where(p -> p.active)
                          .sortedDescendingBy(p -> p.salary)
                          .limit(5)
                          .toList();
```

**Improvements:**
- ✅ **Type-safe** (compile-time validation)
- ✅ **Refactoring-friendly** (IDE renames work)
- ✅ **Self-documenting** (clear intent)
- ✅ **Composable** (easy to add/remove operations)

---

### 6.2 Performance

**Runtime:** ✅ **Zero overhead** (all analysis at build time)
**Build time:** ⚠️ **Slightly increased** (more complex bytecode analysis)

**Comparison:**
- JINQ: Runtime query translation overhead
- Qusaq: One-time build cost, optimal runtime

---

### 6.3 Maintainability

**Code Clarity:**
```java
// Qusaq Iteration 3: Clear intent
List<String> cities = Person.where(p -> p.birthDate.getYear() > 1990)
                            .select(p -> p.city)
                            .distinct()
                            .sortedBy(city -> city)
                            .toList();

// JPQL: Less clear
@Query("SELECT DISTINCT p.city FROM Person p " +
       "WHERE YEAR(p.birthDate) > 1990 " +
       "ORDER BY p.city")
List<String> findCitiesOfYoungPeople();
```

---

## 7. Testing Strategy

### 7.1 Core Feature Tests

```java
@Test
void filtering_simpleWhere() {
    List<Person> adults = Person.where(p -> p.age >= 18).toList();
    assertThat(adults).allMatch(p -> p.age >= 18);
}

@Test
void projection_selectSingleField() {
    List<String> names = Person.select(p -> p.firstName).toList();
    assertThat(names).containsExactly("John", "Jane", "Bob");
}

@Test
void sorting_singleLevel() {
    List<Person> sorted = Person.sortedBy(p -> p.age).toList();
    assertThat(sorted).isSortedAccordingTo(Comparator.comparing(p -> p.age));
}

@Test
void pagination_skipAndLimit() {
    List<Person> page2 = Person.sortedBy(p -> p.id)
                               .skip(10)
                               .limit(10)
                               .toList();
    assertThat(page2).hasSize(10);
}

@Test
void composition_complex() {
    List<String> cities = Person.where(p -> p.birthDate.getYear() > 1990)
                                .select(p -> p.city)
                                .distinct()
                                .sortedBy(city -> city)
                                .limit(5)
                                .toList();

    assertThat(cities).hasSize(5);
    assertThat(cities).isSorted();
    assertThat(cities).doesNotHaveDuplicates();
}
```

---

## 8. Documentation Updates

### 8.1 README Updates

**Add section:**
```markdown
## Fluent Query API (Iteration 3)

Qusaq now supports JINQ-inspired fluent query composition:

### Basic Filtering
```java
List<Person> adults = Person.where(p -> p.age >= 18).toList();
```

### Projection
```java
List<String> names = Person.select(p -> p.firstName).toList();
```

### Sorting & Pagination
```java
List<Person> page = Person.sortedBy(p -> p.lastName)
                          .skip(20)
                          .limit(10)
                          .toList();
```

### Complex Composition
```java
List<String> topCities = Person.where(p -> p.active && p.salary > 100000)
                               .select(p -> p.city)
                               .distinct()
                               .sortedBy(city -> city)
                               .limit(10)
                               .toList();
```
```

---

### 8.2 API Reference

Document all `QusaqStream` methods:
- Method signatures
- Return types
- Examples
- Limitations (what lambdas are supported)

---

## 9. Success Metrics

### 9.1 Functionality Metrics

| Feature | Iteration 2 | Iteration 3 | Improvement |
|---------|------------|-------------|-------------|
| Filtering | ✅ | ✅ | Maintained |
| Projection | ❌ | ✅ | **NEW** |
| Sorting | ❌ | ✅ | **NEW** |
| Pagination | ❌ | ✅ | **NEW** |
| Distinct | ❌ | ✅ | **NEW** |
| Aggregations | ✅ (count) | ✅ (count, min, max, sum, avg) | **5x more** |
| Joins | ❌ | ❌ (future) | Planned |
| Grouping | ❌ | ❌ (future) | Planned |

---

### 9.2 Developer Experience Metrics

**Query Expressiveness:**
- Iteration 2: 1 operation per query (filter OR count)
- Iteration 3: 5+ operations per query (filter + project + sort + paginate)
- **Improvement: 5x more expressive**

**Type Safety:**
- Iteration 2: ✅ Full type safety for filtering
- Iteration 3: ✅ Full type safety through entire pipeline
- **Improvement: Maintained**

**Performance:**
- Iteration 2: Zero runtime overhead
- Iteration 3: Zero runtime overhead
- **Improvement: Maintained**

---

## 10. Conclusion

### 10.1 Summary

**Iteration 3** transforms Qusaq from a **simple filter API** into a **comprehensive, fluent query framework** matching JINQ's expressive power while maintaining Qusaq's **build-time advantage**.

**Key Innovations:**
1. ✅ JINQ-inspired fluent API
2. ✅ Build-time query composition (Qusaq advantage over JINQ)
3. ✅ Direct query entry points (no intermediate stream() method)
4. ✅ Type-safe query pipelines
5. ✅ Zero runtime overhead
6. ✅ Clean, simplified API design

---

### 10.2 Next Steps

1. **Create implementation tracker** (separate document)
2. **Implement Phase 1:** Core fluent API (`where()`, `select()`, `toList()`)
3. **Validate with tests**
4. **Iterate through remaining phases**
5. **Gather developer feedback**

---

**Document End**
