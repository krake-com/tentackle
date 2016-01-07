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


package org.tentackle.pdo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.tentackle.log.Logger;
import org.tentackle.log.LoggerFactory;
import org.tentackle.reflect.AbstractInterceptor;
import org.tentackle.reflect.EffectiveClassProvider;
import org.tentackle.reflect.ReflectionHelper;

/**
 * Implementation for the transaction interception.
 *
 * @author harald
 */
public class TransactionInterceptor extends AbstractInterceptor {

  /**
   * logger for this class.
   */
  private static final Logger LOGGER = LoggerFactory.getLogger(TransactionInterceptor.class);


  private String transactionName;   // the transaction name from the annotation

  @Override
  public void setAnnotation(Annotation annotation) {
    super.setAnnotation(annotation);
    transactionName = ((Transaction) annotation).value();
  }

  @Override
  public Object proceed(Object proxy, Method method, Object[] args,
                        Object orgProxy, Method orgMethod, Object[] orgArgs) throws Throwable {

    if (orgProxy instanceof SessionProvider) {
      Session session = ((SessionProvider) orgProxy).getSession();
      String txName = transactionName != null ?
                      transactionName :
                      (ReflectionHelper.getClassBaseName(method.getDeclaringClass()) + "#" + method.getName());
      long txVoucher = session.begin(txName);
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
          session.rollback(txVoucher);
        }
        catch (RuntimeException rex) {
          LOGGER.severe("rollback failed", rex);
        }
        if (txVoucher != 0) {
          LOGGER.fine("transaction {0} rolled back on {1} with voucher {2}", txName, session, txVoucher);
        }
        throw ex;
      }
    }
    else  {
      throw new PersistenceException(EffectiveClassProvider.getEffectiveClass(orgProxy) +
                                     " is not a SessionProvider");
    }
  }

}
