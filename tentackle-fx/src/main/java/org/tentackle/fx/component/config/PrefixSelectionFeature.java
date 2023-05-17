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

package org.tentackle.fx.component.config;

import org.tentackle.common.StringHelper;
import org.tentackle.fx.FxUtilities;

import javafx.event.EventHandler;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.ListView;
import javafx.scene.control.Skin;
import javafx.scene.control.skin.ComboBoxListViewSkin;
import javafx.scene.input.KeyEvent;
import javafx.util.StringConverter;
import java.util.Collection;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Select items according to a prefix.<br>
 * Useful for ComboBox and ChoiceBox but can be applied to any control providing a list of items
 * that can be selected.<br>
 * The prefix selection is case-sensitive, if the first typed character is uppercase.
 * <p>
 * Example for camelcase navigation:
 * <pre>
 * &#64;ValueTranslatorService(modelClass = MyEntity.class, viewClass = String.class)
 * public class PdoCatalogStringTranslator extends PdoStringTranslator&lt;MyEntity&gt; {
 *
 *   ...
 *
 *   &#64;Override
 *   protected PdoComponentAddon&lt;MyEntity&gt; createPdoAddon() {
 *     return new PdoComponentAddon&lt;&gt;(getComponent(), this::getPdo, this::setPdo) {
 *
 *       &#64;Override
 *       public Collection&lt;MyEntity&gt; getAllPdos(MyEntity proxy) {
 *         if (sortedEntities == null) {
 *           if (getComponent() instanceof FxComboBox&lt;?&gt; fxComboBox) {
 *             Collection&lt;MyEntity&gt; myEntities = super.getAllPdos(proxy);
 *             TreeMap&lt;String, MyEntity&gt; camelCaseMap = new TreeMap&lt;&gt;();
 *             for (MyEntity myEntity : myEntities) {
 *               camelCaseMap.put(StringHelper.filterUpperCase(myEntity.getName()), myEntity);
 *             }
 *             sortedEntities = camelCaseMap.values();
 *             fxComboBox.getProperties().put(PrefixSelectionFeature.ENABLED, PrefixSelectionFeature.CAMEL_CASE);
 *           }
 *           else {
 *             sortedEntities = List.of();
 *           }
 *         }
 *         return sortedEntities;
 *       }
 *     };
 *   }
 * </pre>
 *
 * @author harald
 * @param <T> the control type
 */
@SuppressWarnings("rawtypes")
public class PrefixSelectionFeature<T extends Control> {

  /**
   * To disable this feature, invoke:<br>
   * <pre>
   *  control.getProperties().remove(PrefixSelectionFeature.ENABLED);
   * </pre>
   */
  public static final String ENABLED = "prefixSelectionEnabled";

  /**
   * To enable camel case preselection, invoke:<br>
   * <pre>
   *   control.getProperties().put(PrefixSelectionFeature.ENABLED, PrefixSelectionFeature.CAMEL_CASE);
   * </pre>
   */
  public static final String CAMEL_CASE = "CamelCase";


  protected final T control;
  protected final BooleanSupplier asYouTypeCondition;
  protected final Supplier<Collection> itemProvider;
  protected final Supplier<StringConverter> itemConverter;
  protected final Consumer<Integer> selector;

  protected final StringBuilder prefixBuf;    // collected prefix string
  private long lastPressMillis;               // epochal time of last keystroke
  private Integer index;                      // where to start the search, null if n/a
  private boolean isDropDownVisible;          // true if dropdown is visible

  /**
   * Creates a prefix selection feature.
   *
   * @param control the control to add this feature to
   * @param asYouTypeCondition the condition to activate preselection by keystrokes
   * @param itemProvider the items that can be selected
   * @param itemConverter the item to string converter
   * @param selector the selector to select an item by its index
   */
  public PrefixSelectionFeature(T control,
                                BooleanSupplier asYouTypeCondition,
                                Supplier<Collection> itemProvider,
                                Supplier<StringConverter> itemConverter,
                                Consumer<Integer> selector) {

    this.control = control;
    this.asYouTypeCondition = asYouTypeCondition;
    this.itemProvider = itemProvider;
    this.itemConverter = itemConverter;
    this.selector = selector;

    prefixBuf = new StringBuilder();
  }

  /**
   * Configures the control to support this feature.
   */
  public void configure() {
    control.addEventHandler(KeyEvent.KEY_TYPED, createHandler());
    if (control instanceof ComboBoxBase) {
      ((ComboBoxBase) control).showingProperty().addListener((obs, wasShowing, isShowing) -> {
        isDropDownVisible = isShowing;
        if (isDropDownVisible && isEnabled()) {
          boolean clearIndex = false;
          if (index == null && control instanceof ComboBox && ((ComboBox) control).isEditable()) {
            String value = ((ComboBox) control).getEditor().getText();
            if (value != null) {
              prefixBuf.append(value);
              select();
              clearIndex = true;
            }
          }
          if (index != null) {
            scrollToIndexInDropDown();
            if (clearIndex) {
              index = null;
              prefixBuf.setLength(0);
            }
          }
        }
      });
    }
  }

  /**
   * Scrolls to the selected index in the dropdown.
   */
  protected void scrollToIndexInDropDown() {
    if (index != null) {
      Skin<?> skin = control.getSkin();
      if (skin instanceof ComboBoxListViewSkin) {
        ((ListView<?>) ((ComboBoxListViewSkin<?>) skin).getPopupContent()).scrollTo(index);
      }
    }
  }

  /**
   * Creates the handler to catch the key events.
   *
   * @return the handler
   */
  protected EventHandler<KeyEvent> createHandler() {
    return event -> {
      if (isSelectionByKeyEnabled()) {
        String chr = event.getCharacter();
        if (chr != null) {    // for sure... can it be null at all?
           long now = System.currentTimeMillis();
           if (now > lastPressMillis + FxUtilities.getInstance().getPrefixSelectionTimeout()) {
             // clear after timeout
             prefixBuf.setLength(0);
             index = null;
           }
           lastPressMillis = now;
           prefixBuf.append(chr);
           select();
        }
      }
    };
  }

  /**
   * Returns whether this feature is enabled for the control.
   *
   * @return true if enabled
   */
  protected boolean isEnabled() {
    return control.getProperties().containsKey(ENABLED);
  }

  /**
   * Returns whether preselection via camelcase is enabled.
   *
   * @return true if camelcase
   */
  protected boolean isCamelCaseEnabled() {
    return CAMEL_CASE.equals(control.getProperties().get(ENABLED));
  }

  /**
   * Returns whether selection by key is enabled.
   *
   * @return true if enabled
   */
  protected boolean isSelectionByKeyEnabled() {
    return asYouTypeCondition.getAsBoolean() && isEnabled();
  }

  /**
   * Select an item according to the current prefix.
   */
  protected void select() {
    String prefix = getPrefix();
    int i = 0;
    for (Object item: itemProvider.get()) {
      if ((index == null || i >= index) && isItemMatching(prefix, item)) {
        index = i;
        selector.accept(index);
        if (isDropDownVisible) {
          scrollToIndexInDropDown();
        }
        break;
      }
      i++;
    }
  }

  /**
   * Gets the prefix string to be used for {@link #isItemMatching}.
   *
   * @return the prefix string
   */
  protected String getPrefix() {
    return prefixBuf.toString();
  }

  /**
   * Returns whether item is matching prefix string.
   *
   * @param prefix the prefix string
   * @param item the item
   * @return true if matching
   */
  @SuppressWarnings("unchecked")
  protected boolean isItemMatching(String prefix, Object item) {
    boolean matches = false;
    if (!StringHelper.isAllWhitespace(prefix)) {
      int prefixLength = prefix.length();
      StringConverter converter = itemConverter == null ? null : itemConverter.get();
      String itemText = converter == null ? item.toString() : converter.toString(item);
      int itemTextLength = itemText.length();
      if (itemTextLength >= prefixLength) {
        boolean camelCase = isCamelCaseEnabled();
        if (Character.isUpperCase(prefix.charAt(0))) {  // case sensitive
          if (camelCase) {
            int prefixNdx = 0;
            for (int itemNdx=0; itemNdx < itemTextLength && prefixNdx < prefixLength; itemNdx++) {
              char itemChar = itemText.charAt(itemNdx);
              char prefixChar = prefix.charAt(prefixNdx);
              if (Character.isUpperCase(itemChar)) {   // next prefix char must match
                if (prefixChar == itemChar) {
                  prefixNdx++;
                }
                else {
                  break;
                }
              }
              else if (Character.isLowerCase(prefixChar)) {  // next item char must match
                if (prefixChar == itemChar) {
                  prefixNdx++;
                }
                else {
                  break;
                }
              }
              // else: just move on in itemText
            }
            matches = prefixNdx == prefixLength;
          }
          else {
            matches = itemText.startsWith(prefix);
          }
        }
        else {  // case insensitive
          if (camelCase) {
            // find only uppercase letters in itemText
            int prefixNdx = 0;
            for (int itemNdx=0; itemNdx < itemTextLength && prefixNdx < prefixLength; itemNdx++) {
              char itemChar = itemText.charAt(itemNdx);
              if (Character.isUpperCase(itemChar)) {
                if (Character.toUpperCase(prefix.charAt(prefixNdx)) == itemChar) {
                  prefixNdx++;
                }
                else {
                  break;
                }
              }
            }
            matches = prefixNdx == prefixLength;
          }
          else {
            for (int prefixNdx = 0; prefixNdx < prefixLength; prefixNdx++) {
              if (Character.toUpperCase(prefix.charAt(prefixNdx)) != Character.toUpperCase(itemText.charAt(prefixNdx))) {
                return false;
              }
            }
            matches = true;
          }
        }
      }
    }
    return matches;
  }

}
