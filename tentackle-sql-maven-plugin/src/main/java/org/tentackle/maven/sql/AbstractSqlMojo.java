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


package org.tentackle.maven.sql;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;
import org.wurbelizer.misc.Settings;

import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.print.EntityPrinter;
import org.tentackle.model.print.PrintConfiguration;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendFactory;
import org.tentackle.sql.BackendInfo;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;


/**
 * Base tentackle sql mojo.
 *
 * @author harald
 */
public abstract class AbstractSqlMojo extends AbstractTentackleMojo {

  /**
   * The Maven Project.
   */
  @Parameter(defaultValue = "${project}",
             readonly = true,
             required = true)
  protected MavenProject mvnProject;

  /**
   * Directory holding the model files to be processed.
   */
  @Parameter(defaultValue = "${project.build.directory}/wurbel/model",
             property = "tentackle.modelDir")
  protected File modelDir;

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
   * Map schema names to flat table names.
   */
  @Parameter(property = "tentackle.mapSchemas")
  protected boolean mapSchemas;

  /**
   * Directory holding the SQL generated scripts.
   */
  @Parameter(defaultValue = "${project.build.directory}/sql",
             property = "tentackle.sqlDir")
  protected File sqlDir;

  /**
   * The backend property files.
   */
  @Parameter
  protected List<FileSet> backendProperties;

  /**
   * The backend names.<br>
   * Separated by commas.<br>
   * "all" means generate code for all backends found in classpath.
   */
  @Parameter
  protected String backendNames;

  /**
   * Backend configuration via pom if not from property files.
   */
  @Parameter
  protected List<BackendInfoParameter> backends;

  /**
   * Optional directory where to dump the model.<br>
   * Useful for debugging to verify that the model was interpreted correctly
   * or to pretty-print the model source.
   */
  @Parameter
  protected File dumpDir;

  /**
   * Model dump should be dumped as comment blocks (default).
   */
  @Parameter(defaultValue = "true")
  protected boolean dumpAsComment;

  /**
   * Model dump should include variables (default).
   */
  @Parameter(defaultValue = "true")
  protected boolean dumpVariables;

  /**
   * Minimum number of spaces between columns in attribute section (default = 2).
   */
  @Parameter(defaultValue = "2")
  protected int dumpColumnGap;

  /**
   * Optional annotations that should be printed as attribute options.
   */
  @Parameter
  protected String dumpAnnotationsAsOptions;

  /**
   * Use IF EXISTS for drop index, constraint and column if the backend supports it.<br>
   * If not supported, a warning will be logged.
   */
  @Parameter
  protected boolean useDropIfExists;


  /**
   * total number of errors.
   */
  protected int totalErrors;

  /**
   * The backends to create SQL code for.
   * <p>
   * Maps the backend-name to the backend.
   */
  protected Map<String, BackendInfo> backendInfos;

  /**
   * Connectable backends to create SQL code for.
   * <p>
   * Maps the jdbc-Url to the backend.
   */
  protected Map<String, BackendInfo> connectableBackendInfos;

  /**
   * The created SQL file.
   */
  protected File sqlFile;

  /**
   * Output directory for diagnostics
   */
  protected String sqlDirName;

  /**
   * Writer to the SQL file.
   */
  protected Writer sqlWriter;


  /**
   * Gets the created sql file name without leading path.
   *
   * @return the filename
   */
  protected abstract String getSqlFileName();


  /**
   * The connection to the backend.
   */
  private Connection connection;

  /**
   * Gets the open connection.<br>
   * Opens it if it is closed.
   *
   * @param backendInfo the connection info
   * @return the open connection
   * @throws MojoExecutionException if connection failed
   */
  protected Connection getConnection(BackendInfo backendInfo) throws MojoExecutionException {
    if (connection == null) {
      try {
        connection = backendInfo.connect();
      }
      catch (SQLException sqx) {
        throw new MojoExecutionException("cannot connect to " + backendInfo, sqx);
      }
    }
    return connection;
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
   * Creates the sqlFile, sqlDirName and sqlWriter.
   *
   * @param backendInfo the backend info
   * @throws MojoExecutionException if sql file could not be created
   */
  protected void createSqlFile(BackendInfo backendInfo) throws MojoExecutionException {
    sqlFile = new File(new File(sqlDir, backendInfo.getBackend().getName()), getSqlFileName());
    try {
      // create missing directories, if any
      File dir = sqlFile.getParentFile();
      sqlDirName = getCanonicalPath(dir);
      dir.mkdirs();
      // create the SQL output file
      sqlWriter = Files.newBufferedWriter(sqlFile.toPath(), Settings.encodingCharset,
                                          StandardOpenOption.CREATE,
                                          StandardOpenOption.TRUNCATE_EXISTING);
    }
    catch (IOException iox) {
      throw new MojoExecutionException("cannot create sql file " + sqlFile.getAbsolutePath(), iox);
    }
  }

  /**
   * Opens the resources such as session and creates the SQL file.
   *
   * @param backendInfo the backend info
   * @throws MojoExecutionException if sql file could not be created
   */
  protected void openResources(BackendInfo backendInfo) throws MojoExecutionException {
    createSqlFile(backendInfo);
  }

  /**
   * Closes the resources.
   *
   * @param backendInfo the backend info
   * @throws MojoExecutionException if closing failed
   */
  protected void closeResources(BackendInfo backendInfo) throws MojoExecutionException {
    if (connection != null) {
      try {
        connection.close();
        connection = null;
      }
      catch (SQLException sqx) {
        throw new MojoExecutionException(
          "cannot close connection " + connection, sqx);
      }
    }
    if (sqlWriter != null) {
      try {
        sqlWriter.close();
        sqlWriter = null;
      }
      catch (IOException iox) {
        throw new MojoExecutionException("cannot close sql file " + sqlFile.getAbsolutePath(), iox);
      }
    }
  }

  /**
   * Process all files in a fileset.
   *
   * @param backendInfo the backend info
   * @param fileSet the fileset
   * @throws MojoExecutionException if processing failed
   */
  protected abstract void processFileSet(BackendInfo backendInfo, FileSet fileSet) throws MojoExecutionException;



  @Override
  protected void validate() throws MojoExecutionException {
    mavenProject = mvnProject;    // fix for mavenProject is null, for whatever reason???
    super.validate();
    if (modelDir == null) {
      throw new MojoExecutionException("missing tentackle.modelDir");
    }
    if (modelDir.getPath().contains("${")) {
      throw new MojoExecutionException("undefined variable(s) in modelDir: " + modelDir.getPath());
    }
    if (sqlDir == null) {
      throw new MojoExecutionException("missing tentackle.sqlDir");
    }
    if (sqlDir.getPath().contains("${")) {
      throw new MojoExecutionException("undefined variable(s) in sqlDir: " + sqlDir.getPath());
    }

    findResourceDirs();
    backendInfos = new HashMap<>();
    connectableBackendInfos = new HashMap<>();

    if (backendNames != null) {
      if ("all".equalsIgnoreCase(backendNames)) {
        for (Backend backend: BackendFactory.getInstance().getAllBackends()) {
          backendInfos.put(backend.getName(), new BackendInfo(backend));
        }
      }
      else  {
        for (String backendName: backendNames.split(",")) {
          BackendInfo backendInfo = new BackendInfo(backendName.trim());
          backendInfos.put(backendInfo.getBackend().getName(), backendInfo);
        }
      }
    }

    if (backendProperties != null) {
      // determine URL from property file(s)
      for (FileSet fileset: backendProperties) {
        FileSetManager fileSetManager = new FileSetManager(getLog(), verbosityLevel.isDebug());
        List<String> propertiesDirNames;
        if (fileset.getDirectory() == null) {
          propertiesDirNames = resourceDirs;  // try all resource directories
          if (propertiesDirNames == null || propertiesDirNames.isEmpty()) {
            throw new MojoExecutionException(
                    "no <directory> given in <fileset> of <backendProperties> and no resource directories found");
          }
        }
        else  {
          propertiesDirNames = new ArrayList<>();
          propertiesDirNames.add(fileset.getDirectory());   // use given directory
        }
        for (String propertiesDirName: propertiesDirNames) {
          fileset.setDirectory(propertiesDirName);
          String[] fileNames = fileSetManager.getIncludedFiles(fileset);
          if (fileNames.length > 0) {
            for (String filename : fileNames) {
              File propertiesFile = new File(new File(propertiesDirName), filename);
              Properties props = new Properties();
              try (InputStream is = new FileInputStream(propertiesFile)) {
                getLog().debug("loading property file " + propertiesFile.getAbsolutePath());
                props.load(is);
                BackendInfo backendInfo = new BackendInfo(props);
                // props infos will replace those loaded by name only
                backendInfos.put(backendInfo.getBackend().getName(), backendInfo);
                connectableBackendInfos.put(backendInfo.getUrl(), backendInfo);
              }
              catch (FileNotFoundException nfe) {
                throw new MojoExecutionException("db properties file " + propertiesFile.getAbsolutePath() + " not found", nfe);
              }
              catch (IOException iox) {
                throw new MojoExecutionException("reading " + propertiesFile.getPath() + " failed", iox);
              }
            }
          }
        }
      }
    }

    if (backends != null && !backends.isEmpty()) {
      for (BackendInfoParameter backendParameter: backends) {
        BackendInfo backendInfo = backendParameter.createBackendInfo(getLog());
        backendInfos.put(backendInfo.getBackend().getName(), backendInfo);
        if (backendInfo.isConnectable()) {
          connectableBackendInfos.put(backendInfo.getUrl(), backendInfo);
          processBackend(backendParameter, backendInfo);
        }
      }
    }
  }

  /**
   * Processes the backend.<br>
   * Provided to be overridden in {@link MigrateSqlMojo}.
   *
   * @param backendParameter the backend parameter
   * @param backendInfo the backend info
   * @throws MojoExecutionException if failed
   */
  protected void processBackend(BackendInfoParameter backendParameter, BackendInfo backendInfo) throws MojoExecutionException {
    // overridden in MigrateSqlMojo
  }





  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {

    // validate configuration
    validate();

    totalErrors = 0;

    for (BackendInfo backendInfo: getBackendInfosToExecute()) {
      getLog().debug("processing " + backendInfo);
      openResources(backendInfo);
      try {
        // process files
        if (filesets != null && !filesets.isEmpty()) {
          // explicit filesets given instead of model dir
          for (FileSet fileSet : filesets) {
            processFileSet(backendInfo, fileSet);
          }
        }
        else {
          // all from model dir
          String[] files = modelDir.isDirectory() ? modelDir.list() : null;
          if (files != null && files.length > 0) {
            final FileSet fs = new FileSet();
            fs.setDirectory(modelDir.getPath());
            processFileSet(backendInfo, fs);
          }
          else {
            getLog().warn("no or empty modelDir " + modelDir.getAbsolutePath());
          }
        }

        if (totalErrors > 0) {
          throw new MojoFailureException(totalErrors + " model errors");
        }
      }
      finally {
        closeResources(backendInfo);
      }
    }

    if (dumpDir != null) {
      dumpDir.mkdirs();
      try {
        ModelDefaults defaults = modelDefaults == null ? null : new ModelDefaults(modelDefaults);
        List<String> optionAnnotations = new ArrayList<>();
        if (dumpAnnotationsAsOptions != null) {
          StringTokenizer stok = new StringTokenizer(dumpAnnotationsAsOptions, " ,");
          while (stok.hasMoreTokens()) {
            optionAnnotations.add(stok.nextToken());
          }
        }
        PrintConfiguration configuration = new PrintConfiguration(dumpAsComment, dumpVariables, defaults,
                                                                  optionAnnotations, dumpColumnGap);
        for (Entity entity: Model.getInstance().getAllEntitites()) {
          File dumpFile = new File(dumpDir, entity.getName() + ".txt");
          try (BufferedWriter writer =
                  new BufferedWriter(
                    new OutputStreamWriter(
                      new FileOutputStream(dumpFile), Settings.encodingCharset))) {
            writer.write(new EntityPrinter(entity, configuration).print());
          }
        }
      }
      catch (IOException | ModelException ex) {
        throw new MojoFailureException("creating model dump failed", ex);
      }
    }
  }


  /**
   * Gets the backend infos to execute.
   *
   * @return the backend infos
   */
  protected abstract Collection<BackendInfo> getBackendInfosToExecute();


  /**
   * Writes the intro for a model to the SQL file.
   *
   * @param backendInfo the backend infor
   * @param modelDirName the model directory name
   * @throws MojoExecutionException if writing to SQL file failed
   */
  protected void writeModelIntroComment(BackendInfo backendInfo, String modelDirName) throws MojoExecutionException {
    try {
      sqlWriter.append("\n-- created from ");
      sqlWriter.append(getPathRelativeToBasedir(modelDirName));
      sqlWriter.append(" at ");
      sqlWriter.append(new Date().toString());
      sqlWriter.append(" by ");
      sqlWriter.append(System.getProperty("user.name"));
      sqlWriter.append(" on ");
      sqlWriter.append(getHostName());
      sqlWriter.append("\n-- backend is ");
      sqlWriter.append(backendInfo.toString());
      sqlWriter.append('\n');
    }
    catch (IOException iox) {
      throw new MojoExecutionException("cannot write to sql file " + sqlFile.getAbsolutePath(), iox);
    }
  }

  /**
   * Gets the hostname.
   *
   * @return the hostname
   */
  private String getHostName() {
    String hostName;
    try {
      // may fail if host is misconfigured
      hostName = InetAddress.getLocalHost().getHostName();
    }
    catch (UnknownHostException ex) {
      // return localhost
      hostName = InetAddress.getLoopbackAddress().getHostAddress();
    }
    return hostName;
  }

}
