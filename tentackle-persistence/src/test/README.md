## Unit Testing the Persistence Layer

The tests of this module come in two layers.

### 1. Without a database

Large parts of the persistence layer work *before* any statement is executed: the class variables, the
SQL the entity builds for itself, the domain context, the root columns, the normtext, the token-lock
attributes and the snapshots. That part is covered by tests that run without a database, a session or
a connection.

The recipe mirrors the one of [tentackle-domain](../../../../tentackle-domain/src/main/docs/domain.md#unit-testing-domain-logic),
with the halves swapped: the **real persistence delegate** is paired with a **mock domain delegate**
from [`tentackle-pdo-mock`](../../../../tentackle-pdo-mock/src/main/docs/pdo-mock.md):

```java
// a hand-written equivalent of the generated persistence implementation
@PersistentObjectService(DemoEntity.class)
public class DemoEntityPersistenceImpl extends AbstractPersistentObject<DemoEntity, DemoEntityPersistenceImpl>
       implements DemoEntityPersistence {

  public static final PersistentObjectClassVariables<DemoEntity, DemoEntityPersistenceImpl> CLASSVARIABLES =
      PersistentObjectClassVariables.create(DemoEntity.class, DemoEntityPersistenceImpl.class, "dem");
  ...
}

// Pdo.create() builds the real proxy: real persistence delegate, mocked domain delegate
DemoEntity pdo = Pdo.create(DemoEntity.class);
assertEquals(((DemoEntityPersistenceImpl) pdo.getPersistenceDelegate()).createSelectAllSql(new Postgres()),
             "SELECT dem.* FROM demoentity dem WHERE 1=1");
```

Worth knowing when writing such tests:

- The table name and the class ID come from the `@TableName`/`@ClassId` annotations of the **PDO
  interface**, so a test entity needs neither a model nor the wurblets — only a unique table name,
  because the class variables register themselves by table name.
- The snapshot methods (`revertAttributesToSnapshot`, `createComponentsInSnapshot`,
  `revertComponentsToSnapshot`) are located reflectively by
  [`PersistentObjectClassVariables`](../java/org/tentackle/persist/PersistentObjectClassVariables.java) through
  the **declaring class**. They are therefore declared with the implementation type as parameter — an
  overload, not an override. Declaring them with the base type instead makes them silently unused.
- SQL statements that take the [`Backend`](../../../../tentackle-sql/src/main/docs/sql.md) as a parameter
  (`createSelectAllSql(backend)` and friends) can be verified by passing a backend instance directly;
  the ones taking it from the session need the database-backed tests below.
- `isModified()` is answered by **tracked** entities only, all others throw. Use `attributesModified()`
  for the plain flag.
- Like the domain module, the tests run on the **class path**, not patched into the module
  (`useModulePath=false`), so that the `META-INF/mapped-services/*` entries of the test PDOs and of this
  module are both visible. The entries are created by the `test-analyze` goal of the
  [tentackle-maven-plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md).

### 2. Against an in-memory H2

Everything that needs a live session — the read and delete path, the cursors, the token locks and the
joined selects — is covered by the tests extending
[`AbstractPersistenceDbTest`](java/org/tentackle/persist/AbstractPersistenceDbTest.java). No database
has to be installed: the recipe is the one of `tentackle-database`'s `AbstractDbTest`, one module
below in the reactor.

- `backend.properties` points at `jdbc:h2:mem:persistence`. The base class opens one session for the
  whole suite and gives every test method its own transaction, which is rolled back afterwards.
- The schema is built from two scripts. `META-INF/sql/H2/createttmodel.sql` is generated at build
  time by the [SQL maven plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md)
  from `tokenlock.map` plus the modification tables. `demo-schema.sql` is the hand-written DDL of the
  `Demo*` test PDOs, which have no model to generate one from — it is the one place where the columns,
  the `CN_*` constants and the `getFields`/`setFields` of the `Demo*PersistenceImpl` classes must agree.
- The session is deliberately **not** made thread-local: the tests of layer 1 run in the same JVM on
  the same thread and some of them assert that there is no current session.
- `DemoServerApplication` registers a minimal server application, which is what turns
  [`DefaultLockManager`](../java/org/tentackle/persist/lock/DefaultLockManager.java) from passive into
  enabled. Notice that only an instance created *after* the registration is enabled, and that the
  `LockManager` singleton of the JVM is not: it is created by the `ServiceFactory` the first time any
  test touches it, i.e. without an application.
- `determineContextId()` runs from the `AbstractPersistentObject` constructor, i.e. **before** the
  field initializers of the subclass. A `contextId` field with an initializer would silently overwrite
  the value that was just determined — the generated code has none either, and neither does
  `DemoComponentPersistenceImpl`.

The test entities:

| PDO             | role                                                                     |
|-----------------|--------------------------------------------------------------------------|
| `DemoEntity`    | root entity, tracked, own table, no token lock                            |
| `DemoComponent` | component of a `DemoEntity` aggregate: root columns, normtext, token lock, context condition, untracked |
| `DemoLockable`  | root entity with the token-lock columns, for the token-lock operations, which assert a root entity |
| `DemoBase`/`DemoSub` | single-table inheritance, on the class-variable level only — no mapping and no table |
| `DemoEmbedded`  | embedded PDO borrowing the class variables of its parent                  |
| `DemoOperation` | an operation                                                              |

What is still tested in [tentackle-test-pdo](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md)
instead: everything involving remoting — the TRIP delegates, remote transactions, remote
authentication and the preferences — because it needs a server in a second JVM.
