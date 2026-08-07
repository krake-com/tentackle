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

package org.tentackle.model.parse;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Option parser test.
 */
@SuppressWarnings("missing-explicit-ctor")
public class OptionParserTest {

  @Test
  public void parseOptions() {

    String args = "productSuffix[\"PRE A\"] productId[getHeight()]";
    OptionParser parser = new OptionParser(args, null);
    String next = parser.nextOption(1);
    assertEquals(next, "productSuffix[\"PRE A\"]");
    next = parser.nextOption(1);
    assertEquals(next, "productId[getHeight()]");
    next = parser.nextOption(1);
    assertNull(next);

    args = "blah ...\\[\\][cached , +name, FMT=\"\\,##0\", end ] and some other stuff";
    StringBuilder comment = new StringBuilder();
    parser = new OptionParser(args, comment);
    next = parser.nextOption();
    assertEquals(next, "cached");
    next = parser.nextOption();
    assertEquals(next, "+name");
    next = parser.nextOption();
    assertEquals(next, "FMT=\"\\,##0\"");
    next = parser.nextOption();
    assertEquals(next, "end");
    next = parser.nextOption();
    assertNull(next);
    assertEquals(comment.toString(),"blah ...[] and some other stuff");
  }

  /**
   * An escape sequence at the very end of the text must be honored as well.
   */
  @Test
  public void parseEscapeAtEndOfText() {

    // the escaped comma does not separate the options (and keeps its backslash, as it is no bracket)
    OptionParser parser = new OptionParser("x\\,", null);
    assertEquals(parser.nextOption(), "x\\,");
    assertNull(parser.nextOption());

    // the escaped bracket does not close the option (and loses its backslash)
    parser = new OptionParser("abc\\]", null);
    assertEquals(parser.nextOption(), "abc]");
    assertNull(parser.nextOption());

    // a trailing lone backslash is no escape at all
    parser = new OptionParser("abc\\", null);
    assertEquals(parser.nextOption(), "abc\\");
    assertNull(parser.nextOption());
  }

}
