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

import org.tentackle.common.Service;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.Transport;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.DefaultTripFactory;

import java.lang.reflect.InvocationHandler;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Extended TRIP factory aware of database-related remote delegates.
 * <p>
 * Driver-specific {@link SQLException}s are transferred as their nearest superclass provided by the JDK,
 * since the client usually doesn't have the JDBC driver on its classpath.
 * See also {@link SQLExceptionComponentProvider} for the message and cause.
 */
@Service(TripFactory.class)
public class DbTripFactory extends DefaultTripFactory {

  /**
   * Creates the factory.
   */
  public DbTripFactory() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public InvocationHandler createInvocationHandler(UUID remoteId, Transport transport, Class<?>... clazz) {
    for (Class<?> iFace : clazz) {
      if (RemoteDbDelegate.class.isAssignableFrom(iFace)) {
        return new DbRemoteInvocationHandler(remoteId, transport);
      }
    }
    return super.createInvocationHandler(remoteId, transport, clazz);
  }

  @Override
  public <T> TripType<T> getType(Class<? extends T> clazz) {
    if (SQLException.class.isAssignableFrom(clazz)) {
      clazz = getTransferredClass(clazz);
    }
    return super.getType(clazz);
  }


  /**
   * Gets the class an {@link SQLException} is transferred as.<br>
   * Returns the nearest superclass provided by the JDK, for example, {@link SQLException} for
   * {@code org.postgresql.util.PSQLException}.
   *
   * @param clazz the exception class
   * @return the class to transfer
   * @param <T> the type
   */
  @SuppressWarnings("unchecked")
  protected <T> Class<? extends T> getTransferredClass(Class<? extends T> clazz) {
    Class<?> transferredClass = clazz;
    while (!ReflectionHelper.isJdkClass(transferredClass)) {
      transferredClass = transferredClass.getSuperclass();
    }
    return (Class<? extends T>) transferredClass;
  }

}
