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

package org.tentackle.buildsupport;

import org.tentackle.common.Settings;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds information gathered by the {@link org.tentackle.common.Analyze}-annotation.
 * <p>
 * The {@link AnalyzeProcessor} creates an info file in an apt run and
 * wurblets pick up this information to generate code.
 *
 * @author harald
 */
public class RemoteMethodInfo {

  /**
   * File-format version.<br>
   * 2.1 escapes the whitespace within type strings, see {@link AnnotationProcessingHelper#escapeToken(String)}.
   */
  public static final String INFO_FILE_VERSION = "2.1";

  /**
   * all remote method info files end with .remote
   */
  public static final String INFO_FILE_EXTENSION = ".remote";

  // currently RemoteMethodInfo supports only the type METHOD, but other types will follow.

  /**
   * Information type for a method.
   */
  public static final String TYPE_METHOD = "method";

  /**
   * Information type for a class.
   */
  public static final String TYPE_CLASS = "class";    // not implemented yet!



  /**
   * Part in a classname.
   */
  private static final String NAME_PART = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*";
  /**
   * Pattern to detect full qualified classnames.
   */
  private static final Pattern FQCN_PATTERN = Pattern.compile(NAME_PART + "\\." + NAME_PART + "(\\." + NAME_PART + ")*");



  /**
   * Reads info from a file.
   *
   * @param infoFile the file to read from
   * @return an RemoteMethodInfo object, null if file is empty or does not contain an RemoteMethodInfo-text
   * @throws IOException if reading failed
   */
  public static RemoteMethodInfo readInfo(File infoFile) throws IOException {
    RemoteMethodInfo info;
    try (LineNumberReader reader = new LineNumberReader(
                                     new InputStreamReader(
                                       new FileInputStream(infoFile), Settings.getEncodingCharset()))) {
      info = readInfo(reader);
    }
    return info;
  }


  /**
   * Reads info from a line reader.
   *
   * @param reader is the LineNumberReader
   * @return an RemoteMethodInfo object, null if file is empty or does not contain an RemoteMethodInfo-text
   * @throws IOException if reading failed
   */
  public static RemoteMethodInfo readInfo(LineNumberReader reader) throws IOException {
    RemoteMethodInfo info = null;
    String line;
    while((line = reader.readLine()) != null) {
      if (!line.startsWith("#")) {    // skip comment lines
        StringTokenizer stok = new StringTokenizer(line, ":");
        if (stok.hasMoreTokens()) {
          String type = stok.nextToken();
          if (info == null) {
            // first line defines the info type
            info = new RemoteMethodInfo(type);
          }
          if (stok.hasMoreTokens()) {
            String text = stok.nextToken();
            stok = new StringTokenizer(text);
            // process according to line type
            if (type.equals(TYPE_METHOD)) {
              info.setMethodName(stok.nextToken());
            }
            else if (type.equals(TYPE_CLASS)) {
              info.setClassName(stok.nextToken());
            }
            else if (type.equals("superclass")) {
              info.setSuperClassName(AnnotationProcessingHelper.unescapeToken(stok.nextToken()));
            }
            else if (type.equals("returns")) {
              info.setReturnType(AnnotationProcessingHelper.unescapeToken(stok.nextToken()));
              String gType = stok.nextToken();
              info.setGenericReturnType(gType.equals(".") ? "" : AnnotationProcessingHelper.unescapeToken(gType));
              info.setReturningDbObject(Boolean.parseBoolean(stok.nextToken()));
              info.setReturningDbObjectCollection(Boolean.parseBoolean(stok.nextToken()));
              info.setReturningPdo(Boolean.parseBoolean(stok.nextToken()));
              info.setReturningPdoCollection(Boolean.parseBoolean(stok.nextToken()));
              info.setReturningPdoCursor(Boolean.parseBoolean(stok.nextToken()));
              info.setReturningCursor(Boolean.parseBoolean(stok.nextToken()));
            }
            else if (type.equals("modifiers")) {
              info.setModifiers(AnnotationProcessingHelper.readModifiers(stok));
            }
            else if (type.equals("varargs")) {
              info.setVarArgsMethod(Boolean.parseBoolean(stok.nextToken()));
            }
            else if (type.startsWith("arg")) {
              info.addParameter(new RemoteMethodInfoParameter(info, stok));
            }
          }
        }
      }
    }

    if (info != null && info.getType().equals(TYPE_METHOD) && info.isVarArgsMethod()) {
      // set vararg in the last parameter
      info.parameterList.getLast().setVarArg(true);
    }

    return info;
  }


  // classnames from property file (we don't use <class>.getName() because we want to keep dependencies small
  private final String nameCollection;
  private final String nameSession;
  private final String nameDbObject;
  private final String nameCursor;
  private final String nameContext;
  private final String namePdo;


  // information gathered from apt run
  private final String type;                      // one of TYPE_...
  private String className;                       // name of the class
  private String superClassName;                  // name of super class
  private String methodName;                      // name of the method
  private String returnType;                      // the methods return type
  private String genericReturnType;               // the (generic) type of the return type
  private boolean returningDbObject;              // true if method returns an AbstractDbObject
  private boolean returningDbObjectCollection;    // true if method returns a collection of AbstractDbObjects
  private boolean returningPdo;                   // true if method returns a PDO
  private boolean returningPdoCollection;         // true if method returns a collection of PDOs
  private boolean returningPdoCursor;             // true if method returns a PDO cursor
  private boolean returningCursor;                // true if method returns a non-PDO cursor
  private List<RemoteMethodInfoParameter> parameterList;          // formal parameters
  private boolean varArgsMethod;                  // true if varargs method
  private Modifier[] modifiers;                   // modifiers like static, public, etc...


  /**
   * Creates an RemoteMethodInfo for a given type.
   * Used when reading from infofile.
   *
   * @param type one of TYPE_...
   * @throws IOException if unsupported TYPE_...
   */
  public RemoteMethodInfo(String type) throws IOException {
    ResourceBundle bundle = ResourceBundle.getBundle("org.tentackle.buildsupport.ClassNames");
    nameCollection = bundle.getString("CLASSNAME_COLLECTION");
    nameSession = bundle.getString("CLASSNAME_SESSION");
    nameDbObject = bundle.getString("CLASSNAME_DBOBJECT");
    nameCursor = bundle.getString("CLASSNAME_CURSOR");
    nameContext = bundle.getString("CLASSNAME_CONTEXT");
    namePdo = bundle.getString("CLASSNAME_PDO");

    if (!type.equals(TYPE_METHOD)) {
      throw new IOException("unsupported type '" + type + "'");
    }
    this.type = type;
  }


  /**
   * Creates an RemoteMethodInfo from a method element.
   * Used during apt processing.
   *
   * @param processingEnv the annotation processor's environment
   * @param methodElement the ExecutableElement to create an info from
   * @throws java.io.IOException if some I/O error occurs
   */
  public RemoteMethodInfo(ProcessingEnvironment processingEnv, ExecutableElement methodElement) throws IOException {
    this(TYPE_METHOD);
    Element classElement = methodElement.getEnclosingElement();
    setClassName(classElement.toString());
    try {
      setSuperClassName(((TypeElement) classElement).getSuperclass().toString());
    }
    catch (RuntimeException ex) {
      setSuperClassName(null);    // no superclass
    }
    setMethodName(methodElement.getSimpleName().toString());
    TypeMirror tm = methodElement.getReturnType();
    setReturnType(tm.toString());
    setGenericReturnType("");
    int ndx1 = getReturnType().indexOf('<');
    if (ndx1 > 0) {
      int ndx2 = getReturnType().indexOf('>', ndx1);
      if (ndx2 > ndx1) {
        setGenericReturnType(getReturnType().substring(ndx1, ndx2 + 1));
      }
    }

    setReturningPdo(AnnotationProcessingHelper.isTypeInstanceOf(processingEnv, tm, namePdo));
    setReturningPdoCollection(AnnotationProcessingHelper.isTypeInstanceOf(processingEnv, tm, nameCollection, namePdo));
    setReturningPdoCursor(AnnotationProcessingHelper.isTypeInstanceOf(processingEnv, tm, nameCursor, namePdo));

    if (isReturningPdo()) {
      setReturningDbObject(true);
    }
    else  {
      setReturningDbObject(AnnotationProcessingHelper.isTypeInstanceOf(processingEnv, tm, nameDbObject));
    }

    if (isReturningPdoCollection()) {
      setReturningDbObjectCollection(true);
    }
    else  {
      setReturningDbObjectCollection(AnnotationProcessingHelper.isTypeInstanceOf(processingEnv, tm, nameCollection, nameDbObject));
    }
    if (isReturningPdoCursor()) {
      setReturningCursor(true);
    }
    else  {
      setReturningCursor(AnnotationProcessingHelper.isTypeInstanceOf(processingEnv, tm, nameCursor));
    }

    Set<Modifier> methodElementModifiers = methodElement.getModifiers();
    setModifiers(methodElementModifiers.toArray(new Modifier[0]));
    varArgsMethod = methodElement.isVarArgs();
    List<? extends VariableElement> varList = methodElement.getParameters();
    for (int i=0; i < varList.size(); i++) {
      RemoteMethodInfoParameter par = new RemoteMethodInfoParameter(this, processingEnv, varList.get(i));
      if (i == varList.size()-1) {
        par.setVarArg(varArgsMethod);
      }
      addParameter(par);
    }
  }

  /**
   * Gets the classname for a collection.
   *
   * @return the classname
   */
  public String getNameCollection() {
    return nameCollection;
  }

  /**
   * Gets the classname for a session.
   *
   * @return the classname
   */
  public String getNameSession() {
    return nameSession;
  }

  /**
   * Gets the classname for a simple db object.
   *
   * @return the classname
   */
  public String getNameDbObject() {
    return nameDbObject;
  }

  /**
   * Gets the classname for a cursor.
   *
   * @return the classname
   */
  public String getNameCursor() {
    return nameCursor;
  }

  /**
   * Gets the classname for a domain context.
   *
   * @return the classname
   */
  public String getNameContext() {
    return nameContext;
  }

  /**
   * Gets the classname for a PDO.
   *
   * @return the classname
   */
  public String getNamePdo() {
    return namePdo;
  }

  /**
   * Gets the type of this analyze info.
   *
   * @return the type
   */
  public String getType() {
    return type;
  }


  /**
   * Gets the name of the class this analyze-info is part of.
   *
   * @return the classname
   */
  public String getClassName() {
    return className;
  }

  /**
   * Sets the classname and the packagename from a given classname.
   *
   * @param className the full class name
   */
  public void setClassName(String className) {
    this.className = className;
  }


  /**
   * Gets the name of the superclass this analyze-info is part of.
   *
   * @return the classname
   */
  public String getSuperClassName() {
    return superClassName;
  }

  /**
   * Sets the classname of the superclass.
   *
   * @param superClassName the full class name
   */
  public void setSuperClassName(String superClassName) {
    this.superClassName = superClassName;
  }


  /**
   * Gets the method name the annotation belongs to.
   *
   * @return the method name
   */
  public String getMethodName() {
    return methodName;
  }

  /**
   * Sets the method name the annotation belongs to.
   *
   * @param methodName the method name
   */
  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }


  /**
   * Gets the return type of the method
   * @return the return type
   */
  public String getReturnType() {
    return returnType;
  }

  /**
   * Sets the return type of the method
   * @param returnType the return type
   */
  public void setReturnType(String returnType) {
    this.returnType = returnType;
  }


  /**
   * Gets the generic return type.<br>
   *
   * @return the generic type, null or empty if none
   */
  public String getGenericReturnType() {
    return genericReturnType;
  }

  /**
   * Sets the generic return type.
   *
   * @param genericReturnType the generic type, null or empty if none
   */
  public void setGenericReturnType(String genericReturnType) {
    this.genericReturnType = genericReturnType;
  }


  /**
   * Gets the formal parameters of the method.
   *
   * @return the array of parameters
   */
  public RemoteMethodInfoParameter[] getParameters() {
    return parameterList == null ? null : parameterList.toArray(new RemoteMethodInfoParameter[0]);
  }

  /**
   * Adds a formal parameter
   * @param parameter the formal parameter to add
   */
  public void addParameter(RemoteMethodInfoParameter parameter) {
    if (parameterList == null) {
      parameterList = new ArrayList<>();
    }
    parameterList.add(parameter);
  }


  /**
   * Returns whether the method has varargs.
   * @return true if varargs method
   */
  public boolean isVarArgsMethod() {
    return varArgsMethod;
  }

  /**
   * Sets whether the method has varargs.
   * @param varArgsMethod true if varargs method
   */
  public void setVarArgsMethod(boolean varArgsMethod) {
    this.varArgsMethod = varArgsMethod;
  }

  /**
   * Gets the modifiers for the method
   * @return the array of modifiers
   */
  public Modifier[] getModifiers() {
    return modifiers;
  }

  /**
   * Gets the method's modifiers as a string
   * @return the modifiers as a string
   */
  public String getModifiersAsString() {
    return AnnotationProcessingHelper.objectArrayToString(modifiers, " ");
  }

  /**
   * Checks whether given modifier is set for the method
   * @param modifier the modifier to test
   * @return true if modifier set
   */
  public boolean isModifierSet(Modifier modifier) {
    return AnnotationProcessingHelper.isModifierSet(modifier, modifiers);
  }

  /**
   * Sets the modifiers for the method
   * @param modifiers for the method
   */
  public void setModifiers(Modifier[] modifiers) {
    this.modifiers = modifiers;
  }

  /**
   * Checks whether the method is returning a low-level db object.
   *
   * @return true if method returns an AbstractDbObject
   */
  public boolean isReturningDbObject() {
    return returningDbObject;
  }

  /**
   * Sets whether the method is returning a low-level db object.
   *
   * @param returningDbObject true if method returns an AbstractDbObject
   */
  public void setReturningDbObject(boolean returningDbObject) {
    this.returningDbObject = returningDbObject;
  }

  /**
   * Checks whether the method is returning a collection of low-level db objects.
   *
   * @return true if method returns a collection of AbstractDbObjects
   */
  public boolean isReturningDbObjectCollection() {
    return returningDbObjectCollection;
  }

  /**
   * Sets whether the method is returning a collection of low-level db objects.
   *
   * @param returningDbObjectCollection true if method returns a collection of AbstractDbObjects
   */
  public void setReturningDbObjectCollection(boolean returningDbObjectCollection) {
    this.returningDbObjectCollection = returningDbObjectCollection;
  }

  /**
   * Checks whether the method is returning a cursor.
   *
   * @return true if method returns a cursor
   */
  public boolean isReturningCursor() {
    return returningCursor;
  }

  /**
   * Sets whether the method is returning a cursor.
   * @param returningCursor true if method returns a cursor
   */
  public void setReturningCursor(boolean returningCursor) {
    this.returningCursor = returningCursor;
  }

  public boolean isReturningPdoCursor() {
    return returningPdoCursor;
  }

  public void setReturningPdoCursor(boolean returningPdoCursor) {
    this.returningPdoCursor = returningPdoCursor;
  }


  /**
   * Checks whether the method is returning a PDO.
   * @return true if method returns a PDO.
   */
  public boolean isReturningPdo() {
    return returningPdo;
  }

  /**
   * Sets whether the method is returning a PDO.
   * @param returningPdo true if method returns a PDO
   */
  public void setReturningPdo(boolean returningPdo) {
    this.returningPdo = returningPdo;
  }

  /**
   * Checks whether the method is returning a collection of PDOs.
   *
   * @return true if method returns a collection of PDOs.
   */
  public boolean isReturningPdoCollection() {
    return returningPdoCollection;
  }

  /**
   * Sets whether the method is returning a collection of PDOs.
   *
   * @param returningPdoCollection true if method returns a collection of PDOs.
   */
  public void setReturningPdoCollection(boolean returningPdoCollection) {
    this.returningPdoCollection = returningPdoCollection;
  }

  /**
   * Gets the declaration string.
   * <p>
   * {@inheritDoc}
   */
  @Override
  public String toString() {
    if (type.equals(TYPE_METHOD)) {
      return getModifiersAsString() + " " + getReturnType() + " " + getMethodName() +
             "(" + AnnotationProcessingHelper.objectArrayToString(getParameters(), ", ") + ")";
    }
    return "";
  }


  /**
   * Writes this object to an info file.
   *
   * @param writer is the PrintWriter object
   * @throws IOException if write failed
   */
  public void write(PrintWriter writer) throws IOException {
    if (type.equals(TYPE_METHOD)) {
      writer.println("# " + getClass().getName() + " Version " + INFO_FILE_VERSION);
      writer.println("# " + this);
      writer.println(TYPE_METHOD + ": " + methodName);
      writer.println(TYPE_CLASS + ": " + className);
      if (superClassName != null) {
        writer.println("superclass: " + AnnotationProcessingHelper.escapeToken(superClassName));
      }
      writer.println("returns: " + AnnotationProcessingHelper.escapeToken(returnType) + " " +
                     (genericReturnType == null || genericReturnType.isEmpty() ?
                      "." : AnnotationProcessingHelper.escapeToken(genericReturnType)) +
                     " " + returningDbObject +
                     " " + returningDbObjectCollection +
                     " " + returningPdo +
                     " " + returningPdoCollection +
                     " " + returningPdoCursor +
                     " " + returningCursor);
      writer.println("modifiers: " + getModifiersAsString());
      writer.println("varargs: " + varArgsMethod);
      if (parameterList != null) {
        int argc = 0;
        for (RemoteMethodInfoParameter par: parameterList) {
          writer.print("arg[" + argc++ + "]: ");
          par.write(writer);
          writer.println();
        }
      }
    }
    else {
      throw new IOException("unsupported info type: " + type);
    }
  }


  /**
   * Simplifies some classnames by removing package names.<br>
   * Makes generated code better readable.
   * Can be used by wurblets.
   *
   * @param type the full type
   * @return the shortened type
   */
  public String cleanTypeString(String type) {
    StringBuilder buf = new StringBuilder();
    Matcher matcher = FQCN_PATTERN.matcher(type);
    int lastNdx = 0;
    while (matcher.find()) {
      buf.append(type, lastNdx, matcher.start());
      String name = type.substring(matcher.start(), matcher.end());
      int dotNdx = name.lastIndexOf('.');   // at least one dot!
      buf.append(name.substring(dotNdx + 1));
      lastNdx = matcher.end();
    }
    buf.append(type.substring(lastNdx));
    return buf.toString();
  }

}
