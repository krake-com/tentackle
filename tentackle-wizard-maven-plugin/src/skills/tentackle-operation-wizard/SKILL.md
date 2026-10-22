---
name: tentackle-operation-wizard
description: Create the Java source files for a new Tentackle operation headless with "mvn tentackle-wizard:operation -DoperationName=... -Dcomment=...". Use whenever the task is to add behavior that is not bound to a persistent entity — a report, an import, a batch job, a login procedure — or when asked where the operation interface, its domain and persistence interfaces and their implementations come from.
---

# Creating operations with the operation wizard

`tentackle-wizard:operation` writes the source files a new operation needs: the operation interface,
the domain interface, the persistence interface, and their two implementations — each into the module
that owns the package, as derived from the selected profile.

Interactively it is a JavaFX dialog you cannot use. **Batch mode replaces the dialog with command-line
parameters** and runs headless. An operation is not part of the model, so there is no input file and
no batch directory: everything the dialog would ask for is a `-D` property, and one invocation creates
one operation.

## Before you start

1. The project model must have been generated at least once — the goal loads it, and the default for
   remoting comes from the project's model defaults:

   ```bash
   mvn -DskipTests generate-sources
   ```

2. Run from the **reactor root**. The goal is an aggregator and must see every module to place files.
3. At least one `OperationProfile` must be configured for the plugin in the POM. Read it before
   writing anything — it defines the target packages and the super types. Its packages must already
   exist **as directories** under some module's `src/main/java`, and each must map to exactly one
   module, or the goal fails outright. If one does not exist, `mkdir -p` it in the module that owns
   it — do not change the profile.
4. **Is this really an operation?** Behavior that belongs to one entity belongs *in* that entity, not
   in an operation. Operations are for behavior that spans entities or none at all.

## How to use it

```bash
mvn tentackle-wizard:operation \
    -DoperationName=SendInvoice \
    -Dcomment="sends an invoice to the customer"
```

That generates `SendInvoice`, `SendInvoiceDomain`, `SendInvoicePersistence`, `SendInvoiceDomainImpl`
and `SendInvoicePersistenceImpl` (the exact names come from the framework's naming rules).

| Property                    | Purpose                                                                                    |
|-----------------------------|--------------------------------------------------------------------------------------------|
| `operationName`             | The operation interface name. **Setting it is what enables batch mode.**                    |
| `profile`                   | The `OperationProfile`, case-insensitive. Omit it if only one is configured.                |
| `comment`                   | The short description. Mandatory.                                                           |
| `longComment`               | The long description.                                                                       |
| `remote`                    | `true`/`false`. Defaults to the project's model defaults.                                   |
| `superOperation`            | The super operation; determines all five super types.                                       |
| `abstract`                  | Generate an abstract operation.                                                             |
| `domainInterface`           | Override the derived name. Empty or `-` generates no domain side at all.                    |
| `persistenceInterface`      | Likewise for the persistence side. Suppressing it also disables remoting.                   |
| `domainImplementation`      | Override; empty or `-` generates the interface without an implementation. Ignored if the domain side is dropped. |
| `persistenceImplementation` | Likewise for the persistence implementation.                                                |
| `overwrite`                 | Regenerate over existing files.                                                             |

### The order that works

1. **Look at a comparable operation first** and match its conventions — naming, profile, which sides
   it implements — rather than inventing new ones.
2. **Decide which sides you need.** Domain-only (`-DpersistenceInterface=-`) suits pure business logic
   with no database access of its own; persistence-only (`-DdomainInterface=-`) is rare. If in doubt,
   generate both, which is the default.
3. Run the goal and **read the log**: it prints one `generated …` line per file.
4. **Export new packages — after generating, never before.** If the operation is the first class in
   its package, add the `exports` to that module's `module-info.java`; no wizard goal touches it, and
   the build fails with *"package … is not visible"* until you do. It only works in this order:
   javac rejects an `exports` for a package with no compilation unit (`package is empty or does not
   exist`), and a `package-info.java` does not count as one.
5. Fill in the business logic, then `mvn install`. For a remotable operation, add
   `exports <persistenceImplPackage>.trip;` once that build has generated the delegates.

## Things that will trip you up

- **The goal fails, it does not warn.** Unlike `tentackle-wizard:pdo`, a missing `-Dcomment`, an
  unknown `-Dprofile` or an existing target file aborts the build. That is deliberate — read the
  message, it names what is wrong.
- **Existing files are never overwritten silently.** Re-running the goal fails and lists the files;
  `-Doverwrite=true` regenerates them from the templates and **destroys any business logic written
  into them**. Since an operation is not in the model, this guard is the only thing that notices a
  name is already taken — nothing checks whether an operation of that name exists elsewhere.
- **Remoting pulls in a delegate the wizard does not write.** With remoting enabled (the default in
  most projects), the generated persistence implementation references a
  `<persistenceImplPackage>.trip.<Name>RemoteDelegate`. No wizard goal ever creates that file — the
  wurblets do, on the first build in which the persistence implementation carries a remote-capable
  wurblet. For a PDO that happens by itself (its `MethodsImpl` anchor triggers it); a generated
  operation implementation has no anchor at all, so add one — `// @wurblet <tag> AssertRemote`, or the
  `RemoteMethod` blocks the operation needs anyway — and run `mvn install`. For an operation that is
  not remotable, generate with `-Dremote=false` instead.
- **One operation per invocation.** There is no batch directory and no way to list several names; call
  the goal once per operation.
- **Profile packages must already exist as directories.** The goal maps a package to a module by
  looking for its directory in the reactor's compile source roots; a package that exists nowhere
  aborts the build with `cannot determine module for package …`. This is the normal state of the
  first operation in a project scaffolded from the archetype: the `operation` profile is
  preconfigured, but nothing has ever been generated into its packages. Create each one — empty is
  fine, dot-files such as a `.gitignore` placeholder are ignored — in the module that owns it, e.g.

  ```bash
  mkdir -p myapp-pdo/src/main/java/com/example/myapp/pdo/operation \
           myapp-domain/src/main/java/com/example/myapp/domain/operation \
           myapp-persistence/src/main/java/com/example/myapp/persist/operation
  ```

  Exactly one module per package name: the same empty package in two modules is a split package and
  aborts with `empty split package detected`. Never invent a new package in a profile instead.
- **No class IDs.** Operations have none, so nothing here consumes the `.classid` status files the PDO
  wizard maintains.
- **This goal never touches a database.** It writes Java files. If the task needs data, that is the
  `browse` or `script` goal.
