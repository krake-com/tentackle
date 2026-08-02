# Authentication — Credentials, Providers, and Single Sign-On

## Overview and Motivation

Authentication answers *"who is this?"*. It is the step that runs once, at login, before a
[`Session`](session.md) exists — as opposed to [authorization](../../../../tentackle-pdo/src/main/docs/security.md),
which answers *"what may they do?"* and runs on every operation afterwards.

Tentackle does not prescribe **how** a client proves its identity. It defines the transport for the
proof and a pluggable chain of verifiers, so that a username and a password, an OAuth2 access token,
an OIDC ID token, a Kerberos ticket or a client certificate are all first-class options — and can
coexist in one server, which is what applications with both human users and service accounts need.

The design uses Tentackle's [service SPI](../../../../tentackle-common/src/main/docs/services.md) and the fact that
the client and the server live in different JVMs.

```
 CLIENT                                                  SERVER (TRIP)
 ──────                                                  ─────────────
 Login dialog / ConsoleApplication
   │
   │  backend.properties: authentication=<method>
   ▼
 CredentialsProvider  @CredentialsProviderService("oidc")
   │   (interactive: browser via CredentialsPrompt)
   ▼
 Credentials ──────► SessionInfo.setCredentials(..) ──TRIP──►  RemoteDbConnection.login(SessionInfo)
   PasswordCredentials                                             │
   TokenCredentials                                                ▼
                                                        RemoteDbSessionImpl.verifySessionInfo()
                                                                   │
                                                                   ▼
                                                        AuthenticationManager
                                                          ├─ AuthenticationProvider  @Service   ─┐
                                                          ├─ AuthenticationProvider  @Service    │ supports()/
                                                          └─ AuthenticationProvider  @Service   ─┘ authenticate()
                                                                   │
                                                                   ▼  Authentication (name, claims, roles)
                                                        UserResolver  @Service  (optional)
                                                                   │  → userId / userClassId
                                                                   ▼
                                                        SessionInfo.setUserId/setUserClassId
```

Everything lives in `org.tentackle.session.auth`, so both the client (`Db.open`) and the server
(`RemoteDbSessionImpl`) can reach it without dragging in the persistence stack.

| Artifact                                                                            | Role                                                                             |
|-------------------------------------------------------------------------------------|----------------------------------------------------------------------------------|
| [`Credentials`](../java/org/tentackle/session/auth/Credentials.java)                | What the client presents. Travels on the `SessionInfo` via TRIP.                 |
| [`PasswordCredentials`](../java/org/tentackle/session/auth/PasswordCredentials.java)| Username and password — the default.                                             |
| [`TokenCredentials`](../java/org/tentackle/session/auth/TokenCredentials.java)      | A bearer token, plus optional refresh token, issuer and expiry.                  |
| [`Secret`](../java/org/tentackle/session/auth/Secret.java)                          | A confidential value, encrypted in memory when a `Cryptor` is configured.        |
| [`AuthenticationProvider`](../java/org/tentackle/session/auth/AuthenticationProvider.java) | **The server-side SPI**: verifies credentials.                             |
| [`AuthenticationManager`](../java/org/tentackle/session/auth/AuthenticationManager.java)   | Drives the provider chain. Deny by default.                               |
| [`Authentication`](../java/org/tentackle/session/auth/Authentication.java)          | The proven identity: name, claims, roles, and the application's user.            |
| [`UserResolver`](../java/org/tentackle/session/auth/UserResolver.java)              | Optional SPI mapping a proven identity onto the application's user entity.       |
| [`CredentialsProvider`](../java/org/tentackle/session/auth/CredentialsProvider.java)| **The client-side SPI**: obtains the credentials, interactively if needed.       |
| [`CredentialsPrompt`](../java/org/tentackle/session/auth/CredentialsPrompt.java)    | The UI-agnostic callbacks an interactive provider uses (open a browser, …).      |

---

## The Credential Model

[`SessionInfo`](../java/org/tentackle/session/SessionInfo.java) carries a `Credentials` object.
It defaults to `PasswordCredentials`, so `getUserName()`, `getPassword()` and `setPassword(char[])`
keep working exactly as before — they simply read and write the default credentials underneath:

```java
SessionInfo sessionInfo = Pdo.createSessionInfo("hugo", password, null);
sessionInfo.getCredentials();     // PasswordCredentials("hugo", ...)

// log in with a token instead
sessionInfo.setCredentials(new TokenCredentials(accessToken));
sessionInfo.getPassword();        // null - there is no password anymore
```

Confidential parts are held in a [`Secret`](../java/org/tentackle/session/auth/Secret.java), which
encrypts them in memory — and therefore also on the wire — whenever the application provides a
[`Cryptor`](../../../../tentackle-common/src/main/docs/cryptor.md). `clear()` overwrites the arrays,
`clone()` copies them physically, and `toString()` never reveals them. Custom credential types must
follow the same rule and provide a public no-arg constructor, since TRIP needs one to deserialize them.

> **Transport security is still your job.** Credentials are only as safe as the connection carrying
> them. Use TLS for the TRIP transport (see [QUIC](../../../../tentackle-quic/src/main/docs/quic.md)
> and the SSL section of [TRIP](../../../../tentackle-core/src/main/docs/trip.md)).

---

## The Provider Chain

On the server, `RemoteDbSessionImpl.verifySessionInfo(SessionInfo)` hands the credentials to the
`AuthenticationManager`. It asks every provider that `supports()` them, in order, and takes the first
proven identity.

A provider has three possible answers:

| Answer                          | Meaning                                                  |
|----------------------------------|-----------------------------------------------------------|
| an `Authentication`              | authenticated — the chain stops here                     |
| `null`                           | **abstain** — not my business, ask the next provider      |
| throw `AuthenticationException`  | **veto** — these were mine and they are invalid, stop     |

Providers are ordinary services with a public no-arg constructor:

```java
@Service(AuthenticationProvider.class)
public class OidcAuthenticationProvider implements AuthenticationProvider {

  @Override
  public boolean supports(Credentials credentials) {
    return credentials instanceof TokenCredentials;
  }

  @Override
  public Authentication authenticate(Credentials credentials) {
    Jwt jwt = verify(((TokenCredentials) credentials).getToken());   // signature, issuer, audience
    if (jwt.isExpired()) {
      throw new CredentialsExpiredException("token expired");        // the client may refresh and retry
    }
    DefaultAuthentication authentication = new DefaultAuthentication(jwt.getSubject(), credentials);
    authentication.getAttributes().putAll(jwt.getClaims());
    authentication.getRoles().addAll(jwt.getGroups());
    return authentication;   // no user id: the UserResolver takes it from here
  }

  @Override
  public int getPriority() {
    return -10;   // asked before the password provider
  }
}
```

The chain is ordered by `getPriority()` and then by class name, so it is deterministic no matter in
which order the modules happen to be discovered. Providers are instantiated once per server and must
be thread-safe.

**Authentication is deny by default.** A server with no provider configured refuses every login. If
that is not what you want, you configured nothing where you meant to configure something — the
manager logs a warning at startup saying exactly that.

---

## From a Principal to a User: the `UserResolver`

A provider proves *who the caller is*. It does not necessarily know *which user of this application*
that is: a generic OIDC provider knows a token's subject, nothing more. That last step is the
[`UserResolver`](../java/org/tentackle/session/auth/UserResolver.java), and it is deliberately a
separate SPI so the provider stays reusable across applications.

The manager consults it only when the chain left `Authentication.getUserId()` at zero. A password
provider that looked the user up anyway sets the ids itself and no resolver is needed.

```java
@Service(UserResolver.class)
public class MyUserResolver implements UserResolver {

  @Override
  public void resolveUser(Authentication authentication) {
    DomainContext context = Pdo.createDomainContext(Session.getSession());
    User user = Pdo.create(User.class, context).selectByUniqueDomainKey(authentication.getName());
    if (user == null || !user.isLoginAllowed()) {
      throw new AuthenticationException("login refused");
    }
    authentication.setUserId(user.getId());
    authentication.setUserClassId(user.getClassId());
  }
}
```

The session is already current when the resolver runs, so the persistence layer is available.

Once the identity is established, `verifySessionInfo` copies the user id, the user class id and the
authenticated name onto the client's session info. From there the ordinary machinery takes over: the
`userId`/`userClassId` pair is what `DomainContext` exposes, what the
[`SecurityManager`](../../../../tentackle-pdo/src/main/docs/security.md#grantees-users-and-groups)
resolves grantees from, and what the modification log records. `getAuthentication()` on the remote
session keeps the full identity — claims and external roles included — available to the application,
which is where a `SecurityManager` maps identity-provider groups onto Tentackle grantees.

### Session concerns vs. credential concerns

`verifySessionInfo` is still overridable, and applications do override it — but only for what belongs
to the *session* rather than to the verification of the credentials: refusing a second login of the
same user, setting the locale, writing an audit record. Call `super` first:

```java
@Override
public void verifySessionInfo(SessionInfo sessionInfo) {
  super.verifySessionInfo(sessionInfo);      // the provider chain does the verifying

  LocaleProvider.getInstance().setCurrentLocale(sessionInfo.getLocale());
  getSession().makeCurrent();

  if (!sessionInfo.isCloned()) {
    SessionInfo otherInfo = isUserLoggedIn(sessionInfo);
    if (otherInfo != null) {
      throw new AlreadyLoggedInException(getSession(), otherInfo);
    }
    ...
  }
}
```

This is exactly how the [project archetype](../../../../tentackle-project-archetype/src/main/docs/myapp-walkthrough.md)
generates it, with the password check living in a generated `AuthenticationProvider` next to it.

---

## The Client Side: Obtaining the Credentials

Single sign-on has a client half: somebody has to run the authorization-code flow, open the browser
and wait for the redirect, *before* the session can be opened. That is the
[`CredentialsProvider`](../java/org/tentackle/session/auth/CredentialsProvider.java), registered by
the name of the authentication method:

```java
@CredentialsProviderService("oidc")
public class OidcCredentialsProvider implements CredentialsProvider {

  @Override
  public boolean isInteractive() {
    return true;
  }

  @Override
  public Credentials createCredentials(SessionInfo sessionInfo, CredentialsPrompt prompt) {
    prompt.browse(authorizationEndpoint());        // the desktop client opens the system browser
    prompt.showMessage("waiting for the browser...");
    return new TokenCredentials(awaitToken());     // null if the user cancelled
  }

  @Override
  public Credentials refresh(Credentials credentials) {
    return renew((TokenCredentials) credentials);  // used after a CredentialsExpiredException
  }
}
```

The method is selected in `backend.properties` (see [Backend Properties](backend-properties.md)):

```properties
authentication=oidc
```

It defaults to `password`, which is served by the built-in, non-interactive
`PasswordCredentialsProvider` — the login dialog collected the username and password already, so
there is nothing left to obtain.

`CredentialsPrompt` keeps providers free of any UI toolkit. The desktop client supplies an
implementation that drives the login view and opens the system browser; console applications and
tests get `DefaultCredentialsPrompt`, which prints the URL instead.

### In the desktop client

When the configured method is interactive, the
[login view](../../../../tentackle-fx-rdc/src/main/docs/rdc.md#application-lifecycle-app-login) hides
the username and password fields and shows a single sign-in button. Pressing it runs the provider on
a background thread — the FX thread stays responsive while the user authenticates in the browser —
and the resulting credentials go straight into the login.

If the server answers with a `CredentialsExpiredException`, `LoginFailedHandler` asks the provider to
`refresh(...)` the credentials and retries once, without consuming one of the three login attempts
and without bothering the user.

---

## Known Gaps

- **Mid-session renewal.** Credentials are renewed on the *login* path only. A token that expires
  while a long-running session is already open is not refreshed on a `Session.reOpen()` or a pooled
  reconnect.
- **Direct (two-tier) sessions.** A `jdbc:` or `jndi:` URL never reaches `verifySessionInfo` — the
  client authenticates at the database itself, with the credentials from `backend.properties`, and
  the provider chain is not involved. Authentication in Tentackle's sense is a property of the
  application server tier.

---

## See Also

- [Tentackle Session](session.md) — the session the login creates, and `SessionInfo` itself.
- [Backend Properties](backend-properties.md) — the `authentication` key and the other connection settings.
- [Security — Permissions, Rules, and Grantees](../../../../tentackle-pdo/src/main/docs/security.md) — authorization, i.e. what the authenticated user may do.
- [Cryptor and EncryptedProperties](../../../../tentackle-common/src/main/docs/cryptor.md) — how secrets are protected in memory and in property files.
- [Services / ServiceFinder](../../../../tentackle-common/src/main/docs/services.md) — the SPI mechanism behind the providers.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the protocol carrying the credentials, and its TLS configuration.
- [Application Lifecycle](../../../../tentackle-pdo/src/main/docs/application.md) — where the login sits in the startup sequence.
- [Rich Desktop Client](../../../../tentackle-fx-rdc/src/main/docs/rdc.md) — the login view and the login failed handler.
