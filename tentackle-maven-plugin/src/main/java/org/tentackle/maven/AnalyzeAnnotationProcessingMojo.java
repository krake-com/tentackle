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



/**
 * Generates code and meta-information prior to wurbeling and compiling the sources.
 * <p>
 * Processes up all annotations annotated with {@code @Analyze}, such as {@code @Service}.<br>
 * Processing results are either written to files in analyzeDir or placed on heap
 * for being picked up by wurblets, depending on the handler implementation for
 * each annotation.
 *
 * @goal analyze
 * @phase generate-sources
 * @requiresDependencyResolution compile
 *
 * @author harald
 * @version $Revision: 2450 $
 */
public class AnalyzeAnnotationProcessingMojo extends AbstractAnalyzeAnnotationProcessingMojo {

  /**
   * Directory holding the sources to be processed.<br>
   * Defaults to all java sources of the current project.
   * If this is not desired, filesets must be used.
   *
   * @parameter property="tentackle.sourceDir"
   *            default-value="${project.build.sourceDirectory}"
   * @required
   */
  protected File sourceDir;

  /**
   * Project classpath.
   *
   * @parameter default-value="${project.compileClasspathElements}"
   * @required
   * @readonly
   */
  protected List<String> classpathElements;

  /**
   * Directory analyze results.<br>
   *
   * @parameter property="tentackle.analyzeDir"
   *            default-value="${project.build.directory}/analyze"
   * @required
   */
  protected File analyzeDir;

  /**
   * Directory generated services.<br>
   *
   * @parameter property="tentackle.serviceDir"
   *            default-value="${project.build.directory}/generated-resources/service"
   * @required
   */
  protected File servicesDir;


  @Override
  public void prepareExecute() {
    setMojoParameters(sourceDir, analyzeDir, servicesDir, classpathElements);
  }

}
