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
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;

/**
 * Materializes the {@link #getLayers()} configuration for local development, without running the
 * full (slow) {@code jlink}/{@code jpackage} pipeline.
 * <p>
 * Writes each declared layer's own dependencies into {@code target/tentackle-layers/mp/<name>/} and
 * a matching {@code target/tentackle-layers/layers.properties}, whose leaf entry points directly at
 * the project's own compiled output ({@code target/classes}) rather than a copy of it. This is what
 * an IDE run/debug configuration needs to launch {@code org.tentackle.common.LayerBootstrap} locally;
 * see the {@code layers.md} documentation for the exact run-configuration recipe.
 * <p>
 * Bind this goal to an early phase such as {@code process-classes} in projects that use
 * {@link #getLayers()}, so a plain {@code mvn compile} keeps it up to date:
 * <pre>
 *   &lt;execution&gt;
 *     &lt;goals&gt;&lt;goal&gt;layers&lt;/goal&gt;&lt;/goals&gt;
 *   &lt;/execution&gt;
 * </pre>
 * A project without {@link #getLayers()} configured does not need this goal at all.
 *
 * @author harald
 */
@Mojo(name = "layers",
      requiresDependencyResolution = ResolutionScope.RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      threadSafe = true)
public class LayersMojo extends AbstractJLinkMojo {

  /**
   * The directory to materialize the layers into.
   */
  @Parameter(defaultValue = "${project.build.directory}/tentackle-layers", required = true)
  private File layersDirectory;


  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    if (!isLayered()) {
      getLogger().debug("no layers configured -> nothing to do");
      return;
    }

    File moduleDir = new File(layersDirectory, DEST_MODULEPATH);
    moduleDir.mkdirs();

    LayerResolver layerResolver = new LayerResolver(this);
    layerResolver.resolveAndCopyLayers(moduleDir);
    layerResolver.writeLayersProperties(layersDirectory, moduleDir.getPath(),
            getMavenProject().getBuild().getOutputDirectory());

    getLogger().info("layers materialized in {} - point your IDE run/debug configuration's main class at " +
                     "org.tentackle.common.LayerBootstrap with -Dtentackle.layers={}",
                     layersDirectory, new File(layersDirectory, LayerResolver.LAYERS_PROPERTIES));
  }


  // ------------- unused abstract/overridden methods: this goal never runs jlink/jpackage -----------------

  @Override
  public void prepareExecute() {
    // skip AbstractJLinkMojo#prepareExecute(): no jdeps/jlink tool needed for this goal
  }

  @Override
  protected boolean validate() {
    // skip AbstractJLinkMojo#validate(): no jdeps/jlink tool, no templates needed for this goal
    return isLayered();
  }

  @Override
  protected void createJLinkImage(JLinkResolver.Result result) {
  }

  @Override
  protected void generateFiles(JLinkResolver.Result result) {
  }

  @Override
  protected void validateTemplates() {
  }

  @Override
  protected void installTemplates(boolean overwrite) {
  }

}
