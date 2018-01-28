/*
 * Tentackle - http://www.tentackle.org
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

import java.util.Collection;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.event.EventHandler;
import javafx.scene.control.Control;
import javafx.scene.input.KeyEvent;
import javafx.util.StringConverter;

/**
 * Select items according to a prefix.<br>
 * Useful for ComboBox and ChoiceBox but can be applied to any control providing a list of items
 * that can be selected.
 * <p>
 * The implementation can easily be extended (for ex. camelCase navigation) because access
 * to members and methods is protected.
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
   * Timeout in milliseconds to start over from index 0.
   */
  public static long TIMEOUT = 500;


  protected final T control;
  protected final BooleanSupplier condition;
  protected final Supplier<Collection> itemProvider;
  protected final StringConverter itemConverter;
  protected final Consumer<Integer> selector;

  protected final StringBuilder prefixBuf;    // collected prefix string
  private long lastPressMillis;               // epochal time of last keystroke
  private int index;                          // where to start the search

  /**
   * Creates a prefix selection feature.
   *
   * @param control the control to add this feature to
   * @param condition the condition to activate this feature
   * @param itemProvider the items that can be selected
   * @param itemConverter the item to string converter
   * @param selector the selector to select an item by its index
   */
  public PrefixSelectionFeature(T control,
                                BooleanSupplier condition,
                                Supplier<Collection> itemProvider,
                                StringConverter itemConverter,
                                Consumer<Integer> selector) {

    this.control = control;
    this.condition = condition;
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
  }


  /**
   * Creates the handler to catch the key events.
   *
   * @return the handler
   */
  protected EventHandler<KeyEvent> createHandler() {
    return (event) -> {
      if (isEnabled()) {
        String chr = event.getCharacter();
        if (chr != null) {    // for sure... can it be null at all?
           long now = System.currentTimeMillis();
           if (now > lastPressMillis + TIMEOUT) {
             // clear after timeout
             prefixBuf.setLength(0);
             index = 0;
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
    return condition.getAsBoolean() && control.getProperties().containsKey(ENABLED);
  }

  /**
   * Select an item according to the current prefix.
   */
  protected void select() {
    String prefix = getPrefix();
    int i = 0;
    for (Object item: itemProvider.get()) {
      if (i >= index) {
        if (isItemMatching(prefix, item)) {
          index = i;
          selector.accept(index);
          break;
        }
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
    return prefixBuf.toString().toUpperCase();
  }

  /**
   * Returns whether item is matching prefix string.
   *
   * @param prefix the prefix string
   * @param item the item
   * @return true if matching
   */
  protected boolean isItemMatching(String prefix, Object item) {
    @SuppressWarnings("unchecked")
    String itemText = itemConverter == null ? item.toString() : itemConverter.toString(item);
    return itemText != null && itemText.toUpperCase().startsWith(prefix);
  }

}
