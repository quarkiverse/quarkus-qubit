# Mutation Testing with Pitest

## Overview

Mutation testing validates test effectiveness by introducing small code changes (mutations) and checking if tests detect them. A high mutation score indicates tests catch real bugs, not just execute code paths.

## Current Status

| Metric | Value | Target |
|--------|-------|--------|
| Mutation Score | 92% | 90% |
| Test Strength | 95% | - |
| Killed Mutations | 1,847 | - |
| Survived Mutations | 156 | - |

## Running Mutation Tests

```bash
# Using Maven profile
mvn test -Pmutation -pl deployment

# Direct goal invocation (more control)
mvn pitest:mutationCoverage -pl deployment

# View HTML report
open deployment/target/pit-reports/index.html
```

## Configuration

Pitest is configured in `deployment/pom.xml` with STRONGER mutators:

```xml
<mutators>
    <mutator>STRONGER</mutator>
</mutators>
```

Key settings:
- **16 threads** for parallel execution
- **60s timeout** per test (with 1.5x factor)
- **HTML + XML** output formats
- **No timestamped reports** for stable paths

## Exclusion Strategy

### Classes Excluded from Mutation Testing

| Category | Reason |
|----------|--------|
| Gizmo bytecode generators | Verified by IT when generated code executes correctly |
| Quarkus processors | Build-time processing, covered by integration tests |
| DevUI processors | Display-only utilities, covered by DevUI tests |
| Handler classes | Tested indirectly via registry pattern |
| Java-generated code | Records/enums have compiler-guaranteed methods |
| Fluent builders | VoidMethodCall mutations on fluent setters |

### Tests Excluded from Mutation Runs

| Test Class | Reason |
|------------|--------|
| QubitDevUIBuildTimeDataTest | Starts server, too slow for mutation testing |
| ComplexExpressionsBytecodeTest | Pitest isolation environment incompatibility |
| EqualityOperationsCriteriaTest | Pitest isolation environment incompatibility |

## Understanding Mutations

### Common Mutation Types (STRONGER mutators)

| Mutation | Description | Example |
|----------|-------------|---------|
| Conditionals Boundary | `<` → `<=` | Changes comparison boundary |
| Negate Conditionals | `==` → `!=` | Inverts boolean conditions |
| Math Mutator | `+` → `-` | Changes arithmetic operators |
| Increments | `i++` → `i--` | Reverses increment/decrement |
| Invert Negatives | `-x` → `x` | Removes negation |
| Return Values | `return x` → `return 0` | Changes return values |
| Void Method Calls | Removes void method calls | Deletes side effects |
| Empty Returns | `return x` → `return ""` | Returns empty for collections/strings |

### Interpreting Results

- **Killed**: Test detected the mutation (good)
- **Survived**: No test detected the change (needs investigation)
- **No Coverage**: No test covers this code path
- **Timed Out**: Mutation caused infinite loop (often correct behavior)

## Improving Mutation Score

### Finding Surviving Mutants

1. Open `deployment/target/pit-reports/index.html`
2. Navigate to class with surviving mutations
3. Click on surviving mutation to see exact change
4. Write test that would fail with that mutation

### Example: Killing a Boundary Mutation

```java
// Original code
if (count > 0) { ... }

// Mutation: count > 0 → count >= 0
// This survives if you only test count=1 and count=-1

// Fix: Add boundary test
@Test
void boundaryCondition_zeroValue_shouldNotProcess() {
    // count = 0 kills the >= mutation
    assertThat(process(0)).isFalse();
}
```

## Logging Exclusions

Logging calls are excluded from mutation:
- `io.quarkus.logging.Log`
- `org.jboss.logging.Logger`
- `java.util.logging.Logger`

This prevents false positives from removed log statements.

## Performance Tips

1. **Run incrementally**: Test specific packages while developing
   ```bash
   mvn pitest:mutationCoverage -pl deployment \
       -DtargetClasses=io.quarkiverse.qubit.deployment.analysis.*
   ```

2. **Use history**: Pitest caches results for unchanged code
   ```xml
   <historyInputFile>target/pit-history.bin</historyInputFile>
   <historyOutputFile>target/pit-history.bin</historyOutputFile>
   ```

3. **Limit threads on CI**: 16 threads may be too aggressive
   ```bash
   mvn pitest:mutationCoverage -pl deployment -Dthreads=4
   ```

## Integration with CI

Mutation testing is typically run:
- **Nightly**: Full mutation analysis
- **PR checks**: Incremental (only changed classes)

Not recommended for every commit due to execution time.
