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

import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripComponentProviderService;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;
import org.tentackle.trip.provider.ThrowableComponentProvider;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.Collection;

/**
 * Component provider for {@link SQLException}.
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

}
