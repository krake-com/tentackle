# Backend Properties — The Session Configuration Keys

## Overview and Motivation

Every Tentackle tier — desktop client, application server, daemon, test — reads its connection
configuration from a properties file, by default named `backend.properties`. The file answers one
question: *how does this JVM reach its persistence layer?* For a client that may be a TRIP URL to a
server; for a server or a two-tier client it is a JDBC URL to the database — swapping one for the
other is the whole point of [location transparency](../../../../src/main/docs/tiers.md).

The file travels inside a [`SessionInfo`](../java/org/tentackle/session/SessionInfo.java) as
[`EncryptedProperties`](../../../../tentackle-common/src/main/java/org/tentackle/common/EncryptedProperties.java),
so all keys below are **session properties**. Most of them configure the backend connection, some
configure the server, and a few are interpreted by the application layer. This document is the
reference for all of them.

Four general rules:

- **Keys are case-insensitive.** `jdbcIdleTimeout`, `jdbcidletimeout` and `JDBCIDLETIMEOUT` are the
  same key (camelCase is the convention).
- **Values may be encrypted.** A value starting with `~` is decrypted via the application's
  [Cryptor](../../../../tentackle-common/src/main/docs/cryptor.md) — passwords never appear in
  clear text in a packaged image.
- **Keys may target the JVM.** A key written as `^foo` (or `SYSTEM_foo`) is not stored as a session
  property but *set as the system property* `foo` — used, for example, to point
  `javax.net.ssl.keyStore` at the image's keystore. See
  [Application Bootstrap](../../../../tentackle-pdo/src/main/docs/application.md#well-known-properties).
- **Values may reference system properties.** Applications built on the
  [`Application` hierarchy](../../../../tentackle-pdo/src/main/docs/application.md) run every value
  through `StringHelper.evaluate`, which replaces placeholders by the value of the system property
  of that name:

  | Placeholder         | Replaced by                                                                                     |
  |---------------------|-------------------------------------------------------------------------------------------------|
  | `@{name}`           | the value of `name`; left unchanged (`@{name}`) if there is no such property                    |
  | `@{name?default}`   | the value of `name`, or `default` if the property is missing or blank                           |
  | `@{name!default}`   | the value of `name`, or `default` only if the property is missing                               |
  | `${name}`, `${name?default}`, `${name!default}` | the same, but placeholders within the value of `name` are replaced as well (recursively) |

  A backslash quotes the next character, e.g., `\@{` for a literal `@{`. Prefer `@{…}` for file
  paths: with `${…}`, backslashes in a Windows path would be taken as quotes. Installations built with the
  [jpackage goal](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md#how-the-jpackage-goal-works)
  get the system property `RUNTIMEDIR` pointing at the runtime directory, while a plain jlink image is
  started from its root directory; hence the `@{RUNTIMEDIR?.}` in the [examples](#examples) below.

## How the File Is Found

The properties name defaults to `backend` (the `.properties` extension is appended), taken from
[`SessionInfo.getPropertiesName()`](../java/org/tentackle/session/SessionInfo.java). Applications
built on the [`Application` hierarchy](../../../../tentackle-pdo/src/main/docs/application.md)
accept `--backend=<name>` on the command line to select a different file — this is how the
[generated project](../../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md)
switches between a direct database connection and a remote server connection.

The name is resolved by `PropertiesUtilities` through the pluggable `PropertiesResourceService`
providers; the default provider tries the file system first and falls back to a classpath/module
resource. Command-line properties take precedence over properties from the file.

## Connection Keys

Read by [`BackendInfo`](../../../../tentackle-sql/src/main/java/org/tentackle/sql/BackendInfo.java)
and [`Db`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/Db.java) when the session
opens.

| Key               | Value                                                                                                                                                                                                                   |
|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `url`             | Where the persistence layer lives. Three forms, see below.                                                                                                                                                              |
| `user`            | The username for the *technical* database connection (not necessarily the application user logging in).                                                                                                                 |
| `password`        | The password, usually `~`-encrypted.                                                                                                                                                                                    |
| `schemas`         | Optional comma-separated list of database schemas.                                                                                                                                                                      |
| `driver`          | Optional JDBC driver to load at runtime: `<driver-class>:<jar-url>`. Registers the driver via a `URLClassLoader`, for drivers that are not on the module path (e.g. added to an already-shipped image).                 |
| `jdbcIdleTimeout` | Optional connection inactivity timeout in **minutes**, `0` (default) = none. Some managed/cloud databases close idle connections no matter what; when set, a dummy `SELECT` revives a connection idle longer than this. |
| `jdbcKeepAlive`   | Boolean, default `false`. Only meaningful with `jdbcIdleTimeout`: `true` sends the dummy `SELECT` *periodically* on idle connections; `false` sends it only when an idle connection is re-used.                         |
| `batchsize`       | Enables [JDBC statement batching](../../../../tentackle-database/src/main/docs/batching.md); off by default.                                                                                                            |
| `idsource`        | Selects a non-default [id source](../../../../tentackle-database/src/main/docs/database.md#id-sources) configuration.                                                                                                   |
| `jta`             | `none` (default), `join` or `required`: how the session takes part in [global (JTA/XA) transactions](../../../../tentackle-jta/src/main/docs/jta.md#the-two-modes). Requires the module `tentackle-jta`.               |
| `backend`         | Redirects the *technical* connection settings to another source — see [Redirecting the Backend](#redirecting-the-backend-user-system-and-named-files) below.                                                            |
| `application`     | Overrides the application name (normally derived from the main class). Needed in tests that resolve `@user`/`@system` backend configurations, which are stored per application name.                                    |

### The three forms of `url`

1. **`jdbc:...`** — a direct JDBC connection; the matching
   [backend](../../../../tentackle-sql/src/main/docs/sql.md) (SQL dialect) is derived from the URL.
2. **`jndi:<name>`** — a `DataSource` looked up via JNDI, typical inside containers; the backend is
   derived from the datasource's connection.
3. **A TRIP URI** — anything else, e.g. `trips://myserver:18000/MyApp`: no local database at all, the
   session is *remote* and all persistence operations are delegated to the server at that URI. The
   scheme selects the [transport](../../../../tentackle-core/src/main/docs/trip.md): `trip` (plain
   TCP), `trips` (SSL), `tripe` (pre-shared key encryption), `tripc` (compressing), `tripcs`
   (compressing + SSL), `tripce` (compressing + pre-shared key encryption), and QUIC with
   [tentackle-quic](../../../../tentackle-quic/src/main/docs/quic.md) (`tripq`/`tripcq`) or
   [tentackle-quiche](../../../../tentackle-quiche/src/main/docs/quiche.md) (`tripj`/`tripcj`).

For the `jdbc:` and `jndi:` forms, the URL may carry a `|<BackendName>` suffix to pin the SQL
dialect explicitly when it cannot (or should not) be derived — e.g.
`jndi:jdbc/mydb|PostgreSQL`, or `jdbc:postgresql://host:26257/mydb|CockroachDB`, whose URL alone would
select PostgreSQL. The names are those of the registered backends: `PostgreSQL`, `CockroachDB`, `H2`,
`MariaDB`, `MySQL`, `MsSQL`, `Oracle`, `Oracle8`, `Oracle21`, `Oracle23`, `DB2`, `HANA`, `Firebird`, `Informix`.

### Redirecting the Backend: `@user`, `@system`, and Named Files

By default, the technical connection settings come from the session properties themselves. The
`backend` key redirects them:

| Value                             | Effect                                                                                                                                                                                                                                                                                                                                                                                                                                        |
|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@user` / `@system`               | The [login dialog](../../../../tentackle-fx-rdc/src/main/docs/rdc.md#application-lifecycle-app-login) lets the user manage named backend configurations (URL, credentials, optional runtime-loaded driver) through the UI and pick one at login. They are stored as `BackendConfiguration`s in the **user** (`@user`) or **system** (`@system`) [preferences](../../../../tentackle-core/src/main/docs/preferences.md), per application name. |
| `@user:<name>` / `@system:<name>` | Loads the backend configuration `<name>` directly from the user/system preferences — no dialog interaction. Falls back to the session properties (with a logged warning) if no such configuration exists.                                                                                                                                                                                                                                     |
| `<propertiesName>`                | Loads the technical settings from another properties resource — e.g. to keep technical credentials separate from the session configuration.                                                                                                                                                                                                                                                                                                   |

If the key is missing, the session properties (and possibly user input from the login dialog) are
used as-is.

## Server Keys

Read when the tier is an application server (see [Multi-Tier Cascade](../../../../src/main/docs/tiers.md)):
the TRIP keys by [`TripServer`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/trip/TripServer.java),
the transaction keys by [`DbTransactionFactory`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/DbTransactionFactory.java).

| Key                          | Value                                                                                                                                                                                                                                                   |
|------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `service`                    | The TRIP endpoint the server listens to, as a URI: the scheme selects the transport, the path the connection class registered at the `Registry`, and query parameters are transport-specific — e.g. `trips://myserver:18000/MyApp?idle=10&backlog=100`. |
| `service_<name>`             | Additional endpoints if the server listens on more than one transport, e.g. `service_plain`, `service_ssl`, `service_quic` — each with its own URI.                                                                                                     |
| `connectionclass`            | The fully qualified name of the `RemoteDbConnection` class to export; usually provided programmatically.                                                                                                                                                |
| `timeout`                    | The session timeout for dead client connections, in polling intervals. Defaults to `30` (i.e. 30 seconds at the default interval).                                                                                                                      |
| `timeoutinterval`            | The polling interval for dead sessions in milliseconds. Defaults to `1000`; `0` turns the cleanup thread off entirely (risky).                                                                                                                          |
| `txIdleInterval`             | The polling interval of the [transaction supervisor](../../../../tentackle-database/src/main/docs/database.md#idle-transaction-supervisor) in milliseconds. Defaults to `1000`.                                                                          |
| `txIdleTimeout`              | The number of polling intervals after which an idle database transaction is forcibly rolled back and its connection closed. Defaults to `60` (i.e. one minute at the default interval); `0` or less disables the timeout. |
| `updateService`, `updateURL` | Publish the [self-update service](../../../../tentackle-update/src/main/docs/update.md) for jlink-based clients. Not read by the framework itself, but by the server generated from the [archetype](../../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md), and `updateService` also by the client's [update handler](../../../../tentackle-fx-rdc-update/src/main/docs/fx-rdc-update.md). |

> **Three different timeouts.** `timeout` is the *TRIP session* timeout — how long a server keeps a
> dead client session around. `jdbcIdleTimeout` is the *database connection* inactivity timeout on
> the JDBC side. `txIdleTimeout` limits how long a *transaction* may sit idle while holding its
> connection and locks. They are unrelated; a typical server relies on all three.

### Deriving service URIs

When a server listens on several transports, the endpoints usually differ only slightly — the same
host, a neighboring port, the same application path. Rather than spelling out (or externally
configuring, e.g., from a Maven profile) a full URI for every `service_<name>`, you can **derive** it
from the default `service` URI with a small expression language. It is provided by the general-purpose
[`UriHelper.expand`](../../../../tentackle-core/src/main/java/org/tentackle/io/UriHelper.java) and can
therefore be reused to derive any URI from another, not just TRIP service endpoints.

A variable is written as `$name`, or as `${expression}` when arithmetic is needed. The following
components of the default `service` URI are available (names are case-insensitive):

| Variable    | Value from the default `service` URI                |
|-------------|-----------------------------------------------------|
| `scheme`    | the URI scheme, e.g. `trips`                        |
| `userinfo`  | the user-info part, if any                          |
| `host`      | the host                                            |
| `port`      | the port (numeric)                                  |
| `authority` | the full authority (`userinfo@host:port`)           |
| `path`      | the path *without* its leading `/`, e.g. `MyApp`    |
| `rawpath`   | the path *including* its leading `/`, e.g. `/MyApp` |
| `query`     | the query string without the leading `?`, if any    |
| `fragment`  | the fragment, if any                                |

Inside `${...}` an arithmetic expression over the numeric components and integer literals may be
used, with the operators `+`, `-`, `*`, `/` and `%`, the usual operator precedence and parentheses
for grouping, e.g. `${port+1}` or `${(port+1000)/2}`. It is evaluated with integer semantics.
A literal `$` is escaped with a backslash (`\$`).

```properties
service=trips://myserver:18000/MyApp?idle=10&backlog=100

# derived: same host and path, next port, compressed transport
service_compressed=tripcs://$host:${port+1}/$path?$query
```

Here `service_compressed` expands to `tripcs://myserver:18001/MyApp?idle=10&backlog=100`.
Referencing a variable while no default `service` is defined — or referencing a component the default
URI does not carry (e.g. `$query` when it has no query string) — is a configuration error and fails
startup. The default `service` URI itself is always used verbatim; only the derived `service_<name>`
entries are expanded.

## Desktop Client Keys

Interpreted by the [Rich Desktop Client](../../../../tentackle-fx-rdc/src/main/docs/rdc.md).

| Key              | Value                                                                                                                                                                                                                                                                                                                                            |
|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `autologin`      | Log the user in with the `user`/`password` from the properties without asking for credentials. **The mere presence of the key enables it** (even `autologin=false`!) — remove the key to disable. A failed autologin terminates the application instead of offering retries.                                                                     |
| `authentication` | Name of the authentication method, i.e. of the [`CredentialsProvider`](authentication.md#the-client-side-obtaining-the-credentials) used to obtain the credentials before the session is opened. Defaults to `password`. An interactive method (single sign-on) replaces the username and password fields of the login view by a sign-in button. |
| `noRdcCache`     | The presence of the key disables the RDC's GUI caching — useful while editing database-backed bundles with [BundleMonkey](../../../../tentackle-i18n/src/main/docs/bundlemonkey.md), so changes become visible immediately.                                                                                                                      |

## Application-Level Keys

A further set of well-known keys — `notracker`, `nosecurity`, `statistics`, `scripting`, `locale`,
`readonlyprefs`, `systemprefs`, `noprefsync` — is interpreted by the application bootstrap itself
and documented in
[Application Bootstrap — Well-known properties](../../../../tentackle-pdo/src/main/docs/application.md#well-known-properties).

## Examples

A two-tier client or server connecting directly to the database:

```properties
url=jdbc:postgresql://dbhost/myapp?sslmode=require
user=myapp
password=~mkfII6HTvbc3IcS2q9t7bA==
schemas=myapp
jdbcIdleTimeout=4
jdbcKeepAlive=true
batchsize=100

# server endpoint and self-update service
service=trips://myserver:18000/MyApp?idle=10
updateService=trips://myserver:18000/UpdateService
updateURL=https://myserver/updates
timeout=30

^tentackle.ssl.keyStore=@{RUNTIMEDIR?.}/conf/keystore.p12
^tentackle.ssl.keyStorePassword=~o93jd2iJdqw2Mss3uAA71g==
```

A three-tier desktop client connecting to that server, with user-managed backends:

```properties
url=trips://myserver:18000/MyApp
backend=@user
^tentackle.ssl.trustStore=@{RUNTIMEDIR?.}/conf/truststore.p12
^tentackle.ssl.trustStorePassword=~x0PoR8fFPqmboo3sJ11w7A==
```

## Related Documentation

- [Tentackle Session](session.md) — the session abstraction the properties configure.
- [Authentication](authentication.md) — the `authentication` key: what a credentials provider is and how a login is verified.
- [Application Bootstrap](../../../../tentackle-pdo/src/main/docs/application.md) — how properties files, command-line options and system properties come together at startup.
- [Cryptor and EncryptedProperties](../../../../tentackle-common/src/main/docs/cryptor.md) — the `~`-encryption of secrets in these files.
- [Tentackle SQL](../../../../tentackle-sql/src/main/docs/sql.md) — `BackendInfo` and the backend (dialect) abstraction behind the `url` key.
- [Tentackle Database](../../../../tentackle-database/src/main/docs/database.md) — the engine consuming the connection keys; id sources and diagnostics.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the remoting protocol and its transports behind the TRIP URI forms.
- [Multi-Tier Cascade](../../../../src/main/docs/tiers.md) — why the same file format describes every tier.
- [MyApp Walkthrough](../../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md) — the generated `backend.properties` files in a real project.
