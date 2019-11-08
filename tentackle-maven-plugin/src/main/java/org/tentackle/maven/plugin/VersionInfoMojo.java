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
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;

import org.tentackle.common.StringHelper;
import org.tentackle.maven.AbstractTentackleMojo;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Mojo to determine the versions of all 3rd-party dependencies.<br>
 * There are 2 use cases:
 * <ul>
 *   <li>log versions as properties in XML: to copy the output into an existing pom.</li>
 *   <li>add the version properties to the current project: useful if the project refers to
 *   a dependency used by a third-party dependency. Avoids version mismatchs when setting the
 *   version manually.</li>
 * </ul>
 *
 */
@Mojo(name = "versions",
      aggregator = true,
      defaultPhase = LifecyclePhase.VALIDATE,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class VersionInfoMojo extends AbstractTentackleMojo {

  /**
   * Optional dependency scope.
   */
  @Parameter(property = "scope" )
  private String scope;

  /**
   * True if add the version properties to the current maven project.
   */
  @Parameter
  private boolean addToProperties;

  /**
   * The default depencency builder.
   */
  @Component(hint = "default" )
  private DependencyGraphBuilder dependencyGraphBuilder;

  /**
   * All modules of the project.
   */
  private List<MavenProject> allProjects;

  /**
   * Collected module artifacts to exclude.
   */
  private Set<Artifact> modules;

  /**
   * Collected artifacts.
   */
  private Set<Artifact> artifacts = new HashSet<>();


  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {

    if (getMavenSession().getTopLevelProject().equals(getMavenProject())) {

      ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(getMavenSession().getProjectBuildingRequest());

      allProjects = getMavenProject().getCollectedProjects();
      if (allProjects.isEmpty()) {
        allProjects.add(getMavenProject());
      }

      modules = allProjects.stream().map(p -> p.getArtifact()).collect(Collectors.toSet());

      for (MavenProject subProject : allProjects) {
        buildingRequest.setProject(subProject);

        try {
          scanNode(dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null, List.of(subProject)));
        }
        catch (DependencyGraphBuilderException e) {
          throw new MojoExecutionException("scanning for dependencies failed", e);
        }
      }

      // unique sort by property name
      Map<String, String> versionsMap = new TreeMap<>();    // property:version
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
        if (addToProperties) {
          // add to all projects (inheritance already happened, so we must inherit explicitly
          for (MavenProject project : allProjects) {
            project.getProperties().setProperty(name, version);
            if (verbosityLevel.isDebug()) {
              getLog().info("added property " + propertyXML);
            }
          }
        }
      }

      if (verbosityLevel.isInfo()) {
        if (buf.length() > 0) {
          getLog().info("versions of all 3rd-party dependencies:" + buf);
        }
        else {
          getLog().warn("no 3rd-party dependencies found");
        }
      }
    }
    else {
      getLog().info("skipped");
    }
  }

  @Override
  protected boolean validate() throws MojoExecutionException {
    if (scope != null) {
      if (StringHelper.isAllWhitespace(scope)) {
        scope = null;
      }
      else {
        scope = scope.toLowerCase();
      }
    }
    return super.validate();
  }

  /**
   * Recursively scan for all dependencies.
   *
   * @param node the next node in the dependency tree
   */
  private void scanNode(DependencyNode node) {
    Artifact artifact = node.getArtifact();
    String artifactScope = artifact.getScope();
    if (artifactScope == null) {
      artifactScope = scope;
    }
    else {
      artifactScope = artifactScope.toLowerCase();
    }
    if (scope == null || Objects.equals(artifactScope, scope)) {
      if (!modules.contains(artifact)) {
        artifacts.add(artifact);
      }
      node.getChildren().forEach(this::scanNode);
    }
  }

  private String createPropertyName(Artifact artifact) {
    return "version." + camalize(artifact.getGroupId()) + "." + camalize(artifact.getArtifactId());
  }

  private String camalize(String str) {
    return StringHelper.toCamelCase(str.replace('-', '.').replace('_', '.'));
  }
}
