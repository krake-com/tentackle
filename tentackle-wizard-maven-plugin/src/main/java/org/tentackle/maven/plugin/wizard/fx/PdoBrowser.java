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

import org.apache.maven.plugin.MojoExecutionException;

import org.tentackle.bind.Bindable;
import org.tentackle.common.Timestamp;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.NotificationBuilder;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxCheckBox;
import org.tentackle.fx.component.FxChoiceBox;
import org.tentackle.fx.component.FxComboBox;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.fx.component.FxTextField;
import org.tentackle.fx.component.FxToggleButton;
import org.tentackle.fx.component.Note;
import org.tentackle.fx.component.config.PrefixSelectionFeature;
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
import org.tentackle.maven.plugin.wizard.pdodata.DataItem;
import org.tentackle.maven.plugin.wizard.pdodata.DataList;
import org.tentackle.maven.plugin.wizard.pdodata.DataNode;
import org.tentackle.maven.plugin.wizard.pdodata.DataNodeFactory;
import org.tentackle.maven.plugin.wizard.pdodata.DataObject;
import org.tentackle.maven.plugin.wizard.pdodata.JavaCodeFactory;
import org.tentackle.maven.plugin.wizard.pdodata.NonCompositeRelation;
import org.tentackle.maven.plugin.wizard.pdodata.PdoWrapper;
import org.tentackle.misc.FormatHelper;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.DomainContextProvider;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.application.Platform;
import javafx.beans.property.StringPropertyBase;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Dialog to browse PDOs and generate test code.
 */
@FxControllerService(resources = FxControllerService.RESOURCES_NONE)
public class PdoBrowser extends AbstractFxController implements DomainContextProvider {

  private static final int LATEST_DEFAULT = 100;                // number of latest PDOs to show initially
  private static final double OUTER_DIVIDER_POSITION = 0.84;    // outer split pane divider position
  private static final String POPUP_CSS = "-fx-font-weight: bold; -fx-font-size: 16;";

  @Bindable private int latest = LATEST_DEFAULT;
  @Bindable private PdoProfile profile;
  @Bindable private boolean rootOnly;
  @Bindable private PdoType entity;
  @Bindable private List<PdoWrapper> pdos;

  @FXML private FxSplitPane outerSplitPane;   // top: innerSplitPane, bottom: row details
  @FXML private FxSplitPane innerSplitPane;   // left: pdos, right: PdoView
  @FXML private FxChoiceBox<PdoProfile> profileField;
  @FXML private FxCheckBox rootOnlyField;
  @FXML private FxComboBox<PdoType> entityField;
  @FXML private FxTableView<PdoWrapper> pdosTableView;
  @FXML private FxTextField latestField;
  @FXML private FxButton loadButton;
  @FXML private FxButton finderButton;
  @FXML private FxButton byIdButton;
  @FXML private FxButton duplicateButton;
  @FXML private FxButton configCodeButton;
  @FXML private FxButton configPathsButton;
  @FXML private FxButton generateButton;
  @FXML private FxToggleButton memorizeButton;

  private BrowseContainer container;
  private Tab containerTab;
  private String tabTitle;
  private BrowseMojo mojo;
  private DataNodeFactory nodeFactory;
  private DomainContext context;
  private TablePopup<PdoWrapper> pdosTablePopup;
  private DataObject dataObject;
  private DataObject memorizedDataObject;
  private PdoView pdoView;
  private TableView<DataNode> rowDetailsTable;
  private Timestamp loadingTime;
  private Timestamp memorizeTime;
  @SuppressWarnings("rawtypes")
  private PdoFinder finder;
  private Map<String, Boolean> codePaths;


  @FXML
  private void initialize() {
    pdoView = Fx.load(PdoView.class);
    pdoView.addSelectedItemListener((_, _, newValue) -> updateRowDetails(newValue == null ? null : newValue.getValue()));
    innerSplitPane.getItems().add(pdoView.getView());
    rowDetailsTable = Fx.create(TableView.class);
    MenuItem copyFQCNItem = Fx.create(MenuItem.class);
    copyFQCNItem.setOnAction(_ -> PdoBrowserUtils.copyFQCN(getSelectedDetailDataNode()));
    MenuItem copyTableItem = Fx.create(MenuItem.class);
    copyTableItem.setOnAction(_ -> PdoBrowserUtils.copyTableName(getSelectedDetailDataNode()));
    MenuItem copyClassIdItem = Fx.create(MenuItem.class);
    copyClassIdItem.setOnAction(_ -> PdoBrowserUtils.copyClassId(getSelectedDetailDataNode()));
    MenuItem copySqlItem = PdoBrowserUtils.createCopySqlItem(_ -> PdoBrowserUtils.copySql(getSelectedDetailDataNode()));
    MenuItem findReferencesItem = PdoBrowserUtils.createFindReferencesItem(
                                    _ -> PdoBrowserUtils.copyReferencingSql(getSelectedDetailDataNode()));
    MenuItem genCodeItem = Fx.create(MenuItem.class);
    genCodeItem.setOnAction(_ -> {
      DataNode node = getSelectedDetailDataNode();
      if (node instanceof DataObject dataObj) {
        // no fine control, but useful to quickly create referenced master data
        Fx.copyToClipboard(JavaCodeFactory.generateCode(null, dataObj));
      }
    });
    genCodeItem.setText("copy Java code to clipboard");
    MenuItem browseItem = Fx.create(MenuItem.class);
    browseItem.setOnAction(_ -> {
      DataNode node = getSelectedDetailDataNode();
      if (node instanceof DataObject dataObj) {
        try {
          PdoBrowser browser = container.addBrowserView();
          Platform.runLater(() -> browser.setupForPdo(new PdoWrapper(dataObj.getPdo())));
        }
        catch (MojoExecutionException mx) {
          mojo.getLog().error(mx);
        }
      }
    });
    browseItem.setText("browse in new tab");
    ContextMenu rowDetailsContextMenu = new ContextMenu(copyFQCNItem, copyTableItem, copyClassIdItem,
                                                        copySqlItem, findReferencesItem, genCodeItem, browseItem);
    rowDetailsContextMenu.addEventHandler(WindowEvent.WINDOW_SHOWING, _ -> {
      genCodeItem.setVisible(getSelectedDetailDataNode() instanceof DataObject);
      PdoBrowserUtils.configureContextMenu(getSelectedDetailDataNode(), copyFQCNItem,
                                           copyTableItem, copyClassIdItem, copySqlItem, findReferencesItem);
    });
    rowDetailsTable.getSelectionModel().selectedItemProperty().addListener(
      (_, _, current) ->
        rowDetailsTable.setContextMenu(current == null ? null : rowDetailsContextMenu));
    outerSplitPane.getItems().add(rowDetailsTable);

    profileField.addViewToModelListener(this::updateEntityFieldProperties);
    rootOnlyField.addViewToModelListener(this::updateEntityFieldProperties);
    entityField.setListenerSuppressedIfModelUnchanged(true);
    entityField.setVisibleRowCount(25);
    entityField.addViewToModelListener(this::run);
    latestField.addViewToModelListener(() -> {
      finder = null;
      run();
    });

    loadButton.setGraphic(Fx.createGraphic("reload"));
    loadButton.setDisable(true);
    loadButton.setOnAction(_ -> run());
    byIdButton.setGraphic(Fx.createGraphic("id"));
    byIdButton.setDisable(true);
    byIdButton.setOnAction(_ -> loadById());
    finderButton.setGraphic(Fx.createGraphic("search"));
    finderButton.setDisable(true);
    duplicateButton.setGraphic(Fx.createGraphic("add"));
    duplicateButton.setOnAction(_ -> duplicate());
    memorizeButton.setGraphic(Fx.createGraphic("copy"));
    memorizeButton.setDisable(true);
    memorizeButton.selectedProperty().addListener((_, _, newValue) -> {
      if (newValue) {
        if (dataObject != null) {
          memorizedDataObject = dataObject;
          memorizeTime = new Timestamp();
        }
        // else: temporarily deselected, e.g., after reload
      }
      else {
        memorizedDataObject = null;
        memorizeTime = null;
      }
      showDiff();
    });
    configCodeButton.setGraphic(Fx.createGraphic("preferences"));
    configCodeButton.setOnAction(_ -> JavaCodeFactorySettings.show(getView()));
    configPathsButton.setGraphic(Fx.createGraphic("tree"));
    configPathsButton.setOnAction(_ -> editCodePaths());
    configPathsButton.setDisable(true);
    generateButton.setGraphic(Fx.createGraphic("generate"));
    generateButton.setDisable(true);
    generateButton.setOnAction(_ -> generateCode());

    pdosTableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    pdosTableView.getSelectionModel().getSelectedItems().addListener((ListChangeListener<PdoWrapper>) c -> {
      ObservableList<? extends PdoWrapper> selectedItems = c.getList();
      if (selectedItems.isEmpty()) {
        dataObject = null;
        codePaths = null;
        configPathsButton.setDisable(true);
        generateButton.setDisable(true);
        memorizeButton.setDisable(true);
        showDiff();
      }
      else {
        dataObject = selectedItems.getFirst().getDataObject(nodeFactory);
        memorizeButton.setDisable(false);
        generateButton.setDisable(false);
        updateCodeConfiguration(selectedItems);
        showDiff();
      }
      updateContainerTab();
    });
  }

  @Override
  public void configure() {
    pdosTableView.getConfiguration().setSortingIncluded(true);
    pdosTablePopup = Rdc.createTablePopup(pdosTableView, "pdoView", "PDOs");
    pdosTablePopup.setColumnMenuEnabled(false);
    pdosTablePopup.loadPreferences();
    double leftWidth = pdosTableView.getPrefWidth();
    double totalWidth = pdosTableView.getPrefWidth() + pdoView.getPrefWidth();
    if (leftWidth >= 1.0 && totalWidth > leftWidth) {
      innerSplitPane.setDividerPosition(0,  leftWidth / totalWidth);
    }
    outerSplitPane.setPrefHeight(pdosTableView.getPrefHeight() / OUTER_DIVIDER_POSITION);
    outerSplitPane.setDividerPosition(0, OUTER_DIVIDER_POSITION);

    ContextMenu pdosContextMenu = pdosTableView.getContextMenu();
    pdosContextMenu.getItems().addFirst(Fx.create(SeparatorMenuItem.class));
    MenuItem reloadPdoItem = Fx.create(MenuItem.class);
    reloadPdoItem.setText("reload");
    reloadPdoItem.setOnAction(_ -> reloadPdos());
    pdosContextMenu.getItems().addFirst(reloadPdoItem);
  }

  @Override
  public DomainContext getDomainContext() {
    return context;
  }

  /**
   * Sets the context.
   *
   * @param container the browse container
   *
   * @throws MojoExecutionException if loading the profiles failed
   */
  public void setContext(BrowseContainer container) throws MojoExecutionException {
    this.container = container;
    this.mojo = container.getMojo();
    this.context = container.getContext();
    nodeFactory = new DataNodeFactory(mojo.getModel());
    profileField.getItems().setAll(mojo.getProfiles(PdoProfile.class));
    updateEntityFieldProperties();
    Platform.runLater(entityField::requestFocus);
    showDiff();
  }

  /**
   * Sets the container tab.
   *
   * @param containerTab the tab to which the browser is attached, null if not attached to a tab
   */
  public void setContainerTab(Tab containerTab) {
    this.containerTab = containerTab;
    this.tabTitle = containerTab == null ? null : containerTab.getText();
    updateContainerTab();
  }

  /**
   * Sets the entity type and selects the PDO.<br>
   * Used to initialize a new browser view.
   *
   * @param pdo the PDO to browse
   */
  public void setupForPdo(PdoWrapper pdo) {
    setupForType(new PdoType(pdo.getPdo()));
    if (!pdos.contains(pdo)) {
      pdos = new ArrayList<>();
      pdos.add(pdo);
      pdosTableView.updateView();
    }
    pdosTableView.getSelectionModel().select(pdo);
    pdosTableView.scrollToCentered(pdo);
  }

  /**
   * Sets the entity type.<br>
   * Used to initialize a new browser view.
   *
   * @param type the entity type to browse
   */
  public void setupForType(PdoType type) {
    entity = type;
    entityField.updateView();
    run();
  }


  private void updateRowDetails(DataDiff diffNode) {
    rowDetailsTable.getColumns().clear();
    if (diffNode != null) {
      switch (diffNode.getLeft()) {
        case DataObject dataObj -> showDataObjects(List.of(dataObj));
        case DataList dataList -> showDataObjects(dataList.getNodes());
        case DataItem dataItem -> showDataItem(dataItem);
        default -> {}
      }
    }
  }

  private void reloadPdos() {
    DataObject savedMemorizedDataObject = memorizedDataObject;
    Timestamp savedMemorizeTime = memorizeTime;
    PdoWrapper selectedItem = pdosTableView.getSelectionModel().getSelectedItem();
    for (PdoWrapper wrapper : pdosTableView.getSelectionModel().getSelectedItems()) {
      wrapper.reload();
    }
    pdosTableView.updateView();
    pdosTableView.getSelectionModel().select(selectedItem);
    Platform.runLater(() -> {
      memorizeButton.setSelected(savedMemorizedDataObject != null);
      memorizedDataObject = savedMemorizedDataObject;
      memorizeTime = savedMemorizeTime;
      showDiff();
    });
  }

  private void showDataItem(DataItem dataItem) {
    NonCompositeRelation nonCompositeRelation = dataItem.getNonCompositeRelation();
    if (nonCompositeRelation != null) {
      showDataObjects(List.of(nodeFactory.create(nonCompositeRelation.getRelatedPdo())));
    }
  }

  private void showDataObjects(List<DataObject> dataObjects) {
    if (dataObjects != null && !dataObjects.isEmpty()) {
      int columnCount = 0;
      List<DataNode> nodes = dataObjects.getFirst().getNodes();
      if (nodes != null) {
        for (DataNode node : nodes) {
          final int columnIndex = columnCount;
          TableColumn<DataNode, String> column = new TableColumn<>(node.getName());
          column.setCellValueFactory(callback -> new StringPropertyBase() {
            @Override
            public DataNode getBean() {
              return callback.getValue();
            }

            @Override
            public String getName() {
              return node.getName();
            }

            @Override
            public String getValue() {
              return getBean().getNodes().get(columnIndex).getValue();
            }
          });
          rowDetailsTable.getColumns().add(column);
          columnCount++;
        }
        rowDetailsTable.getItems().setAll(dataObjects);
      }
    }
  }

  private void updateCodeConfiguration(List<? extends PdoWrapper> selectedItems) {
    codePaths = new LinkedHashMap<>();
    for (PdoWrapper selectedItem : selectedItems) {
      codePaths.putAll(selectedItem.getCodeConfiguration(nodeFactory));
    }
    configPathsButton.setDisable(codePaths.isEmpty());
  }

  private void showDiff() {
    if (dataObject != null) {
      pdoView.setDiff(DataDiff.compare(dataObject, memorizedDataObject),
                      loadingTime == null ? "Value" : FormatHelper.formatTimestamp(loadingTime),
                      memorizeTime == null ? "" : FormatHelper.formatTimestamp(memorizeTime));
    }
    else {
      pdoView.setDiff(null, "Value", "");
    }
  }

  private void updateEntityFieldProperties() {
    entityField.getProperties().put(PdoTypeStringTranslator.ROOT_ONLY, rootOnly);
    entityField.getProperties().put(PdoTypeStringTranslator.PROFILE, profile);
    entityField.getProperties().put(PrefixSelectionFeature.ENABLED, PrefixSelectionFeature.CAMEL_CASE);
    Platform.runLater(entityField.getValueTranslator()::bindingPropertiesUpdated);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  private void run(long... ids) {
    PdoWrapper selectedItem = pdosTableView.getSelectionModel().getSelectedItem();
    DataObject savedMemorizedDataObject;
    Timestamp savedMemorizedTime;
    if (entity == null || (memorizedDataObject != null && !entity.getName().equals(memorizedDataObject.getEntity().getName()))) {
      savedMemorizedDataObject = null;
      savedMemorizedTime = null;
    }
    else {
      savedMemorizedDataObject = memorizedDataObject;
      savedMemorizedTime = memorizeTime;
    }

    pdosTableView.getSelectionModel().clearSelection();
    loadingTime = new Timestamp();

    if (entity != null) {
      PersistentDomainObject<?> proxy = on((Class) entity.getClazz());
      if (finder != null && finder.getPdo().getEffectiveClass() != entity.getClazz()) {
        finder = null;
      }
      if (ids.length > 0) {   // load by IDs
        List<PdoWrapper> pdoList = ids[0] == 0 ? new ArrayList<>() : new ArrayList<>(pdos); // clear if first ID is 0
        Set<PdoWrapper> pdoSet = new HashSet<>(pdoList);
        int insertIndex = 0;
        for (long id : ids) {
          PersistentDomainObject<?> pdo = proxy.on().select(id);
          if (pdo != null) {
            PdoWrapper pdoWrapper = new PdoWrapper(pdo);
            if (pdoSet.add(pdoWrapper)) {
              pdoList.add(insertIndex++, pdoWrapper);   // insert at top
            }
          }
        }
        pdos = pdoList;
        // show some feedback
        Note infoNote = Fx.create(Note.class);
        infoNote.setPosition(Note.Position.TOP);
        infoNote.setText(insertIndex + " PDOs loaded");
        infoNote.setFadeEffect(500, 2000, 1000);
        infoNote.show(byIdButton);
      }
      else if (finder == null) {
        pdos = proxy.selectLatest(0, latest).stream().map(PdoWrapper::new).toList();
      }
      loadButton.setDisable(false);
      byIdButton.setDisable(false);
      GuiProvider<?> guiProvider = getGuiProvider(proxy);
      if (guiProvider != null && guiProvider.isFinderAvailable()) {
        finderButton.setDisable(false);
        finderButton.setOnAction(_ -> {
          finder = guiProvider.createFinder();
          finder.setPdo(proxy);
          Stage stage = Fx.createStage(Modality.APPLICATION_MODAL, null);
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
          cancelButton.setOnAction(_ -> {
            stage.close();
            finder = null;
            run();
          });
          FxButton findButton = Fx.create(Button.class);
          findButton.setText("find");
          findButton.setGraphic(Fx.createGraphic("search"));
          findButton.setOnAction(_ -> {
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
          Fx.show(stage);
        });
      }
      else {
        finderButton.setDisable(true);
      }
    }
    else {
      pdos = new ArrayList<>();
      loadButton.setDisable(true);
      finderButton.setDisable(true);
      finder = null;
    }

    pdosTableView.updateView();
    if (selectedItem != null && pdos.contains(selectedItem)) {
      pdosTableView.getSelectionModel().select(selectedItem); // updates the container tab as well
      pdosTableView.scrollToCentered(selectedItem);
    }
    else {
      updateContainerTab();
    }

    Platform.runLater(() -> {
      memorizeButton.setSelected(savedMemorizedDataObject != null);
      memorizedDataObject = savedMemorizedDataObject;
      memorizeTime = savedMemorizedTime;
      showDiff();
    });

  }

  private void loadById() {
    BorderPane borderPane = Fx.create(BorderPane.class);
    HBox hBox = Fx.create(HBox.class);
    hBox.setPadding(new Insets(10.0));
    FxTextArea textField = Fx.create(TextArea.class);
    textField.setWrapText(true);
    textField.setPrefWidth(500);
    textField.setPrefHeight(200);
    textField.setNavigateViaEnterEnabled(false);
    hBox.getChildren().add(textField);
    borderPane.setCenter(hBox);
    Label headerLabel = Fx.create(Label.class);
    headerLabel.setText("Enter IDs (first 0 to clear)");
    headerLabel.setStyle(POPUP_CSS);
    BorderPane.setAlignment(headerLabel, Pos.CENTER);
    borderPane.setTop(headerLabel);
    Button loadButton = Fx.create(Button.class);
    loadButton.setText("load");
    Popup popup = new Popup();
    Parent notification = FxFactory.getInstance().createNotificationBuilder()
                                   .content(borderPane)
                                   .button(loadButton, false, () -> performLoadById(textField.getText()))
                                   .closeButton(NotificationBuilder.CloseButtonMode.ALWAYS)
                                   .hide(popup::hide)
                                   .build();
    FxUtilities.getInstance().showNotification(getView(), popup, notification, null);
  }

  private void performLoadById(String idString) {
    StringTokenizer stok = new StringTokenizer(idString, " ,:;/\t\n\r\f");
    List<Long> idList = new ArrayList<>();
    while (stok.hasMoreTokens()) {
      String idStr = stok.nextToken();
      try {
        idList.add(Long.valueOf(idStr));
      }
      catch (NumberFormatException nx) {
        Fx.warning(getView(), idStr + " is not a number");
      }
    }
    long[] idArgs = new long[idList.size()];
    int i = 0;
    for (Long id : idList) {
      idArgs[i++] = id;
    }
    run(idArgs);
  }

  private void editCodePaths() {
    if (codePaths != null) {
      BorderPane borderPane = Fx.create(BorderPane.class);
      ScrollPane scrollPane = Fx.create(ScrollPane.class);
      VBox vBox = Fx.create(VBox.class);
      vBox.setPadding(new Insets(10.0));
      vBox.setSpacing(5.0);
      scrollPane.setMinWidth(300);
      scrollPane.setMinHeight(150);
      for (Map.Entry<String, Boolean> entry : codePaths.entrySet()) {
        CheckBox checkBox = Fx.create(CheckBox.class);
        checkBox.setText(entry.getKey());
        checkBox.setSelected(entry.getValue());
        checkBox.setOnAction(_ -> entry.setValue(checkBox.isSelected()));
        vBox.getChildren().add(checkBox);
      }
      scrollPane.setContent(vBox);
      borderPane.setCenter(scrollPane);
      Label headerLabel = Fx.create(Label.class);
      headerLabel.setText("Code Paths");
      headerLabel.setStyle(POPUP_CSS);
      BorderPane.setAlignment(headerLabel, Pos.CENTER);
      borderPane.setTop(headerLabel);

      Popup popup = new Popup();
      Parent notification = FxFactory.getInstance().createNotificationBuilder()
                                     .content(borderPane)
                                     .hide(popup::hide)
                                     .build();
      FxUtilities.getInstance().showNotification(getView(), popup, notification, null);
    }
  }

  private void generateCode() {
    ObservableList<PdoWrapper> selectedItems = pdosTableView.getSelectionModel().getSelectedItems();
    if (!selectedItems.isEmpty()) {
      StringBuilder buf = new StringBuilder();
      buf.append("// generated at ").append(FormatHelper.formatShortTimestamp(new Timestamp())).append(" by ")
         .append(System.getProperty("user.name"))
         .append('@')
         .append(mojo.getHostName())
         .append(" from ")
         .append(getDomainContext().getSession()).append('\n');

      DataObject[] dataObjects = new DataObject[selectedItems.size()];
      int i = 0;
      for (PdoWrapper pdoWrapper : selectedItems) {
        dataObjects[i++] = pdoWrapper.getDataObject(nodeFactory);
      }
      String text = JavaCodeFactory.generateCode(codePaths, dataObjects);
      buf.append(text);
      Fx.copyToClipboard(buf.toString());

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

  private void duplicate() {
    try {
      PdoBrowser browser = container.addBrowserView();
      PdoWrapper pdo = pdosTableView.getSelectionModel().getSelectedItem();
      if (pdo != null) {
        Platform.runLater(() -> browser.setupForPdo(pdo));
      }
      else if (entity != null) {
        Platform.runLater(() -> browser.setupForType(entity));
      }
    }
    catch (MojoExecutionException mx) {
      mojo.getLog().error(mx);
    }
  }

  private DataNode getSelectedDetailDataNode() {
    return rowDetailsTable.getSelectionModel().getSelectedItem();
  }

  private void updateContainerTab() {
    if (containerTab != null) {
      if (dataObject != null) {
        containerTab.setText(dataObject.toString());
      }
      else {
        containerTab.setText(entity == null ? tabTitle : entity.toString());
      }
    }
  }

}
