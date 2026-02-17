# JPA Standard Math Functions — Design

**Date**: 2026-02-17
**ROADMAP tasks**: T1-1 (Math.abs), T2-4 (Additional Math Functions)
**Scope**: All standard JPA CriteriaBuilder math functions (no `cb.function()`)

---

## Scope — 10 Functions

| # | CriteriaBuilder | Java Source | Bytecode | JPA Ver |
|---|---|---|---|---|
| 1 | `cb.abs(x)` | `Math.abs(p.age)` | INVOKESTATIC `Math.abs` | 2.0 |
| 2 | `cb.neg(x)` | `-p.age` | INEG/LNEG/FNEG/DNEG | 2.0 |
| 3 | `cb.sqrt(x)` | `Math.sqrt(p.salary)` | INVOKESTATIC `Math.sqrt` | 2.0 |
| 4 | `cb.sign(x)` | `Integer.signum(p.age)` | INVOKESTATIC `Integer.signum` | 3.1 |
| 5 | `cb.ceiling(x)` | `Math.ceil(p.rating)` | INVOKESTATIC `Math.ceil` | 3.1 |
| 6 | `cb.floor(x)` | `Math.floor(p.rating)` | INVOKESTATIC `Math.floor` | 3.1 |
| 7 | `cb.exp(x)` | `Math.exp(p.rate)` | INVOKESTATIC `Math.exp` | 3.1 |
| 8 | `cb.ln(x)` | `Math.log(p.value)` | INVOKESTATIC `Math.log` | 3.1 |
| 9 | `cb.power(x,y)` | `Math.pow(p.base, exp)` | INVOKESTATIC `Math.pow` | 3.1 |
| 10 | `cb.round(x,n)` | `Qubit.round(p.salary, 2)` / `Math.round(p.salary)` | INVOKESTATIC | 3.1 |

JPA 3.2 added no new math functions (focused on `cast`, `left`, `right`, `replace`, set operations).

## Approach: Dedicated MathFunction AST Node

### Why not reuse MethodCall?

The codebase uses dedicated AST nodes when operations have distinct semantics:
- `BinaryOp` for arithmetic operators (`+`, `-`, `*`, `/`, `%`)
- `UnaryOp` for logical NOT (`!`)
- `InExpression` for SQL IN clauses
- `MemberOfExpression` for MEMBER OF
- `Conditional` for ternary expressions
- `InstanceOf` for type checks
- Subquery types for subqueries

`MethodCall` is reserved for actual Java instance methods on objects: `p.name.toLowerCase()`, `p.salary.add(bonus)`, `p.birthDate.getYear()`.

`Math.abs(x)` is a static function wrapping an expression — semantically closer to `UnaryOp` than to `MethodCall`. Using `MethodCall` would bend `target` semantics (target = operand, not the object being called on) and rely on string-based dispatch instead of type-safe enums.

### MathFunction AST Node

```java
/** Mathematical function applied to expression(s). */
record MathFunction(
    MathOp op,
    LambdaExpression operand,
    @Nullable LambdaExpression secondOperand
) implements LambdaExpression {

    enum MathOp {
        ABS, NEG, SQRT, SIGN, CEILING, FLOOR, EXP, LN,   // Unary
        POWER, ROUND;                                       // Binary

        public boolean isBinary() { return this == POWER || this == ROUND; }
    }

    // Compact constructor validates arity:
    // - Binary ops require secondOperand
    // - Unary ops reject secondOperand

    // Factory methods:
    // MathFunction.abs(expr), MathFunction.neg(expr), MathFunction.sqrt(expr), ...
    // MathFunction.power(base, exp), MathFunction.round(expr, decimalPlaces)
}
```

Single sealed type member = one new `case` in each exhaustive switch.

## Architecture — Data Flow

```
Lambda: Person.where(p -> Math.abs(p.age - 30) < 5)

Phase 1: Bytecode Analysis
  INVOKESTATIC java/lang/Math.abs:(I)I
    → MethodInvocationHandler.handleInvokeStatic()
      → recognizes "java/lang/Math" owner
      → pops 1 arg: BinaryOp(FieldAccess("age"), SUB, Constant(30))
      → pushes MathFunction.abs(BinaryOp(...))

Phase 2: Code Generation
  CriteriaExpressionGenerator (main switch)
    → case MathFunction math → MathExpressionBuilder.build(math)
      → cb.abs(generateExpression(math.operand()))
        → JPA: ABS(p.age - 30)
```

No MethodCallHandlerChain involvement — MathFunction bypasses the chain entirely.

## Files to Create

1. **`deployment/.../generation/expression/MathExpressionBuilder.java`**
   - Generates `cb.abs()`, `cb.neg()`, `cb.sqrt()`, `cb.sign()`, `cb.ceiling()`,
     `cb.floor()`, `cb.exp()`, `cb.ln()`, `cb.power()`, `cb.round()` calls
   - Unary functions: `cb.xxx(generateExpression(operand))`
   - Binary functions: `cb.xxx(generateExpression(operand), generateExpression(secondOperand))`
   - `round` special case: second operand is always Integer

2. **`runtime/.../QubitMath.java`**
   - Marker class with static methods (never executed at runtime)
   - `public static <T extends Number> T round(T value, int decimalPlaces) { return value; }`
   - Recognized during bytecode analysis → `MathFunction.round(expr, Constant(n))`

3. **Tests**
   - Bytecode analysis tests: verify each Math method → correct MathFunction node
   - Code generation tests: verify MathFunction → correct cb.xxx() output
   - Integration tests: end-to-end queries with math functions
   - Property-based tests for AST node validation

## Files to Modify

| File | Change |
|---|---|
| `LambdaExpression.java` | Add `MathFunction` record + `MathOp` enum to sealed interface |
| `MethodInvocationHandler.java` | Recognize `java/lang/Math` INVOKESTATIC → push `MathFunction` nodes. Also `Integer.signum`, `Long.signum`, `QubitMath.round` |
| `ArithmeticInstructionHandler.java` | Handle INEG/LNEG/FNEG/DNEG → push `MathFunction.neg(operand)` |
| `OpcodeClassifier.java` | Add `isNegationOpcode(int)` method |
| `QubitConstants.java` | Add `JVM_JAVA_LANG_MATH`, `JVM_JAVA_LANG_INTEGER`, `JVM_JAVA_LANG_LONG`, math method name constants |
| `MethodDescriptors.java` | Add `CB_ABS`, `CB_NEG`, `CB_SQRT`, `CB_SIGN`, `CB_CEILING`, `CB_FLOOR`, `CB_EXP`, `CB_LN`, `CB_POWER`, `CB_ROUND` |
| `CriteriaExpressionGenerator.java` | Add `case MathFunction` → delegate to `MathExpressionBuilder` |
| `CapturedVariableHelper.java` | Add `case MathFunction` → resolve captured vars in operand(s) |
| `JpqlGenerator.java` | Add `case MathFunction` → JPQL strings (ABS, SQRT, SIGN, CEILING, FLOOR, EXP, LN, POWER, ROUND) |
| `JavaSourceGenerator.java` | Add `case MathFunction` → Java source (Math.abs, Math.sqrt, etc.) |
| `BiEntityExpressionBuilder.java` | Add `case MathFunction` if it has exhaustive switch |
| `GroupExpressionBuilder.java` | Add `case MathFunction` if it has exhaustive switch |

## Design Decisions

1. **`sign()` mapping**: Recognize `Integer.signum(x)`, `Long.signum(x)`, and `Math.signum(x)`. All map to `cb.sign()`.

2. **`round()` dual API**:
   - `Math.round(p.salary)` → `MathFunction.round(expr, Constant(0))` → `cb.round(expr, 0)` (integer rounding)
   - `QubitMath.round(p.salary, 2)` → `MathFunction.round(expr, Constant(2))` → `cb.round(expr, 2)` (arbitrary precision)

3. **Negation**: INEG/LNEG/FNEG/DNEG bytecode → `MathFunction.neg(operand)` → `cb.neg(expr)`. Handled in `ArithmeticInstructionHandler` (unary, pops 1 value), not `MethodInvocationHandler`.

4. **`Math.log()` → `cb.ln()`**: Java's `Math.log()` = natural logarithm = JPA's `cb.ln()`. Naming difference is transparent.

5. **`Math.ceil()` → `cb.ceiling()`**: Java abbreviation → JPA full name. Transparent.

6. **No `cb.function()` used**: All 10 functions are standard JPA CriteriaBuilder methods. Trigonometric functions (sin, cos, tan) are Hibernate-only extensions and out of scope.

## Sources

- [Jakarta Persistence 3.2 CriteriaBuilder Javadoc](https://jakarta.ee/specifications/persistence/3.2/apidocs/jakarta.persistence/jakarta/persistence/criteria/criteriabuilder)
- [CriteriaBuilder.java source (GitHub)](https://github.com/jakartaee/persistence/blob/master/api/src/main/java/jakarta/persistence/criteria/CriteriaBuilder.java)
- [Jakarta Persistence 3.1 new features](https://newsroom.eclipse.org/eclipse-newsletter/2022/march/what%E2%80%99s-new-jakarta-persistence-31)
- [A summary of Jakarta Persistence 3.2](https://in.relation.to/2024/04/01/jakarta-persistence-3/)
