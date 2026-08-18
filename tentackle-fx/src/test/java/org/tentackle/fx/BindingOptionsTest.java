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

package org.tentackle.fx;

import org.testng.annotations.Test;

import org.tentackle.bind.BindingException;
import org.tentackle.common.StripMode;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

/**
 * Tests the {@code @Bindable} options applied to text components.<p>
 * Only a delegate is created, no control, so that the tests run without a JavaFX toolkit.
 */
@SuppressWarnings("missing-explicit-ctor")
public class BindingOptionsTest {

  /**
   * A text component delegate acting as its own component.<br>
   * Only the properties the binding options write to are exercised, so the methods
   * that would need a real FX control are left unimplemented.
   */
  private static class TestTextComponent extends FxTextComponentDelegate {

    private int columns;

    @Override
    public FxComponent getComponent() {
      return this;
    }

    @Override
    public boolean isEditable() {
      return true;    // don't delegate back to getComponent(), that would recurse
    }

    @Override
    public void setColumns(int columns) {
      this.columns = columns;
    }

    @Override
    public int getColumns() {
      return columns;
    }

    @Override
    public Object getViewObject() {
      return null;
    }

    @Override
    public void setViewObject(Object viewObject) {
      // nothing to do
    }

    @Override
    public void mapErrorOffsetToCaretPosition() {
      // nothing to do
    }
  }


  private final FxUtilities utilities = FxUtilities.getInstance();


  /**
   * Applies binding options to a fresh component.
   *
   * @param options the option string
   * @return the component the options were applied to
   */
  private TestTextComponent apply(String options) {
    TestTextComponent comp = new TestTextComponent();
    utilities.applyBindingOptions(comp, null, options);
    return comp;
  }


  /**
   * {@code FILLER=} without a value resets the filler to the default blank.
   */
  @Test
  public void testFillerDefault() {
    // an uppercase filler, so that this test is independent of the option's case handling
    TestTextComponent comp = apply("FILLER='X'");
    assertEquals(comp.getFiller(), 'X');

    // must not throw "unsupported @Bindable option"
    utilities.applyBindingOptions(comp, null, "FILLER=");
    assertEquals(comp.getFiller(), ' ');
  }


  /**
   * A quoted filler character keeps its case.
   */
  @Test
  public void testFillerKeepsCase() {
    assertEquals(apply("FILLER='a'").getFiller(), 'a');
    assertEquals(apply("FILLER='A'").getFiller(), 'A');
    assertEquals(apply("filler='a'").getFiller(), 'a');
  }


  /**
   * A filler given as a number is decoded, see {@link Integer#decode(String)}.
   */
  @Test
  public void testFillerAsNumber() {
    assertEquals(apply("FILLER=0x2a").getFiller(), '*');
    assertEquals(apply("FILLER=42").getFiller(), '*');
  }


  /**
   * Option keys remain case-insensitive.
   */
  @Test
  public void testKeysAreCaseInsensitive() {
    assertEquals(apply("UC").getCaseConversion(), CaseConversion.UPPER_CASE);
    assertEquals(apply("uc").getCaseConversion(), CaseConversion.UPPER_CASE);
    assertEquals(apply("Lc").getCaseConversion(), CaseConversion.LOWER_CASE);
    assertEquals(apply("nostrip").getStripMode(), StripMode.NONE);
    assertEquals(apply("cols=12").getColumns(), 12);
  }


  /**
   * Options are applied in one go and combine.
   */
  @Test
  public void testCombinedOptions() {
    TestTextComponent comp = apply("UC, FILLER='a', MAXCOLS=8");
    assertEquals(comp.getCaseConversion(), CaseConversion.UPPER_CASE);
    assertEquals(comp.getFiller(), 'a');
    assertEquals(comp.getMaxColumns(), 8);
  }


  /**
   * An unknown option is still rejected.
   */
  @Test
  public void testUnknownOption() {
    assertThrows(BindingException.class, () -> apply("NOSUCHOPTION"));
  }

}
