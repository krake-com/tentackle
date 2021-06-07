
# Quickstart
The steps below will create a mininum viable Tentackle project, 
that serves as a starting point for a larger project.
It contains a middle-tier server, a desktop FX client and a daemon client.
The numerous modules are the bare minimum to separate the different
abstraction layers like domain logic, persistence model, UI and so on.
Tentackle applications are full-fledged modular JPMS ([Jigsaw](https://openjdk.java.net/projects/jigsaw/quick-start)) projects,
including [Jlink](https://docs.oracle.com/en/java/javase/15/docs/specs/man/jlink.html) or 
[JPackage](https://docs.oracle.com/en/java/javase/15/docs/specs/man/jpackage.html) images and 
an auto-update feature for the clients.
By default, the server connects to a PostgreSQL database, but in principle
you can use any relational database (see the [Tentackle docs](https://tentackle.org) for more info).

## Prerequisites
* Java (JDK) >= 16
* Maven >= 3.6.3
* PostgreSQL >= 9

Verify with:
```
mvn --version
psql --version
```

## Project Setup
Generate your project via the Tentackle Maven archetype:

```
mvn archetype:generate \
    -DarchetypeGroupId=org.tentackle \
    -DarchetypeArtifactId=tentackle-project-archetype \
    -DarchetypeVersion=16.0-SNAPSHOT
```
When all dependencies have been pulled from [Maven Central](https://search.maven.org/),
you will be prompted for:

* **groupId**: the group-ID of your project.
* **artifactId**: the root artifact-ID. All module artifact-IDs will be prefixed by this name, separated by a hyphen (e.g. `myapp-gui`).
* **version**: the project's version.
* **package**: the Java base package.
* **application**: the application's name. It is used as a prefix in several Java classnames (i.e. no spaces, first letter in uppercase, etc...)
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
* The database must not exist yet. It is created in the **next** step!

## Database Setup
Log into postgres and create a user with the same name and password as the `artifactId`. 
Then, create a database with the same name, belonging to that user.

Example:
```
CREATE USER myapp WITH PASSWORD 'myapp';
CREATE DATABASE myapp OWNER myapp;
```
Back on the shell, run the SQL script to create the database tables:
```
psql -U myapp myapp < jlink/server/target/sql/PostgreSQL/createmodel.sql
```
Next, change to the server directory and populate the database:
```
cd myapp-server
mvn -Pinitdb test
```
This runs a special test, that persists some essential data, 
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
You can use any Java IDE that supports at least Java 15, JPMS (Jigsaw) and Maven. 
There are no special plugins necessary. However, not all IDEs provide support
for custom code folding regions, which are pretty nice to automatically collapse generated code.
[IntelliJ](https://www.jetbrains.com/idea/) and [Netbeans](https://netbeans.apache.org/) work fine out of the box.
Others may need some extra plugins or configuration.

## Next Steps
For more info, see the [Tentackle Website](https://tentackle.org)
