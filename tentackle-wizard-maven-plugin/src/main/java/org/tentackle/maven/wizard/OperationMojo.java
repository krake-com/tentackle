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
package org.tentackle.maven.wizard;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import org.tentackle.fx.Fx;
import org.tentackle.fx.FxUtilities;
import org.tentackle.maven.wizard.fx.OperationWizard;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Wizard to create operation files.
 */
@Mojo(name = "operation", inheritByDefault = false, aggregator = true)
public class OperationMojo extends AbstractWizardMojo {

  private List<OperationProfile> operationProfiles;

  public List<OperationProfile> getOperationProfiles() {
    return operationProfiles;
  }

  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    super.execute();

    CountDownLatch latch = new CountDownLatch(1);

    Platform.startup(() -> {
      try {
        OperationWizard wizard = Fx.load(OperationWizard.class);
        wizard.applyMojo(this);
        Stage stage = Fx.createStage(Modality.APPLICATION_MODAL);
        Scene scene = new Scene(wizard.getView());
        stage.setScene(scene);
        stage.setTitle("Operation Wizard");
        wizard.getContainer().updateView();
        stage.setOnHidden(event -> latch.countDown());
        stage.show();
        FxUtilities.getInstance().addStyleSheets();
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
      getLog().error(e);
    }
  }

  @Override
  protected void validate() throws MojoExecutionException {
    super.validate();
    operationProfiles = getProfiles(OperationProfile.class);
    if (operationProfiles.isEmpty()) {
      throw new MojoExecutionException("at least one OperationProfile must be configured");
    }
    Map<String, PackageInfo> packageInfoMap = createPackageMap();
    for (OperationProfile profile: operationProfiles) {
      profile.validate(packageInfoMap);
    }
  }
}
