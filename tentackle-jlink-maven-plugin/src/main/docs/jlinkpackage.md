## Tentackle Maven Plugin for Jlink and Jpackage

Tentackle provides a maven plugin to create self-contained applications using the `jlink` or `jpackage` tools of the JDK.
A self-contained image bundles a *custom, trimmed-down Java runtime* together with the application, so the target
machine needs no pre-installed JDK or JRE: unpack a ZIP or run an installer, and the application launches.

Although part of the framework, the plugin can be used for any kind of java application, whether modular, non-modular,
or even mixed (see [Application Categories and Jigsaw Myths](#application-categories-and-jigsaw-myths) below). It does
so by *analyzing the application and all its dependencies* — reading `module-info` for real modules and falling back
to `jdeps` for the rest — to determine the best packaging strategy automatically. The result is a runtime image that
contains only the modules the application actually needs, which keeps it small and reduces the attack surface.
Only minimum configuration is required, since the maven project model already provides most of the
necessary information; platform-specific details are kept out of the poms and live in editable
[Freemarker templates](#adding-the-plugin-to-your-maven-project) instead.

The plugin provides 3 maven goals:

1. **jlink** — creates a ZIP archive containing a self-contained image of the application: the trimmed modular Java
   runtime, the application's own artifacts, its (optionally filtered) configuration resources, and a generated,
   platform-specific launch script. The result is a portable, unpack-and-run distribution.
2. **jpackage** — goes one step further and produces a *native* executable plus a platform installer
   (`.deb`/`.rpm` on Linux, `.msi`/`.exe` on Windows, `.pkg`/`.dmg` on macOS), with menu entries, shortcuts, and
   icons. It supports per-user or per-system installations, depending on the platform. Internally, it still uses
   jlink to build the runtime first (see [How The Jpackage Goal Works](#how-the-jpackage-goal-works)).
3. **init** — (re-)installs the defaults of the project-specific templates used to generate the scripts and
   tool-option files, so you can start from a known-good baseline and then customize.

For per-user installations an optional [auto-update feature](#auto-update-feature) is available that enables a running
application to download a new image and update itself in place. This is especially useful for desktop applications,
since [Java Webstart](https://en.wikipedia.org/wiki/Java_Web_Start) was removed in Java 11 and the jlink/jpackage
toolset makes it unnecessary.

Notice that the plugin must be executed on the target platform, because `jlink` and `jpackage` are 
platform-specific tools (a Windows installer can only be built on Windows, and so on). The
[Maven toolchain support](#using-the-maven-toolchain) lets you decouple the JDK that *runs the build* from the JDK
that *builds the image*.

### Prerequisites

Java 11 or newer and Maven 3.6.3 or newer is required. The application and its dependencies
may be compiled for older Java versions as long as they don't use deprecated features that were removed
in the meantime. For jpackage you need at least Java 14. However, you can still create images for Java 11 LTS
with jpackage by using maven toolchains (see below).

### Application Categories and Jigsaw Myths

Regardless of whether the jlink- or jpackage-goal is used, the created application image falls into one of
3 categories:

1. full-blown modular (JPMS/Jigsaw) applications running on the module path.
2. modular applications that require non-modularized dependencies (so-called _automatic modules_)
3. traditional applications running on the classpath.

Many Java developers still think that only applications of the first category can be packaged with jlink or jpackage,
because it is required that all artifacts are fully modularized according to the JPMS. 
Fortunately, this is not the whole truth. It is true, of course, that the generated `jimage` file of the runtime must contain 
only real modules and those modules must not refer to non-modular artifacts, but the remaining artifacts 
can still be explicitly passed to the native executable 
(via command line options in case of jlink or via configuration files in case of jpackage).

Similar applies to the so-called _split packages_. For applications running in modular mode,
Java packages are bound to their module and must not appear in more than one artifact.
However, you can still generate a modular runtime and run the application in classpath mode.

In short: if your application runs on Java 11 or newer, you can package it in one or another way.

### Adding the Plugin to Your Maven Project

There are several ways to use the plugin in your project. If the project is already a multi-module
maven project, add another submodule and let this submodule build the jlink or jpackage image,
like so:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>com.example</groupId>
    <artifactId>myapp</artifactId>
    <version>1.0-SNAPSHOT</version>
  </parent>
  
  <artifactId>myapp-package</artifactId>
  <packaging>jlink</packaging>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.build.resourceEncoding>UTF-8</project.build.resourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.example</groupId>
      <artifactId>myapp</artifactId>
      <version>${project.version}</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.tentackle</groupId>
        <artifactId>tentackle-jlink-maven-plugin</artifactId>
        <version>11.7.1.0</version>
        <extensions>true</extensions>
        <configuration>
          <mainClass>com.example.myapp.Main</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>

</project>
```

Otherwise, create a new maven project and add your existing project's artifact to the dependencies,
like so:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>org.example</groupId>
  <artifactId>sample-package</artifactId>
  <version>1.0-SNAPSHOT</version>
  <packaging>jlink</packaging>

  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.build.resourceEncoding>UTF-8</project.build.resourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.example</groupId>
      <artifactId>sample</artifactId>
      <version>1.0-SNAPSHOT</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.tentackle</groupId>
        <artifactId>tentackle-jlink-maven-plugin</artifactId>
        <version>11.7.1.0</version>
        <extensions>true</extensions>
        <configuration>
          <mainClass>com.example.Main</mainClass>
        </configuration>
      </plugin>
    </plugins>
  </build>

</project>
```

There is a third way using maven executions for special scenarios (not covered in this document).

In any case, the important parts are:

- the packaging type: either `jlink` or `jpackage`
- the dependency to the (main-) module of your existing project
- the plugin configuration for the _tentackle-jlink-maven-plugin_ with:
  * `<extensions>true</extensions>`: this makes the new packaging types known to maven.
  * the `<mainClass>` of the project.
  * the `<mainModule>` if it is a modular project (JPMS)
  
That's it!   

Now run `mvn clean install` and the console output will look like this:

```
...
[INFO] --- tentackle-jlink-maven-plugin:11.7.1.0:jlink (default-jlink) @ sample-package ---
[INFO] template directory created: /home/harald/java/sample/pkg/templates
[INFO] installed template name.ftl
[INFO] installed template run.ftl
[INFO] automatic module   sample  org.example:sample:jar:1.0-SNAPSHOT:compile
[INFO] creating jlink image for a classpath application with Java 15.0.1
[INFO] Building zip: /home/harald/java/sample/pkg/target/sample-package-1.0-SNAPSHOT-jlink.zip
[INFO] 
[INFO] --- maven-install-plugin:2.5.2:install (default-install) @ sample-package ---
[INFO] No primary artifact to install, installing attached artifacts instead.
[INFO] Installing /home/harald/java/sample/pkg/pom.xml to /home/harald/.m2/repository/org/example/sample-package/1.0-SNAPSHOT/sample-package-1.0-SNAPSHOT.pom
[INFO] Installing /home/harald/java/sample/pkg/target/sample-package-1.0-SNAPSHOT-jlink.zip to /home/harald/.m2/repository/org/example/sample-package/1.0-SNAPSHOT/sample-package-1.0-SNAPSHOT-linux-amd64.zip
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
...
```

Or if you used the packaging type `jpackage`, it looks like this:

```
[INFO] --- tentackle-jlink-maven-plugin:11.7.1.0:jpackage (default-jpackage) @ sample-package ---
[INFO] template directory created: /home/harald/java/sample/pkg/templates
[INFO] installed template package-image.ftl
[INFO] installed template package-installer.ftl
[INFO] automatic module   sample  org.example:sample:jar:1.0-SNAPSHOT:compile
[INFO] creating jlink image for a classpath application with Java 15.0.1
[INFO] creating application image
[INFO] creating installer
[INFO] 
[INFO] --- maven-install-plugin:2.5.2:install (default-install) @ sample-package ---
[INFO] No primary artifact to install, installing attached artifacts instead.
[INFO] Installing /home/harald/java/sample/pkg/pom.xml to /home/harald/.m2/repository/org/example/sample-package/1.0-SNAPSHOT/sample-package-1.0-SNAPSHOT.pom
[INFO] Installing /home/harald/java/sample/pkg/target/main_1.0-1_amd64.deb to /home/harald/.m2/repository/org/example/sample-package/1.0-SNAPSHOT/sample-package-1.0-SNAPSHOT-linux-amd64.deb
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
```

When the plugin is invoked the first time, it creates a template directory and installs some default
templates. The plugin uses [Freemarker](https://freemarker.apache.org/) as its template engine. 
The templates can and should be modified according to your project-specific needs. 
The defaults, however, already work for the most basic scenarios.
 
The templates refer to a _model_ that provides variables. One of the variables is `osName`.
It is used to derive the platform. For more details, names of the variables, how to define
your own, etc... please refer to the [plugin docs](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-jlink-maven-plugin/plugin-info.html). 
By using templates, we keep the maven poms free from platform-specific stuff and don't need 
to fiddle with maven profiles.

In the case of jlink the following templates are used:

- `name.ftl`: determines the name of the run script
- `run.ftl`: generates the run script

For jpackage:

- `package-image.ftl`: jpackage tool options to create the application image
- `package-installer.ftl`: jpackage tool options to create the installer

Example for package-image.ftl:
```
<#if osName?upper_case?contains("WIN")>

<#elseif osName?upper_case?contains("MAC")>

<#else>

</#if>
--icon src/pkg/admintool.png
--add-launcher AdminViewOnlyClient=src/pkg/viewonly.properties
--java-options "-splash:${runtimeDir}/conf/loading.gif"
```

Example for package-installer.ftl:
```
<#if osName?upper_case?contains("WIN")>
  --win-menu
  --win-menu-group KRAKE
  --win-shortcut
  --win-per-user-install
<#elseif osName?upper_case?contains("MAC")>

<#else>
--linux-menu-group KRAKE
--linux-shortcut
--linux-package-name adminclient
</#if>
--description "FX-Client for the Admintool"
--vendor "Krake Softwaretechnik, Triberg, Germany"
--copyright "(C) 2020, Krake Softwaretechnik, Triberg, Germany"
--license-file src/pkg/license.txt
```

For a complete multi-module JPMS example, please run the Tentackle maven archetype according to 
the project's [Quickstart](../../../../src/main/docs/quickstart.md).
 
### How The Jlink Goal Basically Works

The jlink goal first analyzes all dependencies of the application. For modular dependencies
it reads their `module-info` to determine the requirements. For traditional
dependencies it uses the `jdeps` tool. When all has been collected, it knows the relationships between all 
modules of the application, including the modules of the java runtime necessary to run
the application.

For classpath applications the jlink tool is used to create a modular java runtime
from the runtime modules only. The application's artifacts are copied to the `cp` subfolder
and will be added to the `classPath` variable of the freemarker model.

For modular applications, it depends on whether all modules are full-blown modular dependencies
or some modules refer to dependencies which are not modularized yet.
In the first case all modular modules are passed to jlink, which creates a plain modular application.
Otherwise, only the necessary runtime modules are passed to jlink, the application's modules
are copied to the `mp` subfolder and added to the `modulePath` variable of the freemarker model.

Next, filtered and non-filtered resources, if any, are copied to the `conf` subfolder by default.

Finally, the launch script is generated, and all files are packaged within a single ZIP archive.

### How The Jpackage Goal Works

Despite its name, the jpackage goal uses the jlink _and_ jpackage tools in combination to create an installer.
It works in 4 steps:

1. The first step basically does the same as what the jlink goal does, except that no launch 
   script and no ZIP archive is created. This results in a folder containing the so-called _runtime image_.
2. Next, the jpackage tool is invoked to add the generated native executables to launch the application
   along with their configuration files. This is called the _application image_.
3. Depending on the results of the analysis of the maven project model in phase 1,  
   the configuration files are modified appropriately.
   This is necessary because jpackage as a commandline tool has no clue about the maven project model and 
   makes assumptions that may be wrong for some application setups.
4. Finally, the jpackage tool is invoked again to create the installer from the modified application image.

In phase 2 the template `package-image.ftl` is used to generate the platform-specific jpackage options.
In phase 4 `package-installer.ftl` is used.

### "Unrequired" Dependencies

When running the example in this [Quickstart](../../../../src/main/docs/quickstart.md) you
may have stumbled upon the following console output:

```
[INFO] --- tentackle-jlink-maven-plugin:11.7.1.0:jlink (default-jlink) @ myapp-jlink-server ---
[INFO] full-blown module  com.example.myapp.server     com.example:myapp-server:jar:1.0-SNAPSHOT:compile
[INFO] full-blown module  com.example.myapp.persist    com.example:myapp-persistence:jar:1.0-SNAPSHOT:compile
[INFO] full-blown module  com.example.myapp.pdo        com.example:myapp-pdo:jar:1.0-SNAPSHOT:compile
[INFO] full-blown module  com.example.myapp.common     com.example:myapp-common:jar:1.0-SNAPSHOT:compile
[INFO] full-blown module  org.tentackle.persistence    org.tentackle:tentackle-persistence:jar:11.7.1.0:compile
[INFO] full-blown module  org.tentackle.pdo            org.tentackle:tentackle-pdo:jar:11.7.1.0:compile
[INFO] full-blown module  org.tentackle.session        org.tentackle:tentackle-session:jar:11.7.1.0:compile
[INFO] full-blown module  org.tentackle.core           org.tentackle:tentackle-core:jar:11.7.1.0:compile
[INFO] full-blown module  org.tentackle.database       org.tentackle:tentackle-database:jar:11.7.1.0:compile
[INFO] full-blown module  org.tentackle.sql            org.tentackle:tentackle-sql:jar:11.7.1.0:compile
[INFO] full-blown module  com.example.myapp.domain     com.example:myapp-domain:jar:1.0-SNAPSHOT:compile
[INFO] full-blown module  org.tentackle.domain         org.tentackle:tentackle-domain:jar:11.7.1.0:compile
[INFO] full-blown module  org.tentackle.update         org.tentackle:tentackle-update:jar:11.7.1.0:compile
[INFO] full-blown module  org.tentackle.common         org.tentackle:tentackle-common:jar:11.7.1.0:compile
[INFO] automatic module   org.tentackle.script.groovy  org.tentackle:tentackle-script-groovy:jar:11.7.1.0:compile
[INFO] automatic module   org.codehaus.groovy          org.codehaus.groovy:groovy:jar:indy:3.0.7:compile
[INFO] automatic module   org.tentackle.log.slf4j      org.tentackle:tentackle-log-slf4j:jar:11.7.1.0:compile
[INFO] automatic module   org.slf4j                    org.slf4j:slf4j-api:jar:1.7.30:compile
[INFO] automatic module   logback.classic              ch.qos.logback:logback-classic:jar:1.2.3:runtime
[INFO] automatic module   logback.core                 ch.qos.logback:logback-core:jar:1.2.3:runtime
[INFO] automatic module   org.postgresql.jdbc          org.postgresql:postgresql:jar:42.2.18:runtime
[INFO] creating jlink image for a plain modular application with Java 15.0.1
[INFO] Building zip: /home/harald/java/myapp/myapp/jlink/server/target/myapp-jlink-server-1.0-SNAPSHOT-jlink.zip
```
Although the plugin detects seven automatic modules, it still claims to
create a jlink image for a plain modular application.

How is that possible?

Well, it is possible because those automatic modules are not _required_. 
In other words: there is no module-info that explicitly refers to an automatic 
module in a `required` clause.
All those modules are just _wired_ at runtime. For example, the JDBC driver is loaded by the
`DriverManager`. Throughout the whole application, there is no import statement that refers to 
a Postgres specific class. The same applies to Groovy scripting and the logging implementation,
which are both loaded via Tentackle's [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md).

When the plugin detects such "unrequired" automatic modules, they are moved to the `mp` folder
and added to the `modulePath`. The full-blown modules still go into the modular runtime image as usual.

This is a nice trick if you have to use 3rd-party dependencies that are not modularized yet
and probably never will. Create an abstraction layer of the features
you need and _inject_ an implementation wrapper of those dependencies at runtime. 

### Adding Extra Modules

By default, the plugin adds only those modules to the runtime that are absolutely necessary
to run the application to keep the size of the image as small as possible. 
Sometimes, however, you may want to add some extra JDK modules
for monitoring or other purposes. This can be achieved with the `addModules` option.
Supposed you want to add tools like `jcmd` to the `bin` folder, add this to the configuration section:

```xml
  <addModules>
    <addModule>jdk.jcmd</addModule>
  </addModules>
```

The list of available modules can be obtained with `java --list-modules`.

### Using The Maven Toolchain

Maven provides a feature called [toolchains](https://maven.apache.org/guides/mini/guide-using-toolchains.html).
With toolchains you can instruct a toolchain-aware plugin to use a different JDK version than that
of the current maven build. The Tentackle maven plugin for jlink and jpackage even allows you
to select two different toolchains:

- `<jdkToolchain>`: selects the java version for the jlink, jdeps, and jpackage tools. For example, if
  your maven build runs on Java 11 LTS, but you want to use the latest Java in production, add
  the following lines (provided `toolchains.xml` is configured properly):
```xml
        <jdkToolchain>
          <version>latest</version>
        </jdkToolchain>
```
 
- `<jpackageToolchain>`: selects the java version for the jpackage tool only. This allows using
  the plugin's jpackage goal, for example, with Java 11 LTS, even though the jpackage tool was first released 
  with Java 14. The generated runtime will still be based on Java 11 LTS in this case.

### Extending the Plugin

The plugin delegates the actual creation of an artifact to an
[ArtifactCreator](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-jlink-maven-plugin/apidocs/org/tentackle/maven/plugin/jlink/ArtifactCreator.html),
so if you have special requirements — building several variants of your application in one run (e.g., a full client
and a view-only client), emitting an additional archive format, or post-processing the image — you can supply your
own implementation instead of the built-in one. Because the rest of the pipeline (dependency analysis, runtime
assembly, template model) stays in place, an extension only has to override the step that differs.

### Auto Update Feature

Since [Java Webstart](https://en.wikipedia.org/wiki/Java_Web_Start) was removed from the JDK with Java 11, 
the question arose how to keep applications up to date, especially desktop applications.
In the meantime a few alternatives are available, most to mention [OpenWebstart](https://openwebstart.com/),
which provides a kind of drop-in replacement.

However, the new JPMS with tools like jlink or jpackage is a real game changer when it comes to
application deployment, because you don't need a Java runtime already installed on your target
machine anymore. Unpack a ZIP archive or run an installer, and you're done!
Couldn't we use the same toolset to update an already installed application?
This is the idea behind Tentackle's update feature.

The feature is based on 3 components:

1. The Tentackle jlink/jpackage plugin configured with: `<withUpdater>true</withUpdater>`. 
   This creates an additional platform-specific update script (from template `update.ftl`) 
   that takes an unpacked archive containing the new application,
   ensures that the old application is no more running, updates the existing installation,
   and finally restarts the application. For the jpackage goal the template for the
   update script is `package-update.ftl` and an additional ZIP archive
   is created as well.

2. An update server. Whenever a client tries to connect to a server already running
   a newer version, it receives a `VersionIncompatibleException`. It then talks to
   an update server, sending its platform, architecture, version, etc... and the server
   replies with a URL pointing to an archive containing the necessary files.

3. The client UI part. It handles the exception, asks the user whether to update or abort,
   downloads the archive, verifies the checksum, unpacks it, invokes the update script, and terminates itself.
   
The API to implement the server- and client update service is located in the module
[tentackle-update](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-update/apidocs/org.tentackle.update/org/tentackle/update/package-summary.html).
Although made for Tentackle applications, it can be used as a blueprint to other applications
as well.

The UI part is implemented in [tentackle-fx-rdc-update](https://tentackle.org/static-content/sitedocs/tentackle/latest/tentackle-fx-rdc-update/apidocs/org.tentackle.fx.rdc.update/org/tentackle/fx/rdc/update/package-summary.html),
which is based on [JavaFX](https://openjfx.io/) and the [Tentackle](https://tentackle.org)
framework. As such, it cannot be used by any other kind of application. 
But it's a good starting point for your own update service.

Again, for a working example, please see this [Quickstart](../../../../src/main/docs/quickstart.md).


### Further Reading

[Java Platform Module System](https://en.wikipedia.org/wiki/Java_Platform_Module_System)

[jlink tool](https://docs.oracle.com/en/java/javase/15/docs/specs/man/jlink.html)

[jpackage tool](https://docs.oracle.com/en/java/javase/15/docs/specs/man/jpackage.html)

[jdeps tool](https://docs.oracle.com/en/java/javase/15/docs/specs/man/jdeps.html)
