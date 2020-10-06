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

package org.tentackle.sql;

import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.sql.datatypes.ConvertibleType;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Service(DataTypeFactory.class)
public class DefaultDataTypeFactory implements DataTypeFactory {

  private static class Key {
    private final String javaType;
    private final String variant;

    private Key(String javaType, String variant) {
      this.javaType = Objects.requireNonNull(javaType);
      this.variant = Objects.requireNonNull(variant);
    }

    private Key(DataType<?> dataType) {
      this(dataType.getJavaType(), dataType.getVariant());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Key key = (Key) o;

      if (!javaType.equals(key.javaType)) return false;
      return variant.equals(key.variant);
    }

    @Override
    public int hashCode() {
      int result = javaType.hashCode();
      result = 31 * result + variant.hashCode();
      return result;
    }

    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder();
      if (!variant.isEmpty()) {
        buf.append(variant).append(' ');
      }
      buf.append(javaType);
      return buf.toString();
    }
  }




  private final Map<Key, DataType<?>> dataTypeMap;
  private final ConvertibleType convertibleType;


  /**
   * Creates the datatype factory.
   */
  @SuppressWarnings("rawtypes")
  public DefaultDataTypeFactory() {
    dataTypeMap = new HashMap<>();
    try {
      for (Class<DataType> dataTypeClass: ServiceFactory.getServiceFinder().findServiceProviders(DataType.class)) {
        DataType<?> dataType = dataTypeClass.getDeclaredConstructor().newInstance();
        dataTypeMap.putIfAbsent(new Key(dataType), dataType);   // allows overriding by application (comes first in META-INF)
      }
      convertibleType = (ConvertibleType) get(CONVERTIBLE_TYPE);
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
        InvocationTargetException | NoSuchMethodException nfe) {
      throw new BackendException("supported datatypes could not be determined", nfe);
    }
  }

  @Override
  public DataType<?> get(String name, String variant) {
    return dataTypeMap.get(new Key(name, variant));
  }

  @Override
  public ConvertibleType getConvertibleType() {
    return convertibleType;
  }

}
