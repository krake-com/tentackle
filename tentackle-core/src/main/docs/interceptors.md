# Interceptors — Annotation-Driven Method Interception

## Overview and Motivation

Tentackle's interception API (`org.tentackle.reflect`, module `tentackle-core`)
lets you wrap the invocation of a method with additional behavior — a
*cross-cutting concern* such as starting a transaction, checking a permission,
logging the duration of a call, or transforming arguments and return values —
**without touching the method's body**.

An interceptor is declared as an **annotation on the method** it should guard.
At runtime the framework discovers the annotation, looks up the paired
interceptor implementation, and weaves it around the actual call. Because the
[PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) layer is built on dynamic
proxies, interception is the natural extension point for everything that has to
happen *around* a domain- or persistence method:

```java
public interface Invoice extends PersistentDomainObject<Invoice> {

  @Transaction                         // wrap in a transaction
  @Secured(BookInvoicePermission.class) // check permission first
  void book();
}
```

Calling `invoice.book()` now transparently evaluates the permission, opens a
transaction, runs the method, and commits (or rolls back) — none of which is
visible in the implementing code.

### Why interceptors?

- **Declarative** — the *what* (transactional, secured, logged) sits as an
  annotation next to the method it applies to, not buried in imperative
  boilerplate repeated across every method.
- **Separation of concerns** — the domain- and persistence implementations stay
  free of plumbing. Transactions, security, and logging live in their own
  interceptor classes.
- **Composable** — several annotations on one method form an ordered *chain* of
  interceptors, each able to run code before and after the next.
- **Remoting-aware** — because interceptors sit in the proxy, they apply
  uniformly whether a PDO is invoked locally or across a [TRIP](trip.md)
  connection. Some interceptors (e.g. `@Transaction`) are deliberately
  remote-aware and can short-circuit to the server to save round trips.
- **Compile-time checked** — annotation processors verify that every
  interception annotation is applied to a legal target, so mistakes surface at
  build time, not at runtime.

### Design principles

- **Interceptors are stateless and cached.** An `Interceptor` is created **once
  per class and annotation occurrence** and reused for every invocation.
  Therefore, an interceptor **MUST NOT hold per-invocation state** — all runtime
  context is passed in through the `invoke`/`proceed` parameters.
- **Annotation and implementation are separated.** The annotation (e.g.
  `@Transaction`) is pure metadata; the logic lives in a paired `Interceptor`
  (`TransactionInterceptor`). The `@Interception` meta-annotation links them.
- **Only the proxy is interceptable.** Interception is a feature of the dynamic
  proxy serving an [`Interceptable`](#the-interceptable-marker) interface, not of
  the plain delegate object. An interceptor only fires when the method is called
  *through* the proxy.

---

## The Core Concepts

The mechanism rests on five types in `org.tentackle.reflect`:

| Type                                               | Role                                                                                          |
|----------------------------------------------------|-----------------------------------------------------------------------------------------------|
| `@Interception`                                    | Meta-annotation that marks another annotation as an interceptor and names its implementation. |
| `Interceptor`                                      | The runtime contract — an `InvocationHandler` that can be chained and carries its annotation. |
| `AbstractInterceptor`                              | Convenience base class; you implement a single `proceed(...)` method.                         |
| `Interceptable`                                    | Marker interface; only interfaces extending it can carry interceptors.                        |
| `InterceptableMethod` / `InterceptableMethodCache` | Runtime wiring that resolves and caches the interceptor chain per method.                     |

### The `@Interception` meta-annotation

You never implement `Interceptor` discovery yourself. Instead, you write an
ordinary annotation and meta-annotate it with `@Interception`, telling the
framework which class implements the behavior:

```java
@Documented
@Retention(RetentionPolicy.RUNTIME)   // must be RUNTIME — discovered by reflection
@Target(ElementType.METHOD)
@Interception(implementedBy = LogItInterceptor.class)
public @interface LogIt {
}
```

`@Interception` offers **four ways to bind the implementation**, in increasing
order of decoupling:

1. **By class** — `implementedBy = LogItInterceptor.class`. Direct, type-safe,
   but creates a compile-time dependency on the implementation.
2. **By class name** — `implementedByName = "com.acme.LogItInterceptor"`.
   Avoids the dependency, but is prone to typos.
3. **By service name** — `implementedByService = "logIt"`. The implementation is
   located via the [`ServiceFinder`](../../../../tentackle-common/src/main/docs/services.md)
   SPI and must carry a matching `@ServiceName`.
4. **By service class** — omit all of the above. The framework looks up a
   service registered under the *annotation's* fully-qualified name (the
   implementation is annotated with `@Service` referencing the annotation).

Options 2–4 let the annotation live in one module (e.g., the model/API) while the
interceptor implementation lives in another, without a hard dependency between
them.

### The `type` parameter — PUBLIC, HIDDEN, and ALL

An interception has a *visibility* that controls **where** the annotation may
appear:

- **`PUBLIC`** — the annotation belongs on **interface methods** only, i.e., it is
  part of the model and visible to API consumers. `@Transaction` and `@Secured`
  are public interceptors.
- **`HIDDEN`** — the annotation belongs on **implementing class methods** only,
  i.e., an internal concern invisible in the model interfaces (e.g.
  `@InvokeInBuddyDelegate`).
- **`ALL`** *(default)* — no restriction; the annotation may appear on either.

```java
@Interception(implementedBy = TransactionInterceptor.class, type = Interception.Type.PUBLIC)
public @interface Transaction { ... }
```

### The `Interceptable` marker

```java
public interface Interceptable {
}
```

`Interceptable` is a plain marker interface. Interceptors are only discovered on
methods whose declaring interface (for public interceptors) or implementing
class (for hidden interceptors) is assignable to `Interceptable`. In practice
the PDO interfaces (`DomainObject`, `PersistentObject` and the PDOs and
operations that extend them) are all `Interceptable`, so any method you declare
there can carry interceptors.

---

## Writing an Interceptor

The recommended way is to extend `AbstractInterceptor` and implement the single
`proceed(...)` method. The simplest possible interceptor — log every call:

```java
public class LogItInterceptor extends AbstractInterceptor {

  @Override
  public Object proceed(Object proxy, Method method, Object[] args,
                        Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable {
    LOGGER.info("intercepting " + orgMethod.toGenericString());
    return method.invoke(proxy, args);   // proceed to the next interceptor / the real method
  }
}
```

The call `method.invoke(proxy, args)` is what **continues the chain**. Whatever
you do before it runs *before* the method; whatever you do after it runs *after*
the method returns. To suppress the call entirely (e.g., a failed security check)
simply throw instead of invoking.

### The `proceed` parameters

`AbstractInterceptor` passes two triplets:

| Parameter                          | Meaning                                                                                                                               |
|------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `proxy`, `method`, `args`          | The target to invoke **next** in the chain. Always call `method.invoke(proxy, args)` to proceed.                                      |
| `orgProxy`, `orgMethod`, `orgArgs` | The **original** proxy, interface method and arguments of the outermost call. Use these for diagnostics and to inspect the real call. |

When interceptors are not chained, both triplets are identical. In a chain the
first triplet points at the next interceptor while the `org*` triplet always
refers to the original invocation — so you can log `orgMethod` while still
proceeding through `method`.

### Reading the annotation

The annotation instance that triggered the interceptor is injected via
`setAnnotation()`. Read its attributes there **once** and cache them in fields —
remember the interceptor is reused, so the annotation never changes for a given
instance:

```java
public class OffsetInterceptor extends AbstractInterceptor {

  private int offset;

  @Override
  public void setAnnotation(Annotation annotation) {
    super.setAnnotation(annotation);
    offset = ((Offset) annotation).value();   // read attribute once
  }

  @Override
  public Object proceed(Object proxy, Method method, Object[] args,
                        Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable {
    return (Integer) method.invoke(proxy, args) + offset;   // transform the result
  }
}
```

This `@Offset(5)` example also shows that an interceptor may freely **transform
the return value** (and likewise the `args` before proceeding).

> **Statelessness is mandatory.** Because the framework caches one interceptor
> instance per class + annotation, fields holding *per-call* state would leak
> across concurrent invocations. Only configuration derived from the annotation
> (which is constant) may be stored in fields.

---

## Chaining and Execution Order

A single method may carry several interception annotations. The framework
collects them and builds a **chain**, where each interceptor's
`method.invoke(...)` calls into the next, and the last one calls the real
delegate method.

The order is deterministic:

1. **Public interceptors first**, in the order the annotations appear on the
   **interface** method.
2. **Hidden interceptors next**, in the order they appear on the
   **implementing** method.

So for:

```java
public interface Invoice extends PersistentDomainObject<Invoice> {
  @Transaction
  @Secured(BookPermission.class)
  void book();
}
```

the runtime chain is `Transaction → Secured → book()`. `@Transaction` runs
outermost: it opens the transaction, then delegates to `@Secured`, which checks
the permission and only then proceeds to the actual `book()` implementation. On
the way out the transaction commits.

Chaining is wired in `InterceptionUtilities.findInterceptors()`: the list of
interceptors is linked back-to-front with `setChainedInterceptor()`, and
`AbstractInterceptor.invoke()` transparently forwards `proceed` to the chained
interceptor or, at the end of the chain, to the delegate.

> **Only one interceptor in a chain should ultimately invoke the real method.**
> Each interceptor proceeds to the *next link*; the framework guarantees the
> last link targets the delegate. Do not invoke the delegate directly from the
> middle of a chain.

---

## How It Is Wired at Runtime

You normally never construct any of this by hand — it is set up by the PDO
proxy machinery — but understanding the flow helps when debugging.

1. A dynamic proxy serving an `Interceptable` interface routes every call
   through an invocation handler (e.g. `InterceptableInvocationHandler` or the
   PDO/Operation handlers).
2. The handler asks an `InterceptableMethodCache` (one per effective class) for
   the `InterceptableMethod` of the called `Method`. This is computed **once**
   and cached in a `ConcurrentHashMap`.
3. Building an `InterceptableMethod` calls
   `InterceptionUtilities.createInterceptableMethod()`, which:
   - resolves the **implementing method** on the delegate class;
   - scans the **interface method** for `PUBLIC`/`ALL` interceptor annotations;
   - scans the **implementing method** for `HIDDEN`/`ALL` interceptor
     annotations;
   - instantiates each interceptor (via `implementedBy` / name / service),
     calls `setAnnotation()`, and chains them.
4. On invocation, `InterceptableMethod.invoke()` calls the head interceptor if
   present, otherwise invokes the delegate method directly (no overhead when a
   method has no interceptors).

Because annotations on dynamic proxies are themselves proxies, the framework
deliberately resolves the *real* annotation type via `annotationType()` (not
`getClass()`) when scanning — a subtlety worth knowing if you write your own
discovery code.

---

## Built-in Interceptors

Tentackle ships a number of interceptors. The most important ones, all in
`tentackle-pdo` unless noted:

| Annotation                             | Type   | Purpose                                                                                                            |
|----------------------------------------|--------|--------------------------------------------------------------------------------------------------------------------|
| `@Transaction`                         | PUBLIC | Wrap the method in a transaction — begin/commit, rollback on exception, optional retry, isolation and writability. |
| `@NoTransaction`                       | PUBLIC | Assert that the method runs *without* a (new) transaction; useful for cached PDOs.                                 |
| `@Secured`                             | PUBLIC | Evaluate one or more `Permission`s against the domain context before proceeding (`org.tentackle.security`).        |
| `@PreCommit` / `@PostCommit`           | PUBLIC | Register the method to run as part of the pre-/post-commit phase of the surrounding transaction.                   |
| `@WithinContext` / `@NotWithinContext` | PUBLIC | Require that named objects are (not) present in the current domain context.                                        |
| `@LocalSessionOnly`                    | ALL    | Restrict the method to a local (non-remote) session.                                                               |
| `@InvokeInBuddyDelegate`               | HIDDEN | Internal redirection of an implementation method to a buddy delegate.                                              |
| `@Log`                                 | ALL    | Measure and log the execution time of the method (`tentackle-core`, `org.tentackle.log`).                          |

`@Transaction` is a good illustration of a **remote-aware** interceptor: when the
session is remote and the implementation is a `@RemoteMethod`, it forwards the
call to the server's own `TransactionInterceptor` rather than opening the
transaction locally, saving the begin/commit round trips.

---

## Compile-Time Verification

`@Interception` is itself meta-annotated with `@Analyze`, hooking it into
Tentackle's build-time annotation processing. Depending on the declared `type`,
a matching processor enforces correct usage:

- `AllInterceptorAnnotationProcessor` — verifies the annotated method belongs to
  an `Interceptable` (such as `DomainObject`, `PersistentObject` or an
  implementation).
- `PublicInterceptorAnnotationProcessor` — additionally requires the annotation
  to sit on a **declared interface method** (`type = PUBLIC`).
- `HiddenInterceptorAnnotationProcessor` — additionally requires the annotation
  to sit on an **implementing method** (`type = HIDDEN`).

Misplacing an interceptor annotation — e.g., putting a `PUBLIC` interceptor on a
class method, or any interceptor on a method outside an `Interceptable` — is
therefore reported as a compilation error.

---

## Writing Your Own — Checklist

1. **Create the annotation.** `@Retention(RUNTIME)`, an appropriate `@Target`
   (`METHOD`), and meta-annotate with `@Interception(implementedBy = ...,
   type = ...)`. Add attributes if the behavior is configurable.
2. **Implement the interceptor.** Extend `AbstractInterceptor`, override
   `proceed(...)`, and call `method.invoke(proxy, args)` to continue the chain.
3. **Read configuration in `setAnnotation()`** and cache it in fields. Keep the
   interceptor otherwise **stateless**.
4. **Apply the annotation** on a method of an `Interceptable` interface (PUBLIC)
   or its implementation (HIDDEN). The build verifies placement.
5. **Mind ordering** when combining with other interceptors — public before
   hidden, source order within each.

---

## Related Documentation

- [PDO — Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) —
  the proxy layer interceptors plug into.
- [Validation](validation.md) — a sibling annotation-driven mechanism with the
  same separation of annotation and implementation.
- [TRIP](trip.md) — remoting, relevant to remote-aware interceptors.
- [Services](../../../../tentackle-common/src/main/docs/services.md) — the SPI
  used for `implementedByService` binding.
