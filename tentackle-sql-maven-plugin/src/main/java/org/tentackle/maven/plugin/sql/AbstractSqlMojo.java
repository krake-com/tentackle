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

package org.tentackle.maven.plugin.sql;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;
import org.apache.maven.shared.model.fileset.util.FileSetManager;

import org.tentackle.common.EncryptedProperties;
import org.tentackle.common.Settings;
import org.tentackle.common.StringHelper;
import org.tentackle.maven.AbstractTentackleMojo;
import org.tentackle.model.Attribute;
import org.tentackle.model.Deferrability;
import org.tentackle.model.Entity;
import org.tentackle.model.EntityAliases;
import org.tentackle.model.EntityInfo;
import org.tentackle.model.ForeignKey;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;
import org.tentackle.model.NameVerifier;
import org.tentackle.model.Relation;
import org.tentackle.model.parse.Document;
import org.tentackle.model.print.EntityPrinter;
import org.tentackle.model.print.PrintConfiguration;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendFactory;
import org.tentackle.sql.BackendInfo;
import org.tentackle.sql.BackendInfoFactory;
import org.tentackle.sql.metadata.MetaDataUtilities;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Base tentackle sql mojo.
 *
 * @author harald
 */
public abstract class AbstractSqlMojo extends AbstractTentackleMojo {

  /**
   * Serializes the executions in parallel builds.<br>
   * The model held by the {@link ModelManager} and the {@link NameVerifier} are singletons of the plugin's
   * class realm, which is shared by all projects of a build. Each execution configures and loads the model
   * (defaults, aliases, backends, clearing), so concurrent executions would corrupt each other.
   */
  private static final Lock MODEL_LOCK = new ReentrantLock();

  /**
   * Explicit filesets given instead of the model directory.
   */
  @Parameter
  protected List<FileSet> filesets;

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
   * Uses the model found in the resources of the classpath.<br>
   * Useful to migrate the database tables for dependencies, for example, the Tentackle-framework's model.<br>
   * Requires that the plugin dependencies contain the model resources
   * (see {@link #dumpModelIndex}) amd {@link #dumpModelSource}).
   * <p>
   * Notice: when this flag is true, the model files in the filesets are ignored!
   * If only the tentackle tables should be migrated, make sure to set checkReservedTables to false.
   */
  @Parameter(property = "tentackle.loadModelFromClassPath")
  protected boolean loadModelFromClassPath;

  /**
   * Directory holding the SQL-generated scripts.
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
   * Comma-separated list of backend names.<br>
   * The following special names are recognized:
   * <ul>
   *   <li>all: all non-deprecated backends</li>
   *   <li>none: does not match any backend</li>
   *   <li>deprecated: all deprecated backends</li>
   * </ul>
   */
  @Parameter(property = "tentackle.backendNames")
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
   * Compact model dump.<br>
   * Omits implicit configuration in dumped model code.<br>
   * By default, all configuration keywords are printed for proofreading.<br>
   * In compact mode, however, only the keywords that are actually required are printed,
   * making it more suitable as input for source code generation.
   */
  @Parameter
  protected boolean dumpCompact;

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
   * Dumps the original model source instead of the parsed entity.
   */
  @Parameter
  protected boolean dumpModelSource;

  /**
   * Creates an additional file containing the names of the dumped files.<br>
   * Usually combined with {@link #dumpModelSource} to create {@code META-INF/MODEL-INDEX.LIST} in order to load the model
   * from a jar file.
   */
  @Parameter
  protected File dumpModelIndex;

  /**
   * Minimum number of spaces between columns in the attribute section (default = 2).
   */
  @Parameter(defaultValue = "2")
  protected int dumpColumnGap;

  /**
   * List of annotations that should be printed as attribute options.<br>
   * A comma separates multiple annotations.<br>
   * If the annotation ends with a {@code |}, it matches only annotations without parameters.<br>
   * The special annotation {@code @*} matches all annotations and {@code @*|}
   * matches all annotations without parameters.
   * <p>
   * Example: {@code @NotNull|, @NotZero}
   */
  @Parameter
  protected String dumpAnnotationsAsOptions;

  /**
   * Optional directory where to create bundle property files.<br>
   * Contains all attribute- and relation names with their comments.<br>
   * Useful to copy and paste into the project for auto table config or default editors.
   */
  @Parameter
  protected File bundleDir;

  /**
   * Use IF EXISTS for drop index, constraint, and column if the backend supports it.<br>
   * If not supported, a warning will be logged.
   */
  @Parameter
  protected boolean useDropIfExists;

  /**
   * Create a header comment.
   */
  @Parameter(defaultValue = "true")
  protected boolean generateHeaderComment;

  /**
   * Checks the names used in the model via the {@link org.tentackle.model.NameVerifier}.
   * Turn this off if you want to skip the name checks (to load legacy models, for example).
   */
  @Parameter(defaultValue = "true")
  private boolean checkModelNames;

  /**
   * Check for reserved backend-specific names.<br>
   * Asserts that column-, schema-, table- and index-names don't
   * conflict with reserved words of the configured backends.
   */
  @Parameter(defaultValue = "true")
  private boolean checkBackendSpecificNames;

  /**
   * Check for reserved table names.<br>
   * Invokes {@link org.tentackle.sql.metadata.MetaDataUtilities#isReservedTable(String)}.
   */
  @Parameter(defaultValue = "true")
  protected boolean checkReservedTables;

  /**
   * Optional list of tables names not belonging to the model.<br>
   * Could be just simple names, but in fact is a regular expression.<br>
   * Must not contain the schema name, only the table name!
   */
  @Parameter
  private List<String> alienTables;

  /**
   * Compiled regexes for other tables.
   */
  private List<Pattern> alienTablePatterns;

  /**
   * The model.
   */
  private Model model;

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


  @Override
  protected Lock getExecutionLock() {
    return MODEL_LOCK;
  }

  /**
   * Gets the entity model.
   *
   * @return the model, never null
   */
  protected Model getModel() {
    if (model == null) {
      NameVerifier.getInstance().setEnabled(checkModelNames);
      model = ModelManager.getInstance().getModel(modelName);
    }
    return model;
  }

  /**
   * Gets the created sql file name without the leading path.
   *
   * @return the filename
   */
  protected abstract String getSqlFileName();

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
   * Creates the sqlFile, sqlDirName, and sqlWriter.
   *
   * @param backendInfo the backend info
   * @throws MojoExecutionException if the SQL file could not be created
   */
  protected void createSqlFile(BackendInfo backendInfo) throws MojoExecutionException {
    sqlFile = new File(new File(sqlDir, backendInfo.getBackend().getName()), getSqlFileName());
    try {
      // create missing directories, if any
      File dir = sqlFile.getParentFile();
      sqlDirName = getCanonicalPath(dir);
      dir.mkdirs();
      // create the SQL output file
      sqlWriter = Files.newBufferedWriter(sqlFile.toPath(), Settings.getEncodingCharset(),
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
   * @throws MojoExecutionException if the SQL file could not be created
   */
  protected void openResources(BackendInfo backendInfo) throws MojoExecutionException {
    createSqlFile(backendInfo);
  }

  /**
   * Closes the resources.
   *
   * @param backendInfo the backend info
   * @throws MojoExecutionException if closing failed
   * @throws MojoFailureException if build failure
   */
  protected void closeResources(BackendInfo backendInfo) throws MojoExecutionException, MojoFailureException {
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
   * Returns whether this mojo writes SQL scripts and therefore requires the {@link #sqlDir}.
   *
   * @return true if sqlDir is required (default)
   */
  protected boolean isSqlDirRequired() {
    return true;
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
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      if (modelDir == null) {
        throw new MojoExecutionException("missing tentackle.modelDir");
      }
      if (modelDir.getPath().contains("${")) {
        throw new MojoExecutionException("undefined variable(s) in modelDir: " + modelDir.getPath());
      }
      if (isSqlDirRequired()) {
        if (sqlDir == null) {
          throw new MojoExecutionException("missing tentackle.sqlDir");
        }
        if (sqlDir.getPath().contains("${")) {
          throw new MojoExecutionException("undefined variable(s) in sqlDir: " + sqlDir.getPath());
        }
      }

      backendInfos = new HashMap<>();
      connectableBackendInfos = new HashMap<>();

      BackendFactory.getInstance().getBackends(backendNames).
        forEach(backend -> backendInfos.put(backend.getName(), BackendInfoFactory.getInstance().create(backend)));

      if (backendProperties != null) {
        // determine URL from property file(s)
        for (FileSet fileset : backendProperties) {
          FileSetManager fileSetManager = createFileSetManager(verbosityLevel.isDebug());
          List<String> propertiesDirNames;
          if (fileset.getDirectory() == null) {
            propertiesDirNames = getResourceDirs(false);  // try all resource directories
            if (propertiesDirNames == null || propertiesDirNames.isEmpty()) {
              throw new MojoExecutionException(
              "no <directory> given in <fileset> of <backendProperties> and no resource directories found");
            }
          }
          else {
            propertiesDirNames = new ArrayList<>();
            propertiesDirNames.add(fileset.getDirectory());   // use given directory
          }
          for (String propertiesDirName : propertiesDirNames) {
            fileset.setDirectory(propertiesDirName);
            String[] fileNames = fileSetManager.getIncludedFiles(fileset);
            for (String filename : fileNames) {
              File propertiesFile = new File(new File(propertiesDirName), filename);
              EncryptedProperties props = new EncryptedProperties();
              try (InputStream is = new FileInputStream(propertiesFile)) {
                getLogger().debug("loading property file {}", propertiesFile.getAbsolutePath());
                props.load(is);
                BackendInfo backendInfo = BackendInfoFactory.getInstance().create(props);
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

      if (backends != null && !backends.isEmpty()) {
        for (BackendInfoParameter backendParameter : backends) {
          BackendInfo backendInfo = backendParameter.createBackendInfo(this);
          backendInfos.put(backendInfo.getBackend().getName(), backendInfo);
          if (backendInfo.isConnectable()) {
            connectableBackendInfos.put(backendInfo.getUrl(), backendInfo);
            processBackend(backendParameter, backendInfo);
          }
        }
      }

      if (checkBackendSpecificNames) {
        Set<Backend> validatedBackends = new HashSet<>();
        for (BackendInfo backendInfo : backendInfos.values()) {
          validatedBackends.add(backendInfo.getBackend());
        }
        for (BackendInfo backendInfo : connectableBackendInfos.values()) {
          validatedBackends.add(backendInfo.getBackend());
        }
        getModel().getEntityFactory().setBackends(validatedBackends);
      }

      return true;
    }
    return false;
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
  public void executeImpl() throws MojoExecutionException, MojoFailureException {

    totalErrors = 0;

    for (BackendInfo backendInfo : getBackendInfosToExecute()) {
      getLogger().debug("processing {}", backendInfo);
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
            getLogger().warn("{} {}", modelDir.exists() ? "empty modelDir" : "no modelDir", modelDir.getAbsolutePath());
          }
        }
      }
      catch (RuntimeException rx) {
        throw new MojoExecutionException(rx);
      }

      closeResources(backendInfo);

      if (totalErrors > 0) {
        throw new MojoFailureException(totalErrors + " model errors");
      }
    }

    dumpModel();
    dumpBundles();
    checkFallbackTableAliases();
  }


  /**
   * Dumps the model.
   *
   * @throws MojoFailureException if failed
   */
  protected void dumpModel() throws MojoFailureException {
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
        PrintConfiguration configuration = new PrintConfiguration(dumpCompact, dumpAsComment, dumpVariables,
                                                                  defaults, optionAnnotations, dumpColumnGap);

        Map<String, String> aliasesMap = new TreeMap<>();     // alias:entityname
        Map<String, String> tableMap = new TreeMap<>();       // tablename:entityname
        Map<Integer, String> classidMap = new TreeMap<>();    // classid:entityname
        Set<File> dumpedFiles = new LinkedHashSet<>();        // dumped model files

        for (Entity entity : getModel().getAllEntities()) {
          if (entity.getDefinedTableAlias() != null) {
            aliasesMap.put(entity.getDefinedTableAlias(), entity.getName() + " (configured)");
          }
          else if (entity.getTableAlias() != null) {
            aliasesMap.put(entity.getTableAlias(), entity.getName());
          }
          if (entity.getTableName() != null) {
            tableMap.put(entity.getTableName(), entity.getName());
          }
          if (entity.getClassId() != 0) {
            classidMap.put(entity.getClassId(), entity.getName());
          }

          if (dumpModelSource) {
            EntityInfo entityInfo = getModel().getEntityInfo(entity);
            if (entityInfo.isFile()) {
              File dumpFile = new File(dumpDir, entityInfo.getFile().getName());
              dumpedFiles.add(dumpFile);
              try (BufferedWriter writer =
                     new BufferedWriter(
                       new OutputStreamWriter(
                         new FileOutputStream(dumpFile), Settings.getEncodingCharset()))) {
                String modelSource = entityInfo.getModelSource();
                if (modelSource.startsWith(Document.ORIGIN_INFO_LEAD)) {
                  // Cut the source info. Makes no sense outside the project.
                  int ndx = modelSource.indexOf('\n');
                  if (ndx >= 0) {
                    modelSource = modelSource.substring(ndx + 1);
                  }
                }
                // add model defaults
                ModelDefaults modelDefaults = entityInfo.getModelDefaults();
                if (modelDefaults != null) {
                  modelSource = Document.DEFAULTS_INFO_LEAD + modelDefaults + "]\n" + modelSource;
                }
                writer.write(modelSource);
              }
            }
          }
          else {
            File dumpFile = new File(dumpDir, entity.getName() +
                                              (dumpAsComment || dumpVariables ? ".txt" : ".map"));
            dumpedFiles.add(dumpFile);
            try (BufferedWriter writer =
                   new BufferedWriter(
                     new OutputStreamWriter(
                       new FileOutputStream(dumpFile), Settings.getEncodingCharset()))) {
              writer.write(new EntityPrinter(entity, configuration).print());
            }
          }
        }

        if (dumpModelIndex != null) {
          dumpModelIndex.getParentFile().mkdirs();
          try (BufferedWriter writer =
                 new BufferedWriter(
                   new OutputStreamWriter(
                     new FileOutputStream(dumpModelIndex), Settings.getEncodingCharset()))) {
            for (File dumpedFile : dumpedFiles) {
              String dumpedName;
              File resourceDir = getResourceDir(dumpedFile, false);
              if (resourceDir == null) {
                resourceDir = getResourceDir(dumpedFile, true);   // look in test resources as well!
              }
              if (resourceDir != null) {
                String resourcePath = getCanonicalPath(resourceDir);
                String dumpedFilePath = getCanonicalPath(dumpedFile);
                dumpedName = dumpedFilePath.substring(resourcePath.length());
              }
              else {
                dumpedName = getPathRelativeToBasedir(getCanonicalPath(dumpedFile));
              }
              dumpedName = dumpedName.replace('\\', '/'); // if built on Windows: translate to unix-style paths
              if (dumpedName.startsWith("/")) {
                dumpedName = dumpedName.substring(1);
              }
              writer.write(dumpedName + "\n");
            }
          }
          getLogger().info("model index created in {}", getPathRelativeToBasedir(dumpModelIndex.getAbsolutePath()));
        }

        File file = new File(dumpDir, "_aliases_.txt");
        try (BufferedWriter writer =
               new BufferedWriter(
                 new OutputStreamWriter(
                   new FileOutputStream(file), Settings.getEncodingCharset()))) {
          for (Map.Entry<String,String> entry : aliasesMap.entrySet()) {
            writer.write(entry.getKey());
            writer.write(" = ");
            writer.write(entry.getValue());
            writer.newLine();
          }
        }

        file = new File(dumpDir, "_tables_.txt");
        try (BufferedWriter writer =
               new BufferedWriter(
                 new OutputStreamWriter(
                   new FileOutputStream(file), Settings.getEncodingCharset()))) {
          for (Map.Entry<String,String> entry : tableMap.entrySet()) {
            writer.write(entry.getKey());
            writer.write(" = ");
            writer.write(entry.getValue());
            writer.newLine();
          }
        }

        file = new File(dumpDir, "_classid_.txt");
        try (BufferedWriter writer =
               new BufferedWriter(
                 new OutputStreamWriter(
                   new FileOutputStream(file), Settings.getEncodingCharset()))) {
          for (Map.Entry<Integer,String> entry : classidMap.entrySet()) {
            writer.write(entry.getKey().toString());
            writer.write(" = ");
            writer.write(entry.getValue());
            writer.newLine();
          }
        }
        getLogger().info("model dump created in {}", getPathRelativeToBasedir(dumpDir.getAbsolutePath()));
      }
      catch (IOException | ModelException | MojoExecutionException ex) {
        throw new MojoFailureException("creating model dump failed", ex);
      }
    }
  }

  /**
   * Dumps attribute and relation bundles.
   *
   * @throws MojoFailureException if failed
   */
  protected void dumpBundles() throws MojoFailureException {
    if (bundleDir != null) {
      bundleDir.mkdirs();
      try {
        for (Entity entity : getModel().getAllEntities()) {
          File dumpFile = new File(bundleDir, entity.getName() + ".properties");
          try (BufferedWriter writer =
                 new BufferedWriter(
                   new OutputStreamWriter(
                     new FileOutputStream(dumpFile), Settings.getEncodingCharset()))) {
            writer.write("# attributes\n");
            for (Attribute attribute: entity.getAttributesIncludingInherited()) {
              if (!attribute.isImplicit()) {
                writer.write(attribute.getName() + "=" + attribute.getOptions().getComment() + "\n");
              }
            }
            writer.write("# relations\n");
            for (Relation relation: entity.getRelationsIncludingInherited()) {
              writer.write(StringHelper.firstToLower(relation.getName()) + "=" + relation.getComment() + "\n");
            }
          }
        }
        getLogger().info("bundle property files created in {}", getPathRelativeToBasedir(bundleDir.getAbsolutePath()));
      }
      catch (IOException | ModelException ex) {
        throw new MojoFailureException("creating bundle dump failed", ex);
      }
    }
  }

  /**
   * Check for numbered fallback aliases.
   */
  protected void checkFallbackTableAliases() throws MojoFailureException {
    try {
      StringBuilder aliasBuf = new StringBuilder();

      for (Entity entity : getModel().getAllEntities()) {
        String alias = entity.getTableAlias();
        if (entity.getDefinedTableAlias() == null && alias != null && alias.length() > 1 &&
            StringHelper.isAllDigits(alias.substring(1))) {
          if (!aliasBuf.isEmpty()) {
            aliasBuf.append(", ");
          }
          aliasBuf.append(entity).append('=').append(alias);
        }
      }

      if (!aliasBuf.isEmpty()) {
        getLogger().warn("consider specifying explicit table aliases for: {}", aliasBuf);
      }
    }
    catch (ModelException ex) {
      throw new MojoFailureException("analyzing table aliases failed", ex);
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
   * @param backendInfo the backend info
   * @param modelDirName the model directory name
   * @throws MojoExecutionException if writing to the SQL file failed
   */
  protected void writeModelIntroComment(BackendInfo backendInfo, String modelDirName) throws MojoExecutionException {
    if (generateHeaderComment) {
      try {
        sqlWriter.append('\n').append(backendInfo.getBackend().getSingleLineComment()).append(" created from ");
        sqlWriter.append(getModelLocation(modelDirName));
        sqlWriter.append(" at ");
        sqlWriter.append(new Date().toString());
        sqlWriter.append(" by ");
        sqlWriter.append(System.getProperty("user.name"));
        sqlWriter.append('@');
        sqlWriter.append(getHostName());
        sqlWriter.append('\n').append(backendInfo.getBackend().getSingleLineComment()).append(" backend is ");
        sqlWriter.append(backendInfo.toString());
        sqlWriter.append('\n');
      }
      catch (IOException iox) {
        throw new MojoExecutionException("cannot write to sql file " + sqlFile.getAbsolutePath(), iox);
      }
    }
  }

  /**
   * Returns the model source location to be used in the intro comment.
   *
   * @param modelDirName the model directory name
   * @return the model source description
   */
  protected String getModelLocation(String modelDirName) {
    return getPathRelativeToBasedir(modelDirName);
  }


  /**
   * Returns whether the given table name is reserved and must not be used by the model.
   *
   * @param tableName the tablename (without the schema)
   * @return true if belongs to the alien tables
   */
  protected boolean isAlienTable(String tableName) {
    boolean isOther = false;
    if (tableName != null && alienTables != null) {
      if (alienTablePatterns == null) {
        alienTablePatterns = new ArrayList<>();
        for (String otherTable : alienTables) {
          if (!StringHelper.isAllWhitespace(otherTable)) {
            alienTablePatterns.add(Pattern.compile(otherTable));
          }
        }
      }
      for (Pattern pattern: alienTablePatterns) {
        if (pattern.matcher(tableName).matches()) {
          isOther = true;
          break;
        }
      }
    }
    return isOther;
  }


  /**
   * Checks and logs whether the table name is already used by Tentackle.
   *
   * @param entity the entity
   * @return true if reusing the same table name, false if some other name, or only warning since used in another schema
   */
  protected boolean checkAndLogReservedTable(Entity entity) {
    String plainTableName = entity.getTableNameWithoutSchema();
    if (checkReservedTables && MetaDataUtilities.getInstance().isReservedTable(plainTableName)) {
      String msg = "table '" + plainTableName + "' of entity " + entity + " is reserved by the Tentackle framework";
      if (entity.getSchemaName() == null) {
        getLogger().error(msg);
        return true;
      }
      else {
        getLogger().warn("{} -> consider renaming", msg);
      }
    }
    return false;
  }

  /**
   * Warns once if the model uses deferred/deferrable unique indexes or foreign keys but the
   * backend does not support deferring constraint checks.<br>
   * DDL generation itself already degrades gracefully in that case (falling back to plain,
   * non-deferrable DDL), so this is purely diagnostic.
   *
   * @param backend the backend
   * @param entities the entities to check for deferrable indexes
   * @param foreignKeys the foreign keys to check for deferrability, null if none available
   */
  protected void warnIfDeferredNotSupported(Backend backend, Collection<Entity> entities, Collection<ForeignKey> foreignKeys) {
    if (!backend.isSetConstraintsDeferredSupported()) {
      boolean deferredUsed = foreignKeys != null &&
                             foreignKeys.stream()
                                        .anyMatch(foreignKey -> foreignKey.getDeferrability() != Deferrability.NOT_DEFERRABLE);
      if (!deferredUsed && entities != null) {
        deferredUsed = entities.stream().flatMap(entity -> entity.getIndexes().stream())
                               .anyMatch(index -> index.getDeferrability() != Deferrability.NOT_DEFERRABLE);
      }
      if (deferredUsed) {
        getLogger().warn("backend {} does not support deferred constraints -> " +
                         "deferred/deferrable indexes and foreign keys will be created as NOT DEFERRABLE",
                         backend);
      }
    }
  }

}
