# Test Fixtures

## Overview

The `testutil/fixtures/` package provides pre-configured test fixtures that reduce boilerplate in unit tests. Instead of manually constructing ASM nodes and analysis contexts, tests use fluent builders and factory methods.

## Available Fixtures

### AsmFixtures

Fluent builders for ASM bytecode structures:

```java
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AsmFixtures.*;

// Default test method (name="testLambda", desc="(Ljava/lang/Object;)Z")
MethodNode method = testMethod().build();

// Customized method
MethodNode custom = testMethod()
    .name("customLambda")
    .desc("(Ljava/lang/String;Ljava/lang/Integer;)Z")
    .access(ACC_PUBLIC | ACC_STATIC)
    .build();

// Default test class (name="TestEntity", superName="java/lang/Object")
ClassNode classNode = testClass().build();

// Customized class
ClassNode entity = testClass()
    .name("com/example/Person")
    .superName("com/example/BaseEntity")
    .interfaces("java/io/Serializable")
    .build();
```

### AnalysisContextFixtures

Factory methods for analysis contexts:

```java
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AnalysisContextFixtures.*;

// Simple context (default method, entity param at index 0)
AnalysisContext ctx = simpleContext();

// Context for specific method
AnalysisContext ctx = contextFor(methodNode, 0);

// Bi-entity context (for join queries)
AnalysisContext ctx = biEntityContext();

// With custom entity parameter indices
AnalysisContext ctx = biEntityContextFor(methodNode, 0, 1);
```

### BranchStateFixtures

Pre-configured BranchState objects for branch analysis tests:

```java
import static io.quarkiverse.qubit.deployment.testutil.fixtures.BranchStateFixtures.*;

// Initial state
BranchState.Initial initial = initialState();

// AndMode states
BranchState.AndMode noJump = andModeNoJump();
BranchState.AndMode jumpedFalse = andModeJumpedFalse();
BranchState.AndMode jumpedTrue = andModeJumpedTrue();
BranchState.AndMode fromOr = andModeEnteredFromOrGroup();

// OrMode states
BranchState.OrMode noJump = orModeNoJump();
BranchState.OrMode jumpedTrue = orModeJumpedTrue();
BranchState.OrMode jumpedFalse = orModeJumpedFalse();
BranchState.OrMode fromAnd = orModeEnteredFromAndGroup();

// Custom configurations via builders
BranchState.AndMode custom = andMode()
    .jumpedTo(false)
    .enteredFromOrGroup()
    .build();
```

## Migration Guide

### Before (verbose)

```java
@BeforeEach
void setUp() {
    testMethod = new MethodNode();
    testMethod.name = "testLambda";
    testMethod.desc = "(Ljava/lang/Object;)Z";
    testMethod.instructions = new InsnList();
    context = new AnalysisContext(testMethod, 0);
}
```

### After (concise)

```java
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AsmFixtures.testMethod;
import static io.quarkiverse.qubit.deployment.testutil.fixtures.AnalysisContextFixtures.contextFor;

@BeforeEach
void setUp() {
    testMethod = testMethod().build();
    context = contextFor(testMethod, 0);
}
```

## Design Principles

1. **Sensible defaults** - Fixtures provide working defaults for common test scenarios
2. **Fluent API** - Builder pattern allows customization when needed
3. **Static imports** - Factory methods are designed for static import usage
4. **Immutable outputs** - Builders create new instances; fixtures don't share state

## Adding New Fixtures

When adding new fixtures:

1. Create factory methods in the appropriate `*Fixtures` class
2. Follow existing naming patterns (`simpleX()`, `xFor(params)`)
3. Provide Javadoc with usage examples
4. Add entries to the package-info.java if introducing new fixture classes

## AssertJ Assertions Generator

For domain objects with many properties, consider generating custom AssertJ assertions:

```bash
# Generate assertions for QueryCharacteristics and AnalysisOutcome
mvn test-compile -Passertj-generate
```

Generated assertions are placed in `src/test/java/.../assertions/` and should be committed after review.
