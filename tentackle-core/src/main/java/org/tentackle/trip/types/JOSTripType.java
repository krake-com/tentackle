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

import org.tentackle.common.Settings;
import org.tentackle.trip.Deserializer;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.PreAuthenticationPolicy;
import org.tentackle.trip.Reference;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripFactory;
import org.tentackle.trip.TripPolicyViolationException;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.TripType;
import org.tentackle.trip.impl.DefaultOpCode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Wrapper type using Java Object Serialization (JOS).<br>
 * This is the last resort if the class cannot be serialized via TRIP,
 * and it is not possible to provide a {@link org.tentackle.trip.TripComponentProvider},
 * a {@link org.tentackle.reflect.CanonicalConstructor},
 * a {@link org.tentackle.trip.TripInstanceCreator}
 * or an application-specific {@link org.tentackle.trip.TripType},
 * for whatever reason.
 * <p>
 * The class of the object travels via TRIP ahead of its Java-serialized form. The receiver accepts it only
 * if it serializes that class via JOS itself, and the root object of the Java-serialized form must be
 * of that class. This is checked before any object of the stream is instantiated.
 * <p>
 * TRIP's allowlist does not reach into the Java-serialized form. Hence, it is deserialized through the
 * filter created by {@link org.tentackle.common.Settings#createDeserializationFilter(long)}, which
 * is all that applies to the objects nested within the root object.
 * Before the client is authenticated, i.e. if the stream has a {@link PreAuthenticationPolicy},
 * a Java-serialized object is refused altogether.
 */
// created by ObjectTypeBuilder, not mapped by @TripTypeService
public final class JOSTripType extends AbstractTripType<Object> {

  /**
   * The lazily initialized trip type for the announced class.<br>
   * It cannot be determined in the constructor, since this one is invoked by {@link DefaultOpCode}.
   */
  private static final class ClassTypeHolder {
    @SuppressWarnings("rawtypes")
    private static final TripType<Class> CLASS = TripFactory.getInstance().getType(Class.class);
  }


  /**
   * Creates the type.
   */
  public JOSTripType() {
    super(Object.class);
  }

  @Override
  public boolean isFinal() {
    return true;
  }

  @Override
  public boolean isReferencable(Object value) {
    return true;
  }

  @Override
  public void write(Object value, TripStream stream) {
    Serializer serializer = stream.getSerializer();
    serializer.serializeOpCode(DefaultOpCode.JOS);
    if (value == null) {
      sendNull(stream);
    }
    else {
      Reference<Object> reference = sendReference(stream, value);
      if (reference == null || reference.isObjectFollowing()) {
        // announce the class, so that the receiver can check it before Java Object Serialization takes over
        ClassTypeHolder.CLASS.write(value.getClass(), stream);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream os = new ObjectOutputStream(bos)) {
          os.writeObject(value);
          os.flush();
          serializer.serializeInt(bos.size());
          serializer.serialize(bos.toByteArray(), 0, bos.size());
        }
        catch (IOException iox) {
          throw new TripRuntimeException("cannot serialize object, " + value.getClass(), iox);
        }
      }
    }
  }

  @Override
  public Object read(TripStream stream, OpCode opCode) {
    PreAuthenticationPolicy policy = stream.getReadPolicy();
    if (policy != null) {
      // The JOS opcode is a fixed type that never passes through the dictionary and its allowlist.
      // Before the login, it would hand arbitrary bytes to ObjectInputStream: refuse it before reading anything.
      policy.violation("Java Object Serialization");    // throws if enforcing
    }

    Deserializer deserializer = stream.getDeserializer();

    if (opCode == DefaultOpCode.JOS) {
      opCode = stream.receiveOpCode();
      if (opCode == DefaultOpCode.NULL) {
        return null;
      }
    }

    Object value;
    Reference<Object> reference = receiveReference(stream, opCode);
    if (!reference.isObjectFollowing()) {
      value = reference.getObject();
      if (value == null) {
        throw new TripRuntimeException("reference " + reference + " has no proxy object");
      }
    }
    else {
      Class<?> clazz = ClassTypeHolder.CLASS.read(stream, stream.receiveOpCode());
      if (clazz == null) {
        throw new TripPolicyViolationException("missing class of Java-serialized object");
      }
      if (!(stream.getType(clazz) instanceof JOSTripType)) {
        throw new TripPolicyViolationException(clazz.getName() + " is not serialized via JOS");
      }
      int size = checkSize(stream, deserializer.deserializeInt());
      // read while the data arrives: the size is not trusted
      DeserializerInputStream data = new DeserializerInputStream(deserializer, size);
      Class<?>[] rejectedRoot = new Class<?>[1];
      try (ObjectInputStream is = new ObjectInputStream(data)) {
        // TRIP's allowlist does not reach into the JOS stream: apply the common deserialization filter
        ObjectInputFilter filter = Settings.createDeserializationFilter(size);
        is.setObjectInputFilter(info -> {
          Class<?> serialClass = info.serialClass();
          // Depth 1 is the root object: all of its class descriptors, the superclasses included, are read at
          // this depth, and before the object is instantiated. They must belong to the announced class.
          if (serialClass != null && info.depth() == 1 && !serialClass.isAssignableFrom(clazz)) {
            rejectedRoot[0] = serialClass;
            return ObjectInputFilter.Status.REJECTED;
          }
          return filter.checkInput(info);
        });
        value = is.readObject();
        if (!clazz.isInstance(value)) {
          throw new TripPolicyViolationException("Java-serialized object announced as " + clazz.getName() + ", got " +
                                                 (value == null ? "null" : value.getClass().getName()));
        }
        data.drain();   // nothing left, unless the object was written with trailing data
        stream.addReference(reference, value, false);
        reference.markReceived();
      }
      catch (InvalidClassException iox) {
        if (rejectedRoot[0] != null) {
          throw new TripPolicyViolationException("Java-serialized object announced as " + clazz.getName() +
                                                 " refused for root class " + rejectedRoot[0].getName());
        }
        throw new TripRuntimeException("cannot deserialize object for " + this, iox);
      }
      catch (IOException | ClassNotFoundException iox) {
        throw new TripRuntimeException("cannot deserialize object for " + this, iox);
      }
    }

    return value;
  }

}
