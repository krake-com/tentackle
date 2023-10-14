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

import org.tentackle.common.StringHelper;
import org.tentackle.misc.Convertible;
import org.tentackle.misc.Holder;
import org.tentackle.misc.ObjectUtilities;
import org.tentackle.model.Attribute;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.sql.DataType;
import org.tentackle.sql.DataTypeFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Factory generating Java code that creates PDOs.
 */
public class JavaCodeFactory {


  /**
   * Stereotype for attributes and relations to skip test code generation.
   */
  public static final String NOTEST_STEREOTYPE = "NOTEST";

  private static final String HIDDEN_COMMENT = "// hidden: ";

  /**
   * Generates code from a data object template.
   *
   * @param configuration optional configuration map
   * @param dataObjects the data objects
   * @return the Java code
   */
  public static String generateCode(Map<String, Boolean> configuration, DataObject... dataObjects) {
    return new JavaCodeFactory(configuration, dataObjects).generate();
  }



  private final Map<String, Boolean> configuration;
  private final DataObject[] dataObjects;
  private final Map<String, Holder<Integer>> nameMap;

  private JavaCodeFactory(Map<String, Boolean> configuration, DataObject... dataObjects) {
    this.configuration = configuration;
    this.dataObjects = dataObjects;
    nameMap = new HashMap<>();
  }

  /**
   * Generates the Java code.
   *
   * @return the code
   */
  public String generate() {
    StringBuilder buf = new StringBuilder();
    for (DataObject dataObject : dataObjects) {
      if (!buf.isEmpty()) {
        buf.append('\n');
      }
      String varName = createVarName(dataObject.getType());
      buf.append(createComponentCode(varName, dataObject));
      if (dataObject.getPdo().isRootEntity()) {
        buf.append(varName).append(" = ").append(varName).append(".persist();\n");
      }
    }
    return buf.toString();
  }


  private String createVarName(String type) {
    String varName = StringHelper.firstToLower(type);
    Holder<Integer> holder = nameMap.computeIfAbsent(varName, n -> new Holder<>(0));
    int count = holder.get();
    if (count > 0) {
      varName += count;
    }
    holder.accept(count + 1);
    return varName;
  }

  private String createNodeCode(String varName, DataNode node) {
    String code;
    if (node instanceof DataItem item) {
      code = createItemCode(varName, item);
    }
    else if (node instanceof DataObject component) {
      StringBuilder buf = new StringBuilder();
      Relation relation = component.getRelation();
      if (relation == null || isGenerating(!relation.getStereotypes().contains(NOTEST_STEREOTYPE), node)) {
        String componentName = createVarName(component.getType());
        buf.append(createComponentCode(componentName, component));
        if (relation != null) {
          buf.append(varName).append('.').append(relation.getSetterName()).append('(').append(componentName).append(");\n");
        }
      }
      code = buf.toString();
    }
    else if (node instanceof DataList list) {
      code = createListCode(varName, list);
    }
    else {
      code = "";    // will not happen, but...
    }
    return code;
  }

  private String createItemCode(String varName, DataItem item) {
    StringBuilder buf = new StringBuilder();
    Attribute attribute = item.getAttribute();
    if (!item.isIdOfCompositeRelation() && attribute != null &&
        !attribute.isImplicit() &&
        isGenerating(!attribute.getOptions().getStereotypes().contains(NOTEST_STEREOTYPE), item) &&
        !item.isCounterForListRelation()) {
      if (item.isHidden()) {
        buf.append(HIDDEN_COMMENT);
      }
      Object orgValue = item.getOrgValue();
      NonCompositeRelation nonCompositeRelation = item.getNonCompositeRelation();
      if (nonCompositeRelation != null) {
        Relation relation = nonCompositeRelation.getRelation();
        buf.append(varName).append('.').append(relation.getSetterName()).append("(Objects.requireNonNull(on(")
           .append(relation.getForeignEntity()).append(".class).selectByUniqueDomainKey(")
           .append(extractUdkLiteral(nonCompositeRelation.getRelatedPdo())).append(")));")
           .append(toIdComment(nonCompositeRelation.getRelatedPdo())).append('\n');
      }
      else if (isSettingNecessary(orgValue, attribute)) {
        if (orgValue != null) {
          String str;
          DataType<?> dataType = attribute.getDataType();
          if (attribute.isConvertible()) {
            if (orgValue.getClass().isEnum()) {
              str = orgValue.getClass().getSimpleName() + "." + ((Enum<?>) orgValue).name();
            }
            else {
              try {
                dataType = attribute.getInnerDataType();
                str = orgValue.getClass().getSimpleName() + ".toInternal(" + dataType.valueOfLiteralToCode(((Convertible<?>) orgValue).toExternal().toString(), null) + ")";
              }
              catch (ModelException e) {
                str = dataType.valueOfLiteralToCode(orgValue.toString(), null);
              }
            }
          }
          else {
            str = dataType.valueOfLiteralToCode(item.getValue(), null);
          }
          buf.append(varName).append('.').append(attribute.getSetterName()).append("(").append(str).append(");\n");
        }
        else {
          // null is not the default: set it explicitly
          buf.append(varName).append('.').append(attribute.getSetterName()).append("(null);\n");
        }
      }
    }
    return buf.toString();
  }

  private String createComponentCode(String varName, DataObject component) {
    StringBuilder buf = new StringBuilder();
    if (component.isHidden()) {
      buf.append(HIDDEN_COMMENT);
    }
    buf.append(component.getType()).append(' ').append(varName)
       .append(" = on(").append(component.getType()).append(".class);")
       .append(toIdComment(component.getPdo())).append('\n');
    for (DataNode node : component.getNodes()) {
      buf.append(createNodeCode(varName, node));
    }
    return buf.toString();
  }

  private String createListCode(String varName, DataList list) {
    StringBuilder buf = new StringBuilder();
    if (list.getRelation() != null && isGenerating(!list.getRelation().getStereotypes().contains(NOTEST_STEREOTYPE), list)) {
      for (DataObject object : list.getNodes()) {
        String objectName = createVarName(object.getType());
        NonCompositeRelation listRelation = object.getNmRelation();
        if (listRelation != null && isGenerating(true, list)) {
          Relation relation = listRelation.getRelation();
          Relation nmRelation = relation.getNmRelation();
          buf.append(varName).append(".get").append(relation.getNmMethodName()).append("().add(Objects.requireNonNull(on(")
             .append(nmRelation.getForeignEntity()).append(".class).selectByUniqueDomainKey(")
             .append(extractUdkLiteral(listRelation.getRelatedPdo())).append(")));")
             .append(toIdComment(listRelation.getRelatedPdo())).append('\n');
        }
        else {
          buf.append(createComponentCode(objectName, object));
          if (list.isHidden()) {
            buf.append(HIDDEN_COMMENT);
          }
          buf.append(varName).append('.').append(list.getRelation().getGetterName())
             .append("().add(").append(objectName).append(");\n");
        }
      }
    }
    return buf.toString();
  }

  private boolean isSettingNecessary(Object orgValue, Attribute attribute) {
    String nonNullDefault = attribute.getOptions().getInitialValue();
    if (nonNullDefault == null) {
      nonNullDefault = attribute.getOptions().getNewValue();
    }
    Object defaultValue = nonNullDefault == null ? null : attribute.getDataType().valueOf(nonNullDefault);

    if (defaultValue == null) {
      if (attribute.getDataType().isPrimitive()) {
        if (orgValue instanceof Number number) {
          return !ObjectUtilities.getInstance().isZero(number);
        }
        if (orgValue instanceof Boolean bool) {
          return bool;  // only if TRUE
        }
        if (orgValue instanceof Character c) {
          return c != 0;
        }
      }
    }

    return !Objects.equals(orgValue, defaultValue);
  }

  private String extractUdkLiteral(PersistentDomainObject<?> pdo) {
    Class<?> uniqueDomainKeyType = pdo.getUniqueDomainKeyType();
    if (pdo.getUniqueDomainKey() instanceof Convertible<?> convertible) {
      Object external = convertible.toExternal();
      DataType<?> externalType = DataTypeFactory.getInstance().get(external.getClass());
      return uniqueDomainKeyType.getSimpleName() + ".toInternal(" + externalType.valueOfLiteralToCode(external.toString(), null) + ")";
    }
    DataType<?> dataType = DataTypeFactory.getInstance().get(uniqueDomainKeyType);
    if (dataType != null) {
      return dataType.valueOfLiteralToCode(pdo.getUniqueDomainKey().toString(), null);
    }
    // composite UDK
    return uniqueDomainKeyType.getSimpleName() + ".valueOf(\"" + pdo.getUniqueDomainKey() + "\")";
  }

  private String toIdComment(PersistentDomainObject<?> pdo) {
    if (pdo != null && !pdo.isEmbedded()) {
      return "    // [" + pdo.getId() + "/" + pdo.getSerial() + "]";
    }
    return "";
  }

  private boolean isGenerating(boolean generating, DataNode node) {
    if (configuration != null) {
      String configurationPath = node.getConfigurationPath();
      if (configurationPath != null) {
        Boolean toggle = configuration.get(configurationPath);
        if (toggle != null) {
          generating = toggle;
        }
      }
    }
    return generating;
  }

}
