/**
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


package org.tentackle.buildsupport;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.StringTokenizer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Abstraction of a method parameter.
 */
public class RemoteMethodInfoParameter {

  private final String name;            // the formal parameter name
  private final String type;            // the parameter's type
  private Modifier[] modifiers;         // modifiers like final, etc...
  private boolean instanceOfSession;    // true if paramater is an instance of a Session
  private boolean instanceOfContext;    // true if parameter is an instance of a DomainContext
  private boolean instanceOfDbObject;   // true if parameter is an instance of an AbstractDbObject
  private boolean instanceOfPdo;        // true if parameter is an instance of a PDO
  private boolean varArg;               // true if this is a (last) vararg



  /**
   * Constructs a RemoteMethodInfoParameter.
   *
   * @param processingEnv the annotation processing environment
   * @param var the variable
   */
  public RemoteMethodInfoParameter(ProcessingEnvironment processingEnv, VariableElement var) {
    TypeMirror tm = var.asType();
    name = var.toString();
    type = tm.toString();
    modifiers = var.getModifiers().toArray(new Modifier[0]);
    // determine special types
    instanceOfSession = AnnotationProcessingHelper.isTypeInstanceoOf(processingEnv, tm, RemoteMethodInfo.CLASSNAME_SESSION);
    instanceOfContext = AnnotationProcessingHelper.isTypeInstanceoOf(processingEnv, tm, RemoteMethodInfo.CLASSNAME_CONTEXT);
    instanceOfPdo = AnnotationProcessingHelper.isTypeInstanceoOf(processingEnv, tm, RemoteMethodInfo.CLASSNAME_PDO);
    if (instanceOfPdo) {
      instanceOfDbObject = true;
    }
    else {
      instanceOfDbObject = AnnotationProcessingHelper.isTypeInstanceoOf(processingEnv, tm, RemoteMethodInfo.CLASSNAME_DBOBJECT);
    }
  }

  public RemoteMethodInfoParameter(StringTokenizer stok) {
    name = stok.nextToken();
    type = stok.nextToken();
    instanceOfSession = Boolean.valueOf(stok.nextToken());
    instanceOfContext = Boolean.valueOf(stok.nextToken());
    instanceOfDbObject = Boolean.valueOf(stok.nextToken());
    instanceOfPdo = Boolean.valueOf(stok.nextToken());
    modifiers = AnnotationProcessingHelper.readModifiers(stok);
  }

  public RemoteMethodInfoParameter(String name, String type) {
    this.name = name;
    this.type = type;
  }

  /**
   * Gets the formal parameter name
   * @return the parameter name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the parameter type
   * @return the parameter type
   */
  public String getType() {
    return varArg ? type.substring(0, type.length() - 2) : type;    // subtract [] if varargs
  }

  /**
   * Gets the modifiers
   * @return the array of modifiers
   */
  public Modifier[] getModifiers() {
    return modifiers;
  }

  /**
   * Returns whether the parameter is an instance of a {@link org.tentackle.persist.Db}.
   * @return true if instanceof Db
   */
  public boolean isInstanceOfSession() {
    return instanceOfSession;
  }

  public boolean isInstanceOfDbObject() {
    return instanceOfDbObject;
  }

  public boolean isInstanceOfPdo() {
    return instanceOfPdo;
  }

  public String getModifiersAsString() {
    return AnnotationProcessingHelper.objectArrayToString(modifiers, " ");
  }

  public boolean isInstanceOfContext() {
    return instanceOfContext;
  }

  public boolean isVarArg() {
    return varArg;
  }

  public void setVarArg(boolean varArg) {
    this.varArg = varArg;
  }



  /**
   * Checks if a given modifier is set.
   *
   * @param modifier the modifier to test
   * @return true modifier set
   */
  public boolean isModifierSet(Modifier modifier) {
    return AnnotationProcessingHelper.isModifierSet(modifier, modifiers);
  }

  /**
   * Writes this parameter to the infofile.
   *
   * @param writer the writer bound to the infofile
   * @throws java.io.IOException
   */
  public void write(PrintWriter writer) throws IOException {
    StringBuilder buf = new StringBuilder();
    buf.append(name);
    buf.append(' ');
    buf.append(type);
    buf.append(' ');
    buf.append(Boolean.toString(instanceOfSession));
    buf.append(' ');
    buf.append(Boolean.toString(instanceOfContext));
    buf.append(' ');
    buf.append(Boolean.toString(instanceOfDbObject));
    buf.append(' ');
    buf.append(Boolean.toString(instanceOfPdo));
    String modStr = getModifiersAsString();
    if (modStr.length() > 0) {
      buf.append(' ');
      buf.append(modStr);
    }
    writer.print(buf.toString());
  }

  @Override
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append(getModifiersAsString());
    if (buf.length() > 0) {
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
