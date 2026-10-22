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

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LineMap;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import org.tentackle.buildsupport.BundleAnalyzeHandler;
import org.tentackle.common.Analyze;
import org.tentackle.common.Constants;
import org.tentackle.common.NoBundle;
import org.tentackle.reflect.ReflectionHelper;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tree path scanner for bundle getString() method invocations.<br>
 * Collects the invocations of {@code getString} with a single argument that refer to a bundle,
 * i.e. a static method of a bundle class, such as {@code AppBundle.getString("key")}, or a method of a
 * {@link java.util.ResourceBundle}, such as {@code getBundle().getString("key")}.<br>
 * The key must be a compile-time constant expression of string literals and constant variables,
 * such as {@code "key"}, {@code KEY}, {@code Keys.GREETING} or {@code PREFIX + "title"}.
 * Invocations with other keys cannot be verified and are listed in {@link #getUnverifiedKeys()}.<br>
 * Invocations referring to the bundle of a class annotated with {@link NoBundle} are skipped.<br>
 * Invocations within a declaration annotated with {@code @SuppressWarnings("bundle")}
 * (see {@link Constants#SUPPRESS_WARNINGS_BUNDLE}) are neither listed as unverified nor as unresolved.<br>
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
  private final List<BundleKey> unverifiedKeys = new ArrayList<>(); // bundle invocations with a non-constant key
  private Types types;                                            // type utilities of the current task
  private Elements elements;                                      // element utilities of the current task
  private final Map<String, Boolean> noBundles = new HashMap<>(); // bundle name : annotated with @NoBundle
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
    elements = task.getElements();
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
          Resolution resolution = resolveInvocation(currentPath, classExpr, trees);
          if (resolution != Resolution.OTHER &&
              isNoBundle(new BundleKey(usingClassName, bundleName, null).getBundleName())) {
            resolution = Resolution.OTHER;    // looks like a bundle, but is not
          }
          String key = determineBundleKey(new TreePath(currentPath, stringExpr), trees);
          if (key != null) {  // method getString("....") or getString(CONSTANT) called
            BundleKey bundleKey = new BundleKey(usingClassName, bundleName, key, buildLocation(stringExpr, trees));
            switch (resolution) {
              case BUNDLE -> bundleKeys.add(bundleKey);
              case OTHER -> skippedKeys.add(bundleKey);
              case UNRESOLVED -> {
                bundleKeys.add(bundleKey);    // check it anyway
                if (!isSuppressed(currentPath, trees)) {
                  unresolvedKeys.add(bundleKey);
                }
              }
            }
          }
          else if (resolution != Resolution.OTHER && !isSuppressed(currentPath, trees)) {
            // key is not a compile-time constant
            unverifiedKeys.add(new BundleKey(usingClassName, bundleName, stringExpr.toString(),
                                             buildLocation(stringExpr, trees)));
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
   * Gets the {@code getString} invocations that were ignored because they don't refer to a bundle.<br>
   * This includes the invocations referring to the bundle of a class annotated with {@link NoBundle}.
   *
   * @return the ignored keys, never null
   */
  public List<BundleKey> getSkippedKeys() {
    return skippedKeys;
  }

  /**
   * Gets the collected bundle keys whose invocation could not be resolved, unless suppressed.<br>
   * Those keys are checked nevertheless, i.e. they are contained in {@link #getBundleKeys()} as well.
   *
   * @return the unresolved keys, never null
   */
  public List<BundleKey> getUnresolvedKeys() {
    return unresolvedKeys;
  }

  /**
   * Gets the {@code getString} invocations that refer to a bundle or could not be resolved, but whose argument
   * is not a compile-time constant, unless suppressed.<br>
   * The key of those bundle keys is the source text of the argument expression.
   *
   * @return the unverified keys, never null
   */
  public List<BundleKey> getUnverifiedKeys() {
    return unverifiedKeys;
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
   * Determines whether the class of a bundle is annotated with {@link NoBundle}.
   *
   * @param bundleName the bundle name, may contain nested class names
   * @return true if annotated, false if not or the class is unknown
   */
  private boolean isNoBundle(String bundleName) {
    return noBundles.computeIfAbsent(ReflectionHelper.getOutermostClassName(bundleName), name -> {
      TypeElement clazz = elements == null ? null : elements.getTypeElement(name);
      if (clazz != null) {
        for (AnnotationMirror annotation : clazz.getAnnotationMirrors()) {
          if (annotation.getAnnotationType().toString().equals(NoBundle.class.getName())) {
            return true;
          }
        }
      }
      return false;
    });
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
   * Extracts the bundle key from a string expression.<br>
   * Evaluates string literals, constant variables (such as {@code static final String KEY = "key"}),
   * concatenations of both and parentheses.
   *
   * @param exprPath the path to the argument expression of getString(...)
   * @param trees the trees
   * @return the bundle key string, null if it is not a compile-time constant
   */
  private String determineBundleKey(TreePath exprPath, Trees trees) {
    ExpressionTree expr = (ExpressionTree) exprPath.getLeaf();
    return switch (expr.getKind()) {
      case STRING_LITERAL -> ((LiteralTree) expr).getValue() instanceof String literal ? literal : null;
      case IDENTIFIER, MEMBER_SELECT -> {
        Element element = trees.getElement(exprPath);
        yield element instanceof VariableElement variable && variable.getConstantValue() instanceof String constant ?
              constant : null;
      }
      case PARENTHESIZED -> determineBundleKey(new TreePath(exprPath, ((ParenthesizedTree) expr).getExpression()), trees);
      case PLUS -> {
        BinaryTree binary = (BinaryTree) expr;
        String left = determineBundleKey(new TreePath(exprPath, binary.getLeftOperand()), trees);
        String right = left == null ? null : determineBundleKey(new TreePath(exprPath, binary.getRightOperand()), trees);
        yield right == null ? null : left + right;
      }
      default -> null;
    };
  }

  /**
   * Determines whether warnings are suppressed for a tree.<br>
   * This is the case if the tree or any enclosing declaration, i.e. a class, method or variable,
   * is annotated with {@code @SuppressWarnings("bundle")}.
   *
   * @param path the path to the tree
   * @param trees the trees
   * @return true if suppressed
   */
  private boolean isSuppressed(TreePath path, Trees trees) {
    for (TreePath p = path; p != null; p = p.getParentPath()) {
      if (p.getLeaf() instanceof ClassTree || p.getLeaf() instanceof MethodTree || p.getLeaf() instanceof VariableTree) {
        Element element = trees.getElement(p);
        SuppressWarnings suppressWarnings = element == null ? null : element.getAnnotation(SuppressWarnings.class);
        if (suppressWarnings != null &&
            Arrays.asList(suppressWarnings.value()).contains(Constants.SUPPRESS_WARNINGS_BUNDLE)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Builds the source location of a tree in the current compilation unit.
   *
   * @param tree the tree
   * @param trees the trees
   * @return the location formatted as {@code <file>:[<line>,<column>]}
   */
  private String buildLocation(Tree tree, Trees trees) {
    CompilationUnitTree unit = getCurrentPath().getCompilationUnit();
    JavaFileObject sourceFile = unit.getSourceFile();
    String fileName;
    try {
      fileName = Path.of(sourceFile.toUri()).toString();
    }
    catch (RuntimeException ex) {   // not a file in the default file system
      fileName = sourceFile.getName();
    }
    long pos = trees.getSourcePositions().getStartPosition(unit, tree);
    if (pos == Diagnostic.NOPOS) {
      return fileName;
    }
    LineMap lineMap = unit.getLineMap();
    return fileName + ":[" + lineMap.getLineNumber(pos) + "," + lineMap.getColumnNumber(pos) + "]";
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
