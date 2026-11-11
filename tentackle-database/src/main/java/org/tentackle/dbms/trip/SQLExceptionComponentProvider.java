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

import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripComponentProviderService;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.MethodTripComponent;
import org.tentackle.trip.provider.ThrowableComponentProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.Collection;

/**
 * Component provider for {@link SQLException}.
 * <p>
 * Since the client usually doesn't have the JDBC driver on its classpath, no driver-specific class is transferred:
 * <ul>
 *   <li>a driver-specific SQL exception is transferred as its nearest JDK superclass (see {@link DbTripFactory}),
 *   with the driver's class name appended to the message</li>
 *   <li>a driver-specific cause, which is not an SQL exception, is replaced by an {@link Exception}
 *   carrying the original class name, message and stack trace</li>
 * </ul>
 *
 * @see SQLExceptionInstanceCreator
 */
@TripComponentProviderService(SQLException.class)
public class SQLExceptionComponentProvider extends ThrowableComponentProvider<SQLException> {

  /**
   * Creates the provider.
   */
  public SQLExceptionComponentProvider() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public TripComponent<SQLException, ?>[] getComponents(TripType<SQLException> type) {
    TripComponent<SQLException, ?>[] components = super.getComponents(type);
    for (int i = 0; i < components.length; i++) {
      if (components[i] instanceof MethodTripComponent component) {
        switch (component.getName()) {
          case "message" -> components[i] = new MessageComponent(type, component.getOrdinal(), component.getGetter());
          case "cause" -> components[i] = new CauseComponent(type, component.getOrdinal(), component.getGetter());
          default -> { }
        }
      }
    }
    return components;
  }

  /**
   * Gets the cause to be transferred.<br>
   * SQL exceptions and the JDK's exceptions are transferred as they are.
   * Any other exception, usually specific to the JDBC driver, is replaced by an {@link Exception}
   * carrying the original's {@link Throwable#toString()} as its message and its stack trace.
   *
   * @param cause the cause
   * @return the cause to transfer
   */
  protected static Throwable getTransferredCause(Throwable cause) {
    if (cause == null || cause instanceof SQLException || ReflectionHelper.isJdkClass(cause.getClass())) {
      return cause;
    }
    Exception transferredCause = new Exception(cause.toString(), getTransferredCause(cause.getCause()));
    transferredCause.setStackTrace(cause.getStackTrace());
    return transferredCause;
  }

  @Override
  protected boolean addField(Collection<TripComponent<SQLException, ?>> components, Field field,
                             TripType<SQLException> type, int ordinal) {
    try {
      switch (field.getName()) {
        case "message":
          components.add(TripFactory.getInstance().createComponent(
            type, ordinal, type.getObjectClass().getMethod("getMessage"), null,false, null));
          return true;

        case "vendorCode":
          components.add(TripFactory.getInstance().createComponent(
            type, ordinal, type.getObjectClass().getMethod("getErrorCode"), null,false, null));
          return true;

        case "SQLState":
          components.add(TripFactory.getInstance().createComponent(
            type, ordinal, type.getObjectClass().getMethod("getSQLState"), null,false, null));
          return true;

        case "longUpdateCounts":    // BatchUpdateException
          components.add(TripFactory.getInstance().createComponent(
            type, ordinal, type.getObjectClass().getMethod("getLargeUpdateCounts"), null,false, null));
          return true;
        case "next":
          components.add(TripFactory.getInstance().createComponent(
            type, ordinal, type.getObjectClass().getMethod("getNextException"), null,false, null));
          return true;

        default:
          return super.addField(components, field, type, ordinal);
      }
    }
    catch (NoSuchMethodException e) {
      throw new TripRuntimeException("method is not found in " + type.getObjectClass(), e);
    }
  }



  /**
   * The message with the class name of a driver-specific SQL exception appended.
   */
  private static class MessageComponent extends MethodTripComponent<SQLException, String> {

    private MessageComponent(TripType<SQLException> parentType, int ordinal, Method getter) {
      super(parentType, ordinal, getter, null, false, null);
    }

    @Override
    public String getValue(SQLException parent) {
      String message = super.getValue(parent);
      if (!ReflectionHelper.isJdkClass(parent.getClass())) {
        String className = parent.getClass().getName();
        message = message == null ? className : message + " (" + className + ")";
      }
      return message;
    }
  }

  /**
   * The cause, see {@link #getTransferredCause(Throwable)}.
   */
  private static class CauseComponent extends MethodTripComponent<SQLException, Throwable> {

    private CauseComponent(TripType<SQLException> parentType, int ordinal, Method getter) {
      super(parentType, ordinal, getter, null, false, null);
    }

    @Override
    public Throwable getValue(SQLException parent) {
      return getTransferredCause(super.getValue(parent));
    }
  }

}
