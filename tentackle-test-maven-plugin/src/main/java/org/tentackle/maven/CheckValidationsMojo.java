/**
 * Tentackle - http://www.tentackle.org
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

import java.io.File;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.tentackle.buildsupport.AbstractTentackleProcessor;


/**
 * Mojo to verify all validations.
 *
 * @author harald
 */
@Mojo(name = "validations",
      defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class CheckValidationsMojo extends AbstractTentackleAnnotationProcessingMojo {

  /**
   * Directory holding the sources to be processed.<br>
   * Defaults to all java sources of the current project.
   * If this is not desired, filesets must be used.
   */
  @Parameter(defaultValue = "${project.build.sourceDirectory}",
             property = "tentackle.sourceDir",
             required = true)
  protected File sourceDir;

  /**
   * Project classpath.
   */
  @Parameter(defaultValue = "${project.compileClasspathElements}",
             readonly = true,
             required = true)
  protected List<String> classpathElements;


  /**
   * The locales to check the bundles against.<br>
   * Defaults to the current locale.<br>
   * The fallback locale should come first.
   */
  @Parameter
  protected String locales;

  /**
   * True whether to skip this test.
   */
  @Parameter(defaultValue = "${skipTests}")
  protected boolean skipTests;


  /**
   * Creates a mojo.
   */
  public CheckValidationsMojo() {
    addProcessor(new CheckValidationsProcessor());
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skipTests) {
      getLog().info("Tests are skipped.");
    }
    else {
      super.execute();
    }
  }

  @Override
  public void prepareExecute() throws MojoFailureException {
    setMojoParameters(sourceDir, classpathElements);
  }

  @Override
  protected void initializeProcessor(AbstractTentackleProcessor processor, File srcDir) {
    super.initializeProcessor(processor, srcDir);
    ((CheckValidationsProcessor) processor).setProcessingClassLoader(getProcessingClassloader());
  }

}
