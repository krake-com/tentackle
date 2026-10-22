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

package org.tentackle.fx.rdc.login;

import org.tentackle.bind.Bindable;
import org.tentackle.bind.BindingVetoException;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxCheckBox;
import org.tentackle.fx.component.FxChoiceBox;
import org.tentackle.fx.component.FxListView;
import org.tentackle.fx.component.FxPasswordField;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.fx.container.FxGridPane;
import org.tentackle.fx.container.FxTab;
import org.tentackle.fx.rdc.RdcFxRdcBundle;
import org.tentackle.session.BackendConfiguration;
import org.tentackle.session.DriverConfiguration;
import org.tentackle.validate.ValidationResult;
import org.tentackle.validate.ValidationScopeFactory;
import org.tentackle.validate.ValidationUtilities;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import java.io.File;
import java.text.MessageFormat;
import java.util.List;

/**
 * Controller to add, edit, and remove backends.<br>
 * The backend and JDBC driver configurations are stored in the user or system preferences.
 * One backend can be marked as the default.
 */
@FxControllerService(binding = FxControllerService.BINDING.BINDABLE_INHERITED)
public class Backends extends AbstractFxController {

  /**
   * Opens a modal dialog to edit the backend and driver configurations.<br>
   * The configurations are persisted when the dialog is closed.
   *
   * @param owner the owner window or node
   * @param application the application name
   * @param system true if system preferences, else user preferences
   * @param updatedRunnable invoked whenever the backend was modified, null if none
   */
  public static void editBackends(Object owner, String application, boolean system, Runnable updatedRunnable) {
    Backends controller = Fx.load(Backends.class);
    Scene scene = Fx.createScene(controller.getView());
    Stage stage = Fx.createStage(Modality.APPLICATION_MODAL, owner);
    stage.setScene(scene);
    stage.setTitle(RdcFxRdcBundle.getString("Backends"));
    controller.setApplication(application, system);
    controller.setUpdatedRunnable(updatedRunnable);
    controller.getView().addEventFilter(KeyEvent.KEY_RELEASED, event -> {
      if (event.getCode() == KeyCode.ESCAPE) {
        controller.persist();
        stage.close();
      }
    });
    stage.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, _ -> controller.persist());
    Fx.show(stage);
  }



  private String application;         // the application name
  private boolean system;             // system preferences?
  private Runnable updatedRunnable;   // backends were updated


  // list of backends with buttons to add and remove a backend
  @FXML
  private FxTab backendTab;
  @FXML
  private FxListView<BackendConfiguration> backends;
  @FXML
  private FxButton addBackendButton;
  @FXML
  private FxButton removeBackendButton;

  // the current backend
  @Bindable
  private BackendConfiguration backend;

  @FXML
  private FxGridPane backendPane;
  @FXML
  private FxTextField backendName;
  @FXML
  private FxTextField backendUrl;
  @FXML
  private FxTextArea backendOptions;
  @FXML
  private FxChoiceBox<DriverConfiguration> backendDriver;
  @FXML
  private FxTextField backendUser;
  @FXML
  private FxPasswordField backendPassword;
  @FXML
  private FxCheckBox asDefault;


  // list of drivers with buttons to add and remove a driver
  @FXML
  private FxTab driverTab;
  @FXML
  private FxListView<DriverConfiguration> drivers;
  @FXML
  private FxButton addDriverButton;
  @FXML
  private FxButton removeDriverButton;

  // the current driver
  @Bindable
  private DriverConfiguration driver;

  @FXML
  private FxGridPane driverPane;
  @FXML
  private FxTextField driverName;
  @FXML
  private FxTextField driverDriver;
  @FXML
  private FxTextField driverUrl;
  @FXML
  private FxButton driverButton;


  private String defaultBackendName;


  /**
   * Creates the backends controller.
   */
  public Backends() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @FXML
  private void initialize() {
    asDefault.setBindable(false);

    backends.setBindable(false);
    backends.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    backends.getSelectionModel().selectedItemProperty().addListener((_, oldValue, newValue) -> {
      if (oldValue != null) {
        backendPane.updateModel();  // necessary bec. focus lost may arrive after the change event
        persist(oldValue);
      }
      backend = newValue;
      removeBackendButton.setDisable(backend == null);
      asDefault.setSelected(backend != null && defaultBackendName != null && defaultBackendName.equals(backend.getName()));
      backendPane.setChangeable(backend != null);
      backendPane.updateView();
    });

    addBackendButton.setGraphic(Fx.createGraphic("add"));
    addBackendButton.setOnAction(_ -> {
      backends.getItems().add(new BackendConfiguration(application, null, null, null,null, null, null));
      backends.getSelectionModel().selectLast();
      backendName.requestFocus();
    });

    removeBackendButton.setGraphic(Fx.createGraphic("delete"));
    removeBackendButton.setOnAction(_ -> {
      BackendConfiguration current = backends.getSelectionModel().getSelectedItem();
      if (current != null) {
        backends.getItems().remove(current);
        backends.getSelectionModel().selectFirst();
        backends.scrollTo(0);
        if (current.getName() != null) {
          current.remove(system);
        }
      }
    });

    backendTab.setOnSelectionChanged(_ -> {
      if (backendTab.isSelected()) {
        loadDriverConfigurations();
      }
    });

    drivers.setBindable(false);
    drivers.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
    drivers.getSelectionModel().selectedItemProperty().addListener((_, oldValue, newValue) -> {
      if (oldValue != null) {
        driverPane.updateModel();   // necessary bec. focus lost may arrive after the change event
        persist(driver);
      }
      driver = newValue;
      removeDriverButton.setDisable(driver == null);
      driverPane.setChangeable(driver != null);
      driverButton.setDisable(driver == null);
      driverPane.updateView();
    });
    driverPane.setChangeable(false);
    driverButton.setDisable(true);

    addDriverButton.setGraphic(Fx.createGraphic("add"));
    addDriverButton.setOnAction(_ -> {
      drivers.getItems().add(new DriverConfiguration(null, null, null));
      drivers.getSelectionModel().selectLast();
      driverName.requestFocus();
    });

    removeDriverButton.setGraphic(Fx.createGraphic("delete"));
    removeDriverButton.setOnAction(_ -> {
      DriverConfiguration current = drivers.getSelectionModel().getSelectedItem();
      if (current != null) {
        drivers.getItems().remove(current);
        drivers.getSelectionModel().selectFirst();
        drivers.scrollTo(0);
        if (current.getName() != null) {
          current.remove(system);
        }
      }
    });

    driverButton.setGraphic(Fx.createGraphic("file"));
    driverButton.setOnAction(_ -> {
      FileChooser fileChooser = new FileChooser();
      fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java JAR File", "*.jar"));
      File file = fileChooser.showOpenDialog(Fx.getStage(getView()));
      if (file != null) {
        driver.setUrl(file.getAbsolutePath());
        driverUrl.updateView();
      }
    });
  }

  @Override
  public void configure() {
    // support renaming and check for duplicates
    backendName.getBinding().addToModelListener(e -> {
      if (e.getValue() != null && !e.getValue().equals(backend.getName())) {
        // name changed
        String newName = (String) e.getValue();
        for (BackendConfiguration b : backends.getItems()) {
          if (b != backend && newName.equals(b.getName())) {
            throw new BindingVetoException(true, MessageFormat.format(RdcFxRdcBundle.getString("{0} ALREADY EXISTS"),b));
          }
        }
        if (backend.isPersisted()) {
          backend.remove(system); // remove the old instance, will be persisted as new
        }
        Platform.runLater(backends::refresh); // update name in the list
      }
    });
    driverName.getBinding().addToModelListener(e -> {
      if (e.getValue() != null && !e.getValue().equals(driver.getName())) {
        // name changed
        String newName = (String) e.getValue();
        for (DriverConfiguration b : drivers.getItems()) {
          if (b != driver && newName.equals(b.getName())) {
            throw new BindingVetoException(true, MessageFormat.format(RdcFxRdcBundle.getString("{0} ALREADY EXISTS"),b));
          }
        }
        if (driver.isPersisted()) {
          driver.remove(system);
        }
        Platform.runLater(drivers::refresh);
      }
    });
  }

  /**
   * Sets the application name and loads the configured backends.
   *
   * @param application the application name
   * @param system true to use system preferences, else user preferences
   */
  public void setApplication(String application, boolean system) {
    this.application = application;
    this.system = system;
    backends.getSelectionModel().clearSelection();
    backends.getItems().addAll(BackendConfiguration.getBackendConfigurations(application, system).values());
    drivers.getSelectionModel().clearSelection();
    drivers.getItems().addAll(DriverConfiguration.getDriverConfigurations(system).values());
    BackendConfiguration defaultBackend = BackendConfiguration.getDefault(application, system);
    defaultBackendName = defaultBackend != null ? defaultBackend.getName() : null;
    loadDriverConfigurations();
  }

  /**
   * Gets the runnable invoked when backends were updated.
   *
   * @return the runnable, null if none
   */
  public Runnable getUpdatedRunnable() {
    return updatedRunnable;
  }

  /**
   * Sets the runnable invoked when backends were updated.
   *
   * @param updatedRunnable the runnable, null if none
   */
  public void setUpdatedRunnable(Runnable updatedRunnable) {
    this.updatedRunnable = updatedRunnable;
  }

  /**
   * Gets the selected backend.
   *
   * @return the backend, null if none selected
   */
  public BackendConfiguration getBackend() {
    return backend;
  }

  /**
   * Persists the currently edited backend and driver.<br>
   * Invoked when the dialog is closed.
   */
  public void persist() {
    if (backend != null) {
      backendPane.updateModel();
      persist(backend);
    }
    if (driver != null) {
      driverPane.updateModel();
      persist(driver);
    }
    if (updatedRunnable != null) {
      updatedRunnable.run();
    }
  }


  private void persist(DriverConfiguration driver) {
    List<ValidationResult> results = driver.validate("driver", ValidationScopeFactory.getInstance().getMandatoryScope());
    if (results.isEmpty()) {
      driver.persist(system);
    }
    else {
      Fx.info(getView(), ValidationUtilities.getInstance().resultsToMessage(results));
    }
  }

  private void persist(BackendConfiguration backend) {
    List<ValidationResult> results = backend.validate("backend", ValidationScopeFactory.getInstance().getMandatoryScope());
    if (results.isEmpty()) {
      backend.persist(system);
      if (asDefault.isSelected()) {
        defaultBackendName = backend.getName();
        BackendConfiguration.setDefault(backend, application, system);
      }
      else if (backend.getName().equals(defaultBackendName)) {
        defaultBackendName = null;
        BackendConfiguration.setDefault(null, application, system);
      }
    }
    else {
      Fx.info(getView(), ValidationUtilities.getInstance().resultsToMessage(results));
    }
  }

  private void loadDriverConfigurations() {
    DriverConfiguration driverConfiguration = backendDriver.getSelectionModel().getSelectedItem();
    backendDriver.getItems().setAll(drivers.getItems());
    if (driverConfiguration != null) {
      backendDriver.getSelectionModel().select(driverConfiguration);
    }
  }
}
