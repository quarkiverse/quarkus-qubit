# QUBIT Extension Configuration Reference

This document provides a complete reference for all build-time configuration options available in the QUBIT extension.

## Overview

QUBIT uses Quarkus build-time configuration via SmallRye Config. All properties are prefixed with `quarkus.qubit` and are applied during the build phase (not at runtime).

Configuration can be specified in `application.properties`, `application.yaml`, or any other Quarkus-supported configuration source.

## Configuration Properties

### Scanning Configuration

Controls how QUBIT scans your codebase for lambda expressions during build time.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `quarkus.qubit.scanning.exclude-packages` | `List<String>` | `java.,jakarta.` | Package prefixes to exclude from lambda scanning. Classes in these packages are skipped during analysis. |
| `quarkus.qubit.scanning.include-packages` | `List<String>` | *(none)* | Package prefixes to include (overrides exclude). Useful for including specific framework packages containing lambda queries. |
| `quarkus.qubit.scanning.scan-test-classes` | `boolean` | `true` | Whether to scan test classes (classes in paths containing `.it.` or `.test.`). |

### Generation Configuration

Controls how QUBIT generates query executor classes.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `quarkus.qubit.generation.class-name-prefix` | `String` | `QueryExecutor_` | Prefix for generated executor class names. Generated classes are named: `{targetPackage}.{classNamePrefix}{hash}` |
| `quarkus.qubit.generation.target-package` | `String` | `io.quarkiverse.qubit.generated` | Package for generated executor classes. |

### Logging Configuration

Controls logging verbosity during build-time processing. Useful for debugging lambda analysis issues.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `quarkus.qubit.logging.level` | `String` | `INFO` | Log level for QUBIT build processing. Options: `OFF`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE` |
| `quarkus.qubit.logging.log-scanned-classes` | `boolean` | `false` | Log each scanned class (very verbose). Enable for debugging class scanning issues. |
| `quarkus.qubit.logging.log-generated-classes` | `boolean` | `true` | Log each generated executor class. Useful for tracking which query executors are created. |
| `quarkus.qubit.logging.log-deduplication` | `boolean` | `false` | Log lambda deduplication events. Shows when identical lambdas reuse existing executors. |
| `quarkus.qubit.logging.log-bytecode-analysis` | `boolean` | `false` | Log detailed bytecode analysis steps (TRACE-level detail). Enable for debugging bytecode parsing issues. |

## Example Configurations

### Minimal Configuration (Defaults)

No configuration needed—QUBIT works out of the box with sensible defaults:

```properties
# application.properties
# All defaults are applied automatically
```

### Development Configuration

Enable verbose logging for development and debugging:

```properties
# application.properties
quarkus.qubit.logging.level=DEBUG
quarkus.qubit.logging.log-scanned-classes=true
quarkus.qubit.logging.log-deduplication=true
quarkus.qubit.logging.log-bytecode-analysis=true
```

### Production Configuration

Minimal logging, exclude test classes:

```properties
# application.properties
quarkus.qubit.scanning.scan-test-classes=false
quarkus.qubit.logging.level=WARN
quarkus.qubit.logging.log-generated-classes=false
```

### Custom Package Configuration

Include specific framework packages while excluding standard library:

```properties
# application.properties
quarkus.qubit.scanning.exclude-packages=java.,javax.,sun.,com.sun.
quarkus.qubit.scanning.include-packages=jakarta.validation.,org.hibernate.validator.
```

### Custom Generation Configuration

Use a custom package and prefix for generated classes:

```properties
# application.properties
quarkus.qubit.generation.class-name-prefix=LambdaQuery$
quarkus.qubit.generation.target-package=com.mycompany.generated.queries
```

### YAML Configuration

All properties can also be specified in `application.yaml`:

```yaml
quarkus:
  qubit:
    scanning:
      exclude-packages:
        - java.
        - jakarta.
        - javax.
      include-packages:
        - jakarta.validation.
      scan-test-classes: true
    generation:
      class-name-prefix: QueryExecutor_
      target-package: io.quarkiverse.qubit.generated
    logging:
      level: INFO
      log-scanned-classes: false
      log-generated-classes: true
      log-deduplication: false
      log-bytecode-analysis: false
```

## Configuration Behavior

### Include/Exclude Precedence

When both `include-packages` and `exclude-packages` match a class:
- **Include takes precedence** over exclude
- This allows selective inclusion of packages within an excluded namespace

Example:
```properties
quarkus.qubit.scanning.exclude-packages=jakarta.
quarkus.qubit.scanning.include-packages=jakarta.validation.
```
Result: All `jakarta.*` classes are excluded **except** `jakarta.validation.*` classes.

### Build-Time Only

All QUBIT configuration properties are evaluated at **build time only**. Changes require rebuilding the application:

```bash
mvn clean package
# or
./gradlew clean build
```

### Default Package Exclusions

By default, QUBIT excludes `java.` and `jakarta.` packages because:
- Standard library lambdas (like `Stream` operations) are not JPA queries
- Framework lambdas should not be analyzed as entity queries
- Reduces build time by skipping irrelevant classes

## Troubleshooting

### Lambda Not Detected

If your lambda query isn't being transformed:

1. **Check package exclusions**: Ensure your package isn't excluded
   ```properties
   quarkus.qubit.logging.log-scanned-classes=true
   ```

2. **Enable verbose logging**: See which classes are scanned
   ```properties
   quarkus.qubit.logging.level=DEBUG
   ```

3. **Check test classes**: If lambda is in a test, ensure scanning is enabled
   ```properties
   quarkus.qubit.scanning.scan-test-classes=true
   ```

### Duplicate Executor Generation

If you see warnings about duplicate executors:

1. **Enable deduplication logging**:
   ```properties
   quarkus.qubit.logging.log-deduplication=true
   ```

2. **Check for identical lambdas**: QUBIT deduplicates identical lambda expressions automatically

### Bytecode Analysis Errors

For debugging bytecode parsing issues:

```properties
quarkus.qubit.logging.level=TRACE
quarkus.qubit.logging.log-bytecode-analysis=true
```

## See Also

- [README.md](../README.md) - Getting started and usage examples
- [Quarkus Configuration Guide](https://quarkus.io/guides/config-reference)
- [SmallRye Config Documentation](https://smallrye.io/smallrye-config/)
