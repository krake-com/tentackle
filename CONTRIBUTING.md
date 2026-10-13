# Contributing to Tentackle

Contributions are welcome — bug reports, documentation fixes, and code alike. Tentackle is
maintained by [Krake Softwaretechnik](https://krake.de/), and a framework of this scope lives
on its coherence, so please talk to us before investing time in a larger change.

## Reporting bugs and requesting features

Use the [GitHub issue tracker](https://github.com/krake-com/tentackle/issues). Search the
existing issues first.

A good bug report contains:

- the Tentackle version and the JDK version and vendor,
- the operating system,
- the database backend (H2, PostgreSQL, …) and its version, if persistence is involved,
- the tier setup, if remoting is involved: local or remote session, the transport
  (`trip`, `tripq`, `tripj`, …), and how many tiers,
- a minimal reproducer — ideally a failing test — or at least the complete stack trace.

For a feature request, describe the problem you are trying to solve, not only the solution
you have in mind.

## Reporting security issues

Please do **not** open a public issue for a suspected vulnerability. Send the details to
<harald@krake.de> instead, and allow a reasonable time for a fix before disclosing it.

## Before you start

- **Bug fixes and small documentation fixes** can go straight to a pull request.
- **Anything larger** — a new feature, an API change, a new module, a new dependency — should
  be discussed in an issue first. This saves you from writing code that cannot be merged.

Tentackle makes deliberate trade-offs. A change has to fit them, so it helps to know the
stance described in
[When to use Tentackle — and when not to](README.md#when-to-use-tentackle--and-when-not-to)
and [Correctness First](src/main/docs/correctness-first.md).
[ARCHITECTURE.md](ARCHITECTURE.md) gives the design overview, and the
[Table of Contents](src/main/docs/toc.md) lists every document, grouped by topic.

## Building

Requirements: **Java JDK >= 25** and **Maven >= 3.9.10** (Maven 3 and Maven 4 both work).
No database has to be set up — the tests run against H2.

```bash
# full build including all tests
mvn clean install

# a single module with its reactor dependencies
mvn -pl :tentackle-core -am clean install

# a single test class or method
mvn -Dtest=TestClassName test
mvn -Dtest=TestClassName#methodName test

# additionally run the tests that need a real PostgreSQL server (opt-in)
mvn -Ppostgres test
```

`tentackle-osgi/` is not part of the reactor. It is a separate build, run after
`mvn install` — see [osgi.md](tentackle-osgi/src/main/docs/osgi.md).

## Pull requests

- Branch off `develop` and open the pull request against `develop`. The older LTS lines
  (`java21`, `java17`, `java11`, `java8`) are maintained by backporting; that is done by the
  maintainer, so please do not target those branches.
- Keep a pull request to one topic. Unrelated cleanups belong in a pull request of their own.
- `mvn clean install` must pass.
- Add or adapt tests for what you change. TestNG is the test framework, and H2 is the backend
  for every test: a test must not depend on an external database unless it belongs to the
  `postgres` group.
- Write a short summary line that says what changed, in the style of the existing history,
  e.g. `added connect timeout to TcpConnectionPool`.

## Coding conventions

- Every Java source file starts with the LGPL header. Copy it from any existing file, such as
  [Holder.java](tentackle-core/src/main/java/org/tentackle/misc/Holder.java).
- Indent with 2 spaces, no tabs, and keep lines within about 120 columns. Otherwise follow
  the style of the surrounding code.
- Public API needs Javadoc.
- Use American English spelling in code comments and documentation ("behavior", "serialize",
  "color").
- Every module an application ships with has an explicit `module-info.java`. New packages
  need the matching `exports`, and `opens` where reflection is involved — see
  [modules.md](src/main/docs/modules.md).
- Do not edit generated code by hand. It lives in guarded regions woven into the sources by
  the Wurbelizer and is overwritten by the next build. Change the model or the wurblet
  instead — see [model-definition.md](tentackle-model/src/main/docs/model-definition.md)
  and [wurblets.md](tentackle-wurblets/src/main/docs/wurblets.md).
- FXML, CSS and `.properties` files live next to their controller class in `src/main/java`,
  not in `src/main/resources`.

## Documentation

The prose documentation is part of the repository and is expected to stay in sync with
the code.

- Project-wide documents live in `src/main/docs/`, and each module documents itself in
  `<module>/src/main/docs/`.
- When you add or change a concept, update the module document together with
  [toc.md](src/main/docs/toc.md) and [index.md](src/main/docs/index.md).
- A new document needs a file name that is unique across the whole reactor, because the
  website build flattens all documents into one directory. It also needs an entry in the
  `nav` of `src/mkdocs/mkdocs.yml`.
- Verify with `make build` in `src/mkdocs/`, which fails on broken links — see the
  [MkDocs README](src/mkdocs/README.md).
- The website makes no third-party requests. Do not add external assets, CDN links, or
  analytics.

## AI-assisted contributions

Contributions written with the help of an AI coding agent are welcome, on the same terms as
any other: you have read, understood and tested the change, and you stand behind it. A pull
request the author cannot explain will not be merged.

The repository is prepared for agents: [CLAUDE.md](CLAUDE.md) summarizes the architecture and
conventions, and [AI Agent Tooling Support](src/main/docs/ai-agents.md) describes the MCP
model server and the headless modes of the wizards.

## License

Tentackle is licensed under the [GNU Lesser General Public License, version 2.1](LICENSE).
By submitting a contribution, you agree that it is licensed under the same terms and confirm
that you have the right to contribute it.
