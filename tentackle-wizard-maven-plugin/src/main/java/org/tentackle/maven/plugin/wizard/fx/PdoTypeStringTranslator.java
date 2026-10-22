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

import org.tentackle.fx.FxTextComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.component.FxComboBox;
import org.tentackle.fx.translate.ValueStringTranslator;
import org.tentackle.maven.plugin.wizard.PdoProfile;
import org.tentackle.pdo.PdoFactory;
import org.tentackle.pdo.PersistentDomainObject;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Callback;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Translator for PdoType.<br>
 * Fills the combobox with the PDO types, filtered by profile and root entities, sorted by their camel-case
 * letters, and renders the uppercase letters in bold. The camel-case keyboard navigation itself is enabled
 * via {@code PrefixSelectionFeature.CAMEL_CASE}.
 */
@ValueTranslatorService(modelClass = PdoType.class, viewClass = String.class)
public class PdoTypeStringTranslator extends ValueStringTranslator<PdoType> {

  /** Component property key: Boolean, true to show only root entities. */
  public static final String ROOT_ONLY = "rootOnly";

  /** Component property key: the {@code PdoProfile} to filter by package, null for all. */
  public static final String PROFILE = "profile";


  private Map<String, PdoType> pdoTypeMap;

  /**
   * Creates a translator.
   *
   * @param component the text component
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public PdoTypeStringTranslator(FxTextComponent component) {
    super(component);

    if (component instanceof FxComboBox comboBox) {

      configureComboBox(comboBox);

      Font font = Font.getDefault();
      Font boldFont = Font.font(font.getFamily(), FontWeight.BOLD, font.getSize());

      Callback<ListView<PdoType>, ListCell<PdoType>> cellFactory = new Callback<>() {
        @Override
        public ListCell<PdoType> call(ListView<PdoType> l) {
          return new ListCell<>() {

            @Override
            protected void updateItem(PdoType item, boolean empty) {
              super.updateItem(item, empty);
              if (item == null || empty) {
                setGraphic(null);
              }
              else {
                // render uppercase characters in bold
                TextFlow textFlow = new TextFlow();
                String name = item.getName();
                StringBuilder buf = new StringBuilder();
                for (int i=0; i < name.length(); i++) {
                  char c = name.charAt(i);
                  if (Character.isUpperCase(c)) {
                    if (!buf.isEmpty()) {
                      addText(textFlow, buf.toString(), font);
                      buf.setLength(0);
                    }
                    addText(textFlow, String.valueOf(c), boldFont);
                  }
                  else {
                    buf.append(c);
                  }
                }
                if (!buf.isEmpty()) {
                  addText(textFlow, buf.toString(), font);
                }
                Group group = new Group(textFlow);
                setGraphic(group);
              }
            }
          };
        }
      };
      comboBox.setCellFactory(cellFactory);
      comboBox.setButtonCell(cellFactory.call(null));
    }
  }

  @Override
  public Function<PdoType, String> toViewFunction() {
    return v -> v == null ? null : v.getName();
  }

  @Override
  public Function<String, PdoType> toModelFunction() {
    return s -> s == null ? null : getPdoTypeMap().get(s);
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void bindingPropertiesUpdated() {
    if (getComponent() instanceof FxComboBox comboBox) {
      pdoTypeMap = null;
      configureComboBox(comboBox);
    }
  }

  private void configureComboBox(FxComboBox<PdoType> comboBox) {
    comboBox.getSelectionModel().clearSelection();
    // sort by camel-case letters, then by name
    List<PdoType> pdoTypes = new ArrayList<>(getPdoTypeMap().values());
    pdoTypes.sort(Comparator.comparing(PdoType::getCamelCaseLetters).thenComparing(PdoType::getName));
    comboBox.getItems().setAll(pdoTypes);
  }

  private Map<String, PdoType> getPdoTypeMap() {
    if (pdoTypeMap == null) {
      boolean rootOnly = Boolean.TRUE.equals(((Node) getComponent()).getProperties().get(ROOT_ONLY));
      PdoProfile profile = (PdoProfile) ((Node) getComponent()).getProperties().get(PROFILE);
      Map<String, PdoType> pdoTypeMap = new HashMap<>();
      for (String pdoName: PdoFactory.getInstance().getPersistenceMapper().getMap().keySet()) {
        PersistentDomainObject<?> pdo = PdoFactory.getInstance().create(pdoName);
        Class<? extends PersistentDomainObject<?>> effectiveClass = pdo.getEffectiveClass();
        if ((!rootOnly || pdo.isRootEntity()) && !pdo.getPersistenceDelegate().isEmbedded() &&
            (profile == null || profile.getPdoPackage().equals(effectiveClass.getPackageName()))) {
          PdoType pdoType = new PdoType(effectiveClass.getSimpleName(), effectiveClass);
          pdoTypeMap.put(pdoType.getName(), pdoType);
        }
      }
      this.pdoTypeMap = pdoTypeMap;
    }
    return pdoTypeMap;
  }

  private void addText(TextFlow textFlow, String str, Font font) {
    Text text = new Text(str);
    text.setFont(font);
    textFlow.getChildren().add(text);
  }

}
