# Contributing

Thanks for helping. This is the backend (Java 21, Spring Boot 4.1, PostgreSQL 16). The frontend lives in [subscription-master-frontend](https://github.com/mirkamol-code/subscription-master-frontend).

## Before you start

- For anything bigger than a small fix, open an issue first so we can agree on the approach.
- Security problems go through [SECURITY.md](SECURITY.md), never a public issue.
- Use the domain terms in [GLOSSARY.md](GLOSSARY.md).

## Set up

Follow [Run and test locally](README.md#run-and-test-locally). You need Java 21, Maven 3.9+, Docker, and your own Google OAuth client for manual testing. The test suite needs only Docker: Testcontainers starts PostgreSQL and WireMock replays recorded Google responses.

## Making a change

1. Fork the repo and create a branch off `main` with a plain kebab-case name, for example `price-change-detection`.
2. Write a failing test first, then the code. Integration tests end in `IT` and extend the shared test setup; unit tests end in `Test`. Mark test sections with `// given`, `// when`, `// then`.
3. **Database changes are always a new Flyway migration** (`src/main/resources/db/migration/V<next>__Short_Description.sql`). Never edit an existing migration.
4. Keep secrets out of code and config files. New settings get an environment variable, a row in the README table and an entry in `.env.example`.
5. Run the full build before pushing:

   ```bash
   mvn clean verify
   ```

## Commits and pull requests

- Commit messages and PR titles follow [Conventional Commits](https://www.conventionalcommits.org/): imperative, lowercase, under 50 characters, for example `feat(trial): estimate trial end date`.
- One topic per pull request. Describe what changed, why, and how you tested it.
- CI must pass. A maintainer reviews every PR; expect questions.

By contributing, you agree that your contributions are licensed under the [MIT License](LICENSE).
