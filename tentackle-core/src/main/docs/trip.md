# TRIP — Tentackle Remote Invocation Protocol

## Overview and Motivation

TRIP (Tentackle Remote Invocation Protocol) is Tentackle's binary serialization and remote procedure call (RPC) framework. 
It supersedes both Java Object Serialization (JOS) and Java RMI as the transport layer for multi-tier Tentackle applications.

TRIP allows [PDOs](../../../../tentackle-pdo/src/main/docs/pdo.md) (Persistent Domain Objects) and other framework types to travel between JVMs — from a JavaFX desktop client to a backend server, or between server nodes in a cluster — 
with full type information, and transparent proxying of remote objects.

### Why not RMI or JOS?

Java Object Serialization is verbose on the wire, slow, brittle across class
versions, and a recurring source of security advisories. RMI layers a rigid,
JOS-based remoting model on top of it, and each remote method must declare `throws RemoteException`, 
polluting domain interfaces with infrastructure concerns. TRIP replaces both with a single,
compact, pluggable protocol:

- **Compact wire format** — variable-length numeric values,
  skip codes for default values, optimizations for sparse arrays/matrices/tensors/collections,
  a *type dictionary* so each class' shape is transmitted only once, and both
  reference- and value-based deduplication of repeated objects. The dictionary
  is shared not only between messages but also among all connections of the same 
  client. This significantly reduces the transfer volume and CPU load on both the server 
  and the clients.
- **Reflection-driven, annotation-tunable** — by default every non-`static`,
  non-`transient` field is serialized; annotations let you
  deviate where needed without writing serialization code.
- **Pluggable transports** — the serialization layer produces and consumes byte
  streams; the network layer below it (`Transport`) is swappable. TCP, TLS,
  compressed, loopback, and [QUIC](../../../../tentackle-quic/src/main/docs/quic.md) (RFC-9000) transports ship with the framework.
  Applications can implement custom transports to support other protocols.
- **Transparent RPC**: Client code obtains a dynamic proxy to a `Remote` interface, which is only a marker interface. 
  The framework routes calls to the server-side `RemoteDelegate` transparently.
  There is no need to explicitly export or unexport remote objects since
  the remote delegates and client proxies are automatically managed.
- **SD-WAN compatible**: unlike RMI, TRIP does not send IP addresses, host names, or port numbers back to the client
  when exposing remote objects, which wouldn't work with NAT.



### Design principles

- TRIP is a **framework-level** protocol, not meant as a general-purpose serialization library or internet protocol. 
- Its top-down design solves the task of transferring
  directed cyclic graphs between JVMs, which is an essential requirement for distributed object-oriented applications that
  are usually **not exposed** to the public internet.
- The wire format is **binary and opaque** — applications should not try to parse or construct TRIP payloads by hand.
- Type safety is **compile-time checked** via Java interfaces (the `Remote` pattern) and generics, not runtime duck typing.

---

## Key Concepts

### Transport

The `Transport` interface (`org.tentackle.trip.Transport`) is the central abstraction. It manages network connectivity in two roles:

| Role       | What it does                                                                                                                                        |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| **Client** | Maintains a connection pool to a single remote endpoint. Each client transport owns one dictionary for types transmitted between server and client. |
| **Server** | Accepts incoming connections from multiple clients. Maintains one dictionary per client (to isolate type definitions between clients).              |

Creating a transport does **not** start any threads or open connections. 
Activity begins only when the first connection is requested. 
Please keep in mind that a TRIP **client** is just a JVM talking to another JVM and may well be 
another **server** since a distributed Tentackle application can build up any kind of network topology.

```java
// Client side — request a transport for a remote endpoint
Transport transport = TransportManager.getInstance().requestTransport(
    URI.create("trip://remotehost.example.site:9090"));

// Server side — create and start accepting
Transport serverTransport = TransportManager.getInstance().requestTransport(
    URI.create("trip://0.0.0.0:9090"));
serverTransport.accept();
```

`TransportManager` is a singleton that deduplicates transports: calling `requestTransport(URI)` with the same scheme, host, port and query string returns the same `Transport` instance. Transports are reference-counted via a lease pattern — they close automatically when the last lease is released.
The server uses virtual threads to handle multiple connections concurrently.

**Built-in transports:**

| Scheme     | Description                                                                                                                               |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `trip`     | Plain TCP sockets                                                                                                                         |
| `trips`    | TCP with SSL/TLS encryption                                                                                                               |
| `tripe`    | TCP with pre-shared key (PSK) encryption                                                                                                  |
| `tripc`    | TCP with deflate compression                                                                                                              |
| `tripcs`   | TCP with both SSL and compression                                                                                                         |
| `tripce`   | TCP with both PSK and compression                                                                                                         |
| `tripq`    | UDP QUIC protocol (provided by `tentackle-quic` module)                                                                                   |
| `tripcq`   | UDP QUIC protocol with compression                                                                                                        |
| `tripj`    | UDP QUIC protocol on Cloudflare quiche (provided by `tentackle-quiche` module)                                                            |
| `tripcj`   | UDP QUIC protocol on Cloudflare quiche, with compression                                                                                  |
| `loopback` | In-memory transport for tests: no socket, accepts a single connection, but runs the server side in separate threads like a real transport |

The compressing and PSK-encrypting variants are built from the packet-framed streams in
`org.tentackle.io` — see [io.md](io.md).

**URI parameters of the TCP transports.** Transport options are passed in the query string of the
URI, e.g. `trips://myserver:18000/MyApp?idle=10&backlog=100`. The QUIC transports have their own set,
see [QUIC](../../../../tentackle-quic/src/main/docs/quic.md) and
[quiche](../../../../tentackle-quiche/src/main/docs/quiche.md).

| Parameter        | Side   | Default | Meaning                                                                                                                           |
|------------------|--------|---------|-----------------------------------------------------------------------------------------------------------------------------------|
| `initial`        | client | `1`     | The initial number of pooled connections to the server.                                                                           |
| `increment`      | client | `1`     | The number of connections opened at once when all pooled connections are in use.                                                  |
| `minimum`        | client | `1`     | The number of connections kept open even when idle.                                                                               |
| `maximum`        | client | `0`     | The maximum number of connections, `0` = unlimited.                                                                               |
| `idle`           | client | `10`    | Minutes after which an unused pooled connection is closed, `0` = never.                                                           |
| `usage`          | client | `0`     | Minutes after which a connection is replaced, no matter how busy, `0` = never.                                                    |
| `connecttimeout` | client | `10`    | Seconds to wait for a connection to the server to be established, including the TLS handshake of the SSL transports. Must be > 0. |
| `idle`           | server | `5`     | Minutes after which an unused thread of the call-handling thread pool terminates.                                                 |
| `backlog`        | server | `50`    | The listen backlog of the server socket.                                                                                          |
| `nodelay`        | both   | `1`     | `0` keeps Nagle's algorithm enabled. By default, `TCP_NODELAY` is set on the client's and on the accepted sockets.                |
| `mincompress`    | both   | `1024`  | Compressing transports only: packets smaller than this many bytes are sent uncompressed.                                          |

Connections are created with the pool locked, by the thread requesting one and by the timeout thread
that all pools share to restore the `minimum`. `connecttimeout` therefore bounds how long a server that
does not answer at all — as opposed to one refusing the connection — can hold up both. Without it, that
would be up to the operating system, which typically keeps retrying for minutes.

Since the query string is part of the transport's identity, two URIs that differ only in their
parameters get separate transports (and connection pools).

### SSL/TLS configuration

The SSL-based transports (`trips` and `tripcs`) create their sockets through the SSL-aware
[`TcpSocketFactory`](../java/org/tentackle/io/TcpSocketFactory.java) and
[`TcpServerSocketFactory`](../java/org/tentackle/io/TcpServerSocketFactory.java). Unlike the default
JSSE factories — which always present the *first* certificate found in the keystore — these allow
**selecting the certificate by its alias** and tuning the SSL options.

The configuration is held by [`SslParameters`](../java/org/tentackle/io/SslParameters.java). Each
value is resolved from up to three sources, in order of precedence:

1. the URI **query parameter** (short key, e.g. `ksfile`),
2. the Tentackle **system property** `tentackle.ssl.<name>` (e.g. `tentackle.ssl.keyStore`),
3. the standard JSSE **system property** `javax.net.ssl.<name>` as a fallback, so existing
   configurations (e.g. in `backend.properties`) keep working.

| Query key     | `tentackle.ssl.` suffix  | `javax.net.ssl.` fallback | Side   | Description                                                                                 |
|---------------|--------------------------|---------------------------|--------|---------------------------------------------------------------------------------------------|
| `ksfile`      | `keyStore`               | `keyStore`                | both   | keystore holding the own certificate(s); a client sets it to present a client cert          |
| `kspass`      | `keyStorePassword`       | `keyStorePassword`        | both   | keystore password                                                                           |
| `kstype`      | `keyStoreType`           | `keyStoreType`            | both   | keystore type (default: JDK default, usually `PKCS12`)                                      |
| `ksalias`     | `keyStoreAlias`          | —                         | both   | **alias of the certificate to present** (optional if the keystore holds a single key entry) |
| `kmpass`      | `keyPassword`            | `keyPassword`             | both   | private key password (default: keystore password)                                           |
| `tsfile`      | `trustStore`             | `trustStore`              | both   | truststore verifying the peer (default: JSSE default trust store)                           |
| `tspass`      | `trustStorePassword`     | `trustStorePassword`      | both   | truststore password                                                                         |
| `tstype`      | `trustStoreType`         | `trustStoreType`          | both   | truststore type                                                                             |
| `protocol`    | `protocol`               | —                         | both   | `SSLContext` protocol (default `TLS`)                                                       |
| `protocols`   | `protocols`              | —                         | both   | comma-separated enabled protocols, e.g. `TLSv1.3,TLSv1.2`                                   |
| `ciphers`     | `cipherSuites`           | —                         | both   | comma-separated enabled cipher suites                                                       |
| `namedgroups` | `namedGroups`            | —                         | both   | comma-separated enabled key-exchange groups in preference order, e.g. `X25519MLKEM768`      |
| `clientauth`  | `clientAuth`             | —                         | server | client-certificate policy: `none` (default), `want` or `need`                               |
| `endpointid`  | `endpointIdentification` | —                         | client | endpoint identification algorithm (default `HTTPS`)                                         |
| `hostcheck`   | `hostCheck`              | —                         | client | verify the server's host name against its certificate (default `true`)                      |

The client verifies the server's certificate against the host name of the URI by default, just like
the QUIC transports `tripq` and `tripj` do. A client connecting by an address the certificate is not
issued for (an IP address, for example) can opt out with `hostcheck=false`: any value but `true` turns
the check off, while the certificate chain is still verified against the truststore. Make sure the
server certificate names its host in a `subjectAltName` entry (e.g. `keytool -ext san=dns:myserver`).

Example — a server presenting the certificate stored under alias `server` and requiring client
certificates, with clients verified against a dedicated truststore:

```
trips://0.0.0.0:18000/myapp?ksfile=server.p12&kspass=secret&ksalias=server&tsfile=clients.p12&tspass=secret&clientauth=need
```

Client certificates are supported symmetrically: a client presents one by providing `ksfile`
(and, to pick a specific one, `ksalias`).

#### Post-quantum key exchange

Since Java 27 ([JEP 527](https://openjdk.org/jeps/527)), TLS 1.3 negotiates the hybrid post-quantum
key exchange `X25519MLKEM768` automatically when both ends support it. This protects recorded TRIP
traffic against "harvest now, decrypt later" attacks. Nothing has to be configured for that.

However, the JDK silently falls back to a classical group (`x25519`, `secp256r1`, …) when the peer
does not support it — a client or server on an older JDK, for example. A site that must not accept
such a downgrade restricts the enabled groups with `namedgroups`:

```
trips://0.0.0.0:18000/myapp?ksfile=server.p12&kspass=secret&namedgroups=X25519MLKEM768
```

A peer without post-quantum support then fails the handshake ("No common named group") instead of
quietly negotiating a classical key exchange. The option can be set on either side, or on both, and
applies via `SSLParameters#setNamedGroups`. If it is not set, the JDK default applies, which in turn
can be changed JVM-wide by the `jdk.tls.namedGroups` system property. The certificates and signatures
remain classical, which is fine against "harvest now, decrypt later": only the key exchange must
withstand a future quantum computer.

The QUIC transports bring their own TLS stacks.
[`tripq`/`tripcq`](../../../tentackle-quic/src/main/docs/quic.md#post-quantum-key-exchange) use KWIK
with the post-quantum key exchanges of `agent15-pqc`: its client honors `namedgroups` as well, but
offers only classical groups unless it is set, and its server cannot restrict the groups at all, so it
only logs a prominent warning when `namedgroups` is set.
[`tripj`/`tripcj`](../../../tentackle-quiche/src/main/docs/quiche.md#post-quantum-key-exchange) use
BoringSSL, which offers `X25519MLKEM768` by default, and honor `namedgroups` on both ends.

### Remote and RemoteDelegate

The `Remote` interface (`org.tentackle.trip.Remote`) is a **marker interface** — it has no methods. Any object whose methods should be invocable from another JVM must implement this interface (indirectly, through its interface hierarchy).

```java
public interface MyService extends Remote {
    String greet(String name);
    Customer lookupCustomer(long id);  // Customer can also implement Remote!
    void updateCustomer(Customer c);
}
```

The actual implementation lives on the server and is wrapped by a 
`RemoteDelegate`. 
The client creates a proxy to invoke methods on the remote object 
over the wire.


```java
MyService remote = TripFactory.getInstance().createRemoteProxy(
        myServiceUUID,           // server-side identity
        transport,               // the transport to use
        MyService.class          // the Remote interface
    );
```

When a method is called:

1. **First call** to a method — the proxy sends an `InitialRemoteCall` containing the method name and parameter types along with the serialized arguments.
2. **Subsequent calls** — the server returns a method index with the first response. The proxy then sends compact `IdentifiedRemoteCall` messages using the method index instead of the name.
3. **Remote results** — if a method returns another `Remote` object, the server registers it as a new delegate and returns a `DelegateDescriptor`. The client automatically wraps it in another proxy.
4. **Result deduplication** — if the result contains deduplicatable objects already seen in the call, 
   the server sends back only a reference instead of the full object.

On the server, `DefaultRemoteDelegate` handles the dispatch: 
it looks up the method by name (or index) via reflection, 
invokes it on the target object, and serializes the result. 
If the result itself is a `Remote` object, the delegate registers it and returns a `DelegateDescriptor`,
which in turn automatically creates a proxy in the client.
This creates a tree of remote objects, and when the root of
the tree is removed from the registry, all remote objects 
belonging to the tree are unregistered as well.
The Tentackle's [Session API](../../../../tentackle-session/src/main/docs/session.md) makes use of this mechanism.
When a session is closed, the remote session delegate is unregistered
together with all other remote objects of the session.

The root itself, however, is unregistered only when the registry is closed, i.e. when all
connections of the client are gone. A root that should be released earlier — like the remote
session when the client logs out — implements [`RemoteRoot`](../java/org/tentackle/trip/RemoteRoot.java).
The registry then hands the object its coordinates via `setRegistryCoordinates(registry, id)` when it
creates the delegate, and the object invokes `unregisterFromRegistry()` when it is no longer needed,
which also unregisters the whole tree below it.

#### Returning several remote objects at once

A remote method may also return an **array of `Remote` objects**. Each element is registered
individually, exactly as a single `Remote` result would be, and the client receives an array
of proxies:

```java
public interface MyService extends Remote {
    Remote[] lookupServices(String[] names);   // one roundtrip for all of them
}
```

Elements may be `null`, which allows the server to answer "no such remote object" for individual
entries without failing the whole call. The elements join the returning delegate's tree, so they
are unregistered together with it.

This is what the session layer uses to
[preload remote delegates](../../../../tentackle-database/src/main/docs/database.md#preloading-remote-delegates):
on links with a high latency, fetching hundreds of delegates one by one is the dominating cost
of the application startup.

Notice that only arrays with a component type of `Remote` (or a subtype) are treated this way.
Collections and `Object[]` are serialized as ordinary results, since scanning every list-shaped
result for remote objects would slow down all other remote calls.

### Registry and RemoteLookup

The `Registry` (`org.tentackle.trip.Registry`) is the server-side name/address lookup service. It maps:

| Map                     | Purpose                                       |
|-------------------------|-----------------------------------------------|
| `UUID → RemoteDelegate` | Fast lookup by remote object identity         |
| `String → UUID`         | Named registration for human-readable lookup  |
| `UUID → String`         | Reverse lookup of the registered name         |
| `Remote object → UUID`  | Reverse lookup for already-registered objects |

The registry has a fixed identity (`REGISTRY_ID = new UUID(0, 0)`) and registers itself. Clients access it through a `RemoteLookup` proxy:

```java
// Look up a named service on the server
MyService service = transport.lookup(MyService.class, "myService");
```

### TripStream

The `TripStream` (`org.tentackle.trip.TripStream`) is the high-level I/O context that wraps three components:

- A `Dictionary` — for type compression
- A `Serializer` — low-level primitive byte writer
- A `Deserializer` — low-level primitive byte reader

It manages object identity tracking (reference maps), dictionary management, and opcode dispatch. Applications typically interact with `TripStream` only when writing custom transports or custom `TripType` implementations.

The stream also carries an optional `TripFilter` that decides which components of an object are written, see [Filtering components with TripFilter](#filtering-components-with-tripfilter).

### TripType, TripComponent, and Dictionary

#### TripType

A `TripType<T>` (`org.tentackle.trip.TripType`) is a type descriptor that knows how to serialize and deserialize a specific Java type to and from a `TripStream`. TripTypes are **stateless singletons** — they describe *how* to serialize a type, not the data itself.

```java
TripType<Customer> customerType = TripFactory.getInstance().getType(Customer.class);
```

TripTypes are categorized by `TripTypeCategory` (`CLASS`, `INTERFACE`, `RECORD`, `ENUM`, `PRIMITIVE`, `ARRAY`, `PROXY`). Each TripType knows:

- Its components (`TripComponent[]`) — the fields/properties that make up the type
- Whether it is referenceable (can use back-references)
- Whether it is deduplicatable (equal objects share a single wire representation)
- Whether it is final (cannot be assigned to a supertype on the wire)

#### TripComponent

A `TripComponent<P, T>` (`org.tentackle.trip.TripComponent`) describes one serializable member of a parent type. It provides:

- `getValue(parent)` / `setValue(parent, value)` — read/write access
- `getType()` — the `TripType` for this component
- `getOrdinal()` — position in the serialized dictionary
- `hasDefaultValue()` / `getDefaultValue()` — components with default values are omitted during serialization
- `write(parent, stream, lastOrdinal)` / `read(parent, stream, opCode)` — serialize the component
  of a parent object, and deserialize it back into one. This is what `ObjectTripType` and
  `RecordTripType` drive their component loops with, rather than combining `getValue`/`setValue`
  with the component's `TripType` themselves. Writing goes through the component so that the
  value is read from the parent exactly once, and so that a component of a primitive type can
  keep its value unboxed all the way to the serializer.

Component implementations:

| Type                     | Implementation                | Access mechanism                                                             |
|--------------------------|-------------------------------|------------------------------------------------------------------------------|
| Fields                   | `FieldTripComponent`          | `VarHandle` (final fields are refused, see [JEP-500](#final-fields-jep-500)) |
| Fields of primitive type | `PrimitiveFieldTripComponent` | `VarHandle`, invoked with a primitive signature                              |
| Getters/setters          | `MethodTripComponent`         | Reflective `Method.invoke`                                                   |
| Record components        | `RecordTripComponent`         | `MethodHandle` bound to the accessor                                         |

Two further implementations serve internal purposes: `EnumTripComponent` represents a single enum
constant, and `ClassTripComponent` represents an interface of a dynamic proxy or the component
type of an array (its value is the class itself).

`MethodTripComponent` is not created by the default field scan. It is used for a `@CanonicalGetter`
whose value is not covered by a serializable field (see
[JDK collections and maps](#jdk-collections-and-maps)) — without a setter, since the value is
passed to the canonical constructor — and by component providers such as the one for `Throwable`
(see [`@TripComponentProvider`](#tripcomponentprovider)).

#### Dictionary

The `Dictionary` (`org.tentackle.trip.Dictionary`) is a type registry shared across connections. It maps class names to `DictionaryEntry` objects, each with a unique ordinal.

When an object is first serialized:

1. The full type definition (class name + component names) is sent over the wire
2. The dictionary assigns an ordinal to this type
3. All future uses of the same type send only the ordinal
4. When a type is encountered for the first time at the client side, the
   client sends a temporary dictionary entry which is eventually
   confirmed by the server by sending back a permanent entry. As a result, the server
   is the main source of truth for type definitions.

This compression is maintained separately per client on the server side and as a single dictionary on the client side.

##### Dictionary lifetime and the connection handshake

Every connection starts with a short preamble:

| Bytes | Meaning                                        |
|-------|------------------------------------------------|
| 2     | the TRIP magic number                          |
| 16    | the UUID of the client's transport             |
| 1     | whether the client's dictionary is still empty |

The server keeps one dictionary per client transport UUID and holds it only weakly: once the last
connection of that transport is closed, the dictionary is garbage collected. The client, in
contrast, keeps its dictionary for the whole lifetime of its transport. That asymmetry is why the
last byte exists.

When a connection arrives and the server has no dictionary for its transport UUID — because it was
collected or because the server was restarted — it may only create a new one if the client says
its own dictionary is still empty. A client that already learned entries would otherwise send
ordinals the new dictionary never saw, and the receiving side would block until
`Dictionary.getEntry(int)` times out. Such a client is rejected with *"no such dictionary … (stale
client transport?)"* and has to establish a new transport, which gives both sides a fresh
dictionary. Applications that reconnect must therefore build a new `Transport` rather than reuse
the rejected one.

Because the preamble is part of the wire format, a peer built against a different handshake version
fails immediately with an *"invalid magic"* error instead of misreading the header. The one exception
is a client of the directly preceding version (whose preamble lacks the dictionary byte, see
`TripConstants.MAGIC_1`): the server accepts it with a warning and treats its dictionary as empty,
so that an auto-updating client can still reach the update endpoint and fetch the current version.

#### OpCode

Every serialized block starts with a single-byte `OpCode` (`org.tentackle.trip.OpCode`) that tells the deserializer what type of data follows:

| Range           | Meaning                                    |
|-----------------|--------------------------------------------|
| `0x00` – `0x7F` | Reserved for Tentackle core types          |
| `0x80` – `0xFF` | Available for application-specific opcodes |

Key opcode categories:

| Category         | Examples                             | Description                               |
|------------------|--------------------------------------|-------------------------------------------|
| Fixed type       | Primitives, `String`, dates          | The opcode alone determines the type      |
| Dictionary-based | Objects, records, enums, collections | Opcode + dictionary entry ordinal         |
| Predefined array | `int[]`, `byte[]`, `String[]`        | Type code + size + elements               |
| Reference        | Back-reference                       | 1–4 byte compact index                    |
| Error            | Various                              | Serialization/deserialization error codes |

#### TripFactory

```
org.tentackle.trip.TripFactory
```

`TripFactory` is the central singleton factory for all TRIP infrastructure objects. Key methods:

```java
TripFactory factory = TripFactory.getInstance();

TripType<T>    type      = factory.getType(MyClass.class);
TripStream     stream    = factory.createStream(dictionary, serializer, deserializer);
Serializer     ser       = factory.createSerializer(outputStream);
Deserializer   deser     = factory.createDeserializer(inputStream);
Dictionary     dict      = factory.createDictionary(transportId, isServer);
Registry       registry  = factory.createRegistry();
Transport      transport = factory.createTransport(uri);
TransportManager manager = factory.createTransportManager();
```

---

## Serialization Deep Dive

### How default serialization works

The serialization flow is driven by `TripFactory.getType(Class)`:

1. **Lookup**: The factory checks a cached `ConcurrentHashMap` of known `TripType` instances.
2. **Predefined mapper**: If no cached type exists, the factory checks `@TripTypeService` annotations to find a user-provided `TripType` for the class.
3. **Builder fallback**: If no predefined type is found, a `TripTypeBuilder` generates one at runtime:
    - Classes annotated with `@TripViaJOS` → serialized via Java's built-in serialization
    - Lambdas → `LambdaTripType`, which fails at serialization time unless the capturing class
      is annotated with `@TripSerializeLambda`
    - Regular classes → `ObjectTripType`
    - Records → `RecordTripType`
    - Enums → `EnumTripType` (constants with class bodies are mapped to their enum class)
    - Collections/Maps → `CollectionTypeBuilder` / `MapTypeBuilder`, including the JDK types
      without usable constructors: the `Collections` wrappers (`unmodifiable*`, `synchronized*`,
      `empty*`, `singleton*`, `nCopies`, `newSetFromMap`), `Arrays.asList`,
      `ConcurrentHashMap.newKeySet`, `EnumSet` and `EnumMap`

4. **Component construction**: For `ObjectTripType`, the builder scans the class for serializable fields:
    - Skips `transient`, `static`, and fields annotated with `@TripIgnore`
    - Uses direct field access (`FieldTripComponent`, or `PrimitiveFieldTripComponent` for primitives);
      a `@CanonicalGetter` without a serializable field gets a `MethodTripComponent`

5. **Serialization (write)**:
   ```
   send OpCode (e.g., 0x38 for OBJECT)
   if first time for this type:
       send Dictionary entry (class name + component names)
   if object is null:
       send NULL (0x00)
       return
   if object is referencable and already seen:
       send Reference opcode + back-reference index
       return
   if object is deduplicatable and already seen:
       send Reference opcode + back-reference index
       return
   send instance (via canonical constructor or no-arg constructor)
   for each component:
       send component value (recursively)
   ```

6. **Deserialization (read)**:
   ```
   read OpCode
   if fixed type:
       read the value directly
   if dictionary-based:
       read dictionary entry
       resolve TripType from DictionaryEntry
       if REFERENCE:
           look up back-reference index
           return cached object
   if object is referencable and not yet seen:
       cache a placeholder
   create instance (canonical constructor or no-arg constructor)
   for each component (in dictionary order):
       read component value (recursively)
   invoke the method named by @TripSerializable(validate = ...), if any
   return instance
   ```

### JDK collections and maps

Java collections hold internal delegates that cannot be instantiated directly, so TRIP serializes
their *elements* only, never their fields — see `CollectionTripType#isFieldSerializable`. On the
receiving side the collection is rebuilt through its sized (or no-args) constructor and refilled.
Consequently, everything the JDK keeps in a private field is *not* transported unless a dedicated
`TripType` takes care of it.

A subclass can transport such state itself: annotate a getter for it with `@CanonicalGetter` and the
constructor taking it back with `@CanonicalConstructor` (see
[canonical construction](reflect.md#canonical-construction)). TRIP then creates the map or
collection through that constructor and sends its components *ahead* of the entries, since the
container cannot exist before its constructor arguments are known.
[`ChainedProperties`](../java/org/tentackle/misc/ChainedProperties.java) does exactly this for the
`defaults` of a `Properties`. A map or collection with only a canonical constructor — neither sized
nor no-args — is serializable this way as well.

The full matrix is verified by `JdkCollectionsTest`. In short:

**Round trip faithfully** — the concrete class, the content, the iteration order, null elements,
null keys and null values, shared references and cycles:

| Kind      | Types                                                                                                                                                               |
|-----------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Lists     | `ArrayList`, `LinkedList`, `Vector`, `Stack`, `CopyOnWriteArrayList`                                                                                                |
| Sets      | `HashSet`, `LinkedHashSet`, `TreeSet`, `EnumSet`, `CopyOnWriteArraySet`, `ConcurrentSkipListSet`                                                                    |
| Queues    | `ArrayDeque`, `PriorityQueue`, `ConcurrentLinked{Queue,Deque}`, `LinkedBlocking{Queue,Deque}`, `ArrayBlockingQueue`, `PriorityBlockingQueue`, `LinkedTransferQueue` |
| Maps      | `HashMap`, `LinkedHashMap`, `TreeMap`, `Hashtable`, `Properties`, `ConcurrentHashMap`, `ConcurrentSkipListMap`, `IdentityHashMap`, `WeakHashMap`, `EnumMap`         |
| Immutable | `List.of`, `Set.of`, `Map.of` and their `copyOf` variants                                                                                                           |
| Wrappers  | `Collections.{unmodifiable,synchronized,empty,singleton}*`, `nCopies`, `newSetFromMap`, `Arrays.asList`, `ConcurrentHashMap.newKeySet`                              |

Every collection ordered by a `Comparator` transports it as part of the header, since it lives in
a JDK-internal field and would otherwise leave the receiver re-sorting by natural ordering — or
failing outright for elements that are not `Comparable`:

| Types                                                             | Trip type                  |
|-------------------------------------------------------------------|----------------------------|
| `TreeSet`, `ConcurrentSkipListSet`                                | `SortedSetTripType`        |
| `TreeMap`, `ConcurrentSkipListMap`                                | `SortedMapTripType`        |
| `PriorityQueue`, `PriorityBlockingQueue`                          | `PriorityQueueTripType`    |
| `Collections.{unmodifiable,synchronized}{SortedSet,NavigableSet}` | `WrappedSortedSetTripType` |
| `Collections.{unmodifiable,synchronized}{SortedMap,NavigableMap}` | `WrappedSortedMapTripType` |

This requires a comparator constructor, which every JDK implementation provides. The comparator
must be serializable itself, i.e., a static or top-level class — an anonymous inner class would
expect the enclosing instance as a constructor argument.

A priority queue restores its *heap*, not the heap's layout: the elements are re-inserted one by
one, so the iteration order may differ from the sender's. The head of the queue and the polling
order are identical.

**Round trip, but arrive reconfigured** — the content is intact, the setup is not:

- the capacity of the bounded queues is reduced to the number of elements,
- the access-order flag of a `LinkedHashMap` (LRU caches) is lost,
- the `defaults` of a `Properties` are lost — use
  [`ChainedProperties`](../java/org/tentackle/misc/ChainedProperties.java) instead, which transports
  the whole chain,
- the backing map of `Collections.newSetFromMap` becomes a `LinkedHashMap`,
- the `Collections` wrappers are rebuilt around an `ArrayList`, hence the `RandomAccess`
  variant of the wrapper is delivered.

**Are rejected** with a `TripInstantiationException`, since they cannot be reconstructed without
their backing collection or type token — copy them into a regular collection before sending:

- the views: `List#subList`, `Map#keySet`, `Map#values`, `Map#entrySet`,
  `NavigableMap#descendingMap`, `NavigableSet#descendingSet`,
- `Collections.checked*`,
- `Map.entry` (`java.util.KeyValueHolder`) and `BitSet`, which are neither collections nor maps.

### JDK exceptions

The JDK does not open its packages, so TRIP cannot access the private fields and non-public
constructors of the JDK's exceptions — and many of those fields are final anyway (see
[Final fields](#final-fields-jep-500)). The
[`ThrowableComponentProvider`](../java/org/tentackle/trip/provider/ThrowableComponentProvider.java)
therefore never reads a field declared in a `java.*` class directly (except a public non-final one, such as
`InterruptedIOException.bytesTransferred`): the message, cause and stack trace go through their accessors, and
any other field through its public getter of the same name, if there is one. Such a getter-only value cannot be
written back; it is meant for an instance creator that passes it to the constructor.

**Arrive with message, cause and stack trace** — any exception that has a usable constructor taking the
message and/or the cause. The cause parameter may be of any type the cause is an instance of, such as
`UncheckedIOException(String, IOException)`, and constructors that are not accessible, such as the protected
`ExecutionException(String)`, are skipped.

**Arrive with all their values** — created by a predefined instance creator from the getter values
(see [`AbstractThrowableInstanceCreator`](../java/org/tentackle/trip/creators/AbstractThrowableInstanceCreator.java)):

- `InvocationTargetException` (with its target),
- `FileSystemException` and its subclasses, such as `NoSuchFileException` and `AccessDeniedException`,
- `DateTimeParseException`, `ParseException`,
- `URISyntaxException`, `PatternSyntaxException`, `InvalidPathException`,
- `MissingResourceException`,
- with tentackle-database: `SQLException` and its subclasses, including the next exception, the update
  counts of a `BatchUpdateException`, the values of a `DataTruncation` and the failed properties of an
  `SQLClientInfoException`. A driver-specific exception, such as PostgreSQL's `PSQLException`, is sent as its
  nearest superclass provided by the JDK, since the client usually doesn't have the JDBC driver on its classpath.

**Arrive as `TripInstantiationException`** — an exception without any usable constructor. It carries the original
message, cause and stack trace, and names the original class. The type is never refused, so a non-transferable
exception, even as the cause of another one, no longer breaks the response. Provide an instance creator
(extending `AbstractThrowableInstanceCreator`) and, for values without a getter of the same name, a component
provider (extending `ThrowableComponentProvider` and overriding `getAdditionalGetterNames`) to transfer such an
exception completely.

Suppressed exceptions are not transferred.

### Annotation-driven customization

TRIP's annotations let you tweak serialization behavior without writing custom `TripType` code.

#### `@TripIgnore`

Marks a field or getter/setter method to be skipped during serialization. 
Useful for computed fields, security-sensitive data, or fields that can be reconstructed on 
the other side.

```java
@TripIgnore
private byte[] internalBuffer;

```

#### `@TripSerializable`

Set `value = false` to disable serialization entirely:

```java
@TripSerializable(false)
public class AuditLog {
    // This type will never be serialized via TRIP
}
```

Controls lifecycle hooks applied during serialization:

```java
@TripSerializable(
    prepare = "prepareForWire",   // called before serialization
    validate = "validateFromWire" // called after deserialization
)
public class CustomerOrders implements DomainContextProvider {
    
    private long customerId;
    private List<Order> orders;
    
    public void prepareForWire() {
        if (orders == null) {
            orders = on(Order.class).findByCustomerId(customerId);
        }
    }
    
    public void validateFromWire() {
        if (orders != null) {
            orders.forEach(Order::validate);
        }
    }
}
```

#### `@TripDeduplicatable`

Opts a type into value-based deduplication (only records and Strings longer than 5 characters are
deduplicated by default). The type **must** honor the `equals`/`hashCode`
contract! The value is a method name or `TripDeduplicatable.NEVER` /
`TripDeduplicatable.ALWAYS` (default method: `isDeduplicatable`).

```java
@TripDeduplicatable  // explicitly enable on a class
public class Address {
    private final String street;
    private final String city;
    // equals/hashCode must be implemented!
}

// disable deduplication since benefits would be minimal
@TripDeduplicatable(TripDeduplicatable.NEVER)
public record Point(double x, double y) {
}
```

The annotation accepts three forms:
- **No argument or `"ALWAYS"`** — always deduplicate
- **`"NEVER"`** — never deduplicate
- **Method name** — call this method at runtime to decide (e.g., `"isDeduplicatable"`)

#### `@TripReferencable`

Controls whether an object uses back-references. Referencable objects are tracked by identity: 
if the same instance appears multiple times, subsequent appearances send a reference index instead of the full data.

```java
// Default: most objects are referencable
public class GraphNode {
    // Safe — identity tracking prevents infinite loops
    private GraphNode parent;
}

// value objects usually don't benefit from back-references 
// and speed up serialization if reference tracking is disabled
@TripReferencable(TripReferencable.NEVER)
public class Point {
  private final int x;
  private final int y;

  public Point(int x, int y) {
    this.x = x;
    this.y = y;
  }
}

// if not all objects are referencable
@TripReferencable("isShared")
public class SharedConfig {
    private final boolean shared;
  
    public boolean isShared() {
        return shared;
    }
}
```

A back-reference can only be resolved once the object exists on the receiving side. For classes
built through a `@CanonicalConstructor`, a `@CanonicalBuilder`, or for records, construction is
delayed until the component values are known, which restricts how far a component may reference
back to its own object — see
[delayed construction and back-references](reflect.md#delayed-construction-and-back-references).

#### `@TripSerializeLambda`

By default, TRIP refuses to serialize lambdas, because serializing them would drag in the entire
enclosing instance and all its referenced objects, which is usually not what the developer expects.
This annotation enables capturing and recreating lambdas:

```java
@TripSerializeLambda
public class EventHandler implements Serializable {
    // This lambda will be captured and recreated on the other side
    Supplier<String> lambda = (Supplier<String> & Serializable) () -> "some string";
}
```
Notice that both the enclosing instance and the lambda must implement `Serializable`, since
otherwise the compiler won't create serialization-capable byte code.
Notice further that non-static inner classes are rejected for the same reason.
Providing a `@CanonicalConstructor` or `@TripInstanceCreator` enables serialization again.
(The `@CanonicalConstructor` family — including `@CanonicalBuilder` for the builder pattern — is
described in [reflect.md](reflect.md#canonical-construction).)

#### `@TripInstanceCreator`

When a class has no usable public no-arg constructor, supply a
`TripInstanceCreator` and register it with `@TripInstanceCreatorService(TheClass.class)`.
It can create a bare instance, and — by overriding `isCreationFromValuesSupported`
— build the object from the already-deserialized component values:

```java
@TripInstanceCreatorService(ParseException.class)
public class ParseExceptionInstanceCreator<T extends ParseException>
       extends AbstractThrowableInstanceCreator<T> {

  @Override
  protected T create(Class<T> clazz, Values values) throws ReflectiveOperationException {
    // the values of the getters by component name → public constructor of the actual class
    return newInstance(clazz, new Class<?>[] { String.class, int.class },
                       values.get("message"), values.getInt("errorOffset", 0));
  }
}
```

`AbstractThrowableInstanceCreator` returns `true` for `isCreationFromValuesSupported`, initializes the cause
and applies the stack trace. See [JDK exceptions](#jdk-exceptions).

#### `@TripComponentProvider`

To control exactly which members are serialized, in what order, and through which
accessors, implement `TripComponentProvider` and register it with
`@TripComponentProviderService(TheClass.class)`. This is how `Throwable` is
handled: its message, cause, and stack trace are read and written through their
getters/setters rather than raw fields.

```java
@TripComponentProviderService(Throwable.class)
public class ThrowableComponentProvider<T extends Throwable>
       implements TripComponentProvider<T> {
  @Override
  public TripComponent<T, ?>[] getComponents(TripType<T> type) {
    // getMessage(), getCause(), getStackTrace()/setStackTrace(), honouring @TripIgnore
    ...
  }
}
```

#### Filtering components with TripFilter

The annotations above decide statically, per class, what is serialized. A `TripFilter`
(`org.tentackle.trip.TripFilter`) decides at runtime: set on the sending `TripStream`, it is
asked for each component (field or getter) of an object whether the component is written.

```java
stream.setFilter((parent, component) -> !"payload".equals(component.getName()));
try {
  type.write(object, stream);
}
finally {
  stream.setFilter(null);
}
```

- A rejected component is handled like one holding its default value: it is simply not
  written. The receiver leaves the field at the value the object was created with (`null`,
  `0`, `false`). The wire format does not change and the receiver needs no filter.
- The filter applies to the components of regular objects, including the extra fields of
  collection or map subclasses. It is not consulted for record components, collection elements,
  or map entries.
- It gets the parent object and the `TripComponent`, but not the value, so a getter-backed
  component is not invoked twice. If needed, `component.getValue(parent)` provides it.
- `TripStream.clear()` does not reset the filter. Since a connection's stream is shared across
  messages, remove the filter again in a `finally` block.

The typical use is sending a PDO without its related objects. `PdoComponentTripFilter`
(`org.tentackle.persist.trip`, in `tentackle-persistence`) skips all relations of a PDO —
composite, non-composite and the parent relation of a component — together with their
`...Loaded` and `...BluntLoaded` flags:

```java
stream.setFilter(PdoComponentTripFilter.getInstance());
```

The receiver gets the PDO with its relations not loaded. Accessing one loads it lazily, just as
for a PDO freshly selected from the database. The only exception is an
[embedded entity](../../../../src/main/docs/embedded-vs-datatype.md#embedded-entities), whose
getter is annotated with `@Persistent(embedded = true)`: it has no table of its own, since its
columns are part of the owner's row, so it could not be loaded later and is always sent along.

#### Custom TripType

For types that need a bespoke wire format (e.g., binary packing, protocol-specific encoding,
should not be covered by the dictionary mechanism), create a custom `TripType`:

```java
@TripTypeService(Point2D.class)
public class Point2DTripType extends AbstractTripType<Point2D> {

  public Point2DTripType() {
    super(Point2D.class);
  }

  @Override
  public void write(Point2D value, TripStream stream) {
    if (value == null) {
      sendNull(stream);
    }
    else {
      Serializer serializer = stream.getSerializer();
      serializer.serializeOpCode(MyOpCode.POINT2D);
      serializer.serializeInt(value.getX());
      serializer.serializeInt(value.getY());
    }
  }

  @Override
  public Point2D read(TripStream stream, OpCode opCode) {
    if (opCode == MyOpCode.POINT2D) {
      Deserializer deserializer = stream.getDeserializer();
      return new Point2D(deserializer.deserializeInt(),
                         deserializer.deserializeInt());
    }
    return super.read(stream, opCode);   // handles NULL, throws for any other opcode
  }
}
```

The `@TripTypeService(Point2D.class)` annotation registers this type for the `Point2D` class.
TRIP will discover and use it automatically.
Notice that this needs an application-specific opcode!

#### `@TripViaJOS`

Forces TRIP to fall back to Java Object Serialization for the annotated class. 
The class must implement `Serializable`.

```java
@TripViaJOS
public class ThirdPartyValue implements Serializable { ... }
```

**This is a last resort.** Before reaching for `@TripViaJOS`, consider the following alternatives in decreasing order of preference:

1. **add a @CanonicalConstructor or no-arg constructor**: adding one is usually trivial and makes TRIP work without any annotation.
2. **`TripInstanceCreator`**: implement and register a `@TripInstanceCreatorService` to instantiate objects that have no suitable constructor
3. **`TripComponentProvider`**: implement and register a `@TripComponentProviderService` to explicitly describe which components to serialize without relying on field reflection (useful for third-party classes).
4. **Custom `TripType`**: implement `TripType<T>` directly and register it via a `@TripTypeBuilderService` for full control over the wire format.
5. **Replace `TripFactory` or `ObjectTypeBuilder`**: for global policy changes that affect many types at once.


---

## Hardening Deserialization

### The threat model

Deserializing an object graph means reconstructing arbitrary objects from bytes. Unlike Java
Object Serialization, TRIP **always creates objects through a real constructor**: the
[canonical constructor](reflect.md#canonical-construction) if the class declares one, the no-arg
constructor otherwise (with [`TripInstanceCreator`](#tripinstancecreator) as the escape hatch for
classes that have neither). There is no `Unsafe`-style allocation of uninitialized instances.

The remaining exposure is subtler: for a class deserialized via its **no-arg constructor**, the
component values are applied *afterwards*, through reflective field access or setters. The
argument-validating constructors — where such a class typically enforces its invariants — never
see those values. As long as the bytes come from a trusted peer over a secured channel (your own
client or server, connected via the `trips`/`tripcs` SSL transports, the `tripe`/`tripce` PSK-encrypted transports, or
[QUIC](../../../../tentackle-quic/src/main/docs/quic.md)), that is fine: the sender validated the
objects before serializing them.

The picture changes when the bytes do **not** come from a trusted, live peer — for example when
serialized objects are stored as BLOBs in a database and read back later. Such data may have been
modified outside the application, and deserializing it would materialize objects whose values were
never validated. Two distinct risks follow:

- **Broken invariants** — an object whose fields violate the class's consistency rules enters the
  application as if it were valid.
- **Gadget classes** — an attacker substitutes an unexpected class whose deserialization or
  subsequent use has side effects.

### Per-class defenses

The defense against broken invariants is routing every component value through validating code:

- **Records** are created through their canonical constructor — compact constructor checks run on
  every deserialization. A record with validating constructors cannot be materialized in an
  inconsistent state.
- **Classes with a [`@CanonicalConstructor`](../java/org/tentackle/reflect/CanonicalConstructor.java)**
  (see [canonical construction](reflect.md#canonical-construction)) get the same guarantee: the
  component values arrive as constructor arguments instead of being injected into fields.
- **[`@TripComponentProvider`](#tripcomponentprovider)** puts the application in charge of *how*
  each component is read and applied — useful when validation has to happen in setters.
- **[`@TripInstanceCreator`](#tripinstancecreator)** puts the application in charge of the
  instantiation itself.
- **`@TripSerializable(validate = "...")`** names a method that TRIP invokes after reading all
  components — a hook to verify invariants that span multiple fields.

These defenses address the *broken invariants* risk for classes you control. They cannot address
the *gadget class* risk, because an attacker chooses the class — which is where the allowlist
comes in.

### Final fields (JEP-500)

TRIP never writes a final field. A final field can only be written reflectively, and
[JEP-500](https://openjdk.org/jeps/500) deprecates that: Java 26 and later warn about it, and a
future release will deny it. So when a class is created through its no-arg constructor (or a
`TripInstanceCreator` that does not create from values), deserializing one of its final fields fails
with a `TripRuntimeException` that names the field. The same applies to the final fields of a
`@CanonicalConstructor` class that are not passed to the constructor, and to the final fields of an
exception that has no canonical constructor.

The remedies, in order of preference:

- pass the value to a [`@CanonicalConstructor`](reflect.md#canonical-construction) or `@CanonicalBuilder`,
  or turn the class into a record;
- make the field `transient` (or annotate it with `@TripIgnore`) if it is derived or local state;
- create the instance from the values with a [`@TripInstanceCreator`](#tripinstancecreator) whose
  `isCreationFromValuesSupported` returns `true`;
- drop the `final` modifier if neither is possible, for example, when components back-reference the
  object (see [delayed construction](reflect.md#delayed-construction-and-back-references)).

As a migration aid, `-Dtentackle.trip.mutateFinalFields=true` (or
`FieldTripComponent.setFinalFieldMutationEnabled(true)`) restores the reflective write. The JVM
then also needs `--enable-final-field-mutation=org.tentackle.core` to suppress the JEP-500 warnings.

### Allowlisting: `DisallowedTripType`

The gadget risk is eliminated globally by restricting **which classes may be deserialized at
all**. The hook is the fallback type builder: whenever no predefined `TripType` exists for a
class, the [`TripTypeBuilderFactory`](../java/org/tentackle/trip/TripTypeBuilderFactory.java)
asks its default builder — [`ObjectTypeBuilder`](../java/org/tentackle/trip/builder/ObjectTypeBuilder.java) —
to generate one. An application that must process untrusted data replaces this builder with one
that returns a [`DisallowedTripType`](../java/org/tentackle/trip/types/DisallowedTripType.java)
for every class outside its allowlist. Any attempt to deserialize such a class then fails fast
with a `TripRuntimeException` instead of materializing the object:

```java
public class AllowlistingTypeBuilder<T> extends ObjectTypeBuilder<T> {

  @Override
  public TripType<T> build(Class<T> clazz) {
    if (isAllowed(clazz)) {           // e.g. own packages, records, an explicit set
      return super.build(clazz);
    }
    return new DisallowedTripType<>(clazz);
  }
}
```

The builder is wired in by replacing the factory
([services are overridable](../../../../tentackle-common/src/main/docs/services.md)):

```java
@Service(TripTypeBuilderFactory.class)
public class MyTripTypeBuilderFactory extends DefaultTripTypeBuilderFactory {

  @Override
  protected TripTypeBuilder<?> createDefaultBuilder() {
    return new AllowlistingTypeBuilder<>();
  }
}
```

Note the scope of the hook: predefined types — the built-in `TripType`s for JDK classes and
types registered via `@TripTypeService` or `@TripTypeBuilderService` — take precedence over the
fallback builder and are unaffected. The allowlist governs exactly the dangerous path: arbitrary
object classes the application never intended to receive.

### Practical guidance

- Application-to-application TRIP traffic over the encrypting transports needs no allowlist —
  both ends are the same trusted code base.
- Serialized objects that **leave the live session** (BLOB columns, files, message queues) should
  be treated as untrusted on the way back in: prefer records for the payload classes, and install
  an allowlisting builder in the tier that reads them.
- `@TripViaJOS` routes a class through Java Object Serialization and thereby out of TRIP's
  control; avoid it entirely for data that may be stored or received from outside.

---

## Writing a Custom Transport

TRIP's transport layer is fully pluggable. 
You can add support for any protocol — HTTP/2, HTTP/3, gRPC, WebSocket, or a proprietary protocol — 
by implementing the `Transport` interface or extending one of the provided base classes.

### The Transport contract

The `Transport` interface defines the core contract:

| Method                                                           | Purpose                                                        |
|------------------------------------------------------------------|----------------------------------------------------------------|
| `getUri()` / `getTransportKey()`                                 | Identity — how this transport is uniquely identified           |
| `getId()`                                                        | A `UUID` assigned to this transport instance                   |
| `getConnection()` / `putConnection(Connection)`                  | Client-side connection pool access                             |
| `accept()`                                                       | Server-side: start listening for incoming connections          |
| `isAccepting()` / `isClosed()` / `close()`                       | Lifecycle state                                                |
| `getClientDictionary()`                                          | The client's dictionary                                        |
| `getServerDictionary(UUID, boolean)`                             | Per-client dictionaries                                        |
| `getRegistry()` / `getRemoteLookup()`                            | Access to the server's registry and client's lookup proxy      |
| `lookup(Class, String)`                                          | Look up a named remote object                                  |
| `createConnectionHandler(String, InputStream, OutputStream)`     | Create a handler for an incoming connection                    |
| `createCallHandler(TripStream, TransportClientInfo, RemoteCall)` | Create a handler for an incoming call                          |
| `getConnectionThreadPool()`                                      | Server-side virtual thread pool to handle incoming connections |
| `getCallThreadPool()`                                            | Server-side thread pool to run the calls                       |

### Base classes

Three base classes handle different levels of boilerplate:

| Class                                                         | What it provides                                                     | Best for                                         |
|---------------------------------------------------------------|----------------------------------------------------------------------|--------------------------------------------------|
| `AbstractTransport`                                           | UUID generation, thread pools, dictionary management, lease tracking | New protocol from scratch (e.g., QUIC)           |
| `TcpTransport`                                                | Socket-based client connections and server listening                 | Adding a layer on top of TCP (SSL, compression)  |
| `SslTransport` / `EncryptedTransport` / `CompressedTransport` | Subclasses of `TcpTransport`                                         | Adding SSL, PSK encryption or compression to TCP |

The only abstract method in `AbstractTransport` is:

```java
protected abstract Pool<Connection> createClientConnectionPool();
```

### Transport discovery

Transports are discovered via the `@TransportService` annotation. During the build, the `MappedServiceAnalyzeHandler` (run by the `tentackle-maven-plugin`'s analyze step, see [services](../../../../tentackle-common/src/main/docs/services.md)) writes an entry to `META-INF/mapped-services/org.tentackle.trip.Transport`:

```
org.tentackle.quic.QuicTripTransport = "tripq"
```

At runtime, `DefaultTripFactory` reads these entries and builds a map from protocol scheme to transport class. When you create a transport via `TransportManager.requestTransport(URI)`, the factory looks up `uri.getScheme()` in this map and instantiates the corresponding class via its `getDeclaredConstructor(URI.class).newInstance(uri)` constructor.

### Step-by-step: creating a custom transport

Here is the checklist for adding a new transport:

#### 1. Create the Transport subclass

```java
@TransportService("myproto")
public class MyTripTransport extends AbstractTransport {
    
    public static final String PROTOCOL = "myproto";
    private boolean accepting;
    
    public MyTripTransport(URI uri) {
        super(uri);
    }
    
    @Override
    public synchronized void accept() {
        super.accept();
        // Start your server (open socket, start QUIC connector, etc.)
        accepting = true;
    }
    
    @Override
    public boolean isAccepting() {
        return accepting;
    }
    
    @Override
    protected Pool<Connection> createClientConnectionPool() {
        return new MyTripConnectionPool(this, getClientDictionary());
    }
}
```

#### 2. Create the ConnectionPool

```java
public class MyTripConnectionPool extends AbstractConnectionPool {
    
    public MyTripConnectionPool(Transport transport, Dictionary dictionary) {
        super(transport, dictionary);
    }
    
    @Override
    protected PoolSlot<Connection> createSlot(int slotNumber) {
        // Open a connection to the remote endpoint
        return new ConnectionPoolSlot(this, slotNumber,
                                      new MyTripConnection(getTransport(), getDictionary()));
    }
}
```

The pool parameters (`initial`, `increment`, `minimum`, `maximum`, `idle`, `usage`) are read from the URI query string and control dynamic sizing.

`createSlot()` runs with the pool's monitor held, so opening a connection blocks the other threads using the transport. If it does not depend on that monitor, override `isCreatingSlotsConcurrently()` to return true, as `TcpConnectionPool` does (see [Pooling](misc.md#pooling)).

#### 3. Create the Connection

```java
public class MyTripConnection extends AbstractConnection {
    
    public MyTripConnection(Transport transport, Dictionary dictionary) {
        super(transport, createTripStream(dictionary));   // also writes the connection preamble
    }
    
    @Override
    public boolean isOpen() {
        return /* your connection is open */;
    }
    
    @Override
    public void close() {
        // Close your connection
    }
    
    private static TripStream createTripStream(Dictionary dictionary) {
        TripFactory factory = TripFactory.getInstance();
        return factory.createStream(dictionary,
            factory.createSerializer(/* your output stream */),
            factory.createDeserializer(/* your input stream */));
    }
}
```

#### 4. Handle incoming connections (server side)

Override `accept()` to start your server listener. When a connection arrives, create the handler:

```java
Runnable handler = transport.createConnectionHandler(
    "my-client-connection",
    inputStream,    // from your protocol's connection
    outputStream    // from your protocol's connection
);
transport.getConnectionThreadPool().execute(handler);
```

For each call within a connection, the connection handler delegates to `createCallHandler()`, which runs on a thread from `getCallThreadPool()`.

### QUIC transport example

The [`tentackle-quic`](../../../../tentackle-quic/src/main/docs/quic.md) module (`/tentackle-quic/src/main/java/org/tentackle/quic/`) provides a complete reference implementation using the Kwik library. Here is how it maps to the steps above:

#### Transport class

```java
@TransportService("tripq")
public class QuicTripTransport extends AbstractTransport {
    
    public static final String PROTOCOL = "tripq";
    private boolean accepting;
    
    public QuicTripTransport(URI uri) {
        super(uri);
    }
    
    @Override
    public synchronized void accept() {
        super.accept();  // sets up thread pools, registry
        
        // Configure and start the Kwik QUIC server connector
        ServerConnector serverConnector = ServerConnector.builder()
            .withConfiguration(...)
            .withKeyStore(keyStore, alias, password)
            .withLogger(log)
            .withPort(port)
            .build();
        
        // Register an application protocol handler
        serverConnector.registerApplicationProtocol(alpnId,
            new QuicProtocolConnectionFactory(this));
        serverConnector.start();
        accepting = true;
    }
    
    @Override
    protected Pool<Connection> createClientConnectionPool() {
        return new QuicTripConnectionPool(this, getClientDictionary());
    }
}
```

Key details:

- **Configuration**: like the SSL transports, the QUIC transport takes its keystore and
  truststore from the [SSL parameters](#ssltls-configuration) (`ksfile`, `kspass`, `ksalias`, …).
  QUIC-specific options such as `alpnid`, `maxstreams`, `connecttimeout`, `quicidle` and
  `keepalive` are listed in [QUIC](../../../../tentackle-quic/src/main/docs/quic.md).

- **`createStream()`** method wraps a QUIC stream's I/O in a TRIP `TripStream`:
  ```java
  public TripStream createStream(Dictionary dictionary, QuicStream quicStream) {
      TripFactory factory = TripFactory.getInstance();
      return factory.createStream(dictionary,
          factory.createSerializer(quicStream.getOutputStream()),
          factory.createDeserializer(quicStream.getInputStream()));
  }
  ```

- **Client connections**: `QuicTripConnectionPool` opens a `QuicClientConnection`, and each `createSlot()` call creates a new bidirectional `QuicStream` on that connection, wrapped in a `QuicTripConnection`. Since all pooled connections share that single QUIC connection, it is kept alive by PING frames (see `keepalive` above) and re-established if it is lost.

- **Server-side streams**: `QuicProtocolConnectionFactory` accepts incoming `QuicStream`s from peer-initiated bidirectional streams and hands them to `transport.createConnectionHandler()` on a virtual thread.

---

## Related Documentation

- [PDO / Persistent Domain Objects](../../../../tentackle-pdo/src/main/docs/pdo.md) — the objects that travel over TRIP; location transparency depends on this protocol.
- [QUIC Transport](../../../../tentackle-quic/src/main/docs/quic.md) — the QUIC (RFC-9000) reference transport and how it plugs in.
- [I/O and Networking](io.md) — the packet-framed compressing/encrypting streams the transports are built from, and the reconnection machinery.
- [Session](../../../../tentackle-session/src/main/docs/session.md) — sessions are remoted as a tree of `RemoteDelegate`s over TRIP.
- [The Multi-Tier Cascade](../../../../src/main/docs/tiers.md) — how the same artifact runs at any tier, with TRIP as the transport between them.
- [Correctness First](../../../../src/main/docs/correctness-first.md) — TRIP's version checking and type dictionary as part of the correctness story.
- [Tentackle Modules](../../../../src/main/docs/modules.md) — where `tentackle-core` (TRIP) sits among the framework modules.
- [Exceptions and Error Handling](../../../../src/main/docs/exceptions.md) — how a `Throwable` is put on the wire and rethrown unchanged on the far side.
- [Location Transparency vs. REST, RMI and Spring Remoting](../../../../src/main/docs/remoting-comparison.md) — TRIP set against hand-rolled REST, Java RMI and the Spring Remoting family, point by point.
