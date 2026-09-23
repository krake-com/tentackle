# Global Transactions — JTA and XA

## Overview and Motivation

A Tentackle [transaction](../../../../tentackle-pdo/src/main/docs/transaction.md) is a *local*
transaction: one session, one JDBC connection, one database. That covers almost every application.
But sometimes one unit of work has to commit atomically across more than one transactional resource:

- two databases, for example an operational database and an archive, or two applications sharing
  a business process;
- a database and a message broker (JMS) or another XA-capable resource;
- a Tentackle tier deployed in a Jakarta EE container, where the container demarcates the transactions
  and expects every resource to take part in them.

This is the domain of *global* (distributed) transactions: a transaction manager coordinates all
resources via the XA two-phase commit protocol, and the Java API for it is JTA
(`jakarta.transaction`). The module `tentackle-jta` connects local sessions to a JTA transaction
manager. The programming model does not change: `@Transaction`, `Session.begin()`/`commit()`,
pre- and post-commits, and transparent retry work as before.

### When not to use XA

A two-phase commit costs an additional round trip per resource (the *prepare*) and a log write
in the transaction manager for every commit. If a participant or the coordinator fails between
prepare and commit, the *in-doubt* branches keep their locks until the transaction manager's
recovery resolves them. Not every JDBC driver handles all of this equally well.

For the most common case, a database update plus a message, the *transactional outbox* is usually
the better choice: write the message into a table within the local transaction and deliver it
afterwards. Tentackle's [modification log](../../../../tentackle-session/src/main/docs/modification-tracking.md)
already is such an outbox, written atomically with the business data.

Use XA when you need real atomicity across several databases, or when the container requires it.

## Setup

1. Add the module. It brings the JTA API (`jakarta.transaction-api`), but no transaction manager:

   ```xml
   <dependency>
     <groupId>org.tentackle</groupId>
     <artifactId>tentackle-jta</artifactId>
   </dependency>
   ```

   On the module path, the module `jakarta.transaction` also requires the CDI and interceptor APIs
   (`jakarta.enterprise.cdi-api` and `jakarta.interceptor-api`) for its `@Transactional` annotation,
   even though Tentackle does not use it. A container provides them. On the classpath they are not
   needed.

2. Use XA connections. The sessions must be opened with one of the XA connection managers, the
   counterparts of the [database module's managers](../../../../tentackle-database/src/main/docs/database.md):

   - [`XaConnectionManager`](../java/org/tentackle/jta/XaConnectionManager.java) — one connection
     per session, like the `DefaultConnectionManager`;
   - [`XaMpxConnectionManager`](../java/org/tentackle/jta/XaMpxConnectionManager.java) — N sessions
     multiplexed over M connections, like the `MpxConnectionManager`, for application servers.

   Pass the manager to the `Db` or session pool, or return it from the application's
   `ConnectionManagerProvider.getConnectionManager()`. The connections are created via the JDBC
   driver's `XADataSource` (see [Backends](#backends)). For a `jndi:` URL, the bound data source must
   implement `XADataSource`.

   A session taking part in a global transaction keeps its connection until the global transaction
   completes. Afterwards, the connection is switched back to autocommit mode and a multiplexing
   manager hands it to the next session.

3. Provide the transaction manager to
   [`JtaTransactionSupport`](../java/org/tentackle/jta/JtaTransactionSupport.java). A standalone
   application sets it explicitly, for example with Narayana:

   ```java
   JtaTransactionSupport.setTransactionManager(com.arjuna.ats.jta.TransactionManager.transactionManager());
   ```

   Otherwise, it is looked up via JNDI. The name is taken from the system property
   `tentackle.jta.transactionManager`, or else the common container names are tried
   (`java:comp/TransactionManager`, `java:appserver/TransactionManager`, `java:jboss/TransactionManager`,
   `java:/TransactionManager`).

4. Choose the mode per session in the [backend properties](../../../../tentackle-session/src/main/docs/backend-properties.md):

   ```properties
   url=jdbc:postgresql://localhost/erp
   jta=join
   ```

## The Two Modes

The property `jta` selects the
[`GlobalTransactionMode`](../../../../tentackle-database/src/main/java/org/tentackle/dbms/GlobalTransactionMode.java)
(it can also be set programmatically via `Db.setGlobalTransactionMode()`):

| Value            | A global transaction is running on the thread | No global transaction is running       |
|------------------|-----------------------------------------------|----------------------------------------|
| `none` (default) | ignored: local transaction                    | local transaction                      |
| `join`           | the session joins it                          | local transaction                      |
| `required`       | the session joins it                          | the session **starts** one             |

### `join` — Tentackle as a participant

Use `join` when something else demarcates the global transactions: a Jakarta EE container, Spring's
JTA transaction manager, or the application code itself via `TransactionManager.begin()`/`commit()`.

When the session begins a transaction while a global transaction is associated with the current
thread, it enlists its connection in the global transaction instead of starting a local one. Level 1
of the session's transaction belongs to the owner of the global transaction. The session itself
continues at level 2, just like a nested transaction. So `begin()` returns a voucher of `0`,
and no `@Transaction` method ever commits, rolls back or retries the global transaction. Only its
owner does. The session stays enlisted until the global transaction completes, and further units
of work within the same global transaction simply join it again.

If a unit of work of the session fails, i.e. its outermost joined level is rolled back, the global
transaction is marked *rollback-only*. Its owner can no longer commit it.

### `required` — Tentackle as the coordinator

Use `required` in standalone applications that need atomic transactions across several sessions
(databases) or other XA resources, without an application server.

The outermost `begin()` of a session with no global transaction on the thread starts a new global
transaction and gets a real voucher. Every other session that begins a transaction on the same
thread joins it (see above). The owner's `commit(voucher)` runs the two-phase commit across all
enlisted resources, and `rollback(voucher)` rolls all of them back:

```java
long voucher = erpSession.begin("transfer");    // starts the global transaction
try {
  ...                                           // work in erpSession
  archiveSession.transaction(() -> ...);        // joins it
  erpSession.commit(voucher);                   // two-phase commit of both databases
}
catch (RuntimeException ex) {
  erpSession.rollback(voucher);                 // rolls back both databases
  throw ex;
}
```

With PDOs the same is achieved declaratively: a `@Transaction` method of a PDO in the first session
starts the global transaction, and the work of every other session configured for global
transactions that is done within this method joins it.

## How Tentackle's Features Behave

| Feature                                    | In a global transaction                                                                                                                                                                                                                                                                                                                                                                                    |
|--------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Pre-commits, statement batch               | Run and flushed when the transaction manager calls `beforeCompletion`, i.e. before the *prepare*. An exception rolls back the global transaction and is rethrown by the owner's `commit()`. This also holds for an optimistic-lock conflict of a batched update.                                                                                                                               |
| Post-commits, modification tracking        | Run after the global transaction is committed (`afterCompletion`), and not at all after a rollback. If a post-commit fails, the owner's `commit()` throws the exception, as it would in a local transaction.                                                                                                                                                                                                                                                  |
| [Transparent retry](../../../../tentackle-pdo/src/main/docs/transaction.md#transparent-retry) | Works for the owner: the global transaction is the unit that is retried. A failed commit is *temporary* if a pre-commit threw a temporary exception or a resource failed with `XA_RBDEADLOCK`, `XA_RBTIMEOUT` or `XA_RBTRANSIENT`. A joined session never retries, just like a nested transaction.                                                                                                                                   |
| [Optimistic locking](../../../../tentackle-persistence/src/main/docs/locking.md), token locks | Unchanged: plain SQL within the transaction branch.                                                                                                                                                                                                                                                                                                                                                                                                  |
| Isolation and writability                  | Cannot be changed: a non-default `isolation` or `writability` of `@Transaction` or `begin()` throws a `PersistenceException`. Configure them on the data source instead.                                                                                                                                                                                                                                              |
| Savepoints                                 | Depend on the JDBC driver: some drivers support savepoints within an XA branch, others don't.                                                                                                                                                                                                                                                                                                                                                                                                  |
| `rollbackImmediately()`                    | Rolls back the global transaction if the session owns it, otherwise marks it rollback-only. The connection is never rolled back directly, because it is enlisted.                                                                                                                                                                                                                                                                                                            |
| [Remote sessions](../../../../src/main/docs/tiers.md) | A JTA transaction never crosses a tier boundary. A `@Transaction` method executed on the server (a `@RemoteMethod`) takes part in global transactions on the server. A transaction begun by a remote client spans several remote calls and always stays local.                                                                                                                                                                                                                                        |

The transaction manager may call `afterCompletion` from another thread, for example when it rolls
back a timed-out transaction. After a rollback, Tentackle only resets the session's state and runs
no statements.

## Backends

A backend supports XA if it names the JDBC driver's `XADataSource`
(`Backend.getXADataSourceClassName()`). The data source is created via reflection and configured
with the url and credentials from the backend properties.

| Backend             | XA data source                                                | Notes                                                                              |
|---------------------|---------------------------------------------------------------|------------------------------------------------------------------------------------|
| PostgreSQL          | `org.postgresql.xa.PGXADataSource`                            | Requires `max_prepared_transactions > 0` in the server configuration.              |
| Oracle              | `oracle.jdbc.xa.client.OracleXADataSource`                    |                                                                                    |
| MySQL               | `com.mysql.cj.jdbc.MysqlXADataSource`                         | InnoDB only.                                                                       |
| MariaDB             | `org.mariadb.jdbc.MariaDbDataSource`                          |                                                                                    |
| Microsoft SQL Server | `com.microsoft.sqlserver.jdbc.SQLServerXADataSource`         | Requires the XA stored procedures to be installed and MSDTC to be enabled.        |
| H2                  | `org.h2.jdbcx.JdbcDataSource`                                 | Used by the module's tests.                                                        |
| CockroachDB         | —                                                             | No two-phase commit.                                                               |
| others              | —                                                             | Override `getXADataSourceClassName()`, or bind an `XADataSource` via JNDI.       |

## Recovery

If the application or a database crashes between prepare and commit, the transaction manager
completes the in-doubt branches when it recovers. For that, it needs the transaction log (a durable
directory, configured in the transaction manager) and a way to connect to each database. With
Narayana, register an `XAResourceRecoveryHelper` that opens an XA connection per backend:

```java
XAConnection recoveryConnection = backendInfo.connectXA();
XAResource[] resources = { recoveryConnection.getXAResource() };
```

`tentackle-jta` does not register such helpers itself, since the recovery API is specific to the
transaction manager.

## Related Documentation

- [Transactions and Transparent Retry](../../../../tentackle-pdo/src/main/docs/transaction.md) — `@Transaction`, nesting and retry.
- [Tentackle Session](../../../../tentackle-session/src/main/docs/session.md) — the transaction API of a session.
- [Tentackle Database](../../../../tentackle-database/src/main/docs/database.md) — connection managers and local transactions.
- [Backend Properties](../../../../tentackle-session/src/main/docs/backend-properties.md) — the `jta` key.
- [Modification Tracking](../../../../tentackle-session/src/main/docs/modification-tracking.md) — the modification log as a transactional outbox.
