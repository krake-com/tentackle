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

/**
 * Default implementation of {@link DataTypeFactory}.
 */
@Service(DataTypeFactory.class)
public class DefaultDataTypeFactory implements DataTypeFactory {

  private static class Key {
    private final String type;
    private final String variant;

    private Key(String type, String variant) {
      this.type = Objects.requireNonNull(type);
      this.variant = Objects.requireNonNull(variant);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Key key = (Key) o;

      if (!type.equals(key.type)) return false;
      return variant.equals(key.variant);
    }

    @Override
    public int hashCode() {
      int result = type.hashCode();
      result = 31 * result + variant.hashCode();
      return result;
    }

    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder();
      if (!variant.isEmpty()) {
        buf.append(variant).append(' ');
      }
      buf.append(type);
      return buf.toString();
    }
  }




  private final Map<Key, DataType<?>> javaTypeMap;      // java type + variant
  private final Map<Key, DataType<?>> modelTypeMap;     // model type w/o variant (e.g., to look up JSON, XML)
  private final ConvertibleType convertibleType;


  /**
   * Creates the datatype factory.
   */
  @SuppressWarnings("rawtypes")
  public DefaultDataTypeFactory() {
    javaTypeMap = new HashMap<>();
    modelTypeMap = new HashMap<>();
    try {
      for (Class<DataType> dataTypeClass: ServiceFactory.getServiceFinder().findServiceProviders(DataType.class)) {
        DataType<?> dataType = dataTypeClass.getDeclaredConstructor().newInstance();
        // allows overriding by application (comes first in META-INF)
        javaTypeMap.putIfAbsent(new Key(dataType.getJavaType(), dataType.getVariant()), dataType);
        modelTypeMap.putIfAbsent(new Key(dataType.getModelType(), ""), dataType);
      }
      convertibleType = (ConvertibleType) get(ConvertibleType.TYPE); // speeds lookup
      if (convertibleType == null) {
        throw new BackendException("Convertible type could not be determined");
      }
    }
    catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
        InvocationTargetException | NoSuchMethodException nfe) {
      throw new BackendException("supported data types could not be determined", nfe);
    }
  }

  @Override
  public DataType<?> get(String name, String variant) {
    DataType<?> dataType = javaTypeMap.get(new Key(name, variant));
    if (dataType == null && variant.isEmpty()) {
      dataType = modelTypeMap.get(new Key(name, ""));
    }
    return dataType;
  }

  @Override
  public ConvertibleType getConvertibleType() {
    return convertibleType;
  }

}
