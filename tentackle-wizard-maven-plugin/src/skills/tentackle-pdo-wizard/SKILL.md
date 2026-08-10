---
name: tentackle-pdo-wizard
description: Create the Java source files for new Tentackle entities headless with "mvn tentackle-wizard:pdo -DbatchDir=...". Use whenever the task is to add one or more new entities or PDOs to the application, to bootstrap entities from an existing database schema or a dumped model, or when asked where the PDO interface, the domain and persistence interfaces and their implementations for a new entity come from.
---

# Creating entities with the PDO wizard

`tentackle-wizard:pdo` writes the source files a new PDO needs: the PDO interface carrying the model
block, the domain interface, the persistence interface, and their two implementations — each into the module that owns the package, as derived from the selected profile.

Interactively it is a JavaFX dialog you cannot use. **Batch mode replaces the dialog with a directory of
model definitions** and runs headless: every entity in that directory that does not yet exist in the
project model is generated.

## Before you start

1. The project model must have been generated at least once, otherwise the wizard cannot tell which
   entities, class ids and table names are taken:

   ```bash
   mvn -DskipTests generate-sources
   ```

2. Run from the **reactor root**. The goal is an aggregator and must see every module to place files.
3. At least one `PdoProfile` must be configured for the plugin in the POM. Read it before writing
   anything — it defines the target packages, the super types, the class-id range (`minClassId` /
   `maxClassId`) and the table prefix for each group of entities. A project typically has one profile
   for master data and one for transaction data.

## How to use it

Write one file per entity, then run the goal:

```bash
mvn tentackle-wizard:pdo -DbatchDir=src/model/new
```

`-DbatchDir` takes a single directory; every file in it is an input file. The POM parameter
`batchFilesets` is the same thing with several file sets and include/exclude patterns.

### The input format

Plain model format — the text the Wurbelizer reads from its here-docs. **No `@> … @<` wrapper and no
`$classname`-style variables**, just the sections:

```
# a note attached to a customer
name := Note
id := 2101
table := td.note
alias := note
integrity := full

## attributes
[#TRANSACTIONDATA]
String(80)   subject     subject    the note's subject
String       text        text       the note's body
Timestamp    created     created    when the note was written

## indexes
index := created

## relations
Customer:
 relation = object,
 delete = restrict
```

The generated PDO interface will contain this definition rendered back as a `@> … @<` comment block
with the `$classname`, `$classid` and `$tablename` variables restored — that is the project convention,
and it is not a sign that something was lost.

### The order that works

1. **Look at a comparable entity first** and match its conventions — naming, options, integrity —
   rather than inventing new ones.
2. **Pick a free class id inside the profile's range.** A duplicate class id is the single most common
   mistake, and batch mode has none of the live validation the dialog does.
3. **Validate the definition before generating.** If the `tentackle-model` MCP server is available, call
   `model_validate` with the definition as `source`; it parses and relation-resolves the candidate
   against the whole model in under a second. Otherwise the first feedback you get is a
   `WurbelTerminationException` after a full reactor build.
4. **Select the profile by stereotype.** With more than one `PdoProfile` configured, each entity must
   name its profile in the global options line, case-insensitively: `[#TRANSACTIONDATA]` selects the
   profile named `transactiondata`. Entities matching no profile are skipped.
5. Run the goal, then **read the log** (see below).
6. Fill in the business logic, run `mvn install` so the wurblets generate the persistence and domain
   layers and the SQL plugin the DDL, then call `model_reload` if an MCP server is running.

## Things that will trip you up

- **The goal succeeds even when it generates nothing.** An entity that already exists is skipped with a
  *warning*, an entity matching no profile with an *error*, and a failing template with an *error* — the
  build stays green in all three cases. Never assume it worked: check the log for `generating code for
  entity …` and check that the files exist.
- **Re-running overwrites without asking.** An entity counts as "already in the model" only after a
  build has regenerated the `.map` files. Between generation and that build, running the goal again on
  the same input regenerates the same files from the templates and destroys any business logic written
  into them in the meantime. Generate once, then build.
- **The table name is taken verbatim.** The profile's `tablePrefix` is applied by the interactive
  dialog, not in batch mode. Write the fully qualified table name into the definition, schema and all
  (`td.note`, not `note`).
- **`batchDefaults` are not the project's model defaults.** The batch model is loaded with
  `!ROOT, !ROOTID, !ROOTCLASSID, !BIND, UNTRACKED` (`-DbatchDefaults=…` to change), because a dumped
  model usually has the project defaults baked in already and applying them twice produces entity
  options nobody wants. Options implied by the project defaults do not belong in the definition either.
- **Profile packages must already exist** and each must map to exactly one module of the reactor, or the
  goal fails outright. Do not invent a new package in a profile.
- **Class ids are tracked under `target`.** The last id allocated per profile is remembered in
  `target/wizard/<profile>.classid` so several entities generated before a rebuild do not collide.
  `mvn clean` drops it — after a clean, re-check the ids yourself.
- **This goal never touches a database.** It reads model text and writes Java files. If the task needs
  data, that is the `browse` or `script` goal.
