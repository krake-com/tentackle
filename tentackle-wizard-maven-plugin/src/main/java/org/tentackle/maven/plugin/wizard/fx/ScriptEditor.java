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
import org.tentackle.common.StringHelper;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.component.FxButton;
import org.tentackle.fx.component.FxChoiceBox;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.fx.component.Note;
import org.tentackle.maven.plugin.wizard.ScriptHarness;
import org.tentackle.maven.plugin.wizard.ScriptMojo;
import org.tentackle.session.Session;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Tooltip;
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
  private FxButton copyScriptButton;

  @FXML
  private FxButton runButton;

  @FXML
  private FxButton interruptButton;

  @FXML
  private FxButton closeButton;


  private ScriptMojo mojo;
  private Session session;
  private final AtomicReference<Thread> workerRef = new AtomicReference<>();

  /**
   * Sets up the editor.
   *
   * @param mojo the script mojo
   * @param session the backend session (made current on the worker thread)
   * @param harnesses the available harnesses
   * @param harness the harness to select by default, null if none
   */
  public void setup(ScriptMojo mojo, Session session, Collection<ScriptHarness> harnesses, ScriptHarness harness) {
    this.mojo = mojo;
    this.session = session;
    harnessField.getItems().setAll(harnesses);
    if (harnesses.size() == 1) {
      this.harness = harnesses.iterator().next();
    }
    else if (harness != null) {
      this.harness = harness;
    }
  }

  @FXML
  private void initialize() {
    scriptArea.setNavigateViaEnterEnabled(false);
    scriptArea.setStyle("-fx-font-family: 'monospaced';");
    closeButton.setGraphic(Fx.createGraphic("cancel"));
    copyScriptButton.setGraphic(Fx.createGraphic("script"));
    copyScriptButton.setOnAction(_ -> copy());
    copyScriptButton.setTooltip(new Tooltip("copy the generated script to the clipboard"));
    runButton.setGraphic(Fx.createGraphic("run"));
    runButton.setTooltip(new Tooltip("run the script fragment woven into the current harness"));
    interruptButton.setDisable(true);
    interruptButton.setGraphic(Fx.createGraphic("stop"));
    interruptButton.setTooltip(new Tooltip("interrupt the currently running script"));
    closeButton.setOnAction(_ -> close());
    runButton.setOnAction(_ -> runScript());
    interruptButton.setOnAction(_ -> {
      Thread current = workerRef.get();
      if (current != null) {
        current.interrupt();
      }
    });
  }

  private void runScript() {
    ScriptHarness harness = getSelectedHarness();
    if (harness != null) {
      String fragment = scriptArea.getText();
      runButton.setDisable(true);
      interruptButton.setDisable(false);
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
            runButton.setDisable(false);
            interruptButton.setDisable(true);
          });
        }
      }, "script-runner");
      workerRef.set(worker);
      worker.setDaemon(true);
      worker.start();
    }
  }

  private void copy() {
    ScriptHarness harness = getSelectedHarness();
    if (harness != null) {
      try {
        String code = mojo.weave(harness, scriptArea.getText());
        if (!StringHelper.isAllWhitespace(code)) {
          Fx.copyToClipboard(code);
          // show some feedback
          Note infoNote = Fx.create(Note.class);
          infoNote.setPosition(Note.Position.TOP);
          infoNote.setText("generated script copied to clipboard");
          infoNote.setFadeEffect(500, 2000, 1000);
          infoNote.show(copyScriptButton);
        }
      }
      catch (Exception e) {
        Fx.error(getView(), "failed to copy generated script", e);
      }
    }
  }

  private ScriptHarness getSelectedHarness() {
    if (harness == null) {
      Fx.warning(getView(), "no harness selected");
      return null;
    }
    else {
      return harness;
    }
  }

  private void close() {
    Fx.getStage(getView()).close();
  }

}
