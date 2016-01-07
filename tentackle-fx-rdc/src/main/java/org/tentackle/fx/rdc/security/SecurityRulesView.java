/*
 * Tentackle - http://www.tentackle.org.
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
 *
 */

package org.tentackle.fx.rdc.security;

import java.net.URL;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.tentackle.app.AbstractApplication;
import org.tentackle.bind.Bindable;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.container.FxTab;
import org.tentackle.fx.container.FxTabPane;
import org.tentackle.fx.container.FxVBox;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.fx.rdc.RdcRuntimeException;
import org.tentackle.fx.table.TablePopup;
import org.tentackle.misc.TrackedList;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoUtilities;
import org.tentackle.pdo.PersistentDomainObject;
import org.tentackle.prefs.PersistedPreferencesFactory;
import org.tentackle.reflect.ReflectionHelper;
import org.tentackle.security.pdo.Security;

/**
 * CRUD controller for PDOs.
 *
 * @author harald
 */
@FxControllerService
public class SecurityRulesView extends AbstractFxController implements DomainContextProvider {

  /**
   * Creates and shows the modal stage.
   *
   * @param clazz the secured non-PDO class, null if PDO
   * @param pdo the secured PDO, null if non-PDO clazz
   */
  public static void show(Class<?> clazz, PersistentDomainObject<?> pdo) {
    Stage stage = Fx.createStage(Modality.APPLICATION_MODAL);
    SecurityRulesView controller = Fx.load(SecurityRulesView.class);
    if (pdo != null) {
      controller.setSecuredObject(pdo);
      stage.setTitle(MessageFormat.format(RdcFxRdcBundle.getString(
              "Security Rules for {0}"), pdo.getPlural()));
    }
    else {
      controller.setSecuredObject(clazz);
      stage.setTitle(MessageFormat.format(RdcFxRdcBundle.getString(
              "Security Rules for {0}"), ReflectionHelper.getClassBaseName(clazz)));
    }
    Scene scene = new Scene(controller.getView());
    stage.setScene(scene);
    stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, e -> {
      if (controller.cancel()) {
        e.consume();
      }
    });
    stage.showAndWait();
  }

  @FXML
  private FxVBox vBox;
  @FXML
  private FxTabPane tabPane;
  @FXML
  private FxTab classTab;
  @FXML
  private FxTab pdoTab;
  @FXML
  private FxTableView<Security> classRulesNode;
  @FXML
  private FxTableView<Security> pdoRulesNode;

  @FXML
  private FxButton moveUpButton;
  @FXML
  private FxButton moveDownButton;
  @FXML
  private FxButton addButton;
  @FXML
  private FxButton removeButton;
  @FXML
  private FxButton saveButton;
  @FXML
  private FxButton cancelButton;

  @Bindable
  private ObservableList<Security> classRules;      // the security rules for the clazz
  private TrackedList<Security> trackedClassRules;  // to track the changes
  @Bindable
  private ObservableList<Security> pdoRules;        // the security rules for the PDO
  private TrackedList<Security> trackedPdoRules;    // to track the changes

  private ResourceBundle resources;                 // the resources
  private DomainContext context;                    // the domain context
  private PersistentDomainObject<?> pdo;            // the PDO, null if application class
  private int classId;                              // the class id, 0 if non-PDO class
  private Class<?> clazz;                           // the application class, null if PDO
  private SecurityEditor editor;                    // the editor for a rule
  private TablePopup<Security> classRulesPopup;     // popup menu for the class rules table
  private TablePopup<Security> pdoRulesPopup;       // popup menu for the pdo rules table
  private boolean withinSetupSecuredObject;         // true if selection changes must not trigger setRule

  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  @Override
  public void initialize(URL location, ResourceBundle resources) {
    this.resources = resources;

    pdoRules = FXCollections.observableArrayList();
    classRules = FXCollections.observableArrayList();

    editor = Fx.load(SecurityEditor.class);
    editor.getContainer().addViewToModelListener(() -> Platform.runLater(() -> {
        classRulesNode.refresh();
        pdoRulesNode.refresh();
      })
    );
    vBox.getChildren().add(editor.getView());

    classRulesNode.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> setRule(nv));
    pdoRulesNode.getSelectionModel().selectedItemProperty().addListener((obs, ov, nv) -> setRule(nv));

    tabPane.getSelectionModel().selectedItemProperty().addListener((obs,ov,nv) -> {
      if (!withinSetupSecuredObject) {
        if (nv == classTab) {
          setRule(classRulesNode.getSelectionModel().getSelectedItem());
        }
        else if (nv == pdoTab) {
          setRule(pdoRulesNode.getSelectionModel().getSelectedItem());
        }
        else {
          setRule(null);
        }
      }
    });

    moveUpButton.setGraphic(Fx.createImageView("up"));
    moveUpButton.setOnAction(e -> moveUp());

    moveDownButton.setGraphic(Fx.createImageView("down"));
    moveDownButton.setOnAction(e -> moveDown());

    addButton.setGraphic(Fx.createImageView("add"));
    addButton.setOnAction(e -> add());

    removeButton.setGraphic(Fx.createImageView("delete"));
    removeButton.setOnAction(e -> remove());

    saveButton.setGraphic(Fx.createImageView("save"));
    saveButton.setOnAction(e -> save());

    cancelButton.setGraphic(Fx.createImageView("cancel"));
    cancelButton.setOnAction(e -> cancel());
  }


  @Override
  public void configure() {
    classRulesPopup = new TablePopup<>(classRulesNode, "classRules", false);
    classRulesPopup.loadPreferences(PersistedPreferencesFactory.getInstance().isSystemOnly());
    classRulesNode.setSortable(false);
    pdoRulesPopup = new TablePopup<>(pdoRulesNode, "pdoRules", false);
    pdoRulesPopup.loadPreferences(PersistedPreferencesFactory.getInstance().isSystemOnly());
    pdoRulesNode.setSortable(false);
  }


  /**
   * Configures the view to edit security rule sets for a PDO.<br>
   *
   * @param pdo the pdo
   */
  public void setSecuredObject(PersistentDomainObject<?> pdo) {
    this.pdo = pdo;
    classId = pdo.getClassId();
    if (classId == 0) {
      throw new RdcRuntimeException(pdo.toGenericString() + " does not provide a class-ID");
    }
    clazz = null;
    setupSecuredObject();
  }

  /**
   * Configures the view to edit security rule sets for a PDO-class.<br>
   *
   * @param classId the class-ID
   */
  public void setSecuredObject(int classId) {
    if (classId == 0) {
      throw new RdcRuntimeException("classId must be > 0");
    }
    this.classId = classId;
    clazz = null;
    pdo = Pdo.create(PdoUtilities.getInstance().getPdoClassName(classId),
                     AbstractApplication.getRunningApplication().getDomainContext());
    setupSecuredObject();
  }

  /**
   * Configures the view to edit security rule sets for a non-PDO-class.<br>
   *
   * @param clazz the class
   */
  public void setSecuredObject(Class<?> clazz) {
    if (PersistentDomainObject.class.isAssignableFrom(clazz)) {
      throw new RdcRuntimeException(clazz + " is a persistent domain object");
    }
    this.clazz = clazz;
    pdo = null;
    classId = 0;
    setupSecuredObject();
  }


  /**
   * Sets up the securable object.
   */
  private void setupSecuredObject() {
    withinSetupSecuredObject = true;
    if (pdo != null) {
      context = pdo.getDomainContext();
      trackedClassRules = on(Security.class).selectByObject(classId, 0);
      classRules.setAll(trackedClassRules);
      classTab.setText(MessageFormat.format(RdcFxRdcBundle.getString(
              "Rules for {0}"), pdo.getPlural()));
      if (pdo.isNew()) {
        trackedPdoRules = null;
        pdoRules.clear();
        tabPane.getSelectionModel().select(classTab);
        tabPane.getTabs().remove(pdoTab);
      }
      else {
        trackedPdoRules = on(Security.class).selectByObject(classId, pdo.getId());
        pdoRules.setAll(trackedPdoRules);
        tabPane.getSelectionModel().select(pdoTab);
        pdoTab.setText(MessageFormat.format(RdcFxRdcBundle.getString(
                "Rules for {0} {1}"), pdo.getSingular(), pdo.toString()));
        if (!tabPane.getTabs().contains(pdoTab)) {
          tabPane.getTabs().add(pdoTab);
        }
      }
      editor.setSecurableClass(pdo.getEffectiveClass());
    }
    else {
      context = AbstractApplication.getRunningApplication().getDomainContext();
      if (clazz != null) {
        trackedClassRules = on(Security.class).selectByObjectClass(clazz.getName());
        classRules.setAll(trackedClassRules);
        classTab.setText(MessageFormat.format(RdcFxRdcBundle.getString(
                  "Rules for {0}"), ReflectionHelper.getClassBaseName(clazz)));
      }
      else {
        classRules.clear();
        trackedClassRules = null;
        classTab.setText("");
      }
      pdoRules.clear();
      trackedPdoRules = null;
      tabPane.getTabs().remove(pdoTab);
      tabPane.getSelectionModel().select(classTab);
      editor.setSecurableClass(clazz);
    }
    classRulesNode.getSelectionModel().clearSelection();
    pdoRulesNode.getSelectionModel().clearSelection();
    setRule(null);
    getContainer().updateView();
    withinSetupSecuredObject = false;
  }

  /**
   * Sets the current rule.
   *
   * @param rule the rule, null if none
   */
  private void setRule(Security rule) {
    if (rule == null) {
      editor.setPdo(on(Security.class));
      editor.setChangeable(false);
      moveUpButton.setDisable(true);
      moveDownButton.setDisable(true);
      removeButton.setDisable(true);
    }
    else {
      editor.setPdo(rule);
      editor.setChangeable(true);
      int index = trackedClassRules.indexOf(rule);
      int size;
      if (index >= 0) {
        size = trackedClassRules.size();
      }
      else {
        index = trackedPdoRules.indexOf(rule);
        size = trackedPdoRules.size();
      }
      updateMoveButtons(index, size);
      removeButton.setDisable(false);
    }
  }


  private void updateMoveButtons(int index, int size) {
    moveUpButton.setDisable(index <= 0);
    moveDownButton.setDisable(index >= size - 1);
  }


  private void moveUp() {
    Security rule = editor.getPdo();
    int index = trackedClassRules.indexOf(rule);
    if (index >= 0) {   // cannot be 0, only to select the correct list
      trackedClassRules.remove(index);
      trackedClassRules.add(index - 1, rule);
      classRules.remove(index);
      classRules.add(index - 1, rule);
      classRulesNode.getSelectionModel().select(index - 1);
    }
    else {
      trackedPdoRules.remove(index);
      trackedPdoRules.add(index - 1, rule);
      pdoRules.remove(index);
      pdoRules.add(index - 1, rule);
      pdoRulesNode.getSelectionModel().select(index - 1);
    }
  }

  private void moveDown() {
    Security rule = editor.getPdo();
    int index = trackedClassRules.indexOf(rule);
    if (index >= 0) {
      trackedClassRules.remove(index);
      trackedClassRules.add(index + 1, rule);
      classRules.remove(index);
      classRules.add(index + 1, rule);
      classRulesNode.getSelectionModel().select(index + 1);
    }
    else {
      trackedPdoRules.remove(index);
      trackedPdoRules.add(index + 1, rule);
      pdoRules.remove(index);
      pdoRules.add(index + 1, rule);
      pdoRulesNode.getSelectionModel().select(index + 1);
    }
  }

  private void add() {
    Security sec = on(Security.class);
    Tab tab = tabPane.getSelectionModel().getSelectedItem();
    int ndx;
    if (tab == pdoTab) {
      ndx = pdoRulesNode.getSelectionModel().getSelectedIndex();
      if (ndx == -1) {
        ndx = trackedPdoRules.size();
      }
      trackedPdoRules.add(ndx, sec);
      pdoRules.add(ndx, sec);
      pdoRulesNode.getSelectionModel().select(ndx);
    }
    else {
      ndx = classRulesNode.getSelectionModel().getSelectedIndex();
      if (ndx == -1) {
        ndx = trackedClassRules.size();
      }
      trackedClassRules.add(ndx, sec);
      classRules.add(ndx, sec);
      classRulesNode.getSelectionModel().select(ndx);
    }
  }

  private void remove() {
    Security rule = editor.getPdo();
    if (trackedClassRules.contains(rule)) {
      trackedClassRules.remove(rule);
      classRules.remove(rule);
    }
    else {
      trackedPdoRules.remove(rule);
      pdoRules.remove(rule);
    }
  }

  private void save() {
    getDomainContext().getSession().transaction(() -> {
      if (trackedClassRules != null) {
        PdoUtilities.getInstance().deleteCollection(trackedClassRules.getRemovedObjects());
        int ndx = 0;
        for (Security sec: trackedClassRules) {
          sec.setPriority(ndx);
          if (classId != 0) {
            sec.setObjectClassId(classId);
          }
          else {
            sec.setObjectClassName(clazz.getName());
          }
          ndx++;
        }
        PdoUtilities.getInstance().saveCollection(trackedClassRules);
      }
      if (trackedPdoRules != null) {
        PdoUtilities.getInstance().deleteCollection(trackedPdoRules.getRemovedObjects());
        int ndx = 0;
        for (Security sec: trackedPdoRules) {
          sec.setPriority(ndx);
          sec.setObjectClassId(classId);
          sec.setObjectId(pdo.getId());
          ndx++;
        }
        PdoUtilities.getInstance().saveCollection(trackedPdoRules);
      }
      return null;
    });

    getStage().hide();
  }

  private boolean cancel() {
    if ((trackedClassRules == null || !trackedClassRules.isModified() && !trackedClassRules.isElementModified()) &&
        (trackedPdoRules == null || !trackedPdoRules.isModified() && !trackedPdoRules.isElementModified()) ||
        Fx.question(RdcFxRdcBundle.getString("Rules were modified. Really discard all changes?"), false)) {

      getStage().hide();
      return true;
    }
    return false;
  }

}
