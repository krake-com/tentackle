## Unit Testing Without a Database

Large parts of the persistence layer work *before* any statement is executed: the class variables, the
SQL the entity builds for itself, the domain context, the root columns, the normtext, the token-lock
attributes and the snapshots. This module's own tests (`src/test/java`) cover exactly that part and run
without a database, a session or a connection.

The recipe mirrors the one of [tentackle-domain](../../../../tentackle-domain/src/main/docs/domain.md#unit-testing-domain-logic),
with the halves swapped: the **real persistence delegate** is paired with a **mock domain delegate**
from [`tentackle-pdo-mock`](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md#the-mock-delegates):

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
  the ones taking it from the session cannot.
- `isModified()` is answered by **tracked** entities only, all others throw. Use `attributesModified()`
  for the plain flag.
- Like the domain module, the tests run on the **class path**, not patched into the module
  (`useModulePath=false`), so that the `META-INF/mapped-services/*` entries of the test PDOs and of this
  module are both visible. The entries are created by the `test-analyze` goal of the
  [tentackle-maven-plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md).

Everything that needs a live session &mdash; selects, inserts, cursors, remoting, caches and the
lock manager's bookkeeping &mdash; is tested in
[tentackle-test-pdo](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) instead.
