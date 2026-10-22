# What a Seasoned Java Developer Would Not Expect

Tentackle looks familiar from a distance — entities, transactions, a desktop client, Maven plugins.
Up close, it deviates from mainstream Java frameworks (Spring, JPA/Hibernate, Jakarta EE) in ways
that routinely surprise experienced developers. This document is the distilled list of those
deviations, each with a pointer to the document that covers it in depth.

## The programming model

- **Entities are interfaces, and `new` does not compile.** Every PDO, operation, and most framework
  components are interfaces; implementations are located via the build-time
  [service registry](../../../tentackle-common/src/main/docs/services.md) and instantiated as
  dynamic proxies: `Invoice invoice = on(Invoice.class)`.
- **Multiple inheritance, emulated.** A [PDO](../../../tentackle-pdo/src/main/docs/pdo.md) weaves
  *two separate implementation classes* — one for persistence, one for domain logic — into a single
  object via a proxy. The two concerns never compete for the one `extends` slot, and neither layer
  knows the other.
- **The entity model lives in a comment block.** Attributes, relations, indexes, and validations are
  declared in a [DSL](../../../tentackle-model/src/main/docs/model-definition.md) embedded as a
  comment in the PDO interface source — a single definition from which the persistence interfaces
  and implementations, the DDL, and the database migrations are all generated.
- **Generated code is woven into your sources**, inside guarded folding regions, by the
  [Wurbelizer](../../../tentackle-wurblets/src/main/docs/wurblets.md) — not dumped into
  `target/generated-sources`. You read and debug one file per class.
- **Rich domain objects, not anemic ones.** Because a PDO is never in a degraded state, behavior
  lives *on* the entity (`invoice.approve()`), not in transaction-script services — see
  [PDOs vs. Traditional ORMs](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md).

## No ORM lifecycle

- **There is no `LazyInitializationException` — the exception type does not exist.** A PDO carries
  its session and context inside itself and can navigate any relation at any time, in any JVM,
  no matter how many tiers separate it from the database.
- **No entity states.** No managed/detached/removed, no persistence context, no `merge()`, no fetch
  plans, no Open-Session-In-View, and no DTO layer — the PDO itself travels between JVMs and stays
  fully functional on arrival.
- **No identity map.** A session holds no references to the PDOs loaded through it, so objects are
  garbage-collected the moment your code drops them — batch loops never need `em.clear()`. Shared
  identity is an opt-in, per-entity choice via the
  [PdoCache](../../../tentackle-pdo/src/main/docs/cache.md).
- **Explicit saves.** There is no automatic dirty-flush; you call `persist()` where you mean it.
  Modification is still tracked per attribute — the write is just where you put it.
- **Transactions demarcate the database, not your objects.** A
  [`@Transaction`](../../../tentackle-pdo/src/main/docs/transaction.md) commits or rolls back the
  unit of work; object usability is never tied to its boundary.

## Aggregates are enforced, not a convention

- **The persistence layer knows root entities and components.** Each component row carries its
  root's id and type ([rootId/rootClassId](../../../tentackle-pdo/src/main/docs/root-columns.md)),
  so a component knows its aggregate root without a query.
- **A component cannot be persisted on its own.** The "set the foreign key and save the child" path
  that every ORM allows simply has no API; the only door into the database is the root, and
  persisting the root validates the *entire* aggregate first — including cross-entity invariants.
- **A component loaded outside its root context is made immutable**, and authorization is
  aggregate-scoped: if you may not read the root, its components behave as if they did not exist.
- **Undo is aggregate-scoped too.** A [snapshot](../../../tentackle-pdo/src/main/docs/snapshot.md)
  reverts root attributes, component lists, and modification flags atomically — and a stale
  snapshot is rejected instead of silently misapplied.

## Location transparency and the multi-tier cascade

- **Every JVM is a node, and a server is itself a client.** Nodes stack into a
  [cascade](./tiers.md) of arbitrary depth — client → edge proxy → core server → database — and the
  same artifact runs unchanged at any position. Whether a session is local JDBC or remote is decided
  **solely by a connection URL**; adding or removing a tier is configuration, never code.
- **Caches stack per tier.** Reads trickle up until a tier's
  [PdoCache](../../../tentackle-pdo/src/main/docs/cache.md) hits; invalidations trickle down via
  database-backed serial counters — a coherent multi-level cache hierarchy with surgical expiry,
  no message broker required ([modification tracking](../../../tentackle-session/src/main/docs/modification-tracking.md)).
  CQRS-style read/write splitting [falls out of the cascade](./tiers.md#using-the-cascade-for-cqrs-on-transaction-data)
  as a deployment choice.
- **Cursors work remotely.** A [`ResultSetCursor`](../../../tentackle-persistence/src/main/docs/cursors.md)
  streams a JDBC `ResultSet` held on the server while a client three tiers away drives it — the
  batch code cannot tell the difference.
- **The change tracker is deliberately a non-daemon thread.** If it dies, the application dies with
  it: an app that has silently lost its cache-coherency notifications would be *wrong quietly*, and
  Tentackle would rather stop.

## A wire protocol of its own

- **[TRIP](../../../tentackle-core/src/main/docs/trip.md) replaces RMI and Java serialization**
  with a compact binary format: a type dictionary transmitted once per client, variable-length
  encoding, deduplication, and sparse-collection optimizations. Remote interfaces are plain Java
  interfaces — no `throws RemoteException`, no export/unexport ceremony.
- **SD-WAN and NAT friendly.** Unlike RMI, TRIP never sends hosts, IPs, or ports back to the
  client, so gateways and relays just work — and each new remote proxy costs no extra round-trip.
- **Transports are picked per link by URL scheme**: plain TCP, TLS, deflate compression,
  pre-shared-key encryption (for links without a PKI), and
  [QUIC/RFC-9000](../../../tentackle-quic/src/main/docs/quic.md) — mixed freely within one cascade.
- **Version skew fails at connect time, not mid-stream.** Built-in client/server version checking
  and an evolution-tolerant type dictionary turn incompatibility into a clear, immediate error.

## Correctness is always on

The framework's first commitment is [fail loudly and early rather than corrupt quietly](./correctness-first.md):

- **Optimistic locking cannot be turned off.** Every write carries the `serial` the object had when
  read; a stale write matches zero rows and is rejected — from a form, a batch, or an admin tool
  alike ([locking](../../../tentackle-persistence/src/main/docs/locking.md)). An opt-in, auto-expiring
  **token lock** adds early conflict warning on top.
- **A `@Transaction(retry = true)` transparently retries** a whole unit of work when the failure was
  temporary — an optimistic-lock conflict, a deadlock, a serialization failure.
- **[Validation](../../../tentackle-core/src/main/docs/validation.md) travels with the object.**
  Declared on the member, scope-aware (persisting vs. interactive), severity-aware, executed before
  every persist regardless of code path — and a `ValidationResult` survives a TRIP round-trip with a
  path (`invoice.lineList[3].price`) that a client can map back to the exact widget.
- **Annotations take dynamic parameters.** `condition = "$isPrinted"` or a Groovy expression —
  [CompoundValue](../../../tentackle-core/src/main/docs/compound-value.md) evaluates strings into
  constants, property references, or scripts at runtime, side-stepping Java's compile-time-constant
  limitation.
- **Objects can be switched read-only at runtime.** The
  [Immutable](../../../tentackle-core/src/main/docs/immutable.md) contract guards every generated
  setter; cached PDOs are shared across threads *finally* immutable, and freezing cascades along
  aggregate boundaries. [Freezable](../../../tentackle-common/src/main/docs/freezable.md) extends
  the same idea to mutable JDK value types like `java.util.Date`.
- **[Interceptors](../../../tentackle-core/src/main/docs/interceptors.md) are compile-time
  verified** and deterministically ordered — a misplaced `@Transaction` or `@Secured` is a
  compilation error, not a runtime surprise.

## Everything resolvable at build time is resolved at build time

- **No classpath scanning, ever.** Service discovery is written by the compiler into `META-INF`
  text files and looked up from maps at runtime
  ([services](../../../tentackle-common/src/main/docs/services.md)). Annotation *parameters* are
  captured as data too, so resolving "class id 2001 ⇄ `Message`" loads no classes at all — startup
  time does not grow with the model.
- **The same SPI works on the classpath and the module path.** Every artifact an application ships
  with is a real [JPMS module](./modules.md#named-modules-and-automatic-modules) with an explicit
  `module-info.java`, yet applications run modular, non-modular, or mixed — the service registry is
  data, not a scan, so it does not care which.
- **The database schema is derived, migrated, and verified from the model.** The
  [SQL plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md)
  generates backend-specific DDL, computes incremental migrations by comparing the model against a
  live database's metadata, and can fail the build if the two drift.
- **Even resource bundles and validation annotations are build-checked** by the
  [check plugin](../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md).

## The model is queryable — and the tooling runs headless

A model that lives in a comment block raises an obvious objection: how does any tool — or any coding
agent — see it? Tentackle answers with [tooling of its own](./ai-agents.md):

- **The model is served to coding agents over MCP.** `mvn tentackle-mcp:serve` holds the model
  loaded and *relation-resolved* — as the code generators see it, not as the `.java` source spells
  it with `$classname`, `$classid` and `$mapping` still unexpanded — and answers over Streamable
  HTTP. It is read-only: it writes no file, opens no database connection, and loads no application
  class.
- **Model authoring becomes a sub-second loop.** `model_validate` stages a *candidate* definition
  into a scratch copy of the model, resolves relations across the whole set, and reports the errors
  that would otherwise surface as a `WurbelTerminationException` after a full reactor build. The
  live model is untouched and nothing is written.
- **The JavaFX wizards all run headless.** `tentackle-wizard:pdo` scaffolds an entity's whole set of
  source files from a directory of model definitions, `:browse` turns persisted records into Java
  fixture code, and `:script` runs real application code (`on()`, `op()`) against a live backend —
  the same three tools a developer clicks through, driven entirely from the command line. Six
  ready-made agent skills ship as templates under the plugins' `src/skills/`.

## A full-stack JavaFX framework

Tentackle is one of the very few frameworks that cover a JavaFX application **full-stack** — the
same programming model reaches from the SQL database through the middle tiers into the desktop UI.
The JavaFX ecosystem offers control libraries and themes, but almost nothing that binds a UI to a
persistence layer, a security model, i18n, and a deployment story in one piece:

- **An extended FX layer, not a control kit.** Zero per-widget glue. Every wrapped control is a first-class, bindable
  component carrying a *model type*, a value translator, and mandatory/changeable/error state.
  Controllers are discovered via the SPI, their FXML/CSS/bundles located by convention, and
  components bound to model members **by name** — in the model's own types (`BigDecimal`,
  `LocalDate`, an enum, a PDO), never as raw strings
  ([tentackle-fx](../../../tentackle-fx/src/main/docs/fx.md)).
- **FXML, CSS, and `.properties` live in `src/main/java`**, co-located with their controllers —
  copied as resources during the build, so SceneBuilder finds them by relative path.
- **The rich desktop client is generated from the model.** The
  [RDC layer](../../../tentackle-fx-rdc/src/main/docs/rdc.md) bundles everything the UI needs per
  entity behind one `GuiProvider`: default editors, finders, context menus, and
  [tables](../../../tentackle-fx/src/main/docs/tables.md) whose columns are binding paths — with
  layouts that roam with the user via database-backed preferences, plus printing and Excel export.
- **Validation reaches the widget.** A server-side `ValidationResult` maps back to the exact
  control that caused it ([InteractiveError](../../../tentackle-fx/src/main/docs/interactive-error.md)) —
  authoritative checks on the server, precise feedback on the screen.

The deployment story is equally unusual. The
[jlink/jpackage plugin](../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md) builds
self-contained images (and native installers with menu entries and icons) — and it differs from
other jlink plugins in ways that matter:

- **It packages *any* Java application — modular, non-modular, or mixed.** It debunks the myth that
  jlink requires full modularization: it analyzes all dependencies (reading `module-info`, falling
  back to `jdeps`) and *derives* the packaging category itself — real modules go into the trimmed
  runtime image, automatic modules land next to it on a module path, classpath jars on a classpath.
  Nothing about your dependency graph has to change first.
- **The generated start script is the other half of the image.** Instead of jlink's built-in
  launcher (which cannot pass a module path or classpath at all), the plugin materializes its
  dependency analysis into a platform-specific launch script generated from editable Freemarker
  templates — keeping platform details out of the POMs, which stay minimal because the Maven model
  already provides most of the configuration.
- **Self-update is built in.** A per-user image can replace itself in place via the
  [update service](../../../tentackle-update/src/main/docs/update.md): the server alone dictates the
  target version, the download URL, and the update script, so bulk downloads can be steered to a
  CDN or local mirror instead of through the application server — the successor to the retired
  Java Web Start.

## Batteries a mainstream stack does not include

- **Authorization rules are data, not code.** The
  [security subsystem](../../../tentackle-pdo/src/main/docs/security.md) stores ACLs as ordinary
  PDOs, editable at runtime through a UI, and enforces them inside `select`/`persist`/`delete` —
  a row you may not read is never handed to you.
- **Translations live in the database.** [tentackle-i18n](../../../tentackle-i18n/src/main/docs/i18n.md)
  transparently intercepts bundle loading; translators edit texts live with
  [BundleMonkey](../../../tentackle-i18n/src/main/docs/bundlemonkey.md) — effective immediately in
  every running JVM, including locales the developers never shipped.
- **Preferences roam.** A `java.util.prefs`-style API backed by
  [the database](../../../tentackle-core/src/main/docs/preferences.md), so window geometry and
  table layouts follow the user to any workstation.
- **Business numbers are pooled, transactionally.** [Number sources](../../../tentackle-pdo/src/main/docs/ns.md)
  hand out invoice/order numbers from persistent pools — and take them back.
- **Secrets are never casually readable.** A single application-specific
  [Cryptor](../../../tentackle-common/src/main/docs/cryptor.md) encrypts passwords in filtered
  resources at build time, in properties files, in memory, and on the wire.

## The trade, stated honestly

None of this is free: more exceptions by design, explicit saves, a cohesive but smaller ecosystem,
and machinery you don't need if you control your topology and run a conventional web stack. Where
Tentackle earns its keep is spelled out in
[Why Tentackle Fits Technical and Scientific Applications](./technical-scientific.md) — and where a
mainstream framework is the better fit is
[stated there too](./technical-scientific.md#when-a-business-framework-would-be-the-better-fit).

---

## Related Documentation

- [Correctness First](./correctness-first.md) — the design philosophy behind the always-on guarantees.
- [PDOs vs. Traditional ORMs](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md) — the lifecycle comparison in depth.
- [Multi-Tier Cascade](./tiers.md) — nested servers, stacked caches, and CQRS.
- [TRIP](../../../tentackle-core/src/main/docs/trip.md) — the wire protocol.
- [Service and Configuration API](../../../tentackle-common/src/main/docs/services.md) — build-time service resolution.
- [AI Agent Tooling Support](./ai-agents.md) — the MCP server, the headless batch modes, and the bundled agent skills.
- [Location Transparency vs. REST, RMI and Spring Remoting](./remoting-comparison.md) — the same deviations argued against the alternatives, with the honest limits named.
