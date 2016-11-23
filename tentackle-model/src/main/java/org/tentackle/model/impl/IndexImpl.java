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

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.IndexAttribute;
import org.tentackle.model.ModelException;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.parser.ConfigurationLine;
import org.tentackle.sql.Backend;

/**
 * Index descriptor.
 *
 * @author harald
 */
public class IndexImpl implements Index {

  private final Entity entity;
  private final EntityFactoryImpl factory;
  private final SourceInfo sourceInfo;
  private final List<IndexAttribute> attributes;
  private ConfigurationLine sourceLine;
  private String name;
  private String comment;
  private boolean unique;
  private String filterCondition;


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

  /**
   * Parses a configuration line.
   *
   * @param entity the entity
   * @param line the source line
   * @throws ModelException if parsing the model failed
   */
  public void parse(Entity entity, ConfigurationLine line) throws ModelException {

    // parse the index
    StringTokenizer stok = new StringTokenizer(line.getKey());
    boolean indexFound = false;
    boolean uniqueFound = false;
    while (stok.hasMoreTokens()) {
      String token = stok.nextToken();
      switch(token.toLowerCase()) {
        case "index":
          setSourceLine(line);
          setUnique(uniqueFound);
          indexFound = true;
          uniqueFound = false;
          break;
        case "unique":
          if (indexFound) {
            throw line.createModelException("keyword 'unique' not allowed after 'index'");
          }
          uniqueFound = true;
          break;
        default:
          if (!indexFound) {
            throw line.createModelException("unknown configuration keyword: " + token);
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
      throw line.createModelException("illegal configuration: " + line.getKey());
    }

    // parse the attributes
    if (entity.getAttributes().isEmpty()) {
      throw line.createModelException("index configuration line not allowed before attribute section");
    }

    String text = line.getValue();
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
      if (token.toLowerCase().equals("desc")) {
        if (indexAttribute == null) {
          throw line.createModelException("keyword 'desc' must follow column name");
        }
        indexAttribute.setDescending(true);
      }
      else if (token.toLowerCase().equals("asc")) {
        if (indexAttribute == null) {
          throw line.createModelException("keyword 'asc' must follow column name");
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
        // check if column name exists
        for (Attribute attribute: entity.getAttributes()) {
          if (attribute.getColumnName().equals(token)) {
            // found
            indexAttribute = factory.createIndexAttribute(this, sourceInfo);
            indexAttribute.setAttribute(attribute);
            if (descending != null) {
              indexAttribute.setDescending(descending);
            }
            break;
          }
        }
        if (indexAttribute == null) {
          // not found
          throw line.createModelException("undefined column name: " + token);
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
  public String getName(Entity entity) {
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
    this.name = StringHelper.toLower(name);
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

  @Override
  public String toString() {
    return getName();
  }


  @Override
  public void validate() throws ModelException {
    if (StringHelper.isAllWhitespace(getName())) {
      throw createModelException("missing index name");
    }
    // leading underscore is ok, because index name gets the tablename prepended

    for (Backend backend: factory.getBackends()) {
      try {
        backend.assertValidName("index name", getName(entity));
      }
      catch (RuntimeException rex) {
        throw new ModelException(rex.getMessage(), sourceInfo, rex);
      }
    }

    if (getAttributes().isEmpty()) {
      throw createModelException("index " + this + " does not contain any attributes");
    }
  }

  @Override
  public ConfigurationLine getSourceLine() {
    return sourceLine;
  }

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
    StringBuilder buf = new StringBuilder();

    if (getComment() != null) {
      buf.append("-- ");
      buf.append(getComment());
      buf.append('\n');
    }
    String[] columnNames = new String[getAttributes().size()];
    int attributeCount = 0;
    for (IndexAttribute indexAttribute : getAttributes()) {
      if (indexAttribute.isDescending()) {
        columnNames[attributeCount] = "-" + indexAttribute.getAttribute().getColumnName();
      }
      else {
        columnNames[attributeCount] = indexAttribute.getAttribute().getColumnName();
      }
      attributeCount++;
    }
    buf.append(backend.sqlCreateIndex(entity.getTableName(), getName(entity), isUnique(), getFilterCondition(), columnNames));

    return buf.toString();
  }

}
