# Tentackle FX RDC Tests — the `tentackle-test-fx-rdc` Module

## Overview

`tentackle-test-fx-rdc` is the **test-support module for the UI layer**. It is the
[RDC](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) counterpart of
[`tentackle-test-pdo`](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md): where that module gives
you base classes to test *persistent domain objects* against a database, this one gives you base classes to
**smoke-test the JavaFX front end** — controllers, GUI providers, and table-configuration providers — on a real FX
toolkit, with a live session underneath.

Its central idea is the **generic coverage test**. Rather than write one test per screen, you extend one of three
abstract base classes and the test automatically discovers *every*
[`@FxControllerService`](../../../../tentackle-fx/src/main/docs/fx.md) controller,
[`GuiProvider`](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) or `TableConfigurationProvider` registered via the
[ServiceFinder SPI](../../../../tentackle-common/src/main/docs/common.md), instantiates each one on the FX thread,
and asserts it loads, binds, and renders without error. A single one-line subclass thus guards your whole UI against
broken FXML, unbound fields, missing resources, or refactoring drift.

> **Test scope only.** Like its PDO sibling, this artifact pulls in TestNG, JUnit, Groovy, and PostgreSQL and is
> meant to be declared `<scope>test</scope>` in downstream projects — never as a compile/runtime dependency.

The module is **not** a JPMS module: it has no `module-info.java`; the jar carries
`Automatic-Module-Name: org.tentackle.fx.rdc.test` and lives on the test classpath, where SPI discovery works
without an explicit module descriptor.

---

## How It Builds on `tentackle-test-pdo`

This module depends on [`tentackle-test-pdo`](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md)
and [`tentackle-fx-rdc`](../../../../tentackle-fx-rdc/src/main/docs/rdc.md). The base class `FxRdcTestApplication`
**extends** the PDO module's `TestApplication`, inheriting the entire database/session/transaction lifecycle (open
session, create & populate the in-memory schema, modification tracker, teardown, skip-when-no-backend) and adding
only what the UI needs on top:

```
   org.tentackle.app.AbstractApplication           (tentackle-pdo)
              ▲
   TestApplication                                  (tentackle-test-pdo)  ── DB session, schema, transactions
              ▲
   FxRdcTestApplication                             (tentackle-test-fx-rdc) ── JavaFX toolkit + FX-thread session
              ▲
   ┌──────────┼───────────────────────────┐
   ControllerTest   GuiProviderTest   TableConfigurationProviderTest
```

So an FX-RDC test is a fully bootstrapped Tentackle application *plus* a started FX toolkit.

---

## Package Layout

| Package                       | Role                                                                                                                                       |
|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| `org.tentackle.fx.rdc.testng` | TestNG base classes: `FxRdcTestApplication`, `ControllerTest`, `GuiProviderTest`, `TableConfigurationProviderTest`. The default framework. |
| `org.tentackle.fx.rdc.junit`  | JUnit 5 equivalents of the same four classes, for projects that prefer Jupiter.                                                            |

The two packages are mirror images; pick the one matching your test runner. The descriptions below use the TestNG
names.

---

## `FxRdcTestApplication` — the FX bootstrap

The abstract base of all three test types. It adds the JavaFX dimension to the inherited `TestApplication`:

- **`@BeforeSuite startFx()`** — starts the JavaFX toolkit with `Platform.startup(...)`. If the JVM is **headless**
  (`GraphicsEnvironment.isHeadless()`) or FX is unavailable, it throws TestNG's `SkipException` so the suite is
  **skipped, not failed** in CI without a display. The JUnit variant cannot use a `@BeforeAll` method here,
  because JUnit runs a superclass's `@BeforeAll` methods first. The session would already be open and the
  in-memory database populated, and when the class aborted, that leaked connection would keep the database alive
  for the next class. Instead, the nested `FxExtension` (a `BeforeAllCallback`, which JUnit runs before every
  `@BeforeAll` method) calls the idempotent `startFx()`. A headless JVM or missing FX support raises
  `TestAbortedException`, and the tests are reported as skipped.
- **`setDomainContext(...)` override** — when the inherited context is set, it **clones the session for the FX
  thread** (`context.getSession().clone("FX")`) and makes that clone the current session *inside* `Platform.runLater`,
  so controllers that touch persistence from the UI thread work correctly. It also installs an uncaught-exception
  handler on the FX thread so exceptions thrown asynchronously during rendering are captured and reported.
- **`runFx(name, runnable)`** — the core helper. It runs the test body, then posts a `CountDownLatch` countdown to
  the FX thread, and **waits** (default 30 s, see `setFxTimeout`) until the FX event queue has drained — guaranteeing
  all FX processing triggered by the test has completed. Any captured FX-thread exception fails the test. This is
  how a synchronous TestNG/JUnit assertion can reliably wait for asynchronous FX work.
- **`unregister()` override** — closes the FX-thread session on teardown.

The FX timeout is configurable per test via `setFxTimeout(seconds)`.

---

## The Three Generic Tests

Each test iterates the relevant SPI registry, filters by an optional **package prefix** (so a project can restrict
the run to its own classes), honors a per-service **opt-out**, and exercises each implementation inside `runFx`.

### `ControllerTest`

Verifies that every [`FxController`](../../../../tentackle-fx/src/main/docs/fx.md) can be **loaded and bound**. For
each controller class from `FxFactory.getInstance().getControllerClasses()`:

1. `Fx.load(clazz)` — loads the FXML and instantiates the controller;
2. `controller.validateInjections()` — asserts all `@FXML`/injected members are present;
3. unless the controller opts out of binding, `controller.getBinder().assertAllBound()` — asserts every bound
   field actually resolved.

The `@FxControllerService` annotation drives opt-out: `test() == false` skips the controller entirely, and
`binding() == BINDING.NO` skips the bind assertion. This catches the classic failure modes — renamed FXML ids,
missing resource bundles, fields bound to non-existent properties — across the whole UI at once.

### `GuiProviderTest`

Verifies that every [`GuiProvider`](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) can produce its views. It
maps each serviced PDO class (via `ClassMapper.create(name, GuiProvider.class)`), creates the PDO, obtains its
`GuiProvider`, and — honouring `@GuiProviderService(test=…)` — exercises the full set of view factories:
`createEditor()` / `createFinder()` (when available), `createGraphic()`, `createTableView()`, `createTreeItem()`.
A PDO with no provider (`ClassNotFoundException` cause) is reported and skipped rather than failed.

### `TableConfigurationProviderTest`

Verifies that every `TableConfigurationProvider` can build its table configuration. For each serviced class it
obtains the provider from `TableConfigurationProviderFactory` and, honouring `@TableConfigurationProviderService(test=…)`,
calls `createTableConfiguration()` on the FX thread.

### Common conventions

| Feature            | Behaviour                                                                                                                                       |
|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| **Package prefix** | Each base class takes an optional `packagePrefix` constructor argument; only matching classes are tested. `null`/empty = all.                   |
| **Opt-out**        | The matching `@*Service(test=false)` annotation excludes an implementation; it is logged as "not tested" rather than run.                       |
| **Discovery**      | Implementations are found through the SPI registries (`FxFactory`, `ClassMapper`, the provider factories), so nothing has to be listed by hand. |
| **Reporting**      | Every step is written to the TestNG `Reporter` / the JUnit `TestReporter` for a readable per-class trace.                                       |

---

## Using It in a Downstream Project

Add the dependency in **test scope**:

```xml
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-test-fx-rdc</artifactId>
  <version>${tentackle.version}</version>
  <scope>test</scope>
</dependency>
```

Then a complete UI smoke-test suite is just three one-line subclasses in a test source folder:

```java
public class TestControllers                 extends ControllerTest {}
public class TestGuiProviders                extends GuiProviderTest {}
public class TestTableConfigurationProviders extends TableConfigurationProviderTest {}
```

To restrict a run to your own packages, call the prefix constructor:

```java
public class TestGuiProviders extends GuiProviderTest {
  public TestGuiProviders() {
    super("com.example.myapp");
  }
}
```

These are exactly the example tests shipped in this module's own `src/test/java`: `org.tentackle.test.fx.rdc`
for TestNG and `org.tentackle.test.fx.rdc.junit` for JUnit. The JUnit package also contains `ApplicationTest`,
which runs a plain JUnit `TestApplication` with more than one test method. These tests double as the canonical
usage reference. Database connection parameters come from
`backend.properties`, which selects the in-memory H2 database, inherited from the
[test-pdo](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) session machinery. Unlike
test-pdo's, the URL deliberately carries **no `DB_CLOSE_DELAY=-1`**: `TestApplication` opens its session and
creates the schema *per test class*, and H2 discards an in-memory database when its last connection closes, so
each class starts from an empty one. Keeping the database alive across classes would make the second
`CREATE SEQUENCE` fail.

> Because the tests start a real JavaFX toolkit, they **self-skip on headless build agents**. To run them in CI, you
> need a display (or a virtual one such as `Xvfb`); otherwise the suite reports as skipped, not failed.

> ### ⚠️ The surefire provider has to be pinned
>
> This module puts **both** TestNG and JUnit Jupiter on the classpath, because it offers a base class for each.
> Surefire's auto-detection then picks only the JUnit platform provider. TestNG tests are silently not run: the
> module reports `Tests run: 0` while the build stays green. The POM therefore declares **both**
> `surefire-testng` and `surefire-junit-platform` as plugin dependencies, exactly as
> [test-pdo](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) does. Surefire runs each provider
> in its own forked JVM. A downstream TestNG project pins `surefire-testng`. A downstream JUnit project needs
> `junit-jupiter-engine` and a `junit-platform-launcher` of the same version in test scope; otherwise surefire's
> bundled launcher does not match the engine and test discovery fails.

---

## Related Documentation

- [tentackle-test-pdo](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) — the persistence-layer test-support module this one builds on.
- [Rich Data Components (RDC)](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) — `GuiProvider`, table/tree/editor/finder views under test.
- [Tentackle FX](../../../../tentackle-fx/src/main/docs/fx.md) — `FxController`, `FxFactory`, binding and `@FxControllerService`.
- [PDO — Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — the domain objects the GUI providers render.
- [Common / ServiceFinder SPI](../../../../tentackle-common/src/main/docs/common.md) — the discovery mechanism the generic tests iterate.
