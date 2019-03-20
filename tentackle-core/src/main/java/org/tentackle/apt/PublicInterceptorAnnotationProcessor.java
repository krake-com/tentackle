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


package org.tentackle.apt;

import org.tentackle.common.AnnotationProcessor;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;

import static javax.lang.model.SourceVersion.RELEASE_12;

/**
 * Processor for the public interception annotations in the core package.<br>
 * Applies to interceptors applicable to interceptables interfaces only.
 *
 * @author harald
 */
@SupportedAnnotationTypes({
  "org.tentackle.pdo.Transaction",
  "org.tentackle.pdo.NoTransaction",
  "org.tentackle.security.Secured"
})
@SupportedSourceVersion(RELEASE_12)
@AnnotationProcessor
public class PublicInterceptorAnnotationProcessor extends AbstractInterceptorAnnotationProcessor {

  public PublicInterceptorAnnotationProcessor() {
    super(Type.PUBLIC);
  }

}
