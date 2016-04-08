/*
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.tentackle.script.ScriptFactory;

/**
 * Base class for test mojos.
 *
 * @author harald
 */
public class AbstractTestMojo extends AbstractTentackleAnnotationProcessingMojo {

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
   * Sets the default scripting language to be used in validators.
   */
  @Parameter
  protected String scriptingLanguage;

  /**
   * Locales to check against bundles.
   */
  protected List<Locale> bundleLocales;


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
          getLog().warn("no such locale: " + localeName);
        }
      }
    }
    if (bundleLocales.isEmpty()) {
      Locale bundleLocale = Locale.getDefault();
      getLog().warn("fallback to default locale " + bundleLocale);
      bundleLocales.add(bundleLocale);
    }

    if (scriptingLanguage != null) {
      ScriptFactory.getInstance().setDefaultLanguage(scriptingLanguage);
    }
  }

}
