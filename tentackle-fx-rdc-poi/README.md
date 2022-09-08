### JPMS Notice

This maven project does not contain a `module-info.java`, although `org.apache.poi.poi`
is already modularized since version 5.

The reason is:

    [INFO] --- maven-compiler-plugin:3.8.1:compile (default-compile) @ tentackle-fx-rdc-poi ---
    [WARNING] *********************************************************************************************************************************************************************
    [WARNING] * Required filename-based automodules detected: [commons-math3-3.6.1.jar, SparseBitSet-1.2.jar]. Please don't publish this project to a public artifact repository! *
    [WARNING] *********************************************************************************************************************************************************************

Strictly speaking, POI 5.x shouldn't have been pushed to Maven Central,
before commons-math3 and SparseBitSet were modularized as well...

### IMPORTANT!

If the **tentackle-jlink-maven-plugin** is used to create a jlink or jpackage image,
make sure to move the poi artifact to the classpath! 
Otherwise, all dependencies will be moved to the module path, because POI requires automatic modules. Like so:

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
            </classpathDependencies>`
        ...

Notice: moving it to the mp folder via modulePathOnly is not sufficient, because
it would still be treated as a module. But that module is referenced
from an automatic module and therefore using the wrong classloader, which
will result in a ClassNotFoundException. Maybe, this illegal setup will be changed
in POI 6.x or whatever...

### When all deps are modularized...

That's how the module-info.java would look like:

    module org.tentackle.fx.rdc.poi {
        exports org.tentackle.fx.rdc.poi;
        
        requires org.tentackle.fx.rdc;
        requires org.apache.poi.poi;
    }

### Optionally exclude slf4j artifacts

By default, logging is disabled in POI. If you don't need logging for debugging
purposes and want to keep the images as small as possible, you may consider excluding
the slf4j artifacts, if you're using another logging backend than slf4j.

    <dependency>
      <groupId>org.tentackle</groupId>
      <artifactId>tentackle-fx-rdc-poi</artifactId>
      <exclusions>
        <exclusion>
          <groupId>org.slf4j</groupId>
          <artifactId>slf4j-api</artifactId>
        </exclusion>
        <exclusion>
          <groupId>org.slf4j</groupId>
          <artifactId>jcl-over-slf4j</artifactId>
        </exclusion>
      </exclusions>
    </dependency>