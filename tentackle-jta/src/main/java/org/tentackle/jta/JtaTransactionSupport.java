/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.tentackle.jta;

import jakarta.transaction.HeuristicMixedException;
import jakarta.transaction.HeuristicRollbackException;
import jakarta.transaction.NotSupportedException;
import jakarta.transaction.RollbackException;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.SystemException;
import jakarta.transaction.Transaction;
import jakarta.transaction.TransactionManager;

import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.Service;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.dbms.Db;
import org.tentackle.dbms.GlobalTransactionListener;
import org.tentackle.dbms.GlobalTransactionSupport;
import org.tentackle.dbms.ManagedConnection;
import org.tentackle.log.Logger;
import org.tentackle.session.PersistenceException;

import java.util.List;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.xa.XAException;

/**
 * Global transactions via JTA.
 * <p>
 * The {@link TransactionManager} is either set by the application via {@link #setTransactionManager(TransactionManager)}
 * (standalone applications, e.g. with an embedded Narayana or Atomikos),
 * or looked up via JNDI (application servers). The JNDI names are tried as follows:
 * <ol>
 *   <li>the name given by the system property {@value #JNDI_NAME_PROPERTY}, if set (and only that name)</li>
 *   <li>otherwise, the {@link #DEFAULT_JNDI_NAMES} in that order</li>
 * </ol>
 * A transaction manager found in JNDI is kept until {@link #setTransactionManager(TransactionManager)} is invoked.
 * If none is found, the lookup is repeated on the next request.
 * <p>
 * The sessions must use {@link XaManagedConnection}s, provided by an {@link XaConnectionManager}
 * or an {@link XaMpxConnectionManager}.
 * <p>
 * Subclasses may refine which failures are retryable by overriding {@link #isTemporary(Throwable)}.
 * Such a subclass must be annotated with {@code @Service(GlobalTransactionSupport.class)} to replace this one.
 *
 * @author harald
 */
@Service(GlobalTransactionSupport.class)
public class JtaTransactionSupport implements GlobalTransactionSupport {

  private static final Logger LOGGER = Logger.get(JtaTransactionSupport.class);

  /**
   * System property holding the JNDI name of the transaction manager.
   */
  public static final String JNDI_NAME_PROPERTY = "tentackle.jta.transactionManager";

  /**
   * The JNDI names under which the transaction manager is looked up by default.
   */
  public static final List<String> DEFAULT_JNDI_NAMES = List.of(
      "java:comp/TransactionManager",       // Jakarta EE (some containers)
      "java:appserver/TransactionManager",  // GlassFish, Payara
      "java:jboss/TransactionManager",      // WildFly
      "java:/TransactionManager");          // JBoss, others

  private static volatile TransactionManager transactionManager;

  /**
   * Holds the first exception thrown by a listener while the current thread completes a global transaction
   * begun via {@link #begin(Db, String)}.<br>
   * Installed by {@code begin}, taken by {@link #commit(Db)}, which rethrows the exception, so that the original
   * cause becomes visible (and retryable, if temporary), and removed by {@link #rollback(Db)}.
   * No holder is installed for global transactions owned by someone else, such as a container,
   * since nobody would take the exception.
   */
  private static final ThreadLocal<CompletionFailure> COMPLETION_FAILURE = new ThreadLocal<>();


  /**
   * Sets the transaction manager.
   *
   * @param transactionManager the transaction manager, null to look it up via JNDI on the next request
   */
  public static void setTransactionManager(TransactionManager transactionManager) {
    JtaTransactionSupport.transactionManager = transactionManager;
  }

  /**
   * Gets the transaction manager.<br>
   * If not set, it is looked up via JNDI.
   *
   * @return the transaction manager, never null
   * @throws PersistenceException if no transaction manager is available
   */
  public static TransactionManager getTransactionManager() {
    TransactionManager tm = transactionManager;
    if (tm == null) {
      tm = lookupTransactionManager();
      transactionManager = tm;
    }
    return tm;
  }

  /**
   * Gets the completion failure pending for the current thread.<br>
   * For tests only.
   *
   * @return the failure, null if none
   */
  static RuntimeException getCompletionFailure() {
    CompletionFailure holder = COMPLETION_FAILURE.get();
    return holder == null ? null : holder.failure;
  }

  private static TransactionManager lookupTransactionManager() {
    String configuredName = System.getProperty(JNDI_NAME_PROPERTY);
    List<String> names = configuredName != null ? List.of(configuredName) : DEFAULT_JNDI_NAMES;
    try {
      InitialContext context = new InitialContext();
      for (String name : names) {
        try {
          if (context.lookup(name) instanceof TransactionManager tm) {
            LOGGER.info("using JTA transaction manager {0} bound to {1}", tm, name);
            return tm;
          }
        }
        catch (NamingException nx) {
          LOGGER.fine("no transaction manager bound to {0}", name);
        }
      }
    }
    catch (NamingException nx) {
      throw new PersistenceException("no JNDI context to look up the JTA transaction manager", nx);
    }
    throw new PersistenceException("no JTA transaction manager found in JNDI " + names +
                                   " and none set via " + JtaTransactionSupport.class.getSimpleName() +
                                   ".setTransactionManager()");
  }


  /**
   * Creates the JTA transaction support.
   */
  public JtaTransactionSupport() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }


  @Override
  public boolean isGlobalTransactionActive() {
    try {
      return getTransactionManager().getStatus() != Status.STATUS_NO_TRANSACTION;
    }
    catch (SystemException sx) {
      throw new PersistenceException("cannot determine the status of the global transaction", sx);
    }
  }

  @Override
  public void begin(Db session, String txName) {
    COMPLETION_FAILURE.remove();
    try {
      getTransactionManager().begin();
    }
    catch (NotSupportedException | SystemException ex) {
      throw new PersistenceException(session, "cannot begin global transaction " + txName, ex);
    }
    COMPLETION_FAILURE.set(new CompletionFailure());
  }

  @Override
  public void enlist(Db session, GlobalTransactionListener listener) {
    ManagedConnection connection = session.connection();
    if (!(connection instanceof XaManagedConnection xaConnection)) {
      throw new PersistenceException(session, "connection " + connection + " cannot take part in global transactions, " +
                                              "use an " + XaConnectionManager.class.getSimpleName() +
                                              " or " + XaMpxConnectionManager.class.getSimpleName());
    }
    try {
      Transaction transaction = getTransactionManager().getTransaction();
      if (transaction == null) {
        throw new PersistenceException(session, "no global transaction associated with the current thread");
      }
      // register first: if the enlistment fails, the session detaches, and a left-over synchronization is a no-op,
      // whereas a connection left enlisted could be handed to another session while still in the global transaction
      transaction.registerSynchronization(new ListenerSynchronization(listener));
      if (!transaction.enlistResource(xaConnection.getXAResource())) {
        throw new PersistenceException(session, "connection " + connection + " could not be enlisted in " + transaction);
      }
    }
    catch (RollbackException rx) {
      throw new PersistenceException(session, "global transaction is marked for rollback", rx);
    }
    catch (SystemException | IllegalStateException ex) {
      throw new PersistenceException(session, "enlisting in the global transaction failed", ex);
    }
  }

  @Override
  public void commit(Db session) {
    RuntimeException failure;
    try {
      getTransactionManager().commit();
      failure = takeCompletionFailure();    // committed, but some post-commit failed
    }
    catch (RollbackException rx) {
      failure = takeCompletionFailure();
      if (failure == null) {
        failure = createPersistenceException(session, "global transaction rolled back", rx);
      }
      else {
        failure.addSuppressed(rx);
      }
    }
    catch (HeuristicMixedException | HeuristicRollbackException hx) {
      takeCompletionFailure();
      failure = new PersistenceException(session, "heuristic outcome of the global transaction", hx);
    }
    catch (SystemException | SecurityException | IllegalStateException ex) {
      takeCompletionFailure();
      failure = createPersistenceException(session, "committing the global transaction failed", ex);
    }
    if (failure != null) {
      throw failure;
    }
  }

  @Override
  public void rollback(Db session) {
    try {
      TransactionManager tm = getTransactionManager();
      if (tm.getStatus() != Status.STATUS_NO_TRANSACTION) {
        tm.rollback();
      }
    }
    catch (SystemException | SecurityException | IllegalStateException ex) {
      throw new PersistenceException(session, "rolling back the global transaction failed", ex);
    }
    finally {
      COMPLETION_FAILURE.remove();
    }
  }

  @Override
  public void setRollbackOnly(Db session) {
    try {
      TransactionManager tm = getTransactionManager();
      if (tm.getStatus() != Status.STATUS_NO_TRANSACTION) {
        tm.setRollbackOnly();
      }
    }
    catch (SystemException | IllegalStateException ex) {
      throw new PersistenceException(session, "marking the global transaction rollback-only failed", ex);
    }
  }


  /**
   * Creates a persistence exception for a failed global transaction.<br>
   * The exception is flagged temporary if the XA error code indicates a transient failure,
   * such as a deadlock or timeout, or a temporary exception is found in the cause chain.
   *
   * @param session the session
   * @param message the message
   * @param cause the cause
   * @return the exception
   */
  protected PersistenceException createPersistenceException(Db session, String message, Exception cause) {
    PersistenceException ex = new PersistenceException(session, message, cause);
    ex.setTemporary(isTemporary(cause));
    return ex;
  }

  /**
   * Checks whether a global transaction failed for a transient reason.
   *
   * @param cause the exception
   * @return true if retrying the transaction may succeed
   */
  protected boolean isTemporary(Throwable cause) {
    TentackleRuntimeException temporaryException = ExceptionHelper.extractTemporaryException(false, cause);
    if (temporaryException != null) {
      return true;
    }
    for (Throwable t = cause; t != null; t = t.getCause()) {
      if (isTransientXAException(t)) {
        return true;
      }
      for (Throwable suppressed : t.getSuppressed()) {    // some transaction managers add the causes as suppressed
        if (isTransientXAException(suppressed) || isTransientXAException(suppressed.getCause())) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isTransientXAException(Throwable t) {
    return t instanceof XAException xa &&
           (xa.errorCode == XAException.XA_RBDEADLOCK ||
            xa.errorCode == XAException.XA_RBTIMEOUT ||
            xa.errorCode == XAException.XA_RBTRANSIENT);
  }


  /**
   * Records an exception thrown by a listener, if it is the first one.
   *
   * @param ex the exception
   * @return true if the current thread completes a global transaction begun via {@link #begin(Db, String)},
   * false if the exception cannot be rethrown by {@link #commit(Db)}
   */
  private static boolean recordCompletionFailure(RuntimeException ex) {
    CompletionFailure holder = COMPLETION_FAILURE.get();
    if (holder == null) {
      return false;
    }
    if (holder.failure == null) {
      holder.failure = ex;
    }
    return true;
  }

  private static RuntimeException takeCompletionFailure() {
    CompletionFailure holder = COMPLETION_FAILURE.get();
    COMPLETION_FAILURE.remove();
    return holder == null ? null : holder.failure;
  }


  /**
   * Mutable holder of the first listener exception.
   */
  private static final class CompletionFailure {
    private RuntimeException failure;
  }


  /**
   * Adapts a {@link GlobalTransactionListener} to a JTA {@link Synchronization}.
   *
   * @param listener the listener
   */
  private record ListenerSynchronization(GlobalTransactionListener listener) implements Synchronization {

    @Override
    public void beforeCompletion() {
      try {
        listener.beforeCompletion();
      }
      catch (RuntimeException ex) {
        recordCompletionFailure(ex);
        try {
          getTransactionManager().setRollbackOnly();    // the JTA spec is vague about exceptions thrown here
        }
        catch (SystemException | RuntimeException rx) {
          ex.addSuppressed(rx);
        }
        throw ex;
      }
    }

    @Override
    public void afterCompletion(int status) {
      boolean committed = status == Status.STATUS_COMMITTED;
      try {
        listener.afterCompletion(committed);
      }
      catch (RuntimeException ex) {
        // a failed post-commit is rethrown by commit(), like in a local transaction, if a Tentackle session owns it
        if (!committed) {
          LOGGER.severe("completing a rolled back global transaction failed", ex);
        }
        else if (!recordCompletionFailure(ex)) {
          LOGGER.severe("completing a committed global transaction failed", ex);
        }
      }
    }
  }

}
