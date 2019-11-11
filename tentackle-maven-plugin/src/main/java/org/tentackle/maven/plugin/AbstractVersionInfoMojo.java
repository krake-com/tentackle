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

import org.apache.maven.artifact.Artifact;

import org.tentackle.common.StringHelper;
import org.tentackle.maven.AbstractTentackleMojo;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Base class for the version info mojos.
 */
public abstract class AbstractVersionInfoMojo extends AbstractTentackleMojo {

  /**
   * Generates the version properties in XML format.
   *
   * @param artifacts the artifacts
   * @return the version strings
   */
  protected String generateVersionProperties(Set<Artifact> artifacts) {
    // unique sort by property name
    Map<String, String> versionsMap = new TreeMap<>();    // property:version, sorted by property name
    for (Artifact artifact : artifacts) {
      versionsMap.put(createPropertyName(artifact), artifact.getVersion());
    }

    StringBuilder buf = new StringBuilder();
    for (Map.Entry<String, String> entry : versionsMap.entrySet()) {
      String name = entry.getKey();
      String version = entry.getValue();
      String propertyXML = "<" + name + ">" + version + "</" + name + ">";
      if (verbosityLevel.isInfo()) {
        buf.append('\n').append(propertyXML);
      }
    }

    return buf.toString();
  }

  /**
   * Creates the version property name for an artifact.
   *
   * @param artifact the artifact
   * @return the version name
   */
  protected String createPropertyName(Artifact artifact) {
    return "version." + camalize(artifact.getGroupId()) + "." + camalize(artifact.getArtifactId());
  }

  private String camalize(String str) {
    return StringHelper.toCamelCase(str.replace('-', '.').replace('_', '.'));
  }

}
