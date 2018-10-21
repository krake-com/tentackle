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

package org.tentackle.common;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/**
 *
 * @author harald
 */
public class PathTest {

  private static class StringPath implements Path<StringPath, String> {

    private List<String> elements;
    private List<StringPath> paths;

    public StringPath(List<String> elements, List<StringPath> paths) {
      this.elements = elements;
      this.paths = paths;
    }

    public StringPath(String... elements) {
      this.elements = new ArrayList<>(Arrays.asList(elements));
    }

    @Override
    public List<String> getElements() {
      if (elements == null) {
        elements = new ArrayList<>();
      }
      return elements;
    }

    @Override
    public List<StringPath> getPaths() {
      if (paths == null) {
        paths = new ArrayList<>();
      }
      return paths;
    }

    @Override
    public String toString() {
      StringBuilder buf = new StringBuilder();
      boolean needComma = false;
      for (String s: getElements()) {
        if (needComma) {
          buf.append(", ");
        }
        else {
          needComma = true;
        }
        buf.append(s);
      }
      return buf.toString();
    }
  }


  private static class StringPathFactory implements PathFactory<StringPath, String> {

    @Override
    public StringPath create(List<String> elements, StringPath path) {
      List<String> allElements = new ArrayList<>(elements);
      allElements.addAll(path.getElements());
      return new StringPath(allElements, path.getPaths());
    }

    @Override
    public StringPath create(List<String> elements, List<StringPath> paths) {
      return new StringPath(elements, paths);
    }
  }


  @Test
  public void testSimpleConsolidation() {

    List<StringPath> paths = Arrays.asList(
            new StringPath("b"),
            new StringPath("a"));

    List<StringPath> consPaths = new StringPathFactory().merge(paths);

    assertEquals(consPaths.size(), 2);
    StringPath a = consPaths.get(0);
    assertEquals(a.getElements().size(), 1);
    assertEquals(a.getElements().get(0), "b");
    StringPath b = consPaths.get(1);
    assertEquals(b.getElements().size(), 1);
    assertEquals(b.getElements().get(0), "a");
  }


  @Test
  public void testComplexConsolidation() {

    List<StringPath> paths = Arrays.asList(
            new StringPath("a1", "b1", "c1", "d1", "eX"),
            new StringPath("a1", "b1", "c1", "d2"),
            new StringPath("a1", "b1", "c2"),
            new StringPath("a1", "b1", "c2", "d3"),
            new StringPath("a1", "b1", "c2", "d3", "eX"),
            new StringPath("a1", "b2", "cX", "dX"),
            new StringPath("a1", "b2"));

    /*
     * expected result:
     *
     * a1 -- b1 -- c1 -- d1 -- eX
     *    |     |     |
     *    |     |     -- d2
     *    |     |
     *    |     -- c2 -- d3 -- eX
     *    |
     *    -- b2 -- cX -- dX
     */

    List<StringPath> consPaths = new StringPathFactory().merge(paths);

    assertEquals(consPaths.size(), 1);

    StringPath a1 = consPaths.get(0);
    assertEquals(a1.getElements().size(), 1);
    assertEquals(a1.getElements().get(0), "a1");
    assertEquals(a1.getPaths().size(), 2);

    StringPath p1 = a1.getPaths().get(0);
    StringPath p2 = a1.getPaths().get(1);
    StringPath b2;
    StringPath b1;
    if ("b1".equals(p1.getElements().get(0))) {
      b1 = p1;
      b2 = p2;
    }
    else {
      b1 = p2;
      b2 = p1;
    }

    assertEquals(b2.getPaths().size(), 0);
    assertEquals(b2.getElements().size(), 3);
    assertEquals(b2.getElements().get(0), "b2");
    assertEquals(b2.getElements().get(1), "cX");
    assertEquals(b2.getElements().get(2), "dX");

    assertEquals(b1.getPaths().size(), 2);
    assertEquals(b1.getElements().size(), 1);
    assertEquals(b1.getElements().get(0), "b1");

    p1 = b1.getPaths().get(0);
    p2 = b1.getPaths().get(1);
    StringPath c1;
    StringPath c2;
    if ("c1".equals(p1.getElements().get(0))) {
      c1 = p1;
      c2 = p2;
    }
    else {
      c1 = p2;
      c2 = p1;
    }

    assertEquals(c1.getPaths().size(), 2);
    assertEquals(c1.getElements().size(), 1);

    assertEquals(c2.getPaths().size(), 0);
    assertEquals(c2.getElements().size(), 3);
    assertEquals(c2.getElements().get(0), "c2");
    assertEquals(c2.getElements().get(1), "d3");
    assertEquals(c2.getElements().get(2), "eX");

    p1 = c1.getPaths().get(0);
    p2 = c1.getPaths().get(1);
    StringPath d1;
    StringPath d2;
    if ("d1".equals(p1.getElements().get(0))) {
      d1 = p1;
      d2 = p2;
    }
    else {
      d1 = p2;
      d2 = p1;
    }
    assertEquals(d1.getElements().size(), 2);
    assertEquals(d1.getPaths().size(), 0);
    assertEquals(d1.getElements().get(0), "d1");
    assertEquals(d1.getElements().get(1), "eX");

    assertEquals(d2.getElements().size(), 1);
    assertEquals(d2.getPaths().size(), 0);
    assertEquals(d2.getElements().get(0), "d2");
  }

}
