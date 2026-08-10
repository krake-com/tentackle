# AI Agent Tooling Support

Tentackle ships tooling that lets a coding agent work on a Tentackle application the way a developer
does: inspect the entity model as the code generators see it, scaffold new
[PDOs](../../../tentackle-pdo/src/main/docs/pdo.md), turn persisted records into Java code, and run
real application code against a live backend.

This document is the map. It explains *which* tool answers *which* question, how the pieces fit into
one workflow, and what an agent must know before using them. The details of each tool live in its own
document, linked from every section.

## Why Separate Tooling at All

An agent dropped into a Tentackle project hits three walls that grepping does not get past.

**The model is not where it looks.** The application's data model is written in a DSL inside a
`@> … @<` comment block in the PDO interface — with `$classname`, `$classid` and `$mapping` still
unexpanded. The usable form, the `.map` files under `target/wurbel/model`, only exists after a build.

**Most of the model is derived.** Referencing relations, composite paths, the root entity of a
component, the inferred `root`/`rootId`/`rootClassId` options, resolved data types and backend-specific
column names exist only after the whole model has been loaded and its relations resolved. No file
contains them.

**The interactive tools are GUIs.** The PDO wizard, the operation wizard, the PDO browser and the
script editor are JavaFX applications. An agent cannot click through them.

The answer to the first two is the [MCP server](#analyzing-the-model-the-mcp-server); the answer to
the third is that the `pdo`, `browse` and `script` goals of the
[wizard plugin](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md)
all have a **batch mode** that runs headless and is driven entirely from the command line.

## The Four Capabilities at a Glance

| Task                                                | Tool                                                  | Mode                             | Touches the database |
|-----------------------------------------------------|--------------------------------------------------------|-----------------------------------|----------------------|
| Understand and validate the entity model            | `tentackle-mcp:serve`                                  | long-running MCP server           | no                   |
| Create the source files for new entities            | `tentackle-wizard:pdo -DbatchDir=…`                    | headless, one-shot                | no                   |
| Turn persisted records into Java fixture code       | `tentackle-wizard:browse -Dpdo=… -Doutput=…`           | headless, one-shot                | reads                |
| Run application code against a backend              | `tentackle-wizard:script -Dscript=…`                   | headless, one-shot                | whatever the script does |

The first is read-only and can be left running all day. The second writes Java sources into the
reactor. The third reads from a backend and writes one file. The fourth runs arbitrary code with the
full domain model at hand — it is the only one that can change data.

## Prerequisites

- JDK >= 25 and Maven >= 3.9.0, like the rest of the framework.
- **The model must have been generated at least once.** Everything below reads the `.map` files that
  the Wurbelizer produces:

  ```bash
  mvn -DskipTests generate-sources
  ```

- All goals are **aggregator goals**: run them from the reactor root, so the whole model is visible and
  generated files land in the right modules.
- `browse` and `script` additionally need a reachable backend (database or remote server).

Projects generated from the
[project archetype](../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md)
come with both plugins configured — the MCP plugin with the project's `tentackle.modelDefaults` and
backends, the wizard plugin with the PDO/operation profiles and the backend credentials — so the
commands below work as written.

---

## Analyzing the Model: the MCP Server

`tentackle-mcp-maven-plugin` runs an [MCP](https://modelcontextprotocol.io/) server that holds the
model **loaded and relation-resolved**, exactly as the code generators see it, and exposes it over
Streamable HTTP. See
[The Tentackle MCP Maven Plugin](../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md)
for the full reference.

```bash
mvn tentackle-mcp:serve
```

It logs the endpoint and blocks:

```
[INFO] 42 entities loaded from /home/me/myapp/target/wurbel/model
[INFO] MCP endpoint: http://localhost:8770/mcp
[INFO] claude mcp add --transport http tentackle-model http://localhost:8770/mcp
[INFO] stop with Ctrl-C
```

Register it once with the agent's client — or commit an `.mcp.json` in the project root so the whole
team gets it:

```json
{
  "mcpServers": {
    "tentackle-model": {
      "type": "http",
      "url": "http://localhost:8770/mcp"
    }
  }
}
```

The server is **read-only**: it writes no file, opens no database connection and loads no application
class. It re-scans the model files before each call, so a rebuild in another terminal is picked up
without a restart.

### What It Answers

| Tool                    | Question                                                                                       |
|-------------------------|-------------------------------------------------------------------------------------------------|
| `model_overview`        | What is loaded: sources, backends, effective model defaults, inheritance hierarchies, aggregate roots, configuration warnings. **The entry point.** |
| `model_list_entities`   | The entity index, narrowed by name pattern or by filter (composite, abstract, root, embedded, cached, …). |
| `model_find`            | Where does this name occur? Searches entity, table, attribute, column, relation and index names.  |
| `model_describe_entity` | One entity — canonical definition, attributes, relations, indexes, inheritance, aggregate, references, embedding, unique domain key, DDL. |
| `model_source`          | The model definition as text, `raw`, `canonical` or `compact`, optionally as a `@> … @<` block.    |
| `model_paths`           | How two entities are connected; direct neighbors; composite and embedding paths.                  |
| `model_ddl`             | The `CREATE TABLE` / `CREATE INDEX` / foreign key statements for a backend. Text only.            |
| `model_validate`        | Is the model consistent — or would *this draft definition* be? See below.                         |
| `model_reload`          | Refresh after a build; `full` after an entity was deleted or renamed.                             |

Every entity argument accepts the entity name, `table:<tableName>` or `id:<classId>`, because all three
turn up in the sources an agent is reading.

The grammar itself is served as a resource, `tentackle-model://grammar/model-definition`, so an agent
never has to guess the
[DSL](../../../tentackle-model/src/main/docs/model-definition.md).

### The Validation Loop

`model_validate` is what turns model authoring from a build-cycle game into a sub-second loop. Passed a
`source`, it stages the **candidate** definition into a scratch copy of the model, resolves relations
across the whole set and runs the custom validators — replacing an existing entity if `entity` is
given, adding a new one otherwise. Nothing is written and the live model is untouched:

```
model_validate {"source": "name := Note\ntable := note\nid := 9001\n…"}

candidate (new entity) is NOT consistent:

duplicate entity id 9001 for Note, already assigned to Customer
```

> **Always configure `modelDefaults`** in the plugin configuration. They come from the project's
> property, not from the model sources; without them the server loads a model that differs from what
> the build sees and reports integrity errors that do not exist in the build. It says so in every
> overview and validation result rather than defaulting silently — a warning there is worth acting on.

A ready-made agent skill for the server, `tentackle-model`, ships with the plugin — see
[The Bundled Skills](#the-bundled-skills) below.

---

## Creating Entities: `tentackle-wizard:pdo` in Batch Mode

The `pdo` goal creates the whole set of source files for a new PDO — the PDO interface carrying the
model comment block, the domain and persistence interfaces, their implementations, and the remote
interface and implementation if remoting is enabled — each written into the module that owns its
package.

Interactively that means a JavaFX dialog. **Batch mode replaces the dialog with a directory of model
definitions**: the goal reads a secondary model from those files and generates the code for every
entity in it that does not yet exist in the project model.

```bash
mvn tentackle-wizard:pdo -DbatchDir=src/model/new
```

`-DbatchDir` is the command-line form and takes a single directory, every file in which is treated as
input. The POM equivalent is `batchFilesets`, which takes several file sets and the usual
includes/excludes; directories default to the project base directory.

### The Input Format

The files are in **plain model format** — the same text the Wurbelizer reads from its here-docs, and
the same the [SQL plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md)
writes with `dumpAsComment` and `dumpVariables` both `false`. That is: no `@> … @<` wrapper and no
`$classname`-style variables, just the sections themselves:

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

Two things make this work in an agent's hands:

- **The class id must be free.** Confirm it with `model_list_entities` first — a duplicate class id is
  the single most common mistake, and the wizard's live validation is exactly what batch mode does not
  give you.
- **Validate before generating.** Run each candidate through `model_validate` on the MCP server. The
  parse and consistency errors it reports in under a second are the ones that would otherwise surface
  as a `WurbelTerminationException` after a full reactor build.

### Profile Selection

A [profile](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md#how-the-wizard-places-files-the-model-and-profiles)
tells the wizard the target packages, the super types and the class-id range for a class of entities —
typically one per group, such as master data and transaction data. If more than one `PdoProfile` is
configured, **each entity must name its profile as a stereotype** in the global options line,
case-insensitively: `[#TRANSACTIONDATA]` selects the profile named `transactiondata`. An entity that
matches no profile is skipped with an error; an entity that already exists in the project model is
skipped with a warning.

At least one `PdoProfile` must be configured, or the goal fails.

### Batch-Specific Parameters

| Parameter                  | Property         | Purpose                                                                                    |
|----------------------------|------------------|---------------------------------------------------------------------------------------------|
| `batchDir`                 | `batchDir`       | The single input directory. Command-line form of `batchFilesets`.                            |
| `batchFilesets`            | —                | Input file sets, configured in the POM.                                                      |
| `batchModelDefaults`       | `batchDefaults`  | Model defaults applied to the *batch* model. Defaults to `!ROOT, !ROOTID, !ROOTCLASSID, !BIND, UNTRACKED` — a dumped model usually has the project defaults baked in already, so they must not be applied twice. |
| `dumpColumnGap`            | —                | Minimum spaces between columns in the generated attribute section. Defaults to `2`.          |
| `dumpAnnotationsAsOptions` | —                | Which annotations to render as attribute options, e.g. `@NotNull|, @NotZero`.                |

Class ids are still managed: the goal scans the model for the highest id used per profile, records the
last allocated one under `${project.build.directory}/wizard/<profile>.classid`, and warns when a
profile runs out.

### Afterwards

The generated files are scaffolding. The model block is filled in, but the business logic is not — that
is the part a human or an agent writes next. Then run the build, which drives
`tentackle:analyze` → Wurbelizer → compiler and produces the persistence mappings and the SQL. Once
the build is through, call `model_reload` so the MCP server sees the new entities.

---

## Java Code From the Database: `tentackle-wizard:browse` in Batch Mode

The `browse` goal generates **Java fixture code from persisted PDOs** — the `on(…)` call, the attribute
setters, the components and collections, and the final `save()` that recreates the object. It is the
fastest way to build realistic integration-test data out of records that already exist; see
[tentackle-test-pdo](../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md#generating-fixture-code-with-the-pdo-browser)
for how the generated code feeds into the test base classes.

Interactively it is the PDO Browser, a JavaFX window. Batch mode needs **both** `-Dpdo` and `-Doutput`,
and then runs headless:

```bash
mvn tentackle-wizard:browse \
    -Dpdo=Customer[17878,221221,333] \
    -Doutput=target/fixtures/Customers.java
```

- `-Dpdo=<entity>[<id>,<id>,…]` — the entity name and the object ids to generate for. If any id is not
  found in the database, the goal fails.
- `-Doutput=<file>` — the file to write; parent directories are created.
- `-Ddistinct` — number the variable names instead of reusing them for the 2nd, 3rd, … instance. With
  distinct variables the generated code ends in `persist()` rather than `save()`, so every object stays
  reachable under its own name.
- `-Doffset=<n>` — the number the variable names start at when `distinct` is set.
- `-DmaxLines=<n>` — cap the number of lines emitted in multi-line string literals.

The output is prefixed with a comment recording the generation time, the user, the host and the
session, so a fixture always says where it came from.

The backend `url`, `user` and `password` are plugin parameters and normally already configured in the
POM. **The goal only reads** — it never writes to the database — but it does connect to a *live*
backend, so point it at development or staging rather than production.

Batch mode has no path configuration: the code paths that the interactive browser offers as check boxes
(attributes carrying the `NOTEST` stereotype, multi-line strings, N:M relation lists) fall back to their
defaults. When an agent needs to know *which* objects to name in `-Dpdo`, the ids usually come from a
`SELECT` — or from a `script` run, below.

---

## Running Application Code: `tentackle-wizard:script` in Batch Mode

The `script` goal runs a [Tentackle script](../../../tentackle-core/src/main/docs/scripting.md) against
a live backend with the application's dependencies on the classpath, so the script can use `on()` and
`op()` exactly like application code. It is the tool for ad-hoc queries, one-off data fixes,
migrations, and for *checking what the application actually does* rather than what the sources suggest.

```bash
mvn tentackle-wizard:script -Dscript=src/scripts/list-open-orders.groovy
```

Supplying `-Dscript=<file>` switches the goal to batch mode: it reads the fragment from disk, weaves it,
runs it once, and fails the build with the script error if compilation or execution fails. Without it,
the interactive editor opens instead.

### Fragments and Harnesses

The code you write is only a **fragment**. It is woven into a **harness**: a FreeMarker template that
owns the boilerplate — it implements a `DomainContextProvider` so `on()` and `op()` resolve, and defines
the `run()` method the fragment is inserted into. The **first line of a harness is a she-bang selecting
the scripting language** (`#!groovy`, `#!ruby`, …); it is stripped and does not land in the generated
script.

Harnesses live in the `script/` category of the template directory (`${project.basedir}/templates` by
default); `tentackle-wizard:init` installs a `groovy-example.ftl` to start from. `-Dharness=<filename>`
selects one; it may be omitted when exactly one is configured.

Because the fragment lands *inside* `run()`, it cannot contain import statements. Instead, import lines
are marked with a leading `^`, which the weaver strips and hands to the harness through the `imports`
variable so they end up at the top level of the generated script. The mechanism is language-agnostic —
in a Ruby fragment `^require 'json'` becomes a top-level `require 'json'`.

```groovy
^import org.tentackle.misc.*
^import java.time.LocalDate

def cutoff = new Timestamp(LocalDate.now().minusDays(30))
on(Order.class).selectAll().each { order ->
  if (order.getCreated().before(cutoff) && order.isOpen()) {
    println "${order.getId()}\t${order.getOrderNumber()}\t${order.getCustomer().getName()}"
  }
}
```

FreeMarker exposes four variables to the harness: `content` (the fragment), `imports`, `language` and
`harness` (the harness filename).

### What an Agent Should Know

- **The scripting-language provider must be on the plugin classpath** — add `tentackle-script-groovy`
  (or `-script-ruby`, or `-script-jsr`) as a `<dependency>` of the wizard plugin. The archetype does not
  add one by default.
- **This is the one capability that can change data.** The script runs with the credentials configured
  for the plugin and does whatever it says. Treat a write script the way you would treat a migration:
  read it, know the target backend, and prefer a development database.
- **Reading is a legitimate use and often the best one.** A script that prints ids, counts rows or walks
  an aggregate answers questions the model alone cannot — and its output is exactly what `-Dpdo` needs.
- Batch mode is non-interactive: there is no *Interrupt* button, so guard long loops yourself.

---

## The Bundled Skills

Each of the four capabilities ships with a ready-made **agent skill** — a `SKILL.md` describing when to
reach for the tool, the order of steps that works, and the failure modes that are specific to running it
headless:

| Skill                     | Lives in                                                     | Covers                          |
|---------------------------|--------------------------------------------------------------|---------------------------------|
| `tentackle-model`         | `tentackle-mcp-maven-plugin/src/skills/`                     | `tentackle-mcp:serve`           |
| `tentackle-pdo-wizard`    | `tentackle-wizard-maven-plugin/src/skills/`                  | `tentackle-wizard:pdo`          |
| `tentackle-pdo-browser`   | `tentackle-wizard-maven-plugin/src/skills/`                  | `tentackle-wizard:browse`       |
| `tentackle-script-runner` | `tentackle-wizard-maven-plugin/src/skills/`                  | `tentackle-wizard:script`       |

They are **templates**, not something the build installs. Copy the ones you want into your project and
adapt them — the profile names, the target backend and the harnesses are yours, not the framework's:

```bash
mkdir -p .claude/skills
cp -r path/to/tentackle-mcp-maven-plugin/src/skills/* .claude/skills/
cp -r path/to/tentackle-wizard-maven-plugin/src/skills/* .claude/skills/
```

Agents without skill support can be pointed at the same files as plain instructions — in `AGENTS.md`, in
`CLAUDE.md`, or in whatever the tool calls its project context. The skills cross-reference each other in
the order the workflow below uses them.

## Putting It Together

A realistic end-to-end run, adding an entity to an existing application:

1. **Start the server** — `mvn tentackle-mcp:serve` in another terminal, once. Register it with the
   agent's MCP client.
2. **Orient** — `model_overview` for the project's model defaults and integrity mode, then
   `model_describe_entity` on one or two comparable entities to match their conventions instead of
   inventing new ones.
3. **Check the connections** — `model_paths` before adding a relation; the link often exists already.
4. **Pick a free class id** — `model_list_entities`.
5. **Draft and validate** — write the definition, run `model_validate` with it as `source`, fix, repeat
   until it says the candidate is consistent. Nothing has been written yet.
6. **Generate** — drop the validated definition into a batch directory and run
   `mvn tentackle-wizard:pdo -DbatchDir=…`. Add the profile stereotype if the project has more than one
   profile.
7. **Build** — `mvn install`, so the wurblets generate the persistence and domain layers and the SQL
   plugin the DDL. Then `model_reload` on the server.
8. **Write the logic**, then verify against a real backend with
   `mvn tentackle-wizard:script -Dscript=…`.
9. **Build fixtures** — once records exist, `mvn tentackle-wizard:browse -Dpdo=… -Doutput=…` turns them
   into test code.

Steps 2–5 are the loop that pays for itself: they replace "write a model block, run the reactor, read
the stack trace" with a sub-second answer.

## Boundaries Worth Stating

| Tool                     | Writes files                         | Touches the database        |
|--------------------------|--------------------------------------|------------------------------|
| `tentackle-mcp:serve`    | never                                | never connects               |
| `tentackle-wizard:pdo`   | Java sources across the reactor      | never connects               |
| `tentackle-wizard:browse`| one output file                      | reads only                   |
| `tentackle-wizard:script`| whatever the script writes           | whatever the script does     |

Two more notes:

- **The MCP server has no authentication** and binds to `localhost` by default. It exposes your data
  *model*, not your data — keep it that way unless you know what you are doing.
- **The `.java` source and the MCP server disagree, and both are right.** The block in the PDO interface
  still contains `$classname`, `$classid` and `$mapping`; the server shows the expanded, resolved form.
  Write the variables back when editing the interface.

## Further Reading

- [The Tentackle MCP Maven Plugin](../../../tentackle-mcp-maven-plugin/src/main/docs/tentackle-mcp-maven-plugin.md) — the server's full reference.
- [The Tentackle Wizard Maven Plugin](../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) — all goals, both modes, profiles and templates.
- [Model Definition Syntax](../../../tentackle-model/src/main/docs/model-definition.md) — the DSL an agent writes.
- [Tentackle Model](../../../tentackle-model/src/main/docs/tentackle-model.md) — the API the server exposes.
- [Tentackle Scripting](../../../tentackle-core/src/main/docs/scripting.md) — the pluggable scripting API behind the `script` goal.
- [tentackle-test-pdo](../../../tentackle-test-pdo/src/main/docs/tentackle-test-pdo.md) — where the generated fixture code goes.
- [SQL Maven Plugin](../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) — dumping a model in the format the `pdo` batch mode reads.
- [Quickstart](./quickstart.md) — generating, building and running a multi-tier application.
