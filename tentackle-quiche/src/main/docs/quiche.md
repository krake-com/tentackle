# TRIP over Cloudflare quiche — the tentackle-quiche module

## Overview

`tentackle-quiche` carries [TRIP](../../../tentackle-core/src/main/docs/trip.md) over
[QUIC](https://www.rfc-editor.org/rfc/rfc9000), using
[Cloudflare quiche](https://github.com/cloudflare/quiche) — the Rust implementation that runs a
noticeable share of the public internet — through the Java FFM API (`java.lang.foreign`).

It is the sibling of [tentackle-quic](../../../tentackle-quic/src/main/docs/quic.md), which provides the
same transport on top of [KWIK](https://github.com/ptrd/kwik), a QUIC stack written in pure Java. Both
speak RFC-9000 and **interoperate**: a quiche client can call a KWIK server and vice versa, which the
module's `QuicheKwikInteropTest` verifies on every build.

- Module: `org.tentackle.quiche`
- Artifact: `org.tentackle:tentackle-quiche`
- Schemes: `tripj`, `tripcj` (the `j` is for the Jetty project, which publishes the native binaries)
- License: LGPL 2.1 (the bundled quiche binaries are BSD-2-Clause)

### Which of the two to use

|                 | `tentackle-quic` (KWIK) | `tentackle-quiche` (Cloudflare quiche)    |
|-----------------|-------------------------|-------------------------------------------|
| Implementation  | pure Java               | Rust, called through FFM                  |
| Platforms       | anywhere a JVM runs     | linux/macOS/Windows on x86-64 and aarch64 |
| Extra JVM flags | none                    | `--enable-native-access`                  |

Start with `tentackle-quic`. Reach for `tentackle-quiche` when you want the reference implementation's
congestion control and packet processing, or when you are already shipping native code anyway. Nothing
stops you from deploying both: they register different schemes, so a single application can offer
`tripq` and `tripj` side by side and let each client pick.

## How it fits into TRIP

Exactly as every other transport does — through the
[transport SPI](../../../tentackle-common/src/main/docs/services.md). Nothing in the application changes
but the URI:

```java
// server
Transport server = TransportManager.getInstance().requestTransport(
    URI.create("tripj://0:7001/myservice?ksfile=/etc/tentackle/server.p12&kspass=secret&ksalias=server"));
server.accept();

// client
Transport client = TransportManager.getInstance().requestTransport(
    URI.create("tripj://server.example.com:7001/myservice"));
```

### Why a separate module?

The same reason `tentackle-quic` is one: no other Tentackle module depends on it. It declares
`tentackle-core` as an `optional` dependency and registers itself purely through `@TransportService`,
so an application that does not use QUIC never pulls in the native library.

## Architecture

Quiche is a *protocol* library. It processes packets and keeps connection state; it owns no socket, runs
no event loop, arms no timer, and has no notion of a stream you can read from. Everything KWIK hands
`tentackle-quic` for free, this module has to supply. Hence three layers, of which only the top one is
exported:

```
                    ┌──────────────────────────────────────────────┐
  org.tentackle     │ QuicheTripTransport   CompressedQuicheTrip…  │   the TRIP adapter,
        .quiche     │ QuicheTripConnectionPool  QuicheTripConn…    │   1:1 with org.tentackle.quic
                    │ QuicheProtocolConnection   QuicheLogger      │
                    └──────────────────────────────────────────────┘
                    ┌──────────────────────────────────────────────┐
  org.tentackle     │ QuicheEndpoint   socket + event loop + timers│   what quiche does not do
  .quiche.engine    │ QuicheConnection locking, keep-alive, streams│
                    │ QuicheStream    blocking Input/OutputStream  │
                    │ QuicheServer    retry tokens, version negot. │
                    └──────────────────────────────────────────────┘
                    ┌──────────────────────────────────────────────┐
  org.tentackle     │ Quiche  QuicheConn  QuicheConfig  SockAddr   │   the FFM binding
  .quiche.ffm       │ RecvInfo SendInfo PemExporter NativeLibrary  │
                    └──────────────────────────────────────────────┘
                                        │
                                   libquiche.{so,dylib,dll}
```

| Class                           | Role                                                                         | Side   |
|---------------------------------|------------------------------------------------------------------------------|--------|
| `QuicheTripTransport`           | registers `tripj`, starts the server endpoint, builds the QUIC configuration | both   |
| `CompressedQuicheTripTransport` | registers `tripcj`, wraps the streams in compression                         | both   |
| `QuicheTripConnectionPool`      | one QUIC connection, one stream per pooled TRIP connection                   | client |
| `QuicheTripConnection`          | a pooled TRIP connection on a QUIC stream                                    | client |
| `QuicheProtocolConnection`      | dispatches each incoming stream to a connection handler                      | server |
| `QuicheLogger`                  | bridges quiche's own log onto Tentackle's logging                            | both   |
| `QuicheEndpoint`                | the UDP socket, the event loop, the connection table                         | both   |
| `QuicheConnection`              | one QUIC connection, with its lock and its buffers                           | both   |
| `QuicheStream`                  | a stream as blocking `InputStream`/`OutputStream`                            | both   |
| `QuicheServer`                  | version negotiation, address validation, accept                              | server |

### The event loop

One platform thread per endpoint does everything the protocol needs: receive a datagram, route it by its
destination connection ID, feed it to the connection, run the timers, reap connections quiche reports as
closed. It is a *platform* thread on purpose — the loop lives in `Selector.select()`, a native blocking
call that would pin its carrier if it ran on a virtual thread, and a few endpoints would then starve the
virtual threads doing the actual work.

Application threads never enter the loop. They read and write streams directly.

### Locking

A quiche connection is not thread-safe and the library does no locking of its own, so **every** call into
it happens under that connection's lock: the event loop takes it to feed packets in, application threads
take it to move stream data. This is a correctness requirement, not a tuning decision.

Received data is deliberately *not* drained into Java buffers when it arrives. It stays inside quiche
until somebody reads it, which is what makes QUIC's flow control mean something — buffering it here would
advertise a window this side cannot honor and turn back-pressure into unbounded memory growth.

### Interrupts

A blocked read, a blocked write and the handshake wait are **interrupt-agnostic**, exactly like the
blocking socket streams of the TCP transports: an interrupt of the calling thread does not abort them.
It is remembered and re-asserted when the call returns, so nothing is swallowed either.

This is not a detail. Tentackle's [task dispatchers](../../../tentackle-core/src/main/docs/tasks-daemon.md)
interrupt their own thread whenever work is queued, in order to cut a pending sleep short — see
`DefaultTaskDispatcher.interrupt()`. Such an interrupt regularly lands while the thread is inside a remote
invocation (`DbModificationTracker` polls from its dispatcher thread), and a transport that treated it as
a cancellation would tear the TRIP connection down for a reason that has nothing to do with the
connection. Waiting is therefore ended only by the connection: `QuicheConnection.fail()`, a graceful close
and `close()` all signal the condition and make `checkUsable()` throw, so a connection that is really gone
still releases its readers and writers immediately.

## Server side

1. The keystore, alias and password are resolved by `SslParameters`, exactly as for the TCP transports.
2. Because quiche accepts **PEM file paths and nothing else**, `PemExporter` writes the certificate chain
   and the private key (PKCS#8) to temporary files with owner-only permissions, deleted when the JVM
   exits. This is the one place where a private key leaves the keystore, so the permissions are set when
   the file is created, not afterwards.
3. `QuicheEndpoint` binds the UDP port — `0` as the host binds all interfaces, as elsewhere.
4. `QuicheServer` handles what has to happen before any state is committed to a client, in the order
   RFC-9000 prescribes:
   - **version negotiation** (6) for a client offering a QUIC version quiche does not implement;
   - **address validation** (8.1.2): the first initial packet is answered with a retry carrying a token,
     and only a client that sends the token back has proven it can receive at the address it claims.
     Without this, the server would be an amplifier. The token is an HMAC over the client's address and
     the original connection ID, keyed by a secret generated at startup — unforgeable, entirely
     stateless, and worthless after a restart;
   - **accept**, which finally creates the connection.
5. Each stream a client opens is dispatched to a TRIP connection handler on a virtual thread.

## Client side

1. One QUIC connection per transport, established eagerly by the pool, with every pooled TRIP connection
   a stream multiplexed over it.
2. The server's certificate is **not** verified by default, as for `tripq`: a Tentackle server usually
   presents a self-signed certificate and TRIP authenticates the peers itself. `certcheck=true` turns
   verification on, and then `tsfile` has to name a truststore holding the server's CA — BoringSSL will
   not know it either.
3. `connecttimeout` bounds the handshake.
4. The connection is kept alive with ack-eliciting packets, see below.

## Stream-to-TripStream bridging

```java
public TripStream createStream(Dictionary dictionary, QuicheStream quicStream) {
  TripFactory factory = TripFactory.getInstance();
  return factory.createStream(dictionary,
                              factory.createSerializer(quicStream.getOutputStream()),
                              factory.createDeserializer(quicStream.getInputStream()));
}
```

TRIP serializes onto ordinary blocking streams. `QuicheStream` closes the gap between that and quiche's
"call me again when there is room" API: a call that cannot make progress parks on the connection's
condition until the event loop reports that something moved. Every read is followed by a flush, because
consuming data is what produces the `MAX_STREAM_DATA` frame that lets the peer send more.

## Compression: `tripcj`

`CompressedQuicheTripTransport` inserts `FastCompressedOutputStream` / `CompressedInputStream` between
TRIP and the QUIC stream, controlled by `mincompress`. Identical to `tripcq`.

## Connection URI parameters

```
tripj://host:port/service?parameter=value&...
```

| Parameter        | Side   | Default        | Meaning                                                                   |
|------------------|--------|----------------|---------------------------------------------------------------------------|
| `ksfile`         | server | —              | keystore holding the server certificate (required)                        |
| `kspass`         | server | —              | keystore password                                                         |
| `ksalias`        | server | sole key entry | alias of the certificate to present                                       |
| `kstype`         | server | JDK default    | keystore type                                                             |
| `kmpass`         | server | `kspass`       | private key password                                                      |
| `tsfile`         | client | —              | truststore verifying the server, used when `certcheck=true`               |
| `tspass`         | client | —              | truststore password                                                       |
| `alpnid`         | both   | the URI scheme | ALPN protocol name; both ends must agree                                  |
| `maxstreams`     | both   | 128            | concurrent bidirectional streams granted to the peer                      |
| `streamwindow`   | both   | 250000         | flow control window per stream in bytes                                   |
| `certcheck`      | client | `false`        | verify the server's certificate                                           |
| `connecttimeout` | client | 10             | seconds to wait for the QUIC handshake                                    |
| `quicidle`       | both   | 30             | QUIC max idle timeout in **seconds**                                      |
| `keepalive`      | client | -1             | keep-alive interval in seconds; -1 derives it from `quicidle`, 0 disables |
| `mincompress`    | both   | 1024           | smallest block to compress (`tripcj` only)                                |

## Idle timeout and keep-alive

A QUIC connection is discarded silently by both peers once it stays idle longer than the smaller of the
two idle timeouts (RFC-9000, 10.1). That is far shorter than the pool's own idle timeout, so a pooled
connection would otherwise belong to a QUIC connection that is long gone. The client therefore sends
ack-eliciting packets every `quicidle / 3` seconds by default.

Note that `quicidle` (seconds, QUIC level) has nothing to do with `idle` (minutes, pool level).

## Flow control

`streamwindow` is the number of bytes a peer may send on one stream before the receiver has to grant more
credits. The connection window is `CONNECTION_WINDOW_FACTOR` (10) times that, so the per-stream limit is
the binding one as long as fewer than ten streams transfer at full window simultaneously. Size
`streamwindow` so that ordinary calls fit into it and no call ever has to stop and wait for credits.

## Native library

The binaries ship in `org.mortbay.jetty.quiche:jetty-quiche-native`, built by the Jetty project and
available from Maven Central for linux-x86-64, linux-aarch64, darwin-x86-64, darwin-aarch64 and
win32-x86-64. `NativeLibrary` extracts the one matching the platform to a temporary file and loads it.
Set `-Dtentackle.quiche.library=<path>` to use a different one — a locally built library, or one for a
platform the artifact does not cover.

**`--enable-native-access=org.tentackle.quiche` is required.** Without it the JVM warns on every
restricted call today and will refuse them in a future release.

`SockAddr` is the piece to be suspicious of when porting to a new platform: the BSDs, and with them
macOS, begin `struct sockaddr` with a length byte that Linux and Windows do not have, and `AF_INET6` is
10 on Linux, 30 on macOS and 23 on Windows.

## Build and packaging

```xml
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-quiche</artifactId>
</dependency>
```

The native artifact comes along transitively. The JPMS descriptor:

```java
module org.tentackle.quiche {
  exports org.tentackle.quiche;
  opens org.tentackle.quiche to org.tentackle.core;
  requires transitive org.tentackle.core;
  requires org.mortbay.jetty.quiche;
  provides org.tentackle.common.ModuleHook with org.tentackle.quiche.service.Hook;
}
```

`org.tentackle.quiche.ffm` and `.engine` are internal and not exported.

### Packaging with jlink and jpackage

There is nothing special — the module announces itself through `@TransportService` and provides a module hook — 
so the `tentackle-jlink-maven-plugin` picks it up automatically, just as it does for `tentackle-quic`.

However, the launcher needs `--enable-native-access=org.tentackle.quiche` and the
Freemarker template needs to be updated accordingly.

## See also

- [TRIP over QUIC](../../../tentackle-quic/src/main/docs/quic.md) — the KWIK-based sibling.
- [TRIP](../../../tentackle-core/src/main/docs/trip.md) — the protocol and its transport SPI.
- [I/O and Networking](../../../tentackle-core/src/main/docs/io.md) — `SslParameters` and the stream helpers.
- [Multi-tier cascade](../../../src/main/docs/tiers.md) — where the schemes fit in.
