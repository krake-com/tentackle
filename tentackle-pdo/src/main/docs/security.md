# Security — Permissions, Rules, and Grantees

## Overview and Motivation

Tentackle ships a complete, **ACL-based authorization** subsystem that is woven
into the [PDO](pdo.md) pattern rather than bolted on beside it. Its job is to
answer one question, consistently, on every tier of the application:

> *May **this grantee** perform **this permission** on **this object** (or class)
> within **this domain context**?*

The complementary question — *who is this grantee in the first place?* — is answered once, at login,
by [Authentication](../../../../tentackle-session/src/main/docs/authentication.md). By the time any
rule is evaluated the identity is settled: the `userId`/`userClassId` pair on the session info is
what a grantee is derived from.

Two design choices make it distinctive:

- **Authorization is data, not code.** The access rules themselves are ordinary
  PDOs — instances of the [`Security`](../java/org/tentackle/security/pdo/Security.java)
  entity stored in the `secrules` table. They can be created, edited, queried, and
  shipped between JVMs like any other entity, and edited at runtime through a UI.
  No redeploy is needed to change who may do what.
- **Enforcement is automatic where it matters most.** Because the persistence layer
  knows every PDO's class, id, [aggregate root](pdo.md#aggregates-and-the-root-context),
  and [`DomainContext`](pdo.md#the-domaincontext-and-ddd), it can apply the rules
  *during* `select`, `persist`, and `delete` — without the application asking. A PDO
  the current user may not read is simply **never handed to them**; the framework
  behaves as if the row did not exist.

The whole subsystem lives in `org.tentackle.security` (in `tentackle-pdo`), with the
generated persistence/domain halves of the rule entity in `tentackle-persistence`
and `tentackle-domain`, and the editing UI in `tentackle-fx-rdc`.

| Artifact                                                                      | Role                                                                                               |
|-------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| [`Permission`](../java/org/tentackle/security/Permission.java)                | An action that can be granted or denied (an interface; permissions imply each other by extension). |
| [`SecurityManager`](../java/org/tentackle/security/SecurityManager.java)      | Evaluates a permission request against the rules and returns a `SecurityResult`.                   |
| [`SecurityResult`](../java/org/tentackle/security/SecurityResult.java)        | The verdict: `isAccepted()`, `isDefault()`, and a human-readable `explain(...)`.                   |
| [`Security`](../java/org/tentackle/security/pdo/Security.java)                | One ACL rule, stored as a PDO in `secrules`.                                                       |
| [`GranteeDescriptor`](../java/org/tentackle/security/GranteeDescriptor.java)  | Identifies *who* a rule applies to (a user or group entity, or "all").                             |
| [`SecurityFactory`](../java/org/tentackle/security/SecurityFactory.java)      | SPI entry point: the singleton `SecurityManager` and the predefined permissions.                   |
| [`@Secured`](../java/org/tentackle/security/Secured.java)                     | Method annotation that enforces permissions via an interceptor.                                    |
| [`@PermissionService`](../java/org/tentackle/security/PermissionService.java) | SPI annotation registering a permission implementation.                                            |

---

## Permissions and Their Hierarchy

A **permission** is an interface, not an enum value. Permissions imply one another
by **interface extension**: granting a permission grants everything it extends.
Tentackle predefines six in
[`org.tentackle.security.permissions`](../java/org/tentackle/security/permissions):

```
                 ReadPermission ("READ")
                  ▲            ▲
                  │            │
      WritePermission     ViewPermission
        ("WRITE")            ("VIEW")
                  ▲            ▲
                  └─────┬──────┘
                  EditPermission ("EDIT")

      ExecutePermission ("EXEC")        AllPermission ("ALL")
```

Because `EditPermission extends WritePermission, ViewPermission` and both extend
`ReadPermission`, **granting `EDIT` implies `WRITE`, `VIEW`, and `READ`** as well.
This is what `Permission.isAllowedBy(...)` / `isDeniedBy(...)` compute when a rule
fires.

The split between the *business* permissions and the *presentation* permissions is
deliberate and important:

| Permission | Governs                                                                      | Checked by                                   |
|------------|------------------------------------------------------------------------------|----------------------------------------------|
| **READ**   | whether the *business logic*, acting for the user, may load the data at all  | persistence layer, during `select`           |
| **WRITE**  | whether the business logic may insert/update/delete                          | persistence layer, during `persist`/`delete` |
| **VIEW**   | whether the *user* may **see** the data in the UI (READ alone is not enough) | UI, via `isViewAllowed()`                    |
| **EDIT**   | whether the *user* may **change** the data in the UI intentionally           | UI, via `isEditAllowed()`                    |
| **EXEC**   | whether a service/operation method may be invoked                            | `@Secured`, remote dispatch                  |
| **ALL**    | shorthand standing in for every permission                                   | rule authoring                               |

> A back-end batch job may legitimately need to **READ/WRITE** rows the interactive
> user is not allowed to **VIEW/EDIT**. Separating the two lets the same data be
> processed by logic while staying hidden from the screen — see
> [cache.md → Security](cache.md#security), which recommends `ViewPermission` (not
> `ReadPermission`) on cached entities for exactly this reason.

There is deliberately **no `isReadAllowed()`** on a PDO: a PDO the user cannot read
is never loaded, so there is no instance on which to ask the question.

---

## Where Security Is Enforced

There are three enforcement points. The first is automatic; the other two are
declarative.

### 1. Automatically in the persistence layer (PDOs)

This is the backbone. Every load and store consults the
[`SecurityManager`](../java/org/tentackle/security/SecurityManager.java) through
`SecurityFactory.getInstance()`:

- **Reading a root entity.** When a PDO is selected through its
  [root context](pdo.md#aggregates-and-the-root-context),
  `assertRootContextIsAccepted()` evaluates `READ` on the root; denial throws a
  [`SecurityException`](../java/org/tentackle/security/SecurityException.java).
- **Reading components — aggregate-aware.** Because every component knows its root
  *without a query*, the layer evaluates the **root's** `READ` rule when a component
  is read. If the root is not readable, the component "does not exist" for this user
  and `select` returns `null`. A permission expressed once on the
  [aggregate root](pdo.md#aggregates-prevent-whole-classes-of-bugs) therefore governs
  its whole component tree — no per-component rules needed.
- **Writing.** `persist()`/`delete()` check `WRITE` on the root entity
  (`isWriteAllowed()`). Only root entities are writable; components are saved through
  their root, which is the object actually checked.
- **Class-level pre-checks.** `isReadAllowed(context)` / `isWriteAllowed(context)`
  let code (and `selectAll`) check a *whole class* before issuing a query.

The application normally writes **none** of this; it falls out of the ordinary
`select`/`persist`/`delete` calls. The rule entity itself
([`SecurityPersistenceImpl`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/security/SecurityPersistenceImpl.java))
is always readable, otherwise the manager could never load its own rules.

### 2. Declaratively on methods with `@Secured`

Any method of a PDO interface or an [Operation](operation.md) interface can be
guarded by [`@Secured`](../java/org/tentackle/security/Secured.java). The
[`SecuredInterceptor`](../java/org/tentackle/security/SecuredInterceptor.java)
([an interceptor](../../../../tentackle-core/src/main/docs/interceptors.md)) evaluates
the listed permissions against the object's domain context *before* the method body
runs, throwing `SecurityException` on denial:

```java
public interface InvoiceDomain extends DomainObject<Invoice> {

  @Secured(EditPermission.class)                 // caller needs EDIT on this invoice
  @Transaction void approve();

  @Secured({EditPermission.class, BookkeeperPermission.class}, ored = true)
  @Transaction void postToLedger();              // EDIT *or* a custom Bookkeeper permission
}
```

Annotation options:

| Element    | Meaning                                                                                                                                                                   |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `value()`  | The required permission interfaces.                                                                                                                                       |
| `ored`     | `false` (default) → **all** must be accepted (AND); `true` → **at least one** (OR).                                                                                       |
| `explicit` | `false` (default) → accepted unless a rule *denies*; `true` → a rule must *explicitly* grant it (a defaulted accept counts as denial — see `SecurityResult.isDefault()`). |

For [Operations](operation.md) (table-less services), the interceptor evaluates the
permission against the operation's *effective class*; this is the natural home for
`ExecutePermission` on service entry points.

### 3. In the UI

The UI asks the PDO directly via the presentation predicates on
[`PersistentObject`](../java/org/tentackle/pdo/PersistentObject.java):

- `isViewAllowed()` — show the object / field at all?
- `isEditAllowed()` — let the user change it, or render it read-only?
- `isWriteAllowed()`, `isPermissionAccepted(permission)`, `getSecurityResult(permission)`
  for finer control (and to surface `explain(...)` as a tooltip).

The [`tentackle-fx-rdc`](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) binding
layer uses these to enable/disable widgets automatically, and ships a
`SecurityEditor` / `SecurityRulesView` so administrators can manage rules from
within the application.

---

## The Rule Model

A [`Security`](../java/org/tentackle/security/pdo/Security.java) rule (table
`secrules`, class id `5`) is an ACL entry. Its fields answer *who*, *on what*,
*where*, and *what*:

| Field group        | Columns                                         | Meaning                                                                                                                                                 |
|--------------------|-------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Secured object** | `objectClassName`, `objectClassId`, `objectId`  | The protected target. A non-PDO class (by name), a PDO class (`objectClassId`, `objectId = 0` → *all instances*), or one PDO instance (`objectId ≠ 0`). |
| **Grantee**        | `granteeClassId`, `granteeId`                   | The user/group entity the rule applies to. `0/0` means **all grantees**.                                                                                |
| **Context**        | `domainContextClassId`, `domainContextId`       | Optional restriction to a [domain context](pdo.md#the-domaincontext-and-ddd) entity (e.g. a tenant). `0/0` → any context.                               |
| **Decision**       | `permissions`, `allowed`, `message`, `priority` | A comma-separated permission list, the allow/deny flag, an optional i18n explanation, and the evaluation order.                                         |

### How a request is evaluated

The [`DefaultSecurityManager`](../java/org/tentackle/security/DefaultSecurityManager.java)
treats the rule set as an **access control list walked in priority order, first
match wins**:

1. **Determine the grantee** from the session's user
   (`SessionInfo.getUserId()` / `getUserClassId()`). A **system user**
   (`userId ≤ 0`) bypasses all checks — every permission is granted.
2. **Determine the grantees to check** — by default the user itself plus the
   wildcard `0/0` ("all"). Override `determineGranteesToCheck(...)` to expand this to
   the user's **groups/roles** (see below).
3. **Select the candidate rules** for the target, sorted by `priority` (0 = highest
   / evaluated first). Three separate indexes exist for the three target kinds:
   instance rules, PDO-class rules, and non-PDO-class rules.
4. **Fire the first matching rule.** A rule matches when its context applies
   (`DomainContext.isWithinContext(...)`) **and** the requested permission is
   implied by the rule's permission list (`isAllowedBy` for allow-rules,
   `isDeniedBy` for deny-rules). Its `allowed` flag becomes the verdict.
5. **Fall back.** For an instance request with no matching instance rule, the
   manager retries at the **class** level (`objectId = 0`). If still nothing fires,
   the **default** applies.

```java
SecurityResult r = SecurityFactory.getInstance().getSecurityManager()
        .evaluate(context, SecurityFactory.getInstance().getReadPermission(),
                  classId, objectId);
if (!r.isAccepted()) {
  throw new SecurityException(session, r.explain("not allowed to read"));
}
```

### Accept-by-default vs. deny-by-default

`SecurityManager.setAcceptByDefault(...)` chooses the model when **no rule fires**:

- **accept by default** (the default) → a **blacklist**: everything is permitted
  unless a rule denies it. Add deny-rules for the exceptions.
- **deny by default** → a **whitelist**: nothing is permitted unless a rule grants
  it. Add allow-rules for what is permitted.

Setting *both* accept and deny to false yields a "neither accepted nor denied"
result, which the `explicit = true` mode of `@Secured` can require to be turned into
an explicit grant.

> The manager is **disabled by default** and must be enabled at startup
> (`setEnabled(true)`). While disabled, **every** request is granted regardless of
> the default — convenient for tests and tooling, and the reason production startup
> code must remember to enable it.

---

## Grantees, Users, and Groups

A grantee is identified by a [`GranteeDescriptor`](../java/org/tentackle/security/GranteeDescriptor.java)
— a `(classId, id)` pair, usually constructed from a user or group **PDO**. The
session carries the current user's id and class id; `determineGrantee(context)` turns
them into the descriptor.

Out of the box a user is checked against rules for *itself* and for *all*. To support
**roles or groups**, subclass `DefaultSecurityManager` and override
`determineGranteesToCheck(...)` to add the descriptors of every group the user
belongs to:

```java
@Override
protected Collection<GranteeDescriptor> determineGranteesToCheck(
        DomainContext context, GranteeDescriptor grantee) {
  Collection<GranteeDescriptor> grantees = super.determineGranteesToCheck(context, grantee);
  User user = Pdo.create(User.class, context).select(grantee.getGranteeId());
  for (Group g : user.getGroups()) {
    grantees.add(new GranteeDescriptor(g));     // rules granted to the group now apply
  }
  return grantees;
}
```

The per-grantee expansion is memoized in the manager's `granteeMap`, so the group
lookup happens once per distinct user, not on every check.

---

## Context Scoping and Multi-Tenancy

Because each rule may carry a `domainContext` target and each PDO carries its
[`DomainContext`](pdo.md#the-domaincontext-and-ddd), permissions can be scoped to a
**logical data space**. A rule restricted to context *Tenant 42* fires only when the
object being checked lives within that context (`isWithinContext(...)`). This dovetails
with Tentackle's [multi-tenancy](pdo.md#logical-data-spaces-and-multi-tenancy): a single
rule set can express "user X is a bookkeeper **in tenant A** but only a viewer **in
tenant B**" without duplicating entities. On the middle tier,
`DomainContext.assertPermissions()` additionally verifies the incoming context is
allowed for the connected user (see [pdo.md → Sessions, immutability, and remote
safety](pdo.md#sessions-immutability-and-remote-safety)).

---

## Custom Permissions

Define a new permission by writing an **interface** (extend an existing permission to
inherit its implication) and an **implementation** annotated with
[`@PermissionService`](../java/org/tentackle/security/PermissionService.java) so the
[`ServiceFinder`](../../../../tentackle-common/src/main/docs/services.md) discovers it:

```java
public interface BookkeeperPermission extends EditPermission {   // implies EDIT/WRITE/VIEW/READ
  String NAME = "BOOKKEEPER";
}

@PermissionService(BookkeeperPermission.class)
public final class BookkeeperPermissionImpl
       extends AbstractPdoPermission implements BookkeeperPermission {
  @Override public String getName() { return NAME; }
}
```

The name (`"BOOKKEEPER"`) is what appears in a rule's comma-separated `permissions`
column; `SecurityFactory.getPermissionInterface(name)` maps it back to the interface
at evaluation time. Custom permissions are first-class: usable in `@Secured`, in rule
data, and in `isPermissionAccepted(...)`.

---

## Rules Are PDOs — Lifecycle and Coherency

Treating rules as PDOs has concrete operational consequences:

- **They are cached.** `DefaultSecurityManager` loads the whole rule set with
  `selectAllCached()` into in-memory ACL maps, so evaluation is pure CPU work with no
  database round-trip ([cache.md](cache.md)).
- **They self-invalidate.** The manager registers a
  [`ModificationListener`](../../../../tentackle-session/src/main/docs/session.md) on
  the `Security` class. Any insert/update/delete of a rule — from anywhere in the
  cluster — calls `invalidate()`, and the next request lazily rebuilds the maps. Rule
  changes thus take effect **without a restart** and stay coherent across JVMs.
- **They are remoting-aware.** A remote client's manager initializes against the
  server (`assertRemoteSecurityManagerInitialized()`); evaluation and rule loading
  dispatch over [TRIP](../../../../tentackle-core/src/main/docs/trip.md) like any
  PDO operation. Remote **service** invocation is itself gated by `ExecutePermission`
  before dispatch.
- **They can go stale.** Since the model has no FK from a rule to its target,
  deleting a secured object or a grantee can orphan a rule.
  `removeObsoleteRules(session)` sweeps rules whose object or grantee no longer exists.

---

## Pluggability

`SecurityManager` is an SPI obtained through
[`SecurityFactory`](../java/org/tentackle/security/SecurityFactory.java). The
[`DefaultSecurityManager`](../java/org/tentackle/security/DefaultSecurityManager.java)
(ACL-on-`secrules`) is only the default. An application may:

- **subclass** it to add groups, custom defaults, or extra rule kinds (override the
  `determine…`/`create…SecurityResult` hooks); or
- **replace** it wholesale with a manager backed by LDAP, an external IAM/policy
  engine, or hard-coded policy — the `Security` entity is then unused. Everything
  upstream (`@Secured`, persistence checks, `isViewAllowed()`, the UI) is written
  against the `SecurityManager` interface and keeps working unchanged.

When the identity comes from an external provider, the roles it asserted are available too:
`RemoteDbSessionImpl.getAuthentication()` carries the verified claims and groups of the login, which
is the natural input for a `determineGranteesToCheck` override that maps identity-provider groups
onto Tentackle grantees. See
[Authentication](../../../../tentackle-session/src/main/docs/authentication.md).

---

## Use Cases at a Glance

| Goal                                           | How                                                                                         |
|------------------------------------------------|---------------------------------------------------------------------------------------------|
| Hide a whole entity type from a user           | Deny `READ` (or `VIEW`) rule on the PDO **class** for that grantee.                         |
| Hide one record                                | Deny rule on the PDO **instance** (`objectId ≠ 0`).                                         |
| Read-only screen for some users                | Grant `VIEW` but not `EDIT`; the UI renders fields read-only via `isEditAllowed()`.         |
| Let a batch process data users can't see       | Grant `READ`/`WRITE` to the batch user, withhold `VIEW`/`EDIT`.                             |
| Protect a business operation                   | `@Secured(...)` on the domain/operation method.                                             |
| "At least one of" several rights               | `@Secured({A.class, B.class}, ored = true)`.                                                |
| Require an explicit grant (whitelist a method) | `@Secured(value = …, explicit = true)` and/or deny-by-default.                              |
| Per-tenant authorization                       | Scope rules to a `domainContext` target.                                                    |
| Roles/groups                                   | Override `determineGranteesToCheck(...)`.                                                   |
| App-specific right                             | Custom `Permission` interface + `@PermissionService` impl.                                  |
| Change policy without redeploy                 | Edit `Security` rules (e.g. via the RDC `SecurityEditor`); caches invalidate automatically. |
| Swap the whole mechanism                       | Provide an alternative `SecurityManager` via `SecurityFactory`.                             |
| Lock everything down by default                | `setAcceptByDefault(false)` and grant explicitly.                                           |
| Grant system/superuser access                  | Run under a session whose `userId ≤ 0`.                                                     |

---

## Summary

| Concept                              | What it gives you                                                           |
|--------------------------------------|-----------------------------------------------------------------------------|
| Permissions as extensible interfaces | Implication for free (`EDIT` ⇒ `WRITE`/`VIEW`/`READ`); add your own.        |
| Read/Write vs. View/Edit split       | Business-logic access decoupled from UI presentation.                       |
| Rules as PDOs (`secrules`)           | Authorization is editable data — cached, coherent across JVMs, no redeploy. |
| Automatic persistence-layer checks   | Unauthorized rows are never loaded or written; nothing for the app to wire. |
| Aggregate-aware enforcement          | One rule on the root governs its whole component tree, with no extra query. |
| `@Secured` interceptor               | Declarative method-level guards with AND/OR and explicit-grant modes.       |
| Context-scoped rules                 | Per-tenant / per-data-space authorization.                                  |
| Grantee expansion hook               | Users, groups, and roles.                                                   |
| `SecurityManager` SPI                | Replace or extend the entire mechanism (LDAP, IAM, custom).                 |

## See Also

- [pdo.md](pdo.md) — PDOs, the `DomainContext`, and the aggregate roots the rules key on.
- [cache.md](cache.md) — why cached entities should use `ViewPermission`, not `ReadPermission`.
- [operation.md](operation.md) — securing table-less service operations with `@Secured`.
- [interceptors.md](../../../../tentackle-core/src/main/docs/interceptors.md) — the interception mechanism behind `@Secured`.
- [session.md](../../../../tentackle-session/src/main/docs/session.md) — the session/user the grantee is derived from, and the `ModificationTracker`.
- [authentication.md](../../../../tentackle-session/src/main/docs/authentication.md) — how that user is established at login: credentials, providers, single sign-on.
- [trip.md](../../../../tentackle-core/src/main/docs/trip.md) — how security evaluation and rule loading work across tiers.
- [services.md](../../../../tentackle-common/src/main/docs/services.md) — the SPI discovery used for permissions and the security manager.
- [rdc.md](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) — the `SecurityEditor` UI for managing rules.
