/**
 * Tentackle - http://www.tentackle.org
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


package org.tentackle.dbms.rmi;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Date;
import java.util.regex.Pattern;
import org.tentackle.common.LocaleProvider;
import org.tentackle.log.Loggable;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.LoggerFactory;
import org.tentackle.log.MappedDiagnosticContext;
import org.tentackle.misc.Duration;
import org.tentackle.common.StringHelper;
import org.tentackle.dbms.Db;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.session.Session;


/**
 * Invocation handler to capture RMI method invocations.
 *
 * @author harald
 */
public class RemoteDelegateInvocationHandler implements InvocationHandler {

  /**
   * the mdc session key
   */
  public static final String MDC_SESSION_KEY = "session";

  /**
   * If true method invocations are counted and measured per method.
   * They can be logged via RemoteDbSession#logStatistics by the client request.
   * For debugging and profiling purposes only.
   */
  public static boolean collectStatistics;

  /**
   * If true, all invocations are logged.
   */
  public boolean logInvocations;

  /**
   * If true, args and return values will be logged as well.
   */
  public boolean logInvocationDetails;

  /**
   * Minimum milliseconds a method needs to execute before being logged.<br>
   * 0 to disable.<p>
   * For analysis and debugging purposes only.
   */
  public static long logMinDurationMillis;

  /**
   * Minimum size of returned collection to get logged.<br>
   * 0 to disable.<p>
   * For analysis and debugging purposes only.
   */
  public static int logMinReturnedCollectionSize;

  /**
   * Optional filter to log only RMI-calls for certain MappedDiagnosticContexts.<br>
   * If set, statements are only collected for logging if the MDC is valid and the pattern matches the toString-value
   * of the MDC.
   */
  public static Pattern mdcFilter;


  /**
   * RMI thread name lead string.
   */
  private static final String RMI_THREAD_NAME = "RMI TCP Connection";


  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteDelegateInvocationHandler.class);



  /**
   * The delegate.
   */
  private final RemoteDelegateImpl<?> delegate;


  /**
   * Creates an invocation handler for a {@link RemoteDelegate}.
   * <p>
   * The invocation handler may be used to intercept remote method invocations.
   * The default implementation just sets the session info into the {@link MappedDiagnosticContext}.
   *
   * @param delegate the remote delegate
   */
  public RemoteDelegateInvocationHandler(RemoteDelegateImpl<?> delegate) {
    this.delegate = delegate;
  }


  /**
   * Gets the delegate.
   *
   * @return the delegate
   */
  public RemoteDelegateImpl<?> getDelegate() {
    return delegate;
  }


  /**
   * Updates the {@link MappedDiagnosticContext} with the session data.
   */
  protected void updateMDC() {
    MappedDiagnosticContext mdc = LOGGER.getMappedDiagnosticContext();
    RemoteDbSessionImpl session = delegate.getServerSession();
    mdc.put(MDC_SESSION_KEY, session.getSessionNumber() + ":" + session.getClientSessionInfo().getUserName());
  }

  /**
   * Clears the MDC.
   */
  protected void clearMDC() {
    MappedDiagnosticContext mdc = LOGGER.getMappedDiagnosticContext();
    mdc.remove(MDC_SESSION_KEY);
  }

  /**
   * Checks whether the mapped diagnostic context is valid for statistics logging.
   *
   * @return true if loggable
   */
  protected boolean isMDCValid() {
    if (mdcFilter == null) {
      return true;
    }
    MappedDiagnosticContext mdc = LOGGER.getMappedDiagnosticContext();
    return mdc != null && mdc.matchesPattern(mdcFilter);
  }

  /**
   * Updates the thread local memory.
   */
  protected void updateThreadLocal() {
    delegate.getSession().makeCurrent();
    LocaleProvider.getInstance().setCurrentLocale(delegate.getSession().getSessionInfo().getLocale());
  }


  /**
   * Locks the Db for exclusive use by current thread.
   */
  protected void lockDbForThread() {
    delegate.getSession().setOwnerThread(Thread.currentThread());
  }



  /**
   * Handles an invocation exception.<br>
   * Figures out the real exception cause and throws it,
   * skipping InvocationTargetExceptions.
   * Furthermore, the exception will be logged in the server's log as well (not just the client).
   *
   * @param proxy the dynamic proxy
   * @param method the method to invoke
   * @param args the method's arguments
   * @param cause the invocation exception
   * @return the real RemoteException
   */
  protected RemoteException handleInvocationException(Object proxy, Method method, Object[] args, Throwable cause) {
    // skip all InvocationTargetExceptions (could be chained if method itself is "proxied")
    while (cause instanceof InvocationTargetException) {
      Throwable nextCause = cause.getCause();
      if (nextCause == null) {
        break;
      }
      cause = nextCause;
    }

    Level loggingLevel = Level.SEVERE;
    boolean withStacktrace = true;
    Throwable realCause = cause;

    try {
      if (cause instanceof RemoteException) {
        /**
         * log the exception on the server as well.
         * We use the info level because this exception
         * is meant for the client.
         */
        realCause = cause.getCause();
        if (realCause instanceof Loggable) {
          Loggable loggable = (Loggable) realCause;
          loggingLevel = loggable.getLogLevel();
          withStacktrace = loggable.withStacktrace();
        }
        else {
          loggingLevel = Level.INFO;
        }
        return (RemoteException) cause;
      }
      else  {
        return new RemoteException("remote method execution failed", cause);
      }
    }
    finally {
      Db db = delegate.getSession();
      boolean crashed = db != null && db.isCrashed();

      if (crashed) {
        loggingLevel = Level.WARNING;
      }

      if (loggingLevel != null) {
        StringBuilder message = new StringBuilder();
        message.append("delegate method ").append(method).append(" failed");
        if (crashed) {
          message.append(" due to session cleanup");
        }
        message.append(":\n").append(realCause.getMessage());
        LOGGER.log(loggingLevel, message.toString(), withStacktrace ? realCause : null);
      }

      cleanupDbAfterException(realCause);
    }
  }



  /**
   * Cleans up the db after an exception.<br>
   * Invoked from {@link #handleInvocationException}.
   *
   * @param cause the cause of failure
   */
  protected void cleanupDbAfterException(Throwable cause) {
    Db db = delegate.getSession();
    if (db != null && !db.isCrashed()) {
      try {
        // rollback any pending transaction and/or force detaching the db
        db.rollbackImmediately(cause);
      }
      catch (RuntimeException ex) {
        LOGGER.severe("*** immediate rollback failed ***", ex);
      }
    }
  }


  /**
   * Invokes the delegate's method.
   * <p>
   * If the invocation failed for some reason the stacktrace
   * is logged.
   *
   * @param proxy the dynamic proxy
   * @param method the method to invoke
   * @param args the method's arguments
   * @return the method's return value
   * @throws RemoteException if invocation failed
   */
  protected Object invokeImpl(Object proxy, Method method, Object[] args) throws RemoteException {
    try {
      return method.invoke(delegate, args);
    }
    catch (Exception cause) {
      throw handleInvocationException(proxy, method, args, cause);
    }
  }


  /**
   * Returns whether measuring the duration is necessary.
   *
   * @return true if duration necessary
   */
  protected boolean needDuration() {
    return logInvocations || collectStatistics || logMinDurationMillis > 0 || logMinReturnedCollectionSize > 0;
  }


  /**
   * Logs the invocation.
   * <p>
   *
   * @param method the method executed
   * @param args the method args
   * @param retval the method's return value
   * @param duration the duration when execution was started, null if don't care
   */
  protected void log(Method method, Object[] args, Object retval, Duration duration) {

    boolean durationExceeded = false;

    if (duration != null) {
      if (!duration.isValid()) {
        duration.end();
      }
      if (logMinDurationMillis > 0 && duration.millis() > logMinDurationMillis) {
        durationExceeded = true;
      }
      if (collectStatistics && isMDCValid()) {
        delegate.getServerSession().countMethodInvocation(method, delegate.getServicedClass(), duration);
      }
    }

    if (isMDCValid()) {
      int collectionSize = retval instanceof Collection ? ((Collection) retval).size() : 0;
      boolean collectionSizeExceeded = logMinReturnedCollectionSize > 0 && collectionSize > logMinReturnedCollectionSize;

      if (logInvocations || durationExceeded || collectionSizeExceeded) {
        StringBuilder buf = new StringBuilder();
        if (logInvocationDetails) {
          buf.append(ReflectionHelper.getClassBaseName(delegate.getServicedClass())).
              append(": ").
              append(ReflectionHelper.getClassBaseName(delegate.getClass())).
              append('.').
              append(method.getName()).
              append('(');
          if (args != null) {
            for (int i=0; i < args.length; i++) {
              if (i > 0)  {
                buf.append(',');
              }
              buf.append(StringHelper.objectToLoggableString(args[i]));
            }
          }
          buf.append(") = ").
              append(StringHelper.objectToLoggableString(retval)).
              append(" {").
              append(duration != null ? duration.millisToString() : "<unknown>").
              append("ms");
          if (collectionSize > 0) {
            buf.append(", ").
                append(collectionSize).
                append(" items");
          }
          buf.append('}');
        }
        else {
          buf.append(delegate.getClass().getName()).
              append('.').
              append(method.getName()).
              append(" [").
              append(duration != null ? duration.millisToString() : "<unknown>").
              append("ms]");
        }

        if (durationExceeded || collectionSizeExceeded) {
          if (durationExceeded) {
            buf.insert(0, ">>> duration >>> ");
          }
          if (collectionSizeExceeded) {
            buf.insert(0, ">>> size >>> ");
          }
          LOGGER.warning(buf.toString());
        }
        else {
          LOGGER.info(buf.toString());
        }
      }
    }
    // else no logging
  }


  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just updates the MDC, invokes the method and logs the invoked method (args and return value)
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    RemoteDbSessionImpl serverSession = delegate.getServerSession();

    /**
     * Because RMI requests may be executed by different threads (from the RMI's threadpool)
     * we must make sure that successive invocations using the same session (mapped 1:1 to the Db instances)
     * see each other's modifications. Hence, we synchronize on the session.
     * Because no two RMI-requests refer to the same session simultaneously (except the client
     * is misbehaving) the object monitor on the session will never cause a deadlock because
     * of another conflicting synchronization.
     */
    synchronized (serverSession) {

      if (!serverSession.isOpen()) {
        throw new RemoteSessionClosedException(serverSession + " already closed since " + new Date(serverSession.getClosedSince()));
      }

      InvocationLocals locals = Thread.currentThread().getName().startsWith(RMI_THREAD_NAME) ?
              new InvocationLocals(delegate, LOGGER.getMappedDiagnosticContext(), MDC_SESSION_KEY) : null;

      if (locals != null) {
        // mark session still alive
        delegate.getSession().setAlive(true);

        try {
          // update thread local memory
          updateThreadLocal();

          // set the MDC for logging
          updateMDC();

          // invoke the method with Db temporarily locked to the current thread
          lockDbForThread();
        }
        catch (Throwable t) {
          // for some weird reasons the session is in use or whatever.
          // Can't happen, but if it does, we need to fix this somehow.
          LOGGER.severe("cleanup server session locals after exception", t);
          delegate.getSession().setOwnerThread(null);
          Session.setCurrentSession(null);
          LocaleProvider.getInstance().setCurrentLocale(null);
          clearMDC();
          throw t;
        }
      }

      try {
        Duration duration = needDuration() ? new Duration() : null;
        Object retval = invokeImpl(proxy, method, args);
        log(method, args, retval, duration);
        return retval;
      }
      finally {
        if (locals != null) {
          locals.restore();
        }
      }
    }
  }


}
