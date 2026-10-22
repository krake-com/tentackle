# Tentackle Update — The Application Self-Update Service

## Overview and Motivation

The `tentackle-update` module provides the building blocks for **auto-updating a deployed desktop client**. A
Tentackle application that is shipped as a per-user [jlink](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jlink.html)
or [jpackage](https://docs.oracle.com/en/java/javase/25/docs/specs/man/jpackage.html) image can ask its server
whether a newer version is available, download it, verify it, and replace itself in place — without an installer,
admin rights, or a separate update tool.

The module is intentionally small and split into a **server side** and a **client side** that talk to each other
over [TRIP](../../../../tentackle-core/src/main/docs/trip.md):

- The **server** publishes an [`UpdateService`](../java/org/tentackle/update/UpdateService.java) — a TRIP remote
  service that, given the calling client's [`ClientInfo`](../java/org/tentackle/update/ClientInfo.java), answers
  with an [`UpdateInfo`](../java/org/tentackle/update/UpdateInfo.java) describing the download URL, checksum, size,
  and the script to run.
- The **client** uses [`ClientUpdateUtilities`](../java/org/tentackle/update/ClientUpdateUtilities.java) to detect
  whether it runs from an updatable image, look up the remote service, download and checksum-verify the ZIP, and
  hand off to an external update script that swaps the files and restarts the application.

**The server is the single authority over every decision in an update.** It alone determines *which* image (the
target version) a given client is offered, the *URL* the image is downloaded from, and the *update script* that
performs the swap. The client never computes any of this — it merely reports who it is via `ClientInfo` and then
faithfully executes whatever the server returns. This indirection is deliberate and is what makes the mechanism
scale: because the download URL is dictated per request, the server can point a client at a global **CDN**, a
regional mirror, or a customer-local cache instead of streaming the (potentially hundreds of megabytes large) image
through the application server itself. For large, worldwide installations whose clients reach the data center only
through **SD-WANs** or **company-managed VPNs** — links that are typically bandwidth-constrained and metered — this
is essential: the lightweight `UpdateService` call still travels over the secured TRIP transport, while the bulk
download is steered onto whatever fast, geographically close, cacheable HTTP(S) endpoint the operator chooses.

The module deliberately contains **only the UI-neutral mechanics** (service contract, the data records, the
download/verification helpers, and the server-side service registration). The actual user-facing orchestration — the
"a new version is available, install it?" dialog, the unzip, and the process hand-off — lives one layer up in
[`tentackle-fx-rdc-update`](../../../../tentackle-fx-rdc-update/src/main/docs/fx-rdc-update.md), which is where the JavaFX desktop application
plugs everything together.

### Design principles

- **Server-authoritative updates.** The server decides everything: the target image/version, the download URL and
  the update script to execute. The client contributes only its `ClientInfo` and then trusts the returned
  `UpdateInfo` verbatim. Because the URL is chosen by the server per request, the actual bytes can be served from a
  CDN, a regional mirror, or a customer-local cache — decoupling the (heavy) download from the (light) control
  channel and keeping bulk traffic off bandwidth-constrained SD-WAN/VPN uplinks.
- **Remoting over TRIP.** `UpdateService` is a plain `Remote` interface, so the same call works whether the client
  is connected directly or through a middle-tier server. The reply (`UpdateInfo`) implements
  `ClientTransportCloseRequest` so the transport is closed gracefully after the answer — the client is about to
  terminate and restart, and this avoids socket-reset noise on the server.
- **No secrets in the payload.** `UpdateInfo` is *not* encrypted by itself. To protect it in transit, publish the
  service over the PSK-encrypted (pre-shared key) `tripe` transport rather than over TLS: a new server version may
  come with a new certificate that the outdated client does not trust yet. The download itself is plain HTTP(S)
  and is protected by a **SHA-256 checksum** rather than by transport encryption.
- **Convention-based artifact layout.** The server derives the download URLs purely from a naming convention
  (`<artifact>-<version>-<platform>-<arch>[-pkg].zip` plus a matching `.sha256` file) under a configurable base
  URL, so adding a new platform/architecture is a matter of publishing the right files — no server code changes.
  That base URL is exactly where CDN/mirror steering happens: point it at the edge instead of the data center, and
  every client is served locally without touching any other code.
- **In-place, per-user install.** Because the images are per-user and writable, the client can overwrite its own
  files. There is no shared installation and no privilege escalation.
- **Single-instance safety.** Updating a running image is only safe if exactly one instance owns the directory; the
  client layer enforces this with a PID lockfile (see *How It Fits Together*).

---

## Key Concepts

### The service contract

[`UpdateService`](../java/org/tentackle/update/UpdateService.java) is the entire remote API — a single method:

```java
UpdateInfo getUpdateInfo(ClientInfo info);
```

It extends [`Remote`](../../../../tentackle-core/src/main/docs/trip.md), marking it as a TRIP service that can be
registered on a transport and looked up by clients. The module `opens org.tentackle.update to org.tentackle.core`
precisely so TRIP can serialize the records that cross the wire.

### ClientInfo — what the client tells the server

[`ClientInfo`](../java/org/tentackle/update/ClientInfo.java) is the request record sent to the server:

| Field              | Meaning                                                                     |
|--------------------|-----------------------------------------------------------------------------|
| `application`      | the client application name                                                 |
| `version`          | the currently installed client version                                      |
| `platform`         | the operating system (e.g. `windows`, `macosx`, `linux`)                    |
| `architecture`     | the hardware architecture (e.g. `amd64`, `aarch64`)                         |
| `installationType` | [`JLINK`](../java/org/tentackle/update/InstallationType.java) or `JPACKAGE` |

A convenience constructor fills `platform` and `architecture` from the running JVM via
`StringHelper.getPlatform()`/`getArchitecture()`, so callers normally only pass application, version, and
installation type. `getPlatform()` keeps only the letters of the lowercased `os.name` (cut off at the version on
Windows), and `getArchitecture()` is the lowercased `os.arch`. These are the same values the
[jlink plugin](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md) uses for the classifier of the
ZIP files it creates, so the URL convention below matches the published artifacts. The record is annotated `@TripReferencable(NEVER)`/`@TripDeduplicatable(NEVER)` — it is a tiny
one-shot value, so TRIP serializes it directly without reference bookkeeping.

### UpdateInfo — what the server answers

[`UpdateInfo`](../java/org/tentackle/update/UpdateInfo.java) is the reply record:

| Field            | Meaning                                                                                                  |
|------------------|----------------------------------------------------------------------------------------------------------|
| `version`        | the new application version offered by the server                                                        |
| `urlStr`         | the URL of the downloadable ZIP (stored as a string, exposed via `getUrl()`)                             |
| `checksum`       | the SHA-256 hash used to verify the download                                                             |
| `size`           | the expected ZIP size in bytes (`-1` if the server could not determine it), used to drive a progress bar |
| `updateExecutor` | the file name of the script in the update's `bin` directory that performs the swap (`update.sh` / `update.cmd`) |

Every field here is the server's decision, not the client's. The `urlStr` in particular need not point at the
application server at all — it can name any HTTP(S) endpoint (CDN, mirror, local cache), so the heavy download is
routed independently of the control channel. The client's only "decision" is whether an update applies at all: it
compares `updateInfo.version()` against its own version and, if they differ, downloads exactly the URL, verifies
exactly the checksum, and runs exactly the executor the server named. `UpdateInfo` implements
[`ClientTransportCloseRequest`](../../../../tentackle-core/src/main/docs/trip.md)
and returns `true` from `isCloseAllTransportsRequested()` so the transport is torn down cleanly once the answer has
been received.

### InstallationType

[`InstallationType`](../java/org/tentackle/update/InstallationType.java) distinguishes the two supported per-user
image formats: `JLINK` (a raw jlink runtime image) and `JPACKAGE` (a jpackage application bundle). The two differ
in directory layout, which both the URL convention (the server appends `-pkg` for jpackage) and the file-system
probing on the client (the runtime lives in a different sub-path per OS) have to account for.

---

## Server Side

### ServerUpdateUtilities — registering the service

[`ServerUpdateUtilities`](../java/org/tentackle/update/ServerUpdateUtilities.java) is a `@Service` singleton
(reachable via `getInstance()`). Its one job is `startUpdateService(...)`:

```java
ServerUpdateUtilities.getInstance().startUpdateService(
    "tripe://0.0.0.0:33001/Update",          // TRIP URL the service is registered under
    "https://somehost/myapp/downloads",      // base URL holding the ZIPs and checksums
    UpdateServiceImpl.class);                // your AbstractUpdateServiceImpl subclass
```

It requests (or reuses) the [`Transport`](../../../../tentackle-core/src/main/docs/trip.md) for the given URI,
makes sure it is `accepting`, instantiates the service implementation and registers it under the URI's path.
The implementation class must provide a public constructor with a single `String` argument, which receives the
download base URL. Transport failures are wrapped in a `TripRuntimeException`, while a class that cannot be
instantiated raises a `TentackleRuntimeException`.

### AbstractUpdateServiceImpl — the default implementation

[`AbstractUpdateServiceImpl`](../java/org/tentackle/update/AbstractUpdateServiceImpl.java) implements
`getUpdateInfo()` against the URL naming convention; an application only supplies two pieces of information by
overriding two abstract methods:

```java
public class UpdateServiceImpl extends AbstractUpdateServiceImpl {
  public UpdateServiceImpl(String updateURL) {
    super(updateURL);
  }
  @Override public String getArtifactName() { return "myapp-jlink-client"; }
  @Override public String getVersion()      { return Version.RELEASE; }
}
```

Given a `ClientInfo`, the base implementation builds the download artifact name as:

```
<updateURL>/<artifactName>-<version>-<platform>-<architecture>[-pkg].zip
```

with a sibling `.sha256` file. (`-pkg` is appended for `JPACKAGE` installations.) A missing trailing slash of the
base URL is added automatically. It then:

1. **resolves the checksum** by downloading the `.sha256` file (`FileHelper.download`) and taking its first
   whitespace-delimited token, so the output of `sha256sum` can be published as is;
2. **resolves the size** by issuing an HTTP `HEAD` request (via `java.net.http.HttpClient`, following redirects)
   and reading `content-length`. Any status other than 2xx fails the request, so that the length of an error page
   is never taken for the size of the ZIP;
3. **chooses the executor** — `update.cmd` if the platform contains `win`, `update.sh` elsewhere;
4. returns the assembled `UpdateInfo` (always reporting `getVersion()` as the new version).

If the checksum or the size cannot be determined — typically because the artifact is not published — the call
fails with a `TentackleRuntimeException`, which TRIP passes on to the client.

The checksum hashes and the download sizes are cached in `ConcurrentHashMap`s keyed by URL, so repeated client
requests for the same artifact do not re-fetch the metadata. Only a known size is cached, though: a response
without `content-length` yields `-1` and is queried again on the next request. Every URL-building,
executor-naming, and `UpdateInfo`-construction step is a `protected` method, so subclasses can customize the
layout (different file extensions, per-customer artifacts, signed executors, …) without rewriting the flow.

---

## Client Side

### ClientUpdateUtilities

[`ClientUpdateUtilities`](../java/org/tentackle/update/ClientUpdateUtilities.java) is a `@Service` singleton
gathering everything the client needs:

- **Detecting an updatable image.** `isUpdateableJlinkApplication()`, `isUpdatableJPackageApplication()` and the
  combined `determineInstallationType()` (jlink first) probe the file system to decide whether the running
  application sits in a writable jlink/jpackage layout (returning `null` if not — meaning "this build cannot
  self-update"). The jlink check, `isUpdatableJlinkDirectory(File)`, verifies that the image directory and its
  standard subdirectories (`bin`, `conf`, `include`, `legal`, `lib`) exist and are writable; for the application
  itself, it checks the current working directory, since a jlink image is started from its root. The jpackage
  check first locates the package root (`determineJPackageRoot()`, derived from the path of the running
  executable) and then applies the jlink check to the embedded runtime:

  | OS      | Package root                         | Embedded runtime                   |
  |---------|--------------------------------------|------------------------------------|
  | Windows | the executable's directory (`app/`)  | `runtime`                          |
  | macOS   | two levels above `Contents/MacOS`    | `Contents/runtime/Contents/Home`   |
  | Linux   | the parent of `bin` (`lib/app/`)     | `lib/runtime`                      |

- **Looking up the service.** `getUpdateService(serviceName)` requests the TRIP transport for the URL and returns
  the remote `UpdateService` proxy. If a [`Cryptor`](../../../../tentackle-common/src/main/docs/common.md) is
  configured, the service URL may be given in encrypted form, disguised as an `http:`, `https:` or `rmi:` URL;
  `Cryptor.deriveURL(...)` decrypts it before the transport is requested. This keeps the real address out of
  plain-text configuration files.
- **Downloading and verifying.** `downloadZip(updateInfo, updateDir, progressConsumer)` creates the update
  directory (removing and recreating it if it already exists, so it is always empty), streams the ZIP into it
  through a [`CallbackReadableByteChannel`](../java/org/tentackle/update/CallbackReadableByteChannel.java) (so
  progress can be reported), then recomputes the **SHA-256** digest and compares it case-insensitively against
  `updateInfo.checksum()` (`sha256sum` writes lowercase, `certutil` uppercase). A missing checksum or a mismatch
  fails with a `TentackleRuntimeException`; only a verified file is returned.

### CallbackReadableByteChannel

[`CallbackReadableByteChannel`](../java/org/tentackle/update/CallbackReadableByteChannel.java) is a thin
`ReadableByteChannel` wrapper that, after every read returning data, feeds a `Consumer<Double>` the fraction
`bytesRead / expectedSize` (0.0–1.0). This is what drives the download progress bar. With a negative (unknown)
expected size, the reported value is negative as well, which a JavaFX progress bar displays as indeterminate.
The byte count is a `long`, so images larger than 2 GB are fine.

---

## Publishing an Update

The module does not create the downloadable artifacts — they come from the build and the operator:

- **The ZIP and the update script.** The [jlink plugin](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md#auto-update-feature)
  configured with `<withUpdater>true</withUpdater>` adds the platform-specific `update.sh`/`update.cmd`
  (from the templates `update.ftl` or `package-update.ftl`) to the image's `bin` directory and attaches the image
  as a ZIP with the classifier `<platform>-<architecture>` (plus `-pkg` for the jpackage goal). Deployed to a Maven
  repository, the ZIP is therefore named `<artifactId>-<version>-<platform>-<architecture>[-pkg].zip` — exactly the
  convention `AbstractUpdateServiceImpl` expects, with `getArtifactName()` returning the `artifactId` of the client
  image module. An `extraClassifier` breaks this convention and requires overriding `createBaseUrlName()`.
- **The checksum file.** The `.sha256` file is *not* generated by the build. Create it next to each ZIP, for example
  with `sha256sum myapp-jlink-client-1.2.0-linux-amd64.zip > myapp-jlink-client-1.2.0-linux-amd64.sha256`. Only the
  first token is read, and the case of the hex digits does not matter.
- **The location.** Publish both files below the server's `updateURL`. It may be any HTTP(S) server, CDN or
  mirror; redirects are followed.

The server caches the checksum and the size per URL. Republishing a *different* ZIP under the same version
therefore requires a server restart — or, better, a new version.

---

## How It Fits Together

The end-to-end flow, as wired up by [`tentackle-fx-rdc-update`](../../../../tentackle-fx-rdc-update/src/main/docs/fx-rdc-update.md):

1. **Server startup.** The application server reads its `updateService` and `updateURL`
   [backend properties](../../../../tentackle-session/src/main/docs/backend-properties.md) and calls
   `ServerUpdateUtilities.getInstance().startUpdateService(...)`, registering its `AbstractUpdateServiceImpl`
   subclass on a TRIP transport. The framework itself does not read these properties; the server generated by the
   [archetype](../../../../tentackle-project-archetype/src/main/docs/tentackle-project-archetype.md) does.
2. **Single-instance guard.** On launch, an `UpdatableDesktopApplication` detects via
   `ClientUpdateUtilities.determineInstallationType()` whether it runs from an updatable image and, if so, takes an
   exclusive PID lockfile — an in-place update must not race a second running instance.
3. **Trigger.** The update check is wired into login failure handling
   ([`LoginFailedWithUpdateHandler`](../../../../tentackle-fx-rdc-update/src/main/docs/fx-rdc-update.md)). When the client gets a
   `VersionIncompatibleException` (or an SSL handshake error caused by a server upgrade) *and* it runs from an
   updatable image, it builds a `ClientInfo` and calls `UpdateService.getUpdateInfo(...)` on the service named by
   its own `updateService` property.
4. **Decision.** If `updateInfo.version()` differs from the running version, the user is asked whether to install
   (with an optional countdown for unattended/kiosk setups). Applications may override this to update silently.
5. **Download & verify.** `ClientUpdateUtilities.downloadZip(...)` fetches the ZIP into an `update` directory
   (placed inside the package root for jpackage, or under the working directory for jlink) and verifies the
   SHA-256 checksum.
6. **Swap & restart.** The ZIP is unzipped, and the `updateExecutor` script (`update.sh`/`update.cmd`) from its
   `bin` directory is launched with the current PID (and, for jpackage, the path of the executable to restart).
   The script waits for this process to exit, overwrites the image files, and relaunches the application. The client
   then `System.exit(0)`s. Optionally, the encrypted username/password are handed to the new process via
   environment variables so the user does not have to log in again.

---

## Package Layout

| Package                        | Contents                                                                                                                                                                                                                                                                       |
|--------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `org.tentackle.update`         | The public API: the `UpdateService` contract, the `ClientInfo`/`UpdateInfo` records, `InstallationType`, the server-side `ServerUpdateUtilities` + `AbstractUpdateServiceImpl`, the client-side `ClientUpdateUtilities` and the `CallbackReadableByteChannel` download helper. |
| `org.tentackle.update.service` | The [`Hook`](../java/org/tentackle/update/service/Hook.java) (`ModuleHook`) providing resource-bundle/i18n access for the module.                                                                                                                                              |

---

## Module Dependencies

`tentackle-update` is a thin, high-level module:

- It **requires transitive** [`tentackle-core`](../../../../tentackle-core/src/main/docs/core.md) (and through it
  [`tentackle-common`](../../../../tentackle-common/src/main/docs/common.md)) for TRIP remoting, the `Cryptor`,
  `FileHelper`/`StringHelper` and the `@Service`/`ServiceFactory` SPI. The dependency is declared `optional` in the
  POM to avoid leaking it transitively onto downstream artifacts.
- It **requires** `java.net.http` for the server's HTTP `HEAD` request. The checksum file and the ZIP are
  downloaded via plain `java.net.URL` connections.
- It **opens** `org.tentackle.update` to `org.tentackle.core` so TRIP can serialize `ClientInfo`/`UpdateInfo`.
- It **provides** a `ModuleHook` service (`org.tentackle.update.service.Hook`), which loads resource bundles within
  the context of this module.

## Related Documentation

- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the remote invocation protocol carrying the update service calls.
- [tentackle-fx-rdc-update](../../../../tentackle-fx-rdc-update/src/main/docs/fx-rdc-update.md) — the JavaFX desktop layer that orchestrates the update UI, download, and restart.
- [Jlink/Jpackage plugin](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md#auto-update-feature) — creates the update scripts and the ZIP files.
- [Common](../../../../tentackle-common/src/main/docs/common.md) — `Cryptor`, `FileHelper`, `StringHelper` and the `ModuleHook` mechanism.
- [Services / ServiceFinder](../../../../tentackle-common/src/main/docs/services.md) — the SPI behind the `ServerUpdateUtilities`/`ClientUpdateUtilities` singletons.
