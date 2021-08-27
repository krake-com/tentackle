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
import org.tentackle.reflect.AbstractInterceptor;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionProvider;
import org.tentackle.session.TransactionIsolation;
import org.tentackle.session.TransactionWritability;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Implementation for the transaction interception.
 *
 * @author harald
 */
public class TransactionInterceptor extends AbstractInterceptor {

  private static final Logger LOGGER = Logger.get(TransactionInterceptor.class);


  private String transactionName;                         // the transaction name from the annotation
  private TransactionIsolation transactionIsolation;      // the isolation level
  private TransactionWritability transactionWritability;  // the writability
  private boolean transactionRetry;                       // whether to retry the transaction after a transient exception
  private String transactionRetryPolicy;                  // the policy name
  private Logger.Level transactionRetryLogLevel;          // the retry log level

  @Override
  public void setAnnotation(Annotation annotation) {
    super.setAnnotation(annotation);
    Transaction txAnno = (Transaction) annotation;
    transactionName = txAnno.value();
    transactionIsolation = txAnno.isolation();
    transactionWritability = txAnno.writability();
    transactionRetry = txAnno.retry();
    transactionRetryPolicy = txAnno.retryPolicy();
    transactionRetryLogLevel = txAnno.retryLogLevel();
  }

  @Override
  public Object proceed(Object proxy, Method method, Object[] args,
                        Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable {

    if (orgProxy instanceof SessionProvider) {
      Session session = ((SessionProvider) orgProxy).getSession();
      String txName = Constants.NAME_UNKNOWN.equals(transactionName) ?
                        (ReflectionHelper.getClassBaseName(orgMethod.getDeclaringClass()) + "#" + orgMethod.getName()) :
                        transactionName;
      TransactionRetryPolicy retryPolicy = transactionRetry ? PdoUtilities.getInstance().getTransactionRetryPolicy(transactionRetryPolicy) : null;

      for (int retryCount=1; ;retryCount++) {
        long txVoucher = session.begin(txName, transactionIsolation, transactionWritability);
        if (txVoucher != 0) {
          LOGGER.fine("started transaction {0} on {1} with voucher {2}", txName, session, txVoucher);
        }
        try {
          Object result = method.invoke(proxy, args);
          session.commit(txVoucher);
          if (txVoucher != 0) {
            LOGGER.fine("committed transaction {0} on {1} with voucher {2}", txName, session, txVoucher);
          }
          return result;
        }
        catch (Throwable ex) {
          try {
            if (txVoucher != 0 && ExceptionHelper.extractException(PersistenceException.class, true, ex) != null) {
              // log only if cause is a PersistenceException
              session.rollback(txVoucher);
              LOGGER.fine("transaction {0} rolled back on {1} with voucher {2}", txName, session, txVoucher);
            }
            else {
              session.rollbackSilently(txVoucher);
            }
          }
          catch (RuntimeException rex) {
            LOGGER.severe("rollback failed", rex);
          }

          if (txVoucher == 0 || !isRetrying(retryPolicy, txName, retryCount, ex)) {
            throw ex;
          }
        }
      }
    }
    else  {
      throw new PersistenceException(EffectiveClassProvider.getEffectiveClass(orgProxy) + " is not a SessionProvider");
    }
  }


  private boolean isRetrying(TransactionRetryPolicy retryPolicy, String txName, int retryCount, Throwable ex) {
    if (retryPolicy != null) {
      TentackleRuntimeException temporaryException = ExceptionHelper.extractTemporaryException(false, ex);
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
