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

import org.tentackle.common.Analyze;
import org.tentackle.common.TentackleRuntimeException;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Annotation Processor for all annotations annotated with {@link Analyze}.
 *
 * @author harald
 */
@SupportedAnnotationTypes("*")
@SupportedOptions("verbosity")
public class AnalyzeProcessor extends AbstractTentackleProcessor {

  /**
   * Logfile for compilation errors during the analyze-phase.<br>
   * The logfile is missing, if no errors where detected. Otherwise, analyze info files
   * may be incomplete or missing.
   */
  public static final String COMPILE_ERROR_LOG = "error.log";



  private static final int MAX_HANDLERS = 1000;   // to detect endless loops
  private static final int MAX_DEPTH = 100;       // dto...
  private static final String ANALYZE_CLASSNAME = Analyze.class.getName();


  private File analyzeDir;
  private File serviceDir;
  private ClassLoader processingClassLoader;


  /**
   * Gets the classloader to be used instead of the default.
   *
   * @return the classloader, never null (fallback to current classloader)
   */
  public ClassLoader getProcessingClassLoader() {
    return processingClassLoader == null ? getClass().getClassLoader() : processingClassLoader;
  }

  /**
   * Sets the classloader to be used instead of the default.
   *
   * @param processingClassLoader the classloader, null if default
   */
  public void setProcessingClassLoader(ClassLoader processingClassLoader) {
    this.processingClassLoader = processingClassLoader;
  }

  /**
   * Gets the generated analyze files directory.
   *
   * @return the output dir
   */
  public File getAnalyzeDir() {
    return analyzeDir;
  }

  /**
   * Sets the generated analyze files directory.
   *
   * @param outputDir the output dir
   */
  public void setAnalyzeDir(File outputDir) {
    this.analyzeDir = outputDir;
  }

  /**
   * Gets the generated services resource directory.
   *
   * @return the resources file dir
   */
  public File getServiceDir() {
    return serviceDir;
  }

  /**
   * Sets the generated services resource directory.
   *
   * @param serviceDir the resource dir
   */
  public void setServiceDir(File serviceDir) {
    this.serviceDir = serviceDir;
  }


  /**
   * {@inheritDoc}
   * <p>
   * Processes all annotations that itself or any inherited annotations are annotated with @Analyze.
   * The recursive scan allows annotating only once for a group of annotations.
   */
  @Override
  public boolean process(Set<? extends TypeElement> annotationTypes, RoundEnvironment roundEnv) {
    boolean claimed = false;
    if (!roundEnv.processingOver()) {
      for (TypeElement annotationType: annotationTypes) {
        if (isDebugLogging()) {
          processingEnv.getMessager().printMessage(
                  Kind.NOTE, "processing annotation " + annotationType);
        }
        List<AnalyzeHandler> handlers = new ArrayList<>();
        findHandlers(annotationType, handlers, 0);
        for (AnalyzeHandler handler: handlers) {
          handler.processAnnotation(annotationType, roundEnv);
          claimed = true;
        }
      }
    }
    return claimed;
  }



  /**
   * Finds the handler for a given annotation.
   *
   * @param annotationType the annotation type
   * @param handlers the collected handlers
   * @param depth to detect annotation loops
   */
  private void findHandlers(TypeElement annotationType, List<AnalyzeHandler> handlers, int depth) {

    if (!annotationType.toString().startsWith("java.lang.")) {

      for (AnnotationMirror mi : annotationType.getAnnotationMirrors()) {

        if (mi.getAnnotationType().toString().equals(ANALYZE_CLASSNAME)) {
          Map<? extends ExecutableElement, ? extends AnnotationValue> annotationParameters = mi.getElementValues();
          for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : annotationParameters.entrySet()) {
            ExecutableElement elem = entry.getKey();
            AnnotationValue anno = entry.getValue();
            if (elem.toString().equals("value()")) {
              String handlerClassName = anno.getValue().toString();
              // cut trailing .class, if any
              int ndx = handlerClassName.lastIndexOf(".class");
              if (ndx > 0) {
                handlerClassName = handlerClassName.substring(0, ndx);
              }
              try {
                @SuppressWarnings("unchecked")
                Class<AnalyzeHandler> handlerClass = (Class<AnalyzeHandler>) Class.forName(handlerClassName);
                AnalyzeHandler handler = handlerClass.getDeclaredConstructor().newInstance();
                handler.setProcessor(this);
                handlers.add(handler);
                if (handlers.size() > MAX_HANDLERS) {
                  throw new TentackleRuntimeException("endless-loop detected while processing " + handlerClass);
                }
              }
              catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                     InvocationTargetException | NoSuchMethodException ex) {
                throw new TentackleRuntimeException("cannot instantiate AnalyzeHandler " + handlerClassName, ex);
              }
            }
          }
        }
        else {
          // recursively scan inherited annotation
          TypeElement parentAnnotation = (TypeElement) mi.getAnnotationType().asElement();
          if (++depth > MAX_DEPTH) {
            getProcessingEnvironment().getMessager().printMessage(
                  Kind.WARNING,
                  "annotation-loop detected while processing " + parentAnnotation + " -> ignored",
                  annotationType);
            return;
          }
          findHandlers(parentAnnotation, handlers, depth);
        }
      }
    }
  }

}
