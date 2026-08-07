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

import org.tentackle.common.Constants;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.AttributeSorting;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityFactory;
import org.tentackle.model.Index;
import org.tentackle.model.MethodArgument;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SortType;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.parse.AttributeLine;
import org.tentackle.model.parse.CommentLine;
import org.tentackle.model.parse.ConfigurationLine;
import org.tentackle.model.parse.Document;
import org.tentackle.model.parse.GlobalOptionLine;
import org.tentackle.model.parse.Line;
import org.tentackle.model.parse.OptionLine;
import org.tentackle.model.parse.RelationLine;
import org.tentackle.sql.Backend;
import org.tentackle.sql.DataTypeFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The default entity model factory.
 *
 * @author harald
 */
public class EntityFactoryImpl implements EntityFactory {

  /** the model to use. */
  private final Model model;

  /** last pending comment line. */
  private String pendingComment;

  /** list of backends to validate against. */
  private Collection<Backend> backends;


  /**
   * Creates the entity factory.
   *
   * @param model the model to use
   */
  public EntityFactoryImpl(Model model) {
    this.model = model;
  }


  @Override
  public Model getModel() {
    return model;
  }

  @Override
  public Collection<Backend> getBackends() {
    if (backends == null) {
      backends = new ArrayList<>();
    }
    return backends;
  }

  @Override
  public void setBackends(Collection<Backend> backends) {
    this.backends = backends;
  }

  @Override
  public EntityImpl createEntity(SourceInfo sourceInfo) {
    return new EntityImpl(this, sourceInfo);
  }

  @Override
  public AttributeImpl createAttribute(Entity entity, SourceInfo sourceInfo, boolean implicit) {
    return new AttributeImpl(this, entity, sourceInfo, implicit);
  }

  @Override
  public AttributeOptionsImpl createAttributeOptions(Attribute attribute, SourceInfo sourceInfo) {
    return new AttributeOptionsImpl(attribute, sourceInfo);
  }

  @Override
  public EntityOptionsImpl createEntityOptions(Entity entity, SourceInfo sourceInfo) {
    return new EntityOptionsImpl(this, entity, sourceInfo);
  }

  @Override
  public IndexImpl createIndex(Entity entity, SourceInfo sourceInfo) {
    return new IndexImpl(this, entity, sourceInfo);
  }

  @Override
  public IndexAttributeImpl createIndexAttribute(Index index) {
    return new IndexAttributeImpl(index);
  }

  @Override
  public RelationImpl createRelation(Entity entity, SourceInfo sourceInfo) {
    return new RelationImpl(entity, sourceInfo);
  }



  @Override
  public Entity createEntity(Document document, ModelDefaults modelDefaults) throws ModelException {

    consumePendingComment();

    // parse the model (syntax only)
    List<Line> lines = document.getLines();

    // apply the semantics to the entity
    EntityImpl entity = createEntity(document.getSourceInfo());

    if (modelDefaults != null) {
      // pass the default's value, not just "on": the defaults may turn an option off as well (e.g. "!bind")
      if (modelDefaults.getBind() != null) {
        entity.getOptions().applyOption(CommonOptionsImpl.OPTION_BIND, modelDefaults.getBind());
      }
      if (modelDefaults.getSize() != null) {
        entity.getOptions().applyOption(CommonOptionsImpl.BIND_SIZE, modelDefaults.getSize());
      }
      if (modelDefaults.getAutoSelect()!= null) {
        entity.getOptions().applyOption(Constants.BIND_AUTOSELECT, modelDefaults.getAutoSelect());
      }
    }

    // add id, serial for processing (could be removed or moved later)
    AttributeImpl idAttribute = createAttribute(entity, document.getSourceInfo(), true);
    idAttribute.setDataType(DataTypeFactory.getInstance().get(Long.TYPE));
    idAttribute.setColumnName(Constants.CN_ID);
    idAttribute.setName(Constants.AN_ID);
    idAttribute.getOptions().setComment("object id");
    idAttribute.getOptions().setFromSuper(true);
    entity.getAttributes().add(idAttribute);

    AttributeImpl serialAttribute = createAttribute(entity, document.getSourceInfo(), true);
    serialAttribute.setDataType(DataTypeFactory.getInstance().get(Long.TYPE));
    serialAttribute.setColumnName(Constants.CN_SERIAL);
    serialAttribute.setName(Constants.AN_SERIAL);
    serialAttribute.getOptions().setComment("object serial");
    serialAttribute.getOptions().setFromSuper(true);
    serialAttribute.getOptions().setDefaultValue(1L);
    entity.getAttributes().add(serialAttribute);

    // process the lines
    for (Line line: lines) {
      switch(line.getLineType()) {

        case EMPTY:
          break;    // nothing to do

        case COMMENT:
          // remember the last comment line(s)
          String comment = ((CommentLine) line).getComment();
          if (comment != null && !comment.isEmpty() && comment.charAt(0) == '#') {
            // comments starting with double hash '##' are completely ignored
            continue;
          }
          addPendingComment(comment);
          if (entity.getName() == null) {
            // collect comment lines as long as the name is not set
            entity.getOptions().setLongComment(pendingComment);
            if (StringHelper.isAllWhitespace(entity.getOptions().getComment())) {
              entity.getOptions().setComment(toShortComment(pendingComment));
            }
          }
          break;

        case CONFIGURATION:
          processConfiguration(entity, (ConfigurationLine) line);
          break;

        case GLOBAL_OPTION:
          processGlobalOptions(entity, (GlobalOptionLine) line);
          break;

        case ATTRIBUTE_OPTION:
          processAttributeOption(entity, (OptionLine) line);
          break;

        case ATTRIBUTE:
          processAttribute(entity, (AttributeLine) line);
          break;

        case RELATION:
          processRelation(entity, (RelationLine) line);
          break;

        case RELATION_OPTION:
          processRelationOption(entity, (OptionLine) line);
          break;

        // the line itself knows its source location, and that there may be none at all
        default: throw line.createModelException("unknown line type " + line.getLineType());
      }
    }

    for (Attribute attribute: entity.getAttributes()) {
      if (Constants.AN_TABLESERIAL.equalsIgnoreCase(attribute.getName()) ||
          Constants.CN_TABLESERIAL.equalsIgnoreCase(attribute.getColumnName())) {
        if (!attribute.isImplicit()) {
          throw new ModelException("use [TABLESERIAL] option instead of explicit attribute", entity);
        }
        if (entity.getSuperEntityName() != null) {
          throw new ModelException("[TABLESERIAL] option not allowed in subclasses", entity);
        }
      }

      if (Constants.AN_NORMTEXT.equalsIgnoreCase(attribute.getName()) ||
          Constants.CN_NORMTEXT.equalsIgnoreCase(attribute.getColumnName())) {
        if (!attribute.isImplicit()) {
          throw new ModelException("use [NORMTEXT] option instead of explicit attribute", entity);
        }
        if (entity.getSuperEntityName() != null) {
          throw new ModelException("[NORMTEXT] option not allowed in subclasses", entity);
        }
      }

      if (Constants.AN_EDITEDBY.equalsIgnoreCase(attribute.getName()) ||
          Constants.CN_EDITEDBY.equalsIgnoreCase(attribute.getColumnName()) ||
          Constants.AN_EDITEDSINCE.equalsIgnoreCase(attribute.getName()) ||
          Constants.CN_EDITEDSINCE.equalsIgnoreCase(attribute.getColumnName()) ||
          Constants.AN_EDITEDEXPIRY.equalsIgnoreCase(attribute.getName()) ||
          Constants.CN_EDITEDEXPIRY.equalsIgnoreCase(attribute.getColumnName())) {
        if (!attribute.isImplicit()) {
          throw new ModelException("use [TOKENLOCK] option instead of explicit attribute", entity);
        }
        if (entity.getSuperEntityName() != null) {
          throw new ModelException("[TOKENLOCK] option not allowed in subclasses", entity);
        }
      }
    }

    // determine the context id attribute, if any
    for (Attribute attribute: entity.getAttributes()) {
      if (attribute.getOptions().isContextId()) {
        if (entity.getContextIdAttribute() != null) {
          throw new ModelException("context id attribute set more than once", entity);
        }
        entity.setContextIdAttribute(attribute);
      }
    }

    // find associated attributes associated with object relations
    for (Relation relation: entity.getRelations()) {
      if (relation.getRelationType() == RelationType.OBJECT) {
        MethodArgument firstArg = relation.getMethodArgs().getFirst();
        Attribute attribute = firstArg.getAttribute();
        if (attribute != null) {
          ((RelationImpl) relation).setAttribute(attribute);
          ((AttributeImpl) attribute).setRelation(relation);
        }
      }
    }

    if (entity.isRootOfInheritanceHierarchy() && !entity.getInheritanceType().isMappingToNoTable()) {
      AttributeImpl classIdAttribute = createAttribute(entity, document.getSourceInfo(), true);
      classIdAttribute.setDataType(DataTypeFactory.getInstance().get(Integer.TYPE));
      classIdAttribute.setColumnName(Constants.CN_CLASSID);
      classIdAttribute.setName(Constants.AN_CLASSID);
      classIdAttribute.getOptions().setComment("class id");
      classIdAttribute.getOptions().setFromSuper(true);
      entity.getAttributes().addFirst(classIdAttribute);    // comes first
    }

    // move from start to end of the model
    entity.getAttributes().remove(idAttribute);
    entity.getAttributes().remove(serialAttribute);
    if (!entity.isEmbedded() && entity.getSuperEntityName() == null) {
      // top of hierarchy or no hierarchy at all
      entity.getAttributes().add(idAttribute);
      entity.getAttributes().add(serialAttribute);
    }

    // creates the sorting info, if any
    List<String> sortOpts = entity.getOptions().getSorting();
    if (sortOpts != null && !sortOpts.isEmpty()) {
      List<AttributeSorting> sorting = new ArrayList<>();
      for (String sortOpt: sortOpts) {
        String name = sortOpt.substring(1);
        String suffix = null;
        int ndx = name.indexOf('#');
        if (ndx > 0) {
          suffix = name.substring(ndx + 1);
          name = name.substring(0, ndx);
        }
        SortType sortType = sortOpt.charAt(0) == '+' ? SortType.ASC : SortType.DESC;
        Attribute attribute = entity.getAttributeByJavaName(name, false);
        if (attribute == null) {
          attribute = entity.getAttributeByColumnName(name, false);
        }
        if (attribute == null) {
          throw new ModelException("no such attribute '" + name + "' to sort", entity);
        }
        sorting.add(new AttributeSorting(attribute, suffix, sortType));
      }
      entity.setSorting(sorting);
    }

    entity.validate();
    return entity;
  }


  /**
   * Processes a configuration line.
   *
   * @param entity the entity
   * @param line the configuration line
   * @throws ModelException if processing failed
   */
  protected void processConfiguration(EntityImpl entity, ConfigurationLine line) throws ModelException {
    if (!entity.parseConfiguration(line)) {
      // should be an index config line
      processIndexConfiguration(entity, line);
    }
    consumePendingComment();
  }


  /**
   * Processes an index configuration line.<br>
   * Requires the attribute already processed.
   *
   * @param entity the entity
   * @param line the configuration line
   */
  protected void processIndexConfiguration(EntityImpl entity, ConfigurationLine line) {
    IndexImpl index = createIndex(entity, line.getSourceInfo());
    index.setSourceLine(line);    // delayed parsing!
    String comment = consumePendingComment();
    if (comment != null) {
      index.setLongComment(comment);
      index.setComment(toShortComment(comment));
    }
    entity.getIndexes().add(index);
  }


  /**
   * Processes a global option line.
   *
   * @param entity the entity
   * @param line the global option line
   * @throws ModelException if processing failed
   */
  protected void processGlobalOptions(EntityImpl entity, GlobalOptionLine line) throws ModelException {
    if (entity.getAttributes().size() > 2) {    // ID and Serial predefined
      throw line.createModelException("global options must appear before attributes declaration section");
    }
    EntityOptionsImpl options = entity.getOptions();
    for (String option: line.getOptions()) {
      if (!options.processOption(option)) {
        throw line.createModelException("illegal global option: " + option);
      }
    }

    options.setStereotypes(line.getStereotypes());

    for (String sorting: line.getSortings()) {
      if (!options.processSorting(sorting)) {
        throw line.createModelException("illegal global sorting: " + sorting);
      }
    }
    consumePendingComment();
  }


  /**
   * Processes an attribute option line.
   *
   * @param entity the entity
   * @param line the attribute option line
   * @throws ModelException if processing failed
   */
  protected void processAttributeOption(EntityImpl entity, OptionLine line) throws ModelException {
    Attribute attribute = entity.getAttributeByJavaName(line.getName(), false);
    if (attribute == null) {
      throw line.createModelException("no such attribute: " + line.getName());
    }
    AttributeOptionsImpl attributeOptions = (AttributeOptionsImpl) attribute.getOptions();
    for (String option: line.getOptions()) {
      if (option.startsWith("@")) {
        if (!StringHelper.isAllWhitespace(option.substring(1))) {
          attributeOptions.getAnnotations().add(option);
        }
      }
      else if (option.startsWith("#")) {
        String stereotype = option.substring(1);
        if (!StringHelper.isAllWhitespace(stereotype)) {
          attributeOptions.getStereotypes().add(stereotype);
        }
      }
      else  {
        if (!attributeOptions.processOption(option.toUpperCase(Locale.ROOT))) {
          throw line.createModelException("illegal option '" + option + "' for attribute " + attribute);
        }
      }
    }
    consumePendingComment();
  }

  /**
   * Processes a relation option line.
   *
   * @param entity the entity
   * @param line the attribute option line
   * @throws ModelException if processing failed
   */
  protected void processRelationOption(EntityImpl entity, OptionLine line) throws ModelException {
    Relation relation = entity.getRelation(line.getName(), false);
    if (relation == null) {
      throw line.createModelException("no such relation: " + line.getName());
    }
    for (String option: line.getOptions()) {
      if (option.startsWith("@")) {
        if (!StringHelper.isAllWhitespace(option.substring(1))) {
          relation.getAnnotations().add(option);
        }
      }
      else if (option.startsWith("#")) {
        String stereotype = option.substring(1);
        if (!StringHelper.isAllWhitespace(stereotype)) {
          relation.getStereotypes().add(stereotype);
        }
      }
      else  {
        throw line.createModelException("illegal option '" + option + "' for relation " + relation);
      }
    }
    consumePendingComment();
  }


  /**
   * Processes an attribute line.
   *
   * @param entity the entity
   * @param line the attribute line
   * @throws ModelException if processing failed
   */
  protected void processAttribute(EntityImpl entity, AttributeLine line) throws ModelException {
    AttributeImpl attribute = createAttribute(entity, line.getSourceInfo(), false);
    attribute.parse(entity, line);
    String comment = consumePendingComment();
    if (comment != null) {
      attribute.getOptions().setLongComment(comment);
      if (StringHelper.isAllWhitespace(attribute.getOptions().getComment())) {
        attribute.getOptions().setComment(toShortComment(comment));
      }
    }
    if (StringHelper.isAllWhitespace(attribute.getOptions().getComment())) {
      attribute.getOptions().setComment(attribute.getName());
    }
    entity.getAttributes().add(attribute);
  }


  /**
   * Processes a relation line.
   *
   * @param entity the entity
   * @param line the relation line
   * @throws ModelException if processing failed
   */
  protected void processRelation(EntityImpl entity, RelationLine line) throws ModelException {
    RelationImpl relation = createRelation(entity, line.getSourceInfo());
    relation.parse(entity, line);
    String comment = consumePendingComment();
    if (comment != null) {
      relation.setLongComment(comment);
      if (StringHelper.isAllWhitespace(relation.getComment())) {
        relation.setComment(toShortComment(comment));
      }
    }
    if (StringHelper.isAllWhitespace(relation.getComment())) {
      if (relation.getRelationType() == RelationType.LIST && Objects.equals(relation.getName(), relation.getClassName())) {
        relation.setComment("list of " + relation.getClassName());
      }
      else {
        relation.setComment(StringHelper.firstToLower(relation.getName()));
      }
    }
    entity.getRelations().add(relation);
  }


  /**
   * Adds a pending comment line.
   *
   * @param commentLine the comment line to append
   */
  protected void addPendingComment(String commentLine) {
    if (!StringHelper.isAllWhitespace(commentLine)) {
      if (pendingComment == null) {
        pendingComment = commentLine;
      }
      else {
        pendingComment += '\n' + commentLine;
      }
    }
  }


  /**
   * Returns the pending comment and consumes it.
   *
   * @return the pending comment, null if none
   */
  protected String consumePendingComment() {
    String str = pendingComment;
    pendingComment = null;
    return StringHelper.isAllWhitespace(str) ? null : str;
  }


  /**
   * Takes a probably multi-line comment and extracts the first line w/o trailing punctuation characters.
   *
   * @param comment the comment defined in the model
   * @return the comment header, null if empty comment
   */
  protected String toShortComment(String comment) {
    if (comment == null || comment.isEmpty()) {
      return null;
    }
    StringBuilder header = new StringBuilder(StringHelper.firstLine(comment));
    // cut trailing punctuation characters
    int lastNdx = header.length() - 1;
    while (lastNdx >= 0) {
      char ch = header.charAt(lastNdx);
      if (Character.isWhitespace(ch) || ch == '.' || ch == ',' || ch == ';' || ch == ':' || ch == '!') {
        header.deleteCharAt(lastNdx);
      }
      else {
        break;
      }
      lastNdx--;
    }
    String trimmed = header.toString().trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

}
