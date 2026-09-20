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

import org.tentackle.common.StringHelper;

/**
 * Creates a self-contained java application with the <code>jlink</code> tool.
 * <p>
 * This mojo works for modular, non-modular and even mixed applications. It's not just a wrapper for <em>jlink</em>,
 * but analyzes the project's dependencies and finds the best strategy to invoke <code>jlink</code> to
 * create a directory containing an application-specific <code>jimage</code> module. As a result, it requires
 * only minimum configuration.
 * <p>
 * Basically, applications fall into one of 3 categories:
 * <ol>
 *   <li>Full-blown modular applications: all module-infos must require real modules only. Jlink creates
 *   an image from those modules. Optional artifacts and runtime modules can still be added.</li>
 *   <li>Modular applications with non-modular dependencies: jlink is used to create an image from the minimum necessary java runtime modules only,
 *   which are determined by the plugin either from the module-infos or via the <code>jdeps</code> tool.
 *   The application's dependencies are placed on the modulepath via the generated run-script.</li>
 *   <li>Non-modular traditional classpath applications: same as 2, but all dependencies are placed on the classpath.</li>
 * </ol>
 * Since it is very likely, that even modern modular applications require some 3rd-party dependencies not modularized yet,
 * most of those applications will probably fall into the second category.
 * <p>
 * Artifacts not processed by <code>jlink</code> are copied to separate folders and passed to the java runtime explicitly
 * via the module- and/or classpath. A platform-specific launch script will be generated according to
 * the {@link #runTemplate}. For applications using Tentackle's auto update feature, an update script is generated via the {@link #updateTemplate}
 * as well. Finally, the created directory is packed into a deployable zip file.
 * <p>
 * The minimum plugin configuration is very simple:
 *
 * <pre>
 *   ...
 *   &lt;packaging&gt;jlink&lt;/packaging&gt;
 *   ...
 *       &lt;plugin&gt;
 *         &lt;groupId&gt;org.tentackle&lt;/groupId&gt;
 *         &lt;artifactId&gt;tentackle-jlink-maven-plugin&lt;/artifactId&gt;
 *         &lt;version&gt;${tentackle.version}&lt;/version&gt;
 *         &lt;extensions&gt;true&lt;/extensions&gt;
 *         &lt;configuration&gt;
 *           &lt;mainModule&gt;com.example&lt;/mainModule&gt;
 *           &lt;mainClass&gt;com.example.MyApp&lt;/mainClass&gt;
 *         &lt;/configuration&gt;
 *       &lt;/plugin&gt;
 * </pre>
 *
 * The freemarker templates are copied to the project's template folder, if missing.
 * They become part of the project and can be changed easily according to project specific needs (for example by adding runtime arguments).
 * To install and edit the templates before running jlink (or jpackage, see {@link JPackageMojo}), use {@link InitMojo} first.
 * <p>
 * The template model provides the following variables:
 *
 * <ul>
 *   <li><code>mainModule</code>: the name of the main module. Empty if classpath application.</li>
 *   <li><code>mainClass</code>: the name of the main class.</li>
 *   <li><code>modulePath</code>: the module path.</li>
 *   <li><code>classPath</code>: the class path</li>
 *   <li><code>phase</code>: the mojo lifecycle phase</li>
 *   <li><code>goal</code>: the plugin goal (jlink or jpackage)</li>
 *   <li><code>id</code>: the execution id</li>
 *   <li>all system properties (dots in property names translated to camelCase, e.g. "os.name" becomes "osName"</li>
 *   <li>all maven properties (translated to camelCase as well)</li>
 *   <li>the plugin configuration {@link AbstractJLinkMojo#getVariables()}</li>
 * </ul>
 *
 * Modules not passed to <code>jlink</code> and automatic modules are copied to the <code>mp</code> folder
 * and added to the <code>modulePath</code> template variable. If no such modules are detected, no folder is created.<br>
 * Non-modular classpath artifacts are copied to the <code>cp</code> folder and added to the <code>classPath</code> template variable.
 * Again, the folder will only be created if necessary.<br>
 * Additional project resources, such as property files or logger configurations, are copied to the <code>conf</code> directory and
 * this directory prepended to the classpath.
 * <p>
 * The generation of the ZIP-file and attachment of the artifact for installation and deployment can be customized by an
 * application-specific implementation. This allows further modification of the generated image or files in the jlink target
 * directory. It is also possible to add more than one artifact, for example, each with a different configuration.
 * To do so, provide a plugin dependency that contains a class annotated with {@code @Service(}{@link ArtifactCreator}).
 * <p>
 * Notice that you can create an image for a different java version than the one used for the maven build process
 * by specifying an explicit {@code AbstractJLinkMojo#getToolchain()}.
 */
@Mojo(name = "jlink",
      requiresDependencyResolution = ResolutionScope.RUNTIME,
      defaultPhase = LifecyclePhase.PACKAGE,
      threadSafe = true)

public class JLinkMojo extends AbstractJLinkMojo {

  /** filename of the NAME template. */
  public static final String NAME_TEMPLATE = "name.ftl";

  /** filename of the RUN template. */
  public static final String RUN_TEMPLATE = "run.ftl";

  /** filename of the UPDATE template. */
  public static final String UPDATE_TEMPLATE = "update.ftl";


  /**
   * The name of the template to generate the name of the runner script.<br>
   * For *nixes this is usually {@code run.sh}, for windozes {@code run.cmd} or {@code run.bat}.
   */
  @Parameter(defaultValue = NAME_TEMPLATE)
  private String nameTemplate;

  /**
   * The name of the runner script template.
   */
  @Parameter(defaultValue = RUN_TEMPLATE)
  private String runTemplate;

  /**
   * The name of the update script template.
   */
  @Parameter(defaultValue = UPDATE_TEMPLATE)
  private String updateTemplate;


  /**
   * Gets the name of the name template.
   *
   * @return the template to generate the name of the runner script
   */
  public String getNameTemplate() {
    return nameTemplate;
  }

  /**
   * Gets the name of the runner template.
   *
   * @return the template to generate the runner script
   */
  public String getRunTemplate() {
    return runTemplate;
  }

  /**
   * Gets the name of the update template.
   *
   * @return the template to generate the update script, null if don't generate an update script
   */
  public String getUpdateTemplate() {
    return isWithUpdater() ? updateTemplate : null;
  }

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    super.executeImpl();
    ArtifactCreator.getInstance().createAndAttachArtifact(this);
  }


  @Override
  protected void generateFiles(JLinkResolver.Result result) throws MojoExecutionException {
    // generate the script to run the application in the bin subdirectory
    new JLinkGenerator(this, result).generateScripts();
  }

  @Override
  protected void validateTemplates() throws MojoExecutionException {
    if (StringHelper.isAllWhitespace(nameTemplate)) {
      throw new MojoExecutionException("nameTemplate missing");
    }
    if (StringHelper.isAllWhitespace(runTemplate)) {
      throw new MojoExecutionException("runTemplate missing");
    }
    if (isWithUpdater() && StringHelper.isAllWhitespace(updateTemplate)) {
      throw new MojoExecutionException("updateTemplate missing");
    }
  }

  @Override
  protected void installTemplates(boolean overwrite) throws MojoExecutionException {
    installTemplate(NAME_TEMPLATE, nameTemplate, overwrite);
    installTemplate(RUN_TEMPLATE, runTemplate, overwrite);
    if (getUpdateTemplate() != null) {
      installTemplate(UPDATE_TEMPLATE, getUpdateTemplate(), overwrite);
    }
  }
}
