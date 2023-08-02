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

package org.tentackle.fx.rdc.admin;

import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxFactory;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.container.FxHBox;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.security.SecurityDialogFactory;
import org.tentackle.fx.rdc.table.TablePopup;
import org.tentackle.fx.table.TableConfiguration;
import org.tentackle.pdo.AdminExtension;
import org.tentackle.pdo.AdminExtension.SessionData;
import org.tentackle.pdo.DomainContext;
import org.tentackle.pdo.Pdo;
import org.tentackle.pdo.PdoRemoteSession;

import javafx.fxml.FXML;
import javafx.scene.control.SelectionMode;
import java.text.MessageFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * View showing all sessions logged in to the middle tier.<br>
 * Allows killing sessions as well.
 *
 * @author harald
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class SessionsView extends AbstractFxController {

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

  @FXML
  private ResourceBundle resources;

  private AdminExtension adminExt;
  private TablePopup<SessionData> popup;

  /**
   * Creates a session view controller.
   */
  public SessionsView() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @FXML
  private void initialize() {
    securityButton.setGraphic(Fx.createGraphic("security"));
    securityButton.setOnAction(e -> showSecurityDialog());

    refreshButton.setGraphic(Fx.createGraphic("reload"));
    refreshButton.setOnAction(e -> refresh());

    killButton.setGraphic(Fx.createGraphic("cancel"));
    killButton.setOnAction(e -> killSessions());
    killButton.disableProperty().bind(sessionsNode.getSelectionModel().selectedItemProperty().isNull());

    sessionsNode.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
  }


  @Override
  public void configure() {
    popup = Rdc.createTablePopup(sessionsNode, "Sessions", resources.getString("Sessions"));
    DomainContext context = Pdo.createDomainContext();
    if (context.getSession().isRemote()) {    // needs check in order not to fail when running GUI tests
      PdoRemoteSession remoteSession = (PdoRemoteSession) context.getSession().getRemoteSession();
      adminExt = remoteSession.getExtension(context, AdminExtension.class);
      popup.loadPreferences();
      securityButton.setDisable(!SecurityDialogFactory.getInstance().isDialogAllowed(context));
      killButton.setVisible(isKillSessionAllowed());
      refresh();
    }

    TableConfiguration<SessionData> config = createTableConfiguration();
    config.getBinder().bind();
    config.configure(sessionsNode);
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
   * Returns whether killing a session is allowed.
   *
   * @return true if allowed
   */
  protected boolean isKillSessionAllowed() {
    return true;
  }

  /**
   * Shows the security dialog.
   */
  protected void showSecurityDialog() {
    SecurityDialogFactory.getInstance().showDialog(AdminExtension.class);
  }

  /**
   * Creates the table configuration.
   *
   * @return the table config
   */
  protected TableConfiguration<SessionData> createTableConfiguration() {
    TableConfiguration<SessionData> config = FxFactory.getInstance().createTableConfiguration(SessionData.class, null);
    config.setBaseBundleName(getClass().getName());
    config.addColumn("userId", resources.getString("user-ID"));
    config.addColumn("userName", resources.getString("user"));
    config.addColumn("applicationName", resources.getString("application"));
    config.addColumn("applicationId", resources.getString("appl.-ID"));
    config.addColumn("clientVersion", resources.getString("version"));
    config.addColumn("locale", resources.getString("locale"));
    config.addColumn("timeZone", resources.getString("timezone"));
    config.addColumn("vmInfo", resources.getString("VM"));
    config.addColumn("osInfo", resources.getString("OS"));
    config.addColumn("hostInfo", resources.getString("hostname"));
    config.addColumn("clientHost", resources.getString("client"));
    config.addColumn("since", resources.getString("since"));
    config.addColumn("sessionNumber", resources.getString("session-no."));
    config.addColumn("sessionGroup", resources.getString("group-no."));
    config.addColumn("sessionName", resources.getString("name"));
    config.addColumn("cloned", resources.getString("cloned"));
    config.addColumn("options", resources.getString("options"));
    return config;
  }


  /**
   * Retrieves all sessions and refreshes the table view.
   */
  private void refresh() {
    sessions = adminExt.getSessions();
    sessionsNode.getItems().setAll(sessions);
    sessionsNode.getSelectionModel().clearSelection();
  }

  /**
   * Kills all marked user sessions.
   */
  private void killSessions() {
    Set<SessionData> toKill = new LinkedHashSet<>();
    // include all sessions of a group, even if not all are selected
    for (SessionData session : sessionsNode.getSelectionModel().getSelectedItems()) {
      toKill.add(session);
      if (session.getSessionGroup() != 0) {
        int row = 0;
        for (SessionData otherSession: sessions) {
          if (otherSession.getSessionGroup() == session.getSessionGroup() && otherSession.getUserId() == session.getUserId()) {
            toKill.add(otherSession);
            sessionsNode.getSelectionModel().select(row, null);
          }
          row++;
        }
      }
    }
    if (!toKill.isEmpty()) {
      String msg = toKill.size() > 1 ?
                   MessageFormat.format(resources.getString("really kill {0} sessions?"), toKill.size()) :
                   MessageFormat.format(resources.getString("really kill session {0}?"), toKill.iterator().next().getSessionNumber());
      Fx.yes(msg, false, () -> {
        for (SessionData session : toKill) {
          adminExt.kill(session.getUserId(), session.getSessionGroup(),
                        session.getApplicationName(), session.getApplicationId());
        }
        refresh();
      });
    }
  }

}
