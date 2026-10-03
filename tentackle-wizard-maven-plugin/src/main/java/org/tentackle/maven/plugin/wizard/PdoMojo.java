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

import freemarker.template.TemplateException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.model.fileset.FileSet;

import org.tentackle.fx.Fx;
import org.tentackle.maven.PackageInfo;
import org.tentackle.maven.plugin.wizard.fx.PdoWizard;
import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelDefaults;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;
import org.tentackle.model.print.EntityPrinter;
import org.tentackle.model.print.PrintConfiguration;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationUtilities;

import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Function;

/**
 * Wizard to create PDO files.<br>
 * Brings up an FX UI to create the PDO-related Java files.
 * <p>
 * In batch mode, enabled by {@link #batchFilesets} or {@link #batchDir}, the files are generated
 * headless for every entity of a secondary model that does not exist in the project model yet.
 * <p>
 * Best invoked from within the directory of the Maven parent of the project.
 */
@Mojo(name = "pdo", aggregator = true, threadSafe = true)
public class PdoMojo extends AbstractWizardMojo {

  /**
   * Filesets to read a secondary model from.<br>
   * If set, the PDO wizard works in batch mode, and for each input file (i.e., entity)
   * the corresponding Java code is generated.<br>
   * The files must be in model format, i.e., similar to the here-docs read by the wurbelizer or if dumped
   * by the tentackle-sql plugin with {@code dumpAsComment} and {@code dumpVariables} both set to {@code false}.<br>
   * The whole directory of a fileset is loaded to resolve the relations, but code is generated
   * only for the included files. A fileset without a directory defaults to the project's base directory.
   * <p>
   * If there is more than one {@link PdoProfile}, the name of the profile must be given
   * as an entity stereotype (case-insensitive), for example {@code #MASTERDATA}.
   * With only one profile, entities without such a stereotype use that profile.
   */
  @Parameter
  private List<FileSet> batchFilesets;

  /**
   * Alternative way to specify batch mode from the command line.<br>
   * All files in that single directory are considered as input files.
   * A relative path is resolved against the project's base directory.
   */
  @Parameter(property = "batchDir")
  private String batchDir;

  /**
   * The model defaults applied when printing the model source of the generated PDO interfaces in batch mode.<br>
   * Since the model to load is usually dumped with model defaults applied,
   * we cannot apply them here again. However, some options such as {@code !ROOT},
   * {@code !ROOTID}, {@code !ROOTCLASSID}, {@code !BIND} or {@code UNTRACKED} should be set to
   * avoid unwanted entity options.
   */
  @Parameter(defaultValue = "!ROOT, !ROOTID, !ROOTCLASSID, !BIND, UNTRACKED", property = "batchDefaults")
  private String batchModelDefaults;

  /**
   * Minimum number of spaces between columns in the attribute section (default = 2).
   * <p>
   * For generated model source in batch mode only.
   */
  @Parameter(defaultValue = "2")
  private int dumpColumnGap;

  /**
   * List of annotations that should be printed as attribute options.<br>
   * A comma separates multiple annotations.<br>
   * If the annotation ends with a {@code |}, it matches only annotations without parameters.<br>
   * The special annotation {@code @*} matches all annotations and {@code @*|}
   * matches all annotations without parameters.
   * <p>
   * Example: {@code @NotNull|, @NotZero}
   * <p>
   * For generated model source in batch mode only.
   */
  @Parameter
  private String dumpAnnotationsAsOptions;


  private List<PdoProfile> pdoProfiles;

  private Stage stage;

  @Override
  protected Stage getStage() {
    return stage;
  }

  /**
   * Gets the configured PDO profiles.
   *
   * @return the profiles, at least one
   */
  public List<PdoProfile> getPdoProfiles() {
    return pdoProfiles;
  }

  @Override
  @SuppressWarnings("try")
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    try (LayerScope ignored = openLayerScope()) {
      executeLayered();
    }
  }

  private void executeLayered() throws MojoExecutionException, MojoFailureException {
    super.executeImpl();
    updateClassIDs();

    if (batchDir != null) {
      FileSet fileSet = new FileSet();
      fileSet.setDirectory(resolveFile(batchDir).getPath());
      if (batchFilesets == null) {
        batchFilesets = new ArrayList<>();
      }
      batchFilesets.add(fileSet);
    }

    if (batchFilesets != null) {
      executeBatch();
    }
    else {
      // interactive mode
      runInteractive(() -> {
        PdoWizard wizard = Fx.load(PdoWizard.class);
        wizard.applyMojo(this);
        stage = Fx.createStage(Modality.APPLICATION_MODAL, null);
        Scene scene = Fx.createScene(wizard.getView());
        stage.setScene(scene);
        stage.setTitle("PDO Wizard");
        wizard.getContainer().updateView();
        return stage;
      });
    }
  }

  private void executeBatch() throws MojoExecutionException, MojoFailureException {
    Model batchModel = ModelManager.getInstance().getModel("batch");
    getLogger().info("batch mode: {} filesets to process", batchFilesets.size());
    Set<Entity> includedEntities = new HashSet<>();
    for (FileSet fileSet: batchFilesets) {
      if (fileSet.getDirectory() == null) {
        // defaults to basedir if not given
        fileSet.setDirectory(getBaseDir().getAbsolutePath());
      }
      getLogger().info("loading model from {}", fileSet.getDirectory());
      try {
        batchModel.loadFromDirectory(fileSet.getDirectory(), true);
        for (String fileName : getIncludedFiles(fileSet)) {
          Entity entity = batchModel.getByURL(new File(fileSet.getDirectory(), fileName).toURI().toURL());
          if (entity != null) {
            includedEntities.add(entity);
          }
        }
      }
      catch (ModelException | MalformedURLException e) {
        throw new MojoFailureException("could not load model from " + fileSet.getDirectory(), e);
      }
    }

    Map<String, PdoProfile> profileMap = new HashMap<>(); // stereotype -> profile
    for (PdoProfile profile: pdoProfiles) {
      profileMap.put(profile.getName().toUpperCase(Locale.ROOT), profile);
    }

    List<String> optionAnnotations = new ArrayList<>();
    if (dumpAnnotationsAsOptions != null) {
      StringTokenizer stok = new StringTokenizer(dumpAnnotationsAsOptions, " ,");
      while (stok.hasMoreTokens()) {
        optionAnnotations.add(stok.nextToken());
      }
    }

    ModelDefaults genModelDefaults = null;
    if (batchModelDefaults != null) {
      try {
        genModelDefaults = new ModelDefaults(batchModelDefaults);
      }
      catch (ModelException e) {
        throw new MojoExecutionException("invalid batchDefaults: " + e.getMessage(), e);
      }
    }
    PrintConfiguration sourceConfig = new PrintConfiguration(true, true, true,
                                                             genModelDefaults, optionAnnotations, dumpColumnGap);

    List<String> failedEntities = new ArrayList<>();
    try {
      for (Entity entity : batchModel.getAllEntities()) {
        if (!includedEntities.contains(entity)) {
          continue;   // excluded or loaded only to resolve relations
        }
        Entity existingEntity = getModel().getByEntityName(entity.getName());
        if (existingEntity != null) {
          getLogger().warn("entity {} already exists in model -> skipped", entity);
        }
        else {
          PdoProfile pdoProfile = selectProfile(entity, profileMap);
          if (pdoProfile == null) {
            getLogger().error("no profile for entity {} -> skipped", entity);
            failedEntities.add(entity.getName());
          }
          else if (!generateFromBatch(entity, pdoProfile, new EntityPrinter(entity, sourceConfig).print())) {
            failedEntities.add(entity.getName());
          }
        }
      }
    }
    catch (ModelException mx) {
      throw new MojoFailureException("generating code from model failed", mx);
    }

    if (!failedEntities.isEmpty()) {
      throw new MojoFailureException("no code generated for " + failedEntities.size() + " entities: " +
                                     String.join(", ", failedEntities));
    }
  }

  /**
   * Selects the profile for an entity in batch mode.
   *
   * @param entity the entity
   * @param profileMap the profiles mapped by their uppercase names
   * @return the profile, null if none
   */
  private PdoProfile selectProfile(Entity entity, Map<String, PdoProfile> profileMap) {
    for (String stereotype : entity.getOptions().getStereotypes()) {
      PdoProfile profile = profileMap.get(stereotype.toUpperCase(Locale.ROOT));
      if (profile != null) {
        return profile;
      }
    }
    return pdoProfiles.size() == 1 ? pdoProfiles.getFirst() : null;
  }

  @Override
  protected boolean validate() throws MojoExecutionException {
    if (super.validate()) {
      pdoProfiles = getProfiles(PdoProfile.class);
      if (pdoProfiles.isEmpty()) {
        throw new MojoExecutionException("at least one PdoProfile must be configured");
      }
      Map<String, PackageInfo> packageInfoMap = createPackageMap(false);
      for (PdoProfile profile : pdoProfiles) {
        profile.validate(packageInfoMap);
      }
      return true;
    }
    return false;
  }


  /**
   * Generates the code for the given entity.
   *
   * @param entity the entity
   * @param pdoProfile the PDO profile
   * @param modelSource the model source
   * @return true if generated, false if failed
   */
  private boolean generateFromBatch(Entity entity, PdoProfile pdoProfile, String modelSource) {
    getLogger().info("generating code for entity {} using profile {}", entity, pdoProfile);
    PdoGenerator gen = new PdoGenerator(getModel());
    gen.setProfile(pdoProfile);
    gen.setTemplateDirectory(new File(getTemplateDir(), Constants.CATEGORY_PDO));
    gen.setStatusDir(getStatusDir());
    gen.setModelSource(modelSource);
    gen.setEntityName(entity.getName());
    gen.setShortDescription(entity.getOptions().getComment());
    gen.setInheritanceType(entity.getInheritanceType());
    gen.setSuperEntity(entity.getSuperEntity());
    gen.setClassId(entity.getClassId() != 0 ? entity.getClassId() : null);
    gen.setCacheEnabled(entity.getOptions().isCached());
    gen.setTableName(entity.getTableName());
    gen.setRemoteEnabled(gen.isRemoteDefault()); // this is a project setting, not copied from the entity

    try {
      List<ValidationResult> results = gen.generate();
      if (!results.isEmpty()) {
        getLogger().error("entity {}: {}", entity, ValidationUtilities.getInstance().resultsToMessage(results));
        return false;
      }
      return true;
    }
    catch (IOException | TemplateException ex) {
      getLogger().error("code generation failed for entity {}", entity, ex);
      return false;
    }
  }

  /**
   * Updates the class IDs in the profiles.
   *
   * @throws MojoExecutionException if class IDs overlap or model insane
   */
  private void updateClassIDs() throws MojoExecutionException {
    List<Integer> usedClassIds = new ArrayList<>();
    try {
      for (Entity entity: getModel().getAllEntities()) {
        usedClassIds.add(entity.getClassId());
      }
    }
    catch (ModelException e) {
      throw new MojoExecutionException("cannot determine class IDs", e);
    }

    updateClassIDs(pdoProfiles, usedClassIds, profile -> loadClassIdFromStatus(profile.getName()));

    for (PdoProfile profile : pdoProfiles) {
      if (profile.getMinClassId() > profile.getMaxClassId()) {
        getLogger().warn("no more free class-IDs for {}", profile);
      }
      else if (profile.getMaxClassId() == Integer.MAX_VALUE) {
        getLogger().info("next free class-ID for {} is {}", profile, profile.getMinClassId());
      }
      else {
        getLogger().info("next free class-ID for {} is {}, max. {}",
                         profile, profile.getMinClassId(), profile.getMaxClassId());
      }
    }
  }

  /**
   * Determines the class ID ranges of the profiles and the next free class ID within each range.
   * <p>
   * A profile without a {@code maxClassId} ends right below the {@code minClassId} of the next higher profile.
   * The highest profile without a {@code maxClassId} is unbounded ({@link Integer#MAX_VALUE}).
   * Afterward, {@link PdoProfile#getMinClassId()} is the next free class ID: the configured {@code minClassId}
   * if the range is still unused, otherwise the highest used class ID plus one, or the class ID of the status
   * file, if higher.
   *
   * @param profiles the PDO profiles
   * @param usedClassIds the class IDs used in the model
   * @param statusClassIdProvider provides the next free class ID from the status file, null if none
   * @throws MojoExecutionException if the class ID ranges overlap
   */
  static void updateClassIDs(List<PdoProfile> profiles, Collection<Integer> usedClassIds,
                             Function<PdoProfile, Integer> statusClassIdProvider) throws MojoExecutionException {

    // profiles sorted by min class ID, the highest first
    List<PdoProfile> sortedProfiles = new ArrayList<>(profiles);
    sortedProfiles.sort(Comparator.comparingInt(PdoProfile::getMinClassId).reversed());

    // determine the max class IDs
    int upperBound = 0;   // 0 = unbounded
    for (PdoProfile profile: sortedProfiles) {
      if (upperBound > 0 && profile.getMinClassId() > upperBound) {
        throw new MojoExecutionException("class-IDs of profiles must not overlap: " + profile +
                                         " minClassId " + profile.getMinClassId() + " > " + upperBound);
      }
      if (profile.getMaxClassId() == 0) {
        profile.setMaxClassId(upperBound > 0 ? upperBound : Integer.MAX_VALUE);
      }
      else if (profile.getMaxClassId() < profile.getMinClassId()) {
        throw new MojoExecutionException("maxClassId " + profile.getMaxClassId() + " of profile " + profile +
                                         " is lower than its minClassId " + profile.getMinClassId());
      }
      else if (upperBound > 0 && profile.getMaxClassId() > upperBound) {
        throw new MojoExecutionException("class-IDs of profiles must not overlap: " + profile +
                                         " maxClassId " + profile.getMaxClassId() + " > " + upperBound);
      }
      upperBound = profile.getMinClassId() - 1;
    }

    // determine the highest consumed class IDs
    Map<PdoProfile, Integer> inUseIds = new HashMap<>();   // profile -> highest used class ID
    for (PdoProfile profile: sortedProfiles) {
      inUseIds.put(profile, profile.getMinClassId() - 1);
    }
    for (int classId : usedClassIds) {
      if (classId > 0) {
        for (PdoProfile profile: sortedProfiles) {
          if (classId >= profile.getMinClassId() && classId <= profile.getMaxClassId()) {
            inUseIds.merge(profile, classId, Math::max);
            break;
          }
        }
      }
    }

    // update next free class ID in profiles
    for (PdoProfile profile: sortedProfiles) {
      int classId = inUseIds.get(profile) + 1;
      Integer statusClassId = statusClassIdProvider.apply(profile);
      if (statusClassId != null && statusClassId > classId) {
        classId = statusClassId;
      }
      profile.setMinClassId(classId);
    }
  }


  /**
   * Loads the next free class ID from the status file, if it exists.
   *
   * @param profileName the name of the profile
   * @return the class ID, null if the file is missing, empty or does not contain a number
   */
  private Integer loadClassIdFromStatus(String profileName) {
    Integer classId = null;
    try {
      try (BufferedReader statusReader =
               new BufferedReader(
                   new FileReader(
                       new File(getStatusDir(), profileName + Constants.CLASSID_EXT)))) {

        classId = Integer.parseInt(statusReader.readLine());
      }
    }
    catch (NumberFormatException | IOException e) {
      // this is okay!
    }
    return classId;
  }

}
