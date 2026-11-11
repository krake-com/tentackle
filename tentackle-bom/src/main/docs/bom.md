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
`<dependencyManagement>`. Keep the Tentackle versions in two properties: `tentackle.version` for
the BOM and `tentackle.plugin.version` for the plugins (see [below](#versioning-the-plugins) and
[Hotfix Releases](#hotfix-releases)). After a full release, both have the same value:

```xml
<properties>
  <tentackle.version>25.20.1.0</tentackle.version>
  <tentackle.plugin.version>25.20.1.0</tentackle.plugin.version>
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
plugin), are resolved outside the project's dependency management. The same applies to Tentackle
artifacts listed in the compiler's `<annotationProcessorPaths>`. Give all of them the
`tentackle.plugin.version` property:

```xml
<plugin>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-maven-plugin</artifactId>
  <version>${tentackle.plugin.version}</version>
</plugin>
```

Projects generated from the [project archetype](../../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md)
already wire the BOM and the plugin versions in this way — see the [Quickstart](../../../../src/main/docs/quickstart.md).

## Hotfix Releases

Maven Central limits the number of files a project may upload, so a hotfix that touches only a few
modules does not republish the whole framework. A hotfix release (the `patch` digit of
`<java>.<major>.<minor>.<patch>`) republishes only the fixed modules plus a new `tentackle-bom`.
Every other module keeps the version of the release the hotfix is based on.

For example, a fix in `tentackle-fx` on top of `25.20.1.0` is published as:

| Artifact                   | Version     |
|----------------------------|-------------|
| `tentackle-bom`            | `25.20.1.1` |
| `tentackle-fx`             | `25.20.1.1` |
| all other modules, plugins | `25.20.1.0` |

The **BOM version is the release version**. It pins every module at the right version, and
because dependency management also applies to transitive dependencies, `tentackle-fx-rdc:25.20.1.0`
gets `tentackle-fx:25.20.1.1` in your project. To pick up a hotfix, raise only `tentackle.version`:

```xml
<properties>
  <tentackle.version>25.20.1.1</tentackle.version>
  <tentackle.plugin.version>25.20.1.0</tentackle.plugin.version>
</properties>
```

`tentackle.plugin.version` changes only if the hotfix republishes the plugins as well. The release
notes in the repository's `CHANGELOG.md` list the modules each hotfix republishes.

Because modules may lag behind the BOM, never declare a Tentackle *dependency* with an explicit
`${tentackle.version}`: leave the version to the BOM. Also note that `org.tentackle.common.Version.RELEASE`
reports the version of `tentackle-common`. That is the base release unless the hotfix republished
`tentackle-common` too.

## See also

- [Tentackle Modules](../../../../src/main/docs/modules.md) — what each managed module does.
- [Tentackle Project Archetype](../../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md)
- [Quickstart](../../../../src/main/docs/quickstart.md)
