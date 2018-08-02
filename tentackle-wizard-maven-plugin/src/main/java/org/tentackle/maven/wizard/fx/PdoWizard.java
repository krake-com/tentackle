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

package org.tentackle.maven.wizard.fx;

import javafx.application.Platform;
import javafx.fxml.FXML;

import org.tentackle.bind.Bindable;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxCheckBox;
import org.tentackle.fx.component.FxChoiceBox;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.maven.wizard.PdoGenerator;
import org.tentackle.maven.wizard.PdoProfile;
import org.tentackle.model.Entity;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationUtilities;

import java.util.List;

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


  public void setProfiles(List<PdoProfile> profiles) {
    genProfile.getItems().clear();
    genProfile.getItems().addAll(profiles);
    if (!profiles.isEmpty()) {
      gen.setProfile(profiles.get(0));
    }
  }

  @Override
  public void configure() {
    try {
      for (Entity entity: Model.getInstance().getAllEntitites()) {
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
    // changes in inheritance and superentity may also change the model -> update view
    genInheritanceType.addViewToModelListener(() -> updateViewOfClassIdAndTableName());
    genSuperEntity.addViewToModelListener(() -> updateViewOfClassIdAndTableName());

    cancel.setOnAction(e -> close());

    generate.setOnAction(e -> {
      List<ValidationResult> results = gen.generate();
      if (!results.isEmpty()) {
        Fx.error(ValidationUtilities.getInstance().resultsToMessage(results));
      }
      else {
        Fx.info("files generated!");
        close();
      }
    });
  }

  private void close() {
    Fx.getStage(getView()).close();
  }

  private void updateViewOfClassIdAndTableName() {
    Platform.runLater(() -> {
      genClassId.updateView();
      genTableName.updateView();
    });
  }
}
