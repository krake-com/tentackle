# Quickstart
![Quickstart](./assets/tentackle-quickstart.png)


This chapter takes you from nothing to a running, multi-tier Tentackle
application in a few minutes. The generated project is a complete starting point
for a larger one: a middle-tier server, a JavaFX desktop client and a headless
daemon, split into the [JPMS modules](./modules.md) that keep domain logic,
persistence model and UI cleanly apart (see the [Multi-Tier Cascade](./tiers.md)
for how these tiers cooperate). Everything is a full
JPMS ([Jigsaw](https://openjdk.java.net/projects/jigsaw/quick-start)) build that
produces [jlink](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html) /
[jpackage](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html)
images with built-in client [auto-update](../../../tentackle-update/src/main/docs/update.md).
The server talks to PostgreSQL by default, but any relational database
supported by the [backend abstraction layer](../../../tentackle-sql/src/main/docs/sql.md) will do.

To understand the code you are about to generate, read about
[Persistent Domain Objects](../../../tentackle-pdo/src/main/docs/pdo.md) and the
guarantees behind them in [Correctness First](./correctness-first.md); for a
guided tour of the generated result, see the
[MyApp Walkthrough](../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md).

## Prerequisites
* Java (JDK) >= 25
* Maven >= 3.9.10 (Maven 4 works as well)
* PostgreSQL >= 9

Verify with:
```
mvn --version
psql --version
```

PostgreSQL is only needed to *run* the application. The generated unit tests run
against an in-memory [H2](https://h2database.com) database and require no setup
at all (see [tentackle-test-pdo](../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md)).

_Notice: for older LTS versions see the branches `java21`, `java17`, `java11`
and `java8`. The `java21` and `java17` lines still receive bugfixes; `java11`
and `java8` reached end-of-life._

## Project Setup
Generate your project via the Tentackle Maven archetype
(use the latest [release on Maven Central](https://central.sonatype.com/namespace/org.tentackle)
as the `archetypeVersion`):

```
mvn archetype:generate \
    -DarchetypeGroupId=org.tentackle \
    -DarchetypeArtifactId=tentackle-project-archetype \
    -DarchetypeVersion=
```
When all dependencies have been pulled from [Maven Central](https://central.sonatype.com/namespace/org.tentackle),
you will be asked for:

* **groupId**: the group-ID of your project.
* **artifactId**: the root artifact-ID. All module artifact-IDs will be prefixed by this name, separated by a hyphen (e.g. `myapp-gui`). It also becomes the default database name, user and password.
* **version**: the project's version.
* **package**: the Java base package.
* **application**: the application's name. It is used as a prefix in several Java classnames (i.e., no spaces, first letter in uppercase, etc...)
and in various other places, such as window titles and the TRIP service names.

Example:
```
Define value for property 'groupId': com.example
Define value for property 'artifactId': myapp
Define value for property 'version' 1.0-SNAPSHOT: : 
Define value for property 'package' com.example: : com.example.myapp
Define value for property 'application': MyApp

```
The generated project is located in a subfolder with the same name as the
`artifactId`. It is a Maven reactor of eight modules plus a `jlink`
sub-aggregator, mirroring [Tentackle's own layering](./modules.md):

```
myapp
├── myapp-common        shared constants, session info, domain context, preferences
├── myapp-pdo           PDO interfaces with the embedded model
├── myapp-domain        domain-logic implementations
├── myapp-persistence   persistence implementations + remote delegates
├── myapp-gui           JavaFX controllers, editors, FXML/CSS
├── myapp-client        the desktop FX client
├── myapp-server        the middle-tier server
├── myapp-daemon        a headless console client
└── jlink               jlink/jpackage images (profile "jlink" only)
```

It is not just a scaffold but a small **reference application** — users, user
groups and messages, with CRUD editors, login, security and i18n in English and
German. See the
[Tentackle Project Archetype](../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md)
for what each module contains and why.

Change to the project directory and build it:
```
cd myapp
mvn -Pjlink clean install
```

Notes:

* The profile `jlink` instructs Maven to build the
  [jlink and/or jpackage images](../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md)
  as well. Without it, the build is faster but produces plain jars only —
  the [Run](#run) section below assumes the images exist.
* The build runs the complete Tentackle pipeline: the
  [tentackle-maven-plugin](../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md)
  registers [services](../../../tentackle-common/src/main/docs/services.md), the
  [wurblets](../../../tentackle-persistence-wurblets/src/main/docs/persistence-wurblets.md)
  weave the generated persistence code into guarded source regions, and the
  [SQL plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md)
  derives the DDL from the [model](../../../tentackle-model/src/main/docs/model-definition.md)
  embedded in the PDO interfaces.

## Database Setup
Log into postgres and create a user with the same name and password as the `artifactId`.
Then, create a database with the same name belonging to that user.

Example:
```
CREATE USER myapp WITH PASSWORD 'myapp';
CREATE DATABASE myapp OWNER myapp;
```

These are just the defaults taken from the `dbUrl`, `dbUser` and `dbPasswd`
properties in the generated root `pom.xml` — change them there if you prefer
different credentials or a different [database backend](../../../tentackle-sql/src/main/docs/sql.md).

Back on the shell, run the SQL script to create the database tables:
```
psql -h localhost -U myapp myapp < jlink/server/target/sql/PostgreSQL/createmodel.sql
```
This script was **generated by the build**: the
[SQL plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md)
turned the model into backend-specific DDL. When you later change the model, the
plugin generates migration scripts the same way — the model is the single source
of truth for classes *and* schema.

Next, change to the server directory and populate the database:
```
cd myapp-server
mvn -Pinitdb test
```
This runs a special TestNG group (`initdb`, see `FillDatabase.java` in the
server's test sources) against the real database instead of the in-memory H2
used by ordinary tests. It creates a
[number pool](../../../tentackle-pdo/src/main/docs/ns.md) for message numbers,
the `ADMINS` user group, and three users:

| User     | Password | Purpose                                        |
|----------|----------|------------------------------------------------|
| `gonzo`  | `gonzo`  | interactive login, admin                       |
| `kermit` | `piggy`  | a second interactive user                      |
| `daemon` | `daemon` | the account the headless daemon connects with  |

Only users stored this way may open a session on the middle-tier server — the
example already enforces the [security model](../../../tentackle-pdo/src/main/docs/security.md).

## Run
Start the middle-tier server first, then the daemon and finally the FX desktop client:
```
cd ..
jlink/server/target/jlink/bin/server.sh &
jlink/daemon/target/jlink/bin/daemon.sh &
jlink/client/target/jlink/bin/client.sh
```
A login window appears. Login with user `gonzo` and password `gonzo`.

What is actually running now is a small [multi-tier cascade](./tiers.md):

* The **server** connects to PostgreSQL via JDBC and offers two
  [TRIP](../../../tentackle-core/src/main/docs/trip.md) services:
  the application service at `trips://localhost:8888/MyAppServer` and the
  [update service](../../../tentackle-update/src/main/docs/update.md) at
  `tripe://localhost:8890/MyAppUpdate`.
* The **client** and the **daemon** hold no database connection at all: they open
  remote sessions to the server's TRIP URL. The same code would run unchanged if
  they connected to the database directly — swap the `url` in the module's
  `backend.properties` and nothing else changes (that is the point of
  [location transparency](./tiers.md)).
* Each image reads its connection settings from a `backend.properties` inside
  the image; passwords in it are stored encrypted
  (see [Cryptor and EncryptedProperties](../../../tentackle-common/src/main/docs/cryptor.md)).

The daemon logs events like login/logout in `jlink/daemon/target/jlink/logs/daemon.log` —
it listens for new `Message` PDOs and demonstrates a headless
[ConsoleApplication](../../../tentackle-pdo/src/main/docs/application.md).
Take a look at the other logs in `jlink/server/target/jlink/logs` and
`jlink/client/target/jlink/logs`.

Notice: on Windows, the scripts are named `server.cmd`, `daemon.cmd` and `client.cmd`.

## Explore the code with your favorite IDE
You can use any Java IDE that supports at least Java 25, JPMS (Jigsaw) and Maven.
There are no special plugins necessary. However, not all IDEs provide support
for custom code folding regions, which are pretty nice to automatically collapse
the [wurblet-generated code](../../../tentackle-wurblets/src/main/docs/wurblets.md)
woven into the sources.
[IntelliJ](https://www.jetbrains.com/idea/) and [Netbeans](https://netbeans.apache.org/) work fine out of the box.
Others may need some extra plugins or configuration.

A good place to start reading is the `User` interface in `myapp-pdo`: the
[model definition](../../../tentackle-model/src/main/docs/model-definition.md)
lives in its comment blocks, and the
[MyApp Walkthrough](../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md)
follows it through all tiers, from the database row to the editor window.

## Grow the Project
You rarely write a new entity by hand. The generated project pre-configures the
[wizard plugin](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md),
which interactively scaffolds new PDOs and operations consistent with the
example code — including class-id ranges and package conventions for the
`masterdata` and `transactiondata` profiles:

```
mvn tentackle-wizard:pdo         # create a new entity
mvn tentackle-wizard:operation   # create a new operation
```

After the wizard has generated the sources, rebuild: the wurblets fill in the
persistence code and the SQL plugin produces the DDL (or a
[migration script](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md)
for existing databases).

## The First Operation: JPMS Does Not Come for Free

There is one trap on the way to the first *operation*. The generated root `pom.xml` already
configures an `operation` profile for the wizard, but the packages it names do not exist yet —
the project simply has no operation:

| Profile element                                             | Package                       | Module              |
|-------------------------------------------------------------|-------------------------------|---------------------|
| `operationPackage`, `domainPackage`, `persistencePackage`   | `com.example.myapp.pdo.operation`     | `myapp-pdo`         |
| `domainImplPackage`                                         | `com.example.myapp.domain.operation`  | `myapp-domain`      |
| `persistenceImplPackage`                                    | `com.example.myapp.persist.operation` | `myapp-persistence` |

The wizard decides which module a generated file belongs to by **looking for the package's
directory** in the compile source roots of the reactor. A package without a directory belongs to
no module, so the very first run fails:

```
mvn tentackle-wizard:operation
...
[ERROR] cannot determine module for package com.example.myapp.pdo.operation
```

And you cannot get ahead of it by declaring the packages in the `module-info.java` files first:
javac rejects an `exports` for a package that holds no compilation unit —
`package is empty or does not exist` — and a lone `package-info.java` does not make it non-empty
either. The directories must exist *before* the wizard runs, the `exports` can only be added
*after* it has written the sources. That is JPMS being strict, not the wizard being awkward.

So, once per project:

**1. Create the three directories.** Empty is enough — the wizard accepts an empty package, and
files whose name starts with a dot are ignored, so a `.gitignore` placeholder to make git keep the
directory stays invisible to it:

```
mkdir -p myapp-pdo/src/main/java/com/example/myapp/pdo/operation \
         myapp-domain/src/main/java/com/example/myapp/domain/operation \
         myapp-persistence/src/main/java/com/example/myapp/persist/operation
```

Create each package in **exactly one** module. The same empty package in two modules is a split
package, and the goal aborts with `empty split package detected`.

**2. Run the wizard** from the reactor root (it is an aggregator goal and must see every module):

```
mvn tentackle-wizard:operation
```

**3. Add the `exports`** to the `module-info.java` of the three modules — possible now that the
packages contain sources. At least `exports com.example.myapp.pdo.operation;` in `myapp-pdo` is
required; without it the domain and persistence implementations do not compile
(*"package com.example.myapp.pdo.operation is not visible"*).

**4. Build.** `mvn install`. If the operation is remotable, the
[remote delegate](../../../tentackle-core/src/main/docs/trip.md) pair
`com.example.myapp.persist.operation.trip.<Name>RemoteDelegate` / `…RemoteDelegateImpl` is written by
the wurblets on the first build in which the operation's persistence implementation carries a
remote-capable wurblet — an explicit `// @wurblet <tag> AssertRemote` or the `RemoteMethod` blocks
the operation needs anyway. That creates a fourth new package; export it from `myapp-persistence`
too, mirroring the `…persist.md.trip` and `…persist.td.trip` entries already there.

From the second operation onward none of this applies — the packages exist and are exported. The
same one-time sequence is needed whenever you add a **new profile** to the wizard configuration, for
PDOs just as for operations; the `masterdata` and `transactiondata` profiles work out of the box
only because the archetype ships their packages, populated with the example entities. See
[How the Wizard Places Files](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md#how-the-wizard-places-files-the-model-and-profiles)
for the profile elements, and [Tentackle Modules](./modules.md) for the module layout as a whole.

## Further Reading
* [Tentackle Project Archetype](../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md) — what the archetype scaffolds and how each generated module fits together.
* [MyApp Walkthrough](../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md) — a guided tour through the generated application's code, tier by tier.
* [Application Bootstrap](../../../tentackle-pdo/src/main/docs/application.md) — from `main()` to a running tier.
* [Table of Contents](./toc.md) — the full documentation map.
* [Index](./index.md) — look up a concept and jump straight to the document that covers it.
* [Tentackle Website](https://tentackle.org) — releases, API docs and background articles.
