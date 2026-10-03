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

import com.sun.source.util.Trees;

import org.tentackle.buildsupport.AbstractTentackleProcessor;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.Set;


/**
 * Bundle checker processor.<br>
 * Passes the syntax trees of all root elements to the {@link CheckBundlesScanner}.
 * Claims all annotations, since it is the only processor that runs.
 *
 * @author harald
 */
@SupportedOptions("verbosity")
@SupportedAnnotationTypes("*")
public class CheckBundlesProcessor extends AbstractTentackleProcessor {

  private final CheckBundlesScanner bundleScanner;
  private Trees trees;

  /**
   * Creates the processor.
   *
   * @param bundleScanner the scanner collecting the bundle keys
   */
  public CheckBundlesProcessor(CheckBundlesScanner bundleScanner) {
    this.bundleScanner = bundleScanner;
  }

  @Override
  public synchronized void init(ProcessingEnvironment processingEnvironment) {
    super.init(processingEnvironment);
    trees = Trees.instance(processingEnvironment);
  }

  @Override
  public boolean process(Set<? extends TypeElement> types, RoundEnvironment environment) {
    if (!environment.processingOver()) {
      for (Element element: environment.getRootElements()) {
        bundleScanner.scan(trees.getPath(element), trees);
      }
    }
    return true;
  }
}
