# Quality gates

DungeonHero uses one Java formatting convention: Google Java Format through
Spotless. Java identifiers follow lower camel case for methods, parameters,
locals, and fields; upper camel case for types; and upper snake case for
constants. Checkstyle enforces these naming and import rules.

Run the local gates with:

```powershell
.\gradlew.bat clean test
.\gradlew.bat check
```

`check` runs Spotless formatting verification, Checkstyle, tests, and the
`dependencyAudit` task. The audit rejects dynamic or unpinned external
dependencies. Gradle dependency verification is enabled by
`gradle/verification-metadata.xml`; update its SHA-256 entries only when a
dependency change has been reviewed:

```powershell
.\gradlew.bat --write-verification-metadata sha256 dependencies
```

The optional `dependencyCheckAnalyze` task runs OWASP Dependency-Check against
the current vulnerability database and fails at CVSS 7.0 or higher. It is
intended for CI or scheduled security runs because the database refresh is an
external network operation.

This pass changes no public plugin API, command, permission, or runtime
configuration key. Existing public API and configuration contracts remain
recorded in `SAFETY_BASELINE.md`.
