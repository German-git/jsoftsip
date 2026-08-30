# Build and Package

This document explains how to build JSoftSIP from source, run tests, and produce a distributable package.

## Build from Source

The project is a standard Maven multi-module build.

```bash
mvn clean install
```

This compiles all modules in the correct order:

1. `core`
2. `native-bridge`
3. `ui`
4. `launcher`
5. `packager`

### Prerequisites

- JDK 21 (the build targets Java 21 source and binary level)
- Maven 3.9+
- Linux (the Baresip backend and packaging profile are Linux-only)
- Baresip installed on the system if running in production mode

## Run Tests

### Full Suite

```bash
mvn test
```

This runs all unit tests across every module.

### Per-Module Tests

Use `-am` (also-make) so Maven builds the required dependencies before running the selected module. For example, `native-bridge`, `ui`, and `launcher` all depend on `core`, so running them without `-am` may fail with `NoClassDefFoundError` if the local `core` artifact is stale.

```bash
mvn test -pl core -am
mvn test -pl native-bridge -am
mvn test -pl ui -am
mvn test -pl launcher -am
```

### Individual Test Classes

```bash
# Core module
mvn test -pl core -am -Dtest=CallDurationFormatterTest

# Native-bridge module
mvn test -pl native-bridge -am -Dtest=BaresipCallEventParserTest

# UI module
mvn test -pl ui -am -Dtest=DialerDialogControllerTest
```

## Code Formatting

The project uses [Spotless](https://github.com/diffplug/spotless) with an Eclipse formatter profile to keep Java source code consistent. The profile is defined in `.spotless/eclipse-formatter.xml` and is wired into the build through the `spotless-maven-plugin` in the root `pom.xml`.

### Apply Formatting

```bash
mvn spotless:apply
```

Run this before committing any Java changes. It reformats all Java files in the project according to the configured rules.

### Check Formatting

```bash
mvn spotless:check
```

This verifies that the code is already formatted without modifying it. It is useful for CI pipelines and pre-commit hooks.

## Package with jpackage

The `packager` module contains a `linux` profile that produces a self-contained application image using `jpackage`.

```bash
mvn clean package -Plinux
```

### Skip Tests During Packaging

By default, `mvn clean package -Plinux` runs the full test suite. To package without running tests, activate the `skip-tests` profile:

```bash
mvn clean package -Plinux,skip-tests
```

This profile sets `skipTests=true`, which disables test execution while still compiling the test sources. To skip tests entirely (including compilation), use the standard Maven property instead:

```bash
mvn clean package -Plinux -Dmaven.test.skip=true
```

### What the Profile Does

1. **Stage dependencies**: `maven-dependency-plugin` copies runtime JARs into `packager/target/mods`
2. **Build the runtime image**: `exec-maven-plugin` invokes the JDK's `jlink` directly into `target/runtime-image`, rooted on the launcher module plus JavaFX plus the SQLite driver:
   - `--compress zip-6` shrinks the `lib/modules` file by roughly half
   - `--strip-debug` removes the DWARF sections that distro-built JDK modules ship inside their native libraries (without it, the server VM alone grows by hundreds of MB)
   - `--no-header-files --no-man-pages --strip-native-commands` drop content a bundled application never uses
   - The resolved module set is identical to the one the internal jpackage jlink used to produce (verified against the image `release` file)
3. **Create app image**: `jpackage-maven-plugin` packages the app image consuming the prebuilt runtime through its `runtimeImage` option, so no second, uncompressed jlink runs inside jpackage
4. **Tar the image**: `maven-assembly-plugin` creates a `.tar.gz` from the app image

The resulting self-contained image is about 80 MB (down from roughly 133 MB with the previous configuration). The `.tar.gz` is larger than before because the compressed runtime does not compress again; the installed footprint is what shrinks.

### Output

```
packager/target/JSoftSIP-0.0.1-linux-x64.tar.gz
```

The `linux` profile enforces an amd64 build host via maven-enforcer (`requireOS arch=amd64`), failing fast on non-x64 machines so the `linux-x64` artifact name can never mislabel ARM output.

The archive contains:

```
JSoftSIP/
├── bin/
│   └── JSoftSIP          # Launcher script
├── lib/
│   ├── modules           # Trimmed JVM via jlink
│   └── *.jar             # Application and runtime JARs
└── ...
```

### JVM Trimming

The runtime image is produced by an explicit `jlink` execution in the `packager` module. Only the modules required by the application are included, the result is compressed with `zip-6`, and native debug sections are stripped, yielding a much smaller footprint than a full JDK installation while remaining fully self-contained.

### Baresip Is Not Bundled

The package **does not include Baresip**. The target system must have Baresip installed separately. JSoftSIP expects the `baresip` binary to be available on the system `PATH`.

### Packaging Requirements

- Must run on Linux (the `linux` profile is strictly opt-in: activate it explicitly with `-Plinux`; it never activates automatically)
- JDK 21 must be the active JDK (`JAVA_HOME`)
- Baresip must be installed on the build machine and the target system
  - Default module path: `/usr/lib/baresip/modules`
  - Override with: `-Djsoftsip.baresip.module.path=/path/to/baresip/modules`

## No Windows Packaging

Windows packaging is not implemented. The `linux` profile serves as the reference implementation; a future `windows` profile could be added as a sibling without modifying the existing one.

## No Native Image Packaging

Native image generation was attempted and abandoned. See [`docs/native-image-attempt.md`](native-image-attempt.md) for the full record. The supported distribution format is the `jpackage` app image described above.
