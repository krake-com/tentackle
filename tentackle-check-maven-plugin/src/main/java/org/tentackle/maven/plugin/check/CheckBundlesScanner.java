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

package org.tentackle.maven.plugin.check;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Name;
import java.util.ArrayList;
import java.util.List;


/**
 * Tree path scanner for bundle getString() method invocations.<br>
 * Collects every invocation of {@code getString} with a single string literal argument,
 * regardless of the type it is invoked on.
 *
 * @author harald
 */
public class CheckBundlesScanner extends TreePathScanner<Object, Trees> {

  private final List<BundleKey> bundleKeys = new ArrayList<>();   // found bundle keys

  @Override
  public Object visitMethodInvocation(MethodInvocationTree mit, Trees trees) {
    ExpressionTree methodSelect = mit.getMethodSelect();
    if (methodSelect.getKind() == Kind.MEMBER_SELECT) {   // JCTree.JCFieldAccess
      MemberSelectTree fieldAccess = (MemberSelectTree) methodSelect;
      Name methodName = fieldAccess.getIdentifier();
      if ("getString".equals(methodName.toString())) {
        // method getString() called
        List<? extends ExpressionTree> methodArgs = mit.getArguments();
        if (methodArgs.size() == 1) {
          ExpressionTree stringExpr = methodArgs.get(0);
          String key = determineBundleKey(stringExpr);
          if (key != null) {  // method getString(".....") called
            ExpressionTree classExpr = fieldAccess.getExpression();
            String className = classExpr.toString();
            TreePath currentPath = getCurrentPath();
            CompilationUnitTree compilationUnitTree = currentPath.getCompilationUnit();
            ExpressionTree packageTree = compilationUnitTree.getPackageName();
            String packageName = packageTree.toString();

            // check if className is imported from another package
            String bundleName = null;
            String lastName = "." + className;
            for (ImportTree impTree : compilationUnitTree.getImports()) {
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

            String usingClassName = buildUsingClassName(currentPath);
            bundleKeys.add(new BundleKey(usingClassName, bundleName, key));
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
   * Extracts the bundle key from a string expression.
   *
   * @param stringExpr the argument expression of getString(...)
   * @return the bundle key string, null if it could not be determined
   */
  private String determineBundleKey(ExpressionTree stringExpr) {
    String key = null;
    if (stringExpr.getKind() == Kind.STRING_LITERAL) {    // JCTree.JCLiteral
      LiteralTree stringLit = (LiteralTree) stringExpr;
      Object literal = stringLit.getValue();
      if (literal instanceof String) {
        key = (String) literal;
      }
    }
    // notice: there is no generic way to access the value behind a MEMBER_SELECT, for example.
    // However, if such constants are used in table configs, they can be checked by unit tests.
    return key;
  }

  /**
   * Builds the classname that contains the method invocation.
   *
   * @param path the tree path
   * @return the classname, nested classes separated by a dot
   */
  private String buildUsingClassName(TreePath path) {
    StringBuilder name = new StringBuilder();
    for (Tree tree : path) {
      if (tree.getKind() == Kind.CLASS) {
        String simpleName = ((ClassTree) tree).getSimpleName().toString();
        if (name.isEmpty()) {
          name.append(simpleName);
        }
        else {
          name.insert(0, ".");
          name.insert(0, simpleName);
        }
      }
      else if (tree.getKind() == Kind.COMPILATION_UNIT) {
        name.insert(0, ((CompilationUnitTree) tree).getPackageName().toString() + ".");
      }
    }
    if (name.toString().endsWith(".")) {   // pathological case
      name = new StringBuilder(name.substring(0, name.length() - 1));
    }
    return name.toString();
  }

}
