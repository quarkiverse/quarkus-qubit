# Quarkus Qubit Project - Claude Code Instructions

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
- Use proper error handling
- Verify code compiles before considering it complete

### Javadoc and Comment Standards (Mandatory for All Code Generation)
**Apply these rules during code generation — not as a post-hoc cleanup.**

- **Class Javadoc**: 3-6 lines max. One sentence summary + optional `<p>` with key details.
- **Method Javadoc**: Single-line `/** ... */` format. No multi-line for simple methods.
- **No redundant @param/@return tags**: Omit when the parameter/return description just restates the name or type.
- **Focus on "why" not "what"**: Keep architectural notes (patterns, delegation, optimization rationale). Remove obvious explanations.
- **Keep reference data**: Operator mappings, supported types lists, method-to-SQL mappings are valuable.
- **No verbose examples**: Code is self-documenting. Don't add code blocks that repeat method signatures.
- **Consolidate**: Multiple example blocks → single concise list. Multi-paragraph → 1-2 line summaries.
- **Model**: See `GizmoHelper.java` for the target style — single-line method docs, 4-5 line class doc.

### Quality Improvement Reporting
**After each refactor or code quality improvement, present a summary to the user:**

**Current Change Summary:**
- Files modified and LOC changes (+/- lines)
- Specific improvements made (pattern applied, duplication removed, etc.)
- Metrics if applicable (cyclomatic complexity, coupling, etc.)

**Aggregated Session Summary:**
- Total files modified this session
- Total LOC added/removed
- Cumulative improvements (all patterns applied, total duplication removed)
- Running test status (passes/failures)

**Format Example:**
```
📊 Quality Improvement Summary

Current Change:
- Modified: ExpressionHandler.java (-45 LOC)
- Improvement: Extracted Template Method pattern, eliminated duplication

Session Aggregate:
- Files: 5 modified
- LOC: +120 / -340 (net -220)
- Patterns: Strategy (1), Template Method (2)
- Tests: 473/473 passing
```

**When to Report:**
- After completing each refactoring task
- After removing dead code or failed abstractions
- After applying design patterns
- After Javadoc/documentation cleanup with LOC reduction

**Remaining Issues Summary:**
After updating the tracking document, include a brief summary of remaining non-deferred issues:
```
📋 Remaining Open Issues (20)
- Architecture: 1 (ARCH-007)
- Code Smells: 0
- Documentation: 7 (DOC-002 through DOC-008)
- Maintainability: 6 (MAINT-003 through MAINT-008)
- Testing: 6 (TEST-001 through TEST-006)
```
This helps maintain visibility into the overall quality backlog.

### Debugging Practices
- **Mark all debug logging with a `// DEBUG` comment** when inserting logging statements for debugging purposes:
  ```java
  System.out.println("DEBUG: value = " + value); // DEBUG
  logger.debug("Processing item: {}", item); // DEBUG
  ```
- **Remove ALL debug-marked logging before marking functionality as complete** - search for `// DEBUG` comments and remove those lines
- Debug logging is temporary and must never be committed to the final implementation

### Modern Java 25 Code Review (Pre-Test Requirement)

**Before running tests on any generated or modified code, perform a code review for idiomatic Java 25 patterns.**

Java 25 is an LTS release (September 2025). This project targets `maven.compiler.release=25`.
Features are grouped by maturity: **Stable** features require no flags; **Preview** features need `--enable-preview` and should only be used when explicitly agreed upon.

**Required Review Checklist:**

#### Stable Features (Java 16-25 — no flags required)

1. **Records over POJOs** *(Java 16)*
   - Use `record` for immutable data carriers (~50 records across codebase)
   - Replace Lombok `@Value`/`@Data` with records where appropriate
   - Keep records simple — no complex logic in compact constructors
   - Records are ideal as inner types in sealed interfaces (algebraic data types)

2. **Sealed Classes and Interfaces** *(Java 17)*
   - Use `sealed` for restricted hierarchies (~17 sealed types in codebase)
   - Core pattern: sealed interface + record implementations = algebraic data type
   - Examples: `LambdaAnalysisResult`, `AnalysisOutcome`, `BuilderResult`, `BranchState`, `GenerationResult`, `MethodCallHandler`, `JoinSelectionStrategy`
   - Define `permits` clause explicitly
   - Prefer `record` implementations (inherently `final`) over `non-sealed` classes

3. **Pattern Matching for instanceof** *(Java 16)*
   - Use `instanceof` pattern matching: `if (obj instanceof String s)`
   - Eliminate redundant casts after type checks
   - Combine with record destructuring: `if (this instanceof AndMode(var target, _))`

4. **Pattern Matching for switch** *(Java 21)*
   - Use switch expressions with type patterns for polymorphic dispatch
   - Use `case Type t when condition ->` for guarded patterns
   - Use `case null ->` for explicit null handling
   - Compiler enforces exhaustiveness for sealed types — no default needed

5. **Unnamed Variables and Patterns (`_`)** *(Java 22)*
   - Use `_` for unused variables in catch blocks: `catch (Exception _)`
   - Use `_` in record pattern destructuring: `case Success(var value, _) ->`
   - Use `_` for unused lambda parameters: `(_, index) -> process(index)`
   - Use `_` for unnamed pattern matches: `case NotApplicable _ ->`
   - **This is stable in Java 22+** — no preview flag needed (~8 usages in codebase)

6. **Record Pattern Destructuring** *(Java 21 — enhanced in 22)*
   - Destructure records directly in switch and instanceof:
     ```java
     case BiEntityParameter(_, _, _, var position) -> handlePosition(position);
     case BiEntityPathExpression(var segments, _, var entityPosition) -> ...
     ```
   - Combine with unnamed patterns for ignored components
   - Supports nested destructuring for records containing records

7. **Switch Expressions** *(Java 14)*
   - Use switch expressions with `->` instead of `:` for all cases (~116 usages)
   - Use `yield` for complex blocks
   - Ensure exhaustiveness (compiler enforces for sealed types)
   - Add default cases to non-sealed enum switches for future-proofing

8. **Text Blocks** *(Java 15)*
   - Use `"""` for multi-line strings (SQL, JSON, error messages)
   - Proper indentation alignment with closing `"""`
   - Use `\` for line continuation where needed

9. **Flexible Constructor Bodies** *(Java 25)*
   - Code **may precede** `super(...)` or `this(...)` calls
   - Use for parameter validation before delegation:
     ```java
     public TypedQuery(String name, Class<?> type) {
         Objects.requireNonNull(name, "name must not be null");
         super(name);  // validated before delegation
         this.type = type;
     }
     ```
   - Constraint: must not read uninitialized instance fields before `super()`/`this()`

10. **Scoped Values** *(Java 25)*
    - Thread-safe alternative to `ThreadLocal` with immutable values and limited scope
    - Use `ScopedValue.where(KEY, value).run(() -> ...)` for scoped execution
    - Prefer over `ThreadLocal` in new code, especially with virtual threads

11. **Module Import Declarations** *(Java 25)*
    - `import module java.base;` imports all exported packages of a module
    - Useful for reducing import boilerplate in utility classes
    - Not yet adopted in this codebase — consider for new files when beneficial

12. **Stream Gatherers** *(Java 24)*
    - Custom intermediate stream operations via `stream.gather(Gatherer)`
    - Built-in gatherers: `Gatherers.windowFixed()`, `Gatherers.windowSliding()`, `Gatherers.fold()`, `Gatherers.scan()`, `Gatherers.mapConcurrent()`
    - Prefer over complex multi-step stream pipelines when a custom gatherer is cleaner

13. **Markdown JavaDoc Comments** *(Java 23)*
    - JavaDoc comments can use Markdown instead of HTML
    - Use `` `code` `` instead of `{@code code}`
    - Use `[text](url)` instead of `{@link}` for external links
    - Use `///` line comments for markdown-style JavaDoc
    - **Not yet adopted** in this codebase — HTML JavaDoc remains the convention

#### Best Practices (Java 9-21 — still relevant)

14. **Stream API Best Practices**
    - Use `toList()` instead of `.collect(Collectors.toList())`
    - Prefer `Stream.ofNullable()` for nullable sources
    - Use `takeWhile()`/`dropWhile()` for conditional processing

15. **Optional Improvements**
    - Use `Optional.isEmpty()` instead of `!isPresent()`
    - Use `Optional.ifPresentOrElse()` for branching
    - Avoid `Optional.get()` — use `orElseThrow()` with message

16. **Null Safety**
    - Use `Objects.requireNonNull()` for parameter validation
    - Use `Objects.requireNonNullElse()` for defaults
    - Prefer empty collections over null

17. **Local Variable Type Inference** *(Java 10)*
    - **Explicit types are the default** — always declare the type unless `var` is clearly better
    - Use `var` ONLY when the type is long/complex AND adds no value because the programmer can infer it from the right side (e.g., `new CallSiteProcessor.CallSiteProcessingContext(...)`)
    - Never use `var` for simple types: `int`, `long`, `boolean`, `String`, `byte[]`, `Path`, etc.
    - Never use `var` for method parameters or fields
    - `var` IS required in record pattern destructuring: `case Success(var value, _) ->`

18. **API Deprecation Awareness**
    - No `new Integer()`, `new Long()` — use `valueOf()`
    - No `Thread.stop()` or `finalize()` — finalization permanently removed
    - Use `Files.readString()` / `Files.writeString()`
    - Security Manager permanently disabled (Java 24) — don't use `System.setSecurityManager()`

#### Preview Features (Java 25 — require `--enable-preview`)

19. **Primitive Types in Patterns** *(Third Preview — JEP 507)*
    - Extends pattern matching to primitive types: `case int i when i > 0 ->`
    - Checks value representability without precision loss
    - **Do not use** unless explicitly discussed — still in preview

20. **Stable Values** *(Preview — JEP 502)*
    - Thread-safe lazy initialization: `StableValue.supplier(() -> expensiveComputation())`
    - Values become JVM constants after first access
    - **Do not use** unless explicitly discussed — still in preview

**Review Process:**
```
1. Write/generate code
2. Review against checklist above (stable features only)
3. Refactor to modern Java 25 idioms
4. Run tests to verify correctness
5. Fix any issues, repeat until all pass
```

**Example Transformations (Java 25 Idioms):**

```java
// POJO → Record
// Before (Java 8 style)
public class PersonDto {
    private final String name;
    private final int age;
    // constructor, getters, equals, hashCode, toString...
}
// After
public record PersonDto(String name, int age) {}
```

```java
// Sealed algebraic data type with exhaustive pattern matching
// This is the core pattern used throughout the codebase
public sealed interface GenerationResult {
    record Success(Expr value) implements GenerationResult {}
    record Unsupported(String methodName, String reason) implements GenerationResult {}

    default Expr getOrThrow() {
        return switch (this) {
            case Success(var value) -> value;  // record destructuring
            case Unsupported(_, var reason) ->  // unnamed pattern for ignored field
                throw new IllegalStateException(reason);
        };
    }
}
```

```java
// Unnamed variables in catch blocks (Java 22)
// Before
try { field = clazz.getDeclaredField(name); }
catch (NoSuchFieldException e) { /* e unused */ }

// After
try { field = clazz.getDeclaredField(name); }
catch (NoSuchFieldException _) { /* clearly intentionally unused */ }
```

```java
// Record destructuring with unnamed patterns in switch (Java 22)
// Before
case BiEntityParameter param -> handlePosition(param.position());

// After
case BiEntityParameter(_, _, _, var position) -> handlePosition(position);
```

```java
// Flexible constructor bodies (Java 25)
// Before — had to use static helper or factory method for validation
public TypedQuery(String name, Class<?> type) {
    super(name); // can't validate before this in Java 24
    this.type = Objects.requireNonNull(type);
}

// After — validate before super()
public TypedQuery(String name, Class<?> type) {
    Objects.requireNonNull(name);
    Objects.requireNonNull(type);
    super(name);  // now allowed after statements
    this.type = type;
}
```

```java
// Sealed state machine with record patterns (codebase pattern)
public sealed interface BranchState {
    record Initial() implements BranchState {}
    record AndMode(int lastJumpTarget, boolean inverted) implements BranchState {}
    record OrMode(int lastJumpTarget, boolean inverted) implements BranchState {}

    default int getLastJumpTarget() {
        return switch (this) {
            case Initial _ -> -1;
            case AndMode(var target, _) -> target;
            case OrMode(var target, _) -> target;
        };
    }
}
```

## Project-Specific Guidelines

### Testing Standards

See [docs/testing/](docs/testing/README.md) for complete testing guidelines.

**Quick Reference:**
- Coverage requirements: 85% line, 80% branch, 90% mutation
- Run tests: `mvn test` | `mvn verify -Pcoverage` | `mvn test -Pmutation -pl deployment`

**Key Documents:**
- [Exclusion Patterns](docs/testing/exclusion-patterns.md) - What not to test
- [Coverage Baseline](docs/testing/coverage-baseline.md) - Current metrics and improvement plan
- [Test Fixtures](docs/testing/test-fixtures.md) - Fluent builders for test setup
- [Mutation Testing](docs/testing/mutation-testing.md) - Pitest mutation testing guide

### Testing Philosophy
- **Tests must have real verification** - not just "check if it runs"
- Use proper assertion frameworks (AssertJ, JUnit)
- Test both positive and negative cases
- Each test should verify specific behavior
- Don't write tests that always pass regardless of implementation
- **Never test constants or Java language guarantees:**
  - Don't test that `Constants.VALUE` equals a specific literal (tautological - if constant changes, test must change)
  - Don't test `enum.valueOf()` or `enum.name()` - these are Java guarantees
  - Don't test `enum.ordinal()` values - these are implementation details
  - Constants classes are excluded from JaCoCo coverage metrics
  - Focus tests on **behavior**: methods that compute, transform, or have side effects
- **Never test input=output patterns (trivial getters/constructors):**
  - Don't test that `new Foo(x).getX()` equals `x` - this tests Java field storage, not behavior
  - Don't test record component accessors (e.g., `record.field()` returning constructor arg)
  - Don't test `equals()`/`hashCode()` for records - Java guarantees correct implementation
  - Don't test that exception constructors store message/cause - this is Java Exception behavior
  - Only test getters if they compute/transform values (not simple field returns)
- **Never test enum singleton existence patterns:**
  - Don't test that `EnumType.INSTANCE` is not null - Java guarantees enum constants exist
  - Don't test that `INSTANCE.isSameAs(INSTANCE)` - identity is a Java guarantee
  - Don't test that enum implements its interface - this is a compile-time guarantee
  - Don't test `instanceof Enum` checks - enums are always Enum instances
  - Don't test `values().length` or array contents - these are Java enum guarantees
  - Only test enums if they have **behavior methods** (calculations, mappings, transformations)

### Bytecode and ASM Work
- Understand bytecode patterns before making assumptions
- Use ASM library correctly for bytecode analysis
- Verify bytecode generation with inspection tools
- Handle edge cases in bytecode compilation (e.g., boolean optimizations)

### Maven and Build
- **IMPORTANT: Always prepend `clean` to every Maven command** - use `mvn clean test`, `mvn clean verify`, `mvn clean compile`, etc. Never run `mvn test` or `mvn verify` without `clean`. Stale compiled classes cause false positives/negatives and waste debugging time.
- **Run tests after EACH change** - incremental validation is required
- Run tests in correct module (deployment module for unit tests)
- Use proper Maven commands: `mvn clean test -Dtest="TestClass"`
- Check test output for both passed and failed tests
- Verify build success before considering task complete
- Never make multiple changes before testing - test immediately after each modification

### Testing the Whole Project
When asked to "test the whole project" or run comprehensive tests, execute the following steps in order:
1. **Run unit tests**: `mvn test` - all modules
2. **Run mutation testing**: `mvn test-compile org.pitest:pitest-maven:mutationCoverage` - verify test quality
3. **Check mutation coverage** - review the pit-reports for any surviving mutants
4. **Address surviving mutants** - if critical mutants survive, add tests to kill them

**Mutation Testing Thresholds:**
- Line coverage: ≥80%
- Mutation coverage: ≥70%
- If thresholds are not met, investigate and add missing test cases

### Quarkus Extension Development
- Deployment module: build-time processing, bytecode generation
- Runtime module: runtime components, no heavy processing
- Use **Gizmo2** for bytecode generation (migrated from Gizmo 1)
- Use ASM for bytecode **analysis** (reading lambda bytecode)
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
- ✅ **Mutation testing passed** - Run `mvn test-compile org.pitest:pitest-maven:mutationCoverage` and verify coverage thresholds are met (when testing the whole project)
- ✅ **Lessons learned documented** - Add any new patterns, pitfalls, or insights discovered during implementation to this instructions.md file before committing

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
   - Require 100% pass rate
   - Zero regressions tolerated
   - BUILD SUCCESS is mandatory

5. **Document Thoroughly**
   - Update IMPLEMENTATION_TRACKER.md for new implementations, update code-quality-tracking.md for code quality improvements after each change
   - Record metrics (LOC before/after, test results)
   - Document lessons learned
   - Note anti-patterns to avoid

6. **Summary Dashboard Formatting (code-quality-tracking.md)**
   - When updating the Summary Dashboard table, only show **original value → current value**
   - Remove intermediate strikethrough values to keep the table clean
   - Example: `~~42~~ ~~38~~ **37**` should be simplified to `~~42~~ **37**`
   - If the value hasn't changed from original, show just the number (no strikethrough)
   - This keeps the dashboard readable while still showing progress from initial state

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
See **"Javadoc and Comment Standards"** in the Code Quality Standards section above.
Rules are mandatory during code generation. Session 8+9 removed ~600 lines across 14 files.

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
- `JoinStream<T, R>` - Fluent API for join operations (separate from QubitStream)
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

**Test organization:** `integration-tests/src/test/java/io/quarkus/qubit/it/join/JoinQueryTest.java`

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

**Test File:** `integration-tests/src/test/java/io/quarkus/qubit/it/join/JoinSortingTest.java`

### Common Join Query Errors and Fixes

| Error | Root Cause | Fix |
|-------|-----------|-----|
| `NullPointerException in computeHash()` | Join queries not handled in hash computation | Add join query check at start of `computeHash()` |
| `ClassCastException: ArrayList cannot be cast to Long` | Count/list queries deduplicated to same executor | Include `isCountQuery` in `computeJoinHash()` |
| `ArrayIndexOutOfBoundsException` | `extractCapturedVariables()` returns empty array | Implement extraction from `biPredicates` list |
| Test expects 9 results but gets 5 | JPA entity deduplication behavior | Use `count()` for SQL row counts, adjust test expectations |

---

## Qubit Extension Architecture Reference

### Build-Time Processing Flow

```
1. QubitProcessor.generateQueryExecutors() - Entry point
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
1. QubitStreamImpl.toList() / count() / etc. - Terminal operation
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

The Qubit extension exposes functionality through two parallel APIs:
1. **Entity API** (Static methods): `Person.stream().where(...)`
2. **Repository API** (Injected beans): `personRepository.stream().where(...)`

**Mandatory Test Organization:**
```
integration-tests/src/test/java/io/quarkus/qubit/it/
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
- [ ] **Entity API tests exist** in `integration-tests/src/test/java/io/quarkus/qubit/it/<feature>/`
- [ ] **Repository API tests exist** in `integration-tests/src/test/java/io/quarkus/qubit/it/repository/<feature>/`
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
mvn test -Dtest="io.quarkiverse.qubit.it.repository.**"

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

Current test distribution (as of 2025-12):
- **Deployment module**: ~1769 tests (79 test files - bytecode analysis, AST parsing, code generation)
- **Integration-tests module**: ~835 tests (125 test files - Entity API + Repository API parity)
- **Total**: ~2600+ tests

When adding new features, expect to add tests to **all three areas**:
1. Deployment bytecode tests
2. Integration Entity API tests
3. Integration Repository API tests (mirroring #2)

---

## GROUP BY Queries and Aggregation Patterns (Iteration 7)

### Architecture Overview

**GROUP BY queries use a separate stream type with group context lambdas:**

```java
Person.groupBy((Person p) -> p.department.name)     // Returns GroupStream<Person, String>
      .having((Group<Person, String> g) -> g.count() >= 2)  // GroupQuerySpec<T, K, Boolean>
      .select((Group<Person, String> g) -> new Object[]{g.key(), g.count()})
      .toList();                                    // Returns List<Object[]>
```

**Key Components:**
- `GroupStream<T, K>` - Fluent API for group operations (separate from QubitStream)
- `GroupStreamImpl<T, K>` - Runtime implementation
- `GroupQuerySpec<T, K, U>` - Functional interface for group context lambdas (takes `Group<T, K>`)
- `Group<T, K>` - Context interface providing `key()`, `count()`, `avg()`, `sum*()`, `min()`, `max()`

### Group Context Lambda Analysis

**GroupQuerySpec lambdas have a Group<T, K> parameter:**
```java
// Regular lambda: (Person p) -> p.age > 25
// Group lambda: (Group<Person, String> g) -> g.count() > 5
```

**Detection and Handling:**
1. `InvokeDynamicScanner` detects `groupBy()`, `having()`, `select()` on GroupStream
2. `AnalysisContext.setGroupContextMode(true)` enables group context mode
3. `GroupParameter` AST type represents the Group<T, K> parameter
4. `GroupKeyReference` AST type represents `g.key()` calls
5. `GroupAggregation` AST type represents `g.count()`, `g.avg()`, etc.

### Captured Variable Extraction from Multiple Lambda Sources

**CRITICAL: Complex stream types have MULTIPLE lambda sources - extract from ALL of them.**

```java
// GroupStreamImpl.extractCapturedVariables()
private Object[] extractCapturedVariables(String callSiteId) {
    int capturedCount = QueryExecutorRegistry.getCapturedVariableCount(callSiteId);
    if (capturedCount == 0) return new Object[0];

    List<Object> allCapturedValues = new ArrayList<>();
    int remainingCount = capturedCount;

    // 1. Extract from predicates (WHERE clauses BEFORE grouping)
    for (QuerySpec<T, Boolean> predicate : predicates) { ... }

    // 2. Extract from keyExtractor (GROUP BY key lambda)
    if (remainingCount > 0 && keyExtractor != null) { ... }

    // 3. Extract from havingConditions (HAVING clause lambdas)
    for (GroupQuerySpec<T, K, Boolean> havingCondition : havingConditions) { ... }

    // 4. Extract from selector (SELECT projection lambda)
    if (remainingCount > 0 && selector != null) { ... }

    return allCapturedValues.toArray(new Object[0]);
}
```

**Common Bug Pattern:**
- ❌ Only extracting from `predicates` list
- Result: `ArrayIndexOutOfBoundsException` when HAVING has captured variables
- Fix: Extract from ALL four lambda sources in order

### Nested Lambda Analysis for Group Aggregations

**Group aggregation methods contain nested lambdas:**
```java
// g.avg((Person p) -> p.salary) - nested lambda inside group context
g.avg((Person p) -> p.salary)
g.min((Person p) -> p.hireDate)
g.countDistinct((Person p) -> p.department)
```

**Implementation Chain:**
1. `MethodInvocationHandler` detects `Group.avg()` call in group context
2. Prior instruction is `INVOKEDYNAMIC` for LambdaMetafactory (the nested lambda)
3. `AnalysisContext.analyzeNestedLambda()` recursively analyzes the nested method
4. `GroupAggregation` AST stores aggregation type + nested field expression

**Context Requirements:**
```java
// AnalysisContext must support nested lambda analysis
context.setClassMethods(classMethods);           // List of all class methods
context.setNestedLambdaAnalyzer(this::analyze);  // Recursive analyzer function

// When detecting nested lambda
MethodNode nestedMethod = context.findMethod(lambdaName, lambdaDesc);
LambdaExpression fieldExpr = context.analyzeNestedLambda(nestedMethod, entityParamIndex);
```

### Object[] Array Projection Handling

**GROUP BY select() can return Object[] for multi-value projections:**
```java
.select((Group<Person, String> g) -> new Object[]{g.key(), g.count(), g.avg(p -> p.salary)})
```

**Bytecode Pattern:**
```
ICONST_3                        // Array size
ANEWARRAY java/lang/Object      // Create Object[3]
DUP
ICONST_0                        // Index 0
... g.key() ...
AASTORE                         // Store at index 0
DUP
ICONST_1                        // Index 1
... g.count() ...
AASTORE                         // Store at index 1
// ... repeat for each element
ARETURN
```

**Implementation:**
1. `AnalysisContext.startArrayCreation()` called on ANEWARRAY
2. `AnalysisContext.addArrayElement()` called on each AASTORE
3. `AnalysisContext.completeArrayCreation()` returns `ArrayCreation` AST on ARETURN
4. `ArrayCreation(elementType, elements, arrayClass)` stores element expressions

**JPA Generation:**
```java
// Generate cb.tuple() for Object[] projection
ResultHandle selection = method.invokeInterfaceMethod(
    CB_TUPLE,  // CriteriaBuilder.tuple(Selection...)
    cb,
    selectionArray  // Array of Selection elements
);
// At runtime: tuple.toArray() converts Tuple to Object[]
```

### GROUP BY Count Query Strategies

**Two distinct patterns based on HAVING presence:**

**Without HAVING (optimized):**
```java
// Use COUNT(DISTINCT groupKey) without GROUP BY clause
Person.groupBy(p -> p.department.name).count();

// Generated SQL:
SELECT COUNT(DISTINCT department_name) FROM Person
```

**With HAVING (must execute full query):**
```java
// Cannot use COUNT(DISTINCT) - HAVING requires actual groups
Person.groupBy(p -> p.department.name)
      .having(g -> g.count() >= 2)
      .count();

// Generated SQL:
SELECT department_name FROM Person
GROUP BY department_name
HAVING COUNT(*) >= 2

// Then: return results.size()
```

**Detection Logic:**
```java
if (havingConditions.isEmpty()) {
    // Optimized path: COUNT(DISTINCT key)
    return generateGroupCountOptimized();
} else {
    // Full query path: execute and count results
    return executeFullGroupQueryAndCount();
}
```

### Key Files for GROUP BY Implementation

**Deployment Module:**
- `InvokeDynamicScanner.java` - Detect groupBy/having/select on GroupStream
- `CallSiteProcessor.java` - Process group call sites, compute group hashes
- `LambdaBytecodeAnalyzer.java` - Parse GroupQuerySpec with group context mode
- `MethodInvocationHandler.java` - Handle Group.key/count/avg/etc calls
- `CriteriaExpressionGenerator.java` - Generate cb.groupBy(), cb.having(), cb.count()
- `QueryExecutorClassGenerator.java` - generateGroupQueryBody(), generateGroupCountQueryBody()
- `AnalysisContext.java` - Group context mode, array tracking, nested lambda support

**Runtime Module:**
- `GroupStream.java` - Fluent API interface
- `GroupStreamImpl.java` - Runtime implementation with multi-source captured variable extraction
- `GroupQuerySpec.java` - Group context lambda functional interface
- `Group.java` - Context interface for aggregation methods
- `QueryExecutorRegistry.java` - executeGroupQuery(), executeGroupKeyQuery(), executeGroupCountQuery()

**AST Types Added:**
- `GroupParameter` - Represents Group<T, K> parameter in group context lambdas
- `GroupKeyReference` - Represents g.key() calls
- `GroupAggregation` - Represents g.count(), g.avg(), etc. with nested field extractor
- `ArrayCreation` - Represents Object[] array projections

### Testing GROUP BY Queries

**Test File:** `integration-tests/src/test/java/io/quarkus/qubit/it/fluent/GroupQueryTest.java`

**Key test patterns:**
```java
// Basic groupBy returning keys
List<String> depts = Person.groupBy((Person p) -> p.department.name).toList();

// Group count (number of groups)
long groupCount = Person.groupBy((Person p) -> p.department.name).count();

// HAVING with literal
Person.groupBy((Person p) -> p.department.name)
      .having((Group<Person, String> g) -> g.count() >= 2)
      .toList();

// HAVING with captured variable
long minCount = 2;
Person.groupBy((Person p) -> p.department.name)
      .having((Group<Person, String> g) -> g.count() >= minCount)  // Captured!
      .toList();

// Multi-value projection with Object[]
List<Object[]> results = Person.groupBy((Person p) -> p.department.name)
      .select((Group<Person, String> g) -> new Object[]{g.key(), g.count(), g.avg(p -> p.salary)})
      .toList();

// Sorting groups
Person.groupBy((Person p) -> p.department.name)
      .sortedBy((Group<Person, String> g) -> g.count())
      .toList();

// Pagination on groups
Person.groupBy((Person p) -> p.department.name)
      .skip(1).limit(3)
      .toList();
```

### Common GROUP BY Errors and Fixes

| Error | Root Cause | Fix |
|-------|-----------|-----|
| `ArrayIndexOutOfBoundsException` in captured vars | Only extracting from `predicates`, not `havingConditions` | Extract from all 4 lambda sources |
| `NullPointerException` in nested lambda analysis | Context missing `classMethods` or `nestedLambdaAnalyzer` | Set both before analyzing group lambdas |
| Wrong count value | Using regular COUNT instead of COUNT(DISTINCT) | Use `cb.countDistinct(groupKey)` for group counting |
| `ClassCastException` on Object[] results | Tuple not converted to array | Add `.toArray()` call in result processing |
| HAVING condition ignored | Not checking havingConditions in query generation | Generate `cq.having()` clause |

### Multi-Lambda Stream Pattern (Generalized Lesson)

**When implementing complex stream types with multiple lambda sources:**

1. **Identify all lambda sources** in the stream implementation:
   - QubitStream: predicates, sortOrders, selector
   - JoinStream: biPredicates, sourcePredicates, onConditions, sortOrders
   - GroupStream: predicates, keyExtractor, havingConditions, selector, sortOrders

2. **Extract captured variables from ALL sources** in order:
   ```java
   // Pattern for any complex stream
   for (each lambda source in order) {
       int count = countCapturedFields(lambdaInstance);
       if (count > 0 && remainingCount > 0) {
           Object[] values = CapturedVariableExtractor.extract(lambda, count);
           allCapturedValues.addAll(Arrays.asList(values));
           remainingCount -= count;
       }
   }
   ```

3. **Track context mode** in AnalysisContext:
   - `isBiEntityMode()` for join lambdas
   - `isGroupContextMode()` for group lambdas
   - Each mode affects parameter handling and method call interpretation

4. **Support nested lambda analysis** when needed:
   - Store class methods list in context
   - Provide recursive analyzer function
   - Detect INVOKEDYNAMIC/LambdaMetafactory patterns

---

## Constructor Overloading and Build Item Pitfalls (Iteration 6.5)

### The Problem: Silent Constructor Mismatch

**CRITICAL: When adding new parameters to `QueryTransformationBuildItem`, ALL callers must be updated or silent bugs occur.**

**Scenario from Iteration 6.5:**
```java
// Before: 8-parameter constructor
QueryTransformationBuildItem(queryId, className, entityClass,
    isCountQuery, isAggregationQuery, isJoinQuery, isGroupQuery, capturedVarCount)

// After: Added isSelectJoined between isJoinQuery and isGroupQuery
QueryTransformationBuildItem(queryId, className, entityClass,
    isCountQuery, isAggregationQuery, isJoinQuery, isSelectJoined, isGroupQuery, capturedVarCount)
```

**The Bug:**
```java
// Group query code (unchanged):
new QueryTransformationBuildItem(queryId, className, Object.class,
    isCountQuery, false, false, true, capturedVarCount);
//                              ^^^^^ Intended as isGroupQuery=true
//                              But now matches isSelectJoined=true, isGroupQuery=false!
```

**Result:** All group queries were silently registered as selectJoined queries instead.

### Why This Is Dangerous

1. **Java resolves to the "closest match" constructor** - no compile error
2. **Boolean parameters are interchangeable** - `true` for one flag matches another
3. **Tests for the new feature pass** - you only see failures in unrelated tests
4. **Error messages are misleading** - "No group executor found" doesn't hint at constructor issue

### Prevention Checklist

When adding parameters to `QueryTransformationBuildItem`:

- [ ] **Update ALL callers** - not just the new feature code
- [ ] **Search for all usages**: `grep "QueryTransformationBuildItem(" *.java`
- [ ] **Run FULL test suite** - not just new feature tests
- [ ] **Check executor registration counts** in error messages:
  ```
  Registered executors: 885 list, 80 count, 3 group list, 0 group count
  // If group list is unexpectedly low, check constructor calls
  ```

### Hash Computation Must Include All Flags

**CRITICAL: Lambda deduplication hashes must include ALL discriminating flags.**

**Problem Pattern:**
```java
// computeJoinHash() missing isSelectJoined flag
astString.append("|QUERY_TYPE=").append(isCountQuery ? "COUNT" : "LIST");
// Result: join().toList() and join().selectJoined().toList() get SAME hash!
```

**Deduplication silently reuses wrong executor:**
```
Deduplicated lambda at JoinQueryTest:selectJoinedReturnsPhones:325
    (reusing io.quarkiverse.qubit.generated.QueryExecutor_248)
Registering join-list executor: JoinQueryTest:selectJoinedReturnsPhones:325
// ^^^^^^ Wrong! Should be join-selectJoined executor
```

**Fix: Include all flags in hash computation:**
```java
if (isSelectJoined) {
    astString.append("|SELECT_JOINED=true");
}
astString.append("|QUERY_TYPE=").append(isCountQuery ? "COUNT" : "LIST");
```

### Debugging Multi-Executor Registration Issues

**Diagnostic Steps:**
1. Check executor registration counts in error message
2. Look for unexpected deduplication in build logs
3. Verify hash computation includes all discriminating flags
4. Check constructor parameter order matches all callers

**Key Log Patterns to Watch:**
```
# Good: New executor registered
Generated join query executor: QueryExecutor_123 (INNER JOIN SELECT JOINED, 0 captured vars)

# Bad: Deduplication reusing wrong type
Deduplicated lambda at ... (reusing QueryExecutor_248)
Registering join-list executor: ...  # Should be join-selectJoined!
```

### Testing Requirements After Parameter Changes

**MANDATORY: Run full test suite, not just new feature tests.**

```bash
# Wrong: Only testing new feature
mvn test -Dtest="JoinQueryTest"  # selectJoined tests pass!

# Right: Full regression testing
mvn clean test  # Catches group query failures!
```

**Why:**
- New feature tests verify new code works
- Regression tests verify you didn't break existing code
- Constructor mismatch bugs often appear in UNRELATED test classes

---

## selectJoined() Implementation Pattern (Iteration 6.5)

### Architecture

**selectJoined() returns joined entities instead of source entities:**

```java
// Regular join: returns List<Person>
Person.join(p -> p.phones).toList();

// selectJoined: returns List<Phone> (the joined entities)
Person.join(p -> p.phones).selectJoined().toList();
```

### Implementation Chain

1. **InvokeDynamicScanner** - Detect `selectJoined()` calls on JoinStream
   - Track `pendingJoinSelectJoined` flag during bytecode scanning
   - Use `joinSelectJoinedLine` for proper call site ID line numbers

2. **LambdaCallSite** - Store `isSelectJoined` flag in record

3. **CallSiteProcessor** - Compute hash and generate executor
   - `computeJoinHash()` must include `isSelectJoined` in hash
   - `handleDuplicateLambda()` must pass `isSelectJoined` to build item
   - `generateAndRegisterJoinExecutor()` must pass `isSelectJoined` to bytecode generator

4. **QueryExecutorClassGenerator** - Generate JPA query with `query.select(join)`
   - `generateJoinSelectJoinedQueryBody()` selects joined entity instead of root

5. **QueryExecutorRegistry** - Separate executor map for selectJoined
   - `JOIN_SELECT_JOINED_EXECUTORS` map
   - `executeJoinSelectJoinedQuery()` method

6. **JoinStreamImpl** - Runtime execution
   - `selectJoined()` returns `ListJoinedQubitStream` wrapper
   - Calls `registry.executeJoinSelectJoinedQuery()`

### Key Files Modified

| File | Change |
|------|--------|
| `InvokeDynamicScanner.java` | Detect selectJoined(), track line numbers |
| `LambdaCallSite` record | Add `isSelectJoined` field |
| `CallSiteProcessor.java` | Hash computation, executor generation |
| `LambdaDeduplicator.java` | New computeJoinHash() overload with isSelectJoined |
| `QueryExecutorClassGenerator.java` | generateJoinSelectJoinedQueryBody() |
| `QubitProcessor.java` | QueryTransformationBuildItem with isSelectJoined |
| `QueryExecutorRegistry.java` | New executor map and execution method |
| `QueryExecutorRecorder.java` | registerJoinSelectJoinedExecutor() |
| `JoinStreamImpl.java` | selectJoined() implementation, ListJoinedQubitStream |

### Test Coverage

**Test File:** `integration-tests/src/test/java/io/quarkus/qubit/it/join/JoinQueryTest.java`

```java
// Basic selectJoined
Person.join(p -> p.phones).selectJoined().toList();

// With bi-entity predicate
Person.join(p -> p.phones)
      .where((p, ph) -> ph.type.equals("mobile"))
      .selectJoined().toList();

// With pagination
Person.join(p -> p.phones).selectJoined().limit(5).toList();

// With distinct
Person.join(p -> p.phones).selectJoined().distinct().toList();
```

---

## Join Projections Implementation Pattern (Iteration 6.6)

### Architecture

**select() with BiQuerySpec projects both entities into a result type:**

```java
// Join projection: returns List<PersonPhoneDTO>
Person.join(p -> p.phones)
      .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
      .toList();

// Can also extract scalar fields from joined entity
Person.join(p -> p.phones)
      .select((Person p, Phone ph) -> ph.number)  // Returns List<String>
      .toList();
```

### Implementation Chain

1. **InvokeDynamicScanner** - Detect `select(BiQuerySpec)` calls on JoinStream
   - Track `biEntityProjectionLambdaMethodName` and descriptor
   - Set `pendingJoinProjection` flag for proper call site detection
   - Use `joinProjectionLine` for accurate call site ID

2. **LambdaCallSite** - Store bi-entity projection fields
   - `biEntityProjectionLambdaMethodName`
   - `biEntityProjectionLambdaDescriptor`
   - `isJoinProjectionQuery()` helper method

3. **CallSiteProcessor** - Analyze and generate executor
   - `analyzeJoinQuery()` calls `analyzeBiEntity()` for projection lambda
   - `computeHash()` must call 8-argument `computeJoinHash()` with projection
   - `generateAndRegisterJoinExecutor()` passes projection to generator

4. **CriteriaExpressionGenerator** - Generate JPA construct() call
   - `generateBiEntityProjection()` for bi-entity projections
   - `generateBiEntityConstructorCall()` for DTO construction
   - Uses `BiEntityFieldAccess` with `EntityPosition.FIRST/SECOND`

5. **QueryExecutorClassGenerator** - Generate projection query
   - `generateJoinProjectionQueryBody()` generates `cb.construct()` call
   - Routes correctly based on `isJoinProjection` flag

6. **QueryExecutorRegistry** - Separate executor map
   - `JOIN_PROJECTION_EXECUTORS` map
   - `executeJoinProjectionQuery()` method

7. **JoinStreamImpl** - Runtime execution
   - `select(BiQuerySpec<T, R, S>)` returns `ListProjectionQubitStream<S>`
   - Calls `registry.executeJoinProjectionQuery()`

### Key Files Modified

| File | Change |
|------|--------|
| `InvokeDynamicScanner.java` | Detect select(BiQuerySpec), track projection lambdas |
| `LambdaCallSite` record | Add bi-entity projection fields |
| `CallSiteProcessor.java` | Analyze projection, compute hash, generate executor |
| `LambdaDeduplicator.java` | 8-arg computeJoinHash() with projection |
| `CriteriaExpressionGenerator.java` | generateBiEntityProjection(), generateBiEntityConstructorCall() |
| `QueryExecutorClassGenerator.java` | generateJoinProjectionQueryBody() |
| `QubitProcessor.java` | QueryTransformationBuildItem with isJoinProjection |
| `QueryExecutorRegistry.java` | New executor map and execution method |
| `JoinStreamImpl.java` | select(BiQuerySpec) implementation, ListProjectionQubitStream |

### Critical Bug Patterns and Fixes

#### Bug 1: Deduplication Hash Method Overload Mismatch

**Problem:** When multiple overloads of hash computation exist, calling the wrong one omits critical parameters.

```java
// WRONG: 6-argument version doesn't include projection
return deduplicator.computeJoinHash(
    result.joinRelationshipExpression,
    result.biEntityPredicateExpression,
    result.sortExpressions,
    joinTypeStr,
    callSite.isCountQuery(),
    callSite.isSelectJoinedQuery());

// RIGHT: 8-argument version includes projection and isJoinProjection flag
return deduplicator.computeJoinHash(
    result.joinRelationshipExpression,
    result.biEntityPredicateExpression,
    result.biEntityProjectionExpression,  // Include projection!
    result.sortExpressions,
    joinTypeStr,
    callSite.isCountQuery(),
    callSite.isSelectJoinedQuery(),
    callSite.isJoinProjectionQuery());   // Include flag!
```

**Symptom:** Projection queries get same hash as non-projection queries, deduplicator reuses wrong executor, returns wrong result type.

**Fix:** Always use the most specific method overload that includes all discriminating parameters.

#### Bug 2: Constructor Overload Position Shift

**Problem:** Adding new boolean parameters shifts existing code to match wrong constructor.

```java
// BEFORE (9-argument, Iteration 6.5):
// positions: queryId, className, entityClass, isCountQuery, isAggregationQuery,
//            isJoinQuery, isSelectJoined, isGroupQuery, capturedVarCount

// AFTER (added isJoinProjection at position 8):
// positions: queryId, className, entityClass, isCountQuery, isAggregationQuery,
//            isJoinQuery, isSelectJoined, isJoinProjection, isGroupQuery, capturedVarCount

// Group query code (unchanged but now BROKEN):
new QueryTransformationBuildItem(queryId, className, Object.class,
    isCountQuery, false, false, false, true, capturedVarCount);
//                                     ^^^^ Was isGroupQuery, now matches isJoinProjection!
```

**Symptom:** Group queries fail with "No group query executor found" because they're registered as join projection queries.

**Fix:** Update ALL callers when adding constructor parameters. Use full constructor:
```java
new QueryTransformationBuildItem(queryId, className, Object.class,
    isCountQuery, false, false, false, false, true, capturedVarCount);
//              agg    join   selJ   joinP  group
```

### Prevention Checklist for New Parameters

When adding parameters to `QueryTransformationBuildItem` or hash methods:

- [ ] **Search for ALL usages**: `grep "QueryTransformationBuildItem\|computeJoinHash" *.java`
- [ ] **Update every caller** - not just new feature code
- [ ] **Check constructor overload resolution** - which overload does each call match?
- [ ] **Verify hash method overload** - are you calling the most specific version?
- [ ] **Run FULL test suite** - new feature tests + all existing tests
- [ ] **Check executor counts in error messages** - unexpected counts indicate registration bugs

### Debugging Build-Time to Runtime Flow

**Trace the execution path when tests fail:**

1. **Build-time registration** (CallSiteProcessor):
   ```
   Generated join query executor: QueryExecutor_123 (INNER JOIN PROJECTION, 0 captured vars)
   ```

2. **Executor registration** (QubitProcessor):
   ```
   Registered join projection executor for call site: TestClass:method:line
   ```

3. **Runtime lookup** (QueryExecutorRegistry):
   ```
   Executing join projection query for call site: TestClass:method:line
   ```

4. **Hash computation** (LambdaDeduplicator):
   ```
   Deduplicated lambda at ... (reusing QueryExecutor_XXX)  # WATCH FOR THIS!
   ```

**If you see "Deduplicated" for a query that should have unique executor:**
- Check hash computation includes all discriminating parameters
- Verify method overload being called

### Test Coverage

**Test File:** `integration-tests/src/test/java/io/quarkus/qubit/it/join/JoinQueryTest.java`

```java
// Basic DTO projection
Person.join((Person p) -> p.phones)
      .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
      .toList();

// With bi-entity predicate
Person.join((Person p) -> p.phones)
      .where((Person p, Phone ph) -> ph.type.equals("mobile"))
      .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
      .toList();

// With pagination
Person.join((Person p) -> p.phones)
      .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
      .limit(3)
      .toList();

// With distinct
Person.join((Person p) -> p.phones)
      .distinct()
      .select((Person p, Phone ph) -> new PersonPhoneDTO(p.firstName, ph.number))
      .toList();

// Scalar field extraction from joined entity
Person.join((Person p) -> p.phones)
      .where((Person p, Phone ph) -> ph.type.equals("work"))
      .select((Person p, Phone ph) -> ph.number)
      .toList();
```

---

## Multi-Parameter Method Overload Safety (General Pattern)

### The Problem with Boolean Parameter Proliferation

**As features are added, classes accumulate boolean flags:**

```java
// Iteration 1: Simple
QueryTransformationBuildItem(queryId, className, entityClass, isCountQuery, capturedVarCount)

// Iteration 6: 6 booleans
QueryTransformationBuildItem(queryId, className, entityClass,
    isCountQuery, isAggregationQuery, isJoinQuery, capturedVarCount)

// Iteration 6.5: 7 booleans
QueryTransformationBuildItem(queryId, className, entityClass,
    isCountQuery, isAggregationQuery, isJoinQuery, isSelectJoined, capturedVarCount)

// Iteration 6.6: 8 booleans
QueryTransformationBuildItem(queryId, className, entityClass,
    isCountQuery, isAggregationQuery, isJoinQuery, isSelectJoined, isJoinProjection, capturedVarCount)

// Iteration 7: 9 booleans - EASY TO GET WRONG!
QueryTransformationBuildItem(queryId, className, entityClass,
    isCountQuery, isAggregationQuery, isJoinQuery, isSelectJoined, isJoinProjection, isGroupQuery, capturedVarCount)
```

### Why Constructor Overloads Are Dangerous

1. **No compile-time safety** - `true` matches any boolean parameter
2. **Silent failures** - Code compiles but behaves incorrectly
3. **Parameter position drift** - Adding new params shifts existing code
4. **Cascade effect** - Breaking one type breaks all features using that type

### Best Practices

**When modifying multi-boolean constructors:**

1. **Always use the FULL constructor** in new code
2. **Update ALL existing callers** when adding parameters
3. **Use named constants** for clarity:
   ```java
   private static final boolean IS_COUNT_QUERY = false;
   private static final boolean IS_AGGREGATION = false;
   private static final boolean IS_JOIN = false;
   private static final boolean IS_SELECT_JOINED = false;
   private static final boolean IS_JOIN_PROJECTION = false;
   private static final boolean IS_GROUP = true;

   new QueryTransformationBuildItem(queryId, className, Object.class,
       IS_COUNT_QUERY, IS_AGGREGATION, IS_JOIN, IS_SELECT_JOINED,
       IS_JOIN_PROJECTION, IS_GROUP, capturedVarCount);
   ```

4. **Consider Builder pattern** for future additions (architectural improvement)

5. **Add comments** on positional parameters:
   ```java
   new QueryTransformationBuildItem(queryId, className, Object.class,
       isCountQuery,    // isCountQuery
       false,           // isAggregationQuery
       false,           // isJoinQuery
       false,           // isSelectJoined
       false,           // isJoinProjection
       true,            // isGroupQuery  <-- THIS ONE
       capturedVarCount);
   ```

### Testing Requirements

After modifying constructors or method overloads:

```bash
# MANDATORY: Full test suite, not just new feature
mvn clean test

# Watch for unexpected test failures in UNRELATED features
# Group tests failing after join changes = constructor mismatch likely
```

---

## Code Quality Improvements Summary (2024-2025)

### Completed Refactoring Phases

The codebase underwent extensive quality improvements tracked in `docs/code-quality-tracking.md`:

**Phase 1 - Critical Fixes**: Fixed silent fallback and null returns in SubqueryExpressionBuilder
**Phase 2 - High-Priority**: Extracted large classes, consolidated magic strings
**Phase 3 - Medium-Priority**: Created analyzers, performance optimizations
**Phase 4 - Pattern Matching Modernization**: Upgraded to Java 21, refactored 22 methods to pattern matching switch

### Key Architectural Changes

| Change | Before | After | Reduction |
|--------|--------|-------|-----------|
| MethodInvocationHandler | 1143 lines | 715 lines | 37% |
| CriteriaExpressionGenerator | 1977 lines | 1355 lines | 31% |
| CallSiteProcessor | 1359 lines | 1087 lines | 20% |

### Registry Patterns Implemented

Three registry patterns enable dependency injection and testability:

1. **ExpressionBuilderRegistry** (`generation/expression/ExpressionBuilderRegistry.java`)
   - Holds 8 expression builder instances
   - Enables mock injection for unit testing

2. **InstructionHandlerRegistry** (`analysis/instruction/InstructionHandlerRegistry.java`)
   - Holds 6 instruction handlers
   - Chain of responsibility pattern

3. **QueryTypeHandlerRegistry** (`analysis/handler/QueryTypeHandlerRegistry.java`)
   - Manages query type handlers
   - Strategy pattern for query processing

### Package Structure (After ARCH-008)

```
deployment/src/main/java/io/quarkiverse/qubit/deployment/
├── ast/                    # AST node types (LambdaExpression sealed interface)
├── analysis/               # Lambda analysis, call site processing
│   ├── branch/             # Branch instruction handlers
│   ├── handler/            # Query type handlers
│   └── instruction/        # Bytecode instruction handlers
├── common/                 # Shared utilities, constants
├── devui/                  # Dev UI processors
├── generation/             # Code generation
│   ├── expression/         # Expression builders
│   ├── join/               # Join query builders
│   └── methodcall/         # Method call handlers
└── util/                   # Helper utilities
```

---

## Antora Documentation

Documentation is implemented using Antora in `docs/` directory:

**Configuration**: `docs/antora.yml`
```yaml
name: quarkus-qubit
title: Qubit
version: dev
nav:
  - modules/ROOT/nav.adoc
```

**Pages** (`docs/modules/ROOT/pages/`):
1. `index.adoc` - Overview and installation
2. `getting-started.adoc` - Initial setup guide
3. `queries.adoc` - Query operations documentation
4. `joins.adoc` - Join query documentation
5. `grouping.adoc` - GROUP BY and aggregations
6. `subqueries.adoc` - Subquery documentation
7. `devui.adoc` - Dev UI usage guide

**When adding new features**, update the relevant documentation pages.

---

## Dev UI Implementation

Full Dev UI support is implemented in `deployment/src/main/java/io/quarkiverse/qubit/deployment/devui/`:

**Components**:
- `QubitDevUIProcessor.java` - Build step creating CardPageBuildItem
- `JpqlGenerator.java` - Generates JPQL representation for display
- `JavaSourceGenerator.java` - Generates Java lambda source for display
- `qwc-qubit-queries.js` - Web component for query visualization

**Features**:
- Displays all detected lambda queries
- Shows JPQL representation
- Shows Java source reconstruction
- Displays query metadata (type, captured variables, etc.)

---

## Behavior-Rich Enums (Learned Pattern)

When creating enums that encapsulate behavior, follow this pattern from ENUM-001 and ENUM-002:

```java
public enum FluentMethodType {
    WHERE(config -> config.withPredicate()),
    SELECT(config -> config.withProjection()),
    MIN(config -> config.withAggregation(MIN)),
    // ...

    private final Function<Config, Config> configFactory;

    FluentMethodType(Function<Config, Config> configFactory) {
        this.configFactory = configFactory;
    }

    public Config createConfig(Config base) {
        return configFactory.apply(base);
    }

    public static Optional<FluentMethodType> fromMethodName(String name) {
        return Arrays.stream(values())
            .filter(t -> t.name().equals(name.toUpperCase()))
            .findFirst();
    }
}
```

**When behavior-rich enums work well**:
- Multiple switch statements on the same value (eliminates duplication)
- Common factory method signature across all values
- Simple 1:1 mappings between values and behaviors

**When to avoid**:
- Different method signatures for different values
- Single switch location (no duplication to eliminate)
- Complex per-value logic requiring multiple abstract methods

---

## Equivalent Mutations (Testing Insight)

Some mutation testing survivors are **equivalent mutations** - mutations that produce identical observable behavior:

| Type | Example | Why It Survives |
|------|---------|-----------------|
| Performance optimization | Wrapper type checks before `Number.isAssignableFrom()` | Same result, different performance |
| Defensive programming | Empty stack early returns | `null instanceof X` returns false anyway |
| Dead code guards | Constructor validates, so runtime check unreachable | Input already guaranteed valid |

**Don't chase 100% mutation coverage** - understand why mutations survive:
1. **Logging removed** - Ignore per project instruction
2. **Equivalent** - Same observable behavior
3. **Integration-required** - Quarkus/JPA build-time code needs integration tests

---

## Quarkiverse Extension Compliance

The extension follows Quarkiverse best practices:

- ✅ **quarkiverse-parent v20** - Inherited in pom.xml
- ✅ **CapabilityBuildItem** - Registered in QubitProcessor.featureAndCapability()
- ✅ **FeatureBuildItem** - Standard Quarkus extension pattern
- ✅ **Antora documentation** - Full docs structure
- ✅ **Dev UI** - Lambda query visualization
- ✅ **Build-time processing** - All analysis at build time, no runtime reflection

---

## Code Coverage with JaCoCo

### Overview

The project uses JaCoCo for comprehensive code coverage reporting across all modules. Coverage data is aggregated into a single report at the project root level.

**Key Configuration:**
- JaCoCo version: 0.8.13
- Coverage data file: `target/jacoco.exec` (aggregated)
- Coverage report: `target/coverage/index.html`
- Coverage check: Disabled by default, enable with `-Djacoco.check.skip=false`

### Running Coverage Analysis

**Basic Commands:**

```bash
# Run tests and generate coverage report
mvn clean verify

# Run only deployment module tests with coverage
mvn -pl deployment test

# Generate report after tests (if skipped during test phase)
mvn -pl deployment verify -DskipTests

# Run with coverage threshold checks enabled
mvn clean verify -Djacoco.check.skip=false
```

**View Coverage Report:**

```bash
# Linux
xdg-open target/coverage/index.html

# macOS
open target/coverage/index.html

# Windows
start target/coverage/index.html
```

### Coverage Report Analysis Script

Use this Python script to analyze coverage CSV and generate a summary:

```bash
python3 << 'EOF'
import csv
import os

csv_file = 'target/coverage/jacoco.csv'
if not os.path.exists(csv_file):
    print("Coverage CSV not found. Run: mvn clean verify")
    exit(1)

total = {'classes': 0, 'instr_m': 0, 'instr_c': 0, 'branch_m': 0, 'branch_c': 0, 'line_m': 0, 'line_c': 0}
packages = {}

with open(csv_file, 'r') as f:
    reader = csv.DictReader(f)
    for row in reader:
        total['classes'] += 1
        total['instr_m'] += int(row['INSTRUCTION_MISSED'])
        total['instr_c'] += int(row['INSTRUCTION_COVERED'])
        total['branch_m'] += int(row['BRANCH_MISSED'])
        total['branch_c'] += int(row['BRANCH_COVERED'])
        total['line_m'] += int(row['LINE_MISSED'])
        total['line_c'] += int(row['LINE_COVERED'])

        pkg = row['PACKAGE']
        if pkg not in packages:
            packages[pkg] = {'classes': 0, 'line_m': 0, 'line_c': 0}
        packages[pkg]['classes'] += 1
        packages[pkg]['line_m'] += int(row['LINE_MISSED'])
        packages[pkg]['line_c'] += int(row['LINE_COVERED'])

print("=" * 70)
print("QUARKUS-QUBIT CODE COVERAGE SUMMARY")
print("=" * 70)
print(f"Total Classes: {total['classes']}")
print(f"Instructions: {total['instr_c']:,} / {total['instr_m'] + total['instr_c']:,} ({total['instr_c']/(total['instr_m']+total['instr_c'])*100:.1f}%)")
print(f"Branches: {total['branch_c']:,} / {total['branch_m'] + total['branch_c']:,} ({total['branch_c']/(total['branch_m']+total['branch_c'])*100:.1f}%)")
print(f"Lines: {total['line_c']:,} / {total['line_m'] + total['line_c']:,} ({total['line_c']/(total['line_m']+total['line_c'])*100:.1f}%)")
print("\nCOVERAGE BY PACKAGE:")
print("-" * 70)
for pkg, data in sorted(packages.items(), key=lambda x: x[1]['line_c']/(x[1]['line_m']+x[1]['line_c']) if (x[1]['line_m']+x[1]['line_c']) > 0 else 0, reverse=True):
    total_line = data['line_m'] + data['line_c']
    pct = (data['line_c'] / total_line * 100) if total_line > 0 else 0
    short_pkg = pkg.replace('io.quarkiverse.qubit.deployment.', '...')
    status = "✓" if pct >= 80 else ("◎" if pct >= 50 else "✗")
    print(f"{short_pkg:<50} {pct:>6.1f}% {status}")
EOF
```

### Coverage Configuration Details

**Parent POM Configuration (pom.xml):**

```xml
<!-- Properties -->
<jacoco.version>0.8.13</jacoco.version>
<jacoco.check.skip>true</jacoco.check.skip>  <!-- Enable with -Djacoco.check.skip=false -->

<!-- Plugin Management -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>${jacoco.version}</version>
    <executions>
        <!-- Prepare agent for unit tests -->
        <execution>
            <id>default-prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
            <configuration>
                <destFile>${maven.multiModuleProjectDirectory}/target/jacoco.exec</destFile>
                <append>true</append>
                <propertyName>argLine</propertyName>
            </configuration>
        </execution>
        <!-- Prepare agent for integration tests -->
        <execution>
            <id>default-prepare-agent-integration</id>
            <goals><goal>prepare-agent-integration</goal></goals>
            <configuration>
                <destFile>${maven.multiModuleProjectDirectory}/target/jacoco.exec</destFile>
                <append>true</append>
            </configuration>
        </execution>
        <!-- Generate report -->
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
            <configuration>
                <dataFile>${maven.multiModuleProjectDirectory}/target/jacoco.exec</dataFile>
                <outputDirectory>${maven.multiModuleProjectDirectory}/target/coverage</outputDirectory>
            </configuration>
        </execution>
    </executions>
</plugin>
```

**Multi-Module Aggregation:**

The configuration uses `${maven.multiModuleProjectDirectory}/target/jacoco.exec` to aggregate coverage data from all modules into a single file. This enables:
- Combined coverage from deployment, runtime, and integration-tests modules
- Single HTML report showing all coverage data
- Unified threshold checking across the entire codebase

**Surefire Plugin Integration:**

```xml
<plugin>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <!-- Late property evaluation for JaCoCo agent -->
        <argLine>@{argLine}</argLine>
        <systemPropertyVariables>
            <quarkus.jacoco.data-file>${maven.multiModuleProjectDirectory}/target/jacoco.exec</quarkus.jacoco.data-file>
            <quarkus.jacoco.reuse-data-file>true</quarkus.jacoco.reuse-data-file>
            <quarkus.jacoco.report-location>${maven.multiModuleProjectDirectory}/target/coverage</quarkus.jacoco.report-location>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

### Coverage Threshold Rules

**Bundle-Level Thresholds (when enabled):**
- Instruction coverage: ≥50%
- Branch coverage: ≥50%

**Class-Level Thresholds:**
- Line coverage: ≥40% (with exclusions)

**Excluded from Threshold Checks:**
- Bytecode generators: `QueryExecutorClassGenerator*`, `QubitBytecodeGenerator*`
- Quarkus processors: `Qubit*Processor*`, `Qubit*Enhancer*`, `Qubit*Visitor*`
- Build items: `*BuildItem*`
- Handler framework: `analysis.handler.*`
- Join generation: `generation.join.*`
- Native image processor: `QubitNativeImageProcessor`
- Package info: `**/package-info`

### Coverage Metrics Reference

**Current Coverage State (as of January 2025):**

| Metric | Covered | Total | Coverage |
|--------|---------|-------|----------|
| Instructions | ~17,700 | ~33,800 | ~52% |
| Branches | ~2,000 | ~3,450 | ~58% |
| Lines | ~3,600 | ~6,750 | ~53% |

**Package Coverage:**

| Package | Coverage | Status |
|---------|----------|--------|
| `ast` | ~100% | Excellent |
| `analysis.branch` | ~95% | Excellent |
| `util` | ~89% | Excellent |
| `common` | ~86% | Excellent |
| `analysis.instruction` | ~85% | Excellent |
| `generation.methodcall` | ~67% | Good |
| `analysis` | ~56% | Fair |
| `generation` | ~40% | Needs work |
| `analysis.handler` | ~0% | Critical |

### Coverage Improvement Priorities

**Priority 1 - Integration Test Coverage (Quarkus Processors):**
- `QubitProcessor`, `QubitEntityEnhancer`, `QubitRepositoryEnhancer`
- These require `@QuarkusTest` integration tests
- Currently ~23% coverage, tested indirectly via integration tests

**Priority 2 - Query Handler Framework:**
- `analysis.handler.*` package has 0% coverage
- Consider adding unit tests or marking as integration-tested
- Handlers are tested indirectly through full query execution

**Priority 3 - Bytecode Generators:**
- Gizmo-based code generation is complex to unit test
- Coverage is validated through integration tests that verify generated queries work
- Consider excluding from metrics or accepting lower coverage

### CI Integration

**GitHub Actions Example:**

```yaml
- name: Run tests with coverage
  run: mvn clean verify

- name: Upload coverage report
  uses: actions/upload-artifact@v3
  with:
    name: coverage-report
    path: target/coverage/

- name: Check coverage thresholds
  run: mvn jacoco:check -Djacoco.check.skip=false
  continue-on-error: true  # Or fail build on coverage drop
```

### Troubleshooting

**No coverage data generated:**
- Ensure JaCoCo plugin is listed in `<plugins>` section (not just `<pluginManagement>`)
- Check that `@{argLine}` is used in surefire configuration
- Verify `target/jacoco.exec` file exists after running tests

**Coverage report shows wrong module:**
- Each module generates its own report; the last module overwrites previous
- Use the deployment module's verify phase for the most comprehensive report
- Check `target/coverage/jacoco.csv` for the data source

**Integration tests not contributing to coverage:**
- Ensure `quarkus.test.arg-line=${argLine}` is set in failsafe configuration
- Verify `quarkus-jacoco` dependency is present with `test` scope

**"Classes do not match execution data" warning:**
- Classes were recompiled between test runs
- Run `mvn clean` before coverage generation
- Ensure consistent compilation between modules
