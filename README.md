<!--
Tentackle - https://tentackle.org                                        
                                                                         
This library is free software; you can redistribute it and/or            
modify it under the terms of the GNU Lesser General Public               
License as published by the Free Software Foundation; either             
version 2.1 of the License, or (at your option) any later version.       
                                                                         
This library is distributed in the hope that it will be useful,          
but WITHOUT ANY WARRANTY; without even the implied warranty of           
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU        
Lesser General Public License for more details.                          
                                                                         
You should have received a copy of the GNU Lesser General Public         
License along with this library; if not, write to the Free Software      
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
-->

# Quickstart
This quickstart will create a typical modular Tentackle project, that serves as a starting point for your project.
It's for Unix/Linux, but works on Windows accordingly. The database used is PostgreSQL, but in principle
you can use any relational database (see the Tentackle docs for more info).

## Prerequisites
Make sure you're using Maven >= 3.6.0 and Java >= 12.
Verify with:
```
mvn --version
```

## Project Setup
Setup your multi-module maven project via:

```
mvn archetype:generate 
    -DarchetypeGroupId=org.tentackle 
    -DarchetypeArtifactId=tentackle-project-archetype 
    -DarchetypeVersion=12.0-SNAPSHOT
```
When all dependencies have been pulled from [Maven Central](https://search.maven.org/),
you will be prompted for:

* groupId: the group-ID of your project.
* artifactId: the root artifact-ID. All module artifacts will be prefixed by this name (ex. `myapp-common`).
* version: the project's version.
* package: the base java package.
* application: the application's name (single word!)

Example:
```
Define value for property 'groupId': com.example
Define value for property 'artifactId': myapp
Define value for property 'version' 1.0-SNAPSHOT: : 
Define value for property 'package' com.example: : com.example.myapp
Define value for property 'application': MyApp

```
When finished, the project will be located in a directory with the same name as the `artifactId`.
Change to that directory and build the project:
```
mvn -Pjlink clean install
```
Notice: the profile `jlink` instructs Maven to build the Jlink images as well.

## Database Setup
Log into postgres and create a user with the same name and password as the `artifactId`. 
Then, create a database with the same name, belonging to that user.

Example:
```
CREATE USER myapp WITH PASSWORD 'myapp';
CREATE DATABASE myapp OWNER myapp;
```
Back on the Unix shell, run the SQL script to create the tables:
```
psql -U myapp myapp < jlink/server/target/sql/PostgreSQL/createmodel.sql
```
Next, change to the server directory and populate the database:
```
cd myapp-server
mvn -Pinitdb test
```
This runs a special test, that persists some essential data, particularly users allowed to login.

## Run!
```
cd ..
jlink/server/target/jlink/bin/server.sh &
jlink/client/target/jlink/bin/client.sh
```
A login window appears. Login with user `gonzo` and password `gonzo`.

## Explore the with your favorite IDE
You can use any Java IDE that supports at least Java 12, JPMS (Jigsaw) and Maven. 
There are no special plugins necessary. However, not all IDEs provide support
for custom code folding regions, which are pretty cool to automatically collapse generated code.
IntelliJ and Netbeans work fine out of the box.
Others may need some extra plugins and configuration.

For more info, see the [Tentackle Website](https://tentackle.org)
