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

package org.tentackle.maven.plugin.wizard;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;

import org.tentackle.fx.Fx;
import org.tentackle.fx.ThemeUtilities;
import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.stage.Stage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Base wizard mojo.
 *
 * @author harald
 */
public abstract class AbstractWizardMojo extends AbstractTentackleMojo {

  /**
   * The model name.
   */
  @Parameter(defaultValue = ModelManager.DEFAULT_MODEL_NAME,
             property = "tentackle.modelName")
  protected String modelName;

  /**
   * Directory holding the model files to be processed.<br>
   * Ignored if fileset explicitly given.
   */
  @Parameter(defaultValue = "${project.build.directory}/wurbel/model", property = "tentackle.modelDir")
  protected File modelDir;

  /**
   * Directory holding the status info.<br>
   * Used to store the next free classId per profile in case multiple PDOs are
   * generated without an intermediate mvn clean package/install or wurbel run.<br>
   * Must be a subdirectory of the target directory to be removed via mvn clean.
   */
  @Parameter(defaultValue = "${project.build.directory}/wizard")
  private File statusDir;

  /**
   * The model defaults.
   */
  @Parameter(property = "tentackle.modelDefaults")
  protected String modelDefaults;

  /**
   * The entity aliases.
   */
  @Parameter(property = "tentackle.entityAliases")
  protected String entityAliases;

  /**
   * The list of file sets containing the model.<br>
   * If set, the modelDir is ignored.
   */
  @Parameter
  protected List<FileSet> filesets;

  /**
   * The profiles.
   */
  @Parameter(required = true)
  private List<Profile> profiles;

  /**
   * The directory holding the templates.
   */
  @Parameter(defaultValue = "${project.basedir}/templates")
  private File templateDir;

  /**
   * Load the model from the plugin dependencies on the classpath.
   */
  @Parameter(defaultValue = "true")
  private boolean loadModelFromDependencies;

  /**
   * Named, isolated {@code ModuleLayer}s for dependencies that must run isolated from each other -
   * typically because two of them need different, otherwise conflicting versions of the same module,
   * or because the application itself is built with module layers.
   * <p>
   * If empty (the default), the goal runs exactly as without this feature at all: against the plain
   * plugin classpath (populated the usual way, by declaring the application's dependencies as
   * {@code <dependency>} elements of this plugin). If not empty, this plugin builds the configured
   * {@code ModuleLayer} graph at goal-execution time and installs the {@link #leafLayer}'s classloader
   * as the thread's context classloader for the duration of the goal - which is what every existing,
   * layer-unaware {@code ServiceFactory.getServiceFinder()}/{@code Model.loadFromResources()} call
   * (and, for {@code browse}/{@code script}, the application's own domain/PDO/persistence classes)
   * transparently resolves from. See the {@code layers.md} documentation.
   *
   * @see #leafLayer
   */
  @Parameter
  private List<Layer> layers;

  /**
   * The name of the layer holding the application's own PDO/domain/persistence classes, i.e. the
   * layer whose classloader is installed as the thread's context classloader while the goal runs.
   * Required if {@link #layers} is not empty and must match the {@code name} of one of them.
   */
  @Parameter
  private String leafLayer;

  /**
   * Enables dark mode.<br>
   * If missing, the mode is determined from the desktop settings.
   */
  @Parameter
  private Boolean darkMode;

  /**
   * Selects the theme.<br>
   * The AtlantaFX themes are: {@code Primer}, {@code Cupertino}, and {@code Nord}.<br>
   * The default is {@code Primer}.
   */
  @Parameter
  private String themeName;

  /**
   * The model.
   */
  private Model model;


  /**
   * Gets the model to use.
   *
   * @return the model
   */
  public Model getModel() {
    if (model == null) {
      model = ModelManager.getInstance().getModel(modelName);
    }
    return model;
  }

  /**
   * Gets the profiles.
   *
   * @param <T> the profile type
   * @param clazz the concrete profile class
   * @return the list of profiles
   * @throws MojoExecutionException if profile names are not unique
   */
  @SuppressWarnings("unchecked")
  public <T extends Profile> List<T> getProfiles(Class<T> clazz) throws MojoExecutionException {
    List<T> list = new ArrayList<>();
    for (Profile profile : profiles) {
      if (clazz.isAssignableFrom(profile.getClass())) {
        if (list.contains(profile)) {
          throw new MojoExecutionException("profile " + profile + " configured more than once");
        }
        list.add((T) profile);
      }
    }
    return list;
  }


  /**
   * Gets the template directory.
   *
   * @return the directory holding the wizard templates
   */
  public File getTemplateDir() {
    return templateDir;
  }

  /**
   * Gets the status directory.
   *
   * @return the status dir
   */
  public File getStatusDir() {
    return statusDir;
  }

  /**
   * Gets the configured isolated module layers.
   *
   * @return the layers, never null
   */
  public List<Layer> getLayers() {
    return layers == null ? Collections.emptyList() : layers;
  }

  /**
   * Returns whether this goal runs against an isolated module layer graph.
   *
   * @return true if {@link #getLayers()} is not empty
   */
  public boolean isLayered() {
    return !getLayers().isEmpty();
  }

  /**
   * Gets the name of the layer holding the application's own PDO/domain/persistence classes.
   *
   * @return the leaf layer name, null if not {@link #isLayered()}
   */
  public String getLeafLayer() {
    return leafLayer;
  }

  /**
   * Opens a scope installing {@link #getLeafLayer()}'s classloader as the thread's context
   * classloader for the duration of a goal, if {@link #isLayered()}.<br>
   * A no-op if {@link #layers} is not configured, guaranteeing the goal behaves exactly as before
   * this feature existed: no layer graph is built and the thread's context classloader is left alone.
   *
   * @return the scope, to be closed (e.g. via try-with-resources) once the goal has finished
   * @throws MojoExecutionException if the layer graph could not be built
   */
  protected LayerScope openLayerScope() throws MojoExecutionException {
    if (!isLayered()) {
      return LayerScope.NOOP;
    }
    LayerGraphResolver resolver = new LayerGraphResolver(this);
    Map<String, ModuleLayer> builtLayers = resolver.build();
    ClassLoader leafLoader = resolver.getLeafClassLoader(builtLayers);
    ClassLoader previous = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(leafLoader);
    getLogger().debug("installed leaf layer '{}' as the thread's context classloader", leafLayer);
    return new LayerScope(previous);
  }

  /**
   * Restores the thread's context classloader that was active before {@link #openLayerScope()}.
   */
  protected static final class LayerScope implements AutoCloseable {

    private static final LayerScope NOOP = new LayerScope(null);

    private final ClassLoader previous;

    private LayerScope(ClassLoader previous) {
      this.previous = previous;
    }

    @Override
    public void close() {
      if (previous != null) {
        Thread.currentThread().setContextClassLoader(previous);
      }
    }

  }

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    loadModel();
  }


  /**
   * Gets the model defaults.
   *
   * @return the defaults, null if none
   * @throws MojoExecutionException if parsing the model defaults failed
   */
  protected ModelDefaults getModelDefaults() throws MojoExecutionException {
    if (modelDefaults == null) {
      return null;
    }
    try {
      return new ModelDefaults(modelDefaults);
    }
    catch (ModelException mex) {
      throw new MojoExecutionException(mex.getMessage(), mex);
    }
  }


  /**
   * Gets the entity aliases.
   *
   * @return the aliases, null if none
   * @throws MojoExecutionException if parsing the aliases failed
   */
  protected EntityAliases getEntityAliases() throws MojoExecutionException {
    if (entityAliases == null) {
      return null;
    }
    try {
      return new EntityAliases(entityAliases);
    }
    catch (ModelException mex) {
      throw new MojoExecutionException(mex.getMessage(), mex);
    }
  }

  /**
   * Loads the model.
   * <p>
   * Notice: the model is only needed by the {@link PdoMojo}, but we configure it for the whole plugin.
   *
   * @throws MojoFailureException if the model is inconsistent
   * @throws MojoExecutionException if loading failed due to some other error
   */
  protected void loadModel() throws MojoFailureException, MojoExecutionException {
    int errors = 0;

    if (loadModelFromDependencies) {
      try {
        getModel().loadFromResources(true);
      }
      catch (ModelException e) {
        throw new MojoFailureException(e);
      }
    }

    if (filesets != null && !filesets.isEmpty()) {
      // explicit filesets given instead of model dir
      for (FileSet fileSet : filesets) {
        errors += processFileSet(fileSet);
      }
    }
    else {
      // all from model dir
      String[] files = modelDir.isDirectory() ? modelDir.list() : null;
      if (files != null && files.length > 0) {
        final FileSet fs = new FileSet();
        fs.setDirectory(modelDir.getPath());
        errors += processFileSet(fs);
      }
      else {
        getLogger().warn("{} {}", modelDir.exists() ? "empty modelDir" : "no modelDir", modelDir.getAbsolutePath());
      }
    }

    if (errors > 0) {
      throw new MojoFailureException(errors + " model errors");
    }

    try {
      getLogger().info("{} entities loaded", getModel().getAllEntities().size());
    }
    catch (ModelException e) {
      throw new MojoExecutionException("cannot determine entities", e);
    }
  }


  /**
   * Processes a fileset to load the model.
   *
   * @param fileSet the fileset containing the model
   * @return the number of errors
   * @throws MojoExecutionException if model defaults or entity aliases could not be determined
   */
  protected int processFileSet(FileSet fileSet) throws MojoExecutionException {
    int errors = 0;

    if (fileSet.getDirectory() == null) {
      // directory missing: use modelDir as default
      fileSet.setDirectory(modelDir.getAbsolutePath());
    }

    File dir = new File(fileSet.getDirectory());
    String modelDirName = dir.getPath();

    try {
      getModel().setModelDefaults(getModelDefaults());
      getModel().setEntityAliases(getEntityAliases());
      getModel().loadFromDirectory(modelDirName, true);
    }
    catch (ModelException mex) {
      getLogger().error("parsing model failed in directory {}:\n{}", modelDirName, mex.getMessage());
      errors++;
    }

    return errors;
  }


  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      if (templateDir.exists()) {
        if (!templateDir.isDirectory()) {
          throw new MojoExecutionException(templateDir.getPath() + " is not a directory");
        }
      }
      else {
        templateDir.mkdirs();
        getLogger().info("template directory created: {}", templateDir.getPath());
        installTemplates();
      }
      new LayerGraphResolver(this).validateLayers();
      return true;
    }
    return false;
  }


  /**
   * Copies the templates to the template directory.<br>
   * Overwrites any existing templates.
   *
   * @throws MojoExecutionException if failed
   */
  protected void installTemplates() throws MojoExecutionException {
    installTemplate(Constants.CATEGORY_PDO, "DomainImplementation.ftl");
    installTemplate(Constants.CATEGORY_PDO, "DomainInterface.ftl");
    installTemplate(Constants.CATEGORY_PDO, "PdoInterface.ftl");
    installTemplate(Constants.CATEGORY_PDO, "PersistenceImplementation.ftl");
    installTemplate(Constants.CATEGORY_PDO, "PersistenceInterface.ftl");
    installTemplate(Constants.CATEGORY_PDO, "RemoteInterface.ftl");
    installTemplate(Constants.CATEGORY_PDO, "RemoteImplementation.ftl");

    installTemplate(Constants.CATEGORY_OPERATION, "DomainImplementation.ftl");
    installTemplate(Constants.CATEGORY_OPERATION, "DomainInterface.ftl");
    installTemplate(Constants.CATEGORY_OPERATION, "OperationInterface.ftl");
    installTemplate(Constants.CATEGORY_OPERATION, "PersistenceImplementation.ftl");
    installTemplate(Constants.CATEGORY_OPERATION, "PersistenceInterface.ftl");
    installTemplate(Constants.CATEGORY_OPERATION, "RemoteInterface.ftl");
    installTemplate(Constants.CATEGORY_OPERATION, "RemoteImplementation.ftl");

    installTemplate(Constants.CATEGORY_SCRIPT, "groovy-example.ftl");
  }

  private void installTemplate(String category, String template) throws MojoExecutionException {
    File dir = new File(templateDir, category);
    dir.mkdirs();
    File file = new File(dir, template);
    String path = "/templates/" + category + "/" + template;
    String text = loadResourceFileIntoString(path);
    try (PrintStream ps = new PrintStream(new FileOutputStream(file))) {
      ps.print(text);
      getLogger().info("installed {}-template {}", category, template);
    }
    catch (IOException e) {
      throw new MojoExecutionException("cannot install template " + path, e);
    }
  }


  /**
   * Registers a handler for uncaught exceptions.
   */
  protected void registerUncaughtExceptionHandler() {
    Thread.setDefaultUncaughtExceptionHandler(getUncaughtExceptionHandler());             // all threads
    Thread.currentThread().setUncaughtExceptionHandler(getUncaughtExceptionHandler());    // overwrite existing handler for fx thread
  }

  /**
   * Configures the FX theme.
   */
  protected void configureFx() {
    ThemeUtilities.ThemePreferences preferences = new ThemeUtilities.ThemePreferences(darkMode, themeName);
    ThemeUtilities.getInstance().applyThemePreferences(preferences);
    if (darkMode == null) {
      Platform.getPreferences().colorSchemeProperty().subscribe(
        colorScheme -> ThemeUtilities.getInstance().setDarkMode(colorScheme == ColorScheme.DARK));
    }
  }

  /**
   * Gets the stage if this an interactive FX application.
   *
   * @return the stage, null if console application
   */
  protected abstract Stage getStage();


  private Thread.UncaughtExceptionHandler getUncaughtExceptionHandler() {
    return (_, e) -> {
      getLogger().error("unhandled exception detected", e);
      Stage stage = getStage();
      if (stage != null) {
        Platform.runLater(() -> Fx.error(stage, e.getMessage(), e));
      }
    };
  }

}
