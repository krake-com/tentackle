# Tentackle Web — Bridging Web Frameworks to a Tentackle Tier

## Overview and Motivation

`tentackle-web` is the thinnest module in the framework. It contains exactly two public classes —
[`WebApplication`](../java/org/tentackle/web/app/WebApplication.java) and
[`WebSessionKeyCache`](../java/org/tentackle/web/app/WebSessionKeyCache.java) — plus a
[`ModuleHook`](../java/org/tentackle/web/app/service/Hook.java) for resource-bundle resolution. Its job is
narrow but important: **let a foreign web stack (Spring Boot, a servlet container, a JAX-RS runtime, …) run as a
first-class node in a [Tentackle cascade](../../../../src/main/docs/tiers.md).**

The difficulty it solves is a *lifecycle mismatch*. Tentackle is built on the
[`Session`](../../../../tentackle-session/src/main/java/org/tentackle/session/Session.java) abstraction — a
stateful, user-bound, transaction-carrying object that a desktop client holds open for its whole runtime and that
a server keeps in a pool. A web framework has no such thing. It serves short, stateless HTTP requests, often on a
thread pool, with its *own* notion of authentication, its *own* session id (a cookie or token), and no place to
park a long-lived persistence session. `tentackle-web` reconciles the two worlds:

- A [`WebApplication`](../java/org/tentackle/web/app/WebApplication.java) **is** an
  [`AbstractServerApplication`](../../../../tentackle-persistence/src/main/java/org/tentackle/persist/app/AbstractServerApplication.java)
  — it boots like any Tentackle server, logs into its backend (a database or an upstream server), and owns a
  session pool. So it is a normal tier in the cascade.
- On top of that it maintains a small **web-session-key cache** mapping the web framework's session identifier to a
  Tentackle [`SessionInfo`](../../../../tentackle-session/src/main/java/org/tentackle/session/SessionInfo.java),
  so that each inbound HTTP request can borrow a pooled persistence session, run as the right user, and return the
  session to the pool when the response is sent.

The module deliberately does **not** depend on Spring, Jakarta Servlet, or any web API. It defines the bridge in
terms the web layer can call into; the actual glue (a servlet filter, a security config, a pool factory) lives in
*your* application. The rest of this document explains the two classes and then walks through a complete worked
example: a Spring Boot REST daemon acting as a Tentackle tier.

---

## Where a Web Node Sits in the Cascade

A web application is a [leaf-style node](../../../../src/main/docs/tiers.md) that happens to expose HTTP instead
of, or in addition to, a desktop UI. Like every other node, its role is decided purely by its backend **URL**
(see [Multi-Tier Cascade](../../../../src/main/docs/tiers.md)):

```
        HTTP clients (browsers, mobile, other services)
              │  https://   (REST/JSON, Spring Security, …)
              ▼
     ┌─────────────────────┐
     │  WebApplication      │   ← Tentackle node:  AbstractServerApplication
     │  (e.g. Spring Boot)  │      + WebSessionKeyCache  (HTTP session ↔ SessionInfo)
     └──────────┬──────────┘
                │  url = jdbc:… │ jndi:…  →  DB-connected (owns a SessionPool)
                │  url = trip…           →  proxy (owns a remote MultiUserSessionPool)
                ▼
        Database   ─or─   upstream Tentackle server
```

Because `WebApplication` extends `AbstractServerApplication`, **both deployment shapes work unchanged**:

- With a `jdbc:`/`jndi:` URL the web node is **DB-connected** and owns a
  [`SessionPool`](../../../../tentackle-session/src/main/java/org/tentackle/session/SessionPool.java)
  (`getSessionPool()`), multiplexing many logical sessions over fewer physical JDBC connections.
- With a `trip…` URL it is a **proxy** that forwards upstream and owns a
  [`MultiUserSessionPool`](../../../../tentackle-session/src/main/java/org/tentackle/session/MultiUserSessionPool.java)
  (`getRemoteSessionPool()`), one upstream session per user.

`WebApplication.getSession(...)` (below) hides this difference: it picks the pool that exists.

---

## `WebApplication<T>` — the Tentackle side of the bridge

[`WebApplication<T>`](../java/org/tentackle/web/app/WebApplication.java) is an abstract server application
parameterized by the **web session key type** `T`. `T` is whatever the web framework uses to identify a client
session — most commonly a `String` (the servlet session id), but it can be any key you can hand back to the cache.

The key cache itself is created with the application, so it is never null — a web container may still be draining
requests while the node is already shutting down. Only its cleanup thread follows the application's lifecycle and
slots into the standard startup/shutdown sequence:

```java
private final WebSessionKeyCache<T> sessionInfoCache = new WebSessionKeyCache<>();

@Override protected void finishStartup()  { super.finishStartup(); sessionInfoCache.startup(300000); } // 5 min
@Override protected void cleanup()        {
  try {
    if (sessionInfoCache.isRunning()) {   // the startup may have failed before finishStartup()
      sessionInfoCache.terminate();
    }
  }
  finally {
    super.cleanup();                      // sessions, pools and drivers must be released either way
  }
}
```

So the eviction thread is started with a 5-minute interval once startup has finished. The `isRunning()` guard and
the `finally` matter: if the node fails to come up at all — a wrong backend password, an unreachable database —
`AbstractApplication.start()` calls `stop()`, and the cleanup must still shut down the session pool, the connection
manager and the JDBC drivers rather than abort on a cache that was never started.

The public API is four methods that the web layer calls, plus the inherited server machinery:

| Method                                   | Called from                       | Purpose                                                                                                                                                                                              |
|------------------------------------------|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `addWebSession(T key, SessionInfo info)` | login controller / auth filter    | Register a web session key → `SessionInfo` mapping (replaces any existing one).                                                                                                                      |
| `removeWebSession(T key)`                | logout / session-expired listener | Drop the mapping (no-op if absent).                                                                                                                                                                  |
| `getSession(T key)`                      | per request                       | Borrow a pooled persistence [`Session`](../../../../tentackle-session/src/main/java/org/tentackle/session/Session.java) attached to that user's `SessionInfo`; `null` if the key is unknown/expired. |
| `putSession(Session)`                    | after the response                | Return the session to its pool.                                                                                                                                                                      |
| `getSessionKeys(SessionInfo)`            | app code                          | Reverse-lookup all web keys currently bound to a `SessionInfo`.                                                                                                                                      |

### How `getSession` resolves a pooled session

```java
public Session getSession(T sessionKey) {
  SessionInfo sessionInfo = sessionInfoCache.getSessionInfo(sessionKey);
  if (sessionInfo != null) {                       // key known and not yet GC'd
    SessionPool sessionPool = getSessionPool();
    Db session = null;
    if (sessionPool != null) {                     // DB-connected: pooled local session
      session = (Db) sessionPool.get();
    }
    else {
      MultiUserSessionPool remoteSessionPool = getRemoteSessionPool();
      if (remoteSessionPool != null) {             // proxy: per-user upstream session
        session = (Db) remoteSessionPool.get(sessionInfo);
      }
    }
    if (session != null) {
      session.setSessionInfo(sessionInfo);          // attach the user's identity
      return session;
    }
  }
  return null;
}
```

The web layer never sees the pooling distinction — it asks for "the session for this web key" and gets one bound
to the right user. Attaching the `SessionInfo` is what makes the borrowed session execute *as that user* for the
duration of the request.

### Returning the session

```java
public void putSession(Session session) {
  if (getSessionPool() != null) {                  // DB-connected only
    ((Db) session).setSessionInfo(null);           // dereference SessionInfo → eligible for GC
  }
  session.getPool().put(session);                  // works for both SessionPool and MultiUserSessionPool
}
```

On a DB-connected node the `SessionInfo` is deliberately *detached* when the session goes back to the pool. The
only strong reference to the user's identity then lives in the key cache as a **soft** reference (next section).
Under memory pressure the JVM may reclaim it; if it does, the user simply has to authenticate again — a safe,
self-limiting cache rather than a leak. On a proxy node the upstream session stays user-bound, so the `SessionInfo`
is kept.

---

## `WebSessionKeyCache<T>` — soft, self-cleaning identity map

[`WebSessionKeyCache<T>`](../java/org/tentackle/web/app/WebSessionKeyCache.java) is a
`ConcurrentHashMap<T, SoftReference<SessionInfo>>` with a daemon cleanup thread. Two design choices matter:

- **Soft references.** Each `SessionInfo` is held via a
  [`SoftReference`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ref/SoftReference.html).
  The web framework owns the authoritative session lifecycle; this cache is only an *accelerator* that avoids a
  re-login on every request. If the JVM runs low on memory, it can clear these references, bounding the cache's
  footprint automatically. A lookup whose referent was cleared transparently removes the dead entry and returns
  `null`, so the caller falls back to creating a fresh `SessionInfo`.

- **Background eviction.** A private `CleanupThread` (daemon) wakes on the configured interval (5 minutes, from
  `WebApplication.finishStartup`) and purges map entries whose soft reference was already cleared:

  ```java
  sessionInfoMap.values().removeIf(ref -> ref.get() == null);
  ```

  `startup(long)` refuses to start a second thread, and `terminate()` interrupts and joins it during application
  cleanup. This sweeps up keys for sessions the web container forgot to log out (crashes, dropped connections)
  without relying on the web framework to always fire a logout event.

All three places that evict a dead entry — the lookup, the reverse scan and the sweeper — remove it **conditionally
on the reference they saw**, never by key alone:

```java
sessionInfoMap.remove(sessionKey, sessionInfoRef);
```

Otherwise a re-login racing with the eviction would lose: the login controller stores a fresh `SessionInfo` under
the same web key, and a plain `remove(key)` from the concurrent eviction would delete it again, leaving the caller
convinced it is logged in while every following request gets `null`. (`ConcurrentHashMap.values().removeIf(...)`
already removes value-conditionally, which is why the sweeper needs no extra care.)

`getSessionKeys(SessionInfo)` scans the map for all keys bound to a given identity (removing any dead entries it
meets along the way). It is the basis for "log this user out everywhere" or "how many web sessions does this user
hold" features, and works even when one user has several concurrent browser sessions.

---

## The Per-Request Lifecycle

Putting the pieces together, every authenticated HTTP request follows the same attach/detach dance, regardless of
which web framework drives it:

```
1. authenticate            web framework verifies credentials, yields a principal + a web session id (key T)
2. session = getSession(key)
       └─ cache miss?  →  createSessionInfo(user, password)         (build a Tentackle identity)
                          addWebSession(key, sessionInfo)            (cache it under the web key)
                          getSession(key)                            (now borrow a pooled session)
3. session.makeCurrent()   bind the borrowed session to the current thread for the request
4. … handle the request …  controllers/services run PDO calls as the authenticated user
5. session.clearCurrent()  unbind from the thread
6. putSession(session)     return the session to the pool   (finally-block: always runs)
```

Step 3/5 — `makeCurrent()` / `clearCurrent()` — is essential because web frameworks dispatch on pooled worker
threads. Binding the session to the thread for the request, and unbinding it in a `finally`, is what lets ordinary
PDO code (which reaches for the *current* session) run correctly without threading the session through every call.

---

## Worked Example: a Spring Boot REST Tier

The cleanest way to use `tentackle-web` is to start the Tentackle application first, and once it is fully up,
launch the web framework *inside* it. Below is a complete Spring Boot REST daemon built this way. 
It authenticates with HTTP Basic against the user table and serves
REST endpoints that run as the authenticated Tentackle user.
(changing to JWT is a matter of a few lines of code and covered by Spring, not Tentackle).

> The example uses Spring Boot + Spring Security, but nothing in `tentackle-web` is Spring-specific. The same five
> integration points — *boot order, per-request filter, session pool, authentication source, configuration* —
> apply to any servlet/JAX-RS stack.

### 1. Boot order: Tentackle first, web framework second

The application class extends `WebApplication<String>` (the web key is the servlet session id). Its `main` starts
the Tentackle server; only in `finishStartup()`, after the Tentackle node is fully connected, does it hand control
to Spring:

```java
public class RestServer extends WebApplication<String> {

  public static void main(String[] args) {
    new RestServer().start(args);                       // boot Tentackle node
  }

  public RestServer() {
    super(Constants.RESTDAEMON_NAME, Version.RELEASE);
    userDetailsService = new PlsblUserDetailsManager();
  }

  @Override
  public User getUser(DomainContext context, long userId) {     // framework hook: resolve the logged-in user PDO
    return Pdo.create(User.class, context).selectCached(userId);
  }

  @Override
  public SessionInfo createSessionInfo(String username, char[] password, String base) {
    SessionInfo sessionInfo = super.createSessionInfo(username, password, base);
    sessionInfo.setClientVersion(Version.RELEASE);
    sessionInfo.setSessionName("REST#0");               // MultiUserDbPool derives a name counter from "REST"
    return sessionInfo;
  }

  @Override
  protected void finishStartup() {
    super.finishStartup();                              // key cache + server endpoint now live
    ModificationTracker.submit(userDetailsService::reload);
    Pdo.listen(userDetailsService::reload, User.class); // rebuild Spring user cache whenever the user table changes
    SpringApplication.run(PlsblRestApplication.class);  // only now start Spring Boot
  }
}
```

Two Tentackle features earn their keep here. `getUser(...)` is the framework's hook
([`Application.getUser`](../../../../tentackle-pdo/src/main/java/org/tentackle/app/Application.java)) for turning a
resolved user id into a domain object. And `Pdo.listen(..., User.class)` wires the
[modification tracker](../../../../tentackle-session/src/main/docs/session.md) so that **any change to the user
table — made on any tier of the cascade — automatically refreshes the web layer's authentication cache**, with no
polling and no restart.

### 2. Per-request filter: attach and detach the session

A `DispatcherServlet` subclass wraps each dispatch in the attach/detach lifecycle from above. It is the heart of
the bridge:

```java
public class TentackleDispatcherServlet extends DispatcherServlet {

  @Override
  protected void doDispatch(HttpServletRequest request, HttpServletResponse response) throws Exception {
    Session session = null;
    WebApplication<String> app = (WebApplication<String>) Application.getInstance();
    try {
      Principal principal = request.getUserPrincipal();
      if (principal != null) {
        String sessionId = request.getSession().getId();         // the web session key (T = String)
        session = app.getSession(sessionId);
        if (session == null) {                                    // first request of this web session
          char[] password = extractBasicAuthPassword(request);
          SessionInfo info = app.createSessionInfo(principal.getName(), password, null);
          app.addWebSession(sessionId, info);
          session = app.getSession(sessionId);
        }
        session.makeCurrent();                                    // bind to this worker thread
      }
      super.doDispatch(request, response);                        // run controllers as this user
    }
    finally {
      if (session != null) {
        session.clearCurrent();
        app.putSession(session);                                 // always return to the pool
      }
    }
  }
}
```

It is registered as Spring's dispatcher servlet:

```java
@SpringBootApplication
public class PlsblRestApplication {
  @Bean(name = DispatcherServletAutoConfiguration.DEFAULT_DISPATCHER_SERVLET_BEAN_NAME)
  public TentackleDispatcherServlet dispatcherServlet() { return new TentackleDispatcherServlet(); }

  @Bean
  public ServletRegistrationBean<TentackleDispatcherServlet> dispatcherRegistration() {
    return new ServletRegistrationBean<>(dispatcherServlet());
  }
}
```

A complementary `SessionDestroyedEvent` listener calls `removeWebSession(id)` when Spring expires an HTTP session,
proactively dropping the key (the soft-reference cleanup thread is the backstop for whatever slips through):

```java
@Component
public class SessionExpiredListener implements ApplicationListener<SessionDestroyedEvent> {
  @Override public void onApplicationEvent(SessionDestroyedEvent event) {
    WebApplication<String> app = (WebApplication<String>) Application.getInstance();
    app.removeWebSession(event.getId());
  }
}
```

### 3. A session pool tuned for the web

Desktop clients hold one session and keep it alive with a heartbeat. Web requests do neither, so a web tier needs
a pool with web-appropriate semantics, supplied via the
[`SessionPoolFactory`](../../../../tentackle-session/src/main/java/org/tentackle/session/SessionPoolFactory.java)
service:

```java
@Service(SessionPoolFactory.class)
public class RestSessionPoolFactory extends DbPoolFactory {
  @Override
  public MultiUserSessionPool create(String name, Session session, int maxSize, int maxTotalSize,
                                     long maxIdleMinutes, long maxUsageMinutes) {
    // … read web-specific limits (maxsessions, maxidle, maxusage) from session properties …
    return new MultiUserDbPool(name, db.getConnectionManager(), db.getSessionGroupId(), …) {
      @Override protected boolean determineSessionCloned(SessionInfo info, DbPool pool) {
        return pool.getSize() > 0;        // every session after the first is a clone (no unique "host client")
      }
      @Override protected boolean isSlotRemovable(PoolSlot<Session> slot) {
        return slot.getPoolable().getSessionInfo().isCloned() || slot.getPool().getSize() == 1;
      }
    };
  }
}
```

Two web-specific rules:

- **All sessions are cloned.** In a desktop scenario the pool can recognize a returning "host client" and reuse
  its session. On the web there is no such stable identity, so every session beyond the first is treated as a
  clone.
- **Idle timeout instead of keep-alive.** Web clients send no keep-alive pings, so sessions must **time out** after
  an idle period (`maxIdle`) rather than waiting for an explicit close. The primary (un-cloned) session is removed
  only once all its clones are gone, so per-user locks and state survive until the user is truly finished.

A related tweak lets a remote (proxy) web tier transparently recover a dropped upstream session once, by overriding
the [`ModificationTracker`](../../../../tentackle-session/src/main/java/org/tentackle/session/ModificationTracker.java):

```java
@Service(ModificationTracker.class)
public class RestModificationTracker extends PdoModificationTracker {
  @Override protected boolean isReOpenOnceEnabled() { return true; }
}
```

### 4. Authentication wired to the PDO model

Spring Security's `UserDetailsService` is backed by Tentackle PDOs. The user details are loaded from the (cached)
`User` table and **rebuilt automatically whenever that table changes** (the `Pdo.listen` hook from step 1):

```java
public class PlsblUserDetailsManager implements UserDetailsService {
  public synchronized void reload() {                                   // run from the ModificationTracker thread
    userMap.clear();
    for (User user : Pdo.create(User.class, Pdo.createDomainContext()).selectAllCached()) {
      if (!user.isRetired()) {
        var authorities = user.getUserGroups().stream().map(g -> new PlsblGrantedAuthority(g.getName())).toList();
        userMap.put(user.getName(), new PlsblUserDetails(authorities, user.selectPasswordHash(), user.getName()));
      }
    }
  }
  @Override public UserDetails loadUserByUsername(String username) { … }
}
```

The security config plugs this service, the application's password hashing, and HTTP Basic into Spring Security —
deferring the `UserDetailsService` bean to the running Tentackle application instance:

```java
@Configuration @EnableWebSecurity
public class WebSecurityConfig {
  @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http.authorizeRequests(a -> a.anyRequest().authenticated())
               .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.ALWAYS))
               .httpBasic(Customizer.withDefaults()).build();
  }
  @Bean public UserDetailsService userDetailsService() {
    return ((RestServer) Application.getInstance()).getUserDetailsService();   // the Tentackle-backed service
  }
  @Bean public PasswordEncoder passwordEncoder() { return new PlsblPasswordEncoder(); }
}
```

The password encoder delegates to the application's own hashing so the same credentials work across desktop and
web. The session-creation policy is `ALWAYS` so that every authenticated client gets a stable servlet session id —
the very key the dispatcher servlet uses to look up its Tentackle session.

### 5. Configuration: one property file, two frameworks

Spring's `application.properties` and Tentackle's session properties are **merged into a single file** by giving
the Tentackle keys a prefix and pointing its
[`SessionInfoFactory`](../../../../tentackle-session/src/main/java/org/tentackle/session/SessionInfoFactory.java)
at the same `application` resource:

```properties
# Spring side
server.port=${restPort}
server.servlet.session.timeout=1m

# Tentackle side (prefixed, see RestSessionPoolFactory + the SessionInfoFactory)
tentackle.url=${dbService}
tentackle.user=RESTDAEMON
tentackle.password=…
tentackle.rest.poolname=web
tentackle.rest.maxidle=1
tentackle.rest.maxusage=30
tentackle.rest.maxsessions=10
tentackle.rest.maxtotalsessions=100
```

```java
@Service(SessionInfoFactory.class)
public class PlsblRestSessionInfoFactory extends PlsblSessionInfoFactory {
  private static final String PROPS_NAME  = "application";   // same file Spring reads
  private static final String KEY_PREFIX  = "tentackle.";    // Tentackle keys live under this prefix
  // create(...) overrides set the base name to "application" and apply the key prefix
}
```

A single `application.properties` therefore configures the web port and timeouts *and* the Tentackle backend URL,
credentials, and pool sizing — the two stacks stay in one place without colliding.

### 6. The controllers: PDO access via `DomainContextProvider`

Everything so far exists to make the last step trivial: an ordinary Spring `@RestController` whose methods call
PDOs directly and run as the authenticated user. The bridge is a single interface,
[`DomainContextProvider`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/DomainContextProvider.java).
A controller *implements* it, and in return inherits the same `on(...)` / `op(...)` shorthand that domain code
uses everywhere else in the framework — no `Pdo.create(...)`, no session plumbing, no DTO/entity coupling leaking
into the persistence layer.

A thin base class holds a per-controller [`DomainContext`](../../../../tentackle-pdo/src/main/java/org/tentackle/pdo/DomainContext.java)
and satisfies the interface's single abstract method:

```java
public abstract class RestControllerBase implements DomainContextProvider {

  private final DomainContext context = Pdo.createDomainContext();   // thread-local domain context

  @Override
  public DomainContext getDomainContext() {
    return context;
  }
}
```

`Pdo.createDomainContext()` returns a context bound to the *current* session — and the current session is exactly
what `TentackleDispatcherServlet` attached to the worker thread via `makeCurrent()` (step 2). So although a single
`@RestController` bean is shared across all requests, every method invocation resolves to the session — and thus
the identity — of the client that triggered it. The controller stays a plain Spring singleton; the request-scoped
state lives on the thread, not in the bean.

Implementing `DomainContextProvider` unlocks the convenience methods from the interface:

| Method                    | Equivalent to                                    | Use                                         |
|---------------------------|--------------------------------------------------|---------------------------------------------|
| `on(Ingot.class)`         | `Pdo.create(Ingot.class, getDomainContext())`    | create a PDO to run finders/factories on    |
| `on(Ingot.class, "name")` | same, in a fresh named sub-context               | isolate a unit of work                      |
| `op(SomeOp.class)`        | `OperationFactory…create(…, getDomainContext())` | invoke a remotable operation                |
| `getDomainContext()`      | —                                                | reach the session for explicit transactions |

A concrete controller then extends the base and reads as ordinary PDO code:

```java
@RestController
@RequestMapping("/ingot")
public class IngotController extends RestControllerBase {

  @GetMapping("showAll")
  public List<IngotDTO> showAll() {
    return on(Ingot.class).findAllInStock().stream().map(IngotDTO::create).toList();   // runs as the logged-in user
  }

  @GetMapping("/show")
  public IngotDTO show(@RequestParam String number) {
    return IngotDTO.create(getIngotInStock(number));
  }

  @GetMapping("/update")
  public IngotDTO update(@RequestParam String number, @RequestParam(required = false) Boolean scrap) {
    return getDomainContext().getSession().transaction(() -> {     // wrap writes in a Tentackle transaction
      Ingot ingot = getIngotInStock(number);
      if (scrap != null) {
        ingot.requestTokenLock();                                  // domain rule: must not be in motion
        ingot.setScrap(scrap);
        ingot = ingot.persist();
      }
      return IngotDTO.create(ingot);
    });
  }
}
```

Three conventions worth copying from the `IngotController` example:

- **Reads are a one-liner; writes go through a transaction.** Finder calls like `on(Ingot.class).findAllInStock()`
  need no ceremony. Mutations are wrapped in `getDomainContext().getSession().transaction(() -> …)` so the whole
  unit of work commits or rolls back atomically — and domain rules (`requestTokenLock()`, validation in
  `persist()`) are enforced exactly as they are for a desktop client.
- **Map PDOs to DTOs at the boundary.** Endpoints return small `…DTO` records built with a static `create(pdo)`
  factory, never the live PDO. This keeps the JSON contract stable and avoids serializing persistence internals.
  Use the DTO-wurblet to create the DTOs.
- **Translate failures to HTTP status.** Invalid input or missing/locked entities throw a Spring
  `ResponseStatusException` (`BAD_REQUEST`, `NOT_FOUND`, `NOT_ACCEPTABLE`, …), turning domain-level outcomes into
  meaningful HTTP responses. Because each PDO call already runs as the authenticated user, Tentackle's own
  permission checks apply on top, with no extra code in the controller.

The result is that adding an endpoint is just adding a method: the boot order, per-request session attach, pooling,
and authentication from the previous steps are invisible here — the controller only ever sees "a PDO in the current
user's context".

### Module dependencies

The example module depends on `tentackle-web` plus the application's domain/persistence and the usual pluggable
services (here Groovy scripting and the SLF4J logging backend that matches Spring Boot's Logback):

```xml
<dependency><groupId>org.tentackle</groupId><artifactId>tentackle-web</artifactId></dependency>
<dependency><groupId>org.tentackle</groupId><artifactId>tentackle-log-slf4j</artifactId></dependency>
<!-- + plsbl-domain, plsbl-persistence, Spring Boot starters -->
```

`tentackle-web` itself requires only
[`tentackle-persistence`](../../../../tentackle-persistence/src/main/docs/persistence.md) (transitively the whole
persistence/session/database stack); it pulls in no web library, leaving the choice of web framework entirely to
the application.

---

## Design Notes and Trade-offs

- **Stateless HTTP, stateful persistence.** The web-session-key cache is the only state the bridge adds, and it is
  intentionally *soft* and self-evicting. The web framework remains the source of truth for authentication and
  session lifetime; Tentackle just memoises the matching `SessionInfo` so most requests skip a re-login.
- **Borrow-per-request, not session-per-user.** Persistence sessions are pooled and bound to the *request*, never
  held open per browser. A logged-in user with an idle browser consumes no session — only an entry in the key
  cache — which is what lets a single web tier serve far more users than it has connections.
- **Identity, not just a connection.** Attaching the `SessionInfo` before handing the session out (and detaching it
  on return for DB-connected nodes) is what makes pooled, shared sessions execute as the correct user without any
  per-user connection.
- **One artifact, any topology.** Because `WebApplication` is an `AbstractServerApplication`, the same web daemon
  runs DB-connected or as a proxy purely by changing its `url`, and it can itself sit in front of, or behind, other
  Tentackle servers — see [Multi-Tier Cascade](../../../../src/main/docs/tiers.md).

---

## Related Documentation

- [Tentackle Modules](../../../../src/main/docs/modules.md) — the map of the whole framework.
- [Multi-Tier Cascade](../../../../src/main/docs/tiers.md) — nodes, roles, pooling and caching across tiers; the
  context a web node lives in.
- [Tentackle Session](../../../../tentackle-session/src/main/docs/session.md) — the location-transparent `Session`
  and `SessionInfo` the bridge borrows and binds, and the modification tracking the example uses for live auth.
- [PDO / Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — the objects the REST
  controllers operate on.
- [tentackle-persistence](../../../../tentackle-persistence/src/main/docs/persistence.md) — the server-application
  base (`AbstractServerApplication`) and pools `WebApplication` builds on.
- [Service API](../../../../tentackle-common/src/main/docs/services.md) — the `@Service` mechanism used to plug in
  the web-specific pool, session-info, and modification-tracker implementations.
