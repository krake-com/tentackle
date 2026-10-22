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

package org.tentackle.fx.translate;

import org.tentackle.common.I18NText;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxFxBundle;
import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.ValueTranslatorService;

import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyEvent;
import java.util.Objects;
import java.util.function.Function;

/**
 * {@link I18NText} translator.<br>
 * The component shows the text for the current locale.
 * {@code F2} or the context menu opens the {@link I18NEditor} to edit all translations.
 *
 * @author harald
 */
@ValueTranslatorService(modelClass = I18NText.class, viewClass = String.class)
public class I18NTextStringTranslator extends ValueStringTranslator<I18NText> {

  private static final String STYLE_I18N = "tt-i18n";


  private I18NText savedModelValue;      // saved model value to detect changes in translations that are not displayed

  /**
   * Creates a translator.<br>
   * Adds the CSS style class {@code tt-i18n}, the {@code F2} key handler and the context menu item.
   *
   * @param component the text component
   */
  public I18NTextStringTranslator(FxTextComponent component) {
    super(component);
    Control control = (Control) component;
    if (!control.getStyleClass().contains(STYLE_I18N)) {
      control.getStyleClass().add(STYLE_I18N);

      // register function keys:
      // F2 = edit/view translations
      control.addEventFilter(KeyEvent.ANY, event -> {
        if (!event.isAltDown() && !event.isControlDown() && !event.isMetaDown() &&
            !event.isShiftDown() && !event.isShortcutDown()) {

          if (event.getCode() == KeyCode.F2) {
            event.consume();
            if (event.getEventType() == KeyEvent.KEY_PRESSED) {
              Platform.runLater(this::edit);
            }
          }
        }
      });

      ContextMenu menu;
      if (control.getContextMenu() == null) {
        menu = Fx.create(ContextMenu.class);
        // this replaces the default context menu in TextFields, but that's ok.
        // otherwise we would have to tweak TextFieldBehavior which is still private API
        control.setContextMenu(menu);
      }
      else {
        // append to existing menu
        MenuItem separatorItem = Fx.create(SeparatorMenuItem.class);
        menu = control.getContextMenu();
        menu.getItems().add(separatorItem);
      }
      MenuItem editItem = Fx.create(MenuItem.class);
      editItem.setText(FxFxBundle.getString("EDIT_I18N"));
      editItem.setAccelerator(new KeyCodeCombination(KeyCode.F2));
      editItem.setOnAction(event -> edit());
      menu.getItems().add(editItem);
    }
  }

  @Override
  public boolean isMappingIncomplete() {
    return true;
  }

  @Override
  public void saveModelValue(I18NText modelValue) {
    savedModelValue = modelValue;
  }

  @Override
  public boolean isModelModified() {
    // vacant I18NText is mapped to null for comparing, since no translations will be treated as null in updateI18NText below
    I18NText modelValue = null;
    if (getComponent().getBinding().getModelValue() instanceof I18NText i18NText) {
      modelValue = i18NText.isVacant() ? null : i18NText;
    }
    I18NText savedValue = savedModelValue;
    if (savedValue != null && savedValue.isVacant()) {
      savedValue = null;
    }
    return !Objects.equals(savedValue, modelValue);
  }

  @Override
  public Function<I18NText, String> toViewFunction() {
    return v -> v == null ? null : v.get();   // get text for the current locale
  }

  @Override
  public Function<String, I18NText> toModelFunction() {
    return s -> {
      Object modelValue = getComponent().getBinding().getModelValue();
      if (modelValue instanceof I18NText i18NText) {
        return Objects.equals(i18NText.get(), s) ?
               i18NText :             // no "visual" change: avoid copying the default/fallback-locale to the current locale
               i18NText.with(s);      // add, replace or remove the translation for the current locale
      }
      return s == null ? null : new I18NText(s);
    };
  }


  /**
   * Updates the model with a new I18NText object.<br>
   * Invoked from {@link I18NEditor}.
   *
   * @param i18NText the I18N text object
   */
  public void updateI18NText(I18NText i18NText) {
    getComponent().getBinding().setModelValue(i18NText.isVacant() ? null : i18NText);
  }

  /**
   * Opens the editor for all translations.
   */
  protected void edit() {
    I18NEditor.showDialog(getComponent().getBinding().getModelValue() instanceof I18NText i18NText ?
                          i18NText : new I18NText(),
                          this);
  }

}
