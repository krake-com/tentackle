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
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import org.tentackle.buildsupport.BundleAnalyzeHandler;
import org.tentackle.common.Analyze;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * Tree path scanner for bundle getString() method invocations.<br>
 * Collects the invocations of {@code getString} with a single string literal argument that refer to a bundle,
 * i.e. a static method of a bundle class, such as {@code AppBundle.getString("key")}, or a method of a
 * {@link java.util.ResourceBundle}, such as {@code getBundle().getString("key")}.<br>
 * A bundle class is annotated with an annotation that registers bundles, such as {@code @Bundle},
 * i.e. one that is annotated with {@link Analyze} referring to a {@link BundleAnalyzeHandler}.
 * <p>
 * The trees must be attributed, see {@link #scan(JavacTask, Iterable)}. If the invoked method or the type
 * of the reference cannot be determined, the invocation is collected nevertheless and, in addition,
 * listed in {@link #getUnresolvedKeys()}.
 *
 * @author harald
 */
public class CheckBundlesScanner extends TreePathScanner<Object, Trees> {

  private final List<BundleKey> bundleKeys = new ArrayList<>();   // found bundle keys
  private final List<BundleKey> skippedKeys = new ArrayList<>();  // getString invocations not referring to a bundle
  private final List<BundleKey> unresolvedKeys = new ArrayList<>(); // checked keys of unresolved invocations
  private Types types;                                            // type utilities of the current task
  private TypeMirror bundleType;                                  // java.util.ResourceBundle, null if unknown
  private final Map<String, Boolean> bundleHandlers = new HashMap<>(); // analyze handler name : is bundle handler


  /**
   * Scans the attributed compilation units of a task.
   *
   * @param task the task, already analyzed
   * @param units the compilation units of the task
   */
  public void scan(JavacTask task, Iterable<? extends CompilationUnitTree> units) {
    types = task.getTypes();
    TypeElement bundleElement = task.getElements().getTypeElement("java.util.ResourceBundle");
    bundleType = bundleElement == null ? null : bundleElement.asType();
    Trees trees = Trees.instance(task);
    for (CompilationUnitTree unit : units) {
      scan(new TreePath(unit), trees);
    }
  }

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
            BundleKey bundleKey = new BundleKey(usingClassName, bundleName, key);
            switch (resolveInvocation(currentPath, classExpr, trees)) {
              case BUNDLE -> bundleKeys.add(bundleKey);
              case OTHER -> skippedKeys.add(bundleKey);
              case UNRESOLVED -> {
                bundleKeys.add(bundleKey);    // check it anyway
                unresolvedKeys.add(bundleKey);
              }
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
   * Gets the {@code getString} invocations that were ignored because they don't refer to a bundle.
   *
   * @return the ignored keys, never null
   */
  public List<BundleKey> getSkippedKeys() {
    return skippedKeys;
  }


  /**
   * Gets the collected bundle keys whose invocation could not be resolved.<br>
   * Those keys are checked nevertheless, i.e. they are contained in {@link #getBundleKeys()} as well.
   *
   * @return the unresolved keys, never null
   */
  public List<BundleKey> getUnresolvedKeys() {
    return unresolvedKeys;
  }


  /**
   * How a {@code getString} invocation was resolved.
   */
  private enum Resolution {
    /** static method of a bundle class or invoked on a resource bundle. */
    BUNDLE,
    /** invoked on something else. */
    OTHER,
    /** the method or the type of the reference could not be determined. */
    UNRESOLVED
  }


  /**
   * Determines whether a {@code getString} invocation refers to a bundle.
   *
   * @param invocationPath the path to the method invocation
   * @param classExpr the expression the method is invoked on
   * @param trees the trees
   * @return the resolution
   */
  private Resolution resolveInvocation(TreePath invocationPath, ExpressionTree classExpr, Trees trees) {
    Element method = trees.getElement(invocationPath);
    if (method == null || method.asType().getKind() == TypeKind.ERROR || types == null || bundleType == null) {
      return Resolution.UNRESOLVED;
    }
    if (method.getModifiers().contains(Modifier.STATIC)) {
      // a bundle class such as AppBundle.getString("key")
      Element clazz = trees.getElement(new TreePath(invocationPath, classExpr));
      if (clazz == null || !clazz.getKind().isDeclaredType()) {
        clazz = method.getEnclosingElement();
      }
      return resolveBundleClass(clazz);
    }
    TypeMirror type = trees.getTypeMirror(new TreePath(invocationPath, classExpr));
    if (type == null || type.getKind() == TypeKind.ERROR) {
      return Resolution.UNRESOLVED;
    }
    return types.isAssignable(type, bundleType) ? Resolution.BUNDLE : Resolution.OTHER;
  }


  /**
   * Determines whether a class is a bundle class.
   *
   * @param clazz the class providing the static {@code getString} method
   * @return BUNDLE if annotated with a bundle annotation, UNRESOLVED if an annotation is unknown, else OTHER
   */
  private Resolution resolveBundleClass(Element clazz) {
    Resolution resolution = Resolution.OTHER;
    for (AnnotationMirror annotation : clazz.getAnnotationMirrors()) {
      if (annotation.getAnnotationType().getKind() == TypeKind.ERROR) {
        resolution = Resolution.UNRESOLVED;
      }
      else {
        for (AnnotationMirror meta : annotation.getAnnotationType().asElement().getAnnotationMirrors()) {
          if (meta.getAnnotationType().toString().equals(Analyze.class.getName())) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
                 meta.getElementValues().entrySet()) {
              if ("value".contentEquals(entry.getKey().getSimpleName()) &&
                  isBundleHandler(String.valueOf(entry.getValue().getValue()))) {
                return Resolution.BUNDLE;
              }
            }
          }
        }
      }
    }
    return resolution;
  }


  /**
   * Determines whether an analyze handler registers bundles.
   *
   * @param handlerName the classname of the analyze handler
   * @return true if a {@link BundleAnalyzeHandler}
   */
  private boolean isBundleHandler(String handlerName) {
    return bundleHandlers.computeIfAbsent(handlerName, name -> {
      try {
        return BundleAnalyzeHandler.class.isAssignableFrom(
            Class.forName(name, false, BundleAnalyzeHandler.class.getClassLoader()));
      }
      catch (ClassNotFoundException | LinkageError ex) {
        return false;
      }
    });
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
