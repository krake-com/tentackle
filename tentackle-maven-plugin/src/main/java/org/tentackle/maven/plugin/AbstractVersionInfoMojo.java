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

package org.tentackle.maven.plugin;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.Artifact;

import org.tentackle.common.Settings;
import org.tentackle.common.StringHelper;
import org.tentackle.maven.AbstractTentackleMojo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

/**
 * Base class for the version info mojos.
 * <p>
 * Logs the versions of a set of artifacts as XML elements, ready to be pasted into the properties section
 * of a pom. The property names are of the form {@code version.groupIdInCamelCase.artifactIdInCamelCase}, where
 * dots, dashes and underscores separate the camel case words. Optionally, the result is written to a file
 * as well.
 */
public abstract class AbstractVersionInfoMojo extends AbstractTentackleMojo {

  /**
   * Optional output file.<br>
   * If the filename ends with <code>.properties</code>, it will be created in properties file format
   * instead of XML. The properties file format does not support group versions.
   */
  @Parameter(property = "outputFile")
  private File outputFile;


  /**
   * Controls creation of extra group version properties.<br>
   * A group property is named {@code version.groupIdInCamelCase}, and the artifact properties of that group
   * with the same version refer to it via {@code ${...}}.
   * Only the version shared by most of the artifacts of a groupId is ever turned into a group property.
   * <ul>
   *   <li>0: no group versions (default)</li>
   *   <li>&gt;0: create a version property, if at least the given number of artifacts of the same groupId share the same version.</li>
   *   <li>&lt;0: create a version property, if at least two artifacts of the same groupId share the same version
   *   and no more artifacts than the absolute value of the given number don't,
   *   i.e. -1 means that all but at most one artifact of the same groupId must refer to the same version.</li>
   * </ul>
   */
  @Parameter(property = "groupVersions")
  private int groupVersions;



  /**
   * Generates the version properties.
   *
   * @param comment the comment/title
   * @param artifacts the artifacts
   * @throws MojoExecutionException if writing the output file failed
   */
  protected void generateVersionProperties(String comment, Set<Artifact> artifacts) throws MojoExecutionException {

    record Group(String groupId, String version) {}

    Map<String, String> versionsMap = new TreeMap<>();                    // property -> version: sorted by property name
    Map<Group, List<Artifact>> groupMap = new HashMap<>();                // groupId+version -> list of artifacts: to build group versions

    for (Artifact artifact : artifacts) {
      versionsMap.put(createPropertyName(artifact), artifact.getVersion());
      if (groupVersions != 0) {
        groupMap.computeIfAbsent(new Group(artifact.getGroupId(), artifact.getVersion()), group -> new ArrayList<>()).add(artifact);
      }
    }

    if (isPropertiesFile()) {   // properties files don't use group versions!
      try (Writer writer = new FileWriter(outputFile, Settings.getEncodingCharset())) {
          Properties properties = new Properties();
          for (Map.Entry<String, String> entry : versionsMap.entrySet()) {
            properties.setProperty(entry.getKey(), entry.getValue());
          }
          properties.store(writer, comment);
      }
      catch (IOException e) {
        throw new MojoExecutionException("could not write properties file", e);
      }
    }

    if (!groupMap.isEmpty()) {
      // if more than one group with the same groupId: keep only the one with the most artifacts
      Map<String, Group> reducedGroupMap = new HashMap<>();      // groupId -> group
      for (Map.Entry<Group, List<Artifact>> entry : groupMap.entrySet()) {
        String groupId = entry.getKey().groupId;
        Group group = reducedGroupMap.get(groupId);
        if (group == null || entry.getValue().size() > groupMap.get(group).size()) {
          reducedGroupMap.put(groupId, entry.getKey());
        }
      }

      // remove groups that don't match the criteria
      for (Iterator<Map.Entry<Group, List<Artifact>>> iterator=groupMap.entrySet().iterator(); iterator.hasNext(); ) {
        Map.Entry<Group, List<Artifact>> entry = iterator.next();
        int size = entry.getValue().size();
        String groupId = entry.getKey().groupId;
        if (!reducedGroupMap.containsValue(entry.getKey()) ||
            groupVersions > 0 && size < groupVersions ||
            groupVersions < 0 && (size == 1 || size - groupVersions < artifacts.stream().filter(a -> a.getGroupId().equals(groupId)).toList().size())) {
          iterator.remove();
        }
      }

      // add and replace group version properties
      for (Group group : groupMap.keySet()) {
        String groupProperty = createGroupPropertyName(group.groupId);
        String groupLead = groupProperty + ".";
        versionsMap.put(groupProperty, group.version);
        for (Map.Entry<String, String> entry : versionsMap.entrySet()) {
          if (entry.getKey().startsWith(groupLead) && entry.getValue().equals(group.version)) {
            entry.setValue("${" + groupProperty + "}");
          }
        }
      }
    }

    StringBuilder xmlBuf = new StringBuilder();
    xmlBuf.append("<!-- ").append(comment).append(" -->");
    for (Map.Entry<String, String> entry : versionsMap.entrySet()) {
      String name = entry.getKey();
      String version = entry.getValue();
      xmlBuf.append('\n').
          append("<").append(name).append(">").
          append(version).
          append("</").append(name).append(">");
    }
    String xml = xmlBuf.toString();

    if (outputFile != null && !isPropertiesFile()) {
      try (Writer writer = new FileWriter(outputFile, Settings.getEncodingCharset())) {
        writer.append(xml);
      }
      catch (IOException e) {
        throw new MojoExecutionException("could not write XML file", e);
      }
    }

    if (versionsMap.isEmpty()) {
      getLogger().warn("no artifacts found");
    }
    else {
      getLogger().info("\n{}", xml);
    }
  }

  private String createGroupPropertyName(String groupId) {
    return "version." + camelize(groupId);
  }

  private String createPropertyName(Artifact artifact) {
    return createGroupPropertyName(artifact.getGroupId()) + "." + camelize(artifact.getArtifactId());
  }

  private String camelize(String str) {
    return StringHelper.toCamelCase(str.replace('-', '.').replace('_', '.'));
  }

  private boolean isPropertiesFile() {
    return outputFile != null && outputFile.getName().endsWith(".properties");
  }

}
