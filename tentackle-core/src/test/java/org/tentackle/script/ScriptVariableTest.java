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

package org.tentackle.script;

import org.testng.annotations.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

/**
 * Tests the {@link ScriptVariable}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ScriptVariableTest {

  @Test
  public void testNameAndValue() {
    ScriptVariable var = new ScriptVariable("x", 42);
    assertEquals(var.getName(), "x");
    assertEquals(var.getValue(), 42);
  }

  @Test
  public void testValueLessConstructor() {
    ScriptVariable var = new ScriptVariable("x");
    assertEquals(var.getName(), "x");
    assertNull(var.getValue());
  }

  @Test
  public void testSetValue() {
    ScriptVariable var = new ScriptVariable("x");
    var.setValue("hello");
    assertEquals(var.getValue(), "hello");
    var.setValue(null);
    assertNull(var.getValue());
  }

  @Test
  public void testNullNameThrows() {
    assertThrows(NullPointerException.class, () -> new ScriptVariable(null));
    assertThrows(NullPointerException.class, () -> new ScriptVariable(null, "value"));
  }

  @Test
  public void testToStringWithNullValue() {
    assertEquals(new ScriptVariable("x").toString(), "x=<null>");
  }

  @Test
  public void testToStringWithValue() {
    // name='value'(effectiveClassName)
    assertEquals(new ScriptVariable("x", "hello").toString(), "x='hello'(java.lang.String)");
    assertEquals(new ScriptVariable("n", 42).toString(), "n='42'(java.lang.Integer)");
  }

  @Test
  public void testEqualsAndHashCodeBasedOnNameOnly() {
    ScriptVariable a = new ScriptVariable("x", 1);
    ScriptVariable b = new ScriptVariable("x", 2);   // different value, same name
    ScriptVariable c = new ScriptVariable("y", 1);

    assertEquals(a, a);
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertNotEquals(a, c);
    assertNotEquals(a, null);
    assertNotEquals(a, "x");
  }

  @Test
  public void testVariablesToStringNull() {
    assertEquals(ScriptVariable.variablesToString(null), "");
  }

  @Test
  public void testVariablesToStringEmpty() {
    assertEquals(ScriptVariable.variablesToString(Set.of()), "");
  }

  @Test
  public void testVariablesToStringOrdered() {
    Set<ScriptVariable> variables = new LinkedHashSet<>();
    variables.add(new ScriptVariable("a", 1));
    variables.add(new ScriptVariable("b"));
    String s = ScriptVariable.variablesToString(variables);
    assertEquals(s, "a='1'(java.lang.Integer), b=<null>");
  }

  @Test
  public void testVariablesAreUniqueByNameInSet() {
    // since equals/hashCode are name-based, a Set collapses duplicate names
    Set<ScriptVariable> variables = new LinkedHashSet<>();
    variables.add(new ScriptVariable("a", 1));
    assertTrue(variables.add(new ScriptVariable("b", 2)));
    assertEquals(variables.size(), 2);
    // adding another "a" must be rejected by the set
    assertTrue(!variables.add(new ScriptVariable("a", 99)));
    assertEquals(variables.size(), 2);
  }
}
