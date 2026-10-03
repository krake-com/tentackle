# OS Credential Stores — the tentackle-keyring module

## Overview

Every desktop OS has a credential store built in. It binds the secrets to the logged-in user and
encrypts them with a key that the user's login unlocks. Java has no API for it. The JDK's `KeychainStore`
and `Windows-MY` key stores hold only keys and certificates, not arbitrary secrets, and nothing covers Linux.

`tentackle-keyring` puts these stores behind the [keyring API of tentackle-core](../../../../tentackle-core/src/main/docs/keyring.md).
It is **opt-in**. Add the dependency, and the application's secrets go to the OS instead of the
preferences-based default. Application code doesn't change:

```xml
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-keyring</artifactId>
</dependency>
```

- Module: `org.tentackle.keyring`
- Artifact: `org.tentackle:tentackle-keyring`
- Dependencies: `tentackle-core` only, and no third-party runtime libraries at all
- License: LGPL 2.1

The native APIs are called directly through the Java FFM API (`java.lang.foreign`), the same way
[tentackle-quiche](../../../../tentackle-quiche/src/main/docs/quiche.md) does. There is no JNA, no JNI, and
nothing to extract: the libraries are part of the OS.

### Why not a library from Maven Central?

There are some, but none fits a modular, LGPL-licensed framework:

| Library                                                  | Why not                                                                                      |
|----------------------------------------------------------|----------------------------------------------------------------------------------------------|
| `com.github.javakeyring:java-keyring`                    | JNA plus dbus-java plus a Secret Service client; not JPMS-native, barely maintained.         |
| `com.microsoft.azure:msal4j-persistence-extension`       | JNA; designed around MSAL token caches (file + lock), not a general secret API.              |
| `org.cryptomator:integrations-*`                         | The closest in spirit (JPMS, SPI), but the platform implementations are GPL.                 |
| `de.swiesend:secret-service`, `jkeychain`, `windpapi4j`  | One platform each.                                                                           |

## How it plugs in

The module registers `PlatformKeyringFactory` as `@Service(KeyringFactory.class)`. It extends the
`DefaultKeyringFactory` of tentackle-core, which is used only when no such service exists. The factory
first tries the credential store of the running OS. The store is tested with a lookup of a secret
that does not exist, which never prompts the user. If the store is not usable, the factory logs a warning
and falls back to the keyrings of tentackle-core. The system property `tentackle.keyring` forces any of
them by name:

| Name        | Platform            | Store                                                         |
|-------------|---------------------|---------------------------------------------------------------|
| `macos`     | macOS               | Login keychain, `SecItem*` API of Security.framework          |
| `windows`   | Windows             | Credential Manager, `CredWriteW`/`CredReadW`/`CredDeleteW`    |
| `libsecret` | Linux, other Unixes | freedesktop.org Secret Service via `libsecret-1.so.0`         |

Forcing is strict: a platform keyring forced on the wrong OS, or one whose store is not usable, makes
`getKeyring()` throw a `KeyringException` instead of falling back.

The application doesn't need to `requires` the module. `org.tentackle.common` declares `uses ModuleHook`, and
this module `provides` one, so service binding pulls it into the module graph as soon as it is on the
module path. On the classpath, it is found through its `META-INF/services` entry.

### macOS

The secrets are generic passwords (`kSecClassGenericPassword`) in the user's login keychain. The data
protection keychain would need a signed application with a keychain-access-group entitlement, and that
cannot be assumed.

The keychain lets the application that created an item read it without asking. Every other
application has to be allowed by the user, and so does *the same application after its binary has
changed*, unless both versions are signed with the same identity. With an unsigned application,
every update, for example via [tentackle-update](../../../../tentackle-update/src/main/docs/update.md),
therefore makes macOS ask once per secret. `load` blocks until the user answers. If the user denies
access, it throws a `KeyringException`. Sign your application bundles.

### Windows

The secrets are generic credentials of the user, persisted with `CRED_PERSIST_LOCAL_MACHINE`. Despite
its name, that is per user, but the credentials do not roam to other machines with the profile. They appear
in the Control Panel's Credential Manager under *Windows Credentials*, with the target name
`account@service`. `%`, `@` and `#` in the parts are percent-encoded, so no two ids share a target, and no
target can be mistaken for one of the chunks described below. Windows compares target names case-insensitively,
however, so ids that differ only in case refer to the same credential.

A credential holds at most 2560 bytes, and access tokens issued by Entra ID can be larger than that. Larger
secrets are therefore spread over the additional credentials `target#g.1` … `target#g.n`, and the main
credential refers to them. The generation `g` is 0 or 1. A new secret goes to the generation the old one
does not use, and the main credential is written last. Rewriting it switches from the old secret to the new
one in a single step, and only then are the old chunks removed. A store that fails midway therefore leaves
the previous secret intact, and its leftovers are cleaned up by the next store. A load never assembles a
secret from two different writes: a missing chunk is reported as a `KeyringException`, and nothing is
silently truncated.

### Linux and other Unixes

The secrets go to the default collection (usually *login*) of whatever implements the Secret Service
D-Bus API: GNOME Keyring, KWallet since 5.97, or KeePassXC. The bindings load `libsecret-1.so.0` and the
GLib it depends on (`libglib-2.0.so.0`); if `libsecret` is not installed, the factory falls back as well. They carry the schema `org.tentackle.keyring` and the
attributes `service` and `account`, so other tools find them, for example
`secret-tool lookup service <service> account <account>`.

On a headless machine, over SSH without a desktop session, or in a container, there usually is no
Secret Service. The factory detects this at startup by looking up a secret that does not exist. That
lookup fails fast without a provider, and since nothing matches, it never prompts to unlock a
collection. The factory then falls back to the keyrings of
[tentackle-core](../../../../tentackle-core/src/main/docs/keyring.md#selection).

## Secrets in memory

The keyrings follow the rules of the [keyring API](../../../../tentackle-core/src/main/docs/keyring.md#the-api):
a secret travels as a `char[]` and is converted to UTF-8 with `SecretsHelper`, never via a `String`. Every
Java array and every native buffer that held a secret is wiped before it is released, including the blob
that Windows returns, before `CredFree`. libsecret's `secret_password_free` wipes the returned password
itself.

The one copy that cannot be wiped is on macOS: the `SecItem` API takes and returns the secret as a
`CFData`, which is immutable. It is released right after the call, but its memory is not cleared.

## Native access and packaging

The JPMS descriptor:

```java
module org.tentackle.keyring {
  exports org.tentackle.keyring.platform;
  requires transitive org.tentackle.core;
  provides org.tentackle.common.ModuleHook with org.tentackle.keyring.platform.service.Hook;
}
```

The API package `org.tentackle.keyring` belongs to tentackle-core. This module only adds
`org.tentackle.keyring.platform`. `org.tentackle.keyring.platform.ffm` holds the bindings. It is internal
and not exported.

**`--enable-native-access=org.tentackle.keyring` is required.** Without it, the JVM warns on the
first restricted call, and a future release will refuse them. On the class path, use
`--enable-native-access=ALL-UNNAMED`, or, for an executable JAR, the manifest attribute
`Enable-Native-Access: ALL-UNNAMED`. For a jlink/jpackage image, add the flag to the launcher's JVM
options, just as for `tentackle-quiche`.

## Testing

The module's own tests run the same contract (`AbstractKeyringContractTest`) as the keyrings of
tentackle-core: round trip, replace, a loaded secret is a copy, independent ids, unicode, a secret larger
than a Windows credential, and the rejection of empty secrets and of NUL characters. The Windows chunking is
additionally tested on every platform against a fake credential store that enforces the blob limit and can
fail on demand: stale chunks are removed, a missing chunk is reported, and a store that fails midway keeps
the previous secret. `PlatformKeyringFactoryTest` checks that the module replaces the default factory, that
the keyrings of tentackle-core can still be forced, and that the keyrings of the other platforms fail
cleanly.

The real credential store of the OS is only tested on request, since that needs an unlocked store:

```bash
mvn -Pkeyring -pl :tentackle-keyring test
```

On a Linux machine without a desktop session, a throwaway keyring does the job. `XDG_DATA_HOME` keeps
it away from the user's real login keyring:

```bash
XDG_DATA_HOME=$(mktemp -d) dbus-run-session -- sh -c \
  'echo pw | gnome-keyring-daemon --unlock --components=secrets && mvn -Pkeyring -pl :tentackle-keyring test'
```

## See also

- [Keyring](../../../../tentackle-core/src/main/docs/keyring.md): the API, the default keyrings and the selection.
- [Single Sign-On with Google](../../../../src/main/docs/google-sso.md): remembering a sign-in in the keyring.
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md): how the module replaces the default factory.
