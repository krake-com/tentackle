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

import org.testng.annotations.Test;

import javafx.scene.control.Control;
import javafx.util.StringConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

/**
 * Tests the prefix selection.<p>
 * No control is created, so that the tests run without a JavaFX toolkit: the feature is
 * constructed without one and the control-dependent configuration is overridden.
 */
@SuppressWarnings("missing-explicit-ctor")
public class PrefixSelectionFeatureTest {

  /**
   * The converter a non-editable {@code ComboBox} uses by default: a null item has no text.
   */
  private static final StringConverter<Object> DEFAULT_CONVERTER = new StringConverter<>() {
    @Override
    public String toString(Object item) {
      return item == null ? null : item.toString();
    }

    @Override
    public Object fromString(String text) {
      return text;
    }
  };


  private final List<Integer> selected = new ArrayList<>();


  /**
   * Creates the feature under test.
   *
   * @param camelCase true to enable camel case matching
   * @param items the selectable items, may contain null
   * @return the feature
   */
  @SuppressWarnings("rawtypes")
  private PrefixSelectionFeature<Control> createFeature(boolean camelCase, Object... items) {
    selected.clear();   // TestNG reuses the instance for all test methods
    Collection<Object> itemList = Arrays.asList(items);
    return new PrefixSelectionFeature<>(null,
                                        () -> true,
                                        () -> (Collection) itemList,
                                        () -> DEFAULT_CONVERTER,
                                        selected::add) {
      @Override
      protected boolean isEnabled() {
        return true;    // would need the control's properties
      }

      @Override
      protected boolean isCamelCaseEnabled() {
        return camelCase;
      }
    };
  }


  /**
   * A null item has no text and must not match, rather than throw.
   */
  @Test
  public void testNullItemDoesNotMatch() {
    PrefixSelectionFeature<Control> feature = createFeature(false, "alpha");
    assertFalse(feature.isItemMatching("a", null));
    assertTrue(feature.isItemMatching("a", "alpha"));
  }


  /**
   * A null item in the list is skipped instead of breaking the selection.
   */
  @Test
  public void testNullItemIsSkipped() {
    // the null item is the "nothing selected" entry of the dropdown list
    PrefixSelectionFeature<Control> feature = createFeature(false, null, "berlin", "bremen");
    feature.select();
    assertEquals(selected, List.of());   // nothing typed yet

    feature.prefixBuf.append("br");
    feature.select();
    assertEquals(selected, List.of(2));
  }


  /**
   * Camel case matching tolerates a null item as well.
   */
  @Test
  public void testNullItemWithCamelCase() {
    PrefixSelectionFeature<Control> feature = createFeature(true, null, "BadenWuerttemberg");
    assertFalse(feature.isItemMatching("BW", null));
    assertTrue(feature.isItemMatching("BW", "BadenWuerttemberg"));

    feature.prefixBuf.append("BW");
    feature.select();
    assertEquals(selected, List.of(1));
  }

}
