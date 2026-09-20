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
 * Generates code and meta-information prior to wurbeling and compiling the test sources.
 * <p>
 * Processes all annotations annotated with {@code @Analyze}, such as {@code @Service}.<br>
 * Processing results are either written to files in {@code analyzeDir} or placed on heap
 * for being picked up by wurblets, depending on the handler implementation for
 * each annotation.
 *
 * @author harald
 */
@Mojo(name = "test-analyze",
      defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES,
      requiresDependencyResolution = ResolutionScope.TEST,
      threadSafe = true)
public class AnalyzeTestAnnotationProcessingMojo extends AbstractAnalyzeAnnotationProcessingMojo {

  /**
   * Directory holding the sources to be processed.<br>
   * Defaults to all java test sources of the current project.
   * If this is not desired, filesets must be used.
   */
  @Parameter(defaultValue = "${project.build.testSourceDirectory}",
             property = "wurbel.testSourceDir",
             required = true)
  protected File sourceDir;   // same as in wurbelizer's WurbelMojo

  /**
   * Project classpath.
   */
  @Parameter(defaultValue = "${project.testClasspathElements}",
             readonly = true,
             required = true)
  protected List<String> classpathElements;

  /**
   * Directory analyze results.
   */
  @Parameter(defaultValue = "${project.build.directory}/test-analyze",
             property = "wurbel.testAnalyzeDir",
             required = true)
  protected File analyzeDir;    // same as in wurbelizer's WurbelMojo

  /**
   * Directory generated services.
   */
  @Parameter(defaultValue = "${project.build.directory}/generated-test-resources/services",
             property = "tentackle.testServiceDir",
             required = true)
  protected File servicesDir;


  @Override
  public void prepareExecute() throws MojoFailureException {
    setMojoParameters(sourceDir, analyzeDir, servicesDir, classpathElements);
  }

}
