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
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.resolution.DependencyResolutionException;

import org.tentackle.common.BundleSupport;
import org.tentackle.common.Constants;
import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.Settings;
import org.tentackle.i18n.StoredBundleFactory;
import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.maven.ProjectClassLoader;
import org.tentackle.maven.ProjectDependencies;
import org.tentackle.misc.PropertiesUtilities;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.session.ModificationTracker;
import org.tentackle.session.Session;
import org.tentackle.session.SessionInfo;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
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
  public void prepareExecute() {
    // disable provider because we need only the PDOs from tt-i18n, not the bundle control
    StoredBundleFactory.setEnabled(false);
  }

  @Override
  public void executeImpl() throws MojoExecutionException {

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
    EncryptedProperties props = PropertiesUtilities.getInstance().create();
    props.setProperty(Constants.BACKEND_URL, url);
    props.setProperty(Constants.BACKEND_USER, user);
    props.setProperty(Constants.BACKEND_PASSWORD, password);
    SessionInfo sessionInfo = Pdo.createSessionInfo(props);

    try {
      Session session = Pdo.createSession(sessionInfo);
      try {
        session.makeCurrent();
        context = Pdo.createDomainContext(session);

        ModificationTracker pdoTracker = ModificationTracker.getInstance();   // create the tracker but don't start the thread
        pdoTracker.setSession(session);

        List<MavenProject> projects;
        if (isExecutionRecursive()) {
          projects = new ArrayList<>();
          projects.add(getMavenProject());
        }
        else {
          projects = getMavenProject().getCollectedProjects();
        }

        processBundles(session, projects);
      }
      finally {
        session.close();
      }
    }
    catch (RuntimeException ex) {
      throw new MojoExecutionException("cannot connect to the backend", ex);
    }

    getLogger().info("{} bundle{} processed, {} warning{}, {} error{}, {} update{}",
                     bundleCount, bundleCount == 1 ? "" : "s",
                     warnings, warnings == 1 ? "" : "s",
                     errors, errors == 1 ? "" : "s",
                     updates, updates == 1 ? "" : "s");

    if (errors > 0) {
      throw new MojoExecutionException("pushing to the database failed");
    }
  }

  /**
   * Gets the compile classpath of a project.<br>
   * The project's output directory followed by its resolved compile dependencies.
   *
   * @param project the project
   * @return the classpath elements
   * @throws DependencyResolutionException if the dependencies cannot be resolved
   */
  protected List<String> getCompileClasspathElements(MavenProject project) throws DependencyResolutionException {
    return ProjectDependencies.getClasspathElements(getRepositorySystem(), getMavenSession().getRepositorySession(),
                                                    project, ProjectDependencies.COMPILE_SCOPES);
  }

  /**
   * Process the bundles.
   *
   * @param session the open database session
   * @param projects the maven projects
   * @throws MojoExecutionException if failed
   */
  protected void processBundles(Session session, List<MavenProject> projects) throws MojoExecutionException {
    try {
      for (MavenProject project : projects) {
        List<String> classpathElements = getCompileClasspathElements(project);
        ClassLoader classLoader = createProjectClassLoader(classpathElements);

        session.transaction(() -> {   // within tx to minimize cache reloads in running application servers
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
              resourceName = resourceName.replace('.', '/') + Constants.PROPS_EXTENSION;
              if (verbosityLevel.isDebug()) {
                getLogger().debug("processing {}", resourceName);
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
          return null;
        });
      }
    }
    catch (DependencyResolutionException dx) {
      throw new MojoExecutionException("cannot determine classpath elements", dx);
    }
    catch (MalformedURLException mx) {
      getLogger().error("cannot create project classloader", mx);
    }
    catch (RuntimeException rex) {
      throw new MojoExecutionException("processing the bundles failed", rex);
    }
  }

  /**
   * Creates the classloader to load the bundles of a project from.
   *
   * @param classpathElements the compile classpath elements of the project
   * @return the classloader
   * @throws MalformedURLException if a classpath element is not a valid path
   */
  protected ClassLoader createProjectClassLoader(List<String> classpathElements) throws MalformedURLException {
    // the plugin's classloader is the plexus class realm, which is a URLClassLoader
    return new ProjectClassLoader((URLClassLoader) getClass().getClassLoader(), classpathElements);
  }

  /**
   * Process the given bundle resource name.<br>
   * The resource name must not start with a leading slash because this method directly uses
   * the classloader's getResourceAsStream method.
   *
   * @param classLoader the project classloader
   * @param resourceName the bundle resource name without a leading slash
   * @param bundleName the original bundle class name
   * @param locale the locale, null if default
   * @throws MojoExecutionException if execution failed
   */
  public abstract void processBundle(ClassLoader classLoader, String bundleName, String locale, String resourceName) throws MojoExecutionException;

  /**
   * Loads resource properties via classloader.
   *
   * @param classLoader the classloader to use
   * @param resourceName the resource name (with locale)
   * @return the properties, null if not found
   * @throws IOException if IO error
   */
  protected Properties loadProperties(ClassLoader classLoader, String resourceName) throws IOException {
    try (InputStream is = classLoader.getResourceAsStream(resourceName)) {
      if (is != null) {
        Reader reader = new InputStreamReader(is, Settings.getEncodingCharset());
        Properties props = new OrderedProperties();
        props.load(reader);
        return props;
      }
      return null;
    }
  }

  /**
   * Loads resource properties from the filesystem.
   *
   * @param file the file
   * @return the properties, null if not found
   * @throws IOException if IO error
   */
  protected Properties loadProperties(File file) throws IOException {
    if (file.exists()) {
      try (Reader reader = new FileReader(file, Settings.getEncodingCharset())) {
        Properties properties = new OrderedProperties();
        properties.load(reader);
        return properties;
      }
    }
    return null;
  }
}
