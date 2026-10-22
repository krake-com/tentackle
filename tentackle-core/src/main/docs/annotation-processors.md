# Annotation Processors — Compile-Time Validation

## Overview and Motivation

Tentackle wires much of itself together reflectively. A domain implementation is
instantiated by the framework through a `(PersistentDomainObject)` constructor; a
`ValueTranslator` through `(FxComponent)`; a `GuiProvider` through
`(PersistentDomainObject)`. None of these contracts can be expressed in Java's type
system — an annotation cannot demand a constructor signature.

Left unchecked, a missing constructor is a *runtime* failure, raised deep inside a
factory, at the moment some user first opens some view, in a stack trace that names
the framework rather than your class. The `apt` packages exist to move that failure
to where it belongs: **a compile error, on the offending class, with a message that
says exactly which constructor is missing** — and, because annotation processors run
inside `javac`, underlined in the IDE as you type.

```text
MyThingDomainImpl.java:42: error: class MyThingDomainImpl needs constructor (PersistentDomainObject)
```

This is the [correctness-first](../../../../src/main/docs/correctness-first.md)
principle applied to the build: if a contract can be checked early, check it early.

### What they do *not* do

The processors **generate nothing**. Not one of them touches a `Filer`. This
distinguishes them from the framework's other two build-time mechanisms, which are
easy to confuse with them:

| Mechanism | Runs in | Produces |
|-----------|---------|----------|
| **Annotation processors** (`apt` packages) | `javac` | Nothing. Errors and warnings only. |
| **`@Analyze` handlers** ([tentackle-maven-plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md)) | The `analyze` build phase | The `META-INF` service files that wire the application together. See [services.md](../../../../tentackle-common/src/main/docs/services.md). |
| **Wurblets** (`wurbelizer-maven-plugin`) | The `generate-sources` phase | Java source — interfaces, implementations, queries. |

A caveat on naming, since it is the source of most confusion here: the analyze phase is
*itself* implemented as a `javax` annotation processor —
[`AnalyzeProcessor`](../../../../tentackle-build-support/src/main/java/org/tentackle/buildsupport/AnalyzeProcessor.java),
declared `@SupportedAnnotationTypes("*")`. But it is instantiated directly by the
Maven plugin and run in its own pass, rather than being discovered by `javac`, and it
is not part of the `apt` packages. So "the `@Service` annotation processor generates
`META-INF/services/...`" is true of *that* processor, and never of the ones described
here.

The two build-time mechanisms are nevertheless **interlocked**, in a way that is worth
understanding: the `@Analyze` pipeline is what registers and configures the annotation
processors. That loop is described [below](#how-a-processor-gets-registered).

---

## Where They Live

Every module that defines annotations carrying a contract also defines the processor
that enforces it:

| Package | Module | Processors |
|---------|--------|-----------|
| [`org.tentackle.apt`](../java/org/tentackle/apt/) | tentackle-core | The shared base, plus the four interception processors. |
| [`org.tentackle.apt.visitor`](../java/org/tentackle/apt/visitor/) | tentackle-core | Reusable type visitors. |
| [`org.tentackle.apt.pdo`](../../../../tentackle-pdo/src/main/java/org/tentackle/apt/pdo/) | tentackle-pdo | Domain/persistent object and operation services. |
| [`org.tentackle.session.apt`](../../../../tentackle-session/src/main/java/org/tentackle/session/apt/) | tentackle-session | Master serial event services. |
| [`org.tentackle.fx.apt`](../../../../tentackle-fx/src/main/java/org/tentackle/fx/apt/) | tentackle-fx | Controllers, value translators, table cell types. |
| [`org.tentackle.fx.rdc.apt`](../../../../tentackle-fx-rdc/src/main/java/org/tentackle/fx/rdc/apt/) | tentackle-fx-rdc | GUI providers, context menu items. |

---

## How a Processor Gets Registered

A processor is marked with a single annotation:

```java
@SupportedAnnotationTypes("org.tentackle.pdo.DomainObjectService")
@AnnotationProcessor
public class DomainObjectServiceAnnotationProcessor extends AbstractDomainServiceAnnotationProcessor {
```

[`@AnnotationProcessor`](../../../../tentackle-common/src/main/java/org/tentackle/common/AnnotationProcessor.java)
is itself meta-annotated:

```java
@Service(Processor.class)
public @interface AnnotationProcessor { }
```

So the processor is simply a **service** of type `javax.annotation.processing.Processor`.
The `@Analyze` pipeline writes it into `META-INF/services/javax.annotation.processing.Processor`,
and `javac` — which discovers processors through exactly that file — picks it up
automatically in every downstream module. There is no processor path to configure and
no plugin declaration to maintain: putting `@AnnotationProcessor` on the class is the
whole registration.

This is the interlock. The service mechanism registers the processors; the processors
then validate the classes that the service mechanism will wire.

---

## The Service Processors

Most of the processors validate a `@...Service` annotation, and nearly all extend
[`AbstractServiceAnnotationProcessor`](../java/org/tentackle/apt/AbstractServiceAnnotationProcessor.java).
The base class contributes the mechanics and one universal rule — **a service
implementation may not be abstract** — and offers three checks to its subclasses:

| Method | Checks |
|--------|--------|
| `verifyImplements(element, className)` | The class is assignable to the named type (erased, so generics don't interfere). |
| `verifyConstructor(element, visitor)` | Some **public** constructor matches the visitor. |
| `acceptTypeVisitor(mirror, class)` | A type matches, resolving type variables to their upper bound — so a declaration like `T extends PersistentDomainObject<T>` still matches `PersistentDomainObject`. |

The subclass then overrides `processClass()` and states its contract declaratively:

```java
@Override
protected void processClass(Element element) {
  super.processClass(element);
  if (!verifyConstructor(element, noArgsVisitor)) {
    processingEnv.getMessager().printMessage(
        Diagnostic.Kind.ERROR, "class " + element + " needs a no-args constructor", element);
  }
  if (!verifyConstructor(element, pdoVisitor)) {
    processingEnv.getMessager().printMessage(
        Diagnostic.Kind.ERROR, "class " + element + " needs constructor (PersistentDomainObject)", element);
  }
  verifyImplements(element, "org.tentackle.pdo.DomainObject");
  verifyStatelessDomainLogic(element);
}
```

Note that each check reports independently rather than returning early: a class
missing two constructors is told about both in one compile.

### The visitors

The `apt.visitor` package holds the reusable predicates:

- [`NoArgsVisitor`](../java/org/tentackle/apt/visitor/NoArgsVisitor.java) — matches a
  constructor with no parameters.
- [`ClassArgConstructorVisitor`](../java/org/tentackle/apt/visitor/ClassArgConstructorVisitor.java)
  — matches a single-parameter constructor of a given type.
- [`SuperTypeVisitor`](../java/org/tentackle/apt/visitor/SuperTypeVisitor.java) —
  matches a type *or any of its supertypes and interfaces*, walking the hierarchy.

Contracts needing more than one parameter declare a small inline `SimpleTypeVisitor8`
in the processor itself — as `PersistentObjectServiceAnnotationProcessor` does for
`(PersistentDomainObject, DomainContext)` and `(PersistentDomainObject, Session)`.

### What each processor enforces

| Annotation | Must implement | Must provide constructors |
|------------|----------------|---------------------------|
| `@DomainObjectService` | `DomainObject` | `()`, `(PersistentDomainObject)` |
| `@PersistentObjectService` | `PersistentObject` | `()`, `(PersistentDomainObject)`, `(PersistentDomainObject, DomainContext)`, `(PersistentDomainObject, Session)` |
| `@DomainOperationService` | `DomainOperation` | `()`, `(Operation)` |
| `@PersistentOperationService` | `PersistentOperation` | `()`, `(Operation)`, `(Operation, DomainContext)`, `(Operation, Session)` |
| `@MasterSerialEventService` | `java.util.function.Consumer` | `()` |
| `@FxControllerService` | `FxController` | `()` *or* `(ResourceBundle)` — see below |
| `@ValueTranslatorService` | `ValueTranslator` | `(FxComponent)` *or* `(FxComponent, Class)` |
| `@TableCellTypeService` | `TableCellType` | `()` |
| `@GuiProviderService` | `GuiProvider` | `(PersistentDomainObject)` |
| `@PdoTableContextMenuItemService` | `PdoTableContextMenuItem` | `(PdoTableCell)` |
| `@PdoTreeContextMenuItemService` | `PdoTreeContextMenuItem` | `(PdoTreeCell)` |

`FxControllerServiceAnnotationProcessor` is the only one whose requirement depends on
the annotation's own values: a controller that declares no FXML `url` *and* does
declare `resources` is instantiated programmatically with its bundle, and so must
offer `(ResourceBundle)`; otherwise it is loaded by FXML and needs `()`.

---

## The Interception Processors

[Interception](interceptors.md) is validated by four processors with a different
shape, because they check **annotations rather than implementations**.

[`InterceptionAnnotationProcessor`](../java/org/tentackle/apt/InterceptionAnnotationProcessor.java)
validates the `@Interception` meta-annotation itself: that it is applied to an
annotation type, and that **at most one** of `implementedBy`, `implementedByName` or
`implementedByService` is given — three ways to name the interceptor, of which
exactly one may win.

The other three enforce *where* an interception annotation may be applied. They share
[`AbstractInterceptorAnnotationProcessor`](../java/org/tentackle/apt/AbstractInterceptorAnnotationProcessor.java)
and differ only by the `Interception.Type` passed to the constructor:

| Processor | Type | An annotated method must be declared in |
|-----------|------|------------------------------------------|
| `PublicInterceptorAnnotationProcessor` | `PUBLIC` | an `Interceptable` **interface** |
| `HiddenInterceptorAnnotationProcessor` | `HIDDEN` | a **class** implementing `Interceptable` |
| `AllInterceptorAnnotationProcessor` | `ALL` | either |

The check walks the enclosing type's interfaces and superclasses looking for
`Interceptable`, so inherited implementations are accepted. Applying a `HIDDEN` annotation
to an interface method is rejected as well — the mistake being easy to make and its
consequence (an interceptor that silently never fires) being hard to diagnose.

All three also reject an interception annotation on a method that overrides a method marked
[`@NotInterceptable`](interceptors.md#notinterceptable-sealing-a-method-against-interception), searching the
whole supertype hierarchy. Such methods are invoked by the framework directly, bypassing
the interceptor chain, so the annotation would never take effect.

### The dynamically discovered annotation set

These three processors carry no `@SupportedAnnotationTypes`. They cannot: the set of
interception annotations is open-ended, and applications define their own. Instead
they build it at startup:

```java
@Override
public Set<String> getSupportedAnnotationTypes() {
  return annotationTypes;      // loaded in the constructor
}
```

`loadAnnotationTypes()` reads every `META-INF/services/<the processor's own class name>`
on the classpath — for example `META-INF/services/org.tentackle.apt.PublicInterceptorAnnotationProcessor` —
and treats each line as an annotation type to police. (It reads the files directly
rather than via `ServiceFinder`, which is not available to the compiler.)

Those files are written by
[`InterceptionAnalyzeHandler`](../../../../tentackle-build-support/src/main/java/org/tentackle/buildsupport/InterceptionAnalyzeHandler.java),
because `@Interception` is declared `@Analyze("org.tentackle.buildsupport.InterceptionAnalyzeHandler")`.
The handler reads the annotation's `type` and appends the annotation's name to the file
named after the corresponding processor — defaulting to `ALL` when no type is given.

So the full loop, for `@Transaction` in tentackle-pdo:

```text
@Interception(type = PUBLIC)  on  @Transaction
        │
        │  analyze phase — InterceptionAnalyzeHandler
        ▼
META-INF/services/org.tentackle.apt.PublicInterceptorAnnotationProcessor
        │     └─ contains: org.tentackle.pdo.Transaction
        │
        │  javac in a downstream module
        ▼
PublicInterceptorAnnotationProcessor.loadAnnotationTypes()
        │
        ▼
every @Transaction is checked to sit on an Interceptable interface method
```

Declaring a new interception annotation therefore needs no change to any processor —
`@Interception` on it is enough, and the next build teaches the processor about it.

---

## The Stateless Domain Logic Warning

One processor check is a warning rather than an error, and it encodes a design rule
rather than a mechanical contract.
[`AbstractDomainServiceAnnotationProcessor.verifyStatelessDomainLogic()`](../../../../tentackle-pdo/src/main/java/org/tentackle/apt/pdo/AbstractDomainServiceAnnotationProcessor.java)
warns about every **non-static field** in a domain implementation:

```text
warning: PDO domain logic should be stateless! Consider making 'cachedTotal'
effectively immutable and annotate it with @SuppressWarnings("stateful-domain-logic").
```

Domain implementations back PDOs that are shared, cached and serialized between tiers;
mutable instance state in one is a bug waiting for a concurrent reader. Where the field
really is safe — effectively immutable, computed once — the warning is suppressed with
the constant
[`Constants.SUPPRESS_WARNINGS_STATEFUL_DOMAIN_LOGIC`](../../../../tentackle-common/src/main/java/org/tentackle/common/Constants.java):

```java
@SuppressWarnings("stateful-domain-logic")
private final Money cachedTotal;
```

The annotation works on the individual field or on the whole class. It applies only to
`@DomainObjectService` and `@DomainOperationService`; persistent implementations hold
the entity's attributes and are expected to have fields.

---

## The Chicken-and-Egg Rule

A module cannot run annotation processors that it is itself compiling — the classes do
not exist yet when `javac` starts. Every module defining processors therefore compiles
with them switched off:

```xml
<compilerArgs>
  <!-- proc:none because of chicken-egg problem with annotation processors in same module -->
  <arg>-proc:none</arg>
</compilerArgs>
```

This applies to **tentackle-core, tentackle-pdo, tentackle-session, tentackle-fx and
tentackle-fx-rdc** — exactly the five modules in the table above. Everything
downstream, including every application module, gets the processors automatically.

The same problem affects the generated service files, so the few that a
processor-defining module needs for *itself* are **hand-maintained** under
`src/main/resources` rather than generated. They carry a comment saying so:

```text
# tentackle-pdo is compiled with proc:none due to chicken-egg
org.tentackle.pdo.Transaction
org.tentackle.pdo.NoTransaction
org.tentackle.security.Secured
org.tentackle.pdo.WithinContext
org.tentackle.pdo.NotWithinContext
```

This is worth knowing before adding an interception annotation to one of those five
modules: the analyze phase will not pick it up, and the corresponding
`src/main/resources/META-INF/services/org.tentackle.apt.*InterceptorAnnotationProcessor`
file must be edited by hand. In any other module, the annotation alone suffices.

---

## Adding Your Own

To validate an application-specific service annotation:

1. Extend `AbstractServiceAnnotationProcessor`.
2. Annotate with `@SupportedAnnotationTypes("...")` and `@AnnotationProcessor`.
3. Override `processClass()`, call `super`, and state the contract with
   `verifyConstructor(...)` / `verifyImplements(...)`.
4. Report through `processingEnv.getMessager()` — `Kind.ERROR` for a broken contract,
   `Kind.WARNING` for a suspicious but legal one.

The processor must live in a module *other* than the one whose classes it checks.

---

## Summary

| Concern | How the processors address it |
|---------|-------------------------------|
| **Reflective contracts** | Constructor and interface requirements that Java cannot express are checked by `javac` instead of failing at first use. |
| **Diagnosis** | The error names the class and the exact missing constructor, in the IDE, on the right line. |
| **Registration** | `@AnnotationProcessor` is `@Service(Processor.class)`; the analyze phase writes the `Processor` service file and `javac` finds it. |
| **Extensibility** | Interception processors load their annotation set from `META-INF`, so new annotations need no processor change. |
| **Design rules** | Stateful domain logic warns rather than errors, and is suppressible where genuinely safe. |
| **Bootstrapping** | The five processor-defining modules compile `-proc:none` and hand-maintain their own service files. |

They produce no output — which is exactly the point. A successful build means every
annotated class already satisfies the contract the framework will rely on at runtime.

### See also

- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) — the `@Analyze` pipeline that registers the processors.
- [Interceptors](interceptors.md) — what the interception annotations do at runtime.
- [Tentackle Build Support](../../../../tentackle-build-support/src/main/docs/build-support.md) — the analyze handlers.
- [Tentackle Maven Plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) — the plugin driving the analyze phase.
- [PDO](../../../../tentackle-pdo/src/main/docs/pdo.md) — the domain/persistence implementations being checked.
- [Correctness First](../../../../src/main/docs/correctness-first.md) — the principle.
