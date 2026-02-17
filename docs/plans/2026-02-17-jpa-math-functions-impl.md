# JPA Standard Math Functions — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Implement all 10 standard JPA CriteriaBuilder math functions (abs, neg, sqrt, sign, ceiling, floor, exp, ln, power, round) using a dedicated `MathFunction` AST node.

**Architecture:** New `MathFunction` sealed record in `LambdaExpression` with a `MathOp` enum. Bytecode analysis in `MethodInvocationHandler` (INVOKESTATIC for `Math.*`) and `ArithmeticInstructionHandler` (INEG/DNEG for neg). Code generation via new `MathExpressionBuilder` called directly from `CriteriaExpressionGenerator`.

**Tech Stack:** Java 17+, ASM bytecode analysis, Gizmo2 code generation, JPA Criteria API (Jakarta Persistence 3.1+), JUnit 5, AssertJ, jqwik (property-based testing)

**Design doc:** `docs/plans/2026-02-17-jpa-math-functions-design.md`

---

## Task 1: MathFunction AST Node

**Files:**
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/ast/LambdaExpression.java`

**Step 1: Add `MathFunction` record after `CorrelatedVariable` (after line 1311)**

Add inside the sealed interface, after the `CorrelatedVariable` record:

```java
// ─── Mathematical Functions ───────────────────────────────────────────────

/**
 * Mathematical function applied to one or two expressions.
 *
 * <p>Represents standard JPA CriteriaBuilder math functions:
 * <ul>
 *   <li>Unary: {@code cb.abs()}, {@code cb.neg()}, {@code cb.sqrt()}, {@code cb.sign()},
 *       {@code cb.ceiling()}, {@code cb.floor()}, {@code cb.exp()}, {@code cb.ln()}</li>
 *   <li>Binary: {@code cb.power()}, {@code cb.round()}</li>
 * </ul>
 *
 * @param op the math operation
 * @param operand the primary operand expression
 * @param secondOperand the second operand (non-null for binary ops, null for unary)
 */
record MathFunction(
        MathOp op,
        LambdaExpression operand,
        @Nullable LambdaExpression secondOperand) implements LambdaExpression {

    /** Standard JPA CriteriaBuilder math operations. */
    public enum MathOp {
        // Unary functions (JPA 2.0)
        ABS,
        NEG,
        SQRT,
        // Unary functions (JPA 3.1)
        SIGN,
        CEILING,
        FLOOR,
        EXP,
        LN,
        // Binary functions (JPA 3.1)
        POWER,
        ROUND;

        /** Returns true if this operation requires a second operand. */
        public boolean isBinary() {
            return this == POWER || this == ROUND;
        }
    }

    public MathFunction {
        Objects.requireNonNull(op, "MathOp cannot be null");
        Objects.requireNonNull(operand, "operand cannot be null");
        if (op.isBinary() && secondOperand == null) {
            throw new IllegalArgumentException(op + " requires a second operand");
        }
        if (!op.isBinary() && secondOperand != null) {
            throw new IllegalArgumentException(op + " is unary but received a second operand");
        }
    }

    // Unary factory methods
    public static MathFunction abs(LambdaExpression operand) {
        return new MathFunction(MathOp.ABS, operand, null);
    }

    public static MathFunction neg(LambdaExpression operand) {
        return new MathFunction(MathOp.NEG, operand, null);
    }

    public static MathFunction sqrt(LambdaExpression operand) {
        return new MathFunction(MathOp.SQRT, operand, null);
    }

    public static MathFunction sign(LambdaExpression operand) {
        return new MathFunction(MathOp.SIGN, operand, null);
    }

    public static MathFunction ceiling(LambdaExpression operand) {
        return new MathFunction(MathOp.CEILING, operand, null);
    }

    public static MathFunction floor(LambdaExpression operand) {
        return new MathFunction(MathOp.FLOOR, operand, null);
    }

    public static MathFunction exp(LambdaExpression operand) {
        return new MathFunction(MathOp.EXP, operand, null);
    }

    public static MathFunction ln(LambdaExpression operand) {
        return new MathFunction(MathOp.LN, operand, null);
    }

    // Binary factory methods
    public static MathFunction power(LambdaExpression base, LambdaExpression exponent) {
        return new MathFunction(MathOp.POWER, base, exponent);
    }

    public static MathFunction round(LambdaExpression operand, LambdaExpression decimalPlaces) {
        return new MathFunction(MathOp.ROUND, operand, decimalPlaces);
    }
}
```

Add `import org.jspecify.annotations.Nullable;` to the imports if not already present (check existing usage of `@Nullable` in the file — `CapturedVariable` already uses it at line 203).

**Step 2: Verify compilation**

Run: `mvn compile -pl deployment -q`
Expected: FAIL — exhaustive switches in other files will break because `MathFunction` is a new permitted type. This is expected and confirms the sealed interface is working correctly.

**Step 3: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/ast/LambdaExpression.java
git commit -m "Add MathFunction AST node to LambdaExpression sealed interface"
```

---

## Task 2: Fix Exhaustive Switches (Compilation)

Every exhaustive switch over `LambdaExpression` must handle `MathFunction`. Add minimal `case MathFunction` branches to restore compilation. We'll fill in the real logic in later tasks.

**Files:**
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/analysis/CapturedVariableHelper.java`
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/CriteriaExpressionGenerator.java`
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/BiEntityExpressionBuilder.java`
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/GroupExpressionBuilder.java`
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/devui/JpqlGenerator.java`
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/devui/JavaSourceGenerator.java`

**Step 1: Add placeholder cases to all files**

For each file, find every switch on LambdaExpression and add the MathFunction case. Approach depends on the switch's default behavior:

- **Switches with `default -> { }` (no-op)**: No change needed (MathFunction falls through to default).
- **Switches with `default -> expression` (identity return)**: No change needed.
- **Switches with `default -> throw`**: Add `case LambdaExpression.MathFunction _ -> throw new UnsupportedExpressionException(...)` or a TODO placeholder.
- **Exhaustive switches with no default**: Must add a case.

Check each file by compiling. The compiler will tell you which switches need updating.

**Step 2: Compile and verify**

Run: `mvn compile -pl deployment -q`
Expected: SUCCESS (all switches handle MathFunction, even if with placeholder logic)

**Step 3: Run existing tests to verify no regression**

Run: `mvn test -pl deployment -q`
Expected: All existing tests pass (MathFunction is never constructed yet, so no path reaches the new cases)

**Step 4: Commit**

```bash
git add -A
git commit -m "Add placeholder MathFunction cases to all exhaustive switches"
```

---

## Task 3: MathFunction AST Validation Tests

**Files:**
- Modify: `deployment/src/test/java/io/quarkiverse/qubit/deployment/ast/AstNodeValidationTest.java`

**Step 1: Write validation tests**

Add a new `@Nested` class inside `AstNodeValidationTest` following the existing pattern (see `ConstructorCallValidationTests` or `ConditionalValidationTests` as templates):

```java
@Nested
@DisplayName("MathFunction validation")
class MathFunctionValidationTests {

    private final LambdaExpression operand = new FieldAccess("age", int.class);
    private final LambdaExpression secondOperand = new Constant(2, int.class);

    @Test
    @DisplayName("unary factory creates correct node")
    void unaryFactory_createsCorrectNode() {
        var abs = MathFunction.abs(operand);
        assertThat(abs.op()).isEqualTo(MathFunction.MathOp.ABS);
        assertThat(abs.operand()).isEqualTo(operand);
        assertThat(abs.secondOperand()).isNull();
    }

    @Test
    @DisplayName("binary factory creates correct node")
    void binaryFactory_createsCorrectNode() {
        var power = MathFunction.power(operand, secondOperand);
        assertThat(power.op()).isEqualTo(MathFunction.MathOp.POWER);
        assertThat(power.operand()).isEqualTo(operand);
        assertThat(power.secondOperand()).isEqualTo(secondOperand);
    }

    @Test
    @DisplayName("null op throws NullPointerException")
    void constructor_withNullOp_throwsNPE() {
        assertThatThrownBy(() -> new MathFunction(null, operand, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("MathOp cannot be null");
    }

    @Test
    @DisplayName("null operand throws NullPointerException")
    void constructor_withNullOperand_throwsNPE() {
        assertThatThrownBy(() -> new MathFunction(MathFunction.MathOp.ABS, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("operand cannot be null");
    }

    @Test
    @DisplayName("binary op without second operand throws IllegalArgumentException")
    void constructor_binaryOpWithoutSecondOperand_throwsIAE() {
        assertThatThrownBy(() -> new MathFunction(MathFunction.MathOp.POWER, operand, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a second operand");
    }

    @Test
    @DisplayName("unary op with second operand throws IllegalArgumentException")
    void constructor_unaryOpWithSecondOperand_throwsIAE() {
        assertThatThrownBy(() -> new MathFunction(MathFunction.MathOp.ABS, operand, secondOperand))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is unary but received a second operand");
    }

    @ParameterizedTest(name = "{0} is unary")
    @EnumSource(value = MathFunction.MathOp.class, names = { "ABS", "NEG", "SQRT", "SIGN", "CEILING", "FLOOR", "EXP", "LN" })
    void unaryOps_isBinaryReturnsFalse(MathFunction.MathOp op) {
        assertThat(op.isBinary()).isFalse();
    }

    @ParameterizedTest(name = "{0} is binary")
    @EnumSource(value = MathFunction.MathOp.class, names = { "POWER", "ROUND" })
    void binaryOps_isBinaryReturnsTrue(MathFunction.MathOp op) {
        assertThat(op.isBinary()).isTrue();
    }

    @ParameterizedTest(name = "{0} factory creates correct op")
    @EnumSource(value = MathFunction.MathOp.class, names = { "ABS", "NEG", "SQRT", "SIGN", "CEILING", "FLOOR", "EXP", "LN" })
    void allUnaryFactories_createCorrectOp(MathFunction.MathOp op) {
        MathFunction result = switch (op) {
            case ABS -> MathFunction.abs(operand);
            case NEG -> MathFunction.neg(operand);
            case SQRT -> MathFunction.sqrt(operand);
            case SIGN -> MathFunction.sign(operand);
            case CEILING -> MathFunction.ceiling(operand);
            case FLOOR -> MathFunction.floor(operand);
            case EXP -> MathFunction.exp(operand);
            case LN -> MathFunction.ln(operand);
            default -> throw new IllegalArgumentException("Not unary: " + op);
        };
        assertThat(result.op()).isEqualTo(op);
        assertThat(result.secondOperand()).isNull();
    }
}
```

**Step 2: Run the tests**

Run: `mvn test -pl deployment -Dtest="AstNodeValidationTest" -q`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add deployment/src/test/java/io/quarkiverse/qubit/deployment/ast/AstNodeValidationTest.java
git commit -m "Add MathFunction AST node validation tests"
```

---

## Task 4: QubitConstants — Math Method Names

**Files:**
- Modify: `runtime/src/main/java/io/quarkiverse/qubit/runtime/internal/QubitConstants.java`

**Step 1: Add math constants**

Add after the existing BigDecimal arithmetic section (around line 150):

```java
// ─── Math Class Methods ──────────────────────────────────────────────────

/** JVM internal name for java.lang.Math. */
public static final String JVM_JAVA_LANG_MATH = "java/lang/Math";

/** JVM internal name for java.lang.Integer. */
public static final String JVM_JAVA_LANG_INTEGER = "java/lang/Integer";

/** JVM internal name for java.lang.Long. */
public static final String JVM_JAVA_LANG_LONG = "java/lang/Long";

// Math method names
public static final String METHOD_ABS = "abs";
public static final String METHOD_SQRT = "sqrt";
public static final String METHOD_CEIL = "ceil";
public static final String METHOD_FLOOR = "floor";
public static final String METHOD_EXP = "exp";
public static final String METHOD_LOG = "log";
public static final String METHOD_POW = "pow";
public static final String METHOD_ROUND = "round";
public static final String METHOD_SIGNUM = "signum";

/** Math unary methods that map to JPA CriteriaBuilder unary functions. */
public static final Set<String> MATH_UNARY_METHODS = Set.of(
        METHOD_ABS, METHOD_SQRT, METHOD_CEIL, METHOD_FLOOR,
        METHOD_EXP, METHOD_LOG, METHOD_SIGNUM);

/** Math binary methods that map to JPA CriteriaBuilder binary functions. */
public static final Set<String> MATH_BINARY_METHODS = Set.of(METHOD_POW);

/** Signum method name (same for Integer.signum and Long.signum). */
public static final Set<String> SIGNUM_OWNERS = Set.of(
        JVM_JAVA_LANG_MATH, JVM_JAVA_LANG_INTEGER, JVM_JAVA_LANG_LONG);
```

Also add the QubitMath owner constant (for Task 10):

```java
/** JVM internal name for QubitMath marker class. */
public static final String JVM_QUBIT_MATH = "io/quarkiverse/qubit/QubitMath";
```

**Step 2: Compile**

Run: `mvn compile -pl runtime -q`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add runtime/src/main/java/io/quarkiverse/qubit/runtime/internal/QubitConstants.java
git commit -m "Add math method constants to QubitConstants"
```

---

## Task 5: MethodDescriptors — CriteriaBuilder Math Methods

**Files:**
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/MethodDescriptors.java`

**Step 1: Add math function MethodDesc constants**

Add after the `HCB_SECOND` constant (after line 145):

```java
// ─── Math Functions (JPA 2.0 + 3.1) ─────────────────────────────────────

/** cb.abs(Expression) → Expression (JPA 2.0) */
public static final MethodDesc CB_ABS = MethodDesc.of(CriteriaBuilder.class, "abs", Expression.class, Expression.class);

/** cb.neg(Expression) → Expression (JPA 2.0) */
public static final MethodDesc CB_NEG = MethodDesc.of(CriteriaBuilder.class, "neg", Expression.class, Expression.class);

/** cb.sqrt(Expression) → Expression<Double> (JPA 2.0) */
public static final MethodDesc CB_SQRT = MethodDesc.of(CriteriaBuilder.class, "sqrt", Expression.class, Expression.class);

/** cb.sign(Expression) → Expression<Integer> (JPA 3.1) */
public static final MethodDesc CB_SIGN = MethodDesc.of(CriteriaBuilder.class, "sign", Expression.class, Expression.class);

/** cb.ceiling(Expression) → Expression (JPA 3.1) */
public static final MethodDesc CB_CEILING = MethodDesc.of(CriteriaBuilder.class, "ceiling", Expression.class,
        Expression.class);

/** cb.floor(Expression) → Expression (JPA 3.1) */
public static final MethodDesc CB_FLOOR = MethodDesc.of(CriteriaBuilder.class, "floor", Expression.class, Expression.class);

/** cb.exp(Expression) → Expression<Double> (JPA 3.1) */
public static final MethodDesc CB_EXP = MethodDesc.of(CriteriaBuilder.class, "exp", Expression.class, Expression.class);

/** cb.ln(Expression) → Expression<Double> (JPA 3.1) */
public static final MethodDesc CB_LN = MethodDesc.of(CriteriaBuilder.class, "ln", Expression.class, Expression.class);

/** cb.power(Expression, Expression) → Expression<Double> (JPA 3.1) */
public static final MethodDesc CB_POWER = MethodDesc.of(CriteriaBuilder.class, "power", Expression.class,
        Expression.class, Expression.class);

/** cb.power(Expression, Number) → Expression<Double> (JPA 3.1) */
public static final MethodDesc CB_POWER_NUMBER = MethodDesc.of(CriteriaBuilder.class, "power", Expression.class,
        Expression.class, Number.class);

/** cb.round(Expression, Integer) → Expression (JPA 3.1) */
public static final MethodDesc CB_ROUND = MethodDesc.of(CriteriaBuilder.class, "round", Expression.class,
        Expression.class, Integer.class);
```

**Step 2: Compile**

Run: `mvn compile -pl deployment -q`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/MethodDescriptors.java
git commit -m "Add CriteriaBuilder math function descriptors to MethodDescriptors"
```

---

## Task 6: Unary Negation Bytecode Handling

**Files:**
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/analysis/instruction/ArithmeticInstructionHandler.java`
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/common/OpcodeClassifier.java`

**Step 1: Add negation opcode support to OpcodeClassifier**

In `OpcodeClassifier.java`, add after `isArithmeticOpcode()` (after line 16):

```java
/** Checks if opcode is numeric negation (INEG, LNEG, FNEG, DNEG). */
public static boolean isNegationOpcode(int opcode) {
    return opcode >= INEG && opcode <= DNEG;
}
```

Also add imports for `INEG` and `DNEG` if not already imported via `static org.objectweb.asm.Opcodes.*`.

**Step 2: Add INEG/LNEG/FNEG/DNEG handling to ArithmeticInstructionHandler**

In `ArithmeticInstructionHandler.java`:

1. Add to `SUPPORTED_OPCODES` set (line 23-33):

```java
// Negation: NEG for int, long, float, double
INEG, LNEG, FNEG, DNEG,
```

2. In `handle()` method (line 46-58), add a branch before the existing `if` chain:

```java
if (OpcodeClassifier.isNegationOpcode(opcode)) {
    handleNegationOperation(ctx, opcode);
} else if (OpcodeClassifier.isArithmeticOpcode(opcode)) {
```

3. Add the handler method:

```java
/** Handles unary negation: pushes MathFunction.neg(operand). */
private void handleNegationOperation(AnalysisContext ctx, int opcode) {
    BytecodeValidator.requireStackSize(ctx.getStack(), 1, OpcodeNames.get(opcode));
    LambdaExpression operand = ctx.pop();
    ctx.push(LambdaExpression.MathFunction.neg(operand));
}
```

Add import: `import io.quarkiverse.qubit.deployment.ast.LambdaExpression;`

**Step 3: Compile**

Run: `mvn compile -pl deployment -q`
Expected: SUCCESS

**Step 4: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/analysis/instruction/ArithmeticInstructionHandler.java \
      deployment/src/main/java/io/quarkiverse/qubit/deployment/common/OpcodeClassifier.java
git commit -m "Handle INEG/LNEG/FNEG/DNEG bytecode as MathFunction.neg()"
```

---

## Task 7: Math Method Recognition in MethodInvocationHandler

**Files:**
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/analysis/instruction/MethodInvocationHandler.java`

**Step 1: Add Math method handling to `handleInvokeStatic()`**

In `handleInvokeStatic()` (line 196-215), add before the temporal factory methods loop:

```java
// Handle Math static methods (Math.abs, Math.sqrt, Math.ceil, Math.floor, etc.)
if (handleMathStaticMethod(ctx, staticInsn)) {
    return;
}

// Handle Integer.signum() and Long.signum()
if (handleSignumMethod(ctx, staticInsn)) {
    return;
}
```

Then add the handler methods at the end of the class (before the final closing brace):

```java
// ─── Math Static Method Handling ─────────────────────────────────────────

/**
 * Handles INVOKESTATIC on java.lang.Math: maps to MathFunction AST nodes.
 * Returns true if handled, false otherwise.
 */
private boolean handleMathStaticMethod(AnalysisContext ctx, MethodInsnNode staticInsn) {
    if (!staticInsn.owner.equals(JVM_JAVA_LANG_MATH)) {
        return false;
    }

    String methodName = staticInsn.name;

    // Unary math functions: abs, sqrt, ceil, floor, exp, log, signum
    if (MATH_UNARY_METHODS.contains(methodName)) {
        return handleUnaryMathMethod(ctx, methodName);
    }

    // Binary math functions: pow
    if (MATH_BINARY_METHODS.contains(methodName)) {
        return handleBinaryMathMethod(ctx, methodName);
    }

    // Math.round(x) → round(x, 0)
    if (methodName.equals(METHOD_ROUND)) {
        return handleMathRound(ctx);
    }

    return false;
}

private boolean handleUnaryMathMethod(AnalysisContext ctx, String methodName) {
    if (ctx.getStack().isEmpty()) {
        return false;
    }
    LambdaExpression operand = ctx.pop();
    LambdaExpression.MathFunction.MathOp op = mapUnaryMathOp(methodName);
    ctx.push(new LambdaExpression.MathFunction(op, operand, null));
    return true;
}

private boolean handleBinaryMathMethod(AnalysisContext ctx, String methodName) {
    if (ctx.getStack().size() < 2) {
        return false;
    }
    LambdaExpression secondOperand = ctx.pop();
    LambdaExpression firstOperand = ctx.pop();
    LambdaExpression.MathFunction.MathOp op = mapBinaryMathOp(methodName);
    ctx.push(new LambdaExpression.MathFunction(op, firstOperand, secondOperand));
    return true;
}

private boolean handleMathRound(AnalysisContext ctx) {
    if (ctx.getStack().isEmpty()) {
        return false;
    }
    LambdaExpression operand = ctx.pop();
    // Math.round(x) → cb.round(x, 0) — round to nearest integer
    ctx.push(LambdaExpression.MathFunction.round(operand, LambdaExpression.Constant.ZERO_INT));
    return true;
}

/**
 * Handles Integer.signum(int) and Long.signum(long) static methods.
 * Both map to cb.sign().
 */
private boolean handleSignumMethod(AnalysisContext ctx, MethodInsnNode staticInsn) {
    if (!staticInsn.name.equals(METHOD_SIGNUM)) {
        return false;
    }
    if (!SIGNUM_OWNERS.contains(staticInsn.owner)) {
        return false;
    }
    if (ctx.getStack().isEmpty()) {
        return false;
    }
    LambdaExpression operand = ctx.pop();
    ctx.push(LambdaExpression.MathFunction.sign(operand));
    return true;
}

private static LambdaExpression.MathFunction.MathOp mapUnaryMathOp(String methodName) {
    return switch (methodName) {
        case METHOD_ABS -> LambdaExpression.MathFunction.MathOp.ABS;
        case METHOD_SQRT -> LambdaExpression.MathFunction.MathOp.SQRT;
        case METHOD_CEIL -> LambdaExpression.MathFunction.MathOp.CEILING;
        case METHOD_FLOOR -> LambdaExpression.MathFunction.MathOp.FLOOR;
        case METHOD_EXP -> LambdaExpression.MathFunction.MathOp.EXP;
        case METHOD_LOG -> LambdaExpression.MathFunction.MathOp.LN;
        case METHOD_SIGNUM -> LambdaExpression.MathFunction.MathOp.SIGN;
        default -> throw new IllegalArgumentException("Unknown unary math method: " + methodName);
    };
}

private static LambdaExpression.MathFunction.MathOp mapBinaryMathOp(String methodName) {
    return switch (methodName) {
        case METHOD_POW -> LambdaExpression.MathFunction.MathOp.POWER;
        default -> throw new IllegalArgumentException("Unknown binary math method: " + methodName);
    };
}
```

Add the required static imports from `QubitConstants`:

```java
import static io.quarkiverse.qubit.runtime.internal.QubitConstants.*;
```

(Check which constants are already imported and add the new ones: `JVM_JAVA_LANG_MATH`, `MATH_UNARY_METHODS`, `MATH_BINARY_METHODS`, `METHOD_ABS`, `METHOD_SQRT`, `METHOD_CEIL`, `METHOD_FLOOR`, `METHOD_EXP`, `METHOD_LOG`, `METHOD_POW`, `METHOD_ROUND`, `METHOD_SIGNUM`, `SIGNUM_OWNERS`)

**Step 2: Compile**

Run: `mvn compile -pl deployment -q`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/analysis/instruction/MethodInvocationHandler.java
git commit -m "Recognize Math static methods in bytecode analysis"
```

---

## Task 8: Bytecode Analysis Tests — Math Methods

**Files:**
- Modify: `deployment/src/test/java/io/quarkiverse/qubit/deployment/testutil/LambdaTestSources.java`
- Create: `deployment/src/test/java/io/quarkiverse/qubit/deployment/bytecode/MathOperationsBytecodeTest.java`

**Step 1: Add test lambda methods to `LambdaTestSources`**

Add after the existing arithmetic operations section (after line ~485). These lambdas use `TestPerson` fields which has `int age`, `double salary`, `float height`:

```java
// ─── Math Operations ─────────────────────────────────────────────────────

public static QuerySpec<TestPerson, Boolean> mathAbs() {
    return (TestPerson p) -> Math.abs(p.age) > 5;
}

public static QuerySpec<TestPerson, Boolean> mathAbsArithmetic() {
    int target = 30;
    return (TestPerson p) -> Math.abs(p.age - target) < 5;
}

public static QuerySpec<TestPerson, Boolean> mathSqrt() {
    return (TestPerson p) -> Math.sqrt(p.salary) > 200;
}

public static QuerySpec<TestPerson, Boolean> mathCeil() {
    return (TestPerson p) -> Math.ceil(p.height) > 6;
}

public static QuerySpec<TestPerson, Boolean> mathFloor() {
    return (TestPerson p) -> Math.floor(p.salary) > 50000;
}

public static QuerySpec<TestPerson, Boolean> mathExp() {
    return (TestPerson p) -> Math.exp(p.height) > 100;
}

public static QuerySpec<TestPerson, Boolean> mathLog() {
    return (TestPerson p) -> Math.log(p.salary) > 10;
}

public static QuerySpec<TestPerson, Boolean> mathPow() {
    return (TestPerson p) -> Math.pow(p.age, 2) > 900;
}

public static QuerySpec<TestPerson, Boolean> mathPowCapturedExponent() {
    double exponent = 2.0;
    return (TestPerson p) -> Math.pow(p.salary, exponent) > 1000000;
}

public static QuerySpec<TestPerson, Boolean> mathRound() {
    return (TestPerson p) -> Math.round(p.salary) > 50000;
}

public static QuerySpec<TestPerson, Boolean> integerSignum() {
    return (TestPerson p) -> Integer.signum(p.age) > 0;
}

public static QuerySpec<TestPerson, Boolean> unaryNegation() {
    return (TestPerson p) -> -p.age < -18;
}

public static QuerySpec<TestPerson, Boolean> doubleNegation_arithmetic() {
    return (TestPerson p) -> -p.salary < -50000;
}
```

**Step 2: Write the bytecode test**

Create `MathOperationsBytecodeTest.java`:

```java
package io.quarkiverse.qubit.deployment.bytecode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.MathFunction;
import io.quarkiverse.qubit.deployment.ast.LambdaExpression.MathFunction.MathOp;

@DisplayName("Math operations bytecode analysis")
class MathOperationsBytecodeTest extends PrecompiledLambdaAnalyzer {

    @Nested
    @DisplayName("Unary math functions")
    class UnaryMathFunctions {

        static Stream<Arguments> unaryMathMethods() {
            return Stream.of(
                    Arguments.of("mathAbs", MathOp.ABS),
                    Arguments.of("mathSqrt", MathOp.SQRT),
                    Arguments.of("mathCeil", MathOp.CEILING),
                    Arguments.of("mathFloor", MathOp.FLOOR),
                    Arguments.of("mathExp", MathOp.EXP),
                    Arguments.of("mathLog", MathOp.LN),
                    Arguments.of("integerSignum", MathOp.SIGN));
        }

        @ParameterizedTest(name = "{0} → MathOp.{1}")
        @MethodSource("unaryMathMethods")
        void unaryMathMethod_producesMathFunctionNode(String lambdaMethod, MathOp expectedOp) {
            LambdaExpression expr = analyzeLambda(lambdaMethod);

            // Top-level is BinaryOp (comparison like > 5)
            assertThat(expr).isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;

            // Left side should be MathFunction
            assertThat(comparison.left()).isInstanceOf(MathFunction.class);
            MathFunction mathFunc = (MathFunction) comparison.left();
            assertThat(mathFunc.op()).isEqualTo(expectedOp);
            assertThat(mathFunc.secondOperand()).isNull();
        }
    }

    @Nested
    @DisplayName("Binary math functions")
    class BinaryMathFunctions {

        @Test
        @DisplayName("Math.pow(p.age, 2) produces POWER node")
        void mathPow_producesPowerNode() {
            LambdaExpression expr = analyzeLambda("mathPow");

            LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;
            assertThat(comparison.left()).isInstanceOf(MathFunction.class);

            MathFunction mathFunc = (MathFunction) comparison.left();
            assertThat(mathFunc.op()).isEqualTo(MathOp.POWER);
            assertThat(mathFunc.secondOperand()).isNotNull();
        }

        @Test
        @DisplayName("Math.round(p.salary) produces ROUND node with 0 decimal places")
        void mathRound_producesRoundNodeWithZero() {
            LambdaExpression expr = analyzeLambda("mathRound");

            LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;
            assertThat(comparison.left()).isInstanceOf(MathFunction.class);

            MathFunction mathFunc = (MathFunction) comparison.left();
            assertThat(mathFunc.op()).isEqualTo(MathOp.ROUND);
            assertThat(mathFunc.secondOperand()).isInstanceOf(LambdaExpression.Constant.class);

            LambdaExpression.Constant decPlaces = (LambdaExpression.Constant) mathFunc.secondOperand();
            assertThat(decPlaces.value()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Unary negation")
    class UnaryNegation {

        @Test
        @DisplayName("-p.age produces NEG node")
        void unaryNegation_producesNegNode() {
            LambdaExpression expr = analyzeLambda("unaryNegation");

            // The expression tree should contain a MathFunction.NEG somewhere
            // Exact structure depends on how the compiler emits -p.age < -18
            assertThat(expr).isInstanceOf(LambdaExpression.BinaryOp.class);
            LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;

            // Left side: -p.age → MathFunction.NEG(FieldAccess("age"))
            assertThat(comparison.left()).isInstanceOf(MathFunction.class);
            MathFunction neg = (MathFunction) comparison.left();
            assertThat(neg.op()).isEqualTo(MathOp.NEG);
        }
    }

    @Nested
    @DisplayName("Math with arithmetic expressions")
    class MathWithArithmetic {

        @Test
        @DisplayName("Math.abs(p.age - target) produces ABS with nested expression")
        void mathAbsWithArithmetic_producesNestedNode() {
            LambdaExpression expr = analyzeLambda("mathAbsArithmetic");

            LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;
            assertThat(comparison.left()).isInstanceOf(MathFunction.class);

            MathFunction absFunc = (MathFunction) comparison.left();
            assertThat(absFunc.op()).isEqualTo(MathOp.ABS);
            // The operand should be a BinaryOp(SUB) for p.age - target
            assertThat(absFunc.operand()).isInstanceOf(LambdaExpression.BinaryOp.class);
        }
    }
}
```

**Step 3: Run tests**

Run: `mvn test -pl deployment -Dtest="MathOperationsBytecodeTest" -q`
Expected: All tests PASS

**Step 4: Commit**

```bash
git add deployment/src/test/java/io/quarkiverse/qubit/deployment/testutil/LambdaTestSources.java \
      deployment/src/test/java/io/quarkiverse/qubit/deployment/bytecode/MathOperationsBytecodeTest.java
git commit -m "Add bytecode analysis tests for math operations"
```

---

## Task 9: CapturedVariableHelper — MathFunction Support

**Files:**
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/analysis/CapturedVariableHelper.java`

**Step 1: Add MathFunction handling to `collectCapturedVariableIndices()` switch (around line 38-90)**

Add a new case:

```java
case LambdaExpression.MathFunction mathFunc -> {
    collectCapturedVariableIndices(mathFunc.operand(), indices);
    if (mathFunc.secondOperand() != null) {
        collectCapturedVariableIndices(mathFunc.secondOperand(), indices);
    }
}
```

**Step 2: Add MathFunction handling to `renumberCapturedVariables()` switch (around line 98-156)**

Add a new case:

```java
case LambdaExpression.MathFunction(var op, var operand, var secondOp) ->
    new LambdaExpression.MathFunction(
            op,
            renumberCapturedVariables(operand, offset),
            secondOp != null ? renumberCapturedVariables(secondOp, offset) : null);
```

**Step 3: Compile and run tests**

Run: `mvn test -pl deployment -Dtest="CapturedVariableCoverageTest" -q`
Expected: PASS

**Step 4: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/analysis/CapturedVariableHelper.java
git commit -m "Handle MathFunction in CapturedVariableHelper"
```

---

## Task 10: MathExpressionBuilder

**Files:**
- Create: `deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/MathExpressionBuilder.java`

**Step 1: Create the builder**

Follow the pattern from `ArithmeticExpressionBuilder`. The builder takes a `MathFunction` node and generates the appropriate `cb.xxx()` call:

```java
package io.quarkiverse.qubit.deployment.generation.expression;

import static io.quarkiverse.qubit.deployment.generation.MethodDescriptors.*;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression.MathFunction;
import io.quarkus.gizmo2.Expr;
import io.quarkus.gizmo2.desc.MethodDesc;

/**
 * Generates JPA CriteriaBuilder math function calls from {@link MathFunction} AST nodes.
 *
 * <p>Maps {@link MathFunction.MathOp} to the corresponding CriteriaBuilder method:
 * <ul>
 *   <li>Unary: {@code cb.abs()}, {@code cb.neg()}, {@code cb.sqrt()}, {@code cb.sign()},
 *       {@code cb.ceiling()}, {@code cb.floor()}, {@code cb.exp()}, {@code cb.ln()}</li>
 *   <li>Binary: {@code cb.power(expr, expr)}, {@code cb.round(expr, int)}</li>
 * </ul>
 */
public final class MathExpressionBuilder {

    private MathExpressionBuilder() {
    }

    /**
     * Builds the JPA CriteriaBuilder call for a math function.
     *
     * @param cb the CriteriaBuilder expression
     * @param operand the primary operand JPA expression
     * @param secondOperand the second operand (null for unary ops)
     * @param op the math operation
     * @return the resulting JPA expression
     */
    public static Expr build(io.quarkus.gizmo2.creator.BlockCreator bc, Expr cb,
            Expr operand, Expr secondOperand, MathFunction.MathOp op) {
        return switch (op) {
            // Unary functions
            case ABS -> bc.invokeInterface(CB_ABS, cb, operand);
            case NEG -> bc.invokeInterface(CB_NEG, cb, operand);
            case SQRT -> bc.invokeInterface(CB_SQRT, cb, operand);
            case SIGN -> bc.invokeInterface(CB_SIGN, cb, operand);
            case CEILING -> bc.invokeInterface(CB_CEILING, cb, operand);
            case FLOOR -> bc.invokeInterface(CB_FLOOR, cb, operand);
            case EXP -> bc.invokeInterface(CB_EXP, cb, operand);
            case LN -> bc.invokeInterface(CB_LN, cb, operand);
            // Binary functions
            case POWER -> bc.invokeInterface(CB_POWER, cb, operand, secondOperand);
            case ROUND -> bc.invokeInterface(CB_ROUND, cb, operand, secondOperand);
        };
    }

    /** Maps MathOp to its MethodDesc for testing/inspection purposes. */
    public static MethodDesc methodDescFor(MathFunction.MathOp op) {
        return switch (op) {
            case ABS -> CB_ABS;
            case NEG -> CB_NEG;
            case SQRT -> CB_SQRT;
            case SIGN -> CB_SIGN;
            case CEILING -> CB_CEILING;
            case FLOOR -> CB_FLOOR;
            case EXP -> CB_EXP;
            case LN -> CB_LN;
            case POWER -> CB_POWER;
            case ROUND -> CB_ROUND;
        };
    }
}
```

**Note:** Check how `ArithmeticExpressionBuilder` and `StringExpressionBuilder` use `bc.invokeInterface()` — the exact API may vary. The pattern is `bc.invokeInterface(methodDesc, receiver, args...)`.

**Step 2: Compile**

Run: `mvn compile -pl deployment -q`
Expected: SUCCESS

**Step 3: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/MathExpressionBuilder.java
git commit -m "Add MathExpressionBuilder for JPA math function code generation"
```

---

## Task 11: CriteriaExpressionGenerator — Wire MathFunction

**Files:**
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/CriteriaExpressionGenerator.java`

**Step 1: Add MathFunction handling to `generateExpressionAsJpaExpression()` switch**

Find the switch in `generateExpressionAsJpaExpression()` (around line 335-388). Add a new case:

```java
case LambdaExpression.MathFunction mathFunc -> generateMathFunction(bc, cb, root, capturedValues, mathFunc);
```

**Step 2: Add the `generateMathFunction()` method**

```java
/** Generates a JPA math function expression from a MathFunction AST node. */
private Expr generateMathFunction(BlockCreator bc, Expr cb, Expr root, Expr capturedValues,
        LambdaExpression.MathFunction mathFunc) {

    // Generate the primary operand as a JPA Expression
    Expr operandExpr = generateExpressionAsJpaExpression(bc, cb, root, capturedValues, mathFunc.operand());

    // Generate the second operand for binary operations
    Expr secondExpr = null;
    if (mathFunc.op().isBinary()) {
        if (mathFunc.op() == LambdaExpression.MathFunction.MathOp.ROUND) {
            // round() second arg is Integer, not Expression
            secondExpr = generateExpression(bc, cb, root, capturedValues, mathFunc.secondOperand());
        } else {
            // power() second arg is Expression
            secondExpr = generateExpressionAsJpaExpression(bc, cb, root, capturedValues, mathFunc.secondOperand());
        }
    }

    return MathExpressionBuilder.build(bc, cb, operandExpr, secondExpr, mathFunc.op());
}
```

Add the import:

```java
import io.quarkiverse.qubit.deployment.generation.expression.MathExpressionBuilder;
```

**Step 3: Also add MathFunction case to `generatePredicate()`** if math functions can appear in predicate position (they can — e.g., a boolean comparison where a math function is one side: `Math.abs(x) > 5` → the comparison is the predicate, but the math function is the expression). Actually, MathFunction returns an Expression, not a Predicate, so it shouldn't appear in `generatePredicate()`. It will appear in `generateExpressionAsJpaExpression()` which is called by comparison handlers.

Check if any of the switches have a `default -> throw` that would catch MathFunction. If so, update the placeholder from Task 2 to call `generateMathFunction()`.

**Step 4: Compile and run tests**

Run: `mvn test -pl deployment -Dtest="*CriteriaTest" -q`
Expected: All existing criteria tests still pass

**Step 5: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/CriteriaExpressionGenerator.java
git commit -m "Wire MathFunction to MathExpressionBuilder in CriteriaExpressionGenerator"
```

---

## Task 12: Criteria Generation Tests — Math Functions

**Files:**
- Create: `deployment/src/test/java/io/quarkiverse/qubit/deployment/criteria/MathOperationsCriteriaTest.java`

**Step 1: Write criteria generation tests**

Follow the pattern from existing criteria tests (e.g., `StringOperationsCriteriaTest`):

```java
package io.quarkiverse.qubit.deployment.criteria;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.quarkiverse.qubit.deployment.ast.LambdaExpression;

@DisplayName("Math operations criteria generation")
class MathOperationsCriteriaTest extends CriteriaQueryTestBase {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "mathAbs", "mathAbsArithmetic", "mathSqrt", "mathCeil",
            "mathFloor", "mathExp", "mathLog", "mathPow",
            "mathPowCapturedExponent", "mathRound", "integerSignum",
            "unaryNegation", "doubleNegation_arithmetic"
    })
    @DisplayName("generates criteria for math operations")
    void mathOperation_generatesCriteria(String lambdaMethodName) {
        LambdaExpression expression = analyzeLambda(lambdaMethodName);
        assertCriteriaGenerationSucceeds(expression);
    }

    @ParameterizedTest(name = "{0} calls cb.{1}()")
    @ValueSource(strings = { "mathAbs" })
    @DisplayName("Math.abs generates cb.abs()")
    void mathAbs_callsCbAbs(String lambdaMethodName) {
        LambdaExpression expression = analyzeLambda(lambdaMethodName);
        CriteriaQueryStructure structure = generateCriteriaQuery(expression);
        assertCriteriaMethodCalled(structure, "abs");
    }

    @ParameterizedTest(name = "{0} calls cb.{1}()")
    @ValueSource(strings = { "mathSqrt" })
    @DisplayName("Math.sqrt generates cb.sqrt()")
    void mathSqrt_callsCbSqrt(String lambdaMethodName) {
        LambdaExpression expression = analyzeLambda(lambdaMethodName);
        CriteriaQueryStructure structure = generateCriteriaQuery(expression);
        assertCriteriaMethodCalled(structure, "sqrt");
    }
}
```

**Note:** Adapt the test patterns based on what assertion methods are available in `CriteriaQueryTestBase`. Check `BytecodeInspector` capabilities — it may support method name assertions on the generated bytecode.

**Step 2: Run tests**

Run: `mvn test -pl deployment -Dtest="MathOperationsCriteriaTest" -q`
Expected: All tests PASS

**Step 3: Commit**

```bash
git add deployment/src/test/java/io/quarkiverse/qubit/deployment/criteria/MathOperationsCriteriaTest.java
git commit -m "Add criteria generation tests for math operations"
```

---

## Task 13: JpqlGenerator — Math Function JPQL

**Files:**
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/devui/JpqlGenerator.java`

**Step 1: Add MathFunction case to `expressionToJpql()` switch (around line 227)**

Replace the placeholder with:

```java
case LambdaExpression.MathFunction mathFunc -> mathFunctionToJpql(mathFunc);
```

**Step 2: Add the helper method**

```java
private String mathFunctionToJpql(LambdaExpression.MathFunction mathFunc) {
    String operand = expressionToJpql(mathFunc.operand());
    return switch (mathFunc.op()) {
        case ABS -> "ABS(" + operand + ")";
        case NEG -> "-(" + operand + ")";
        case SQRT -> "SQRT(" + operand + ")";
        case SIGN -> "SIGN(" + operand + ")";
        case CEILING -> "CEILING(" + operand + ")";
        case FLOOR -> "FLOOR(" + operand + ")";
        case EXP -> "EXP(" + operand + ")";
        case LN -> "LN(" + operand + ")";
        case POWER -> {
            String second = expressionToJpql(mathFunc.secondOperand());
            yield "POWER(" + operand + ", " + second + ")";
        }
        case ROUND -> {
            String second = expressionToJpql(mathFunc.secondOperand());
            yield "ROUND(" + operand + ", " + second + ")";
        }
    };
}
```

**Step 3: Remove the old `case "abs"`, `case "sqrt"`, `case "mod"` from `methodCallToJpql()`** (lines 368-374)

These are now handled by the `MathFunction` case. However, only remove them if nothing else generates `MethodCall` with these names — which shouldn't happen after our changes. Leave them for backward compatibility if unsure.

**Step 4: Compile and test**

Run: `mvn test -pl deployment -Dtest="JpqlGeneratorTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/devui/JpqlGenerator.java
git commit -m "Add JPQL generation for MathFunction expressions"
```

---

## Task 14: JavaSourceGenerator — Math Function Java Source

**Files:**
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/devui/JavaSourceGenerator.java`

**Step 1: Add MathFunction case to `expressionToJava()` switch (around line 65)**

```java
case LambdaExpression.MathFunction mathFunc -> mathFunctionToJava(mathFunc, param);
```

**Step 2: Add the helper method**

```java
private String mathFunctionToJava(LambdaExpression.MathFunction mathFunc, String param) {
    String operand = expressionToJava(mathFunc.operand(), param);
    return switch (mathFunc.op()) {
        case ABS -> "Math.abs(" + operand + ")";
        case NEG -> "-(" + operand + ")";
        case SQRT -> "Math.sqrt(" + operand + ")";
        case SIGN -> "Integer.signum(" + operand + ")";
        case CEILING -> "Math.ceil(" + operand + ")";
        case FLOOR -> "Math.floor(" + operand + ")";
        case EXP -> "Math.exp(" + operand + ")";
        case LN -> "Math.log(" + operand + ")";
        case POWER -> {
            String second = expressionToJava(mathFunc.secondOperand(), param);
            yield "Math.pow(" + operand + ", " + second + ")";
        }
        case ROUND -> {
            String second = expressionToJava(mathFunc.secondOperand(), param);
            yield "Qubit.round(" + operand + ", " + second + ")";
        }
    };
}
```

**Step 3: Compile and test**

Run: `mvn test -pl deployment -Dtest="JavaSourceGeneratorTest" -q`
Expected: PASS

**Step 4: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/devui/JavaSourceGenerator.java
git commit -m "Add Java source generation for MathFunction expressions"
```

---

## Task 15: BiEntity and Group Expression Builders

**Files:**
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/BiEntityExpressionBuilder.java`
- Modify: `deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/GroupExpressionBuilder.java`

**Step 1: Update BiEntityExpressionBuilder**

Add MathFunction handling to the `generateBiEntityExpressionAsJpaExpression()` switch. The math function should recursively generate its operand(s) using the bi-entity context:

```java
case LambdaExpression.MathFunction mathFunc -> {
    Expr operandExpr = generateBiEntityExpressionAsJpaExpression(bc, cb, rootFirst, rootSecond, capturedValues, mathFunc.operand());
    Expr secondExpr = null;
    if (mathFunc.op().isBinary()) {
        if (mathFunc.op() == LambdaExpression.MathFunction.MathOp.ROUND) {
            secondExpr = generateBiEntityExpression(bc, cb, rootFirst, rootSecond, capturedValues, mathFunc.secondOperand());
        } else {
            secondExpr = generateBiEntityExpressionAsJpaExpression(bc, cb, rootFirst, rootSecond, capturedValues, mathFunc.secondOperand());
        }
    }
    yield MathExpressionBuilder.build(bc, cb, operandExpr, secondExpr, mathFunc.op());
}
```

**Step 2: Update GroupExpressionBuilder**

Add MathFunction handling to relevant switches. If the group switches have a `default -> null` or `default -> throw`, check whether MathFunction needs explicit handling or falls through correctly.

**Step 3: Compile and run all tests**

Run: `mvn test -pl deployment -q`
Expected: All tests PASS

**Step 4: Commit**

```bash
git add deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/BiEntityExpressionBuilder.java \
      deployment/src/main/java/io/quarkiverse/qubit/deployment/generation/expression/GroupExpressionBuilder.java
git commit -m "Handle MathFunction in BiEntity and Group expression builders"
```

---

## Task 16: QubitMath Marker Class

**Files:**
- Create: `runtime/src/main/java/io/quarkiverse/qubit/QubitMath.java`

**Step 1: Create the marker class**

```java
package io.quarkiverse.qubit;

/**
 * Marker class for Qubit math operations that have no direct Java equivalent.
 *
 * <p>Methods in this class are never executed at runtime. During build-time
 * bytecode analysis, calls to these methods are intercepted and replaced
 * with JPA Criteria API expressions.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Rounds salary to 2 decimal places → cb.round(expr, 2)
 * Person.where(p -> QubitMath.round(p.salary, 2) > 50000).toList();
 * }</pre>
 */
public final class QubitMath {

    private QubitMath() {
    }

    /**
     * Rounds a numeric value to the specified number of decimal places.
     *
     * <p>Maps to {@code CriteriaBuilder.round(Expression, Integer)} at build time.
     *
     * @param value the numeric value to round
     * @param decimalPlaces the number of decimal places
     * @return the value unchanged (never executed at runtime)
     */
    public static double round(double value, int decimalPlaces) {
        return value; // Never executed — intercepted at build time
    }

    /**
     * Rounds a numeric value to the specified number of decimal places.
     *
     * @param value the numeric value to round
     * @param decimalPlaces the number of decimal places
     * @return the value unchanged (never executed at runtime)
     */
    public static float round(float value, int decimalPlaces) {
        return value;
    }
}
```

**Step 2: Add QubitMath recognition to MethodInvocationHandler**

In `handleInvokeStatic()`, add after the Math handling block:

```java
// Handle QubitMath.round(value, decimalPlaces) marker method
if (handleQubitMathMethod(ctx, staticInsn)) {
    return;
}
```

And add the handler:

```java
/**
 * Handles QubitMath.round(value, decimalPlaces) marker method.
 */
private boolean handleQubitMathMethod(AnalysisContext ctx, MethodInsnNode staticInsn) {
    if (!staticInsn.owner.equals(JVM_QUBIT_MATH)) {
        return false;
    }
    if (!staticInsn.name.equals(METHOD_ROUND)) {
        return false;
    }
    if (ctx.getStack().size() < 2) {
        return false;
    }
    LambdaExpression decimalPlaces = ctx.pop();
    LambdaExpression operand = ctx.pop();
    ctx.push(LambdaExpression.MathFunction.round(operand, decimalPlaces));
    return true;
}
```

**Step 3: Add QubitMath test lambdas to LambdaTestSources**

```java
public static QuerySpec<TestPerson, Boolean> qubitRound() {
    return (TestPerson p) -> QubitMath.round(p.salary, 2) > 50000;
}
```

Add the import: `import io.quarkiverse.qubit.QubitMath;`

**Step 4: Add bytecode test**

Add to `MathOperationsBytecodeTest`:

```java
@Test
@DisplayName("QubitMath.round(p.salary, 2) produces ROUND node with 2 decimal places")
void qubitRound_producesRoundNodeWithDecimalPlaces() {
    LambdaExpression expr = analyzeLambda("qubitRound");

    LambdaExpression.BinaryOp comparison = (LambdaExpression.BinaryOp) expr;
    assertThat(comparison.left()).isInstanceOf(MathFunction.class);

    MathFunction mathFunc = (MathFunction) comparison.left();
    assertThat(mathFunc.op()).isEqualTo(MathOp.ROUND);
    assertThat(mathFunc.secondOperand()).isInstanceOf(LambdaExpression.Constant.class);

    LambdaExpression.Constant decPlaces = (LambdaExpression.Constant) mathFunc.secondOperand();
    assertThat(decPlaces.value()).isEqualTo(2);
}
```

**Step 5: Compile and test**

Run: `mvn test -pl deployment -Dtest="MathOperationsBytecodeTest" -q`
Expected: PASS

**Step 6: Commit**

```bash
git add runtime/src/main/java/io/quarkiverse/qubit/QubitMath.java \
      deployment/src/main/java/io/quarkiverse/qubit/deployment/analysis/instruction/MethodInvocationHandler.java \
      deployment/src/test/java/io/quarkiverse/qubit/deployment/testutil/LambdaTestSources.java \
      deployment/src/test/java/io/quarkiverse/qubit/deployment/bytecode/MathOperationsBytecodeTest.java
git commit -m "Add QubitMath marker class for round(value, decimalPlaces)"
```

---

## Task 17: Full Test Suite Verification

**Step 1: Run complete deployment module tests**

Run: `mvn test -pl deployment`
Expected: All tests PASS, including new math tests and all existing tests (no regression)

**Step 2: Run complete integration tests**

Run: `mvn test -pl integration-tests`
Expected: All integration tests PASS

**Step 3: Run full project build**

Run: `mvn verify`
Expected: Full build PASSES

**Step 4: Commit any remaining fixes**

If any tests fail, fix them and commit with descriptive messages.

---

## Task 18: Integration Tests — End-to-End Math Queries

**Files:**
- Create: `integration-tests/src/test/java/io/quarkiverse/qubit/it/math/AbstractMathOperationsTest.java`

**Step 1: Write integration test**

Follow the pattern from existing integration tests (e.g., `AbstractComparisonTest`):

```java
package io.quarkiverse.qubit.it.math;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for math function queries.
 * Tests that math functions generate valid SQL and return correct results.
 */
@DisplayName("Math operations")
public abstract class AbstractMathOperationsTest {

    @Test
    @DisplayName("Math.abs() filters by absolute value")
    void mathAbs_filtersCorrectly() {
        // Person.where(p -> Math.abs(p.age - 30) < 5).toList()
        // Should find people with ages 26-34
        var results = personOps().where(p -> Math.abs(p.age - 30) < 5).toList();
        assertThat(results).allMatch(p -> Math.abs(p.getAge() - 30) < 5);
    }

    @Test
    @DisplayName("Math.sqrt() in predicate")
    void mathSqrt_worksInPredicate() {
        var results = personOps().where(p -> Math.sqrt(p.salary) > 200).toList();
        assertThat(results).allMatch(p -> Math.sqrt(p.getSalary()) > 200);
    }

    @Test
    @DisplayName("unary negation in predicate")
    void unaryNegation_worksInPredicate() {
        var results = personOps().where(p -> -p.age < -25).toList();
        assertThat(results).allMatch(p -> p.getAge() > 25);
    }

    // Add similar tests for: ceil, floor, exp, log, pow, round, sign
}
```

**Note:** The exact API (`personOps()`, method names, etc.) depends on the integration test infrastructure. Check existing integration tests for the correct pattern.

**Step 2: Run integration tests**

Run: `mvn test -pl integration-tests -Dtest="*MathOperations*"`
Expected: PASS

**Step 3: Commit**

```bash
git add integration-tests/
git commit -m "Add end-to-end integration tests for math operations"
```

---

## Task 19: Final Verification and Cleanup

**Step 1: Run full build**

Run: `mvn verify`
Expected: SUCCESS

**Step 2: Review all changes**

Run: `git log --oneline main..HEAD`

Verify commits are clean and focused:
1. MathFunction AST node
2. Placeholder switch cases
3. AST validation tests
4. QubitConstants
5. MethodDescriptors
6. Negation bytecode handling
7. Math method recognition
8. Bytecode analysis tests
9. CapturedVariableHelper
10. MathExpressionBuilder
11. CriteriaExpressionGenerator wiring
12. Criteria generation tests
13. JpqlGenerator
14. JavaSourceGenerator
15. BiEntity/Group builders
16. QubitMath marker class
17. Integration tests

**Step 3: Squash or keep granular commits per preference**

---

## Reference: File Paths Summary

| Category | File | Action |
|---|---|---|
| **AST** | `deployment/.../ast/LambdaExpression.java` | Add `MathFunction` record |
| **Analysis** | `deployment/.../instruction/MethodInvocationHandler.java` | Recognize `Math.*` INVOKESTATIC |
| **Analysis** | `deployment/.../instruction/ArithmeticInstructionHandler.java` | Handle INEG/LNEG/FNEG/DNEG |
| **Analysis** | `deployment/.../common/OpcodeClassifier.java` | Add `isNegationOpcode()` |
| **Analysis** | `deployment/.../analysis/CapturedVariableHelper.java` | Handle `MathFunction` |
| **Constants** | `runtime/.../internal/QubitConstants.java` | Add math constants |
| **Generation** | `deployment/.../generation/MethodDescriptors.java` | Add CB_ABS, CB_NEG, etc. |
| **Generation** | `deployment/.../generation/expression/MathExpressionBuilder.java` | **CREATE** |
| **Generation** | `deployment/.../generation/CriteriaExpressionGenerator.java` | Wire `MathFunction` |
| **Generation** | `deployment/.../generation/expression/BiEntityExpressionBuilder.java` | Handle `MathFunction` |
| **Generation** | `deployment/.../generation/expression/GroupExpressionBuilder.java` | Handle `MathFunction` |
| **DevUI** | `deployment/.../devui/JpqlGenerator.java` | JPQL for math functions |
| **DevUI** | `deployment/.../devui/JavaSourceGenerator.java` | Java source for math functions |
| **Runtime** | `runtime/.../QubitMath.java` | **CREATE** marker class |
| **Tests** | `deployment/.../ast/AstNodeValidationTest.java` | Validation tests |
| **Tests** | `deployment/.../testutil/LambdaTestSources.java` | Test lambdas |
| **Tests** | `deployment/.../bytecode/MathOperationsBytecodeTest.java` | **CREATE** |
| **Tests** | `deployment/.../criteria/MathOperationsCriteriaTest.java` | **CREATE** |
| **Tests** | `integration-tests/.../math/AbstractMathOperationsTest.java` | **CREATE** |
