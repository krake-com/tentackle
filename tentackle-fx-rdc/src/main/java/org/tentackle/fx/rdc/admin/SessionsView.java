/*
 * Copyright (C) 2015 by:
 *
 * Hydro Aluminium Rolled Users GmbH
 * Koblenzer Strasse 122
 * D-41468 Neuss
 * Germany
 * Tel. +49 2131 382 0
 * http://www.hydro.com/Deutschland
 *
 * All Rights Reserved.
 */

package org.tentackle.fx.rdc.admin;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import org.tentackle.bind.Bindable;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.container.FxHBox;
import org.tentackle.fx.rdc.RdcFactory;
import org.tentackle.fx.rdc.security.SecurityDialogFactory;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.fx.rdc.table.TablePopup;
import org.tentackle.pdo.AdminExtension;
import org.tentackle.pdo.AdminExtension.SessionData;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoRemoteSession;



/**
 * View showing all sessions logged in to the middle tier.<br>
 * Allows killing sessions as well.
 *
 * @author harald
 */
@FxControllerService
public class SessionsView extends AbstractFxController {

  @Bindable
  private List<SessionData> sessions;

  @FXML
  private FxTableView<SessionData> sessionsNode;
  @FXML
  private FxHBox buttonBox;
  @FXML
  private FxButton securityButton;
  @FXML
  private FxButton refreshButton;
  @FXML
  private FxButton killButton;


  private ResourceBundle resources;
  private AdminExtension adminExt;
  private TablePopup<SessionData> popup;



  @Override
  @SuppressWarnings("unchecked")
  public void initialize(URL location, ResourceBundle resources) {
    this.resources = resources;

    securityButton.setGraphic(Fx.createImageView("security"));
    securityButton.setOnAction(e -> security());

    refreshButton.setGraphic(Fx.createImageView("reload"));
    refreshButton.setOnAction(e -> refresh());

    killButton.setGraphic(Fx.createImageView("cancel"));
    killButton.setOnAction(e -> kill());
    killButton.disableProperty().bind(sessionsNode.getSelectionModel().selectedItemProperty().isNull());

    popup = RdcFactory.getInstance().createTablePopup(sessionsNode, "Sessions", false, resources.getString("Sessions"));

    TableConfiguration<SessionData> config = createTableConfiguration();
    config.getBinder().bind();
    config.configure(sessionsNode);
  }


  @Override
  public void configure() {
    DomainContext context = Pdo.createDomainContext();
    if (context.getSession().isRemote()) {    // needs check to run tests
      PdoRemoteSession remoteSession = (PdoRemoteSession) context.getSession().getRemoteSession();
      adminExt = remoteSession.getExtension(context, AdminExtension.class);
      popup.loadPreferences();
      securityButton.setDisable(!SecurityDialogFactory.getInstance().isDialogAllowed(context));
      refresh();
    }
  }


  /**
   * Gets the button box.<br>
   * Used to add additional buttons, for example to close the stage.
   *
   * @return the button box
   */
  public FxHBox getButtonBox() {
    return buttonBox;
  }

  /**
   * Retrieves all sessions and refreshes the table view.
   */
  private void refresh() {
    sessions = adminExt.getSessions();
    sessionsNode.updateView();
  }

  /**
   * Kills all sessions for marked user session.
   */
  private void kill() {
    SessionData session = sessionsNode.getSelectionModel().getSelectedItem();
    if (session != null &&
        Fx.question(resources.getString("really kill the session?"), false) &&
        adminExt.kill(session.getUserId(), session.getSessionGroup(),
                      session.getApplicationName(), session.getApplicationId()) > 0) {
      refresh();
    }
  }

  /**
   * Shows the security dialog.
   */
  private void security() {
    SecurityDialogFactory.getInstance().showDialog(AdminExtension.class);
  }


  private TableConfiguration<SessionData> createTableConfiguration() {
    TableConfiguration<SessionData> config = FxFactory.getInstance().createTableConfiguration(SessionData.class, null);
    config.addColumn("userId", resources.getString("user-ID"));
    config.addColumn("userName", resources.getString("user"));
    config.addColumn("applicationName", resources.getString("application"));
    config.addColumn("applicationId", resources.getString("appl.-ID"));
    config.addColumn("clientVersionInfo", resources.getString("version"));
    config.addColumn("locale", resources.getString("locale"));
    config.addColumn("timeZone", resources.getString("timezone"));
    config.addColumn("vmInfo", resources.getString("VM"));
    config.addColumn("osInfo", resources.getString("OS"));
    config.addColumn("hostInfo", resources.getString("hostname"));
    config.addColumn("clientHost", resources.getString("client"));
    config.addColumn("since", resources.getString("since"));
    config.addColumn("sessionNumber", resources.getString("session-no."));
    config.addColumn("sessionGroup", resources.getString("group-no."));
    config.addColumn("options", resources.getString("options"));
    return config;
  }

}
