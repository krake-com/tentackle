---
name: tentackle-sql-runner
description: Execute an SQL script against a development database with "mvn tentackle-sql:run -Dscript=file". Use whenever the task needs plain SQL locally — querying rows, counting, finding object ids for a fixture, trying out a generated migration, fixing up local data — or when asked what is actually in a table as opposed to what the model says. Not the tool for rolling out migrations to real environments.
---

# Running SQL with the SQL plugin

`tentackle-sql:run` executes an SQL script against the backend(s) the project already configures for
`tentackle-sql:migrate`, and prints every statement's result into the Maven log. Result sets come back
as an aligned table with column headers, so a `SELECT` is a perfectly good way to *look* at the
database — no JDBC code, no credentials to find, no application classes involved.

It is the lowest-level of the Tentackle agent tools: it sees tables and columns, not entities and
attributes. Use it when the question is about the database itself.

## It is a development tool

`tentackle-sql:run` is for the database on the developer's machine and for a build's test database. It
has no notion of which scripts a database has already seen — no versioning, no checksums, no rollback,
no record that it ran at all.

**Rolling out a migration is a different job.** The script that `tentackle-sql:migrate` generated gets
reviewed, committed to the repository as a versioned migration, and applied by a migration tool such as
Flyway or Liquibase, which tracks what each database has already had applied. Use `run` to try that
script out locally; do not use it to bring staging or production forward, and do not propose it as the
deployment step.

## Before you start

- Run from the **reactor root**. The goal is an aggregator.
- **Know which backends are configured.** The goal connects to *every* connectable backend the plugin
  knows — inline `<backends>` in the POM, `backendProperties` file sets, or a `<serverId>` from
  `settings.xml`. A project that configures PostgreSQL *and* H2 will run your script against both, in
  turn. Read the `tentackle-sql-maven-plugin` configuration before running anything that writes.
- The JDBC driver for each backend must be a `<dependency>` of the plugin. In a configured project it
  is.
- This goal does not need the model — it never looks at `target/wurbel/model`. Any database reachable
  with the configured credentials will do.

## How to use it

```bash
mvn tentackle-sql:run -Dscript=target/query.sql
```

`-Dscript=<file>` is the only parameter and is required; there is no inline-SQL form, so write the
statements to a file first (a scratch directory is fine — it is not part of the build).

```sql
-- how many open orders per customer?
SELECT c.name, COUNT(*) AS open_orders
  FROM td.order o JOIN md.customer c ON c.id = o.customer_id
 WHERE o.status = 'OPEN'
 GROUP BY c.name
 ORDER BY 2 DESC;
```

The log then holds one entry per statement: the SQL, the elapsed time, and either the aligned result
set or the update count.

**Script syntax:** statements are separated by `;` (the backend's separator), `--` line comments and
`/* … */` block comments are stripped by the runner and never reach the database, and quoted strings
are respected when splitting. Keep one statement per `;` and do not rely on client-side commands of
`psql`, `sqlplus` and friends — this is not a database CLI, it is a JDBC script runner.

## Where it fits among the other tools

- **`tentackle-mcp:serve`** answers what the *model* says — entities, relations, generated DDL. Ask it
  first when the question is "how is this mapped?"; ask `tentackle-sql:run` when it is "what is in
  there?".
- **`tentackle-wizard:script`** runs Groovy or Ruby *through the domain model*, with `on()` and `op()`,
  validation and locking. Prefer it for anything that must go through business logic.
- **`tentackle-sql:run`** bypasses all of that. That is its strength for inspection and its danger for
  writes.
- A common chain: `SELECT id …` here → feed the ids to
  `mvn tentackle-wizard:browse -Dpdo=Entity[…] -Doutput=…` to generate fixture code.

## Things that will trip you up

- **There is no transaction.** The connection is opened with autocommit *on* and the runner is not
  transactional, so every statement commits as it executes. A script that fails on statement 7 leaves
  statements 1–6 committed and the build red. There is no rollback. Put `BEGIN`/`COMMIT` in the script
  yourself if you need atomicity and the backend supports it in a script.
- **It runs against every configured backend.** The script is executed once per connectable backend, in
  sequence. If one of them is a production or shared database, that is where your `UPDATE` lands too.
  Check the configuration, and say which databases will be hit before running a write.
- **It bypasses the domain layer entirely.** No validation, no optimistic locking, no `serial` bump, no
  token locks, no modification tracking, no cache invalidation — running clients will not notice the
  change and may overwrite it. For data fixes on a live system, `tentackle-wizard:script` is almost
  always the right tool instead.
- **Table and column names are the *mapped* ones**, not the Java names: schema-qualified as in the
  model (`td.order`), or flattened when the project sets `mapSchemas` for a backend that has no
  schemas. Get the real names from the MCP server (`model_describe_entity` with the `ddl` section, or
  `model_ddl`) rather than guessing from the entity name.
- **Never hand-edit the schema this way.** Structural changes belong in the model plus
  `tentackle-sql:migrate`, otherwise the next `tentackle-sql:validate` fails and the database drifts
  from the model. Use `run` to try a *generated* migration on your own database — and let Flyway or
  Liquibase apply the committed script everywhere else.
- **Prefer read-only.** Start with a `SELECT` or a `COUNT(*)` to confirm the rows you think you are
  touching, and show the user the statements before running anything destructive.
