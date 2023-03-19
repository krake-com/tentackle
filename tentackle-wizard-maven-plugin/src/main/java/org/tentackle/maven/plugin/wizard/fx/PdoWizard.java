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
import org.tentackle.maven.plugin.wizard.PdoGenerator;
import org.tentackle.maven.plugin.wizard.PdoMojo;
import org.tentackle.maven.plugin.wizard.PdoProfile;
import org.tentackle.model.Entity;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationUtilities;

import javafx.application.Platform;
import javafx.fxml.FXML;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controller for the PDO wizard.
 */
@FxControllerService(resources = FxControllerService.RESOURCES_NONE)
public class PdoWizard extends AbstractFxController {

  @Bindable
  private final PdoGenerator gen;


  @FXML
  private FxChoiceBox<PdoProfile> genProfile;

  @FXML
  private FxTextField genEntityName;

  @FXML
  private FxTextField genClassId;

  @FXML
  private FxTextField genTableName;

  @FXML
  private FxChoiceBox<Entity> genSuperEntity;

  @FXML
  private FxChoiceBox<InheritanceType> genInheritanceType;

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


  public PdoWizard() {
    gen = new PdoGenerator();
    gen.setInheritanceType(InheritanceType.NONE);
    gen.setRemoteEnabled(true);
  }

  /**
   * Configures the wizard with mojo's properties.
   *
   * @param mojo the PDO mojo
   */
  public void applyMojo(PdoMojo mojo) {
    genProfile.getItems().clear();
    List<PdoProfile> profiles = mojo.getPdoProfiles();
    genProfile.getItems().addAll(profiles);
    if (!profiles.isEmpty()) {
      gen.setProfile(profiles.get(0));
    }
    gen.setTemplateDirectory(new File(mojo.getTemplateDir(), Constants.CATEGORY_PDO));
    gen.setStatusDir(mojo.getStatusDir());
    gen.setLogger(mojo.getLog());
  }

  @Override
  public void configure() {
    cancel.setGraphic(Fx.createGraphic("cancel"));
    generate.setGraphic(Fx.createGraphic("generate"));
    try {
      for (Entity entity: Model.getInstance().getAllEntities()) {
        if (entity.isAbstract()) {
          genSuperEntity.getItems().add(entity);
        }
      }
    }
    catch (ModelException e) {
      Fx.error("cannot load super entities", e);
    }

    genProfile.setDeselectAllowed(false);
    genInheritanceType.setDeselectAllowed(false);

    // changes may also change the model -> update view
    genProfile.addViewToModelListener(() -> {
      genTableName.updateView();
      genClassId.updateView();
    });
    genEntityName.addModelToViewListener(() -> {
      genDomainInterface.updateView();
      genDomainImplementation.updateView();
      genPersistenceInterface.updateView();
      genPersistenceImplementation.updateView();
    });
    genInheritanceType.addViewToModelListener(this::updateViewOfClassIdAndTableNameAndSuperEntity);
    genSuperEntity.addViewToModelListener(this::updateViewOfClassIdAndTableName);

    genTableName.focusedProperty().addListener((observable, oldValue, newValue) -> {
      if (newValue && gen.getProfile() != null && gen.getProfile().getTablePrefix() != null &&
          gen.getProfile().getTablePrefix().equals(gen.getTableName())) {
        Platform.runLater(() -> genTableName.positionCaret(gen.getTableName().length()));
      }
    });

    cancel.setOnAction(e -> close());

    generate.setOnAction(e -> {

      Consumer<Boolean> generateIt = ok -> {
        if (ok) {
          try {
            List<ValidationResult> results = gen.generate();
            if (results.isEmpty()) {
              Fx.info("PDO classes generated!").setOnHidden(event -> close());
            }
            else {
              Fx.error(ValidationUtilities.getInstance().resultsToMessage(results));
            }
          }
          catch (IOException | TemplateException ex) {
            Fx.error("code generation failed", ex);
          }
        }
      };

      if (gen.getDomainImplementation() == null && gen.getPersistenceImplementation() != null) {
        Fx.question("do you really don't want a domain implementation?", false, generateIt);
      }
      else if (gen.getPersistenceImplementation() == null && gen.getDomainImplementation() != null) {
        Fx.question("do you really don't want a persistence implementation?", false, generateIt);
      }
      else if (gen.getDomainImplementation() == null && gen.getPersistenceImplementation() == null) {
        Fx.question("do you really don't want the implementation files?", false, generateIt);
      }
      else {
        generateIt.accept(true);
      }
    });
  }

  private void close() {
    Fx.getStage(getView()).close();
  }

  private void updateViewOfClassIdAndTableName() {
    genClassId.updateView();
    genTableName.updateView();
  }

  private void updateViewOfClassIdAndTableNameAndSuperEntity() {
    updateViewOfClassIdAndTableName();
    genSuperEntity.updateView();
  }
}
