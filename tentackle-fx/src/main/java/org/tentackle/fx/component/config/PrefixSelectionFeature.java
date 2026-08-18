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
 * Useful for (non-editable) ComboBoxes and ChoiceBoxes but can be applied to any control providing a list of items
 * that can be selected.<br>
 * The prefix selection is case-sensitive if the first typed character is uppercase.
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
   * Gets the text displayed for an item.<br>
   * Both the item and the converter's result may be null: the default converter of a
   * {@link javafx.scene.control.ComboBox} maps a null item to a null text.
   *
   * @param item the item; may be null
   * @return the displayed text, null if the item has none
   */
  @SuppressWarnings("unchecked")
  protected String getItemText(Object item) {
    StringConverter converter = itemConverter == null ? null : itemConverter.get();
    if (converter != null) {
      return converter.toString(item);
    }
    return item == null ? null : item.toString();
  }

  /**
   * Returns whether the item is matching prefix string.<br>
   * Items without a displayable text never match. Notice that a null item is perfectly valid,
   * as it is the common idiom for the "nothing selected" entry of a dropdown list.
   *
   * @param prefix the prefix string
   * @param item the item; may be null
   * @return true if matching
   */
  protected boolean isItemMatching(String prefix, Object item) {
    boolean matches = false;
    String itemText = getItemText(item);
    if (itemText != null && !StringHelper.isAllWhitespace(prefix)) {
      int prefixLength = prefix.length();
      int itemTextLength = itemText.length();
      if (itemTextLength >= prefixLength) {
        boolean camelCase = isCamelCaseEnabled();
        if (Character.isUpperCase(prefix.charAt(0))) {  // case-sensitive
          if (camelCase) {
            int prefixNdx = 0;
            for (int itemNdx=0; itemNdx < itemTextLength && prefixNdx < prefixLength; itemNdx++) {
              char itemChar = itemText.charAt(itemNdx);
              char prefixChar = prefix.charAt(prefixNdx);
              if (!Character.isLowerCase(itemChar)) {   // UC or digit: next prefix char must match
                if (prefixChar == itemChar) {
                  prefixNdx++;
                }
                else {
                  break;
                }
              }
              else if (!Character.isUpperCase(prefixChar)) {  // next item char must match
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
        else {  // case-insensitive
          if (camelCase) {
            // find only uppercase letters in itemText
            int prefixNdx = 0;
            for (int itemNdx=0; itemNdx < itemTextLength && prefixNdx < prefixLength; itemNdx++) {
              char itemChar = itemText.charAt(itemNdx);
              if (!Character.isLowerCase(itemChar)) {   // UC or digit
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
