# Table Names — Schemas, Schema Mapping and Prefixes

An entity's table name is declared once, in the model:

```
table := md.ingotcategory
```

The part before the dot is a **schema**. Not every database can serve that name as written, and not every
deployment wants the same physical name. Tentackle therefore separates the *logical* name in the model from the
*physical* name in the database, and offers two build-time knobs to bridge the gap — `mapSchema` and `prefix`.
Neither of them touches the model.

Both are declared on [`@TableName`](../java/org/tentackle/session/TableName.java):

```java
@TableName(value =/*@*/"md.ingotcategory"/*@*/,   // @wurblet < Inject --string $tablename
           mapSchema =/*@*/true/*@*/,             // @wurblet < Inject $mapSchema
           prefix =/*@*/""/*@*/)                  // @wurblet < Inject --string $tablePrefix
```

The three attributes are injected from wurblet properties, so they are configured **in the pom**, not in the
source — the annotation shown above is generated.

---

## Which Backends Need This

The generated DDL is schema-qualified only if the backend supports schemas. `Backend.isSchemaSupported()`
returns `false` for:

| Backend               | Why                                                                              |
|-----------------------|----------------------------------------------------------------------------------|
| **MySQL**, **MariaDB**| `CREATE SCHEMA` is a synonym for `CREATE DATABASE` — a schema is a database of its own, not a namespace inside one. `MariaDb` extends `MySql` and inherits this. |
| **Oracle**            | A schema *is* a user. Putting `md.` and `td.` into schemas means creating two extra users, with their own grants. See [The Oracle Backend](../../../../tentackle-sql/src/main/docs/oracle.md). |
| **Informix**          | "there is a `CREATE SCHEMA`, but this is a different beast" (`Informix:190`).     |
| **Firebird**          | Schemas arrive with Firebird 6 only. See [The Firebird Backend](../../../../tentackle-sql/src/main/docs/firebird.md). |

PostgreSQL, CockroachDB, H2, SQL Server, DB2 and SAP HANA support schemas, so a model using them needs no mapping there. If your
application must run on *both* kinds of backend, map the schemas — the flattened names work everywhere.

Calling `sqlCreateSchema()` on a backend that does not support schemas throws a `BackendException`, so this is
not something you can ignore.

---

## `mapSchema` — Flattening Schema-Qualified Names

With `mapSchema = true`, every dot in the table name becomes an underscore:

| Model               | Physical table    |
|---------------------|-------------------|
| `md.ingotcategory`  | `md_ingotcategory`|
| `td.message`        | `td_message`      |
| `tokenlock`         | `tokenlock`       |

All tables end up in the connecting user's default schema, but master data still starts with `md_` and
transaction data with `td_`, so the grouping survives.

Tentackle's own technical tables (`modlog`, `tokenlock`, `prefnode`, `prefkey`, `numpool`, `numrange`,
`secrules`, `bundle`, `bundlekey`, `modification`) carry **no** schema, so mapping leaves them untouched.

> **Watch the identifier length.** Flattening makes names longer, and the limit is per backend:
> 30 characters on Oracle, 64 on MySQL/MariaDB (`getMaxNameLength()`). A name that is too long is rejected at
> build time, not at runtime.

---

## `prefix` — Prepending to the Table Name

`prefix` is prepended to the *whole* name, after the schema has (or has not) been mapped:

| Model         | `prefix="pls"`, `mapSchema=false` | `prefix="pls"`, `mapSchema=true` |
|---------------|-----------------------------------|-----------------------------------|
| `md.user`     | `plsmd.user`                      | `plsmd_user`                      |
| `td.message`  | `plstd.message`                   | `plstd_message`                   |
| `tokenlock`   | `plstokenlock`                    | `plstokenlock`                    |

The unmapped column is the interesting one on Oracle: the schema *names* get the prefix, so the main user `pls`
keeps its own name while master data lands in the schema-user `plsmd` and transaction data in `plstd`. That
lets several instances of the same application share one Oracle server without colliding.

> ### ⚠️ `prefix` has no counterpart on the DDL side
>
> `prefix` is applied only where the runtime table name is derived — in
> [`TableNameAnalyzeHandler`](../../../../tentackle-build-support/src/main/java/org/tentackle/buildsupport/TableNameAnalyzeHandler.java)
> and [`SessionUtilities.determineTablename`](../java/org/tentackle/session/SessionUtilities.java).
> Neither `tentackle-model` nor the `tentackle-sql-maven-plugin` knows about it: `Model` offers
> `setSchemaNameMapped()`, but nothing equivalent for a prefix. The generated `createmodel.sql` and the
> migration scripts therefore use the **unprefixed** names.
>
> A prefixed deployment has to bridge that itself — for example by running the DDL as the prefixed schema
> user, or by post-processing the generated script. If you only need the tables grouped, prefer `mapSchema`,
> which *is* implemented on both sides.

---

## Where Each Knob Is Configured

Two independent pipelines derive a physical table name, and **they must agree**:

```
                       @TableName(mapSchema, prefix)          the name the application uses
  model file   ────►   TableNameAnalyzeHandler        ────►   META-INF/mapped-services/
  table := md.user     SessionUtilities                       org.tentackle.session.TableName

                       Model.setSchemaNameMapped()            the name the database has
  model file   ────►   ModelImpl.addEntityInfo        ────►   CREATE TABLE / ALTER TABLE
  table := md.user     (tentackle-sql-maven-plugin)
```

| Knob            | Configured in                                                             | Property                | Affects                        |
|-----------------|---------------------------------------------------------------------------|-------------------------|--------------------------------|
| `mapSchema`     | `wurbelizer-maven-plugin` → `<wurbletProperties>`                         | —                       | the runtime name (`@TableName`)|
| `tablePrefix`   | `wurbelizer-maven-plugin` → `<wurbletProperties>`                         | —                       | the runtime name (`@TableName`)|
| `mapSchemas`    | [`tentackle-sql-maven-plugin`](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) | `tentackle.mapSchemas`  | the generated DDL and migration|
| `mapSchemas`    | [`tentackle-wizard-maven-plugin`](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) | `tentackle.mapSchemas`  | the model the wizard browses   |
| `tentackle.mapSchemas` | `maven-surefire-plugin` → `<argLine>`                              | system property         | `ModelImpl`, for unit tests    |
| `createSchemas` | `tentackle-sql-maven-plugin`, `create` goal                               | —                       | emits `CREATE SCHEMA`; ignored when `mapSchemas` is set |
| `schemaNames` / `schemas` | `tentackle-sql-maven-plugin`, backend configuration             | —                       | restricts `migrate` to the listed schemas; ignored when `mapSchemas` is set |

> **Mind the plural.** The annotation attribute and the wurblet property are `mapSchema` (singular); the model
> API, the maven parameters and the system property are `mapSchema**s**` (plural). They are the same decision
> and must always be given the same value.

The wizard plugin's `<tablePrefix>` inside a `<PdoProfile>` is a **different thing entirely**: it pre-fills the
table name in the wizard (e.g. `md.`) so a new PDO lands in the right group. It ends up in the model, not in
`@TableName.prefix`.

---

## A Complete Example

An Oracle application that maps schemas and uses no prefix. In the parent pom:

```xml
<!-- generate @TableName(mapSchema=true, prefix="") -->
<plugin>
  <groupId>org.wurbelizer</groupId>
  <artifactId>wurbelizer-maven-plugin</artifactId>
  <configuration>
    <wurbletProperties>
      <mapSchema>true</mapSchema>
      <tablePrefix />
      <backends>oracle, h2</backends>
      ...
    </wurbletProperties>
  </configuration>
</plugin>

<!-- generate flat DDL to match -->
<plugin>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-sql-maven-plugin</artifactId>
  <configuration>
    <backendNames>oracle, h2</backendNames>
    <mapSchemas>true</mapSchemas>
    ...
  </configuration>
</plugin>

<!-- and let the tests see the same model -->
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <configuration>
    <argLine>-Duser.language=en -Duser.region=US -Dtentackle.mapSchemas=true</argLine>
  </configuration>
</plugin>
```

The wizard plugin gets the same setting, so browsing shows the tables that really exist:

```xml
<plugin>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-wizard-maven-plugin</artifactId>
  <configuration>
    <mapSchemas>true</mapSchemas>
    <profiles>
      <PdoProfile>
        <name>masterdata</name>
        <tablePrefix>md.</tablePrefix>   <!-- pre-fills the wizard, see above -->
        ...
```

The build then produces, for the model line `table := md.ingotcategory`:

```java
// in the PDO interface
@TableName(value=/*@*/"md.ingotcategory"/*@*/, mapSchema=/*@*/true/*@*/, prefix=/*@*/""/*@*/)
```

```properties
# in META-INF/mapped-services/org.tentackle.session.TableName
com.hydro.plsbl.pdo.md.IngotCategory = "md_ingotcategory"
```

```sql
-- in createmodel.sql
CREATE TABLE md_ingotcategory ( -- ingot category
```

No `CREATE SCHEMA` is emitted, and the framework's own tables (`modlog`, `tokenlock`, …) keep their plain names.

---

## When the Two Sides Disagree

| Symptom                                                                     | Likely cause                                                                 |
|-----------------------------------------------------------------------------|------------------------------------------------------------------------------|
| Runtime fails with *table or view does not exist* / *relation does not exist*| `mapSchema` and `mapSchemas` differ — the application asks for `md.user`, the database has `md_user` (or vice versa) |
| `tentackle-sql:migrate` wants to create **every** table                      | the same, seen from the DDL side: the model's names do not match anything in the database |
| Build fails with *schemas are not supported by backend …*                    | `mapSchemas` is not set for a backend that needs it                          |
| Build fails with *name too long*                                             | the flattened `schema_table` name exceeds the backend's identifier limit      |
| Unit tests see different tables than the application                         | `-Dtentackle.mapSchemas` missing from the surefire `argLine`                  |

Changing the setting on an existing database is a **rename**, not a no-op. The migrator will not guess it — use
a `rename <tablename>: <newtablename>` migration hint (see the
[SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md)).

---

## Related Documentation

- [Tentackle Session](session.md) — where `@TableName` lives, alongside the other model-describing annotations.
- [Model Definition](../../../../tentackle-model/src/main/docs/model-definition.md) — the `table` keyword and
  the `$tablename` variable.
- [Tentackle SQL Maven Plugin](../../../../tentackle-sql-maven-plugin/src/main/docs/tentackle-sql-maven-plugin.md) —
  the `mapSchemas`, `createSchemas` and `schemaNames` parameters.
- [Tentackle Wizard Maven Plugin](../../../../tentackle-wizard-maven-plugin/src/main/docs/tentackle-wizard-maven-plugin.md) —
  `mapSchemas` and the profile `tablePrefix`.
- [The Oracle Backend](../../../../tentackle-sql/src/main/docs/oracle.md) — why Oracle in particular has no
  usable schemas.
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md) — how the
  `@TableName` mapped-service entries are produced and found.
