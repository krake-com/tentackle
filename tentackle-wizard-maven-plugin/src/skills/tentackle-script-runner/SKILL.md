---
name: tentackle-script-runner
description: Run Groovy or Ruby code against a live Tentackle backend headless with "mvn tentackle-wizard:script -Dscript=file". Use whenever the task is to query or inspect persisted data, check what the application actually does at runtime, apply an ad-hoc data fix or a one-off migration, or find the object ids for a fixture — anything that needs the domain model against a real database rather than a test.
---

# Running application code with the script goal

`tentackle-wizard:script` runs a script against a live backend **with the application's dependencies on
the classpath**, so it can use `on()` and `op()` exactly like application code. It answers questions the
sources cannot: what is actually in the database, what an operation really does, how many rows a change
would touch.

Interactively it is a JavaFX editor. **Batch mode reads the code from a file and runs it once**, failing
the build if compilation or execution fails.

## Before you start

- Run from the **reactor root**; the goal is an aggregator.
- A **reachable backend** is required. `url`, `user` and `password` are plugin parameters, normally
  configured in the POM. Look at which database that is *before* running anything that writes.
- The **scripting-language provider must be on the plugin classpath** — `tentackle-script-groovy`,
  `tentackle-script-ruby` or `tentackle-script-jsr` as a `<dependency>` of the wizard plugin. It is not
  there by default. If it is missing, say so instead of trying to work around it.
- At least one **harness** must exist (see below). Harnesses are provided by the project; if there is
  none, ask rather than inventing one.

## Fragments and harnesses

The code you write is only a **fragment**. It is woven into a **harness**: a FreeMarker template that
owns the boilerplate — it implements a `DomainContextProvider` so `on()` and `op()` resolve, and defines
the `run()` method your fragment is inserted into. The harness's first line is a she-bang selecting the
language (`#!groovy`, `#!ruby`, …) and is stripped before the script is generated.

Harnesses live in the `script/` category of the template directory (`templates/script/` by default).
`-Dharness=<filename>` picks one; it may be omitted when exactly one exists.

Because the fragment lands *inside* `run()`, **it cannot contain import statements**. Mark import lines
with a leading `^` instead: the weaver strips the `^` and places the line at the top level of the
generated script. This is language-agnostic — in Ruby, `^require 'json'` becomes a top-level `require`.

```groovy
^import org.tentackle.misc.*
^import java.time.LocalDate

def cutoff = new Timestamp(LocalDate.now().minusDays(30))
on(Order.class).selectAll().each { order ->
  if (order.isOpen() && order.getCreated().before(cutoff)) {
    println "${order.getId()}\t${order.getOrderNumber()}\t${order.getCustomer().getName()}"
  }
}
```

## How to use it

```bash
mvn tentackle-wizard:script -Dscript=src/scripts/list-open-orders.groovy
```

- `-Dscript=<file>` — the fragment file, relative to the project base directory. **Supplying it is
  what selects batch mode**; without it the interactive editor opens, which is useless to you.
- `-Dharness=<filename>` — the harness to weave into, filename only, no directory. Required when the
  project configures more than one; an unknown name fails the goal.

`println` output lands in the Maven log. Write the fragment to a file under a scratch or scripts
directory, not to the module sources — it is not part of the build.

## Things that will trip you up

- **This is the one tool here that can change data.** It runs with the configured credentials and does
  whatever it says. Before running anything that writes: confirm the target backend, and prefer showing
  the user a read-only version of the script first. Treat a write script like a migration.
- **Start read-only.** Count and print before you update. A `selectAll()` with a `println` is a cheap way
  to confirm the fragment selects what you think it selects.
- **Imports need the `^`.** A plain `import` line inside the fragment is a syntax error inside `run()`,
  and the error message points at the *generated* script, not at your file.
- **Line numbers in errors refer to the woven script.** Compilation and runtime errors carry the
  positions of the generated source, which is the harness plus your fragment. Count from the harness's
  `run()` method, or run with `-X` to see the generated script in the debug log.
- **There is no interrupt in batch mode.** The *Interrupt* button exists only in the editor. A runaway
  loop has to be killed with the build. Bound your queries.
- **The session is already current and a modification tracker is running.** Do not create your own
  session; use `on()` and `op()` as the harness set them up.
- **Failure fails the build.** A compilation or execution error ends the goal with
  `script execution failed for harness '…'`. Partial writes made before the error are *not* rolled back
  unless the fragment itself ran in a transaction.
- **Use it to feed the other tools.** Printing object ids here is the normal way to find the arguments
  for `mvn tentackle-wizard:browse -Dpdo=…`.
