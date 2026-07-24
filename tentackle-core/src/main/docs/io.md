# I/O and Networking — Stream Building Blocks and Reconnection

## Overview and Motivation

`org.tentackle.io` is a small, deliberately unglamorous package: it holds the stream and networking
primitives the rest of the framework is built from. Most applications never import it directly.
It matters because it answers a question the JDK leaves open — **how do you compress, encrypt and
frame a byte stream that is a network link rather than a file?** — and because it contains the
machinery that lets a dropped database or server connection heal itself.

Two themes run through the package:

### Packets, not files

`GZIPOutputStream` and `ZipOutputStream` are built for files. They rely on end-of-file detection to
know where the data ends, and a socket has no EOF until the connection is gone. A communication
link carries a *sequence of packets* over one long-lived stream, and the receiver must know where
each packet ends without closing anything.

Tentackle's answer is to make the packet size part of the protocol. Every stream in this package
that transforms data writes a **two-byte header** in front of each packet, so the reader knows
exactly how many bytes to consume. This framing is the shared base
([`PacketSizedOutputStream`](../java/org/tentackle/io/PacketSizedOutputStream.java) /
[`PacketSizedInputStream`](../java/org/tentackle/io/PacketSizedInputStream.java)) on which
compression and encryption are layered.

### Reconnection as a policy, not an if-statement

Networks fail. A long-running client, a modification tracker polling a server, a nested tier
holding a session to its parent — all of them can lose their connection and, in a correctly built
application, should simply carry on once it returns. Rather than scattering retry loops across the
code, the package factors the decision (*how long to wait, whether to block, how to reconnect*)
into a [`ReconnectionPolicy`](../java/org/tentackle/io/ReconnectionPolicy.java) and the execution
into the [`Reconnector`](../java/org/tentackle/io/Reconnector.java) service.

Everything here is used by the [TRIP transports](trip.md) and the
[database session](../../../../tentackle-database/src/main/docs/database.md); the package has no
dependencies beyond [`tentackle-common`](../../../../tentackle-common/src/main/docs/common.md) and
the [logging API](logging.md).


## Key Concepts

### Packet framing

[`PacketSizedOutputStream`](../java/org/tentackle/io/PacketSizedOutputStream.java) and
[`PacketSizedInputStream`](../java/org/tentackle/io/PacketSizedInputStream.java) are abstract
`FilterStream`s providing `writeHeader(size)` / `readHeader()`. The header is two bytes,
little-endian, so a packet holds at most 64K - 1 bytes. `readHeader()` returns -1 at the end of the
stream. Both track a `closed` flag (`isClosed()`) and make `close()` idempotent — important for
streams stacked on a socket that several layers may try to close.

Subclasses use the header's most significant bit as a flag where they need one, which is why the
usable payload differs between the compressing and encrypting variants.

### Compression

[`CompressedOutputStream`](../java/org/tentackle/io/CompressedOutputStream.java) buffers written
data and deflates it packet by packet. Two optimizations make it suitable for a chatty RPC link:

- **Small packets are not compressed.** Below `minCompressSize` (64 bytes by default) deflating
  costs more than it saves, so the packet goes out verbatim. The header's MSB says which it is,
  and the buffer size is therefore capped at 32K - 1.
- **Incompressible packets are not compressed either.** If the deflated result is not actually
  smaller than the original, the stream writes the original.

The receiving [`CompressedInputStream`](../java/org/tentackle/io/CompressedInputStream.java) reads
the header, inflates or passes through accordingly, and reassembles the application's byte stream.
Both sides log throughput statistics at `FINE`.

The TRIP transports subclass this in
[`FastCompressedOutputStream`](../java/org/tentackle/trip/transport/FastCompressedOutputStream.java),
overriding `createDeflater()` to use `Deflater.BEST_SPEED` — for network traffic, CPU time is worth
more than the last few percent of ratio.

### Encryption

[`EncryptingOutputStream`](../java/org/tentackle/io/EncryptingOutputStream.java) and
[`DecryptingInputStream`](../java/org/tentackle/io/DecryptingInputStream.java) are the same idea
with a [`Cryptor`](../../../../tentackle-common/src/main/docs/common.md) instead of a `Deflater`.
Packets are encrypted whole, prefixed with the two-byte size header (unsigned, so 64K - 1 is the
maximum buffer; the default is half that). The decrypting side adapts its buffer to the incoming
packet size dynamically. Both default to `Cryptor.getInstance()` when no cryptor is passed.

These are what the [`EncryptedTransport`](../java/org/tentackle/trip/transport/EncryptedTransport.java)
and `CompressedEncryptedTransport` stack onto their sockets — see [trip.md](trip.md) for the
transport variants (TCP, SSL, compressed, encrypted, loopback) and when each applies.

### SSL/TLS socket factories

[`TcpSocketFactory`](../java/org/tentackle/io/TcpSocketFactory.java) (client) and
[`TcpServerSocketFactory`](../java/org/tentackle/io/TcpServerSocketFactory.java) (server) are the
SSL-aware replacements for the JDK's default socket factories. The default
`SSLServerSocketFactory` always presents the *first* suitable certificate in the keystore and gives
no way to change that; these factories let you **select the certificate by its alias** and tune the
SSL options (enabled protocols, cipher suites, client-certificate policy, endpoint identification).
Constructed without arguments they behave as plain TCP factories; constructed with an
[`SslParameters`](../java/org/tentackle/io/SslParameters.java) they build an `SSLContext` and produce
configured `SSLSocket`/`SSLServerSocket`s. Client certificates are supported by simply providing a
keystore (and optionally an alias) on the client side.

[`SslParameters`](../java/org/tentackle/io/SslParameters.java) resolves each configuration value from
the transport URI's query string, falling back to a `tentackle.ssl.<name>` system property and then,
for the keystore/truststore settings, to the standard `javax.net.ssl.<name>` property — so existing
JSSE configurations (e.g. in `backend.properties`) keep working. It builds the `SSLContext` once,
wrapping the JSSE key managers so the aliased certificate is always chosen. The parameters and their
resolution order are documented in [trip.md](trip.md#ssltls-configuration).

The [`SslTransport`](../java/org/tentackle/trip/transport/SslTransport.java) (`trips`) and
`CompressedSslTransport` (`tripcs`) use these factories.

### Reconnection

Three pieces cooperate:

- [`ReconnectionPolicy<T>`](../java/org/tentackle/io/ReconnectionPolicy.java) — the application's
  decision, for a resource type `T`. `isBlocking()` decides whether the thread that hit the dead
  resource waits for the resource to come back or moves on; `timeToReconnect()` returns the delay
  before the next attempt (0 aborts reconnection altogether); `reconnect(resource)` performs the
  actual reconnect.
- [`Reconnector`](../java/org/tentackle/io/Reconnector.java) — a `@Service` singleton
  (`Reconnector.getInstance()`, replaceable via the
  [service API](../../../../tentackle-common/src/main/docs/services.md)). `submit(policy, resource)`
  either reconnects inline (blocking policy) or hands the job to a daemon thread pool. It retries
  until `reconnect` succeeds, logging every thousandth failure so a long outage doesn't flood the
  log, and gives up only when the policy says so.
- [`ReconnectedException`](../java/org/tentackle/io/ReconnectedException.java) — thrown *after* a
  successful reconnect, carrying the original failure as its cause. It tells the waiting thread:
  the resource is alive again, but whatever you were doing was lost — start over. It only makes
  sense with a blocking policy; with a non-blocking one the reconnect happens in the background and
  the calling thread must poll for availability itself.

The [database session](../../../../tentackle-database/src/main/docs/database.md) is the canonical
user: `Db.enableReconnection(blocking, millis)` installs a
[`DefaultReconnectionPolicy`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/DefaultReconnectionPolicy.java)
(fixed interval, `reconnect` = `Db.reOpen()`), and `Db.optionallyReconnect()` submits it to the
`Reconnector`. The `DbModificationTracker`'s polling loop catches `ReconnectedException`, logs
"resuming normal operation" and starts its next poll — a server can be restarted underneath a
running client without the client noticing more than a pause.

### Server-side dispatching

[`SocketChannelDispatcher`](../java/org/tentackle/io/SocketChannelDispatcher.java) is a compact
accept loop: give it a name, a `SocketAddress` and a handler factory, and it binds a
`ServerSocketChannel`, accepts connections in a daemon thread pool, and passes each `SocketChannel`
to a fresh handler. It derives the protocol family from the address type, so it serves IPv4, IPv6
and **Unix domain sockets** without further configuration. Resources are released through a
`Cleaner` rather than a finalizer, so a forgotten dispatcher is still closed (with a warning) when
it becomes unreachable; `shutdown()` is the orderly path.

### Byte-array and character streams

The remaining classes are small utilities that fill gaps in the JDK:

| Class                                                                                       | Purpose                                                                                                 |
|---------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------|
| [`NotifyingByteArrayOutputStream`](../java/org/tentackle/io/NotifyingByteArrayOutputStream.java) | A `ByteArrayOutputStream` that notifies waiting threads whenever data is written                     |
| [`BlockingByteArrayInputStream`](../java/org/tentackle/io/BlockingByteArrayInputStream.java)     | Reads from the above, blocking until data becomes available — an in-memory pipe                      |
| [`MutableByteArrayInputStream`](../java/org/tentackle/io/MutableByteArrayInputStream.java)       | A `ByteArrayInputStream` whose buffer can be replaced (`setBuffer`), avoiding a new stream per packet |
| [`AppendableWriter`](../java/org/tentackle/io/AppendableWriter.java)                             | A `Writer` to any `Appendable` — a `StringWriter` replacement that lets you supply the `StringBuilder`/`StringBuffer` (not thread-safe with a `StringBuilder`) |
| [`UriHelper`](../java/org/tentackle/io/UriHelper.java)                                           | Parses a URI query string into a typed map, skipping malformed pairs                                  |

The byte-array streams are what make TRIP's **loopback transport** and `EchoTransceiver` work:
the same serialization path that talks to a socket can talk to memory, which is how a
[single-tier application](../../../../src/main/docs/tiers.md) runs the identical code as a
client-server deployment. `UriHelper` parses the query parameters of transport URIs
(`AbstractTransport`, `AbstractConnectionPool`, and the
[QUIC transport](../../../../tentackle-quic/src/main/docs/quic.md)).


## How It Fits Together

A TRIP client connecting over a compressed, encrypted TCP link stacks the package like this:

```
TRIP serializer  →  EncryptingOutputStream  →  FastCompressedOutputStream  →  socket
                    (Cryptor, 2-byte header)   (Deflater BEST_SPEED,
                                                skips small/incompressible packets)
```

and the server's `SocketChannelDispatcher` accepts the connection, handing the channel to a
handler that mirrors the stack on the way in. Should the link die, the session's
`ReconnectionPolicy` decides whether the caller waits; the `Reconnector` keeps trying; and when it
succeeds, a `ReconnectedException` tells the waiting code to retry its operation. None of this is
visible to application code — it is the plumbing under
[TRIP](trip.md) and the [session](../../../../tentackle-session/src/main/docs/session.md).


## Package Layout

| Group                | Classes                                                                                                                    |
|----------------------|----------------------------------------------------------------------------------------------------------------------------|
| Packet framing       | `PacketSizedOutputStream`, `PacketSizedInputStream`                                                                        |
| Compression          | `CompressedOutputStream`, `CompressedInputStream`                                                                          |
| Encryption           | `EncryptingOutputStream`, `DecryptingInputStream`                                                                          |
| SSL/TLS sockets      | `TcpSocketFactory`, `TcpServerSocketFactory`, `SslParameters`                                                              |
| Reconnection         | `Reconnector`, `ReconnectionPolicy`, `ReconnectedException`                                                                |
| Server sockets       | `SocketChannelDispatcher`                                                                                                  |
| Byte arrays & chars  | `NotifyingByteArrayOutputStream`, `BlockingByteArrayInputStream`, `MutableByteArrayInputStream`, `AppendableWriter`         |
| URIs                 | `UriHelper`                                                                                                                |


## Related Documentation

- [Tentackle Core](core.md) — the runtime foundation this package belongs to.
- [TRIP](trip.md) — the remote invocation protocol and its transports, the main consumer.
- [TRIP over QUIC](../../../../tentackle-quic/src/main/docs/quic.md) — the QUIC transport.
- [Tentackle Database](../../../../tentackle-database/src/main/docs/database.md) — session reconnection in practice.
- [Tentackle Common](../../../../tentackle-common/src/main/docs/common.md) — the `Cryptor` used by the encrypting streams.
- [Multi-Tier Cascade](../../../../src/main/docs/tiers.md) — why the loopback path matters.
