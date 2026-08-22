# Location Transparency vs. REST, RMI and Spring Remoting

## Overview

Every multi-tier Java application has to answer one question: **what happens at the seam between
the tiers?** A desktop client needs data that lives in a database it must not talk to directly. A
node at the edge of a plant network needs to run logic whose authoritative state sits in a data
center. Something has to cross the wire.

There are three broad answers in the Java world:

1. **Hand-rolled REST between your own tiers.** Define endpoints, define DTOs, serialize to JSON,
   call over HTTP, map the errors back.
2. **Classic RPC remoting** — Java RMI, or the Spring Remoting family (`RmiServiceExporter`,
   `HttpInvokerServiceExporter`, Hessian). Declare a remote *service interface* and let a proxy
   make the call look local.
3. **Location-transparent domain objects** — Tentackle's answer. There is no seam to design,
   because the unit that crosses the wire is the domain object itself, and it keeps working on the
   other side.

This document makes that contrast concrete. It is deliberately even-handed: each approach gets a
section naming what it does *better* than Tentackle, and the last section is an honest accounting
of what location transparency costs and does not promise.

> The one-line version:
>
> **REST between tiers makes you build a second, weaker type system and maintain it forever.
> RMI and Spring Remoting make the *call* location-transparent, which is the easy half.
> Tentackle makes the *object* location-transparent — so there is nothing left to hand-roll.**

---

## The Running Example

One feature, carried through all three approaches. An invoice can be approved; approval navigates to
the customer, checks a credit limit against the customer's open items, and writes the invoice back.
The button that triggers it is in a JavaFX client three tiers away from the database.

In Tentackle it is a method on the entity, written once in the domain layer:

```java
// InvoiceDomainImpl — the domain layer, with no remoting in sight
@Transaction
public void approve() {
  Customer customer = getCustomer();                       // fetched on demand, locally or over TRIP
  if (customer.getCreditLimit().compareTo(customer.openAmount()) < 0) {
    throw new DomainException("credit limit exceeded for " + customer.getName());
  }
  setState(InvoiceState.APPROVED);
  me().persist();                                          // whole aggregate validated, then written
}
```

```java
// JavaFX client — the same call, three tiers from the database
invoice.approve();
```

That is the whole feature: one domain method and one call site. The
[`@Transaction`](../../../tentackle-pdo/src/main/docs/transaction.md) opens a real database
transaction on the tier that owns the connection, however far away that is. Keep those lines in
view; the next two sections are about what the other approaches require to reach the same place.

---

## Part 1 — Tentackle vs. Hand-Rolled REST Between Tiers

### What the seam actually costs

REST between your own tiers is not one decision; it is a per-operation tax. For `approve()` you write:

| # | Artifact                                      | Why it exists                                                            |
|---|-----------------------------------------------|--------------------------------------------------------------------------|
| 1 | `InvoiceService.approve(id)` on the server     | the real logic                                                           |
| 2 | `POST /invoices/{id}/approval` controller      | a route, a verb, and a status-code policy for every outcome              |
| 3 | `ApproveRequest` / `InvoiceResponse` DTOs      | entities are unsafe to expose (see [PDOs vs. ORMs](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md)) |
| 4 | JSON mapping for both                          | dates, `BigDecimal` scale, enums, nulls, absent-vs-null                 |
| 5 | A client-side HTTP call                        | URL building, headers, timeouts, retries                                |
| 6 | Client-side DTOs (or a shared module)          | either duplication, or the coupling REST was supposed to avoid          |
| 7 | Exception → status → client-exception mapping  | `409`? `422`? and how does the *message* survive?                       |
| 8 | Auth-token propagation                          | the user identity has to be re-established on every hop                 |
| 9 | A versioning policy for the endpoint            | because the two sides now deploy on their own schedules — in theory     |
| 10| Contract tests                                  | nothing in the compiler checks steps 2–8                                |

Ten artifacts for one verb, of which exactly one is the feature. None of them are hard; that is the
problem. They are easy, endless, and the compiler cannot help you with a single one.

### The parallel type system, and its drift

The DTO layer is the structural cost. You now maintain **two models of the same domain** — the real
one and the wire one — plus a mapping between them. Adding a field to an invoice means touching the
entity, the response DTO, the request DTO, the mapper, the client DTO, and the client mapper. Six
edits, five of which are mechanical, and any one of them can be forgotten. Forgetting one does not
break the build; it produces a `null` in production.

In Tentackle a field is added in the [model definition](../../../tentackle-model/src/main/docs/model-definition.md)
block and the build regenerates everything that needs to know about it. There is no wire model,
because the PDO *is* the wire model — it travels over [TRIP](../../../tentackle-core/src/main/docs/trip.md)
and arrives fully functional, not as a bag of fields.

### The API ends up shaped like your screens

The subtler damage is to the design. Because each call costs a round trip, you cannot afford a
chatty API, so endpoints get coarsened to match whatever the current screen needs — and the next
screen wants a slightly different shape, so it gets its own endpoint, and eventually a
backend-for-frontend. The tier boundary, which started as an architectural line, becomes a mirror of
the UI.

Two well-known symptoms follow:

- **N+1 across the network.** Navigating a relation per row is a latent performance bug locally and
  a catastrophe over HTTP. The fix is an `?expand=lines,customer` parameter — which is a fetch plan,
  hand-rolled, in a query string, with no type checking.
- **Transactions cannot span calls.** A unit of work that touches two endpoints is not a
  transaction. You either coarsen the endpoint until the transaction fits inside it (letting the
  transaction boundary dictate the API shape), or you build a saga with compensating actions.

In a Tentackle cascade neither symptom arises. Relations are navigated on demand at any tier, and
[`@Transaction`](../../../tentackle-pdo/src/main/docs/transaction.md) demarcates a real database
transaction executed on the DB-connected tier, no matter how many hops away the code that declared
it is running.

### Adding a tier

REST does not compose downward. If a gateway has to be inserted between the client and the server —
a security zone, a site uplink, a relay — that gateway has to re-implement every endpoint it
forwards, with its own controllers, its own DTOs, and its own mapping. The tax is paid again per
tier.

In Tentackle a proxy tier is a configuration value: point the node's `url` at the tier above instead
of at the database, and it forwards everything, including calls that did not exist when it was
deployed. See the [Multi-Tier Cascade](./tiers.md).

### Side by side

```java
// ── Hand-rolled REST ───────────────────────────────────────────────
// server
@PostMapping("/invoices/{id}/approval")
ResponseEntity<InvoiceDto> approve(@PathVariable long id) {
  try {
    return ResponseEntity.ok(mapper.toDto(invoiceService.approve(id)));
  }
  catch (CreditLimitException e) {
    return ResponseEntity.status(409).body(null);        // and the message? and the details?
  }
}

// client
HttpResponse<String> res = http.send(HttpRequest.newBuilder()
    .uri(URI.create(baseUrl + "/invoices/" + id + "/approval"))
    .header("Authorization", "Bearer " + token)
    .POST(HttpRequest.BodyPublishers.noBody()).build(),
    BodyHandlers.ofString());
if (res.statusCode() == 409) {
  throw new CreditLimitException("credit limit exceeded");   // reconstructed by hand
}
InvoiceDto dto = json.readValue(res.body(), InvoiceDto.class);
// ...and now map the DTO back onto whatever the UI actually binds to
```

```java
// ── Tentackle ──────────────────────────────────────────────────────
invoice.approve();
```

The exception is not reconstructed by hand either: a `Throwable` survives the trip between tiers
with its type, message and cause intact, and carries a `temporary` flag that drives transparent
retry — see [Exceptions and Error Handling](./exceptions.md).

### Where hand-rolled REST is the right answer

All of the above is about REST **between your own tiers**. As an *external* interface REST is not a
workaround, it is the correct tool, and Tentackle does not compete with it:

- **Heterogeneous or non-Java clients.** A browser, a mobile app, a Python notebook, a partner's
  integration. They cannot speak TRIP, and should not have to.
- **A public or long-lived contract.** When the other side is a different team, a different company,
  or a version you will still be supporting in five years, an explicit, versioned, inspectable
  contract with its own type system is a *feature*. That is exactly what a DTO layer is for, and it
  is worth its cost at a real trust boundary.
- **Independent deployment across an organizational seam.** Two teams shipping on separate cadences
  need a negotiated interface, not a shared artifact.
- **Ecosystem.** OpenAPI, gateways, `curl`, Postman, caching proxies, WAFs, everyone's existing
  monitoring.

The Tentackle position is not "REST is wrong". It is: **pay for a wire contract once, at the edge
where it earns its keep — not between your own tiers, where it buys nothing and costs forever.**
That is precisely what [`tentackle-web`](../../../tentackle-web/src/main/docs/web.md) is for: a
Spring Boot (or servlet, or JAX-RS) application runs as a first-class node in the cascade, speaks
TRIP inward and REST outward, and the DTO layer exists in exactly one place — the outermost one.

---

## Part 2 — Tentackle vs. RMI and Spring Remoting

Classic RPC remoting is Tentackle's nearest relative, and it deserves credit: RMI, and Spring's
`RmiServiceExporter` / `HttpInvokerServiceExporter` / Hessian family, understood that a remote call
should look like a method call, not like a URL. The contrast here is therefore sharper and more
interesting than with REST — it is about *how far* the transparency reaches.

### The headline: transparent calls vs. transparent objects

> RMI and Spring Remoting make the **call** location-transparent.
> Tentackle makes the **object** location-transparent.

This is not a slogan; it decides how much of the ten-line tax from Part 1 you still pay.

Under RMI or Spring Remoting you get a proxy for a *service interface*. The call itself is
transparent, but everything it **returns** is serialized by value into an inert copy. That copy has
no session, no persistence context, and no way to fetch anything it did not bring with it. So if
your persistence layer is JPA, the returned entity is *detached*, and you are immediately back to:

- DTOs, because a detached entity throws `LazyInitializationException` on the client;
- fetch plans, because you must predict on the server every relation the client will touch;
- `merge()`, because the object the client sends back is not the object the server has.

Transparent remoting layered on a non-transparent persistence model buys you the easy half and
leaves the hard half exactly where it was. That is the structural reason the approach never removed
the DTO layer in practice.

A PDO carries its `DomainContext` — and through it a working `Session` — inside itself. Arriving on
another tier, it can still navigate relations, still run queries, still persist. **There is no
`LazyInitializationException`; the exception type does not exist.** The consequences are laid out in
[PDOs vs. Traditional ORMs](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md) and
[PDO](../../../tentackle-pdo/src/main/docs/pdo.md).

### One hop vs. a cascade

RMI and Spring Remoting connect a client to a server. That is the whole topology. Nothing in either
composes into a chain: a node that is simultaneously a server to those below it and a client to
those above it, with its own cache and its own transport choice per link.

In Tentackle that node is the normal case, not an extension. A middle tier is a server and a client
at once; whether it terminates at the database or forwards upstream is decided by its `url`. Caches
stack per tier — reads trickle up until one hits, invalidations trickle down to every leaf — which
is what makes a deep cascade faster rather than merely longer. See
[Multi-Tier Cascade](./tiers.md) and [PDO Caching](../../../tentackle-pdo/src/main/docs/cache.md).

### Infrastructure in the domain signature

RMI puts the transport into your types. Every remote method declares `throws RemoteException`, so
the infrastructure concern is visible in — and propagates out of — every domain interface it
touches. Objects must be exported and unexported, a registry must be found, distributed GC runs in
the background, and the whole model was designed around a Security Manager that the JDK has since
permanently disabled (JEP 486, Java 24); RMI activation was removed outright in Java 17 (JEP 407).

In TRIP, `Remote` is a **marker interface only**. There is no checked remoting exception in a domain
signature, and no export/unexport ceremony: remote delegates and client proxies are created and
reclaimed by the framework.

### The wire

Java Object Serialization is verbose, slow, brittle across class versions, and has been a steady
source of security advisories; RMI is built directly on it, and Spring's HTTP invoker shipped it
over HTTP. TRIP replaces it with a format designed for this job:

- a **type dictionary**, so each class's shape is transmitted once per client and then shared across
  every connection that client holds — not repeated in every message;
- variable-length numeric encoding, skip codes for default values, and optimizations for sparse
  arrays, matrices and collections;
- both reference- and value-based deduplication of repeated objects;
- reflection-driven and annotation-tunable, so class evolution does not mean writing serialization
  code.

### NAT, gateways and constrained links

RMI hands the client a stub containing the server's host, IP and port. Behind NAT, a relay, or an
SD-WAN, that address is meaningless and the call fails — the classic `java.rmi.server.hostname`
ordeal. **TRIP never sends a host, IP or port back downstream**, so gateways and relays work without
special configuration, and each new remote proxy costs no extra round trip.

Transport choice is per link, by URL scheme: plain TCP, TLS, deflate compression for thin WAN links,
pre-shared-key encryption where no PKI exists, and [QUIC/RFC-9000](../../../tentackle-quic/src/main/docs/quic.md)
for lossy cellular or satellite uplinks — mixed freely within one cascade. RMI offers socket
factories and, in practice, TCP.

### What rides along

With RMI or Spring Remoting, the *call* crosses the wire and nothing else. The user identity,
the transaction, the security context and cache coherence are all yours to re-establish on the far
side. A Tentackle session carries the authenticated user and its
[security context](../../../tentackle-pdo/src/main/docs/security.md); `begin`/`commit` are routed to
the tier that owns the database; and cache invalidation ripples down the cascade through
database-backed serial counters, with **no message broker**
([modification tracking](../../../tentackle-session/src/main/docs/modification-tracking.md)).

### Status of the alternatives

Worth stating plainly, because it affects the choice: the **Spring Remoting family is gone**. The
`org.springframework.remoting` exporters — RMI, HTTP invoker, Hessian — were deprecated in Spring
Framework 5.3 and removed in 6.0; Spring's guidance is REST or a dedicated RPC stack. RMI itself
remains in the JDK, but as maintained legacy: activation removed, the Security Manager it assumed
permanently disabled.

### Where RMI or Spring Remoting still win

- **You already have them.** A working RMI deployment that does not need NAT traversal, extra tiers,
  or a rich object model is not a problem to be solved. Tentackle's own
  [RMI → TRIP migration guide](./migrate_rmi_trip.md) exists for when it becomes one.
- **No framework buy-in.** Exporting one service interface is a small, local decision. Tentackle's
  location transparency comes with the PDO programming model attached — it is not a library you
  bolt onto an existing JPA application.
- **Familiarity.** RMI is in the JDK and in every Java book written before 2010.

### Summary table

| Dimension                      | Hand-rolled REST between tiers        | RMI / Spring Remoting                   | Tentackle                                        |
|--------------------------------|---------------------------------------|-----------------------------------------|--------------------------------------------------|
| Unit that crosses the wire     | A DTO you wrote                       | A serialized copy, inert on arrival     | The PDO, fully functional on arrival             |
| Per-operation work             | ~10 artifacts (route, DTOs, mapping…) | A method on a remote interface          | None — it is just a method                       |
| Parallel wire model            | Yes, maintained by hand               | Usually still yes (detached entities)   | No                                               |
| Lazy relation on the far tier  | Impossible — pre-serialize it         | `LazyInitializationException`           | Always works; the exception type does not exist  |
| Fetch plans / `?expand=`       | Hand-rolled, untyped                  | Required (`@EntityGraph`, fetch joins)  | Not needed; [eager relations](../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md) are an optimization, not a correctness device |
| Transaction across the hop     | No — coarsen the endpoint, or a saga  | Yours to propagate                      | `@Transaction`, executed on the DB-connected tier |
| Infrastructure in signatures   | HTTP status codes, everywhere         | `throws RemoteException`                | None — `Remote` is a marker only                 |
| Adding a tier                  | Re-implement every endpoint           | Not a supported shape                   | Change one URL                                   |
| Caching between tiers          | HTTP caching, if the semantics fit    | Yours to build                          | Per-tier PDO cache, invalidation ripples down    |
| Works behind NAT / SD-WAN      | Yes                                   | RMI: painful (stub leaks host/port)     | Yes — no address ever travels downstream         |
| Error fidelity across the wire | Status code + whatever you encode     | Java exception, wrapped                 | Original `Throwable`, with a `temporary` flag    |
| Non-Java clients               | **Yes** — its main strength           | No                                      | No — put a [web node](../../../tentackle-web/src/main/docs/web.md) at the edge |
| Public / versioned contract    | **Yes** — its other main strength     | No                                      | No — same answer                                 |

---

## The Classic Objection: "Location Transparency Is a Lie"

Any senior engineer reading this far is thinking of Waldo et al., *A Note on Distributed Computing*
(1994): the argument that local and remote calls are fundamentally different because of **latency,
partial failure, and concurrency**, and that pretending otherwise produces systems that fail in ways
their authors never modeled. The objection is correct, and it killed a generation of remoting
frameworks. So it is worth being precise about what Tentackle claims.

**Tentackle makes the API shape transparent. It does not claim the physics away.**

- **Partial failure is explicit, not hidden.** Remoting failures surface as exceptions carrying a
  `temporary` flag; [`@Transaction`](../../../tentackle-pdo/src/main/docs/transaction.md) retries on
  temporary failures and propagates the rest ([exceptions](./exceptions.md)). Sessions are grouped
  across tiers so a dead upstream link tears down the whole group rather than leaving half-open
  chains, and a keep-alive daemon detects silent death.
- **Losing coherence is treated as an error, not a degradation.** The change tracker is deliberately
  a *non-daemon* thread: if it dies, the application dies with it, because an application that has
  silently stopped receiving invalidations would be [wrong quietly](./correctness-first.md).
- **Latency is a deployment concern, and it is yours.** A chatty loop over an aggregate is fast in
  one JVM and slow across a satellite uplink, and no framework changes that. What Tentackle provides
  is the machinery to *manage* it where it hurts — a per-tier cache that turns a remote read into a
  local one, eager relations to collapse round trips, and cursors that stream rather than
  materialize. What it removes is the need to *rewrite the code* when the answer changes.
- **Concurrency is not hand-waved.** Optimistic (`serial`) locking is always on, token locking is
  active on the database-owning tier, and shared cached objects are immutable
  ([correctness first](./correctness-first.md)).

The distinction that matters: the 1994 critique targets frameworks that promise you *need not think*
about the network. Tentackle promises something narrower and more defensible — that **thinking about
the network does not require rewriting your domain logic**. Where the code runs is configuration;
what the code means is not.

---

## Honest Limits

Beyond the above, the trade is real and worth naming:

- **Java on both ends.** TRIP is a framework protocol for JVM-to-JVM communication on networks you
  control. It is explicitly *not* a general-purpose serialization library or an internet protocol.
  Polyglot service meshes are not the target.
- **Both ends share the model artifact.** Client and server are built from one domain model, so they
  deploy in a coordinated way. Version skew is *managed* rather than avoided — a client checks the
  server version at login and fails with `VersionIncompatibleException`, and the TRIP type
  dictionary tolerates class evolution — but two teams that need to ship on genuinely independent
  schedules want a negotiated wire contract, which means REST at that seam.
- **The wire is opaque.** No `curl`, no Postman, no Swagger UI, no reading a request in a proxy log.
  You debug with the framework's logging, not with HTTP tooling. For an internal tier boundary this
  is usually a fair trade; for an interface you support externally it is not.
- **It is a programming model, not a library.** You cannot bolt Tentackle's location transparency
  onto an existing JPA/Spring application to fix its DTO layer. It comes with the PDO model.
- **A smaller ecosystem.** Fewer libraries, less Stack Overflow, less hiring familiarity. The model
  is more cohesive; it is also less widely known. That is the honest cost of the coherence.

---

## Choosing

| If your seam is…                                                              | Use                                              |
|--------------------------------------------------------------------------------|--------------------------------------------------|
| Between your own Java tiers, both of which you build and deploy                | Tentackle — the seam disappears                  |
| Between a JVM pinned to hardware and a central database across a fixed network | Tentackle — see [technical & scientific](./technical-scientific.md) |
| Between your application and a browser, a mobile app, or another team          | REST, at a [web node](../../../tentackle-web/src/main/docs/web.md) in the cascade |
| A public, versioned, long-supported contract                                   | REST (or gRPC) — the DTO layer is the point      |
| Between polyglot services on infrastructure you don't control                   | Not Tentackle                                    |
| An existing RMI deployment that works and isn't growing                        | Leave it; [migrate](./migrate_rmi_trip.md) when it stops working |

---

## Related Documentation

- [Multi-Tier Cascade](./tiers.md) — how nodes stack, and why a server is also a client.
- [PDOs vs. Traditional ORMs](../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md) — the other half of the argument: why the object is never in a degraded state.
- [TRIP — Tentackle Remote Invocation Protocol](../../../tentackle-core/src/main/docs/trip.md) — the wire format and transports behind all of this.
- [Why Tentackle Fits Technical and Scientific Applications](./technical-scientific.md) — where an imposed topology makes location transparency decisive.
- [Tentackle Web](../../../tentackle-web/src/main/docs/web.md) — running a Spring Boot/servlet/JAX-RS node as a tier, and putting REST where it belongs.
- [Exceptions and Error Handling](./exceptions.md) — how a `Throwable` survives a trip between tiers, and the `temporary` flag behind transparent retry.
- [Correctness First](./correctness-first.md) — the design philosophy the distributed behavior follows from.
- [Migrate from RMI to TRIP](./migrate_rmi_trip.md) — the practical upgrade path.
