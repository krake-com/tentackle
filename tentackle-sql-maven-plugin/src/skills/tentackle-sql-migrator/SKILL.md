---
name: tentackle-sql-migrator
description: Generate the SQL that brings a database in line with the entity model, with "mvn tentackle-sql:migrate", and check for drift with "mvn tentackle-sql:validate". Use whenever the model changed and the database has to follow — after adding or changing an entity, attribute, index or relation — when a table or column was renamed, or when asked whether a database still matches the model.
---

# Migrating a database to the model

`tentackle-sql:migrate` connects to the database, reads its metadata through JDBC, compares it table by
table against the entity model, and **writes the SQL that would bring the database in line** — added,
altered and dropped columns and indexes, foreign keys, renamed and dropped tables. `tentackle-sql:validate`
is the same thing with the verdict turned into a build failure.

**It generates; it does not apply.** Nothing is executed. The script lands in
`target/sql/<backend>/migratemodel.sql`, one per configured backend. What happens to it next is a
separate, deliberate step: review it, **commit it as a versioned migration**, and let the project's
migration tool — Flyway, Liquibase or whatever it uses — apply it to the databases it manages.
`tentackle-sql:run` is for trying it out on your own database, not for rolling it out.

## Before you start

- Run from the **reactor root**. The goal is an aggregator.
- **Build first.** The comparison uses the *generated* model under `target/wurbel/model`, so a model
  change that has not been through a build is invisible here:

  ```bash
  mvn -DskipTests install
  ```

- It runs against **every configured connectable backend**, in sequence. Know which databases those
  are before you start; the goal only reads metadata, but the script it produces is meant for them.
- The JDBC driver of each backend must be a `<dependency>` of the plugin.

## How to use it

```bash
mvn tentackle-sql:migrate          # generate the migration script(s)
mvn tentackle-sql:validate         # fail the build if any migration would be necessary
```

Then **read `target/sql/<backend>/migratemodel.sql`**. Only afterwards, and only against your own
development database, try it out:

```bash
mvn tentackle-sql:run -Dscript=target/sql/postgres/migratemodel.sql
```

Once it does what it should, move the script into the project's migration directory under the version
it belongs to, commit it, and leave the rollout to the migration tool. A generated script that is not
committed is a migration that only ever happened on one machine.

If nothing differs, the script says so and the log reports `database meta data matches object model`.

Use `validate` as the cheap question "is this database still in sync with the model?". It is the right
check after pulling changes, and in CI. (The `migrate` goal's `<validate>true</validate>` does the same
from the POM; there is no `-Dvalidate` property.)

## Renamed columns keep their data

The migrator works to preserve data: a renamed column is migrated with `RENAME` (or rename plus type
change), not dropped and re-added. It pairs the columns the model added with the columns the database
no longer has, in stages — an unambiguous match by SQL type first, then a best match by *syllable
distance* among the candidates of the same type, longest names first. Bulk renames therefore usually
come out right in one go: `x_position → position_x`, `y_position → position_y` and
`z_position → position_z` are paired correctly rather than shuffled.

Where it guesses, it shows its work: the alternative `ADD` and `DROP` statements are written into the
script as comments, so a wrong guess can be corrected by hand instead of being discovered afterwards.
Only columns with no counterpart left become a real `DROP` or `ADD`.

**Table renames are different — those are not guessed.** Without a `rename <table>: <newtable>` hint,
the old table is reported as unexpected and the new one is created empty.

## When hints are needed

**Migration hints** are plain text files per backend, supplied through `<migrationHints>` file sets
inside a `<backend>` of the POM (backends from `backendProperties` files get no hints). Sections start
at the beginning of a line with a lowercase keyword and a colon, and run until the next keyword:

```
-- rename the customer table, convert a column, and order two migrations
rename kunde: customer

migrate customer#kdnr:
ALTER TABLE customer ADD COLUMN customer_no bigint;
UPDATE customer SET customer_no = CAST(kdnr AS bigint);
ALTER TABLE customer DROP COLUMN kdnr;

depend invoice: customer
```

Reach for a hint when:

- **a table was renamed or has to be dropped** — `rename <table>: <new>`, `drop <table>: <comment>`;
- **the data has to be converted**, for instance a string column becoming a number. Where a backend
  cannot change the type in place, the migrator emits the `ALTER` *commented out* rather than a
  statement that would fail or truncate — that comment is the signal to write a
  `migrate <table>#<col>` hint with the conversion SQL;
- **the automatic guess is wrong** — an `add`- or `drop`-matching hint disables the rename the migrator
  would have generated, and `rename <table>#<col>: <newcol>` forces a specific pairing;
- **order matters** — `depend <table>: t1, t2` delays a table until others are migrated;
- **something has to run around the migration** — `always before` / `always after`,
  `before all` / `after all`, `before <table>` / `after <table>`, or `migrate <table>` to replace the
  automatic migration entirely. `hint <table>: <regex>` picks one of the alternatives the migrator
  commented out.

Read the plugin documentation for the exact semantics before writing one. Two traps: write the SQL in
uppercase, since a line starting with a lowercase keyword and containing a colon opens a new section;
and a `--` comment line *inside* a section without a colon is not a comment keyword but part of that
section's text.

## Things that will trip you up

- **A required migration does not fail the build.** `migrate` logs
  `database meta data differs from object model` as a *warning* and exits successfully. Never conclude
  from a green build that the database is in sync — use `validate`, or read the log and the script.
- **Always read the generated SQL before applying it.** The interesting parts are the `DROP` statements
  and the commented-out alternatives: a `DROP COLUMN` that you did not intend means the rename guess
  missed, and a commented-out `ALTER … TYPE` means the data needs converting by hand.
- **`tentackle-sql:run` is a development tool, not the rollout path.** It has no versioning, no record
  of what a database already ran, and no transaction — it executes with autocommit on, so a migration
  failing halfway leaves the statements before it committed. Fine on your own database with a backup;
  for anything shared, the committed script and Flyway or Liquibase.
- **One script per backend.** `target/sql/postgres/migratemodel.sql` and
  `target/sql/h2/migratemodel.sql` are different scripts — apply each to its own database, and do not
  assume the PostgreSQL one is representative.
- **Tables that are not in the model** are reported as unexpected. If they legitimately do not belong to
  the model, add them to `<alienTables>` rather than dropping them; `failOnUnexpectedTables` turns the
  report into an error — and lists them in the script as `DROP TABLE` statements, so read before you
  run it.
- **Hints can be version-filtered.** With a backend `<minVersion>` (a fixed version, a `SELECT`, or a
  `DatabaseVersionLoader` class), hints in version-named sub-directories only apply when they are
  strictly newer than the database's version; without `<minVersion>`, all of them apply. If a hint seems
  to be ignored, check which directory it sits in.
- **`tentackle-sql:create` is not the tool for this.** It emits the DDL of the *whole* schema from
  scratch, for bootstrapping an empty database. Running it against a database that already has data is
  never what a change to the model calls for — that is what `migrate` exists for.
