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
import org.tentackle.common.LocaleProvider;
import org.tentackle.fx.AbstractFxController;
import org.tentackle.fx.Fx;
import org.tentackle.fx.FxControllerService;
import org.tentackle.fx.FxTextComponent;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;

/**
 * Editor for the I18NText translations.
 */
@FxControllerService(binding = FxControllerService.BINDING.NO)
public class I18NEditor extends AbstractFxController {

  /**
   * Shows the editor dialog.
   *
   * @param i18NText the I18N text object
   * @param i18NTextStringTranslator the translator for the current component
   */
  public static void showDialog(I18NText i18NText, I18NTextStringTranslator i18NTextStringTranslator) {
    I18NEditor i18NEditor = Fx.load(I18NEditor.class);
    i18NEditor.setI18NText(i18NText, i18NTextStringTranslator);
    Stage stage = Fx.createStage(Modality.WINDOW_MODAL, i18NEditor.component);
    Scene scene = Fx.createScene(i18NEditor.getView());
    stage.setTitle(i18NEditor.resources.getString("translations"));
    stage.setScene(scene);
    Fx.show(stage);
  }



  @FXML private ResourceBundle resources;

  @FXML private Label headerLabel;
  @FXML private VBox translationsPane;
  @FXML private Button applyButton;
  @FXML private Button discardButton;

  private I18NText i18NText;
  private I18NTextStringTranslator i18NTextStringTranslator;
  private Control component;
  private Map<Locale, TextInputControl> editorMap;


  /**
   * Creates the editor.
   */
  public I18NEditor() {
    // see -Xlint:missing-explicit-ctor since Java 16
  }

  @FXML
  private void initialize() {
    applyButton.setOnAction(event -> apply());
    discardButton.setOnAction(event -> close());
  }

  /**
   * Sets up the editor.
   *
   * @param i18NText the I18N text object
   * @param i18NTextStringTranslator the translator for the current component
   */
  private void setI18NText(I18NText i18NText, I18NTextStringTranslator i18NTextStringTranslator) {
    this.i18NText = Objects.requireNonNull(i18NText);
    this.i18NTextStringTranslator = Objects.requireNonNull(i18NTextStringTranslator);

    component = (Control) i18NTextStringTranslator.getComponent();
    double width = component.getWidth();
    double height = component.getHeight();
    if (component instanceof FxTextComponent textComponent) {
      headerLabel.setText(textComponent.getBinding().getMember().getMemberName());
    }

    Locale fallbackLocale = LocaleProvider.getInstance().getFallbackLocale();
    Set<Locale> locales = new LinkedHashSet<>();
    for (Locale effectiveLocale : LocaleProvider.getInstance().getEffectiveLocales()) {
      locales.add(Locale.of(effectiveLocale.getLanguage()));    // flatten to language only (see I18NText)
    }

    editorMap = new LinkedHashMap<>();    // editors ordered by effective locales from LocaleProvider to provide consistent update order
    Map<String, VBox> vBoxMap = new TreeMap<>();
    for (Locale locale : locales) {
      TextInputControl editor = component instanceof TextArea ? Fx.create(TextArea.class) : Fx.create(TextField.class);
      editor.setPrefWidth(width);     // same dimensions as original component
      editor.setPrefHeight(height);
      editorMap.put(locale, editor);
      VBox vBox = Fx.create(VBox.class);
      vBox.setSpacing(5.0);
      vBox.setPadding(new Insets(5.0));
      Label languageLabel = Fx.create(Label.class);
      StringBuilder labelText = new StringBuilder();
      labelText.append(locale.getLanguage()).append(" = ").append(locale.getDisplayLanguage());
      boolean isFallback = fallbackLocale.getLanguage().equals(locale.getLanguage());
      boolean isCurrent = LocaleProvider.getInstance().getEffectiveLocale().getLanguage().equals(locale.getLanguage());
      if (isFallback || isCurrent) {
        labelText.append(" (");
        if (isFallback) {
          labelText.append(resources.getString("default"));
        }
        if (isCurrent) {
          if (isFallback) {
            labelText.append(", ");
          }
          labelText.append(resources.getString("current"));
        }
        labelText.append(")");
      }
      languageLabel.setText(labelText.toString());
      vBox.getChildren().addAll(languageLabel, editor);
      vBoxMap.put(locale.getLanguage(), vBox);
    }
    // display is sorted by language name with fallback language always on top
    translationsPane.getChildren().add(vBoxMap.remove(fallbackLocale.getLanguage()));
    translationsPane.getChildren().addAll(vBoxMap.values());

    updateView();
  }

  private void updateView() {
    for (Map.Entry<Locale, TextInputControl> entry : editorMap.entrySet()) {
      entry.getValue().setText(i18NText.getTranslations().get(entry.getKey()));
    }
  }

  private void apply() {
    Map<Locale, String> translations = i18NText.getTranslations();
    for (Map.Entry<Locale, TextInputControl> entry : editorMap.entrySet()) {
      Locale locale = entry.getKey();
      String newText = entry.getValue().getText();
      String oldText = translations.get(locale);
      if (!Objects.equals(oldText, newText)) {
        i18NText = i18NText.with(locale, newText);
      }
    }
    i18NTextStringTranslator.updateI18NText(i18NText);
    close();
  }

  private void close() {
    Fx.getStage(getView()).close();
  }

}
