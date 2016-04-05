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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Locale;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;


/**
 * Processor to verify all bundle getString() invocations.
 * <p>
 * Finds all invocations of <code>&lt;reference&gt;.getString(&lt;string-literal&gt;)</code>.<br>
 * Where <code>&lt;reference&gt;</code> is:
 * <ul>
 * <li>a classname: is treated as a bundle classname (e.g. AppFxRdcBundle)</li>
 * <li>a method: it is assumed to be a method like getBundle() that refers to a bundle
 * with the same name as the class containing the method invocation</li>
 * <li>a variable or constant: same as method, i.e. the bundle name is that of the containing class</li>
 * </ul>
 *
 * If a class must not be checked for bundle keys, it needs to be excluded from the fileset.
 * <p>
 * Currently, the following bundles are not covered by this mojo: (TODO)
 * <ul>
 * <li>PdoBundles, i.e. translations for &#64;Singular, &#64;Plural</li>
 * <li>ValidationBundles, i.e. translations in validators (message=...)</li>
 * <li>&#64;FxControllerService with resource=...</li>
 * <li>&#64;Bundle with value=...</li>
 * </ul>
 *
 * @author harald
 */
@Mojo(name = "check-bundles",
      defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class CheckBundlesProcessingMojo extends AbstractTentackleAnnotationProcessingMojo {

  private final CheckBundlesScanner bundleScanner;
	private final CheckBundlesProcessor processor;

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
   * The locale to check the bundles against.<br>
   * Defaults to the current locale.
   */
  @Parameter
  protected String locale;


  /**
   * Classloader for ResourceBundle.getBundle().
   */
  private BundleClassLoader bundleLoader;


  /**
   * Creates a mojo.
   */
  public CheckBundlesProcessingMojo() {
    bundleScanner = new CheckBundlesScanner();
    processor = new CheckBundlesProcessor(bundleScanner);
    addProcessor(processor);
  }


  @Override
  public void prepareExecute() {
    setMojoParameters(sourceDir, classpathElements);
    try {
      bundleLoader = new BundleClassLoader((URLClassLoader) getClass().getClassLoader(), classpathElements);
    }
    catch (MalformedURLException mx) {
      getLog().error("cannot create bundle classloader", mx);
    }
  }

  @Override
  public void finishExecute() throws MojoExecutionException {

    Locale bundleLocale;
    if (locale != null) {
      bundleLocale = Locale.forLanguageTag(locale);
    }
    else {
      bundleLocale = Locale.getDefault();
    }

    int errors = 0;
    int count = 0;
    for (BundleKey key: bundleScanner.getBundleKeys()) {
      count++;
      if (!key.exists(bundleLocale, bundleLoader)) {
        getLog().error("missing resource " + key);
        errors++;
      }
    }

    if (errors > 0) {
      String msg = errors + " errors";
      getLog().error(count + " keys checked (" + bundleLocale + "), " + msg);
      throw new MojoExecutionException(msg);
    }
    else {
      getLog().info(count + " keys checked (" + bundleLocale + "), no errors");
    }
  }



  /**
   * Classloader to load the bundles.<br>
   * Necessary since the compliler's classpath options don't change the task's classloader.
   */
  private static class BundleClassLoader extends URLClassLoader {

    /**
     * Create from another classloader.
     *
     * @param classLoader the task's classloader
     */
    private BundleClassLoader(URLClassLoader classLoader, List<String> classpathElements) throws MalformedURLException {
      super(classLoader.getURLs());
      if (classpathElements != null) {
        for (String classpathElement : classpathElements) {
          File classPathFile = new File(classpathElement);
          URL url = classPathFile.toURI().toURL();
          addURL(url);
        }
      }
    }

    @Override
    public void addURL(URL url) {
      super.addURL(url);
    }
  }

}
