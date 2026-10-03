### JPMS Notice

This maven project does not contain a `module-info.java`, although Apache POI ships a module
descriptor (`org.apache.poi.poi`) since version 5.
Its manifest declares the `Automatic-Module-Name` `org.tentackle.fx.rdc.poi` instead.

The reason is that POI requires `commons-math3`, which is still a filename-based automatic module.
Compiling a real module against it makes the compiler warn:

    [WARNING] * Required filename-based automodules detected: [commons-math3-3.6.1.jar]. Please don't publish this project to a public artifact repository! *

Strictly speaking, POI 5.x shouldn't have been published to Maven Central
before all of its dependencies were modularized as well.

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
(plus a `ModuleHook`, which every Tentackle module provides):

    module org.tentackle.fx.rdc.poi {

      exports org.tentackle.fx.rdc.poi;

      requires org.tentackle.fx.rdc;
      requires org.apache.poi.poi;

      provides org.tentackle.common.ModuleHook with org.tentackle.fx.rdc.poi.service.Hook;

    }

See also [poi.md](src/main/docs/poi.md).
