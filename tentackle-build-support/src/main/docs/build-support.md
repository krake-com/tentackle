# Tentackle Build Support — The Annotation Analyze Phase

## Overview and Motivation

`tentackle-build-support` is a small, **build-time-only** module that provides the infrastructure for
Tentackle's *analyze phase*: a Java annotation-processing pass that runs over an application's sources
**before** they are wurbeled and compiled. During this pass it inspects the Tentackle annotations in the code
and turns them into two kinds of artifacts:

- **service descriptors** under `META-INF` (so the [`ServiceFinder`](../../../../tentackle-common/src/main/docs/services.md)
  can discover implementations in both modular (JPMS) and classpath runtimes), and
- **analyze info files** under a `target` subdirectory that later build steps — most importantly the
  [wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md) — read to
  generate code (for example, the remote-delegate code for `@RemoteMethod`).

The module is never shipped inside, nor used by, the running application; its `package-info` states plainly
*"Build support only. Not used at runtime."* It is packaged with an `Automatic-Module-Name` of
`org.tentackle.buildsupport` and depends only on [`tentackle-common`](../../../../tentackle-common/src/main/docs/common.md)
and [FreeMarker](https://freemarker.apache.org/).

### Why a build-time analyze phase?

There are two common ways to wire up services and metadata: scan the classpath and read annotations
reflectively at **runtime**, or extract the same information at **build time** via annotation processing.
Tentackle takes the second route (see [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md)).
The payoff is that the annotated classes never have to be loaded just to read their annotations — the work is
done once, during the build — which keeps startup fast and works identically on the classpath and the module
path. This module is the engine that performs that extraction.

### Driven by the Maven plugin

`tentackle-build-support` only supplies the processor and handlers; it does not bind itself to any build
lifecycle. The [`tentackle-maven-plugin`](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md)
registers the [`AnalyzeProcessor`](../java/org/tentackle/buildsupport/AnalyzeProcessor.java) and runs it from its
`analyze` (and `test-analyze`) goals during `generate-sources`, supplying the two output directories
(`analyzeDir` and `servicesDir`) and the source/classpath to scan.

---

## The Processing Pipeline

```
@Analyze-annotated annotation in the sources
            │
            ▼
   AnalyzeProcessor  (javac AbstractProcessor, @SupportedAnnotationTypes("*"))
            │  recursively follows meta-annotations to find the @Analyze marker
            ▼
   AnalyzeHandler    (instantiated by classname from @Analyze("..."))
            │  writes via ResourceManager to one of two locations
            ├──────────────► servicesDir :  META-INF/services/…  (+ optional RESOURCE-INDEX.LIST)
            └──────────────► analyzeDir  :  <class>/service.log, *.remote, record.info, …
                                              (later read by wurblets / packaged into META-INF)
```

### `AbstractTentackleProcessor` — the processor base

[`AbstractTentackleProcessor`](../java/org/tentackle/buildsupport/AbstractTentackleProcessor.java) extends
`javax.annotation.processing.AbstractProcessor` and adds the cross-cutting concerns shared by Tentackle's
processors: a per-directory `ResourceManager` cache, verbosity handling (the `verbosity` processor option,
`info`/`debug`), and a `cleanup()` that closes all open resources. It always reports `SourceVersion.latest()`.
It also carries an optional source directory, which neither the processors of this module nor the Tentackle
Maven plugins use.

### `AnalyzeProcessor` and the `@Analyze` meta-annotation

[`AnalyzeProcessor`](../java/org/tentackle/buildsupport/AnalyzeProcessor.java) supports *all* annotations
(`@SupportedAnnotationTypes("*")`) and claims those it found a handler for.
For each annotation type it encounters it walks the annotation's own
annotations — **recursively** — looking for the
[`@Analyze`](../../../../tentackle-common/src/main/docs/services.md) meta-annotation
(`org.tentackle.common.Analyze`). `@Analyze` carries the **class name of a handler as a `String`** (not a
`Class`), precisely so that the application never gains a compile- or run-time dependency on the build-support
handlers:

```java
@Analyze("org.tentackle.buildsupport.ServiceAnalyzeHandler")
public @interface Service { ... }
```

Because the scan is recursive, an annotation only has to be marked `@Analyze` once; any annotation
itself meta-annotated with it (directly or through several levels) is routed to the same handler. Loops are
guarded by a limit on the nesting level of meta-annotations (100) and on the number of handlers (1000).
For every match the processor loads the named handler class, instantiates it, injects itself, and calls
`processAnnotation(...)`. A new handler instance is created for each annotation type and round.

The handler classes — and the annotation classes a handler needs to load, such as `@RemoteMethod` — are loaded via
the processor's *processing classloader*. The Maven plugin sets it to a classloader that also searches the
project's classpath; without one, the processor falls back to its own classloader.

### `AnalyzeHandler` / `AbstractAnalyzeHandler`

[`AnalyzeHandler`](../java/org/tentackle/buildsupport/AnalyzeHandler.java) is the contract a handler implements:
`processAnnotation(annotationType, roundEnv)` plus the processor accessors.
[`AbstractAnalyzeHandler`](../java/org/tentackle/buildsupport/AbstractAnalyzeHandler.java) adds two conveniences:
`getDirectory(baseDir, className)` (maps a dotted class name to a directory tree under the analyze dir) and a
`print(kind, msg, element)` that emits compiler diagnostics tagged with the handler name. A handler can do
whatever is appropriate — its purpose is not restricted to services.

### `ResourceManager` — writing the output

[`ResourceManager`](../java/org/tentackle/buildsupport/ResourceManager.java) owns a base directory and hands out
lazily created, cached `PrintWriter`s/`Reader`s for slash-separated relative resource names (creating
intermediate directories as needed, using the encoding configured by `Settings`). It remembers the names of all
resources it handed out, even after `close()`. Each processor keeps one `ResourceManager` per output
directory, so handlers simply ask for `getResourceManager(serviceDir)` or `getResourceManager(analyzeDir)` and
append to the right file. The Maven layer later collects the service-directory resource names to build the
optional `RESOURCE-INDEX.LIST`, and writes an `error.log` marker (`AnalyzeProcessor.COMPILE_ERROR_LOG`) when the
analyze compilation reported errors, signaling downstream phases that the analyze info may be incomplete.

---

## The Handlers

Each handler reacts to one family of annotations and emits the appropriate descriptor and/or analyze file.

| Handler                                                                                                        | Reacts to                                                                                            | Produces                                                                                                                                                                                                                                                 |
|----------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [`ServiceAnalyzeHandler`](../java/org/tentackle/buildsupport/ServiceAnalyzeHandler.java)                       | `@Service` / `@ServiceName` (direct or as meta-annotations; honors `value`, `meta`, `method`)        | the servicing class appended to `META-INF/services/<serviced class>` (or `META-INF/meta-services/…` if `meta = true`)                                                                                                                                    |
| [`MappedServiceAnalyzeHandler`](../java/org/tentackle/buildsupport/MappedServiceAnalyzeHandler.java)           | `@MappedService` (as a meta-annotation)                                                              | a line `<annotated class> = <key>` in `META-INF/mapped-services/<service>`, the key being taken from the annotated annotation's element named by `method`                                                                                                |
| [`TableNameAnalyzeHandler`](../java/org/tentackle/buildsupport/TableNameAnalyzeHandler.java)                   | `@TableName`                                                                                         | a line `<class> = "<table>"` in `META-INF/mapped-services/org.tentackle.session.TableName`; needs its own handler to apply `mapSchema` and `prefix` (see [Table Names](../../../../tentackle-session/src/main/docs/table-names.md))                      |
| [`BundleAnalyzeHandler`](../java/org/tentackle/buildsupport/BundleAnalyzeHandler.java)                         | `@Bundle` and similar annotations (e.g. `@GuiProviderService`, `@TableConfigurationProviderService`) | a line `<bundle> [= <class>]` in `META-INF/bundles/<annotation>`; the bundle name defaults to the class name and is overridden by `value`, `noBundle = true` suppresses the line (for i18n)                                                              |
| [`FxControllerBundleAnalyzeHandler`](../java/org/tentackle/buildsupport/FxControllerBundleAnalyzeHandler.java) | `@FxControllerService`                                                                               | like `BundleAnalyzeHandler` (its subclass), but the bundle name comes from `resources`, and `RESOURCES_NONE` means no bundle                                                                                                                             |
| [`InterceptionAnalyzeHandler`](../java/org/tentackle/buildsupport/InterceptionAnalyzeHandler.java)             | annotations annotated with `org.tentackle.reflect.Interception`                                      | the interceptor annotation appended to `META-INF/services/org.tentackle.apt.<Type>InterceptorAnnotationProcessor` for the interception type (`ALL` / `PUBLIC` / `HIDDEN`), which registers it with the matching annotation processor of `tentackle-core` |
| [`RemoteMethodAnalyzeHandler`](../java/org/tentackle/buildsupport/RemoteMethodAnalyzeHandler.java)             | `@RemoteMethod`                                                                                      | an info file `<class>/<method>.remote` (`RemoteMethodInfo`) describing the method, later consumed by the `RemoteMethod` wurblet; the annotation's `value` overrides the file name                                                                        |
| [`RecordDTOAnalyzeHandler`](../java/org/tentackle/buildsupport/RecordDTOAnalyzeHandler.java)                   | `@RecordDTO`                                                                                         | an info file `<record>/record.info` (`RecordDTOInfo`) describing a record DTO, used for DTO generation                                                                                                                                                   |

The descriptors go to the services directory, the info files to the analyze directory. All handlers that write
descriptors except the bundle handlers also append a line to `<class>/service.log` in the analyze directory — a
trail of what was registered for which class, useful when debugging a missing service.

> **Dependency discipline.** Handlers reference Tentackle layers above `tentackle-common` only by string (e.g.,
> `InterceptionAnalyzeHandler` names `org.tentackle.reflect.Interception` and its APT classes as literals, and
> `TableNameAnalyzeHandler` never refers to `TableName`) so that this module stays free of dependencies on
> `tentackle-core` and the rest of the stack.

---

## Info Files — the bridge to wurblets

Some annotations carry too much structure to be captured in a one-line descriptor, so their handlers serialize a
small record into an info file that a wurblet deserializes in a later build step:

- [`RemoteMethodInfo`](../java/org/tentackle/buildsupport/RemoteMethodInfo.java) (with
  [`RemoteMethodInfoParameter`](../java/org/tentackle/buildsupport/RemoteMethodInfoParameter.java)) — gathers the
  signature of a `@RemoteMethod` (return type and its generic part, superclass, parameters, modifiers, varargs) into
  versioned files ending in `.remote`. It also classifies the return type and each parameter: whether it is a
  `Session`, a `DomainContext`, a PDO or low-level db object, a collection of those, or a cursor. The `RemoteMethod`
  wurblet reads these files to generate the [TRIP](../../../../tentackle-core/src/main/docs/trip.md)
  remote-delegate code.
- [`RecordDTOInfo`](../java/org/tentackle/buildsupport/RecordDTOInfo.java) (with
  [`RecordDTOInfoParameter`](../java/org/tentackle/buildsupport/RecordDTOInfoParameter.java)) — captures a record
  DTO's components into a `record.info` file used for DTO generation. See
  [The DTO Wurblet and `@RecordDTO`](../../../../tentackle-wurblets/src/main/docs/dto-wurblet.md#what-recorddto-actually-does)
  for how the wurblet consumes it.

Each info type defines its own `INFO_FILE_VERSION`, writes itself through a `PrintWriter` and reads itself back
from a `LineNumberReader`, so the write side (this module, during analyze) and the read side (the wurblets) agree
on a stable on-disk format.

The format is line- and whitespace-separated, but type strings are not whitespace-free — javac renders a wildcard
bound as `? extends Foo` and a type annotation as `@Bar Foo`. Types are therefore escaped on the way out and
unescaped on the way in (`AnnotationProcessingHelper.escapeToken()`: whitespace becomes `\s`, a backslash `\\`).
Since a java type never contains a backslash, unescaping is a no-op for info files written before the escaping was
introduced, and a stale `target/analyze` from an older build still reads correctly.

---

## Code Generation Helpers (`codegen`)

The [`org.tentackle.buildsupport.codegen`](../java/org/tentackle/buildsupport/codegen) sub-package is a thin
wrapper over [FreeMarker](https://freemarker.apache.org/) used by the build-time code generators of the
[SQL](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md),
[wizard](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) and
[jlink](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md) Maven plugins:

| Class                                                                                    | Role                                                                                                                          |
|------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------|
| [`AbstractGenerator`](../java/org/tentackle/buildsupport/codegen/AbstractGenerator.java) | Holds the template directory and builds a configured FreeMarker `Configuration` (UTF-8, rethrowing exception handler).        |
| [`TemplateModel`](../java/org/tentackle/buildsupport/codegen/TemplateModel.java)         | A `Map<String,Object>` template model that camel-cases keys when adding `Properties`/maps and null-safely stringifies values. |
| [`GeneratedFile`](../java/org/tentackle/buildsupport/codegen/GeneratedFile.java)         | Renders a named template with a model to an output **file**.                                                                  |
| [`GeneratedString`](../java/org/tentackle/buildsupport/codegen/GeneratedString.java)     | Renders a named template with a model to a **string**.                                                                        |

---

## Supporting pieces

- [`AnnotationProcessingHelper`](../java/org/tentackle/buildsupport/AnnotationProcessingHelper.java) — static
  utilities for processors: escaping info-file tokens, reading/checking `Modifier`s, joining object arrays with
  their plain `toString()` (unlike `StringHelper.objectArrayToString`, which formats for logging), and a recursive
  `isTypeInstanceOf(...)` that walks `TypeMirror` super-types (with optional generic-argument matching).
- [`ClassNames.properties`](../resources/org/tentackle/buildsupport/ClassNames.properties) — the fully-qualified
  class names `RemoteMethodInfo` classifies types against (`Collection`, `Session`, `AbstractDbObject`,
  `ScrollableResource`, `DomainContext`, `PersistentDomainObject`), referenced by string to avoid hard dependencies.
  Being a resource bundle, it can be overridden by derived frameworks to point at their own base classes. Since a
  stale name would silently never match, `AnalyzeClassNamesTest` in `tentackle-test-pdo` verifies that the
  classes still exist.

---

## Module Dependencies

- **[`tentackle-common`](../../../../tentackle-common/src/main/docs/common.md)** — for the `@Analyze`, `@Service`,
  `@ServiceName`, `@MappedService`, `@RemoteMethod` annotations, `Constants` (the `META-INF` paths), `Settings`
  (the encoding), `StringHelper` and the `ServiceFinder` contracts the descriptors feed.
- **[FreeMarker](https://freemarker.apache.org/)** — the template engine behind the `codegen` package.

This is a **build-time only** module: it runs on the annotation-processor / plugin classpath and is never part of
the deployed application.

## Related Documentation

- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) — the `@Analyze` /
  `@Service` mechanism and the `ServiceFinder` that consumes the generated descriptors.
- [Tentackle Maven Plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) — the
  `analyze` / `test-analyze` goals that drive the `AnalyzeProcessor` and configure its output directories.
- [Tentackle Persistence Wurblets](../../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md)
  and [Tentackle Wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md) — the code generators that
  read the analyze info files (e.g., the `RemoteMethod` wurblet).
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the remoting protocol whose delegates are generated
  from `@RemoteMethod` analyze info.
