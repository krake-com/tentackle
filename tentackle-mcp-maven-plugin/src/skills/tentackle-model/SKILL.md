---
name: tentackle-model
description: Inspect and change the Tentackle data model of this application through the tentackle-model MCP server. Use whenever the task touches entities, attributes, relations, class ids, table or column names, indexes, aggregates and components, the unique domain key, a PDO interface's "@> ... @<" model block, a .map file, or when a build fails with a model or wurblet error.
---

# The Tentackle model

This application's data model is defined in a DSL block inside each PDO interface and compiled by the Wurbelizer
into `.map` files. The `tentackle-model` MCP server holds that model loaded and relation-resolved, exactly as
the code generators see it. Use it instead of grepping — most of the model is derived and appears in no file.

## Before you start

The server must be running. If its tools are not available, ask the user to start it:

```bash
mvn tentackle-mcp:serve
```

It must be started at the reactor root and needs the model to have been generated at least once
(`mvn -DskipTests generate-sources`).

## How to use it

**Always start with `model_overview`.** It reports the project's model defaults, integrity mode, aggregate roots
and any configuration warnings. Everything else is interpreted in that light — in particular, options implied by
the model defaults must not be repeated in a definition you write.

Then:

- **Finding things** — `model_list_entities` with a `pattern` or `filter` for the index; `model_find` when you
  know a name fragment but not where it lives (it searches entity, table, attribute, column, relation and index
  names at once).
- **Understanding one entity** — `model_describe_entity`. Ask for the sections you need: `definition` for the
  canonical DSL, `aggregate` for the root and composite paths, `references` for who points at it, `attributes`
  and `relations` for the members, `ddl` for the generated SQL. Its header names the source file and line of the
  `@> ... @<` block — that is the file to edit.
- **Understanding a connection** — `model_paths` between two entities, or with `from` alone for the direct
  neighbors. Check this before adding a relation: the link often already exists.
- **Copying a shape** — `model_source` with `format: "compact"` and `asComment: true` gives the exact block
  shape to imitate.

## Writing or changing a model definition

Follow this order. It is the whole reason the server exists.

1. `model_describe_entity` on one or two comparable entities and match their conventions — table and column
   naming, options, integrity — rather than inventing new ones.
2. Read the `tentackle-model://grammar/model-definition` resource for any syntax you are not certain of. Do not
   guess the DSL; a definition that looks plausible and does not parse costs a full build to discover.
3. Pick a free class id and confirm it with `model_list_entities`. A duplicate class id is the single most
   common mistake.
4. **Call `model_validate` with your draft as `source` before writing anything.** Pass `entity` as well when you
   are changing an existing entity. Fix what it reports and call it again until it says the candidate is
   consistent. Nothing is written by this — the live model and every file are untouched.
5. Only then edit the PDO interface and paste the definition into its `@> ... @<` block.
6. Run the build so the wurblets regenerate the persistence and domain layers.

## Things that will trip you up

- **The `.java` source and the server disagree, and both are right.** The block in the PDO interface still
  contains `$classname`, `$classid`, `$mapping` and friends; the server shows the expanded, resolved form. Write
  the variables back when you edit the interface.
- **Most of what you need is derived.** Referencing relations, composite paths, root entities, resolved types
  and backend column names exist only in the loaded model. Grepping the sources will not find them.
- **The server is read-only.** It never edits a file. You do that yourself, after `model_validate` agrees.
- **After a build that deleted or renamed an entity, call `model_reload` with `full: true`.** Changed and added
  files are picked up automatically; a removed one is not.
- **Warnings in the overview matter.** If it reports that no model defaults are configured, the model differs
  from what the build sees and integrity errors it reports may be spurious. Tell the user to set `modelDefaults`
  in the plugin configuration rather than working around the errors.
