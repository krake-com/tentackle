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


package org.tentackle.reflect;

import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;


/**
 * Methods related to the reflection API.
 *
 * @author harald
 */
public final class ReflectionHelper {

  /**
   * Gets the basename of a classname(-like) string.
   * The basename is the last name of a pathname with dots.
   *
   * @param str the classname
   * @return the basename
   */
  public static String getClassBaseName(String str)  {
    return StringHelper.lastAfter(str, '.');
  }


  /**
   * Gets the basename of a class.
   * The basename is the class name without the package.
   *
   * @param clazz the class
   * @return the basename
   * @see #getClassBaseName(java.lang.String)
   */
  public static String getClassBaseName(Class<?> clazz)  {
    return getClassBaseName(clazz.getName());
  }



  /**
   * Gets the package name.
   *
   * @param str the classname
   * @return the package name, null if no package
   */
  public static String getPackageName(String str) {
    int ndx = str.lastIndexOf('.');
    return ndx > 0 ? str.substring(0, ndx) : null;
  }


  /**
   * Gets the package name.
   *
   * @param clazz the class
   * @return the package name, null if no package
   */
  public static String getPackageName(Class<?> clazz) {
    return getPackageName(clazz.getName());
  }


  /**
   * Creates a string from a method.<br>
   * This variant produces a much shorter string than {@link Method#toString()} and
   * shortens logging output.
   *
   * @param method the method
   * @return the string
   */
  public static String methodToString(Method method) {
    try {
      StringBuilder sb = new StringBuilder();
      sb.append(getClassBaseName(method.getDeclaringClass())).append('.');
      sb.append(method.getName()).append('(');
      Class<?>[] params = method.getParameterTypes();
      for (int j = 0; j < params.length; j++) {
        sb.append(getClassBaseName(params[j]));
        if (j < (params.length - 1)) {
          sb.append(',');
        }
      }
      sb.append(')');
      return sb.toString();
    }
    catch (RuntimeException e) {
      return "<" + e + ">";
    }
  }


  /**
   * create a source-usable string from clazz.getName().
   * e.g. "[[[B" will be transformed to "byte[][][]"
   *
   * @param classname the classname
   * @return the converted string
   */
  public static String makeDeclareString(String classname) {
    if (classname.startsWith("["))  {
      // array
      StringBuilder buf = new StringBuilder();
      int len = classname.length();
      int i = 0;
      while (i < len) {
        if (classname.charAt(i) == '[') {
          buf.append("[]");
          ++i;
        }
        else  {
          break;
        }
      }

      String type = classname.substring(i);
      if (type.endsWith(";")) {
        // object
        type = type.substring(0, len - 1);
      }
      else if ("Z".equals(type)) {
        type = "boolean";
      }
      else if ("B".equals(type))  {
        type = "byte";
      }
      else if ("C".equals(type))  {
        type = "char";
      }
      else if ("D".equals(type))  {
        type = "double";
      }
      else if ("F".equals(type))  {
        type = "float";
      }
      else if ("I".equals(type))  {
        type = "int";
      }
      else if ("J".equals(type))  {
        type = "long";
      }
      else if ("S".equals(type))  {
        type = "short";
      }

      buf.insert(0, type);

      return buf.toString();
    }

    return classname;
  }



  private static final Map<Class<?>,Class<?>>  PRIMITIVES = new HashMap<>();

  static {
    PRIMITIVES.put(Boolean.TYPE, Boolean.class);
    PRIMITIVES.put(Byte.TYPE, Byte.class);
    PRIMITIVES.put(Character.TYPE, Character.class);
    PRIMITIVES.put(Short.TYPE, Short.class);
    PRIMITIVES.put(Integer.TYPE, Integer.class);
    PRIMITIVES.put(Long.TYPE, Long.class);
    PRIMITIVES.put(Double.TYPE, Double.class);
    PRIMITIVES.put(Float.TYPE, Float.class);
  }


  /**
   * Converts a primitive class to its wrapper class.
   *
   * @param primitiveClass the primitive
   * @return the wrapper
   * @throws ClassCastException if not a primitive class
   */
  public static Class<?> primitiveToWrapperClass(Class<?> primitiveClass) {
    Class<?> wrapperClass = PRIMITIVES.get(primitiveClass);
    if (wrapperClass == null) {
      throw new ClassCastException("not a primitive: " + primitiveClass);
    }
    return wrapperClass;
  }


  private static final Map<Class<?>,Class<?>> WRAPPERS = new HashMap<>();

  static {
    WRAPPERS.put(Boolean.class, Boolean.TYPE);
    WRAPPERS.put(Byte.class, Byte.TYPE);
    WRAPPERS.put(Character.class, Character.TYPE);
    WRAPPERS.put(Short.class, Short.TYPE);
    WRAPPERS.put(Integer.class, Integer.TYPE);
    WRAPPERS.put(Long.class, Long.TYPE);
    WRAPPERS.put(Double.class, Double.TYPE);
    WRAPPERS.put(Float.class, Float.TYPE);
  }

  /**
   * Converts a wrapper class to its primitive class.
   * @param wrapperClass the wrapper
   * @return the primitive
   * @throws ClassCastException if not a wrapper class
   */
  public static Class<?> wrapperToPrimitiveClass(Class<?> wrapperClass) {
    Class<?> primitiveClass = WRAPPERS.get(wrapperClass);
    if (primitiveClass == null) {
      throw new ClassCastException("not a wrapper: " + wrapperClass);
    }
    return primitiveClass;
  }


  /**
   * Tests if a given class implements or extends a set of interfaces or classes.
   *
   * @param superClasses the interfaces or classes
   * @param clazz the class to test
   * @param all true if all must match (logically and), false if at least one (logically or)
   * @return true if match
   */
  @SuppressWarnings("unchecked")
  public static boolean isAssignableFrom(Class<?> clazz, Class<?>[] superClasses, boolean all) {
    if (clazz != null && superClasses != null) {
      for (Class<?> superClass: superClasses) {
        if (superClass.isAssignableFrom(clazz)) {
          if (!all) {
            return true;
          }
        }
        else  {
          if (all) {
            return false;
          }
        }
      }
      return all;
    }
    return true;
  }


  /**
   * Tests if given class has a set of annotations.
   * <p>
   * Notice: if the clazz or the annotations is null, true will be returned.
   *
   * @param clazz the class to test
   * @param annotations the annotations
   * @param all true if all must match (logically and), false if at least one (logically or)
   * @return true if match
   */
  @SuppressWarnings("unchecked")
  public static boolean isAnnotationPresent(Class<?> clazz, Class<Annotation>[] annotations, boolean all) {
    if (clazz != null && annotations != null) {
      for (Class<? extends Annotation> annotation: annotations) {
        if (clazz.isAnnotationPresent(annotation)) {
          if (!all) {
            return true;
          }
        }
        else  {
          if (all) {
            return false;
          }
        }
      }
      return all;
    }
    return true;
  }


  /**
   * Gets all declared fields of given class and its superclasses.
   *
   * @param clazz the class to get all declared and inherited fields
   * @param boundingClasses bounding classes that all classes must implement or extend, null if all (avoid!)
   * @param boundToAll true if all boundingClasses must match (logically and), false if at least one (logically or)
   * @param annotations the annotations, null if none
   * @param annotatedByAll true if all annotations must match (logically and), false if at least one (logically or)
   * @return all fields
   */
  public static Field[] getAllFields(Class<?> clazz,
                                     Class<?>[] boundingClasses, boolean boundToAll,
                                     Class<Annotation>[] annotations, boolean annotatedByAll) {
    Field[] allFields = new Field[0];
    while (clazz != null &&
           isAssignableFrom(clazz, boundingClasses, boundToAll) &&
           isAnnotationPresent(clazz, annotations, annotatedByAll)) {
      Field[] fields = clazz.getDeclaredFields();
      Field[] tempFields = new Field[allFields.length + fields.length];
      System.arraycopy(allFields, 0, tempFields, 0, allFields.length);
      System.arraycopy(fields, 0, tempFields, allFields.length, fields.length);
      allFields = tempFields;
      clazz = clazz.getSuperclass();
    }
    return allFields;
  }



  /**
   * Gets all declared methods of given class and its superclasses.
   *
   * @param clazz the class to get all declared and inherited methods
   * @param boundingClasses bounding classes that all classes must implement or extend, null if all (avoid!)
   * @param boundToAll true if all boundingClasses must match (logically and), false if at least one (logically or)
   * @param annotations the annotations, null if none
   * @param annotatedByAll true if all annotations must match (logically and), false if at least one (logically or)
   * @return all methods
   */
  public static Method[] getAllMethods(Class<?> clazz,
                                       Class<?>[] boundingClasses, boolean boundToAll,
                                       Class<Annotation>[] annotations, boolean annotatedByAll) {
    Method[] allMethods = new Method[0];
    while (clazz != null &&
           isAssignableFrom(clazz, boundingClasses, boundToAll) &&
           isAnnotationPresent(clazz, annotations, annotatedByAll)) {
      Method[] methods = clazz.isInterface() ? clazz.getMethods() : clazz.getDeclaredMethods();
      Method[] tempMethods = new Method[allMethods.length + methods.length];
      System.arraycopy(allMethods, 0, tempMethods, 0, allMethods.length);
      System.arraycopy(methods, 0, tempMethods, allMethods.length, methods.length);
      allMethods = tempMethods;
      clazz = clazz.getSuperclass();
    }
    return allMethods;
  }



  /**
   * Checks whether a method is a getter.<br>
   * All non-void non-static methods without parameters are considered as getters.
   *
   * @param method the method
   * @return true if method is a getter
   */
  public static boolean isGetter(Method method) {
    return method.getReturnType() != Void.TYPE &&
           !Modifier.isStatic(method.getModifiers()) &&
           method.getParameterTypes().length == 0;
  }


  /**
   * Checks whether a method is a setter.<br>
   * All void non-static methods with a single parameter are considered as setters.
   * @param method the method
   * @return true if method is a setter
   */
  public static boolean isSetter(Method method) {
    return method.getReturnType() == Void.TYPE &&
           !Modifier.isStatic(method.getModifiers()) &&
           method.getParameterTypes().length == 1;
  }


  /**
   * Gets the method name reduced to the part used in paths.
   * <p>
   * Examples:<br>
   * <pre>
   * getBlah -&gt; blah
   * setBlah -&gt; blah
   * isValid -&gt; valid (if boolean)
   * computeThisAndThat -&gt; computeThisAndThat
   * gettingWorse -&gt; gettingWorse
   * isselDussel -&gt; isselDussel
   * </pre>
   *
   * @param method the method
   * @return the path
   */
  public static String getPathMethodName(Method method) {
    String name = method.getName();
    int length = name.length();
    if (name.startsWith("get") && length > 3 && Character.isUpperCase(name.charAt(3))) {
      name = name.substring(3);
    }
    if (name.startsWith("set") && length > 3 && Character.isUpperCase(name.charAt(3))) {
      name = name.substring(3);
    }
    else if (name.startsWith("is") && length > 2 && Character.isUpperCase(name.charAt(2)) &&
             (method.getReturnType().equals(Boolean.class) || method.getReturnType().equals(Boolean.TYPE))) {
      name = name.substring(2);
    }
    return StringHelper.firstToLower(name);
  }


  /**
   * Gets the method name reduced to the part used in paths if method is an attribute getter.<br>
   * It is also verified that the methodname conforms to the rules for attribute getters.
   *
   * @param method the method
   * @return the method's reduced name, null if this is not an attribute getter
   */
  public static String getPathGetterName(Method method) {
    if (isGetter(method)) {
      return getPathMethodName(method);
    }
    else  {
      return null;
    }
  }


  /**
   * Gets the method name reduced to the part used in paths if method is an attribute setter.<br>
   * It is also verified that the methodname conforms to the rules for attribute getters,
   *
   * @param method the method
   * @return the method's reduced name, null if this is not an attribute getter
   */
  public static String getPathSetterName(Method method) {
    if (isSetter(method)) {
      String name = getPathMethodName(method);
      if (!name.startsWith("set")) {
        return name;
      }
    }
    return null;
  }




  /**
   * Gets the value of an attribute along a member path.
   * <p>
   * The attribute is accessible by either a getter-method
   * or a field, which must be public.
   * <p>
   * Example:<br>
   * <pre>
   * "invoice.invoiceLine.index"
   * </pre>
   * may correspond to the java path:
   * <pre>
   * this.getInvoice().getInvoiceLine().getIndex().
   * </pre>
   * <p>
   *
   * If a reference in the path is null, the returned value will be null by default.
   * This behaviour can be changed by appending an asterisk, i.e.
   * <code>"invoice.invoiceLine.index*"</code> will throw a TentackleRuntimeException
   * if invoice or invoiceLine is null.
   *
   * <p>
   * Notice: if path is null or empty the root object will be returned.
   * <p>
   * Throws TentackleRuntimeException if value cannot be retrieved
   *
   * @param root the root object the path refers to
   * @param path the member path in dot-notation relative to given object
   * @return the evaluated object
   *
   */
  public static Object getValueByPath(Object root, String path) {

    StringBuilder donePath = new StringBuilder();

    if (path != null) {

      // last char asterisk: no null references allowed
      boolean noNullRefs = path.lastIndexOf('*') == path.length() - 1;

      if (noNullRefs) {
        if (path.length() > 1) {
          path = path.substring(0, path.length() - 1);
        }
        else  {
          return root;
        }
      }

      Object value;
      StringTokenizer stok = new StringTokenizer(path, ".");

      while (stok.hasMoreTokens()) {

        if (root == null) {
          if (noNullRefs) {
            throw new TentackleRuntimeException("reference '" + donePath + "' is null");
          }
          else  {
            return null;
          }
        }

        Class<?> clazz = root.getClass();
        String element = stok.nextToken();
        if (element.endsWith("()")) {
          // cut trailing () as this is a common error if a method is meant
          element = element.substring(0, element.length() - 2);
        }
        if (donePath.length() > 0) {
          donePath.append('.');
        }
        donePath.append(element);

        Method method = getMethodForElement(clazz, element);
        if (method != null && isGetter(method)) {
          try {
            try {
              value = method.invoke(root);
            }
            catch (IllegalAccessException ex) {
              method.setAccessible(true);
              value = method.invoke(root);
            }
          }
          catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new TentackleRuntimeException("cannot retrieve '" + donePath + "' in " +
                                                root.getClass().getName() + " by method " + method.getName(), ex);
          }
        }
        else  {
          Field field = getFieldForElement(clazz, element);
          if (field != null) {
            try {
              try {
                value = field.get(root);
              }
              catch (IllegalAccessException ex) {
                field.setAccessible(true);
                value = field.get(root);
              }
            }
            catch (IllegalAccessException | IllegalArgumentException ex) {
              throw new TentackleRuntimeException("cannot retrieve '" + donePath + "' in " +
                                                  root.getClass().getName() + " by field " + field.getName(), ex);
            }
          }
          else  {
            throw new TentackleRuntimeException("cannot find '" + element + "' in " + clazz.getName());
          }
        }

        if (stok.hasMoreTokens()) {
          if (value == null && noNullRefs) {
            throw new TentackleRuntimeException("path '" + donePath + "' is null");
          }
          else  {
            root = value;
          }
        }
        else  {
          return value;
        }
      }
    }
    else  {
      return root;
    }

    throw new TentackleRuntimeException("no such path '" + donePath + "'");
  }


  /**
   * Gets the static value of an attribute along a member path starting
   * at a class.
   * <p>
   * The attribute is accessible by either a getter-method
   * or a field, which must be public.
   * <p>
   * Example:<br>
   * <pre>
   * "org.tentackle.misc.StringHelper.now"
   * </pre>
   * may correspond to the java path:
   * <pre>
   * SqlHelper.now()
   * </pre>
   *
   * @param clazz the class where the path starts
   * @param path the member path in dot-notation relative to given class
   * @return the object
   */
  public static Object getStaticValueByPath(Class<?> clazz, String path) {

    if (path != null) {

      String element = path;
      int ndx = path.indexOf('.');
      if (ndx > 0) {
        // only the first path member is static!
        element = path.substring(0, ndx);
        path = path.substring(ndx + 1);
      }
      else  {
        path = null;
      }

      Method method = getMethodForElement(clazz, element);
      if (method != null && Modifier.isStatic(method.getModifiers())) {
        try {
          try {
            return getValueByPath(method.invoke(null), path);
          }
          catch (IllegalAccessException ex) {
            method.setAccessible(true);
            return getValueByPath(method.invoke(null), path);
          }
        }
        catch (IllegalAccessException | InvocationTargetException | RuntimeException ex) {
          throw new TentackleRuntimeException("cannot retrieve '" + path + "' in " +
                                              clazz.getName() + " by method " + method.getName(), ex);
        }
      }
    }

    throw new TentackleRuntimeException("path is null");
  }



  /**
   * Checks whether a given path in dot-notation is valid.
   * <p>
   * Same as {@link #getValueByPath(java.lang.Object, java.lang.String)}
   * <p>
   * Note: because the verification is only done according to the declared types
   * the method may return false for a path that properly evaluates via
   * {@link #getValueByPath(java.lang.Object, java.lang.String)}.
   * <p>
   * Furthermore, methods may be static and are not required to be getters.
   * <p>
   * Throws TentackleRuntimeException if value cannot be retrieved.
   *
   * @param clazz the clazz the path refers to
   * @param path the member path in dot-notation relative to given class
   * @return true if path exists
   */
  public static boolean verifyPath(Class<?> clazz, String path) {

    StringBuilder donePath = new StringBuilder();

    if (path != null && path.length() > 0) {

      StringTokenizer stok = new StringTokenizer(path, ".");

      while (stok.hasMoreTokens() && clazz != null) {

        String element = stok.nextToken();
        if (donePath.length() == 0) {
          donePath.append(element);
        }
        else  {
          donePath.append('.');
          donePath.append(element);
        }

        Method method = getMethodForElement(clazz, element);
        if (method != null) {
          clazz = method.getReturnType();
        }
        else  {
          Field field = getFieldForElement(clazz, element);
          if (field != null) {
            clazz = field.getType();
          }
          else  {
            throw new TentackleRuntimeException("cannot find '" + element + "' in " + clazz.getName());
          }
        }

        if (stok.hasMoreTokens()) {
          if (clazz == null) {
            throw new TentackleRuntimeException("path '" + donePath + "' is null");
          }
        }
        else  {
          return true;
        }
      }
    }
    else  {
      return true;
    }

    throw new TentackleRuntimeException("no such path '" + donePath + "'");
  }


  /**
   * Finds the topmost overridden interface method for a given method.
   *
   * @param clazz the interface
   * @param method the method
   * @return the possibly overridden method, else the original method
   */
  public static Method findDeclaredMethod(Class<?> clazz, Method method) {
    try {
      method = clazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
      // if found: return that method
    }
    catch (NoSuchMethodException nme) {
      // try interfaces
      for (Class<?> ifClass: clazz.getInterfaces()) {
        Method m = findDeclaredMethod(ifClass, method);
        if (!m.equals(method)) {
          method = m;
          break;
        }
      }
    }
    return method;
  }



  @SuppressWarnings("unchecked")
  private static Method getMethodForElement(Class<?> clazz, String element) {
    Method method = null;
    String camelName = StringHelper.firstToUpper(element);   // camelcase name part
    try {
      method = clazz.getMethod(element);
    }
    catch (NoSuchMethodException ex1) {
      // try "is"
      try {
        method = clazz.getMethod("is" +  camelName);
      }
      catch (NoSuchMethodException ex2) {
        // try "get"
        try {
          method = clazz.getMethod("get" +  camelName);
        }
        catch (NoSuchMethodException ex3) {
          // return null below
        }
      }
    }
    return method;
  }

  @SuppressWarnings("unchecked")
  private static Field getFieldForElement(Class<?> clazz, String element) {
    Field field = null;
    try {
      field = clazz.getField(element);
    }
    catch (NoSuchFieldException ex1) {
      // return null below
    }
    return field;
  }



  /**
   * prevent instantiation.
   */
  private ReflectionHelper() {}

}
