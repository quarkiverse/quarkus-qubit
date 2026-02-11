# Contributing to Quarkus Qubit

Thank you for your interest in contributing to Quarkus Qubit!

## Building from Source

```bash
git clone https://github.com/quarkiverse/quarkus-qubit.git
cd quarkus-qubit
./mvnw clean install
```

This runs unit tests against an in-memory H2 database by default.

## Integration Tests

Integration tests run against real databases using Testcontainers.
To run them locally:

```bash
./mvnw clean verify -pl integration-tests
```

The CI pipeline tests against PostgreSQL, MySQL, and MariaDB.

## Pull Requests

Before submitting a PR, please ensure:

- All existing tests pass (`./mvnw clean verify`)
- New features include appropriate tests
- Documentation is updated for user-facing changes (see `docs/` for Antora sources)

## Reporting Issues

Use [GitHub Issues](https://github.com/quarkiverse/quarkus-qubit/issues) to report bugs or request features.
Include a minimal reproducer when reporting bugs.
