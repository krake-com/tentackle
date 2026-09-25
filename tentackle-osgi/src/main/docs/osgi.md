# OSGi Bundles

The Tentackle artifacts on Maven Central are plain JARs (named JPMS modules or automatic modules), not OSGi
bundles. For applications running in an OSGi container — typically an Eclipse RCP or another Equinox- or
Felix-based runtime — the repository contains a separate build under `tentackle-osgi/` that wraps the runtime
modules into bundles, each with a small activator that registers the module's services with Tentackle's
[Service API](../../../../tentackle-common/src/main/docs/services.md).

`tentackle-osgi` is **not** a module of the main reactor. It is built separately, after the main build, and its
bundles are not published to Maven Central. There is no public P2 repository either.

## Building the bundles

1. Build and install the main reactor, so the Tentackle artifacts are in the local Maven repository:

   ```bash
   mvn clean install
   ```

2. Build the bundles in the `tentackle-osgi` subfolder:

   ```bash
   cd tentackle-osgi
   mvn clean install
   ```

The bundles are built with the [Apache Felix `maven-bundle-plugin`](https://felix.apache.org/documentation/subprojects/apache-felix-maven-bundle-plugin-bnd.html),
which also installs them into the local repository (and, on `deploy`, into the configured remote one). Each bundle
carries the sources of both the wrapped module and its activator under `OSGI-OPT/src`, which lets an IDE such as
Eclipse attach them automatically.

### Versioning

OSGi versions are restricted to `major.minor.micro[.qualifier]`, so the bundles cannot reuse the Tentackle version
(e.g. `25.19.0.0`). They are versioned `LTS.a.b` instead — for example `25.3.2` — where `a` and `b` are counted
independently of the Tentackle release. The Tentackle version the bundles wrap is set by the `tentackle.version`
property in [`tentackle-osgi/pom.xml`](../../../pom.xml) and must match the version installed in step 1.

## What is bundled

There is one bundle per runtime module, named after the module's package prefix with an `.osgi` suffix, e.g.
`tentackle.pdo.osgi` for `tentackle-pdo`:

| Layer       | Bundles                                                                                                                                   |
|-------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| Common      | `tentackle.common.osgi`, `tentackle.sql.osgi`                                                                                             |
| Core        | `tentackle.core.osgi`, `tentackle.quic.osgi`, `tentackle.quiche.osgi`, `tentackle.keyring.osgi`, `tentackle.i18n.osgi`                     |
| Database    | `tentackle.session.osgi`, `tentackle.database.osgi`, `tentackle.jta.osgi`                                                                  |
| Model       | `tentackle.model.osgi`                                                                                                                    |
| Application | `tentackle.pdo.osgi`, `tentackle.domain.osgi`, `tentackle.persistence.osgi`, `tentackle.web.osgi`                                          |
| UI          | `tentackle.fx.osgi`, `tentackle.fx.atlanta.osgi`, `tentackle.fx.rdc.osgi`, `tentackle.fx.rdc.poi.osgi`                                     |
| Providers   | `tentackle.log.slf4j.osgi`, `tentackle.log.log4j2v.osgi`, `tentackle.script.groovy.osgi`, `tentackle.script.ruby.osgi`                     |

Not bundled are the auto-update modules (`tentackle-update`, `tentackle-fx-rdc-update`), which target
jlink-based applications, the JSR-223 scripting provider `tentackle-script-jsr`, and — naturally — the build-time
and test-support modules.

A bundle embeds exactly its Tentackle module and the module's activator (`Embed-Transitive` is `false`); all other
dependencies, including the third-party libraries, are expected as bundles of their own. Imports are declared
optional and `DynamicImport-Package` is `*`, because the Service API loads implementation classes by name, often
from a bundle the requesting one does not know at build time. The bundles run on OSGi core 6 or later.

## The activators

The activator of a bundle does one thing: it tells the `ServiceFactory` which classloader provides the bundle's
services.

Without OSGi, a single classloader sees all `META-INF/services` entries, and `ServiceFactory` finds every
implementation there. In an OSGi container, each bundle has its own classloader, and a bundle cannot see the
resources of another. Therefore, `ServiceFactory` supports **explicit classloaders per service**, and each activator
registers its bundle's classloader for every service the bundle provides. The list of those services is the
resource index `META-INF/RESOURCE-INDEX.LIST`, which the
[`analyze` goal](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) of the
`tentackle-maven-plugin` generates alongside the service entries when its `index` parameter is set, as it is for
the Tentackle modules:

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

The explicit classloaders form a stack per service: starting a bundle pushes its loader, stopping it pops it
again, so whatever was registered before becomes active again. See
[Service and Configuration API → OSGi Bundles](../../../../tentackle-common/src/main/docs/services.md#osgi-bundles).
The activator of `tentackle.web.osgi` is currently empty, since `tentackle-web` provides no services.

The activators live in separate modules under
[`tentackle-osgi-activators/`](../../../tentackle-osgi-activators/pom.xml), one per bundle. Each is a regular
dependency of its bundle, which embeds only its direct dependencies: had the activator been compiled into the
bundle module itself, its dependency on `tentackle-common` would be direct as well, and every bundle would embed a
copy of `org.tentackle.common`. Only `tentackle.common.osgi` compiles its activator itself, since it wraps
`tentackle-common` anyway.

### Your own bundles

Application modules that declare services (via `@Service` or `@MappedService`, including the generated PDO
implementations) need the same treatment when deployed as bundles: set the `index` parameter of the `analyze`
goal, and add an activator like the one above.

## Related Documentation

- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) — the `ServiceFactory`
  and its per-service classloaders.
- [Tentackle Maven Plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) — the
  `analyze` goal and its `index` parameter.
- [Tentackle Modules](../../../../src/main/docs/modules.md) — the modules the bundles wrap.
- [Tentackle Logging](../../../../tentackle-core/src/main/docs/logging.md) — the logging providers, bundled as
  `tentackle.log.slf4j.osgi` and `tentackle.log.log4j2v.osgi`.
