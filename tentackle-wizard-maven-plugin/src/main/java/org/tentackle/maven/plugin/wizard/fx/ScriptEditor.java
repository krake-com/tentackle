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

import org.tentackle.bind.Bindable;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxChoiceBox;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.maven.plugin.wizard.ScriptHarness;
import org.tentackle.maven.plugin.wizard.ScriptMojo;
import org.tentackle.session.Session;

import javafx.application.Platform;
import javafx.fxml.FXML;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controller for the script editor.<br>
 * Lets the user pick a harness, edit a script fragment and run or interrupt it.
 */
@FxControllerService(resources = FxControllerService.RESOURCES_NONE)
public class ScriptEditor extends AbstractFxController {

  @Bindable
  private ScriptHarness harness;

  @FXML
  private FxChoiceBox<ScriptHarness> harnessField;

  @FXML
  private FxTextArea scriptArea;

  @FXML
  private FxButton close;

  @FXML
  private FxButton run;

  @FXML
  private FxButton interrupt;


  private ScriptMojo mojo;
  private Session session;
  private final AtomicReference<Thread> workerRef = new AtomicReference<>();

  /**
   * Sets up the editor.
   *
   * @param mojo the script mojo
   * @param session the backend session (made current on the worker thread)
   * @param harnesses the available harnesses
   */
  public void setup(ScriptMojo mojo, Session session, Collection<ScriptHarness> harnesses) {
    this.mojo = mojo;
    this.session = session;
    harnessField.getItems().setAll(harnesses);
    if (harnesses.size() == 1) {
      this.harness = harnesses.iterator().next();
    }
  }

  @FXML
  private void initialize() {
    scriptArea.setNavigateViaEnterEnabled(false);
    close.setGraphic(Fx.createGraphic("cancel"));
    run.setGraphic(Fx.createGraphic("run"));
    interrupt.setDisable(true);
    close.setOnAction(_ -> close());
    run.setOnAction(_ -> runScript());
    interrupt.setOnAction(_ -> {
      Thread current = workerRef.get();
      if (current != null) {
        current.interrupt();
      }
    });
  }

  private void runScript() {
    ScriptHarness harness = harnessField.getValue();
    if (harness == null) {
      Fx.error(getView(), "no harness selected");
      return;
    }
    String fragment = scriptArea.getText();
    run.setDisable(true);
    interrupt.setDisable(false);
    Thread worker = new Thread(() -> {
      session.makeCurrent();
      try {
        mojo.runFragment(harness, fragment);
        Platform.runLater(() -> Fx.info(getView(), "script '" + harness.getName() + "' finished"));
      }
      catch (Throwable t) {
        Platform.runLater(() -> Fx.error(getView(), "script '" + harness.getName() + "' failed", t));
      }
      finally {
        workerRef.set(null);
        Platform.runLater(() -> {
          run.setDisable(false);
          interrupt.setDisable(true);
        });
      }
    }, "script-runner");
    workerRef.set(worker);
    worker.setDaemon(true);
    worker.start();
  }

  private void close() {
    Fx.getStage(getView()).close();
  }

}
