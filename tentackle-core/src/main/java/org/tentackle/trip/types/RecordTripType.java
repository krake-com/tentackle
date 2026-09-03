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

import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.trip.DictionaryEntry;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Reference;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripInstantiationException;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripTypeCategory;
import org.tentackle.trip.impl.DefaultOpCode;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Fallback for records w/o a dedicated trip type.<br>
 * This is the core type for the majority of records.<br>
 * Since records are immutable, all their components must be
 * known before a record can be instantiated.
 * For the same reason, there are no component defaults, and
 * records cannot have a back reference to a parent record.
 * Furthermore, records are final and all records are de-duplicatable unless
 * explicitly marked as not de-duplicatable via {@link org.tentackle.trip.TripDeduplicatable}.
 * <p>
 * The serialization format is as follows:
 * <ul>
 *   <li>The opcode {@link DefaultOpCode#RECORD}.</li>
 *   <li>The dictionary entry if new for this stream.<br>
 *       Otherwise, the ordinal according to the dictionary.</li>
 *   <li>Followed by one of:
 *     <ul>
 *        <li>The null code if the record is null.</li>
 *        <li>The reference, if the same or equal record was already sent.</li>
 *        <li>The reference plus for each component:
 *          <ul>
 *            <li>The component's ordinal, according to the dictionary, if not simply the next.</li>
 *            <li>The component's serialization according to its type.</li>
 *          </ul>
 *          </li>The opcode {@link DefaultOpCode#END}</li>
 *        </li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * Due to the restrictions of records, the components of the receiver's and sender's record
 * declaration must be identical, except for the order of components.
 * <p>
 * Application-specific record types should extend this class and annotate
 * it with @{@link org.tentackle.trip.TripTypeService}.
 * <p>
 * Please notice that back-references to records from within one of their components,
 * directly or indirectly, are not supported and will result in a "missing reference" exception
 * during deserialization.
 * We don't support this because records are immutable and meant to represent data structures
 * forming a regular directed graph.
 * For example, creating a record with an array of records as one of its components and later
 * setting an array element to point to this created record is considered uncommon programming practice
 * and should be avoided. If this cannot be avoided, use regular classes instead.
 *
 * @param <T> the type
 */
// created by a RecordTypeBuilder, not mapped by @TripTypeService
public class RecordTripType<T extends Record> extends AbstractDictionaryBasedTripType<T> {

  private final TripComponent<T, ?>[] components;
  private final Constructor<T> canonicalConstructor;

  /**
   * Creates the type.
   *
   * @param clazz the record's class
   */
  public RecordTripType(Class<T> clazz) {
    super(clazz, TripTypeCategory.RECORD);
    components = createComponents();
    canonicalConstructor = ReflectionHelper.getCanonicalConstructor(clazz);
  }

  @Override
  public boolean isDeduplicateable(T value) {
    if (deduplicationEnabled) {
      if (deduplicatableMethod == null) {
        // all records are de-duplicatable, except when explicitly marked as not de-duplicatable
        return !Boolean.FALSE.equals(deduplicatable);
      }
      try {
        return (Boolean) deduplicatableMethod.invoke(value);
      }
      catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
        throw new TripRuntimeException("cannot invoke deduplicateable method", e);
      }
    }
    return false;
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void write(T value, TripStream stream) {
    stream.getSerializer().serializeOpCode(DefaultOpCode.RECORD);
    sendDictionary(stream);
    if (value == null) {
      sendNull(stream);
    }
    else {
      Reference<T> reference = sendReference(stream, value);
      if (reference == null || reference.isObjectFollowing()) {
        prepareWrite(value);
        int lastOrdinal = -1;
        for (TripComponent<T, ?> component : getComponents()) {
          // A record component never has a default value, so this always writes the ordinal and
          // the value. Delegating lets a primitive component stay unboxed, see RecordTripComponent.
          lastOrdinal = component.write(value, stream, lastOrdinal);
        }
        sendEnd(stream);
      }
    }
  }

  @Override
  @SuppressWarnings("rawtypes")
  public T read(TripStream stream, OpCode opCode) {
    DictionaryEntry entry = null;

    if (opCode == DefaultOpCode.RECORD) {
      // determine the dictionary entry from the stream, if provided
      opCode = stream.receiveOpCode();
      if (opCode == DefaultOpCode.DICTIONARY || opCode.isDictionaryOrdinal()) {
        entry = stream.receiveDictionary(opCode);
        opCode = stream.receiveOpCode();
      }

      if (opCode == DefaultOpCode.NULL) {
        return null;
      }
    }
    // else: invoked as a component of another object (see ObjectTripType#readComponents)

    if (entry == null) {
      // invoked as a component of another object.
      // this trip type is already the runtime-type!
      entry = stream.getDictionaryEntry(getObjectClass());
    }

    T value = null;
    boolean readContent = true;
    Reference<T> reference = null;
    if (opCode.isReference()) {
      reference = receiveReference(stream, opCode);
      readContent = reference.isObjectFollowing();
      if (readContent) {
        opCode = stream.receiveOpCode();    // fetch the next opcode for loop below
      }
      else {
        value = reference.getObject();
        if (value == null) {
          throw new TripRuntimeException("reference " + reference + " has no record object");
        }
      }
    }

    if (readContent) {
      Object[] constructorArgs = new Object[components.length];

      int lastOrdinal = -1;
      while (opCode != DefaultOpCode.END) {
        int ordinal;
        if (opCode.isComponentOrdinal()) {
          lastOrdinal = ordinal = stream.readNextComponentOrdinal(lastOrdinal, opCode);   // component ordinal in the dictionary
          opCode = stream.receiveOpCode();
        }
        else {
          ordinal = ++lastOrdinal;
        }
        TripComponent component = entry.getTypeComponent(ordinal);
        if (component == null) {
          throw new TripRuntimeException(getObjectClass() + " does not provide the component " +
                                         entry.getComponent(ordinal).getComponentName());
        }
        // the canonical constructor takes its arguments as objects, so a primitive component
        // has to be boxed here no matter what
        constructorArgs[component.getOrdinal()] = component.readValue(stream, opCode);
        // next component
        opCode = stream.receiveOpCode();
      }

      try {
        value = canonicalConstructor.newInstance(constructorArgs);
      }
      catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
        throw new TripInstantiationException(getObjectClass(), "cannot instantiate record", e);
      }

      if (reference != null) {
        stream.addReference(reference, value, isDeduplicateable(value));
        reference.markReceived();
      }

      validateRead(value);
    }
    return value;
  }

  @Override
  public TripComponent<T, ?>[] getComponents() {
    return components;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private TripComponent<T, ?>[] createComponents() {
    Collection<TripComponent<T, ?>> tripComponents = new ArrayList<>();
    int ordinal = 0;
    for (RecordComponent recordComponent : getObjectClass().getRecordComponents()) {
      tripComponents.add(TripFactory.getInstance().createComponent(this, ordinal++, recordComponent));
    }
    return tripComponents.toArray(new TripComponent[0]);
  }

}
