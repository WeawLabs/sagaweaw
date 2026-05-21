# Contributing to Sagaweaw

Thank you for your interest in contributing!

## Reporting bugs

Open an [issue](https://github.com/amosjuda/sagaweaw/issues/new?template=bug_report.md) with:
- Sagaweaw version
- Spring Boot version and database (PostgreSQL / MySQL / H2)
- Minimal reproduction (saga class + stack trace)

## Suggesting features

Open an [issue](https://github.com/amosjuda/sagaweaw/issues/new?template=feature_request.md) describing the use case first. Large changes should be discussed before a PR is opened.

## Submitting a pull request

1. Fork the repository and create a branch from `main`
2. Make your changes — keep the scope small and focused
3. Run the test suite: `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw verify`
4. Open a PR against `main` — CI must pass before review

## Code style

- Java 21, Spring Boot 4.x
- No wildcard imports
- No comments explaining *what* the code does — only *why* when non-obvious
- Lombok `@Slf4j` and `@Builder` are available in all modules

## Local setup

```bash
git clone https://github.com/amosjuda/sagaweaw.git
cd sagaweaw
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw test          # unit tests
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./mvnw verify         # + integration tests (requires Docker)
```

## License

By contributing you agree that your changes will be licensed under the [Apache 2.0 License](LICENSE).
