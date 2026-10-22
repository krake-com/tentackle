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

The plugin provides 4 maven goals, all with the prefix `tentackle-jlink`:

1. **jlink** — creates a ZIP archive containing a self-contained image of the application: the trimmed modular Java
   runtime, the application's own artifacts, its (optionally filtered) configuration resources, and a generated,
   platform-specific launch script. The result is a portable, unpack-and-run distribution.
2. **jpackage** — goes one step further and produces a *native* executable plus a platform installer
   (`.deb`/`.rpm` on Linux, `.msi`/`.exe` on Windows, `.pkg`/`.dmg` on macOS), with menu entries, shortcuts, and
   icons. It supports per-user or per-system installations, depending on the platform. Internally, it still uses
   jlink to build the runtime first (see [How The Jpackage Goal Works](#how-the-jpackage-goal-works)).
3. **init** — (re-)installs the defaults of the project-specific templates used to generate the scripts and
   tool-option files, so you can start from a known-good baseline and then customize.
4. **layers** — materializes the [isolated module layers](#isolated-module-layers) of an application into
   `target/tentackle-layers`, for running and debugging it from the IDE without building an image.

For per-user installations an optional [auto-update feature](#auto-update-feature) is available that enables a running
application to download a new image and update itself in place. This is especially useful for desktop applications,
since [Java Webstart](https://en.wikipedia.org/wiki/Java_Web_Start) was removed in Java 11 and the jlink/jpackage
toolset makes it unnecessary.

Notice that the plugin must be executed on the target platform, because `jlink` and `jpackage` are 
platform-specific tools (a Windows installer can only be built on Windows, and so on). The
[Maven toolchain support](#using-the-maven-toolchain) lets you decouple the JDK that *runs the build* from the JDK
that *builds the image*.

If you read only one section of this document, make it
[Why The Plugin Generates The Start Script](#why-the-plugin-generates-the-start-script). It is the shortest path to
understanding what the plugin actually does.

### Prerequisites

The plugin requires the same JDK and Maven versions as the rest of the framework
(JDK >= 25, Maven >= 3.9.10, Maven 4 is supported as well). The application and its dependencies
may be compiled for older Java versions as long as they don't use deprecated features that were removed
in the meantime. The JDK the image is linked from can be selected independently of the JDK running the build
by using maven toolchains (see [below](#using-the-maven-toolchain)).

### What Jlink Needs From The JPMS

Everything the plugin does follows from a handful of JPMS rules. They are worth recalling, because they explain
why the plugin sometimes builds a picture-perfect modular image and sometimes falls back to something that looks
suspiciously like the good old classpath.

#### Three kinds of module

The module system sorts every jar on your dependency list into one of three buckets:

| Kind                           | What it is                                                                  | Lives on        |
|--------------------------------|-----------------------------------------------------------------------------|-----------------|
| **explicit** (a "real" module) | a jar with a `module-info.class`, i.e. a proper module descriptor           | the module path |
| **automatic**                  | a plain jar *on the module path*. The JPMS invents a descriptor for it      | the module path |
| **unnamed**                    | everything on the class path, lumped together into one big anonymous module | the class path  |

An automatic module gets its name from the `Automatic-Module-Name` entry of the jar's manifest, or, if that is
missing, from the jar's file name (the `.jar` suffix and a trailing `-<version>` are stripped, and anything that
isn't a valid identifier character becomes a dot). It exports *all* of its packages, it reads *every* other module
including the unnamed one, and its `provides` clauses are synthesized from the `META-INF/services` entries in the jar.
In short: an automatic module is a classpath jar wearing a module costume.

#### The rules that shape the packaging strategy

- **jlink links explicit modules only.** It needs a real module descriptor to compute the module graph and to place
  a module into the `jimage` file. Hand it an automatic module and it refuses, flat out. There is no flag to
  override this, and that is by design: the whole point of a linked image is that its module graph is known at link
  time, and an automatic module is precisely a module whose graph *isn't* known.
- **An explicit module cannot read the unnamed module.** There is no way to write `requires` for something on the
  class path. If your `module-info` needs a type from a non-modular jar, that jar *must* be on the module path,
  which makes it an automatic module — and per the previous rule, jlink then can't link it.
- **The two rules combine, and this is the important part.** If module `A` in your application declares
  `requires B` and `B` is automatic, jlink cannot link `B` — and therefore cannot link `A` either, since `A`'s
  graph is incomplete without `B`. The failure is contagious: a single required automatic module knocks your
  *entire application* out of the runtime image. What remains linkable is the JDK's own modules. This is exactly
  the difference between category 1 and category 2 [below](#application-categories-and-jigsaw-myths).
- **Split packages are fatal on the module path, harmless on the class path.** A package must not be provided by
  more than one module. Two artifacts sharing a package (a classic in the pre-JPMS world) can therefore never both
  be modules — but they can happily coexist in the unnamed module.

#### How modules get resolved at startup

At startup the JVM resolves a module graph, and only resolved modules exist as far as the runtime is concerned.
A jar sitting on the module path that nobody pulls in is *not loaded at all* — its classes are invisible even to
`Class.forName`. Resolution proceeds like this:

1. **Roots.** With `-m mymodule/Main`, the main module is the one and only root.
2. **`requires`.** The graph is closed transitively over the `requires` clauses. `requires static` is ignored here —
   it's a compile-time-only dependency.
3. **Service binding.** For every resolved module that declares `uses X`, any observable module that declares
   `provides X` is pulled in *and resolved* — even though nothing `requires` it. This is how a plugin finds its
   implementation without ever naming it.
4. **Automatic modules are all-or-nothing.** The moment *one* automatic module ends up in the graph, *every*
   automatic module on the module path is resolved along with it.

Steps 3 and 4 are what make ["unrequired" dependencies](#unrequired-dependencies) work, and they are the reason
Tentackle's own [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) keeps
working inside a linked image.

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
The categories differ only in *where the pieces end up*:

| Category         | What goes into the `jimage`             | What ends up next to it        | How it is launched                     |
|------------------|-----------------------------------------|--------------------------------|----------------------------------------|
| 1 plain modular  | JDK modules **and** application modules | nothing (except `conf`)        | `-m mainmodule/MainClass`              |
| 2 mixed modular  | JDK modules only                        | application modules in `mp/`   | `-p mp/... -m mainmodule/MainClass`    |
| 3 classpath      | JDK modules only                        | all artifacts in `cp/`         | `-cp cp/... MainClass`                 |

Which category you land in isn't a setting — the plugin derives it from your dependencies. And whatever *doesn't*
fit into the image has to be handed to the JVM on the command line, which brings us to the heart of the matter.

### Why The Plugin Generates The Start Script

At first glance the generated start script looks like a convenience: a little `run.sh` so nobody has to type a
`java` command by hand. It is not. **The start script is the other half of the image**, and understanding why is
the key to understanding this plugin.

Here is the thing: jlink *can* create a launcher itself, via `--launcher name=module/mainclass`. The plugin
deliberately doesn't use it, because that launcher can do exactly one thing — start a main class from a module that
lives *inside* the runtime image. It cannot pass `--module-path`, it cannot pass `-cp`, and it cannot be told
anything about artifacts that live next to the image rather than in it.
As soon as a single piece of your application fails to make it into the image — one automatic module, one
non-modular jar, one directory of configuration resources — that launcher is useless, and per the JPMS rules above,
that is the *normal* case, not the exception.

So the image alone never tells the whole story. The dependency analysis produces exactly four values:

- `mainModule` — empty for a classpath application
- `mainClass`
- `modulePath` — whatever had to stay outside the image, in `mp/`
- `classPath` — whatever runs unnamed, in `cp/`, plus `conf`

(this stops being literally true the moment `<layers>` are configured — see
[Isolated Module Layers](#isolated-module-layers) below, where module-path and class-path become
per-layer and the start script launches a bootstrap instead of the real main class)

and the start script is *where those four values become an executable command line*. The script isn't
documentation of the analysis; it **is** the analysis, materialized. That is also why those same four values are the
core variables of the [Freemarker template model](#adding-the-plugin-to-your-maven-project): the template is the
seam where the plugin's knowledge about your project meets the platform's idea of how to start a process.

Once you see this, the whole plugin collapses into one sentence: *decide what can go into the image, and record what
couldn't, so the launcher can add it back.* Every feature — the category detection, the `mp/` and `cp/` folders, the
template model, the cfg patching — serves that sentence.

Two details of the generated script are worth knowing:

- It is written to `bin/` — right next to the `java` executable of the linked runtime — and its first action is to
  `cd` to the image root, so that the relative paths `mp/...`, `cp/...` and `conf/...` resolve no matter where the
  user invoked it from. Symlinks are resolved first (via `readlink -f`, or an equivalent on macOS).
- Its name comes from its own template (`name.ftl`), so it is `run.cmd` on Windows and `run.sh` elsewhere.
- It passes its own arguments on to the application (`"$@"`, or `%*` on Windows), and the `sh` variant
  `exec`s the JVM, so that the application replaces the shell and receives its signals directly.

And the jpackage goal? It faces the very same problem. jpackage builds its own native launcher, so there is no shell
script to generate — instead the plugin **patches the launcher's `.cfg` file** afterward, writing the module path
into a `java-options` entry and the class path into `app.classpath`. Different mechanism, identical purpose: tell the
JVM about everything that didn't fit into the image. See [How The Jpackage Goal Works](#how-the-jpackage-goal-works).

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
        <version>${tentackle.version}</version>
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
        <version>${tentackle.version}</version>
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

Note that `<mainModule>` is what decides between a modular and a classpath application: leave it out, and the plugin
builds a category 3 image even if every one of your jars has a `module-info`.

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

The `full-blown module` / `automatic module` lines are the plugin telling you which bucket each of your dependencies
landed in, and the `creating jlink image for a ...` line reports the category it derived from that. If you ever
wonder why your image turned out bigger, smaller or differently laid out than expected, this is the place to look.

When the plugin is invoked the first time, it creates a template directory and installs some default
templates. The plugin uses [Freemarker](https://freemarker.apache.org/) as its template engine. 
The templates can and should be modified according to your project-specific needs. 
The defaults, however, already work for the most basic scenarios.
 
The templates refer to a _model_ that provides variables:

| Variable                                                  | Meaning                                                                                       |
|-----------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| `mainModule`, `mainClass`, `modulePath`, `classPath`      | the four values [described above](#why-the-plugin-generates-the-start-script); `mainModule` is empty for a classpath application |
| `phase`, `goal`, `id`                                     | the lifecycle phase, the goal (`jlink` or `jpackage`) and the execution id                     |
| `layered`                                                 | whether [isolated module layers](#isolated-module-layers) are configured                       |
| `bootstrapModule`, `bootstrapClass`, `layersConfigPath`   | only if layered: the module and class to launch instead of the main class, and the path of the generated `layers.properties` |
| `runtimeDir`                                              | jpackage option templates only: the platform-specific path of the runtime image               |
| `runScript`                                               | update script templates of the `jlink` goal only: the name of the run script                   |
| system properties, maven properties, `<variables>`        | each converted to camelCase (`os.name` becomes `osName`)                                       |

`osName` is the one you'll reach for most, since it is how a template derives the platform. The system properties,
the maven properties and your own `<variables>` override each other in the order given by `variablesPrecedence`
(see the [Configuration Reference](#configuration-reference)); the other variables cannot be overridden.
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
This is the "contagious failure" from [the JPMS rules](#the-rules-that-shape-the-packaging-strategy): one required
automatic module is enough to move *all* of your modules out of the image. The plugin warns about this
(`required ... is not a full-blown module`); set `<failOnAutomaticRequired>true</failOnAutomaticRequired>` to turn
that warning into a build failure if a plain modular image is a hard requirement for you.

Next, filtered and non-filtered resources, if any, are copied to the `conf` subfolder by default. The `conf` folder
is added to the classpath, which is why an application can always read its configuration as a resource, in every
category.

Finally, the launch script is generated, and all files are packaged within a single ZIP archive. The interesting
parts of the resulting layout:

```
myapp/
├── bin/          the linked runtime's java executable — plus the generated run.sh / run.cmd
├── lib/          the jimage holding the linked modules
├── conf/         configuration resources (added to the classpath)
├── mp/           modules that could not be linked (category 2)
└── cp/           artifacts running in the unnamed module (category 3, or mixed setups)
```

`mp` and `cp` only exist if the analysis actually put something there — for a plain modular application, the
classpath consists of `conf` alone. A [layered](#isolated-module-layers) application additionally gets an
`mp-leaf/` folder and one `mp/<layer>/` folder per layer.

If you want to force individual artifacts out of the image, the plugin offers escape hatches:
`<classpathDependencies>` pushes an artifact onto the classpath even if it is a proper module, and
`<modulePathOnly>` keeps a module on the module path instead of linking it into the image — handy when a module has
to be replaceable in the field without rebuilding the image.

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

Step 3 is the jpackage counterpart of the generated start script, and it deserves a word of explanation, because it
is the one step that looks like the plugin is meddling. jpackage's native launcher reads a `.cfg` file next to the
application image, and that file — not a command line — is where the module path and the class path have to be
declared. Unfortunately the two are not declared symmetrically: the module path goes into a `java-options` entry as
a plain `-p`, whereas the class path is *ignored* if given as `-cp` there and has to be set as `app.classpath` in the
`[Application]` section instead. The plugin knows the correct spelling for both, patches the `.cfg` files of every
launcher, and cleans up the jars that jpackage duplicated into the `app` directory along the way.

The application image also gets `-DROOTDIR`, `-DAPPDIR` and `-DRUNTIMEDIR` passed as java options, so the
application — and your own templates, via `${runtimeDir}` — can find its way around the installation.
A [layered](#isolated-module-layers) application additionally gets `-Dtentackle.layers` and
`-Dtentackle.layers.basedir`, both pointing into the runtime image, because the native launcher — unlike the start
script — does not change into the image directory first.

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

Since nothing requires them, they don't drag the application out of the image: the full-blown modules are linked as
usual, and only the automatic ones are copied to the `mp` folder and added to the `modulePath`.

But if the module graph never mentions them — how do they get *loaded* at runtime? This is where
[service binding and the all-or-nothing rule](#how-modules-get-resolved-at-startup) come in, and it is worth
following the chain once:

1. `org.tentackle.common` declares `uses org.tentackle.common.ModuleHook`, and every Tentackle module — including
   the ones on the module path — declares `provides ModuleHook with ...`.
2. The JVM resolves the boot layer *with service binding*, so those provider modules are pulled into the graph
   although no `requires` clause names them.
3. As soon as one of them is an automatic module, the all-or-nothing rule resolves *every* automatic module on the
   module path — including the JDBC driver and the logging backend, which provide nothing at all.
4. Now that they are resolved, their classes and their `META-INF/services` entries are visible to the class loader,
   which is exactly what `DriverManager` and Tentackle's `ServiceFinder` need.

The takeaway is that a module on the module path is not automatically available — it has to be *reachable*, whether
by `requires`, by service binding, or by riding along with another automatic module. Tentackle's service API arranges
for the first domino to fall.

This is a nice trick if you have to use 3rd-party dependencies that are not modularized yet
and probably never will. Create an abstraction layer of the features
you need and _inject_ an implementation wrapper of those dependencies at runtime. 
You get a fully modular, jlink-able application *and* keep the freedom to use the un-modularized library of your
choice — the exact opposite of the all-or-nothing migration that the JPMS is often accused of demanding.

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

This is also the cure for the classic runtime surprise of trimmed images: a `NoClassDefFoundError` for a JDK class
that nobody could see coming, because it is reached reflectively and therefore invisible to both `module-info` and
`jdeps`. Groovy, for instance, needs `java.desktop` for `java.beans.BeanInfo`, and `jdk.localedata` is required for
any locale outside of `en`. There is a mirror-image option, `<excludeModules>`, for the rare case that the analysis
drags in something you know you don't need.

### Using The Maven Toolchain

Maven provides a feature called [toolchains](https://maven.apache.org/guides/mini/guide-using-toolchains.html).
With toolchains you can instruct a toolchain-aware plugin to use a different JDK version than that
of the current maven build. The Tentackle maven plugin for jlink and jpackage even allows you
to select two different toolchains:

- `<jdkToolchain>`: selects the java version for the jlink, jdeps, and jpackage tools. For example, if
  your maven build runs on the current LTS release, but you want to use the latest Java in production, add
  the following lines (provided `toolchains.xml` is configured properly):
```xml
        <jdkToolchain>
          <version>latest</version>
        </jdkToolchain>
```
 
- `<jpackageToolchain>`: selects the java version for the jpackage tool only, while the runtime is still
  linked by the tools of `<jdkToolchain>`. This allows building installers for a runtime whose own jpackage
  tool lacks a feature, or for an older Java runtime without a jpackage tool at all (it was introduced in
  Java 14). An empty `<jpackageToolchain/>` deselects the toolchain configured by the maven-toolchain-plugin.

If toolchains cannot be used, the tools can be given explicitly via `jdepsTool`, `jlinkTool` and `jpackageTool`
(user properties of the same name).

Keep in mind that the linked runtime is the JDK the *tools* come from, not the one maven runs on: the toolchain
you select here is the Java version your users will actually be running.

### Configuration Reference

Besides the parameters explained above (`mainClass`, `mainModule`, `addModules`, `excludeModules`,
`modulePathOnly`, `classpathDependencies`, `variables`, `withUpdater`, `layers`/`leafLayer`, `jdkToolchain` and
`jpackageToolchain`), the goals accept the following. Like all Tentackle mojos, they also inherit `verbosity`,
`skip`, `charset` and `minLogLevel` (see the
[`tentackle-maven-plugin` docs](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md#common-configuration)).

**Image contents** (`jlink` and `jpackage`):

| Parameter                | Default                             | Meaning                                                                                                 |
|--------------------------|-------------------------------------|---------------------------------------------------------------------------------------------------------|
| `resourcesDirectory`     | `${project.build.directory}/classes` | Resources copied to the image's `conf` directory, such as logger configurations or backend properties. Set it to a non-existent directory (e.g. `none`) to copy nothing. |
| `extraDirectories`       |                                     | Extra directories copied to the image.                                                                  |
| `extraClasspathElements` |                                     | Extra classpath elements, relative to the image root.                                                   |
| `failOnAutomaticRequired`| unset                               | What happens if a modular application requires an automatic module: unset logs a warning, `true` fails the build, `false` logs it as info. Ignored for classpath applications. |
| `templateDir`            | `${project.basedir}/templates`      | The directory holding the templates.                                                                    |
| `variablesPrecedence`    | `system, maven, variables`          | The order in which system properties, maven properties and the extra `variables` override each other (later wins). Also accepted: `sys`; `mvn`, `properties`, `props`, `pom`; `vars`, `var`, `extras`, `extra`, `template`. |

**jlink options** (`jlink` and `jpackage`):

| Parameter                  | Default | Meaning                                                               |
|----------------------------|---------|-----------------------------------------------------------------------|
| `stripDebug`               | `false` | jlink's `--strip-debug`.                                              |
| `compress`                 |         | jlink's `--compress`: `zip-0` (none) to `zip-9` (best). The legacy values `0`, `1` and `2` are deprecated since Java 21. |
| `noHeaderFiles`            | `false` | jlink's `--no-header-files`: omit the `include` directory.            |
| `noManPages`               | `false` | jlink's `--no-man-pages`: omit the `man` directory.                   |
| `ignoreSigningInformation` | `false` | jlink's `--ignore-signing-information`: allow signed modular jars.   |
| `saveOpts`                 |         | jlink's `--save-opts`: the file to save the jlink options to.        |
| `jlinkOptions`             |         | Further jlink options, e.g. platform-specific or hidden ones such as `--upgrade-files`. |

**Output**:

| Parameter         | Goal                | Default                              | Meaning                                              |
|-------------------|---------------------|--------------------------------------|------------------------------------------------------|
| `imageDirectory`  | `jlink`, `jpackage` | `${project.build.directory}/jlink`   | The directory jlink creates the image in.            |
| `zipDirectory`    | `jlink`, `jpackage` | `${project.build.directory}`         | The directory for the ZIP files.                     |
| `extraClassifier` | `jlink`, `jpackage` |                                      | An additional classifier for the deployed artifacts. |
| `packageDirectory`| `jpackage`          | `${project.build.directory}/jpackage`| The directory the installers are created in.         |
| `layersDirectory` | `layers`            | `${project.build.directory}/tentackle-layers` | The directory the layers are materialized into. |

**Templates** — the file names of the templates in `templateDir`:

| Parameter                  | Goal       | Default                 | Template for                                      |
|----------------------------|------------|-------------------------|---------------------------------------------------|
| `nameTemplate`             | `jlink`    | `name.ftl`              | the name of the start script, e.g. `run.sh`        |
| `runTemplate`              | `jlink`    | `run.ftl`               | the start script                                   |
| `updateTemplate`           | `jlink`    | `update.ftl`            | the update script, if `withUpdater` is set         |
| `packageImageTemplate`     | `jpackage` | `package-image.ftl`     | the jpackage options creating the application image |
| `packageInstallerTemplate` | `jpackage` | `package-installer.ftl` | the jpackage options creating the installer        |
| `packageUpdateTemplate`    | `jpackage` | `package-update.ftl`    | the update script, if `withUpdater` is set         |

**jpackage only**:

- `jpackageOptions`: further jpackage options, e.g. platform-specific or hidden ones.
- `mainJar`: the jar holding the main class. Only needed for classpath applications, and only if the top-level
  artifact of the dependency tree is not the main jar. A unique substring is sufficient, e.g. `myapp-server`.

**init only**:

- `copyAll` (default `true`): copy all templates. If `false`, only the templates already present in
  `templateDir` are overwritten, which restores the defaults of the customized templates without adding the
  ones the project doesn't use.

The `init` and `layers` goals need none of the jlink, jdeps or jpackage tools.

All goals are thread-safe and run concurrently in a parallel build (`mvn -T …`).

### Extending the Plugin

The plugin delegates the actual creation of an artifact to an
[ArtifactCreator](https://tentackle.org/tentackle/tentackle-jlink-maven-plugin/apidocs/org/tentackle/maven/plugin/jlink/ArtifactCreator.html),
so if you have special requirements — building several variants of your application in one run (e.g., a full client
and a view-only client), emitting an additional archive format, or post-processing the image — you can supply your
own implementation instead of the built-in one. Because the rest of the pipeline (dependency analysis, runtime
assembly, template model) stays in place, an extension only has to override the step that differs.

Likewise, the template model itself is pluggable via a
[TemplateModelFactory](https://tentackle.org/tentackle/tentackle-jlink-maven-plugin/apidocs/org/tentackle/maven/plugin/jlink/TemplateModelFactory.html)
if the built-in variables aren't enough for your scripts.

### Isolated Module Layers

Sometimes two parts of the same application genuinely need different, otherwise conflicting versions
of the same dependency. JPMS resolves one flat `Configuration` per boot, so there is no command-line
way to put two modules of the same name on one module path — but a `java.lang.ModuleLayer` can hold
its own, independently-resolved graph, parented on another layer, so a sibling layer can define a
module of the same name without colliding. See
[layers.md](../../../../tentackle-common/src/main/docs/layers.md) for the full runtime story
(`ModuleLayerBuilder`, `LayerBootstrap`, reaching an isolated layer via `ServiceFinder`); this section
covers only the plugin configuration and packaging layout.

Configure one `<layer>` per isolated dependency group. Each layer's dependencies are resolved
**independently** of the project's own dependency graph (Maven only ever resolves one version per
`groupId:artifactId` within a single project, so two versions could never both appear in the
project's own `<dependencies>` in the first place):

```xml
<configuration>
  <mainModule>com.example.app</mainModule>
  <mainClass>com.example.app.Main</mainClass>
  <leafLayer>app</leafLayer>
  <layers>
    <layer>
      <name>lib-v1</name>
      <dependencies>
        <dependency>
          <groupId>org.example</groupId>
          <artifactId>some-lib</artifactId>
          <version>1.0</version>
        </dependency>
      </dependencies>
    </layer>
    <layer>
      <name>lib-v2</name>
      <leafParent>true</leafParent>
      <dependencies>
        <dependency>
          <groupId>org.example</groupId>
          <artifactId>some-lib</artifactId>
          <version>2.0</version>
        </dependency>
      </dependencies>
    </layer>
  </layers>
</configuration>
```

`leafLayer` names the layer holding the application's own main module — required as soon as any
`<layer>` is configured, and `mainModule` becomes required too (a classpath application has nothing
for a `ModuleLayer` to hold). A layer marked `<leafParent>true</leafParent>` becomes one of the leaf's
own parents, i.e. the leaf module may directly `requires` one of its modules; layers not marked this
way are still built and usable (typically as another layer's own `<parent>`), just not directly
visible to the leaf. A layer's optional `<parent>` holds the comma-separated names of its own parent
layers; without it, the layer is parented on the boot layer. Since a single module cannot `requires` two modules of the same name, two
sibling layers pinning conflicting versions should not both be `leafParent` — instead, give each its
own dedicated consumer layer and have the leaf reach them via
`ServiceFactory.getServiceFinder(ModuleLayer, ...)` (see the worked example in `layers.md`).

Packaging layout, once `<layers>` are configured:

- The application's own main module is never linked into the jimage, even if the rest of the
  application would otherwise qualify as plain-modular: it must remain a loose file so
  `ModuleLayerBuilder` can find it later, and it must never share a directory with whatever boots
  `LayerBootstrap`. It goes to its own, dedicated `mp-leaf/` folder instead of the regular `mp/`,
  together with every other module that would otherwise end up in `mp/` — except
  `org.tentackle.common`, which hosts `LayerBootstrap` itself. The leaf layer resolves them on its
  own, so a sibling layer can still hold a different version of any of them. Modules linked into the
  jimage stay there.
- Each declared `<layer>` gets its own `mp/<name>/` folder, populated from its independent Aether
  resolution.
- A generated `conf/layers.properties` describes the whole graph for `LayerBootstrap` to read at
  startup.
- The generated launcher (`run.ftl`, or the native launcher created by jpackage) runs
  `org.tentackle.common.LayerBootstrap` instead of the real main class, with
  `-Dtentackle.layers=conf/layers.properties` (jpackage: an absolute path plus
  `-Dtentackle.layers.basedir`, see [How The Jpackage Goal Works](#how-the-jpackage-goal-works)).
  `modulePath`/`classPath` (the regular, shared bucket) only ever contain what is needed to boot
  `LayerBootstrap` itself — `org.tentackle.common` on the module path, plus whatever was put on the
  class path — never a `<layer>`'s own jars.

For local development and IDE run/debug configurations, the full `jlink`/`jpackage` goals are too
slow to run on every edit-compile-run cycle, and IDEs don't invoke Maven plugin goals to build their
auto-generated run configurations anyway. The lightweight `layers` goal (bind it to `process-classes`
in projects using `<layers>`) materializes the same information into `target/tentackle-layers/`
instead, with the leaf entry pointing directly at `target/classes` rather than a copy of it (all paths
in the generated file are absolute):

```xml
<execution>
  <goals><goal>layers</goal></goals>
</execution>
```

Then create a plain "Application" run/debug configuration by hand (IDEs won't auto-generate this
one) with main class `org.tentackle.common.LayerBootstrap`, the project's normal module path
(unaffected by `<layers>`, since their jars were never part of the project's own dependency graph),
VM option `-Dtentackle.layers=target/tentackle-layers/layers.properties`, and working directory set
to the project's base directory. Debugging needs no special support beyond that: `LayerBootstrap` and
the real application run in one JVM, so breakpoints and stepping work normally across every layer's
classes, exactly as they already do with the framework's other multi-classloader mechanisms (OSGi
bundles) — the only visible difference is that the process's top-level main class is the bootstrap,
not the application's own, the same as e.g. a repackaged Spring Boot launcher.


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
[tentackle-update](https://tentackle.org/tentackle/tentackle-update/apidocs/org.tentackle.update/org/tentackle/update/package-summary.html).
Although made for Tentackle applications, it can be used as a blueprint to other applications
as well.

The UI part is implemented in [tentackle-fx-rdc-update](https://tentackle.org/tentackle/tentackle-fx-rdc-update/apidocs/org.tentackle.fx.rdc.update/org/tentackle/fx/rdc/update/package-summary.html),
which is based on [JavaFX](https://openjfx.io/) and the [Tentackle](https://tentackle.org)
framework. As such, it cannot be used by any other kind of application. 
But it's a good starting point for your own update service.

Again, for a working example, please see this [Quickstart](../../../../src/main/docs/quickstart.md).


### Further Reading

[Tentackle Modules](../../../../src/main/docs/modules.md) — how the framework itself is modularized.

[Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) — the SPI that makes
"unrequired" dependencies work.

[Isolated Module Layers](../../../../tentackle-common/src/main/docs/layers.md) — running two versions of
the same dependency in parallel, via `<layers>` and `org.tentackle.common.LayerBootstrap`.

[Java Platform Module System](https://en.wikipedia.org/wiki/Java_Platform_Module_System)

[jlink tool](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html)

[jpackage tool](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html)

[jdeps tool](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jdeps.html)
