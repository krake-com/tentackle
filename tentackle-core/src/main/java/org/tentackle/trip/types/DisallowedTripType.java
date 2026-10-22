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

package org.tentackle.trip.types;

import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Reference;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Trip type for disallowed types.<br>
 * This type is used to programmatically prevent instances of certain classes to be deserialized.<br>
 * The application usually replaces {@link org.tentackle.trip.builder.ObjectTypeBuilder} with its own builder,
 * which checks for allowed classes and returns this type for all others.
 * <p>
 * Background:<br>
 * Deserialization of arbitrary objects may be harmful if the data source cannot be trusted.
 * For example, when serialized data is read from a database and not received through a secured
 * communication channel from a trusted application, the serialized data could have been modified
 * in a malicious way. Since the deserialization of objects usually works by setting the values
 * of the fields via reflection, it bypasses the constructors and thus all consistency checks.
 * Although this risk can be eliminated by defining a {@link org.tentackle.reflect.CanonicalConstructor}
 * for certain classes, it is not possible to cover all possible classes this way.<br>
 * For records, this problem does not exist since they are always created via their
 * canonical constructor.
 *
 * @param <T> the type
 * @see org.tentackle.trip.TripTypeBuilderFactory
 */
// no TripTypeService annotation since this type is created by builders only
public class DisallowedTripType<T> extends ObjectTripType<T> {

  /**
   * Creates the type.
   *
   * @param clazz the disallowed class
   */
  public DisallowedTripType(Class<T> clazz) {
    super(clazz);
  }

  @Override
  protected T readComponents(TripStream stream, OpCode opCode, DictionaryEntry entry,
                             Reference<T> reference, T value, Consumer<T> initializer,
                             Map<TripComponent<T, ?>, Object> componentValues) {

    throw new TripRuntimeException(getObjectClass() + " is not allowed to be deserialized");
  }

}
