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

package org.tentackle.maven.plugin.mcp;

import org.tentackle.model.Entity;
import org.tentackle.model.EntityInfo;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendException;
import org.tentackle.sql.BackendFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Holds the loaded model and everything the tools need to work on it consistently.
 * <p>
 * Two concerns live here that no single tool can solve on its own:
 * <ul>
 * <li><strong>Consistency.</strong> The model implementation synchronizes each method, which makes a single
 *     getter safe but not a tool call that walks an entity through a hundred of them. Helidon serves concurrent
 *     requests on virtual threads, so a whole invocation runs under {@link #call(ModelOperation)} and a refresh can
 *     never land halfway through a description.</li>
 * <li><strong>Staleness.</strong> The server outlives many builds. Model files are re-scanned, at most once per
 *     staleness interval, and the model is refreshed when they are moved.</li>
 * </ul>
 *
 * @author harald
 */
public class ModelSession {

  /** The extension of a model source file. */
  public static final String MODEL_FILE_EXTENSION = ".map";

  /** Default minimum delay between two checks for changed model files in milliseconds. */
  public static final long DEFAULT_STALENESS_CHECK_MILLIS = 1000;

  private final ModelConfiguration config;
  private final long stalenessCheckMillis;
  private final ReentrantLock lock;
  private final List<String> warnings;
  private final Collection<Backend> backends;

  private Model model;
  private Map<String, Long> fileState;
  private Map<String, String> entitySources;
  private long lastStalenessCheck;

  /**
   * Creates a session.
   *
   * @param config the model configuration
   * @param stalenessCheckMillis the minimum delay between two checks for changed model files
   */
  public ModelSession(ModelConfiguration config, long stalenessCheckMillis) {
    this.config = config;
    this.stalenessCheckMillis = stalenessCheckMillis;
    this.lock = new ReentrantLock();
    this.warnings = new ArrayList<>();
    this.backends = new ArrayList<>();
    this.fileState = new HashMap<>();
    this.entitySources = new HashMap<>();
  }


  /**
   * An operation on the loaded model.
   *
   * @param <T> the result type
   */
  @FunctionalInterface
  public interface ModelOperation<T> {
    /**
     * Performs the operation.
     *
     * @return the result
     * @throws ModelException if the model is inconsistent
     */
    T execute() throws ModelException;
  }

  /**
   * Invokes an operation on the model, exclusively.
   * <p>
   * The model is checked for changed files first, so that a tool never answers from a model known to be
   * stale.
   *
   * @param <T> the result type
   * @param operation the operation
   * @return the result of the operation
   * @throws ModelException if the model is inconsistent
   */
  public <T> T call(ModelOperation<T> operation) throws ModelException {
    lock.lock();
    try {
      checkStale();
      return operation.execute();
    }
    finally {
      lock.unlock();
    }
  }

  /**
   * Gets the model configuration.
   *
   * @return the configuration
   */
  public ModelConfiguration getConfiguration() {
    return config;
  }

  /**
   * Gets the model.
   *
   * @return the model, never null
   */
  public Model getModel() {
    if (model == null) {
      throw new IllegalStateException("model '" + config.modelName() + "' is not loaded");
    }
    return model;
  }

  /**
   * Gets the backends the model is validated against.
   *
   * @return the backends, empty if none configured
   */
  public Collection<Backend> getBackends() {
    return backends;
  }

  /**
   * Gets the backend for the given name.
   *
   * @param name the backend name
   * @return the backend
   * @throws IllegalArgumentException if there is no such backend
   */
  public Backend getBackend(String name) {
    for (Backend backend : backends) {
      if (backend.getName().equalsIgnoreCase(name)) {
        return backend;
      }
    }
    try {
      return BackendFactory.getInstance().getBackendByName(name);
    }
    catch (BackendException bex) {
      throw new IllegalArgumentException("unknown backend '" + name + "', available are: " +
                                         String.join(", ", allBackendNames()), bex);
    }
  }

  /**
   * Gets the names of all backends known to the runtime.
   *
   * @return the names
   */
  public static List<String> allBackendNames() {
    return BackendFactory.getInstance().getAllBackends().stream().map(Backend::getName).sorted().toList();
  }

  /**
   * Gets the names of the configured backends.
   *
   * @return the names, empty if none configured
   */
  public List<String> backendNames() {
    return backends.stream().map(Backend::getName).toList();
  }

  /**
   * Gets the configuration warnings.
   * <p>
   * Reported by every overview and validation result so that missing model defaults are never mistaken for a
   * broken model.
   *
   * @return the warnings, empty if the configuration is complete
   */
  public List<String> getWarnings() {
    return List.copyOf(warnings);
  }

  /**
   * Loads the model.
   * <p>
   * All sources are loaded without resolving relations, then {@link Model#updateRelations()} wires the whole set
   * at once. This is the only order in which relations across dependency models resolve.
   *
   * @throws ModelException if the model is inconsistent
   */
  public void load() throws ModelException {
    lock.lock();
    try {
      model = ModelManager.getInstance().getModel(config.modelName());
      model.clearModel();

      warnings.clear();
      if (config.modelDefaults() == null) {
        warnings.add("no model defaults configured: the model is loaded exactly as written, which usually " +
                     "differs from what the build sees and may show integrity errors that do not exist in the build");
      }
      model.setModelDefaults(config.modelDefaults());
      model.setEntityAliases(config.entityAliases());
      model.setSchemaNameMapped(config.schemaNameMapped());

      backends.clear();
      if (config.backendNames() == null) {
        warnings.add("no backends configured: names, types and sizes are not validated against a database backend");
      }
      else {
        try {
          backends.addAll(BackendFactory.getInstance().getBackends(config.backendNames()));
          model.getEntityFactory().setBackends(backends);
        }
        catch (BackendException bex) {
          throw new ModelException("backends '" + config.backendNames() + "': " + bex.getMessage() +
                                   " (available: " + String.join(", ", allBackendNames()) + ")", bex);
        }
      }

      for (String modelDir : config.modelDirs()) {
        model.loadFromDirectory(modelDir, false);
      }
      if (config.fromResources()) {
        model.loadFromResources(false);
      }
      model.updateRelations();

      fileState = scanModelDirs();
      entitySources = snapshotSources();
      lastStalenessCheck = System.currentTimeMillis();
    }
    finally {
      lock.unlock();
    }
  }

  /**
   * Reloads the model.
   *
   * @param full true for a complete reload, false to refresh changed files only
   * @return the names of the entities whose source changed, never null
   * @throws ModelException if the model is inconsistent
   */
  public List<String> reload(boolean full) throws ModelException {
    lock.lock();
    try {
      Map<String, String> before = entitySources;
      if (full) {
        load();
      }
      else {
        model.refreshModel();
        fileState = scanModelDirs();
        entitySources = snapshotSources();
        lastStalenessCheck = System.currentTimeMillis();
      }
      return changedEntities(before, entitySources);
    }
    finally {
      lock.unlock();
    }
  }

  /**
   * Refreshes the model if any model file changed since the last check.
   * <p>
   * A removed file forces a full reload: {@link Model#refreshModel()} picks up new and modified files, but the
   * name, table, and class-id maps keep entities whose source is gone.
   *
   * @return the names of the entities whose source changed, empty if nothing moved
   */
  public List<String> checkStale() {
    lock.lock();
    try {
      if (config.modelDirs().isEmpty()) {
        return List.of();     // a model read from the classpath only is a snapshot
      }
      long now = System.currentTimeMillis();
      if (now - lastStalenessCheck < stalenessCheckMillis) {
        return List.of();
      }
      lastStalenessCheck = now;

      Map<String, Long> current = scanModelDirs();
      if (current.equals(fileState)) {
        return List.of();
      }
      boolean removed = !current.keySet().containsAll(fileState.keySet());
      return reload(removed);
    }
    catch (ModelException mex) {
      // the model on disk became inconsistent: keep serving the last good one, but say so
      addWarning("model files changed but could not be reloaded: " + mex.getMessage());
      return List.of();
    }
    finally {
      lock.unlock();
    }
  }

  /**
   * Gets the model source of an entity as loaded.
   *
   * @param entity the entity
   * @return the source, null if unknown
   */
  public String getModelSource(Entity entity) {
    return entitySources.get(entity.getName());
  }

  /**
   * Gets the number of model source files found in the configured directories.
   *
   * @return the file count
   */
  public int getModelFileCount() {
    return fileState.size();
  }


  /**
   * Adds a warning to be reported with every overview and validation result.
   *
   * @param warning the warning
   */
  protected void addWarning(String warning) {
    if (!warnings.contains(warning)) {
      warnings.add(warning);
    }
  }

  private Map<String, Long> scanModelDirs() {
    Map<String, Long> state = new HashMap<>();
    for (String modelDir : config.modelDirs()) {
      Path dir = Path.of(modelDir);
      if (!Files.isDirectory(dir)) {
        continue;
      }
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + MODEL_FILE_EXTENSION)) {
        for (Path file : stream) {
          state.put(file.toAbsolutePath().toString(), Files.getLastModifiedTime(file).toMillis());
        }
      }
      catch (IOException iox) {
        // a directory that cannot be scanned is treated as unchanged: the model itself reports the real error
        addWarning("cannot scan model directory " + modelDir + ": " + iox.getMessage());
      }
    }
    return state;
  }

  private Map<String, String> snapshotSources() throws ModelException {
    Map<String, String> sources = new LinkedHashMap<>();
    for (Entity entity : model.getAllEntities()) {
      EntityInfo info = model.getEntityInfo(entity);
      sources.put(entity.getName(), info == null ? "" : info.getModelSource());
    }
    return sources;
  }

  private List<String> changedEntities(Map<String, String> before, Map<String, String> after) {
    Set<String> names = new TreeSet<>();
    names.addAll(before.keySet());
    names.addAll(after.keySet());
    List<String> changed = new ArrayList<>();
    for (String name : names) {
      if (!Objects.equals(before.get(name), after.get(name))) {
        changed.add(name);
      }
    }
    return changed;
  }

}
