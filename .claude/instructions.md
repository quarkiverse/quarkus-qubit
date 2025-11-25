# Quarkus Qusaq Project - Claude Code Instructions

## Core Principles

### Thoroughness and Completeness
- **Be thorough and methodical** in all implementations
- **Complete ALL tasks fully** - never do partial work or skip items
- When updating multiple files or tests, update **every single one** - no exceptions
- If there are 107 tests to update, update all 107, not 106
- **Failure is not an option** - strive for zero errors and complete success

### Verification and Validation
- **Run tests after EACH modification or addition** - not just at the end of all work
- **Incremental testing is mandatory** - test after every change, every file update, every fix
- Don't batch testing until the end - **verify immediately after each change**
- Don't assume code works - **run it and confirm**
- If tests fail, **fix them immediately** - don't leave broken tests
- Use proper assertions and validation in tests
- Verify edge cases and boundary conditions

### Systematic Approach
- **Plan before implementing** - use TodoWrite tool for complex tasks
- Break down large tasks into manageable steps
- Track progress through all steps
- Mark tasks complete only when fully finished (not partial)
- Don't batch completions - mark each task done immediately after finishing

### Code Quality Standards
- Follow existing code patterns and conventions
- Write clear, maintainable code
- Add meaningful comments only when necessary
- Use proper error handling
- Verify code compiles before considering it complete

### Modern Java 17 Code Review (Pre-Test Requirement)

**Before running tests on any generated or modified code, perform a code review for idiomatic Java 17 patterns.**

**Required Review Checklist:**

1. **Records over POJOs**
   - Use `record` for immutable data carriers
   - Replace Lombok `@Value`/`@Data` with records where appropriate
   - Keep records simple (no complex logic in compact constructors)

2. **Pattern Matching**
   - Use `instanceof` pattern matching: `if (obj instanceof String s)`
   - Use switch pattern matching for type checks (Java 17 preview)
   - Eliminate redundant casts after type checks

3. **Text Blocks**
   - Use `"""` for multi-line strings (SQL, JSON, error messages)
   - Proper indentation alignment with closing `"""`
   - Use `\` for line continuation where needed

4. **Sealed Classes**
   - Use `sealed` for restricted hierarchies (like LambdaExpression)
   - Define `permits` clause explicitly
   - Use `non-sealed` or `final` for permitted subclasses

5. **Switch Expressions**
   - Use switch expressions with `->` instead of `:` for simple cases
   - Use `yield` for complex blocks
   - Ensure exhaustiveness (no missing cases)

6. **Stream API Best Practices**
   - Use `toList()` instead of `.collect(Collectors.toList())`
   - Prefer `Stream.ofNullable()` for nullable sources
   - Use `takeWhile()`/`dropWhile()` for conditional processing

7. **Optional Improvements**
   - Use `Optional.isEmpty()` instead of `!isPresent()`
   - Use `Optional.ifPresentOrElse()` for branching
   - Avoid `Optional.get()` - use `orElseThrow()` with message

8. **Null Safety**
   - Use `Objects.requireNonNull()` for parameter validation
   - Use `Objects.requireNonNullElse()` for defaults
   - Prefer empty collections over null

9. **Local Variable Type Inference**
   - Use `var` for local variables when type is obvious
   - Avoid `var` when it reduces readability
   - Never use `var` for method parameters or fields

10. **API Deprecation Awareness**
    - No `new Integer()`, `new Long()` - use `valueOf()`
    - No `Thread.stop()` or `finalize()`
    - Use `Files.readString()` / `Files.writeString()`

**Review Process:**
```
1. Write/generate code
2. Review against checklist above
3. Refactor to modern Java 17 idioms
4. Run tests to verify correctness
5. Fix any issues, repeat until all pass
```

**Example Transformations:**

```java
// Before (Java 8 style)
public class PersonDto {
    private final String name;
    private final int age;
    // constructor, getters, equals, hashCode, toString...
}

// After (Java 17)
public record PersonDto(String name, int age) {}
```

```java
// Before
if (expression instanceof MethodCall) {
    MethodCall mc = (MethodCall) expression;
    return mc.methodName();
}

// After
if (expression instanceof MethodCall mc) {
    return mc.methodName();
}
```

```java
// Before
String query = "SELECT p FROM Person p\n" +
               "WHERE p.age > :minAge\n" +
               "ORDER BY p.name";

// After
String query = """
    SELECT p FROM Person p
    WHERE p.age > :minAge
    ORDER BY p.name
    """;
```

## Project-Specific Guidelines

### Testing Philosophy
- **Tests must have real verification** - not just "check if it runs"
- Use proper assertion frameworks (AssertJ, JUnit)
- Test both positive and negative cases
- Each test should verify specific behavior
- Don't write tests that always pass regardless of implementation

### Bytecode and ASM Work
- Understand bytecode patterns before making assumptions
- Use ASM library correctly for bytecode analysis
- Verify bytecode generation with inspection tools
- Handle edge cases in bytecode compilation (e.g., boolean optimizations)

### Maven and Build
- **Run tests after EACH change** - incremental validation is required
- Run tests in correct module (deployment module for unit tests)
- Use proper Maven commands: `mvn test -Dtest="TestClass"`
- Check test output for both passed and failed tests
- Verify build success before considering task complete
- Never make multiple changes before testing - test immediately after each modification

### Quarkus Extension Development
- Deployment module: build-time processing, bytecode generation
- Runtime module: runtime components, no heavy processing
- Use Gizmo for bytecode generation
- Follow Quarkus extension patterns

### Quarkus Knowledge and Research
- **Supplement internal knowledge with web research** when additional Quarkus information is needed
- **Primary sources for Quarkus research:**
  - **quarkus.io** - Official Quarkus documentation and guides
  - **quarkiverse.io** - Quarkus extensions ecosystem
- **When to research:**
  - Unfamiliar Quarkus extension patterns or APIs
  - Build-time vs runtime processing details
  - Gizmo bytecode generation techniques
  - Quarkus configuration and best practices
  - Extension development patterns and conventions
- Use WebSearch or WebFetch tools to access latest documentation
- Verify information against official Quarkus docs when uncertain

## Communication Style
- Be concise but complete in responses
- Use markdown formatting for readability
- Provide file references with clickable links: `[filename.java](path/to/filename.java#L42)`
- Show test results and verification output
- Explain **what** was done and **why** it works

## Error Handling
- When encountering errors:
  1. Analyze the root cause thoroughly
  2. Consider multiple solution approaches
  3. Implement the most appropriate fix
  4. Verify the fix resolves the issue
  5. Ensure no regressions were introduced

- Never leave errors unresolved
- If an approach doesn't work, pivot to a better solution
- Learn from failures and adjust strategy

## Task Completion Criteria
A task is **only** complete when:
- ✅ All code is implemented
- ✅ All tests pass (0 failures, 0 errors)
- ✅ Build succeeds
- ✅ Code has been verified by running it
- ✅ No TODOs or partial implementations remain
- ✅ Documentation updated if needed

## Anti-Patterns to Avoid
- ❌ Partial implementations ("I'll do 90% and leave the rest")
- ❌ Assuming code works without testing
- ❌ **Batching all testing until the end** - test after EACH change
- ❌ Making multiple changes before running tests
- ❌ Leaving broken tests
- ❌ Incomplete error handling
- ❌ Taking shortcuts on comprehensive tasks
- ❌ Batch marking tasks as complete before finishing all
- ❌ Writing tests that don't actually verify behavior

## When Things Don't Work

### First Attempt Fails
- **Don't give up** - analyze why it failed
- Consider alternative approaches
- Ask clarifying questions if requirements are unclear
- Research the problem thoroughly

### Complex Problems
- Break down into smaller sub-problems
- Solve each piece methodically
- **Test after each sub-problem is solved** - incremental verification
- Integrate solutions step by step
- **Verify each step before proceeding** - run tests, don't assume it works

### Multiple Approaches Needed
- **Be prepared to pivot** - if Mockito doesn't work, try bytecode inspection
- Evaluate trade-offs of different solutions
- Choose the most maintainable and reliable approach
- Document why the chosen approach was selected

## Remember
> "Be thorough and methodical."
> "Failure is not an option."

These aren't just instructions - they're the standard for all work on this project.

## Success Metrics
- All tests pass: `Tests run: N, Failures: 0, Errors: 0, Skipped: 0`
- Build succeeds: `BUILD SUCCESS`
- Code is maintainable and follows project patterns
- No regressions introduced
- Complete feature implementations (not partial)

---

## Refactoring Best Practices (Learned from Sessions 1-8)

### Evidence-Based Decision Making
**Always verify assumptions with concrete evidence before making architectural decisions.**

- Use `grep` searches to verify interface/method usage before removal
- Check actual LOC counts against documentation claims
- Validate that abstractions are actually used, not just defined
- Example from Session 8:
  ```bash
  $ grep "\.canBuild(" CriteriaExpressionGenerator.java
  # 0 results → Interface method never called → Safe to remove
  ```

**Anti-Pattern:**
- ❌ Assuming an interface is used because it's implemented
- ❌ Trusting documentation without verification

**Best Practice:**
- ✅ Grep for actual usage in coordinator classes
- ✅ Verify method calls, not just declarations
- ✅ Let evidence drive architectural decisions

### Failed Abstraction Detection
**Listen to the code - when all implementations return null, the abstraction is wrong.**

**Warning Signs:**
1. All interface implementations return `null` from methods
2. Coordinator calls specialized methods instead of interface methods
3. Interface requires one-size-fits-all signature but reality needs diversity
4. Parameter objects created but only used by unused interface methods

**Session 8 Example:**
- CriteriaExpressionBuilder interface: 4 methods × 5 builders = 20 dead methods returning null
- BuildContext parameter object: Created but never actually used
- Specialized methods (buildStringPattern, buildTemporalComparison) were the real pattern

**Solution:**
- Remove failed abstraction entirely
- Embrace specialized delegation pattern
- Direct method calls > unused polymorphism

**Key Principle:**
> **YAGNI (You Aren't Gonna Need It)** - Don't create abstractions before you know you need them

### Interface Segregation Principle (ISP)
**One-size-fits-all interfaces rarely work in practice.**

**Problem Pattern:**
```java
// One interface for all expression types
interface CriteriaExpressionBuilder {
    boolean canBuild(LambdaExpression expression);
    ResultHandle buildPredicate(...);
    ResultHandle buildExpression(...);
    ResultHandle buildExpressionAsJpaExpression(...);
}
```

**Reality:**
- String operations need: `buildStringPattern(fieldExpr, argument)`
- Temporal operations need: `buildTemporalComparison(methodCall, cb, fieldExpr, argument)`
- Different signatures = forced to use specialized methods anyway

**Better Approach:**
- Skip the interface
- Use specialized methods with appropriate signatures
- Let the type system enforce correctness

### Documentation Accuracy
**Verify all documentation against actual code - numbers don't lie.**

**Lessons from Session 8:**
- User caught 11 inconsistencies in IMPLEMENTATION_TRACKER.md
- LOC counts didn't match reality
- Session numbers were wrong
- File locations were incorrect

**Best Practices:**
1. **Read actual files** to verify LOC counts (use `wc -l` or read file)
2. **Check git history** for session/date attribution
3. **Grep for actual usage** patterns, don't assume
4. **Update docs immediately** after code changes
5. **Cross-verify** metrics against multiple sources

**Documentation Verification Checklist:**
- ✅ LOC counts match actual files
- ✅ Session numbers match context
- ✅ Test results match latest runs
- ✅ File lists match actual directory structure
- ✅ Metrics align with success criteria

### Systematic Refactoring Process
**Follow a disciplined approach for large refactorings.**

**Proven Pattern (from P1, P2, P3):**

1. **Plan with TodoWrite**
   - Break down into discrete steps
   - One task `in_progress` at a time
   - Mark complete immediately after finishing

2. **Implement Incrementally**
   - Create infrastructure first (interfaces, base classes)
   - Extract handlers/builders one at a time
   - Test after each extraction

3. **Strangler Fig Pattern** (for critical refactorings)
   - Keep old implementation initially
   - Add new implementation alongside
   - Add feature flag to switch between them
   - Test both implementations
   - Remove old only after verification

4. **Test-Driven Verification**
   - Run full test suite after each major change
   - Require 100% pass rate (473/473 tests)
   - Zero regressions tolerated
   - BUILD SUCCESS is mandatory

5. **Document Thoroughly**
   - Update IMPLEMENTATION_TRACKER.md after each session
   - Record metrics (LOC before/after, test results)
   - Document lessons learned
   - Note anti-patterns to avoid

### Duplication Elimination
**Recognize duplication early and eliminate it with appropriate patterns.**

**Session 4 Example:**
- IFEQ and IFNE handlers had ~70 lines of duplicate code
- Both implemented identical `handleBooleanFieldPattern()` logic
- Only difference: operator inversion behavior

**Solution - Template Method Pattern:**
```java
abstract class AbstractZeroEqualityBranchHandler {
    // Shared complex logic (AND/OR combination)
    ResultHandle handleBooleanFieldPattern(...) {
        // 70 lines of shared code
        Expression expr = createBooleanEvaluationExpression(field);
        // ...
    }

    // Hook method - subclasses override
    abstract Expression createBooleanEvaluationExpression(FieldAccess field);
}

class IfEqualsZeroHandler extends AbstractZeroEqualityBranchHandler {
    Expression createBooleanEvaluationExpression(FieldAccess field) {
        return field; // IFEQ: field == true
    }
}

class IfNotEqualsZeroHandler extends AbstractZeroEqualityBranchHandler {
    Expression createBooleanEvaluationExpression(FieldAccess field) {
        return new UnaryOp(field, NOT); // IFNE: field == false
    }
}
```

**Benefits:**
- Single source of truth for complex logic
- Bug fixes apply to all subclasses
- Easier to add new zero-comparison instructions
- Improved maintainability score

### Javadoc Brevity Principles
**Documentation should be concise yet complete.**

**Applied in Session 8 + 9:**

1. **Remove Verbose Examples**
   - Code is self-documenting
   - Examples repeat what method signatures show
   - Remove 3-5 code block examples per class

2. **Focus on "Why" Not "What"**
   - Keep architectural notes (Strategy pattern, delegation)
   - Keep optimization explanations (constant folding, index conversion)
   - Remove obvious explanations of what parameters are

3. **Keep Essential Reference Data**
   - Operator mappings tables are useful (EQ → equal())
   - Supported types lists are valuable
   - Method-to-SQL-function mappings stay

4. **Simplify Parameter Descriptions**
   - Before: "the left operand Expression handle"
   - After: "the left operand Expression"
   - Before: "ResultHandle pointing to the arithmetic Expression"
   - After: "the arithmetic Expression"

5. **Consolidate Verbose Sections**
   - Multiple example blocks → Single concise list
   - Multi-paragraph explanations → 1-2 line summaries

**Metrics from Full Review:**
- ~600 Javadoc lines removed across 14 files
- Class Javadocs: 50+ lines → 10-15 lines typical
- Method Javadocs: Multi-line → Single focused line
- Zero functional impact, improved readability

### Todo List Discipline
**Use TodoWrite effectively for complex multi-step tasks.**

**Best Practices:**

1. **Task States**
   - `pending`: Not yet started
   - `in_progress`: Currently working (ONLY ONE at a time)
   - `completed`: Fully finished

2. **Task Descriptions - Two Forms Required**
   - `content`: Imperative form ("Run tests", "Fix errors")
   - `activeForm`: Present continuous ("Running tests", "Fixing errors")

3. **Completion Rules**
   - Mark complete IMMEDIATELY after finishing
   - Don't batch completions
   - ONLY mark complete when FULLY accomplished:
     - Tests passing ✅
     - Implementation complete ✅
     - No errors/blockers ✅
   - Keep in_progress if blocked/errored

4. **Task Breakdown**
   - Create specific, actionable items
   - Break complex tasks into smaller steps
   - Clear, descriptive task names

5. **When to Use TodoWrite**
   - Complex multi-step tasks (3+ steps)
   - Non-trivial tasks requiring planning
   - Multiple operations or file changes
   - User explicitly requests todo list

6. **When NOT to Use TodoWrite**
   - Single straightforward task
   - Trivial operations (<3 steps)
   - Purely conversational/informational requests

### Session Continuity & Documentation
**Maintain excellent documentation for multi-session projects.**

**IMPLEMENTATION_TRACKER.md Structure:**
- Header: Status, dates, overall progress
- Phase sections: P1-P7 with detailed step tables
- Session notes: What was done, lessons learned
- Success metrics: Before/after comparisons
- Test logs: Where to find verification

**Critical Information to Track:**
1. **Files Modified**: Complete list with LOC changes
2. **Test Results**: Exact counts (276/276 deployment, 197/197 integration)
3. **Session Attribution**: Which work happened in which session
4. **Lessons Learned**: Anti-patterns discovered, solutions applied
5. **Root Cause Analysis**: Why problems occurred, how fixed

**Example Entry:**
```markdown
### Session 8: Failed Abstraction Cleanup (2025-11-17)

**Problem:** Interface with all methods returning null
**Evidence:** `grep "\.canBuild(" → 0 results`
**Solution:** Removed interface + parameter object + 20 dead methods
**Impact:** 179 LOC removed, ISP violation fixed
**Tests:** 473/473 passing (100%)
**Lessons:** Listen to the code, YAGNI, evidence-based decisions
```

### Code Quality Patterns Applied

**Strategy Pattern** (when it works):
- BranchCoordinator with 5 specialized handlers
- Each handler: canHandle() + handle()
- Chain of Responsibility iteration

**Template Method Pattern** (for shared logic):
- AbstractZeroEqualityBranchHandler
- Shared complex logic + abstract hooks
- Eliminates 70 lines of duplication

**Specialized Delegation** (instead of failed Strategy):
- Direct method calls with appropriate signatures
- No forced polymorphism
- Type-safe, clear intent

**Parameter Object Pattern** (use sparingly):
- Only create if actually used by multiple methods
- BuildContext was created but never used → removed
- AnalysisContext IS used extensively → kept

### Anti-Patterns Identified & Avoided

From 8 sessions of intensive refactoring:

1. **❌ Creating Abstractions Too Early**
   - Don't design interfaces before knowing actual usage patterns
   - Let the code tell you what abstraction it needs
   - Prefer duplication over wrong abstraction (can refactor later)

2. **❌ One-Size-Fits-All Interfaces**
   - Different operation types need different signatures
   - Forcing uniform interface leads to unused methods
   - Specialized methods > generic interface methods

3. **❌ Parameter Objects for Unused Interfaces**
   - Only create parameter objects if they're actually used
   - Don't create infrastructure for failed abstractions

4. **❌ Trusting Documentation Without Verification**
   - Always verify LOC counts against actual files
   - Cross-check metrics against test results
   - Use grep to validate usage claims

5. **❌ Leaving Dead Code**
   - Methods that return null → remove them
   - Unused interfaces → delete them
   - Dead parameter objects → eliminate them

6. **❌ Batching Todo Completions**
   - Mark tasks complete immediately after finishing
   - One in_progress task at a time
   - Don't mark complete if blocked/errored

7. **❌ Verbose Documentation**
   - Remove examples that repeat method signatures
   - Focus on "why" (architecture) not "what" (obvious)
   - Keep reference data, remove redundant explanations

### Success Patterns That Work

**From 8 Sessions, 473/473 Tests Passing, Zero Regressions:**

✅ **Evidence-based decisions** (grep before removing)
✅ **Listen to the code** (null returns = wrong abstraction)
✅ **Test after each change** (incremental verification)
✅ **Document immediately** (don't defer documentation)
✅ **Verify metrics** (read actual files for LOC counts)
✅ **One task in_progress** (focused execution)
✅ **Mark complete immediately** (real-time progress tracking)
✅ **YAGNI principle** (don't create abstractions speculatively)
✅ **ISP compliance** (specialized methods > forced interface)
✅ **Template Method** (eliminate duplication with base classes)
✅ **Brevity in docs** (concise yet complete)

---

## Join Queries and Multi-Entity Lambda Patterns (Iteration 6)

### Architecture Overview

**Join queries use a separate stream type and bi-entity lambdas:**

```
Person.join(p -> p.phones)           // Returns JoinStream<Person, Phone>
      .where((p, ph) -> ph.type.equals("mobile"))  // BiQuerySpec<T, R, Boolean>
      .toList();                     // Returns List<Person>
```

**Key Components:**
- `JoinStream<T, R>` - Fluent API for join operations (separate from QusaqStream)
- `JoinStreamImpl<T, R>` - Runtime implementation
- `BiQuerySpec<T, R, U>` - Functional interface for two-entity lambdas
- `JoinType` enum - INNER or LEFT join types

### Multi-Entity Lambda Analysis

**Bi-entity lambdas have 2 parameters:**
```java
// Single-entity: (Person p) -> p.age > 25
// Bi-entity: (Person p, Phone ph) -> ph.type.equals("mobile")
```

**Detection Pattern:**
1. `InvokeDynamicScanner` detects `join()` or `leftJoin()` method calls
2. `LambdaBytecodeAnalyzer` parses bi-entity lambdas with 2 ALOAD instructions
3. `AnalysisContext` tracks both parameters with separate indices (0=source, 1=joined)
4. `CriteriaExpressionGenerator.generateBiEntityPredicate()` generates JPA with root + join handles

### Deduplication for Count vs List Queries

**CRITICAL: Include query type in hash computation to prevent deduplication errors**

```java
// LambdaDeduplicator.computeJoinHash() - Include isCountQuery in hash
astString.append("|QUERY_TYPE=").append(isCountQuery ? "COUNT" : "LIST");
```

**Problem Pattern:**
- `Person.join(...).toList()` and `Person.join(...).count()` would get same hash
- Both would use the same executor (wrong!)
- Count query would get List executor → ClassCastException

**Solution:**
- Always include query type (COUNT/LIST) in hash computation
- Different executors for count vs list operations

### JPA/Hibernate Entity Behavior

**Entity deduplication vs SQL row counts:**

```java
// SQL returns 9 rows (one per phone)
// But Hibernate returns 5 unique Person entities
var results = Person.join(p -> p.phones).toList();
assertThat(results).hasSize(5);  // NOT 9!

// COUNT shows actual SQL row count
long count = Person.join(p -> p.phones).count();
assertThat(count).isEqualTo(9);  // Actual join matches
```

**Key Insight:**
- When selecting entities (not scalars), JPA persistence context deduplicates
- Use `count()` to verify actual SQL-level row counts
- Test expectations must account for entity deduplication behavior

### Captured Variable Extraction for Multi-Lambda Streams

**JoinStreamImpl extracts from multiple lambda sources:**

```java
private Object[] extractCapturedVariables() {
    // Extract from bi-predicates (most common)
    for (BiQuerySpec<T, R, Boolean> biPredicate : biPredicates) { ... }
    // Extract from source predicates
    for (QuerySpec<T, Boolean> sourcePredicate : sourcePredicates) { ... }
    // Extract from ON conditions
    for (BiQuerySpec<T, R, Boolean> onCondition : onConditions) { ... }
}
```

**Pattern:**
1. Get expected count from registry: `QueryExecutorRegistry.getCapturedVariableCount(callSiteId)`
2. Iterate through all lambda lists
3. Count captured fields using reflection
4. Extract using `CapturedVariableExtractor.extract()`
5. Combine into single array

### Key Files for Join Query Implementation

**Deployment Module:**
- `InvokeDynamicScanner.java` - Detect join/leftJoin calls, parse relationship lambdas
- `CallSiteProcessor.java` - Process join call sites, compute join hashes
- `LambdaBytecodeAnalyzer.java` - Parse bi-entity lambdas with 2 parameters
- `CriteriaExpressionGenerator.java` - `generateBiEntityPredicate()` for joins
- `QueryExecutorClassGenerator.java` - `generateJoinQueryBody()`, `generateJoinCountQueryBody()`
- `LambdaDeduplicator.java` - `computeJoinHash()` with isCountQuery

**Runtime Module:**
- `JoinStream.java` - Fluent API interface
- `JoinStreamImpl.java` - Runtime implementation with captured variable extraction
- `BiQuerySpec.java` - Two-entity lambda functional interface
- `JoinType.java` - INNER/LEFT enum
- `QueryExecutorRegistry.java` - `executeJoinListQuery()`, `executeJoinCountQuery()`

### Testing Join Queries

**Test organization:** `integration-tests/src/test/java/io/quarkus/qusaq/it/join/JoinQueryTest.java`

**Key test patterns:**
```java
// Basic join
Person.join((Person p) -> p.phones).toList();

// With bi-entity predicate
Person.join((Person p) -> p.phones)
      .where((Person p, Phone ph) -> ph.type.equals("mobile"))
      .toList();

// With captured variables
String targetType = "mobile";
Person.join((Person p) -> p.phones)
      .where((Person p, Phone ph) -> ph.type.equals(targetType))
      .toList();

// LEFT JOIN
Person.leftJoin((Person p) -> p.phones).toList();

// Count query
long count = Person.join((Person p) -> p.phones).count();

// Join with sorting (Iteration 6.5)
Person.join((Person p) -> p.phones)
      .where((Person p, Phone ph) -> ph.type.equals("mobile"))
      .sortedBy((Person p, Phone ph) -> p.age)
      .toList();

// Sort by joined entity field
Person.join((Person p) -> p.phones)
      .sortedDescendingBy((Person p, Phone ph) -> ph.number)
      .toList();
```

### Join Query Sorting Implementation (Iteration 6.5)

**Key insight:** Join query sorting uses bi-entity lambdas just like predicates, but needs separate analysis and ORDER BY generation.

**Implementation Chain:**
1. `InvokeDynamicScanner` - Already captures sort lambdas in `LambdaCallSite.sortLambdas()`
2. `CallSiteProcessor.analyzeBiEntitySortLambdas()` - Uses `bytecodeAnalyzer.analyzeBiEntity()` instead of `analyze()`
3. `LambdaDeduplicator.computeJoinHash()` - Include sort expressions in hash
4. `QueryExecutorClassGenerator.applyBiEntityOrderBy()` - Generate ORDER BY using both root and join handles
5. `CriteriaExpressionGenerator.generateBiEntityExpressionAsJpaExpression()` - Generate JPA expressions with bi-entity support

**Critical Difference from Regular Sorting:**
- Regular queries: `applyOrderBy()` uses `generateExpressionAsJpaExpression()` with root only
- Join queries: `applyBiEntityOrderBy()` uses `generateBiEntityExpressionAsJpaExpression()` with root + join handle

**Test File:** `integration-tests/src/test/java/io/quarkus/qusaq/it/join/JoinSortingTest.java`

### Common Join Query Errors and Fixes

| Error | Root Cause | Fix |
|-------|-----------|-----|
| `NullPointerException in computeHash()` | Join queries not handled in hash computation | Add join query check at start of `computeHash()` |
| `ClassCastException: ArrayList cannot be cast to Long` | Count/list queries deduplicated to same executor | Include `isCountQuery` in `computeJoinHash()` |
| `ArrayIndexOutOfBoundsException` | `extractCapturedVariables()` returns empty array | Implement extraction from `biPredicates` list |
| Test expects 9 results but gets 5 | JPA entity deduplication behavior | Use `count()` for SQL row counts, adjust test expectations |

---

## Qusaq Extension Architecture Reference

### Build-Time Processing Flow

```
1. QusaqProcessor.generateQueryExecutors() - Entry point
2. InvokeDynamicScanner.scanClass() - Find lambda call sites
3. CallSiteProcessor.processCallSite() - Analyze each call site
   a. LambdaBytecodeAnalyzer.analyze() - Parse lambda bytecode
   b. LambdaDeduplicator.computeHash() - Check for duplicates
   c. QueryExecutorClassGenerator.generate*() - Generate bytecode
4. GeneratedClassBuildItem - Register generated class
5. QueryTransformationBuildItem - Link call site to executor
```

### Runtime Execution Flow

```
1. QusaqStreamImpl.toList() / count() / etc. - Terminal operation
2. getCallSiteId() - Stack walk to find caller
3. extractCapturedVariables() - Reflection to get lambda values
4. QueryExecutorRegistry.executeListQuery() - Lookup executor
5. QueryExecutor.execute() - Run generated JPA Criteria query
```

### Lambda Expression Types (LambdaExpression sealed interface)

| Type | Example | JPA Generation |
|------|---------|----------------|
| `FieldAccess` | `p.age` | `root.get("age")` |
| `PathExpression` | `p.address.city` | `root.get("address").get("city")` |
| `MethodCall` | `p.name.startsWith("J")` | `cb.like(root.get("name"), "J%")` |
| `BinaryOp` | `p.age > 25` | `cb.greaterThan(root.get("age"), 25)` |
| `UnaryOp` | `!p.active` | `cb.not(root.get("active"))` |
| `CapturedVariable` | `p.age > minAge` | Parameter from captured array |
| `BiEntityFieldAccess` | `(p, ph) -> ph.type` | `join.get("type")` |

### Query Executor Types

| Executor Type | Registry Method | Return Type |
|---------------|-----------------|-------------|
| List | `registerListExecutor()` | `List<T>` |
| Count | `registerCountExecutor()` | `Long` |
| Aggregation | `registerAggregationExecutor()` | `Object` (min/max/avg/sum) |
| Join List | `registerJoinListExecutor()` | `List<T>` |
| Join Count | `registerJoinCountExecutor()` | `Long` |

---

## Comprehensive Test Suite Creation Standards

### Three-Dimensional Test Matrix

**CRITICAL: Every feature MUST have tests across THREE dimensions to ensure complete coverage.**

#### Dimension 1: Deployment vs Integration Tests

| Test Type | Location | Purpose |
|-----------|----------|---------|
| **Deployment Tests** | `deployment/src/test/java/` | Unit tests for bytecode analysis, AST parsing, code generation |
| **Integration Tests** | `integration-tests/src/test/java/` | End-to-end tests with real database, full Quarkus app |

**Deployment tests verify:**
- Lambda bytecode analysis produces correct AST
- QueryExecutor bytecode generation is correct
- Hash computation for deduplication works
- Edge cases in bytecode patterns (boolean optimizations, etc.)

**Integration tests verify:**
- Generated queries execute correctly against real database
- Results match expected data
- Captured variables work at runtime
- Full stack from API call to SQL execution

#### Dimension 2: Entity API vs Repository API (Integration Tests)

The Qusaq extension exposes functionality through two parallel APIs:
1. **Entity API** (Static methods): `Person.stream().where(...)`
2. **Repository API** (Injected beans): `personRepository.stream().where(...)`

**Mandatory Test Organization:**
```
integration-tests/src/test/java/io/quarkus/qusaq/it/
├── <feature>/                    # Entity API tests
│   ├── BasicQueryTest.java
│   ├── FeatureATest.java
│   └── FeatureBTest.java
└── repository/<feature>/         # Repository API tests (mirror of entity tests)
    ├── RepositoryBasicQueryTest.java
    ├── RepositoryFeatureATest.java
    └── RepositoryFeatureBTest.java
```

#### Dimension 3: Feature Coverage Matrix

Each feature needs tests covering: happy path, edge cases, captured variables, combinations, data types, and operators (see Feature Test Matrix Template below).

### Test Parity Requirements

**Deployment ↔ Integration Parity:**
- Features with complex bytecode handling MUST have deployment unit tests
- Every deployment test scenario SHOULD have corresponding integration test
- Bytecode edge cases (e.g., boolean field optimizations) need both levels

**Entity API ↔ Repository API Parity:**
- For **every** test in `<feature>/SomeTest.java`, there MUST be a corresponding test in `repository/<feature>/RepositorySomeTest.java`
- Test names should match with `Repository` prefix: `testAgeGreaterThan` → `repositoryTestAgeGreaterThan`
- Test logic should be identical except for the API entry point

### Feature Test Matrix Template

When implementing a new feature, create tests covering this complete matrix:

| Category | Test Cases | Priority |
|----------|-----------|----------|
| **Happy Path** | Basic usage, common scenarios | P0 (Required) |
| **Edge Cases** | Null values, empty results, boundary conditions | P0 (Required) |
| **Captured Variables** | External variables in lambdas | P0 (Required) |
| **Combinations** | Feature + other features (where+sort, where+limit, etc.) | P1 (Required) |
| **Data Types** | String, Integer, Long, Double, BigDecimal, temporal types | P1 (Required) |
| **Operators** | All supported operators for the feature | P1 (Required) |
| **Negation** | NOT versions of predicates | P2 (Recommended) |
| **Multiple Predicates** | Chained where() clauses | P2 (Recommended) |

### Required Test Categories by Feature Type

**For Predicate Features (where clauses):**
```java
// 1. Basic predicate
@Test void basicEquals() { ... }

// 2. With captured variable
@Test void equalsWithCapturedVariable() { ... }

// 3. Combined with other predicates
@Test void equalsAndGreaterThan() { ... }

// 4. Combined with sorting
@Test void equalsWithSorting() { ... }

// 5. Combined with pagination
@Test void equalsWithPagination() { ... }

// 6. Null handling
@Test void equalsWithNullValue() { ... }

// 7. Empty result
@Test void equalsNoMatches() { ... }
```

**For Aggregation Features (count, min, max, avg, sum):**
```java
// 1. Basic aggregation
@Test void countAll() { ... }

// 2. With where clause
@Test void countWithPredicate() { ... }

// 3. Empty result handling
@Test void countEmptyResult() { ... }

// 4. Null value handling
@Test void avgWithNullValues() { ... }
```

**For Join Features:**
```java
// 1. Basic join
@Test void basicInnerJoin() { ... }

// 2. Join with bi-entity predicate
@Test void joinWithBiEntityPredicate() { ... }

// 3. Join with captured variable
@Test void joinWithCapturedVariable() { ... }

// 4. LEFT JOIN vs INNER JOIN
@Test void leftJoinIncludesNullRelationships() { ... }

// 5. Count on join (verify SQL row count vs entity count)
@Test void joinCount() { ... }

// 6. Join + distinct
@Test void joinDistinct() { ... }
```

### Test Implementation Checklist

Before marking a feature as complete, verify:

**Deployment Tests (Unit Level):**
- [ ] **Bytecode analysis tests exist** in `deployment/src/test/java/.../bytecode/`
- [ ] **AST parsing verified** - Lambda expressions produce correct AST
- [ ] **Edge cases covered** - Boolean optimizations, null handling, etc.

**Integration Tests (End-to-End):**
- [ ] **Entity API tests exist** in `integration-tests/src/test/java/io/quarkus/qusaq/it/<feature>/`
- [ ] **Repository API tests exist** in `integration-tests/src/test/java/io/quarkus/qusaq/it/repository/<feature>/`
- [ ] **Entity ↔ Repository parity verified** - Every entity test has corresponding repository test

**Coverage Requirements:**
- [ ] **All priority P0 categories covered** (happy path, edge cases, captured variables)
- [ ] **All priority P1 categories covered** (for production-ready features)
- [ ] **Tests use real assertions** - Not just "runs without exception"
- [ ] **Edge cases tested** - Null values, empty results, boundary conditions
- [ ] **Captured variables tested** - External variables in lambdas work correctly

**Verification:**
- [ ] **All tests pass** - `mvn test` shows 0 failures, 0 errors
- [ ] **Both modules pass** - deployment and integration-tests modules

### Test Tracking Document

Maintain `REPOSITORY_PATTERN_TEST_COVERAGE_TRACKING.md` to track:
- Which entity tests have repository equivalents
- Coverage gaps that need to be addressed
- Test counts for each feature area

**Format:**
```markdown
## Feature: String Operations

| Entity Test | Repository Test | Status |
|-------------|-----------------|--------|
| StringOperationsTest.startsWithBasic | RepositoryStringOperationsTest.repositoryStartsWithBasic | ✅ |
| StringOperationsTest.containsWithCaptured | RepositoryStringOperationsTest.repositoryContainsWithCaptured | ✅ |
| StringOperationsTest.endsWithNull | (missing) | ❌ |

Coverage: 2/3 (67%)
```

### Anti-Patterns in Testing

**❌ Avoid These:**
```java
// Bad: No real assertion
@Test void testFeature() {
    List<Person> results = Person.stream().where(p -> p.age > 25).toList();
    assertThat(results).isNotNull(); // Too weak!
}

// Bad: Testing only happy path
@Test void testEquals() {
    // Only tests when data exists, no edge cases
}

// Bad: Entity tests without repository equivalents
// Results in asymmetric coverage
```

**✅ Do This Instead:**
```java
// Good: Specific assertions
@Test void testAgeGreaterThan() {
    List<Person> results = Person.stream().where(p -> p.age > 25).toList();
    assertThat(results).hasSize(3);
    assertThat(results).extracting(Person::getName)
                       .containsExactlyInAnyOrder("Alice", "Bob", "Charlie");
    assertThat(results).allMatch(p -> p.getAge() > 25);
}

// Good: Edge case coverage
@Test void testAgeGreaterThanNoMatches() {
    List<Person> results = Person.stream().where(p -> p.age > 1000).toList();
    assertThat(results).isEmpty();
}

// Good: Paired with repository test
@Test void repositoryTestAgeGreaterThan() {
    List<Person> results = personRepository.stream().where(p -> p.age > 25).toList();
    assertThat(results).hasSize(3);
    // Same assertions as entity test
}
```

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest="JoinQueryTest"

# Run tests in a package
mvn test -Dtest="io.quarkus.qusaq.it.repository.**"

# Run with verbose output
mvn test -Dtest="JoinQueryTest" -X
```

### Success Criteria

A feature is fully tested when:
- ✅ `mvn test` passes with 0 failures, 0 errors in **both** deployment and integration-tests modules
- ✅ **Deployment tests exist** for bytecode analysis and code generation
- ✅ **Entity API tests exist** in integration-tests
- ✅ **Repository API tests exist** mirroring entity tests
- ✅ Test matrix coverage ≥ 90% for P0+P1 categories
- ✅ `REPOSITORY_PATTERN_TEST_COVERAGE_TRACKING.md` shows parity
- ✅ Edge cases and captured variables are covered

### Test Count Reference

Current test distribution (as of Iteration 6):
- **Deployment module**: ~280 tests (bytecode analysis, AST parsing, code generation)
- **Integration-tests module**: ~670 tests (Entity API + Repository API parity)
- **Total**: ~950+ tests

When adding new features, expect to add tests to **all three areas**:
1. Deployment bytecode tests
2. Integration Entity API tests
3. Integration Repository API tests (mirroring #2)
