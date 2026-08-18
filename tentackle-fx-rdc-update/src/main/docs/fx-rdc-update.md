# Tentackle FX RDC Update — Auto-Update for the Rich Desktop Client

## Overview and Motivation

The `tentackle-fx-rdc-update` module is the **JavaFX desktop integration** of the application self-update feature.
It is the layer that turns the transport-neutral mechanics of [`tentackle-update`](../../../../tentackle-update/src/main/docs/update.md)
into an actual user experience: detect that the running client is outdated, ask the user whether to install the new
version, show a download/installation progress bar, swap the image and restart — all without a separate installer
or admin rights.

It does this by extending two extension points of the Rich Desktop Client framework
([`tentackle-fx-rdc`](../../../../tentackle-fx-rdc/src/main/docs/rdc.md)):

- [`UpdatableDesktopApplication`](../java/org/tentackle/fx/rdc/update/UpdatableDesktopApplication.java) — a drop-in
  base class for the application's main class that adds single-instance protection and wires in the update-aware
  login-failure handler.
- [`LoginFailedWithUpdateHandler`](../java/org/tentackle/fx/rdc/update/LoginFailedWithUpdateHandler.java) — a
  specialized `LoginFailedHandler` that interprets a version/handshake failure at login as "the client is too old"
  and drives the whole download-and-restart flow.

The module deliberately holds **only the FX-specific orchestration and its localized messages**. The service
contract, the data records (`ClientInfo`/`UpdateInfo`), the download/checksum helpers and the server-side service
registration all live in [`tentackle-update`](../../../../tentackle-update/src/main/docs/update.md); this module
consumes them.

### Design principles

- **Update-on-login, not background polling.** The natural moment to discover an incompatible version is when the
  client tries to log in to a server that has since been upgraded. Rather than poll, the module hooks the
  *login-failure* path: a [`VersionIncompatibleException`](../../../../tentackle-session/src/main/docs/session.md)
  (or an SSL handshake error caused by a new server certificate) becomes the trigger to check for an update.
- **Only self-update when self-update is possible.** Everything is gated on
  `ClientUpdateUtilities.determineInstallationType()` returning non-null — i.e., the client really runs from an
  updatable, writable jlink/jpackage image. Otherwise, the user is told to contact their administrator.
- **User in control, with an unattended escape hatch.** By default, the user is asked before installing, with a
  short countdown that auto-confirms — convenient for kiosks/dashboards — but the relevant methods are `protected`
  so an application can update silently.
- **Seamless restart.** Where a `Cryptor` is available, the current (encrypted) credentials are passed to the
  relaunched process via environment variables so the user does not have to log in again after the update.

---

## Key Concepts

### UpdatableDesktopApplication

[`UpdatableDesktopApplication<C>`](../java/org/tentackle/fx/rdc/update/UpdatableDesktopApplication.java) extends the
RDC `DesktopApplication<C>` (where `C` is the main controller type) and adds two things:

1. **It installs the update-aware login handler.** It overrides `createLoginFailedHandler(view, sessionInfo)` to
   return a [`LoginFailedWithUpdateHandler`](../java/org/tentackle/fx/rdc/update/LoginFailedWithUpdateHandler.java)
   instead of the plain RDC handler.
2. **It enforces a single running instance** (`assertNotAlreadyRunning()`, called from `initialize()`). An in-place
   update must not race a second instance writing the same image, so when the application runs from an updatable
   directory, it acquires an exclusive lock on a per-application lockfile (`<appname>.lck`) and writes its PID into
   it. If the lock is already held, it reads and reports the offending PID and refuses to start. The `FileChannel`
   is kept open (and referenced) for the whole lifetime of the process so the lock survives.
   The lockfile lives in the installation root — the jlink image root, or the package root determined by
   `determineJPackageRoot()` for jpackage, whose launchers do not necessarily start the application in its own
   installation directory (`createLockFilePath(...)`).

   The check is a no-op for non-updatable installations (normal jar/classpath launches), so the same application
   class works in development and in a packaged deployment.

Applications simply make their main class extend `UpdatableDesktopApplication` instead of `DesktopApplication` to
opt into auto-update.

### LoginFailedWithUpdateHandler

[`LoginFailedWithUpdateHandler`](../java/org/tentackle/fx/rdc/update/LoginFailedWithUpdateHandler.java) is the heart
of the module — a `LoginFailedHandler` whose `handle(Exception)` decides whether a failed login is actually an
out-of-date client:

- It reacts only to a `VersionIncompatibleException` or an `SSLHandshakeException` (extracted from the exception
  chain) — the latter because a server upgrade may rotate its TLS certificate.
- It proceeds only if `determineInstallationType()` reports an updatable image **and** a remote `UpdateService` is
  configured (via the application's `updateService` property). Otherwise, it either delegates to the standard
  handler or, when the image is not updatable, terminates with a localized "outdated, call your administrator"
  message.
- It builds a [`ClientInfo`](../../../../tentackle-update/src/main/docs/update.md) from the application name,
  version and installation type, calls `UpdateService.getUpdateInfo(...)` and — if the returned version differs —
  returns a `Runnable` that performs the update.

Its responsibilities, all overridable, are:

| Method                               | Purpose                                                                                          |
|--------------------------------------|--------------------------------------------------------------------------------------------------|
| `handle(Exception)`                  | Recognize the outdated-client situation and kick off the flow.                                   |
| `createUpdateService()`              | Look up the remote `UpdateService` from the `updateService` application property (null if none). |
| `createUpdateRunnable(...)`          | Decide whether to install — by default asks the user; override for silent updates.               |
| `showUpdateDialog(...)`              | Show the "install version X?" notification with Yes/No buttons.                                  |
| `createCountdownTimeline(...)`       | A 3-second countdown that auto-confirms the install; return `null` to disable.                   |
| `createUpdateDirectory(...)`         | Choose where to download/unzip — the package root for jpackage, `./update` for jlink.            |
| `update(...)`                        | Download, verify, unzip, launch the update script and exit.                                      |
| `passSessionInfoViaEnvironment(...)` | Hand the encrypted credentials to the restarted process for auto-login.                          |
| `terminate(...)`                     | Show an error/info and close the window.                                                         |

The `update(...)` method is where the hand-off happens: it calls
[`ClientUpdateUtilities.downloadZip(...)`](../../../../tentackle-update/src/main/docs/update.md) (reporting
progress through `showApplicationStatus`), unzips the archive with `FileHelper.unzip(...)`, makes the
`updateExecutor` script (`update.sh`/`update.cmd` in the archive's `bin` directory) executable and launches it with
`ProcessBuilder`. It passes the current PID (so the script can wait for this process to exit before overwriting its
files — required on Windows) and, for jpackage, the path of the executable to restart. Finally, it
`System.exit(0)`s so the script can replace the running image.

### UpdateFxRdcBundle and localization

[`UpdateFxRdcBundle`](../java/org/tentackle/fx/rdc/update/UpdateFxRdcBundle.java) is the `@Bundle`-annotated
accessor for the module's localized messages, resolved through the `BundleFactory`. The bundle ships English
(`UpdateFxRdcBundle.properties`) and German (`UpdateFxRdcBundle_de.properties`) variants and supplies the user-
facing strings of the update flow: `DOWNLOADING`, `INSTALLING_{0}`, `UPDATE_COMPLETE`, `UPDATE_FAILED`,
`{0}_{1}_OUTDATED_CALL_ADMIN` and `{0}_{1}_OUTDATED_INSTALL_{2}`. The build verifies bundle completeness for the
`en_US` and `de_DE` locales via the `bundles` goal of the `tentackle-check-maven-plugin`.

---

## How It Fits Together

The client-side half of the end-to-end update flow (the server side is set up via
[`ServerUpdateUtilities`](../../../../tentackle-update/src/main/docs/update.md)):

1. **Launch.** The application's main class extends `UpdatableDesktopApplication`. On `initialize()`, if it runs
   from an updatable image, it takes the PID lockfile to guarantee it is the only instance.
2. **Login fails with a version mismatch.** The user tries to connect to an upgraded server and gets a
   `VersionIncompatibleException` (or a TLS handshake error). The RDC framework routes this to
   `LoginFailedWithUpdateHandler.handle(...)`.
3. **Check.** If the image is updatable and an `updateService` property is configured, the handler asks the remote
   `UpdateService` for the matching `UpdateInfo`.
4. **Confirm.** If a newer version is offered, the user is shown an "install version X?" dialog (with the
   auto-confirm countdown). Unattended applications can override this to proceed silently.
5. **Install.** `ClientUpdateUtilities.downloadZip(...)` downloads and SHA-256-verifies the ZIP into the update
   directory; the handler unzips it and launches the bundled update script with the current PID (and credentials
   for auto-login), then exits so the script can overwrite the image and restart it.
6. **Not updatable.** If the client is outdated but cannot self-update (e.g., a plain jar launch), it shows the
   "contact your administrator" message and stops.

---

## Package Layout

| Package                               | Contents                                                                                                                                 |
|---------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------|
| `org.tentackle.fx.rdc.update`         | The public API: `UpdatableDesktopApplication`, `LoginFailedWithUpdateHandler` and the `UpdateFxRdcBundle` message accessor.              |
| `org.tentackle.fx.rdc.update.service` | The [`Hook`](../java/org/tentackle/fx/rdc/update/service/Hook.java) (`ModuleHook`) providing resource-bundle/i18n access for the module. |

---

## Module Dependencies

`tentackle-fx-rdc-update` is a thin glue module bridging the desktop client and the update mechanics:

- It **requires transitive** [`tentackle-fx-rdc`](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) — the Rich
  Desktop Client framework whose `DesktopApplication` and `LoginFailedHandler` it extends. The dependency is
  declared `optional` in the POM to avoid forcing it transitively onto downstream artifacts.
- It **requires transitive** [`tentackle-update`](../../../../tentackle-update/src/main/docs/update.md) — the
  `UpdateService` contract, the `ClientInfo`/`UpdateInfo` records and the `ClientUpdateUtilities` it drives.
- It **provides** a `ModuleHook` service (`org.tentackle.fx.rdc.update.service.Hook`) for i18n bundle resolution.

## Related Documentation

- [Tentackle Update](../../../../tentackle-update/src/main/docs/update.md) — the underlying service contract, data records and download/checksum helpers.
- [FX RDC](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) — the Rich Desktop Client framework providing `DesktopApplication` and the login-handling extension points.
- [FX](../../../../tentackle-fx/src/main/docs/fx.md) — the JavaFX binding layer beneath the RDC.
- [Session](../../../../tentackle-session/src/main/docs/session.md) — `VersionIncompatibleException` and `SessionInfo`, the credentials carried into the relaunched process.
