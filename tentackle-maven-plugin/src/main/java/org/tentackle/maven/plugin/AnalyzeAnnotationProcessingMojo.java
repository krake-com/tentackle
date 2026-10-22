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

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import java.io.File;
import java.util.List;


/**
 * Generates code and meta-information prior to wurbeling and compiling the sources.
 * <p>
 * Processes all annotations annotated with {@code @Analyze}, such as {@code @Service}.<br>
 * Depending on the handler of each annotation, the results are either written as service
 * descriptors to {@code servicesDir}, as analyze info to {@code analyzeDir}, or placed on heap
 * to be picked up by the wurblets.
 *
 * @author harald
 */
@Mojo(name = "analyze",
      defaultPhase = LifecyclePhase.GENERATE_SOURCES,
      requiresDependencyResolution = ResolutionScope.COMPILE,
      threadSafe = true)
public class AnalyzeAnnotationProcessingMojo extends AbstractAnalyzeAnnotationProcessingMojo {

  /**
   * Directory holding the sources to be processed.<br>
   * Defaults to all java sources of the current project.
   * If this is not desired, filesets must be used.
   */
  @Parameter(defaultValue = "${project.build.sourceDirectory}",
             property = "wurbel.sourceDir",
             required = true)
  protected File sourceDir;   // same as in wurbelizer's WurbelMojo

  /**
   * The project's compile classpath.
   */
  @Parameter(defaultValue = "${project.compileClasspathElements}",
             readonly = true,
             required = true)
  protected List<String> classpathElements;

  /**
   * Directory for the analyze-results.
   */
  @Parameter(defaultValue = "${project.build.directory}/analyze",
             property = "wurbel.analyzeDir",
             required = true)
  protected File analyzeDir;    // same as in wurbelizer's WurbelMojo

  /**
   * Directory for the generated services.<br>
   * Must be registered as a resource directory to end up in the artifact.
   */
  @Parameter(defaultValue = "${project.build.directory}/generated-resources/services",
             property = "tentackle.serviceDir",
             required = true)
  protected File servicesDir;


  @Override
  public void prepareExecute() throws MojoFailureException {
    setMojoParameters(sourceDir, analyzeDir, servicesDir, classpathElements);
  }

}
