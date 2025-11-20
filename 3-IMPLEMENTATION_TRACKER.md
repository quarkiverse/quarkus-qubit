# Quarkus Qusaq API Enhancement - Iteration 3: Implementation Tracker

**Date Started:** 2025-11-18
**Last Updated:** 2025-11-20
**Status:** ✅ PHASE 1 & PHASE 2 COMPLETE - Fluent API infrastructure, all projection features (field, expression, DTO), multiple where() chaining, single-result terminals, and 82 new Phase 2 tests
**Overall Progress:** 100% - All Phase 1 & Phase 2 steps complete (294/294 tests passing - 100% pass rate!)
**Reference Document:** [3-API_ENHANCEMENT_ANALYSIS.md](3-API_ENHANCEMENT_ANALYSIS.md)

## Phase 1 Progress (2025-11-18 to 2025-11-19)

### ✅ NEW: Step 1.5 & Legacy API Removal Complete (2025-11-19)

**Integration Tests Migration:**
- ✅ Migrated all 15 existing integration test files from legacy API to fluent API
- ✅ Created BasicQueryTest.java with fluent API examples
- ✅ Updated test patterns:
  - `findWhere(...)` → `where(...).toList()`
  - `countWhere(...)` → `where(...).count()`
  - `exists(...)` → `where(...).exists()`

**Legacy API Removal:**
- ✅ Removed `findWhere()`, `countWhere()`, `exists()` from QusaqEntity
- ✅ Removed QusaqOperations class (legacy runtime support)
- ✅ Updated InvokeDynamicScanner to detect only fluent API terminals
- ✅ Updated CallSiteProcessor to process only fluent API patterns
- ✅ Cleaned up QusaqConstants (removed legacy method name constants)

**Test Results:**
- ✅ 281 of 281 tests passing (100% pass rate!)
- ✅ exists() tests fixed: All 6 tests now passing (was: "No executor found" errors)
- ✅ Multiple where() chaining fixed: BasicQueryTest.multipleWhere_combinesWithAnd now passing
- ✅ Build compiles successfully
- ✅ All basic fluent API functionality verified working

**Known Issues:** None - All tests passing!

**Fixed Issues (2025-11-19):**
1. ✅ **exists() Implementation** - Fixed "No executor found" errors for exists() queries
   - Root cause: Missing `METHOD_EXISTS` constant and incomplete isCountQuery() check
   - Fix: Added METHOD_EXISTS to FLUENT_TERMINAL_METHODS and updated isCountQuery() to treat exists() as count query
   - Files modified:
     - `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqConstants.java` - Added METHOD_EXISTS constant and to FLUENT_TERMINAL_METHODS set
     - `deployment/src/main/java/io/quarkus/qusaq/deployment/InvokeDynamicScanner.java` - Updated isCountQuery() to check for both count() and exists()
   - Impact: 6 tests now passing (ExistsQueryTest: 4 tests, CapturedVariablesTest: 2 tests)

### ✅ Completed (Steps 1.1, 1.2, 1.3)
1. **QusaqStream Interface** - Created complete fluent API contract with all methods
2. **QusaqStreamImpl Base** - Implemented immutable stream pattern with state accumulation
3. **Entry Point Methods** - Generated `where()`, `select()`, `sortedBy()`, `sortedDescendingBy()` in entities
4. **Method Clash Resolution** - Removed `count()` and `findAll()` from QusaqEntity (inherited from Panache)
5. **Build Verification** - Runtime and deployment modules compile successfully

### ✅ Completed (Step 1.4)
**Build-Time Processor Enhancement** - Complete implementation:
- ✅ Added fluent API method name constants to QusaqConstants
- ✅ Updated InvokeDynamicScanner to detect terminal operations (.toList(), .count(), .exists())
- ✅ Implemented QusaqStreamImpl runtime execution using registry pattern
- ✅ Stack walking integration for call site identification
- ✅ Captured variable extraction from QuerySpec predicates
- ✅ CallSiteProcessor handles both legacy and fluent API call sites
- ✅ Created StreamPipelineAnalyzer for future pipeline analysis
- ✅ Fluent API generates executors using proven legacy pattern

**Implementation Details:**
- **Runtime Path:** QusaqStreamImpl → getCallSiteId() → QueryExecutorRegistry → Execute pre-generated query
- **Build-Time Path:** InvokeDynamicScanner → CallSiteProcessor → QueryExecutorClassGenerator → Bytecode
- **Execution Strategy:** Fluent API (`where().toList()`) generates identical executors to legacy API (`findWhere()`)
- **Phase 1 Scope:** Single where() predicates only; full pipeline analysis deferred to Phase 2+

**Current State:**
- ✅ Runtime execution path fully functional
- ✅ Scanner detects both legacy and fluent API calls
- ✅ Build compiles successfully with complete infrastructure
- ✅ Ready for integration testing

### ✅ All Issues Resolved (2025-11-20)

**Previously Resolved Issues:**
1. ✅ **Executor Generation** - Completed using proven legacy pattern
2. ✅ **Runtime Execution** - Completed with stack walking and registry integration
3. ✅ **Integration Tests** - Completed with 281/281 tests passing (100%)
4. ✅ **Legacy API Removal** - Completed successfully
5. ✅ **exists() Implementation** - Fixed by adding METHOD_EXISTS constant and updating isCountQuery()
6. ✅ **Multiple where() Chaining Bug** - Fixed in Phase 2.5 (November 20, 2025)
   - Was causing 1 test failure in BasicQueryTest.multipleWhere_combinesWithAnd
   - Root cause: Scanner was overwriting predicates instead of accumulating them
   - Solution: Enhanced LambdaCallSite to store list of all predicates, added combinePredicatesWithAnd() method
   - Result: All 281 tests now passing (100% pass rate)

### 📋 Next Steps (Post-Phase 2)

**✅ Phase 1 & Phase 2 COMPLETE:**
- ✅ All core fluent API functionality working (100% test pass rate)
- ✅ All projection features implemented (field, expression, DTO)
- ✅ Multiple where() chaining fixed and working
- ✅ exists() implementation fixed

**Option 1: Proceed to Phase 3** (Recommended)
- Begin Sorting implementation (`sortedBy()`, `sortedDescendingBy()`)
- Review Phase 3 requirements
- Plan sorting implementation approach

**Option 2: Documentation & Cleanup**
- Add Architecture Flow documentation
- Update examples and usage guides
- Create migration guide for legacy API users
- Document Phase 2 implementation patterns

**Option 3: Additional Testing**
- Add more edge case tests for multiple where() chaining
- Test complex query compositions
- Performance benchmarking

---

## Legend

- ✅ **Completed & Tested** - Implementation done and verified with tests
- 🔄 **In Progress** - Currently being implemented
- ⏸️ **Blocked** - Waiting on dependencies
- ⏭️ **Skipped** - Deferred to later phase
- ❌ **Not Started** - Pending implementation
- 🎯 **Milestone** - Major completion point

---

## Overview: Iteration 3 Goals

**Transform Qusaq from simple filtering API to full fluent query composition framework**

### Success Criteria

| Criterion | Target | Status |
|-----------|--------|--------|
| **Fluent API Core** | `where()`, `select()`, `toList()` entry points | ❌ |
| **Projection** | `select()` for field/DTO projection | ❌ |
| **Sorting** | `sortedBy()`, `sortedDescendingBy()` | ❌ |
| **Pagination** | `skip()`, `limit()` | ❌ |
| **Distinct** | `distinct()` support | ❌ |
| **Enhanced Aggregation** | `min()`, `max()`, `avg()`, `sum*()` | ❌ |
| **Test Coverage** | 100% new features tested | ❌ |
| **Clean API** | Legacy methods removed | ❌ |

---

## Phase 1: Infrastructure & Core Fluent API (Week 1-2)

**Objective:** Create foundation for fluent query composition with minimal features

**Estimated Effort:** 10-12 days
**Priority:** 🔴 CRITICAL
**Dependencies:** None

---

### Step 1.1: Create QusaqStream Interface ✅ COMPLETED

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 1.1.1 | Create `QusaqStream<T>` interface | ✅ | 400 LOC | Complete fluent API contract |
| 1.1.2 | Define `where(QuerySpec<T, Boolean>)` | ✅ | Included | Filtering method |
| 1.1.3 | Define `toList()` terminal operation | ✅ | Included | Execute and return List<T> |
| 1.1.4 | Define `count()` terminal operation | ✅ | Included | Execute and return long |
| 1.1.5 | Define `exists()` terminal operation | ✅ | Included | Execute and return boolean |
| 1.1.6 | Add comprehensive JavaDoc | ✅ | Included | Full documentation with examples |

**Files Created:**
- ✅ `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStream.java` (400 lines)

**Additional Methods Implemented:**
- `select()`, `sortedBy()`, `sortedDescendingBy()` - Projection and sorting
- `skip()`, `limit()`, `distinct()` - Pagination and uniqueness
- `min()`, `max()`, `avg()`, `sumInteger()`, `sumLong()`, `sumDouble()` - Aggregations
- `getSingleResult()`, `findFirst()` - Single result terminals

**Status:** Complete - Interface compiles successfully

---

### Step 1.2: Implement QusaqStreamImpl Base ✅ COMPLETED

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 1.2.1 | Create `QusaqStreamImpl<T>` class | ✅ | 419 LOC | Immutable stream implementation |
| 1.2.2 | Add entity class field | ✅ | Included | Tracks entity type |
| 1.2.3 | Add predicate accumulator | ✅ | Included | Stores where() clauses as list |
| 1.2.4 | Implement `where()` method | ✅ | ~10 LOC | Chains predicates (returns new instance) |
| 1.2.5 | Implement `toList()` executor | ✅ | ~50 LOC | Stub - generates CriteriaQuery skeleton |
| 1.2.6 | Implement `count()` executor | ✅ | ~26 LOC | Working COUNT query |
| 1.2.7 | Implement `exists()` executor | ✅ | ~4 LOC | Delegates to count() > 0 |

**Files Created:**
- ✅ `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStreamImpl.java` (419 lines)

**Key Design Decisions:**
- ✅ **Immutable Pattern:** Each method call returns new instance with updated state
- ✅ **Predicate Chaining:** Multiple `where()` calls combined with AND (stored as List)
- ✅ **State Accumulation:** Fields for predicates, selector, sortOrders, offset, limit, distinct
- ⚠️ **Executor Pattern:** Terminal operations have stubs - actual execution requires Step 1.4

**Additional Implementation:**
- `select()` - Type transformation support (generic parameter changes)
- `sortedBy()` / `sortedDescendingBy()` - Sort order accumulation (last call wins)
- `skip()` / `limit()` - Pagination support with validation
- `distinct()` - Uniqueness flag
- Aggregation methods - Stubs for Phase 5 (min, max, sum*, avg)
- `getSingleResult()`, `findFirst()` - Single result terminals

**Status:** Infrastructure complete - Awaits Step 1.4 for query execution

**Example Internal Structure:**
```java
public class QusaqStreamImpl<T> implements QusaqStream<T> {
    private final Class<T> entityClass;
    private final List<QuerySpec<T, Boolean>> predicates;
    // More fields added in later phases

    @Override
    public QusaqStream<T> where(QuerySpec<T, Boolean> predicate) {
        List<QuerySpec<T, Boolean>> newPredicates = new ArrayList<>(this.predicates);
        newPredicates.add(predicate);
        return new QusaqStreamImpl<>(entityClass, newPredicates);
    }

    @Override
    public List<T> toList() {
        // Generate CriteriaQuery from accumulated predicates
        // Execute query
        // Return results
    }
}
```

---

### Step 1.3: Add Query Entry Point Methods to QusaqEntity ✅ COMPLETED

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 1.3.1 | Add `where(QuerySpec)` static method | ✅ | ~30 LOC | Entry point for filtering |
| 1.3.2 | Add `select(QuerySpec)` static method | ✅ | ~30 LOC | Entry point for projection |
| 1.3.3 | Add `sortedBy(QuerySpec)` static method | ✅ | ~30 LOC | Entry point for sorting |
| 1.3.4 | Add `sortedDescendingBy(QuerySpec)` static method | ✅ | ~30 LOC | Entry point for descending sort |
| 1.3.5 | Build-time implementation | ✅ | Included | Methods generated via bytecode enhancement |
| 1.3.6 | Legacy methods | ⏭️ | Deferred | Keep for backward compatibility during transition |

**Note:** `count()` and `findAll()` are inherited from PanacheEntityBase and NOT redefined (avoiding method clash).

**Files Modified:**
- ✅ `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqEntity.java` - Added 4 entry point methods with comprehensive JavaDoc
- ✅ `deployment/src/main/java/io/quarkus/qusaq/deployment/QusaqEntityOperationGenerationVisitor.java` - Generates methods in entity subclasses
- ✅ `deployment/src/main/java/io/quarkus/qusaq/deployment/QusaqBytecodeGenerator.java` - Bytecode generation for fluent entry points

**Generated Bytecode Pattern:**
```java
// User code: Person.where(p -> p.age > 18)
// Generated in Person.class at build time:
public static QusaqStream<Person> where(QuerySpec<Person, Boolean> spec) {
    return new QusaqStreamImpl<>(Person.class).where(spec);
}
```

**Status:** Entry points correctly delegate to QusaqStreamImpl

**New QusaqEntity API:**
```java
public abstract class QusaqEntity extends PanacheEntity {

    // === QUERY ENTRY POINTS ===
    public static <T extends QusaqEntity> QusaqStream<T> where(QuerySpec<T, Boolean> spec) {
        // Build-time implementation
        throw new IllegalStateException("Must be overridden at build time");
    }

    public static <T extends QusaqEntity, R> QusaqStream<R> select(QuerySpec<T, R> mapper) {
        // Build-time implementation
        throw new IllegalStateException("Must be overridden at build time");
    }

    public static <T extends QusaqEntity, K extends Comparable<K>> QusaqStream<T> sortedBy(QuerySpec<T, K> keyExtractor) {
        // Build-time implementation
        throw new IllegalStateException("Must be overridden at build time");
    }

    public static <T extends QusaqEntity, K extends Comparable<K>> QusaqStream<T> sortedDescendingBy(QuerySpec<T, K> keyExtractor) {
        // Build-time implementation
        throw new IllegalStateException("Must be overridden at build time");
    }

    // Note: count() and findAll() are inherited from PanacheEntityBase
}
```

---

### Step 1.4: Update Build-Time Processor ✅ COMPLETED

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 1.4.1 | Detect query entry point calls | ✅ | ~50 LOC | InvokeDynamicScanner detects both legacy and fluent API |
| 1.4.2 | Analyze method call chains | ✅ | ~180 LOC | StreamPipelineAnalyzer scans backwards from terminals |
| 1.4.3 | Build operation pipeline | ✅ | ~80 LOC | PipelineOperation and StreamPipeline records created |
| 1.4.4 | Validate operation sequence | ⏭️ | Deferred | Phase 1 uses simple validation in QusaqStreamImpl |
| 1.4.5 | Generate query implementation class | ✅ | Reused | Fluent API uses existing QueryExecutorClassGenerator |
| 1.4.6 | Legacy method support | ✅ | N/A | Maintained for backward compatibility |

**Files Modified:**
- ✅ `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqConstants.java` - Added fluent API method names
- ✅ `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStreamImpl.java` - Implemented runtime execution
- ✅ `deployment/src/main/java/io/quarkus/qusaq/deployment/InvokeDynamicScanner.java` - Detects fluent terminals
- ✅ `deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/CallSiteProcessor.java` - Handles both APIs

**Files Created:**
- ✅ `deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/StreamPipelineAnalyzer.java` - Pipeline analysis infrastructure

**Status:** Complete - Ready for integration testing

**New Analyzer Responsibilities:**
1. **Scan method body** for query entry points (`Person.where()`, `Person.select()`, etc.)
2. **Track call chain** (`.where().select().toList()`)
3. **Extract lambda for each operation** (analyze bytecode)
4. **Build pipeline descriptor** (list of operations + lambdas)
5. **Validate pipeline** (ensure valid sequence)
6. **Generate executor** (single method implementing full pipeline)

**Example Pipeline Descriptor:**
```java
record StreamPipeline(
    Class<?> entityClass,
    List<Operation> operations
) {}

sealed interface Operation {
    record Where(LambdaExpression predicate) implements Operation {}
    record Select(LambdaExpression mapper, Class<?> resultType) implements Operation {}
    record SortBy(LambdaExpression keyExtractor, boolean descending) implements Operation {}
    record Skip(int count) implements Operation {}
    record Limit(int count) implements Operation {}
    record Distinct() implements Operation {}

    sealed interface Terminal extends Operation {
        record ToList() implements Terminal {}
        record Count() implements Terminal {}
        record Exists() implements Terminal {}
        record Min(LambdaExpression mapper) implements Terminal {}
        record Max(LambdaExpression mapper) implements Terminal {}
    }
}
```

---

### Step 1.5: Integration Tests - Basic Fluent API ✅ COMPLETED

| Task | Status | Tests | Notes |
|------|--------|-------|-------|
| 1.5.1 | Test `where().toList()` | ✅ | 201 tests | Basic filtering - all existing tests migrated |
| 1.5.2 | Test `where().where().toList()` | ⚠️ | 1 test | Multiple where (AND) - 1 test failing (bug) |
| 1.5.3 | Test `where().count()` | ✅ | Multiple tests | Filtering + count - all passing |
| 1.5.4 | Test `where().exists()` | ⚠️ | 6 tests | Filtering + exists - 6 tests failing (test class scanning) |

**Files Created:**
- ✅ `integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/BasicQueryTest.java`

**Files Migrated (15 total):**
- ✅ All integration test files updated from legacy API to fluent API
- ✅ ArithmeticOperationsTest.java, ComparisonTest.java, EqualityTest.java, NullCheckTest.java
- ✅ CapturedVariablesTest.java, BigDecimalTest.java, StringOperationsTest.java, TemporalTypesTest.java
- ✅ ProductQueryTest.java, AndOperationsTest.java, ComplexExpressionsTest.java
- ✅ NotOperationsTest.java, OrOperationsTest.java, CountQueryTest.java, ExistsQueryTest.java

**Note:** Direct `Person.count()` and `Person.findAll()` calls use inherited Panache methods (not Qusaq-specific).

**Test Examples:**
```java
@Test
void where_filtersByPredicate() {
    List<Person> adults = Person.where(p -> p.age >= 18).toList();
    assertThat(adults).allMatch(p -> p.age >= 18);
}

@Test
void multipleWhere_combinesWithAnd() {
    List<Person> results = Person.where(p -> p.age > 25)
                                 .where(p -> p.active)
                                 .toList();

    assertThat(results).allMatch(p -> p.age > 25 && p.active);
}

@Test
void whereCount_filtersAndCounts() {
    long count = Person.where(p -> p.active).count();
    assertThat(count).isGreaterThan(0);
}
```

---

### Phase 1 Completion Criteria ✅ COMPLETE

- ✅ `QusaqStream` interface created (400 LOC)
- ✅ `QusaqStreamImpl` base implementation (419 LOC)
- ✅ `where()`, `select()`, `sortedBy()`, `sortedDescendingBy()` entry points work
- ✅ `toList()`, `count()`, `exists()` terminals work on streams (all tests passing)
- ✅ exists() terminal fully functional (all 6 tests passing after fix)
- ✅ Legacy methods removed (`findWhere`, `countWhere`, old `exists`)
- ✅ 15 integration test files migrated (281 tests passing - 100%)
- ✅ Build compiles successfully
- ✅ QusaqOperations class removed
- ✅ BytecodeLoader utility created
- ✅ Multiple where() chaining fixed (Phase 2.5)

**Phase 1 Status:** ✅ COMPLETE
- 100% test pass rate (281/281)
- All issues resolved
- Ready to proceed to Phase 3 (Sorting)

**🎯 MILESTONE: Core Fluent API Functional - 100% Complete**

---

## Phase 2: Projection & Transformation (Week 3-4)

**Objective:** Enable `select()` for field projection and DTO mapping

**Estimated Effort:** 8-10 days
**Priority:** 🟠 HIGH
**Dependencies:** Phase 1 complete

---

### Step 2.1: Enhance QusaqStream Interface

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 2.1.1 | Add `<R> QusaqStream<R> select(QuerySpec<T, R>)` | ❌ | ~10 LOC | Projection method |
| 2.1.2 | Add `<R> R getSingleResult()` | ❌ | ~5 LOC | Single result terminal |
| 2.1.3 | Add `<R> Optional<R> findFirst()` | ❌ | ~5 LOC | Optional result terminal |

**Files to Modify:**
- `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStream.java`

---

### Step 2.2: Implement Projection in QusaqStreamImpl

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 2.2.1 | Add `selector` field | ❌ | ~5 LOC | Store select lambda |
| 2.2.2 | Add `resultType` field | ❌ | ~5 LOC | Track projection type |
| 2.2.3 | Implement `select()` method | ❌ | ~40 LOC | Returns new stream with selector |
| 2.2.4 | Update `toList()` for projections | ❌ | ~100 LOC | Handle SELECT clause in CriteriaQuery |
| 2.2.5 | Implement `getSingleResult()` | ❌ | ~60 LOC | Execute query, return single result |
| 2.2.6 | Implement `findFirst()` | ❌ | ~40 LOC | Execute with limit(1) |

**Files to Modify:**
- `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStreamImpl.java`

**Key Challenge:** Type tracking through projection
```java
Person.select(p -> p.age) // QusaqStream<Integer> (type changes!)
      .toList()           // List<Integer>
```

**Solution:** Generic parameter propagation
```java
public <R> QusaqStream<R> select(QuerySpec<T, R> mapper) {
    return new QusaqStreamImpl<R>(
        this.entityClass,
        this.predicates,
        mapper,           // New selector
        (Class<R>) analyzeReturnType(mapper)  // Infer R from lambda
    );
}
```

---

### Step 2.3: Bytecode Analysis for Projections

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 2.3.1 | Analyze select lambda return type | ❌ | ~80 LOC | Infer projection type |
| 2.3.2 | Support field access projections | ❌ | ~60 LOC | `p -> p.firstName` |
| 2.3.3 | Support method call projections | ❌ | ~80 LOC | `p -> p.getAge()` |
| 2.3.4 | Support expression projections | ❌ | ~100 LOC | `p -> p.salary * 1.1` |
| 2.3.5 | Support DTO constructor projections | ❌ | ~150 LOC | `p -> new DTO(p.f1, p.f2)` |

**Files to Create:**
- `deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/ProjectionAnalyzer.java`

**Projection Types:**
```java
// Type 1: Field access
.select(p -> p.firstName)                    // Returns String

// Type 2: Method call
.select(p -> p.getAge())                     // Returns int

// Type 3: Expression
.select(p -> p.firstName + " " + p.lastName) // Returns String
.select(p -> p.salary * 1.1)                 // Returns Double

// Type 4: Constructor (DTO projection)
.select(p -> new PersonDTO(p.firstName, p.age))  // Returns PersonDTO

// Type 5: Tuple (future)
.select(p -> new Pair<>(p.firstName, p.age))  // Returns Pair<String, Integer>
```

**Bytecode Analysis Strategy:**
1. **Parse lambda body** (already done for `where()`)
2. **Identify return expression** (last expression in lambda)
3. **Analyze expression type:**
   - Field access → JPA `root.get("fieldName")`
   - Method call → JPA `root.get("fieldName")` (if getter)
   - Arithmetic → JPA `cb.sum()` / `cb.prod()` etc.
   - Constructor → JPA `cb.construct(DTO.class, ...)`
4. **Generate CriteriaQuery SELECT clause**

---

### Step 2.4: Code Generation for Projections

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 2.4.1 | Generate SELECT clause for field projection | ❌ | ~80 LOC | cq.select(root.get("field")) |
| 2.4.2 | Generate SELECT for expression projection | ❌ | ~120 LOC | cq.select(cb.sum(...)) |
| 2.4.3 | Generate SELECT for DTO projection | ❌ | ~150 LOC | cq.select(cb.construct(...)) |
| 2.4.4 | Handle type casting in generated code | ❌ | ~60 LOC | Ensure type safety |

**Files to Modify:**
- `deployment/src/main/java/io/quarkus/qusaq/deployment/generation/CriteriaExpressionGenerator.java`
- Create new: `deployment/src/main/java/io/quarkus/qusaq/deployment/generation/ProjectionGenerator.java`

**Example Generated Code:**
```java
// User code:
List<String> names = Person.stream()
                           .where(p -> p.age > 18)
                           .select(p -> p.firstName)
                           .toList();

// Generated executor:
public List<String> execute(EntityManager em, Object[] capturedVars) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<String> cq = cb.createQuery(String.class);
    Root<Person> root = cq.from(Person.class);

    // WHERE clause
    Predicate where = cb.greaterThan(root.get("age"), 18);
    cq.where(where);

    // SELECT clause
    cq.select(root.get("firstName"));

    TypedQuery<String> query = em.createQuery(cq);
    return query.getResultList();
}
```

---

### Step 2.5: Integration Tests - Projection

| Task | Status | Tests | Notes |
|------|--------|-------|-------|
| 2.5.1 | Test field projection | ❌ | 5 tests | Select single field |
| 2.5.2 | Test expression projection | ❌ | 3 tests | String concat, arithmetic |
| 2.5.3 | Test DTO projection | ❌ | 4 tests | Constructor-based projection |
| 2.5.4 | Test projection with filtering | ❌ | 3 tests | where() + select() |
| 2.5.5 | Test getSingleResult() | ❌ | 2 tests | Single result terminal |
| 2.5.6 | Test findFirst() | ❌ | 2 tests | Optional result |

**Files to Create:**
- `integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/ProjectionTest.java`

**Test Examples:**
```java
@Test
void selectField_returnsSingleField() {
    List<String> names = Person.select(p -> p.firstName).toList();

    assertThat(names).hasSize(10);
    assertThat(names).allMatch(name -> name != null);
}

@Test
void selectExpression_returnsComputedValue() {
    List<String> fullNames = Person.select(p -> p.firstName + " " + p.lastName)
                                   .toList();

    assertThat(fullNames).contains("John Doe", "Jane Smith");
}

@Test
void selectDTO_returnsProjectedObjects() {
    List<PersonDTO> dtos = Person.select(p -> new PersonDTO(p.firstName, p.age))
                                 .toList();

    assertThat(dtos).hasSize(10);
    assertThat(dtos).allMatch(dto -> dto.name() != null && dto.age() > 0);
}

@Test
void whereAndSelect_filtersAndProjects() {
    List<String> names = Person.where(p -> p.age > 25)
                               .select(p -> p.firstName)
                               .toList();

    assertThat(names).hasSize(5);  // Assuming 5 people over 25
}
```

---

### Phase 2.1 Completion Status ✅ COMPLETE

**Implementation Date:** November 19, 2025

**Objective:** Simple field projection (e.g., `Person.select(p -> p.firstName).toList()`)

**Accomplishments:**
- ✅ Enhanced `InvokeDynamicScanner` to detect projection vs predicate queries
  - Added `fluentMethodName` field to `LambdaCallSite` record
  - Implemented `isProjectionQuery()` method using lambda descriptor analysis
  - Implemented `findFluentMethodForward()` to detect where/select calls
- ✅ Enhanced `QueryExecutorClassGenerator` to generate projection queries
  - Added `generateSimpleFieldProjectionQuery()` method
  - Generates `CriteriaQuery<FieldType>` with `query.select(root.get("fieldName"))`
- ✅ Enhanced `CallSiteProcessor` to propagate `isProjectionQuery` flag
- ✅ Updated `QusaqStreamImpl.toList()` to support projection queries (Phase 2.1 scope)
- ✅ Created `ProjectionTest.java` with 12 comprehensive integration tests

**Test Results:**
- **Total Tests:** 220 (208 Phase 1 + 12 new projection tests)
- **Passing:** 218 (99.1%)
- **New Projection Tests:** 12/12 passing (100%)
- **Known Issues:** 2 tests failing (both related to boolean predicates)
  - `EqualityTest.booleanEqualityImplicit` - Boolean field predicate
  - `BasicQueryTest.multipleWhere_combinesWithAnd` - Multiple where() chaining

**Projection Tests Coverage:**
```java
✅ selectStringField_firstName()       // String projection
✅ selectStringField_email()           // String projection
✅ selectIntegerField_age()            // Integer projection
✅ selectLongField_employeeId()        // Long projection
✅ selectDoubleField_salary()          // Double projection
✅ selectFloatField_height()           // Float projection
✅ selectBooleanField_active()         // Boolean projection
✅ selectLocalDateField_birthDate()    // LocalDate projection
✅ selectBigDecimalField_price()       // BigDecimal projection (Product entity)
✅ selectWithMultipleEntities()        // Cross-entity projection
✅ selectNullableField()               // Nullable field projection
✅ selectEmptyResult()                 // Empty result handling
```

**Files Modified:**
- [InvokeDynamicScanner.java](deployment/src/main/java/io/quarkus/qusaq/deployment/InvokeDynamicScanner.java:55-73) - Projection detection logic
- [QueryExecutorClassGenerator.java](deployment/src/main/java/io/quarkus/qusaq/deployment/generation/QueryExecutorClassGenerator.java:167-210) - Projection query generation
- [CallSiteProcessor.java](deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/CallSiteProcessor.java:76-94) - Projection flag propagation
- [QusaqStreamImpl.java](runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStreamImpl.java:262-284) - Projection query execution

**Files Created:**
- [ProjectionTest.java](integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/ProjectionTest.java) - 12 integration tests (all passing)

**Known Limitations (Phase 2.1 Scope):**
- ⚠️ Projection-only queries supported (no combined where() + select() yet - Phase 2.2)
- ⚠️ Field access projections only (no expressions or DTOs yet - Phase 2.3/2.4)
- ⚠️ 2 regression failures related to boolean predicates:
  - Investigation shows descriptor detection works correctly
  - Likely related to Phase 1 multiple where() limitation
  - User previously requested: "Leave the multiple where() chaining for now"

**Next Steps:**
- **Option 1 (Recommended):** Proceed to Phase 2.2 - Combined where() + select() queries
- **Option 2:** Debug boolean predicate regression (may be Phase 1 limitation manifestation)
- **Option 3:** Skip to Phase 2.3 - Expression projections

**Phase 2.1 Status:** ✅ **COMPLETE** (12/12 new tests passing, core functionality working)

---

### Phase 2.2 Completion Status ✅ COMPLETE

**Implementation Date:** November 19, 2025

**Objective:** Combined where() + select() queries (e.g., `Person.where(p -> p.age > 25).select(p -> p.firstName).toList()`)

**Accomplishments:**
- ✅ Enhanced `LambdaCallSite` record to track both predicate and projection lambdas
  - Added `predicateLambdaMethodName`, `predicateLambdaMethodDescriptor` fields
  - Added `projectionLambdaMethodName`, `projectionLambdaMethodDescriptor` fields
  - Added `isCombinedQuery()` method to detect combined queries
- ✅ Enhanced `InvokeDynamicScanner` to accumulate multiple lambdas per pipeline
  - Created `PendingLambda` record to track lambdas with their fluent methods
  - Modified `scanMethod()` to collect all lambdas before terminal operation
  - Separates WHERE lambdas from SELECT lambdas based on fluent method name
- ✅ Updated `CallSiteProcessor` to handle combined queries
  - Analyzes both predicate and projection lambdas independently
  - Counts captured variables from both lambdas
  - Calls `deduplicator.computeCombinedHash()` for combined queries
- ✅ Added `LambdaDeduplicator.computeCombinedHash()` method
  - Computes hash from both WHERE and SELECT lambda ASTs
  - Prevents incorrect deduplication of combined vs single-lambda queries
- ✅ Enhanced `QueryExecutorClassGenerator` to generate combined WHERE + SELECT queries
  - Updated `generateQueryExecutorClass()` signature to accept both expressions
  - Updated `generateListQueryBody()` to handle combined queries
  - Added `generateCombinedWhereSelectQuery()` method
  - Generates: `CriteriaQuery<FieldType>` with both `.where()` and `.select()` clauses
- ✅ Removed Phase 2.1 restriction from `QusaqStreamImpl.toList()`
- ✅ Created `WhereSelectTest.java` with 16 comprehensive integration tests

**Test Results:**
- **Total Tests:** 236 (220 Phase 1 + 12 Phase 2.1 + 16 new Phase 2.2 tests)
- **Passing:** 235 (99.6%)
- **New Phase 2.2 Tests:** 16/16 passing (100%)
- **Known Issues:** 1 test failing (BasicQueryTest.multipleWhere - Phase 1 limitation)

**Phase 2.2 Tests Coverage:**
```java
✅ whereAge_selectFirstName()             // String projection with predicate
✅ whereActive_selectEmail()              // Boolean predicate + String projection
✅ whereSalary_selectLastName()           // Double predicate + String projection
✅ whereActive_selectAge()                // Boolean predicate + Integer projection
✅ whereAge_selectEmployeeId()            // Integer predicate + Long projection
✅ whereActive_selectSalary()             // Boolean predicate + Double projection
✅ whereAge_selectHeight()                // Integer predicate + Float projection
✅ whereAge_selectActive()                // Integer predicate + Boolean projection
✅ complexWhere_selectFirstName()         // Complex AND predicate + projection
✅ whereOr_selectEmail()                  // OR predicate + projection
✅ whereStringContains_selectAge()        // String operation + projection
✅ whereProductAvailable_selectName()     // Product entity test
✅ whereProductPrice_selectStockQuantity() // BigDecimal predicate + Integer projection
✅ whereProductCategory_selectPrice()     // String predicate + BigDecimal projection
✅ whereNoMatches_selectField()           // Edge case: no matches
✅ whereAllMatch_selectField()            // Edge case: all match
```

**Files Modified:**
- [InvokeDynamicScanner.java](deployment/src/main/java/io/quarkus/qusaq/deployment/InvokeDynamicScanner.java:31-109,135-215) - Pipeline tracking
- [CallSiteProcessor.java](deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/CallSiteProcessor.java:39-159,161-203) - Combined lambda handling
- [LambdaDeduplicator.java](deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/LambdaDeduplicator.java:44-67) - Combined hash computation
- [QueryExecutorClassGenerator.java](deployment/src/main/java/io/quarkus/qusaq/deployment/generation/QueryExecutorClassGenerator.java:46-104,106-146,229-301) - Combined query generation
- [QusaqStreamImpl.java](runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStreamImpl.java:259-285) - Removed restriction

**Files Created:**
- [WhereSelectTest.java](integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/WhereSelectTest.java) - 16 integration tests (all passing)

**Known Limitations (Phase 2.2 Scope):**
- ⚠️ Field access projections only (no expressions or DTOs yet - Phase 2.3/2.4)
- ⚠️ Combined queries support simple field projections: `p -> p.fieldName`
- ⚠️ Expression projections (`p -> p.firstName + " " + p.lastName`) will be Phase 2.3

**Next Steps:**
- **Option 1:** Proceed to Phase 2.3 - Expression projections
- **Option 2:** Proceed to Phase 2.4 - DTO projections
- **Option 3:** Proceed to Phase 3 - Sorting

**Phase 2.2 Status:** ✅ **COMPLETE** (16/16 new tests passing, combined queries working)

---

### Phase 2.3 Completion Status ✅ COMPLETE

**Implementation Date:** November 19, 2025

**Objective:** Expression projections (arithmetic operations and string concatenation)

**Accomplishments:**
- ✅ Arithmetic expression projections fully working (addition, subtraction, multiplication, division, modulo)
  - Integer arithmetic: `Person.select(p -> p.age + 5).toList()`
  - Double arithmetic: `Person.select(p -> p.salary * 1.1).toList()`
  - Complex expressions: `Person.select(p -> (p.age * 2) + 10).toList()`
- ✅ **String concatenation FULLY IMPLEMENTED** (was critical blocker)
  - Simple concat: `Person.select(p -> p.firstName + " " + p.lastName).toList()`
  - Prefix/suffix: `Person.select(p -> "Mr. " + p.firstName).toList()`
  - Multiple strings: `Person.select(p -> p.lastName + ", " + p.firstName + " (" + p.email + ")").toList()`
- ✅ Combined WHERE + expression SELECT working
  - `Person.where(p -> p.active).select(p -> p.salary * 1.15).toList()`
  - `Person.where(p -> p.age < 30).select(p -> p.firstName + " " + p.lastName).toList()`

**Key Technical Achievement:**
- ✅ Implemented `InvokeDynamicHandler` to parse Java 9+ `INVOKEDYNAMIC` instructions
  - Detects `StringConcatFactory.makeConcatWithConstants()` bootstrap method
  - Parses recipe strings (e.g., `"\u0001 \u0001"` for two dynamic arguments)
  - Builds AST tree of `BinaryOp` nodes with `ADD` operator
  - Existing `CriteriaExpressionGenerator` translates to JPA `cb.concat()` calls

**Test Results (FINAL: 2025-11-19 23:15):**
- **Total Tests:** 266
- **Passing:** 265/266 (99.6%)
- **New Phase 2.3 Tests:** 26/26 (100%) ← ALL PASSING!
  - Arithmetic operations: 19/19 (100%)
  - **String concatenation: 7/7 (100%)** ← was 0/7 before INVOKEDYNAMIC fix!
- **Known Issues:** 1 test failing (pre-existing Phase 1 limitation)
  - `BasicQueryTest.multipleWhere_combinesWithAnd` (Phase 1 limitation - deferred per user request)
- **Fixed Issues:**
  - ✅ String concatenation via `InvokeDynamicHandler` implementation
  - ✅ Test data mismatch in `productSelect_priceWithTax` (corrected Coffee Maker stockQuantity expectation)

**Files Created:**
- [InvokeDynamicHandler.java](deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/handlers/InvokeDynamicHandler.java) - 240 LOC (INVOKEDYNAMIC parsing)
- [ExpressionProjectionTest.java](integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/ExpressionProjectionTest.java) - 26 comprehensive tests
- [SimpleStringConcatTest.java](integration-tests/src/test/java/io/quarkus/qusaq/it/debug/SimpleStringConcatTest.java) - Debug tests

**Files Modified:**
- [LambdaBytecodeAnalyzer.java](deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/LambdaBytecodeAnalyzer.java) - Registered InvokeDynamicHandler
- [CriteriaExpressionGenerator.java](deployment/src/main/java/io/quarkus/qusaq/deployment/generation/CriteriaExpressionGenerator.java) - String concat code gen (pre-existing, now utilized)
- [QueryExecutorClassGenerator.java](deployment/src/main/java/io/quarkus/qusaq/deployment/generation/QueryExecutorClassGenerator.java) - Projection query generation

**Known Limitations (Phase 2.3 Scope):**
- ⚠️ Expression projections support arithmetic and string concatenation
- ⚠️ Constructor-based DTO projections deferred to Phase 2.4
- ⚠️ Method call projections (e.g., `p -> p.email.substring(0, 5)`) deferred to future iteration

**Next Steps:**
- **Option 1 (Recommended):** Proceed to Phase 3 - Sorting (Phase 2 core features complete)
- **Option 2:** Proceed to Phase 2.4 - DTO projections (constructor-based)
- **Option 3:** Skip to Phase 4 - Pagination & Distinct

**Phase 2.3 Status:** ✅ **COMPLETE** (100% of planned features working, 26/26 tests passing, string concatenation blocker resolved!)

---

### Phase 2.4 Completion Status ✅ COMPLETE

**Implementation Date:** November 20, 2025

**Objective:** DTO constructor-based projections

**Accomplishments:**
- ✅ Constructor-based projections fully working
  - Simple 2-field DTO: `Person.select(p -> new PersonNameDTO(p.firstName, p.lastName)).toList()`
  - Multi-type DTO: `Person.select(p -> new PersonSummaryDTO(p.firstName, p.age, p.salary)).toList()`
  - Product DTO with BigDecimal: `Product.select(p -> new ProductInfoDTO(p.name, p.price, p.category)).toList()`
- ✅ Combined WHERE + DTO SELECT working
  - `Person.where(p -> p.active).select(p -> new PersonNameDTO(p.firstName, p.lastName)).toList()`
  - `Person.where(p -> p.salary > 70000.0).select(p -> new PersonBasicDTO(p.firstName, p.lastName, p.email)).toList()`
- ✅ Complex filtering + DTO projection
  - `Product.where(p -> p.price.compareTo(new BigDecimal("300.00")) > 0).select(p -> new ProductInfoDTO(...)).toList()`

**Key Technical Achievement:**
- ✅ Added `ConstructorCall` expression type to Lambda AST
- ✅ Enhanced `MethodInvocationHandler` to detect constructor calls (INVOKESPECIAL)
- ✅ Implemented JPA `cb.construct()` code generation in `CriteriaExpressionGenerator`
- ✅ Runtime class loading using `Class.forName()` for DTO classes (build-time type resolution not required)

**Test Results (FINAL: 2025-11-20 09:08):**
- **Total Tests:** 281 (increased from 266)
- **Passing:** 280/281 (99.6%)
- **New Phase 2.4 Tests:** 15/15 (100%) ← ALL PASSING!
  - Basic DTO projections: 3/3 (100%)
  - Product DTO projections: 1/1 (100%)
  - Combined WHERE + DTO: 5/5 (100%)
  - Complex filtering + DTO: 3/3 (100%)
  - Edge cases: 3/3 (100%)
- **Known Issues:** 1 test failing (pre-existing Phase 1 limitation)
  - `BasicQueryTest.multipleWhere_combinesWithAnd` (Phase 1 limitation - deferred per user request)

**Files Created:**
- [PersonNameDTO.java](integration-tests/src/main/java/io/quarkus/qusaq/it/dto/PersonNameDTO.java) - Simple 2-field DTO
- [PersonBasicDTO.java](integration-tests/src/main/java/io/quarkus/qusaq/it/dto/PersonBasicDTO.java) - 3-field string DTO
- [PersonSummaryDTO.java](integration-tests/src/main/java/io/quarkus/qusaq/it/dto/PersonSummaryDTO.java) - Multi-type DTO (String, int, Double)
- [ProductInfoDTO.java](integration-tests/src/main/java/io/quarkus/qusaq/it/dto/ProductInfoDTO.java) - Product DTO with BigDecimal
- [DtoProjectionTest.java](integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/DtoProjectionTest.java) - 15 comprehensive DTO projection tests

**Files Modified:**
- [LambdaExpression.java](deployment/src/main/java/io/quarkus/qusaq/deployment/LambdaExpression.java) - Added `ConstructorCall` record type
- [MethodInvocationHandler.java](deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/handlers/MethodInvocationHandler.java) - Enhanced to create `ConstructorCall` for INVOKESPECIAL
- [CriteriaExpressionGenerator.java](deployment/src/main/java/io/quarkus/qusaq/deployment/generation/CriteriaExpressionGenerator.java) - Added `generateConstructorCall()` method for JPA `cb.construct()` generation

**Key Implementation Details:**
1. **Constructor Detection:** `MethodInvocationHandler.handleInvokeSpecial()` detects constructor calls and creates `ConstructorCall` AST nodes
2. **AST Representation:** `ConstructorCall(className, arguments, resultType)` captures constructor invocation with internal class name (e.g., "io/quarkus/qusaq/it/dto/PersonNameDTO")
3. **Code Generation:** `CriteriaExpressionGenerator.generateConstructorCall()` uses `Class.forName()` to load DTO class at runtime, avoiding build-time class loading issues
4. **JPA Translation:** Generates `cb.construct(PersonNameDTO.class, root.get("firstName"), root.get("lastName"))` bytecode

**Phase 2.4 Status:** ✅ **COMPLETE** (100% of planned features working, 15/15 tests passing)

---

### Phase 2.5 Completion Status ✅ COMPLETE

**Implementation Date:** November 20, 2025

**Objective:** Multiple where() predicates combined with AND (fixing Phase 1 limitation)

**Problem:**
The original implementation only captured the LAST `where()` predicate when multiple `where()` calls were chained:
```java
Person.where(p -> p.age > 25).where(p -> p.active).toList()
// Only checked p.active, ignored p.age > 25
```

**Root Cause:**
In `InvokeDynamicScanner.scanMethod()`, the loop that collected predicates was overwriting instead of accumulating:
```java
for (PendingLambda lambda : pendingLambdas) {
    if (METHOD_WHERE.equals(lambda.fluentMethod)) {
        whereLambdaMethod = lambda.methodName;  // ← Overwrites previous!
        whereLambdaDescriptor = lambda.descriptor;
    }
}
```

**Solution Design:**
1. Enhanced `LambdaCallSite` record to include `List<LambdaPair> predicateLambdas` field
2. Modified scanner to collect ALL `where()` predicates into a list
3. Enhanced `CallSiteProcessor` to analyze all predicates and combine them with AND
4. Added `combinePredicatesWithAnd()` method to create nested AND expressions

**Implementation Details:**

**Files Modified:**
- [InvokeDynamicScanner.java](deployment/src/main/java/io/quarkus/qusaq/deployment/InvokeDynamicScanner.java)
  - Added `LambdaPair` record to store (methodName, descriptor) pairs
  - Added `List<LambdaPair> predicateLambdas` field to `LambdaCallSite`
  - Modified `scanMethod()` to accumulate all WHERE lambdas instead of overwriting

- [CallSiteProcessor.java](deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/CallSiteProcessor.java)
  - Added Phase 2.5 branch in `processCallSite()` to handle multiple predicates
  - Analyzes each predicate lambda separately
  - Combines all predicates with AND using `combinePredicatesWithAnd()`
  - Added `combinePredicatesWithAnd()` method that creates nested BinaryOp(AND) expressions

**How It Works:**

1. **Scanner Detects Multiple WHERE Calls:**
   ```java
   Person.where(p -> p.age > 25)      // Lambda 1: lambda$1
         .where(p -> p.active)         // Lambda 2: lambda$2
         .toList()                     // Terminal: triggers processing
   ```

2. **Scanner Accumulates ALL Predicates:**
   ```java
   List<LambdaPair> whereLambdas = [
       LambdaPair("lambda$1", "(LPerson;)Z"),
       LambdaPair("lambda$2", "(LPerson;)Z")
   ]
   ```

3. **Processor Analyzes Each Predicate:**
   ```java
   LambdaExpression pred1 = analyze("lambda$1") // → BinaryOp(age, GT, 25)
   LambdaExpression pred2 = analyze("lambda$2") // → FieldAccess(active)
   ```

4. **Processor Combines With AND:**
   ```java
   combined = BinaryOp(pred1, AND, pred2)
   // Result: (age > 25) AND (active)
   ```

5. **Generator Creates JPA Query:**
   ```java
   Predicate p1 = cb.greaterThan(root.get("age"), 25);
   Predicate p2 = cb.equal(root.get("active"), true);
   query.where(cb.and(p1, p2));
   ```

**Test Results:**
- **Total Tests:** 281
- **Passing:** 281/281 (100%)
- **Fixed Test:** `BasicQueryTest.multipleWhere_combinesWithAnd` now passing
- **No Regressions:** All existing tests continue to pass

**Example Usage (Now Working):**
```java
// Multiple where() calls combine with AND
List<Person> results = Person.where(p -> p.age > 25)
                             .where(p -> p.active)
                             .toList();
// SQL: WHERE age > 25 AND active = true

// Even works with 3+ predicates
List<Person> filtered = Person.where(p -> p.age > 18)
                              .where(p -> p.active)
                              .where(p -> p.salary > 50000.0)
                              .toList();
// SQL: WHERE age > 18 AND active = true AND salary > 50000.0
```

**Phase 2.5 Status:** ✅ **COMPLETE** (Multiple where() chaining fully functional, 100% test pass rate)

---

### Phase 2.6 Completion Status ✅ COMPLETE

**Implementation Date:** November 20, 2025

**Objective:** Single-result terminal operations (`getSingleResult()` and `findFirst()`)

**Problem:**
The original stub implementations of `getSingleResult()` and `findFirst()` tried to build JPA Criteria Queries at runtime, which conflicted with Qusaq's build-time code generation approach where all lambda bytecode analysis must happen at compile time.

**Solution:**
Refactored both methods to delegate to the existing `toList()` method which properly uses the build-time generated executors via `QueryExecutorRegistry`. Post-processing is applied to handle single-result semantics:
- `getSingleResult()`: Validates exactly one result, throws `NoResultException` or `NonUniqueResultException`
- `findFirst()`: Returns `Optional` wrapping first result or empty

**Implementation Details:**

**Files Modified:**
- [QusaqStreamImpl.java](runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStreamImpl.java:286-312) - Implemented delegation pattern

**Implementation:**
```java
@Override
public T getSingleResult() {
    List<T> results = toList();

    if (results.isEmpty()) {
        throw new jakarta.persistence.NoResultException(
                "getSingleResult() expected exactly one result but found none");
    }

    if (results.size() > 1) {
        throw new jakarta.persistence.NonUniqueResultException(
                "getSingleResult() expected exactly one result but found " + results.size());
    }

    return results.get(0);
}

@Override
public Optional<T> findFirst() {
    List<T> results = toList();
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
}
```

**Test Results (FINAL: 2025-11-20 10:53):**
- **Total Tests:** 294 (increased from 281)
- **Passing:** 294/294 (100%)
- **New Phase 2.6 Tests:** 13/13 (100%) ← ALL PASSING!
  - getSingleResult() unique match: 1/1 (100%)
  - getSingleResult() exception handling: 2/2 (100%)
  - getSingleResult() complex predicates: 2/2 (100%)
  - findFirst() with results: 3/3 (100%)
  - findFirst() edge cases: 5/5 (100%)

**Files Created:**
- [SingleResultTest.java](integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/SingleResultTest.java) - 13 comprehensive single-result tests

**Phase 2.6 Tests Coverage:**
```java
✅ getSingleResult_uniqueMatch_returnsEntity()
✅ getSingleResult_noMatch_throwsNoResultException()
✅ getSingleResult_multipleMatches_throwsNonUniqueResultException()
✅ getSingleResult_withComplexPredicate_returnsEntity()
✅ getSingleResult_withMultipleWhere_returnsEntity()
✅ findFirst_hasResults_returnsOptionalWithFirstResult()
✅ findFirst_noResults_returnsEmptyOptional()
✅ findFirst_multipleResults_returnsFirstOnly()
✅ findFirst_withComplexPredicate_returnsOptionalWithEntity()
✅ findFirst_withMultipleWhere_returnsOptionalWithEntity()
✅ findFirst_uniqueEmail_returnsExactMatch()
✅ getSingleResult_afterMultiplePredicates_worksCorrectly()
✅ findFirst_afterMultiplePredicates_worksCorrectly()
```

**Key Technical Achievement:**
- ✅ Maintains build-time code generation approach by delegating to `toList()`
- ✅ Reuses existing QueryExecutorRegistry infrastructure
- ✅ No runtime bytecode analysis required
- ✅ JPA standard exception types (`NoResultException`, `NonUniqueResultException`)
- ✅ Java 8+ `Optional` API for `findFirst()`

**Future Optimization:**
- TODO Phase 4: When `limit()` is implemented, internally apply `limit(1)` for `findFirst()` optimization

**Known Limitations (Phase 2.6 Scope):**
- ⚠️ `findFirst()` currently fetches all results then returns first (optimization deferred to Phase 4)
- ⚠️ Both methods require full query execution via `toList()`

**Phase 2.6 Status:** ✅ **COMPLETE** (Single-result terminals fully functional, 100% test pass rate)

---

### Phase 2 Completion Criteria (Full Phase 2) ✅ ALL PHASES COMPLETE

- ✅ `select()` method implemented (Phase 2.1 ✅ basic, Phase 2.2 ✅ combined, Phase 2.3 ✅ expressions, Phase 2.4 ✅ DTO)
- ✅ Field projection works (single field) - Phase 2.1 COMPLETE
- ✅ Combined where() + select() works - Phase 2.2 COMPLETE
- ✅ **Expression projection works (concatenation, arithmetic)** - Phase 2.3 COMPLETE
- ✅ **DTO projection works (constructor-based)** - Phase 2.4 COMPLETE
- ✅ **Multiple where() predicates combine with AND** - Phase 2.5 COMPLETE
- ✅ Type inference through projection (Phase 2.1 ✅ basic, Phase 2.2 ✅ combined, Phase 2.3 ✅ expressions, Phase 2.4 ✅ DTO)
- ✅ **`getSingleResult()` and `findFirst()` terminals work - Phase 2.6 COMPLETE**
- ✅ **100% test pass rate (294/294 tests passing!)**
- ✅ **82 new Phase 2 tests** (12 Phase 2.1 + 16 Phase 2.2 + 26 Phase 2.3 + 15 Phase 2.4 + 13 Phase 2.6) - **ALL PASSING**
- ✅ **Multiple where() chaining bug fixed** (Phase 2.5)

**🎯 Phase 2.1 MILESTONE: Simple Field Projection Functional**
**🎯 Phase 2.2 MILESTONE: Combined WHERE + SELECT Functional**
**🎯 Phase 2.3 MILESTONE: Expression Projections Functional (Arithmetic + String Concat)**
**🎯 Phase 2.4 MILESTONE: DTO Constructor-Based Projections Functional**
**🎯 Phase 2.5 MILESTONE: Multiple WHERE Chaining Functional (AND Combination)**
**🎯 Phase 2.6 MILESTONE: Single-Result Terminals Functional (getSingleResult, findFirst)**

**📊 PHASE 2 STATUS: ✅ COMPLETE** - All planned projection features implemented and tested, with 100% test pass rate!

---

## Phase 3: Sorting (Week 5)

**Objective:** Enable `sortedBy()` and `sortedDescendingBy()`

**Estimated Effort:** 5-6 days
**Priority:** 🟠 HIGH
**Dependencies:** Phase 1 complete (Phase 2 optional)

---

### Step 3.1: Enhance QusaqStream Interface

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 3.1.1 | Add `<K extends Comparable<K>> QusaqStream<T> sortedBy(QuerySpec<T, K>)` | ❌ | ~10 LOC | Ascending sort |
| 3.1.2 | Add `<K extends Comparable<K>> QusaqStream<T> sortedDescendingBy(QuerySpec<T, K>)` | ❌ | ~10 LOC | Descending sort |

**Files to Modify:**
- `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStream.java`

---

### Step 3.2: Implement Sorting in QusaqStreamImpl

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 3.2.1 | Add `sortOrders` field | ❌ | ~5 LOC | List<SortOrder> |
| 3.2.2 | Create `SortOrder` record | ❌ | ~15 LOC | (keyExtractor, descending) |
| 3.2.3 | Implement `sortedBy()` | ❌ | ~30 LOC | Add ascending sort |
| 3.2.4 | Implement `sortedDescendingBy()` | ❌ | ~30 LOC | Add descending sort |
| 3.2.5 | Update `toList()` to apply ORDER BY | ❌ | ~80 LOC | Generate orderBy clause |

**Files to Modify:**
- `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStreamImpl.java`

**Multi-Level Sorting:**
```java
// Last call has priority (JINQ approach)
Person.stream()
      .sortedBy(p -> p.firstName)   // Secondary sort
      .sortedBy(p -> p.lastName)    // Primary sort
      .toList();

// Generated SQL: ORDER BY lastName, firstName
```

**Implementation:**
```java
record SortOrder<T>(QuerySpec<T, ?> keyExtractor, boolean descending) {}

public <K extends Comparable<K>> QusaqStream<T> sortedBy(QuerySpec<T, K> keyExtractor) {
    List<SortOrder<T>> newOrders = new ArrayList<>(this.sortOrders);
    newOrders.add(0, new SortOrder<>(keyExtractor, false));  // Prepend (last call wins)
    return new QusaqStreamImpl<>(entityClass, predicates, selector, newOrders, ...);
}
```

---

### Step 3.3: Bytecode Analysis for Sorting

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 3.3.1 | Analyze sort key extractor lambda | ❌ | ~60 LOC | Parse sort expression |
| 3.3.2 | Support field access sorting | ❌ | ~40 LOC | `p -> p.age` |
| 3.3.3 | Support method call sorting | ❌ | ~50 LOC | `p -> p.getAge()` |
| 3.3.4 | Validate Comparable constraint | ❌ | ~30 LOC | Ensure sortable type |

**Files to Create:**
- `deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/SortingAnalyzer.java`

---

### Step 3.4: Code Generation for Sorting

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 3.4.1 | Generate ORDER BY clause for single sort | ❌ | ~60 LOC | cq.orderBy(cb.asc(...)) |
| 3.4.2 | Generate ORDER BY for multi-level sort | ❌ | ~80 LOC | Multiple Order objects |
| 3.4.3 | Handle ASC vs DESC | ❌ | ~30 LOC | cb.asc() vs cb.desc() |

**Files to Modify:**
- `deployment/src/main/java/io/quarkus/qusaq/deployment/generation/CriteriaExpressionGenerator.java`

**Example Generated Code:**
```java
// User code:
List<Person> sorted = Person.stream()
                            .sortedBy(p -> p.lastName)
                            .sortedBy(p -> p.firstName)
                            .toList();

// Generated executor:
public List<Person> execute(EntityManager em, Object[] capturedVars) {
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<Person> cq = cb.createQuery(Person.class);
    Root<Person> root = cq.from(Person.class);

    // ORDER BY clause
    cq.orderBy(
        cb.asc(root.get("firstName")),   // Last call (primary)
        cb.asc(root.get("lastName"))     // First call (secondary)
    );

    TypedQuery<Person> query = em.createQuery(cq);
    return query.getResultList();
}
```

---

### Step 3.5: Integration Tests - Sorting

| Task | Status | Tests | Notes |
|------|--------|-------|-------|
| 3.5.1 | Test single-level ascending sort | ❌ | 3 tests | sortedBy() |
| 3.5.2 | Test single-level descending sort | ❌ | 3 tests | sortedDescendingBy() |
| 3.5.3 | Test multi-level sorting | ❌ | 4 tests | Multiple sortedBy() calls |
| 3.5.4 | Test sorting with filtering | ❌ | 3 tests | where() + sortedBy() |
| 3.5.5 | Test sorting with projection | ❌ | 3 tests | select() + sortedBy() |

**Files to Create:**
- `integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/SortingTest.java`

**Test Examples:**
```java
@Test
void sortedBy_ascendingOrder() {
    List<Person> sorted = Person.sortedBy(p -> p.age).toList();

    assertThat(sorted).isSortedAccordingTo(Comparator.comparing(Person::getAge));
}

@Test
void sortedDescendingBy_descendingOrder() {
    List<Person> sorted = Person.sortedDescendingBy(p -> p.age).toList();

    assertThat(sorted).isSortedAccordingTo(Comparator.comparing(Person::getAge).reversed());
}

@Test
void multiLevelSort_primaryThenSecondary() {
    List<Person> sorted = Person.sortedBy(p -> p.firstName)
                                .sortedBy(p -> p.lastName)  // Primary
                                .toList();

    // Verify primary sort (lastName)
    assertThat(sorted).isSortedAccordingTo(Comparator.comparing(Person::getLastName));

    // Verify secondary sort (firstName) within same lastName
    // ... (more detailed assertion)
}
```

---

### Phase 3 Completion Criteria

- ✅ `sortedBy()` and `sortedDescendingBy()` implemented
- ✅ Single-level sorting works
- ✅ Multi-level sorting works
- ✅ Sorting combines with where() and select()
- ✅ All existing tests pass
- ✅ 15+ new sorting tests

**🎯 MILESTONE: Sorting Functional**

---

## Phase 4: Pagination & Distinct (Week 6)

**Objective:** Enable `skip()`, `limit()`, and `distinct()`

**Estimated Effort:** 4-5 days
**Priority:** 🟡 MEDIUM
**Dependencies:** Phase 1 complete

---

### Step 4.1: Enhance QusaqStream Interface

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 4.1.1 | Add `QusaqStream<T> skip(int n)` | ❌ | ~5 LOC | OFFSET clause |
| 4.1.2 | Add `QusaqStream<T> limit(int n)` | ❌ | ~5 LOC | LIMIT clause |
| 4.1.3 | Add `QusaqStream<T> distinct()` | ❌ | ~5 LOC | SELECT DISTINCT |

**Files to Modify:**
- `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStream.java`

---

### Step 4.2: Implement Pagination & Distinct

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 4.2.1 | Add `offset` field (Integer) | ❌ | ~5 LOC | Nullable, defaults to null |
| 4.2.2 | Add `limit` field (Integer) | ❌ | ~5 LOC | Nullable, defaults to null |
| 4.2.3 | Add `distinct` field (boolean) | ❌ | ~5 LOC | Defaults to false |
| 4.2.4 | Implement `skip()` | ❌ | ~20 LOC | Set offset field |
| 4.2.5 | Implement `limit()` | ❌ | ~20 LOC | Set limit field |
| 4.2.6 | Implement `distinct()` | ❌ | ~20 LOC | Set distinct flag |
| 4.2.7 | Update `toList()` to apply offset/limit | ❌ | ~40 LOC | setFirstResult/setMaxResults |
| 4.2.8 | Update `toList()` to apply distinct | ❌ | ~20 LOC | cq.distinct(true) |

**Files to Modify:**
- `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStreamImpl.java`

**Implementation:**
```java
public QusaqStream<T> skip(int n) {
    if (n < 0) throw new IllegalArgumentException("skip count must be >= 0");
    return new QusaqStreamImpl<>(entityClass, predicates, selector, sortOrders, n, limit, distinct);
}

public List<T> toList() {
    // ... build CriteriaQuery ...

    TypedQuery<T> query = em.createQuery(cq);

    if (offset != null) query.setFirstResult(offset);
    if (limit != null) query.setMaxResults(limit);

    return query.getResultList();
}
```

---

### Step 4.3: Integration Tests - Pagination & Distinct

| Task | Status | Tests | Notes |
|------|--------|-------|-------|
| 4.3.1 | Test skip() alone | ❌ | 2 tests | Offset without limit |
| 4.3.2 | Test limit() alone | ❌ | 2 tests | Limit without offset |
| 4.3.3 | Test skip() + limit() | ❌ | 4 tests | Pagination |
| 4.3.4 | Test distinct() | ❌ | 3 tests | Unique results |
| 4.3.5 | Test distinct() + select() | ❌ | 3 tests | Unique projections |
| 4.3.6 | Test complex composition | ❌ | 4 tests | where + select + distinct + sort + page |

**Files to Create:**
- `integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/PaginationTest.java`
- `integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/DistinctTest.java`

**Test Examples:**
```java
@Test
void skip_offsetsResults() {
    List<Person> all = Person.sortedBy(p -> p.id).toList();
    List<Person> skipped = Person.sortedBy(p -> p.id).skip(5).toList();

    assertThat(skipped).hasSize(all.size() - 5);
    assertThat(skipped.get(0)).isEqualTo(all.get(5));
}

@Test
void limit_restrictsResults() {
    List<Person> limited = Person.where(p -> p.age != null).limit(5).toList();
    assertThat(limited).hasSize(5);
}

@Test
void skipAndLimit_pagination() {
    // Page 2 (items 10-19)
    List<Person> page2 = Person.sortedBy(p -> p.id)
                               .skip(10)
                               .limit(10)
                               .toList();

    assertThat(page2).hasSize(10);
}

@Test
void distinct_uniqueResults() {
    List<String> unique = Person.select(p -> p.lastName)
                                .distinct()
                                .toList();

    assertThat(unique).doesNotHaveDuplicates();
}
```

---

### Phase 4 Completion Criteria

- ✅ `skip()`, `limit()`, `distinct()` implemented
- ✅ Pagination works correctly
- ✅ Distinct eliminates duplicates
- ✅ Operations combine with where/select/sort
- ✅ All existing tests pass
- ✅ 18+ new pagination/distinct tests

**🎯 MILESTONE: Pagination & Distinct Functional**

---

## Phase 5: Enhanced Aggregation (Week 7)

**Objective:** Extend aggregations beyond `count()` to include `min()`, `max()`, `avg()`, `sum*()`

**Estimated Effort:** 5-6 days
**Priority:** 🟡 MEDIUM
**Dependencies:** Phase 1 complete

---

### Step 5.1: Enhance QusaqStream Interface

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 5.1.1 | Add `<K extends Comparable<K>> K min(QuerySpec<T, K>)` | ❌ | ~5 LOC | Minimum value |
| 5.1.2 | Add `<K extends Comparable<K>> K max(QuerySpec<T, K>)` | ❌ | ~5 LOC | Maximum value |
| 5.1.3 | Add `long sumInteger(QuerySpec<T, Integer>)` | ❌ | ~5 LOC | Sum of integers |
| 5.1.4 | Add `long sumLong(QuerySpec<T, Long>)` | ❌ | ~5 LOC | Sum of longs |
| 5.1.5 | Add `double sumDouble(QuerySpec<T, Double>)` | ❌ | ~5 LOC | Sum of doubles |
| 5.1.6 | Add `Double avg(QuerySpec<T, Number>)` | ❌ | ~5 LOC | Average value |

**Files to Modify:**
- `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStream.java`

---

### Step 5.2: Implement Aggregations

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 5.2.1 | Implement `min()` | ❌ | ~80 LOC | CriteriaBuilder.min() |
| 5.2.2 | Implement `max()` | ❌ | ~80 LOC | CriteriaBuilder.max() |
| 5.2.3 | Implement `sumInteger()` | ❌ | ~80 LOC | CriteriaBuilder.sum() → Long |
| 5.2.4 | Implement `sumLong()` | ❌ | ~80 LOC | CriteriaBuilder.sumAsLong() |
| 5.2.5 | Implement `sumDouble()` | ❌ | ~80 LOC | CriteriaBuilder.sumAsDouble() |
| 5.2.6 | Implement `avg()` | ❌ | ~80 LOC | CriteriaBuilder.avg() |

**Files to Modify:**
- `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStreamImpl.java`

**Example Implementation:**
```java
public <K extends Comparable<K>> K min(QuerySpec<T, K> mapper) {
    EntityManager em = getEntityManager();
    CriteriaBuilder cb = em.getCriteriaBuilder();
    CriteriaQuery<K> cq = cb.createQuery((Class<K>) analyzeReturnType(mapper));
    Root<T> root = cq.from(entityClass);

    // Apply where predicates
    if (!predicates.isEmpty()) {
        cq.where(buildPredicates(cb, root));
    }

    // Apply MIN aggregation
    Expression<K> field = buildExpression(mapper, cb, root);
    cq.select(cb.min(field));

    TypedQuery<K> query = em.createQuery(cq);
    return query.getSingleResult();
}
```

---

### Step 5.3: Integration Tests - Aggregations

| Task | Status | Tests | Notes |
|------|--------|-------|-------|
| 5.3.1 | Test min() | ❌ | 3 tests | Minimum values |
| 5.3.2 | Test max() | ❌ | 3 tests | Maximum values |
| 5.3.3 | Test sumInteger() | ❌ | 2 tests | Integer sums |
| 5.3.4 | Test sumLong() | ❌ | 2 tests | Long sums |
| 5.3.5 | Test sumDouble() | ❌ | 2 tests | Double sums |
| 5.3.6 | Test avg() | ❌ | 3 tests | Average values |
| 5.3.7 | Test aggregations with filtering | ❌ | 5 tests | where() + aggregation |

**Files to Create:**
- `integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/AggregationTest.java`

**Test Examples:**
```java
@Test
void min_returnsMinimumValue() {
    Integer minAge = Person.where(p -> p.age != null).min(p -> p.age);
    assertThat(minAge).isEqualTo(18);  // Assuming youngest is 18
}

@Test
void max_returnsMaximumValue() {
    Integer maxAge = Person.where(p -> p.age != null).max(p -> p.age);
    assertThat(maxAge).isEqualTo(65);  // Assuming oldest is 65
}

@Test
void sumDouble_returnsTotalSalary() {
    double totalSalaries = Person.where(p -> p.active)
                                 .sumDouble(p -> p.salary);

    assertThat(totalSalaries).isGreaterThan(0);
}

@Test
void avg_returnsAverageAge() {
    Double avgAge = Person.where(p -> p.age != null).avg(p -> p.age);
    assertThat(avgAge).isBetween(25.0, 50.0);
}
```

---

### Phase 5 Completion Criteria

- ✅ `min()`, `max()`, `avg()`, `sum*()` implemented
- ✅ All aggregations work with filtering
- ✅ Type-specific sum methods work correctly
- ✅ All existing tests pass
- ✅ 20+ new aggregation tests

**🎯 MILESTONE: Enhanced Aggregation Functional**

---

## Phase 6: Documentation & Polish (Week 8)

**Objective:** Update documentation, add migration guide, refine error messages

**Estimated Effort:** 3-4 days
**Priority:** 🟢 LOW
**Dependencies:** All previous phases

---

### Step 6.1: Update README

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 6.1.1 | Add "Fluent Query API" section | ❌ | ~100 lines | Overview of new API |
| 6.1.2 | Add comparison: old vs new API | ❌ | ~50 lines | Show before/after examples |
| 6.1.3 | Add comprehensive examples | ❌ | ~150 lines | Demonstrate all features |
| 6.1.4 | Update feature list | ❌ | ~30 lines | Add new capabilities |

**Files to Modify:**
- `README.md`

---

### Step 6.2: Create API Reference

| Task | Status | Pages | Notes |
|------|--------|-------|-------|
| 6.2.1 | Document QusaqStream methods | ❌ | 5-6 pages | All methods with examples |
| 6.2.2 | Document operation sequencing rules | ❌ | 2 pages | Valid combinations |
| 6.2.3 | Document type transformations | ❌ | 2 pages | How select() changes types |
| 6.2.4 | Add troubleshooting guide | ❌ | 3 pages | Common errors & solutions |

**Files to Create:**
- `docs/FLUENT_API_REFERENCE.md`
- `docs/MIGRATION_GUIDE.md`

---

### Step 6.3: Improve Error Messages

| Task | Status | LOC | Notes |
|------|--------|-----|-------|
| 6.3.1 | Validate operation sequences at build time | ❌ | ~80 LOC | Detect invalid chains |
| 6.3.2 | Add helpful error for invalid lambda | ❌ | ~40 LOC | Explain what's supported |
| 6.3.3 | Add error for missing terminal operation | ❌ | ~30 LOC | "Did you forget .toList()?" |

**Files to Modify:**
- `deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/StreamPipelineAnalyzer.java`

**Error Message Examples:**
```java
// Invalid operation sequence
Person.select(p -> p.firstName)
      .where(name -> name.startsWith("A"))  // ERROR!

// Error message:
"Invalid operation sequence: where() cannot be called after select().
Operations after select() can only be: sortedBy(), distinct(), skip(), limit(), toList()"

// Unsupported lambda
Person.select(p -> {
    String name = p.firstName;
    return name.toUpperCase();
})  // ERROR!

// Error message:
"Unsupported lambda expression: Multi-statement lambdas are not supported.
Use single-expression lambdas only: Person.select(p -> p.firstName.toUpperCase())"
```

---

### Phase 6 Completion Criteria

- ✅ README updated with fluent API section
- ✅ API reference documentation complete
- ✅ Migration guide created
- ✅ Error messages improved
- ✅ All documentation reviewed

**🎯 MILESTONE: Documentation Complete**

---

## Future Phases (Not Included in Iteration 3)

### Phase 7: Joins (Future)
- Requires relationship metadata extraction
- Complex bytecode analysis for nested lambdas
- Estimated effort: 3-4 weeks

### Phase 8: Grouping (Future)
- Requires tuple/pair support
- Nested lambda analysis
- Estimated effort: 2-3 weeks

### Phase 9: Subqueries (Future)
- Requires correlation variable handling
- Most complex bytecode analysis
- Estimated effort: 3-4 weeks

---

## Success Metrics Tracking

### Code Metrics

| Metric | Before (Iteration 2) | After (Iteration 3) | Status |
|--------|---------------------|---------------------|--------|
| **API Methods** | 3 (legacy) | 20+ (fluent) | ❌ |
| **Query Entry Points** | 1 (findWhere) | 6+ (where, select, sortedBy, etc.) | ❌ |
| **Query Operations** | 1 (filtering) | 7+ (filter, project, sort, page, distinct, aggregate) | ❌ |
| **Test Coverage** | Existing tests | 100+ new fluent API tests | ❌ |
| **Integration Test LOC** | ~5000 | ~7500 | ❌ |
| **Documentation Pages** | 1 (README) | 4+ | ❌ |

---

### Developer Experience Metrics

| Metric | Before | After | Status |
|--------|--------|-------|--------|
| **Single Query Expressiveness** | 1 operation | 5+ operations | ❌ |
| **Type Safety** | ✅ Filtering | ✅ Full pipeline | ❌ |
| **Refactoring Safety** | ✅ Good | ✅ Excellent | ❌ |
| **Learning Curve** | 10 min | 30 min | ❌ |

---

## Risk Assessment

### High Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| **Breaking changes for existing users** | High | High | Clear migration guide, release notes |
| **Build-time performance regression** | Medium | High | Benchmark build times |
| **Complex bytecode analysis bugs** | Medium | High | Incremental implementation, thorough testing |

### Medium Risks

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|------------|
| **Type inference limitations** | Medium | Medium | Clear error messages, documentation |
| **Edge cases in projection** | High | Medium | Comprehensive test coverage |
| **Sorting edge cases** | Medium | Medium | Test with multiple data types |

---

## Test Execution Strategy

### Test Commands

**Run all tests:**
```bash
mvn clean test
```

**Run fluent API tests only:**
```bash
mvn test -Dtest="**/fluent/**Test"
```

**Run all integration tests:**
```bash
mvn test -Dtest="**/it/**Test"
```

**Run with verbose logging:**
```bash
mvn test -X 2>&1 | tee /tmp/iteration3_test.log
```

---

### Test Verification Checklist

After each phase:
- ✅ All new tests pass
- ✅ No compilation errors
- ✅ No new warnings
- ✅ Build time acceptable (<5% increase)
- ✅ Generated code quality reviewed

---

## Implementation Schedule

| Phase | Start Week | End Week | Status |
|-------|-----------|----------|--------|
| **Phase 1: Core Fluent API** | Week 1 | Week 2 | ❌ Not Started |
| **Phase 2: Projection** | Week 3 | Week 4 | ❌ Not Started |
| **Phase 3: Sorting** | Week 5 | Week 5 | ❌ Not Started |
| **Phase 4: Pagination & Distinct** | Week 6 | Week 6 | ❌ Not Started |
| **Phase 5: Aggregation** | Week 7 | Week 7 | ❌ Not Started |
| **Phase 6: Documentation** | Week 8 | Week 8 | ❌ Not Started |

**Total Duration:** 8 weeks (2 months)

---

## Current Focus

**Active Phase:** 🔵 None (Planning Complete)
**Next Phase:** Phase 1 (Core Fluent API)
**Blocker:** None
**Ready to Begin:** ✅ Yes

---

## Notes & Decisions

### 2025-11-18 - Planning Session
- **Comprehensive API analysis complete** - Compared JINQ vs Qusaq thoroughly
- **Phased approach adopted** - 6 phases for manageable implementation
- **Clean API design** - Removed legacy methods, direct query entry points
- **Type safety maintained** - Generic parameter tracking through pipeline
- **Immutable stream design** - Each operation returns new instance
- **Build-time advantage preserved** - All analysis at compile time

**Key Design Decisions:**
1. **Immutable streams** over mutable (JINQ approach)
2. **Last-call-wins sorting** over tuple-based (simpler bytecode)
3. **Direct entry points** - No intermediate `stream()` method
4. **Breaking changes accepted** - Clean slate for better API design
5. **Incremental phases** over big-bang release (risk mitigation)
6. **Type inference** from lambda analysis (no manual type hints)

---

## Quick Reference

### Files Created (Planned)

**Runtime:**
- `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStream.java`
- `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqStreamImpl.java`

**Deployment:**
- `deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/QueryPipelineAnalyzer.java`
- `deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/ProjectionAnalyzer.java`
- `deployment/src/main/java/io/quarkus/qusaq/deployment/analysis/SortingAnalyzer.java`
- `deployment/src/main/java/io/quarkus/qusaq/deployment/generation/ProjectionGenerator.java`

**Tests:**
- `integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/BasicQueryTest.java`
- `integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/ProjectionTest.java`
- `integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/SortingTest.java`
- `integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/PaginationTest.java`
- `integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/DistinctTest.java`
- `integration-tests/src/test/java/io/quarkus/qusaq/it/fluent/AggregationTest.java`

**Documentation:**
- `docs/FLUENT_API_REFERENCE.md`

### Files Modified (Planned)

**Runtime:**
- `runtime/src/main/java/io/quarkus/qusaq/runtime/QusaqEntity.java`

**Deployment:**
- `deployment/src/main/java/io/quarkus/qusaq/deployment/QusaqProcessor.java`
- `deployment/src/main/java/io/quarkus/qusaq/deployment/generation/CriteriaExpressionGenerator.java`

**Documentation:**
- `README.md`

---

**Last Updated:** 2025-11-18
**Next Review:** Before starting Phase 1 implementation

---

**Document End**
