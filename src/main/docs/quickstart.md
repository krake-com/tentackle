# Quickstart

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
The server talks to PostgreSQL by default, but any relational database will do.

To understand the code you are about to generate, read about
[Persistent Domain Objects](../../../tentackle-pdo/src/main/docs/pdo.md) and the
guarantees behind them in [Correctness First](./correctness-first.md); for a
guided tour of the generated result, see the
[MyApp Walkthrough](../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md).

## Prerequisites
* Java (JDK) >= 25
* Maven >= 3.9.0
* PostgreSQL >= 9

Verify with:
```
mvn --version
psql --version
```
_Notice: for older LTS versions see the branches java17, java11 and java8.
Java17 still receives bugfixes. Java11 and java8 reached end-of-life._

## Project Setup
Generate your project via the Tentackle Maven archetype:

```
mvn archetype:generate \
    -DarchetypeGroupId=org.tentackle \
    -DarchetypeArtifactId=tentackle-project-archetype \
    -DarchetypeVersion=25.18.3.0
```
When all dependencies have been pulled from [Maven Central](https://search.maven.org/search?q=org.tentackle),
you will be asked for:

* **groupId**: the group-ID of your project.
* **artifactId**: the root artifact-ID. All module artifact-IDs will be prefixed by this name, separated by a hyphen (e.g. `myapp-gui`).
* **version**: the project's version.
* **package**: the Java base package.
* **application**: the application's name. It is used as a prefix in several Java classnames (i.e., no spaces, first letter in uppercase, etc...)
and in various other places.

Example:
```
Define value for property 'groupId': com.example
Define value for property 'artifactId': myapp
Define value for property 'version' 1.0-SNAPSHOT: : 
Define value for property 'package' com.example: : com.example.myapp
Define value for property 'application': MyApp

```
The generated project is located in a subfolder with the same name as the `artifactId`.
Change to that directory and build the project:
```
cd myapp
mvn -Pjlink clean install
```

Notes:

* The profile `jlink` instructs Maven to build the Jlink and/or JPackage images as well.

## Database Setup
Log into postgres and create a user with the same name and password as the `artifactId`. 
Then, create a database with the same name belonging to that user.

Example:
```
CREATE USER myapp WITH PASSWORD 'myapp';
CREATE DATABASE myapp OWNER myapp;
```
Back on the shell, run the SQL script to create the database tables:
```
psql -h localhost -U myapp myapp < jlink/server/target/sql/PostgreSQL/createmodel.sql
```
Next, change to the server directory and populate the database:
```
cd myapp-server
mvn -Pinitdb test
```
This runs a special test that persists some essential data, 
particularly users allowed to connect to the middle-tier server.

## Run
Start the middle-tier server first, then the daemon and finally the FX desktop client:
```
cd ..
jlink/server/target/jlink/bin/server.sh &
jlink/daemon/target/jlink/bin/daemon.sh &
jlink/client/target/jlink/bin/client.sh
```
A login window appears. Login with user `gonzo` and password `gonzo`.

The daemon just logs events like login/logout in `jlink/daemon/target/jlink/logs/daemon.log`.
Take a look at the other logs in `jlink/server/target/jlink/logs` and 
`jlink/client/target/jlink/logs`.

Notice: on Windows, the scripts are named `server.cmd`, `daemon.cmd` and `client.cmd`.

## Explore the code with your favorite IDE
You can use any Java IDE that supports at least Java 21, JPMS (Jigsaw) and Maven. 
There are no special plugins necessary. However, not all IDEs provide support
for custom code folding regions, which are pretty nice to automatically collapse generated code.
[IntelliJ](https://www.jetbrains.com/idea/) and [Netbeans](https://netbeans.apache.org/) work fine out of the box.
Others may need some extra plugins or configuration.

## Further Reading
* [Tentackle Project Archetype](../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md) — what the archetype scaffolds and how each generated module fits together.
* [MyApp Walkthrough](../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md) — a guided tour through the generated application's code, tier by tier.
* [Table of Contents](./toc.md) — the full documentation map.
* [Index](./index.md) — look up a concept and jump straight to the document that covers it.
* [Tentackle Website](https://tentackle.org) — releases, API docs and background articles.
