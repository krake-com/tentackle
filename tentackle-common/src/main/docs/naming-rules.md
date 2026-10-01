# Naming Rules — Derived Class Names as a Replaceable Service

## Overview and Motivation

A single [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) or
[operation](../../../../tentackle-pdo/src/main/docs/operation.md) interface such as `Invoice` fans
out into a whole family of types: the domain interface and implementation, the persistence
interface and implementation, and — when remoting is enabled — a remote delegate interface and
implementation. Tentackle never hard-codes how these names are formed. All of them are derived by
[`NamingRules`](../java/org/tentackle/common/NamingRules.java), a singleton in `tentackle-common`
that acts as the **single authority for class-naming conventions** — consulted at build time by the
code generators and the wizard, and **at runtime** to locate the remote delegates.

Because the singleton is created via the [service API](services.md), an application can replace it
with `@Service(NamingRules.class)` and establish its own conventions — without touching the
framework, the wurblets, or the wizard templates.

## The Default Conventions

| Artifact                         | Default name                          | Example for `Invoice`       |
|----------------------------------|---------------------------------------|-----------------------------|
| Domain interface                 | `<Pdo>Domain`                         | `InvoiceDomain`             |
| Domain implementation            | `<Pdo>DomainImpl`                     | `InvoiceDomainImpl`         |
| Persistence interface            | `<Pdo>Persistence`                    | `InvoicePersistence`        |
| Persistence implementation       | `<Pdo>PersistenceImpl`                | `InvoicePersistenceImpl`    |
| Remote delegate interface        | `<Pdo>RemoteDelegate`                 | `InvoiceRemoteDelegate`     |
| Remote delegate implementation   | `<Pdo>RemoteDelegateImpl`             | `InvoiceRemoteDelegateImpl` |
| Remote delegate package          | persistence-impl package + `.trip`    | `com.example.persist.trip`  |
| Remote delegate impl. package    | same as the remote delegate package   | `com.example.persist.trip`  |

Two things are worth noting:

- **Operations follow the same rules as PDOs** by default: the `getOperation…` methods simply
  delegate to their `getPdo…` counterparts. They are separate methods, though, so a replacement
  may give operations different conventions.
- Apart from the `.trip` sub-package for remote delegates, **packages are not a `NamingRules`
  concern** — where the domain and persistence types live is decided by the application layout
  (and, when scaffolding, by the [wizard](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md)
  profiles). `NamingRules` derives *simple class names*, plus the remote sub-package relative to
  the persistence implementation's package.

## The API: Forward and Reverse

Every class-name rule comes as a **pair**: a forward method deriving the name, and a reverse method
parsing it back — returning `null` if the given name does not match the convention. (The two
package rules, `get…RemoteInterfacePackageName` and `get…RemoteImplementationPackageName`, have no
reverse.)

```java
NamingRules rules = NamingRules.getInstance();

rules.getPdoPersistenceImplementation("Invoice");          // "InvoicePersistenceImpl"
rules.getPdoFromPersistenceImplementation("InvoicePersistenceImpl");  // "Invoice"
rules.getPdoFromPersistenceImplementation("SomethingElse");           // null
rules.getPdoFromPersistenceImplementation("PersistenceImpl");         // "" (the suffix alone)
```

The reverse methods are there for code that has to *recognize* names rather than generate them —
for example, tooling mapping an implementation class back to its PDO. The framework itself
currently only uses the forward methods, but a replacement should nevertheless override forward
and reverse methods **consistently**, so that both directions keep agreeing.

Since the `getOperation…` methods delegate to the `getPdo…` methods, overriding a PDO rule changes
the corresponding operation rule as well, unless the operation rule is overridden too.

## Who Uses the Rules

### At runtime: locating the remote delegates

In a [multi-tier setup](../../../../src/main/docs/tiers.md), every persistence class talks to the
tier above through a per-session *remote delegate* (see
[TRIP](../../../../tentackle-core/src/main/docs/trip.md)). These delegate classes are **found by
name, not by registration**: the
[`RemoteDbDelegateLocator`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/trip/RemoteDbDelegateLocator.java)
service — by default
[`DbRemoteDelegateLocator`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/trip/DbRemoteDelegateLocator.java),
replaceable via `@Service` — takes the package of the persistence implementation and the
simple name of its effective class, asks `NamingRules` for the remote interface and implementation
names and packages (the `.trip` sub-package by default), and loads them via `Class.forName` —
verifying that the results really are a `RemoteDbDelegate` interface and a `RemoteDbDelegateImpl` class.

This is why generated delegates need no `META-INF` entries at all: the convention *is* the
registration. Handwritten delegates that live elsewhere can opt out by annotating the
implementation with
[`@RemoteService`](../../../../tentackle-core/src/main/java/org/tentackle/trip/RemoteService.java),
which registers it as a mapped service and bypasses the naming lookup (see
[`RemoteDbSession`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/trip/RemoteDbSession.java)).

### At build time: the code generators

The [persistence wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md)
consult the same singleton: the generated `getRemoteDelegate()` in a persistence implementation is
typed with the derived remote-interface name, `@RemoteMethod`-based remoting uses it to address
the delegate, and `AssertRemote` creates missing remote interface/implementation source files from
FreeMarker templates — in the package and under the names `NamingRules` dictates.

### At scaffolding time: the wizard

The [wizard](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md)
derives every generated source name from the entity or operation name via `NamingRules` — the
domain and persistence interfaces and implementations, the super-type names for inherited
entities, and the remote packages passed to the templates.

## Replacing the Rules

Applications with their own naming conventions subclass `NamingRules`, override the relevant
method pairs, and register the subclass:

```java
@Service(NamingRules.class)
public class MyNamingRules extends NamingRules {

  @Override
  public String getPdoPersistenceImplementation(String pdoName) {
    return pdoName + "Db";
  }

  @Override
  public String getPdoFromPersistenceImplementation(String persistenceImplementation) {
    return persistenceImplementation.endsWith("Db")
           ? persistenceImplementation.substring(0, persistenceImplementation.length() - 2)
           : null;
  }
}
```

Service resolution follows the module order described in
[Ordering and Overriding](services.md#ordering-and-overriding), so the application's registration
wins over the default.

Because the rules are consulted in **two worlds**, a replacement must be visible in both:

- **at runtime**, as an ordinary dependency of the application — otherwise the server derives the
  wrong delegate names and remote session setup fails with a `ClassNotFoundException`;
- **at build time**, on the classpath of the build tooling that generates code — i.e., as a plugin
  dependency of the `wurbelizer-maven-plugin` (which runs the wurblets) and of the
  `tentackle-wizard-maven-plugin`.

The build-time and runtime rules must agree: the names woven into the generated sources are the
names the runtime locator will look for.

## Related Documentation

- [Tentackle Common](common.md) — the module this service lives in.
- [Services / ServiceFinder](services.md) — the `@Service` mechanism behind the replaceable singleton.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the remoting protocol behind the remote delegates.
- [The Multi-Tier Cascade](../../../../src/main/docs/tiers.md) — how remote delegates connect the tiers.
- [Persistence Wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) — the generators consuming the rules at build time.
- [Wizard Maven Plugin](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) — scaffolding new PDOs and operations with the derived names.
- [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) — the domain/persistence split whose types are being named.
