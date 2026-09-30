## The Tentackle MCP Maven Plugin

`tentackle-mcp-maven-plugin` runs an [MCP](https://modelcontextprotocol.io/) server that exposes the
[Tentackle model](../../../../tentackle-model/src/main/docs/tentackle-model.md) — entities, attributes, relations,
indexes, inheritance and aggregate structure — to coding agents, in exactly the shape the code generators see it.

It exists because everything an agent needs in order to reason about an application's data model is present, and
almost none of it is where an agent will look:

- **The model lives in generated output.** The DSL is written in a `@> … @<` comment block inside a PDO
  interface, with `$classname`, `$classid` and `$mapping` still unexpanded. The usable form — the `.map` files —
  is produced by the Wurbelizer into `target/wurbel/model` and shipped as `META-INF/MODEL-INDEX.LIST`.
- **Most of the model is derived, not written.** `getReferencingRelations()`, `getCompositePaths()`,
  `getRootEntity()`, `getLeafEntities()`, the root/rootId/rootClassId inference, the resolved data types and the
  backend-specific column names exist only after the whole model has been loaded and its relations resolved. No
  file contains them.
- **The feedback loop is a full build.** Asking whether a model definition parses and is consistent with the
  other forty entities has meant running the reactor and reading a `WurbelTerminationException`.

The server closes all three with one long-lived process holding a loaded, relation-resolved model.

> **It is read-only.** No file is written, no database is contacted, no domain class is loaded. It reads model
> text and answers questions about it.

### Why a Maven plugin

The model's configuration — the model directory, the model defaults, the entity aliases, the backends — already
lives in the POM and drives the Wurbelizer. Serving the model *as the build sees it* is the entire point, so the
POM stays the single source of truth: a standalone launcher would duplicate that configuration in command-line
arguments that silently drift out of step. As a bonus there is no classpath to assemble by hand.

The server speaks MCP over HTTP rather than stdio, which is what makes hosting it in Maven possible at all: on
stdio, stdout *is* the protocol channel and Maven writes to it unconditionally.

### Prerequisites

JDK >= 25 and Maven >= 3.9.10 (Maven 4 is supported as well), like the rest of Tentackle. The model must have been generated at least once, so
run a build before the first start:

```bash
mvn -DskipTests generate-sources
```

### Overview of Goals

| Goal    | Default phase | Purpose                                                    |
|---------|---------------|------------------------------------------------------------|
| `serve` | none          | Runs the MCP server until stopped with `Ctrl-C`.            |

The goal is an aggregator and is not bound to a lifecycle phase: the model spans the whole reactor, so the
server runs once, at the root, and is started explicitly.

### Configuration

```xml
<plugin>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-mcp-maven-plugin</artifactId>
  <version>${tentackle.version}</version>
  <configuration>
    <modelDefaults>${tentackle.modelDefaults}</modelDefaults>
    <backends>PostgreSQL</backends>
    <port>8770</port>
  </configuration>
</plugin>
```

Model parameters — mostly the same as in the
[SQL](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) and
[wizard](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) plugins, so a
project that configures those can reuse its settings. Three differ, though:

- `backends` is a plain comma-separated string of backend names here. The SQL plugin's `backends` is a list of
  connectable `<backend>` elements; its list of names is called `backendNames` (`tentackle.backendNames`).
- `loadModelFromDependencies` defaults to `false` here, but to `true` in the wizard plugin.
- `mapSchemas` is shared with the SQL plugin and the wizard's `browse` goal; the other wizard goals don't have it.

The parameters in detail:

- `modelName` (`tentackle.modelName`): the model name. Defaults to `default`.
- `modelDir` (`tentackle.modelDir`): the directory holding the `.map` files. Defaults to
  `${project.build.directory}/wurbel/model`; the Tentackle parent POM points it at the reactor root's
  `target/wurbel/model`.
- `filesets`: model directories in load order, dependency models first. If given, `modelDir` is ignored. Every
  directory is loaded without resolving relations and a single resolution pass follows once the last one is in —
  the only order in which relations across dependency models resolve.
- `modelDefaults` (`tentackle.modelDefaults`): the project-wide model defaults. **Always set this**, see below.
- `entityAliases` (`tentackle.entityAliases`): fixed table aliases, for instance `usr=User, grp=Group`.
- `mapSchemas` (`tentackle.mapSchemas`): map schema names.
- `backends` (`tentackle.backends`): comma-separated database backends to validate against, for instance
  `PostgreSQL,H2`. `all` selects every non-deprecated backend. Without them names, types and sizes are not
  verified and `model_ddl` has nothing to render.
- `loadModelFromDependencies`: additionally load the model from the plugin's classpath. Defaults to `false`.

Server parameters:

- `host` (`tentackle.mcp.host`): the address to bind to. Defaults to `localhost`.
- `port` (`tentackle.mcp.port`): the port to listen on. Defaults to `8770`.
- `path` (`tentackle.mcp.path`): the path of the MCP endpoint. Defaults to `/mcp`.
- `serverName`: the name reported to the client during initialization. Defaults to `tentackle-model`.
- `stalenessCheckMillis`: minimum delay between two checks for changed model files. Defaults to `1000`.
- `maxResults`: hard upper bound for the number of rows a tool may return. Defaults to `1000`.

Like all Tentackle mojos, the goal also inherits `verbosity`, `skip`, `charset`, `jdkToolchain` and
`minLogLevel` (see the [`tentackle-maven-plugin` docs](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md#common-configuration)).
It is thread-safe, but the loaded model is a JVM-wide singleton, so concurrent executions in a parallel build are
serialized (see [Parallel builds](../../../../tentackle-maven-support/src/main/docs/tentackle-maven-support.md#parallel-builds)).

> **Always configure `modelDefaults`.** They come from the parent POM's property, not from the model sources.
> Without them the model is loaded exactly as written, which differs from what the build sees and produces
> integrity errors — typically `untracked relation … not allowed in conjunction with FULL-integrity` — that do
> not exist in the build. The server starts either way, but says so in every overview and validation result
> rather than defaulting silently.

### The `serve` Goal

```bash
mvn tentackle-mcp:serve
```

It logs the endpoint and the command that registers it, then blocks:

```
[INFO] 42 entities loaded from /home/me/myapp/target/wurbel/model
[INFO] MCP endpoint: http://localhost:8770/mcp
[INFO] claude mcp add --transport http tentackle-model http://localhost:8770/mcp
[INFO] stop with Ctrl-C
```

Every parameter can be overridden on the command line:

```bash
mvn tentackle-mcp:serve -Dtentackle.mcp.port=9000 -Dtentackle.backends=PostgreSQL
```

> Note: the server binds to `localhost` by default and has no authentication. It exposes your data model, not
> your data — but keep it that way unless you know what you are doing.

### Configuring an MCP client

The transport is Streamable HTTP, which every current MCP client supports.

**Claude Code** — either run

```bash
claude mcp add --transport http tentackle-model http://localhost:8770/mcp
```

or commit an `.mcp.json` in the project root so the whole team gets it:

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

**Codex CLI** — in `~/.codex/config.toml`, or `.codex/config.toml` for a single project:

```toml
[mcp_servers.tentackle-model]
url = "http://localhost:8770/mcp"
```

**VS Code, Cursor and the JetBrains agents** use the same `type` / `url` shape as Claude Code, in their
respective `mcp.json`.

Any client that cannot speak MCP can still use the server: it is plain JSON-RPC 2.0 over HTTP POST.

### The Tools

`Entity` alone answers some eighty questions; projecting them one-to-one would produce a tool list costing more
context than the model. Two collapses do most of the work:

- The **own / inherited / subentities / all** quartets that the model API repeats for attributes, relations,
  indexes and referencing relations become one `scope` argument.
- The **facet** getters (inheritance, aggregate, embedding, deep references, DDL, unique domain key) become a
  `sections` argument on the same tool.

| Tool                    | What it answers                                                                                                                  |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `model_overview`        | What model is loaded: sources, backends, effective defaults, entity count, inheritance hierarchies, aggregate roots, integrity distribution, configuration warnings. The entry point. |
| `model_list_entities`   | A column-aligned index, narrowed by `pattern` (substring, glob or `regex:`) and `filter` (composite, abstract, root, embedded, tracked, remote, cached, provided, has_subentities, has_superentity, deeply_referenced). |
| `model_find`            | Searches entity, table, attribute, column, relation and index names — and optionally comments — across the whole model.           |
| `model_describe_entity` | One entity, with `scope` and `sections`. See below.                                                                              |
| `model_source`          | The model definition as text: `raw`, `canonical` or `compact`, optionally as a `@> … @<` block and with the variables kept.       |
| `model_paths`           | How entities are connected: the relation graph between two of them, their direct neighbors, the composite paths or the embedding paths. |
| `model_ddl`             | `CREATE TABLE`, `CREATE INDEX` and foreign key statements for a backend, for the entities that own a table — the same rule the SQL plugin generates by. Text only — nothing is executed. |
| `model_validate`        | Validates the loaded model, or a **candidate** definition that has not been written anywhere. See below.                          |
| `model_reload`          | Refreshes changed files, or clears and reloads. Needed after deleting or renaming an entity.                                      |

Every entity argument accepts the entity name, `table:<tableName>` or `id:<classId>` — all three appear in the
sources an agent is reading, and none of them is reliably the one it has at hand.

The arguments of the tools (a `limit` is always capped by `maxResults`, which is also its default):

| Tool                    | Arguments                                                                                                                                                         |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `model_overview`        | none                                                                                                                                                              |
| `model_list_entities`   | `pattern` (substring, glob with `*`/`?`, or `regex:`), `filter` (array of facets, all must match), `limit`                                                         |
| `model_find`            | `pattern` (required, same syntax), `kinds` (array of `entity`, `table`, `attribute`, `column`, `relation`, `index`; default all), `comments` (default `false`), `limit` |
| `model_describe_entity` | `entity` (required), `scope` (`own`, `inherited`, `subentities` or `all`; default `inherited`), `sections` (array, see below)                                      |
| `model_source`          | `entity` (required), `format` (`raw`, `canonical` or `compact`; default `canonical`), `asComment` (default `false`), `useVariables` (default `false`)              |
| `model_paths`           | `from` (required), `to` (omit for the direct neighbors of `from`), `kind` (`relations`, `composite` or `embedding`; default `relations`), `direction` (`forward`, `reverse` or `both`; default `both`), `maxDepth` (default `4`), `limit` |
| `model_ddl`             | `entity` (array; omit for the whole model), `backend` (default: the first configured backend), `parts` (array of `table`, `indexes`, `foreignkeys`; default all)   |
| `model_validate`        | `source` (a candidate definition, without the `@>`/`@<` markers), `entity` (the entity the candidate replaces; omit for a new one)                                |
| `model_reload`          | `full` (clear and reload instead of refreshing changed files; default `false`)                                                                                    |

#### `model_describe_entity`

The primary representation is the **canonical model definition** — the densest, least ambiguous description of
an entity that exists, and the language the caller has to write back. Sections that the DSL cannot express
because they are computed from the whole graph are added explicitly:

`definition`, `attributes`, `relations`, `indexes`, `inheritance`, `aggregate`, `references`, `embedding`,
`udk`, `ddl` — defaulting to `definition, inheritance, references`.

The header additionally reports the source file and line of the `@> … @<` block, which is the file to edit and
which no other section reveals.

#### `model_validate`

Without arguments it reloads and validates everything that is configured. With `source` it validates a
**candidate** model definition: the candidate is staged into a scratch copy of the model sources — replacing
`entity` if given, added as a new entity otherwise — relations are resolved across the whole set and the
`CustomModelValidator` implementations run. Nothing is written and the live model is untouched.

That turns "write a model block, run the reactor, read the stack trace" into a sub-second loop:

```
model_validate {"source": "name := Note\ntable := note\nid := 9001\n…"}

candidate (new entity) is NOT consistent:

duplicate entity id 9001 for Note, already assigned to Customer
```

### Resources and Prompts

| Resource                                     | Content                                                                                                          |
|----------------------------------------------|--------------------------------------------------------------------------------------------------------------------|
| `tentackle-model://grammar/model-definition` | [Model Definition Syntax](../../../../tentackle-model/src/main/docs/model-definition.md), packaged with the plugin, so an agent never has to guess the DSL. |
| `tentackle-model://index`                    | All entities with class id, table and super entity.                                                              |
| `tentackle-model://entity/{name}`            | One entity's model source.                                                                                        |

One prompt, `new-entity`, walks an agent through adding an entity in the order that works: look at comparable
entities, check the class id, validate the candidate, and only then write the file. Its arguments are `entity`
(required, the name of the new entity), `purpose` (what the entity is for, in one sentence) and `root` (the
aggregate root, if the entity is a component).

### How It Behaves

- **Read-only.** Nothing is written, no database connection is opened, no application class is loaded. The
  server parses model text. Every tool is announced with a `readOnlyHint`, and only `model_reload` and
  `model_validate` clear it — they reload the model, which changes what the other tools see.
- **Self-refreshing.** Model files are re-scanned before each call, at most once per `stalenessCheckMillis`, and
  the model is reloaded when they moved. A rebuild in another terminal is picked up without a restart. A
  *removed* file forces a full reload, because a refresh alone keeps entities whose source is gone — that is what
  `model_reload` with `full` is for.
- **Never half a model.** Sources are read into a scratch model before the loaded one is replaced, so a build
  that is halfway through writing the model directory leaves the server on the model it had, and says so in its
  warnings. An answer is never a model with entities silently missing from it.
- **Consistent.** The model implementation synchronizes individual methods, which makes a single getter safe but
  not a tool call that walks an entity through a hundred of them. A whole invocation therefore runs under one
  lock, so a refresh can never land halfway through a description.
- **Honest about its configuration.** Missing model defaults or backends are reported in every overview and
  validation result, so a configuration gap is never mistaken for a broken model.

### The Skill

`src/skills/tentackle-model/SKILL.md` in this module is a ready-made agent skill describing when and how to use
the server. It is a template: copy it into your own project.

```bash
mkdir -p .claude/skills
cp path/to/tentackle-mcp-maven-plugin/src/skills/tentackle-model/SKILL.md .claude/skills/tentackle-model/
```

Agents that do not support skills can be pointed at the same file as plain instructions — in `AGENTS.md`, in a
Codex prompt, or in whatever the tool calls its project context.

The [wizard plugin](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md#the-skills)
ships four more, one per headless goal: `tentackle-pdo-wizard`, `tentackle-operation-wizard`,
`tentackle-pdo-browser` and `tentackle-script-runner`, and the
[SQL plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md#the-skills)
three further ones, `tentackle-sql-migrator`, `tentackle-sql-runner` and `tentackle-sql-reveng`. Together with
this one they cover the whole loop — reverse-engineer an existing schema, inspect the model, scaffold entities
and operations, migrate the database, run code against a backend, build fixtures, drop down to SQL.

### Further Reading

- [AI Agent Tooling Support](../../../../src/main/docs/ai-agents.md) — how this server and the headless
  wizard goals fit into one agent workflow.
- [Tentackle Model](../../../../tentackle-model/src/main/docs/tentackle-model.md) — the API the server exposes.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — the DSL an agent writes back.
- [SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) — the same
  model, turned into DDL that actually runs.
- [Wizard Maven Plugin](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) —
  the interactive counterpart for humans.
- [Model Context Protocol](https://modelcontextprotocol.io/) — the protocol specification.
- [Plugin reference (generated)](https://tentackle.org/tentackle/tentackle-mcp-maven-plugin/plugin-info.html)
