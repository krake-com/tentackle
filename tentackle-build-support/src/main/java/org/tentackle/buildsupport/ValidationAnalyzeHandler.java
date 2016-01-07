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

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;


/**
 * Handler to analyze validation annotations.<br>
 * Used to check for syntax errors in scripts.
 *
 * @author harald
 */
public class ValidationAnalyzeHandler extends AbstractAnalyzeHandler {

  private AbstractTentackleProcessor processor;     // the annotation processor

  @Override
  public void setProcessor(AbstractTentackleProcessor processor) {
    this.processor = processor;
  }

  @Override
  public AbstractTentackleProcessor getProcessor() {
    return processor;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void processAnnotation(TypeElement annotation, RoundEnvironment roundEnv) {

    /**
     * @todo
     *
     * - check if Validator contains a script
     * - if so: compile it and check for errors
     */
  }


}
