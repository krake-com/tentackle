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

package org.tentackle.persist.wurblet;

import org.wurbelizer.wurbel.WurbelException;

import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.ModelException;
import org.tentackle.sql.DataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Generates the table- and field declarations for the query DSL.
 * <p>
 * The declarations are plain constants of {@code org.tentackle.dbms.dsl.Table} and
 * {@code org.tentackle.dbms.dsl.Field}, derived from the model:
 * <pre>
 *   private static final Table         CUSTOMER = DSL.table("customer", "cu");
 *   private static final Field&lt;Long&gt;   ID       = CUSTOMER.field("id", Long.class);
 *   private static final Field&lt;String&gt; NAME     = CUSTOMER.field("name", String.class);
 * </pre>
 * The Java type of a field is the one of its <em>effective</em> {@link DataType}, i.e. the type the
 * persistence layer reads and writes: the non-primitive type for primitives (values in a query are
 * objects) and the external type for {@code Convertible}s.
 *
 * @see DSL
 */
public class DslDeclarationFactory {

  /**
   * A generated declaration.
   *
   * @param comment the javadoc comment, without the surrounding tags and the trailing period
   * @param code the java code, without indentation
   */
  public record Declaration(String comment, String code) { }


  private final Entity entity;              // the entity to generate the declarations for
  private String visibility;                // the visibility of the generated constants
  private String constantPrefix;            // the prefix of the field constants
  private String tableConstant;             // the name of the table constant
  private String tableAlias;                // the table alias, null for none


  /**
   * Creates the factory.
   *
   * @param entity the entity
   * @throws WurbelException if the entity does not map to a table of its own
   */
  public DslDeclarationFactory(Entity entity) throws WurbelException {
    this.entity = Objects.requireNonNull(entity, "entity must be provided");

    if (entity.isEmbedded()) {
      throw new WurbelException(entity + " is embedded and thus has no table to select from");
    }
    if (entity.isAbstract() && entity.getHierarchyInheritanceType().isMappingToNoTable()) {
      throw new WurbelException(entity + " does not map to any table and is not persistable");
    }

    visibility = "private";
    constantPrefix = "";
    tableConstant = toConstantName(entity.getName());
    tableAlias = entity.getTableAlias();
  }


  /**
   * Sets the visibility of the generated constants.
   *
   * @param visibility the visibility, {@code "private"} by default
   */
  public void setVisibility(String visibility) {
    this.visibility = visibility;
  }

  /**
   * Sets the prefix of the field constants.<br>
   * Useful if the declarations of more than one entity are generated into the same class,
   * as a join query needs them.
   *
   * @param constantPrefix the prefix, empty by default
   */
  public void setConstantPrefix(String constantPrefix) {
    this.constantPrefix = constantPrefix;
  }

  /**
   * Sets the name of the table constant.
   *
   * @param tableConstant the constant name, the uppercased entity name by default
   */
  public void setTableConstant(String tableConstant) {
    this.tableConstant = tableConstant;
  }

  /**
   * Gets the name of the table constant.
   *
   * @return the constant name
   */
  public String getTableConstant() {
    return tableConstant;
  }

  /**
   * Sets the table alias the columns are qualified with.
   *
   * @param tableAlias the alias, null for none, the model's alias by default
   */
  public void setTableAlias(String tableAlias) {
    this.tableAlias = tableAlias;
  }

  /**
   * Gets the default prefix for the field constants.
   *
   * @return the uppercased entity name followed by an underscore
   */
  public String getDefaultConstantPrefix() {
    return toConstantName(entity.getName()) + "_";
  }


  /**
   * Determines the attributes to generate the fields for.
   *
   * @param javaNames the java names of the attributes, empty for all attributes mapped by the entity
   * @return the attributes
   * @throws WurbelException if there is no such attribute or it is stored in another table
   */
  public List<Attribute> determineAttributes(List<String> javaNames) throws WurbelException {
    List<Attribute> attributes = new ArrayList<>();

    if (javaNames.isEmpty()) {
      try {
        attributes.addAll(entity.getMappedAttributes());
      }
      catch (ModelException mex) {
        throw new WurbelException("cannot determine the mapped attributes of " + entity, mex);
      }
    }
    else {
      for (String javaName : javaNames) {
        Attribute attribute = entity.getAttributeByJavaName(javaName, true);
        if (attribute == null) {
          throw new WurbelException("no such attribute '" + javaName + "' in " + entity);
        }
        assertSameTable(attribute);
        attributes.add(attribute);
      }
    }

    return attributes;
  }

  /**
   * Creates the declaration of the table.
   *
   * @return the declaration
   */
  public Declaration createTableDeclaration() {
    StringBuilder code = new StringBuilder();
    code.append(visibility).append(" static final Table ").append(tableConstant)
        .append(" = DSL.table(").append(StringHelper.toDoubleQuotes(entity.getTableName()));
    if (tableAlias != null && !tableAlias.isEmpty()) {
      code.append(", ").append(StringHelper.toDoubleQuotes(tableAlias));
    }
    code.append(");");

    return new Declaration("the query DSL table of entity " + entity.getName(), code.toString());
  }

  /**
   * Creates the declaration of the field for an attribute.
   *
   * @param attribute the attribute
   * @return the declaration
   * @throws WurbelException if the attribute's datatype could not be determined
   */
  public Declaration createFieldDeclaration(Attribute attribute) throws WurbelException {
    DataType<?> dataType;
    String columnName;
    try {
      dataType = attribute.getEffectiveDataType();
      columnName = attribute.getColumnName();
    }
    catch (ModelException mex) {
      throw new WurbelException("cannot determine the column of " + attribute, mex);
    }

    String javaType;
    String arguments = StringHelper.toDoubleQuotes(columnName);
    String comment = "the query DSL field for '" + attribute.getName() + "', column '" + columnName + "'";

    if (dataType.isJavaTypeGenerified()) {
      // there is no class literal for a generic type such as Binary<String> -> untyped field
      javaType = "Object";
      comment += " (untyped, since " + dataType.getJavaType() + " is generic)";
    }
    else {
      javaType = dataType.isPrimitive() ? dataType.toNonPrimitive().getJavaType() : dataType.getJavaType();
      if (dataType.getVariant().isEmpty()) {
        arguments += ", " + javaType + ".class";
      }
      else {
        // a variant such as the large string cannot be determined from the class alone
        arguments += ", DataTypeFactory.getInstance().get(" + javaType + ".class, " +
                     StringHelper.toDoubleQuotes(dataType.getVariant()) + ")";
      }
    }

    String code = visibility + " static final Field<" + javaType + "> " +
                  constantPrefix + toConstantName(attribute.getName()) +
                  " = " + tableConstant + ".field(" + arguments + ");";

    return new Declaration(comment, code);
  }


  /**
   * Asserts that the attribute's column belongs to the entity's table.
   *
   * @param attribute the attribute
   * @throws WurbelException if the attribute is stored in another table
   */
  private void assertSameTable(Attribute attribute) throws WurbelException {
    Entity attributeEntity = attribute.getEntity();
    if (attributeEntity != null && !entity.getTableName().equalsIgnoreCase(attributeEntity.getTableName())) {
      throw new WurbelException("attribute '" + attribute.getName() + "' is stored in table '" +
                                attributeEntity.getTableName() + "', not in '" + entity.getTableName() +
                                "': generate a separate block for " + attributeEntity);
    }
  }

  /**
   * Converts a java name to the name of a constant.
   *
   * @param javaName the java name
   * @return the constant name
   */
  private String toConstantName(String javaName) {
    return javaName.toUpperCase(Locale.ROOT);
  }

}
