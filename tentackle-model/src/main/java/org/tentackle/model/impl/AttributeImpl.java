/*
 * Tentackle - https://tentackle.org.
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
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.model.AccessScope;
import org.tentackle.model.Attribute;
import org.tentackle.model.CodeFactory;
import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelElement;
import org.tentackle.model.ModelException;
import org.tentackle.model.NameVerifier;
import org.tentackle.model.Relation;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.parse.AttributeLine;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;
import org.tentackle.sql.SqlNameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Attribute implementation.
 *
 * @author harald
 */
public class AttributeImpl implements Attribute, Comparable<AttributeImpl>, Cloneable {

  private final EntityFactoryImpl factory;
  private final Entity entity;
  private final SourceInfo sourceInfo;
  private final boolean implicit;

  private List<Entity> embeddingPath;
  private AttributeOptionsImpl options;
  private AttributeLine sourceLine;
  private int ordinal;
  private String javaName;
  private String pathName;
  private String columnName;
  private DataType<?> dataType;
  private String applicationTypeName;
  private String innerTypeName;
  private DataType<?> innerType;
  private Integer size;
  private Integer scale;
  private Boolean nullable;
  private Relation relation;


  /**
   * Creates an attribute.
   *
   * @param factory the factory to create the attribute options
   * @param entity the entity this attribute belongs to
   * @param sourceInfo the source info
   * @param implicit true if implicit attribute
   * @param options the attribute's options, null to create from source info
   */
  public AttributeImpl(EntityFactoryImpl factory, Entity entity, SourceInfo sourceInfo, boolean implicit, AttributeOptionsImpl options) {
    this.factory = factory;
    this.entity = entity;
    this.sourceInfo = sourceInfo;
    this.implicit = implicit;
    this.options = options == null ? factory.createAttributeOptions(this, sourceInfo) : options;
  }

  /**
   * Creates an attribute.
   *
   * @param factory the factory to create the attribute options
   * @param entity the entity this attribute belongs to
   * @param sourceInfo the source info
   * @param implicit true if implicit attribute
   */
  public AttributeImpl(EntityFactoryImpl factory, Entity entity, SourceInfo sourceInfo, boolean implicit) {
    this(factory, entity, sourceInfo, implicit, null);
  }

  @Override
  public Model getModel() {
    return factory.getModel();
  }

  @Override
  public Attribute createEmbedded(Entity embeddingEntity, String pathName, String columnName) {
    AttributeImpl attribute = clone();
    if (attribute.embeddingPath == null) {
      attribute.embeddingPath = new ArrayList<>();
    }
    attribute.embeddingPath.add(embeddingEntity);
    attribute.options = options.clone(attribute);   // clone with updated reference to cloned attribute
    attribute.pathName = pathName;
    attribute.columnName = columnName;
    return attribute;
  }


  /**
   * Clones an attribute.<br>
   * The method is protected, since {@link #createEmbedded(Entity, String, String)} should be used instead,
   * which invokes {@code clone()}.
   *
   * @return the cloned attribute
   */
  @Override
  protected AttributeImpl clone() {
    try {
      return (AttributeImpl) super.clone();
    }
    catch (CloneNotSupportedException cnx) {
      throw new TentackleRuntimeException(cnx);   // should not happen, but...
    }
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
    hash = 73 * hash + Objects.hashCode(entity);
    hash = 73 * hash + Objects.hashCode(getPathName());
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
    return Objects.equals(this.getPathName(), other.getPathName());
  }

  @Override
  public int compareTo(AttributeImpl o) {
    int rv = Compare.compare((EntityImpl) entity, (EntityImpl) o.entity);
    if (rv == 0) {
      rv = Compare.compare(getPathName(), o.getPathName());
    }
    return rv;
  }

  @Override
  public boolean isImplicit() {
    return implicit;
  }

  @Override
  public boolean isHidden() {
    return options.isHidden() || options.isFromSuper() ||
           options.getAccessScope() != AccessScope.PUBLIC;
  }

  @Override
  public Entity getEntity() {
    return entity;
  }

  @Override
  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(int ordinal) {
    this.ordinal = ordinal;
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

    try {
      DataType<?> dataType = DataTypeFactory.getInstance().get(line.getJavaType());
      if (dataType == null) {
        if ("SQLXML".equals(line.getJavaType())) {
          // SQLXML is not a DataType's javaType of its own: it selects the SQLXML variant of String,
          // bridged through the JDBC SQLXML interface to a native XML column where supported.
          setDataType(DataTypeFactory.getInstance().get("String", DataTypeFactory.SQLXML_VARIANT));
        }
        // maybe the special Convertible type (see org.tentackle.misc.Convertible)
        else if (line.getInnerType() != null) {
          setDataType(DataTypeFactory.getInstance().getConvertibleType());
          setInnerTypeName(line.getInnerType());
          setApplicationTypeName(line.getJavaType());
        }
        else {
          throw line.createModelException("unknown type " + line.getJavaType());
        }
      }
      else {
        setDataType(dataType);
        if (line.getInnerType() != null) {
          setInnerTypeName(line.getInnerType());
        }
      }

      if ("String".equals(line.getJavaType()) && Integer.valueOf(0).equals(line.getSize())) {
        // special handling for LARGESTRING: String with explicit size of 0
        setDataType(DataTypeFactory.getInstance().get(line.getJavaType(), DataTypeFactory.LARGE_VARIANT));
      }

      if (isConvertible()) {
        // verify the valid inner type
        setApplicationTypeName(line.getJavaType());
        getInnerTypeName();
      }

      getOptions().applyEntityOptions(entity.getOptions(), getDataType());
      getOptions().setComment(line.getComment());

      for (String option : line.getOptions()) {
        if (!getOptions().processOption(option)) {
          if (option.startsWith("@")) {
            if (!StringHelper.isAllWhitespace(option.substring(1))) {
              // treat as annotation
              getOptions().getAnnotations().add(option);
            }
          }
          else if (option.startsWith("#")) {
            String stereotype = option.substring(1);
            if (!StringHelper.isAllWhitespace(stereotype)) {
              getOptions().getStereotypes().add(stereotype);
            }
          }
          else {
            throw line.createModelException("illegal attribute option: " + option);
          }
        }
      }

      // replace SIZE by MAXCOLS=size and/or SCALE=scale, if set
      if (getOptions().getBindOptions().contains(CommonOptionsImpl.BIND_SIZE)) {
        getOptions().removeBindOption(CommonOptionsImpl.BIND_SIZE);
        // size option set: replace by [MAXCOLS=<size>][,SCALE=<scale] if not already defined in model
        // <=0 is ignored, as it is not a valid size value anyway and String(0) is used to represent a large string.
        if (getSize() != null && getSize() > 0 && !getOptions().getBindOptions().contains(Constants.BIND_MAXCOLS)) {
          getOptions().addBindOption(Constants.BIND_MAXCOLS + "=" + getSize());
        }
        if (getScale() != null && !getOptions().getBindOptions().contains(Constants.BIND_SCALE)) {
          getOptions().addBindOption(Constants.BIND_SCALE + "=" + getScale());
        }
      }
    }
    catch (RuntimeException rx) {
      throw line.createModelException("syntax error", rx);
    }
  }


  @Override
  public boolean isConvertible() {
     return getDataType() == DataTypeFactory.getInstance().getConvertibleType();
  }

  @Override
  public boolean isEmbedded() {
    return entity.isEmbedded();
  }

  @Override
  public Entity getEmbeddingEntity() {
    return embeddingPath == null ? null : embeddingPath.getLast();
  }

  @Override
  public List<Entity> getEmbeddingPath() {
    return embeddingPath;
  }

  @Override
  public String getPathName() {
    return pathName != null ? pathName : javaName;
  }


  /**
   * Sets the application-specific type if Convertible.
   *
   * @param applicationTypeName the name of the application-specific type
   */
  public void setApplicationTypeName(String applicationTypeName) {
    this.applicationTypeName = applicationTypeName;
  }

  @Override
  public String getApplicationTypeName() throws ModelException {
    if (isConvertible()) {
      if (StringHelper.isAllWhitespace(applicationTypeName)) {
        throw createModelException("application-specific type not set");
      }
    }
    else  {
      throw createModelException("application-specific type not supported for " + dataType);
    }
    return applicationTypeName;
  }


  /**
   * Sets the inner type name.
   *
   * @param innerTypeName the name of the inner type
   * @throws ModelException if datatype is Convertible, and the inner type is not supported or inner type not applicable to datatype
   */
  public void setInnerTypeName(String innerTypeName) throws ModelException {
    if (!dataType.isModelProvidingInnerType()) {
      throw createModelException("type " + dataType + " does not support inner types");
    }
    if (isConvertible()) {
      innerType = DataTypeFactory.getInstance().get(innerTypeName);
      if (innerType == null) {
        throw createModelException("inner type " + innerTypeName + " not supported for type " + dataType);
      }
    }
    this.innerTypeName = innerTypeName;
    // else: just a generic type name
  }

  @Override
  public String getInnerTypeName() throws ModelException {
    if (!dataType.isModelProvidingInnerType()) {
      throw createModelException("inner type not supported for type " + dataType);
    }
    if (StringHelper.isAllWhitespace(innerTypeName)) {
      throw createModelException("inner type not set for type " + dataType);
    }
    return innerTypeName;
  }

  @Override
  public DataType<?> getInnerDataType() throws ModelException {
    if (innerType == null) {
      throw createModelException("not a Convertible type");
    }
    return innerType;
  }

  @Override
  public DataType<?> getEffectiveDataType() throws ModelException {
    return isConvertible() ? getInnerDataType() : getDataType();
  }

  @Override
  public String getJavaType() throws ModelException {
    StringBuilder buf = new StringBuilder();
    if (isConvertible()) {
      buf.append(getApplicationTypeName());
    }
    else  {
      buf.append(dataType.getJavaType());
      if (dataType.isJavaTypeGenerified()) {
        String genType = getInnerTypeName();
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
  public String getColumnName(Backend backend, int columnIndex) throws ModelException {
    DataType<?> effectiveDataType = getEffectiveDataType();
    if (effectiveDataType.isColumnCountBackendSpecific() && backend == null) {
      throw new ModelException("backend-specific type " + effectiveDataType + " has no known column names");
    }
    return getColumnName() + getEffectiveDataType().getColumnSuffix(backend, columnIndex).orElse("");
  }

  @Override
  public DataType<?> getDataType() {
    return dataType;
  }

  @Override
  public Integer getSize() {
    return size;
  }

  @Override
  public Integer getScale() {
    return scale;
  }

  @Override
  public AttributeOptionsImpl getOptions() {
    return options;
  }


  public void setColumnName(String columnName) {
    this.columnName = columnName;
  }

  public void setDataType(DataType<?> dataType) {
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
    DataType<?> type = getEffectiveDataType();
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
    return getPathName();
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
    if (dataType.isModelProvidingInnerType() && StringHelper.isAllWhitespace(getInnerTypeName())) {
      throw createModelException("missing inner type");
    }
    if (getScale() != null && !dataType.isNumeric()) {
      throw createModelException("non-numeric type does not support scale");
    }

    getOptions().validate();

    if (getOptions().isUTC() && !dataType.isUTCSupported()) {
      throw createModelException("UTC option not applicable to " + dataType);
    }

    if (getOptions().isWithTimezone() && !dataType.isTimezoneApplicable()) {
      throw createModelException("TZ option not applicable to " + dataType);
    }

    if (getOptions().isMapNull() && !dataType.isMapNullSupported()) {
      throw createModelException("MAPNULL option not applicable to " + dataType);
    }

    if (getOptions().isMute() && dataType.isPrimitive() && getOptions().getDefaultValue() == null) {
      throw createModelException("MUTE only applicable to nullable columns or columns with a default value");
    }

    if (isEmbedded()) {
      if (getOptions().isContextId()) {
        throw createModelException("CONTEXT option not allowed for embedded attributes");
      }
      if (getOptions().isDomainKey()) {
        throw createModelException("KEY option not allowed for embedded attributes");
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
  public String toMethodArgument(String value) throws ModelException {
    return CodeFactory.getInstance().createMethodArgument(this, value);
  }

}
