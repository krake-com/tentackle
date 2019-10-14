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

import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.CodeFactory;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.IndexAttribute;
import org.tentackle.model.ModelElement;
import org.tentackle.model.ModelException;
import org.tentackle.model.NameVerifier;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.parse.ConfigurationLine;
import org.tentackle.sql.Backend;
import org.tentackle.sql.SqlNameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

/**
 * Index descriptor.
 * <p>
 * An index line is of the form:
 * <pre>
 *   [unique] index &lt;name&gt; := part1[ part2 ...]
 * </pre>
 *
 * where {@code part} is:
 * <pre>
 *   [+|-]&lt;column&gt; | &lt;function&gt;(&lt;column&gt;) [asc|desc]
 * </pre>
 *
 * where:
 * <ul>
 *   <li>{@code name}: is the index name (the table-name will be prepended, separated by an underscore)</li>
 *   <li>{@code column}: is either the database column-name of the entity attribute name</li>
 *   <li>{@code function}: is a function name</li>
 *   <li>{@code ASC} or {@code +} for ascending order (default)</li>
 *   <li>{@code DESC} or {@code -} for descending order</li>
 * </ul>
 *
 * Examples:
 * <pre>
 *   unique index udk := code
 *   index locid := location_id desc name
 *   index ucmt := -upper(comment)
 * </pre>
 *
 * @author harald
 */
public class IndexImpl implements Index {

  private final Entity entity;
  private final EntityFactoryImpl factory;
  private final SourceInfo sourceInfo;
  private final List<IndexAttribute> attributes;
  private ConfigurationLine sourceLine;
  private int ordinal;
  private String name;
  private String comment;
  private boolean unique;
  private String filterCondition;
  private boolean parsed;


  /**
   * Creates an index.
   *
   * @param factory the factory to create indexes
   * @param entity the entity this index belongs to
   * @param sourceInfo the source info
   */
  public IndexImpl(EntityFactoryImpl factory, Entity entity, SourceInfo sourceInfo) {
    this.entity = entity;
    this.sourceInfo = sourceInfo;
    this.factory = factory;
    attributes = new ArrayList<>();
  }

  @Override
  public Entity getEntity() {
    return entity;
  }

  @Override
  public SourceInfo getSourceInfo() {
    return sourceInfo;
  }

  @Override
  public ModelElement getParent() {
    return getEntity();
  }

  @Override
  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(int ordinal) {
    this.ordinal = ordinal;
  }

  /**
   * Parses a configuration line.
   *
   * @param entity the entity
   * @throws ModelException if parsing the model failed
   */
  public void parse(Entity entity) throws ModelException {

    parsed = true;

    // parse the index
    StringTokenizer stok = new StringTokenizer(sourceLine.getKey());
    boolean indexFound = false;
    boolean uniqueFound = false;
    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();
      switch (token.toLowerCase()) {

        case "index":
          setUnique(uniqueFound);
          indexFound = true;
          uniqueFound = false;
          break;

        case "unique":
          if (indexFound) {
            throw sourceLine.createModelException("keyword 'unique' not allowed after 'index'");
          }
          uniqueFound = true;
          break;

        default:
          if (!indexFound) {
            throw sourceLine.createModelException("unknown configuration keyword: " + token);
          }
          int ndx = token.indexOf('.');
          if (ndx > 0) {
            // cut leading schema name
            token = token.substring(ndx + 1);
          }
          setName(token);
      }
    }
    if (!indexFound) {
      throw sourceLine.createModelException("illegal configuration: " + sourceLine.getKey());
    }

    // parse the attributes
    if (entity.getAttributes().isEmpty()) {
      throw sourceLine.createModelException("index configuration line not allowed before attribute section");
    }

    String text = sourceLine.getValue();
    int ndx = text.indexOf('|');
    if (ndx > 0) {
      setFilterCondition(text.substring(ndx + 1));
      text = text.substring(0, ndx);
    }
    else {
      ndx = text.toUpperCase().indexOf("WHERE");
      if (ndx > 0) {
        setFilterCondition(text.substring(ndx + 5));
        text = text.substring(0, ndx);
      }
    }

    stok = new StringTokenizer(text, " \t,");
    IndexAttributeImpl indexAttribute = null;

    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();
      if (token.equalsIgnoreCase("desc")) {
        if (indexAttribute == null) {
          throw sourceLine.createModelException("keyword 'desc' must follow column name");
        }
        indexAttribute.setDescending(true);
      }
      else if (token.equalsIgnoreCase("asc")) {
        if (indexAttribute == null) {
          throw sourceLine.createModelException("keyword 'asc' must follow column name");
        }
        indexAttribute.setDescending(false);
      }
      else  {
        if (indexAttribute != null) {
          getAttributes().add(indexAttribute);
          indexAttribute = null;
        }
        // short form for desc or asc is + or - prepended to the column name
        Boolean descending = null;
        if (token.startsWith("+")) {
          descending = Boolean.FALSE;
          token = token.substring(1);
        }
        if (token.startsWith("-")) {
          descending = Boolean.TRUE;
          token = token.substring(1);
        }

        // check if functional index
        String function = null;
        String column = token;
        int ndx1 = token.indexOf('(');
        if (ndx1 >= 0) {
          int ndx2 = token.indexOf(')', ndx1);
          if (ndx2 >= 0) {
            function = token.substring(0, ndx1).trim().toUpperCase();
            column = token.substring(ndx1 + 1, ndx2).trim().toLowerCase();
          }
        }

        // check if column name exists
        for (Attribute attribute: entity.getAttributes()) {
          if (attribute.getColumnName().equals(column) ||
              attribute.getName().equals(column)) {
            // found
            indexAttribute = factory.createIndexAttribute(this);
            indexAttribute.setAttribute(attribute);
            indexAttribute.setFunctionName(function);
            if (descending != null) {
              indexAttribute.setDescending(descending);
            }
            break;
          }
        }

        if (indexAttribute == null) {
          // not found
          throw sourceLine.createModelException("undefined column name: " + token);
        }
      }
    }
    if (indexAttribute != null) {
      getAttributes().add(indexAttribute);
    }
  }



  @Override
  public String getName() {
    return name;
  }

  @Override
  public String createDatabaseIndexName(Entity entity) {
    return entity.getTableNameWithoutSchema() + "_" + name;
  }

  @Override
  public String getComment() {
    return comment;
  }

  @Override
  public boolean isUnique() {
    return unique;
  }

  @Override
  public String getFilterCondition() {
    return filterCondition;
  }

  @Override
  public List<IndexAttribute> getAttributes() {
    return attributes;
  }


  public void setName(String name) {
    this.name = name;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public void setUnique(boolean unique) {
    this.unique = unique;
  }

  public void setFilterCondition(String filterCondition) {
    if (StringHelper.isAllWhitespace(filterCondition)) {
      this.filterCondition = null;
    }
    else  {
      this.filterCondition = filterCondition.trim();
    }
  }

  /**
   * Sets the index to be parsed.<br>
   * Only necessary for model converters if the properties are set explicitly to prevent parsing.
   *
   * @param parsed true if don't parse anymore
   */
  public void setParsed(boolean parsed) {
    this.parsed = parsed;
  }

  @Override
  public String toString() {
    return getName();
  }

  /**
   * Returns whether index is already parsed.<br>
   * Indexes are parsed delayed because some attributes may be added later when
   * the whole model is known.
   *
   * @return true if parsed
   */
  public boolean isParsed() {
    return parsed;
  }


  @Override
  public void validate() throws ModelException {
    if (getEntity().getOptions().isProvided()) {
      throw createModelException("[PROVIDED] entities cannot have index definitions");
    }
    if (StringHelper.isAllWhitespace(getName())) {
      throw createModelException("missing index name");
    }
    String diag = NameVerifier.getInstance().verifyIndexName(this);
    if (diag != null) {
      throw createModelException(diag);
    }
    // leading underscore is ok, because index name gets the tablename prepended

    for (Backend backend: factory.getBackends()) {
      try {
        backend.assertValidName(SqlNameType.INDEX_NAME, createDatabaseIndexName(entity));
        if (filterCondition != null && !backend.isFilteredIndexSupported()) {
          throw createModelException("filtered/partial index not supported by " + backend);
        }
        for (IndexAttribute attribute : getAttributes()) {
          if (attribute.getFunctionName() != null && !backend.isFunctionBasedIndexSupported()) {
            throw createModelException("function-based index not supported by " + backend);
          }
        }
      }
      catch (RuntimeException rex) {
        throw new ModelException(rex.getMessage(), this, rex);
      }
    }

    if (getAttributes().isEmpty()) {
      throw createModelException("index " + this + " does not contain any attributes");
    }
  }

  /**
   * Gets the source line.
   *
   * @return the line
   */
  public ConfigurationLine getSourceLine() {
    return sourceLine;
  }

  /**
   * Sets the source line.
   *
   * @param sourceLine the line
   */
  public void setSourceLine(ConfigurationLine sourceLine) {
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
      ex = new ModelException(message, entity);
    }
    return ex;
  }

  @Override
  public String sqlCreateIndex(Backend backend, Entity entity) {
    return CodeFactory.getInstance().createSqlIndex(backend, entity, this);
  }


  /**
   * Checks whether other index is logically equal to this index.
   *
   * @param otherIndex the other index
   * @return true if equal
   */
  public boolean isLogicallyEqualTo(Index otherIndex) {
    boolean logicallyEqual = true;
    if (Objects.equals(getFilterCondition(), otherIndex.getFilterCondition())) {
      if (getAttributes().size() == otherIndex.getAttributes().size()) {
        for (int position = 0; position < getAttributes().size(); position++) {
          IndexAttribute indexAttribute = getAttributes().get(position);
          IndexAttribute otherAttribute = otherIndex.getAttributes().get(position);
          Attribute indexAttr = indexAttribute.getAttribute();
          Attribute otherAttr = otherAttribute.getAttribute();
          String indexColumnName = indexAttr == null ? null : indexAttr.getColumnName();
          String otherColumnName = otherAttr == null ? null : otherAttr.getColumnName();
          if (indexAttribute.isDescending() != otherAttribute.isDescending() ||
              !Objects.equals(indexColumnName, otherColumnName) ||
              !Objects.equals(indexAttribute.getFunctionName(), otherAttribute.getFunctionName())) {
            logicallyEqual = false;
            break;
          }
        }
      }
      else {
        logicallyEqual = false;
      }
    }
    else {
      logicallyEqual = false;
    }
    return logicallyEqual;
  }

}
