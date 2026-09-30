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

import org.tentackle.common.InterruptedRuntimeException;
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

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

/**
 * Wizard to create PDO files.<br>
 * Brings up an FX UI to create the PDO-related Java files.
 * <p>
 * Best invoked from within the directory of the maven parent of the project.
 */
@Mojo(name = "pdo", aggregator = true, threadSafe = true)
public class PdoMojo extends AbstractWizardMojo {

  /**
   * Filesets to read a secondary model from.<br>
   * If set, the PDO wizard works in batch mode, and for each input file (i.e., entity)
   * the corresponding Java code is generated.<br>
   * The files must be in model-format, i.e., similar to the here-docs read by the wurbelizer or if dumped
   * by the tentackle-sql plugin with {@code dumpAsComment} and {@code dumpVariables} both set to {@code false}.
   * <p>
   * If there is more than one {@link PdoProfile}, the name of the profile must be given
   * as an entity stereotype (case-insensitive), for example {@code #MASTERDATA}.
   */
  @Parameter
  private List<FileSet> batchFilesets;

  /**
   * Alternative way to specify batch mode from the command line.<br>
   * Notice that in this case, all files in that directory are considered as input files,
   * and there is only one such directory configurable.
   */
  @Parameter(property = "batchDir")
  private String batchDir;

  /**
   * The model defaults to use in batch mode.<br>
   * Since the model to load is usually dumped with model defaults applied,
   * we cannot apply them here again. However, some options such as {@code !ROOT},
   * {@code !ROOTID}, {@code !ROOTCLASSID} or {@code UNTRACKED} should be set to
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
      fileSet.setDirectory(batchDir);
      if (batchFilesets == null) {
        batchFilesets = new ArrayList<>();
      }
      batchFilesets.add(fileSet);
    }

    if (batchFilesets != null) {
      Model batchModel = ModelManager.getInstance().getModel("batch");
      getLogger().info("batch mode: {} filesets to process", batchFilesets.size());
      for (FileSet fileSet: batchFilesets) {
        if (fileSet.getDirectory() == null) {
          // relative to basedir if not given
          fileSet.setDirectory(getBaseDir().getAbsolutePath());
        }
        getLogger().info("loading model from {}", fileSet.getDirectory());
        try {
          batchModel.loadFromDirectory(fileSet.getDirectory(), true);
        }
        catch (ModelException e) {
          throw new MojoFailureException("could not load model from " + fileSet.getDirectory(), e);
        }
      }

      Map<String, PdoProfile> profileMap = new HashMap<>(); // stereotype -> profile
      for (PdoProfile profile: pdoProfiles) {
        profileMap.put(profile.getName().toUpperCase(), profile);
      }

      List<String> optionAnnotations = new ArrayList<>();
      if (dumpAnnotationsAsOptions != null) {
        StringTokenizer stok = new StringTokenizer(dumpAnnotationsAsOptions, " ,");
        while (stok.hasMoreTokens()) {
          optionAnnotations.add(stok.nextToken());
        }
      }

      ModelDefaults projectModelDefaults = getModelDefaults();

      ModelDefaults genModelDefaults = null;
      if (batchModelDefaults != null) {
        try {
          genModelDefaults = new ModelDefaults(batchModelDefaults);
        }
        catch (ModelException e) {
          throw new RuntimeException(e);
        }
      }
      PrintConfiguration sourceConfig = new PrintConfiguration(true, true, true,
                                                               genModelDefaults, optionAnnotations, dumpColumnGap);

      try {
        for (Entity entity : batchModel.getAllEntities()) {
          Entity existingEntity = getModel().getByEntityName(entity.getName());
          if (existingEntity != null) {
            getLogger().warn("entity {} already exists in model -> skipped", entity);
          }
          else {
            PdoProfile pdoProfile = null;
            for (String stereotype : new ArrayList<>(entity.getOptions().getStereotypes())) {
              PdoProfile profile = profileMap.get(stereotype.toUpperCase(Locale.ROOT));
              if (profile != null) {
                pdoProfile = profile;
                break;
              }
            }
            if (pdoProfile == null) {
              getLogger().error("no profile for entity {} -> skipped", entity);
            }
            else {
              generateFromBatch(entity, pdoProfile, new EntityPrinter(entity, sourceConfig).print(), projectModelDefaults);
            }
          }
        }
      }
      catch (ModelException mx) {
        throw new MojoFailureException("generating code from model failed", mx);
      }
    }
    else {
      // interactive mode
      CountDownLatch latch = new CountDownLatch(1);

      Platform.startup(() -> {
        try {
          configureFx();
          registerUncaughtExceptionHandler();
          PdoWizard wizard = Fx.load(PdoWizard.class);
          wizard.applyMojo(this);
          stage = Fx.createStage(Modality.APPLICATION_MODAL, null);
          Scene scene = Fx.createScene(wizard.getView());
          stage.setScene(scene);
          stage.setTitle("PDO Wizard");
          wizard.getContainer().updateView();
          stage.setOnHidden(_ -> latch.countDown());
          Fx.show(stage);
        }
        catch (Throwable t) {
          getLogger().error(t.getMessage(), t);
          latch.countDown();
        }
      });

      try {
        latch.await();
      }
      catch (InterruptedException e) {
        throw new InterruptedRuntimeException(e);
      }
    }
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
   * @param projectModelDefaults the model defaults of the project (not the batch model defaults)
   */
  private void generateFromBatch(Entity entity, PdoProfile pdoProfile, String modelSource, ModelDefaults projectModelDefaults) {
    if (pdoProfile == null) {
      getLogger().info("generating code for entity {}", entity);
    }
    else {
      getLogger().info("generating code for entity {} using profile {}", entity, pdoProfile);
    }
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
    gen.setRemoteEnabled(projectModelDefaults.getRemote()); // this a project setting, not copied from the entity

    try {
      List<ValidationResult> results = gen.generate();
      if (!results.isEmpty()) {
        getLogger().error(ValidationUtilities.getInstance().resultsToMessage(results));
      }
    }
    catch (IOException | TemplateException ex) {
      getLogger().error("code generation failed for entity {}", entity, ex);
    }
  }

  /**
   * Updates the classIds in the profiles.
   *
   * @throws MojoExecutionException if class ids overlap or model insane
   */
  private void updateClassIDs() throws MojoExecutionException {

    // profiles sorted by classid, the highest first
    TreeSet<PdoProfile> sortedProfiles = new TreeSet<>((o1, o2) -> o2.getMinClassId() - o1.getMinClassId());

    // map of profiles to next free classid
    Map<PdoProfile, Integer> inUseIds = new HashMap<>();

    for (PdoProfile profile: pdoProfiles) {
      sortedProfiles.add(profile);
      inUseIds.put(profile, profile.getMinClassId());
    }

    // update maxClassId
    int maxClassId = 0;
    int diff = 0;
    for (PdoProfile profile: sortedProfiles) {
      if (maxClassId > 0) {
        diff = maxClassId - profile.getMinClassId();
      }
      if (profile.getMaxClassId() == 0) {
        profile.setMaxClassId(maxClassId);
      }
      else if (profile.getMaxClassId() > maxClassId) {
        throw new MojoExecutionException("class-IDs of profiles must not overlap: " + profile + " maxClassId <= " + maxClassId);
      }
      maxClassId = profile.getMinClassId() - 1;
    }

    // set max ID in last profile according to the last difference guess
    PdoProfile lastProfile = sortedProfiles.first();
    if (lastProfile.getMaxClassId() == 0) {
      lastProfile.setMaxClassId(lastProfile.getMinClassId() + diff);
    }

    // determine consumed class IDs
    try {
      for (Entity entity: getModel().getAllEntities()) {
        int classId = entity.getClassId();
        if (classId > 0) {
          for (PdoProfile profile: sortedProfiles) {
            if (classId >= profile.getMinClassId() && classId <= profile.getMaxClassId()) {
              if (inUseIds.get(profile) < classId) {    // must exist...
                inUseIds.put(profile, classId);
              }
              break;
            }
          }
        }
      }
    }
    catch (ModelException e) {
      throw new MojoExecutionException("cannot determine class IDs", e);
    }

    // update next free class ID in profiles
    for (Map.Entry<PdoProfile,Integer> entry: inUseIds.entrySet()) {
      PdoProfile profile = entry.getKey();
      int classId = entry.getValue() + 1;
      Integer statusClassId = loadClassIdFromStatus(profile.getName());
      if (statusClassId != null && statusClassId > classId) {
        classId = statusClassId;
      }
      profile.setMinClassId(classId);
      if (classId > profile.getMaxClassId()) {
        getLogger().warn("no more free class-IDs for {}", profile);
      }
      else {
        getLogger().info("next free class-ID for {} is {}, max. {}",
                         profile, profile.getMinClassId(), profile.getMaxClassId());
      }
    }
  }


  /**
   * Loads the class id from the status file if exists.
   *
   * @param profileName the name of the profile
   * @return the classid, null if missing or empty or does not contain a number
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
