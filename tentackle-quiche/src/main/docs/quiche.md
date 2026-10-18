# TRIP over Cloudflare quiche — the tentackle-quiche module

## Overview

`tentackle-quiche` carries [TRIP](../../../../tentackle-core/src/main/docs/trip.md) over
[QUIC](https://www.rfc-editor.org/rfc/rfc9000), using
[Cloudflare quiche](https://github.com/cloudflare/quiche) — the Rust implementation that runs a
noticeable share of the public internet — through the Java FFM API (`java.lang.foreign`).

It is the sibling of [tentackle-quic](../../../../tentackle-quic/src/main/docs/quic.md), which provides the
same transport on top of [KWIK](https://github.com/ptrd/kwik), a QUIC stack written in pure Java. Both
speak RFC-9000 and **interoperate**: a quiche client can call a KWIK server and vice versa, which the
module's `QuicheKwikInteropTest` verifies on every build.

- Module: `org.tentackle.quiche`
- Artifact: `org.tentackle:tentackle-quiche`
- Schemes: `tripj`, `tripcj` (the `j` is for the Jetty project, which publishes the native binaries)
- License: LGPL 2.1 (the bundled quiche binaries are BSD-2-Clause)

### Which of the two to use

|                 | `tentackle-quic` (KWIK)    | `tentackle-quiche` (Cloudflare quiche)                                           |
|-----------------|----------------------------|----------------------------------------------------------------------------------|
| Implementation  | pure Java                  | Rust, called through FFM                                                         |
| Platforms       | anywhere a JVM runs        | Linux and macOS on x86-64 and aarch64, Windows on x86-64                         |
| Extra JVM flags | none                       | `--enable-native-access`                                                         |
| Schemes         | `tripq`, `tripcq`          | `tripj`, `tripcj`                                                                |
| Stalled paths   | waits for the idle timeout | migrates to a new source port, see [Connection migration](#connection-migration) |

Start with `tentackle-quiche`: it migrates a stalled connection to a fresh source port instead of
waiting for the idle timeout (see [Connection migration](#connection-migration)), and it brings the
reference implementation's congestion control and packet processing. Reach for
[`tentackle-quic`](../../../../tentackle-quic/src/main/docs/quic.md) when you need a pure-Java stack —
for example, when the platform is not among those quiche ships binaries for, or the operating system is
too old to run them and cannot be upgraded. Nothing stops you from deploying both: they register
different schemes, so a single application can offer `tripj` and `tripq` side by side and let each
client pick.

## How it fits into TRIP

Exactly as every other transport does — through the
[transport SPI](../../../../tentackle-common/src/main/docs/services.md). Nothing in the application changes
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
| `QuicheEndpoint`                | the UDP sockets, the event loop, the connection table                        | both   |
| `QuicheConnection`              | one QUIC connection, with its lock, its buffers and its migration            | both   |
| `QuicheStream`                  | a stream as blocking `InputStream`/`OutputStream`                            | both   |
| `QuicheServer`                  | version negotiation, address validation, accept                              | server |

### The event loop

One platform thread per endpoint does everything the protocol needs: receive a datagram, route it by its
destination connection ID, feed it to the connection, run the timers, reap connections quiche reports as
closed. It is a daemon *platform* thread named `QUIC endpoint <address>` on purpose — the loop lives in
`Selector.select()`, a native blocking call that would pin its carrier if it ran on a virtual thread, and a
few endpoints would then starve the virtual threads doing the actual work.

Application threads never enter the loop. They read and write streams directly.

A server has a single endpoint, shared by all its clients. A client has one endpoint per QUIC connection,
replaced whenever the connection is re-established. Should the loop itself fail — an I/O error of the
selector, or an unexpected exception — every connection on the endpoint fails and the endpoint closes
itself, giving its port back; a server transport then reports `isAccepting() == false`.

What the loop does *not* treat as a failure is a lost datagram. A send that fails, or finds the socket's
buffer full, drops the datagram, and quiche's loss recovery retransmits its content like any other lost
packet. Likewise, an ICMP "port unreachable" that Windows reports on a later `receive()` is ignored. Quiche's
pacing hints are not implemented: packets go out as soon as quiche produces them.

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
It is remembered and re-asserted when the call returns, so nothing is swallowed either. The handshake wait
ends at its deadline, of course.

This is not a detail. Tentackle's [task dispatchers](../../../../tentackle-core/src/main/docs/tasks-daemon.md)
interrupt their own thread whenever work is queued, in order to cut a pending sleep short — see
`DefaultTaskDispatcher.interrupt()`. Such an interrupt regularly lands while the thread is inside a remote
invocation (`DbModificationTracker` polls from its dispatcher thread), and a transport that treated it as
a cancellation would tear the TRIP connection down for a reason that has nothing to do with the
connection. Waiting is therefore ended only by the connection: `QuicheConnection.fail()` and `close()`
signal the condition and make `checkUsable()` throw, so a connection that is really gone releases its
readers and writers immediately. A graceful close signals the condition, too, but the connection only
fails once quiche reports it closed. Until then, and in general, a blocked thread looks at the
connection's state at least every 250 ms (`QuicheStream.POLL_INTERVAL_MILLIS`), so even a wakeup that
never comes cannot park it for good.

## Server side

1. The keystore, alias and password are resolved by `SslParameters`, exactly as for the TCP transports.
   The server never asks its clients for a certificate: they authenticate through TRIP once the
   connection stands.
2. Because quiche accepts **PEM file paths and nothing else**, `PemExporter` writes the certificate chain
   and the private key (PKCS#8) to temporary files with owner-only permissions. Quiche reads them right
   away, so they are deleted as soon as they are loaded, and only a JVM crash in between could leave one
   behind. This is the one place where a private key leaves the keystore, so the permissions are set when
   the file is created, not afterward.
3. `QuicheEndpoint` binds the UDP port — `0` as the host binds all interfaces (IPv6 as well, where
   available), `0.0.0.0` all IPv4 interfaces.
4. `QuicheServer` handles what has to happen before any state is committed to a client, in the order
   RFC-9000 prescribes:
   - **version negotiation** (6) for a client offering a QUIC version quiche does not implement. Only a
     long header packet carries a version, and it is answered only if the datagram is as large as a
     client's initial packet (1200 bytes), so the answer cannot be abused for amplification either;
   - **address validation** (8.1.2): the first initial packet is answered with a retry carrying a token,
     and only a client that sends the token back has proven it can receive at the address it claims.
     Without this, the server would be an amplifier. The token is an HMAC over the client's IP address
     and port, the original connection ID and the time it was issued, keyed by a secret generated at
     startup — unforgeable, entirely stateless, valid for 10 seconds
     (`QuicheServer.RETRY_TOKEN_LIFETIME_SECONDS`), and worthless after a restart;
   - **accept**, which finally creates the connection.

   Any other packet for an unknown connection — a stray short header packet of a connection that is gone,
   say — is dropped. The server sends no stateless resets.
5. Each stream a client opens is dispatched to a TRIP connection handler on a virtual thread.

## Client side

1. One QUIC connection per transport, with every pooled TRIP connection a stream multiplexed over it. The
   pool is created when the transport hands out its first connection, and establishes the QUIC connection
   right away. The server's host name is resolved only then, once for the lifetime of the pool.
2. The server's certificate **is** verified by default, as for `tripq`: against the truststore named by
   `tsfile`, or the JVM's default CAs if none is configured. Neither is known to BoringSSL, so the
   certificates are exported to a PEM file, loaded and deleted again, just like the server's key. A
   self-signed server certificate therefore needs a `tsfile` holding it. A rejected certificate fails the
   connection with a `TripRuntimeException` whose cause is an `SSLHandshakeException` — exactly like the
   TCP/SSL transports and `tripq`/`tripcq` report the same failure, so code reacting to it (such as the
   client auto-update's `LoginFailedWithUpdateHandler`) does not need to know which transport is in use.
   This relies on `quiche_conn_local_error`, which reports the QUIC transport error a rejected certificate
   is encoded as (a TLS alert, per [RFC 9001 §4.8](https://www.rfc-editor.org/rfc/rfc9001.html#section-4.8))
   — any other handshake failure is deliberately left without that cause, to avoid mistaking it for one.
   `certcheck=false` turns verification off — for a client that runs on a trusted, non-public network
   against a server presenting a self-signed certificate and relying on TRIP to authenticate the peers
   itself.
3. `connecttimeout` bounds the handshake.
4. The connection is kept alive with ack-eliciting packets, see below.
5. A connection whose path stalls moves to a new source port, see [Connection migration](#connection-migration).
   One that is lost anyway is re-established on a new endpoint, i.e., from a fresh source port too.
   That happens when the next connection is requested from the pool, not by the periodic monitoring
   of the pool: the monitoring only removes the dead connections and leaves the pool below its
   `minimum` size, because waiting for a handshake there would block the thread that monitors all pools.

## Stream-to-TripStream bridging

```java
TripStream createStream(Dictionary dictionary, QuicheStream quicStream) {
  TripFactory factory = TripFactory.getInstance();
  return factory.createStream(dictionary,
                              factory.createSerializer(quicStream.getOutputStream()),
                              factory.createDeserializer(quicStream.getInputStream()));
}
```

TRIP serializes onto ordinary blocking streams. `QuicheStream` closes the gap between that and quiche's
"call me again when there is room" API: a call that cannot make progress parks on the connection's
condition until the event loop reports that something moved. Every read that returns data is followed by
a flush, because consuming data is what produces the `MAX_STREAM_DATA` frame that lets the peer send more.
Reads and writes are moved through a 16 KiB buffer per connection, so larger ones take several rounds.

Closing the output stream ends the stream with a `FIN`. Closing the input stream tells the peer to stop
sending (`STOP_SENDING`), and a stream is forgotten once both halves are closed. A stream the peer reset
or stopped surfaces as an `IOException`, and so does reading from or writing to a closed half.

## Compression: `tripcj`

`CompressedQuicheTripTransport` inserts `FastCompressedOutputStream` / `CompressedInputStream` between
TRIP and the QUIC stream, controlled by `mincompress`. Identical to `tripcq`.

## Connection URI parameters

```
tripj://host:port/service?parameter=value&...
```

| Parameter        | Side   | Default                                        | Meaning                                                                                         |
|------------------|--------|------------------------------------------------|-------------------------------------------------------------------------------------------------|
| `ksfile`         | server | `tentackle.ssl.keyStore`                       | keystore holding the server certificate; required here or via the system property               |
| `kspass`         | server | `tentackle.ssl.keyStorePassword`               | keystore password                                                                               |
| `ksalias`        | server | sole key entry                                 | alias of the certificate to present; required only for a multi-entry keystore                   |
| `kstype`         | server | `tentackle.ssl.keyStoreType` / JDK default     | keystore type                                                                                   |
| `kmpass`         | server | `kspass`                                       | private key password                                                                            |
| `tsfile`         | client | `tentackle.ssl.trustStore` / JVM's default CAs | truststore verifying the server, used when `certcheck=true`                                     |
| `tspass`         | client | `tentackle.ssl.trustStorePassword`             | truststore password                                                                             |
| `tstype`         | client | `tentackle.ssl.trustStoreType` / JDK default   | truststore type                                                                                 |
| `alpnid`         | both   | the URI scheme                                 | ALPN protocol name; both ends must agree                                                        |
| `maxstreams`     | both   | 128                                            | concurrent bidirectional streams granted to the peer, >= 1; the default of the pool's `maximum` |
| `streamwindow`   | both   | 250000                                         | flow control window per stream in bytes, >= 1500                                                |
| `certcheck`      | client | `true`                                         | verify the server's certificate; any value but `true` turns verification off                    |
| `connecttimeout` | client | 10                                             | seconds to wait for the QUIC handshake, > 0                                                     |
| `quicidle`       | both   | 30                                             | QUIC max idle timeout in **seconds**; 0 disables the timeout (and the derived keep-alive)       |
| `keepalive`      | client | -1                                             | keep-alive interval in seconds; negative derives it from `quicidle`, 0 disables                 |
| `migrate`        | client | 2                                              | probe timeouts without any acknowledgment before migrating, >= 0; 0 disables                    |
| `mincompress`    | both   | 1024                                           | smallest block to compress (`tripcj` only)                                                      |

The keystore and truststore parameters are resolved by the shared
[`SslParameters`](../../../../tentackle-core/src/main/docs/trip.md#ssltls-configuration), exactly as for
`tripq`: each also honors a `tentackle.ssl.<name>` system property and, as a fallback, the standard
`javax.net.ssl.<name>` property. An invalid `maxstreams`, `streamwindow` or `migrate` fails with a
`TripRuntimeException`. On the client, a port that is not > 0 or a `connecttimeout` that is not > 0
fails the creation of the connection pool with an `IllegalArgumentException`. The pool parameters
(`initial`, `increment`, `minimum`, `maximum`, `idle`, `usage`) are described in
[trip.md](../../../../tentackle-core/src/main/docs/trip.md).

Note that `keepalive` is the *interval* between keep-alive packets here, whereas `tentackle-quic` hands it
to KWIK as the *duration* for which the connection is kept alive.

## Idle timeout and keep-alive

A QUIC connection is discarded silently by both peers once it stays idle longer than the smaller of the
two idle timeouts (RFC-9000, 10.1). That is far shorter than the pool's own idle timeout, so a pooled
connection would otherwise belong to a QUIC connection that is long gone. The client therefore sends an
ack-eliciting packet every `quicidle / 3` seconds by default — no more often than every 500 ms, but at
least twice per idle timeout. Keep-alives are sent on that fixed cadence, whether there is other traffic
or not, and only once the connection is established.

That last bound is what makes the keep-alive reliable, and it matters for a `keepalive` configured by hand
as well: a keep-alive falling due together with the idle timeout comes too late, because the endpoint's
event loop runs the timeout first &mdash; once the idle timeout has expired, the peer has dropped the
connection anyway. The packets the connection exchanges on its own (a loss probe, the acknowledgment
answering it) restart the idle timer, so an interval without a comfortable margin below `quicidle` will
keep the connection alive most of the time and lose it occasionally. A configured `keepalive` of half the
idle timeout or more is therefore logged as a warning, though not corrected.

Note that `quicidle` (seconds, QUIC level) has nothing to do with `idle` (minutes, pool level).

## Connection migration

Routers and firewalls commonly spread traffic over parallel paths by hashing each flow's addresses and
ports (ECMP, per-flow load balancing and policing). A flow that lands on a path that is congested,
lossy or throttled stays there — for UDP and TCP alike — while a new source port puts it onto another
one. On long-haul routes such as Europe–China this is a common cause of connections that stall for no
visible reason and recover as soon as they are re-opened. QUIC can move a connection to a new address
without disturbing its streams (RFC-9000, 9), because packets are routed by connection ID rather than by
address. quiche implements the protocol side of this; deciding when to migrate, opening the socket and
routing the packets is left to the application, and this module does it as follows.

**Spare connection IDs.** Once the handshake is complete, each side hands the peer as many further
connection IDs as the peer is willing to hold — the peer's `active_connection_id_limit` counts the one in
use as well, so with this module's `ACTIVE_CONNECTION_ID_LIMIT` of 8 that is 7 spare ones — and routes
packets carrying any of them to the connection. When the peer retires one, the route is dropped and a new ID is
issued. A new path needs an unused ID in each direction, so without these a client could not migrate at
all.

**Stall detection.** On the client, the connection checks whenever quiche's timer expires whether the
probe timeout (PTO) fired `migrate` times in a row without a single byte being acknowledged in between.
Only an established connection is checked, and none that is closing or already probing a new path. The
PTO follows the measured round trip time and backs off exponentially, so the default of 2 amounts to
roughly three times the first probe timeout — a few round trips, well below the idle timeout — on a slow
path as well as on a fast one. The first PTO alone fires on any lost tail; the second one means that not
even the probe got through. That is the best case: the acknowledged bytes are sampled only when the timer
expires, so an acknowledgment that arrived after the last sample makes the first PTO a new baseline, and
the stall is detected one PTO later — about seven times the first one. An idle connection sends its
keep-alives, so a path that dies while nothing is going on is detected as well.

**Migration.** The client opens a new UDP socket bound to the same local address as the endpoint's own
socket (the wildcard address, for a client) on a port chosen by the system, and probes the path from it
(PATH_CHALLENGE). Only once the server has answered does it switch: packets still in flight on the old
path are considered lost and retransmitted on the new one, the connection ID of the old path is retired,
and the old socket is closed — unless it is the endpoint's own, which stays open until the connection is
gone. If the probe fails, the connection stays where it is and tries again once `migrate` further probe
timeouts have passed without an acknowledgment. The server needs no configuration: quiche validates the
new path itself and follows the client, which shows up as `the peer migrated to ...` in the server's log.

**Limits.** Quiche keeps the paths a connection has used, up to the connection ID limit, so the number of
migrations per connection is bounded by `ACTIVE_CONNECTION_ID_LIMIT`. What a stall that finds no spare
connection ID of the server left does, depends on whether a call is in flight. Each pooled connection the
pool lends out holds a lease on the QUIC connection until it is returned, i.e., for the duration of one
call. If there is no lease, the connection is closed right away, and the next call re-establishes it
from a fresh source port instead of waiting for the idle timeout. An idle connection sends its
keep-alives, so a path that dies while nothing is going on is usually detected before the next call
needs it. If there is a call in flight, the connection stays where it is, so that the call survives if
the path recovers. This logs a warning (once per connection), and the idle timeout ends the connection
if the path does not recover. The check is repeated whenever another `migrate` probe timeouts have
passed without an acknowledgment, so the connection is closed soon after the last call has ended. A lease and the decision to close are a single
atomic operation, so a call never starts on a connection that is being closed. A migration
moves the client's source port only: it cannot help if the server's address, or the client's address as a
whole, is what the network is penalizing. The client notices a stall in either direction by its own packets
going unacknowledged; while it is only receiving — a large response, with nothing but acknowledgments
going back — it is the next keep-alive that exposes a broken return path, so detection may take up to one
keep-alive interval longer.

## Flow control

`streamwindow` is the number of bytes a peer may send on one stream before the receiver has to grant more
credits. The connection window is `CONNECTION_WINDOW_FACTOR` (10) times that, so the per-stream limit is
the binding one as long as fewer than ten streams transfer at full window simultaneously. Size
`streamwindow` so that ordinary calls fit into it and no call ever has to stop and wait for credits.

Every pooled connection holds one QUIC stream for as long as it sits in the pool, so the number of
streams limits the pool, too. As with `tentackle-quic`, the pool's `maximum` therefore defaults to
`maxstreams` rather than *unlimited*: a busy client gets a *temporary* `TripRuntimeException`
("max. pool size … reached") the caller can retry, instead of opening one stream too many. Should the
server nevertheless grant fewer streams than the client's `maxstreams` — the client cannot detect
such a mismatch — the two transports differ: `tentackle-quic` waits up to `streamtimeout` for the peer
to grant another stream, while quiche fails at once, also with a *temporary* `TripRuntimeException`
(`no QUIC stream available …`). There is no `streamtimeout` parameter here. Configure the same
`maxstreams` on both ends.

## Native library

The binaries ship in `org.mortbay.jetty.quiche:jetty-quiche-native`, built by the Jetty project and
available from Maven Central for linux-x86-64, linux-aarch64, darwin-x86-64, darwin-aarch64 and
win32-x86-64. `NativeLibrary` extracts the one matching the platform to a temporary file and loads it. It
looks the binary up in the `org.mortbay.jetty.quiche` module of the boot layer first, and on the class path
otherwise. The file is deleted when the JVM exits — except on Windows, which cannot delete a library that is
still loaded, so there the extracted DLL stays in the temporary directory.

Set `-Dtentackle.quiche.library=<absolute path>` to use a different library — a locally built one, or one
for a platform the artifact does not cover, such as Windows on aarch64 or one of the BSDs.

**`--enable-native-access=org.tentackle.quiche` is required.** Without it, the JVM warns on the first
restricted call, and a future release will refuse them. On the class path, use
`--enable-native-access=ALL-UNNAMED`, or, for an executable JAR, the manifest attribute
`Enable-Native-Access: ALL-UNNAMED`.

`SockAddr` is the piece to be suspicious of when porting to a new platform: the BSDs, and with them
macOS, begin `struct sockaddr` with a length byte that Linux and Windows do not have, and `AF_INET6` is
10 on Linux, 30 on macOS, 23 on Windows, 28 on FreeBSD and 24 on OpenBSD and NetBSD.

## Build and packaging

```xml
<dependency>
  <groupId>org.tentackle</groupId>
  <artifactId>tentackle-quiche</artifactId>
</dependency>
```

The version is managed by `tentackle-bom`. The native artifact comes along transitively. The JPMS
descriptor:

```java
module org.tentackle.quiche {
  exports org.tentackle.quiche;
  opens org.tentackle.quiche to org.tentackle.core;
  requires transitive org.tentackle.core;
  requires org.mortbay.jetty.quiche;
  provides org.tentackle.common.ModuleHook with org.tentackle.quiche.service.Hook;
}
```

`org.tentackle.quiche.ffm`, `.engine` and `.service` are internal and not exported.

### Packaging with jlink and jpackage

The module announces itself through `@TransportService` and provides a module hook, so the
`tentackle-jlink-maven-plugin` picks it up automatically, just as it does for `tentackle-quic`. The only
thing to add is `--enable-native-access=org.tentackle.quiche` to the launcher's JVM options, by adapting
the FreeMarker template of the launcher accordingly.

## See also

- [TRIP over QUIC](../../../../tentackle-quic/src/main/docs/quic.md) — the KWIK-based sibling.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the protocol and its transport SPI.
- [I/O and Networking](../../../../tentackle-core/src/main/docs/io.md) — `SslParameters` and the stream helpers.
- [Multi-tier cascade](../../../../src/main/docs/tiers.md) — where the schemes fit in.
