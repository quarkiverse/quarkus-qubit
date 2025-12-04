# QUBIT Code Quality Tracking Document

This document provides a comprehensive analysis of code quality issues identified in the QUBIT codebase, organized by category, severity, and file location. Each issue includes a description, location, suggested improvement, and priority level.

## Table of Contents

1. [Summary Dashboard](#summary-dashboard)
2. [Critical Issues](#critical-issues)
3. [Architectural Improvements](#architectural-improvements)
4. [Code Smells](#code-smells)
5. [Enum and Type-Safety Improvements](#enum-and-type-safety-improvements)
6. [Bug Risks](#bug-risks)
7. [Documentation Gaps](#documentation-gaps)
8. [Performance Optimizations](#performance-optimizations)
9. [Maintainability Improvements](#maintainability-improvements)
10. [Testing Recommendations](#testing-recommendations)
11. [Refactoring Roadmap](#refactoring-roadmap)

---

## Summary Dashboard

| Category | Critical | High | Medium | Low | Total | Resolved |
|----------|----------|------|--------|-----|-------|----------|
| Architectural | 0 | ~~4~~ 1 | 12 | 9 |
| Code Smells | 0 | ~~3~~ 1 | ~~12~~ ~~11~~ ~~10~~ 9 | 8 | ~~23~~ ~~22~~ ~~21~~ 20 | ~~2~~ ~~3~~ ~~4~~ 5 |
| Enum/Type-Safety | 0 | 0 | 2 | 4 | 6 | 0 |
| Bug Risks | ~~2~~ 0 | ~~5~~ 4 | 4 | 2 | ~~13~~ 10 | 3 |
| Documentation | 0 | ~~2~~ 1 | 6 | 4 | 12 | 1 |
| Performance | 0 | 1 | 3 | 2 | 6 | 0 |
| Maintainability | 0 | ~~7~~ 1 | ~~12~~ 0 | ~~6~~ 4 | ~~25~~ 5 | 21 |
| **Total** | ~~**2**~~ **0** | ~~**22**~~ **8** | ~~**42**~~ ~~**28**~~ ~~**27**~~ **26** | ~~**25**~~ **25** | ~~**91**~~ ~~**62**~~ ~~**61**~~ **60** | **39** |

> ✅ **Phase 1 Complete**: All critical issues (CRI-001, CRI-002) and high-priority bug risk (BR-001) have been resolved.
>
> ✅ **MAINT-001, MAINT-002 Complete**: SubqueryAnalyzer and GroupMethodAnalyzer extracted from MethodInvocationHandler. Class size reduced from 1143 to 715 lines (37% reduction).
>
> ✅ **ARCH-002 Complete**: LambdaAnalysisResult refactored from 15-field record to sealed interface with 4 specialized result types (SimpleQueryResult, AggregationQueryResult, JoinQueryResult, GroupQueryResult).
>
> ✅ **Phase 4 Complete (MAINT-009 through MAINT-017)**: Java 21 pattern matching switch expressions applied across 4 files, 22 methods refactored. Upgraded pom.xml from Java 17 to Java 21. All 375 deployment tests pass.
>
> ✅ **CS-001 Complete**: Extracted 11 magic strings from MethodInvocationHandler.java to QubitConstants.java. Added new JVM_* constants for collection interfaces and standard library classes.
>
> ✅ **ARCH-001 Progress (CriteriaExpressionGenerator)**: Extracted BiEntityExpressionBuilder (555 lines) and GroupExpressionBuilder (411 lines). CriteriaExpressionGenerator reduced from 1977 to 1355 lines (31% reduction). All 375 deployment tests pass.
>
> ✅ **ARCH-001 Substantially Resolved**: Analyzed LambdaExpression.java (1119 lines) - well-organized sealed interface with clear section separators for Core Expressions, Relationship Navigation, Collection Operations, Join Queries, Grouping Operations, and Subqueries. Extracting to sub-interfaces would break sealed pattern without benefit. All four originally-identified large classes now addressed.
>
> ✅ **ARCH-003 Complete**: Created `ExpressionBuilder` marker interface with comprehensive documentation. All 8 expression builders now implement this interface. Analysis determined that a functional interface would add complexity without benefit due to fundamentally different method signatures across builder categories. The marker interface provides type-level documentation, IDE navigation support, and clear organizational pattern.
>
> ✅ **ARCH-004 Complete**: Created `ExpressionBuilderRegistry` record for dependency injection of expression builders. CriteriaExpressionGenerator now accepts registry via constructor, enabling testability with mock builders. Default no-arg constructor maintains backward compatibility. All 375 deployment tests pass.
>
> ✅ **ARCH-005 Complete**: Created `InstructionHandlerRegistry` record for dependency injection of instruction handlers. LambdaBytecodeAnalyzer now accepts registry via constructor, enabling testability with mock handlers. Handler order is preserved (chain of responsibility pattern). All 1113 tests pass.
>
> ✅ **ARCH-006 Complete**: Refactored `AnalysisContext` to use constructor-based immutable configuration. Created `NestedLambdaSupport` record to bundle classMethods and analyzer function. Configuration fields (groupContextMode, nestedLambdaSupport) are now final. Processing state (currentInstructionIndex, hasSeenBranch, pendingArray*) remains mutable as required by bytecode analysis. All 1113 tests pass.
>
> ✅ **ARCH-009 Complete**: Added 20 factory methods to 5 AST node types in LambdaExpression.java: BinaryOp (13 methods for logical, comparison, arithmetic ops), UnaryOp (1 method), PathExpression (2 methods), BiEntityFieldAccess (2 methods), BiEntityPathExpression (2 methods). All 375 deployment tests pass.
>
> ✅ **ARCH-008 Complete**: Full module boundary refactoring implemented. Created `ast/` package (LambdaExpression), moved `InvokeDynamicScanner` to `analysis/`, created `common/` package (PatternDetector, BytecodeValidator, BytecodeAnalysisException, BytecodeAnalysisConstants), flattened `branch/handlers/` into `branch/`, renamed `handlers/` to `instruction/` and `builders/` to `expression/`, removed orphaned BytecodeInstructionHandler.java. Added package-info.java for all 9 packages. All tests pass.
>
> ✅ **CS-002 Complete**: Refactored `tryLoadClass()` in ClassLoaderHelper from nested try-catch blocks to classloader list iteration pattern. Uses `initialize=false` consistently for build-time safety. All 1113 tests pass.
>
> ⏸️ **CS-003 Deferred**: JSpecify null-safety annotations were implemented but reverted due to VSCode JDT.LS compatibility issues. External annotations (.eea files) for third-party libraries cannot be loaded in VSCode, causing 700+ unresolvable warnings. EEA files preserved in `.eea/` directory for future use with Eclipse IDE.

---

## Critical Issues

### CRI-001: Silent Fallback in SubqueryExpressionBuilder.generateFieldPath() ✅ RESOLVED
- **File**: [SubqueryExpressionBuilder.java:322-344](deployment/src/main/java/io/quarkus/qubit/deployment/generation/builders/SubqueryExpressionBuilder.java#L322-L344)
- **Severity**: Critical
- **Status**: ✅ **RESOLVED** (Phase 1)
- **Description**: The method returns `root` as a fallback when expression type is unrecognized, which could produce incorrect JPA queries silently.
- **Fix Applied**:
  - Added null check with `IllegalArgumentException`
  - Replaced silent `return root` fallback with explicit `IllegalArgumentException` for unsupported types
  - Added proper Javadoc with `@throws` documentation

### CRI-002: Null Return in generateSubqueryExpression() Without Warning ✅ RESOLVED
- **File**: [SubqueryExpressionBuilder.java:449-481](deployment/src/main/java/io/quarkus/qubit/deployment/generation/builders/SubqueryExpressionBuilder.java#L449-L481)
- **Severity**: Critical
- **Status**: ✅ **RESOLVED** (Phase 1)
- **Description**: Returns `null` for unhandled expression types without logging, making debugging difficult.
- **Fix Applied**:
  - Added `Logger` to the class
  - Added explicit null check at method start (returns null for null input - valid case)
  - Added `log.warnf()` for unhandled expression types before returning null
  - Added proper Javadoc documentation

---

## Architectural Improvements

### ARCH-001: Excessively Large Classes ✅ SUBSTANTIALLY RESOLVED
- **Severity**: High
- **Status**: ✅ **SUBSTANTIALLY RESOLVED** - Three major classes reduced by 20-37%, fourth is well-organized
- **Files Affected**:
  - [CriteriaExpressionGenerator.java](deployment/src/main/java/io/quarkus/qubit/deployment/generation/CriteriaExpressionGenerator.java): ~~**1977 lines**~~ → **1355 lines** ✅ (31% reduction)
  - [CallSiteProcessor.java](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/CallSiteProcessor.java): ~~**1359 lines**~~ → **1087 lines** ✅ (20% reduction)
  - [MethodInvocationHandler.java](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/instruction/MethodInvocationHandler.java): ~~**1143 lines**~~ → **715 lines** ✅ (37% reduction)
  - [LambdaExpression.java](deployment/src/main/java/io/quarkus/qubit/deployment/ast/LambdaExpression.java): **1119 lines** ✅ (well-organized, see analysis below)
- **Fix Applied (CriteriaExpressionGenerator)**:
  - Created [BiEntityExpressionBuilder.java](deployment/src/main/java/io/quarkus/qubit/deployment/generation/builders/BiEntityExpressionBuilder.java) (555 lines) - handles bi-entity (join) query expressions
  - Created [GroupExpressionBuilder.java](deployment/src/main/java/io/quarkus/qubit/deployment/generation/builders/GroupExpressionBuilder.java) (411 lines) - handles GROUP BY query expressions
  - Created [ExpressionGeneratorHelper.java](deployment/src/main/java/io/quarkus/qubit/deployment/generation/builders/ExpressionGeneratorHelper.java) interface for clean delegation
  - CriteriaExpressionGenerator now delegates to these specialized builders
- **Fix Applied (CallSiteProcessor)**:
  - Created [LambdaAnalysisResult.java](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/LambdaAnalysisResult.java) (84 lines) - extracted sealed interface with 4 result types and SortExpression record
  - Created [CapturedVariableHelper.java](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/CapturedVariableHelper.java) (246 lines) - extracted 5 static utility methods for captured variable operations
  - CallSiteProcessor now uses static imports for helper methods
- **LambdaExpression Analysis** (1119 lines - acceptable as-is):
  - Well-organized sealed interface defining AST node types for lambda expressions
  - Clear section separators (`// ===...===`) divide logical groups:
    - Core Expressions (lines 1-237): BinaryOp, UnaryOp, FieldAccess, MethodCall, Constant, etc.
    - Relationship Navigation (lines 239-346): RelationType, PathSegment, PathExpression
    - Collection Operations (lines 348-444): InExpression, MemberOfExpression
    - Join Queries (lines 446-600): EntityPosition, BiEntityParameter, BiEntityFieldAccess, BiEntityPathExpression
    - Grouping Operations (lines 602-787): GroupKeyReference, GroupAggregationType, GroupAggregation, GroupParameter
    - Subqueries (lines 789-1117): SubqueryAggregationType, SubqueryBuilderReference, ScalarSubquery, ExistsSubquery, InSubquery, CorrelatedVariable
  - Extracting to sub-interfaces would break sealed interface pattern and complicate imports
  - Each record has proper validation and Javadoc documentation
  - **Conclusion**: File size is appropriate for a comprehensive AST definition; no refactoring needed
- **Related Items**:
  - → ~~**MAINT-001** extracts ~200 lines from MethodInvocationHandler (subquery analysis)~~ ✅ **RESOLVED**
  - → ~~**MAINT-002** extracts ~150 lines from MethodInvocationHandler (group analysis)~~ ✅ **RESOLVED**
  - ~~Combined extraction reduces MethodInvocationHandler from 1143 to ~800 lines~~ → Actual: 715 lines (37% reduction)

### ARCH-002: LambdaAnalysisResult Record Has Too Many Fields ✅ RESOLVED
- **File**: [CallSiteProcessor.java:41-92](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/CallSiteProcessor.java#L41-L92)
- **Severity**: High
- **Status**: ✅ **RESOLVED**
- **Description**: Record with 15 fields violated Single Responsibility Principle.
- **Fix Applied**:
  - Converted 15-field record to sealed interface with 4 specialized result types
  - Each query type now has its own result record with only the relevant fields:
    - `SimpleQueryResult`: where, select, combined, sorting-only queries (4 fields)
    - `AggregationQueryResult`: min, max, avg, sum* queries (4 fields)
    - `JoinQueryResult`: join, leftJoin with BiQuerySpec (6 fields)
    - `GroupQueryResult`: groupBy with GroupQuerySpec (6 fields)
  - Updated `processCallSite()` to use pattern matching dispatch (if-else instanceof for Java 17)
  - Updated `computeHash()` to use pattern matching dispatch
  - Updated all `analyze*()` methods to return the correct specialized types
  - All 1113 tests pass

### ARCH-003: Missing Interface for Expression Builders ✅ RESOLVED
- **Severity**: High
- **Status**: ✅ **RESOLVED**
- **Description**: Expression builders (`ArithmeticExpressionBuilder`, `StringExpressionBuilder`, etc.) share common patterns but don't implement a common interface.
- **Analysis Findings**:
  - **Category A (Binary Operators)**: `ArithmeticExpressionBuilder` and `ComparisonExpressionBuilder` have similar signatures but different semantics (Expression vs Predicate return types)
  - **Category B (Method Calls)**: `StringExpressionBuilder`, `TemporalExpressionBuilder`, `BigDecimalExpressionBuilder` have fundamentally different APIs - StringExpressionBuilder has 4 specialized build methods with varying signatures
  - **Category C (Higher-Level)**: `BiEntityExpressionBuilder`, `GroupExpressionBuilder`, `SubqueryExpressionBuilder` use delegation to `ExpressionGeneratorHelper`
  - A functional interface would force artificial method unification, adding complexity without real polymorphic benefit
- **Fix Applied**:
  - Created [ExpressionBuilder.java](deployment/src/main/java/io/quarkus/qubit/deployment/generation/builders/ExpressionBuilder.java) - marker interface with comprehensive Javadoc documenting:
    - Three categories of expression builders (Binary Operators, Method Calls, Higher-Level)
    - Design rationale explaining why a functional interface was not appropriate
    - Clear organization of the builders package
  - All 8 expression builders now implement `ExpressionBuilder`:
    - `ArithmeticExpressionBuilder`, `ComparisonExpressionBuilder`
    - `StringExpressionBuilder`, `TemporalExpressionBuilder`, `BigDecimalExpressionBuilder`
    - `BiEntityExpressionBuilder`, `GroupExpressionBuilder`, `SubqueryExpressionBuilder`
  - Benefits: Type-level documentation, IDE "find implementations" support, organizational clarity

### ARCH-004: Hardcoded Builder Instantiation ✅ RESOLVED
- **File**: [CriteriaExpressionGenerator.java:91-96](deployment/src/main/java/io/quarkus/qubit/deployment/generation/CriteriaExpressionGenerator.java#L91-L96)
- **Severity**: Medium
- **Status**: ✅ **RESOLVED**
- **Description**: Builders were instantiated directly, limiting testability.
- **Fix Applied**:
  - Created [ExpressionBuilderRegistry.java](deployment/src/main/java/io/quarkus/qubit/deployment/generation/builders/ExpressionBuilderRegistry.java) record holding all 8 builder instances
  - Added `createDefault()` static factory method for production use
  - Added null validation in compact constructor
  - Added constructor to CriteriaExpressionGenerator accepting registry for DI
  - Default no-arg constructor uses `ExpressionBuilderRegistry.createDefault()` for backward compatibility
  - All builder access now through `builderRegistry.arithmeticBuilder()` etc.
- **Benefits**:
  - **Testability**: Tests can inject mock builders via registry constructor
  - **Immutability**: Record provides immutable, thread-safe configuration
  - **Backward Compatibility**: Existing code works unchanged with no-arg constructor
  - **Validation**: Null checks prevent misconfiguration

### ARCH-005: Handler List Not Configurable ✅ RESOLVED
- **File**: [LambdaBytecodeAnalyzer.java:47-54](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/LambdaBytecodeAnalyzer.java#L47-L54)
- **Severity**: Medium
- **Status**: ✅ **RESOLVED**
- **Description**: Handler list was hardcoded, limiting extensibility and testability.
- **Fix Applied**:
  - Created [InstructionHandlerRegistry.java](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/InstructionHandlerRegistry.java) record holding the ordered handler list
  - Added `createDefault()` static factory method for production use
  - Added validation in compact constructor (null check, empty list check)
  - Creates defensive immutable copy with `List.copyOf()`
  - Added constructor to LambdaBytecodeAnalyzer accepting registry for DI
  - Default no-arg constructor uses `InstructionHandlerRegistry.createDefault()` for backward compatibility
  - Handler access now through `handlerRegistry.handlers()` in `delegateToHandlers()`
- **Benefits**:
  - **Testability**: Tests can inject mock handlers via registry constructor
  - **Extensibility**: Custom handlers can be added without modifying analyzer
  - **Immutability**: Record provides immutable, thread-safe configuration
  - **Order Preservation**: Handler order matters for chain of responsibility - registry maintains order
  - **Backward Compatibility**: Existing code works unchanged with no-arg constructor

### ARCH-006: Mutable State in AnalysisContext ✅ RESOLVED
- **File**: [AnalysisContext.java](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/AnalysisContext.java)
- **Severity**: Medium
- **Status**: ✅ **RESOLVED**
- **Description**: `AnalysisContext` had multiple mutable fields set after construction via setters.
- **Analysis**: AnalysisContext has two categories of state:
  - **Configuration State**: `groupContextMode`, `classMethods`, `nestedLambdaAnalyzer` - should be immutable
  - **Processing State**: `currentInstructionIndex`, `hasSeenBranch`, `pendingArrayElementType`, `pendingArrayElements` - must remain mutable (mutated during instruction analysis)
- **Fix Applied**:
  - Created `NestedLambdaSupport` record inside AnalysisContext to bundle classMethods and analyzer function
  - Made `groupContextMode` final (set at construction)
  - Made `nestedLambdaSupport` final (set at construction, nullable)
  - Added 4 new constructor overloads for different use cases:
    - `AnalysisContext(method, entityParamIndex)` - simple single-entity (existing)
    - `AnalysisContext(method, firstEntityParamIndex, secondEntityParamIndex)` - bi-entity (existing)
    - `AnalysisContext(method, entityParamIndex, nestedLambdaSupport)` - group context
    - `AnalysisContext(method, entityParamIndex, groupContextMode, nestedLambdaSupport)` - full config
    - `AnalysisContext(method, firstEntityParamIndex, secondEntityParamIndex, nestedLambdaSupport)` - bi-entity with nested
  - Removed setter methods: `setGroupContextMode()`, `setClassMethods()`, `setNestedLambdaAnalyzer()`
  - Updated `findMethod()` and `analyzeNestedLambda()` to use `nestedLambdaSupport` field
  - Added `hasNestedLambdaSupport()` method for checking configuration
  - Updated `LambdaBytecodeAnalyzer` to use new constructors:
    - Created `createNestedLambdaSupport()` factory method
    - Updated `analyzeGroupContext()` to use group context constructor
    - Updated `analyzeMethodInstructions()` methods to pass NestedLambdaSupport
  - Added ARCH-006 documentation comments throughout
- **Benefits**:
  - Configuration state is now immutable and set at construction time
  - Clear separation between configuration and processing state
  - No more setter-based initialization anti-pattern
  - Thread-safety for configuration (processing state is still single-threaded by design)

### ARCH-007: Missing Central Configuration
- **Severity**: Medium
- **Description**: No centralized configuration for analysis/generation options.
- **Suggested Fix**: Create `QubitConfiguration` class for tunable parameters.

### ARCH-008: No Clear Module Boundaries Within Deployment ✅ RESOLVED
- **Severity**: Low
- **Status**: ✅ **RESOLVED**
- **Description**: Package structure existed but responsibilities overlapped.
- **Fix Applied**:
  - Created `ast/` package: Moved `LambdaExpression.java` to dedicated AST package
  - Moved `InvokeDynamicScanner.java` from root to `analysis/` where it belongs
  - Created `common/` package: Moved shared utilities (`PatternDetector`, `BytecodeValidator`, `BytecodeAnalysisException`, `BytecodeAnalysisConstants`)
  - Flattened `analysis/branch/handlers/` into `analysis/branch/` (reduced nesting from 4 to 3 levels)
  - Renamed `analysis/handlers/` to `analysis/instruction/` for clarity
  - Renamed `generation/builders/` to `generation/expression/` for clarity
  - Removed orphaned `BytecodeInstructionHandler.java` (dead code)
  - Added `package-info.java` for all 9 packages with comprehensive Javadoc
- **New Package Structure**:
  ```
  deployment/
  ├── ast/                    # AST node types (LambdaExpression)
  ├── analysis/               # Bytecode analysis
  │   ├── instruction/        # Instruction handlers (renamed from handlers/)
  │   └── branch/             # Branch handlers (flattened from branch/handlers/)
  ├── generation/             # Code generation
  │   └── expression/         # Expression builders (renamed from builders/)
  ├── common/                 # Shared utilities
  └── util/                   # Low-level utilities
  ```
- **See Also**: [ARCH-008-module-boundaries.md](ARCH-008-module-boundaries.md) for full design document

### ARCH-009: Missing Factory Methods for Complex AST Nodes ✅ RESOLVED
- **File**: [LambdaExpression.java](deployment/src/main/java/io/quarkus/qubit/deployment/LambdaExpression.java)
- **Severity**: Low
- **Status**: ✅ **RESOLVED**
- **Description**: Some records have factory methods (e.g., `InExpression.in()`), others don't.
- **Fix Applied**:
  - Added 20 factory methods to 5 AST node types:
  - **BinaryOp** (13 factory methods):
    - Logical: `and()`, `or()`
    - Comparison: `eq()`, `ne()`, `lt()`, `le()`, `gt()`, `ge()`
    - Arithmetic: `add()`, `sub()`, `mul()`, `div()`, `mod()`
  - **UnaryOp** (1 factory method):
    - `not()` for logical NOT operation
  - **PathExpression** (2 factory methods):
    - `single(fieldName, fieldType, relationType)` - single-segment path
    - `field(fieldName, fieldType)` - simple field path (FIELD relation)
  - **BiEntityFieldAccess** (2 factory methods):
    - `fromFirst(fieldName, fieldType)` - field from first entity
    - `fromSecond(fieldName, fieldType)` - field from second entity
  - **BiEntityPathExpression** (2 factory methods):
    - `fromFirst(segments, resultType)` - path from first entity
    - `fromSecond(segments, resultType)` - path from second entity
  - All 375 deployment tests pass
- **Codebase Refactored to Use Factory Methods**:
  - 9 files updated to use `BinaryOp` factory methods instead of constructor:
    - `LambdaBytecodeAnalyzer.java` - `and()`
    - `ArithmeticInstructionHandler.java` - `add()`, `sub()`, `mul()`, `div()`, `mod()`, `and()`, `or()`
    - `BranchHandler.java` - `and()`, `or()`
    - `MethodInvocationHandler.java` - `eq()`
    - `CapturedVariableHelper.java` - `and()`
    - `IfEqualsZeroInstructionHandler.java` - `ne()`, `eq()`
    - `IfNotEqualsZeroInstructionHandler.java` - `eq()`
    - `SubqueryAnalyzer.java` - `and()`
    - `InvokeDynamicHandler.java` - `add()`
  - Removed unused operator constant static imports from all files
  - All 1488 tests pass (375 deployment + 1113 integration)

---

## Code Smells

### CS-001: Magic Strings in Multiple Files ✅ RESOLVED
- **Severity**: High
- **Status**: ✅ **RESOLVED**
- **Files Affected**:
  - ~~[MethodInvocationHandler.java](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/MethodInvocationHandler.java): Multiple hardcoded strings~~ ✅
  - [CriteriaExpressionGenerator.java](deployment/src/main/java/io/quarkus/qubit/deployment/generation/CriteriaExpressionGenerator.java): Already using constants from QubitConstants ✅
- **Fix Applied**:
  - Added new section "JVM Internal Class Names" to QubitConstants.java with consistent `JVM_*` naming:
    - `JVM_JAVA_LANG_STRING`, `JVM_JAVA_LANG_BOOLEAN`, `JVM_JAVA_MATH_BIG_DECIMAL`
    - `JVM_JAVA_TIME_LOCAL_DATE`, `JVM_JAVA_TIME_LOCAL_DATE_TIME`, `JVM_JAVA_TIME_LOCAL_TIME`
    - `JVM_PREFIX_JAVA_TIME_LOCAL` for startsWith checks
    - 11 collection interface constants (`JVM_JAVA_UTIL_COLLECTION`, `JVM_JAVA_UTIL_LIST`, etc.)
    - `COLLECTION_INTERFACE_OWNERS` Set using the new constants
  - Updated MethodInvocationHandler.java to use all new constants
  - All 375 deployment tests pass

### CS-002: Duplicate Catch Blocks in tryLoadClass() ✅ RESOLVED
- **File**: ~~[MethodInvocationHandler.java:1109-1122](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/MethodInvocationHandler.java#L1109-L1122)~~ → [ClassLoaderHelper.java:46-70](deployment/src/main/java/io/quarkiverse/qubit/deployment/common/ClassLoaderHelper.java#L46-L70)
- **Severity**: High
- **Status**: ✅ **RESOLVED**
- **Description**: Nested try-catch blocks for ClassNotFoundException.
- **Fix Applied**:
  - Method extracted to `ClassLoaderHelper.java` (ARCH-008)
  - Refactored nested try-catch to classloader list iteration pattern
  - Uses `initialize=false` consistently for build-time safety
  - Added null check for context classloader
- **New Code**:
```java
public static Class<?> tryLoadClass(String className) {
    if (className == null || className.isEmpty()) {
        return null;
    }
    // Try classloaders in preference order
    ClassLoader[] loaders = {
        Thread.currentThread().getContextClassLoader(),
        ClassLoaderHelper.class.getClassLoader()
    };
    for (ClassLoader loader : loaders) {
        if (loader == null) {
            continue;  // Context classloader may be null
        }
        try {
            return Class.forName(className, false, loader);
        } catch (ClassNotFoundException ignored) {
            // Try next classloader
        }
    }
    log.debugf("Class not loadable at build-time: %s", className);
    return null;
}
```
- **Benefits**: Cleaner code, easier to extend with more classloaders, no nested catches.

### CS-003: Inconsistent Null Handling ⏸️ DEFERRED
- **Severity**: High
- **Status**: ⏸️ **DEFERRED** - Reverted due to VSCode JDT.LS compatibility issues
- **Description**: Some methods return null, some throw, inconsistent across codebase.
- **Previous Attempt**:
  - Applied JSpecify `@NullMarked` to all 9 package-info.java files
  - Added `@Nullable` annotations to 21+ files with null-returning methods
  - Created External Annotations (.eea files) for third-party libraries (Gizmo, ASM, Jandex)
- **Why Reverted**:
  - VSCode's JDT Language Server cannot resolve external annotation paths correctly
  - The `annotationpath` attribute in `.classpath` causes build errors: "Invalid external annotation path"
  - Eclipse IDE supports EEA files properly, but VSCode does not
  - Generated 700+ IDE warnings that could not be suppressed without external annotations working
- **Future Plan**:
  - Wait for VSCode Java extension to improve EEA path resolution
  - Consider alternative: Eclipse IDE for development with full null-safety
  - Or: Use runtime null checks instead of compile-time annotations
- **Preserved Artifacts**:
  - `.eea/` directory with external annotations for Gizmo, ASM, and Jandex
  - Documentation in `.eea/README.md` for future use with Eclipse IDE
- **Original Examples** (still applicable):
  - `AnalysisContext.pop()`: Returns null if empty
  - `LambdaBytecodeAnalyzer.analyze()`: Returns null on failure
  - Record constructors: Throw `NullPointerException`

### CS-004: Long Method: handleSubqueryBuilderMethod()
- **File**: [MethodInvocationHandler.java:910-958](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/MethodInvocationHandler.java#L910-L958)
- **Severity**: Medium
- **Description**: Method is 48 lines with large switch statement.
- **Suggested Fix**: Extract each case to named method, use enum dispatch.
- **Subsumed By**: → **MAINT-001** (entire subquery handling [861-1063] moved to separate class)

### CS-005: Deep Nesting in processCallSite()
- **File**: [CallSiteProcessor.java:85-178](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/CallSiteProcessor.java#L85-L178)
- **Severity**: Medium
- **Description**: Multiple if-else nesting levels make code hard to follow.
- **Suggested Fix**: Use early returns, extract to strategy methods.
- **Duplicate Of**: → **MAINT-006** (same method, same fix approach)

### CS-006: Excessive Boolean Parameters ✅ RESOLVED
- **File**: ~~[CallSiteProcessor.java:111-118](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/CallSiteProcessor.java#L111-L118)~~
- **Severity**: Medium
- **Status**: ✅ **RESOLVED**
- **Description**: `deduplicator.handleDuplicateLambda()` had 6 boolean parameters (isCountQuery, isAggregationQuery, isJoinQuery, isSelectJoined, isJoinProjection, isGroupQuery).
- **Fix Applied**:
  - Created [QueryCharacteristics.java](deployment/src/main/java/io/quarkiverse/qubit/deployment/analysis/QueryCharacteristics.java) record bundling all 6 boolean flags
  - Added factory methods for common query types: `forList()`, `forCount()`, `forAggregation()`, `forJoinList()`, `forJoinCount()`, `forSelectJoined()`, `forJoinProjection()`, `forGroupList()`, `forGroupCount()`
  - Added `fromCallSite()` factory method for extracting characteristics from `LambdaCallSite`
  - Updated `LambdaDeduplicator.handleDuplicateLambda()` to accept `QueryCharacteristics` (reduced from 11 to 6 parameters)
  - Updated `QueryTransformationBuildItem` to use `QueryCharacteristics` internally (reduced from 6 telescoping constructors to 3)
  - Updated call sites in `CallSiteProcessor` to use `QueryCharacteristics.fromCallSite()` and factory methods
  - All 1,113 tests pass
- **Benefits**:
  - **Readability**: Query type intent is now explicit (e.g., `QueryCharacteristics.forGroupCount()` vs `isCountQuery=true, isGroupQuery=true`)
  - **Maintainability**: Adding new query type flags requires only updating `QueryCharacteristics` record
  - **Type Safety**: `QueryCharacteristics` record provides compile-time type checking
  - **Reduced Boilerplate**: Eliminated 6 telescoping constructors in `QueryTransformationBuildItem`

### CS-007: Commented Code Blocks ✅ N/A (Clean Codebase)
- **Files**: Various
- **Severity**: Medium
- **Status**: ✅ **N/A** - Codebase is clean (no commented-out code found)
- **Description**: Original issue suggested some files contain commented-out code blocks.
- **Investigation Results**:
  - Comprehensive grep search performed using 15+ patterns across deployment and runtime modules
  - Patterns searched: commented return/if/for/while/try statements, commented method calls, commented variable assignments, block comments with code, debug logging, TODO/FIXME markers
  - **Finding**: No actual commented-out code blocks found
  - **Legitimate comments found** (not issues):
    - Javadoc documentation with code examples
    - Explanatory comments describing operations (e.g., `// cb.concat(left, right)`)
    - Section separator comments (e.g., `// ===...===`)
    - Active code with "temporary debug" comments (logging is active, not commented out)
- **Conclusion**: Issue is not applicable - codebase maintains clean comment hygiene

### CS-008: Switch Expressions Without Default Cases ✅ RESOLVED
- **Severity**: Medium
- **Status**: ✅ **RESOLVED**
- **Description**: Some switch expressions relied on exhaustive enum matching but had no explicit default case.
- **Files Fixed**:
  - [ControlFlowAnalyzer.java:150-156](deployment/src/main/java/io/quarkiverse/qubit/deployment/analysis/ControlFlowAnalyzer.java#L150-L156): Added default for `LabelClassification` enum switch
  - [SubqueryExpressionBuilder.java:246-253](deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/SubqueryExpressionBuilder.java#L246-L253): Added default for `SubqueryAggregationType` enum switch
  - [SubqueryExpressionBuilder.java:276-292](deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/SubqueryExpressionBuilder.java#L276-L292): Added default for `SubqueryAggregationType` enum switch
  - [GroupExpressionBuilder.java:260-285](deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/GroupExpressionBuilder.java#L260-L285): Added default for `GroupAggregationType` enum switch
- **Fix Applied**: Added `default -> throw new IllegalStateException("Unexpected enum value: " + enumVar)` to all exhaustive enum switches for future-proofing. If a new enum value is added, the code will fail fast with a clear error message rather than silently producing incorrect behavior.
- **Benefits**:
  - **Future-proofing**: New enum values cause immediate, clear failures
  - **Defensive programming**: Catches unexpected states at runtime
  - **Consistent pattern**: All enum switches now follow the same convention

### CS-009: Repeated Pattern: Pop Multiple Items From Stack
- **File**: [MethodInvocationHandler.java](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/MethodInvocationHandler.java)
- **Severity**: Medium
- **Description**: Pattern of checking stack size and popping multiple items repeated often.
- **Suggested Fix**: Extract helper method `popN(int n)` or `popPair()`.

### CS-010: Raw Type Usage
- **Files**: Various generated code paths
- **Severity**: Medium
- **Description**: Some generic types are used without parameters.
- **Suggested Fix**: Add proper type parameters.

### CS-011: Empty Catch Blocks (Implicit)
- **Severity**: Low
- **Description**: Some catch blocks only log without proper error propagation.
- **Suggested Fix**: Ensure proper error handling chain.

### CS-012: Long Parameter Lists
- **File**: [CallSiteProcessor.java:302-312](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/CallSiteProcessor.java#L302-L312)
- **Severity**: Low
- **Description**: `generateAndRegisterExecutor()` has 11 parameters.
- **Suggested Fix**: Create parameter object or builder.

### CS-013: Inconsistent Naming: entityParameterIndex vs entityParameterSlot
- **Files**: Various
- **Severity**: Low
- **Description**: Terms "index" and "slot" used interchangeably.
- **Suggested Fix**: Standardize on one term.

### CS-014: Static Utility Method Candidates
- **Severity**: Low
- **Description**: Some private methods don't use instance state.
- **Suggested Fix**: Consider extracting to utility classes.

---

## Enum and Type-Safety Improvements

This section documents opportunities to improve code quality through better use of enums, EnumMap/EnumSet, and type-safe dispatch patterns. These improvements offer performance benefits (O(1) lookup, cache-friendly arrays), memory efficiency, and enhanced compile-time safety.

### Current Enum Inventory

The codebase has 13 well-designed enums:

| Enum | Location | Purpose |
|------|----------|---------|
| `BinaryOp.Operator` | LambdaExpression.java:62 | Binary operation types (EQ, NE, LT, LE, GT, GE, AND, OR, ADD, SUB, MUL, DIV, MOD) |
| `UnaryOp.Operator` | LambdaExpression.java:186 | Unary operation types (NOT) |
| `RelationType` | LambdaExpression.java:401 | Relationship navigation types (FIELD, ONE_TO_MANY, MANY_TO_ONE, ONE_TO_ONE, MANY_TO_MANY) |
| `EntityPosition` | LambdaExpression.java:636 | Bi-entity parameter position (FIRST, SECOND) |
| `GroupAggregationType` | LambdaExpression.java:876 | Group aggregation functions (COUNT, COUNT_DISTINCT, AVG, SUM, MIN, MAX) |
| `SubqueryAggregationType` | LambdaExpression.java:1034 | Subquery aggregation functions (AVG, SUM, MIN, MAX, COUNT) |
| `JoinType` | InvokeDynamicScanner.java:36 | Join types (INNER, LEFT) |
| `QueryContext` | InvokeDynamicScanner.java:47 | Query context types (SIMPLE, BI_ENTITY, GROUP) |
| `LabelClassification` | ControlFlowAnalyzer.java:40 | Label roles (TRUE_SINK, FALSE_SINK, INTERMEDIATE) |
| `StringOperationType` | StringExpressionBuilder.java:101 | String operation categories (TRANSFORMATION, PATTERN, SUBSTRING, UTILITY) |
| `BranchPattern` | PatternDetector.java:31 | Branch patterns (AND_PATTERN, OR_PATTERN, COMPLEX) |
| `SortDirection` | SortDirection.java:12 | Sort directions (ASC, DESC) |
| `JoinType` (runtime) | JoinType.java:6 | Runtime join types (INNER, LEFT) |

### ENUM-001: Create `FluentMethodType` Enum for API Method Dispatch
- **Files**: [QubitRepositoryEnhancer.java:177-202](deployment/src/main/java/io/quarkiverse/qubit/deployment/QubitRepositoryEnhancer.java#L177-L202), [QubitConstants.java:22-67](runtime/src/main/java/io/quarkiverse/qubit/runtime/QubitConstants.java#L22-L67)
- **Severity**: Medium
- **Priority**: High (affects multiple files, enables EnumMap usage)
- **Description**: Method name dispatch uses string constants with switch statements. Multiple `Set.of()` collections (`FLUENT_ENTRY_POINT_METHODS`, `FLUENT_INTERMEDIATE_METHODS`, `FLUENT_TERMINAL_METHODS`) categorize methods.
- **Current Pattern**:
```java
// QubitConstants.java - string constants
public static final String METHOD_WHERE = "where";
public static final String METHOD_SELECT = "select";
// ... 10+ more constants

public static final Set<String> FLUENT_ENTRY_POINT_METHODS = Set.of(
    METHOD_WHERE, METHOD_SELECT, METHOD_SORTED_BY, ...);

// QubitRepositoryEnhancer.java - string switch
QubitBytecodeGenerator.FluentMethodConfig config = switch (methodName) {
    case METHOD_WHERE -> FluentMethodConfig.forWhere(...);
    case METHOD_SELECT -> FluentMethodConfig.forSelect(...);
    // ... 10 cases
    default -> null;
};
```
- **Suggested Fix**: Create `FluentMethodType` enum with behavior:
```java
public enum FluentMethodType {
    WHERE("where", MethodCategory.ENTRY_POINT) {
        @Override
        public FluentMethodConfig createConfig(Type entityType, String internalName) {
            return FluentMethodConfig.forWhere(entityType, internalName);
        }
    },
    SELECT("select", MethodCategory.ENTRY_POINT) {
        @Override
        public FluentMethodConfig createConfig(Type entityType, String internalName) {
            return FluentMethodConfig.forSelect(entityType, internalName);
        }
    },
    // ... other values

    private final String methodName;
    private final MethodCategory category;

    public abstract FluentMethodConfig createConfig(Type entityType, String internalName);

    public static Optional<FluentMethodType> fromMethodName(String name) {
        return Arrays.stream(values())
            .filter(m -> m.methodName.equals(name))
            .findFirst();
    }

    public enum MethodCategory { ENTRY_POINT, INTERMEDIATE, TERMINAL }
}
```
- **Benefits**:
  - Eliminates 10+ string constants from `QubitConstants`
  - Compile-time exhaustiveness checking in switch expressions
  - Behavior attached to enum values (Strategy pattern)
  - `EnumSet` replaces `Set.of()` for category membership (faster, type-safe)
  - Single source of truth for method metadata

### ENUM-002: Create `TemporalAccessorMethod` Enum
- **Files**: [TemporalExpressionBuilder.java:38-68](deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/TemporalExpressionBuilder.java#L38-L68), [QubitConstants.java:174-179](runtime/src/main/java/io/quarkiverse/qubit/runtime/QubitConstants.java#L174-L179)
- **Severity**: Low
- **Priority**: Medium
- **Description**: Temporal accessor methods mapped to SQL functions via string constants and switch statement.
- **Current Pattern**:
```java
// QubitConstants.java
public static final String METHOD_GET_YEAR = "getYear";
public static final String SQL_YEAR = "YEAR";
// ... 6 pairs

// TemporalExpressionBuilder.java
private static final Set<String> TEMPORAL_ACCESSOR_METHODS = Set.of(
    METHOD_GET_YEAR, METHOD_GET_MONTH_VALUE, ...);

public static String mapTemporalAccessorToSqlFunction(String methodName) {
    return switch (methodName) {
        case METHOD_GET_YEAR -> SQL_YEAR;
        case METHOD_GET_MONTH_VALUE -> SQL_MONTH;
        // ... 6 cases
    };
}
```
- **Suggested Fix**: Create `TemporalAccessorMethod` enum:
```java
public enum TemporalAccessorMethod {
    GET_YEAR("getYear", "YEAR"),
    GET_MONTH_VALUE("getMonthValue", "MONTH"),
    GET_DAY_OF_MONTH("getDayOfMonth", "DAY"),
    GET_HOUR("getHour", "HOUR"),
    GET_MINUTE("getMinute", "MINUTE"),
    GET_SECOND("getSecond", "SECOND");

    private final String javaMethod;
    private final String sqlFunction;

    public String toSqlFunction() { return sqlFunction; }

    public static Optional<TemporalAccessorMethod> fromJavaMethod(String name) {
        return Arrays.stream(values())
            .filter(m -> m.javaMethod.equals(name))
            .findFirst();
    }
}
```
- **Benefits**: Eliminates 12 string constants, type-safe mapping, single source of truth.

### ENUM-003: Create `ExecutorType` Enum for QueryExecutorRegistry
- **File**: [QueryExecutorRegistry.java:21-30](runtime/src/main/java/io/quarkiverse/qubit/runtime/QueryExecutorRegistry.java#L21-L30)
- **Severity**: Medium
- **Priority**: Medium
- **Description**: Ten separate `ConcurrentHashMap` instances for different executor types with repetitive registration/execution methods.
- **Current Pattern**:
```java
private static final Map<String, QueryExecutor<List<?>>> LIST_EXECUTORS = new ConcurrentHashMap<>();
private static final Map<String, QueryExecutor<Long>> COUNT_EXECUTORS = new ConcurrentHashMap<>();
private static final Map<String, QueryExecutor<?>> AGGREGATION_EXECUTORS = new ConcurrentHashMap<>();
private static final Map<String, QueryExecutor<List<?>>> JOIN_LIST_EXECUTORS = new ConcurrentHashMap<>();
// ... 6 more maps

// Then 10 pairs of register/execute methods with nearly identical code
```
- **Suggested Fix**: Create `ExecutorType` enum with type tokens:
```java
public enum ExecutorType {
    LIST(new TypeToken<QueryExecutor<List<?>>>() {}),
    COUNT(new TypeToken<QueryExecutor<Long>>() {}),
    AGGREGATION(new TypeToken<QueryExecutor<?>>() {}),
    JOIN_LIST(new TypeToken<QueryExecutor<List<?>>>() {}),
    JOIN_COUNT(new TypeToken<QueryExecutor<Long>>() {}),
    JOIN_SELECT_JOINED(new TypeToken<QueryExecutor<List<?>>>() {}),
    JOIN_PROJECTION(new TypeToken<QueryExecutor<List<?>>>() {}),
    GROUP_LIST(new TypeToken<QueryExecutor<List<?>>>() {}),
    GROUP_COUNT(new TypeToken<QueryExecutor<Long>>() {});

    // Single unified map
    private static final Map<ExecutorType, Map<String, QueryExecutor<?>>> EXECUTORS =
        new EnumMap<>(ExecutorType.class);

    static {
        for (ExecutorType type : values()) {
            EXECUTORS.put(type, new ConcurrentHashMap<>());
        }
    }

    public static void register(ExecutorType type, String callSiteId, QueryExecutor<?> executor) {
        EXECUTORS.get(type).put(callSiteId, executor);
    }
}
```
- **Benefits**:
  - `EnumMap` provides O(1) lookup with minimal memory overhead (array-backed)
  - Reduces 10 static fields to 1
  - Eliminates ~300 lines of repetitive registration/execution code
  - Easier to add new executor types

### ENUM-004: Create `SubqueryMethod` Enum
- **Files**: [SubqueryAnalyzer.java:133-145](deployment/src/main/java/io/quarkiverse/qubit/deployment/analysis/instruction/SubqueryAnalyzer.java#L133-L145), [QubitConstants.java:101-115](runtime/src/main/java/io/quarkiverse/qubit/runtime/QubitConstants.java#L101-L115)
- **Severity**: Low
- **Priority**: Low
- **Description**: Subquery method dispatch uses string constants with switch statement.
- **Current Pattern**:
```java
// QubitConstants.java
public static final String SUBQUERY_AVG = "avg";
public static final String SUBQUERY_EXISTS = "exists";
// ... 9 constants

public static final Set<String> SUBQUERY_METHODS = Set.of(...);

// SubqueryAnalyzer.java
switch (methodName) {
    case METHOD_WHERE -> handleBuilderWhere(...);
    case SUBQUERY_AVG -> handleBuilderScalarSubquery(..., SubqueryAggregationType.AVG, ...);
    // ... 9 cases
}
```
- **Suggested Fix**: Create `SubqueryMethod` enum with handler dispatch:
```java
public enum SubqueryMethod {
    WHERE("where") {
        @Override
        public void handle(SubqueryAnalyzer analyzer, AnalysisContext ctx, ...) {
            analyzer.handleBuilderWhere(ctx, ...);
        }
    },
    AVG("avg", SubqueryAggregationType.AVG, Double.class),
    SUM("sum", SubqueryAggregationType.SUM, Number.class),
    MIN("min", SubqueryAggregationType.MIN, Comparable.class),
    MAX("max", SubqueryAggregationType.MAX, Comparable.class),
    COUNT("count", SubqueryAggregationType.COUNT, Long.class),
    EXISTS("exists", false),
    NOT_EXISTS("notExists", true),
    IN("in", false),
    NOT_IN("notIn", true);

    private final String methodName;

    public static Optional<SubqueryMethod> fromName(String name) { ... }
}
```
- **Benefits**: Type-safe dispatch, eliminates 9 string constants, compile-time exhaustiveness.

### ENUM-005: Missing EnumMap/EnumSet Usage
- **Severity**: Low
- **Priority**: Low (optimization)
- **Description**: Codebase uses `HashMap` and `Set.of()` where `EnumMap`/`EnumSet` would be more efficient.
- **Current Pattern**:
```java
// ControlFlowAnalyzer.java:60
Map<LabelNode, LabelClassification> classifications = new HashMap<>();

// Various files
private static final Set<String> STRING_PATTERN_METHODS = Set.of(...);
```
- **Suggested Improvements**:
  1. Where keys are enum values, use `EnumMap` instead of `HashMap`
  2. For enum membership checks, use `EnumSet` instead of `Set.of()`
- **Benefits**:
  - `EnumMap`: Array-backed, O(1) access, cache-friendly, ~3x faster than HashMap for enum keys
  - `EnumSet`: Bit-vector implementation, extremely compact, O(1) operations
- **Example**:
```java
// Instead of:
private static final Set<String> TEMPORAL_ACCESSOR_METHODS = Set.of(...);

// With enum:
private static final EnumSet<TemporalAccessorMethod> TEMPORAL_ACCESSOR_METHODS =
    EnumSet.allOf(TemporalAccessorMethod.class);
```

### ENUM-006: Behavior-Rich Enums for Expression Builders
- **Files**: [StringExpressionBuilder.java](deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/StringExpressionBuilder.java)
- **Severity**: Low
- **Priority**: Low (design improvement)
- **Description**: `StringOperationType` enum exists but doesn't carry behavior. Method dispatch still uses string switches.
- **Current Pattern**:
```java
public enum StringOperationType {
    TRANSFORMATION,  // toLowerCase, toUpperCase, trim
    PATTERN,         // startsWith, endsWith, contains
    SUBSTRING,       // substring
    UTILITY          // equals, length, isEmpty
}

// Separate sets for categorization
private static final Set<String> STRING_PATTERN_METHODS = Set.of(...);
private static final Set<String> STRING_TRANSFORMATION_METHODS = Set.of(...);
```
- **Suggested Fix**: Create behavior-rich enum with method mapping:
```java
public enum StringMethod {
    TO_LOWER_CASE("toLowerCase", StringOperationType.TRANSFORMATION, CB_LOWER),
    TO_UPPER_CASE("toUpperCase", StringOperationType.TRANSFORMATION, CB_UPPER),
    TRIM("trim", StringOperationType.TRANSFORMATION, CB_TRIM),
    STARTS_WITH("startsWith", StringOperationType.PATTERN, "%s%%"),
    ENDS_WITH("endsWith", StringOperationType.PATTERN, "%%%s"),
    CONTAINS("contains", StringOperationType.PATTERN, "%%%s%%"),
    SUBSTRING("substring", StringOperationType.SUBSTRING, null),
    EQUALS("equals", StringOperationType.UTILITY, CB_EQUAL),
    LENGTH("length", StringOperationType.UTILITY, CB_LENGTH),
    IS_EMPTY("isEmpty", StringOperationType.UTILITY, null);

    private final String methodName;
    private final StringOperationType category;
    private final String criteriaBuilderMethod;

    public StringOperationType getCategory() { return category; }

    public static Optional<StringMethod> fromName(String name) { ... }

    public static EnumSet<StringMethod> byCategory(StringOperationType type) {
        return Arrays.stream(values())
            .filter(m -> m.category == type)
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(StringMethod.class)));
    }
}
```
- **Benefits**: Single source of truth, compile-time safety, EnumSet for category filtering.

### Summary: Enum Improvement Priorities

| ID | Priority | Impact | Effort | Description |
|----|----------|--------|--------|-------------|
| ENUM-001 | High | High | Medium | FluentMethodType - affects multiple files, high repetition |
| ENUM-003 | Medium | High | Medium | ExecutorType - eliminates 10 maps, ~300 lines |
| ENUM-002 | Medium | Medium | Low | TemporalAccessorMethod - simple 1:1 mapping |
| ENUM-004 | Low | Medium | Low | SubqueryMethod - 9 method dispatch |
| ENUM-005 | Low | Low | Low | EnumMap/EnumSet usage - optimization |
| ENUM-006 | Low | Low | Medium | Behavior-rich StringMethod - design improvement |

---

## Bug Risks

### BR-001: Potential ArrayIndexOutOfBoundsException ✅ RESOLVED
- **File**: [AnalysisContext.java:512-519](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/AnalysisContext.java#L512-L519)
- **Severity**: High
- **Status**: ✅ **RESOLVED** (Phase 1)
- **Description**: Array element tracking doesn't validate index bounds.
- **Fix Applied**:
  - Changed silent ignore to `IllegalStateException` when called outside array creation mode
  - Added `@throws` documentation

### BR-002: Race Condition in queryCounter
- **File**: [CallSiteProcessor.java:327-328](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/CallSiteProcessor.java#L327-L328)
- **Severity**: High
- **Description**: `AtomicInteger` used but class name generation could still collide in edge cases.
- **Suggested Fix**: Include additional uniqueness factor (timestamp, hash).

### BR-003: Null Check After Dereference
- **File**: [SubqueryExpressionBuilder.java:347-349](deployment/src/main/java/io/quarkus/qubit/deployment/generation/builders/SubqueryExpressionBuilder.java#L347-L349)
- **Severity**: High
- **Description**: Null check comes after potential dereference in some code paths.
- **Suggested Fix**: Move null checks to method entry.

### BR-004: Missing Validation for PathSegment
- **File**: [LambdaExpression.java:271-288](deployment/src/main/java/io/quarkus/qubit/deployment/LambdaExpression.java#L271-L288)
- **Severity**: Medium
- **Description**: `PathSegment` doesn't validate that `fieldName` is not empty.
- **Suggested Fix**: Add validation in compact constructor.

### BR-005: GroupKeyReference Allows Null keyExpression
- **File**: [LambdaExpression.java:624-633](deployment/src/main/java/io/quarkus/qubit/deployment/LambdaExpression.java#L624-L633)
- **Severity**: Medium
- **Description**: `keyExpression` can be null by design, but this may cause NPE in code generation.
- **Suggested Fix**: Add null-safety documentation and handling.

### BR-006: Unchecked Cast in generateConstant()
- **File**: [SubqueryExpressionBuilder.java:458-476](deployment/src/main/java/io/quarkus/qubit/deployment/generation/builders/SubqueryExpressionBuilder.java#L458-L476)
- **Severity**: Medium
- **Description**: Multiple instanceof checks but no else branch for unknown types.
- **Suggested Fix**: Add validation or throw for unsupported types.
- **Related**: Pattern matching refactoring (like MAINT-011) would address this with exhaustive switch

### BR-007: Missing Bounds Check in readArrayValue
- **Files**: Various code generation files
- **Severity**: Medium
- **Description**: Array access without explicit bounds checking.
- **Suggested Fix**: Add validation before array access.

### BR-008: Potential Integer Overflow in Index Calculation
- **File**: [CallSiteProcessor.java:554-576](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/CallSiteProcessor.java#L554-L576)
- **Severity**: Low
- **Description**: `indexOffset` accumulation could overflow for very large lambda chains.
- **Suggested Fix**: Use long or add overflow check.

### BR-009: Thread Safety of classMethods Field
- **File**: [AnalysisContext.java:102](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/AnalysisContext.java#L102)
- **Severity**: Low
- **Description**: `classMethods` is set after construction without synchronization.
- **Suggested Fix**: Document thread-safety requirements or make immutable.

---

## Documentation Gaps

### DOC-001: Missing Package-Level Documentation ✅ RESOLVED
- **Severity**: High
- **Status**: ✅ **RESOLVED** (as part of ARCH-008)
- **Description**: No `package-info.java` files explaining package purposes.
- **Fix Applied**: Added `package-info.java` with comprehensive Javadoc for all 9 packages:
  - `io.quarkiverse.qubit.deployment` - Main deployment package overview
  - `io.quarkiverse.qubit.deployment.ast` - AST node type definitions
  - `io.quarkiverse.qubit.deployment.analysis` - Bytecode analysis
  - `io.quarkiverse.qubit.deployment.analysis.instruction` - Instruction handlers
  - `io.quarkiverse.qubit.deployment.analysis.branch` - Branch handlers
  - `io.quarkiverse.qubit.deployment.generation` - Code generation
  - `io.quarkiverse.qubit.deployment.generation.expression` - Expression builders
  - `io.quarkiverse.qubit.deployment.common` - Shared utilities
  - `io.quarkiverse.qubit.deployment.util` - Low-level utilities
- **See Also**: → **ARCH-008** for full module boundary refactoring

### DOC-002: Incomplete Javadoc on Public API
- **Severity**: High
- **Files**:
  - `QubitStream.java`: Some methods missing parameter documentation
  - `QubitEntity.java`: Missing class-level design rationale
- **Suggested Fix**: Complete Javadoc for all public methods.

### DOC-003: Missing Error Handling Documentation
- **Severity**: Medium
- **Description**: Exception conditions not documented in method signatures.
- **Suggested Fix**: Add `@throws` documentation.

### DOC-004: Undocumented Design Decisions
- **Severity**: Medium
- **Description**: Why certain patterns were chosen not explained.
- **Examples**:
  - Why sealed interface for `LambdaExpression`?
  - Why strategy pattern for handlers?
- **Suggested Fix**: Add design rationale in class-level Javadoc.

### DOC-005: Missing Examples in Complex Methods
- **Severity**: Medium
- **Files**: Various bytecode generation methods
- **Suggested Fix**: Add `@example` or code samples in Javadoc.

### DOC-006: Iteration Comments Without Context
- **Severity**: Medium
- **Description**: Comments like "Iteration 7:" appear without explaining iteration scope.
- **Suggested Fix**: Add CHANGELOG or iteration documentation.

### DOC-007: Missing Configuration Documentation
- **Severity**: Low
- **Description**: No documentation of configurable options.
- **Suggested Fix**: Add configuration reference document.

### DOC-008: Incomplete README
- **Severity**: Low
- **Description**: Missing development setup, architecture overview.
- **Suggested Fix**: Enhance README with contributor guide.

---

## Performance Optimizations

### PERF-001: Repeated MethodDescriptor Creation
- **Severity**: High
- **Files**: Various generation files
- **Description**: `MethodDescriptor.ofMethod()` called repeatedly with same arguments.
- **Example**:
```java
// Called many times with same arguments
MethodDescriptor.ofMethod(CriteriaBuilder.class, "equal", Predicate.class, Expression.class, Object.class)
```
- **Suggested Fix**: Cache common `MethodDescriptor` instances as static finals.

### PERF-002: Unnecessary List Copies
- **File**: [LambdaExpression.java](deployment/src/main/java/io/quarkus/qubit/deployment/LambdaExpression.java)
- **Severity**: Medium
- **Description**: `List.copyOf()` in record constructors creates defensive copies even when input is already immutable.
- **Suggested Fix**: Check if input is already unmodifiable list.

### PERF-003: StringBuilder Usage
- **Files**: Hash computation methods
- **Severity**: Medium
- **Description**: String concatenation in loops instead of StringBuilder.
- **Suggested Fix**: Use StringBuilder for hash building.

### PERF-004: Collection Pre-sizing
- **Files**: Various
- **Severity**: Low
- **Description**: ArrayList created without initial capacity when size is known.
- **Suggested Fix**: Pre-size collections when size is predictable.

### PERF-005: Unnecessary Boxing
- **Files**: Various
- **Severity**: Low
- **Description**: Primitive values boxed unnecessarily in some paths.
- **Suggested Fix**: Use primitive-specialized methods where available.

---

## Maintainability Improvements

### MAINT-001: Extract Subquery Analysis ✅ RESOLVED
- **File**: [MethodInvocationHandler.java](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/MethodInvocationHandler.java)
- **Severity**: High
- **Status**: ✅ **RESOLVED**
- **Description**: Subquery handling (200+ lines) should be separate class.
- **Fix Applied**:
  - Created [SubqueryAnalyzer.java](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/SubqueryAnalyzer.java) (329 lines)
  - Extracted: `isSubqueriesMethodCall()`, `isSubqueryBuilderMethodCall()`, `handleSubqueriesFactoryMethod()`, `handleSubqueryBuilderMethod()`, and all helper methods
  - MethodInvocationHandler now delegates to SubqueryAnalyzer
- **Also Resolved**:
  - → **CS-004** (handleSubqueryBuilderMethod is now in SubqueryAnalyzer)
  - → Partially addresses **ARCH-001** (reduces MethodInvocationHandler class size by ~280 lines)

### MAINT-002: Extract Group Analysis ✅ RESOLVED
- **File**: [MethodInvocationHandler.java](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/MethodInvocationHandler.java)
- **Severity**: High
- **Status**: ✅ **RESOLVED**
- **Description**: Group method handling should be separate class.
- **Fix Applied**:
  - Created [GroupMethodAnalyzer.java](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/GroupMethodAnalyzer.java) (183 lines)
  - Extracted: `isGroupMethodCall()`, `handleGroupMethod()`, `handleGroupKey()`, `handleGroupCount()`, `handleGroupCountDistinct()`, `handleGroupAggregationWithField()`, `handleGroupMinMax()`, `inferFieldType()`
  - MethodInvocationHandler now delegates to GroupMethodAnalyzer
- **Also Resolved**: → Partially addresses **ARCH-001** (reduces MethodInvocationHandler class size by ~150 lines)

### MAINT-003: Reduce Cyclomatic Complexity
- **Files**:
  - `generateBinaryOperation()`: Many if-else branches
  - `handleInvokeVirtual()`: Multiple dispatch paths
- **Severity**: High
- **Suggested Fix**: Use polymorphism or visitor pattern.
- **Addressed By**: Pattern matching items **MAINT-009 through MAINT-017** reduce cyclomatic complexity by converting if-else instanceof chains to exhaustive switch expressions

### MAINT-004: Constants Consolidation
- **File**: [QubitConstants.java](runtime/src/main/java/io/quarkus/qubit/runtime/QubitConstants.java)
- **Severity**: Medium
- **Description**: Some constants defined locally instead of in central constants file.
- **Suggested Fix**: Audit and consolidate all constants.

### MAINT-005: Consistent Error Messages
- **Severity**: Medium
- **Description**: Error messages vary in format and detail level.
- **Suggested Fix**: Create error message templates.

### MAINT-006: Method Extraction Candidates
- **Severity**: Medium
- **Locations**:
  - `analyzeLambdas()`: 30+ lines, multiple concerns
  - `processCallSite()`: 90+ lines (also covered by **CS-005**)
- **Suggested Fix**: Extract to smaller focused methods, use early returns to reduce nesting.

### MAINT-007: Test Data Builders
- **Severity**: Medium
- **Description**: Test setup code could benefit from builders.
- **Suggested Fix**: Create test data builder utilities.

### MAINT-008: Assertion Messages
- **Severity**: Low
- **Description**: Some assertions lack descriptive messages.
- **Suggested Fix**: Add context to assertion messages.

### MAINT-009: Pattern Matching in generatePredicate() ✅ RESOLVED
- **File**: [CriteriaExpressionGenerator.java:122-147](deployment/src/main/java/io/quarkus/qubit/deployment/generation/CriteriaExpressionGenerator.java#L122-L147)
- **Severity**: Medium
- **Status**: ✅ **RESOLVED** (Phase 4)
- **Description**: 7 consecutive `if-else instanceof` branches for expression type dispatch.
- **Current Code**:
```java
if (expression instanceof LambdaExpression.BinaryOp binOp) {
    return generateBinaryOperation(...);
} else if (expression instanceof LambdaExpression.UnaryOp unOp) {
    return generateUnaryOperation(...);
} else if (expression instanceof LambdaExpression.FieldAccess field) {
    // ...
} // 4 more branches
```
- **Suggested Fix**: Refactor using Java 21 switch pattern matching:
```java
return switch (expression) {
    case LambdaExpression.BinaryOp binOp -> generateBinaryOperation(...);
    case LambdaExpression.UnaryOp unOp -> generateUnaryOperation(...);
    case LambdaExpression.FieldAccess field -> // ...
    case PathExpression pathExpr -> // ...
    case LambdaExpression.MethodCall methodCall -> generateMethodCall(...);
    case InExpression inExpr -> generateInPredicate(...);
    case MemberOfExpression memberOfExpr -> generateMemberOfPredicate(...);
    case null, default -> null;
};
```
- **Benefits**: Exhaustiveness checking by compiler, cleaner syntax, easier to add new cases.

### MAINT-010: Pattern Matching in generateExpressionAsJpaExpression() ✅ RESOLVED
- **File**: [CriteriaExpressionGenerator.java:350-388](deployment/src/main/java/io/quarkus/qubit/deployment/generation/CriteriaExpressionGenerator.java#L350-L388)
- **Severity**: High
- **Status**: ✅ **RESOLVED** (Phase 4)
- **Description**: 11 consecutive `if-else instanceof` branches handling all expression types.
- **Current Code**:
```java
if (expression instanceof LambdaExpression.FieldAccess field) {
    return generateFieldAccess(...);
} else if (expression instanceof PathExpression pathExpr) {
    return generatePathExpression(...);
} // 9 more branches
return null;
```
- **Suggested Fix**: Refactor using Java 21 switch pattern matching:
```java
return switch (expression) {
    case LambdaExpression.FieldAccess field -> generateFieldAccess(...);
    case PathExpression pathExpr -> generatePathExpression(...);
    case LambdaExpression.Constant constant -> wrapAsLiteral(method, cb, generateConstant(...));
    case LambdaExpression.CapturedVariable capturedVar -> // ...
    case LambdaExpression.MethodCall methodCall -> generateMethodCall(...);
    case LambdaExpression.BinaryOp binOp -> generateBinaryOperation(...);
    case LambdaExpression.ConstructorCall constructorCall -> generateConstructorCall(...);
    case LambdaExpression.Parameter _ -> null;  // Identity sort functions
    case InExpression inExpr -> generateInPredicate(...);
    case MemberOfExpression memberOfExpr -> generateMemberOfPredicate(...);
    case LambdaExpression.UnaryOp unaryOp -> generateUnaryOperation(...);
    case null, default -> null;
};
```
- **Benefits**: Single expression, no fallthrough risk, compiler-enforced exhaustiveness.

### MAINT-011: Pattern Matching in generateConstant() ✅ RESOLVED
- **File**: [CriteriaExpressionGenerator.java:645-690](deployment/src/main/java/io/quarkus/qubit/deployment/generation/CriteriaExpressionGenerator.java#L645-L690)
- **Severity**: Medium
- **Status**: ✅ **RESOLVED** (Phase 4)
- **Description**: 11 consecutive `if-else instanceof` branches for value type dispatch.
- **Current Code**:
```java
if (value == null) {
    return method.loadNull();
} else if (value instanceof String s) {
    return method.load(s);
} else if (value instanceof Integer i) {
    return method.load(i);
} // 8 more branches including BigDecimal, LocalDate, LocalDateTime, LocalTime
```
- **Suggested Fix**: Refactor using Java 21 switch pattern matching:
```java
return switch (value) {
    case null -> method.loadNull();
    case String s -> method.load(s);
    case Integer i -> method.load(i);
    case Long l -> method.load(l);
    case Boolean b -> method.load(b);
    case Double d -> method.load(d);
    case Float f -> method.load(f);
    case BigDecimal bd -> method.newInstance(constructorDescriptor(BigDecimal.class, String.class), method.load(bd.toString()));
    case LocalDate ld -> method.invokeStaticMethod(methodDescriptor(LocalDate.class, METHOD_OF, ...));
    case LocalDateTime ldt -> // ...
    case LocalTime lt -> // ...
    default -> method.loadNull();
};
```
- **Benefits**: Cleaner handling of null case, exhaustive type checking, easier maintenance.

### MAINT-012: Pattern Matching in collectCapturedVariableIndices() ✅ RESOLVED
- **File**: [CallSiteProcessor.java:1210-1270](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/CallSiteProcessor.java#L1210-L1270)
- **Severity**: High
- **Status**: ✅ **RESOLVED** (Phase 4)
- **Description**: 12+ consecutive `if-else instanceof` branches for AST visitor traversal.
- **Current Code**:
```java
if (expression instanceof LambdaExpression.CapturedVariable capturedVar) {
    capturedIndices.add(capturedVar.index());
} else if (expression instanceof LambdaExpression.BinaryOp binOp) {
    collectCapturedVariableIndices(binOp.left(), capturedIndices);
    collectCapturedVariableIndices(binOp.right(), capturedIndices);
} else if (expression instanceof LambdaExpression.UnaryOp unaryOp) {
    // ...
} // 9 more branches
```
- **Suggested Fix**: Refactor using Java 21 switch pattern matching:
```java
switch (expression) {
    case null -> { /* no-op */ }
    case LambdaExpression.CapturedVariable capturedVar -> capturedIndices.add(capturedVar.index());
    case LambdaExpression.BinaryOp binOp -> {
        collectCapturedVariableIndices(binOp.left(), capturedIndices);
        collectCapturedVariableIndices(binOp.right(), capturedIndices);
    }
    case LambdaExpression.UnaryOp unaryOp -> collectCapturedVariableIndices(unaryOp.operand(), capturedIndices);
    case LambdaExpression.MethodCall methodCall -> {
        collectCapturedVariableIndices(methodCall.target(), capturedIndices);
        methodCall.arguments().forEach(arg -> collectCapturedVariableIndices(arg, capturedIndices));
    }
    case LambdaExpression.ConstructorCall constructorCall ->
        constructorCall.arguments().forEach(arg -> collectCapturedVariableIndices(arg, capturedIndices));
    case InExpression inExpr -> { /* collect from field and collection */ }
    case MemberOfExpression memberOfExpr -> { /* collect from value and collectionField */ }
    // Leaf nodes that don't contain captured variables:
    case LambdaExpression.PathExpression _, LambdaExpression.BiEntityFieldAccess _,
         LambdaExpression.BiEntityPathExpression _, LambdaExpression.BiEntityParameter _ -> { /* no-op */ }
    default -> { /* no-op for other types */ }
}
```
- **Benefits**: Compiler-enforced exhaustiveness over sealed interface, pattern guards available.

### MAINT-013: Pattern Matching in renumberCapturedVariables() ✅ RESOLVED
- **File**: [CallSiteProcessor.java:1285-1365](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/CallSiteProcessor.java#L1285-L1365)
- **Severity**: High
- **Status**: ✅ **RESOLVED** (Phase 4)
- **Description**: 15+ consecutive `if-else instanceof` branches for AST transformation.
- **Current Code**:
```java
if (expression instanceof LambdaExpression.CapturedVariable capturedVar) {
    return new LambdaExpression.CapturedVariable(capturedVar.index() + offset, capturedVar.type());
} else if (expression instanceof LambdaExpression.BinaryOp binOp) {
    return new LambdaExpression.BinaryOp(
        renumberCapturedVariables(binOp.left(), offset),
        binOp.operator(),
        renumberCapturedVariables(binOp.right(), offset));
} // 13 more branches
```
- **Suggested Fix**: Refactor using Java 21 switch pattern matching:
```java
return switch (expression) {
    case null -> null;
    case LambdaExpression.CapturedVariable cv -> new LambdaExpression.CapturedVariable(cv.index() + offset, cv.type());
    case LambdaExpression.BinaryOp bin -> new LambdaExpression.BinaryOp(
        renumberCapturedVariables(bin.left(), offset), bin.operator(), renumberCapturedVariables(bin.right(), offset));
    case LambdaExpression.UnaryOp un -> new LambdaExpression.UnaryOp(un.operator(), renumberCapturedVariables(un.operand(), offset));
    case LambdaExpression.MethodCall mc -> new LambdaExpression.MethodCall(
        renumberCapturedVariables(mc.target(), offset), mc.methodName(),
        mc.arguments().stream().map(a -> renumberCapturedVariables(a, offset)).toList(), mc.returnType());
    case LambdaExpression.ConstructorCall cc -> new LambdaExpression.ConstructorCall(
        cc.className(), cc.arguments().stream().map(a -> renumberCapturedVariables(a, offset)).toList(), cc.resultType());
    case LambdaExpression.Cast c -> new LambdaExpression.Cast(renumberCapturedVariables(c.expression(), offset), c.targetType());
    case LambdaExpression.InstanceOf io -> new LambdaExpression.InstanceOf(renumberCapturedVariables(io.expression(), offset), io.targetType());
    case LambdaExpression.Conditional cond -> new LambdaExpression.Conditional(
        renumberCapturedVariables(cond.condition(), offset),
        renumberCapturedVariables(cond.trueValue(), offset),
        renumberCapturedVariables(cond.falseValue(), offset));
    case InExpression in -> new InExpression(
        renumberCapturedVariables(in.field(), offset), renumberCapturedVariables(in.collection(), offset), in.negated());
    case MemberOfExpression mo -> new MemberOfExpression(
        renumberCapturedVariables(mo.value(), offset), renumberCapturedVariables(mo.collectionField(), offset), mo.negated());
    // Leaf nodes without captured variables - return as-is
    case LambdaExpression.PathExpression _, LambdaExpression.BiEntityFieldAccess _,
         LambdaExpression.BiEntityPathExpression _, LambdaExpression.BiEntityParameter _,
         LambdaExpression.FieldAccess _, LambdaExpression.Constant _,
         LambdaExpression.Parameter _, LambdaExpression.NullLiteral _ -> expression;
    default -> expression;
};
```
- **Benefits**: Sealed interface ensures exhaustiveness, unified return expression, cleaner code.

### MAINT-014: Pattern Matching in generateSubqueryExpression() ✅ RESOLVED
- **File**: [SubqueryExpressionBuilder.java:457-498](deployment/src/main/java/io/quarkus/qubit/deployment/generation/builders/SubqueryExpressionBuilder.java#L457-L498)
- **Severity**: Medium
- **Status**: ✅ **RESOLVED** (Phase 4)
- **Description**: 5 consecutive `if-else instanceof` branches with null fallback (relates to CRI-002).
- **Current Code**:
```java
if (expr instanceof FieldAccess field) {
    return generateFieldPath(method, field, subRoot);
} else if (expr instanceof PathExpression pathExpr) {
    return generateFieldPath(method, pathExpr, subRoot);
} else if (expr instanceof LambdaExpression.CorrelatedVariable correlated) {
    return generateFieldPath(method, correlated.fieldExpression(), outerRoot);
} else if (expr instanceof LambdaExpression.Constant constant) {
    return generateConstant(method, constant);
} else if (expr instanceof LambdaExpression.CapturedVariable capturedVar) {
    // ...
}
return null;
```
- **Suggested Fix**: Refactor using Java 21 switch pattern matching with improved error handling:
```java
return switch (expr) {
    case FieldAccess field -> generateFieldPath(method, field, subRoot);
    case PathExpression pathExpr -> generateFieldPath(method, pathExpr, subRoot);
    case LambdaExpression.CorrelatedVariable correlated -> generateFieldPath(method, correlated.fieldExpression(), outerRoot);
    case LambdaExpression.Constant constant -> generateConstant(method, constant);
    case LambdaExpression.CapturedVariable capturedVar -> {
        ResultHandle index = method.load(capturedVar.index());
        ResultHandle value = method.readArrayValue(capturedValues, index);
        yield method.checkCast(value, Object.class);
    }
    case null -> null;
    default -> {
        log.warnf("Unhandled expression type in generateSubqueryExpression: %s", expr.getClass().getSimpleName());
        yield null;  // Or throw IllegalArgumentException for stricter handling
    }
};
```
- **Benefits**: Addresses CRI-002 by adding logging for unhandled cases, cleaner structure.

### MAINT-015: Pattern Matching in generateFieldPath() ✅ RESOLVED
- **File**: [SubqueryExpressionBuilder.java:322-350](deployment/src/main/java/io/quarkus/qubit/deployment/generation/builders/SubqueryExpressionBuilder.java#L322-L350)
- **Severity**: High (relates to CRI-001)
- **Status**: ✅ **RESOLVED** (Phase 4)
- **Description**: Silent fallback returning `root` for unrecognized expression types.
- **Current Code**:
```java
if (expr instanceof FieldAccess field) {
    // ...
} else if (expr instanceof PathExpression pathExpr) {
    // ...
}
// Fallback: return root (shouldn't happen for well-formed expressions)
return root;
```
- **Suggested Fix**: Refactor using Java 21 switch pattern matching with explicit error handling:
```java
return switch (expr) {
    case FieldAccess field -> {
        ResultHandle fieldName = method.load(field.fieldName());
        yield method.invokeInterfaceMethod(
            MethodDescriptor.ofMethod(Path.class, PATH_GET, Path.class, String.class), root, fieldName);
    }
    case PathExpression pathExpr -> {
        ResultHandle currentPath = root;
        for (PathSegment segment : pathExpr.segments()) {
            currentPath = method.invokeInterfaceMethod(
                MethodDescriptor.ofMethod(Path.class, PATH_GET, Path.class, String.class),
                currentPath, method.load(segment.fieldName()));
        }
        yield currentPath;
    }
    case null -> throw new IllegalArgumentException("Field path expression cannot be null");
    default -> throw new IllegalArgumentException("Unsupported expression type for field path: " + expr.getClass().getSimpleName());
};
```
- **Benefits**: Addresses CRI-001 by replacing silent fallback with explicit error, compiler exhaustiveness checking.

### MAINT-016: Pattern Matching in inferFieldType() ✅ RESOLVED
- **File**: [GroupMethodAnalyzer.java:170-177](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/GroupMethodAnalyzer.java#L170-L177)
- **Severity**: Low
- **Status**: ✅ **RESOLVED** (Phase 4) - Method moved to GroupMethodAnalyzer during MAINT-002
- **Description**: Simple 2-branch instanceof check for field type inference.
- **Current Code**:
```java
if (fieldExpr instanceof LambdaExpression.FieldAccess field) {
    return field.fieldType();
} else if (fieldExpr instanceof LambdaExpression.PathExpression path) {
    return path.resultType();
}
return Object.class;
```
- **Suggested Fix**: Refactor using Java 21 switch pattern matching:
```java
return switch (fieldExpr) {
    case LambdaExpression.FieldAccess field -> field.fieldType();
    case LambdaExpression.PathExpression path -> path.resultType();
    case null, default -> Object.class;
};
```
- **Benefits**: Concise single expression, consistent with other pattern matching refactors.

### MAINT-017: Pattern Matching in extractEntityClassInfo() ✅ RESOLVED
- **File**: [SubqueryAnalyzer.java:290-315](deployment/src/main/java/io/quarkus/qubit/deployment/analysis/handlers/SubqueryAnalyzer.java#L290-L315)
- **Severity**: Medium
- **Status**: ✅ **RESOLVED** (Phase 4) - Method moved to SubqueryAnalyzer during MAINT-001
- **Description**: Nested instanceof checks for entity class extraction from constants.
- **Current Code**:
```java
if (expr instanceof LambdaExpression.Constant constant) {
    Object value = constant.value();
    if (value instanceof Type asmType) {
        // ...
    } else if (value instanceof Class<?> clazz) {
        return new EntityClassInfo(clazz, null);
    }
}
```
- **Suggested Fix**: Refactor using Java 21 switch pattern matching with nested patterns:
```java
return switch (expr) {
    case LambdaExpression.Constant(Type asmType, _) -> {
        String className = asmType.getClassName();
        Class<?> loadedClass = tryLoadClass(className);
        yield loadedClass != null
            ? new EntityClassInfo(loadedClass, null)
            : new EntityClassInfo(Object.class, className);
    }
    case LambdaExpression.Constant(Class<?> clazz, _) -> new EntityClassInfo(clazz, null);
    case null, default -> {
        log.warnf("Expected Class constant for entity class, got: %s", expr);
        yield new EntityClassInfo(Object.class, null);
    }
};
```
- **Benefits**: Deconstruction patterns for record types, cleaner nested type checking.

---

## Testing Recommendations

### TEST-001: Missing Unit Tests for Edge Cases
- **Priority**: High
- **Areas**:
  - Null handling in all handlers
  - Empty collections
  - Maximum size inputs
  - Invalid bytecode scenarios

### TEST-002: Integration Test Coverage
- **Priority**: High
- **Recommendation**: Add end-to-end tests for:
  - Complex nested subqueries
  - Multi-level joins
  - Combined group/having/select

### TEST-003: Property-Based Testing
- **Priority**: Medium
- **Recommendation**: Consider property-based tests for:
  - AST transformations
  - Expression evaluation

### TEST-004: Benchmark Tests
- **Priority**: Medium
- **Recommendation**: Add performance benchmarks for:
  - Bytecode analysis speed
  - Code generation throughput

### TEST-005: Error Path Testing
- **Priority**: Medium
- **Recommendation**: Test all error conditions and logging.

### TEST-006: Mutation Testing
- **Priority**: Low
- **Recommendation**: Run mutation testing to validate test effectiveness.

---

## Refactoring Roadmap

### Phase 1: Critical Fixes (Immediate) ✅ COMPLETE
1. ~~Fix `CRI-001`: Add proper error handling in `generateFieldPath()`~~ ✅
2. ~~Fix `CRI-002`: Add logging for unhandled expression types~~ ✅
3. ~~Fix `BR-001`: Add array bounds validation~~ ✅

> **Completed**: 2024 - All 1113 tests pass after Phase 1 fixes.

### Phase 2: High-Priority Improvements (Week 1-2)
1. ~~`ARCH-001`: Begin extracting large classes~~
2. ~~`ARCH-002`: Refactor `LambdaAnalysisResult` to sealed interface~~
3. ~~`CS-001`: Consolidate magic strings~~
4. `DOC-001`: Add package documentation

### Phase 3: Medium-Priority Improvements (Week 3-4)
1. ~~`MAINT-001`: Extract `SubqueryAnalysisHandler`~~
2. ~~`MAINT-002`: Extract `GroupMethodHandler`~~
3. `PERF-001`: Cache MethodDescriptor instances
4. Complete Javadoc coverage

### Phase 4: Pattern Matching Modernization ✅ COMPLETE
Applied Java 21 switch pattern matching to replace if-else instanceof chains:
1. ~~**High Priority** (address critical issues)~~:
   - ~~`MAINT-015`: `generateFieldPath()` - fixes CRI-001 silent fallback~~ ✅
   - ~~`MAINT-014`: `generateSubqueryExpression()` - fixes CRI-002 null return~~ ✅
2. ~~**High Priority** (AST traversal methods)~~:
   - ~~`MAINT-012`: `collectCapturedVariableIndices()` - 12+ branches~~ ✅
   - ~~`MAINT-013`: `renumberCapturedVariables()` - 15+ branches~~ ✅
   - ~~`MAINT-010`: `generateExpressionAsJpaExpression()` - 11 branches~~ ✅
3. ~~**Medium Priority** (expression generators)~~:
   - ~~`MAINT-009`: `generatePredicate()` - 7 branches~~ ✅
   - ~~`MAINT-011`: `generateConstant()` - 11 value type branches~~ ✅
   - ~~`MAINT-017`: `extractEntityClassInfo()` - nested patterns~~ ✅
4. ~~**Low Priority** (simple cases)~~:
   - ~~`MAINT-016`: `inferFieldType()` - 2 branches~~ ✅

> **Completed**: 2024-11-28 - Upgraded pom.xml to Java 21, refactored 22 methods across 4 files using pattern matching switch expressions. All 375 deployment tests pass.

### Phase 5: Low-Priority Improvements (Ongoing)
1. Address remaining code smells
2. Add comprehensive tests
3. Performance optimizations
4. Documentation enhancements

---

## Quality Metrics Targets

| Metric | Current (Est.) | Target | Status |
|--------|---------------|--------|--------|
| Max Class Size | ~~1977~~ ~~1355~~ 1119 LOC | < 500 LOC | ✅ **ARCH-001 Complete**: MethodInvocationHandler: 1143→715 (37%), CriteriaExpressionGenerator: 1977→1355 (31%), CallSiteProcessor: 1359→1087 (20%). LambdaExpression (1119 lines) is well-organized sealed interface - acceptable as-is |
| Max Method Size | ~100 LOC | < 30 LOC | Extract focused methods |
| Cyclomatic Complexity | ~~15~~ ~8 | < 10 | ✅ Pattern matching reduced branching significantly |
| Test Coverage | Unknown | > 80% | Add unit/integration tests |
| Javadoc Coverage | ~60% | > 95% | Document public API |
| Critical Issues | ~~2~~ **0** | 0 | ✅ **Phase 1 Complete** |
| High Issues | ~~22~~ ~~13~~ ~~12~~ ~~11~~ **7** | 0 | ✅ MAINT-001/002, ARCH-001, ARCH-002, MAINT-010/012/013/015, CS-001/002/003, ARCH-003/004/005/006, DOC-001 resolved |
| Pattern Matching | ~~9 locations~~ **0** | 0 | ✅ **Phase 4 Complete** - All 9 refactored to Java 21 switch |

---

## Issue Tracking Template

When addressing issues, use this template:

```markdown
## Issue: [ID]

**Status**: [ ] Not Started / [ ] In Progress / [ ] Completed

### Changes Made
- [ ] Code change 1
- [ ] Code change 2

### Testing
- [ ] Unit tests added/updated
- [ ] Integration tests pass
- [ ] Manual testing completed

### Notes
[Any additional context]
```

---

## Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2024 | QUBIT Team | Initial quality analysis |
| 1.1 | 2024-11-28 | Claude | Phase 1 complete: Fixed CRI-001, CRI-002, BR-001 |
| 1.2 | 2024-11-28 | Claude | MAINT-001, MAINT-002: Extracted SubqueryAnalyzer (329 lines) and GroupMethodAnalyzer (183 lines) from MethodInvocationHandler. Reduced from 1143 to 715 lines (37% reduction). All 1113 tests pass. |
| 1.3 | 2024-11-28 | Claude | ARCH-002: Refactored LambdaAnalysisResult from 15-field record to sealed interface with 4 specialized result types: SimpleQueryResult (4 fields), AggregationQueryResult (4 fields), JoinQueryResult (6 fields), GroupQueryResult (6 fields). Uses if-else instanceof pattern matching (Java 17). All 1113 tests pass. |
| 1.4 | 2024-11-28 | Claude | **Phase 4 Complete (MAINT-009 through MAINT-017)**: Upgraded pom.xml from Java 17 to Java 21. Refactored 22 methods across 4 files (CriteriaExpressionGenerator, CallSiteProcessor, SubqueryExpressionBuilder, LoadInstructionHandler) to use Java 21 pattern matching switch expressions. Note: Multi-pattern cases with unnamed `_` require Java 21 preview, so used separate named variables instead. All 375 deployment tests pass. |
| 1.5 | 2024-11-28 | Claude | **CS-001 Complete**: Extracted 11 magic strings from MethodInvocationHandler.java to QubitConstants.java. Added new JVM_* naming convention for JVM internal class names (JVM_JAVA_LANG_STRING, JVM_JAVA_TIME_LOCAL_DATE, etc.). Moved COLLECTION_INTERFACE_OWNERS Set to QubitConstants. All 375 deployment tests pass. |
| 1.6 | 2024-11-28 | Claude | **ARCH-001 Progress (CriteriaExpressionGenerator)**: Extracted BiEntityExpressionBuilder.java (555 lines) and GroupExpressionBuilder.java (411 lines). Created ExpressionGeneratorHelper interface for clean delegation. CriteriaExpressionGenerator reduced from 1977 to 1355 lines (31% reduction). Combined with previous MethodInvocationHandler reduction (37%), two major large classes now significantly reduced. All 375 deployment tests pass. |
| 1.7 | 2024-11-28 | Claude | **ARCH-001 Progress (CallSiteProcessor)**: Extracted LambdaAnalysisResult.java (84 lines) as public sealed interface and CapturedVariableHelper.java (246 lines) with 5 static utility methods. CallSiteProcessor reduced from 1359 to 1087 lines (20% reduction). Three of four large classes now significantly reduced. All 375 deployment tests pass. |
| 1.8 | 2024-11-29 | Claude | **ARCH-001 Substantially Resolved**: Analyzed LambdaExpression.java (1119 lines) - determined to be well-organized sealed interface. File has clear section separators dividing 6 logical groups: Core Expressions, Relationship Navigation, Collection Operations, Join Queries, Grouping Operations, and Subqueries. Extracting to sub-interfaces would break sealed pattern without benefit. All four originally-identified large classes now addressed. Updated summary dashboard: Architectural high issues 2→1, total resolved 27→28. |
| 1.9 | 2024-11-29 | Claude | **ARCH-003 Complete**: Created ExpressionBuilder.java marker interface with comprehensive documentation. Deep analysis found builders fall into 3 categories with fundamentally different APIs: Binary Operators (Arithmetic, Comparison), Method Calls (String, Temporal, BigDecimal), and Higher-Level (BiEntity, Group, Subquery). A functional interface would add complexity without benefit. All 8 builders now implement ExpressionBuilder for type-level documentation and IDE support. All 375 deployment tests pass. Updated: Architectural high 1→0, total 65→64, resolved 28→29. |
| 2.0 | 2024-11-29 | Claude | **ARCH-004 Complete**: Created ExpressionBuilderRegistry.java record for dependency injection. Registry holds all 8 builder instances with null validation. CriteriaExpressionGenerator now has two constructors: no-arg (backward compatible, uses default registry) and registry-accepting (for testing). All builder access via `builderRegistry.builderName()`. Enables mock injection for unit testing. All 375 deployment tests pass. Updated: Architectural medium 5→4, total 64→63, resolved 29→30. |
| 2.1 | 2024-11-29 | Claude | **ARCH-005 Complete**: Created InstructionHandlerRegistry.java record for dependency injection. Registry holds 6 instruction handlers with order preserved (chain of responsibility). LambdaBytecodeAnalyzer now has two constructors: no-arg (backward compatible, uses default registry) and registry-accepting (for testing). Handler access via `handlerRegistry.handlers()`. Enables mock injection for unit testing and custom handler extensibility. All 1113 tests pass. Updated: Architectural medium 4→3, total 63→62, resolved 30→31. |
| 2.2 | 2024-11-29 | Claude | **ARCH-006 Complete**: Refactored AnalysisContext mutable state to use constructor-based immutable configuration. Created NestedLambdaSupport record to bundle classMethods and analyzer. Made groupContextMode and nestedLambdaSupport fields final. Added 4 new constructor overloads. Removed setter methods (setGroupContextMode, setClassMethods, setNestedLambdaAnalyzer). Updated LambdaBytecodeAnalyzer with createNestedLambdaSupport() factory method. Processing state (currentInstructionIndex, hasSeenBranch, pendingArray*) remains mutable as required. All 1113 tests pass. Updated: Architectural medium 3→2, total 62→61, resolved 31→32. |
| 2.3 | 2024-11-29 | Claude | **ARCH-009 Complete**: Added 20 factory methods to 5 AST node types in LambdaExpression.java for consistent creation patterns. BinaryOp: 13 methods (logical: and/or, comparison: eq/ne/lt/le/gt/ge, arithmetic: add/sub/mul/div/mod). UnaryOp: not() method. PathExpression: single()/field() for single-segment paths. BiEntityFieldAccess: fromFirst()/fromSecond() for entity-specific access. BiEntityPathExpression: fromFirst()/fromSecond() for entity-specific paths. All 375 deployment tests pass. Updated: Architectural low 3→2, total 61→60, resolved 32→33. |
| 2.4 | 2024-11-29 | Claude | **ARCH-009 Usage Complete**: Refactored 9 files to use BinaryOp factory methods instead of direct constructor calls. Files updated: LambdaBytecodeAnalyzer (and), ArithmeticInstructionHandler (add/sub/mul/div/mod/and/or), BranchHandler (and/or), MethodInvocationHandler (eq), CapturedVariableHelper (and), IfEqualsZeroInstructionHandler (ne/eq), IfNotEqualsZeroInstructionHandler (eq), SubqueryAnalyzer (and), InvokeDynamicHandler (add). Removed unused operator constant imports from all files. All 1488 tests pass (375 deployment + 1113 integration). |
| 2.5 | 2025-11-29 | Claude | **ARCH-008 Complete + DOC-001 Complete**: Full module boundary refactoring. Created `ast/` package (LambdaExpression), moved `InvokeDynamicScanner` to `analysis/`, created `common/` package (PatternDetector, BytecodeValidator, BytecodeAnalysisException, BytecodeAnalysisConstants), flattened `branch/handlers/` into `branch/`, renamed `handlers/` to `instruction/` and `builders/` to `expression/`, removed orphaned BytecodeInstructionHandler.java. Added `package-info.java` for all 9 packages (resolves DOC-001). Updated: Architectural low 2→1, Documentation high 2→1, total 59→58, resolved 34→35. All tests pass. |
| 2.6 | 2025-12-01 | Claude | **CS-002 Complete**: Refactored `tryLoadClass()` in ClassLoaderHelper.java from nested try-catch blocks to classloader list iteration pattern. Uses `initialize=false` consistently for build-time safety. Added null check for context classloader. Updated: Code Smells high 2→1, total 58→57, resolved 35→36. All 1113 tests pass. |
| 2.7 | 2025-12-01 | Claude | **CS-003 Partial**: Added JSpecify `@Nullable` annotations to 5 key files with null-returning methods: AnalysisContext.java (11 annotations), ClassLoaderHelper.java (2), LambdaBytecodeAnalyzer.java (9), CriteriaExpressionGenerator.java (5), CallSiteProcessor.java (8). JSpecify 1.0.0 already transitive dep, `@NullMarked` already on all 9 packages (ARCH-008). With @NullMarked, unannotated types are @NonNull by default. ~10 more files have return null statements for future work. All 375 deployment tests + full integration tests pass. |
| 2.8 | 2025-12-01 | Claude | **CS-003 Complete**: Extended null safety audit to cover record nullable fields. Added @Nullable to 4 records with nullable fields: InvokeDynamicScanner.LambdaCallSite (18 nullable fields including projectionLambdaMethodName, aggregationLambdaMethodName, joinType, groupByLambdaMethodName, etc.), PatternDetector.BranchPatternAnalysis (top field), EntityClassInfo (className field), RelationshipMetadataExtractor.FieldRelationship (mappedBy field). All tests pass. IDE null-safety warnings eliminated. |
| 2.9 | 2025-12-01 | Claude | **CS-003 Full Audit Complete**: Comprehensive null safety audit verified 21 files have @Nullable annotations, all 9 packages have @NullMarked, 18 files with `return null;` statements properly annotated. Updated summary dashboard: Code Smells high 1→0, total 57→56, resolved 36→37. All high-severity code smells (CS-001, CS-002, CS-003) now resolved. |
| 3.0 | 2025-12-04 | Claude | **CS-003 Reverted/Deferred**: JSpecify null-safety annotations reverted due to VSCode JDT.LS compatibility issues. External annotations (.eea files) for Gizmo, ASM, and Jandex cannot be loaded - VSCode reports "Invalid external annotation path" for any path format. Without EEA support, 700+ warnings from third-party library interop cannot be suppressed. EEA files preserved in `.eea/` for future Eclipse IDE use. Updated: Code Smells high 0→1, total 56→57, resolved 37→36. |
| 3.1 | 2025-12-04 | Claude | **Enum and Type-Safety Analysis Complete**: Added new section documenting 6 opportunities for enum-based improvements. Catalogued 13 existing enums. Identified: ENUM-001 (FluentMethodType - High priority, eliminates 10+ string constants), ENUM-002 (TemporalAccessorMethod - 6 Java→SQL mappings), ENUM-003 (ExecutorType - consolidates 10 ConcurrentHashMaps into EnumMap), ENUM-004 (SubqueryMethod - 9 dispatch methods), ENUM-005 (EnumMap/EnumSet usage opportunities), ENUM-006 (behavior-rich StringMethod). Added new category to Summary Dashboard: 2 Medium, 4 Low = 6 total. Updated total issues: 57→63. |
| 3.2 | 2025-12-04 | Claude | **CS-006 Complete**: Created `QueryCharacteristics` record to replace 6 boolean parameters in `handleDuplicateLambda()`. Record bundles isCountQuery, isAggregationQuery, isJoinQuery, isSelectJoined, isJoinProjection, isGroupQuery flags. Added 9 factory methods (forList, forCount, forAggregation, forJoinList, forJoinCount, forSelectJoined, forJoinProjection, forGroupList, forGroupCount) and fromCallSite() extractor. Updated LambdaDeduplicator (11→6 parameters), QueryTransformationBuildItem (6→3 constructors), and CallSiteProcessor call sites. Updated: Code Smells medium 12→11, total 63→62, resolved 36→37. All 1,113 tests pass. |
| 3.3 | 2025-12-04 | Claude | **CS-007 N/A (Clean Codebase)**: Comprehensive investigation for commented-out code blocks using 15+ grep patterns across deployment and runtime modules. Searched for: commented return/if/for/while/try statements, commented method calls with semicolons, commented variable assignments, block comments with code structures, debug logging comments, TODO/FIXME markers, consecutive comment blocks. **Finding**: No actual commented-out code blocks found. All matches were legitimate explanatory comments (Javadoc examples, operation descriptions like `// cb.concat(left, right)`, section separators). Issue marked as N/A - codebase maintains clean comment hygiene. Updated: Code Smells medium 11→10, total 62→61, resolved 37→38. |
| 3.4 | 2025-12-04 | Claude | **CS-008 Complete**: Added default cases to 4 exhaustive enum switches for future-proofing. Files modified: ControlFlowAnalyzer.java (LabelClassification enum), SubqueryExpressionBuilder.java (2 SubqueryAggregationType switches), GroupExpressionBuilder.java (GroupAggregationType enum). All default cases throw IllegalStateException with descriptive message. Benefits: fail-fast behavior if new enum values added, defensive programming, consistent pattern. Updated: Code Smells medium 10→9, total 61→60, resolved 38→39. All 1,113 tests pass. |

