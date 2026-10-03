# Cryptor and EncryptedProperties — Application-Specific Secret Handling

## Overview and Motivation

Every real deployment has the same awkward problem: the application needs a database password, a
keystore passphrase, a daemon credential — and those have to live *somewhere* the application can
read them at startup. A plain-text `backend.properties` in a distribution image is the outcome
nobody wants but many projects end up with.

Tentackle's answer is deliberately modest, and its own javadoc is candid about why:

> The security of symmetric encryption algorithms in general depends on the confidentiality of the
> passphrase. Thus, the passphrase should ideally not be part of the application, but provided via
> some external media, a mounted USB-stick, manual input, PGP keyring, whatever. However, in
> practice this isn't always doable.

So [`Cryptor`](../java/org/tentackle/common/Cryptor.java) is not a secrets-management system and
does not pretend to be one. It is a symmetric en/decryptor that an application subclasses with its
own salt and passphrase, and that the framework then uses consistently everywhere a secret would
otherwise sit in the clear:

- **at build time** — the [tentackle-maven-plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md)
  encrypts passwords into filtered resources, so the packaged image contains no clear-text secrets;
- **in configuration** — [`EncryptedProperties`](../java/org/tentackle/common/EncryptedProperties.java)
  transparently decrypts any value marked with a `~`;
- **in memory** — [`Secret`](../../../../tentackle-core/src/main/java/org/tentackle/misc/Secret.java)
  holds passwords and tokens (such as the user's password in the session info's
  `PasswordCredentials`) encrypted rather than as a bare `char[]`;
- **on the wire** — the [TRIP](../../../../tentackle-core/src/main/docs/trip.md) `tripe`/`tripce`
  transports encrypt with the same `Cryptor` (see [io.md](../../../../tentackle-core/src/main/docs/io.md)).

What it buys you is honest and worth stating plainly: it raises the cost of a **casual** compromise
— someone reading a properties file out of a distribution image, a support archive, or a log — and
it keeps secrets from being *incidentally* disclosed. It does not defend against an attacker who
can read your application's own bytes, because such an attacker can recover the passphrase from
them. Judge it on that basis, and put genuinely high-value secrets behind an externally supplied
passphrase (see [Threat model](#threat-model-and-limits) below).


## Cryptor

### Providing one

`Cryptor` is optional. Without one, the framework stores secrets unencrypted and everything still
works. An application enables it by subclassing and registering with the
[service API](services.md):

```java
@Service(Cryptor.class)
public class MyAppCryptor extends Cryptor {

  public MyAppCryptor() {
    super(getSalt(), getPassphrase());
  }
  ...
}
```

Two lookups exist, and the difference matters:

| Method                        | Behavior                                                                                                                                          |
|-------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------|
| `Cryptor.getInstance()`       | The singleton, or **`null`** if the application configured none. Callers must handle null — this is the "encryption is optional" path.            |
| `Cryptor.getInstanceSafely()` | The singleton, throwing `TentackleRuntimeException` if absent. Used where a value is already known to be encrypted and there is no sane fallback. |

> **Watch the constructor.** If the application's cryptor is configured but its constructor throws a
> `TentackleRuntimeException` (a typo in the salt, say), `getInstance()` silently returns `null`, as
> if no cryptor were configured at all — and secrets are stored unencrypted from then on.

### What it does

The `(salt, passphrase)` constructor derives a key with **PBKDF2WithHmacSHA1** and encrypts with
**AES**, defaulting to 1024 iterations and a key strength of 256. A notable detail: the constructor
**scratches its inputs** — the passphrase char array is blanked and the salt array zeroed once the
key is derived, so they do not linger in the heap for a dump to find. The same care runs through the
API: `encrypt(char[])` clears the array it was given, and `decrypt64ToChars` returns a `char[]`
precisely so the caller can erase it after use rather than being handed an immortal `String`.

Alternatively, the `(SecretKey, Cipher)` constructor takes a ready-made key and a template cipher,
whose algorithm and provider are used to create the ciphers.

`Cryptor` is thread-safe: since a `Cipher` is stateful, each thread gets its own, created lazily
(via `getCipher()`, or from the template cipher). This matters because the singleton is shared by
all threads, for example by the encrypting transports of a server.

The three algorithm choices are `protected` methods, so a subclass can override them
(`getCipher()` is not used with the `(SecretKey, Cipher)` constructor):

| Method                     | Default                                              |
|----------------------------|------------------------------------------------------|
| `getSecretKeyFactory()`    | `SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")` |
| `createSecretKeySpec(...)` | `new SecretKeySpec(key, "AES")`                      |
| `getCipher()`              | `Cipher.getInstance("AES")`                          |

> **Note on the defaults.** `Cipher.getInstance("AES")` resolves, per the JCE provider defaults, to
> AES in **ECB** mode — which encrypts identical plaintext blocks to identical ciphertext blocks and
> uses no per-value IV. For short, high-entropy secrets like passwords this is mostly a
> non-issue; for anything longer or more structured it leaks patterns. Likewise 1024 PBKDF2
> iterations is modest by current guidance. Both are adjustable without touching the rest of the
> framework: pass a higher iteration count to the `(salt, passphrase, iterations, keyStrength)`
> constructor, and override `getCipher()` to select an authenticated mode. If you do change the
> cipher, remember that build-time encryption and runtime decryption must use the *same* `Cryptor`
> — which they will, since both resolve it through the service API.

### The API surface

Everything comes in byte, char and base64 flavors, because the right representation differs by
caller:

- `encrypt(byte[])` / `decrypt(byte[])`, with offset+length variants;
- `encrypt(char[])` — encodes, encrypts, and wipes both the char array and the intermediate bytes;
- `encrypt64(...)` / `decrypt64(...)` — base64 strings, the form used in properties files;
- `decrypt64ToBytes(...)` — a base64 string decrypted to bytes;
- `decrypt64ToChars(...)` / `decryptToChars(...)` — the erasable-result variants.

Texts, salts and passphrases are converted to bytes with
[`Settings.getEncodingCharset()`](../java/org/tentackle/common/Settings.java), which defaults to UTF-8
and is set by the Maven plugins from `project.build.sourceEncoding`. Build-time encryption and
runtime decryption must therefore use the same charset, unless the secrets are plain ASCII.

The `String`-based `(salt, passphrase)` convenience constructor cannot scratch its inputs, since
strings are immutable. Prefer the `(byte[], char[])` variant.

`Cryptor` also implements `Function<String,String>` (`apply` = `encrypt64`). That is not an
idle convenience: it is exactly what lets the Maven plugin use an application's own cryptor as a
property converter, described [below](#build-time-encrypting-secrets-into-filtered-resources).

### deriveURL — the hidden gem

[`deriveURL(url, protocols...)`](../java/org/tentackle/common/Cryptor.java) decrypts a URL that has
been disguised behind a protocol the application doesn't actually use. If the URL starts with one of
the given fake protocols, the text after `=~` up to the next `&` is decrypted and returned:

```
https://somehost.somedomain.org/login?id=~GK+AG1QIjpBaD51HP/kw9Hzpd...&user=100
```

The javadoc labels it "Hidden gem... ;)" — it lets a real endpoint travel inside something that
looks like an ordinary web link.


## EncryptedProperties

[`EncryptedProperties`](../java/org/tentackle/common/EncryptedProperties.java) is a `java.util.Properties`
subclass with one central idea: **a value beginning with `~` is encrypted**, and `getProperty`
returns it decrypted. Nothing else in the application needs to know.

```properties
url=jdbc:postgresql://dbhost/mydb
user=myapp
password=~GK+AG1QIjpBaD51HP/kw9HzpdKZLt2FrInFxd1jtPWvGzaw5lcLcHy5RB
```

`getProperty("password")` hands back the clear-text password; the file never contains it. If a
genuine value happens to start with a tilde, escape it with a backslash. Mind that
`Properties.load` itself drops a single backslash in front of a character that is not an escape
sequence, so in a *file* the escape must be doubled:

```properties
greeting=\\~notEncrypted
```

In Java code, `setProperty("greeting", "\\~notEncrypted")` stores the escaped value (one backslash
in the string). `setEncryptedProperty` applies the escape automatically when it has to store a
value unencrypted.

### Reading values

| Method                       | Returns                                                                          |
|------------------------------|-----------------------------------------------------------------------------------|
| `getProperty(key)`           | Decrypted value (via `Cryptor.getInstanceSafely()` when the value is marked `~`)  |
| `getPropertyAsChars(key)`    | Same, as a `char[]` the caller **can erase after use** — prefer this for secrets  |
| `getPropertyAsChars(key, defaults)` | Same, but returns `defaults` if there is no such key                        |
| `getPropertyBlunt(key)`      | The **raw** stored value, still encrypted. For copying/inspecting without decrypting |
| `getPropertyIgnoreCase(key)` | Case-insensitive lookup                                                          |

Note the asymmetry that follows from `getInstanceSafely()`: reading a `~`-marked value **without** a
configured `Cryptor` throws rather than returning ciphertext. That is intentional — silently handing
an encrypted string to code expecting a password would fail later, and less clearly.

### Writing values

`setEncryptedProperty(key, value)` accepts a `String` or a `char[]` and stores it encrypted with the
`~` marker — *if* a `Cryptor` is configured. If none is, it stores the value as-is (escaping a
leading `~`). This is the "encryption is optional" principle again: the same code path works in both
cases, and the file format tells you which happened. With a `Cryptor`, the `char[]` variant erases
the given array after encryption.

`setProperty(key, value)` stores the value as given, i.e., never encrypts it.

### Key prefixes and case-insensitivity

Two features exist for integration rather than security:

- **`setKeyPrefix(...)`** / `getKeyPrefix()` — a prefix prepended to keys on `getProperty`,
  `setProperty` and friends, so Tentackle's settings can be merged into a shared file (a Spring
  `application.properties`, say) without colliding. `stringPropertyNames()` then reports only the
  prefixed key space, with the prefix stripped. Caveat from the javadoc: the low-level `Hashtable`
  methods that deal in `Object`s bypass the prefix entirely.
- **`getPropertyIgnoreCase` / `getKeyIgnoreCase`** — a case map, built on first use. Building it
  throws `TentackleRuntimeException` if two existing keys collide case-insensitively, rather than
  silently picking one. Keys added later via `setProperty` update the map.
  `getKeyIgnoreCase` returns the given key unchanged if there is no match.

`setName(...)` / `getName()` label the source (typically the file name) for diagnostics;
[`FileHelper.loadProperties(name)`](../java/org/tentackle/common/FileHelper.java) sets it
automatically. `clone()` returns an independent copy, including the case map.


## How It Fits Together

### Build time: encrypting secrets into filtered resources

This is the part most projects actually rely on, and the archetype wires it up for you. The
`tentackle-maven-plugin`'s `properties` goal converts a Maven property through a converter class and
publishes the result as a new property:

```xml
<propertyDescriptors>
  <propertyDescriptor>
    <input>${dbPasswd}</input>
    <converter>@org.tentackle.common.Cryptor</converter>
    <property>encryptedPasswd</property>
  </propertyDescriptor>
</propertyDescriptors>
```

The `@` prefix means "look the converter up via `META-INF/services`" — so it resolves to the
*application's* `@Service(Cryptor.class)`, not the base class. (That is why the plugin declares the
application's `-common` artifact in its own `<dependencies>`: the converter must be on the plugin's
classpath.) Because `Cryptor` implements `Function<String,String>`, the plugin can call it directly.

The resulting property lands in a filtered resource with the `~` marker written by hand:

```properties
url=${dbUrl}?sslmode=disable
user=${dbUser}
password=~${encryptedPasswd}
^javax.net.ssl.keyStorePassword=~${encryptedCertPasswd}
```

The clear-text password lives only in the build environment (a developer's settings, a CI secret);
the packaged [jlink image](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md)
contains only ciphertext. At runtime the session layer loads this file as `EncryptedProperties` and
`getProperty("password")` just works — see
[session.md](../../../../tentackle-session/src/main/docs/session.md) and the
[MyApp walkthrough](../../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md).

### Runtime: the rest of the chain

- **`BackendConfiguration`** stores backend passwords encrypted when a `Cryptor` is configured.
- **`Secret`** keeps the user's password (in the session info's `PasswordCredentials`) as
  encrypted bytes rather than a clear `char[]`, so a heap dump of a running client doesn't
  surrender it. The same applies on the wire, since the encrypted bytes are what gets serialized.
- **Login and auto-update** pass the credentials, encrypted, along on restart, which is how
  [fx-rdc-update](../../../../tentackle-fx-rdc-update/src/main/docs/fx-rdc-update.md) restarts a
  client seamlessly without re-prompting.
- **TRIP transports** `tripe` and `tripce` encrypt the connection with the same `Cryptor` as a
  pre-shared key — see [io.md](../../../../tentackle-core/src/main/docs/io.md) for the streams and
  [trip.md](../../../../tentackle-core/src/main/docs/trip.md) for the transport schemes.

One `Cryptor` therefore covers the secret's whole life: encrypted at build, decrypted at startup,
held encrypted in memory, and used as the PSK on the wire.


## Threat Model and Limits

Being clear about this is more useful than overselling it.

**What it protects against.** Secrets sitting in plain text where they can be read *without*
attacking the application: properties files inside distribution images, configuration checked into
a repository, support bundles, backups, screenshots, heap dumps of a running client.

**What it does not protect against.** An attacker who can read your application's code or memory.
The passphrase is, by construction, available to the application — so it is available to anyone who
has the application. The archetype's generated `MyAppCryptor` illustrates the trade honestly: it
assembles its salt and passphrase character-by-character in scrambled order (`salt[1]='E';
salt[0]='D'; …`) with the comment *"just to make it a little harder..."*. That is obfuscation, not
security, and the generated code says so.

**The recommended step up**, straight from the class javadoc: derive the passphrase from an
**external** source — manual entry at startup, a mounted volume, a PGP keyring, a platform secret
store — instead of compiling it in. The `Cryptor` constructor takes a `char[]`; where it comes from
is entirely up to your subclass, and everything downstream (`EncryptedProperties`, the transports,
`SessionInfo`) is unaffected by the change.

**If you need more**, use more: a real secrets manager, per-environment key rotation, an
authenticated cipher mode via `getCipher()`, a higher iteration count. `Cryptor` is designed to be
extended, and the service API means one class swap changes it everywhere.


## Related Documentation

- [Tentackle Common](common.md) — the module these classes belong to.
- [Service and Configuration API](services.md) — how `@Service(Cryptor.class)` is discovered.
- [Tentackle Maven Plugin](../../../../tentackle-maven-plugin/src/main/docs/tentackle-maven-plugin.md) — the `properties` goal and converters.
- [Tentackle Session](../../../../tentackle-session/src/main/docs/session.md) — backend configuration and credentials.
- [I/O and Networking](../../../../tentackle-core/src/main/docs/io.md) — the encrypting streams built on `Cryptor`.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the `tripe`/`tripce` transports.
- [MyApp Walkthrough](../../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md) — a generated application's `Cryptor` end to end.
- [Jlink/Jpackage Maven Plugin](../../../../tentackle-jlink-maven-plugin/src/main/docs/jlinkpackage.md) — the images the encrypted secrets ship in.
