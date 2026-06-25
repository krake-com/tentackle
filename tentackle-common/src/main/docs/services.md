## Tentackle Service and Configuration API

In Tentackle the implementations of interfaces and most other components
are created by factories. PDOs and operations, for example, are interfaces and
the implementing classes are not known by the application code. Instead, they are
injected at runtime by a factory. This also applies to most other components
of the framework, such as singletons. 

Assumed that `Invoice` is a PDO, you cannot write `new Invoice()` 
but must use a factory method, i.e. `on(Invoice.class)`.

The implementations are registered via special annotations. The application can
easily replace components by simply adding a properly annotated class.
Here is an example from the Tentackle tutorial:
```java
/**
 * Application specific session info factory.
 */
@Service(SessionInfoFactory.class)
public class TrackerSessionInfoFactory implements SessionInfoFactory {
    ...
}
```

This replaces the default factory of the framework by an application-specific
implementation.

### How does it work?

Basically, there are two ways to locate the implementations:

1. Parsing the bytecode for the annotations at runtime, usually at 
application startup. This is also known as classpath scanning and is used
by most of the popular frameworks.
1. Parsing the source code for the annotations at build time. This is based on
annotation processing and Java's compiler API. More and more of the newer microservice frameworks
are using this technique to minimize startup times.

There are pros and cons for either approach. Tentackle uses the second
one. The [tentackle-maven-plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md)
([plugin reference](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-maven-plugin/plugin-info.html))
analyzes the source code for annotations directly or indirectly annotated
with [@Analyze](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-common/apidocs/org.tentackle.common/org/tentackle/common/Analyze.html). This is a so-called meta annotation that causes the invocation
of an [AnalyzeHandler](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-build-support/apidocs/org/tentackle/buildsupport/AnalyzeHandler.html) which is defined by the specific annotation. This handler
can do whatever is appropriate and its purpose is not limited to services. 
However, in most cases it will create some configuration
files in a subdirectory of `target`. This information is
processed in subsequent maven build phases and the result stored in the generated
artifacts, usually somewhere in `META-INF`. Or it is picked up by wurblets
to generate source code, such as the [RemoteMethod](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-persistence-wurblets/apidocs/org/tentackle/persist/wurblet/RemoteMethod.html)
wurblet.

One of the biggest advantages of this approach as opposed to classpath scanning is that it's no more necessary
to load the classes with their annotations at runtime to examine their parameters. 
This already happened at build-time!
For example, the [@ClassId](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-session/apidocs/org.tentackle.session/org/tentackle/session/ClassId.html)
annotation assigns a unique integer to a PDO class and we can obtain this information at runtime without
loading the annotated classes.

### Features of the ServiceFinder

- can be used as a replacement for `java.util.ServiceLoader`
- works in traditional classpath and in new modulepath (JPMS) mode
- no restrictions on the kind of configurations, not only for loading classes
- each configuration type gets its own subfolder in `META-INF`
- easy to extend by application specific annotations

### Service API

A [ServiceFactory](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-common/apidocs/org.tentackle.common/org/tentackle/common/ServiceFactory.html)
creates ServiceFinders. Since the ServiceFactory itself is loaded by the `ServiceLoader`, it can be replaced as well.

For each subfolder in `META-INF` there is one
[ServiceFinder](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-common/apidocs/org.tentackle.common/org/tentackle/common/ServiceFinder.html). 
There can be as many finders as needed. 

#### Simple services

One of them is for `META-INF/services' and is associated with the annotation
[@Service](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-common/apidocs/org.tentackle.common/org/tentackle/common/Service.html):
```java
@Analyze("org.tentackle.buildsupport.ServiceAnalyzeHandler")
public @interface Service {
  ...
}
```

Notice that the classname for the analyze handler is given as a string, not a class reference.
This is necessary to avoid dependencies of the application to the handlers, which are
not needed at runtime.

`@Service` can be used directly or as a meta annotation. Example for direct use:
```java
@Service(My.class)
public class MyImpl implements My {
  ...
}

```
The implementation can be obtained by the application with:
```java
My impl = ServiceFactory.createService(My.class);
```

This creates an application specific annotation by annotating it with @Service:
```java
@Service(ReferenceRegistryProvider.class)
public @interface ReferenceRegistryService {
  ...
} 
```
And then:
```java
@ReferenceRegistryService
public class ReferenceRegistryProviderImpl implements ReferenceRegistryProvider {
  ...
}

```

Lists of implementating classes (ordered along the class- or modulepath) can be obtained as follows:
```java
for (Class<ReferenceRegistryProvider> clazz :
     ServiceFactory.getServiceFinder().findServiceProviders(ReferenceRegistryProvider.class)) {
 ...
}
```

#### Mapped services

Mapped services are designed to describe relations between 3 elements such as:

- a property, for example some datatype or an interface
- a class related to that property
- the value of the property, for example the concrete integer 6 or an implementation of the service

Mapped services are stored in `META-INF/mapped-services`.

As an example, consider the [DomainObjectService](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-pdo/apidocs/org.tentackle.pdo/org/tentackle/pdo/DomainObjectService.html).
This annotation associates the domain implementation to its entity. Example from the tutorial:
```java
/**
 * Domain implementation for Message.
 */
@DomainObjectService(Message.class)
public class MessageDomainImpl extends AbstractDomainObject<Message, MessageDomainImpl> implements MessageDomain {
  ...
}
```
Other examples:
```java
/**
 * Message.
 * <p>
 * Events logged as messages.
 */
@TableName(value =/**/"td.message"/**/, // @wurblet < Inject --string $tablename
           mapSchema =/**/false/**/,    // @wurblet < Inject $mapSchema
           prefix =/**/""/**/)          // @wurblet < Inject --string $tablePrefix
@ClassId(/**/2001/**/)                  // @wurblet < Inject $classid
@Singular("Message")
@Plural("Messages")
public interface Message extends TransactionData<Message>, MessagePersistence, MessageDomain {
  ...
}
```

The mapping of the class IDs to the PDO interfaces, for example, can be obtained as follows:
```
Map<String, String> nameMap = ServiceFactory.getServiceFinder().createNameMap(ClassId.class.getName());

```

Notice that in order to use Tentackle's service API, your application just needs a dependency
to `tentackle-common` and a configuration of the `tentackle-maven-plugin` in the maven poms.

If you want to use some higher-level utilities such as the [ClassMapperFactory](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-core/apidocs/org.tentackle.core/org/tentackle/reflect/ClassMapperFactory.html)
you also need `tentackle-core`.

### The ModuleHook

As already mentioned, Tentackle services work in classpath and modular mode. Besides the limitation
to classes, that's another motivation why Tentackle's mapping isn't based on jigsaw's
`uses` / `provides` declarations in `module-info` and on `java.util.ServiceLoader`, but on `META-INF`. 
However, for reasons how the JPMS works, especially when it comes to jlink'd applications loaded from a jimage file, we need a `hook` 
into the modules that provide service configurations to determine the module dependencies.
Another reason is the [resource bundle handling](./bundle.md), which works differently in modular than in classpath mode.

Therefore, each such module needs a [ModuleHook](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-common/apidocs/org.tentackle.common/org/tentackle/common/ModuleHook.html)
and a `provides` declaration in `module-info`:
```
provides org.tentackle.common.ModuleHook with com.example.tracker.common.service.Hook;
```
And the implementation of the hook:
```java
/**
 * Hook for the common module.
 */
public class Hook implements ModuleHook {

  @Override
  public ResourceBundle getBundle(String baseName, Locale locale) {
    return ResourceBundle.getBundle(baseName, locale);
  }

}
```
You can tell whether a Tentackle application runs in classpath or modular mode by looking into
the logs.

Modular mode:
```
...
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
...

```
Classpath mode:
```
INFO  o.tentackle.app.AbstractApplication.initialize - no module hooks found
```

### OSGi bundles

Although the artifacts hosted on maven central are not OSGi compatible, you can easily create
the OSGi bundles on your own. Simply git clone the [tentackle repo](https://bitbucket.org/krake-oss/tentackle/src/master/)
, build it and run another build
in the `tentackle-osgi` subfolder. This will create the bundles along with
their OSGi activators and the sources, which are especially useful for the Eclipse-IDE.
The activators tell the service factory which classloader to use and for which resources in `META-INF`:
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

We don't provide a public P2 repository yet, but that might change in the future.

---

## Related Documentation

- [Tentackle Maven Plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) — the build-time tool that analyzes `@Analyze` annotations and writes the `META-INF` service configurations.
- [Tentackle Modules](../../../../src/main/docs/modules.md) — how the framework is split into build-time, runtime and common modules, each contributing services.
- [Resource Bundles](./bundle.md) — bundle handling, which (like services) differs between classpath and modular mode.
- [Internationalization](./internationalization.md) — database-backed I18N built on top of the bundle and service infrastructure.
- [PDO / Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — PDOs and operations are interfaces whose implementations are located via these services.
- [Wurblets](../../../../tentackle-wurblets/src/main/docs/wurblets.md) — code generators that consume the analysis results (e.g. the `RemoteMethod` wurblet).
