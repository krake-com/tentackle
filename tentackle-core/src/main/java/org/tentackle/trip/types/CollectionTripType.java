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
import org.tentackle.trip.TripComponentProvider;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripInstantiationException;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.DefaultOpCode;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.function.Consumer;


interface CollectionTypeHolder {
  TripType<Integer> INTEGER_TRIP_TYPE = TripFactory.getInstance().getType(Integer.TYPE);
}


/**
 * Trip type for {@link Collection}s.
 * <p>
 * Java collections contain internal delegates that cannot be instantiated directly.
 * Therefore, we serialize the collection's elements only. If collections explicitly
 * need additional fields to be serialized, a {@link TripComponentProvider} must
 * be created.
 * <p>
 * The collection is rebuilt through its sized (or no-args) constructor and refilled with the
 * elements. Collections providing neither, but a {@link org.tentackle.reflect.CanonicalConstructor}
 * instead, are created by that constructor once its arguments arrive. Since the collection cannot
 * exist before, its components precede the elements in the stream in this case, the other way round
 * otherwise. Either way the collection is created before its elements are read, so the elements may
 * back-reference the collection itself.
 *
 * @param <T> the collection type
 */
// created by CollectionTypeBuilder, not mapped by @TripTypeService
@SuppressWarnings("rawtypes")
public class CollectionTripType<T extends Collection> extends ObjectTripType<T> {

  private static final OpCode OP_CODE = DefaultOpCode.COLLECTION;

  private Constructor<T> sizedConstructor;
  private Constructor<T> noArgsConstructor;

  /**
   * Creates the type.
   *
   * @param clazz the collection's class
   */
  public CollectionTripType(Class<T> clazz) {
    super(clazz);
    if (isInstantiable() && !isDelayedConstruction()) {
      try {
        sizedConstructor = clazz.getDeclaredConstructor(Integer.TYPE);
      }
      catch (NoSuchMethodException e) {
        sizedConstructor = null;
        try {
          noArgsConstructor = clazz.getDeclaredConstructor();
        }
        catch (NoSuchMethodException ex) {
          throw new TripInstantiationException(clazz, "no sized, no-args or canonical constructor", ex);
        }
      }
    }
    // else: - interface or abstract. Since the concrete type is not known yet,
    //         it will be determined when serialized. Therefore, read() and write() won't be invoked.
    //       - or delayed construction, i.e., the collection is created by its canonical constructor or builder.
  }

  /**
   * Gets the sized constructor.
   *
   * @return the sized constructor, null if none
   */
  public Constructor<T> getSizedConstructor() {
    return sizedConstructor;
  }

  /**
   * Gets the no-args constructor.
   *
   * @return the no-args constructor, null if none
   */
  public Constructor<T> getNoArgsConstructor() {
    return noArgsConstructor;
  }

  @Override
  public void write(T value, TripStream stream) {
    stream.getSerializer().serializeOpCode(getOpCode());
    sendDictionary(stream);
    if (value == null) {
      sendNull(stream);
    }
    else {
      Reference<T> reference = sendReference(stream, value);
      if (reference == null || reference.isObjectFollowing()) {
        prepareWrite(value);
        writeHeader(value, stream);
        if (isDelayedConstruction()) {
          // the canonical constructor's arguments must arrive before the elements,
          // since the collection does not exist yet. Unconditional: the reader expects the end opcode.
          writeComponents(value, stream);
          writeElements(value, stream);
        }
        else {
          writeElements(value, stream);
          if (getComponents().length > 0) {
            // additional members (TripComponentProvider!)
            writeComponents(value, stream);
          }
        }
      }
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public T read(TripStream stream, OpCode opCode, Consumer<T> initializer) {
    DictionaryEntry entry = null;

    if (opCode == getOpCode()) {
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
          throw new TripRuntimeException("reference " + reference + " has no collection object");
        }
      }
    }

    if (readContent) {
      CollectionHeader<T> header = readHeader(stream, opCode);
      int size = header.size();
      value = header.value();

      if (isDelayedConstruction()) {
        // the collection does not exist yet: the canonical constructor creates it from the components,
        // applies the initializer and registers the reference (see ObjectTripType).
        value = readComponents(stream, stream.receiveOpCode(), entry, reference, value, initializer);
        readElements(stream, value, size);
      }
      else {
        if (initializer != null) {
          initializer.accept(value);
        }

        if (reference != null && !isConverted()) {
          // register before reading the elements, so that elements may back-reference the collection itself
          stream.addReference(reference, value, isDeduplicateable(value));
          reference.markReceived();
        }

        readElements(stream, value, size);

        if (getComponents().length > 0) {
          value = readComponents(stream, stream.receiveOpCode(), entry, reference, value, initializer);
        }
      }

      value = convert(value);
      validateRead(value);

      if (reference != null && isConverted()) {
        stream.addReference(reference, value, isDeduplicateable(value));
        reference.markReceived();
      }
    }
    return value;
  }

  /**
   * Writes the elements of the collection.
   *
   * @param value the collection instance
   * @param stream the stream to write to
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected void writeElements(T value, TripStream stream) {
    for (Object element : value) {
      if (element == null) {
        sendNull(stream);   // collection allows null elements, obviously
      }
      else {
        TripType elementType = stream.getType(element.getClass());
        elementType.write(element, stream);
      }
    }
  }

  /**
   * Reads the elements of the collection.
   * <p>
   * The next opcode is unread both on entry and on return, so that the elements can be read
   * before or after the components (see {@link #read(TripStream, OpCode, Consumer)}).
   *
   * @param stream the stream to read from
   * @param value the collection instance to fill
   * @param size the number of elements
   */
  @SuppressWarnings("unchecked")
  protected void readElements(TripStream stream, T value, int size) {
    if (size > 0) {
      OpCode opCode = stream.receiveOpCode();
      int elementCount = 0;
      while (elementCount < size) {
        if (opCode == DefaultOpCode.NULL) {
          value.add(null);  // collection allows null elements, obviously
        }
        else {
          TripType<?> elementType = stream.deriveType(opCode);
          if (elementType.isDictionaryBased()) {
            opCode = stream.receiveOpCode();
          }
          value.add(elementType.read(stream, opCode));
        }
        if (++elementCount < size) {
          // opcode for the next element
          opCode = stream.receiveOpCode();
        }
      }
    }
  }

  /**
   * Converts the collection.<br>
   * The default implementation does nothing.<br>
   * Provided for {@link ImmutableCollectionTripType}.
   *
   * @param value the collection read from the stream (not from reference!)
   * @return the converted collection
   */
  protected T convert(T value) {
    return value;
  }

  /**
   * Tells whether {@link #convert(Collection)} returns a different instance.<br>
   * Non-converted collections are registered as references before reading the elements,
   * so that elements may back-reference the collection itself. Converted collections
   * can only be registered after the conversion.
   *
   * @return true if the collection is converted
   */
  protected boolean isConverted() {
    return false;
  }

  @Override
  protected OpCode getOpCode() {
    return OP_CODE;
  }

  /**
   * Creates a new collection instance.<br>
   * Not invoked for delayed construction, i.e., if the collection is created by its
   * canonical constructor or builder.
   *
   * @param size the size of the collection
   * @return the collection instance
   */
  protected T createCollection(int size) {
    try {
      if (sizedConstructor != null) {
        // Some JDK collections reject a capacity of zero, such as the bounded queues
        // (ArrayBlockingQueue, LinkedBlockingQueue) and the priority queues.
        // Since the size is only a capacity hint, requesting one element instead is safe.
        return sizedConstructor.newInstance(Math.max(size, 1));
      }
      else {
        return noArgsConstructor.newInstance();
      }
    }
    catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new TripInstantiationException(getObjectClass(), "cannot instantiate collection", e);
    }
  }

  @Override
  protected T createInitializedDefault() {
    return null;    // delayed construction until the size is known
  }

  @Override
  protected boolean isFieldSerializable(Field field) {
    // The fields in the java collections must not be serialized, since the components are serialized instead.
    // Otherwise, we would get exceptions "module java.base does not open java.util to module org.tentackle.core".
    // Such a field can still be transported by a @CanonicalGetter, see ChainedProperties.
    return super.isFieldSerializable(field) && !field.getDeclaringClass().getName().startsWith("java.");
  }

  /**
   * Writes the collection header.
   *
   * @param value the collection instance
   * @param stream the stream to write to
   */
  protected void writeHeader(T value, TripStream stream) {
    CollectionTypeHolder.INTEGER_TRIP_TYPE.write(value.size(), stream);
  }

  /**
   * Returned by {@link #readHeader(TripStream, OpCode)}.
   *
   * @param size the size of the collection
   * @param value the collection instance, null for delayed construction
   * @param <T> the collection type
   */
  protected record CollectionHeader<T>(int size, T value) {}

  /**
   * Reads the collection header and creates a new collection instance.
   * <p>
   * Subclasses overriding this method must return a header with a null instance for
   * delayed construction, since the collection is created by its canonical constructor
   * or builder from the components that follow.
   *
   * @param stream the stream to read from
   * @param opCode the opcode to read (already read from the stream)
   * @return the collection header
   */
  protected CollectionHeader<T> readHeader(TripStream stream, OpCode opCode) {
    int size = checkSize(CollectionTypeHolder.INTEGER_TRIP_TYPE.read(stream, opCode));
    // the collection grows while its elements are read, so the size is only a capacity hint
    return new CollectionHeader<>(size, isDelayedConstruction() ? null : createCollection(limitPreAllocation(size)));
  }

}
