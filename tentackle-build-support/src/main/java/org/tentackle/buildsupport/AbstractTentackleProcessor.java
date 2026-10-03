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


package org.tentackle.buildsupport;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract Tentackle annotation processor.
 * <p>
 * Adds the concerns shared by Tentackle's processors: a {@link ResourceManager} per output directory,
 * verbosity handling via the processor option {@code verbosity} ({@code info} or {@code debug}),
 * and a {@link #cleanup()} that closes all resources. The supported source version is always the latest.
 * <p>
 * In order to avoid warnings, annotation processors should be annotated
 * with {@code @SupportedOptions("verbosity")}.
 *
 * @author harald
 */
public abstract class AbstractTentackleProcessor extends AbstractProcessor {

  private File sourceDir;                                     // source directory for java files
  private final Map<File,ResourceManager> resourceManagers;   // manager for generated resources
  private boolean debugLogging;                               // true if debug logging
  private boolean infoLogging;                                // true if info logging



  /**
   * Creates an annotation processor.
   */
  public AbstractTentackleProcessor() {
    resourceManagers = new HashMap<>();
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latest();
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnv) {
    super.init(processingEnv);
    String verbosity = processingEnv.getOptions().get("verbosity");
    if (verbosity != null) {
      if ("info".equalsIgnoreCase(verbosity)) {
        infoLogging = true;
      }
      else if ("debug".equalsIgnoreCase(verbosity)) {
        debugLogging = true;
        infoLogging  = true;
      }
    }
  }



  /**
   * Gets whether to log info messages.
   *
   * @return true if info logging
   */
  public boolean isInfoLogging() {
    return infoLogging;
  }


  /**
   * Gets whether to log debug messages.
   *
   * @return true if debug logging
   */
  public boolean isDebugLogging() {
    return debugLogging;
  }



  /**
   * Gets the resource manager for a directory.<br>
   * The manager is created on first access and cached until {@link #cleanup()}.
   *
   * @param directory the base directory
   * @return the resource manager
   */
  public ResourceManager getResourceManager(File directory) {
    return resourceManagers.computeIfAbsent(directory, ResourceManager::new);
  }

  /**
   * Gets the resource managers.
   *
   * @return the resource managers, never null
   */
  public Collection<ResourceManager> getResourceManagers() {
    return resourceManagers.values();
  }


  /**
   * Closes all resource managers and removes them from the cache.
   *
   * @throws IOException if cleanup failed
   */
  public void cleanup() throws IOException {
    for (ResourceManager resourceManager: resourceManagers.values()) {
      resourceManager.close();
    }
    resourceManagers.clear();
  }


  /**
   * Gets the processing environment.
   *
   * @return the processing environment
   */
  public ProcessingEnvironment getProcessingEnvironment() {
    return processingEnv;
  }


  /**
   * Gets the source directory.<br>
   * An optional hint for processors that need to know where the sources live.
   * It is not used by the processors of this module and not set by the Tentackle Maven plugins.
   *
   * @return the source file dir, null if not set
   */
  public File getSourceDir() {
    return sourceDir;
  }

  /**
   * Sets the source directory.
   *
   * @param sourceDir the source dir, null if unknown
   */
  public void setSourceDir(File sourceDir) {
    this.sourceDir = sourceDir;
  }

}

