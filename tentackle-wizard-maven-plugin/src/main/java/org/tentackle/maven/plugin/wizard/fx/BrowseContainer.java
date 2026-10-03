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

import org.tentackle.fx.Fx;
import org.tentackle.fx.container.FxTabPane;
import org.tentackle.maven.plugin.wizard.BrowseMojo;
import org.tentackle.pdo.DomainContext;

import javafx.collections.ListChangeListener;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Container dialog for the browse mojo.<br>
 * Allows adding views and creates the tab pane dynamically as soon as there is more than one view.
 */
public class BrowseContainer {

  private final BrowseMojo mojo;
  private final DomainContext context;

  private Stage stage;
  private HBox hBox;
  private PdoBrowser initialBrowser;
  private FxTabPane tabPane;
  private int tabNumber;


  /**
   * Creates the container.
   *
   * @param mojo the browse mojo
   * @param context the domain context
   */
  public BrowseContainer(BrowseMojo mojo, DomainContext context) {
    this.mojo = mojo;
    this.context = context;
  }

  /**
   * Gets the browse mojo.
   *
   * @return the mojo
   */
  public BrowseMojo getMojo() {
    return mojo;
  }

  /**
   * Gets the domain context.
   *
   * @return the domain context
   */
  public DomainContext getContext() {
    return context;
  }

  /**
   * Shows the initial browser dialog.
   *
   * @return the stage
   * @throws MojoExecutionException if the browser view could not be created
   */
  public Stage show() throws MojoExecutionException {
    stage = Fx.createStage(Modality.NONE, null);
    hBox = Fx.create(HBox.class);
    initialBrowser = Fx.load(PdoBrowser.class);
    hBox.getChildren().add(initialBrowser.getView());
    stage.setScene(Fx.createScene(hBox));
    stage.setTitle("PDO Browser");
    initialBrowser.setContext(this);
    initialBrowser.getContainer().updateView();
    Fx.show(stage);
    return stage;
  }

  /**
   * Creates a new browser view and adds it to the tab pane.<br>
   * If the tab pane is not created yet, it is created.
   *
   * @return the new browser view
   * @throws MojoExecutionException if the browser view could not be created
   */
  public PdoBrowser addBrowserView() throws MojoExecutionException {
    if (tabPane == null) {
      tabPane = Fx.create(TabPane.class);
      hBox.getChildren().remove(initialBrowser.getView());
      Tab tab = new Tab("1", initialBrowser.getView());
      tabNumber = 1;
      tab.setClosable(false);
      initialBrowser.setContainerTab(tab);
      tabPane.getTabs().add(tab);
      tabPane.getTabs().addListener((ListChangeListener<? super Tab>) c -> {
        while (c.next()) {
          if (c.wasRemoved()) {
            c.getRemoved().forEach(_ -> tabRemoved());
          }
        }
      });
      stage.setScene(Fx.createScene(tabPane));
    }
    PdoBrowser browser = Fx.load(PdoBrowser.class);
    Tab tab = new Tab(Integer.toString(++tabNumber), browser.getView());
    tabPane.getTabs().add(tab);
    browser.setContext(this);
    browser.setContainerTab(tab);
    tabPane.getSelectionModel().select(tabPane.getTabs().size() - 1);
    browser.getContainer().updateView();
    return browser;
  }

  /**
   * Invoked after a tab was closed.<br>
   * If only the initial tab is left, the tab pane is removed and the initial browser view is shown without tabs.
   */
  private void tabRemoved() {
    if (tabPane != null && tabPane.getTabs().size() <= 1) {
      hBox = Fx.create(HBox.class);
      initialBrowser.setContainerTab(null);
      hBox.getChildren().add(initialBrowser.getView());
      stage.setScene(Fx.createScene(hBox));
      tabPane = null;
    }
  }

}
