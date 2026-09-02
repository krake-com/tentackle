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

package org.tentackle.pdo;

import org.tentackle.common.Constants;
import org.tentackle.common.ExceptionHelper;
import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionUtilities;
import org.tentackle.session.TransactionIsolation;
import org.tentackle.session.TransactionWritability;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Implementation of the {@link Transaction} interception.
 */
public final class TransactionInterceptor extends RemoteMethodAwareInterceptor {

  private static final Logger LOGGER = Logger.get(TransactionInterceptor.class);


  private String transactionName;                         // the transaction name from the annotation
  private boolean running;                                // true if tx must be running already
  private boolean rollbackSilently;                       // true: don't log rollbacks
  private TransactionIsolation transactionIsolation;      // the isolation level
  private TransactionWritability transactionWritability;  // the writability
  private boolean deferred;                               // whether to defer deferrable constraints
  private boolean transactionRetry;                       // whether to retry the transaction after a transient exception
  private String transactionRetryPolicy;                  // the policy name
  private Logger.Level transactionRetryLogLevel;          // the retry log level


  /**
   * Creates a {@link Transaction} interceptor.
   */
  public TransactionInterceptor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void setAnnotation(Annotation annotation) {
    super.setAnnotation(annotation);
    Transaction txAnno = (Transaction) annotation;
    transactionName = txAnno.value();
    running = txAnno.running();
    rollbackSilently = txAnno.rollbackSilently();
    transactionIsolation = txAnno.isolation();
    transactionWritability = txAnno.writability();
    deferred = txAnno.deferred();
    transactionRetry = txAnno.retry();
    transactionRetryPolicy = txAnno.retryPolicy();
    transactionRetryLogLevel = txAnno.retryLogLevel();
  }

  @Override
  public Object proceed(Object proxy, Method method, Object[] args,
                        Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable {

    Session session = getSession(orgProxy);

    if (session.isRemote() && isRemoteMethod(orgMethod)) {
      // If the session is remote and the implementation method is annotated with @RemoteMethod,
      // then delegate to the TransactionInterceptor at the remote server.
      // The presence of @RemoteMethod indicates that there is a RemoteMethod wurblet,
      // and the generated code will delegate the invocation to the remote server.
      // This saves two network round trips (begin and commit).
      return method.invoke(proxy, args);
    }

    if (running) {
      if (session.isTxRunning()) {
        if (Constants.NAME_UNKNOWN.equals(transactionName) || transactionName.equals(session.getTxName())) {
          return method.invoke(proxy, args);
        }
        throw new PersistenceException(session, "unexpected transaction named '" + session.getTxName() +
                                                "' running (expected '" + transactionName + "')");
      }
      throw new PersistenceException(session, "no transaction running");
    }
    else {
      String txName = Constants.NAME_UNKNOWN.equals(transactionName) ?
                        (orgMethod.getDeclaringClass().getSimpleName() + "#" + orgMethod.getName()) :
                        transactionName;

      TransactionRetryPolicy retryPolicy = transactionRetry ? PdoUtilities.getInstance().getTransactionRetryPolicy(transactionRetryPolicy) : null;

      for (int retryCount = 1; ; retryCount++) {
        long txVoucher = session.begin(txName, transactionIsolation, transactionWritability);
        if (deferred && txVoucher != 0) {
          session.setConstraintsDeferred(true);
        }
        try {
          Object result = method.invoke(proxy, args);
          session.commit(txVoucher);
          return result;
        }
        catch (Throwable t) {
          // transaction failed!
          try {
            if (rollbackSilently || txVoucher == 0 || SessionUtilities.getInstance().isSilentRollbackSufficient(t)) {
              // silent rollback requested, nested tx, temporary or application-specific exception (domain logic) -> no detailed logging
              session.rollbackSilently(txVoucher);
            }
            else {
              // the cause is a non-temporary PersistenceException (usually some database error such as a constraint exception) -> log the details
              session.rollback(txVoucher);
            }
          }
          catch (RuntimeException rex) {
            // rollback may fail for several reasons, usually some further database errors. Those errors
            // are mapped to a PersistenceException and just cluttering the logfile, if logged.
            // Other exceptions like NPEs, however, definitely provide useful information that must be logged.
            if (ExceptionHelper.extractException(PersistenceException.class, true, rex) == null) {
              LOGGER.severe("rollback failed after " + t.getClass().getSimpleName() + " (" + t.getMessage() + ")", rex);
            }
          }

          if (txVoucher == 0 || !isRetrying(retryPolicy, txName, retryCount, t)) {
            throw t;
          }
        }
      }
    }
  }


  private boolean isRetrying(TransactionRetryPolicy retryPolicy, String txName, int retryCount, Throwable t) {
    if (retryPolicy != null) {
      TentackleRuntimeException temporaryException = ExceptionHelper.extractTemporaryException(false, t);
      if (temporaryException != null) {
        long waitMillis = retryPolicy.waitMillis(txName, retryCount);
        if (waitMillis > 0) {
          LOGGER.log(transactionRetryLogLevel, null, () ->
              ExceptionHelper.getMessage(temporaryException) + " -> retrying transaction " + txName + " after " + waitMillis + " ms");
          try {
            Thread.sleep(waitMillis);
            return true;
          }
          catch (InterruptedException e) {
            throw new InterruptedRuntimeException(e);
          }
        }
      }
    }
    return false;
  }

}
