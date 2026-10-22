# Tentackle Architecture

This is a design overview for people who want to understand how Tentackle is put together —
before reading the code, or before changing it. It names the central ideas, shows how they
depend on each other, and points to the document that covers each one in depth. It is a map,
not the reference: the full documentation starts at the
[Table of Contents](src/main/docs/toc.md).

## The design in one paragraph

Tentackle builds multi-tier Java applications around a single domain model. An entity is a
**Persistent Domain Object (PDO)**: one interface, backed by a persistence implementation and
a domain implementation that do not know each other and are joined at runtime behind a
dynamic proxy. The persistence side is remoting-capable, so a PDO travels between JVMs as a
real object and every one of its methods works in every JVM, on any tier. Which tier a JVM is
on is decided by a connection URL. The implementations are found through a service registry
that is written at build time, and the persistence code itself is generated from a model that
lives in the PDO interface.

Everything below elaborates on one of these sentences.

## Design goals

- **One domain model, written once, running unchanged on every tier.** No DTO layer, no
  endpoints, no duplicated business rules between client and server.
- **Correctness by default.** Whatever goes wrong fails loudly and early instead of
  corrupting data quietly — see [Correctness First](src/main/docs/correctness-first.md).
- **Topology is configuration, not code.** Two tiers, three tiers, or a cascade of nested
  servers: the application code is the same.
- **Decisions are taken at build time where possible.** Service discovery, code generation
  and many checks happen in the build, not at startup or in production.
- **Real modules.** Everything an application ships with is a JPMS module, and applications
  deploy as self-contained jlink/jpackage images.

The price is deliberate: Tentackle assumes Java on both ends and one team owning the model.
[When to use Tentackle — and when not to](README.md#when-to-use-tentackle--and-when-not-to)
spells out where that trade is a poor one.

## Layers and modules

The reactor has 42 modules of three kinds: **runtime** modules an application ships with,
**build-time** modules (Maven plugins and code generators), and **common** modules used by
both. The runtime modules stack up as follows; as a rule, a layer builds on those below it.

```
 UI           tentackle-fx   tentackle-fx-rdc   tentackle-fx-atlanta   tentackle-web
              ─────────────────────────────────────────────────────────────────────
 Application  tentackle-pdo  ◀── implemented by ──  tentackle-persistence
              (interfaces)                          tentackle-domain
              ─────────────────────────────────────────────────────────────────────
 Database     tentackle-session  ◀── implemented by ──  tentackle-database
              (interfaces)                              (+ tentackle-jta, optional)
              ─────────────────────────────────────────────────────────────────────
 Core         tentackle-core   tentackle-i18n   transports (quic, quiche)
              logging and scripting providers
              ─────────────────────────────────────────────────────────────────────
 Common       tentackle-common   tentackle-sql              (also used by the build)
```

Two structural rules shape this picture:

- **Interfaces and implementations live in different modules.** `tentackle-session` declares
  the session API and `tentackle-database` implements it; `tentackle-pdo` declares the PDO API
  and `tentackle-persistence` and `tentackle-domain` implement its two halves. There is no
  compile-time edge from an API module to its implementation — the connection is made only
  through the service registry.
- **Everything optional is a provider.** Logging backends, scripting languages, QUIC
  transports, the OS keyring, themes and the spreadsheet export are separate modules that
  plug in by being on the path.

See [Tentackle Modules](src/main/docs/modules.md) for every module and its role.

## The service registry

Nearly every type an application touches is an interface, so something has to connect an
interface to its implementation at runtime. That is the service API in `tentackle-common`:
annotations mark implementations, the build records them under `META-INF`, and a
`ServiceFinder` looks them up.

- `@Service` binds one implementation to an interface — a logger provider, a factory, a theme.
- `@MappedService` relates a key to a value. This is how a PDO interface finds its persistence
  and domain implementations, and how it gets its `@ClassId`.
- `@Analyze` is the mechanism underneath: the `tentackle-maven-plugin` scans for types that
  carry it, directly or indirectly, and writes the registry.

Because the registry is written by the build, there is no classpath scanning at startup, and
the same application runs on the module path, the class path, or a mix of both. Replacing a
framework component means annotating your own class; there is no configuration file to edit.

See [Service and Configuration API](tentackle-common/src/main/docs/services.md).

## Persistent Domain Objects

A PDO is described by three interfaces and backed by two classes:

```
                         ┌──────────────────────────┐
   application code ───▶ │  Invoice (dynamic proxy) │
                         └────────────┬─────────────┘
                      PdoInvocationHandler routes by interface
                ┌─────────────────────┴──────────────────────┐
                ▼                                            ▼
   ┌──────────────────────────┐              ┌──────────────────────────┐
   │  InvoicePersistenceImpl  │              │    InvoiceDomainImpl     │
   │  mostly generated        │   no mutual  │    written by hand       │
   │  attributes, relations,  │   dependency │    business rules        │
   │  finders, persist()      │              │    approve()             │
   └──────────────────────────┘              └──────────────────────────┘
```

`Invoice` extends `InvoicePersistence` and `InvoiceDomain`. The proxy sends each call to the
delegate whose interface declares the method, so the object behaves as if it inherited from
both base classes — multiple inheritance, emulated. The persistence layer stays agnostic of
the domain layer and vice versa.

Three further ideas belong to the PDO model:

- **Aggregates are first-class.** The persistence layer knows root entities from their
  components, and a component knows its root without a query. A component cannot be persisted
  on its own, and persisting the root validates the whole aggregate.
- **Operations** carry behavior that is not tied to one entity. They follow the same
  split-and-proxy pattern.
- **Interceptors** attach cross-cutting rules to interface methods by annotation —
  `@Transaction`, security checks, context assertions — and are woven into the same proxy.

See [PDO](tentackle-pdo/src/main/docs/pdo.md),
[Operation](tentackle-pdo/src/main/docs/operation.md),
[Interceptors](tentackle-core/src/main/docs/interceptors.md) and
[PDOs vs. Traditional ORMs](tentackle-pdo/src/main/docs/pdo-vs-orm.md).

## The model and code generation

The model of an entity — table, attributes, indexes, relations, validations — lives in a
structured comment block in the source of its PDO interface. It is the single source for
everything that can be derived:

```
                    model block in the PDO interface
                                  │
        ┌─────────────────┬───────┴────────┬──────────────────┐
        ▼                 ▼                ▼                  ▼
   wurblets          persistence      SQL plugin         MCP plugin
   persistence       wurblets         DDL and            the resolved model,
   interfaces,       persistence      migrations         served to coding
   DTOs              implementation,                     agents
                     queries
```

Generation is done by wurblets, templates run at build time by the third-party
[Wurbelizer](https://wurbelizer.org/). The generated code is not kept in a separate tree: it
is woven into the hand-written sources inside guarded regions, so generated and hand-written
code share one class. `tentackle-model` is the API all of these tools read the model through.

See [Model Definition Syntax](tentackle-model/src/main/docs/model-definition.md),
[Wurblets](tentackle-wurblets/src/main/docs/wurblets.md) and
[Persistence Wurblets](tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md).

## Sessions and tiers

Every JVM is a node that opens a `Session` to a backend. The URL of that session decides what
the node is:

- `jdbc:` or `jndi:` — a **local** session. The node executes SQL itself.
- `trip:` and its variants — a **remote** session. Every call is forwarded to the node named
  in the URL, where a mirror session does the next hop.

A middle-tier server is itself a client: it opens its own session upstream and accepts
sessions from downstream. If its upstream is a database, it owns the connections; if it is
another Tentackle server, it is a proxy. Servers can therefore be nested to any depth.

```
   leaf clients (JavaFX, web)
          │  trip://
          ▼
     edge server        server and client at once, forwards upstream
          │  trips://
          ▼
     core server        owns the JDBC connections and the locks
          │  jdbc:
          ▼
      database
```

A PDO's persistence delegate is either the local implementation or a remote delegate that
forwards to the next tier. Domain code cannot tell the difference, which is why a relation can
be navigated and a `@Transaction` method called from any tier — the transaction opens on the
tier that holds the connection. There is no detached state and no
`LazyInitializationException`.

Change notification follows the same path. Every tracked name — typically a table — owns a
serial counter in the database and every write bumps it; the modification tracker of each JVM compares serials, so
a client three tiers away sees the same changes as one connected directly, without a message
broker. This keeps PDO caches coherent across the cascade.

See [Multi-Tier Cascade](src/main/docs/tiers.md),
[Session](tentackle-session/src/main/docs/session.md),
[Database](tentackle-database/src/main/docs/database.md) and
[Modification Tracking](tentackle-session/src/main/docs/modification-tracking.md).

## TRIP

TRIP, the Tentackle Remote Invocation Protocol, is the binary serialization and RPC framework
in `tentackle-core` that carries remote sessions. It replaces Java serialization and RMI.

- **Serialization** is reflection-driven and tuned by annotations. A type dictionary sends
  the shape of each class only once, and repeated objects are deduplicated, so object graphs
  with cycles travel compactly.
- **RPC** works through dynamic proxies to `Remote` interfaces, routed to `RemoteDelegate`s on
  the server. Remote objects are not exported by hand.
- **Transports** are pluggable below the serialization layer: TCP, TLS, compression,
  pre-shared-key encryption, and QUIC in two interoperable implementations (`tentackle-quic`
  on pure-Java KWIK, `tentackle-quiche` on Cloudflare quiche via the FFM API).
- **No addresses on the wire.** TRIP never sends host names or ports back to the client, so it
  works through NAT and SD-WAN.

TRIP is a framework-level protocol for applications that own both ends. It is not meant as a
public API; [tentackle-web](tentackle-web/src/main/docs/web.md) is the bridge for that.

See [TRIP](tentackle-core/src/main/docs/trip.md) and
[Location Transparency vs. REST, RMI and Spring Remoting](src/main/docs/remoting-comparison.md).

## Correctness guarantees

These are properties of the architecture, not optional features:

| Guarantee                         | Mechanism                                                                    | Document                                                     |
|-----------------------------------|------------------------------------------------------------------------------|--------------------------------------------------------------|
| No lost update                    | Optimistic locking via the `serial` column, always on; optional token locks  | [locking.md](tentackle-persistence/src/main/docs/locking.md) |
| No invalid object persisted       | Declarative validation that travels with the object and runs on every tier   | [validation.md](tentackle-core/src/main/docs/validation.md)  |
| No mutation of shared objects     | Immutability, cascading along aggregate boundaries; cached PDOs are frozen   | [immutable.md](tentackle-core/src/main/docs/immutable.md)    |
| Atomic undo                       | Snapshots that revert a whole aggregate                                      | [snapshot.md](tentackle-pdo/src/main/docs/snapshot.md)       |
| Aggregates change only as a whole | Components are persisted through their root only                             | [pdo.md](tentackle-pdo/src/main/docs/pdo.md)                 |
| Rules that cannot be forgotten    | Interceptors on interface methods, verified at compile time                  | [interceptors.md](tentackle-core/src/main/docs/interceptors.md) |

Errors cross tier boundaries as well: an exception carries a `temporary` flag that drives
transparent retry of transactions. See
[Exceptions and Error Handling](src/main/docs/exceptions.md).

## The user interface

The UI layer is one consumer of the model among others, not part of it. `tentackle-fx` adds
binding, validation feedback and configuration-driven tables to JavaFX; `tentackle-fx-rdc`
builds a rich desktop client on top of PDOs and operations, with editors, finders and tables
per entity. FXML, CSS and resource bundles sit next to their controller classes in
`src/main/java`. A web application is a leaf of the cascade toward HTTP, built on
`tentackle-web`.

See [tentackle-fx](tentackle-fx/src/main/docs/fx.md),
[Rich Desktop Client](tentackle-fx-rdc/src/main/docs/rdc.md) and
[Tentackle Web](tentackle-web/src/main/docs/web.md).

## What the build does

Much of the architecture is carried by the build, so it helps to know what an application's
build contributes:

- **Generate.** The Wurbelizer runs the wurblets and weaves persistence interfaces,
  implementations and queries into the sources.
- **Analyze.** `tentackle-maven-plugin` processes `@Analyze`-annotated types and writes the
  service registry under `META-INF`.
- **Check.** `tentackle-check-maven-plugin` validates resource bundles and validation
  annotations.
- **Derive the database.** `tentackle-sql-maven-plugin` generates DDL and migration scripts
  from the model.
- **Package.** `tentackle-jlink-maven-plugin` builds self-contained jlink/jpackage images
  and works out the right packaging for each artifact by itself.

The wizard and MCP plugins sit beside this pipeline: one scaffolds PDOs and operations, the
other serves the resolved model to coding agents. See
[AI Agent Tooling Support](src/main/docs/ai-agents.md).

## Finding your way around the repository

- Each module documents itself in `<module>/src/main/docs/`; project-wide documents are in
  `src/main/docs/`.
- `tentackle-project-archetype` scaffolds a complete multi-tier application. Generating one
  with the [Quickstart](src/main/docs/quickstart.md) and reading the
  [MyApp Walkthrough](tentackle-project-archetype/src/main/docs/myapp-walkthrough.md) is the
  fastest way to see all of the above working together.
- `tentackle-osgi/` is a separate build that wraps the runtime modules into OSGi bundles.
- [What a Seasoned Java Developer Would Not Expect](src/main/docs/unexpected.md) lists the
  deviations from mainstream Java frameworks, each with a pointer to its document.
- [CONTRIBUTING.md](CONTRIBUTING.md) explains how to build, test and submit changes.
