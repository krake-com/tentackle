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
   a single client may open.
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
3. Each pool slot (`createSlot`) opens a new **bidirectional** QUIC stream
   (`connection.createStream(true)`) and wraps it in a `QuicTripConnection`.

So one QUIC *connection* carries many TRIP *connections*, each being one QUIC
*stream* — taking direct advantage of QUIC's multiplexing without head-of-line
blocking. Pool sizing (`initial`, `increment`, `minimum`, `maximum`, `idle`,
`usage`) follows the standard TRIP connection-pool parameters read from the URI
query string.

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
tripq://<host>:<port>?ksfile=...&kspass=...&ksalias=...[&kstype=...][&alpnid=...][&maxstreams=...][&certcheck=...]
tripcq://<host>:<port>?...same as tripq...&mincompress=1024
```

The keystore parameters (`ksfile`, `kspass`, `ksalias`, `kstype`) are resolved by the shared
[`SslParameters`](../../../../tentackle-core/src/main/docs/trip.md#ssltls-configuration), so each
also honors a `tentackle.ssl.<name>` system property and, as a fallback, the standard
`javax.net.ssl.<name>` property. See that table for the full precedence rules.

| Parameter     | Side            | Default                                          | Description                                                                                    |
|---------------|-----------------|--------------------------------------------------|------------------------------------------------------------------------------------------------|
| `ksfile`      | server          | `tentackle.ssl.keyStore` / `javax.net.ssl.keyStore` | Path to the TLS keystore file. **Required** (here or via a system property).                 |
| `kspass`      | server          | `tentackle.ssl.keyStorePassword` / `javax.net.ssl.keyStorePassword` | Keystore password; also used as the key password unless `kmpass` is set.   |
| `ksalias`     | server          | the sole key entry, if the keystore has exactly one | Alias of the server certificate within the keystore. Required only for multi-entry keystores. |
| `kstype`      | server          | `tentackle.ssl.keyStoreType` / `javax.net.ssl.keyStoreType` / JDK default | Keystore type, e.g. `PKCS12` or `JKS`.                                  |
| `kmpass`      | server          | the keystore password (`kspass`)                 | Private key password, if it differs from the keystore password.                                |
| `alpnid`      | both            | the URI scheme (`tripq` / `tripcq`)              | ALPN application-protocol identifier. Client and server must match.                            |
| `maxstreams`  | server          | `128`                                            | Maximum concurrent peer-initiated bidirectional streams per connection.                        |
| `certcheck`   | client          | `false`                                          | When `true`, the client validates the server certificate. When `false`, validation is skipped. |
| `mincompress` | both (`tripcq`) | `1024`                                           | Minimum block size in bytes before deflate compression is applied.                             |

> On the server, if the keystore file (`ksfile`) or the certificate alias (`ksalias`, unless the
> keystore holds a single key entry) cannot be resolved, `accept()` throws a
> `TripRuntimeException`. The port must be in the range `0..65535`.

---

## Build, packaging, and jlink notes

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

### jlink / jpackage caveat

Since version 0.10.9, KWIK pulls in the **automatic module**
`io.whitfin.siphash`. Automatic modules cannot be placed on the module path by
jlink. When building a runtime image with the `tentackle-jlink-maven-plugin`,
move `tentackle-quic` and KWIK explicitly to the module path so the plugin does
not, as a side effect, push *all* modules onto it:

```xml
<plugin>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-jlink-maven-plugin</artifactId>
  <configuration>
    <modulePathOnly>
      <module>org.tentackle.quic</module>
      <module>tech.kwik.core</module>
    </modulePathOnly>
    ...
  </configuration>
</plugin>
```

This is safe precisely because `tentackle-quic` is not required by any other
Tentackle module — it only contributes the QUIC transports via
`@TransportService`.

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
