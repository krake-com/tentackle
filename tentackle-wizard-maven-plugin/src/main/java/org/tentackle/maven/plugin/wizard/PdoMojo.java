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
import org.apache.maven.plugins.annotations.Mojo;

import org.tentackle.common.InterruptedRuntimeException;
import org.tentackle.fx.Fx;
import org.tentackle.maven.PackageInfo;
import org.tentackle.maven.plugin.wizard.fx.PdoWizard;
import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;

/**
 * Wizard to create PDO files.<br>
 * Brings up an FX UI to create the PDO related Java files.
 * <p>
 * Best invoked from within the directory of the maven parent of the project.
 */
@Mojo(name = "pdo", aggregator = true)
public class PdoMojo extends AbstractWizardMojo {

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
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    super.executeImpl();
    updateClassIDs();

    CountDownLatch latch = new CountDownLatch(1);

    Platform.startup(() -> {
      try {
        registerUncaughtExceptionHandler();
        PdoWizard wizard = Fx.load(PdoWizard.class);
        wizard.applyMojo(this);
        stage = Fx.createStage(Modality.APPLICATION_MODAL);
        Scene scene = Fx.createScene(wizard.getView());
        stage.setScene(scene);
        stage.setTitle("PDO Wizard");
        wizard.getContainer().updateView();
        stage.setOnHidden(event -> latch.countDown());
        stage.show();
      }
      catch (Throwable t) {
        getLog().error(t);
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
      for (Entity entity: Model.getInstance().getAllEntities()) {
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
        getLog().warn("no more free class-IDs for " + profile);
      }
      else {
        getLog().info("next free class-ID for " + profile +
                      " is " + profile.getMinClassId() + ", max. " + profile.getMaxClassId());
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
