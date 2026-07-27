# Tentackle PDO Tests — the `tentackle-test-pdo` Module

## Overview

`tentackle-test-pdo` is the **test-support module** for writing
[PDO](../../../../tentackle-pdo/src/main/docs/pdo.md)-based tests. It does three distinct jobs:

1. **Provides test base classes** — abstract TestNG and JUnit 5 base classes (`AbstractPdoTest`,
   `TestApplication`) that open a [session](../../../../tentackle-session/src/main/docs/session.md), populate an
   in-memory database, manage the per-class/per-method transaction lifecycle, run the
   [modification tracker](../../../../tentackle-session/src/main/docs/session.md) and tear everything down again — so
   a test class only has to write the assertions.
2. **Provides mock delegates** — lightweight `Mock*` domain/persistence delegates that let you instantiate PDOs and
   operations in a unit test **without a database**.
3. **Generates the framework's SQL** — at build time it runs the
   [SQL Maven plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) over the
   framework model (number sources, security, preferences, …) and packages the resulting DDL, model dump and
   `MODEL-INDEX.LIST` into the jar, so downstream tests can create the **TT (Tentackle) tables** in an in-memory
   database from the classpath.

> **Test scope only.** This artifact pulls in TestNG, JUnit, H2 and PostgreSQL and (deliberately, see below) marks
> the framework layers `optional` to keep the classpath order right. It must be included with
> `<scope>test</scope>` in downstream projects — never as a compile/runtime dependency.

This module is **not** a JPMS module. It has no `module-info.java`; the jar carries the
`Automatic-Module-Name: org.tentackle.pdo.test`. It is meant to live on the test classpath, where Tentackle's
[ServiceFinder SPI](../../../../tentackle-common/src/main/docs/common.md) discovery still works without an explicit
module descriptor.

---

## Package Layout

| Package                    | Role                                                                                                                                                          |
|----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `org.tentackle.pdo.testng` | TestNG base classes: `AbstractPdoTest` and `TestApplication`. The **default** framework (the module's own tests run under TestNG via `src/test/testng.xml`).  |
| `org.tentackle.pdo.junit`  | JUnit 5 equivalents of the same two base classes, for projects that prefer Jupiter.                                                                           |
| `org.tentackle.pdo.test`   | `DbTestUtilities` — low-level helpers that build and run the database-population SQL script from the model. Shared by both the TestNG and JUnit base classes. |
| `org.tentackle.pdo.mock`   | `MockDomainObject`, `MockPersistentObject`, `MockDomainOperation`, `MockPersistentOperation` — no-op delegates for database-less unit tests. Ships in the separate `tentackle-pdo-mock` artifact (see below) and reaches downstream projects through this one. |

---

## The Test Base Classes

There are two parallel families, one per test framework, with the same shape. Pick the one that matches your
project's test runner; the TestNG variant is the default in Tentackle.

### `AbstractPdoTest`

The base class for tests **on PDO level** — i.e., tests that talk to a live (usually in-memory) database through the
PDO API. It implements `DomainContextProvider`, so the running test *is* the context source for the PDOs it
creates.

**Transaction handling** is declared per test class through the `TransactionType` enum, chosen via the
constructors:

| Constructor                                        | Transaction                              | Commit/rollback  |
|----------------------------------------------------|------------------------------------------|------------------|
| `AbstractPdoTest()`                                | `CLASS` (one transaction per test class) | rollback         |
| `AbstractPdoTest(TransactionType)`                 | as given                                 | rollback         |
| `AbstractPdoTest(TransactionType, boolean commit)` | as given                                 | commit if `true` |

`TransactionType` is `CLASS` (transaction spans the whole class), `METHOD` (one per test method) or `NONE` (no
automatic transaction). Tests **roll back by default** so they leave no trace; pass `commit = true` only for the
rare scenario that must persist.

**Lifecycle** (TestNG annotations shown; JUnit uses the equivalent `@BeforeAll`/`@AfterAll`/…):

- `@BeforeSuite openSessionsAndStartModificationTracker()` — opens the session (`Pdo.createSession()`), builds the
  `DomainContext`, and **if the backend is in-memory** creates the database tables and populates them. It also
  picks a default scripting language if exactly one is available, and starts the `ModificationTracker` (with a fast
  500 ms poll interval suited to tests).
- `@BeforeClass`/`@AfterClass` — begin/end the transaction when `TransactionType.CLASS`.
- `@BeforeMethod`/`@AfterMethod` — begin/end the transaction when `TransactionType.METHOD`; a failed method flips
  `rollbackLogged` so the rolled-back statements are logged for diagnosis.
- `@AfterSuite closeSessionsAndTerminateModificationTracker()` — terminates the tracker and closes the session.

**Extension points** a concrete test can override:

| Method                     | Purpose                                                                                                                                                                      |
|----------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `createDatabaseTables(Db)` | Build the schema. The default loads the model from the classpath via `DbTestUtilities` and runs the generated script. Override to add extra model sources.                   |
| `populateDatabase()`       | Insert test fixtures after the tables exist. Default does nothing.                                                                                                           |
| `createDomainContext()`    | Supply the (thread-local) `DomainContext`. Default `Pdo.createDomainContext()`.                                                                                              |
| `openSession()`            | Open the session; default throws TestNG `SkipException` (JUnit: aborts) when no backend is reachable, so the suite is **skipped, not failed**, in an incomplete environment. |

**Helpers** for richer scenarios: `runPreCommits()`/`runPostCommits()` force the pre/post-commit callbacks even
though the test will roll back; `setRollbackLogged(true)` logs the SQL even on success; `runClass(...)`,
`runInOtherJVM(...)` and `waitForProcess(...)` spawn a test class in a **separate JVM** (used by the remote/RPC
tests — see `testng.xml`'s `remote1`/`remote2` groups).

### `TestApplication`

The base class for tests that must run as a full
[`AbstractApplication`](../../../../tentackle-pdo/src/main/docs/pdo.md) — i.e., with the complete application
bootstrap (`SessionInfo`, property application, registration) rather than just a bare session. Same in-memory
table creation/population, but driven through the application lifecycle. The JUnit variant keeps a static reference
to the running application because JUnit's `@AfterAll` must be static, and guards against two `TestApplication`s
running at once.

---

## `DbTestUtilities` — model → DDL → database

`DbTestUtilities` (in `org.tentackle.pdo.test`, an `@Service` defaulting to itself, so it is replaceable) is the
low-level bridge from the model to a live database used by the base classes:

- `createPopulateScript(Db)` — loads the **default** model from the classpath
  ([`Model.loadFromResources`](../../../../tentackle-model/src/main/docs/tentackle-model.md)) and renders a complete
  DDL script for the session's [backend](../../../../tentackle-sql/src/main/docs/sql.md): the object-sequence (if
  the backend supports sequences), every table-providing, non-provided entity's `CREATE TABLE` and indexes, any
  schemas, and finally the foreign keys.
- `runScript(Db, String)` — executes that script against the low-level session.

Because the script is built from the same `tentackle-model` object graph the code generators use, the in-memory
test schema can never disagree with the generated persistence layer.

---

## The Mock Delegates

The `org.tentackle.pdo.mock` package lets a **unit** test work with PDOs and operations without any persistence
backend. Recall the [PDO pattern](../../../../tentackle-pdo/src/main/docs/pdo.md): a PDO is a dynamic proxy backed
by a *domain* delegate and a *persistence* delegate. The mocks supply trivial stand-ins for those delegates:

> **Where they live.** The package is its own artifact, **`tentackle-pdo-mock`**, which depends on nothing but
> `tentackle-pdo`. It sits *below* the domain- and persistence layer, so those modules can use the mocks in their
> own unit tests — a dependency on `tentackle-test-pdo` would be a cycle, because this module depends on them.
> Downstream projects need not care: `tentackle-test-pdo` depends on `tentackle-pdo-mock`, so the classes arrive
> transitively under their unchanged package and class names.

| Mock                         | Stands in for                                                                            |
|------------------------------|------------------------------------------------------------------------------------------|
| `MockDomainObject<T,D>`      | the domain delegate of a `PersistentDomainObject`                                        |
| `MockPersistentObject<T,P>`  | the persistence delegate of a `PersistentDomainObject` (also a `PdoMethodCacheProvider`) |
| `MockDomainOperation<T,D>`   | the domain delegate of an `Operation`                                                    |
| `MockPersistentOperation<T>` | the persistence delegate of an `Operation` (also an `OperationMethodCacheProvider`)      |

Most methods throw "not implemented" — the mocks deliberately implement only what a no-database test touches (id,
session/context wiring, method caches), so you can exercise *domain logic* in isolation and get a loud failure the
moment a test reaches for real persistence.

---

## Build-Time SQL Generation

Besides shipping Java, this module's POM generates the framework's own schema and packages it as resources so
downstream tests (and this module's tests) can stand up the **TT tables** in memory. The relevant build steps:

- **`tentackle-maven-plugin`** (`analyze`, `test-analyze`) — the standard
  [resource-index / analysis](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) step.
- **`tentackle-sql-maven-plugin`** (`create`, in `generate-resources`) — runs over the framework model on the
  classpath and writes into `target/generated-resources/META-INF/`:
  - `sql/createttmodel.sql` — the DDL for all framework tables, for **all** backends (`backendNames=all`),
    prepended with `src/sql/ttheader.sql`;
  - `model/…` + `MODEL-INDEX.LIST` — a dump of the model source so `Model.loadFromResources(...)` can reload it
    from the jar;
  - an `object_sequence_id` sequence definition (the PDO id generator).

  These land under `META-INF`, are declared as a `<resource>` directory, and are therefore baked into the jar.
- **`maven-jar-plugin`** — sets `Automatic-Module-Name: org.tentackle.pdo.test`.
- **`maven-surefire-plugin`** — overridden to use the TestNG provider and the `src/test/testng.xml` suite.

### Why the framework dependencies are `optional`

`tentackle-persistence` and `tentackle-domain` are declared `optional` with the comment *"don't mix up classpath
order"*. The PDO runtime resolves the persistence and domain implementation layers via the
[ServiceFinder SPI](../../../../tentackle-common/src/main/docs/common.md), and the **order** in which those layers
appear on the classpath matters. Marking them optional keeps this test artifact from forcing them transitively into
a downstream project's classpath in the wrong position; the downstream project declares the layers it actually
wants itself.

---

## The Module's Own Tests

Although primarily a *support* library, the module also **contains PDO tests** (under `src/test/java`) that exercise
the framework end-to-end against H2 (in-memory) and PostgreSQL (multi-process). They double as worked examples of
the base classes above. Coverage spans, among others:

| Area                       | Example test(s)                                                                                    |
|----------------------------|----------------------------------------------------------------------------------------------------|
| PDO factory & proxy wiring | `PdoFactoryTest`, `PdoInvocationTest`, `PdoUtilitiesTest`, `IllegalModelNamesTest`                 |
| Snapshots                  | `SnapshotTest` (see [snapshot.md](../../../../tentackle-pdo/src/main/docs/snapshot.md))            |
| Model                      | `ModelTest`                                                                                        |
| Database / DBMS            | `BatchingTest`, `ResultSetTest`, `H2ConnectionTest`, `SqlScriptTest`                               |
| Sessions & remoting        | `SessionPoolExecutorTest`, `RemoteTransactionTest`, plus the out-of-JVM server in `persist/server` |
| Security & number sources  | `SecurityTest`, `NumberSourceTest`                                                                 |
| Preferences                | `RemotePreferencesTest`                                                                            |

The `pdofactory` test package (`Invoice`/`SingleInvoice`/`CollectiveInvoice` with handwritten domain and
persistence impls, an `Operation` and a `LogIt` interceptor) is a self-contained miniature PDO model used to test
the factory and [interceptor](../../../../tentackle-core/src/main/docs/interceptors.md) machinery directly, without
running the code generator.

The `testng.xml` suite runs three groups — `local`, `remote1`, `remote2` — single-threaded, over the whole
`org.tentackle.*` package tree; the `remote*` groups drive the out-of-process server.

### Test configuration

Connection parameters come from filtered resources (`backend.properties`, `client.properties`) substituting the
`dbUrl`/`dbUser`/`dbPasswd`/`dbService` build properties, with `h2.properties` selecting the in-memory default
(`jdbc:h2:mem:default`). Logging is routed to `target/logs/test.log` via
[`tentackle-log-log4j2v`](../../../../tentackle-log-log4j2v/src/main/docs/tentackle-log-log4j2v.md) because the
console is contended by parallel processes on Windows.

---

## Using It in a Downstream Project

```xml
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-test-pdo</artifactId>
  <version>${tentackle.version}</version>
  <scope>test</scope>
</dependency>
```

A typical TestNG test then looks like:

```java
public class CustomerTest extends org.tentackle.pdo.testng.AbstractPdoTest {

  // default: one rolled-back transaction per class against the in-memory DB
  public CustomerTest() {
    super();
  }

  @Override
  protected void populateDatabase() {
    // insert fixtures once, after createDatabaseTables() built the schema
  }

  @Test(groups = "local")
  public void createAndReload() {
    Customer c = on(Customer.class);
    c.setName("Acme");
    c.save();
    Assert.assertNotNull(c.getId());
    // rolled back automatically at end of class
  }
}
```

Override `createDatabaseTables(Db)` to add your application's model on top of the framework's TT tables, and choose
`TransactionType.METHOD` (or `commit = true`) where the default per-class rollback doesn't fit.

### Generating Fixture Code with the PDO Browser

Writing the fixture code by hand gets tedious once tests need realistic object graphs. The *PDO Browser* — the
`browse` goal of the
[wizard maven plugin](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) —
automates it: it connects to a live backend (typically a development or staging database), lets you navigate the
persisted PDOs along their relations, and turns selected records into ready-to-paste Java code that recreates
them. The recipe:

1. Start the PDO browser from the aggregator directory: `mvn tentackle-wizard:browse`
   (the backend `url` and `user` are either configured in the POM or passed via `-D`).
2. Select the PDO type in the *entity* combo box and load records via *load*, *find by ID*, or *use finder*.
3. In the table, select the PDOs to generate Java code for.
4. Click *generate* — the browser builds the Java code (`on(...)`, the attribute setters, the component
   relations, a final `save()`) for the selected objects and copies it to the clipboard.
5. Paste the code into the test class (see `createDatabaseTables(Db)` above): the `populateDatabase()` override is
   invoked right after `createDatabaseTables(Db)` has built the in-memory schema, so the pasted code runs against a
   fresh database.
6. Repeat for all PDOs that should be available to *all* tests — put those into a common base test class of the
   suite — and then for the PDOs needed by the individual test classes.

Because the generated code recreates the objects through the plain PDO API, the tests need no backend at run
time — the fixtures populate the in-memory H2 database like any other test code. This makes creating CI tests
with realistic data super easy: browse, select, generate, paste.

---

## Related Documentation

- [PDO — Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — the runtime pattern under test.
- [Operations](../../../../tentackle-pdo/src/main/docs/operation.md) — what the `Mock*Operation` delegates stand in for.
- [Snapshots](../../../../tentackle-pdo/src/main/docs/snapshot.md) — exercised by `SnapshotTest`.
- [Tentackle Model](../../../../tentackle-model/src/main/docs/tentackle-model.md) — the object graph `DbTestUtilities` turns into DDL.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — `Backend`/`DataType` used to render the schema.
- [SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) — generates the packaged TT DDL.
- [Wizard Maven Plugin](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) — the PDO Browser that generates test-fixture code.
- [Session](../../../../tentackle-session/src/main/docs/session.md) — sessions and the modification tracker the base classes drive.
- [tentackle-test-fx-rdc](../../../../tentackle-test-fx-rdc/src/main/docs/tentackle-test-fx-rdc.md) — the analogous test-support module for the RDC/UI layer.
