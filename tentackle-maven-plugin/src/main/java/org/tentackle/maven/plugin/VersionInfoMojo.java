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

import javax.inject.Inject;
import javax.inject.Named;

import org.tentackle.common.StringHelper;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mojo to determine the versions of all 3rd-party dependencies of the current project.<br>
 * It logs the versions in XML-format ready to use within the properties section of a pom file.
 * The property names are of the form {@code version.groupIdInCamelCase.artifactIdInCamelCase}.<br>
 * If no dependency scope is given, the dependencies of all scopes are listed.
 * <p>
 * Example:
 * <pre>
 *   mvn -Dscope=compile tentackle:versions
 *   ...
 *   [INFO] 
 *   [INFO] --- tentackle-maven-plugin:11.5.1.0:versions (default-cli) @ myapplic-parent ---
 *   [INFO] versions of 3rd-party dependencies for scope 'compile':
 *   &lt;version.orgCodehausGroovy.groovy&gt;2.5.8&lt;/version.orgCodehausGroovy.groovy&gt;
 *   &lt;version.orgOpenjfx.javafxBase&gt;13.0.1&lt;/version.orgOpenjfx.javafxBase&gt;
 *   &lt;version.orgOpenjfx.javafxControls&gt;13.0.1&lt;/version.orgOpenjfx.javafxControls&gt;
 *   &lt;version.orgOpenjfx.javafxFxml&gt;13.0.1&lt;/version.orgOpenjfx.javafxFxml&gt;
 *   &lt;version.orgOpenjfx.javafxGraphics&gt;13.0.1&lt;/version.orgOpenjfx.javafxGraphics&gt;
 *   &lt;version.orgOpenjfx.javafxMedia&gt;13.0.1&lt;/version.orgOpenjfx.javafxMedia&gt;
 *   &lt;version.orgOpenjfx.javafxWeb&gt;13.0.1&lt;/version.orgOpenjfx.javafxWeb&gt;
 *   &lt;version.orgSlf4j.slf4jApi&gt;1.7.28&lt;/version.orgSlf4j.slf4jApi&gt;
 *   &lt;version.orgTentackle.tentackleCommon&gt;11.5.1.0&lt;/version.orgTentackle.tentackleCommon&gt;
 *   &lt;version.orgTentackle.tentackleCore&gt;11.5.1.0&lt;/version.orgTentackle.tentackleCore&gt;
 *   &lt;version.orgTentackle.tentackleDatabase&gt;11.5.1.0&lt;/version.orgTentackle.tentackleDatabase&gt;
 *   &lt;version.orgTentackle.tentackleDomain&gt;11.5.1.0&lt;/version.orgTentackle.tentackleDomain&gt;
 *   &lt;version.orgTentackle.tentackleFx&gt;11.5.1.0&lt;/version.orgTentackle.tentackleFx&gt;
 *   &lt;version.orgTentackle.tentackleFxRdc&gt;11.5.1.0&lt;/version.orgTentackle.tentackleFxRdc&gt;
 *   &lt;version.orgTentackle.tentackleFxRdcPoi&gt;11.5.1.0&lt;/version.orgTentackle.tentackleFxRdcPoi&gt;
 *   &lt;version.orgTentackle.tentackleFxRdcUpdate&gt;11.5.1.0&lt;/version.orgTentackle.tentackleFxRdcUpdate&gt;
 *   &lt;version.orgTentackle.tentackleI18n&gt;11.5.1.0&lt;/version.orgTentackle.tentackleI18n&gt;
 *   &lt;version.orgTentackle.tentackleLogSlf4j&gt;11.5.1.0&lt;/version.orgTentackle.tentackleLogSlf4j&gt;
 *   &lt;version.orgTentackle.tentacklePdo&gt;11.5.1.0&lt;/version.orgTentackle.tentacklePdo&gt;
 *   &lt;version.orgTentackle.tentacklePersistence&gt;11.5.1.0&lt;/version.orgTentackle.tentacklePersistence&gt;
 *   &lt;version.orgTentackle.tentackleScriptGroovy&gt;11.5.1.0&lt;/version.orgTentackle.tentackleScriptGroovy&gt;
 *   &lt;version.orgTentackle.tentackleSession&gt;11.5.1.0&lt;/version.orgTentackle.tentackleSession&gt;
 *   &lt;version.orgTentackle.tentackleSql&gt;11.5.1.0&lt;/version.orgTentackle.tentackleSql&gt;
 *   &lt;version.orgTentackle.tentackleUpdate&gt;11.5.1.0&lt;/version.orgTentackle.tentackleUpdate&gt;
 *   ...
 * </pre>
 *
 * Notice: the whole project must have been successfully built before invoking this goal.
 */
@Mojo(name = "versions",
      aggregator = true,
      defaultPhase = LifecyclePhase.VALIDATE,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class VersionInfoMojo extends AbstractVersionInfoMojo {

  /**
   * Optional dependency scope.
   */
  @Parameter(property = "scope" )
  private String scope;

  /**
   * The default dependency builder.
   */
  @Inject
  @Named("default")
  private DependencyGraphBuilder dependencyGraphBuilder;

  /**
   * Collected module artifacts to exclude.
   */
  private Set<Artifact> modules;

  /**
   * Collected artifacts.
   */
  private final Set<Artifact> artifacts = new HashSet<>();


  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {

    if (getMavenSession().getTopLevelProject().equals(getMavenProject())) {

      ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(getMavenSession().getProjectBuildingRequest());

      List<MavenProject> allProjects = getMavenProject().getCollectedProjects();
      if (allProjects.isEmpty()) {
        allProjects.add(getMavenProject());
      }

      modules = allProjects.stream().map(MavenProject::getArtifact).collect(Collectors.toSet());

      for (MavenProject subProject : allProjects) {
        buildingRequest.setProject(subProject);

        try {
          scanNode(dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null));
        }
        catch (DependencyGraphBuilderException e) {
          throw new MojoExecutionException("scanning for dependencies failed", e);
        }
      }

      String comment = "3rd-party dependencies of project '" + getMavenProject().getName() + "'";
      if (scope != null) {
        comment += " for scope '" + scope + "'";
      }

      generateVersionProperties(comment, artifacts);
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
        scope = scope.toLowerCase(Locale.ROOT);
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
      artifactScope = artifactScope.toLowerCase(Locale.ROOT);
    }
    if (scope == null || Objects.equals(artifactScope, scope)) {
      if (!modules.contains(artifact)) {
        artifacts.add(artifact);
      }
      node.getChildren().forEach(this::scanNode);
    }
  }

}
