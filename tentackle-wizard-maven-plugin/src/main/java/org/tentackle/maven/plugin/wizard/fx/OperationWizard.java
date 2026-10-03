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

package org.tentackle.maven.plugin.wizard.fx;

import freemarker.template.TemplateException;

import org.tentackle.bind.Bindable;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxCheckBox;
import org.tentackle.fx.component.FxChoiceBox;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.maven.plugin.wizard.Constants;
import org.tentackle.maven.plugin.wizard.OperationGenerator;
import org.tentackle.maven.plugin.wizard.OperationMojo;
import org.tentackle.maven.plugin.wizard.OperationProfile;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationUtilities;

import javafx.fxml.FXML;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Controller for the operation wizard.
 */
@FxControllerService(resources = FxControllerService.RESOURCES_NONE)
public class OperationWizard extends AbstractFxController {

  @Bindable
  private OperationGenerator gen;


  @FXML
  private FxChoiceBox<OperationProfile> genProfile;

  @FXML
  private FxTextField genOperationName;

  @FXML
  private FxTextField genSuperOperation;

  @FXML
  private FxCheckBox genAbstractOperation;

  @FXML
  private FxTextField genShortDescription;

  @FXML
  private FxTextArea genLongDescription;

  @FXML
  private FxTextField genDomainInterface;

  @FXML
  private FxTextField genPersistenceInterface;

  @FXML
  private FxTextField genDomainImplementation;

  @FXML
  private FxTextField genPersistenceImplementation;

  @FXML
  private FxCheckBox genRemoteEnabled;

  @FXML
  private FxButton cancel;

  @FXML
  private FxButton generate;


  /**
   * Creates the operation wizard controller.
   */
  public OperationWizard() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  /**
   * Configures the wizard with mojo's properties.
   *
   * @param mojo the operation mojo
   */
  public void applyMojo(OperationMojo mojo) {
    gen = new OperationGenerator(mojo.getModel());
    genProfile.getItems().clear();
    List<OperationProfile> profiles = mojo.getOperationProfiles();
    genProfile.getItems().addAll(profiles);
    if (!profiles.isEmpty()) {
      gen.setProfile(profiles.get(0));
    }
    gen.setTemplateDirectory(new File(mojo.getTemplateDir(), Constants.CATEGORY_OPERATION));
    gen.initializeRemoteDefault();
  }

  @Override
  public void configure() {
    cancel.setGraphic(Fx.createGraphic("cancel"));
    generate.setGraphic(Fx.createGraphic("generate"));

    genProfile.setDeselectAllowed(false);

    // changes may also change the model -> update view
    genOperationName.addModelToViewListener(() -> {
      genDomainInterface.updateView();
      genDomainImplementation.updateView();
      genPersistenceInterface.updateView();
      genPersistenceImplementation.updateView();
      genRemoteEnabled.updateView();
    });
    genDomainInterface.addModelToViewListener(genDomainImplementation::updateView);
    genPersistenceInterface.addModelToViewListener(() -> {
      genPersistenceImplementation.updateView();
      genRemoteEnabled.updateView();
    });

    cancel.setOnAction(_ -> close());

    generate.setOnAction(_ -> {
      if (gen.getDomainInterface() != null && gen.getDomainImplementation() == null) {
        Fx.yes(getView(), "do you really want to generate without a domain implementation?", false, this::generateIt);
      }
      else if (gen.getPersistenceInterface() != null && gen.getPersistenceImplementation() == null) {
        Fx.yes(getView(), "do you really want to generate without a persistence implementation?", false, this::generateIt);
      }
      else {
        generateIt();
      }
    });
  }

  private void generateIt() {
    try {
      List<ValidationResult> results = gen.generate();
      if (results.isEmpty()) {
        Fx.info(getView(), "operation classes generated!", this::close);
      }
      else {
        Fx.error(getView(), ValidationUtilities.getInstance().resultsToMessage(results));
      }
    }
    catch (IOException | TemplateException ex) {
      Fx.error(getView(), "code generation failed", ex);
    }
  }

  private void close() {
    Fx.getStage(getView()).close();
  }

}
