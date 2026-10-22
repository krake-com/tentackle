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

package org.tentackle.fx.component.auto;

import org.tentackle.fx.component.AutoCompletionPopup;
import org.tentackle.log.Logger;
import org.tentackle.misc.SubString;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.scene.control.TextInputControl;
import java.util.List;
import java.util.function.Function;

/**
 * Auto-completion controller.<br>
 * Runs the suggestion generator in the background whenever the user changes the text,
 * but never more than one at a time. Input arriving while the generator is running is
 * processed as soon as it has finished.
 */
public class AutoCompletionController {

  private static final Logger LOGGER = Logger.get(AutoCompletionController.class);

  private final TextInputControl component;
  private final Function<String, List<List<SubString>>> suggestionGenerator;
  private final AutoCompletionPopup popup;
  private final Service<List<List<SubString>>> backgroundService;
  private final ChangeListener<String> textListener = this::textChanged;
  private final ChangeListener<Boolean> focusListener = this::focusChanged;
  private String lastInput;
  private String textInput;
  private boolean updatingItem;

  /**
   * Creates the controller.
   *
   * @param component the text editor component
   * @param suggestionGenerator the suggestions generator
   */
  public AutoCompletionController(TextInputControl component, Function<String, List<List<SubString>>> suggestionGenerator) {
    this.component = component;
    this.suggestionGenerator = suggestionGenerator;
    component.textProperty().addListener(textListener);
    component.focusedProperty().addListener(focusListener);
    popup = createAutoCompletionPopup();

    // run the suggestions generator in background and not more than one at a time.
    // The last input is just remembered, if the generator is running and executed when finished.
    // This allows us to operate at the speed of typing and avoid annoying timeouts.
    backgroundService = new Service<>() {
      @Override
      protected Task<List<List<SubString>>> createTask() {
        return new Task<>() {
          protected List<List<SubString>> call() {
            String input;
            synchronized (backgroundService) {
              input = lastInput;
              lastInput = null;
            }
            return AutoCompletionController.this.suggestionGenerator.apply(input);
          }
        };
      }
    };

    backgroundService.setOnSucceeded(event -> {
      @SuppressWarnings("unchecked")
      List<List<SubString>> suggestions = (List<List<SubString>>) event.getSource().getValue();
      if (!suggestions.isEmpty() &&
          // if only one suggestion: don't pop up if it matches the text component's contents
          (suggestions.size() != 1 || !suggestions.getFirst().getFirst().getString().equals(textInput))) {
        showPopup(suggestions);
      }
      else {
        hidePopup();
      }
      synchronized (backgroundService) {
        if (lastInput != null) {
          // some unprocessed input pending
          backgroundService.reset();
          backgroundService.start();
        }
      }
    });

    backgroundService.setOnFailed(event -> LOGGER.warning("suggestions could not be determined", event.getSource().getException()));
  }

  /**
   * Gets the text input component.
   *
   * @return the component
   */
  public TextInputControl getComponent() {
    return component;
  }

  /**
   * Gets the suggestions generator.
   *
   * @return the generator
   */
  public Function<String, List<List<SubString>>> getSuggestionGenerator() {
    return suggestionGenerator;
  }

  /**
   * Removes all listeners and hides the popup, if shown.<br>
   * Provided in case auto-completion is removed from a component.
   */
  public void close() {
    component.textProperty().removeListener(textListener);
    component.focusedProperty().removeListener(focusListener);
    hidePopup();
  }

  /**
   * Sets the selected item's string value into the text component.
   *
   * @param item the item's string
   */
  public void updateItem(String item) {
    updatingItem = true;
    component.setText(item);
    updatingItem = false;
  }


  /**
   * Creates the auto-completion popup.
   *
   * @return the popup
   */
  protected AutoCompletionPopup createAutoCompletionPopup() {
    return new AutoCompletionPopup(this);
  }

  /**
   * Shows or updates the popup.
   *
   * @param suggestions the suggestions
   */
  protected void showPopup(List<List<SubString>> suggestions) {
    popup.getSuggestions().setAll(suggestions);
    popup.showPopup();
  }

  /**
   * Hides the popup.
   */
  protected void hidePopup() {
    popup.hide();
  }


  private void focusChanged(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
    hidePopup();
  }

  private void textChanged(ObservableValue<? extends String> observable, String oldValue, String input) {
    if (!updatingItem && Platform.isFxApplicationThread()) {  // don't fire while updating or controller loading
      textInput = input;
      if (input != null && !input.isEmpty() && component.isEditable() && component.isFocused()) {
        synchronized (backgroundService) {
          lastInput = input;
          if (!backgroundService.isRunning()) {
            // run the suggestions in background
            backgroundService.reset();
            backgroundService.start();
          }
        }
        // else: just remember the last input, don't run the suggestions generator if job is still running
        return;
      }
      hidePopup();
    }
  }

}
