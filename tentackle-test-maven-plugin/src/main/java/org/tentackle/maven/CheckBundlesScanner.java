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

package org.tentackle.maven;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.TypeTag;
import com.sun.tools.javac.tree.JCTree;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.lang.model.element.Name;


/**
 * Tree path scanner for bundle getString() method invocations.
 *
 * @author harald
 */
public class CheckBundlesScanner extends TreePathScanner<Object, Trees> {

  private final List<BundleKey> bundleKeys = new ArrayList<>();   // found bundle keys

  @Override
  public Object visitMethodInvocation(MethodInvocationTree mit, Trees trees) {
    ExpressionTree methodSelect = mit.getMethodSelect();
    if (methodSelect instanceof JCTree.JCFieldAccess) {
      JCTree.JCFieldAccess fieldAccess = (JCTree.JCFieldAccess) methodSelect;
      Name methodName = fieldAccess.getIdentifier();
      if ("getString".equals(methodName.toString())) {
        // method getString() called
        List<? extends ExpressionTree> methodArgs = mit.getArguments();
        if (methodArgs.size() == 1) {
          ExpressionTree stringExpr = methodArgs.get(0);
          if (stringExpr instanceof JCTree.JCLiteral) {
            JCTree.JCLiteral stringLit = (JCTree.JCLiteral) stringExpr;
            if (stringLit.typetag == TypeTag.CLASS && stringLit.value instanceof String) {
              // method getString(".....") called
              ExpressionTree classExpr = fieldAccess.getExpression();
              String className = classExpr.toString();
              TreePath currentPath = getCurrentPath();
              CompilationUnitTree compilationUnitTree = currentPath.getCompilationUnit();
              ExpressionTree packageTree = compilationUnitTree.getPackageName();
              String packageName = packageTree.toString();

              // check if className is imported from another package
              String bundleName = null;
              String lastName = "." + className;
              for (ImportTree impTree: compilationUnitTree.getImports()) {
                Tree ident = impTree.getQualifiedIdentifier();
                String identName = ident.toString();
                if (identName.endsWith(lastName)) {
                  bundleName = identName;
                  break;    // found!
                }
              }
              if (bundleName == null) {
                // must be in the same package or is an internal reference such as "resources"
                bundleName = packageName + lastName;
              }

              String key = (String) stringLit.value;
              String usingClassName = buildUsingClassName(currentPath);
              bundleKeys.add(new BundleKey(usingClassName, bundleName, key));
            }
          }
        }
      }
    }
    return super.visitMethodInvocation(mit, trees);
  }


  /**
   * Gets the collected bundle keys.
   *
   * @return the bundle keys, never null
   */
  public List<BundleKey> getBundleKeys() {
    return bundleKeys;
  }


  /**
   * Builds the classname that contains the method invocation.
   *
   * @param path the tree path
   * @return the classname
   */
  private String buildUsingClassName(TreePath path) {
    String name = null;
    Iterator<Tree> iter = path.iterator();
    while (iter.hasNext()) {
      Tree tree = iter.next();
      if (tree.getKind() == Tree.Kind.CLASS) {
        String simpleName = ((ClassTree) tree).getSimpleName().toString();
        if (name == null) {
          name = simpleName;
        }
        else {
          name = simpleName + "." + name;
        }
      }
      else if (tree.getKind() == Tree.Kind.COMPILATION_UNIT) {
        name = ((CompilationUnitTree) tree).getPackageName().toString() + "." + name;
      }
    }
    if (name.endsWith(".")) {   // pathological case
      name = name.substring(0, name.length() - 1);
    }
    return name;
  }

}
