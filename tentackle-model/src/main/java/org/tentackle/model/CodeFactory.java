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
 *
 */

package org.tentackle.model;

import org.tentackle.common.Constants;
import org.tentackle.common.Service;
import org.tentackle.common.ServiceFactory;
import org.tentackle.common.StringHelper;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataType;
import org.tentackle.sql.SqlType;

import java.util.List;

interface CodeFactoryHolder {
  CodeFactory INSTANCE = ServiceFactory.createService(CodeFactory.class, CodeFactory.class);
}


/**
 * Factory for code snippets used by generators such as wurblets.
 */
@Service(CodeFactory.class)    // defaults to self
public class CodeFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  public static CodeFactory getInstance() {
    return CodeFactoryHolder.INSTANCE;
  }


  /**
   * Creates source code for the @Bindable-annotation.
   *
   * @param attribute the attribute
   * @return the java code, null if no binding
   */
  public String createBindableAnnotation(Attribute attribute) {
    if (attribute.getOptions().isBindable()) {
      String bindOptions = attribute.getOptions().getBindOptions();
      if (StringHelper.isAllWhitespace(bindOptions)) {
        return "@Bindable";
      }
      else {
        return "@Bindable(options=\"" + bindOptions + "\")";
      }
    }
    return null;
  }

  /**
   * Creates the source code for a method argument.
   *
   * @param attribute the attribute
   * @param value the argument name
   * @return the java code
   */
  public String createMethodArgument(Attribute attribute, String value) throws ModelException {
    DataType<?> dataType = attribute.getEffectiveDataType();
    if (dataType.isDowncastNecessary()) {
      String downCast = "(" + dataType.getJavaType() + ")";
      if (!value.startsWith(downCast)) {
        value = downCast + " " + value;
      }
    }
    return value;
  }

  /**
   * Creates the name of the getter for an attribute.
   *
   * @param attribute the attribute
   * @return the getter name
   */
  public String createGetterName(Attribute attribute) {
    StringBuilder buf = new StringBuilder();
    if (attribute.getDataType().isBool()) {
      buf.append("is");
    }
    else  {
      buf.append("get");
    }
    buf.append(attribute.getMethodNameSuffix());
    return buf.toString();
  }

  /**
   * Creates the name of the setter for an attribute.
   *
   * @param attribute the attribute
   * @return the setter name
   */
  public String createSetterName(Attribute attribute) {
    return "set" + attribute.getMethodNameSuffix();
  }

  /**
   * Creates the name of the getter for a relation.
   *
   * @param relation the relation
   * @return the getter name
   */
  public String createGetterName(Relation relation) {
    return "get" + relation.getMethodNameSuffix();
  }

  /**
   * Creates the name of the setter for a relation.
   *
   * @param relation the relation
   * @return the setter name
   */
  public String createSetterName(Relation relation) {
    return "set" + relation.getMethodNameSuffix();
  }

  /**
   * Creates the declared type of a relation.
   *
   * @param relation the relation
   * @param withinForeignEntity true if we need the type from within the foreign entity,
   *                            else from within the relation's entity
   * @return the declared java type
   */
  public String createDeclaredJavaType(Relation relation, boolean withinForeignEntity) {
    String type = relation.getClassName();
    if (relation.getForeignEntity().isAbstract()) {
      if (withinForeignEntity) {
        type = "T";
      }
      else {
        type += "<?>";
      }
    }
    if (relation.getRelationType() == RelationType.LIST && !relation.isReversed()) {
      return (relation.isTracked() ? "TrackedList" : "List") + "<" + type + ">";
    }
    else {
      return type;
    }
  }

  /**
   * Creates the java type of a relation.
   *
   * @param relation the relation
   * @return the java type
   */
  public String createJavaType(Relation relation) {
    if (relation.getRelationType() == RelationType.LIST && !relation.isReversed()) {
      return (relation.isTracked() ? "TrackedArrayList" : "ArrayList") + "<>";
    }
    else {
      return relation.getClassName();
    }
  }


  /**
   * Creates the table creation sql code.
   *
   * @param entity the entity
   * @param backend the backend to create sql code for
   * @return the SQL code
   * @throws ModelException if model inconsistent
   */
  @SuppressWarnings({ "rawtypes", "unchecked" })
  public String createSqlTable(Entity entity, Backend backend) throws ModelException {
    StringBuilder buf = new StringBuilder();
    buf.append(backend.sqlCreateTableIntro(entity.getTableName(), entity.getOptions().getComment()));

    List<Attribute> tableAttributes = entity.getTableAttributes();
    int attributeNum = tableAttributes.size();
    int attributeCount = 1;

    for (Attribute attribute: tableAttributes) {
      DataType dataType = attribute.getEffectiveDataType();
      for (int columnIndex = 0; columnIndex < dataType.getColumnCount(); columnIndex++) {
        buf.append("    ");
        String columnName = attribute.getColumnName(columnIndex);
        int size = dataType.getSize(backend, columnIndex, attribute.getSize());
        int scale = dataType.getScale(backend, columnIndex, attribute.getScale());
        SqlType sqlType = dataType.getSqlType(backend, columnIndex);
        String comment = attribute.getOptions().getComment();
        if (comment != null && !comment.isEmpty()) {
          comment += dataType.getCommentSuffix(columnIndex).orElse("");
        }
        Object defaultValue = dataType.getColumnValue(columnIndex, attribute.getOptions().getDefaultValue());
        buf.append(backend.sqlCreateColumn(
            columnName, comment, dataType.getSqlType(backend, columnIndex), size, scale,
            attribute.isNullable(), defaultValue,
            attribute.getName().equals(Constants.AN_ID) && !entity.getOptions().isNoPrimaryKey(),
            attributeCount < attributeNum || columnIndex < dataType.getColumnCount() - 1));
      }
      attributeCount++;
    }
    buf.append(backend.sqlCreateTableClosing(entity.getTableName(), entity.getOptions().getComment()));
    buf.append(backend.sqlCreateTableComment(entity.getTableName(), entity.getOptions().getComment()));
    for (Attribute attribute: tableAttributes) {
      DataType dataType = attribute.getEffectiveDataType();
      for (int columnIndex = 0; columnIndex < dataType.getColumnCount(); columnIndex++) {
        String columnName = attribute.getColumnName(columnIndex);
        String comment = attribute.getOptions().getComment();
        if (comment != null && !comment.isEmpty()) {
          comment += dataType.getCommentSuffix(columnIndex).orElse("");
        }
        buf.append(backend.sqlCreateColumnComment(entity.getTableName(), columnName, comment));
      }
    }
    return buf.toString();
  }

  /**
   * Creates the SQL code to create an index.
   *
   * @param backend the database backend
   * @param entity the entity
   * @param index the index
   * @return the SQL code
   */
  public String createSqlIndex(Backend backend, Entity entity, Index index) {
    StringBuilder buf = new StringBuilder();

    if (index.getComment() != null) {
      buf.append(backend.getSingleLineComment()).append(' ');
      buf.append(index.getComment());
      buf.append('\n');
    }
    String[] columnNames = new String[index.getAttributes().size()];
    int attributeCount = 0;
    for (IndexAttribute indexAttribute : index.getAttributes()) {
      String indexPart;
      if (indexAttribute.getFunctionName() != null) {
        indexPart = indexAttribute.getFunctionName() + "(" + indexAttribute.getAttribute().getColumnName() + ")";
      }
      else {
        indexPart = indexAttribute.getAttribute().getColumnName();
      }
      if (indexAttribute.isDescending()) {
        columnNames[attributeCount] = "-" + indexPart;
      }
      else {
        columnNames[attributeCount] = indexPart;
      }
      attributeCount++;
    }
    buf.append(backend.sqlCreateIndex(entity.getTableName(), index.createDatabaseIndexName(entity),
                                      index.isUnique(), index.getFilterCondition(), columnNames));

    return buf.toString();
  }

  /**
   * Creates the foreign key creation sql code.
   *
   * @param foreignKey the foreign key
   * @param backend the backend to create sql code for
   * @return the SQL code
   */
  public String createSqlForeignKey(ForeignKey foreignKey, Backend backend) {
    return backend.sqlCreateForeignKey(
        foreignKey.getReferencingTableProvidingEntity().getTableName(),
        foreignKey.getReferencingAttribute().getColumnName(),
        foreignKey.getReferencedTableProvidingEntity().getTableName(),
        foreignKey.getReferencedAttribute().getColumnName(),
        foreignKey.getReferencingEntity().getTableAlias() + "_" + foreignKey.getReferencingAttribute().getColumnName() + "_fkey",
        foreignKey.isComposite() && foreignKey.getReferencingEntity().getIntegrity().isCompositesCheckedByDatabase());
  }

}
