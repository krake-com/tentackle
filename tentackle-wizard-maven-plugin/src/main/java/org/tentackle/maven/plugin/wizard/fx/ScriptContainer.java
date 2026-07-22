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

import org.tentackle.fx.Fx;
import org.tentackle.maven.plugin.wizard.ScriptHarness;
import org.tentackle.maven.plugin.wizard.ScriptMojo;
import org.tentackle.session.Session;

import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.Collection;

/**
 * Container dialog for the script mojo.<br>
 * Shows a single {@link ScriptEditor} view.
 */
public class ScriptContainer {

  private final ScriptMojo mojo;
  private final Session session;
  private final Collection<ScriptHarness> harnesses;

  /**
   * Creates the container.
   *
   * @param mojo the script mojo
   * @param session the backend session
   * @param harnesses the available harnesses
   */
  public ScriptContainer(ScriptMojo mojo, Session session, Collection<ScriptHarness> harnesses) {
    this.mojo = mojo;
    this.session = session;
    this.harnesses = harnesses;
  }

  /**
   * Shows the script editor dialog.
   *
   * @return the stage
   */
  public Stage show() {
    Stage stage = Fx.createStage(Modality.NONE, null);
    ScriptEditor editor = Fx.load(ScriptEditor.class);
    stage.setScene(Fx.createScene(editor.getView()));
    stage.setTitle("Script Runner");
    editor.setup(mojo, session, harnesses);
    editor.getContainer().updateView();
    Fx.show(stage);
    return stage;
  }

}
