/*
 * Tentackle - http://www.tentackle.org.
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

package org.tentackle.model.impl;

import org.tentackle.common.Compare;
import org.tentackle.common.Constants;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.CodeFactory;
import org.tentackle.model.DataType;
import org.tentackle.model.Entity;
import org.tentackle.model.ModelElement;
import org.tentackle.model.ModelException;
import org.tentackle.model.NameVerifier;
import org.tentackle.model.Relation;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.parse.AttributeLine;
import org.tentackle.sql.Backend;
import org.tentackle.sql.SqlNameType;

import java.util.Objects;

/**
 * Attribute implementation.
 *
 * @author harald
 */
public class AttributeImpl implements Attribute, Comparable<AttributeImpl> {

  private final EntityFactoryImpl factory;
  private final Entity entity;
  private final SourceInfo sourceInfo;
  private final AttributeOptionsImpl options;
  private final boolean implicit;

  private AttributeLine sourceLine;
  private String javaName;
  private String columnName;
  private DataType dataType;
  private String applicationType;   // the application specific type
  private String innerName;
  private Integer size;
  private Integer scale;
  private Boolean nullable;   // overrides nullable
  private Relation relation;


  /**
   * Creates an attribute.
   *
   * @param factory the factory to create the attribute options
   * @param entity the entity this attribute belongs to
   * @param sourceInfo the source info
   * @param implicit true if implicit attribute
   */
  public AttributeImpl(EntityFactoryImpl factory, Entity entity, SourceInfo sourceInfo, boolean implicit) {
    this.factory = factory;
    this.entity = entity;
    this.sourceInfo = sourceInfo;
    this.implicit = implicit;
    options = factory.createAttributeOptions(this, sourceInfo);
  }

  @Override
  public SourceInfo getSourceInfo() {
    return sourceInfo;
  }

  @Override
  public ModelElement getParent() {
    return entity;
  }


  @Override
  public int hashCode() {
    int hash = 3;
    hash = 73 * hash + Objects.hashCode(this.entity);
    hash = 73 * hash + Objects.hashCode(this.javaName);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final AttributeImpl other = (AttributeImpl) obj;
    if (!Objects.equals(this.entity, other.entity)) {
      return false;
    }
    return Objects.equals(this.javaName, other.javaName);
  }

  @Override
  public int compareTo(AttributeImpl o) {
    if (o == null) {
      return Integer.MAX_VALUE;
    }
    int rv = Compare.compare((EntityImpl) entity, (EntityImpl) o.entity);
    if (rv == 0) {
      rv = Compare.compare(javaName, o.javaName);
    }
    return rv;
  }

  @Override
  public boolean isImplicit() {
    return implicit;
  }

  @Override
  public Entity getEntity() {
    return entity;
  }

  /**
   * Parses an attribute line.
   *
   * @param entity the entity
   * @param line the source line
   * @throws ModelException if parsing the model failed
   */
  public void parse(Entity entity, AttributeLine line) throws ModelException {
    setSourceLine(line);

    setColumnName(line.getColumnName());
    setName(line.getJavaName());
    setScale(line.getScale());
    setSize(line.getSize());
    setDataType(DataType.createFromJavaType(line.getJavaType()));
    if (getDataType() == null) {
      setDataType(DataType.APPLICATION);
    }
    setInnerName(line.getInnerName());
    if (getDataType() == DataType.APPLICATION) {
      // verify the valid inner type
      setApplicationType(line.getJavaType());
      getInnerType();
    }

    getOptions().applyEntityOptions(entity.getOptions(), getDataType());
    getOptions().setComment(line.getComment());

    for (String option: line.getOptions()) {
      if (!getOptions().processOption(option)) {
        if (option.startsWith("@")) {
          // treat as annotation
          getOptions().getAnnotations().add(option);
        }
        else {
          throw line.createModelException("illegal attribute option: " + option);
        }
      }
    }

    // replace SIZE by MAXCOL=size and/or SCALE=scale, if set
    if (getOptions().getBindOptions().contains(CommonOptionsImpl.BIND_SIZE)) {
      getOptions().removeBindOption(CommonOptionsImpl.BIND_SIZE);
      // size option set: replace by [MAXCOL=<size>][,SCALE=<scale] if not already defined in model
      if (getSize() != null && !getOptions().getBindOptions().contains(Constants.BIND_MAXCOL)) {
        getOptions().addBindOption(Constants.BIND_MAXCOL + "=" + getSize());
      }
      if (getScale() != null && !getOptions().getBindOptions().contains(Constants.BIND_SCALE)) {
        getOptions().addBindOption(Constants.BIND_SCALE + "=" + getScale());
      }
    }
  }


  /**
   * Sets the application specific type.<br>
   * Only allowed {@link DataType#APPLICATION}.
   *
   * @param applicationType the application specific type
   * @throws ModelException if this is not {@link DataType#APPLICATION}
   */
  public void setApplicationType(String applicationType) throws ModelException {
    if (applicationType != null && dataType != DataType.APPLICATION) {
      throw createModelException("setting the application specific type is not allowed for " + this);
    }
    this.applicationType = applicationType;
  }

  /**
   * Gets the application specific type.
   *
   * @return the application specific type, never null or empty
   * @throws ModelException if application specific type not set or this is not {@link DataType#APPLICATION}
   */
  @Override
  public String getApplicationType() throws ModelException {
    if (dataType == DataType.APPLICATION) {
      if (applicationType == null || applicationType.trim().length() == 0) {
        throw createModelException("application specific type not set");
      }
    }
    else  {
      throw createModelException("application specific type not supported for " + this);
    }
    return applicationType;
  }


  /**
   * Sets the inner type.
   *
   * @param innerType the inner type
   * @throws ModelException if this is neither {@link DataType#APPLICATION} nor {@link DataType#BINARY}
   */
  public void setInnerName(String innerType) throws ModelException {
    if (innerType != null && dataType != DataType.APPLICATION && dataType != DataType.BINARY) {
      throw createModelException("setting the inner type is not allowed for " + this);
    }
    this.innerName = innerType;
  }

  /**
   * Returns the inner type.
   *
   * @return the inner type, null if none (only if {@link DataType#BINARY})
   * @throws ModelException this is {@link DataType#APPLICATION} and inner type not set
   */
  @Override
  public String getInnerName() throws ModelException {
    if (dataType == DataType.APPLICATION && innerName == null) {
      throw createModelException("inner type not set for application-specific type '" + getApplicationType() + "'");
    }
    else if (dataType != DataType.APPLICATION && dataType != DataType.BINARY) {
      throw createModelException("inner type not supported for " + this);
    }
    return innerName;
  }


  /**
   * Gets the innertype for application specific types.
   *
   * @return the inner type to convert to agains the SQL backend
   * @throws ModelException if not {@link DataType#APPLICATION} or innertype not a legal DataType.
   */
  @Override
  public DataType getInnerType() throws ModelException {
    DataType innerType = DataType.createFromJavaType(getInnerName());
    if (innerType == null) {
      throw createModelException("illegal inner type: " + getInnerName());
    }
    return innerType;
  }


  @Override
  public DataType getEffectiveType() throws ModelException {
    if (dataType == DataType.APPLICATION) {
      return getInnerType();
    }
    return dataType;
  }


  /**
   * Gets the java type.<br>
   * Returns the java type with optional generic info.
   * <p>
   * Examples:
   * <pre>
   *  Date
   *  Binary&lt;Invoice&gt;
   *  MyType&lt;int&gt;
   * </pre>
   *
   * @return the type string
   * @throws ModelException if type is misconfigured
   */
  @Override
  public String getJavaType() throws ModelException {
    StringBuilder buf = new StringBuilder();
    if (dataType == DataType.APPLICATION) {
      buf.append(getApplicationType());
    }
    else  {
      buf.append(dataType.toString());
      if (dataType == DataType.BINARY) {
        String genType = getInnerName();
        if (genType != null) {
          buf.append('<');
          buf.append(genType);
          buf.append('>');
        }
      }
    }
    return buf.toString();
  }

  @Override
  public Relation getRelation() {
    return relation;
  }

  @Override
  public String getName() {
    return javaName;
  }

  @Override
  public String getColumnName() {
    return columnName;
  }

  @Override
  public DataType getDataType() {
    return dataType;
  }

  @Override
  public Integer getSize() {
    return size;
  }

  @Override
  public int getSizeWithDefault() {
    return size == null ? 0 : size;
  }

  @Override
  public Integer getScale() {
    return scale;
  }

  @Override
  public int getScaleWithDefault() {
    return scale == null ? 0 : scale;
  }


  @Override
  public AttributeOptionsImpl getOptions() {
    return options;
  }


  public void setColumnName(String columnName) {
    this.columnName = columnName;
  }

  public void setDataType(DataType dataType) {
    this.dataType = dataType;
  }

  public void setRelation(Relation relation) {
    this.relation = relation;
  }

  public void setName(String javaName) {
    this.javaName = javaName;
  }

  public void setScale(Integer scale) {
    this.scale = scale;
  }

  public void setSize(Integer size) {
    this.size = size;
  }

  @Override
  public boolean isNullable() throws ModelException {
    if (nullable != null) {
      return nullable;
    }
    DataType type = getEffectiveType();
    return !type.isPrimitive() && !getOptions().isMapNull();
  }

  /**
   * Overrides nullable feature.
   *
   * @param nullable null to determine from datatype
   */
  public void setNullable(Boolean nullable) {
    this.nullable = nullable;
  }


  @Override
  public String toString() {
    return getName();
  }


  @Override
  public void validate() throws ModelException {

    if (StringHelper.isAllWhitespace(getName())) {
      throw createModelException("missing Java name");
    }
    if (StringHelper.isReservedWord(getName())) {
      throw createModelException("'" + getName() + "' is a reserved Java keyword");
    }
    String diag = NameVerifier.getInstance().verifyAttributeName(this);
    if (diag != null) {
      throw createModelException(diag);
    }
    if (StringHelper.isAllWhitespace(getColumnName())) {
      throw createModelException("missing column name");
    }
    diag = NameVerifier.getInstance().verifyColumnName(this);
    if (diag != null) {
      throw createModelException(diag);
    }

    for (Backend backend: factory.getBackends()) {
      try {
        backend.assertValidName(SqlNameType.COLUMN_NAME, getColumnName());
      }
      catch (RuntimeException rex) {
        throw new ModelException(rex.getMessage(), this, rex);
      }
    }

    if (dataType == null) {
      throw createModelException("missing data type");
    }
    if (dataType == DataType.APPLICATION) {
      getInnerType();
    }
    if (getScale() != null && !dataType.isNumeric()) {
      throw createModelException("non-numeric type does not support scale");
    }

    getOptions().validate();

    if (getOptions().isUTC() && dataType != DataType.TIMESTAMP) {
      throw createModelException("UTC option not applicable to " + dataType);
    }

    if (getOptions().isWithTimezone() && !dataType.isDateOrTime()) {
      throw createModelException("TZ option not applicable to " + dataType);
    }

    if (getOptions().isMapNull()) {
      switch(dataType) {
        case CHARACTER:
        case STRING:
        case DATE:
        case TIMESTAMP:
        case LOCALDATE:
        case LOCALDATETIME:
          break;    // ok

        default:
          throw createModelException("MAPNULL option not applicable to " + dataType);
      }
    }
  }

  /**
   * Gets the source line.
   *
   * @return the line
   */
  public AttributeLine getSourceLine() {
    return sourceLine;
  }

  /**
   * Sets the source line.
   *
   * @param sourceLine the line
   */
  public void setSourceLine(AttributeLine sourceLine) {
    this.sourceLine = sourceLine;
  }


  /**
   * Creates a model exception.
   * <p>
   * Refers to the source line if set, otherwise just the message.
   *
   * @param message the message
   * @return the exception
   */
  public ModelException createModelException(String message) {
    ModelException ex;
    if (sourceLine != null) {
      ex = sourceLine.createModelException(message);
    }
    else  {
      ex = new ModelException(message, this);
    }
    return ex;
  }


  @Override
  public String getMethodNameSuffix() {
    return StringHelper.firstToUpper(javaName);
  }

  @Override
  public String getGetterName() {
    return CodeFactory.getInstance().createGetterName(this);
  }

  @Override
  public String getSetterName() {
    return CodeFactory.getInstance().createSetterName(this);
  }

  @Override
  public String getBindableAnnotation() {
    return CodeFactory.getInstance().createBindableAnnotation(this);
  }

  @Override
  public String toMethodArgument(String value) {
    return CodeFactory.getInstance().createMethodArgument(this, value);
  }

}
