# Testing Guidelines

## Overview

This directory contains testing documentation for the Quarkus Qubit extension.

## Quick Reference

| Metric | Current | Target |
|--------|---------|--------|
| Line Coverage | **91%** | 85% |
| Branch Coverage | 82.5% | 80% |
| Mutation Score | **82%** | 90% |
| Test Strength | **86%** | - |

## Running Tests

```bash
# Unit tests only
mvn test -pl deployment

# Unit + integration tests
mvn verify

# With coverage enforcement (using profile)
mvn verify -Pcoverage

# Mutation testing (using profile)
mvn test -Pmutation -pl deployment

# Alternative: Direct goal invocation
mvn pitest:mutationCoverage -pl deployment
```

## Viewing Reports

```bash
# JaCoCo coverage
open deployment/target/site/jacoco/index.html

# Pitest mutations
open deployment/target/pit-reports/index.html
```

## Documentation

| Document | Purpose |
|----------|---------|
| [coverage-baseline.md](coverage-baseline.md) | Current metrics and improvement plan |
| [exclusion-patterns.md](exclusion-patterns.md) | What's excluded from coverage and why |
| [test-fixtures.md](test-fixtures.md) | Test fixtures for reducing boilerplate |
| [mutation-testing.md](mutation-testing.md) | Pitest mutation testing guide |

## Three-Tier Coverage Model

1. **JaCoCo** - Line and branch coverage (verifies code execution)
2. **Pitest** - Mutation testing (verifies test effectiveness)
3. **Integration Tests** - End-to-end behavior verification

## Key Principles

1. **Exclusions require justification** - Document why code is excluded
2. **IT-covered code can be excluded from unit tests** - But must be verified
3. **Mutation score matters** - High line coverage with low mutation score = weak tests
4. **Test behavior, not implementation** - Focus on what code does, not how
