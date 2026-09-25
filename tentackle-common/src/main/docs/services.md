# Service and Configuration API — Locating Implementations Without Scanning

## Overview and Motivation

Sooner or later every Tentackle newcomer writes this line:

```java
Invoice invoice = new Invoice();    // does not compile
```

`Invoice` is an interface. So is every PDO, every operation, and most of the framework's own
components. You ask a factory instead:

```java
Invoice invoice = on(Invoice.class);    // or Pdo.create(Invoice.class)
```

Nothing in your code names the implementing class — and that is the point. The persistence
implementation lives in one module, the domain implementation in another, and neither is on your
compile-time horizon. Something has to connect "this interface" to "that class" at runtime.

That something is the **service API**: a set of annotations that mark implementations, a build step
that records them, and a runtime finder that looks them up. If you know `java.util.ServiceLoader`,
you already know the shape of it. Tentackle's version differs in three ways that matter:

- **The registry is written by the compiler, not scanned at startup.** No jar walking, no bytecode
  reading, no classes loaded just to be inspected.
- **A "service" need not be a class.** An entry is a key/value record, so it can just as well map a
  PDO interface to the integer `2001`.
- **It works the same on the classpath and the module path**, without `provides` clauses for every
  service.

Registering an implementation is a single annotation. To replace the framework's session-info
factory with your own, you write the class and annotate it — there is no XML, no configuration
file, and nothing to edit in the framework:

```java
@Service(SessionInfoFactory.class)
public class TrackerSessionInfoFactory implements SessionInfoFactory {
  ...
}
```

Drop that class on the path and it wins. The rest of this document explains why it wins, what else
the same machinery carries, and how nearly every pluggable point in Tentackle is built on it.

> **Scope.** This document covers the annotations and the runtime API. The build-time pipeline that
> produces the `META-INF` entries — the annotation processor, the handlers, the info files — is
> documented in [build-support.md](../../../../tentackle-build-support/src/main/docs/build-support.md)
> and [tentackle-maven-plugin.md](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md).


## Why Not Classpath Scanning?

There are only two places to find an annotation: in the bytecode at runtime, or in the source at
build time.

| | **Classpath scanning** | **Build-time analysis** |
|---|---|---|
| When | Application startup | Compile time |
| How | Open every jar, read every candidate class | Annotation processing via the compiler API |
| Cost | Seconds on a large application | Paid once, by the build |
| Side effects | Loads annotated classes *and their annotation types* just to read them | None at runtime |
| Used by | Most classic frameworks | Newer microservice frameworks — and Tentackle |

Scanning is dynamic and needs no build step, and it pays for that at startup. It defeats lazy class
loading, and it sits badly with ahead-of-time deployment formats such as
[jlink images](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md) or GraalVM
native images, where the set of classes is meant to be known in advance.

Tentackle moves the work to the compiler. The
[tentackle-maven-plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md)
analyzes the sources for annotations that are themselves annotated with
[`@Analyze`](../java/org/tentackle/common/Analyze.java), and writes plain-text configuration files
into `target`, which the build then packages under `META-INF`.

**The decisive advantage is not the scan you avoid — it is the classes you never load.** Because the
*parameters* of an annotation are captured at build time and written out as data, the runtime never
has to load the annotated class, or even the annotation type, to read them. Take
[`@ClassId`](../../../../tentackle-session/src/main/java/org/tentackle/session/ClassId.java), which
assigns a unique integer to a PDO:

```java
@ClassId(2001)
public interface Message extends TransactionData<Message>, MessagePersistence, MessageDomain {
```

At runtime, resolving "class id 2001 ⇄ `com.example.tracker.pdo.Message`" is a lookup in a `Map<String,String>`
built from a text file. `Message` itself stays unloaded until someone actually needs it. That is what
makes a cold [TRIP](../../../../tentackle-core/src/main/docs/trip.md) message decode cheap, and it is
why startup time does not grow with the size of your model.

The trade is a build-time dependency and a compile step. What you get back is a runtime that is
fast, deterministic, and free of the analysis code entirely — the handlers live in
`tentackle-build-support`, which never ships.


## The Annotations

Three annotations cover almost everything. All three are markers for the same underlying mechanism;
they differ in what they write and what shape of question it answers.

| Annotation | Written to | Answers |
|---|---|---|
| [`@Service`](../java/org/tentackle/common/Service.java) | `META-INF/services/` | "Who implements this interface?" |
| [`@ServiceName`](../java/org/tentackle/common/ServiceName.java) | `META-INF/services/` | The same, but keyed by an arbitrary **string** |
| [`@MappedService`](../java/org/tentackle/common/MappedService.java) | `META-INF/mapped-services/` | "Which class serves *which other class or value*?" |

### `@Service` — one implementation for an interface

The workhorse. It names the **serviced** class (the interface); the annotated class is the
**servicing** one (the provider). Keeping those two words apart makes the rest of the API read
easily.

```java
@Service(My.class)
public class MyImpl implements My {
  ...
}
```

```java
My impl = ServiceFactory.createService(My.class);
```

That is the standard way to obtain a framework singleton. Close to a hundred distinct types in
Tentackle are obtained exactly like this — see [What Is Built On This](#what-is-built-on-this).

### `@ServiceName` — when the key is not a class

Identical to `@Service`, except the service is an arbitrary string. Useful when the serviced class
is not on the annotated module's compile path at all:

```java
@ServiceName("com.example.NotVisibleHere")
public class MyImpl { ... }
```

### `@Service` as a meta-annotation — your own vocabulary

Annotate an *annotation* with `@Service`, and you have minted a domain-specific registration
annotation. The framework does this constantly, and applications can too:

```java
@Service(ReferenceRegistryProvider.class)
public @interface ReferenceRegistryService {
}
```

```java
@ReferenceRegistryService
public class ReferenceRegistryProviderImpl implements ReferenceRegistryProvider {
  ...
}
```

`ReferenceRegistryProviderImpl` now lands in `META-INF/services/…ReferenceRegistryProvider`, exactly
as if it had carried `@Service(ReferenceRegistryProvider.class)` — but the intent is stated in your
own words, and the registration cannot be spelled wrong.

### `@MappedService` — relating three things

`@Service` relates two things: an interface and its implementation. A great deal of framework wiring
needs three:

- a **property** — an interface, a datatype, a concept;
- a **class** related to it;
- a **value** — another class, or a literal.

`@MappedService` is a meta-annotation only: you put it on *your* annotation, naming the property.
The classic case is the domain half of a PDO:

```java
@MappedService(DomainObject.class)
public @interface DomainObjectService {
  Class<? extends PersistentDomainObject> value();
}
```

```java
@DomainObjectService(Message.class)
public class MessageDomainImpl extends AbstractDomainObject<Message, MessageDomainImpl>
                               implements MessageDomain {
  ...
}
```

which produces, in `META-INF/mapped-services/org.tentackle.pdo.DomainObject`:

```
com.example.tracker.domain.MessageDomainImpl = com.example.tracker.pdo.Message
```

Read it as *provider = key*. The value need not be a class — `@ClassId` maps to an `int` and
[`@Singular`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/Singular.java) /
[`@Plural`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/Plural.java) map to display
names, so the same file format carries pure metadata:

```
com.example.tracker.pdo.Message = 2001
```

Reading it back inverts the line — the key of the map is the *value* side:

```java
Map<String, String> nameMap = ServiceFactory.getServiceFinder().createNameMap(ClassId.class.getName());
String pdoName = nameMap.get("2001");   // com.example.tracker.pdo.Message
```

Most of the time you will not call this directly. The
[`ClassMapper`](../../../../tentackle-core/src/main/docs/reflect.md#class-mapping) in `tentackle-core`
is the runtime engine that consumes mapped services, and it adds the thing that makes them powerful:
**lenient lookup**, which walks up the superclass chain and then the interfaces. That is why one
provider registered for a base class serves an entire hierarchy.

### `@Analyze` — the mechanism underneath

All of the above are ordinary annotations made special by one meta-annotation:

```java
@Analyze("org.tentackle.buildsupport.ServiceAnalyzeHandler")
public @interface Service {
  Class<?> value();
  String method() default "value";
  boolean meta() default false;
}
```

Two details are worth pausing on:

- **The handler is named as a string, not a class literal.** Deliberately. A class reference would
  drag `tentackle-build-support` onto your application's runtime path for no reason. The string is
  resolved by the compiler plugin, which does have it.
- **`method()`** tells the handler which annotation element carries the value, so your annotation can
  call it something meaningful instead of `value`.

`@Analyze` is not limited to services. Any handler can do anything — some write `META-INF` entries,
others produce info files that [wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md)
turn into source code, such as the `RemoteMethod` wurblet. The full handler roster is in
[build-support.md](../../../../tentackle-build-support/src/main/docs/build-support.md).

To use any of it, an application needs a dependency on **`tentackle-common`** and the
`tentackle-maven-plugin` in its POM. Nothing else. Higher-level conveniences such as
[`ClassMapperFactory`](../../../../tentackle-core/src/main/java/org/tentackle/reflect/ClassMapperFactory.java)
additionally need `tentackle-core`.


## The Runtime API

### ServiceFactory

[`ServiceFactory`](../java/org/tentackle/common/ServiceFactory.java) is the entry point: it creates
services and hands out finders.

```java
My impl  = ServiceFactory.createService(My.class);                    // first provider found
My impl2 = ServiceFactory.createService(My.class, MyDefault.class);   // …or a fallback
Class<My> cl = ServiceFactory.createServiceClass(My.class);           // just the class
```

It is itself loaded through the JDK's `ServiceLoader` and annotated `@Service(ServiceFactory.class)`
— defaulting to itself — so an application can replace the factory too. (`ServiceFactory` cannot
bootstrap itself with its own annotation: at the point `tentackle-common` is compiled, the analyze
plugin does not exist yet. Its registration is a checked-in resource under
`src/main/resources/META-INF/services`. A small chicken-and-egg detail, visible in the source as a
comment.)

`setFactoryClassname` / `setFactoryClassloader` provide an escape hatch for environments where
`ServiceLoader` cannot be used — call them very early, typically in `main`.

### ServiceFinder

[`ServiceFinder`](../java/org/tentackle/common/ServiceFinder.java) is the lookup interface, and there
is **one finder per `META-INF` subfolder** — one for `services/`, one for `mapped-services/`, one for
`bundles/`, and as many more as you care to define. Each namespace is separate, so unrelated
configurations cannot collide.

```java
ServiceFinder finder = ServiceFactory.getServiceFinder();                              // META-INF/services/
ServiceFinder mapped = ServiceFactory.getServiceFinder(Constants.MAPPED_SERVICE_PATH); // META-INF/mapped-services/
```

The API splits along two axes — *first vs. unique vs. all*, and *configuration vs. loaded class*:

| Method | Returns |
|---|---|
| `findFirstServiceProvider(Class)` | The first provider class along the path. **This is the override path** — an application's own provider shadows the framework's. |
| `findUniqueServiceProvider(Class)` | The provider, verifying it is declared **exactly once**. For factories and singletons where two would be a bug. |
| `findServiceProviders(Class)` | **All** provider classes, in path order. |
| `findFirstServiceConfiguration(String)` / `findServiceConfigurations(String)` | The raw name → URL entries, **without loading anything**. |
| `findServiceURLs(String)` | The resource URLs backing a service. |
| `createNameMap(String)` | `value → provider` for mapped services; first along the path wins. |
| `createNameMapToAll(String)` | `value → all providers`, for cases where every contribution counts. |

The `…Configuration` methods are the ones that make this more than a `ServiceLoader` clone: they let
the framework read registrations as *data*. That is how `@ClassId` resolves without touching a class,
and it is available to your code for the same trick.


## Ordering and Overriding

"The first provider wins" only means something if *first* is well defined. It is — and in modular
mode it is more interesting than a classpath order.

[`ModuleSorter`](../java/org/tentackle/common/ModuleSorter.java) reads the module graph at startup
(via the [ModuleHook](#the-modulehook)s), topologically sorts it depth-first, and assigns each module
an ordinal. When a finder collects URLs for a service name, they come back **highest module first**:
modules at the top of the dependency hierarchy — your application, which requires everything else —
precede the ones they depend on. Unnamed (classpath) and automatic modules come last.

The practical consequence is the one you want: **a module can always override a service declared by a
module it depends on**, simply by declaring its own. Your application overrides `tentackle-fx`, which
overrides `tentackle-core`. No priority numbers, no ordering configuration — the `requires` graph you
already wrote *is* the precedence order.

Two adjustments exist for the cases where it is not:

- [`@ModuleOrdinal`](../java/org/tentackle/common/ModuleOrdinal.java) — a module-level annotation
  pinning an explicit ordinal. The automatic ordinals are spaced by 100 precisely to leave room to
  insert between them.
- [`@ClasspathFirst`](../java/org/tentackle/common/ClasspathFirst.java) — inverts the rule for **one
  named service**, putting classpath and automatic modules ahead of the module path. This exists for
  a real problem: a non-modularized artifact could otherwise never override a service from a real
  module. `PoiTableUtilities` in
  [tentackle-fx-rdc-poi](../../../../tentackle-fx-rdc-poi/src/main/docs/poi.md) is the live example.
  It is itself a `meta = true` service — the one user of that flag in the framework — which is why it
  gets its own `META-INF/meta-services/` namespace: the provider it names lives in a different module
  from the annotation.

In classpath mode there is no module graph, and the order is discovery order along the classpath.


## The ModuleHook

Tentackle's registry is `META-INF` data rather than jigsaw `provides` clauses — for the two reasons
already given (a service is not always a class, and the JDK loader forces one shape of lookup). But
JPMS still has to be told something, because a module's resources and its position in the graph are
not discoverable from outside it. This matters most in a jlink'd application loaded from a jimage,
and again for [resource bundles](bundle.md), which resolve differently in modular mode.

So each module contributing services provides exactly one
[`ModuleHook`](../java/org/tentackle/common/ModuleHook.java):

```
provides org.tentackle.common.ModuleHook with com.example.tracker.common.service.Hook;
```

```java
public class Hook implements ModuleHook {

  @Override
  public ResourceBundle getBundle(String baseName, Locale locale) {
    return ResourceBundle.getBundle(baseName, locale);
  }

}
```

The implementation looks pointless, and it is anything but: `ResourceBundle` resolves bundles
relative to the **caller's** module, so this method must be implemented *in the hook itself* rather
than inherited from a base class. That is the whole trick — the hook is a piece of code the framework
can call *from inside your module*. `ModuleHook` extends `ResourceBundleProvider` for exactly this.

The hooks are also how `ModuleSorter` discovers the graph, which is why the startup log doubles as a
mode indicator:

```
INFO  o.tentackle.app.AbstractApplication.initialize - 14 module hooks found:
org.tentackle.common []
org.tentackle.core [org.tentackle.common]
org.tentackle.session [org.tentackle.core]
org.tentackle.pdo [org.tentackle.session]
org.tentackle.domain [org.tentackle.pdo]
com.example.tracker.common [org.tentackle.pdo]
com.example.tracker.pdo [com.example.tracker.common]
com.example.tracker.domain [org.tentackle.domain, com.example.tracker.pdo]
org.tentackle.update [org.tentackle.common]
org.tentackle.sql [org.tentackle.common]
org.tentackle.database [org.tentackle.sql, org.tentackle.session]
org.tentackle.persistence [org.tentackle.pdo, org.tentackle.database]
com.example.tracker.persist [org.tentackle.persistence, com.example.tracker.pdo]
com.example.tracker.server [com.example.tracker.domain, org.tentackle.update, com.example.tracker.persist]
```

Classpath mode:

```
INFO  o.tentackle.app.AbstractApplication.initialize - no module hooks found
```

That listing is the precedence order from the previous section, printed. If a service is not being
overridden the way you expect, read it bottom-up.


## ModuleLayers

`ServiceFinder`/`ServiceFactory`/`ModuleSorter` also work against an explicit `java.lang.ModuleLayer`
instead of a plain `ClassLoader`, which is what lets an application isolate two conflicting versions
of the same dependency in sibling module layers — `ServiceFactory.getServiceFinder(ModuleLayer,
String)`, `DefaultServiceFinder(ModuleLayer, String)` and `ModuleSorter.forLayer(ModuleLayer)` are the
new entry points, all purely additive. This is new, separate ground built on top of everything above;
see [layers.md](layers.md) for the full runtime story, the `tentackle-jlink-maven-plugin` integration,
and a worked example.


## What Is Built On This

The service API is easy to mistake for an implementation detail. It is closer to the opposite: it is
the mechanism that lets most of Tentackle's headline features exist at all. A few of the load-bearing
cases:

### The interface/implementation module split

[modules.md](../../../../src/main/docs/modules.md) describes a framework where the API modules do not
know their implementations: `tentackle-session` is implemented by `tentackle-database`,
`tentackle-pdo` by `tentackle-persistence` and `tentackle-domain`. That split is not a convention
maintained by discipline — it is enforced by the fact that the connection is only ever made through
`META-INF` data. There is no compile-time edge to accidentally introduce.

### PDOs and multiple inheritance

A [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) is a dynamic proxy over *two* delegates. When
`Pdo.create(Invoice.class)` runs, `PdoInvocationHandler` resolves both halves through mapped services
— [`@PersistentObjectService`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/PersistentObjectService.java)
and `@DomainObjectService` — via a `ClassMapper`, and binds them as
[mixins](../../../../tentackle-core/src/main/docs/reflect.md#proxies-and-mixins). Java's single
inheritance is worked around at the registry level. `@DomainOperationService` /
`@PersistentOperationService` do the same for
[operations](../../../../tentackle-pdo/src/main/docs/operation.md).

### Class IDs, and why remoting is cheap

`@ClassId` is the mapped service that gives every PDO a stable integer identity. It is on the wire in
[TRIP](../../../../tentackle-core/src/main/docs/trip.md), in the `classid` column in the database, and
in the [cache](../../../../tentackle-pdo/src/main/docs/cache.md) — all resolvable from `META-INF`
without loading a single entity class.

### Pluggable everything else

The pattern repeats across the framework. `@Service` for singletons and factories, `@MappedService`
where a type must be related to a type:

| Feature | Registration | What gets plugged in |
|---|---|---|
| [Logging](../../../../tentackle-core/src/main/docs/logging.md) | `@Service(Logger.class)` | `tentackle-log-slf4j` or `tentackle-log-log4j2v`, chosen by what is on the path (`LoggerFactory` is itself a replaceable `@Service`) |
| [Scripting](../../../../tentackle-core/src/main/docs/scripting.md) | `@Service(ScriptingLanguage.class)` | `tentackle-script-groovy` / `-ruby` / `-jsr` |
| [TRIP](../../../../tentackle-core/src/main/docs/trip.md) | `@TripTypeService`, `@TripComponentProviderService`, `@TripInstanceCreatorService` | Wire types and their (de)serializers |
| [TRIP transports](../../../../tentackle-core/src/main/docs/io.md) | `@TransportService(PROTOCOL)`, `@RemoteService` | Transports keyed by **protocol string** — `trip`, `tripe`, `tripce`, and [QUIC](../../../../tentackle-quic/src/main/docs/quic.md), each a drop-in |
| [Interceptors](../../../../tentackle-core/src/main/docs/interceptors.md) | `@InterceptionService` | Interceptable implementations, resolved like any mapped service |
| [Validation](../../../../tentackle-core/src/main/docs/validation.md) | `@ValidationScopeService`, `@ValidationSeverityService`, `@ValidationScriptConverter` | Scopes, severities and script converters |
| [Security](../../../../tentackle-pdo/src/main/docs/security.md) | `@PermissionService` | Permission types |
| [FX](../../../../tentackle-fx/src/main/docs/fx.md) / [RDC](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) | `@GuiProviderService`, `@TableCellTypeService`, `@TableConfigurationProviderService`, `@BuilderService`, `@ConfiguratorService`, `@Pdo*ContextMenuItemService` | GUI providers, cell types, table configs, context menus |
| [Secrets](cryptor.md) | `@Service(Cryptor.class)` | The application's own `Cryptor`, resolved identically at build time and runtime |
| [Bundles](bundle.md) / [i18n](internationalization.md) | `META-INF/bundles/` + `ModuleHook` | Bundle discovery in both modes |

Two entries deserve a note. The **`Cryptor`** case is the neatest demonstration of registrations
being *data*: the `tentackle-maven-plugin`'s `properties` goal writes `@org.tentackle.common.Cryptor`
as a converter, the `@` meaning "resolve through `META-INF/services`" — so the *build* encrypts with
the same application-specific cryptor the *runtime* will decrypt with, without either naming the
class. And the **logging** case is what most projects meet first without noticing: swapping the log
backend is a dependency change, nothing more.

### Your own extensions

None of this is privileged. An application defines an annotation, meta-annotates it with `@Service`
or `@MappedService`, and immediately has a build-time-populated registry of its own — with the same
ordering and override rules, and no runtime scanning. The framework uses the extension point it
publishes, which is the useful test of whether one is real.


## OSGi Bundles

The artifacts on Maven Central are not OSGi bundles, but you can build them: clone the
[tentackle repo](https://bitbucket.org/krake-oss/tentackle/src/master/), build it, then run a second
build in the `tentackle-osgi` subfolder. That produces the bundles with their activators and sources
(the latter are handy in the Eclipse IDE). See [OSGi Bundles](../../../../tentackle-osgi/src/main/docs/osgi.md)
for the build, the bundle list and how to prepare your own bundles.

OSGi is the reason `ServiceFactory` supports **per-service classloaders**. Each service (path + name)
keeps a stack of explicit loaders: setting one pushes, removing pops — so deactivating a bundle
re-activates whatever was registered before it. The activator drives that with a resource index
generated by the analyze goal:

```java
public class Activator implements BundleActivator {

  @Override
  public void start(BundleContext context) throws Exception {
    ServiceFactory.applyResourceIndex(getClass().getClassLoader(), "META-INF/RESOURCE-INDEX.LIST", true);
  }

  @Override
  public void stop(BundleContext context) throws Exception {
    ServiceFactory.applyResourceIndex(getClass().getClassLoader(), "META-INF/RESOURCE-INDEX.LIST", false);
  }

}
```

There is no public P2 repository yet, though that may change.


## Related Documentation

- [OSGi Bundles](../../../../tentackle-osgi/src/main/docs/osgi.md) — building the bundles, and the activators driving the per-service classloaders.
- [Tentackle Common](common.md) — the module these classes belong to.
- [Isolated Module Layers](layers.md) — the `ModuleLayer`-aware extension of this API.
- [Tentackle Build Support](../../../../tentackle-build-support/src/main/docs/build-support.md) — the analyze phase, the processor, and the full handler roster.
- [Tentackle Maven Plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) — the `analyze` goal that writes the `META-INF` configurations, and the `properties` goal's `@`-converters.
- [Tentackle Modules](../../../../src/main/docs/modules.md) — the interface/implementation split this API makes possible.
- [Reflection](../../../../tentackle-core/src/main/docs/reflect.md) — `ClassMapper`, the runtime engine resolving `@MappedService`, including lenient lookup.
- [PDO / Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — the proxies assembled from two mapped services.
- [Resource Bundles](bundle.md) — bundle handling, which (like services) differs between classpath and modular mode.
- [Internationalization](internationalization.md) — database-backed I18N built on the bundle and service infrastructure.
- [Cryptor](cryptor.md) — a service resolved identically at build time and runtime.
- [Wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md) — code generators consuming analysis results.
