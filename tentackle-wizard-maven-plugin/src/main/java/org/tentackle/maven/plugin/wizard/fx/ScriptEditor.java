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
import org.tentackle.fx.component.FxLabel;
import org.tentackle.fx.component.FxTextArea;
import org.tentackle.fx.component.Note;
import org.tentackle.maven.plugin.wizard.ScriptFragmentFile;
import org.tentackle.maven.plugin.wizard.ScriptHarness;
import org.tentackle.maven.plugin.wizard.ScriptMojo;
import org.tentackle.session.Session;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Controller for the script editor.<br>
 * Lets the user pick a harness, edit a script fragment and run or interrupt it.
 * The fragment can be loaded from and saved to the filesystem. The file used last
 * is remembered and reloaded the next time the editor comes up.
 */
@FxControllerService(resources = FxControllerService.RESOURCES_NONE)
public class ScriptEditor extends AbstractFxController {

  @Bindable
  private ScriptHarness harness;

  @FXML
  private FxChoiceBox<ScriptHarness> harnessField;

  @FXML
  private FxLabel fileLabel;

  @FXML
  private FxTextArea scriptArea;

  @FXML
  private FxButton loadButton;

  @FXML
  private FxButton saveButton;

  @FXML
  private FxButton saveAsButton;

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
  private ScriptFragmentFile fragmentFile;
  private boolean modified;

  /**
   * Sets up the editor.
   *
   * @param mojo the script mojo
   * @param session the backend session (made current on the worker thread)
   * @param harnesses the available harnesses
   * @param harness the harness to select by default, null if none; ignored if there is only one harness
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
    fragmentFile = new ScriptFragmentFile(mojo.getPreferences(), mojo.getBaseDir());
    File lastFile = fragmentFile.getLastFile();
    if (lastFile != null) {
      // pick up where the last session left off
      readFragment(lastFile);
    }
    else {
      updateFileLabel();
    }
    getStage().setOnCloseRequest(event -> {
      if (modified || workerRef.get() != null) {
        event.consume();   // don't close before the user decided
        requestClose();
      }
    });
  }

  @FXML
  private void initialize() {
    scriptArea.setNavigateViaEnterEnabled(false);
    scriptArea.setStyle("-fx-font-family: 'monospaced';");
    scriptArea.textProperty().addListener((_, _, _) -> setModified(true));
    closeButton.setGraphic(Fx.createGraphic("cancel"));
    loadButton.setGraphic(Fx.createGraphic("file"));
    loadButton.setOnAction(_ -> load());
    loadButton.setTooltip(new Tooltip("load the script fragment from a file"));
    saveButton.setGraphic(Fx.createGraphic("save"));
    saveButton.setOnAction(_ -> save());
    saveButton.setTooltip(new Tooltip("save the script fragment to its file"));
    saveAsButton.setGraphic(Fx.createGraphic("save"));
    saveAsButton.setOnAction(_ -> saveAs());
    saveAsButton.setTooltip(new Tooltip("save the script fragment to another file"));
    copyScriptButton.setGraphic(Fx.createGraphic("script"));
    copyScriptButton.setOnAction(_ -> copy());
    copyScriptButton.setTooltip(new Tooltip("copy the generated script to the clipboard"));
    runButton.setGraphic(Fx.createGraphic("run"));
    runButton.setTooltip(new Tooltip("run the script fragment woven into the selected harness"));
    interruptButton.setDisable(true);
    interruptButton.setGraphic(Fx.createGraphic("stop"));
    interruptButton.setTooltip(new Tooltip("interrupt the currently running script"));
    closeButton.setOnAction(_ -> requestClose());
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
          showNote("generated script copied to clipboard", copyScriptButton);
        }
      }
      catch (Exception e) {
        Fx.error(getView(), "failed to copy generated script", e);
      }
    }
  }

  /**
   * Loads a fragment from a file selected by the user.<br>
   * Unsaved changes must be confirmed first.
   */
  private void load() {
    confirmDiscard(() -> {
      FileChooser fileChooser = createFileChooser("Load Script Fragment");
      File file = fileChooser.showOpenDialog(getStage());
      if (file != null) {
        readFragment(file);
      }
    });
  }

  /**
   * Saves the fragment to its file.<br>
   * If there is no file yet, the user is asked for one.
   */
  private void save() {
    File file = fragmentFile.getFile();
    if (file == null) {
      saveAs();
    }
    else {
      writeFragment(file);
    }
  }

  /**
   * Saves the fragment to a file selected by the user.
   */
  private void saveAs() {
    FileChooser fileChooser = createFileChooser("Save Script Fragment");
    File file = fileChooser.showSaveDialog(getStage());
    if (file != null) {
      writeFragment(file);
    }
  }

  private FileChooser createFileChooser(String title) {
    // no extension filters: fragment filenames are arbitrary
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle(title);
    fileChooser.setInitialDirectory(fragmentFile.getDefaultDirectory());
    File file = fragmentFile.getFile();
    if (file != null) {
      fileChooser.setInitialFileName(file.getName());
    }
    return fileChooser;
  }

  private void readFragment(File file) {
    try {
      scriptArea.setText(fragmentFile.load(file));
      setModified(false);
    }
    catch (IOException e) {
      Fx.error(getView(), "cannot read the script fragment from " + file, e);
    }
  }

  private void writeFragment(File file) {
    try {
      fragmentFile.save(file, scriptArea.getText());
      setModified(false);
      showNote("fragment saved to " + file.getName(), saveButton);
    }
    catch (IOException e) {
      Fx.error(getView(), "cannot save the script fragment to " + file, e);
    }
  }

  private void setModified(boolean modified) {
    this.modified = modified;
    updateFileLabel();
  }

  private void updateFileLabel() {
    File file = fragmentFile == null ? null : fragmentFile.getFile();
    if (file == null) {
      fileLabel.setText(modified ? "*" : "");
      fileLabel.setTooltip(null);
    }
    else {
      fileLabel.setText(modified ? file.getName() + " *" : file.getName());
      fileLabel.setTooltip(new Tooltip(file.getAbsolutePath()));
    }
  }

  /**
   * Asks the user whether unsaved changes may be discarded.<br>
   * The question dialog is asynchronous, hence the action is invoked from within the answer's callback.
   *
   * @param action the action to run if there are no unsaved changes or the user agreed to discard them
   */
  private void confirmDiscard(Runnable action) {
    if (modified) {
      Fx.yes(getView(), "discard unsaved changes?", false, action);
    }
    else {
      action.run();
    }
  }

  private void showNote(String text, Node owner) {
    Note infoNote = Fx.create(Note.class);
    infoNote.setPosition(Note.Position.TOP);
    infoNote.setText(text);
    infoNote.setFadeEffect(500, 2000, 1000);
    infoNote.show(owner);
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

  /**
   * Closes the editor.<br>
   * Unsaved changes must be confirmed first.
   */
  private void requestClose() {
    if (workerRef.get() != null) {
      Fx.yes(getView(), "a script is still running: interrupt it and close?", false, () -> confirmDiscard(this::close));
    }
    else {
      confirmDiscard(this::close);
    }
  }

  private void close() {
    Thread worker = workerRef.get();
    if (worker != null) {
      worker.interrupt();
    }
    getStage().close();   // does not fire onCloseRequest again
  }

}
