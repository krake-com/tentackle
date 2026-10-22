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

package org.tentackle.dbms.trip;

import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.LocaleProvider;
import org.tentackle.common.StringHelper;
import org.tentackle.dbms.Db;
import org.tentackle.log.Loggable;
import org.tentackle.log.Logger;
import org.tentackle.log.Logger.Level;
import org.tentackle.log.MappedDiagnosticContext;
import org.tentackle.misc.TimeKeeper;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.trip.TripRuntimeException;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Date;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Invocation handler to capture TRIP method invocations.
 *
 * @author harald
 */
public class RemoteDbDelegateInvocationHandler implements InvocationHandler {

  private static final Logger LOGGER = Logger.get(RemoteDbDelegateInvocationHandler.class);

  /**
   * The keep-alive methods, which may be invoked concurrently to the other methods of the same session.
   */
  private static final Set<Method> KEEP_ALIVE_METHODS;

  static {
    try {
      KEEP_ALIVE_METHODS = Set.of(DbRemoteDelegate.class.getMethod("isAlive"),
                                  DbRemoteDelegate.class.getMethod("setAlive", boolean.class));
    }
    catch (NoSuchMethodException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }


  /**
   * the mdc session key
   */
  public static final String MDC_SESSION_KEY = "session";


  /**
   * If true, method invocations are counted and measured per method.
   * They can be logged via RemoteDbSession#logStatistics by the client request.
   * For debugging and profiling purposes only.
   */
  private static boolean collectStatistics;

  /**
   * If true, all invocations are logged.
   */
  private static boolean logInvocations;

  /**
   * If true, args and return values will be logged as well.
   */
  private static boolean logInvocationDetails;

  /**
   * Minimum milliseconds a method needs to execute before being logged.<br>
   * 0 to disable.<p>
   * For analysis and debugging purposes only.
   */
  private static long logMinDurationMillis;

  /**
   * Minimum size of the returned collection to get logged.<br>
   * 0 to disable.<p>
   * For analysis and debugging purposes only.
   */
  private static int logMinReturnedCollectionSize;

  /**
   * Optional filter to log only TRIP-calls for certain MappedDiagnosticContexts.<br>
   * If set, statements are only collected for logging if the MDC is valid and the pattern matches the toString-value
   * of the MDC.
   */
  private static Pattern mdcFilter;


  /**
   * Returns whether statistics should be collected.
   *
   * @return true if method invocations are counted and measured per method
   */
  public static boolean isCollectingStatistics() {
    return collectStatistics;
  }

  /**
   * Sets whether statistics should be collected.
   *
   * @param collect true if method invocations are counted and measured per method
   */
  public static void setCollectingStatistics(boolean collect) {
    collectStatistics = collect;
    updateDurationNecessary();
  }

  /**
   * Returns whether each invocation should be logged.
   *
   * @return true if invocations are logged
   */
  public static boolean isLoggingInvocations() {
    return logInvocations;
  }

  /**
   * Sets whether each invocation should be logged.
   *
   * @param log true if invocations are logged
   */
  public static void setLoggingInvocations(boolean log) {
    logInvocations = log;
    updateDurationNecessary();
  }

  /**
   * Returns whether args and return values will be logged as well.
   *
   * @return true if log arguments and return value
   */
  public static boolean isLoggingInvocationDetails() {
    return logInvocationDetails;
  }

  /**
   * Sets whether args and return values will be logged as well.
   *
   * @param log true if log arguments and return value
   */
  public static void setLoggingInvocationDetails(boolean log) {
    logInvocationDetails = log;
  }

  /**
   * Gets the minimum milliseconds a method needs to execute before being logged.
   *
   * @return 0 if disabled
   */
  public static long getLogMinDurationMillis() {
    return logMinDurationMillis;
  }

  /**
   * Sets the minimum milliseconds a method needs to execute before being logged.
   *
   * @param millis 0 to disable
   */
  public static void setLogMinDurationMillis(long millis) {
    logMinDurationMillis = millis;
    updateDurationNecessary();
  }

  /**
   * Gets the minimum size of the returned collection to get logged.
   *
   * @return 0 if disabled
   */
  public static int getLogMinReturnedCollectionSize() {
    return logMinReturnedCollectionSize;
  }

  /**
   * Sets the minimum size of the returned collection to get logged.
   *
   * @param size 0 to disable
   */
  public static void setLogMinReturnedCollectionSize(int size) {
    logMinReturnedCollectionSize = size;
    updateDurationNecessary();
  }

  /**
   * Gets the MDC filter.
   *
   * @return the filter, null if none
   */
  public static Pattern getMdcFilter() {
    return mdcFilter;
  }

  /**
   * Sets the optional filter to log only TRIP-calls for certain MappedDiagnosticContexts.<br>
   * If set, statements are only collected for logging if the MDC is valid and the pattern matches the toString-value
   * of the MDC.
   *
   * @param filter the filter, null to clear
   */
  public static void setMdcFilter(Pattern filter) {
    mdcFilter = filter;
  }

  /**
   * Set to true if method duration must be measured.
   */
  private static boolean durationNecessary;

  /**
   * The delegate.
   */
  private final RemoteDbDelegateImpl<?> delegate;


  /**
   * Creates an invocation handler for a {@link RemoteDbDelegate}.
   * <p>
   * The invocation handler may be used to intercept remote method invocations.
   * The default implementation just sets the session info into the {@link MappedDiagnosticContext}.
   *
   * @param delegate the remote delegate
   */
  public RemoteDbDelegateInvocationHandler(RemoteDbDelegateImpl<?> delegate) {
    this.delegate = delegate;
  }


  /**
   * Gets the delegate.
   *
   * @return the delegate
   */
  public RemoteDbDelegateImpl<?> getDelegate() {
    return delegate;
  }

  /**
   * {@inheritDoc}
   * <p>
   * The default implementation just updates the MDC, invokes the method, and logs the invoked method (args and return value).
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

    // TRIP dispatches the method invocations to a thread pool (regular threads, not virtual).
    // Successive invocations of the same session are serialized by enterInvocation/exitInvocation below,
    // which also ensures the happens-before visibility.

    RemoteDbSessionImpl serverSession = delegate.getServerSession();

    if (!serverSession.isOpen()) {
      // This will normally not happen since the session removes itself from the registry when it was closed,
      // but probably in case of a race condition.
      throw new RemoteSessionClosedException(serverSession + " already closed since " + new Date(serverSession.getClosedSince()));
    }

    RemoteDbSessionInvocationFilter invocationFilter = serverSession.getInvocationFilter();
    if (invocationFilter != null) {
      invocationFilter.filter(proxy, method, args);
    }

    if (KEEP_ALIVE_METHODS.contains(method)) {
      // The client's keep-alive pings the session from another thread, concurrently to its regular invocations.
      // The alive-flag is thread-safe: no ownership of the session.
      return invokeImpl(proxy, method, args);
    }

    // The local session is not thread-safe: reject invocations running in parallel for the same session.
    if (!serverSession.enterInvocation()) {
      throw new PersistenceException(delegate.getSession(), "invocation of " + method.getName() + " rejected: " +
                                     serverSession + " is in use by " + serverSession.getInvokingThread() +
                                     " -> client misbehaving?");
    }

    try {
      if (!serverSession.isOpen()) {
        // closed by the client or the cleanup thread after the check above
        throw new RemoteSessionClosedException(serverSession + " already closed since " + new Date(serverSession.getClosedSince()));
      }

      try {
        updateThreadInfo();
        updateMDC();
      }
      catch (Throwable t) {
        LOGGER.severe("cleanup server session locals after exception", t);
        clearThreadInfo();
        clearMDC();
        throw t;
      }

      try {
        if (durationNecessary) {
          TimeKeeper timeKeeper = new TimeKeeper();
          Object returnValue = invokeImpl(proxy, method, args);
          timeKeeper.end();
          log(method, args, returnValue, timeKeeper);
          return returnValue;
        }
        else {
          return invokeImpl(proxy, method, args);
        }
      }
      finally {
        clearThreadInfo();
        clearMDC();
      }
    }
    finally {
      serverSession.exitInvocation();
    }
  }


  /**
   * Updates the {@link MappedDiagnosticContext} with the session data.
   */
  protected void updateMDC() {
    MappedDiagnosticContext mdc = LOGGER.getMappedDiagnosticContext();
    mdc.put(MDC_SESSION_KEY, delegate.getServerSession().getMdcInfo());
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
   * Updates the thread-locals and sets the session's owner thread.
   */
  protected void updateThreadInfo() {
    Db session = delegate.getSession();
    session.setAlive(true);
    session.makeCurrent();
    LocaleProvider.getInstance().setCurrentLocale(session.getSessionInfo().getLocale());
    Thread currentThread = Thread.currentThread();
    Thread ownerThread = session.getOwnerThread();
    if (ownerThread != null) {
      if (ownerThread != currentThread) {
        // Invocations running in parallel for the same session are rejected by the invocation handler.
        // Hence, the owner thread was set by some other code using this session.
        LOGGER.warning("session {0} owned by thread {1}", session, ownerThread);
      }
      else {
        // The owner thread is the current thread and was not released at the end of the last invocation.
        // Shouldn't happen, but one never knows...
        LOGGER.warning("session {0} still owned by current thread", session);
      }
    }
    session.setOwnerThread(currentThread);
  }

  /**
   * Clears the thread-locals and session's owner thread.
   */
  protected void clearThreadInfo() {
    Db session = delegate.getSession();
    if (session != null) {
      session.setOwnerThread(null);
      Session.setCurrentSession(null);
    }
    LocaleProvider.getInstance().setCurrentLocale(null);
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
   */
  protected RuntimeException handleInvocationException(Object proxy, Method method, Object[] args, Throwable cause) {
    // skip all InvocationTargetExceptions (could be chained if the method itself is "proxied")
    while (cause instanceof InvocationTargetException) {
      Throwable nextCause = cause.getCause();
      if (nextCause == null) {
        break;
      }
      cause = nextCause;
    }

    Level loggingLevel = Level.SEVERE;
    boolean withStacktrace = true;

    try {
      if (cause instanceof TripRuntimeException tre) {
        // some protocol error
        return tre;
      }
      else {
        /*
         * log the exception on the server as well.
         * We use the info level because this exception
         * is meant for the client.
         */
        if (cause instanceof Loggable loggable) {
          loggingLevel = loggable.getLogLevel();
          withStacktrace = loggable.withStacktrace();
        }
        else {
          loggingLevel = Level.INFO;
        }
        cause.setStackTrace(ExceptionHelper.filterStackTrace(cause.getStackTrace()));
        if (cause instanceof RuntimeException rx) {
          return rx;
        }
        return new PersistenceException(cause);
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
        if (withStacktrace) {
          LOGGER.log(loggingLevel, message.toString(), cause);
        }
        else {
          message.append(": ").append(cause.getMessage());
          LOGGER.log(loggingLevel, message.toString());
        }
      }

      cleanupDbAfterException(cause);
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
    if (db != null && !db.isCrashed() &&
        !db.isTxWithSavepoints()) {   // if save points are used, the client application handles everything on its own
      try {
        // roll back any pending transaction and/or force detaching the db.
        // this improves stability in case the client is misbehaving, waits at a user prompt, or whatever.
        // if it behaves as expected, further rollbacks will just decrement a counter.
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
   * If the invocation failed for some reason, the stacktrace is logged.
   *
   * @param proxy the dynamic proxy
   * @param method the method to invoke
   * @param args the method's arguments
   * @return the method's return value
   */
  protected Object invokeImpl(Object proxy, Method method, Object[] args) {
    try {
      return method.invoke(delegate, args);
    }
    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException cause) {
      throw handleInvocationException(proxy, method, args, cause);
    }
  }

  /**
   * Logs and counts the invocation.
   * <p>
   *
   * @param method the method executed
   * @param args the method args
   * @param returnValue the method's return value
   * @param timeKeeper the duration when execution was started
   */
  protected void log(Method method, Object[] args, Object returnValue, TimeKeeper timeKeeper) {

    boolean durationExceeded = logMinDurationMillis > 0 && timeKeeper.millis() > logMinDurationMillis;
    if (collectStatistics && isMDCValid()) {
      delegate.getServerSession().countMethodInvocation(method, delegate.getServicedClass(), timeKeeper);
    }

    if (isMDCValid()) {
      int collectionSize = returnValue instanceof Collection ? ((Collection<?>) returnValue).size() : 0;
      boolean collectionSizeExceeded = logMinReturnedCollectionSize > 0 && collectionSize > logMinReturnedCollectionSize;

      if (durationExceeded || collectionSizeExceeded) {
        LOGGER.warning(() -> {
          StringBuilder buf = createInvocationLog(method, args, returnValue, timeKeeper, collectionSize);
          if (durationExceeded) {
            buf.insert(0, ">>> duration exceeded:\n");
          }
          if (collectionSizeExceeded) {
            buf.insert(0, ">>> collection size exceeded:\n");
          }
          return buf.toString();
        });
      }
      else if (logInvocations) {
        LOGGER.info(() -> createInvocationLog(method, args, returnValue, timeKeeper, collectionSize).toString());
      }
    }
    // else no logging
  }


  private StringBuilder createInvocationLog(Method method, Object[] args, Object returnValue,
                                            TimeKeeper timeKeeper, int collectionSize) {
    StringBuilder buf = new StringBuilder();
    if (logInvocationDetails) {
      buf.append(delegate.getServicedClass().getSimpleName()).
          append(": ").
          append(delegate.getClass().getSimpleName()).
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
          append(StringHelper.objectToLoggableString(returnValue)).
          append(" {").
          append(timeKeeper.millisToString()).
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
          append(timeKeeper.millisToString()).
          append("ms]");
    }
    return buf;
  }

  private static void updateDurationNecessary() {
    durationNecessary = logInvocations ||
                        collectStatistics ||
                        logMinDurationMillis > 0 ||
                        logMinReturnedCollectionSize > 0;
  }

}
