# Cursors — Streaming Large Result Sets, Locally and Remotely

## Overview and Motivation

Some queries don't fit in memory. A nightly batch over every invoice, an export of a million rows, a
migration pass — loading these into a `List` is the obvious approach and the wrong one.

The usual answer is a database cursor, and the usual catch is that it only works where the JDBC
connection is: a cursor is a live server-side resource tied to an open `ResultSet`. In a
[multi-tier](../../../../src/main/docs/tiers.md) architecture, that would mean batch code runs
against the database tier or not at all — exactly the kind of "this code only works here" exception
Tentackle is built to avoid.

[`ResultSetCursor`](../java/org/tentackle/persist/ResultSetCursor.java) removes the catch. It is a
[`ScrollableResource<T>`](../../../../tentackle-core/src/main/java/org/tentackle/misc/ScrollableResource.java)
that streams PDOs from a query, and it works **whether the session is local or remote**. The same
class, the same API, the same code:

```java
try (ScrollableResource<SeismicEvent> cursor = on(SeismicEvent.class).findUnprocessed()) {
  while (cursor.next()) {
    cursor.get().process();
  }
}
```

Connected straight to the database, that iterates a JDBC `ResultSet`. Connected to an application
server three tiers up, the cursor lives on the server holding the `ResultSet`, and the client drives
it over [TRIP](../../../../tentackle-core/src/main/docs/trip.md). The application code above does
not change and does not know which happened — consistent with the rest of the
[PDO model](../../../../tentackle-pdo/src/main/docs/pdo.md), where any method can be invoked from any
JVM.

Cursors also carry a memory argument that goes beyond the result set. Because Tentackle has no
identity map, each PDO becomes garbage the moment the loop moves past it — there is no session cache
filling up behind you and nothing to `clear()`. See
[pdo-vs-orm.md](../../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md) for that comparison.


## Obtaining a Cursor

You rarely construct one. There are three sources, in increasing order of specificity:

**1. Built-in methods.** Every PDO inherits two cursor methods from
[`AbstractPersistentObject`](../java/org/tentackle/persist/AbstractPersistentObject.java), no code
generation required:

| Method                           | Streams                                                          |
|----------------------------------|------------------------------------------------------------------|
| `selectAllAsCursor()`            | Every entity in the current context                              |
| `selectByNormTextAsCursor(text)` | Everything matching a [normtext](persistence.md#normtext) search |

**2. Generated select methods.** Add `--cursor` to a
[`PdoSelectList`](../../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md) wurblet and
the generated query method returns a `ScrollableResource<Pdo>` instead of a `List`. The wurblet
generates both sides: locally the body ends in `new ResultSetCursor<>(me(), rs)`; remotely, the
server creates a [`RemoteResultSetCursor`](../java/org/tentackle/persist/trip/RemoteResultSetCursor.java)
delegate and the client wraps it in `new ResultSetCursor<>(getDomainContext(), remoteDelegate...)`.

**3. Custom queries.** `executeScrollableQuery(query[, js])` builds a cursor over any `Query`.

All three return the same type, and the built-in methods show the local/remote fork the generated
ones also perform:

```java
public ScrollableResource<T> selectAllAsCursor() {
  if (getSession().isRemote()) {
    DomainContext context = getDomainContext();
    return new ResultSetCursor<>(context, getRemoteDelegate().selectAllAsCursor(context));
  }
  JoinedSelect<T> js = getEagerJoinedSelect();
  return new ResultSetCursor<>(pdo, resultAllCursor(js), js);
}
```

The `--cursor` option comes with constraints, all of which follow from streaming:

| Constraint                         | Why                                                                                                                                                         |
|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Incompatible with `--resultset`    | `--resultset` hands out the raw `ResultSetWrapper`; a cursor *is* the wrapper's manager                                                                     |
| Incompatible with `--oneshot`      | A one-shot statement is closed right after the query, but a cursor keeps reading from it — this also rules out optional conditions, which imply `--oneshot` |
| Incompatible with immutability     | Immutability is applied to a completed collection, which a stream never is                                                                                  |
| Incompatible with tracking         | A [`TrackedList`](../../../../tentackle-core/src/main/docs/misc.md) needs the whole list to track additions and removals                                    |
| With joins: must be sorted by `id` | Rows of one aggregate must arrive consecutively for the cursor to assemble them (see [below](#joins-and-aggregates))                                        |


## Key Concepts

### One class, two modes

`ResultSetCursor` holds either a `ResultSetWrapper` (local) or a `RemoteResultSetCursor` (remote) —
never both. Its constructors assert which:

```java
public ResultSetCursor(T pdo, ResultSetWrapper rs, JoinedSelect<T> js) {   // local
  ...
  assertSessionIsLocal();
}

public ResultSetCursor(DomainContext context, RemoteResultSetCursor<T> remoteCursor) {   // remote
  ...
  assertSessionIsRemote();
}
```

Every navigation method then forks on `session.isRemote()`: locally it drives the JDBC result set,
remotely it forwards to the delegate. The only exception is `isBeforeFirst()`, which a remote cursor
answers from its own row counter without a round trip. The remote interface mirrors the local API almost
method for method — `next`, `previous`, `first`, `last`, `scroll`, `setRow`, `get`, `toList`, `fetch` —
which is why the two modes present one face to the caller.

PDOs arriving from a remote cursor are passed through `configureRemoteObject`, which re-attaches them
to the local [`DomainContext`](../../../../tentackle-pdo/src/main/docs/domain-context-provider.md) and
session. A PDO that crossed the wire is a fully functional PDO on arrival, never a detached husk.

### Navigation

The [`ScrollableResource`](../../../../tentackle-core/src/main/java/org/tentackle/misc/ScrollableResource.java)
contract is deliberately close to JDBC's, with **1-based rows** and row 0 meaning *before the first*:

| Method                          | Effect                                                                                               |
|---------------------------------|------------------------------------------------------------------------------------------------------|
| `next()` / `previous()`         | Move one row; false if there is none (`next()` stays put, `previous()` ends up before the first row) |
| `first()` / `last()`            | Jump to the ends; false if empty                                                                     |
| `scroll(n)`                     | Move n rows, negative backwards                                                                      |
| `setRow(n)` / `getRow()`        | Absolute positioning                                                                                 |
| `beforeFirst()` / `afterLast()` | Park outside the result set                                                                          |
| `get()`                         | Materialize the PDO at the current row                                                               |

Backward and absolute navigation depend on the underlying result set being scrollable; a
`FORWARD_ONLY` cursor supports only forward movement. Cursors with [joins](#joins-and-aggregates) are
forward-only by design: `setRow`, `scroll`, `last`, `previous` and `afterLast` throw a
`PersistenceException`, while `next`, `first` and `beforeFirst` work as usual.

The implementation carries a small scar from this: after `first()`, the fetch logic deliberately avoids
calling `beforeFirst()`, because some databases (Oracle among them) reject it on forward-only cursors.

### Fetching in blocks — `fetch()`

`next()`/`get()` is the natural loop, and over a remote session it costs **two round trips per row**.
For large result sets that is the dominant cost, so cursors offer a block API:

```java
try (ResultSetCursor<Customer> cursor = ...) {
  for (FetchList<Customer> block = cursor.fetch(); block != null; block = cursor.fetch()) {
    for (Customer c : block) {
      c.process();
    }
    if (block.isClosed()) {
      break;
    }
  }
}
```

`fetch()` returns a [`FetchList<T>`](../java/org/tentackle/persist/ResultSetCursor.java) — an
`ArrayList` with one extra bit: **`isClosed()`**, meaning *this was the last block*. Its javadoc
explains the point plainly: the flag "adds a closed flag to save a round trip in remote sessions."
Without it the client would have to ask whether more rows exist; with it, the server says so while
delivering the final block. Notice that the built-in and generated methods return a
`ScrollableResource<T>`, whose `fetch()` is declared to return a plain `List<T>`: either cast the cursor to
`ResultSetCursor<T>` as above, or ask the cursor itself via `cursor.isClosed()`.

Block size is `setFetchSize(rows)` — forwarded to the remote cursor or to the JDBC result set as
appropriate. If the fetch size is unset (`< 1`), `fetch()` falls back to reading everything from the
first row on, marking the list closed and closing the cursor. Notice that for a local session the fetch size is only a
*hint* to the JDBC driver, which the driver may discard: H2 does, and `getFetchSize()` then keeps
reporting 0, so every `fetch()` against it takes the fallback. Blocking therefore pays off where it
was meant to — remote cursors and drivers that honor the hint, PostgreSQL among them.

`toList()` and `toListAndClose()` are the "actually, it does fit in memory" shortcuts, and are still
worth using remotely: `toList()` collects on the server side and returns in one round trip rather than a
`get()`-per-row conversation (`toListAndClose()` adds a second one to close the cursor). Both rewind the
cursor to the first row before collecting.

### Joins and aggregates

A cursor can carry a [`JoinedSelect`](../java/org/tentackle/persist/JoinedSelect.java),
so [eager relations](../../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md)
stream too. This is where the `ORDER BY id` requirement comes from: an outer join returns *several
rows per root entity*, and `get()` assembles them by reading ahead until the id changes. If rows of
one aggregate are not consecutive, the cursor cannot know an entity is complete. Sorting by `id`
guarantees they are. Because `get()` reads ahead, a joined cursor can only move forward (see
[Navigation](#navigation)).

### Resource management

A cursor is a live resource — a JDBC `ResultSet` locally, a server-side cursor remotely — and must be
closed. It is `AutoCloseable`; use try-with-resources.

If you forget, the cursor still cleans up, but noisily. Rather than a finalizer, `ResultSetCursor`
registers its state with a `Cleaner`, so an unreachable cursor is closed on GC and logs a warning:

```
closing unreferenced local ResultSetCursor <rs>
closing unreferenced remote ResultSetCursor <cursor>
```

Seeing those in a log means application code leaked a cursor: the resource was reclaimed, but only
whenever GC noticed — and in the remote case a server-side cursor and its database connection stayed
alive until then. Treat the warning as a bug to fix, not as reassurance. A programmatic `close()`
takes the same path but marks the close as intentional, so no warning is logged.


## How It Fits Together

A remote batch, end to end:

1. Application calls the built-in `selectAllAsCursor()` on a PDO whose session is remote.
2. The client's remote delegate invokes the method on the server, which runs the query, builds a
   local `ResultSetCursor` over the JDBC `ResultSet`, and returns a `RemoteResultSetCursor` handle.
3. The client wraps that handle in its own `ResultSetCursor` — the object the application holds.
4. `fetch()` pulls a block per round trip; each PDO is reattached to the client's domain context on
   arrival. The final block carries `isClosed()`, so no extra call is needed to discover the end.
5. `close()` (via try-with-resources) releases the server-side cursor and its `ResultSet`.

Swap the session for a local one and steps 2–3 collapse into a direct `ResultSetWrapper`; steps 1, 4
and 5 read identically in the source. That is the whole point.


## Related Documentation

- [Tentackle Persistence](persistence.md) — the layer cursors belong to.
- [PdoSelectList and PdoSelectUnique](../../../../tentackle-persistence-wurblets/src/main/docs/pdo-select.md) — the `--cursor` option that generates cursor methods.
- [Eager Relations](../../../../tentackle-persistence-wurblets/src/main/docs/eager-relations.md) — joined selects, which cursors can stream.
- [PDOs vs. Traditional ORMs](../../../../tentackle-pdo/src/main/docs/pdo-vs-orm.md) — why cursor loops need no `clear()`.
- [The Multi-Tier Cascade](../../../../src/main/docs/tiers.md) — the remoting model cursors stay transparent across.
- [TRIP](../../../../tentackle-core/src/main/docs/trip.md) — the protocol remote cursors talk over.
- [Miscellaneous](../../../../tentackle-core/src/main/docs/misc.md) — the `ScrollableResource` contract.
