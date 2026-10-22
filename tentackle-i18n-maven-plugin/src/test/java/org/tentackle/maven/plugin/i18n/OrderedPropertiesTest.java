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

package org.tentackle.maven.plugin.i18n;

import org.testng.annotations.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

/**
 * Tests {@link OrderedProperties}.
 * <p>
 * The class exists so that the {@code pull} goal does not shuffle the property files it writes:
 * {@link Properties} is a {@link java.util.Hashtable} and stores its entries in whatever order the
 * hashing happens to produce, which turns every pull into an unreadable diff. Only what
 * {@code load()} and {@code store()} go through is overridden, so these are the tests.
 *
 * @author harald
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class OrderedPropertiesTest {

  /**
   * The keys are stored in the order they were put in, not in hash order.
   */
  @Test
  public void storeKeepsTheInsertionOrder() throws Exception {
    Properties props = new OrderedProperties();
    props.setProperty("zebra", "Z");
    props.setProperty("alpha", "A");
    props.setProperty("middle", "M");

    assertEquals(store(props), List.of("zebra=Z", "alpha=A", "middle=M"));
  }

  /**
   * A file that is loaded and stored again comes out the way it went in.
   */
  @Test
  public void loadAndStoreKeepTheFileOrder() throws Exception {
    Properties props = new OrderedProperties();
    props.load(new StringReader("zebra=Z\nalpha=A\nmiddle=M\n"));

    assertEquals(store(props), List.of("zebra=Z", "alpha=A", "middle=M"));
  }

  /**
   * Which is what a plain {@link Properties} does not do - the very reason for this class.
   */
  @Test
  public void aPlainPropertiesDoesNotKeepTheOrder() throws Exception {
    Properties ordered = new OrderedProperties();
    Properties plain = new Properties();
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      String key = "key" + i;
      keys.add(key + "=value" + i);
      ordered.setProperty(key, "value" + i);
      plain.setProperty(key, "value" + i);
    }

    assertEquals(store(ordered), keys);
    assertTrue(!store(plain).equals(keys) || keys.size() < 2, "plain properties happened to be ordered");
  }

  /**
   * Overwriting a value leaves the key where it was, so that an updated translation does not jump
   * to the end of the file.
   */
  @Test
  public void overwritingAValueKeepsThePosition() throws Exception {
    Properties props = new OrderedProperties();
    props.setProperty("zebra", "Z");
    props.setProperty("alpha", "A");
    props.setProperty("zebra", "Zulu");

    assertEquals(store(props), List.of("zebra=Zulu", "alpha=A"));
  }

  /**
   * A removed key is gone from both the ordered view and the properties themselves.
   */
  @Test
  public void removeDropsTheKey() throws Exception {
    Properties props = new OrderedProperties();
    props.setProperty("zebra", "Z");
    props.setProperty("alpha", "A");

    props.remove("zebra");

    assertEquals(store(props), List.of("alpha=A"));
    assertNull(props.getProperty("zebra"));
    assertEquals(props.keySet(), Collections.singleton("alpha"));
  }

  /**
   * {@code putAll} appends in the order of the given map.
   */
  @Test
  public void putAllAppendsInOrder() throws Exception {
    Properties props = new OrderedProperties();
    props.setProperty("zebra", "Z");
    Map<String, String> more = new LinkedHashMap<>();
    more.put("alpha", "A");
    more.put("middle", "M");

    props.putAll(more);

    assertEquals(store(props), List.of("zebra=Z", "alpha=A", "middle=M"));
  }

  /**
   * The views agree with each other and with the underlying properties, which is what the mojos
   * iterate over.
   */
  @Test
  public void theViewsAreOrderedAndComplete() {
    Properties props = new OrderedProperties();
    props.setProperty("zebra", "Z");
    props.setProperty("alpha", "A");

    assertEquals(new ArrayList<>(props.keySet()), List.of("zebra", "alpha"));
    assertEquals(Collections.list(props.keys()), List.of("zebra", "alpha"));
    assertEquals(props.entrySet().stream().map(Map.Entry::getKey).toList(), List.of("zebra", "alpha"));
    assertEquals(props.stringPropertyNames(), java.util.Set.of("zebra", "alpha"));
    assertEquals(props.getProperty("alpha"), "A");
    assertEquals(props.size(), 2);
  }

  /**
   * The string representation shows the order too, which is what makes a debug log readable.
   */
  @Test
  public void toStringShowsTheOrder() {
    Properties props = new OrderedProperties();
    props.setProperty("zebra", "Z");
    props.setProperty("alpha", "A");

    assertEquals(props.toString(), "{zebra=Z, alpha=A}");
  }


  /**
   * Stores the properties and returns the {@code key=value} lines in the order written.
   *
   * @param props the properties
   * @return the lines, without the comments
   * @throws Exception if storing failed
   */
  private List<String> store(Properties props) throws Exception {
    StringWriter writer = new StringWriter();
    props.store(writer, null);
    List<String> lines = new ArrayList<>();
    for (String line : writer.toString().lines().toList()) {
      if (!line.isBlank() && !line.startsWith("#")) {
        lines.add(line.trim());
      }
    }
    return lines;
  }

}
