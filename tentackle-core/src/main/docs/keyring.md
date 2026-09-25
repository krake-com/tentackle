# Keyring — Secrets on the Client

## Overview

A client that remembers a secret, such as the tokens of a [single sign-on](../../../../src/main/docs/google-sso.md)
or a password, needs a place to keep it. The keyring API in `tentackle-core` (package `org.tentackle.keyring`)
gives it one, with a single, platform-agnostic API:

```java
Keyring keyring = Keyring.getInstance();
SecretId id = new SecretId("com.example.myapp/google", clientId);

keyring.store(id, "MyApp Google sign-in", token);   // insert or replace
char[] token = keyring.load(id);                    // null if there is none
keyring.remove(id);
```

Out of the box, the secrets are kept in the Java user preferences, encrypted with the application's
[`Cryptor`](../../../../tentackle-common/src/main/docs/cryptor.md), or only in memory if there is no cryptor.
That works everywhere and needs nothing, but it does not *protect* a secret.

The real protection is the credential store that every desktop OS has built in. It binds the secrets to
the logged-in user and encrypts them with a key that the user's login unlocks. The
[tentackle-keyring](../../../../tentackle-keyring/src/main/docs/platform-keyring.md) module adds it. The
module is **opt-in**: add it to the application's dependencies, and it replaces the default keyring
via `@Service`. No code changes. Without it, the application runs as before.

## The API

| Type                                                                      | Purpose                                                                       |
|---------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| [`Keyring`](../java/org/tentackle/keyring/Keyring.java)                   | `store`, `load`, `remove`, plus `isSecure()` and `isPersistent()`.            |
| [`SecretId`](../java/org/tentackle/keyring/SecretId.java)                 | The identity of a secret: a *service* and an *account*.                       |
| [`KeyringFactory`](../java/org/tentackle/keyring/KeyringFactory.java)     | Selects the keyring once; replaceable via `@Service(KeyringFactory.class)`.   |
| [`KeyringException`](../java/org/tentackle/keyring/KeyringException.java) | The store could not be accessed. A missing secret is *not* an exception.      |
| [`AbstractKeyring`](../java/org/tentackle/keyring/AbstractKeyring.java)   | Base class of the keyrings: argument checks, locking, the availability probe. |

Every credential store organizes its entries by **service** and **account**, and so does `SecretId`. The
service says what the secret is for, typically the application plus a purpose
(`"com.example.myapp/google"`). The account says whose it is within that service, for example the user
or an OAuth client id. The optional **label** is what the OS tools show the user: Keychain Access,
Credential Manager, Seahorse, KWalletManager.

Secrets are `char[]`, never `String`, so they can be wiped. The keyring never keeps a reference to an
array that is passed in, and it always hands out a fresh array, which the caller should wipe with
`Arrays.fill(secret, '\0')` when done. Internally, all intermediate buffers are wiped before they are
released. A secret must not be empty and must not contain NUL characters, because some of the native
APIs take C strings.

All keyrings are thread-safe.

## The keyrings

| Name          | Module              | Store                                                         | `isSecure` | `isPersistent` |
|---------------|---------------------|---------------------------------------------------------------|:----------:|:--------------:|
| `preferences` | `tentackle-core`    | Java user preferences, encrypted with the `Cryptor`           | **no**     | yes            |
| `memory`      | `tentackle-core`    | The JVM's heap                                                | yes        | **no**         |
| `macos`       | `tentackle-keyring` | Login keychain                                                | yes        | yes            |
| `windows`     | `tentackle-keyring` | Credential Manager                                            | yes        | yes            |
| `libsecret`   | `tentackle-keyring` | freedesktop.org Secret Service                                | yes        | yes            |

The `preferences` keyring keeps the secrets in the node `/org/tentackle/keyring`, each under a hash of its
id. The Java preferences are local to the machine and user, unlike Tentackle's database-backed
[preferences](preferences.md), which roam with the user and are therefore the wrong place for a secret
bound to one device. The encryption keeps the secrets from being picked up by simply looking at the file.
It is obfuscation, not protection: whoever has the file and the application, which carries the cryptor's
key, has the secrets. That is why the keyring reports `isSecure() == false`.

### Selection

The keyring is picked once, on first use, by the `KeyringFactory`:

1. the keyring named by the system property `tentackle.keyring`, if set. Forcing a keyring that is
   not usable is an error, never a silent fallback;
2. the credential store of the OS, if `tentackle-keyring` is present and the store is usable;
3. the `preferences` keyring, if the application provides a `Cryptor`;
4. the `memory` keyring.

Without `tentackle-keyring`, no `@Service(KeyringFactory.class)` exists, and the
[`DefaultKeyringFactory`](../java/org/tentackle/keyring/DefaultKeyringFactory.java) steps in, covering
steps 1, 3 and 4. The module registers the `PlatformKeyringFactory`, a subclass that adds step 2. Since only the
module registers a provider, it wins in modular and classpath mode alike, whatever the order on the path.

An application that would rather not remember a secret at all than keep it in an insecure place checks
`isSecure()`. An application that wants a different policy, or a keyring of its own (a hardware token, a
password manager's CLI), replaces the factory. It extends `PlatformKeyringFactory` if it uses
`tentackle-keyring`, and `DefaultKeyringFactory` otherwise:

```java
@Service(KeyringFactory.class)
public class MyKeyringFactory extends PlatformKeyringFactory {

  @Override
  protected Keyring selectBestKeyring() {
    Keyring keyring = super.selectBestKeyring();
    if (!keyring.isSecure()) {
      return new MemoryKeyring();   // rather forget than store insecurely
    }
    return keyring;
  }
}
```

## Testing

In an application's tests, force the memory keyring with `-Dtentackle.keyring=memory`, so the tests
neither depend on nor pollute the developer's keychain or preferences.

## See also

- [OS Credential Stores](../../../../tentackle-keyring/src/main/docs/platform-keyring.md): the opt-in module for the keychain, the Credential Manager and the Secret Service.
- [Single Sign-On with Google](../../../../src/main/docs/google-sso.md): remembering a sign-in in the keyring.
- [Authentication](../../../../tentackle-session/src/main/docs/authentication.md): the credential model.
- [Cryptor and EncryptedProperties](../../../../tentackle-common/src/main/docs/cryptor.md): what the `preferences` keyring encrypts with.
- [Service and Configuration API](../../../../tentackle-common/src/main/docs/services.md): replacing the `KeyringFactory`.
