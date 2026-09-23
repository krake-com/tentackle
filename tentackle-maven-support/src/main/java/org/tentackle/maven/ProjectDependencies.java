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

package org.tentackle.maven;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Exclusion;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dependency handling based on the Maven Resolver API ({@code org.eclipse.aether}) only.
 * <p>
 * Works the same way with Maven 3 and Maven 4 and replaces the legacy
 * {@code org.apache.maven.artifact} based approaches, such as {@code MavenProject.getArtifacts()},
 * {@code maven-artifact-transfer} or {@code maven-dependency-tree}.
 * <p>
 * Two flavors are provided:
 * <ul>
 *   <li>The dependencies of a {@link MavenProject}, see {@link #collect} and {@link #resolve}.</li>
 *   <li>Plain lists of Maven coordinates, see {@link #toAetherDependency(Dependency, String, ArtifactTypeRegistry)}
 *   and {@link LayerDependencyResolver}.</li>
 * </ul>
 *
 * @author harald
 */
public final class ProjectDependencies {

  /** Default scope of dependencies not specifying any scope. */
  public static final String SCOPE_COMPILE = "compile";

  /** The scopes needed at runtime. */
  public static final Set<String> RUNTIME_SCOPES = Set.of("compile", "runtime");

  /** The scopes needed to compile. */
  public static final Set<String> COMPILE_SCOPES = Set.of("compile", "provided", "system");

  private ProjectDependencies() {
  }

  /**
   * Converts a POM dependency to a resolver dependency.
   *
   * @param dependency the POM dependency, must have groupId, artifactId and version
   * @param scope the scope to set, null or empty if use the scope of the given dependency (or "compile" if none)
   * @param registry the artifact type registry, null if none
   * @return the resolver dependency
   */
  public static org.eclipse.aether.graph.Dependency toAetherDependency(Dependency dependency, String scope,
                                                                        ArtifactTypeRegistry registry) {
    String typeId = dependency.getType() == null || dependency.getType().isBlank() ? "jar" : dependency.getType();
    ArtifactType type = registry == null ? null : registry.get(typeId);
    String classifier = dependency.getClassifier() == null ? "" : dependency.getClassifier();
    Map<String, String> properties = null;
    if (dependency.getSystemPath() != null && !dependency.getSystemPath().isBlank()) {
      properties = Map.of(ArtifactProperties.LOCAL_PATH, dependency.getSystemPath());     // scope "system"
    }
    Artifact artifact;
    if (type == null) {
      artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
                                     classifier, typeId, dependency.getVersion(), properties, (File) null);
    }
    else {
      if (classifier.isEmpty() && type.getClassifier() != null) {
        classifier = type.getClassifier();
      }
      artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
                                     classifier, type.getExtension(), dependency.getVersion(), type);
      if (properties != null) {
        artifact = artifact.setProperties(properties);
      }
    }
    String effectiveScope = scope != null && !scope.isEmpty() ? scope :
                            dependency.getScope() == null || dependency.getScope().isBlank() ?
                            SCOPE_COMPILE : dependency.getScope();
    List<org.eclipse.aether.graph.Exclusion> exclusions = new ArrayList<>();
    for (Exclusion exclusion : dependency.getExclusions()) {
      exclusions.add(new org.eclipse.aether.graph.Exclusion(
              exclusion.getGroupId(), exclusion.getArtifactId(), "*", "*"));
    }
    return new org.eclipse.aether.graph.Dependency(artifact, effectiveScope,
            dependency.getOptional() == null ? Boolean.FALSE : Boolean.valueOf(dependency.isOptional()), exclusions);
  }

  /**
   * Creates the collect request for the dependencies of a project.<br>
   * Honors the project's {@code dependencyManagement} and remote repositories.
   *
   * @param project the project
   * @param session the repository system session
   * @return the request
   */
  public static CollectRequest createCollectRequest(MavenProject project, RepositorySystemSession session) {
    ArtifactTypeRegistry registry = session.getArtifactTypeRegistry();

    CollectRequest request = new CollectRequest();
    request.setRootArtifact(new DefaultArtifact(project.getGroupId(), project.getArtifactId(), "", "pom", project.getVersion()));
    request.setRequestContext("project");
    request.setRepositories(project.getRemoteProjectRepositories());

    List<org.eclipse.aether.graph.Dependency> dependencies = new ArrayList<>();
    for (Dependency dependency : project.getDependencies()) {
      dependencies.add(toAetherDependency(dependency, null, registry));
    }
    request.setDependencies(dependencies);

    DependencyManagement management = project.getDependencyManagement();
    if (management != null) {
      List<org.eclipse.aether.graph.Dependency> managed = new ArrayList<>();
      for (Dependency dependency : management.getDependencies()) {
        org.eclipse.aether.graph.Dependency managedDependency = toAetherDependency(dependency, null, registry);
        if (dependency.getScope() == null || dependency.getScope().isBlank()) {
          managedDependency = managedDependency.setScope("");     // management must not force a scope, if none given
        }
        managed.add(managedDependency);
      }
      request.setManagedDependencies(managed);
    }
    return request;
  }

  /**
   * Collects the dependency graph of a project.<br>
   * The artifacts in the returned graph are not resolved, i.e. have no files.
   *
   * @param repositorySystem the repository system
   * @param session the repository system session
   * @param project the project
   * @return the root node of the graph
   * @throws DependencyCollectionException if collecting failed
   */
  public static DependencyNode collect(RepositorySystem repositorySystem, RepositorySystemSession session,
                                       MavenProject project) throws DependencyCollectionException {
    return repositorySystem.collectDependencies(session, createCollectRequest(project, session)).getRoot();
  }

  /**
   * Resolves the dependencies of a project, transitively.
   *
   * @param repositorySystem the repository system
   * @param session the repository system session
   * @param project the project
   * @param scopes the scopes to include, null or empty for all
   * @return the resolved artifacts, each with its file, in classpath order
   * @throws DependencyResolutionException if resolving failed
   */
  public static List<Artifact> resolve(RepositorySystem repositorySystem, RepositorySystemSession session,
                                       MavenProject project, Set<String> scopes) throws DependencyResolutionException {
    DependencyRequest request = new DependencyRequest(createCollectRequest(project, session), scopeFilter(scopes));
    List<Artifact> artifacts = new ArrayList<>();
    for (ArtifactResult result : repositorySystem.resolveDependencies(session, request).getArtifactResults()) {
      artifacts.add(result.getArtifact());
    }
    return artifacts;
  }

  /**
   * Gets the classpath elements of a project.<br>
   * Replaces the deprecated {@code MavenProject.getCompileClasspathElements()} and friends.
   *
   * @param repositorySystem the repository system
   * @param session the repository system session
   * @param project the project
   * @param scopes the scopes to include, see {@link #COMPILE_SCOPES} and {@link #RUNTIME_SCOPES}
   * @return the project's output directory followed by the paths to the resolved dependencies
   * @throws DependencyResolutionException if resolving failed
   */
  public static List<String> getClasspathElements(RepositorySystem repositorySystem, RepositorySystemSession session,
                                                  MavenProject project, Set<String> scopes)
         throws DependencyResolutionException {
    List<String> elements = new ArrayList<>();
    elements.add(project.getBuild().getOutputDirectory());
    for (Artifact artifact : resolve(repositorySystem, session, project, scopes)) {
      elements.add(getFile(artifact).getAbsolutePath());
    }
    return elements;
  }

  /**
   * Gets the file of a resolved artifact.<br>
   * Since Maven Resolver 2.0 (Maven 4) {@code Artifact.getFile()} is deprecated in favor of {@code getPath()},
   * which does not exist in Resolver 1.x (Maven 3). As long as both are supported, this is the only
   * place using the deprecated method.
   *
   * @param artifact the resolved artifact
   * @return the file, null if not resolved
   */
  @SuppressWarnings("deprecation")
  public static File getFile(Artifact artifact) {
    return artifact.getFile();
  }

  /**
   * Creates a filter accepting dependencies of the given scopes only.<br>
   * The root node is always accepted.
   *
   * @param scopes the scopes, null or empty to accept all
   * @return the filter
   */
  public static DependencyFilter scopeFilter(Set<String> scopes) {
    if (scopes == null || scopes.isEmpty()) {
      return (node, parents) -> true;
    }
    return (node, parents) -> node.getDependency() == null || scopes.contains(node.getDependency().getScope());
  }

  /**
   * Gets the coordinates of an artifact as {@code groupId:artifactId:version}.
   *
   * @param artifact the artifact
   * @return the coordinates
   */
  public static String toCoordinates(Artifact artifact) {
    return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion();
  }

}
