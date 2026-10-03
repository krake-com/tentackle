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

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;


/**
 * A handler invoked for an annotation annotated with {@link org.tentackle.common.Analyze}.
 * <p>
 * Handlers are named by their classname in {@code @Analyze} and instantiated by the {@link AnalyzeProcessor}
 * via their no-arg constructor. A new instance is created for each annotation type and round.
 * A handler can do whatever is appropriate, usually writing service descriptors or analyze info files.
 *
 * @author harald
 */
public interface AnalyzeHandler {

  /**
   * Sets the annotation processor.
   *
   * @param processor the annotation processor
   */
  void setProcessor(AnalyzeProcessor processor);


  /**
   * Gets the annotation processor.
   *
   * @return the annotation processor
   */
  AnalyzeProcessor getProcessor();


  /**
   * Processes the annotation.
   *
   * @param annotationType the annotation type
   * @param roundEnv the processing environment
   */
  void processAnnotation(TypeElement annotationType, RoundEnvironment roundEnv);

}
