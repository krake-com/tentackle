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

package org.tentackle.maven.plugin.jlink;

import org.apache.maven.artifact.Artifact;
import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;

import java.io.File;

/**
 * Artifact with module information.
 */
public class ModularArtifact implements Comparable<ModularArtifact> {

  private final Artifact artifact;
  private final JavaModuleDescriptor moduleDescriptor;

  /**
   * Creates a modular artifact.
   *
   * @param artifact the maven artifact
   * @param moduleDescriptor the JPMS module descriptor
   */
  public ModularArtifact(Artifact artifact, JavaModuleDescriptor moduleDescriptor) {
    this.artifact = artifact;
    this.moduleDescriptor = moduleDescriptor;
  }

  /**
   * Gets the maven artifact.
   *
   * @return the artifact
   */
  public Artifact getArtifact() {
    return artifact;
  }

  /**
   * Gets the JPMS module descriptor.
   *
   * @return the module descriptor
   */
  public JavaModuleDescriptor getModuleDescriptor() {
    return moduleDescriptor;
  }

  /**
   * Gets the module name.
   *
   * @return the module name, never null
   */
  public String getModuleName() {
    return moduleDescriptor.name();
  }

  @Override
  public String toString() {
    return getModuleName();
  }

  /**
   * Gets the artifact's file.
   *
   * @return the file
   */
  public File getFile() {
    return artifact.getFile();
  }

  /**
   * Gets the filename of the artifact.
   *
   * @return the name of the jar file
   */
  public String getFileName() {
    return getFile().getName();
  }

  /**
   * Gets the path to the artifact's jar file.
   *
   * @return the path
   */
  public String getPath() {
    return getFile().getPath();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    return artifact.compareTo(((ModularArtifact) o).artifact) == 0;
  }

  @Override
  public int hashCode() {
    return artifact.hashCode();
  }

  @Override
  public int compareTo(ModularArtifact o) {
    return artifact.compareTo(o.artifact);
  }

}
