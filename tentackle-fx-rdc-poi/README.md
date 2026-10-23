### JPMS Notice

This Maven project does not contain a `module-info.java`, although Apache POI ships a module
descriptor (`org.apache.poi.poi`) since version 5.
Its manifest declares the `Automatic-Module-Name` `org.tentackle.fx.rdc.poi` instead.

The reason is that POI requires two automatic modules: `com.zaxxer.sparsebitset`, which at least
declares an `Automatic-Module-Name`, and `commons.math3`, whose name is merely derived from the
filename of `commons-math3-3.6.1.jar`.
Compiling a real module against it makes the compiler warn:

    [WARNING] * Required filename-based automodules detected: [commons-math3-3.6.1.jar]. Please don't publish this project to a public artifact repository! *

Strictly speaking, POI 5.x and 6.x shouldn't have been published to Maven Central
before all of its dependencies were given a stable module name.

Because `tentackle-fx-rdc` is a real module, its default `TableUtilities` would always win over
the service provided by this automatic module. `PoiTableUtilities` is therefore annotated with
`@ClasspathFirst(TableUtilities.class)`.

### IMPORTANT!

If the **tentackle-jlink-maven-plugin** is used to create a `jlink` or `jpackage` image,
make sure to move the POI artifact to the classpath!
Otherwise, POI and all its dependencies end up on the module path. Like so:

    <plugin>
        <groupId>org.tentackle</groupId>
        <artifactId>tentackle-jlink-maven-plugin</artifactId>
        ...
        <configuration>
            ...
            <classpathDependencies>
                <dependency>
                    <groupId>org.apache.poi</groupId>
                    <artifactId>poi</artifactId>
                </dependency>
            </classpathDependencies>
        ...

Moving it to the module path folder via `modulePathOnly` is not enough, because POI would still
be treated as a module. That module, however, is referenced from an automatic module and therefore
loaded by the wrong class loader, which results in a `ClassNotFoundException`.

### When all dependencies are modularized...

The `module-info.java` would look like this
(plus a `ModuleHook`, which every Tentackle module provides),
and the `@ClasspathFirst` annotation could be dropped:

    module org.tentackle.fx.rdc.poi {

      exports org.tentackle.fx.rdc.poi;

      requires transitive org.tentackle.fx.rdc;
      requires org.apache.poi.poi;

      provides org.tentackle.common.ModuleHook with org.tentackle.fx.rdc.poi.service.Hook;

    }

See also [poi.md](src/main/docs/poi.md).
