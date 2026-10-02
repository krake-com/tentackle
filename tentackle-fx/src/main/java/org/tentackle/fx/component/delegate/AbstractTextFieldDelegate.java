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

package org.tentackle.fx.component.delegate;

import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.FxTextComponentDelegate;
import org.tentackle.fx.FxUtilities;
import org.tentackle.fx.component.auto.AutoCompletionController;
import org.tentackle.misc.SubString;

import javafx.application.Platform;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.TextInputControl;
import java.util.List;
import java.util.function.Function;

/**
 * Base delegate for components derived from {@link TextInputControl}.<br>
 * Installs the delegate as the filter of the component's {@link TextFormatter} and provides the auto-completion.
 *
 * @author harald
 * @param <T> the text component type
 */
public abstract class AbstractTextFieldDelegate<T extends TextInputControl & FxTextComponent> extends FxTextComponentDelegate {

  private final T component;                                      // the component
  private AutoCompletionController autoCompletionController;      // != null if auto-completion enabled

  /**
   * Creates the delegate.
   *
   * @param component the component
   */
  public AbstractTextFieldDelegate(T component) {
    this.component = component;
    component.setTextFormatter(new TextFormatter<>(this));
  }

  @Override
  public T getComponent() {
    return component;
  }

  @Override
  public void mapErrorOffsetToCaretPosition() {
    Integer errorOffset = getErrorOffset();
    if (errorOffset != null) {
      Platform.runLater(() -> {
        component.deselect();
        component.positionCaret(errorOffset);
      });
    }
  }

  @Override
  public void setAutoCompletion(Function<String, List<List<SubString>>> autoCompletion) {
    super.setAutoCompletion(autoCompletion);
    if (autoCompletion != null) {
      if (autoCompletionController == null) {
        autoCompletionController = FxUtilities.getInstance().createAutoCompletion(component, autoCompletion);
      }
    }
    else if (autoCompletionController != null) {
      autoCompletionController.close();
      autoCompletionController = null;
    }
  }

}
