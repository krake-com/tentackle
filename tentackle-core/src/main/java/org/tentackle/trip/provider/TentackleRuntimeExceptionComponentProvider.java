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

package org.tentackle.trip.provider;

import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripComponentProviderService;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripType;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Component provider for {@link TentackleRuntimeException}.
 *
 * @param <T> the tentackle runtime exception type
 */
@TripComponentProviderService(TentackleRuntimeException.class)
public class TentackleRuntimeExceptionComponentProvider<T extends TentackleRuntimeException> extends ThrowableComponentProvider<T> {

  /**
   * Creates the provider.
   */
  public TentackleRuntimeExceptionComponentProvider() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  protected boolean addField(Collection<TripComponent<T, ?>> components, Field field,
                             TripType<T> type, int ordinal) {
    if ("temporary".equals(field.getName())) {
      try {
        components.add(TripFactory.getInstance().createComponent(
          type, ordinal,
          type.getObjectClass().getMethod("isTemporary"),
          type.getObjectClass().getMethod("setTemporary", Boolean.TYPE), false, null));
        return true;
      }
      catch (NoSuchMethodException e) {
        throw new TripRuntimeException("isTemporary() method is not found in " + type.getObjectClass());
      }
    }
    return super.addField(components, field, type, ordinal);
  }

}
