---
name: tentackle-sql-reveng
description: Reverse-engineer tables of an existing database into Tentackle model source with "mvn tentackle-sql:reveng". Use whenever the task is to introduce Tentackle into a project whose database already exists, to bootstrap entities from a live schema rather than hand-writing model text, or when asked where a batch-mode model definition for an existing table would come from.
---

# Reverse-engineering entities from an existing database

`tentackle-sql:reveng` reads the columns, indexes and foreign keys of existing database tables via
JDBC metadata and writes one model-source file per table — the exact plain-text format
`tentackle-wizard:pdo -DbatchDir=…` already reads (see the `tentackle-pdo-wizard` skill). It never
generates Java code itself; it is always the first of two steps, and lives in the SQL plugin (not the
wizard plugin) because it is purely a database-introspection tool — it never touches the wizard's
model, profiles or templates.

## How to use it

```bash
mvn tentackle-sql:reveng -Dtentackle.reveng.url=jdbc:postgresql://localhost/legacydb \
                          -Dtentackle.reveng.user=app -Dtentackle.reveng.password=secret \
                          -Dtentackle.reveng.tables=CUSTOMER,ORDER_ITEM
mvn tentackle-wizard:pdo -DbatchDir=target/reveng-model
```

Key parameters (all `-Dtentackle.reveng.*`):

- `url`, `user`, `password` — the JDBC connection to the *existing* database being reverse-engineered.
  This is independent of the application's own backend configuration.
- `tables` (required) — comma-separated table names to reverse-engineer together, in one run.
- `schemas` — optional comma-separated schema filter.
- `outputDir` (default `target/reveng-model`) — pass this same path as `pdo`'s `-DbatchDir`.
- `startClassId` (default `1`) — the class id of the first table; consecutive ids follow in the order
  of `tables`. Pick a value inside the target profile's `minClassId`/`maxClassId` range, exactly as you
  would when hand-writing batch model text — this goal has no knowledge of the profiles configured for
  `pdo` and cannot check for collisions itself.
- `stereotype` — written into every generated entity so `pdo`'s batch mode can select the matching
  `PdoProfile` when more than one is configured, e.g. `-Dtentackle.reveng.stereotype=MasterData`.

## The order that works

1. **Reverse-engineer every table an aggregate needs in one run.** A foreign key to a table that is
   *not* part of the same run is left as a plain attribute (with a comment), not a relation — `pdo`'s
   batch mode loads every file in the output directory together and cannot resolve a relation to an
   entity that isn't among them. Add the missing table to `tables` and run again, or add the relation
   by hand afterward.
2. **Read every generated `.map` file before running `pdo`.** This goal produces a starting point, not
   a finished model:
   - Every foreign key becomes a plain, non-composite object relation. Whether it should really be
     `component`/`composite`, need `delete = cascade`, or an eager `select` strategy is an
     aggregate-design decision the DDL does not encode — decide it and edit the relation block.
   - A table with no literal `id` column is declared `[nopkey]` and will not behave like a normal
     identity-bearing PDO; this is a genuine mismatch with Tentackle's object-identity model, not
     something to just accept without checking whether that table should actually get a real
     surrogate id instead.
   - A `NOT NULL` column whose type has no Java primitive form (`String`, dates, `BigDecimal`, …)
     gets a `@NotNull` validation instead of an enforced-by-type constraint — read the model-definition
     doc's note on this if the distinction matters for the entity.
   - Check the logged warnings: they call out multi-column foreign keys (unsupported — Tentackle
     relations are single-column only), foreign keys outside the batch, and duplicate indexes the
     database itself created (common for a foreign key's backing index) that were dropped rather than
     declared twice.
3. **Fix the class ids before generating**, not after. Once `pdo` has generated files from a given
   `id := …`, changing it means touching the generated sources by hand — cheaper to get the range
   right in `startClassId` first.
4. Then follow the `tentackle-pdo-wizard` skill's own steps from "Validate the definition" onward.

## Things that will trip you up

- **This goal never touches the application's own model or profiles.** It only reads the *external*
  database and writes text files; it does not know about `minClassId`/`maxClassId` ranges, existing
  entities, or already-used class ids in the target project. Cross-check `startClassId` yourself.
- **Table and column names are normalized, not preserved verbatim.** `order_item` becomes entity
  `OrderItem`; a foreign key column becomes an attribute named `<relation>Id` (derived from the
  column name with a trailing `_id` stripped, not from the original column name) so the framework's
  naming convention between a relation and its backing attribute holds. The original database column
  name is kept as the model's column name either way. The `id` and `classid` columns are not written
  at all: the model adds them implicitly.
- **A second run does not merge with a first one.** By default an existing `<Entity>.map` in
  `outputDir` is left untouched and a warning is logged; pass `-Dtentackle.reveng.overwrite=true` to
  replace it — which discards any hand-editing already done on that file.
