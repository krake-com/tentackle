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
import org.tentackle.fx.component.FxComboBox;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.maven.plugin.wizard.Constants;
import org.tentackle.maven.plugin.wizard.PdoGenerator;
import org.tentackle.maven.plugin.wizard.PdoMojo;
import org.tentackle.maven.plugin.wizard.PdoProfile;
import org.tentackle.model.Entity;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.ModelException;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationUtilities;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Controller for the PDO wizard.
 */
@FxControllerService(resources = FxControllerService.RESOURCES_NONE)
public class PdoWizard extends AbstractFxController {

  @Bindable
  private PdoGenerator gen;


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
  private FxComboBox<InheritanceType> genInheritanceType;

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
  private FxCheckBox genCacheEnabled;

  @FXML
  private FxButton cancel;

  @FXML
  private FxButton generate;


  /**
   * Creates the PDO wizard controller.
   */
  public PdoWizard() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @FXML
  private void initialize() {
    cancel.setGraphic(Fx.createGraphic("cancel"));
    generate.setGraphic(Fx.createGraphic("generate"));
  }

  /**
   * Configures the wizard with mojo's properties.
   *
   * @param mojo the PDO mojo
   */
  public void applyMojo(PdoMojo mojo) {
    gen = new PdoGenerator(mojo.getModel());
    gen.setInheritanceType(InheritanceType.NONE);
    genProfile.getItems().clear();
    List<PdoProfile> profiles = mojo.getPdoProfiles();
    genProfile.getItems().addAll(profiles);
    if (!profiles.isEmpty()) {
      gen.setProfile(profiles.get(0));
    }
    gen.setTemplateDirectory(new File(mojo.getTemplateDir(), Constants.CATEGORY_PDO));
    gen.setStatusDir(mojo.getStatusDir());
    gen.setRemoteEnabled(gen.isRemoteDefault());

    try {
      for (Entity entity: gen.getModel().getAllEntities()) {
        if (entity.isAbstract()) {
          genSuperEntity.getItems().add(entity);
        }
      }
    }
    catch (ModelException e) {
      Fx.error(getView(), "cannot load super entities", e);
    }

    genProfile.setDeselectAllowed(false);
    genInheritanceType.setDeselectAllowed(false);
    genInheritanceType.addModelToViewListener(() -> genCacheEnabled.updateView());

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

    genTableName.focusedProperty().addListener((_, _, newValue) -> {
      if (newValue && gen.getProfile() != null && gen.getProfile().getTablePrefix() != null &&
          gen.getProfile().getTablePrefix().equals(gen.getTableName())) {
        Platform.runLater(() -> genTableName.positionCaret(gen.getTableName().length()));
      }
    });

    cancel.setOnAction(_ -> close());

    generate.setOnAction(_ -> {
      if (gen.getDomainImplementation() == null && gen.getPersistenceImplementation() != null) {
        Fx.yes(getView(), "do you really want to generate without a domain implementation?", false, this::generateIt);
      }
      else if (gen.getPersistenceImplementation() == null && gen.getDomainImplementation() != null) {
        Fx.yes(getView(), "do you really want to generate without a persistence implementation?", false, this::generateIt);
      }
      else if (gen.getDomainImplementation() == null && gen.getPersistenceImplementation() == null) {
        Fx.yes(getView(), "do you really want to generate without the implementation files?", false, this::generateIt);
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
        Fx.info(getView(), "PDO classes generated!", this::close);
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

  private void updateViewOfClassIdAndTableName() {
    genClassId.updateView();
    genTableName.updateView();

    // a new cell factory forces the cells to be recreated, which disables the types not matching the super entity
    genInheritanceType.setCellFactory(new Callback<>() {
      @Override
      public ListCell<InheritanceType> call(ListView<InheritanceType> param) {
        return new ListCell<>() {
          @Override
          protected void updateItem(InheritanceType item, boolean empty) {
            super.updateItem(item, empty);
            if (item != null) {
              boolean itemInvalid = false;
              if (gen.getSuperEntity() != null) {
                itemInvalid = item != InheritanceType.NONE && item != gen.getSuperEntity().getInheritanceType();
              }
              setDisable(itemInvalid);    // see PdoWizard.css
              setText(item.toString());
            }
            else {
              setDisable(false);
              setText(null);
            }
          }
        };
      }
    });
  }

  private void updateViewOfClassIdAndTableNameAndSuperEntity() {
    updateViewOfClassIdAndTableName();
    genSuperEntity.updateView();
  }
}
