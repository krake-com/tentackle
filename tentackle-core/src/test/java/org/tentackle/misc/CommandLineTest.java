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

package org.tentackle.misc;

import org.tentackle.common.EncryptedProperties;
import org.testng.annotations.Test;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests for {@link CommandLine}.
 */
@SuppressWarnings("missing-explicit-ctor")
public class CommandLineTest {

  @Test
  public void testEmpty() {
    CommandLine cl = new CommandLine();
    assertNull(cl.getArgs());
    assertTrue(cl.getArguments().isEmpty());
    assertTrue(cl.getOptions().isEmpty());
    assertFalse(cl.isOptionSet("anything"));
    assertNull(cl.getOptionValue("anything"));
  }

  @Test
  public void testNonOptionArgumentsPreserveOrder() {
    CommandLine cl = new CommandLine(new String[] {"file1", "file2", "file3"});
    assertEquals(cl.getArguments(), List.of("file1", "file2", "file3"));
    assertTrue(cl.getOptions().isEmpty());
  }

  @Test
  public void testFlagOptionHasNullValue() {
    CommandLine cl = new CommandLine(new String[] {"--enabled"});
    assertTrue(cl.isOptionSet("enabled"));
    assertNull(cl.getOptionValue("enabled"));
    assertTrue(cl.getArguments().isEmpty());
  }

  @Test
  public void testValueOption() {
    CommandLine cl = new CommandLine(new String[] {"--timeout=30"});
    assertTrue(cl.isOptionSet("timeout"));
    assertEquals(cl.getOptionValue("timeout"), "30");
  }

  @Test
  public void testValueWithEmbeddedEquals() {
    // only the first '=' separates the option from the value
    CommandLine cl = new CommandLine(new String[] {"--url=a=b=c"});
    assertEquals(cl.getOptionValue("url"), "a=b=c");
  }

  @Test
  public void testEmptyValue() {
    CommandLine cl = new CommandLine(new String[] {"--name="});
    assertTrue(cl.isOptionSet("name"));
    assertEquals(cl.getOptionValue("name"), "");
  }

  @Test
  public void testLeadingEqualsIsNotTreatedAsValue() {
    // '=' at index 0 (ndx not > 0) means the whole thing stays as the option name
    CommandLine cl = new CommandLine(new String[] {"--=weird"});
    assertTrue(cl.isOptionSet("=weird"));
    assertNull(cl.getOptionValue("=weird"));
  }

  @Test
  public void testMixedArgsAndOptions() {
    CommandLine cl = new CommandLine(new String[] {"cmd", "--verbose", "target", "--level=5"});
    assertEquals(cl.getArguments(), List.of("cmd", "target"));
    assertTrue(cl.isOptionSet("verbose"));
    assertEquals(cl.getOptionValue("level"), "5");
  }

  @Test
  public void testLastOptionWins() {
    CommandLine cl = new CommandLine(new String[] {"--x=1", "--x=2"});
    assertEquals(cl.getOptionValue("x"), "2");
    assertEquals(cl.getOptions().size(), 1);
  }

  @Test
  public void testNullArgumentsAreIgnored() {
    CommandLine cl = new CommandLine(new String[] {"a", null, "--o=v", null});
    assertEquals(cl.getArguments(), List.of("a"));
    assertEquals(cl.getOptionValue("o"), "v");
  }

  @Test
  public void testSetArgsResets() {
    CommandLine cl = new CommandLine(new String[] {"--first=1", "arg"});
    assertTrue(cl.isOptionSet("first"));
    cl.setArgs(new String[] {"--second=2"});
    assertFalse(cl.isOptionSet("first"));
    assertTrue(cl.isOptionSet("second"));
    assertTrue(cl.getArguments().isEmpty());
  }

  @Test
  public void testGetArgsReturnsOriginal() {
    String[] args = {"--a", "b"};
    CommandLine cl = new CommandLine(args);
    assertEquals(cl.getArgs(), args);
  }

  @Test
  public void testGetOptionsAsProperties() {
    CommandLine cl = new CommandLine(new String[] {"--timeout=30", "--enabled"});
    EncryptedProperties props = cl.getOptionsAsProperties();
    assertEquals(props.getProperty("timeout"), "30");
    // a flag option (null value) becomes an empty string
    assertEquals(props.getProperty("enabled"), "");
  }
}
