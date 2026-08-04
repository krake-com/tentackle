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

import org.tentackle.trip.OpCode;
import org.tentackle.trip.Reference;
import org.tentackle.trip.TripComponent;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.TripTypeCategory;
import org.tentackle.trip.impl.DefaultOpCode;

import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Base class for all trip types.
 *
 * @param <T> the type
 */
public abstract class AbstractTripType<T> implements TripType<T> {

  /**
   * Global flag to enable/disable deduplication.<br>
   * Deduplication is enabled by default and should not be disabled in most cases.
   * Provided for testing purposes only.
   */
  public static boolean deduplicationEnabled = true;

  /**
   * Upper bound for pre-allocating from a size read off the stream.<br>
   * The container still grows to whatever the stream really delivers, so this only prevents a
   * corrupted size from allocating gigabytes before the first element was even read.
   */
  private static final int MAX_PRE_ALLOCATED_SIZE = 4096;

  @SuppressWarnings("rawtypes")
  private static final TripComponent[] NO_COMPONENTS = new TripComponent[0];
  private static final TripTypeCategory[] NO_CATEGORIES = new TripTypeCategory[0];

  private final Class<T> clazz;
  private final boolean isFinal;
  private final boolean isInstantiable;
  private final TripTypeCategory category;

  /**
   * Creates the type.
   *
   * @param clazz the object's class
   * @param expectedCategories the expected categories, none if it's not to be checked
   */
  public AbstractTripType(Class<T> clazz, TripTypeCategory... expectedCategories) {
    this.clazz = clazz;

    if (clazz.isPrimitive()) {
      category = TripTypeCategory.PRIMITIVE;
      isInstantiable = false;
      isFinal = true;
    }
    else if (clazz.isEnum()) {
      category = TripTypeCategory.ENUM;
      isInstantiable = false;
      isFinal = true;
    }
    else if (clazz.isRecord()) {
      category = TripTypeCategory.RECORD;
      isInstantiable = true;
      isFinal = true;
    }
    else if (clazz.isInterface()) {
      category = TripTypeCategory.INTERFACE;
      isInstantiable = false;
      isFinal = false;
    }
    else if (clazz.isArray()) {
      category = TripTypeCategory.ARRAY;
      isInstantiable = true;
      // The component type is not final!<br>
      // It is perfectly ok to write: "Object[] array = new Blah[10]".
      isFinal = false;    // see AbstractArrayTripType#isFinal()
    }
    else if (Proxy.isProxyClass(clazz)) {
      category = TripTypeCategory.PROXY;
      isInstantiable = true;
      isFinal = true;
    }
    else {
      category = TripTypeCategory.CLASS;
      isFinal = Modifier.isFinal(clazz.getModifiers());
      isInstantiable = !Modifier.isAbstract(clazz.getModifiers());
    }

    if (expectedCategories != null && expectedCategories.length > 0) {
      for (TripTypeCategory expectedCategory : expectedCategories) {
        if (category == expectedCategory) {
          return;
        }
      }
      throw new TripRuntimeException("unexpected category: " + category);
    }
  }

  /**
   * Creates the type.
   *
   * @param clazz the object's class
   */
  public AbstractTripType(Class<T> clazz) {
    this(clazz, NO_CATEGORIES);
  }

  @Override
  public boolean isReferencable(T value) {
    return false;
  }

  @Override
  public boolean isDeduplicateable(T value) {
    return false;
  }

  @Override
  public boolean isFinal() {
    return isFinal;
  }

  public boolean isInstantiable() {
    return isInstantiable;
  }

  @Override
  public boolean isDictionaryBased() {
    return false;
  }

  @Override
  public Class<T> getObjectClass() {
    return clazz;
  }

  @Override
  public TripTypeCategory getCategory() {
    return category;
  }

  @Override
  @SuppressWarnings("unchecked")
  public TripComponent<T, ?>[] getComponents() {
    return NO_COMPONENTS;
  }

  @Override
  public T read(TripStream stream, OpCode opCode) {
    return read(stream, opCode, null);
  }

  @Override
  public T read(TripStream stream, OpCode opCode, Consumer<T> initializer) {
    if (opCode == DefaultOpCode.NULL) {
      if (getCategory() == TripTypeCategory.PRIMITIVE) {
        throw new TripRuntimeException("cannot convert null to primitive " + this);
      }
      return null;
    }
    throw new TripRuntimeException("illegal opcode " + opCode + " for component " + this);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(clazz.getName()).append("/").append(category);
    TripComponent<T, ?>[] components = getComponents();
    if (components != null) {
      buf.append('[').append(components.length).append(']');
    }
    return buf.toString();
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) return true;
    if (object == null || getClass() != object.getClass()) return false;

    AbstractTripType<?> that = (AbstractTripType<?>) object;
    return clazz.equals(that.clazz);
  }

  @Override
  public int hashCode() {
    return clazz.hashCode();
  }


  /**
   * Validates a size or length read from the stream.
   *
   * @param size the size read from the stream
   * @return the size, never negative
   * @throws TripRuntimeException if the size is negative
   */
  protected int checkSize(int size) {
    if (size < 0) {
      throw new TripRuntimeException("negative size " + size + " for " + this);
    }
    return size;
  }

  /**
   * Limits a size read from the stream to what may be pre-allocated for it.
   * <p>
   * Containers that grow while their elements are read must not be sized by the stream: a
   * corrupted size would allocate far more than the stream can ever fill. The container reaches
   * its real size as the elements arrive, and a size larger than the remaining data ends up as a
   * closed stream instead of an {@link OutOfMemoryError}.
   *
   * @param size the size read from the stream
   * @return the capacity to pre-allocate, never negative
   * @throws TripRuntimeException if the size is negative
   */
  protected int limitPreAllocation(int size) {
    return Math.min(checkSize(size), MAX_PRE_ALLOCATED_SIZE);
  }

  /**
   * Sends the null opcode to the stream.
   *
   * @param stream the stream to write to
   */
  protected void sendNull(TripStream stream) {
    stream.getSerializer().serializeOpCode(DefaultOpCode.NULL);
  }

  /**
   * Sends the end opcode to the stream.
   *
   * @param stream the stream to write to
   */
  protected void sendEnd(TripStream stream) {
    stream.getSerializer().serializeOpCode(DefaultOpCode.END);
  }

  /**
   * Sends the reference to the stream, if necessary.<br>
   * If a reference is returned, the trip type must check {@link Reference#isObjectFollowing()} to see if the
   * value's contents must be serialized once.
   *
   * @param stream the stream to write to
   * @param value the value to create a reference for
   * @return the reference, or null if the value is not referencable or de-duplicatable
   */
  protected Reference<T> sendReference(TripStream stream, T value) {
    Reference<T> reference;
    boolean deduplicate = isDeduplicateable(value);
    if (isReferencable(value) || deduplicate) {
      reference = stream.getReference(value, deduplicate);
      reference.send(stream);
    }
    else {
      reference = null;
    }
    return reference;
  }

  /**
   * Reads the reference from the stream.<br>
   * If the reference is already seen, the reference will hold the referenced object.
   * Otherwise, the object must be read from the stream.
   *
   * @param stream the stream to read from
   * @param opCode the opcode to read (already read from the stream)
   * @return the reference
   */
  protected Reference<T> receiveReference(TripStream stream, OpCode opCode) {
    Reference<T> reference = Reference.read(stream, opCode);
    if (reference.isSent()) {
      // already seen -> take from stream cache
      reference = stream.getReference(clazz, reference.getReferenceId());
    }
    // else: must be read from the stream
    return reference;
  }

  /**
   * Updates the dictionary entry if this type's object class is new for the stream
   * and sends the class definition.<br>
   * Otherwise, sends the ordinal of the dictionary entry.
   *
   * @param stream the stream to write to
   */
  protected void sendDictionary(TripStream stream) {
    stream.sendDictionary(getObjectClass());
  }

}
