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
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;
import org.tentackle.common.StringHelper;
import org.tentackle.common.Compare;
import org.tentackle.model.AccessScope;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.model.SelectionType;
import org.tentackle.model.SourceInfo;
import org.tentackle.model.parser.RelationLine;

/**
 * Relation implementation.
 *
 * @author harald
 */
public class RelationImpl implements Relation, Comparable<RelationImpl> {

  /** property default (if object non-composite lazy). */
  static final String DEFAULT = "DEFAULT";

  /** property relation = ... */
  static final String RELATION = "RELATION";

  /** property select = ... */
  static final String SELECT = "SELECT";

  /** property delete = ... */
  static final String DELETE = "DELETE";

  /** property link = ... */
  static final String LINK = "LINK";

  /** property args = ... */
  static final String ARGS = "ARGS";

  /** property nm = ... */
  static final String NM = "NM";

  /** property method = ... */
  static final String METHOD = "METHOD";

  /** property name = ... */
  static final String NAME = "NAME";

  /** property scope = ... */
  static final String SCOPE = "SCOPE";

  /** property comment = ... */
  static final String COMMENT = "COMMENT";


  /** composite relation flag. */
  static final String COMPOSITE = "COMPOSITE";

  /** tracked relation flag. */
  static final String TRACKED = "TRACKED";

  /** referenced relation flag. */
  static final String REFERENCED = "REFERENCED";

  /** processed relation flag. */
  static final String PROCESSED = "PROCESSED";

  /** readonly relation flag. */
  static final String READONLY = "READONLY";

  /** writeonly relation flag. */
  static final String WRITEONLY = "WRITEONLY";

  /** readonly + writeonly. */
  static final String NOMETHOD = "NOMETHOD";

  /** serialized relation flag. */
  static final String SERIALIED = "SERIALIZED";

  /** clear-on-remote-save relation flag. */
  static final String REMOTECLEAR = "REMOTECLEAR";

  /** map list relation to reversed 1:1 object relation. */
  static final String REVERSED = "REVERSED";

  /** cached selection flag. */
  static final String CACHED = "CACHED";

  /** invocation from main class flag. */
  static final String MAIN = "MAIN";

  /** cascade delete flag. */
  static final String CASCADE = "CASCADE";

  /** indexed link method flag. */
  static final String INDEXED = "INDEXED";



  private final Entity entity;
  private final SourceInfo sourceInfo;

  private List<String> annotations;
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
  private SelectionType selectionType;
  private boolean selectionCached;
  private boolean selectionFromMainClass;
  private String selectionWurbletArguments;
  private String methodName;
  private List<String> methodArgs;
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
    methodArgs = new ArrayList<>();
    accessScope = AccessScope.PUBLIC;
  }


  /**
   * Gets the source info.
   *
   * @return the source info
   */
  public SourceInfo getSourceInfo() {
    return sourceInfo;
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

    for (Map.Entry<String,String> entry: line.getProperties().entrySet()) {

      String prop = entry.getKey();
      if (prop.startsWith("@")) {
        getAnnotations().add(prop);
        continue;
      }

      StringTokenizer stok = new StringTokenizer(entry.getValue());

      switch(prop.toUpperCase()) {

        case DEFAULT:
          break;    // do nothing, just white noise (looks better ;))

        /** property relation = ... */
        case RELATION:
          while (stok.hasMoreTokens()) {
            String token = stok.nextToken();
            switch(token.toUpperCase()) {
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

              case SERIALIED:
                setSerialized(true);
                break;

              case REMOTECLEAR:
                setClearOnRemoteSave(true);
                break;

              case REVERSED:
                setReversed(true);
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

        /** property select = ... */
        case SELECT:
          while (stok.hasMoreTokens()) {
            String token = stok.nextToken();
            switch(token.toUpperCase()) {
              case MAIN:
                setSelectionFromMainClass(true);
                break;

              case CACHED:
                setSelectionCached(true);
                break;

              default:
                try {
                  setSelectionType(SelectionType.valueOf(token.toUpperCase()));
                }
                catch (IllegalArgumentException ex) {
                  // add to wurblet args
                  if (selectionWurbletArguments == null) {
                    selectionWurbletArguments = token;
                  }
                  else {
                    selectionWurbletArguments += " " + token;
                  }
                }
            }
          }
          break;

        /** property delete = ... */
        case DELETE:
          while (stok.hasMoreTokens()) {
            String token = stok.nextToken();
            switch(token.toUpperCase()) {
              case MAIN:
                setDeletionFromMainClass(true);
                break;

              case CASCADE:
                setDeletionCascaded(true);
                break;

              default:
                throw createModelException("illegal keyword in delete property: " + token);
            }
          }
          break;

        /** property link = ... */
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

        /** property args = ... */
        case ARGS:
          while (stok.hasMoreTokens()) {
            getMethodArgs().add(stok.nextToken());
          }
          break;

        /** property method = ... */
        case METHOD:
          // method = &lt;methodname&lt;
          setMethodName(entry.getValue());
          break;

        /** property nm = ... */
        case NM:
          // nm = &lt;N:M relation name&lt [mnMethodName];
          setNmName(stok.nextToken());
          if (stok.hasMoreTokens()) {
            setNmMethodName(stok.nextToken());
          }
          break;

        /** property name = ... */
        case NAME:
          setName(entry.getValue());
          break;

        /** property scope = ... */
        case SCOPE:
          try {
            setAccessScope(AccessScope.valueOf(entry.getValue().toUpperCase()));
          }
          catch (IllegalArgumentException ex) {
            throw createModelException("illegal keyword in scope property: " + entry.getValue());
          }
          break;

        /** property comment = ... */
        case COMMENT:
          setComment(entry.getValue());
          break;

        default:
          throw createModelException("unknown property: " + prop);
      }
    }

    // init defaults
    if (methodArgs.isEmpty()) {
      // setup defaults if not yet done
      if (getRelationType() == RelationType.LIST) {
        methodArgs.add("getId()");
      }
      else {
        methodArgs.add(buildName(true) + "Id");
      }
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
        buf.append(foreignEntity).append('#');
        if (foreignAttribute != null) {
          buf.append(foreignAttribute);
        }
        else {
          buf.append('?');
        }
      }
    }
    else {
      if (!methodArgs.isEmpty()) {
        boolean needComma = false;
        for (String methodArg: methodArgs) {
          if (needComma) {
            buf.append(',');
          }
          buf.append(methodArg);
          needComma = true;
        }
      }
      else {
        buf.append('?');
      }
    }
    return buf.toString();
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    if (!reversed) {
      buf.append(entity).append(": ");
    }
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
    }

    // check constraints

    if (composite) {
      if (selectionType != SelectionType.LAZY && selectionType != SelectionType.EAGER) {
        throw createModelException("composite relations must be eager or lazy");
      }
    }

    if (reversed) {
      if (composite || relationType != RelationType.LIST) {
        throw createModelException("reversed 1:1 mapping only allowed for non-composite list relations");
      }
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
    }
    if (composite && selectionCached) {
      throw createModelException("cached select is not allowed for composite relations");
    }
    if (processed && !composite) {
      throw createModelException("processed only allowed for composite relations");
    }
    if (serialized && (isComposite() || selectionType != SelectionType.LAZY && selectionType != SelectionType.EAGER)) {
      throw createModelException("serialized is only allowed for lazy or eager non-composite relations");
    }

    if (nmName != null && (!composite || relationType == RelationType.OBJECT)) {
      throw createModelException("nm-relations must be composite lists");
    }

    if (selectionWurbletArguments != null && relationType != RelationType.LIST) {
      throw createModelException(
              "extra wurblet arguments are only allowed for list relations (in select property): " +
                      selectionWurbletArguments);
    }
  }


  @Override
  public String getName() {
    return name == null ? StringHelper.firstToLower(className) : name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public RelationLine getSourceLine() {
    return sourceLine;
  }

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
  public String getMethodName() {
    return methodName;
  }

  @Override
  public List<String> getMethodArgs() {
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
  public boolean isSelectionFromMainClass() {
    return selectionFromMainClass;
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

  public void setMethodArgs(List<String> methodArgs) {
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

  public void setSelectionFromMainClass(boolean selectionFromMainClass) {
    this.selectionFromMainClass = selectionFromMainClass;
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
    return "get" + getMethodNameSuffix();
  }

  @Override
  public String getSetterName() {
    return "set" + getMethodNameSuffix();
  }

  @Override
  public String getDeclaredJavaType() {
    String type = getClassName();
    if (getForeignEntity().isAbstract()) {
      type += "<?>";
    }
    if (getRelationType() == RelationType.LIST && !reversed) {
      return (isTracked() ? "TrackedList" : "List") + "<" + type + ">";
    }
    else {
      return type;
    }
  }

  @Override
  public String getJavaType() {
    if (getRelationType() == RelationType.LIST && !reversed) {
      return (isTracked() ? "TrackedArrayList" : "ArrayList") + "<>";
    }
    else {
      return getClassName();
    }
  }

  @Override
  public boolean isDeepReference() {
    return deepReference;
  }

  public void setDeepReference(boolean deepReference) {
    this.deepReference = deepReference;
  }

}
