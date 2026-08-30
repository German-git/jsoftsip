# Testing

This document describes how to run the JSoftSIP test suite, the test structure per module, and important exclusions.

## Test Execution via Maven

JSoftSIP uses JUnit Jupiter (JUnit 5) as the testing framework. Tests are executed through Maven Surefire.

### Full Suite

Run all tests across all modules:

```bash
mvn test
```

### Per-Module Execution

Run tests for a specific module. `-am` (also-make) is required so Maven builds dependency modules (notably `core`) first — without it, a stale local `core` artifact causes `NoClassDefFoundError` for recently added core classes (see `docs/build-and-package.md`):

```bash
mvn test -pl core -am
mvn test -pl native-bridge -am
mvn test -pl ui -am
mvn test -pl launcher -am
```

### Individual Test Classes

Run a single test class within a module:

```bash
# Core
mvn test -pl core -am -Dtest=DefaultRegistrationServiceTest

# Native-bridge
mvn test -pl native-bridge -am -Dtest=BaresipConfigPatcherTest

# UI
mvn test -pl ui -am -Dtest=CallCardTest

# Launcher
mvn test -pl launcher -am -Dtest=JSoftSipContextTest
```

## JUnit Jupiter Configuration

- **Framework**: JUnit Jupiter 5.13.4
- **Runner**: Maven Surefire Plugin 3.5.6
- **Scope**: `test` for most modules; `native-bridge` and `launcher` both use `compile` + `optional` so tests can run from both Maven and IntelliJ without leaking JUnit into production builds

### Module-Specific Surefire Configuration

#### `core`

Tests run on the classpath (`useModulePath=false`) because the module descriptor intentionally stays binding-free. The logging emission tests need `logback-classic`, which must not appear as a `requires` in the JPMS descriptor of an API-only logging module.

#### `native-bridge`

JUnit runs on the module path while the test classes themselves execute from the unnamed module. The following JVM arguments are added automatically:

```
--add-opens org.junit.platform.commons/org.junit.platform.commons.util=ALL-UNNAMED
--add-opens org.junit.platform.commons/org.junit.platform.commons.logging=ALL-UNNAMED
```

#### `launcher`

Tests run from the unnamed module, so the surefire `argLine` opens the JUnit platform's internal packages for reflection:

```
--add-opens org.junit.platform.commons/org.junit.platform.commons.logging=ALL-UNNAMED
--add-opens org.junit.platform.commons/org.junit.platform.commons.util=ALL-UNNAMED
```

## Mock Backend for Testing

The `MOCK` backend enables testing the UI and core services without a live Baresip process.

Activate it via system property:

```bash
mvn -pl launcher javafx:run -Djsoftsip.sip.backend=mock
```

Or when running tests that exercise UI components:

```bash
mvn test -pl ui -Djsoftsip.sip.backend=mock
```

The mock backend:

- Uses `MockSipClient` (a no-op client that simulates events)
- Uses `MockCallService` (simulates call lifecycle without a real SIP stack)
- Hides Baresip-specific settings tabs automatically

## Important Test Exclusions

The following tests require a real Baresip process running at `127.0.0.1:4444` and are **excluded from the default build** so that `mvn test` stays green on machines without Baresip:

| Excluded Test Class                  | Reason                                           |
|--------------------------------------|--------------------------------------------------|
| `BaresipCallIntegrationTest`         | Integration tests against a live Baresip process |
| `CtrlTcpConnectionTest`              | Direct TCP connection tests to Baresip ctrl_tcp  |

### Running Excluded Tests Manually

If Baresip is running locally with `ctrl_tcp` enabled on port `4444`, you can run these tests explicitly:

```bash
mvn test -pl native-bridge -am -Dtest=BaresipCallIntegrationTest
mvn test -pl native-bridge -am -Dtest=CtrlTcpConnectionTest
```

Additionally, `BaresipSipClientTest` contains live-Baresip methods that are marked `@Disabled` by default so the `FakeCtrlConnection` unit tests always run while the live methods stay skipped.

## Known Test Debt

The suite still carries deliberate, bounded compromises. They are
listed here so future reviews do not rediscover them:

- **`Thread.sleep` sites**: about twenty sleeps remain across eight
  test classes (debounce windows, executor drains, process exit
  waits). All are bounded and small (< 1 s each); replacing them
  with latches is welcome but not blocking.
- **Launcher coverage**: `JSoftSipApplication` and `StartupSplash`
  have no dedicated tests. Their close-request behaviour is covered
  indirectly by `ShutdownCleanupTest` and `ExitConfirmationPolicyTest`
  at the policy level; exercising the real stages requires a full FX
  harness that the suite does not provide yet.
- **Live-Baresip methods**: see the exclusions table above.
- The former `@Disabled` process-leak regression test in
  `BaresipLauncherTest` is enabled again: the ctrl_tcp wait budget
  became injectable (`BaresipLauncher` package-private constructor),
  so it runs in milliseconds.

## Test Isolation Properties

| Property | Purpose |
|----------|---------|
| `jsoftsip.config.dir` | Redirects the whole configuration directory (database, master key, baresip config) for tests or custom deployments. Tests set it per-fixture with a `@TempDir` and restore/clear it afterwards; classes that mutate it run with `@Execution(SAME_THREAD)`. See `ConfigDirectoryResolver`. |

## Planned Dependency Moves

- **JUnit 5.13.4 → 6.x**: planned once the surrounding ecosystem
  (surefire support, vintage engine, third-party listeners used by
  the FX toolkit tests) ships stable 6.x artifacts. Not a drop-in
  bump today; revisit per release cycle.
- slf4j/logback minor bumps are routine and can ride along any
  release; no API impact expected.

