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

import org.apache.maven.plugin.MojoExecutionException;

import org.tentackle.common.ServiceFactory;


interface ArtifactCreatorHolder {
  ArtifactCreator INSTANCE = ServiceFactory.createService(ArtifactCreator.class, DefaultArtifactCreator.class, false);
}

/**
 * Creates and attaches the artifact to be deployed.<br>
 * May be replaced via &#64;{@link org.tentackle.common.Service} annotation from within an application-specific plugin dependency.
 * For example, to further modify the files or create a bunch of artifacts with different configurations.
 * <p>
 * Example:
 * <pre>
 *   &#64;Service(ArtifactCreator.class)
 *   public class SpecialArtifactCreator implements ArtifactCreator {
 *
 *     &#64;Override
 *     public void createAndAttachArtifact(JlinkMojo mojo) throws MojoExecutionException {
 *       ...
 *     }
 *   }
 * </pre>
 *
 * Add as plugin dependency:
 *
 * <pre>
 *   &lt;plugin&gt;
 *     &lt;groupId&gt;org.tentackle&lt;/groupId&gt;
 *     &lt;artifactId&gt;tentackle-jlink-maven-plugin&lt;/artifactId&gt;
 *     &lt;dependencies&gt;
 *       &lt;dependency&gt;
 *         &lt;groupId&gt;com.example&lt;/groupId&gt;
 *         &lt;artifactId&gt;special-creator&lt;/artifactId&gt;
 *         &lt;version&gt;1.0&lt;/version&gt;
 *       &lt;/dependency&gt;
 *     &lt;/dependencies&gt;
 *     &lt;configuration&gt;
 *       ...
 *     &lt;/configuration&gt;
 *   &lt;/plugin&gt;
 * </pre>
 */
public interface ArtifactCreator {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static ArtifactCreator getInstance() {
    return ArtifactCreatorHolder.INSTANCE;
  }

  /**
   * Creates and attaches the ZIP file created by the jlink goal.
   *
   * @param mojo the jlink mojo
   * @throws MojoExecutionException if failed
   */
  void createAndAttachArtifact(JlinkMojo mojo) throws MojoExecutionException;

  /**
   * Creates and attaches the installers created by the jpackage goal.
   *
   * @param mojo the jpackage mojo
   * @param minTime the minimum creating time of the artifacts
   * @throws MojoExecutionException if failed
   */
  void createAndAttachInstallers(JPackageMojo mojo, long minTime) throws MojoExecutionException;

}
