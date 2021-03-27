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
import org.tentackle.model.AccessScope;
import org.tentackle.model.Attribute;
import org.tentackle.model.CodeFactory;
import org.tentackle.model.Entity;
import org.tentackle.model.MethodArgument;
import org.tentackle.model.ModelElement;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.TrackType;
import org.tentackle.model.parse.OptionParser;
import org.tentackle.model.parse.RelationLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;

/**
 * Relation implementation.
 *
 * @author harald
 */
public class RelationImpl implements Relation, Comparable<RelationImpl> {

  /** property default (if object non-composite lazy). */
  public static final String DEFAULT = "default";

  /** property relation = ... */
  public static final String RELATION = "relation";

  /** property select = ... */
  public static final String SELECT = "select";

  /** property delete = ... */
  public static final String DELETE = "delete";

  /** property link = ... */
  public static final String LINK = "link";

  /** property args = ... */
  public static final String ARGS = "args";

  /** property nm = ... */
  public static final String NM = "nm";

  /** property method = ... */
  public static final String METHOD = "method";

  /** property name = ... */
  public static final String NAME = "name";

  /** property scope = ... */
  public static final String SCOPE = "scope";

  /** property comment = ... */
  public static final String COMMENT = "comment";

  /** property count = ... */
  public static final String COUNT = "count";


  /** composite relation flag. */
  public static final String COMPOSITE = "composite";

  /** tracked relation flag. */
  public static final String TRACKED = "tracked";

  /** referenced relation flag. */
  public static final String REFERENCED = "referenced";

  /** processed relation flag. */
  public static final String PROCESSED = "processed";

  /** readonly relation flag. */
  public static final String READONLY = "readonly";

  /** writeonly relation flag. */
  public static final String WRITEONLY = "writeonly";

  /** readonly + writeonly. */
  public static final String NOMETHOD = "nomethod";

  /** serialized relation flag. */
  public static final String SERIALIZED = "serialized";

  /** clear-on-remote-save relation flag. */
  public static final String REMOTECLEAR = "remoteclear";

  /** map list relation to reversed 1:1 object relation. */
  public static final String REVERSED = "reversed";

  /** skip relation in snapshots or copies. */
  public static final String SHALLOW = "shallow";

  /** sets the relation to immutable after loading. */
  public static final String IMMUTABLE = "immutable";

  /** add toString value to the partOfNormText. */
  public static final String PART_OF_NORMTEXT = "normtext";

  /** add blunt method to interface. */
  public static final String BLUNT_DECLARED = "blunt";

  /** cached selection flag. */
  public static final String CACHED = "cached";

  /** cascade delete flag. */
  public static final String CASCADE = "cascade";



  private final Entity entity;
  private final SourceInfo sourceInfo;

  private int ordinal;
  private List<String> annotations;
  private List<String> stereotypes;
  private RelationLine sourceLine;
  private String name;
  private String comment;
  private String className;
  private RelationType relationType;
  private AccessScope accessScope;
  private boolean composite;
  private boolean tracked;
  private boolean referenced;
  private boolean processed;
  private boolean readOnly;
  private boolean writeOnly;
  private boolean serialized;
  private boolean clearOnRemoteSave;
  private boolean reversed;
  private boolean shallow;
  private boolean immutable;
  private boolean partOfNormText;
  private boolean bluntDeclared;
  private Attribute countAttribute;
  private SelectionType selectionType;
  private boolean selectionTypeDetermined;
  private boolean selectionCached;
  private String selectionWurbletArguments;
  private String methodName;
  private List<String> args;
  private List<MethodArgument> methodArgs;
  private String nmName;
  private String nmMethodName;
  private String linkMethodName;
  private String linkMethodIndex;
  private boolean deletionFromMainClass;
  private boolean deletionCascaded;
  private Entity foreignEntity;
  private Attribute attribute;
  private Attribute foreignAttribute;
  private Relation foreignRelation;
  private Relation nmRelation;
  private Relation definingNmRelation;
  private boolean deepReference;



  /**
   * Creates a relation.
   *
   * @param entity the entity this relation belongs to
   * @param sourceInfo the source info
   */
  public RelationImpl(Entity entity, SourceInfo sourceInfo) {
    this.entity = entity;
    this.sourceInfo = sourceInfo;

    annotations = new ArrayList<>();
    stereotypes = new ArrayList<>();
    methodArgs = new ArrayList<>();
    accessScope = AccessScope.PUBLIC;
  }


  /**
   * Gets the source info.
   *
   * @return the source info
   */
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


  @Override
  public int hashCode() {
    int hash = 5;
    hash = 31 * hash + Objects.hashCode(this.entity);
    hash = 31 * hash + Objects.hashCode(this.name);
    hash = 31 * hash + Objects.hashCode(this.foreignEntity);
    hash = 31 * hash + Objects.hashCode(this.attribute);
    hash = 31 * hash + Objects.hashCode(this.foreignAttribute);
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
    final RelationImpl other = (RelationImpl) obj;
    if (!Objects.equals(this.entity, other.entity)) {
      return false;
    }
    if (!Objects.equals(this.name, other.name)) {
      return false;
    }
    if (!Objects.equals(this.foreignEntity, other.foreignEntity)) {
      return false;
    }
    if (!Objects.equals(this.attribute, other.attribute)) {
      return false;
    }
    return Objects.equals(this.foreignAttribute, other.foreignAttribute);
  }

  @Override
  public int compareTo(RelationImpl o) {
    if (o == null) {
      return Integer.MAX_VALUE;
    }
    int rv = Compare.compare((EntityImpl) entity, (EntityImpl) o.entity);
    if (rv == 0) {
      rv = Compare.compare(name, o.name);
      if (rv == 0) {
        rv = Compare.compare((EntityImpl) foreignEntity, (EntityImpl) o.foreignEntity);
        if (rv == 0) {
          rv = Compare.compare((AttributeImpl) attribute, (AttributeImpl) o.attribute);
          if (rv == 0) {
            rv = Compare.compare((AttributeImpl) foreignAttribute, (AttributeImpl) o.foreignAttribute);
          }
        }
      }
    }
    return rv;
  }

  /**
   * Parses a relation line.
   *
   * @param entity the entity
   * @param line the source line
   * @throws ModelException if parsing the model failed
   */
  public void parse(Entity entity, RelationLine line) throws ModelException {
    setSourceLine(line);
    setClassName(line.getClassName());

    try {

      for (Map.Entry<String, String> entry : line.getProperties().entrySet()) {

        String prop = entry.getKey();
        if (prop.startsWith("@") && !StringHelper.isAllWhitespace(prop.substring(1))) {
          getAnnotations().add(prop);
          continue;
        }
        if (prop.startsWith("#")) {
          String stereotype = prop.substring(1);
          if (!StringHelper.isAllWhitespace(stereotype)) {
            getStereotypes().add(stereotype);
          }
          continue;
        }

        StringTokenizer stok = new StringTokenizer(entry.getValue());

        switch (prop.toLowerCase()) {

          case DEFAULT:
            break;    // do nothing, just white noise (looks better ;))

          // property relation = ...
          case RELATION:
            while (stok.hasMoreTokens()) {
              String token = stok.nextToken();
              switch (token.toLowerCase()) {
                case COMPOSITE:
                  setComposite(true);
                  break;

                case TRACKED:
                  setTracked(true);
                  break;

                case REFERENCED:
                  setReferenced(true);
                  break;

                case PROCESSED:
                  setProcessed(true);
                  break;

                case READONLY:
                  setReadOnly(true);
                  break;

                case WRITEONLY:
                  setWriteOnly(true);
                  break;

                case NOMETHOD:
                  setReadOnly(true);
                  setWriteOnly(true);
                  break;

                case SERIALIZED:
                  setSerialized(true);
                  break;

                case REMOTECLEAR:
                  setClearOnRemoteSave(true);
                  break;

                case REVERSED:
                  setReversed(true);
                  break;

                case SHALLOW:
                  setShallow(true);
                  break;

                case IMMUTABLE:
                  setImmutable(true);
                  break;

                case PART_OF_NORMTEXT:
                  setPartOfNormText(true);
                  break;

                case BLUNT_DECLARED:
                  setBluntDeclared(true);
                  break;

                default:
                  try {
                    setRelationType(RelationType.valueOf(token.toUpperCase()));
                  }
                  catch (IllegalArgumentException ex) {
                    throw createModelException("illegal keyword in relation property: " + token);
                  }
              }
            }
            break;

          // property select = ...
          case SELECT:
            boolean pipeFound = false;
            while (stok.hasMoreTokens()) {
              String token = stok.nextToken();
              if (token.startsWith("|")) {
                // lead for extra selection wurblet arguments
                if (token.length() > 1) {
                  token = token.substring(1);   // remove leading |
                }
                pipeFound = true;
              }
              if (pipeFound) {
                // add to wurblet args
                if (selectionWurbletArguments == null) {
                  selectionWurbletArguments = token;
                }
                else {
                  selectionWurbletArguments += " " + token;
                }
              }
              else {
                if (CACHED.equals(token.toLowerCase())) {
                  setSelectionCached(true);
                }
                else {
                  try {
                    setSelectionType(SelectionType.valueOf(token.toUpperCase()));
                  }
                  catch (IllegalArgumentException ex) {
                    throw createModelException("illegal keyword in select property: " + token);
                  }
                }
              }
            }
            break;

          // property delete = ...
          case DELETE:
            while (stok.hasMoreTokens()) {
              String token = stok.nextToken();
              if (CASCADE.equals(token.toLowerCase())) {
                setDeletionCascaded(true);
              }
              else {
                throw createModelException("illegal keyword in delete property: " + token);
              }
            }
            break;

          // property link = ...
          case LINK:
            while (stok.hasMoreTokens()) {
              String token = stok.nextToken();
              if (linkMethodName == null) {
                setLinkMethodName(token);
              }
              else if (linkMethodIndex == null) {
                setLinkMethodIndex(token);
              }
              else {
                throw createModelException("illegal keyword in link property: " + token);
              }
            }
            break;

          // property args = ...
          case ARGS:
            OptionParser op = new OptionParser(entry.getValue(), null);
            String nextArg;
            while ((nextArg = op.nextOption(1)) != null) {
              if (args == null) {
                args = new ArrayList<>();
              }
              args.add(nextArg);
            }
            break;

          // property method = ...
          case METHOD:
            setMethodName(entry.getValue());
            break;

          // property nm = ...
          case NM:
            setNmName(stok.nextToken());
            if (stok.hasMoreTokens()) {
              setNmMethodName(stok.nextToken());
            }
            break;

          // property name = ...
          case NAME:
            setName(entry.getValue());
            break;

          // property scope = ... *
          case SCOPE:
            try {
              setAccessScope(AccessScope.valueOf(entry.getValue().toUpperCase()));
            }
            catch (IllegalArgumentException ex) {
              throw createModelException("illegal keyword in scope property: " + entry.getValue());
            }
            break;

          // property count = ...
          case COUNT:
            Attribute ca = entity.getAttributeByJavaName(entry.getValue(), false);
            if (ca == null) {
              throw createModelException("unknown counter attribute: " + entry.getValue());
            }
            if (!ca.getDataType().isNumeric()) {
              throw createModelException("counter attribute " + ca + " must be numeric");
            }
            if (ca.isNullable()) {
              throw createModelException("counter attribute " + ca + " must not be nullable");
            }
            ((AttributeOptionsImpl) ca.getOptions()).setHidden(true);
            setCountAttribute(ca);
            break;

          // property comment = ...
          case COMMENT:
            setComment(entry.getValue());
            break;

          default:
            throw createModelException("unknown property: " + prop);
        }
      }

      // setup default args
      if (getRelationType() == RelationType.LIST) {
        methodArgs.add(new MethodArgument(this, entity.getAttributeByJavaName(Constants.AN_ID, true)));
      }
      else {
        methodArgs.add(new MethodArgument(this, entity.getAttributeByJavaName(getVariableName() + "Id", true)));
      }
      if (args != null) {
        for (String arg : args) {
          int ndx = arg.indexOf('[');
          if (ndx < 0) {
            throw createModelException("invalid syntax: " + arg);
          }
          String foreignName = arg.substring(0, ndx).trim();
          String attribName = arg.substring(ndx + 1);
          if (attribName.endsWith("]")) {
            attribName = attribName.substring(0, attribName.length() - 1);
          }
          if (attribName.endsWith("()")) {
            // method name: determine attribute name
            attribName = attribName.substring(0, attribName.length() - 2);
            if (attribName.startsWith("is")) {
              attribName = attribName.substring(2);
            }
            else if (attribName.startsWith("get")) {
              attribName = attribName.substring(3);
            }
            else {
              throw createModelException("invalid method name: " + arg);
            }
            methodArgs.add(new MethodArgument(this, foreignName, entity.getAttributeByJavaName(attribName, true)));
          }
          else {
            // fixed value
            methodArgs.add(new MethodArgument(this, foreignName, attribName));
          }
        }
      }
    }
    catch (RuntimeException rx) {
      throw createModelException("syntax error", rx);
    }
  }


  @Override
  public Entity getEntity() {
    return entity;
  }

  @Override
  public List<String> getAnnotations() {
    return annotations;
  }

  public void setAnnotations(List<String> annotations) {
    this.annotations = annotations;
  }

  @Override
  public List<String> getStereotypes() {
    return stereotypes;
  }

  public void setStereotypes(List<String> stereotypes) {
    this.stereotypes = stereotypes;
  }

  /**
   * Returns whether selection type was determined by rule.
   *
   * @return false if provided by the model, true if by automatic rule
   */
  public boolean isSelectionTypeDetermined() {
    return selectionTypeDetermined;
  }

  @Override
  public String getGetterSetterComment() {
    StringBuilder buf = new StringBuilder();
    if (reversed) {
      buf.append("reversed as 1:1 from ").append(entity);
    }
    else {
      buf.append(getVariableName());
    }
    if (deepReference) {
      buf.append(" deeply");
    }
    buf.append(" via ");
    if (attribute != null || foreignAttribute != null) {
      if (attribute != null) {
        buf.append(attribute);
      }
      else  {
        buf.append(foreignEntity).append('#').append(foreignAttribute);
      }
    }
    else if (!methodArgs.isEmpty()) {
      buf.append(methodArgs.get(0));
    }
    if (methodArgs.size() > 1) {
      boolean firstSkipped = false;
      for (MethodArgument methodArg: methodArgs) {
        if (firstSkipped) {
          buf.append(" & ").append(methodArg.getForeignAttribute());
        }
        firstSkipped = true;
      }
    }
    return buf.toString();
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (composite) {
      buf.append("composite ");
    }
    if (getRelationType() == RelationType.LIST && !reversed) {
      buf.append("list of ");
    }
    if (foreignEntity != null) {
      buf.append(foreignEntity).append(" ");
      buf.append(getGetterSetterComment());
    }
    else {
      buf.append('?').append(getName()).append('?');
    }
    if (comment != null && !comment.isEmpty()) {
      buf.append(" (").append(comment).append(')');
    }
    return buf.toString();
  }


  @Override
  public void validate() throws ModelException {

    getRelationType();    // force loading of relation type if not set yet

    if (className == null) {
      throw createModelException("missing classname");
    }

    if (accessScope == null) {
      throw createModelException("missing access scope");
    }

    // defaults
    if (selectionType == null) {
      selectionType = selectionCached ? SelectionType.ALWAYS : SelectionType.LAZY;
      selectionTypeDetermined = true;
    }

    // check constraints

    if (bluntDeclared) {
      if (accessScope != AccessScope.PUBLIC) {
        throw createModelException("access scope must be public to declare blunt method in interface");
      }
      if (selectionType != SelectionType.LAZY && selectionType != SelectionType.EAGER) {
        throw createModelException("blunt methods are only generated for eager or lazy relations");
      }
    }

    if (composite && selectionType != SelectionType.LAZY && selectionType != SelectionType.EAGER) {
      throw createModelException("composite relations must be eager or lazy");
    }

    if (reversed && (composite || relationType != RelationType.LIST)) {
      throw createModelException("reversed 1:1 mapping only allowed for non-composite list relations");
    }

    if (relationType == RelationType.LIST) {
      if (selectionCached) {
        throw createModelException("cached select is not allowed for list relations");
      }
      if (deletionCascaded && !composite) {
        throw createModelException("cascaded delete is not allowed for non-composite list relations");
      }
    }
    else {
      if (linkMethodIndex != null) {
        throw createModelException("indexed link method is not allowed for object relations");
      }
      if (deletionCascaded) {
        throw createModelException("object relations are always deleted cascaded");
      }
      if (linkMethodName != null && !referenced) {
        throw createModelException("object relations with link= option must be referenced");
      }
      if (methodArgs.size() > 1) {
        throw createModelException("object relations cannot have extra args");
      }
    }

    if (composite && selectionCached) {
      throw createModelException("cached select is not allowed for composite relations");
    }
    if (processed && !composite) {
      throw createModelException("processed only allowed for composite relations");
    }
    if (serialized) {
      if (selectionCached) {
        throw createModelException("cached select is not allowed for serialized relations");
      }
      if (isComposite() || selectionType != SelectionType.LAZY && selectionType != SelectionType.EAGER) {
        throw createModelException("serialized is only allowed for lazy or eager non-composite relations");
      }
    }

    if (nmName != null && (!composite || relationType == RelationType.OBJECT)) {
      throw createModelException("nm-relations must be composite lists");
    }

    if (selectionWurbletArguments != null && relationType != RelationType.LIST) {
      throw createModelException(
              "extra wurblet arguments are only allowed for list relations (in select property): " +
                      selectionWurbletArguments);
    }

    if (countAttribute != null) {
      if (relationType != RelationType.LIST || reversed || !composite) {
        throw createModelException(
              "counter attribute only allowed for composite non-reversed list relations");
      }
      if (entity.getOptions().getTrackType() != TrackType.FULLTRACKED) {
        throw createModelException("entity must be FULLTRACKED to support counter attributes");
      }
    }
  }


  /**
   * Gets the name of this relation.<br>
   * If no explicit name set, it defaults to the className.<br>
   *
   * @return the name
   */
  @Override
  public String getName() {
    return name == null ? className : name;
  }

  public void setName(String name) {
    if (name != null) {
      name = StringHelper.firstToUpper(name);
    }
    this.name = name;
  }

  /**
   * Gets the source line.
   *
   * @return the line
   */
  public RelationLine getSourceLine() {
    return sourceLine;
  }

  /**
   * Sets the source line.
   *
   * @param sourceLine the line
   */
  public void setSourceLine(RelationLine sourceLine) {
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

  /**
   * Creates a model exception.
   * <p>
   * Refers to the source line if set, otherwise just the message.
   *
   * @param message the message
   * @param cause the cause
   * @return the exception
   */
  public ModelException createModelException(String message, Throwable cause) {
    ModelException ex;
    if (sourceLine != null) {
      ex = sourceLine.createModelException(message, cause);
    }
    else  {
      ex = new ModelException(message, entity, cause);
    }
    return ex;
  }

  @Override
  public String getComment() {
    return comment;
  }

  @Override
  public String getClassName() {
    return className;
  }

  @Override
  public RelationType getRelationType() {
    if (relationType == null) {
      relationType = reversed ? RelationType.LIST : RelationType.OBJECT;
    }
    return relationType;
  }

  @Override
  public AccessScope getAccessScope() {
    return accessScope;
  }

  @Override
  public Attribute getAttribute() {
    return attribute;
  }

  @Override
  public Entity getForeignEntity() {
    return foreignEntity;
  }

  @Override
  public Attribute getForeignAttribute() {
    return foreignAttribute;
  }

  @Override
  public Relation getForeignRelation() {
    return foreignRelation;
  }

  @Override
  public Relation getNmRelation() {
    return nmRelation;
  }

  @Override
  public Relation getDefiningNmRelation() {
    return definingNmRelation;
  }

  @Override
  public boolean isComposite() {
    return composite;
  }

  @Override
  public boolean isTracked() {
    return tracked;
  }

  @Override
  public boolean isReferenced() {
    return referenced;
  }

  @Override
  public boolean isProcessed() {
    return processed;
  }

  @Override
  public boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public boolean isWriteOnly() {
    return writeOnly;
  }

  @Override
  public boolean isSerialized() {
    return serialized;
  }

  @Override
  public boolean isClearOnRemoteSave() {
    return clearOnRemoteSave;
  }

  @Override
  public boolean isReversed() {
    return reversed;
  }

  @Override
  public boolean isShallow() {
    return shallow;
  }

  public void setShallow(boolean shallow) {
    this.shallow = shallow;
  }

  @Override
  public boolean isImmutable() {
    return immutable;
  }

  public void setImmutable(boolean immutable) {
    this.immutable = immutable;
  }

  @Override
  public boolean isPartOfNormText() {
    return partOfNormText;
  }

  public void setPartOfNormText(boolean partOfNormText) {
    this.partOfNormText = partOfNormText;
  }

  @Override
  public boolean isBluntDeclared() {
    return bluntDeclared;
  }

  public void setBluntDeclared(boolean bluntDeclared) {
    this.bluntDeclared = bluntDeclared;
  }

  @Override
  public Attribute getCountAttribute() {
    return countAttribute;
  }

  @Override
  public String getMethodName() {
    return methodName;
  }

  @Override
  public List<MethodArgument> getMethodArgs() {
    return methodArgs;
  }

  @Override
  public String getNmName() {
    return nmName;
  }

  @Override
  public String getNmMethodName() {
    return nmMethodName;
  }

  @Override
  public String getLinkMethodName() {
    return linkMethodName;
  }

  @Override
  public String getLinkMethodIndex() {
    return linkMethodIndex;
  }

  @Override
  public SelectionType getSelectionType() {
    return selectionType;
  }

  @Override
  public boolean isSelectionCached() {
    return selectionCached;
  }

  @Override
  public String getSelectionWurbletArguments() {
    return selectionWurbletArguments;
  }

  @Override
  public boolean isDeletionFromMainClass() {
    return deletionFromMainClass;
  }

  @Override
  public boolean isDeletionCascaded() {
    return deletionCascaded;
  }


  public void setNmRelation(Relation nmRelation) {
    this.nmRelation = nmRelation;
  }

  public void setDefiningNmRelation(Relation definingNmRelation) {
    this.definingNmRelation = definingNmRelation;
  }

  public void setAttribute(Attribute attribute) {
    this.attribute = attribute;
  }

  public void setForeignEntity(Entity foreignEntity) {
    this.foreignEntity = foreignEntity;
  }

  public void setForeignAttribute(Attribute foreignAttribute) {
    this.foreignAttribute = foreignAttribute;
  }

  public void setForeignRelation(Relation foreignRelation) {
    this.foreignRelation = foreignRelation;
  }

  public void setClassName(String className) {
    this.className = className;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public void setComposite(boolean composite) {
    this.composite = composite;
  }

  public void setLinkMethodName(String linkMethodName) {
    this.linkMethodName = linkMethodName;
  }

  public void setMethodArgs(List<MethodArgument> methodArgs) {
    this.methodArgs = methodArgs;
  }

  public void setNmName(String nmName) {
    this.nmName = nmName;
  }

  public void setNmMethodName(String nmMethodName) {
    this.nmMethodName = nmMethodName;
  }

  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  public void setReadOnly(boolean readOnly) {
    this.readOnly = readOnly;
  }

  public void setReferenced(boolean referenced) {
    this.referenced = referenced;
  }

  public void setProcessed(boolean processed) {
    this.processed = processed;
  }

  public void setRelationType(RelationType relationType) {
    this.relationType = relationType;
  }

  public void setSerialized(boolean serialized) {
    this.serialized = serialized;
  }

  public void setClearOnRemoteSave(boolean clearOnRemoteSave) {
    this.clearOnRemoteSave = clearOnRemoteSave;
  }

  public void setReversed(boolean reversed) {
    this.reversed = reversed;
  }

  public void setCountAttribute(Attribute countAttribute) {
    this.countAttribute = countAttribute;
  }

  public void setTracked(boolean tracked) {
    this.tracked = tracked;
  }

  public void setWriteOnly(boolean writeOnly) {
    this.writeOnly = writeOnly;
  }

  public void setSelectionType(SelectionType selectionType) {
    this.selectionType = selectionType;
  }

  public void setSelectionCached(boolean selectionCached) {
    this.selectionCached = selectionCached;
  }

  public void setSelectionWurbletArguments(String selectionWurbletArguments) {
    this.selectionWurbletArguments = selectionWurbletArguments;
  }

  public void setAccessScope(AccessScope accessScope) {
    this.accessScope = accessScope;
  }

  public void setDeletionCascaded(boolean deletionCascaded) {
    this.deletionCascaded = deletionCascaded;
  }

  public void setDeletionFromMainClass(boolean deletionFromMainClass) {
    this.deletionFromMainClass = deletionFromMainClass;
  }

  public void setLinkMethodIndex(String linkMethodIndex) {
    this.linkMethodIndex = linkMethodIndex;
  }


  // ----------------- convenience methods ---------------------------


  /**
   * Gets the unique build name of the relation.
   *
   * @param tolower is true if first letter is lowercase, else uppercase
   * @return the setter/getter method name in main class
   */
  private String buildName(boolean tolower) {
    String str = name == null ? className : name;
    if (tolower) {
      str = StringHelper.firstToLower(str);
    }
    else {
      str = StringHelper.firstToUpper(str);
    }
    if (getRelationType() == RelationType.LIST && !reversed && name == null) {
      str += "List";
    }
    return str;
  }

  /**
   * Gets the variable name.<br>
   *
   * @return the variable name
   */
  @Override
  public String getVariableName() {
    return buildName(true);
  }

  /**
   * Gets the suffix to be used in methodnames.
   * <p>
   * Example:
   * <pre>
   *  "set" + getMethodNameSuffix() would return "setBlah" if the
   *  classname is "Blah" or the name is "blah".
   * </pre>
   * @return the suffix
   */
  @Override
  public String getMethodNameSuffix() {
    return buildName(false);
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
  public String getDeclaredJavaType(boolean withinForeignEntity) {
    return CodeFactory.getInstance().createDeclaredJavaType(this, withinForeignEntity);
  }

  @Override
  public String getJavaType() {
    return CodeFactory.getInstance().createJavaType(this);
  }

  @Override
  public boolean isDeepReference() {
    return deepReference;
  }

  public void setDeepReference(boolean deepReference) {
    this.deepReference = deepReference;
  }

}
