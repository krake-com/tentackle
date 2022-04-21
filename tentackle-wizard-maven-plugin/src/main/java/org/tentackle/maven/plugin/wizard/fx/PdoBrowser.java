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

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.maven.plugin.MojoExecutionException;

import org.tentackle.bind.Bindable;
import org.tentackle.common.Timestamp;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxCheckBox;
import org.tentackle.fx.component.FxChoiceBox;
import org.tentackle.fx.component.FxComboBox;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.fx.component.Note;
import org.tentackle.fx.container.FxBorderPane;
import org.tentackle.fx.container.FxHBox;
import org.tentackle.fx.container.FxSplitPane;
import org.tentackle.fx.rdc.GuiProvider;
import org.tentackle.fx.rdc.GuiProviderFactory;
import org.tentackle.fx.rdc.PdoFinder;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.table.TablePopup;
import org.tentackle.maven.plugin.wizard.BrowseMojo;
import org.tentackle.maven.plugin.wizard.PdoProfile;
import org.tentackle.maven.plugin.wizard.pdodata.DataDiff;
import org.tentackle.maven.plugin.wizard.pdodata.DataObject;
import org.tentackle.maven.plugin.wizard.pdodata.JavaCodeFactory;
import org.tentackle.maven.plugin.wizard.pdodata.PdoWrapper;
import org.tentackle.misc.FormatHelper;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PersistentDomainObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog to browse PDOs and generate test code.
 */
@FxControllerService(resources = FxControllerService.RESOURCES_NONE)
public class PdoBrowser extends AbstractFxController implements DomainContextProvider {

  /**
   * Shows the modal dialog.
   *
   * @param mojo the browser mojo
   * @param context the domain context
   */
  public static Stage show(BrowseMojo mojo, DomainContext context) throws MojoExecutionException {
    Stage stage = Fx.createStage(Modality.APPLICATION_MODAL);
    PdoBrowser diffDialog = Fx.load(PdoBrowser.class);
    Scene scene = Fx.createScene(diffDialog.getView());
    stage.setScene(scene);
    stage.setTitle("PDO Browser");
    diffDialog.setContext(mojo, context);
    diffDialog.getContainer().updateView();
    stage.show();
    return stage;
  }



  private static final int LATEST_DEFAULT = 100;      // number of latest to show initially

  @Bindable private int latest = LATEST_DEFAULT;
  @Bindable private PdoProfile profile;
  @Bindable private boolean rootOnly;
  @Bindable private PdoType entity;
  @Bindable private List<PdoWrapper> pdos;

  @FXML private FxSplitPane splitPane;
  @FXML private FxChoiceBox<PdoProfile> profileField;
  @FXML private FxCheckBox rootOnlyField;
  @FXML private FxComboBox<PdoType> entityField;
  @FXML private FxTableView<PdoWrapper> pdosTableView;
  @FXML private FxTextField latestField;
  @FXML private FxButton refreshButton;
  @FXML private FxButton finderButton;
  @FXML private FxButton configButton;
  @FXML private FxButton generateButton;
  @FXML private FxButton memorizeButton;
  @FXML private FxButton closeButton;

  private BrowseMojo mojo;
  private DomainContext context;
  private TablePopup<PdoWrapper> pdosTablePopup;
  private DataObject dataObject;
  private DataObject oldDataObject;
  private PdoView pdoView;
  private Timestamp loadingTime;
  private Timestamp memorizeTime;
  @SuppressWarnings("rawtypes")
  private PdoFinder finder;
  private Map<String, Boolean> codeConfiguration;


  @FXML
  private void initialize() {
    pdoView = Fx.load(PdoView.class);
    splitPane.getItems().add(pdoView.getView());

    profileField.addViewToModelListener(this::updateEntityFieldProperties);
    rootOnlyField.addViewToModelListener(this::updateEntityFieldProperties);
    entityField.setListenerSuppressedIfModelUnchanged(true);
    entityField.setVisibleRowCount(25);
    entityField.addViewToModelListener(this::run);
    latestField.addViewToModelListener(() -> {
      finder = null;
      run();
    });

    refreshButton.setGraphic(Fx.createGraphic("reload"));
    refreshButton.setDisable(true);
    refreshButton.setOnAction(e -> run());
    finderButton.setGraphic(Fx.createGraphic("search"));
    finderButton.setDisable(true);
    finderButton.setFocusTraversable(true); // trigger focus lost
    memorizeButton.setGraphic(Fx.createGraphic("copy"));
    memorizeButton.setDisable(true);
    memorizeButton.setOnAction(event -> {
      oldDataObject = dataObject;
      memorizeTime = new Timestamp();
      showDiff();
    });
    configButton.setGraphic(Fx.createGraphic("preferences"));
    configButton.setOnAction(e -> showCodeConfigurationDialog());
    configButton.setDisable(true);
    generateButton.setGraphic(Fx.createGraphic("edit"));
    generateButton.setDisable(true);
    generateButton.setOnAction(e -> generateCode());
    closeButton.setGraphic(Fx.createGraphic("close"));
    closeButton.setOnAction(this::close);

    pdosTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    pdosTableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<PdoWrapper>) c -> {
      ObservableList<? extends PdoWrapper> selectedItems = c.getList();
      if (selectedItems.isEmpty()) {
        dataObject = null;
        oldDataObject = null;
        memorizeTime = null;
        codeConfiguration = null;
        configButton.setDisable(true);
        generateButton.setDisable(true);
        memorizeButton.setDisable(true);
        showDiff();
      }
      else {
        dataObject = selectedItems.get(0).getDataObject();
        memorizeButton.setDisable(false);
        generateButton.setDisable(false);
        updateCodeConfiguration(selectedItems);
        showDiff();
      }
    });
  }

  @Override
  public void configure() {
    pdosTablePopup = Rdc.createTablePopup(pdosTableView, "pdoView", "PDOs");
    pdosTablePopup.setColumnMenuEnabled(false);
    pdosTablePopup.loadPreferences();
    double leftWidth = pdosTableView.getPrefWidth();
    double totalWidth = pdosTableView.getPrefWidth() + pdoView.getPrefWidth();
    if (leftWidth >= 1.0 && totalWidth > leftWidth) {
      splitPane.setDividerPosition(0,  leftWidth / totalWidth);
    }
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  /**
   * Sets the context.
   *
   * @param mojo the browser mojo
   * @param context the domain context
   * @throws MojoExecutionException if loading the profiles failed
   */
  public void setContext(BrowseMojo mojo, DomainContext context) throws MojoExecutionException {
    this.mojo = mojo;
    this.context = context;
    profileField.getItems().setAll(mojo.getProfiles(PdoProfile.class));
    updateEntityFieldProperties();
    entityField.requestFocus();
    showDiff();
  }


  private void updateCodeConfiguration(List<? extends PdoWrapper> selectedItems) {
    codeConfiguration = new LinkedHashMap<>();
    for (PdoWrapper selectedItem : selectedItems) {
      codeConfiguration.putAll(selectedItem.getCodeConfiguration());
    }
    configButton.setDisable(codeConfiguration.isEmpty());
  }

  private void showDiff() {
    if (dataObject != null) {
      pdoView.setDiff(DataDiff.compare(dataObject, oldDataObject),
                      loadingTime == null ? "Value" : FormatHelper.formatTimestampFast(loadingTime),
                      memorizeTime == null ? "" : FormatHelper.formatTimestampFast(memorizeTime));
    }
    else {
      pdoView.setDiff(null, "Value", "");
    }
  }

  private void updateEntityFieldProperties() {
    entityField.getProperties().put(PdoTypeStringTranslator.ROOT_ONLY, rootOnly);
    entityField.getProperties().put(PdoTypeStringTranslator.PROFILE, profile);
    Platform.runLater(entityField.getValueTranslator()::bindingPropertiesUpdated);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void run() {
    PdoWrapper selectedItem = pdosTableView.getSelectionModel().getSelectedItem();
    DataObject savedOldDataObject = oldDataObject;
    Timestamp savedMemorizedTime = memorizeTime;

    pdosTableView.getSelectionModel().clearSelection();
    loadingTime = new Timestamp();

    if (entity != null) {
      PersistentDomainObject<?> proxy = on((Class) entity.getClazz());
      if (finder != null && finder.getPdo().getEffectiveClass() != entity.getClazz()) {
        finder = null;
      }
      if (finder == null) {
        pdos = proxy.selectLatest(0, latest).stream().map(PdoWrapper::new).toList();
      }
      refreshButton.setDisable(false);
      GuiProvider<?> guiProvider = getGuiProvider(proxy);
      if (guiProvider != null && guiProvider.isFinderAvailable()) {
        finderButton.setDisable(false);
        finderButton.setOnAction(event -> {
          finder = guiProvider.createFinder();
          finder.setPdo(proxy);
          Stage stage = Fx.createStage(Modality.APPLICATION_MODAL);
          FxBorderPane borderPane = Fx.create(BorderPane.class);
          borderPane.setPadding(new Insets(10));
          borderPane.setCenter(finder.getView());
          FxHBox hBox = Fx.create(HBox.class);
          hBox.setSpacing(10);
          hBox.setPadding(new Insets(20, 0, 0, 0));
          hBox.setAlignment(Pos.BASELINE_RIGHT);
          FxButton cancelButton = Fx.create(Button.class);
          cancelButton.setText("cancel");
          cancelButton.setGraphic(Fx.createGraphic("cancel"));
          cancelButton.setOnAction(e -> {
            stage.close();
            finder = null;
            run();
          });
          FxButton findButton = Fx.create(Button.class);
          findButton.setText("find");
          findButton.setGraphic(Fx.createGraphic("search"));
          findButton.setOnAction(e -> {
            pdos = finder.runSearch().stream().map(pdo -> new PdoWrapper((PersistentDomainObject<?>) pdo)).toList();
            pdosTableView.updateView();
            stage.close();
          });
          hBox.getChildren().addAll(findButton, cancelButton);
          borderPane.setBottom(hBox);
          Scene scene = Fx.createScene(borderPane);
          stage.setScene(scene);
          stage.setTitle("Find " + proxy.getPlural());
          borderPane.updateView();
          stage.show();
        });
      }
      else {
        finderButton.setDisable(true);
      }
    }
    else {
      pdos = new ArrayList<>();
      refreshButton.setDisable(true);
      finderButton.setDisable(true);
      finder = null;
    }

    pdosTableView.updateView();
    if (selectedItem != null && pdos.contains(selectedItem)) {
      oldDataObject = savedOldDataObject;
      memorizeTime = savedMemorizedTime;
      pdosTableView.getSelectionModel().select(selectedItem);
    }
  }

  private void showCodeConfigurationDialog() {
    if (codeConfiguration != null) {
      Stage stage = Fx.createStage(Modality.APPLICATION_MODAL);
      stage.initOwner(getStage());
      BorderPane borderPane = Fx.create(BorderPane.class);
      ScrollPane scrollPane = Fx.create(ScrollPane.class);
      VBox vBox = Fx.create(VBox.class);
      vBox.setPadding(new Insets(10.0));
      vBox.setSpacing(5.0);
      scrollPane.setMinWidth(300);
      scrollPane.setMinHeight(150);
      for (Map.Entry<String, Boolean> entry : codeConfiguration.entrySet()) {
        CheckBox checkBox = Fx.create(CheckBox.class);
        checkBox.setText(entry.getKey());
        checkBox.setSelected(entry.getValue());
        checkBox.setOnAction(e -> entry.setValue(checkBox.isSelected()));
        vBox.getChildren().add(checkBox);
      }
      scrollPane.setContent(vBox);
      borderPane.setCenter(scrollPane);
      HBox hBox = Fx.create(HBox.class);
      Button closeButton = Fx.create(Button.class);
      closeButton.setText("close");
      closeButton.setGraphic(Fx.createGraphic("close"));
      closeButton.setOnAction(event -> stage.close());
      hBox.getChildren().add(closeButton);
      hBox.setPadding(new Insets(10.0));
      hBox.setAlignment(Pos.BASELINE_RIGHT);
      borderPane.setBottom(hBox);
      Scene scene = Fx.createScene(borderPane);
      stage.setScene(scene);
      stage.setTitle("Code Configuration");
      stage.show();
    }
  }

  private void generateCode() {
    ObservableList<PdoWrapper> selectedItems = pdosTableView.getSelectionModel().getSelectedItems();
    if (!selectedItems.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      buf.append("// generated at ").append(FormatHelper.formatShortTimestampFast(new Timestamp())).append(" by ")
         .append(System.getProperty("user.name"))
         .append(" on ")
         .append(mojo.getHostName())
         .append(" from ")
         .append(getDomainContext().getSession()).append("\n\n");

      DataObject[] dataObjects = new DataObject[selectedItems.size()];
      int i = 0;
      for (PdoWrapper pdoWrapper : selectedItems) {
        dataObjects[i++] = pdoWrapper.getDataObject();
      }
      String text = JavaCodeFactory.generateCode(codeConfiguration, dataObjects);
      buf.append(text);

      ClipboardContent cbc = new ClipboardContent();
      cbc.putString(buf.toString());
      Clipboard.getSystemClipboard().setContent(cbc);

      // show some feedback
      Note infoNote = Fx.create(Note.class);
      infoNote.setPosition(Note.Position.TOP);
      String topic = selectedItems.size() > 1 ? (selectedItems.size() + " " + dataObject.getPdo().getPlural()) : dataObject.toString();
      infoNote.setText("Java code for " + topic + " copied to clipboard");
      infoNote.setFadeEffect(500, 2000, 1000);
      infoNote.show(generateButton);
    }
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private GuiProvider<?> getGuiProvider(PersistentDomainObject proxy) {
    if (GuiProviderFactory.getInstance().isGuiProviderAvailable(proxy.getEffectiveClass())) {
      return GuiProviderFactory.getInstance().createGuiProvider(proxy);
    }
    return null;
  }

  private void close(Event event) {
    getStage().hide();
  }

}
