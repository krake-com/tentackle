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
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxTableView;
import org.tentackle.fx.container.FxHBox;
import org.tentackle.fx.rdc.Rdc;
import org.tentackle.fx.rdc.security.SecurityDialogFactory;
import org.tentackle.fx.rdc.table.TablePopup;
import org.tentackle.fx.table.TableCellType;
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
 * Allows sending a logout request to the selected sessions or killing them. Both apply to all sessions
 * of the same session group and user as well. The security button edits the rules for the
 * {@link AdminExtension}, which the server checks for every call.
 * <p>
 * Requires a remote session, i.e. a three-tier setup.
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
  private FxButton logoutButton;
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
    securityButton.setOnAction(_ -> showSecurityDialog());

    refreshButton.setGraphic(Fx.createGraphic("reload"));
    refreshButton.setOnAction(_ -> refresh());

    logoutButton.setGraphic(Fx.createGraphic("exit"));
    logoutButton.setOnAction(_ -> killSessions(false));
    logoutButton.disableProperty().bind(sessionsNode.getSelectionModel().selectedItemProperty().isNull());

    killButton.setGraphic(Fx.createGraphic("cancel"));
    killButton.setOnAction(_ -> killSessions(true));
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
   * Used to add additional buttons, for example, to close the stage.
   *
   * @return the button box
   */
  public FxHBox getButtonBox() {
    return buttonBox;
  }


  /**
   * Returns whether killing a session is allowed.<br>
   * Override to hide the kill button.
   *
   * @return true if allowed (default)
   */
  protected boolean isKillSessionAllowed() {
    return true;
  }

  /**
   * Shows the security dialog for the {@link AdminExtension}.
   */
  protected void showSecurityDialog() {
    SecurityDialogFactory.getInstance().showDialog(getView(), AdminExtension.class);
  }

  /**
   * Creates the table configuration.
   *
   * @return the table configuration
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  protected TableConfiguration<SessionData> createTableConfiguration() {
    TableConfiguration<SessionData> config = FxFactory.getInstance().createTableConfiguration(SessionData.class, null);
    config.setBaseBundleName(getClass().getName());
    config.addColumn("userId", resources.getString("user-ID"));
    config.addColumn("userName", resources.getString("user"));
    config.addColumn("applicationName", resources.getString("application"));
    config.addColumn("applicationId", resources.getString("appl.-ID"));
    config.addColumn("clientVersion", resources.getString("version"));
    config.addColumn("locale", resources.getString("locale"));
    config.addColumn("timeZone", resources.getString("timezone"))
            .setCellType((TableCellType) new SessionsViewTimezoneCellType());
    config.addColumn("vmInfo", resources.getString("VM"));
    config.addColumn("osInfo", resources.getString("OS"));
    config.addColumn("hostInfo", resources.getString("hostname"));
    config.addColumn("clientHost", resources.getString("client"));
    config.addColumn("since", resources.getString("since")).setFilterable(false);
    config.addColumn("sessionNumber", resources.getString("session-no."));
    config.addColumn("sessionGroup", resources.getString("group-no."));
    config.addColumn("sessionName", resources.getString("name"));
    config.addColumn("cloned", resources.getString("cloned"));
    config.addColumn("options", resources.getString("options"));
    config.setFilterEnabled(true);
    return config;
  }


  /**
   * Retrieves all sessions and refreshes the table view.
   */
  private void refresh() {
    sessions = adminExt.getSessions();
    // the items are wrapped in transformation lists if filtering is enabled -> modify the root source
    FxUtilities.getInstance().getRootSource(sessionsNode.getItems()).setAll(sessions);
    sessionsNode.getSelectionModel().clearSelection();
  }

  /**
   * Kills or sends a logout request to all marked user sessions.
   *
   * @param kill true to kill, else send logout request
   */
  private void killSessions(boolean kill) {
    Set<SessionData> sessionData = new LinkedHashSet<>();
    // include all sessions of a group, even if not all are selected
    // (copied, since the selection is modified below)
    for (SessionData session : List.copyOf(sessionsNode.getSelectionModel().getSelectedItems())) {
      sessionData.add(session);
      if (session.getSessionGroup() != 0) {
        for (SessionData otherSession: sessions) {
          if (otherSession.getSessionGroup() == session.getSessionGroup() && otherSession.getUserId() == session.getUserId()) {
            sessionData.add(otherSession);
            // the row index refers to the view, which may be filtered and sorted (-1 if not visible)
            int row = sessionsNode.getItems().indexOf(otherSession);
            if (row >= 0) {
              sessionsNode.getSelectionModel().select(row, null);
            }
          }
        }
      }
    }

    if (!sessionData.isEmpty()) {
      String msg = kill ?
                     sessionData.size() > 1 ?
                     MessageFormat.format(resources.getString("really kill {0} sessions?"), sessionData.size()) :
                     MessageFormat.format(resources.getString("really kill session {0}?"), sessionData.iterator().next().getSessionNumber()) :
                     sessionData.size() > 1 ?
                       MessageFormat.format(resources.getString("log out {0} sessions?"), sessionData.size()) :
                       MessageFormat.format(resources.getString("log out session {0}?"), sessionData.iterator().next().getSessionNumber());
      Fx.yes(getView(), msg, false, () -> {
        for (SessionData session : sessionData) {
          if (kill) {
            adminExt.kill(session.getUserId(), session.getSessionGroup(),
                          session.getApplicationName(), session.getApplicationId());
          }
          else {
            adminExt.logout(session.getUserId(), session.getSessionGroup(),
                            session.getApplicationName(), session.getApplicationId());
          }
        }
        if (kill) {
          refresh();
        }
        else {
          // logout requires manual refresh since the request is executed by the remote tracker and may be completely ignored
          Fx.info(getView(), resources.getString("logout request submitted"));
        }
      });
    }
  }

}
