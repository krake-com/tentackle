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

import org.tentackle.trip.Deserializer;
import org.tentackle.trip.OpCode;
import org.tentackle.trip.Reference;
import org.tentackle.trip.Serializer;
import org.tentackle.trip.TripRuntimeException;
import org.tentackle.trip.TripStream;
import org.tentackle.trip.impl.DefaultOpCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
 */
// created by ObjectTypeBuilder, not mapped by @TripTypeService
public final class JOSTripType extends AbstractTripType<Object> {

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
    Deserializer deserializer = stream.getDeserializer();

    if (opCode == DefaultOpCode.JOS) {
      opCode = deserializer.deserializeOpCode();
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
      int size = deserializer.deserializeInt();
      byte[] data = new byte[size];
      deserializer.deserialize(data, 0, size);
      try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(data))) {
        value = is.readObject();
        stream.addReference(reference, value, false);
        reference.markReceived();
      }
      catch (IOException | ClassNotFoundException iox) {
        throw new TripRuntimeException("cannot deserialize object for " + this, iox);
      }
    }

    return value;
  }

}
