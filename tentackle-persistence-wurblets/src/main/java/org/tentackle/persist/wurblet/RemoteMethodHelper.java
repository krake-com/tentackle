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


package org.tentackle.persist.wurblet;

import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.Modifier;
import org.tentackle.buildsupport.RemoteMethodInfo;
import org.tentackle.buildsupport.RemoteMethodInfoParameter;


/**
 * Extracts the wurblet tags for a method from a classfile.
 *
 * @author harald
 */
public class RemoteMethodHelper {

  private final RemoteMethodInfo info;              // the analyze info
  private final RemoteMethodInfoParameter[] params; // parameters
  private final boolean staticMethod;               // true if method is static
  private final boolean returningVoid;              // true if method returns void
  private final String pdoClassName;                // the PDO classname, null if not a PDO
  private final boolean returningObject;            // truf if method returns a [App]DbObject
  private final boolean returningCollection;        // true if method returns a list of PDOs
  private final boolean returningCursor;            // true if method returns a DbCursor


  /**
   * Loads a class and inspects the method via reflection
   *
   * @param info is the RemoteMethodInfo from the last analyze-run
   * @param pdoClassName if this is a pdo, null if no PDO
   * @param addThis is true if add 'this' to the remote parameters
   */
  public RemoteMethodHelper(RemoteMethodInfo info, String pdoClassName, boolean addThis) {

    this.info = info;
    this.pdoClassName = pdoClassName;

    String classBaseName = info.getClassName().substring(info.getClassName().lastIndexOf('.') + 1);
    String returnType = info.getReturnType();
    returningVoid = returnType.equals("void");
    returningCollection = info.isReturningDbObjectCollection();  // PDO implies this as well
    returningCursor = info.isReturningCursor(); // PDO implies this as well
    returningObject = info.isReturningDbObject(); // PDO implies this as well

    if (addThis) {
      info.addParameter(new RemoteMethodInfoParameter(info, "this", classBaseName));
    }

    // check static method
    staticMethod = info.isModifierSet(Modifier.STATIC);
    params = info.getParameters();
  }




  /**
   * @return  the method name (not the possibly changed identifier)
   */
  public String getMethodName() {
    return info.getMethodName();
  }

  /**
   * @return the return type
   */
  public String getReturnType() {
    return info.cleanTypeString(info.getReturnType());
  }

  /**
   * Gets the generic return type.
   *
   * @return the type, the empty string if no generics
   */
  public String getGenericReturnType() {
    return info.getGenericReturnType().equals("<>") ? "" : info.cleanTypeString(info.getGenericReturnType());
  }

  /**
   * @return true if isPdo methods
   */
  public boolean isPdo() {
    return pdoClassName != null;
  }

  /**
   * @return true if method is returning void
   */
  public boolean isReturningVoid() {
    return returningVoid;
  }

  /**
   * @return true if method is returning an object
   */
  public boolean isReturningObject() {
    return returningObject;
  }

  /**
   * @return true if method is returning a list of db-bjects
   */
  public boolean isReturningCollection() {
    return returningCollection;
  }

  /**
   * @return true if method is returning a DbCursor
   */
  public boolean isReturningCursor() {
    return returningCursor;
  }


  /**
   * @return true if method is static
   */
  public boolean isStaticMethod() {
    return staticMethod;
  }




  /**
   * @return the name of the first parameter
   */
  public String getFirstName() {
    return params != null && params.length > 0 ? params[0].getName() : null;
  }


  /**
   * @return true if first parameter is an instance of Db
   */
  public boolean isFirstInstanceOfDb() {
    return params != null && params.length > 0 && params[0].isInstanceOfSession();
  }

  /**
   * @return true if first parameter is an instance of DomainContext
   */
  public boolean isFirstInstanceOfDomainContext() {
    return params != null && params.length > 0 && params[0].isInstanceOfContext();
  }


  /**
   * @return the number of parameters
   */
  public int getParamCount() {
    return params == null ? 0 : params.length;
  }


  /**
   * @return the invocation parameter string
   */
  public String getInvocationParameterString() {
    StringBuilder buf = new StringBuilder();
    if (params != null) {
      boolean addSep = false;
      for (RemoteMethodInfoParameter par: params) {
        if (!par.isInstanceOfSession()) {
          if (addSep) {
            buf.append(", ");
          }
          boolean isThis = par.getName().equals("this");
          if (isThis && isPdo()) {
            buf.append("me()");
          }
          else {
            buf.append(par.getName());
          }
          addSep = true;
        }
      }
    }
    return buf.toString();
  }



  /**
   * @return the remote invocation parameter string
   */
  public String getRemoteInvocationParameterString() {
    StringBuilder buf = new StringBuilder();
    if (params != null) {
      boolean addSep = false;
      for (RemoteMethodInfoParameter par: params) {
        if (!par.getName().equals("this")) {
          if (addSep) {
            buf.append(", ");
          }
          buf.append(par.getName());
          addSep = true;
        }
      }
    }
    return buf.toString();
  }


  /**
   * @return the invocation parameter string
   */
  public String getDeclarationParameterString() {
    StringBuilder buf = new StringBuilder();
    if (params != null) {
      boolean addSep = false;
      for (RemoteMethodInfoParameter par: params) {
        if (!par.isInstanceOfSession()) {
          if (addSep) {
            buf.append(", ");
          }
          boolean isThis = par.getName().equals("this");
          if (isThis && isPdo()) {
            buf.append(pdoClassName);
          }
          else {
            buf.append(info.cleanTypeString(par.getType()));
            if (par.isVarArg()) {
              buf.append("...");
            }
          }
          buf.append(' ');
          buf.append(isThis ? "obj" : par.getName());
          addSep = true;
        }
      }
    }
    return buf.toString();
  }


  /**
   * @return the code lines to set the db into the parameters
   */
  public List<String> getUpdateDbInParametersStatements() {
    List<String> statements = new ArrayList<>();
    if (params != null) {
      for (RemoteMethodInfoParameter par: params) {
        if (par.isInstanceOfDbObject() ||
            par.isInstanceOfPdo()) {
          statements.add("getSession().applyTo(" + par.getName() + ")");
        }
      }
    }
    return statements;
  }

}
