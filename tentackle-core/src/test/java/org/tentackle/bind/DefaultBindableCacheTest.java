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

package org.tentackle.bind;

import org.testng.annotations.Test;

import org.tentackle.common.StringHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/**
 * Tests the {@link DefaultBindableCache}.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class DefaultBindableCacheTest {

  private final BindingFactory factory = new AbstractBindingFactory();
  private final BindableCache cache = factory.getBindableCache();


  /** getter, setter and a field, plus an unannotated member. */
  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  public static class Person {

    @Bindable(ordinal = 3)
    private String note;

    private String firstName;
    private int age;

    @Bindable(ordinal = 1, options = "UC")
    public String getFirstName() {
      return firstName;
    }

    // same element as the getter: must be merged
    @Bindable(ordinal = 1)
    public void setFirstName(String firstName) {
      this.firstName = firstName;
    }

    @Bindable(ordinal = 2)
    public int getAge() {
      return age;
    }

    // not annotated: ignored
    public String getIgnored() {
      return "ignored";
    }
  }


  /** subclass adding one bindable member. */
  @SuppressWarnings("missing-explicit-ctor")
  public static class Employee extends Person {

    private String department;

    @Bindable(ordinal = 5)
    public String getDepartment() {
      return department;
    }
  }


  /** deliberately ordered out of natural order. */
  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  public static class Sortable {

    @Bindable(ordinal = 5)
    public String getZebra() {
      return "z";
    }

    @Bindable(ordinal = 1)
    public String getBanana() {
      return "b";
    }

    @Bindable(ordinal = 1)
    public String getApple() {
      return "a";
    }
  }


  /** two setters mapping to the same element => error. */
  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  public static class DuplicateSetters {

    @Bindable
    public void setValue(String value) {
    }

    @Bindable
    public void setValue(int value) {
    }
  }


  /** an immutable record with a "with"-method. */
  public record Money(@Bindable long amount) {
    public Money withAmount(long newAmount) {
      return new Money(newAmount);
    }
  }


  // NOTE: the cache keeps the camelName as derived from the member: lowercase-first for getters/setters,
  // but capitalized-first for fields. Normalize to a capitalized key so the tests can address elements
  // by the "ordinal + camelName" identity documented on BindableCache regardless of that quirk.
  private static Map<String, BindableElement> byName(Collection<BindableElement> elements) {
    Map<String, BindableElement> map = new LinkedHashMap<>();
    for (BindableElement element : elements) {
      map.put(StringHelper.firstToUpper(element.getCamelName()), element);
    }
    return map;
  }

  private static List<String> names(Collection<BindableElement> elements) {
    List<String> names = new ArrayList<>();
    for (BindableElement element : elements) {
      names.add(StringHelper.firstToUpper(element.getCamelName()));
    }
    return names;
  }


  @Test
  public void testDeclaredElements() {
    Collection<BindableElement> elements = cache.getBindableMap(Person.class, true);
    Map<String, BindableElement> map = byName(elements);

    assertEquals(map.size(), 3);
    assertTrue(map.containsKey("FirstName"));
    assertTrue(map.containsKey("Age"));
    assertTrue(map.containsKey("Note"));
  }

  @Test
  public void testGetterAndSetterMerged() {
    Map<String, BindableElement> map = byName(cache.getBindableMap(Person.class, true));
    BindableElement firstName = map.get("FirstName");
    assertNotNull(firstName.getGetter());
    assertNotNull(firstName.getSetter());
    assertNull(firstName.getField());
    assertEquals(firstName.getType(), String.class);
    // options carried over from the annotation
    assertEquals(firstName.getBindingOptions(), "UC");
  }

  @Test
  public void testFieldBinding() {
    Map<String, BindableElement> map = byName(cache.getBindableMap(Person.class, true));
    BindableElement note = map.get("Note");
    assertNotNull(note.getField());
    assertNull(note.getGetter());
    assertNull(note.getSetter());
    assertEquals(note.getOrdinal(), 3);
  }

  @Test
  public void testUnannotatedMemberIgnored() {
    Map<String, BindableElement> map = byName(cache.getBindableMap(Person.class, true));
    assertNull(map.get("Ignored"));
  }

  @Test
  public void testSortedByOrdinalThenName() {
    List<String> names = names(cache.getBindableMap(Sortable.class, true));
    assertEquals(names, List.of("Apple", "Banana", "Zebra"));
  }

  @Test
  public void testDeclaredOnlyExcludesInherited() {
    List<String> names = names(cache.getBindableMap(Employee.class, true));
    assertEquals(names, List.of("Department"));
  }

  @Test
  public void testInheritedIncludesSuperclass() {
    Collection<BindableElement> elements = cache.getBindableMap(Employee.class, false);
    Map<String, BindableElement> map = byName(elements);
    assertTrue(map.containsKey("Department"));
    assertTrue(map.containsKey("FirstName"));
    assertTrue(map.containsKey("Age"));
    assertTrue(map.containsKey("Note"));
  }

  @Test
  public void testCachingReturnsSameInstance() {
    Collection<BindableElement> first = cache.getBindableMap(Person.class, true);
    Collection<BindableElement> second = cache.getBindableMap(Person.class, true);
    // same cached element instances
    assertSame(byName(first).get("FirstName"), byName(second).get("FirstName"));
  }

  @Test
  public void testDeclaredAndInheritedAreCachedSeparately() {
    Collection<BindableElement> declared = cache.getBindableMap(Employee.class, true);
    Collection<BindableElement> inherited = cache.getBindableMap(Employee.class, false);
    assertEquals(byName(declared).size(), 1);
    assertTrue(byName(inherited).size() > 1);
  }

  @Test
  public void testDuplicateSettersThrow() {
    BindingException ex = expectThrows(BindingException.class,
                                       () -> cache.getBindableMap(DuplicateSetters.class, true));
    assertTrue(ex.getMessage().contains("more than one setters"));
  }

  @Test
  public void testRecordWithMethodDetected() {
    Map<String, BindableElement> map = byName(cache.getBindableMap(Money.class, true));
    BindableElement amount = map.get("Amount");
    assertNotNull(amount);
    assertNotNull(amount.getGetter());
    // the "with"-method is auto-detected for the record component
    assertNotNull(amount.getFrom());
    assertEquals(amount.getFrom().getName(), "withAmount");

    // the from-method actually produces a new record
    Object updated = amount.setObject(new Money(1L), 5L);
    assertEquals(updated, new Money(5L));
  }
}
