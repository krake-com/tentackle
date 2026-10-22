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

package org.tentackle.maven.plugin.check;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import org.tentackle.common.BundleFactory;
import org.tentackle.maven.AbstractTentackleAnnotationProcessingMojo;
import org.tentackle.script.ScriptFactory;
import org.tentackle.script.ScriptRuntimeException;
import org.tentackle.script.ScriptingLanguage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Base class of the check mojos.
 * <p>
 * Parses the configured locales and points the JVM-wide factories at the project's classpath.
 * The checks are skipped together with the unit tests via {@code -DskipTests}.
 *
 * @author harald
 */
public abstract class AbstractCheckMojo extends AbstractTentackleAnnotationProcessingMojo {

  /**
   * Serializes the executions in parallel builds.<br>
   * The checks configure singletons of the plugin's class realm, which is shared by all projects of a build:
   * the classloaders of the {@code BundleFactory} and {@code ValidationUtilities}, and the default language
   * of the {@code ScriptFactory}. Concurrent executions would check against the wrong project's classpath.
   */
  private static final Lock EXECUTION_LOCK = new ReentrantLock();

  @Override
  protected Lock getExecutionLock() {
    return EXECUTION_LOCK;
  }


  /**
   * Directory holding the sources to be processed.<br>
   * Defaults to all Java sources of the current project.
   * Use {@code filesets} to check only a subset or to exclude files.
   */
  @Parameter(defaultValue = "${project.build.sourceDirectory}",
             property = "tentackle.sourceDir",
             required = true)
  protected File sourceDir;

  /**
   * The project's compile classpath.<br>
   * Includes the project's own output directory, so the compiled classes and copied resources
   * of the project are found as well.
   */
  @Parameter(defaultValue = "${project.compileClasspathElements}",
             readonly = true,
             required = true)
  protected List<String> classpathElements;


  /**
   * The locales to check the bundles and validation messages against.<br>
   * A comma, semicolon or whitespace separated list. Both the {@code en_US} and the {@code en-US}
   * notation are accepted. Defaults to the build's default locale.
   * <p>
   * The fallback locale should come first, because the bundles of the other locales are
   * compared against it.
   */
  @Parameter
  protected String locales;

  /**
   * Skips the checks.<br>
   * Bound to {@code skipTests}, so {@code -DskipTests} skips them together with the unit tests.
   */
  @Parameter(defaultValue = "${skipTests}")
  protected boolean skipTests;

  /**
   * The default scripting language of the validator scripts.<br>
   * Required if scripts use the shorthand {@code {...}} instead of naming their language
   * explicitly, such as {@code #!groovy{...}}. The language's provider must be added as a plugin
   * dependency.
   */
  @Parameter
  protected String scriptingLanguage;

  /**
   * The parsed locales, in the configured order.
   */
  protected List<Locale> bundleLocales;


  @Override
  public void prepareExecute() throws MojoFailureException {
    if (!skipTests) {
      setMojoParameters(sourceDir, classpathElements);

      // grab the locales
      bundleLocales = new ArrayList<>();
      if (locales != null) {
        StringTokenizer stok = new StringTokenizer(locales, ",; \n\r\t");
        while (stok.hasMoreTokens()) {
          String localeName = stok.nextToken().replace('_', '-');   // transform en_US to en-US
          Locale bundleLocale = Locale.forLanguageTag(localeName);
          if (bundleLocale != null && !bundleLocale.toString().isEmpty()) {
            bundleLocales.add(bundleLocale);
          }
          else {
            getLogger().warn("no such locale: {}", localeName);
          }
        }
      }
      if (bundleLocales.isEmpty()) {
        Locale bundleLocale = Locale.getDefault();
        getLogger().warn("fallback to default locale {}", bundleLocale);
        bundleLocales.add(bundleLocale);
      }

      BundleFactory.getInstance().setClassLoader(getProcessingClassloader());

      // the factory is shared by all projects of the build: don't inherit the language of another project
      try {
        if (scriptingLanguage != null) {
          ScriptFactory.getInstance().setDefaultLanguage(scriptingLanguage);
        }
        else if (ScriptFactory.getInstance().getDefaultLanguage() != null) {
          ScriptFactory.getInstance().setDefaultLanguage((ScriptingLanguage) null);
        }
      }
      catch (ScriptRuntimeException sx) {
        throw new MojoFailureException("invalid scripting language '" + scriptingLanguage +
                                       "' (provider missing in the plugin dependencies?)", sx);
      }
    }
  }

  @Override
  protected boolean validate() throws MojoExecutionException {
    if (skipTests) {
      getLogger().info("tests are skipped");
      return false;
    }
    return super.validate();
  }
}
