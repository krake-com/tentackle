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

import org.codehaus.plexus.languages.java.jpms.JavaModuleDescriptor;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.VersionScheme;

import org.tentackle.maven.ProjectDependencies;

import java.io.File;
import java.util.Comparator;
import java.util.Objects;

/**
 * Artifact with module information.
 */
public class ModularArtifact implements Comparable<ModularArtifact> {

  private final Artifact artifact;
  private final JavaModuleDescriptor moduleDescriptor;

  /**
   * Creates a modular artifact.
   *
   * @param artifact the resolved artifact
   * @param moduleDescriptor the JPMS module descriptor
   */
  public ModularArtifact(Artifact artifact, JavaModuleDescriptor moduleDescriptor) {
    this.artifact = artifact;
    this.moduleDescriptor = moduleDescriptor;
  }

  /**
   * Gets the resolved artifact.
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
    return ProjectDependencies.getFile(artifact);
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

    return compareTo((ModularArtifact) o) == 0;
  }

  @Override
  public int hashCode() {
    return Objects.hash(artifact.getGroupId(), artifact.getArtifactId(), artifact.getExtension(),
                        artifact.getClassifier(), artifact.getVersion());
  }

  @Override
  public int compareTo(ModularArtifact o) {
    return COMPARATOR.compare(artifact, o.artifact);
  }


  private static final VersionScheme VERSION_SCHEME = new GenericVersionScheme();

  /**
   * Orders artifacts by groupId, artifactId, extension, classifier and version.
   */
  private static final Comparator<Artifact> COMPARATOR =
      Comparator.comparing(Artifact::getGroupId)
                .thenComparing(Artifact::getArtifactId)
                .thenComparing(Artifact::getExtension)
                .thenComparing(Artifact::getClassifier)
                .thenComparing(ModularArtifact::compareVersions);

  private static int compareVersions(Artifact a1, Artifact a2) {
    try {
      return VERSION_SCHEME.parseVersion(a1.getVersion()).compareTo(VERSION_SCHEME.parseVersion(a2.getVersion()));
    }
    catch (InvalidVersionSpecificationException ex) {
      return a1.getVersion().compareTo(a2.getVersion());    // should not happen: the generic scheme accepts anything
    }
  }

}
