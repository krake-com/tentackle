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

import org.tentackle.common.StringHelper;
import org.tentackle.common.TentackleRuntimeException;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * Methods related to the reflection API.
 *
 * @author harald
 */
public final class ReflectionHelper {

  private static final Map<Class<?>,Class<?>>  PRIMITIVES = new HashMap<>();
  private static final String PRIMITIVE_NAME_SUFFIX = "#TYPE";
  private static final int PRIMITIVE_NAME_SUFFIX_LENGTH = PRIMITIVE_NAME_SUFFIX.length();
  private static final String CLASS_CLASS_NAME = "@C";
  private static final String OBJECT_CLASS_NAME = "@O";
  private static final String IMMUTABLE_COLLECTIONS = "java.util.ImmutableCollections$";
  private static final String SYNTHETIC_THIS_PARAMETER = "this$0";

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
   * Gets the basename of a classname(-like) string.<br>
   * The basename is the last name of a pathname with dots.
   *
   * @param str the classname
   * @return the basename
   */
  public static String getClassBaseName(String str)  {
    return StringHelper.lastAfter(str, '.');
  }

  /**
   * Gets the serializable classname of a class.<br>
   * Appends "#TYPE" for primitive types to the classname of their non-primitive wrapper class.
   * Replaces "java.lang.Class" with "@C" and "java.lang.Object" with "@O".
   *
   * @see #loadClass(String)
   *
   * @param clazz the class to get the serializable classname for
   * @return the serializable classname, never null
   */
  public static String getSerializableClassName(Class<?> clazz) {
    if (clazz == Class.class) {
      return CLASS_CLASS_NAME;
    }
    if (clazz == Object.class) {
      return OBJECT_CLASS_NAME;
    }
    if (clazz.isPrimitive()) {
      return primitiveToWrapperClass(clazz).getName() + PRIMITIVE_NAME_SUFFIX;
    }
    return clazz.getName();
  }

  /**
   * Same as {@link Class#forName(String)}, but also handles primitive types.<br>
   * Expands "@O" and "@C" to "java.lang.Object" and "java.lang.Class" respectively.
   * This is the counterpart of {@link #getSerializableClassName(Class)}.
   *
   * @param className the classname to load, e.g. "java.lang.String" or "java.lang.Integer#TYPE"
   * @return the loaded class, never null
   * @throws ClassNotFoundException if no such class exists
   */
  public static Class<?> loadClass(String className) throws ClassNotFoundException {
    if (CLASS_CLASS_NAME.equals(className)) {
      return Class.class;
    }
    if (OBJECT_CLASS_NAME.equals(className)) {
      return Object.class;
    }
    boolean primitive = false;
    if (className.endsWith(PRIMITIVE_NAME_SUFFIX)) {
      className = className.substring(0, className.length() - PRIMITIVE_NAME_SUFFIX_LENGTH);
      primitive = true;
    }
    Class<?> clazz = Class.forName(className);
    if (primitive) {
      clazz = wrapperToPrimitiveClass(clazz);
    }
    return clazz;
  }

  /**
   * Returns the absolute path name of the package.<br>
   * Example:
   * <pre>
   *   com.example.myapp.Blah -&gt; "/com/example/myapp"
   * </pre>
   *
   * @param clazz the class
   * @return the package path name, "/" if the class is in the root package
   */
  public static String getPackagePathName(Class<?> clazz) {
    String pkgName = clazz.getPackageName();
    return "/" + pkgName.replace('.', '/');
  }

  /**
   * Gets the package name of a classname(-like) string.<br>
   * Works also for inner classes, provided the class names start with an uppercase letter
   * and the package names don't.
   *
   * @param str the classname
   * @return the package name, "" if no package, str if str already is a package name
   */
  public static String getPackageName(String str) {
    int ndx;
    if (str.isEmpty() || Character.isUpperCase(str.charAt(0))) {
      return "";
    }
    while ((ndx = str.lastIndexOf('.')) >= 0 && ndx < str.length() - 1 && Character.isUpperCase(str.charAt(ndx + 1))) {
      str = str.substring(0, ndx);
    }
    return str;
  }

  /**
   * Gets the outermost class name.<br>
   * Cuts inner classes, lambdas and anonymous classes.
   *
   * @param name the probably inner classname
   * @return the outermost classname
   */
  public static String getOutermostClassName(String name) {
    String pkgName = ReflectionHelper.getPackageName(name);
    if (pkgName.length() < name.length() - 1) {
      String remainder = name.substring(pkgName.length() + (pkgName.isEmpty() ? 0 : 1));
      if (Character.isUpperCase(remainder.charAt(0))) {
        int ndx = remainder.indexOf('.');
        if (ndx >= 0) {
          remainder = remainder.substring(0, ndx);  // cut inner classes
        }
      }
      int ndx = remainder.indexOf('$');
      if (ndx >= 0) {
        remainder = remainder.substring(0, ndx);    // cut lambdas and anonymous classes
      }
      name = pkgName + (pkgName.isEmpty() ? "" : ".") + remainder;
    }
    return name;
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
      sb.append(method.getDeclaringClass().getSimpleName()).append('.');
      sb.append(method.getName()).append('(');
      Class<?>[] params = method.getParameterTypes();
      for (int j = 0; j < params.length; j++) {
        sb.append(params[j].getSimpleName());
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
   * Creates a source-usable string from {@code clazz.getName()}.<br>
   * For example, {@code "[[[B"} will be transformed to {@code "byte[][][]"} and
   * {@code "[Ljava.lang.String;"} to {@code "java.lang.String[]"}.
   * Non-array class names are returned unchanged.
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
      if (type.startsWith("L") && type.endsWith(";")) {
        // object: Lclassname;
        type = type.substring(1, type.length() - 1);
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
   * <p>
   * Notice: if the clazz or the superClasses is null, true will be returned.
   *
   * @param clazz the class to test
   * @param superClasses the interfaces or classes
   * @param all true if all must match (logically and), false if at least one (logically or)
   * @return true if match
   */
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
   * Tests if the given class has a set of annotations.
   * <p>
   * Notice: if the clazz or the annotations is null, true will be returned.
   *
   * @param clazz the class to test
   * @param annotations the annotations
   * @param all true if all must match (logically and), false if at least one (logically or)
   * @return true if match
   */
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
   * Extracts the inner class from a generic type.<br>
   * The type must be a parameterized generic type (usually determined by the binding)
   * for a single type argument.<br>
   * Works for wildcard types as well.<br>
   * Examples:
   * <pre>
   *   &#64;Bindable List&lt;OrgUnit&gt;
   *   &#64;Bindable List&lt;OrgUnit&lt;?&gt;&gt;
   *   &#64;Bindable List&lt;? extends OrgUnit&gt;
   *   &#64;Bindable List&lt;? extends OrgUnit&lt;?&gt;&gt;
   * </pre>
   * Will return <code>OrgUnit.class</code>.
   *
   * @param type the type
   * @param <T> the inner type
   * @return the class, null if the type is not describing a class or is not parameterized
   */
  @SuppressWarnings("unchecked")
  public static <T> Class<T> extractGenericInnerTypeClass(Type type) {
    Class<T> clazz = null;
    if (type instanceof ParameterizedType parameterizedType) {
      Type[] typeArguments = parameterizedType.getActualTypeArguments();
      if (typeArguments.length == 1) {
        Type typeArgument = typeArguments[0];
        if (typeArgument instanceof Class) {
          clazz = (Class<T>) typeArgument;
        }
        else if (typeArgument instanceof WildcardType wildcardType) {
          typeArguments = wildcardType.getLowerBounds();
          if (typeArguments.length != 1) {
            typeArguments = wildcardType.getUpperBounds();
          }
          if (typeArguments.length == 1) {
            typeArgument = typeArguments[0];
            if (typeArgument instanceof Class) {
              clazz = (Class<T>) typeArgument;
            }
            else if (typeArgument instanceof ParameterizedType parTypeArg) {
              Type rawType = parTypeArg.getRawType();
              if (rawType instanceof Class) {
                clazz = (Class<T>) rawType;
              }
            }
          }
        }
        else if (typeArgument instanceof ParameterizedType parTypeArg) {
          Type rawType = parTypeArg.getRawType();
          if (rawType instanceof Class) {
            clazz = (Class<T>) rawType;     // something like OrgUnit<?>
          }
        }
      }
    }
    return clazz;
  }

  /**
   * Gets all declared fields of the given class and its superclasses.<br>
   * The superclasses are scanned as long as they match the bounding classes and annotations.
   *
   * @param clazz the class to get all declared and inherited fields
   * @param boundingClasses bounding classes that all classes must implement or extend, null if all
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
   * Gets all declared methods of the given class and its superclasses.<br>
   * The superclasses are scanned as long as they match the bounding classes and annotations.
   * For interfaces, all public methods including the inherited ones are returned.
   *
   * @param clazz the class to get all declared and inherited methods
   * @param boundingClasses bounding classes that all classes must implement or extend, null if all
   * @param boundToAll true if all boundingClasses must match (logically and), false if at least one (logically or)
   * @param annotations the class annotations, null if none
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
   * Returns whether a class is a non-static inner class.
   *
   * @param clazz the class to check
   * @return true if the class is a non-static inner class
   */
  public static boolean isNonStaticInnerClass(Class<?> clazz) {
    return clazz.getEnclosingClass() != null && clazz.isMemberClass() &&
           !Modifier.isStatic(clazz.getModifiers());
  }

  /**
   * Returns whether a class is an immutable list, set or map of the JDK.
   *
   * @param clazz the class to check
   * @return true if the class is an immutable list, set or map
   * @see List#of()
   * @see Set#of()
   * @see Map#of()
   */
  public static boolean isImmutableCollection(Class<?> clazz) {
    return clazz.getName().startsWith(IMMUTABLE_COLLECTIONS);
  }

  /**
   * Determines the name a class is mapped by.<br>
   * Member classes must keep the {@code $xxx} of their binary name to stay loadable by
   * {@code Class.forName()}. Arrays are named by their component class, followed by the
   * array suffix, since an array is neither a member class nor does it have an enclosing
   * class, no matter what its component class is.
   *
   * @param clazz the class
   * @return the name to map, never null
   */
  public static String getMappingName(Class<?> clazz) {
    int dimensions = 0;
    Class<?> componentClass = clazz;
    while (componentClass.isArray()) {
      componentClass = componentClass.getComponentType();
      dimensions++;
    }
    String className;
    if (componentClass.getEnclosingClass() != null && componentClass.isMemberClass()) {
      className = componentClass.getName();
    }
    else {
      className = componentClass.getCanonicalName();
      if (className == null) {
        // local class (or anonymous or hidden -> useless but better than null name)
        className = componentClass.getName();
      }
    }
    return dimensions == 0 ? className : className + "[]".repeat(dimensions);
  }

  /**
   * Gets the canonical constructor of a record class or of a regular class
   * with its constructor annotated with {@link CanonicalConstructor}.
   * <p>
   * Since records always provide a canonical constructor, this method will never
   * return null for record classes.
   *
   * @param clazz the class
   * @param <T> the class type
   * @return the canonical constructor, null for none
   * @throws TentackleRuntimeException if the class is not a record and
   *         more than one constructor is annotated with {@link CanonicalConstructor}
   */
  @SuppressWarnings("unchecked")
  public static <T> Constructor<T> getCanonicalConstructor(Class<T> clazz) {
    if (clazz.isRecord()) {
      RecordComponent[] recordComponents = clazz.getRecordComponents();
      Class<?>[] parameterTypes = new Class<?>[recordComponents.length];
      int ndx = 0;
      for (RecordComponent recordComponent : recordComponents) {
        parameterTypes[ndx++] = recordComponent.getType();
      }
      for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
        if (Arrays.equals(constructor.getParameterTypes(), parameterTypes)) {
          return (Constructor<T>) constructor;
        }
      }
      // cannot happen, but one never knows...
      throw new TentackleRuntimeException("no canonical constructor for record " + clazz.getName());
    }
    else {
      Constructor<T> canonicalConstructor = null;
      for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
        if (constructor.isAnnotationPresent(CanonicalConstructor.class)) {
          if (canonicalConstructor != null) {
            throw new TentackleRuntimeException("multiple constructors annotated with @CanonicalConstructor in " + clazz);
          }
          canonicalConstructor = (Constructor<T>) constructor;
        }
      }
      return canonicalConstructor;
    }
  }

  /**
   * The canonical builder info.
   *
   * @param constructor the builder constructor
   * @param methods the builder's methods to set the attributes of the object to build
   * @param buildMethod the method to build the object from the attributes set in the builder
   */
  public record CanonicalBuilderInfo(Constructor<?> constructor,
                                     Map<String, Method> methods, Method buildMethod) {
    /**
     * Gets a builder method by name.
     *
     * @param name the name of the method
     * @return the method, null for none
     */
    public Method getMethod(String name) {
      return methods.get(name);
    }
  }

  /**
   * Gets the canonical builder info of a class
   * with the class or one of its constructors annotated with {@link CanonicalBuilder}.
   *
   * @param clazz the class
   * @return the builder info, null for none
   * @throws TentackleRuntimeException if there is more than one {@link CanonicalBuilder} annotation,
   *         or the builder provides no no-args constructor
   */
  public static CanonicalBuilderInfo getCanonicalBuilderInfo(Class<?> clazz) {
    CanonicalBuilder annotation = clazz.getAnnotation(CanonicalBuilder.class);
    for (Constructor<?> constructor : clazz.getDeclaredConstructors()) {
      CanonicalBuilder constructorAnnotation = constructor.getAnnotation(CanonicalBuilder.class);
      if (constructorAnnotation != null) {
        if (annotation != null) {
          throw new TentackleRuntimeException("multiple @CanonicalBuilder annotations in " + clazz);
        }
        annotation = constructorAnnotation;
      }
    }
    if (annotation != null) {
      Class<?> builderClass = annotation.value();
      Constructor<?> builderConstructor;
      try {
        builderConstructor = builderClass.getDeclaredConstructor();
      }
      catch (NoSuchMethodException e) {
        throw new TentackleRuntimeException("no default constructor in builder " + builderClass);
      }
      Map<String, Method> methods = new HashMap<>();
      Method buildMethod = null;
      for (Method method : builderClass.getMethods()) {
        if (!Modifier.isStatic(method.getModifiers())) {
          if (method.getReturnType().equals(clazz) && method.getParameterCount() == 0) {
            buildMethod = method;
          }
          else if (method.getParameterCount() == 1 &&
                   method.getReturnType() != Object.class && method.getReturnType().isAssignableFrom(builderClass)) {
            methods.put(method.getName(), method);
          }
        }
      }
      return new CanonicalBuilderInfo(builderConstructor, methods, buildMethod);
    }
    return null;
  }

  /**
   * Gets the parameter names of a constructor.<br>
   * Each parameter must be annotated with {@link CanonicalName} or
   * the code was compiled with the {@code -parameters} option.
   * If neither annotation is present nor the {@code -parameters} option was used,
   * the names are {@code null}.
   *
   * @param constructor the constructor to get the parameter names for
   * @return the parameter names, empty array for no parameters
   */
  public static String[] getCanonicalNames(Constructor<?> constructor) {
    Annotation[][] parameterAnnotations = constructor.getParameterAnnotations();
    String[] canonicalNames = new String[parameterAnnotations.length];
    for (int i = 0; i < parameterAnnotations.length; i++) {
      Annotation[] annotations = parameterAnnotations[i];
      for (Annotation annotation : annotations) {
        if (annotation.annotationType().equals(CanonicalName.class)) {
          canonicalNames[i] = ((CanonicalName) annotation).value();
          break;
        }
      }
    }
    if (isNonStaticInnerClass(constructor.getDeclaringClass())) {
      canonicalNames[0] = SYNTHETIC_THIS_PARAMETER;
    }
    Parameter[] parameters = constructor.getParameters();
    if (parameters.length == canonicalNames.length) {   // must be the same, but one never knows...
      for (int i = 0; i < canonicalNames.length; i++) {
        if (canonicalNames[i] == null) {
          String name = parameters[i].getName();
          if (!(name.startsWith("arg") && name.length() > 3 && Character.isDigit(name.charAt(3)))) {   // argN if no -parameters
            canonicalNames[i] = parameters[i].getName();
          }
        }
      }
    }
    return canonicalNames;
  }

  /**
   * Gets the parameter names of a method.<br>
   * Each parameter must be annotated with {@link CanonicalName} or
   * the code was compiled with the {@code -parameters} option.
   * If neither annotation is present nor the {@code -parameters} option was used,
   * the names are {@code null}.
   *
   * @param method the method to get the parameter names for
   * @return the parameter names, empty array for no parameters
   */
  public static String[] getCanonicalNames(Method method) {
    Annotation[][] parameterAnnotations = method.getParameterAnnotations();
    String[] canonicalNames = new String[parameterAnnotations.length];
    for (int i = 0; i < parameterAnnotations.length; i++) {
      Annotation[] annotations = parameterAnnotations[i];
      for (Annotation annotation : annotations) {
        if (annotation.annotationType().equals(CanonicalName.class)) {
          canonicalNames[i] = ((CanonicalName) annotation).value();
          break;
        }
      }
    }
    Parameter[] parameters = method.getParameters();
    if (parameters.length == canonicalNames.length) {   // must be the same, but one never knows...
      for (int i = 0; i < canonicalNames.length; i++) {
        if (canonicalNames[i] == null) {
          String name = parameters[i].getName();
          if (!(name.startsWith("arg") && name.length() > 3 && Character.isDigit(name.charAt(3)))) {   // argN if no -parameters
            canonicalNames[i] = parameters[i].getName();
          }
        }
      }
    }
    return canonicalNames;
  }

  /**
   * Gets all canonical members of a class.<br>
   * Canonical members are fields and getters annotated with {@link CanonicalField} or {@link CanonicalGetter}.
   * <p>
   * For non-static inner classes, the field "this$0" will be included automatically.
   *
   * @param clazz the class
   * @return the canonical members ordered by their ordinal, empty if none
   */
  public static Member[] getCanonicalMembers(Class<?> clazz) {
    boolean addThis = isNonStaticInnerClass(clazz);
    Map<Integer, Member> canonicalMembers = new TreeMap<>();
    Field[] fields = clazz.getDeclaredFields();
    if (addThis) {
      for (Field field : fields) {
        if (field.isSynthetic() && field.getName().equals(SYNTHETIC_THIS_PARAMETER)) {
          canonicalMembers.put(0, field);
          break;
        }
      }
      if (canonicalMembers.isEmpty()) {
        throw new TentackleRuntimeException("no this$0 found for non-static inner class " + clazz);
      }
    }
    for (Field field : fields) {
      CanonicalField annotation = field.getAnnotation(CanonicalField.class);
      if (annotation != null) {
        int position = annotation.ordinal();
        if (addThis) {
          position++;
        }
        if (canonicalMembers.put(position, field) != null) {
          throw new TentackleRuntimeException("duplicate canonical field ordinal " + annotation.ordinal() +
                                              " in " + clazz);
        }
      }
    }
    for (Method method : clazz.getMethods()) {
      CanonicalGetter annotation = method.getAnnotation(CanonicalGetter.class);
      if (annotation != null) {
        if (!isGetter(method)) {
          throw new TentackleRuntimeException("method is not a getter: " + method);
        }
        int position = annotation.ordinal();
        if (addThis) {
          position++;
        }
        if (canonicalMembers.put(position, method) != null) {
          throw new TentackleRuntimeException("duplicate canonical getter ordinal " + annotation.ordinal() +
                                              " in " + clazz);
        }
      }
    }
    // the concrete position value is irrelevant, only the order matters
    return canonicalMembers.values().toArray(new Member[0]);
  }

  /**
   * Returns the name of the corresponding getter for a field.
   *
   * @param clazz the field's type
   * @param fieldName the field's name
   * @return the getter name, e.g. "getBlah" or "isBlah" for a primitive boolean field
   */
  public static String getGetterName(Class<?> clazz, String fieldName) {
    return (clazz == Boolean.TYPE ? "is" : "get") +
           StringHelper.firstToUpper(fieldName);
  }

  /**
   * Returns the name of the corresponding getter for a field.
   *
   * @param field the field
   * @return the getter name, e.g. "getBlah" or "isBlah" for a primitive boolean field
   */
  public static String getGetterName(Field field) {
    return getGetterName(field.getType(), field.getName());
  }

  /**
   * Gets the field name from a clazz and method name.<br>
   * Skips the prefix "get" or "is", if any.
   *
   * @param clazz the return type of the method (currently unused)
   * @param methodName the method name
   * @return the field name
   */
  public static String getFieldName(Class<?> clazz, String methodName) {
    String fieldName = skipMethodNamePrefix(methodName, "get");
    if (fieldName == null) {
      fieldName = skipMethodNamePrefix(methodName, "is");
    }
    return fieldName == null ? methodName : fieldName;
  }

  /**
   * Gets the field name from a method.
   *
   * @param method the method
   * @return the field name
   */
  public static String getFieldName(Method method) {
    return getFieldName(method.getReturnType(), method.getName());
  }

  /**
   * Returns the getter for a field.
   *
   * @param field the field
   * @return the getter, null if the field does not have a corresponding getter
   */
  public static Method getGetter(Field field) {
    try {
      Method method = field.getDeclaringClass().getMethod(getGetterName(field.getType(), field.getName()));
      if (method.getReturnType() == field.getType() && method.getParameterTypes().length == 0) {
        return method;
      }
      return null;
    }
    catch (NoSuchMethodException e) {
      return null;
    }
  }

  /**
   * Returns the setter for a field.
   *
   * @param field the field
   * @return the setter, null if the field does not have a corresponding setter
   */
  public static Method getSetter(Field field) {
    String methodName = "set" + StringHelper.firstToUpper(field.getName());
    try {
      Method method = field.getDeclaringClass().getMethod(methodName, field.getType());
      if (method.getReturnType() == Void.TYPE && method.getParameterTypes().length == 1 &&
          field.getType() == method.getParameterTypes()[0]) {
        return method;
      }
      return null;
    }
    catch (NoSuchMethodException e) {
      return null;
    }
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
   *
   * @param method the method
   * @return true if the method is a setter
   */
  public static boolean isSetter(Method method) {
    return method.getReturnType() == Void.TYPE &&
           !Modifier.isStatic(method.getModifiers()) &&
           method.getParameterTypes().length == 1;
  }

  /**
   * Checks whether a method is a function that accepts one argument and produces a result.
   *
   * @param method the method
   * @return true if the method is a function
   */
  public static boolean isFunction(Method method) {
    return method.getReturnType() != Void.TYPE &&
           !Modifier.isStatic(method.getModifiers()) &&
           method.getParameterTypes().length == 1;
  }

  /**
   * Skips the prefix of a method name.<br>
   * The first letter after the prefix must be in uppercase.<br>
   * Used to skip well-known prefixes like "set", "get", "is", etc...
   * <p>
   * Example:
   * <pre>
   *   skipMethodNamePrefix("getBlah", "get") -&gt; "blah"
   * </pre>
   *
   * @param methodName the method name
   * @param prefix the prefix to skip
   * @return the method name without the prefix, null if the method does not start with prefix or letter after
   * prefix is not in uppercase
   */
  public static String skipMethodNamePrefix(String methodName, String prefix) {
    int prefixLength = prefix.length();
    if (methodName.length() > prefixLength && methodName.startsWith(prefix) &&
        Character.isUpperCase(methodName.charAt(prefixLength))) {
      return StringHelper.firstToLower(methodName.substring(prefixLength));
    }
    return null;
  }

  /**
   * Gets the method name reduced to the part used in paths if the method is an attribute getter.
   * <p>
   * Examples:<br>
   * <pre>
   * getBlah -&gt; blah
   * isValid -&gt; valid
   * computeThisAndThat -&gt; computeThisAndThat
   * gettingWorse -&gt; gettingWorse
   * isselDussel -&gt; isselDussel
   * </pre>
   *
   * @param method the method
   * @return the method's reduced name, null if this is not an attribute getter
   */
  public static String getGetterPathName(Method method) {
    if (isGetter(method)) {
      String methodName = method.getName();
      String elementName = skipMethodNamePrefix(methodName, "get");
      if (elementName == null) {
        elementName = skipMethodNamePrefix(methodName, "is");
        if (elementName == null) {
          elementName = methodName;
        }
      }
      return elementName;
    }
    return null;
  }

  /**
   * Gets the method name reduced to the part used in paths if the method is an attribute setter.
   * <p>
   * Examples:<br>
   * <pre>
   * setBlah -&gt; blah
   * runIt -&gt; runIt
   * </pre>
   *
   * @param method the method
   * @return the method's reduced name, null if this is not an attribute setter
   */
  public static String getSetterPathName(Method method) {
    if (isSetter(method)) {
      String methodName = method.getName();
      String elementName = skipMethodNamePrefix(methodName, "set");
      if (elementName == null) {
        elementName = methodName;
      }
      return elementName;
    }
    return null;
  }

  /**
   * Gets the method name reduced to the part used in binding or validation paths, if the method is a "from"- or "with"-method.
   * <p>
   * Examples:<br>
   * <pre>
   * fromBlah -&gt; blah
   * withBlah -&gt; blah
   * </pre>
   *
   * @param method the method
   * @return the method's reduced name, null if this is not a from- or with-method
   */
  public static String getFromPathName(Method method) {
    if (isFunction(method) && !method.getReturnType().isPrimitive()) {    // must return an (immutable) object
      String methodName = method.getName();
      String elementName = skipMethodNamePrefix(methodName, "from");
      if (elementName == null) {
        elementName = skipMethodNamePrefix(methodName, "with");
        if (elementName == null) {
          elementName = methodName;
        }
      }
      return elementName;
    }
    return null;
  }

  /**
   * Gets the value of an attribute along a member path.
   * <p>
   * The attribute is accessible by either a getter-method
   * or a field, which must be public. Each path element is resolved by a method named like the element,
   * then with an "is" or "get" prefix, and finally by a field. A trailing {@code "()"} is ignored.
   * If the declaring class is not accessible, {@code setAccessible(true)} is tried,
   * which requires the package to be opened to {@code org.tentackle.core}.
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
   * This behavior can be changed by appending an asterisk, i.e.
   * <code>"invoice.invoiceLine.index*"</code> will throw a TentackleRuntimeException
   * if invoice or invoiceLine is null.
   *
   * <p>
   * Notice: if {@code path} is null or empty the root object will be returned.
   *
   * @param root the root object the path refers to
   * @param path the member path in dot-notation relative to the given object
   * @return the evaluated object
   * @throws TentackleRuntimeException if the value cannot be retrieved
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
        if (!donePath.isEmpty()) {
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
          catch (InaccessibleObjectException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
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
            catch (InaccessibleObjectException | IllegalAccessException | IllegalArgumentException ex) {
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
   * Only the first element of the path is static. It is resolved by a public static method
   * (named like the element, or with an "is" or "get" prefix) or a public static field.
   * The rest of the path is evaluated against the returned object by {@link #getValueByPath(Object, String)}.
   * <p>
   * Example:<br>
   * <pre>
   * DateHelper.class, "now"
   * </pre>
   * corresponds to the java path:
   * <pre>
   * DateHelper.now()
   * </pre>
   *
   * @param clazz the class where the path starts
   * @param path the member path in dot-notation relative to the given class
   * @return the object
   * @throws TentackleRuntimeException if the path is null, there is no such static member, or the value cannot be retrieved
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

      Field field = getFieldForElement(clazz, element);
      if (field != null && Modifier.isStatic(field.getModifiers())) {
        try {
          try {
            return getValueByPath(field.get(null), path);
          }
          catch (IllegalAccessException ex) {
            field.setAccessible(true);
            return getValueByPath(field.get(null), path);
          }
        }
        catch (IllegalAccessException | RuntimeException ex) {
          throw new TentackleRuntimeException("cannot retrieve '" + path + "' in " +
                                              clazz.getName() + " by field " + field.getName(), ex);
        }
      }

      throw new TentackleRuntimeException("no static method or field '" + element + "' in " + clazz.getName());
    }

    throw new TentackleRuntimeException("path is null");
  }

  /**
   * Finds the declared interface for a given class.<br>
   * Checks the interfaces of the class and its superclasses
   * until it finds a declared interface that is of the given interface type
   * or a subtype of.
   *
   * @param clazz the class
   * @param superIface the declared interface
   * @param <I> the type of the declared interface
   * @return the declared interface, null if not found
   * @see #findAllInterfaces(Class, Class)
   */
  @SuppressWarnings("unchecked")
  public static <I> Class<? extends I> findInterface(Class<?> clazz, Class<I> superIface) {
    while (clazz != null) {
      Class<?>[] ifaces = clazz.getInterfaces();
      for (Class<?> iface : ifaces) {
        if (superIface.isAssignableFrom(iface)) {
          return (Class<? extends I>) iface;
        }
      }
      clazz = clazz.getSuperclass();
    }
    return null;
  }

  /**
   * Finds all declared interfaces for a given class.<br>
   * Same as {@link #findInterface(Class, Class)}, but collects all such interfaces.
   *
   * @param clazz the class
   * @param superIface the declared interface
   * @param <I> the type of the declared interface
   * @return the interfaces, never null but possibly empty
   */
  @SuppressWarnings("unchecked")
  public static <I> Collection<Class<? extends I>> findAllInterfaces(Class<?> clazz, Class<I> superIface) {
    Collection<Class<? extends I>> result = new ArrayList<>();
    while (clazz != null) {
      Class<?>[] ifaces = clazz.getInterfaces();
      for (Class<?> iface : ifaces) {
        if (superIface.isAssignableFrom(iface)) {
          result.add((Class<? extends I>) iface);
        }
      }
      clazz = clazz.getSuperclass();
    }
    return result;
  }


  /**
   * Creates a proxy class by the given interfaces.<br>
   * The method exists since {@code Proxy#getProxyClass()} is deprecated.
   *
   * @param interfaces the proxy interfaces
   * @return the proxy class
   * @param <T> the type of the proxy class
   */
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static <T> Class<T> createProxyClass(Class<?>[] interfaces) {
    if (interfaces == null || interfaces.length == 0) {
      throw new TentackleRuntimeException("no interfaces given");
    }
    return (Class<T>) Proxy.newProxyInstance(interfaces[0].getClassLoader(), interfaces, FAKE_INVOCATION_HANDLER).getClass();
  }

  private static class FakeInvocationHandler implements InvocationHandler {
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      return null;
    }
  }

  private static final InvocationHandler FAKE_INVOCATION_HANDLER = new FakeInvocationHandler();


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
