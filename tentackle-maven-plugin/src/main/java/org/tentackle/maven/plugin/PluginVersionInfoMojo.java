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

import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.VersionRequest;
import org.eclipse.aether.resolution.VersionResolutionException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Mojo to determine the versions of all maven plugins used in the current project and its modules.<br>
 * It logs the versions in XML-format ready to use within the properties section of a pom file.
 * The property names are of the form {@code version.groupIdInCamelCase.artifactIdInCamelCase}.
 * Missing versions and the meta versions {@code RELEASE} and {@code LATEST} are resolved against
 * the plugin repositories.
 * <p>
 * Example:
 * <pre>
 *   mvn tentackle:plugin-versions
 *   ...
 *   [INFO] 
 *   [INFO] --- tentackle-maven-plugin:13.0-SNAPSHOT:plugin-versions (default-cli) @ admin-parent ---
 *   [INFO]
 *   &lt;!-- plugins of project 'admin-parent' --&gt;
 *   &lt;version.orgApacheMavenPlugins.mavenCleanPlugin&gt;3.1.0&lt;/version.orgApacheMavenPlugins.mavenCleanPlugin&gt;
 *   &lt;version.orgApacheMavenPlugins.mavenCompilerPlugin&gt;3.8.1&lt;/version.orgApacheMavenPlugins.mavenCompilerPlugin&gt;
 *   &lt;version.orgApacheMavenPlugins.mavenDependencyPlugin&gt;3.1.1&lt;/version.orgApacheMavenPlugins.mavenDependencyPlugin&gt;
 *   &lt;version.orgApacheMavenPlugins.mavenDeployPlugin&gt;2.8.2&lt;/version.orgApacheMavenPlugins.mavenDeployPlugin&gt;
 *   &lt;version.orgApacheMavenPlugins.mavenEnforcerPlugin&gt;3.0.0-M1&lt;/version.orgApacheMavenPlugins.mavenEnforcerPlugin&gt;
 *   &lt;version.orgApacheMavenPlugins.mavenInstallPlugin&gt;2.5.2&lt;/version.orgApacheMavenPlugins.mavenInstallPlugin&gt;
 *   &lt;version.orgApacheMavenPlugins.mavenJarPlugin&gt;3.1.2&lt;/version.orgApacheMavenPlugins.mavenJarPlugin&gt;
 *   &lt;version.orgApacheMavenPlugins.mavenJavadocPlugin&gt;3.1.1&lt;/version.orgApacheMavenPlugins.mavenJavadocPlugin&gt;
 *   &lt;version.orgApacheMavenPlugins.mavenResourcesPlugin&gt;3.1.0&lt;/version.orgApacheMavenPlugins.mavenResourcesPlugin&gt;
 *   &lt;version.orgApacheMavenPlugins.mavenSitePlugin&gt;3.8.2&lt;/version.orgApacheMavenPlugins.mavenSitePlugin&gt;
 *   ...
 * </pre>
 *
 * Notice: the whole project must have been successfully built before invoking this goal.
 */
@Mojo(name = "plugin-versions",
      aggregator = true,
      defaultPhase = LifecyclePhase.VALIDATE,
      threadSafe = true)
public class PluginVersionInfoMojo extends AbstractVersionInfoMojo {

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {

    if (getMavenSession().getTopLevelProject().equals(getMavenProject())) {

      // copy: the collected projects are shared with other threads
      List<MavenProject> allProjects = new ArrayList<>(getMavenProject().getCollectedProjects());
      if (allProjects.isEmpty()) {
        allProjects.add(getMavenProject());
      }

      Set<Artifact> pluginArtifacts = new HashSet<>();

      for (MavenProject subProject : allProjects) {
        for (Plugin plugin : subProject.getBuildPlugins()) {
          pluginArtifacts.add(new DefaultArtifact(plugin.getGroupId(), plugin.getArtifactId(), "jar", resolveVersion(subProject, plugin)));
        }
      }

      generateVersionProperties("plugins of project '" + getMavenProject().getName() + "'", pluginArtifacts);
    }
    else {
      getLogger().info("skipped");
    }
  }

  /**
   * Determines the version of a plugin.<br>
   * Usually given by the effective model, but may be missing or a meta version such as RELEASE or LATEST.
   *
   * @param project the project
   * @param plugin the plugin
   * @return the version
   * @throws MojoExecutionException if the version cannot be resolved
   */
  private String resolveVersion(MavenProject project, Plugin plugin) throws MojoExecutionException {
    String version = plugin.getVersion();
    if (version == null || version.isBlank() || "RELEASE".equals(version) || "LATEST".equals(version)) {
      try {
        version = getRepositorySystem().resolveVersion(getMavenSession().getRepositorySession(),
                new VersionRequest(new DefaultArtifact(plugin.getGroupId(), plugin.getArtifactId(), "jar",
                                                       version == null || version.isBlank() ? "RELEASE" : version),
                                   project.getRemotePluginRepositories(), "plugin")).getVersion();
      }
      catch (VersionResolutionException e) {
        throw new MojoExecutionException("cannot resolve version of plugin " + plugin.getKey(), e);
      }
    }
    return version;
  }

}
