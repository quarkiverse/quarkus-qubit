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
