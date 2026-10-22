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

package org.tentackle.reflect;

import org.testng.annotations.Test;

import org.tentackle.common.TentackleRuntimeException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.testng.Assert.fail;

/**
 * Tests the reflection helper.
 *
 * @author harald
 */
@SuppressWarnings("missing-explicit-ctor")
public class ReflectionHelperTest {

  private static final String MY_CLASS = "org.tentackle.reflect.ReflectionHelperTest$MyClass";
  private static final String MY_RECORD = "org.tentackle.reflect.ReflectionHelperTest$MyRecord";

  @SuppressWarnings("missing-explicit-ctor")
  public static class MyClass {

    public int myField = 10;

    @Override
    public String toString() {
      return "MyClass";
    }
  }


  /**
   * A member record, which adds {@link Record} to the superclass chain.
   */
  public record MyRecord(String name) {}


  public MyClass otherClass;


  public MyClass getMyClass() {
    return new MyClass();
  }


  // -------------------------- name helpers ---------------------------------

  @Test
  public void testClassBaseName() {
    assertEquals(ReflectionHelper.getClassBaseName(getClass().getName()), "ReflectionHelperTest");
    assertEquals(ReflectionHelper.getClassBaseName("Simple"), "Simple");
    assertEquals(ReflectionHelper.getClassBaseName(""), "");
  }

  @Test
  public void testPackageName() {
    assertEquals(ReflectionHelper.getPackageName("aa.bb.cc.Test"),"aa.bb.cc");
    assertEquals(ReflectionHelper.getPackageName("aa.bb.cc.Test$1"),"aa.bb.cc");
    assertEquals(ReflectionHelper.getPackageName("aa.bb.cc.test"),"aa.bb.cc.test");
    assertEquals(ReflectionHelper.getPackageName("aa.bb.cc.Outer.Inner"),"aa.bb.cc");
    assertEquals(ReflectionHelper.getPackageName("Blah"),"");
    assertEquals(ReflectionHelper.getPackageName(""),"");
  }

  @Test
  public void testOutermostClassName() {
    assertEquals(ReflectionHelper.getOutermostClassName("aa.bb.cc.Outer.Inner1.Inner2"),"aa.bb.cc.Outer");
    assertEquals(ReflectionHelper.getOutermostClassName("aa.bb.cc.Blah"),"aa.bb.cc.Blah");
    assertEquals(ReflectionHelper.getOutermostClassName("aa.bb.cc.Blah$1"),"aa.bb.cc.Blah");
    assertEquals(ReflectionHelper.getOutermostClassName("aa.bb.cc.Blah$$Lambda$1/2147972"),"aa.bb.cc.Blah");
    assertEquals(ReflectionHelper.getOutermostClassName("aa.bb.cc"),"aa.bb.cc");
    assertEquals(ReflectionHelper.getOutermostClassName("aa.bb.cc."),"aa.bb.cc.");
    assertEquals(ReflectionHelper.getOutermostClassName("Blah"),"Blah");
    assertEquals(ReflectionHelper.getOutermostClassName("Blah."),"Blah");
    assertEquals(ReflectionHelper.getOutermostClassName("Outer.Inner1.Inner2"),"Outer");
    assertEquals(ReflectionHelper.getOutermostClassName("Outer.Inner1$$Lambda$1/23231.Inner2"),"Outer");
    assertEquals(ReflectionHelper.getOutermostClassName(""),"");
  }

  @Test
  public void testPackagePathName() {
    assertEquals(ReflectionHelper.getPackagePathName(getClass()), "/org/tentackle/reflect");
    assertEquals(ReflectionHelper.getPackagePathName(String.class), "/java/lang");
  }

  @Test
  public void testMappingName() {
    // top level classes and primitives are named by their canonical name
    assertEquals(ReflectionHelper.getMappingName(String.class), "java.lang.String");
    assertEquals(ReflectionHelper.getMappingName(String[].class), "java.lang.String[]");
    assertEquals(ReflectionHelper.getMappingName(Object[].class), "java.lang.Object[]");
    assertEquals(ReflectionHelper.getMappingName(int[].class), "int[]");

    // a member class must keep the $xxx of its binary name to stay loadable by Class.forName()
    assertEquals(ReflectionHelper.getMappingName(MyClass.class), MY_CLASS);
    assertEquals(ReflectionHelper.getMappingName(Map.Entry.class), "java.util.Map$Entry");

    // an array is neither a member class nor does it have an enclosing class:
    // the component class determines the name
    assertEquals(ReflectionHelper.getMappingName(MyClass[].class), MY_CLASS + "[]");
    assertEquals(ReflectionHelper.getMappingName(MyClass[][].class), MY_CLASS + "[][]");
    assertEquals(ReflectionHelper.getMappingName(MyRecord[].class), MY_RECORD + "[]");
    assertEquals(ReflectionHelper.getMappingName(Map.Entry[].class), "java.util.Map$Entry[]");

    // classes without a canonical name fall back to their binary name
    Runnable anonymous = () -> { };
    assertNull(anonymous.getClass().getCanonicalName());
    assertEquals(ReflectionHelper.getMappingName(anonymous.getClass()), anonymous.getClass().getName());
  }


  // -------------------------- serializable class name ----------------------

  @Test
  public void testSerializableClassName() {
    assertEquals(ReflectionHelper.getSerializableClassName(String.class), "java.lang.String");
    assertEquals(ReflectionHelper.getSerializableClassName(Class.class), "@C");
    assertEquals(ReflectionHelper.getSerializableClassName(Object.class), "@O");
    assertEquals(ReflectionHelper.getSerializableClassName(int.class), "java.lang.Integer#TYPE");
    assertEquals(ReflectionHelper.getSerializableClassName(boolean.class), "java.lang.Boolean#TYPE");
  }

  @Test
  public void testLoadClass() throws ClassNotFoundException {
    assertSame(ReflectionHelper.loadClass("java.lang.String"), String.class);
    assertSame(ReflectionHelper.loadClass("@C"), Class.class);
    assertSame(ReflectionHelper.loadClass("@O"), Object.class);
    assertSame(ReflectionHelper.loadClass("java.lang.Integer#TYPE"), int.class);
    assertSame(ReflectionHelper.loadClass("java.lang.Double#TYPE"), double.class);
  }

  @Test
  public void testSerializableClassNameRoundTrip() throws ClassNotFoundException {
    for (Class<?> clazz : new Class<?>[] {
        String.class, Class.class, Object.class,
        int.class, long.class, short.class, byte.class,
        char.class, boolean.class, float.class, double.class,
        MyClass.class }) {
      assertSame(ReflectionHelper.loadClass(ReflectionHelper.getSerializableClassName(clazz)), clazz);
    }
  }

  @Test
  public void testLoadClassUnknown() {
    assertThrows(ClassNotFoundException.class, () -> ReflectionHelper.loadClass("no.such.Class"));
  }


  // -------------------------- method / declare strings ---------------------

  @Test
  public void testMethodToString() throws NoSuchMethodException {
    Method noArg = getClass().getMethod("getMyClass");
    assertEquals(ReflectionHelper.methodToString(noArg), "ReflectionHelperTest.getMyClass()");

    Method withArgs = Bean.class.getMethod("setName", String.class);
    assertEquals(ReflectionHelper.methodToString(withArgs), "Bean.setName(String)");
  }

  @Test
  public void testMakeDeclareString() {
    assertEquals(ReflectionHelper.makeDeclareString("[[[B"), "byte[][][]");
    assertEquals(ReflectionHelper.makeDeclareString("[I"), "int[]");
    assertEquals(ReflectionHelper.makeDeclareString("[Z"), "boolean[]");
    assertEquals(ReflectionHelper.makeDeclareString("[C"), "char[]");
    assertEquals(ReflectionHelper.makeDeclareString("[D"), "double[]");
    assertEquals(ReflectionHelper.makeDeclareString("[F"), "float[]");
    assertEquals(ReflectionHelper.makeDeclareString("[J"), "long[]");
    assertEquals(ReflectionHelper.makeDeclareString("[S"), "short[]");
    // non-array names are returned unchanged
    assertEquals(ReflectionHelper.makeDeclareString("java.lang.String"), "java.lang.String");
    assertEquals(ReflectionHelper.makeDeclareString(String[].class.getName()), "java.lang.String[]");
    assertEquals(ReflectionHelper.makeDeclareString(String[][].class.getName()), "java.lang.String[][]");
  }


  // -------------------------- primitive / wrapper --------------------------

  @Test
  public void testPrimitiveToWrapper() {
    assertSame(ReflectionHelper.primitiveToWrapperClass(int.class), Integer.class);
    assertSame(ReflectionHelper.primitiveToWrapperClass(boolean.class), Boolean.class);
    assertSame(ReflectionHelper.primitiveToWrapperClass(char.class), Character.class);
    assertThrows(ClassCastException.class, () -> ReflectionHelper.primitiveToWrapperClass(String.class));
    assertThrows(ClassCastException.class, () -> ReflectionHelper.primitiveToWrapperClass(Integer.class));
  }

  @Test
  public void testWrapperToPrimitive() {
    assertSame(ReflectionHelper.wrapperToPrimitiveClass(Integer.class), int.class);
    assertSame(ReflectionHelper.wrapperToPrimitiveClass(Boolean.class), boolean.class);
    assertSame(ReflectionHelper.wrapperToPrimitiveClass(Character.class), char.class);
    assertThrows(ClassCastException.class, () -> ReflectionHelper.wrapperToPrimitiveClass(String.class));
    assertThrows(ClassCastException.class, () -> ReflectionHelper.wrapperToPrimitiveClass(int.class));
  }


  // -------------------------- assignable / annotations ---------------------

  @Test
  public void testIsAssignableFrom() {
    assertTrue(ReflectionHelper.isAssignableFrom(Integer.class, new Class<?>[]{Number.class, Comparable.class}, true));
    assertFalse(ReflectionHelper.isAssignableFrom(Integer.class, new Class<?>[]{Number.class, String.class}, true));
    assertTrue(ReflectionHelper.isAssignableFrom(Integer.class, new Class<?>[]{String.class, Number.class}, false));
    assertFalse(ReflectionHelper.isAssignableFrom(Integer.class, new Class<?>[]{String.class, Runnable.class}, false));
    // null arguments are treated as "match"
    assertTrue(ReflectionHelper.isAssignableFrom(null, new Class<?>[]{Number.class}, true));
    assertTrue(ReflectionHelper.isAssignableFrom(Integer.class, null, true));
  }

  @Deprecated
  @SuppressWarnings("missing-explicit-ctor")
  static class Deprecatable {
  }

  @Test
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public void testIsAnnotationPresent() {
    Class<Annotation>[] deprecated = new Class[]{Deprecated.class};
    Class<Annotation>[] deprecatedAndFunctional = new Class[]{Deprecated.class, FunctionalInterface.class};
    assertTrue(ReflectionHelper.isAnnotationPresent(Deprecatable.class, deprecated, true));
    assertFalse(ReflectionHelper.isAnnotationPresent(Bean.class, deprecated, true));
    assertFalse(ReflectionHelper.isAnnotationPresent(Deprecatable.class, deprecatedAndFunctional, true));
    assertTrue(ReflectionHelper.isAnnotationPresent(Deprecatable.class, deprecatedAndFunctional, false));
    // null arguments are treated as "match"
    assertTrue(ReflectionHelper.isAnnotationPresent(null, deprecated, true));
    assertTrue(ReflectionHelper.isAnnotationPresent(Bean.class, null, true));
  }


  // -------------------------- generic inner type ---------------------------

  public List<String> stringList;
  public List<? extends Number> extendsList;
  public List<? super Integer> superList;
  public List<List<String>> nestedList;
  public List<?> wildcardList;
  public String noGeneric;

  @Test
  public void testExtractGenericInnerTypeClass() throws NoSuchFieldException {
    assertSame(ReflectionHelper.extractGenericInnerTypeClass(field("stringList")), String.class);
    assertSame(ReflectionHelper.extractGenericInnerTypeClass(field("extendsList")), Number.class);
    assertSame(ReflectionHelper.extractGenericInnerTypeClass(field("superList")), Integer.class);
    assertSame(ReflectionHelper.extractGenericInnerTypeClass(field("nestedList")), List.class);
    assertSame(ReflectionHelper.extractGenericInnerTypeClass(field("wildcardList")), Object.class);
    assertNull(ReflectionHelper.extractGenericInnerTypeClass(field("noGeneric")));
  }

  private java.lang.reflect.Type field(String name) throws NoSuchFieldException {
    return getClass().getField(name).getGenericType();
  }


  // -------------------------- fields / methods -----------------------------

  @SuppressWarnings("missing-explicit-ctor")
  static class Base {
    int baseField;
    public void baseMethod() { }
  }

  @SuppressWarnings("missing-explicit-ctor")
  static class Derived extends Base {
    int derivedField;
    public void derivedMethod() { }
  }

  @Test
  public void testGetAllFields() {
    Field[] fields = ReflectionHelper.getAllFields(Derived.class, null, false, null, false);
    List<String> names = Arrays.stream(fields).map(Field::getName).toList();
    assertTrue(names.contains("derivedField"));
    assertTrue(names.contains("baseField"));
  }

  @Test
  public void testGetAllMethods() {
    Method[] methods = ReflectionHelper.getAllMethods(Derived.class, null, false, null, false);
    List<String> names = Arrays.stream(methods).map(Method::getName).toList();
    assertTrue(names.contains("derivedMethod"));
    assertTrue(names.contains("baseMethod"));
  }


  // -------------------------- class inspection -----------------------------

  @SuppressWarnings("missing-explicit-ctor")
  class NonStaticInner { }

  @SuppressWarnings("missing-explicit-ctor")
  static class StaticNested { }

  @Test
  public void testIsNonStaticInnerClass() {
    assertTrue(ReflectionHelper.isNonStaticInnerClass(NonStaticInner.class));
    assertFalse(ReflectionHelper.isNonStaticInnerClass(StaticNested.class));
    assertFalse(ReflectionHelper.isNonStaticInnerClass(ReflectionHelperTest.class));
  }

  @Test
  public void testIsImmutableCollection() {
    assertTrue(ReflectionHelper.isImmutableCollection(List.of(1, 2).getClass()));
    assertTrue(ReflectionHelper.isImmutableCollection(java.util.Set.of(1).getClass()));
    assertTrue(ReflectionHelper.isImmutableCollection(Map.of("a", 1).getClass()));
    assertFalse(ReflectionHelper.isImmutableCollection(new ArrayList<>().getClass()));
  }


  // -------------------------- canonical constructor ------------------------

  @Test
  public void testCanonicalConstructor() {
    record MyRecord(int myField, String myString) { }

    try {
      Constructor<MyRecord> constructor = ReflectionHelper.getCanonicalConstructor(MyRecord.class);
      assertEquals(constructor.getParameterCount(), 2);
      assertEquals(constructor.getParameterTypes()[0], int.class);
      assertEquals(constructor.getParameterTypes()[1], String.class);
    }
    catch (TentackleRuntimeException ex) {
      fail(ex.getMessage());
    }
  }

  @SuppressWarnings("missing-explicit-ctor")
  static class AnnotatedConstructor {
    AnnotatedConstructor() { }
    @CanonicalConstructor
    AnnotatedConstructor(int a, String b) { }
  }

  @SuppressWarnings("missing-explicit-ctor")
  static class NoCanonicalConstructor {
    NoCanonicalConstructor(int a) { }
  }

  @SuppressWarnings("missing-explicit-ctor")
  static class MultipleCanonicalConstructors {
    @CanonicalConstructor
    MultipleCanonicalConstructors(int a) { }
    @CanonicalConstructor
    MultipleCanonicalConstructors(String b) { }
  }

  @Test
  public void testCanonicalConstructorOnClass() {
    Constructor<AnnotatedConstructor> constructor = ReflectionHelper.getCanonicalConstructor(AnnotatedConstructor.class);
    assertNotNull(constructor);
    assertEquals(constructor.getParameterCount(), 2);
    assertNull(ReflectionHelper.getCanonicalConstructor(NoCanonicalConstructor.class));
    assertThrows(TentackleRuntimeException.class,
        () -> ReflectionHelper.getCanonicalConstructor(MultipleCanonicalConstructors.class));
  }


  // -------------------------- canonical builder ----------------------------

  @CanonicalBuilder(WidgetBuilder.class)
  @SuppressWarnings("missing-explicit-ctor")
  static class Widget {
    final String name;
    final int size;
    Widget(WidgetBuilder builder) {
      this.name = builder.name;
      this.size = builder.size;
    }
  }

  @SuppressWarnings("missing-explicit-ctor")
  private static class WidgetBuilder {
    String name;
    int size;
    public WidgetBuilder() { }
    public WidgetBuilder name(String name) { this.name = name; return this; }
    public WidgetBuilder size(int size) { this.size = size; return this; }
    public Widget build() { return new Widget(this); }
  }

  @Test
  public void testCanonicalBuilderInfo() throws Exception {
    ReflectionHelper.CanonicalBuilderInfo info = ReflectionHelper.getCanonicalBuilderInfo(Widget.class);
    assertNotNull(info);
    assertEquals(info.constructor().getParameterCount(), 0);
    assertNotNull(info.getMethod("name"));
    assertNotNull(info.getMethod("size"));
    assertNull(info.getMethod("nope"));
    assertNotNull(info.buildMethod());
    assertEquals(info.buildMethod().getName(), "build");

    // no annotation -> null
    assertNull(ReflectionHelper.getCanonicalBuilderInfo(Bean.class));
  }


  // -------------------------- canonical names ------------------------------

  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  static class NamedParams {
    NamedParams(@CanonicalName("first") String a, @CanonicalName("second") int b) { }
    public void doIt(@CanonicalName("x") int x, @CanonicalName("y") int y) { }
  }

  @Test
  public void testGetCanonicalNamesConstructor() throws NoSuchMethodException {
    Constructor<NamedParams> constructor = NamedParams.class.getDeclaredConstructor(String.class, int.class);
    assertEquals(ReflectionHelper.getCanonicalNames(constructor), new String[]{"first", "second"});
  }

  @Test
  public void testGetCanonicalNamesMethod() throws NoSuchMethodException {
    Method method = NamedParams.class.getMethod("doIt", int.class, int.class);
    assertEquals(ReflectionHelper.getCanonicalNames(method), new String[]{"x", "y"});
  }


  // -------------------------- canonical members ----------------------------

  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  static class CanonicalMembers {
    @CanonicalField(ordinal = 1) public int b;
    @CanonicalField(ordinal = 0) public String a;
    @CanonicalGetter(ordinal = 2) public double getC() { return 0; }
    public int ignored;
  }

  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  static class DuplicateOrdinal {
    @CanonicalField(ordinal = 0) public int a;
    @CanonicalField(ordinal = 0) public int b;
  }

  @Test
  public void testGetCanonicalMembers() {
    Member[] members = ReflectionHelper.getCanonicalMembers(CanonicalMembers.class);
    assertEquals(members.length, 3);
    assertEquals(members[0].getName(), "a");
    assertTrue(members[0] instanceof Field);
    assertEquals(members[1].getName(), "b");
    assertTrue(members[1] instanceof Field);
    assertEquals(members[2].getName(), "getC");
    assertTrue(members[2] instanceof Method);
  }

  @Test
  public void testGetCanonicalMembersEmpty() {
    assertEquals(ReflectionHelper.getCanonicalMembers(Bean.class).length, 0);
  }

  @Test
  public void testGetCanonicalMembersDuplicateOrdinal() {
    assertThrows(TentackleRuntimeException.class, () -> ReflectionHelper.getCanonicalMembers(DuplicateOrdinal.class));
  }


  // -------------------------- getters / setters ----------------------------

  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  public static class Bean {
    private String name;
    private boolean active;
    private int noAccessors;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Bean withName(String name) { this.name = name; return this; }
    public int sizeOf(String s) { return s.length(); }
    public static Bean create() { Bean b = new Bean(); b.name = "created"; return b; }
    public void applyValue(String v) { this.name = v; }
    public String computeStuff() { return "stuff"; }
  }

  @Test
  public void testGetGetterName() throws NoSuchFieldException {
    assertEquals(ReflectionHelper.getGetterName(String.class, "name"), "getName");
    assertEquals(ReflectionHelper.getGetterName(boolean.class, "active"), "isActive");
    assertEquals(ReflectionHelper.getGetterName(Bean.class.getDeclaredField("name")), "getName");
    assertEquals(ReflectionHelper.getGetterName(Bean.class.getDeclaredField("active")), "isActive");
  }

  @Test
  public void testGetFieldName() {
    assertEquals(ReflectionHelper.getFieldName("getName"), "name");
    assertEquals(ReflectionHelper.getFieldName("isActive"), "active");
    assertEquals(ReflectionHelper.getFieldName("compute"), "compute");
  }

  @Test
  public void testGetGetterAndSetter() throws NoSuchFieldException {
    Field nameField = Bean.class.getDeclaredField("name");
    Field activeField = Bean.class.getDeclaredField("active");
    Field noAccessorsField = Bean.class.getDeclaredField("noAccessors");

    assertEquals(ReflectionHelper.getGetter(nameField).getName(), "getName");
    assertEquals(ReflectionHelper.getSetter(nameField).getName(), "setName");
    assertEquals(ReflectionHelper.getGetter(activeField).getName(), "isActive");
    assertEquals(ReflectionHelper.getSetter(activeField).getName(), "setActive");
    assertNull(ReflectionHelper.getGetter(noAccessorsField));
    assertNull(ReflectionHelper.getSetter(noAccessorsField));
  }

  @Test
  public void testIsGetterIsSetterIsFunction() throws NoSuchMethodException {
    Method getName = Bean.class.getMethod("getName");
    Method setName = Bean.class.getMethod("setName", String.class);
    Method withName = Bean.class.getMethod("withName", String.class);
    Method create = Bean.class.getMethod("create");

    assertTrue(ReflectionHelper.isGetter(getName));
    assertFalse(ReflectionHelper.isSetter(getName));
    assertFalse(ReflectionHelper.isFunction(getName));

    assertFalse(ReflectionHelper.isGetter(setName));
    assertTrue(ReflectionHelper.isSetter(setName));
    assertFalse(ReflectionHelper.isFunction(setName));   // void return

    assertTrue(ReflectionHelper.isFunction(withName));
    assertFalse(ReflectionHelper.isSetter(withName));    // non-void return

    // static methods are neither getters nor setters
    assertFalse(ReflectionHelper.isGetter(create));
  }

  @Test
  public void testSkipMethodNamePrefix() {
    assertEquals(ReflectionHelper.skipMethodNamePrefix("getBlah", "get"), "blah");
    assertEquals(ReflectionHelper.skipMethodNamePrefix("isValid", "is"), "valid");
    assertNull(ReflectionHelper.skipMethodNamePrefix("getblah", "get"));    // lowercase after prefix
    assertNull(ReflectionHelper.skipMethodNamePrefix("get", "get"));        // nothing after prefix
    assertNull(ReflectionHelper.skipMethodNamePrefix("compute", "get"));    // no prefix
  }

  @Test
  public void testGetterPathName() throws NoSuchMethodException {
    assertEquals(ReflectionHelper.getGetterPathName(Bean.class.getMethod("getName")), "name");
    assertEquals(ReflectionHelper.getGetterPathName(Bean.class.getMethod("isActive")), "active");
    assertEquals(ReflectionHelper.getGetterPathName(Bean.class.getMethod("computeStuff")), "computeStuff");
    assertNull(ReflectionHelper.getGetterPathName(Bean.class.getMethod("setName", String.class)));
  }

  @Test
  public void testSetterPathName() throws NoSuchMethodException {
    assertEquals(ReflectionHelper.getSetterPathName(Bean.class.getMethod("setName", String.class)), "name");
    assertEquals(ReflectionHelper.getSetterPathName(Bean.class.getMethod("applyValue", String.class)), "applyValue");
    assertNull(ReflectionHelper.getSetterPathName(Bean.class.getMethod("getName")));
  }

  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  static class FromBean {
    public FromBean fromName(String s) { return this; }
    public FromBean withValue(String s) { return this; }
    public FromBean convert(String s) { return this; }
    public int sizeOf(String s) { return s.length(); }
  }

  @Test
  public void testFromPathName() throws NoSuchMethodException {
    assertEquals(ReflectionHelper.getFromPathName(FromBean.class.getMethod("fromName", String.class)), "name");
    assertEquals(ReflectionHelper.getFromPathName(FromBean.class.getMethod("withValue", String.class)), "value");
    assertEquals(ReflectionHelper.getFromPathName(FromBean.class.getMethod("convert", String.class)), "convert");
    // primitive return type is not a "from"-method
    assertNull(ReflectionHelper.getFromPathName(FromBean.class.getMethod("sizeOf", String.class)));
  }


  // -------------------------- value by path --------------------------------

  @Test
  public void testGetValueByPath() {
    assertEquals(ReflectionHelper.getValueByPath(this, "myClass.myField"), 10);
    assertNull(ReflectionHelper.getValueByPath(this, "otherClass.myField"));

    try {
      ReflectionHelper.getValueByPath(this, "otherClass.myField*");
      fail("should have thrown a TentackleRuntimeException!");
    }
    catch (TentackleRuntimeException ex) {
      // fine
    }
  }

  @Test
  public void testGetValueByPathTrailingParentheses() {
    // trailing "()" is tolerated and stripped
    assertEquals(ReflectionHelper.getValueByPath(this, "myClass().myField"), 10);
  }

  @Test
  public void testGetValueByPathNullOrEmptyReturnsRoot() {
    assertSame(ReflectionHelper.getValueByPath(this, null), this);
    assertSame(ReflectionHelper.getValueByPath(this, ""), this);
    // a lone asterisk means "no null refs allowed" but the (empty) path evaluates to root
    assertSame(ReflectionHelper.getValueByPath(this, "*"), this);
  }

  @Test
  public void testGetValueByPathUnknownElement() {
    assertThrows(TentackleRuntimeException.class, () -> ReflectionHelper.getValueByPath(this, "doesNotExist"));
  }

  @Test
  public void testGetStaticValueByPath() {
    // Bean.create() returns a Bean whose name is "created"
    assertEquals(ReflectionHelper.getStaticValueByPath(Bean.class, "create.name"), "created");
  }

  @SuppressWarnings("unused")
  public static class StaticHolder {
    public static final Bean BEAN = Bean.create();
    private StaticHolder() {}
  }

  @Test
  public void testGetStaticValueByPathField() {
    assertSame(ReflectionHelper.getStaticValueByPath(StaticHolder.class, "BEAN"), StaticHolder.BEAN);
    assertEquals(ReflectionHelper.getStaticValueByPath(StaticHolder.class, "BEAN.name"), "created");
  }

  @Test
  public void testGetStaticValueByPathNoSuchMember() {
    TentackleRuntimeException ex = expectThrows(TentackleRuntimeException.class,
        () -> ReflectionHelper.getStaticValueByPath(Bean.class, "doesNotExist"));
    assertTrue(ex.getMessage().contains("doesNotExist"), ex.getMessage());
  }

  @Test
  public void testGetStaticValueByPathNull() {
    assertThrows(TentackleRuntimeException.class, () -> ReflectionHelper.getStaticValueByPath(Bean.class, null));
  }

  @SuppressWarnings({"missing-explicit-ctor", "unused"})
  public static class OtherBean {
    public String name = "field";   // same element name as Bean#getName, but resolved to a field
    public boolean isActive() { return true; }
  }

  @Test
  public void testGetValueByPathRepeated() {
    // the resolved members are cached per class and element: repeated evaluation must yield the
    // current value, and the same element name must not be confused between classes
    Bean bean = new Bean();
    OtherBean otherBean = new OtherBean();
    for (int i = 0; i < 3; i++) {
      bean.setName("name" + i);
      bean.setActive(i % 2 == 0);
      assertEquals(ReflectionHelper.getValueByPath(bean, "name"), "name" + i);
      assertEquals(ReflectionHelper.getValueByPath(bean, "active"), i % 2 == 0);
      assertEquals(ReflectionHelper.getValueByPath(otherBean, "name"), "field");
      assertEquals(ReflectionHelper.getValueByPath(otherBean, "active"), true);
      // unknown elements are cached as well, but must still fail every time
      assertThrows(TentackleRuntimeException.class, () -> ReflectionHelper.getValueByPath(bean, "doesNotExist"));
      assertEquals(ReflectionHelper.getStaticValueByPath(StaticHolder.class, "BEAN.name"), "created");
    }
  }


  // -------------------------- interfaces -----------------------------------

  interface Animal { }
  interface Pet extends Animal { }
  @SuppressWarnings("missing-explicit-ctor")
  static class Dog implements Pet { }

  @Test
  public void testFindInterface() {
    assertSame(ReflectionHelper.findInterface(Dog.class, Animal.class), Pet.class);
    assertSame(ReflectionHelper.findInterface(Dog.class, Pet.class), Pet.class);
    assertNull(ReflectionHelper.findInterface(Dog.class, Runnable.class));
  }

  @Test
  public void testFindAllInterfaces() {
    Collection<Class<? extends Animal>> all = ReflectionHelper.findAllInterfaces(Dog.class, Animal.class);
    assertEquals(all.size(), 1);
    assertTrue(all.contains(Pet.class));
    assertTrue(ReflectionHelper.findAllInterfaces(Dog.class, Runnable.class).isEmpty());
  }


  // -------------------------- proxy ----------------------------------------

  @Test
  public void testCreateProxyClass() {
    Class<?> proxyClass = ReflectionHelper.createProxyClass(new Class<?>[]{Runnable.class});
    assertTrue(Proxy.isProxyClass(proxyClass));
    assertTrue(Runnable.class.isAssignableFrom(proxyClass));
  }

  @Test
  public void testCreateProxyClassNoInterfaces() {
    assertThrows(TentackleRuntimeException.class, () -> ReflectionHelper.createProxyClass(new Class<?>[0]));
    assertThrows(TentackleRuntimeException.class, () -> ReflectionHelper.createProxyClass(null));
  }

}
