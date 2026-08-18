# Tentackle BOM — Bill of Materials

## Overview and Motivation

A Tentackle application typically depends on several Tentackle modules at once
(`tentackle-pdo`, `tentackle-persistence`, `tentackle-fx-rdc`, the maven plugins, …). Pinning each
module's version by hand is error-prone and invites version skew. **`tentackle-bom`** solves this
the standard Maven way: it is a `pom`-packaged *Bill of Materials* whose `<dependencyManagement>`
section declares the matching version for every published Tentackle artifact.

Import it once and then depend on Tentackle modules **without a `<version>`** — the BOM supplies it,
guaranteeing every module comes from the same release.

## Using the BOM

Import it with `scope=import` in your project's (or parent's) `dependencyManagement`:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.tentackle</groupId>
      <artifactId>tentackle-bom</artifactId>
      <version>25.19.0.0</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

Then declare the modules you need, version-free:

```xml
<dependencies>
  <dependency>
    <groupId>org.tentackle</groupId>
    <artifactId>tentackle-fx-rdc</artifactId>
  </dependency>
</dependencies>
```

Projects generated from the [project archetype](../../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md)
already wire the BOM in for you — see the [Quickstart](../../../../src/main/docs/quickstart.md).

## See also

- [Tentackle Modules](../../../../src/main/docs/modules.md) — what each managed module does.
- [Tentackle Project Archetype](../../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md)
- [Quickstart](../../../../src/main/docs/quickstart.md)
