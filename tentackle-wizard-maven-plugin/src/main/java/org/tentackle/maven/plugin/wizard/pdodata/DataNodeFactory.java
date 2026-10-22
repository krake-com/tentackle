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

package org.tentackle.maven.plugin.wizard.pdodata;

import org.tentackle.common.Constants;
import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;
import org.tentackle.log.Logger;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.pdo.PersistentObject;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.session.Persistent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for data nodes from PDOs.
 */
public class DataNodeFactory {

  private static final Logger LOGGER = Logger.get(DataNodeFactory.class);

  private final Model model;

  /**
   * Creates a new factory.
   *
   * @param model the model
   */
  public DataNodeFactory(Model model) {
    this.model = model;
  }

  /**
   * Creates the data object with its sub nodes.<br>
   * Loads all components of the PDO first.
   *
   * @param pdo the PDO
   * @return the PDO's data, never null
   */
  public DataObject create(PersistentDomainObject<?> pdo) {
    pdo.loadComponents(false);
    return getDataObject(pdo, null, null, null, false, null);
  }


  // --- all private below ---

  /**
   * Collected info for attributes and components of a PDO class.
   */
  private class ClassInfo {

    private final Class<? extends PersistentDomainObject<?>> pdoClass;
    private final Entity entity;
    private final Set<MethodInfo> methodInfos;

    private ClassInfo(Class<? extends PersistentDomainObject<?>> pdoClass, Class<?> poClass) {
      this.pdoClass = pdoClass;
      entity = lookupEntity(pdoClass.getSimpleName());
      methodInfos = new TreeSet<>();      // attributes first, then components and lists, each sorted by name
      Method[] pdoClassMethods = pdoClass.getMethods();
      Method[] poClassMethods = ReflectionHelper.getAllMethods(poClass, new Class<?>[] {PersistentObject.class}, false, null, false);
      Method[] methods = new Method[pdoClassMethods.length + poClassMethods.length];
      System.arraycopy(pdoClassMethods, 0, methods, 0, pdoClassMethods.length);
      System.arraycopy(poClassMethods, 0, methods, pdoClassMethods.length, poClassMethods.length);
      for (Method method : methods) {
        if (method.getParameterCount() == 0 && !method.isBridge()) {        // skip bridge methods such as List<Blah> implemented by TrackedList<Blah>
          String name = method.getName();
          Class<?> type = method.getReturnType();
          boolean isGetter = name.length() > 3 && name.startsWith("get") && Character.isUpperCase(name.charAt(3));
          if (isGetter) {
            name = StringHelper.firstToLower(name.substring(3));
          }
          else if (name.length() > 2 && name.startsWith("is") && Character.isUpperCase(name.charAt(2))
                   && (type == Boolean.class || type == Boolean.TYPE)) {
            name = StringHelper.firstToLower(name.substring(2));
            isGetter = true;
          }
          if (isGetter) {
            Persistent annotation = method.getAnnotation(Persistent.class);
            if (annotation != null && annotation.component()) {
              boolean isHidden = false;
              if (!Modifier.isPublic(method.getModifiers())) {
                method.setAccessible(true);
                isHidden = true;
              }
              boolean isComponent = false;
              boolean isList = false;
              if (PersistentDomainObject.class.isAssignableFrom(type)) {
                isComponent = true;
              }
              else if (List.class.isAssignableFrom(type)) {
                Type genType = method.getGenericReturnType();
                if (genType instanceof ParameterizedType parGenType) {
                  Type[] typeArguments = parGenType.getActualTypeArguments();
                  if (typeArguments.length == 1) {
                    Type typeArg = typeArguments[0];
                    if (typeArg instanceof Class<?> clz) {
                      if (PersistentDomainObject.class.isAssignableFrom(clz)) {
                        type = clz;
                        isComponent = true;
                        isList = true;
                      }
                    }
                  }
                }
              }
              methodInfos.add(new MethodInfo(name, annotation.comment(), method, type, isComponent, isList, isHidden));
            }
          }
        }
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      ClassInfo classInfo = (ClassInfo) o;
      return pdoClass.equals(classInfo.pdoClass);
    }

    @Override
    public int hashCode() {
      return pdoClass.hashCode();
    }
  }

  /**
   * Method info for getters of attributes or composite relations.
   */
  private static class MethodInfo implements Comparable<MethodInfo> {     // attributes first, then sorted by name

    private final String name;
    private final String comment;
    private final Method getter;
    private final String typeName;
    private final boolean isComponent;
    private final boolean isList;
    private final boolean isHidden;

    private MethodInfo(String name, String comment, Method getter, Class<?> type, boolean isComponent, boolean isList, boolean isHidden) {
      this.name = name;
      this.comment = comment;
      this.getter = getter;
      this.isComponent = isComponent;
      this.isList = isList;
      this.typeName = type.getSimpleName();
      this.isHidden = isHidden;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      MethodInfo that = (MethodInfo) o;
      return isComponent == that.isComponent && name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return 31 * Boolean.hashCode(isComponent) + name.hashCode();
    }

    @Override
    public int compareTo(MethodInfo o) {
      int rv = Boolean.compare(isComponent, o.isComponent);   // attributes first, then relations
      if (rv == 0) {
        rv = name.compareTo(o.name);
      }
      return rv;
    }

  }

  /**
   * Map of PDO-classes to class infos.
   */
  private final Map<Class<? extends PersistentDomainObject<?>>, ClassInfo> classMap = new ConcurrentHashMap<>();


  @SuppressWarnings("unchecked")
  private DataObject getDataObject(PersistentDomainObject<?> pdo, String name, String comment,
                                          PersistentDomainObject<?> parentPdo, boolean hidden, Relation compositeRelation) {

    ClassInfo classInfo = classMap.computeIfAbsent(pdo.getEffectiveClass(),
                                effectiveClass -> new ClassInfo(effectiveClass, pdo.getPersistenceDelegate().getClass()));
    if (comment == null || comment.equals(classInfo.entity.getName())) {
      String entityComment = classInfo.entity.getOptions().getComment();
      if (entityComment != null) {
        comment = entityComment;
      }
    }

    List<DataNode> nodes = new ArrayList<>();
    for (MethodInfo methodInfo : classInfo.methodInfos) {
      try {
        Object value = methodInfo.getter.invoke(pdo.getPersistenceDelegate());
        if (methodInfo.isComponent) {
          Relation relation = classInfo.entity.getRelation(methodInfo.name, true);
          if (relation != null && relation.isEmbedding()) {
            StringBuilder prefix = new StringBuilder();
            if (compositeRelation != null && compositeRelation.isEmbedding()) {
              prefix.append(compositeRelation.getColumnPrefix());
            }
            if (relation.isEmbedding()) {
              prefix.append(relation.getColumnPrefix());
            }
            relation = relation.createEmbedded(classInfo.entity, methodInfo.name, prefix.toString());
          }
          if (methodInfo.isList) {
            List<DataObject> dataObjects = new ArrayList<>();
            List<? extends PersistentDomainObject<?>> components = (List<? extends PersistentDomainObject<?>>) value;
            boolean needSort = false;
            if (components != null) {       // should never be null, but one never knows...
              for (PersistentDomainObject<?> component : components) {
                DataObject dataObject = createDataObject(methodInfo.name, methodInfo.comment, component, pdo, false, relation);
                dataObjects.add(dataObject);
                if (dataObject.getNmRelation() != null) {
                  needSort = true;
                }
              }
            }
            if (needSort) {
              dataObjects.sort((o1, o2) -> {
                if (o1.getNmRelation() != null && o2.getNmRelation() != null) {
                  return o1.getNmRelation().toString().compareTo(o2.getNmRelation().toString());
                }
                return Long.compare(o1.getId(), o2.getId());
              });
            }
            nodes.add(new DataList(methodInfo.typeName, methodInfo.name, methodInfo.comment, dataObjects, relation,
                                   new SqlCondition(pdo.getId(), relation == null ? null : relation.getForeignAttribute())));
          }
          else {
            nodes.add(createDataObject(methodInfo.name, methodInfo.comment, (PersistentDomainObject<?>) value, pdo, methodInfo.isHidden, relation));
          }
        }
        else {
          if (Constants.AN_ROOTID.equals(methodInfo.name) && !pdo.isRootIdProvided() ||
              Constants.AN_ROOTCLASSID.equals(methodInfo.name) && !pdo.isRootClassIdProvided() ||
              Constants.AN_TABLESERIAL.equals(methodInfo.name) && !pdo.isTableSerialProvided() ||
              Constants.AN_NORMTEXT.equals(methodInfo.name) && !pdo.isNormTextProvided() ||
              Constants.AN_EDITEDBY.equals(methodInfo.name) && !pdo.isTokenLockProvided() ||
              Constants.AN_EDITEDSINCE.equals(methodInfo.name) && !pdo.isTokenLockProvided() ||
              Constants.AN_EDITEDEXPIRY.equals(methodInfo.name) && !pdo.isTokenLockProvided()) {
            continue;
          }
          if (pdo.isEmbedded() && (
              Constants.AN_ID.equals(methodInfo.name) ||
              Constants.AN_SERIAL.equals(methodInfo.name) ||
              Constants.AN_CLASSID.equals(methodInfo.name))) {
            continue;
          }
          boolean idOfCompositeRelation = false;
          NonCompositeRelation nonCompositeRelation = null;
          if ("long".equals(methodInfo.typeName) || "Long".equals(methodInfo.typeName)) {
            // maybe a 1:1 object relation
            for (Relation relation : classInfo.entity.getAllRelations()) {
              if (relation.getRelationType() == RelationType.OBJECT) {
                Attribute attribute = relation.getAttribute();
                if (attribute != null && attribute.getName().equals(methodInfo.name)) {
                  Relation foreignRelation = relation.getForeignRelation();
                  if (relation.isComposite() ||
                      foreignRelation != null && foreignRelation.isComposite()) {
                    idOfCompositeRelation = true;
                    break;
                  }
                  else {
                    String getterName = relation.getGetterName();
                    try {
                      Method getter = classInfo.pdoClass.getMethod(getterName);
                      Object getterValue = getter.invoke(pdo);
                      if (getterValue instanceof PersistentDomainObject<?> relatedPdo && relatedPdo.isUniqueDomainKeyProvided()) {
                        nonCompositeRelation = new NonCompositeRelation(relation, relatedPdo);
                        break;
                      }
                    }
                    catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
                      LOGGER.warning("cannot load 1:1 relation: " + getterName, e);
                    }
                  }
                }
              }
            }
          }
          Attribute attribute = classInfo.entity.getAttributeByJavaName(methodInfo.name, true);
          if (attribute != null || !Constants.AN_CLASSID.equals(methodInfo.name)) {    // classId is also available via context menu
            Entity entity = classInfo.entity;
            if (compositeRelation != null && compositeRelation.isEmbedding()) {
              if (attribute != null) {
                attribute = attribute.createEmbedded(classInfo.entity, compositeRelation.getPathName(),
                                                     compositeRelation.getColumnPrefix() + attribute.getColumnName());
              }
              entity = compositeRelation.getEmbeddingPath().getFirst();
            }
            nodes.add(new DataItem(methodInfo.typeName, methodInfo.name, value, methodInfo.comment,
                                   entity, attribute, idOfCompositeRelation, nonCompositeRelation,
                                   new SqlCondition(pdo.getId(), classInfo.entity.getAttributeByJavaName(Constants.AN_ID, true))));
          }
        }
      }
      catch (IllegalAccessException | InvocationTargetException e) {
        LOGGER.warning("cannot determine method return value", e);
      }
    }

    Relation objectRelation = null;
    NonCompositeRelation nmRelation = null;
    if (parentPdo != null) {
      ClassInfo parentInfo = classMap.get(parentPdo.getEffectiveClass());
      if (parentInfo != null) {   // must exist, but one never knows...
        Relation relation = parentInfo.entity.getRelation(name, true);   // the name of a list element has no [index] yet
        if (relation != null && relation.isComposite()) {
          objectRelation = relation;
          Relation nmRel = relation.getNmRelation();
          if (nmRel != null) {
            String getterName = nmRel.getGetterName();
            try {
              Method getter = classInfo.pdoClass.getMethod(getterName);
              Object getterValue = getter.invoke(pdo);
              if (getterValue instanceof PersistentDomainObject<?> relatedPdo && relatedPdo.isUniqueDomainKeyProvided()) {
                nmRelation = new NonCompositeRelation(relation, relatedPdo);
              }
            }
            catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
              LOGGER.warning("cannot load N:M relation: " + getterName, e);
            }
          }
        }
      }
    }

    return new DataObject(pdo, name, comment, nodes, classInfo.entity, objectRelation, nmRelation, hidden,
                          new SqlCondition(pdo.getId(), classInfo.entity.getAttributeByJavaName(Constants.AN_ID, true)));
  }

  private DataObject createDataObject(String name, String comment, PersistentDomainObject<?> pdo,
                                             PersistentDomainObject<?> parentPdo, boolean hidden, Relation compositeRelation) {
    DataObject dataObject;
    if (pdo == null) {
      Entity entity = compositeRelation == null ? null : compositeRelation.getForeignEntity();
      dataObject = new DataObject(null, name, comment, null, entity, null, null, hidden, null);
    }
    else {
      dataObject = getDataObject(pdo, name, comment, parentPdo, hidden, compositeRelation);
    }
    return dataObject;
  }

  private Entity lookupEntity(String typeName) {
    try {
      Entity entity = model.getByEntityName(typeName);
      if (entity == null) {
        throw new ModelException("entity " + typeName + " missing in model");
      }
      return entity;
    }
    catch (ModelException e) {
      throw new TentackleRuntimeException(e.getMessage(), e);
    }
  }

}
