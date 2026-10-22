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

package org.tentackle.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * Default implementation of an {@link ObjectSerializer} based on Java Object Serialization.
 * <p>
 * The objects must implement {@link java.io.Serializable}.
 *
 * @author harald
 */
// @Service does not work in tentackle-common, since the analyze goal of the tentackle-maven-plugin is not available at this build step yet.
// Needs explicit config in src/main/resources/META-INF/services.
@Service(ObjectSerializer.class)
public class DefaultObjectSerializer implements ObjectSerializer {

  /**
   * Creates the Java Object Serialization based serializer.
   */
  public DefaultObjectSerializer() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @Override
  public void serialize(Object object, OutputStream out) {
    try (ObjectOutputStream os = new ObjectOutputStream(out)) {
      os.writeObject(object);
      os.flush();
    }
    catch (IOException e) {
      throw new TentackleRuntimeException(e);
    }
  }

  @Override
  public Object deserialize(InputStream in) {
    try (ObjectInputStream is = new ObjectInputStream(in)) {
      return is.readObject();
    }
    catch (IOException | ClassNotFoundException e) {
      throw new TentackleRuntimeException(e);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T copy(T object) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    serialize(object, out);
    return (T) deserialize(new ByteArrayInputStream(out.toByteArray()));
  }

}
