## Unit Testing Domain Logic

Domain logic is tested without a database by pairing the real domain delegate with a **mock persistence
delegate** from [`tentackle-pdo-mock`](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md#the-mock-delegates)
and letting the ordinary factory build the PDO. This module's own tests (`src/test/java`) follow that recipe
and double as worked examples:

```java
// the mock is registered like any other persistence implementation
@PersistentObjectService(NumberRange.class)
public class NumberRangePersistenceMock extends MockPersistentObject<NumberRange, NumberRangePersistenceMock>
       implements NumberRangePersistence {
  // back the attributes the domain logic reads and writes; everything else inherits "not implemented"
}

// Pdo.create() builds the real proxy: real domain delegate, mocked persistence delegate
NumberRange range = Pdo.create(NumberRange.class);
range.setBegin(10);
range.setEnd(20);
assertEquals(range.size(), 10);
```

Two things are worth knowing:

- Both delegates must **implement their declaring interface directly** (`NumberRangeDomain` resp.
  `NumberRangePersistence`). The `Mixin` locates the declaring interface via `getInterfaces()` on the
  implementation class, so inheriting it from a base class is not enough.
- The tests run on the **class path**, not patched into the module (`useModulePath=false` in the surefire
  configuration). A patched module resolves `META-INF/mapped-services/*` from the patch directory alone, which
  would hide this module's own domain implementations behind the ones registered by the tests.

Tests that need a live database belong in
[`tentackle-test-pdo`](../../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) instead.
