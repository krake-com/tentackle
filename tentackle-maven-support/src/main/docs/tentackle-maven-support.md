# Tentackle Maven Support — The Shared Mojo Foundation

## Overview and Motivation

`tentackle-maven-support` is a small, **build-time-only** library that provides the common base classes and
helpers shared by every Tentackle Maven plugin. It is not a plugin itself and defines no goals; instead it supplies
the `AbstractMojo` subclasses, the toolchain/tool-location plumbing, the annotation-processing harness, and the
assorted utilities that the actual plugins extend.

Seven plugins build on it:

- [`tentackle-maven-plugin`](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) — the analyze phase, property/beaninfo/version generation
- [`tentackle-sql-maven-plugin`](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) — DDL generation
- [`tentackle-i18n-maven-plugin`](../../../../tentackle-i18n-maven-plugin/src/main/docs/tentackle-i18n-maven-plugin.md) — i18n resource handling
- [`tentackle-check-maven-plugin`](../../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md) — model/consistency checks
- [`tentackle-jlink-maven-plugin`](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md) — jlink/jpackage image building
- [`tentackle-wizard-maven-plugin`](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) — project wizard
- [`tentackle-mcp-maven-plugin`](../../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md) — MCP server serving the model to coding agents

By factoring the recurring concerns into one place, each plugin only has to implement its own goal logic. The
module is packaged with an `Automatic-Module-Name` of `org.tentackle.maven` and is never shipped inside, nor used
by, the running application — its `package-info` simply reads *"Support for Tentackle Maven Plugins."*

### What it provides

- A **common mojo lifecycle** (`AbstractTentackleMojo`) with a fixed `prepare → validate → execute → finish`
  template, uniform skip handling, verbosity and encoding setup, and `java.util.logging`→Maven log bridging.
- A **JDK-toolchain–aware tool locator** (`JavaToolFinder`, plus the toolchain accessors on the base mojo) so
  external tools like `java`, `jdeps`, `jlink` and `jpackage` are found reliably across environments.
- An **annotation-processing harness** (`AbstractTentackleAnnotationProcessingMojo`) that runs the build-support
  [analyze processors](../../../../tentackle-build-support/src/main/docs/build-support.md) over a project's
  sources via the in-process Java compiler.
- **JPMS-oriented project introspection** — a package scanner that builds a package→`PackageInfo` map and
  detects split packages, which are illegal under the module system.

### Dependencies

It depends on `maven-core`, `maven-plugin-annotations` and the Maven Shared `file-management` (filesets), on
[Wurbelizer](https://www.wurbelizer.org/) (for `Verbosity`/`Constants`/`Settings`) and on
[`tentackle-build-support`](../../../../tentackle-build-support/src/main/docs/build-support.md) (which transitively
pulls in [`tentackle-common`](../../../../tentackle-common/src/main/docs/common.md) for `Settings`, `ToolRunner`
and `ToolFinder`).

---

## `AbstractTentackleMojo` — the common base mojo

[`AbstractTentackleMojo`](../java/org/tentackle/maven/AbstractTentackleMojo.java) extends Maven's `AbstractMojo`
and is the root of all Tentackle mojos. Its central idea is a **sealed `execute()` template**: subclasses never
override `execute()` — they implement `executeImpl()` and may optionally hook `prepareExecute()` /
`finishExecute()`.

```java
@Override
public final void execute() throws MojoExecutionException, MojoFailureException {
  installJavaLoggingHandler();   // route java.util.logging → Maven log
  prepareExecute();              // optional hook (no-op by default)
  if (validate()) {              // shared checks; may decide to skip
    executeImpl();               // the goal's actual work
    finishExecute();             // optional hook (no-op by default)
  }
}
```

### Shared mojo parameters

All are injected by Maven and exposed via getters, so every plugin inherits them for free:

| Parameter                                      | Purpose                                                           |
|------------------------------------------------|-------------------------------------------------------------------|
| `${project}`, `${session}`, `${mojoExecution}` | the Maven project, build session and execution                    |
| `${settings}` + `SettingsDecrypter`            | `settings.xml` plus credential decryption (e.g. for servers)      |
| `jdkToolchain` + `ToolchainManager`            | per-plugin selection (or deselection) of the `"jdk"` toolchain    |
| `tentackle.verbosity`                          | `default` / `info` / `debug` (debug also implied by Maven's `-X`) |
| `skip`                                         | skip the goal; defaults to skipping when packaging is `pom`       |
| `charset` (`${project.build.sourceEncoding}`)  | encoding for reading/writing files                                |
| `minLogLevel` (default `WARNING`)              | threshold for the JUL→Maven log bridge                            |

### Skip and validation logic

`validate()` runs first: it resolves the encoding and verbosity, requires `project.baseDir`, and applies the
**skip-by-default** rule — when `skip` is unset and the project is a recursive `pom` packaging (an aggregator
parent), the goal is skipped. Subclasses override `validate()` to add their own preconditions (calling `super`),
and return `false` to short-circuit the run.

### Toolchain and Java-home resolution

`getToolchain(...)` honours an explicit `<jdkToolchain>` configuration (overriding the maven-toolchain-plugin),
an empty `<jdkToolchain/>` to *deselect* a configured toolchain, or otherwise falls back to the toolchain from the
build context. `getJavaHome(Toolchain)` extracts and validates the `JAVA_HOME` of a `JavaToolchainImpl`. These
feed `getToolFinder()`, which hands back a toolchain-aware `JavaToolFinder`.

### Utility methods

A grab-bag of helpers the plugins rely on:

- `determineJavaToolVersion(File)` / `getMajorVersion(String)` — run a tool with `--version` and parse the Java
  version (skipping incubator-module noise), then reduce it to a major number.
- `getPathRelativeToBasedir(String)` — shorten absolute paths for readable log output.
- `getResourceDirs(boolean)` / `getResourceDir(File, boolean)` / `getCanonicalPath(File)` — locate (test) resource
  directories from the project model and test membership.
- `createFileSetManager(boolean)` / `getIncludedFiles(FileSet)` — expand Maven shared filesets.
- `loadResourceFileIntoString(String)` — read a classpath resource as a string.
- `toDescriptorName(File)` — derive a clean descriptor/module name from an artifact jar filename (stripping the
  extension, `-SNAPSHOT`, version and platform classifiers).
- `getHostName()` — best-effort hostname (falls back to the loopback address).
- `installJavaLoggingHandler()` — reset the JUL root logger and attach a [`MavenLogHandler`](#mavenloghandler).

### Package scanning and split-package detection

`createPackageMap(boolean resourceRoots)` walks the compile-source roots (or non-filtered resource roots) of the
project — recursively across the reactor's collected projects when run non-recursively — and builds a
`Map<String, PackageInfo>`. While doing so, it enforces a JPMS rule: **a package containing files must not appear in
more than one module**. A genuine split (two modules both contributing files to the same package) throws a
`MojoExecutionException`; empty package directories are tolerated and tracked as "empty duplicates" so a
non-empty occurrence can win.

---

## `AbstractTentackleAnnotationProcessingMojo` — the analyze harness

[`AbstractTentackleAnnotationProcessingMojo`](../java/org/tentackle/maven/AbstractTentackleAnnotationProcessingMojo.java)
extends the base mojo to run Tentackle's
[annotation analyze phase](../../../../tentackle-build-support/src/main/docs/build-support.md) — the pass that
generates service descriptors and analyze-info files **before** wurbeling and the real compilation. It is the
direct superclass of the analyze/SQL/i18n/check mojos.

It implements `executeImpl()` to drive the **in-process Java compiler** (`ToolProvider.getSystemJavaCompiler()`)
purely for annotation processing:

1. The set of source files is taken either from explicit `<filesets>` or, by default, from the configured
   `sourceDir` (`**/*.java`).
2. For each fileset, the registered
   [`AbstractTentackleProcessor`](../../../../tentackle-build-support/src/main/java/org/tentackle/buildsupport/AbstractTentackleProcessor.java)s
   are initialized, then a `CompilationTask` is created with `-proc:only` (unless a `proc:` arg is supplied),
   the project classpath (added to **both** `-classpath` and `--module-path`), the source encoding and
   `-Averbosity=…` passed through to the processors.
3. Diagnostics are collected; when `showCompileOutput` is set they are formatted (`file:line:col: message`) into
   `getCompileErrorLog()`. Analyze and compile errors are counted separately — analyze errors fail the build,
   while compile errors only warn (the sources have not been wurbeled yet, so some are expected).

Configuration entry points for subclasses:

- `setMojoParameters(File sourceDir, List<String> classpathElements)` — sets the source directory and creates the
  [`ProjectClassLoader`](#projectclassloader); `addProcessor(...)` registers the processors to run.
- Overridable hooks: `createMissingDirs()`, `initializeProcessor(...)`, `cleanupProcessors(...)` and
  `filterFileNames(...)` (to skip files / opt out of processing).
- Mojo parameters: `showCompileOutput`, `encoding`, `compilerArgs`, `compilerArgument`, `filesets`.

---

## Supporting classes

### `JavaToolFinder`

[`JavaToolFinder`](../java/org/tentackle/maven/JavaToolFinder.java) extends
[`ToolFinder`](../../../../tentackle-common/src/main/docs/common.md) and resolves a tool executable by name in a
robust order: the **toolchain** first (if one was supplied), then the **directory of the currently running `java`**
(via `ProcessHandle.current()`, safe regardless of `JAVA_HOME`/`PATH`), then **`$JAVA_HOME/bin`**, and finally the
inherited `PATH` search. It also appends OS-appropriate suffixes (`.exe`/`.cmd`/`.bat` on Windows, `.sh`
elsewhere). This is what lets the jlink plugin reliably find `jlink`, `jpackage`, `jdeps`, etc.

### `MavenLogHandler`

[`MavenLogHandler`](../java/org/tentackle/maven/MavenLogHandler.java) is a `java.util.logging.Handler` that
forwards records at or above a minimum level to Maven's `Log`, mapping `SEVERE→error`, `WARNING→warn`,
`INFO→info` and everything else to `debug`. Because Tentackle's own code logs through `java.util.logging` by
default, this bridge keeps build-time framework logging visible in the Maven output without flooding it (default
threshold `WARNING`).

### `ProjectClassLoader`

[`ProjectClassLoader`](../java/org/tentackle/maven/ProjectClassLoader.java) is a `URLClassLoader` that adds the
project's classpath elements on top of the plugin's own classloader, so the analyze processors can load and
inspect classes from the project being built. It exposes `addURL` publicly.

### `PackageInfo`

[`PackageInfo`](../java/org/tentackle/maven/PackageInfo.java) is the value object produced by the base mojo's
package scan: the package name, the owning Maven project, and the directory path. `isContainingFiles()` underpins
split-package detection, and `getEmptyDuplicates()`/`setEmptyDuplicates(...)` track empty package directories that
occur in several modules (which cannot be used in profiles, since the generator could not decide which to pick).
Hidden files are ignored by both the scan and `isContainingFiles()`, so a leftover directory holding nothing but
a `.gitignore` stays an empty package. `PackageInfo.isHidden(File)` decides this, treating a leading dot as hidden
on all platforms, since Windows only knows the DOS hidden attribute.

---

## How It Fits Into the Build

```
                tentackle-maven-support
                ┌────────────────────────────────────────────┐
                │ AbstractTentackleMojo                      │  lifecycle, toolchains,
                │   └─ AbstractTentackleAnnotationProcessing │  skip/verbosity, utilities
                └────────────────────────────────────────────┘
                        ▲           ▲            ▲
        ┌───────────────┘     ┌─────┘       ┌────┴───────────┐
  tentackle-maven-plugin  …-sql-…   …-i18n-… / …-check-… / …-jlink-… / …-wizard-… / …-mcp-…
        (analyze, properties,  (DDL)   (each implements executeImpl())
         beaninfo, versions)
                        │
                        ▼ runs
        tentackle-build-support  (AnalyzeProcessor / handlers → META-INF + analyze info)
```

The plugins supply the goals and bind to the lifecycle; `tentackle-maven-support` supplies the shared mojo
machinery; and the [annotation processors](../../../../tentackle-build-support/src/main/docs/build-support.md) it
drives produce the service descriptors and analyze-info files later consumed by the
[wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md).

---

## Related Documentation

- [Build Support — the annotation analyze phase](../../../../tentackle-build-support/src/main/docs/build-support.md)
- [Tentackle Maven Plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md)
- [SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) ·
  [i18n Maven Plugin](../../../../tentackle-i18n-maven-plugin/src/main/docs/tentackle-i18n-maven-plugin.md) ·
  [Check Maven Plugin](../../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md) ·
  [Wizard Maven Plugin](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) ·
  [MCP Maven Plugin](../../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md)
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md)
