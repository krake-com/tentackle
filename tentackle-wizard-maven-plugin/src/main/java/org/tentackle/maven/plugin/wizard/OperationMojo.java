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
import org.tentackle.maven.plugin.wizard.fx.OperationWizard;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Wizard to create operation files.<br>
 * Brings up an FX UI to create the operation related Java files.
 * <p>
 * Best invoked from within the directory of the maven parent of the project.
 */
@Mojo(name = "operation", aggregator = true)
public class OperationMojo extends AbstractWizardMojo {

  private List<OperationProfile> operationProfiles;

  private Stage stage;

  @Override
  protected Stage getStage() {
    return stage;
  }

  public List<OperationProfile> getOperationProfiles() {
    return operationProfiles;
  }

  @Override
  public void executeImpl() throws MojoExecutionException, MojoFailureException {
    super.executeImpl();

    CountDownLatch latch = new CountDownLatch(1);

    Platform.startup(() -> {
      try {
        configureFx();
        registerUncaughtExceptionHandler();
        OperationWizard wizard = Fx.load(OperationWizard.class);
        wizard.applyMojo(this);
        stage = Fx.createStage(Modality.APPLICATION_MODAL, null);
        Scene scene = Fx.createScene(wizard.getView());
        stage.setScene(scene);
        stage.setTitle("Operation Wizard");
        wizard.getContainer().updateView();
        stage.setOnHidden(event -> latch.countDown());
        Fx.show(stage);
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
      operationProfiles = getProfiles(OperationProfile.class);
      if (operationProfiles.isEmpty()) {
        throw new MojoExecutionException("at least one OperationProfile must be configured");
      }
      Map<String, PackageInfo> packageInfoMap = createPackageMap(false);
      for (OperationProfile profile : operationProfiles) {
        profile.validate(packageInfoMap);
      }
      return true;
    }
    return false;
  }
}
