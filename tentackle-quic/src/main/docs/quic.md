# TRIP over QUIC — the `tentackle-quic` module

## Overview

`tentackle-quic` adds a **QUIC** (RFC 9000) transport to
[TRIP](../../../../tentackle-core/src/main/docs/trip.md), Tentackle's Remote
Invocation Protocol. It lets PDOs and remote calls travel between JVMs over UDP
instead of TCP, while reusing all of TRIP's serialization, dictionary, registry,
and remote-proxy machinery unchanged.

QUIC is a modern, UDP-based transport that bundles features TCP-based stacks have
to assemble from several layers:

- **Built-in TLS 1.3** — encryption and authentication are part of the protocol,
  not an optional layer on top.
- **Stream multiplexing without head-of-line blocking** — many independent
  bidirectional streams share a single connection; a lost packet on one stream
  does not stall the others. This maps naturally onto TRIP's connection-pool
  model, where each pooled connection is one QUIC stream.
- **Faster connection establishment** — the TLS handshake is folded into the
  transport handshake, saving round-trips compared to TCP+TLS.
- **Connection migration** — a QUIC connection survives the client changing its
  IP address or port (e.g., switching networks).

The module is a thin adapter: it wires the [KWIK](https://github.com/ptrd/kwik)
QUIC library (`tech.kwik.core`) into TRIP's pluggable transport SPI. No
application code changes are required to use it — selecting QUIC is purely a
matter of the connection URI scheme.

- **Module:** `org.tentackle.quic`
- **Maven artifact:** `org.tentackle:tentackle-quic`
- **Protocol schemes:** `tripq` (plain QUIC), `tripcq` (QUIC + compression)
- **License:** LGPL 2.1

---

## How it fits into TRIP

TRIP's network layer is fully pluggable behind the `Transport` interface, and
transports advertise themselves through the `@TransportService` annotation
(see [Writing a Custom Transport](../../../../tentackle-core/src/main/docs/trip.md#writing-a-custom-transport)).
`tentackle-quic` is a complete, real-world implementation of that SPI built on
`AbstractTransport` — the base class intended for adding a protocol from scratch.

Because the transport is discovered by URI scheme, the rest of the framework is
unaware of QUIC. A client obtains a QUIC transport exactly like any other:

```java
// Client — talk to a server over QUIC
Transport transport = TransportManager.getInstance().requestTransport(
    URI.create("tripq://server.example.site:9090?ksfile=keystore.p12&kspass=secret&ksalias=server"));

// Server — accept incoming QUIC connections
Transport serverTransport = TransportManager.getInstance().requestTransport(
    URI.create("tripq://0.0.0.0:9090?ksfile=keystore.p12&kspass=secret&ksalias=server"));
serverTransport.accept();
```

### Why a separate module?

QUIC support is deliberately **not** baked into `tentackle-core`. Pulling in the
KWIK library (and its transitive dependencies) is only worthwhile for
applications that actually use QUIC. The module therefore:

- depends on `tentackle-core` as an **`optional`** dependency, so it does *not*
  alter the dependency chain of projects that merely use TCP transports, and
- is not `requires`d by any other Tentackle module. It plugs itself in purely
  via the `@TransportService`-annotated transport classes, discovered at runtime
  through the service index.

To activate QUIC, an application simply needs `tentackle-quic` (and `tech.kwik.core`)
on the module/class path; the transports register themselves automatically.

---

## Architecture

The module consists of a handful of classes that mirror TRIP's transport
contract. Server and client responsibilities are clearly separated.

```
                         QuicTripTransport  (@TransportService "tripq")
                         /                \
              server: accept()        client: createClientConnectionPool()
                    |                            |
       ServerConnector (KWIK)          QuicTripConnectionPool
                    |                            |
   QuicProtocolConnectionFactory      QuicClientConnection (KWIK)
                    |                            |
     QuicProtocolConnection            createStream(true) -> QuicStream
       acceptPeerInitiatedStream(s)             |
                    |                     QuicTripConnection
        QuicStream -> createConnectionHandler   (wraps a TripStream)
```

| Class                           | Role                                                                                                          | Side   |
|---------------------------------|---------------------------------------------------------------------------------------------------------------|--------|
| `QuicTripTransport`             | The `Transport` implementation; scheme `tripq`. Starts the KWIK server connector and creates the client pool. | both   |
| `CompressedQuicTripTransport`   | Subclass adding deflate compression; scheme `tripcq`.                                                         | both   |
| `QuicTripConnectionPool`        | Opens one `QuicClientConnection` and creates a pooled `QuicTripConnection` (one QUIC stream) per slot.        | client |
| `QuicTripConnection`            | An `AbstractConnection` wrapping a single bidirectional `QuicStream` as a `TripStream`.                       | client |
| `QuicProtocolConnectionFactory` | KWIK `ApplicationProtocolConnectionFactory`; creates a protocol connection per incoming QUIC connection.      | server |
| `QuicProtocolConnection`        | Accepts peer-initiated QUIC streams and dispatches each to a TRIP connection handler on a virtual thread.     | server |
| `QuicLogger`                    | Bridges KWIK's logging API onto Tentackle's `Logger`.                                                         | both   |
| `service.Hook`                  | `ModuleHook` for resource-bundle resolution within the module.                                                | both   |

### Server side

`QuicTripTransport.accept()` builds and starts a KWIK `ServerConnector`:

1. **TLS keystore** — QUIC mandates TLS, so a server certificate is required. The
   keystore, certificate alias and password are resolved by
   [`SslParameters`](../../../../tentackle-core/src/main/docs/io.md) — the very same
   configuration the TCP-based `trips`/`tripcs` transports use — and the loaded
   keystore is handed to the connector via `withKeyStore(keyStore, alias, password)`.
   This means the `tentackle.ssl.*` system-property fallbacks and the single-entry
   keystore alias auto-selection apply to QUIC too (see below).
2. **Connection config** — `maxOpenPeerInitiatedBidirectionalStreams` is set from
   the `maxstreams` parameter (default 128), capping how many concurrent streams
   a single client may open. The flow control windows are set from `streamwindow`
   (see [Flow control](#flow-control)).
3. **Bind address** — the host/port come from the URI. A host of `"0"` binds all
   interfaces (IPv4 and IPv6) via `withPort(port)`; any other host binds a
   specific `DatagramSocket`.
4. **ALPN protocol handler** — `registerApplicationProtocol(alpnId, factory)`
   registers a `QuicProtocolConnectionFactory`. The ALPN id defaults to the URI
   scheme but can be overridden with `alpnid`.

When a client opens a stream, KWIK calls
`QuicProtocolConnection.acceptPeerInitiatedStream(QuicStream)`. The stream is
handed to `transport.createConnectionHandler(...)` and run on a **virtual thread**
from the transport's connection thread pool — exactly the same handler path used
by every other TRIP transport, so call dispatch, registry lookups, and serialization
behave identically. The stream is closed in a `finally` block when the handler
returns.

The factory declares **no** unidirectional streams
(`maxConcurrentPeerInitiatedUnidirectionalStreams() == 0`, since TRIP is
request/response over bidirectional streams) and effectively unlimited
bidirectional streams (`Integer.MAX_VALUE`), with the real cap enforced by the
`maxstreams` server config above.

### Client side

`QuicTripConnectionPool` is created lazily by `AbstractTransport` on first use:

1. It opens a single `QuicClientConnection` to the URI's host and port, using the
   ALPN id (`alpnid`, default = scheme).
2. By default, server-certificate checking is **disabled**
   (`noServerCertificateCheck()`), matching TRIP's design assumption that the
   transport runs on a trusted, non-public network. Set `certcheck=true` to
   enforce certificate validation.
3. Establishing that connection is bounded by `connecttimeout` (default 10
   seconds). Since QUIC runs over UDP, an unreachable or unresponsive server
   produces no answer at all, so the client waits for the full timeout before
   failing with a `TripRuntimeException`. Lower it when a client should fail
   over quickly rather than wait.
4. Each pool slot (`createSlot`) opens a new **bidirectional** QUIC stream
   (`connection.createStream(true)`) and wraps it in a `QuicTripConnection`.

So one QUIC *connection* carries many TRIP *connections*, each being one QUIC
*stream* — taking direct advantage of QUIC's multiplexing without head-of-line
blocking. Pool sizing (`initial`, `increment`, `minimum`, `maximum`, `idle`,
`usage`) follows the standard TRIP connection-pool parameters read from the URI
query string, with one QUIC-specific difference: `maximum` defaults to
`maxstreams` rather than to *unlimited*. See [Stream limit](#stream-limit).

`shutdown()` closes the underlying `QuicClientConnection`, tearing down all of its
streams at once.

### Stream-to-TripStream bridging

The single integration point with TRIP serialization is `createStream`:

```java
public TripStream createStream(Dictionary dictionary, QuicStream quicStream) {
    TripFactory factory = TripFactory.getInstance();
    return factory.createStream(dictionary,
        factory.createSerializer(quicStream.getOutputStream()),
        factory.createDeserializer(quicStream.getInputStream()));
}
```

A `QuicStream` exposes ordinary `InputStream`/`OutputStream` objects; wrapping
them in a TRIP `Serializer`/`Deserializer` is all that is needed to run the full
TRIP protocol — dictionary compression, back-references, deduplication, and remote
proxying — over QUIC.

---

## Compression: `tripcq`

`CompressedQuicTripTransport` (scheme `tripcq`) extends `QuicTripTransport` and
layers Tentackle's streaming deflate compression on top of every QUIC stream, on
both the server and client paths:

- `createCompressedOutputStream` wraps the QUIC output in a
  `FastCompressedOutputStream`. The `mincompress` parameter (default 1024 bytes)
  sets the threshold below which a block is sent uncompressed, avoiding the
  overhead of compressing tiny payloads.
- `createCompressedInputStream` wraps the QUIC input in a `CompressedInputStream`.

Both `createConnectionHandler` (server) and `createStream` (the TripStream bridge)
are overridden to insert the compression streams, so client and server agree on
the wire format. Use `tripcq` when payloads are large and bandwidth is the
bottleneck; use plain `tripq` when payloads are small or already compact.

---

## Connection URI parameters

All configuration is carried in the connection URI's query string. The same URI
shape is used on both client and server (with host `0` / `0.0.0.0` selecting the
server bind-all case).

```
tripq://<host>:<port>?ksfile=...&kspass=...&ksalias=...[&kstype=...][&alpnid=...][&maxstreams=...][&streamwindow=...][&certcheck=...][&connecttimeout=...][&streamtimeout=...][&quicidle=...][&keepalive=...]
tripcq://<host>:<port>?...same as tripq...&mincompress=1024
```

The keystore parameters (`ksfile`, `kspass`, `ksalias`, `kstype`) are resolved by the shared
[`SslParameters`](../../../../tentackle-core/src/main/docs/trip.md#ssltls-configuration), so each
also honors a `tentackle.ssl.<name>` system property and, as a fallback, the standard
`javax.net.ssl.<name>` property. See that table for the full precedence rules.

| Parameter        | Side            | Default                                                                   | Description                                                                                       |
|------------------|-----------------|---------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------|
| `ksfile`         | server          | `tentackle.ssl.keyStore` / `javax.net.ssl.keyStore`                       | Path to the TLS keystore file. **Required** (here or via a system property).                      |
| `kspass`         | server          | `tentackle.ssl.keyStorePassword` / `javax.net.ssl.keyStorePassword`       | Keystore password; also used as the key password unless `kmpass` is set.                          |
| `ksalias`        | server          | the sole key entry, if the keystore has exactly one                       | Alias of the server certificate within the keystore. Required only for multi-entry keystores.     |
| `kstype`         | server          | `tentackle.ssl.keyStoreType` / `javax.net.ssl.keyStoreType` / JDK default | Keystore type, e.g. `PKCS12` or `JKS`.                                                            |
| `kmpass`         | server          | the keystore password (`kspass`)                                          | Private key password, if it differs from the keystore password.                                   |
| `alpnid`         | both            | the URI scheme (`tripq` / `tripcq`)                                       | ALPN application-protocol identifier. Client and server must match.                               |
| `maxstreams`     | server          | `128`                                                                     | Maximum concurrent peer-initiated bidirectional streams per connection.                           |
| `streamwindow`   | both            | `250000`                                                                  | Flow control window per stream in bytes; must be >= 1500. See below.                              |
| `certcheck`      | client          | `false`                                                                   | When `true`, the client validates the server certificate. When `false`, validation is skipped.    |
| `connecttimeout` | client          | `10`                                                                      | Seconds to wait for the QUIC handshake before giving up. Must be > 0.                             |
| `streamtimeout`  | client          | `10`                                                                      | Seconds to wait for the peer to grant the credits to open another stream. Must be > 0. See below. |
| `quicidle`       | both            | `30`                                                                      | QUIC max idle timeout in **seconds**. See below.                                                  |
| `keepalive`      | client          | unlimited                                                                 | Seconds to keep the QUIC connection alive. `0` disables the keep-alive. See below.                |
| `mincompress`    | both (`tripcq`) | `1024`                                                                    | Minimum block size in bytes before deflate compression is applied.                                |

> On the server, if the keystore file (`ksfile`) or the certificate alias (`ksalias`, unless the
> keystore holds a single key entry) cannot be resolved, `accept()` throws a
> `TripRuntimeException`. The port must be in the range `0..65535`.

### Idle timeout and keep-alive

QUIC connections are not kept open indefinitely: according to
[RFC-9000, 10.1](https://www.rfc-editor.org/rfc/rfc9000.html#name-idle-timeout), both peers
silently discard a connection that stays idle for longer than the *minimum* of the two peers'
`max_idle_timeout` values &mdash; without notifying the application. `quicidle` configures that
timeout on both ends, so the effective value is well-defined rather than being the minimum of two
differing library defaults.

Note that this has nothing to do with the connection pool's `idle` parameter, which is given in
**minutes** and is always much longer. All pooled connections are streams multiplexed over a single
QUIC connection, so the pool would keep handing out connections belonging to a QUIC connection that
is long gone. To prevent that, the client keeps the QUIC connection alive by sending PING frames,
for as long as the pool exists. Set `keepalive=0` to disable this, for example, when the connection
is short-lived anyway, or `keepalive=<seconds>` to limit it. Should the QUIC connection be lost
nevertheless (network loss, peer restart, `keepalive=0`), the pool drops the dead connections and
re-establishes the QUIC connection on the next request. The connection it replaces is closed
explicitly: KWIK stops a connection's keep-alive actor when the connection is closed, but not when it
is terminated silently, and that actor runs on a non-daemon thread that would otherwise keep the JVM
from terminating.

The corresponding pool parameters (`initial`, `increment`, `minimum`, `maximum`, `idle`, `usage`)
are described in [trip.md](../../../../tentackle-core/src/main/docs/trip.md).

### Stream limit

A peer may only open as many concurrent streams as the other end grants it
([RFC-9000, 4.6](https://www.rfc-editor.org/rfc/rfc9000.html#name-controlling-concurrency)), and
`maxstreams` configures that limit. Fresh credits are granted only when a stream is *closed* — but
every pooled connection holds its stream for as long as it sits in the pool, so a busy client walks
straight into the limit. KWIK's `createStream` does not fail there, it waits, and it waits with the
connection pool's *and* the transport's monitor held. No other thread could return a connection to
free a stream, so the wait would never end, and the whole client transport would be stuck.

Two things prevent this. The pool's `maximum` defaults to `maxstreams` instead of *unlimited*, so the
pool stops enlarging itself before it reaches the limit and requests fail with a *temporary*
`TripRuntimeException` ("max. pool size reached") — the caller can retry, and the transport stays
usable. And should the peer nevertheless grant fewer streams than configured here — the client cannot
detect a `maxstreams` mismatch — `streamtimeout` bounds the wait, again with a temporary exception.

Raising `maximum` above `maxstreams` therefore only makes sense if the server grants more.

### Flow control

QUIC limits how much data a peer may send before the receiving side grants further credits
([RFC-9000, 4](https://www.rfc-editor.org/rfc/rfc9000.html#name-flow-control)), both per stream and per
connection. `streamwindow` configures the per-stream window on **both** ends &mdash; on the server it bounds
the size of an incoming remote call, on the client the size of the response &mdash; and the connection window
is ten times that value.

Both ends are configured explicitly, because KWIK's defaults are asymmetric: its client advertises 250kB per
stream, while its server falls back to the 5kB *minimum* of `ApplicationProtocolSettings` unless the buffer
sizes are set. A call larger than the window still succeeds, but has to squeeze through it in stop-and-go
fashion, one credit update per window, which costs a round-trip each time. The default is therefore chosen
to cover ordinary calls in full, and only needs raising when calls routinely carry payloads larger than it.

> Do not size the connection window down to the stream window. Both limits would then be exhausted at the
> very same offset, and since KWIK never retransmits a lost `MAX_DATA` frame and ignores an incoming
> `DATA_BLOCKED` frame, a dropped datagram carrying that update would wedge the stream permanently: the
> receiver waits for data the sender is not allowed to send. Keeping the connection window a multiple of
> the stream window makes the stream limit the binding one, and `MAX_STREAM_DATA` *is* retransmitted.
>
> Mind that the connection window is shared by *all* streams of the connection, so this only holds as long
> as fewer than ten streams transfer at full window simultaneously; beyond that the connection limit can
> become binding after all. The factor cannot simply be raised to cover all `maxstreams` streams, because
> it has to match on both ends and KWIK's client hardcodes exactly this factor in
> `defaultStreamReceiveBufferSize()`, with no way to configure it. Sizing `streamwindow` so that ordinary
> calls fit into it keeps the number of streams blocked on flow control — and with it the exposure — small.

---

## Build and packaging

The module declares its dependencies as:

```xml
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-core</artifactId>
  <optional>true</optional>   <!-- don't modify the dependency chain! -->
</dependency>
<dependency>
  <groupId>tech.kwik</groupId>
  <artifactId>kwik</artifactId>
</dependency>
```

### JPMS descriptor

```java
module org.tentackle.quic {
  exports org.tentackle.quic;
  opens   org.tentackle.quic to org.tentackle.core;   // TRIP instantiates the transport reflectively
  requires transitive tech.kwik.core;
  requires transitive org.tentackle.core;
  provides org.tentackle.common.ModuleHook with org.tentackle.quic.service.Hook;
}
```

The package is `opens`ed to `org.tentackle.core` because TRIP instantiates the
transport reflectively through its `(URI)` constructor when resolving the URI
scheme.

---

## Choosing a QUIC scheme

| Scheme   | Transport  | Encryption         | Compression | When to use                                                                                   |
|----------|------------|--------------------|-------------|-----------------------------------------------------------------------------------------------|
| `tripq`  | UDP / QUIC | TLS 1.3 (built in) | no          | Lossy or high-latency networks; many concurrent streams; mobile clients that change networks. |
| `tripcq` | UDP / QUIC | TLS 1.3 (built in) | deflate     | As `tripq`, when payloads are large and bandwidth-bound.                                      |

Compared with the TCP-based schemes (`trip`, `trips`, `tripe`, `tripc`, …), QUIC
provides TLS without a separate handshake layer and avoids TCP head-of-line
blocking across the many pooled connections a busy client maintains. The trade-off
is a UDP-based stack and the additional KWIK dependency, which is why it ships as
its own optional module.

---

## See also

- [TRIP — Tentackle Remote Invocation Protocol](../../../../tentackle-core/src/main/docs/trip.md)
  — the protocol this transport plugs into, including the
  [custom-transport guide](../../../../tentackle-core/src/main/docs/trip.md#writing-a-custom-transport).
- [KWIK](https://github.com/ptrd/kwik) — the underlying QUIC implementation
  (`tech.kwik.core`).
- RFC 9000 — QUIC: A UDP-Based Multiplexed and Secure Transport.
