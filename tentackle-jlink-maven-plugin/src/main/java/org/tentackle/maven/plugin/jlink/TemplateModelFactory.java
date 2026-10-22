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

package org.tentackle.maven.plugin.jlink;

import org.apache.maven.plugin.MojoExecutionException;

import org.tentackle.buildsupport.codegen.TemplateModel;
import org.tentackle.common.ServiceFactory;

interface TemplateModelFactoryHolder {
  TemplateModelFactory INSTANCE = ServiceFactory.createService(TemplateModelFactory.class, DefaultTemplateModelFactory.class, false);
}

/**
 * Factory for the freemarker template model.
 * <p>
 * May be replaced via &#64;{@link org.tentackle.common.Service} annotation from within an application-specific plugin dependency.
 * For example, to add template variables from other sources. Extending {@link DefaultTemplateModelFactory}
 * keeps the built-in variables.
 * <p>
 * Example:
 * <pre>
 *   &#64;Service(TemplateModelFactory.class)
 *   public class SpecialTemplateModelFactory implements TemplateModelFactory {
 *
 *     &#64;Override
 *     public TemplateModel create(AbstractJLinkMojo mojo, JLinkResolver.Result result) throws MojoExecutionException {
 *       ...
 *     }
 *   }
 * </pre>
 *
 * Add as plugin dependency:
 *
 * <pre>
 *   &lt;plugin&gt;
 *     &lt;groupId&gt;org.tentackle&lt;/groupId&gt;
 *     &lt;artifactId&gt;tentackle-jlink-maven-plugin&lt;/artifactId&gt;
 *     &lt;dependencies&gt;
 *       &lt;dependency&gt;
 *         &lt;groupId&gt;com.example&lt;/groupId&gt;
 *         &lt;artifactId&gt;special-model-factory&lt;/artifactId&gt;
 *         &lt;version&gt;1.0&lt;/version&gt;
 *       &lt;/dependency&gt;
 *     &lt;/dependencies&gt;
 *     &lt;configuration&gt;
 *       ...
 *     &lt;/configuration&gt;
 *   &lt;/plugin&gt;
 * </pre>
 */
public interface TemplateModelFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static TemplateModelFactory getInstance() {
    return TemplateModelFactoryHolder.INSTANCE;
  }

  /**
   * Creates the template model.
   *
   * @param mojo the mojo
   * @param result the resolver result
   * @return the model
   * @throws MojoExecutionException if model creation failed
   */
  TemplateModel create(AbstractJLinkMojo mojo, JLinkResolver.Result result) throws MojoExecutionException;

}
