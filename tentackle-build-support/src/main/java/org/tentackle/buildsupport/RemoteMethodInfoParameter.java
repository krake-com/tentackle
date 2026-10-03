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

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.io.PrintWriter;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Abstraction of a method parameter of a {@link RemoteMethodInfo}.
 */
public class RemoteMethodInfoParameter {

  private final RemoteMethodInfo info;  // the parent info
  private final String name;            // the formal parameter name
  private final String type;            // the parameter's type
  private Modifier[] modifiers;         // modifiers like final, etc...
  private boolean instanceOfSession;    // true if parameter is an instance of a Session
  private boolean instanceOfContext;    // true if parameter is an instance of a DomainContext
  private boolean instanceOfDbObject;   // true if parameter is an instance of an AbstractDbObject
  private boolean instanceOfPdo;        // true if parameter is an instance of a PDO
  private boolean varArg;               // true if this is a (last) vararg



  /**
   * Constructs a RemoteMethodInfoParameter during annotation processing.
   *
   * @param info the parent info
   * @param processingEnv the annotation processing environment
   * @param var the variable
   */
  public RemoteMethodInfoParameter(RemoteMethodInfo info, ProcessingEnvironment processingEnv, VariableElement var) {
    this.info = info;
    TypeMirror tm = var.asType();
    name = var.toString();
    type = tm.toString();
    Set<Modifier> varModifiers = var.getModifiers();
    modifiers = varModifiers.toArray(new Modifier[0]);
    // determine special types
    instanceOfSession = AnnotationProcessingHelper.isTypeInstanceOf(processingEnv, tm, info.getNameSession());
    instanceOfContext = AnnotationProcessingHelper.isTypeInstanceOf(processingEnv, tm, info.getNameContext());
    instanceOfPdo = AnnotationProcessingHelper.isTypeInstanceOf(processingEnv, tm, info.getNamePdo());
    instanceOfDbObject = instanceOfPdo || AnnotationProcessingHelper.isTypeInstanceOf(processingEnv, tm, info.getNameDbObject());
  }

  /**
   * Constructs a RemoteMethodInfoParameter from an info file.
   *
   * @param info the parent info
   * @param stok the tokenizer to read from
   */
  public RemoteMethodInfoParameter(RemoteMethodInfo info, StringTokenizer stok) {
    this.info = info;
    name = stok.nextToken();
    type = AnnotationProcessingHelper.unescapeToken(stok.nextToken());
    instanceOfSession = Boolean.parseBoolean(stok.nextToken());
    instanceOfContext = Boolean.parseBoolean(stok.nextToken());
    instanceOfDbObject = Boolean.parseBoolean(stok.nextToken());
    instanceOfPdo = Boolean.parseBoolean(stok.nextToken());
    modifiers = AnnotationProcessingHelper.readModifiers(stok);
  }

  /**
   * Constructs a RemoteMethodInfoParameter with a name and type only.<br>
   * All flags are false and there are no modifiers.
   *
   * @param info the parent info
   * @param name the parameter name
   * @param type the parameter type
   */
  public RemoteMethodInfoParameter(RemoteMethodInfo info, String name, String type) {
    this.info = info;
    this.name = name;
    this.type = type;
  }

  /**
   * Gets the parent info.
   *
   * @return the parent
   */
  public RemoteMethodInfo getInfo() {
    return info;
  }

  /**
   * Gets the formal parameter name.
   *
   * @return the parameter name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the parameter type.<br>
   * For a vararg, this is the component type.
   *
   * @return the parameter type
   */
  public String getType() {
    return varArg ? type.substring(0, type.length() - 2) : type;    // subtract [] if varargs
  }

  /**
   * Gets the modifiers.
   *
   * @return the array of modifiers, null if none
   */
  public Modifier[] getModifiers() {
    return modifiers;
  }

  /**
   * Returns whether the parameter is an instance of a session.
   *
   * @return true if instanceof Session
   */
  public boolean isInstanceOfSession() {
    return instanceOfSession;
  }

  /**
   * Returns whether the parameter is an instance of a low-level db object.<br>
   * Also true for a PDO.
   *
   * @return true if instanceof AbstractDbObject or PDO
   */
  public boolean isInstanceOfDbObject() {
    return instanceOfDbObject;
  }

  /**
   * Returns whether the parameter is an instance of a PDO.
   *
   * @return true if instanceof PersistentDomainObject
   */
  public boolean isInstanceOfPdo() {
    return instanceOfPdo;
  }

  /**
   * Gets the modifiers as a string.
   *
   * @return the blank-separated modifiers, empty if none
   */
  public String getModifiersAsString() {
    return AnnotationProcessingHelper.objectArrayToString(modifiers, " ");
  }

  /**
   * Returns whether the parameter is an instance of a domain context.
   *
   * @return true if instanceof DomainContext
   */
  public boolean isInstanceOfContext() {
    return instanceOfContext;
  }

  /**
   * Returns whether this is the vararg parameter of a varargs method.
   *
   * @return true if vararg
   */
  public boolean isVarArg() {
    return varArg;
  }

  /**
   * Sets whether this is the vararg parameter of a varargs method.
   *
   * @param varArg true if vararg
   */
  public void setVarArg(boolean varArg) {
    this.varArg = varArg;
  }



  /**
   * Checks if a given modifier is set.
   *
   * @param modifier the modifier to test
   * @return true if modifier set
   */
  public boolean isModifierSet(Modifier modifier) {
    return AnnotationProcessingHelper.isModifierSet(modifier, modifiers);
  }

  /**
   * Writes this parameter to the info file.
   *
   * @param writer the writer bound to the infofile
   */
  public void write(PrintWriter writer) {
    StringBuilder buf = new StringBuilder();
    buf.append(name);
    buf.append(' ');
    buf.append(AnnotationProcessingHelper.escapeToken(type));
    buf.append(' ');
    buf.append(instanceOfSession);
    buf.append(' ');
    buf.append(instanceOfContext);
    buf.append(' ');
    buf.append(instanceOfDbObject);
    buf.append(' ');
    buf.append(instanceOfPdo);
    String modStr = getModifiersAsString();
    if (!modStr.isEmpty()) {
      buf.append(' ');
      buf.append(modStr);
    }
    writer.print(buf);
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(getModifiersAsString());
    if (!buf.isEmpty()) {
      buf.append(' ');
    }
    buf.append(getType());
    if (isVarArg()) {
      buf.append("...");
    }
    buf.append(' ');
    buf.append(name);
    return buf.toString();
  }
}
