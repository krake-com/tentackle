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

package org.tentackle.maven.plugin.i18n;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

import org.tentackle.common.BundleSupport;
import org.tentackle.common.Constants;
import org.tentackle.i18n.StoredBundleFactory;
import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.maven.ProjectClassLoader;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.PersistenceException;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;


/**
 * Base class for i18n mojos.
 *
 * @author harald
 */
public abstract class AbstractI18nMojo extends AbstractTentackleMojo implements DomainContextProvider {

  /**
   * Project classpath.
   */
  @Parameter(defaultValue = "${project.compileClasspathElements}",
             readonly = true,
             required = true)
  protected List<String> classpathElements;

  /**
   * The backend URL.
   */
  @Parameter(required = true)
  protected String url;

  /**
   * The backend username.
   */
  @Parameter(required = true)
  public String user;

  /**
   * The backend password.
   */
  @Parameter
  public String password;

  /**
   * Additional locales except the default one.<br>
   */
  @Parameter
  protected String locales;


  /**
   * Additional locale suffixes to scan for property files.<br>
   * Without leading underscore.
   */
  protected List<String> localeSuffixes;

  /**
   * The domain context to access the stored bundles.
   */
  protected DomainContext context;

  /**
   * Bundle bundleCount.
   */
  protected int bundleCount;

  /**
   * Error bundleCount.
   */
  protected int errors;

  /**
   * Warning bundleCount.
   */
  protected int warnings;

  /**
   * Number of updated translations.
   */
  protected int updates;


  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      findResourceDirs();
      return true;
    }
    return false;
  }


  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    // disable provider because we need only the PDOs from tt-i18n, not the bundle control
    StoredBundleFactory.setEnabled(false);

    // validate configuration
    if (validate()) {

      // grab the extra locale suffixes
      localeSuffixes = new ArrayList<>();
      if (locales != null) {
        StringTokenizer stok = new StringTokenizer(locales, ",; \n\r\t");
        while (stok.hasMoreTokens()) {
          String localeName = stok.nextToken().replace('_', '-');   // transform en_US to en-US
          if (!localeName.isEmpty()) {
            localeSuffixes.add(localeName);
          }
        }
      }

      // open connection to backend
      Properties props = new Properties();
      props.setProperty(Constants.BACKEND_URL, url);
      props.setProperty(Constants.BACKEND_USER, user);
      props.setProperty(Constants.BACKEND_PASSWORD, password);
      SessionInfo sessionInfo = Pdo.createSessionInfo(props);

      try (Session session = Pdo.createSession(sessionInfo)) {
        session.makeCurrent();
        context = Pdo.createDomainContext(session);

        ModificationTracker pdoTracker = ModificationTracker.getInstance();   // create tracker but don't start the thread
        pdoTracker.setSession(session);

        ClassLoader classLoader = new ProjectClassLoader((URLClassLoader) getClass().getClassLoader(), classpathElements);

        // for all annotations like @Bundle or @FxControllerService (need a META-INF/services file!)
        for (BundleSupport bundleSupport : BundleSupport.getBundles(classLoader)) {
          String bundleName = bundleSupport.getBundleName();
          String suffix = null;
          String resourceName = bundleName;
          Iterator<String> iter = localeSuffixes.iterator();
          for (; ; ) {
            if (suffix != null) {
              resourceName = bundleName + "_" + suffix;
            }
            resourceName = resourceName.replace('.','/' ) + Constants.PROPS_EXTENSION;
            if (verbosityLevel.isDebug()) {
              getLog().debug("processing " + resourceName);
            }
            processBundle(classLoader, bundleName, suffix, resourceName);
            if (iter.hasNext()) {
              suffix = iter.next();
            }
            else {
              break;
            }
          }
        }
      }
      catch (MalformedURLException mx) {
        getLog().error("cannot create project classloader", mx);
      }
      catch (PersistenceException rex) {
        throw new MojoExecutionException("processing the bundles failed", rex);
      }

      getLog().info(bundleCount + " bundle" + (bundleCount == 1 ? "" : "s") + " processed, " +
                    warnings + " warning" + (warnings == 1 ? "" : "s") + ", " +
                    errors + " error" + (errors == 1 ? "" : "s") + ", " +
                    updates + " update" + (updates == 1 ? "" : "s"));

      if (errors > 0) {
        throw new MojoExecutionException("pushing to the database failed");
      }
    }
  }


  /**
   * Process the given bundle resource name.<br>
   * The resource name must not start with a leading slash because this method directly uses
   * the classloader's getResourceAsStream method.
   *
   * @param classLoader the project classloader
   * @param resourceName the bundle resource name without leading slash
   * @param bundleName the original bundle class name
   * @param locale the locale, null if default
   */
  public abstract void processBundle(ClassLoader classLoader, String bundleName, String locale, String resourceName);

}
