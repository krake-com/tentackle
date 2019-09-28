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

import org.tentackle.common.ServiceFactory;
import org.tentackle.maven.AbstractTemplateModel;

interface TemplateModelCreatorHolder {
  TemplateModelCreator INSTANCE = ServiceFactory.createService(TemplateModelCreator.class, DefaultTemplateModelCreator.class, false);
}

/**
 * Creates the freemarker template model.
 * <p>
 * May be replaced via &#64;{@link org.tentackle.common.Service} annotation from within an application-specific plugin dependency.
 * For example, to add template variables from other sources.
 * <p>
 * Example:
 * <pre>
 *   &#64;Service(TemplateModelCreator.class)
 *   public class SpecialTemplateModelCreator implements TemplateModelCreator {
 *
 *     &#64;Override
 *     public AbstractTemplateModel createModel(AbstractJlinkMojo mojo, JlinkResolver.Result result) throws MojoExecutionException {
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
 *         &lt;artifactId&gt;special-creator&lt;/artifactId&gt;
 *         &lt;version&gt;1.0&lt;/version&gt;
 *       &lt;/dependency&gt;
 *     &lt;/dependencies&gt;
 *     &lt;configuration&gt;
 *       ...
 *     &lt;/configuration&gt;
 *   &lt;/plugin&gt;
 * </pre>
 */
public interface TemplateModelCreator {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static TemplateModelCreator getInstance() {
    return TemplateModelCreatorHolder.INSTANCE;
  }

  /**
   * Creates the template model.
   *
   * @param mojo the mojo
   * @param result the resolver result
   * @return the model
   * @throws MojoExecutionException if model creation failed
   */
  AbstractTemplateModel createModel(AbstractJlinkMojo mojo, JlinkResolver.Result result) throws MojoExecutionException;

}
