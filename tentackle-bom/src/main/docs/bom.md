# Tentackle BOM — Bill of Materials

## Overview and Motivation

A Tentackle application typically depends on several Tentackle modules at once
(`tentackle-pdo`, `tentackle-persistence`, `tentackle-fx-rdc`, …). Pinning each module's version by
hand is error-prone and invites version skew. **`tentackle-bom`** solves this the standard Maven way:
it is a `pom`-packaged *Bill of Materials* whose `<dependencyManagement>` section declares the
matching version for every Tentackle runtime and test-support module.

Import it once and then depend on those modules **without a `<version>`** — the BOM supplies it,
guaranteeing every module comes from the same release.

## What the BOM Manages

| Kind                                     | Artifacts                                                                                                                                                                                                                                                                                                                      |
|------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Runtime modules                          | `tentackle-common`, `-core`, `-sql`, `-session`, `-database`, `-jta`, `-pdo`, `-domain`, `-persistence`, `-update`, `-i18n`, `-quic`, `-quiche`, `-keyring`, `-model`, `-log-slf4j`, `-log-log4j2v`, `-script-groovy`, `-script-ruby`, `-script-jsr`, `-fx`, `-fx-atlanta`, `-fx-rdc`, `-fx-rdc-poi`, `-fx-rdc-update`, `-web` |
| Test support (managed with `test` scope) | `tentackle-pdo-mock`, `tentackle-test-pdo`, `tentackle-test-fx-rdc`                                                                                                                                                                                                                                                            |

**Not** managed are the build-tooling artifacts: the Maven plugins (`tentackle-maven-plugin`,
`tentackle-sql-maven-plugin`, `tentackle-wizard-maven-plugin`, …), `tentackle-maven-support`,
`tentackle-build-support`, the wurblets and the project archetype. An imported BOM only fills
`<dependencyManagement>` — it never reaches `<pluginManagement>` — so plugin versions must be set
explicitly (see [below](#versioning-the-plugins)).

The BOM inherits from `tentackle-parent`, and Maven imports a BOM's *effective* dependency
management, including what it inherits. Importing `tentackle-bom` therefore also aligns the
third-party dependencies Tentackle is built and tested with — JavaFX, log4j, SLF4J, Groovy, JRuby,
POI and others, some of them with a preset scope (e.g. H2, TestNG and JUnit as `test`). Your own
`<dependencyManagement>` entries take precedence over imported ones, so declare a third-party
version yourself if you need a different one.

## Using the BOM

Import it with `<type>pom</type>` and `<scope>import</scope>` in your project's (or parent's)
`<dependencyManagement>`. Keep the Tentackle version in a property, so the BOM and the plugins
stay in sync:

```xml
<properties>
  <tentackle.version></tentackle.version>
</properties>

<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.tentackle</groupId>
      <artifactId>tentackle-bom</artifactId>
      <version>${tentackle.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Then declare the modules you need, version-free. The test-support modules need no `<scope>` either,
as the BOM already manages them as `test`:

```xml
<dependencies>
  <dependency>
    <groupId>org.tentackle</groupId>
    <artifactId>tentackle-fx-rdc</artifactId>
  </dependency>
  <dependency>
    <groupId>org.tentackle</groupId>
    <artifactId>tentackle-test-pdo</artifactId>
  </dependency>
</dependencies>
```

## Versioning the Plugins

The Tentackle plugins, and any Tentackle artifacts declared as dependencies *of* a plugin (such as
`tentackle-persistence-wurblets` for the wurbelizer, or `tentackle-script-groovy` for the check
plugin), are resolved outside the project's dependency management. Give them the same property:

```xml
<plugin>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-maven-plugin</artifactId>
  <version>${tentackle.version}</version>
</plugin>
```

Projects generated from the [project archetype](../../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md)
already wire the BOM and the plugin versions in this way — see the [Quickstart](../../../../src/main/docs/quickstart.md).

## See also

- [Tentackle Modules](../../../../src/main/docs/modules.md) — what each managed module does.
- [Tentackle Project Archetype](../../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md)
- [Quickstart](../../../../src/main/docs/quickstart.md)
