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

import org.tentackle.model.Attribute;
import org.tentackle.model.Relation;
import org.tentackle.sql.datatypes.StringType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates the default configuration to control code generation.<br>
 * The configuration maps the configuration paths of the nodes, i.e. the dotted paths of the attributes and
 * relations, to a flag whether code is generated for them. Only configurable nodes are included:
 * <ul>
 *   <li>attributes and relations with the stereotype {@value JavaCodeFactory#NOTEST_STEREOTYPE}:
 *   {@code false} by default</li>
 *   <li>multi-line string attributes and N:M relation lists: {@code true} by default</li>
 * </ul>
 */
public class CodeConfigurationFactory {

  /**
   * Generates a configuration map from data object trees.<br>
   * Sets the configuration path of every node in the trees as a side effect.
   *
   * @param dataObjects the data objects
   * @return the configuration map, empty if nothing is configurable
   */
  public static Map<String, Boolean> createConfig(DataObject... dataObjects) {
    return new CodeConfigurationFactory(dataObjects).create();
  }


  private final DataObject[] dataObjects;

  private CodeConfigurationFactory(DataObject... dataObjects) {
    this.dataObjects = dataObjects;
  }

  private Map<String, Boolean> create() {
    Map<String, Boolean> configuration = new LinkedHashMap<>();
    String configPath = "";
    for (DataObject dataObject : dataObjects) {
      analyzeComponent(configuration, configPath, dataObject);
    }
    return configuration;
  }

  private void analyzeComponent(Map<String, Boolean> configuration, String configPath, DataObject component) {
    configPath = updatePath(configPath, component);
    if (component.getRelation() != null && component.getRelation().getStereotypes().contains(JavaCodeFactory.NOTEST_STEREOTYPE)) {
      configuration.put(configPath, Boolean.FALSE);
    }
    List<DataNode> nodes = component.getNodes();
    if (nodes != null) {
      for (DataNode node : nodes) {
        analyzeNode(configuration, configPath, node);
      }
    }
  }

  private void analyzeNode(Map<String, Boolean> configuration, String configPath, DataNode node) {
    if (node instanceof DataItem item) {
      analyzeItem(configuration, configPath, item);
    }
    else if (node instanceof DataObject component) {
      analyzeComponent(configuration, configPath, component);
    }
    else if (node instanceof DataList list) {
      analyzeList(configuration, configPath, list);
    }
  }

  private void analyzeItem(Map<String, Boolean> configuration, String configPath, DataItem item) {
    Attribute attribute = item.getAttribute();
    if (attribute != null) {
      configPath = updatePath(configPath, item);
      if (attribute.getOptions().getStereotypes().contains(JavaCodeFactory.NOTEST_STEREOTYPE)) {
        configuration.put(configPath, Boolean.FALSE);
      }
      else if (attribute.getDataType() instanceof StringType &&
               item.getOrgValue() instanceof String str && str.contains("\n")) {
        // multiline strings: allow turning off
        configuration.put(configPath, Boolean.TRUE);
      }
    }
  }

  private void analyzeList(Map<String, Boolean> configuration, String configPath, DataList list) {
    Relation relation = list.getRelation();
    if (relation != null) {
      configPath = updatePath(configPath, list);
      if (relation.getStereotypes().contains(JavaCodeFactory.NOTEST_STEREOTYPE)) {
        configuration.put(configPath, Boolean.FALSE);
      }
      List<DataObject> nodes = list.getNodes();
      if (nodes != null) {
        for (DataObject object : nodes) {
          if (object.getNmRelation() != null) {
            configuration.put(configPath, Boolean.TRUE);    // allow NM relations to be skipped
          }
          analyzeComponent(configuration, configPath, object);
        }
      }
    }
  }

  private String updatePath(String path, DataNode node) {
    String name = node.getName();
    if (name != null && !name.contains("[")) {    // not root and not list element
      if (!path.isEmpty()) {
        path += ".";
      }
      path += name;
    }
    node.setConfigurationPath(path);
    return path;
  }

}
