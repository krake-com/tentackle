# Tentackle Project Archetype — Scaffolding a Complete Application

## Overview and Motivation

`tentackle-project-archetype` is a **Maven archetype** that generates a ready-to-run, multi-module Tentackle
application from a single command. Rather than hand-assembling the eight modules, the dozens of plugin executions,
and the JPMS wiring that a Tentackle app needs, a developer runs `mvn archetype:generate` once and gets a complete,
buildable, testable project — including a working example domain (users, user groups, messages), a JavaFX desktop
client, a middle-tier server, a console daemon, and jlink/jpackage packaging.

It is a *build-time-only* artifact: it is never a dependency of the running application. It is packaged with
`<packaging>maven-archetype</packaging>` and consists almost entirely of **template resources** under
[`src/main/resources/archetype-resources`](../resources/archetype-resources) plus the archetype descriptor
[`archetype-metadata.xml`](../resources/META-INF/maven/archetype-metadata.xml).

### What the generated project demonstrates

The archetype is both a scaffold and a **reference application**. The generated code shows, end to end, how the
framework's pieces fit together:

- the [PDO pattern](../../../../tentackle-pdo/src/main/docs/pdo.md) — entities defined as interfaces with an
  embedded model, split into domain and persistence layers;
- [Wurbelizer code generation](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md)
  driving the persistence mappings, DDL and remote delegates;
- a [JavaFX RDC](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) desktop UI with login, preferences, theming,
  an about box, and CRUD editors;
- TRIP-based [client/server remoting](../../../../tentackle-core/src/main/docs/trip.md), security, preferences, and
  the [auto-update](../../../../tentackle-update/src/main/docs/update.md) service;
- full [JPMS](../../../../tentackle-common/src/main/docs/common.md) module declarations and the service-hook
  pattern;
- [i18n](../../../../tentackle-i18n/src/main/docs/i18n.md) resource bundles in English and German.

---

## How a Project Is Generated

The archetype declares one required property, `application`, in
[`archetype-metadata.xml`](../resources/META-INF/maven/archetype-metadata.xml); the usual `groupId`,
`artifactId`, `version` and `package` come from Maven's archetype machinery:

```bash
mvn archetype:generate \
    -DarchetypeGroupId=org.tentackle \
    -DarchetypeArtifactId=tentackle-project-archetype \
    -DarchetypeVersion=25.0 \
    -DgroupId=com.example \
    -DartifactId=myapp \
    -Dversion=1.0-SNAPSHOT \
    -Dpackage=com.example.myapp \
    -Dapplication=MyApp
```

`application` is the human-facing CamelCase name woven into class names (`MyAppServer`,
`MyAppFxClient`, …), window titles, and bundle text. `artifactId` (the *rootArtifactId*) becomes the module prefix
(`myapp-common`, `myapp-pdo`, …) and the default database name/user.

The same property set is used by the module's own integration test:
[`src/test/resources/projects/myapp`](../../test/resources/projects/myapp) holds an
[`archetype.properties`](../../test/resources/projects/myapp/archetype.properties) and a
[`goal.txt`](../../test/resources/projects/myapp/goal.txt) (`verify -Pjlink`), so the build verifies that a freshly
generated project compiles, tests, and even produces jlink images.

### Template mechanics

Archetype resources are processed by **Velocity**, so the template files differ from ordinary source in three ways:

- **Path substitution** — directories and files named `__rootArtifactId__-…`, `__application__…` and
  `__packageInPathFormat__` are renamed using the generation properties. Hence `__application__FxClient.java`
  becomes `MyAppFxClient.java`.
- **Content substitution** — `${package}`, `${application}`, `${rootArtifactId}` etc. are expanded inside the
  files. Because Velocity also interprets `#` and `$`, every template begins with the three `#set` directives
  defining `$symbol_pound`, `$symbol_dollar` and `$symbol_escape`, so that a literal `$` (Maven property
  references in the generated POMs) or `#` survives expansion.
- **Filtering selection** — [`archetype-metadata.xml`](../resources/META-INF/maven/archetype-metadata.xml) lists,
  per module, which file sets are `filtered` (text/source/properties) and which are copied verbatim (`.png`,
  `.gif`, `.xcf`, the jlink `.ftl` templates). `packaged="true"` file sets are relocated under the generated
  package directory.

The archetype's own [`pom.xml`](../../../pom.xml) additionally pre-filters the generated root
[`pom.xml`](../resources/archetype-resources/pom.xml) at *archetype build time* (with `\` as the escape string),
so that the Tentackle, Wurbelizer, JavaFX, and library versions of the current release are baked in as
`${project.version}`, `${wurbelizer.version}`, `${testng.version}` and friends.

---

## Anatomy of the Generated Application

The generated root [`pom.xml`](../resources/archetype-resources/pom.xml) is a `pom`-packaging aggregator that
imports [`tentackle-bom`](../../../../tentackle-bom/src/main/docs/bom.md), centralizes all plugin configuration in `<pluginManagement>`,
and lists eight reactor modules plus a `jlink` sub-aggregator:

```
myapp (root pom)
├── myapp-common        constants, version, session, domain context, cryptor, locale, preferences
├── myapp-pdo           PDO interfaces (entities + domain/persistence facets) and the embedded model
├── myapp-domain        domain-logic implementations (open module)
├── myapp-persistence   persistence implementations + TRIP remote delegates (open module)
├── myapp-gui           JavaFX RDC controllers, editors, FXML/CSS, images (open module)
├── myapp-client        desktop FX client application
├── myapp-server        middle-tier TRIP server + update service
├── myapp-daemon        headless console client
└── jlink               jlink/jpackage images (profile "jlink" only)
    ├── client
    ├── server
    └── daemon
```

### Module layering and JPMS

Each module has an explicit `module-info.java`. The dependency edges mirror Tentackle's architecture and enforce
the strict separation between the persistence- and domain layers:

| Module                   | `requires transitive`                                           | Role                                                                                                                                                                                                                                        |
|--------------------------|-----------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `…-common`               | `org.tentackle.pdo`                                             | base types shared by everything ([`Constants`](../resources/archetype-resources/__rootArtifactId__-common/src/main/java/common/Constants.java), `Version`, `…SessionInfo`, `…DomainContext`, `…Preferences`, `…Cryptor`, `…LocaleProvider`) |
| `…-pdo`                  | `…-common`                                                      | the PDO *interfaces* — entity, domain facet, persistence facet — that define the model                                                                                                                                                      |
| `…-domain` *(open)*      | `…-pdo`, `org.tentackle.domain`                                 | domain-logic implementations (`UserDomainImpl`, `MessageDomainImpl`, …)                                                                                                                                                                     |
| `…-persistence` *(open)* | `…-pdo`, `org.tentackle.persistence`                            | persistence implementations and TRIP remote delegates                                                                                                                                                                                       |
| `…-gui` *(open)*         | `…-pdo`, `org.tentackle.fx.rdc`                                 | FX controllers, editors, providers, FXML/CSS                                                                                                                                                                                                |
| `…-client`               | `…-gui`, `…-persist`, `…-domain`, `org.tentackle.fx.rdc.update` | the desktop application                                                                                                                                                                                                                     |
| `…-server`               | `…-persist`, `…-domain`, `org.tentackle.update`                 | the middle-tier server                                                                                                                                                                                                                      |
| `…-daemon`               | `…-persist`, `…-domain`                                         | a headless console client                                                                                                                                                                                                                   |

The domain, persistence, and gui modules are **open** because the framework reflects into them (TRIP
serialization, FX/FXML, the proxy-based PDO assembly). Every module `provides org.tentackle.common.ModuleHook`
with its own `service.Hook`, the SPI entry point that lets the framework discover and initialize the module in
both modular and non-modular runs.

### The example model — entities and the PDO split

The PDO module carries the **model**, embedded as special comment blocks in the entity interfaces. For example
[`User`](../resources/archetype-resources/__rootArtifactId__-pdo/src/main/java/pdo/md/User.java) is an interface
that extends its persistence facet (`UserPersistence`), its domain facet (`UserDomain`) and a shared base
(`OrgUnit`), and declares its table, class id, and attributes in the `@{ … @}` / `@> … @<` model block that the
wurblets read:

```java
@TableName(value =/*@*/"md.user"/*@*/, …)
@ClassId(/*@*/1001/*@*/)
@Singular("User")
@Plural("Users")
public interface User extends OrgUnit<User>, UserPersistence, UserDomain {
  // @wurblet modelComment ModelComment
  // @wurblet uniqueDomainKey UniqueDomainKey
}
```

The example divides entities into two schemas that the wizard profiles set up: **master data** (`pdo.md` — class
ids ≥ 1000: `OrgUnit`, `User`, `UserGroup`, `User2Group`) and **transaction data** (`pdo.td` — class ids ≥ 2000:
`Message`). For each entity the project ships the interface (`pdo/md`), the generated domain
(`pdo/md/domain`) and persistence (`pdo/md/persist`) facet interfaces, the domain implementation
(`domain/md`), the persistence implementation (`persist/md`) and the TRIP remote delegate
(`persist/md/trip`) — a complete worked example of the [PDO pattern](../../../../tentackle-pdo/src/main/docs/pdo.md).

### Applications

Each runnable module subclasses a Tentackle application base class:

- [`…FxClient`](../resources/archetype-resources/__rootArtifactId__-client/src/main/java/client/__application__FxClient.java)
  extends `UpdatableDesktopApplication` — the JavaFX desktop client with login, an updatable main controller,
  preferences, and the auto-update hook.
- [`…Server`](../resources/archetype-resources/__rootArtifactId__-server/src/main/java/server/__application__Server.java)
  extends `ServerApplication` — the TRIP middle tier that also starts the update service.
- [`…Daemon`](../resources/archetype-resources/__rootArtifactId__-daemon/src/main/java/daemon/__application__Daemon.java)
  extends `ConsoleApplication` — a headless example that listens for new `Message` PDOs and logs them.

All three implement the same `getUser(...)` contract via `Pdo.create(User.class, context).selectCached(userId)`,
illustrating that PDO methods work identically whether the JVM is directly connected to the database (server) or
remote (client/daemon).

---

## The Generated Build Pipeline

The most instructive part of the archetype is the `<pluginManagement>` block in the generated root
[`pom.xml`](../resources/archetype-resources/pom.xml): it is a complete, correctly ordered Tentackle build. The
plugins it configures, in build order:

1. [**tentackle-maven-plugin**](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) —
   `analyze` / `test-analyze` (and `properties` in the jlink modules) generate service descriptors and analyze
   metadata before compilation.
2. [**wurbelizer-maven-plugin**](https://www.wurbelizer.org/) — `wurbel` / `test-wurbel` run the
   [persistence wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) over
   the PDO interfaces, facet interfaces, implementations, and remote delegates (selected by the configured
   `<filesets>`), weaving generated code into the guarded regions and emitting the model under
   `target/wurbel/model`.
3. [**tentackle-sql-maven-plugin**](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md)
   — turns the model into DDL for the `td` and `md` schemas, with migration-hint support.
4. [**tentackle-check-maven-plugin**](../../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md)
   — `bundles` / `validations` verify i18n bundle completeness (`en_US, de_DE`) and Groovy validation scripts.
5. [**tentackle-i18n-maven-plugin**](../../../../tentackle-i18n-maven-plugin/src/main/docs/tentackle-i18n-maven-plugin.md)
   — i18n resource handling for the German locale.
6. **maven-compiler-plugin** — compiles with the framework annotation processors
   (`tentackle-core`, `tentackle-pdo`, `tentackle-fx`, `tentackle-fx-rdc`) on the processor path.
7. [**tentackle-jlink-maven-plugin**](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md) — builds the native images under
   the `jlink` profile.

The build also wires shared model defaults (`tentackle.modelDefaults`), per-column sizes, the
`generated-resources` service directories and a US-locale surefire configuration — all the settings a real
Tentackle project needs, pre-filled.

### The wizard plugin

The [**tentackle-wizard-maven-plugin**](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md)
is configured (but not bound to a phase) so the developer can *grow* the generated project: its `PdoProfile`s
(`masterdata`, `transactiondata`) and `OperationProfile` (`operation`) capture the package conventions and class-id
ranges, and the [FreeMarker templates](../resources/archetype-resources/templates) under `templates/pdo` and
`templates/operation` are the scaffolds it uses to generate new PDOs and operations consistently with the example.

### The MCP plugin

The [**tentackle-mcp-maven-plugin**](../../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md)
is configured (but not bound to a phase either) with the project's `tentackle.modelDefaults` and the same backends
the wurblets generate for, so `mvn tentackle-mcp:serve` in the project root serves the generated model — as the
build sees it — to a coding agent over MCP. It is read-only and needs the model, so run a build first.

---

## How It Fits Into Tentackle

```
            tentackle-project-archetype  (maven-archetype, build-time only)
                          │ mvn archetype:generate -Dapplication=MyApp
                          ▼
        ┌──────────────────────────────────────────────────────────────┐
        │  generated multi-module project (common → pdo → domain /      │
        │  persistence → gui → client / server / daemon, + jlink)        │
        └──────────────────────────────────────────────────────────────┘
                          │ mvn install  (drives the plugin pipeline)
                          ▼
   analyze → wurbel → sql → check/i18n → compile → (jlink)  →  runnable app
        (each step documented in the linked plugin docs above)
```

The archetype packages, in one place, the conventions every other Tentackle module assumes: the module layering,
the PDO/model authoring style, the wurblet filesets, the i18n layout, and the full plugin choreography. It is the
fastest way to see all of [`tentackle-pdo`](../../../../tentackle-pdo/src/main/docs/pdo.md),
[`tentackle-persistence`](../../../../tentackle-persistence/src/main/docs/persistence.md),
[`tentackle-fx-rdc`](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) and the
[Maven plugins](../../../../tentackle-maven-support/src/main/docs/tentackle-maven-support.md) working together.

---

## Related Documentation

- [PDO — Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) ·
  [Operations](../../../../tentackle-pdo/src/main/docs/operation.md)
- [Persistence layer](../../../../tentackle-persistence/src/main/docs/persistence.md) ·
  [Persistence Wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md)
- [Domain layer](../../../../tentackle-domain/src/main/docs/domain.md) ·
  [Model definition](../../../../tentackle-model/src/main/docs/model-definition.md)
- [FX RDC UI](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) ·
  [Auto-update](../../../../tentackle-update/src/main/docs/update.md)
- [Maven Support](../../../../tentackle-maven-support/src/main/docs/tentackle-maven-support.md) and the plugins:
  [maven](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) ·
  [sql](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) ·
  [check](../../../../tentackle-check-maven-plugin/src/main/docs/tentackle-check-maven-plugin.md) ·
  [i18n](../../../../tentackle-i18n-maven-plugin/src/main/docs/tentackle-i18n-maven-plugin.md) ·
  [wizard](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) ·
  [mcp](../../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md)
- [Common / Services & Configuration](../../../../tentackle-common/src/main/docs/common.md)
