---
name: tentackle-pdo-browser
description: Generate Java fixture code from PDOs persisted in a database, headless, with "mvn tentackle-wizard:browse -Dpdo=Entity[id,id] -Doutput=file". Use whenever the task is to build test data or an integration-test fixture from records that already exist, to reproduce a bug from a real object, or when asked how to turn a database row into the Java code that recreates it.
---

# Generating fixture code with the PDO browser

`tentackle-wizard:browse` reads PDOs from a live backend and writes the Java code that recreates
them — the `on(…)` call, the attribute setters, the components and the collections, ending in `save()`.
It is the fastest way to turn real records into integration-test data instead of hand-writing fixtures.

Interactively it is the PDO Browser, a JavaFX window. **Batch mode runs headless and needs to know which
objects you want**, by entity name and object id.

## Before you start

- The project model must have been generated at least once (`mvn -DskipTests generate-sources`), and the
  application's classes must be on the plugin classpath — in a configured project they are.
- Run from the **reactor root**; the goal is an aggregator.
- A **reachable backend** is required. `url`, `user` and `password` are plugin parameters and are
  normally already configured in the POM. Check which database that is: the goal connects to a *live*
  backend, so it should be a development or staging one.
- The goal **only reads**. It never writes to the database.

## How to use it

Batch mode is switched on by supplying **both** `-Dpdo` and `-Doutput`:

```bash
mvn tentackle-wizard:browse \
    -Dpdo=Customer[17878,221221,333] \
    -Doutput=target/fixtures/Customers.java
```

- `-Dpdo=<entity>[<id>,<id>,…]` — the entity's **simple name** and one or more **object ids**.
- `-Doutput=<file>` — the file to write; parent directories are created.
- `-Ddistinct` — number the variable names instead of reusing one per entity for the 2nd, 3rd, …
  instance. With distinct variables the generated code ends in `persist()` rather than `save()`, so
  every object stays reachable under its own name. Use this when the fixture has to reference the
  objects afterwards.
- `-Doffset=<n>` — the number the variable names start at when `-Ddistinct` is set. Without it the first
  instance is left unnumbered.
- `-DmaxLines=<n>` — cap the number of lines emitted in multi-line string literals.

The output starts with a comment recording the generation time, the user, the host and the session, so
a fixture always says where it came from. Paste it into a test base class or an `@BeforeClass` fixture
method; see the `tentackle-test-pdo` documentation for where it belongs.

### Finding the ids

`-Dpdo` wants **object ids** — the `id` column — not domain keys and not order numbers. Get them from a
`SELECT`, or from a `tentackle-wizard:script` run that prints them:

```groovy
on(Customer.class).selectAll().each { println "${it.getId()}\t${it.getName()}" }
```

## Things that will trip you up

- **A wrong entity name fails silently.** The name is matched against the simple class names of the
  registered PDOs; if nothing matches, the goal writes a file containing only the header comment and
  the build succeeds. Always check that the output file actually holds code.
- **A wrong id fails loudly.** An id that is not in the database aborts the goal with
  `entity … with ID … not found in database`. That asymmetry is worth remembering: silence means the
  entity name, an error means the ids.
- **One entity per run.** `-Dpdo` takes a single entity with its ids. Fixtures spanning several entities
  need several runs into several files — or one run per entity plus manual assembly.
- **Batch mode has no code-path configuration.** The check boxes the interactive browser offers fall
  back to their defaults: attributes and relations carrying the `NOTEST` stereotype are **excluded**,
  multi-line string attributes and N:M relation lists are **included**. If a fixture must omit
  something, mark it `NOTEST` in the model or edit the generated code.
- **The generated code recreates, it does not update.** It builds new objects and saves them; ids and
  serials are not carried over. Referenced master data is created along the way, which is usually what
  you want and occasionally more than you want.
- **It is the Java view, not the SQL view.** Values are read through the PDO's getters, so enums,
  `Money`, `Timestamp` and application-specific data types appear as Java values, not as their column
  representation. Do not expect the generated code to mirror the table.
- **Reproducing a bug is a legitimate use.** Generating the fixture for the object that misbehaves, then
  running the failing code against it in a test, is usually faster than reasoning about the data.
