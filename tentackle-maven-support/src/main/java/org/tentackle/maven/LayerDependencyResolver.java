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
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves a plain list of Maven coordinates independently of any project's own dependency graph,
 * via Aether &mdash; exactly as if the dependencies were those of their own, standalone project.
 * <p>
 * Shared by any mojo needing to resolve a set of dependencies that is not necessarily part of the
 * enclosing project's own dependency graph, for example, to build an isolated {@code ModuleLayer}
 * (see {@code org.tentackle.common.ModuleLayerBuilder}) or a set of artifacts to load into a
 * separate classloader.
 *
 * @author harald
 */
public final class LayerDependencyResolver {

  private static final String RUNTIME_SCOPE = "runtime";

  private LayerDependencyResolver() {
  }

  /**
   * Resolves the given dependencies, transitively, independently of any enclosing project's own
   * dependency graph.
   *
   * @param repositorySystem the repository system
   * @param session the repository system session
   * @param repositories the remote repositories to resolve against
   * @param dependencies the dependencies, each with an explicit groupId, artifactId, and version
   * @return the resolved artifact files
   * @throws DependencyResolutionException if resolution failed
   */
  public static List<File> resolve(RepositorySystem repositorySystem, RepositorySystemSession session,
                                    List<RemoteRepository> repositories, List<Dependency> dependencies)
          throws DependencyResolutionException {

    ArtifactTypeRegistry registry = session.getArtifactTypeRegistry();
    CollectRequest collectRequest = new CollectRequest();
    collectRequest.setDependencies(dependencies.stream()
            .map(dependency -> ProjectDependencies.toAetherDependency(dependency, RUNTIME_SCOPE, registry))
            .collect(Collectors.toList()));
    collectRequest.setRepositories(repositories);

    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);

    DependencyResult dependencyResult = repositorySystem.resolveDependencies(session, dependencyRequest);

    List<File> files = new ArrayList<>();
    for (ArtifactResult artifactResult : dependencyResult.getArtifactResults()) {
      files.add(ProjectDependencies.getFile(artifactResult.getArtifact()));
    }
    return files;
  }

}
