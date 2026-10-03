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

package org.tentackle.maven.plugin.wizard;

import org.tentackle.buildsupport.codegen.AbstractGenerator;
import org.tentackle.model.Model;
import org.tentackle.validate.ScopeConfigurator;
import org.tentackle.validate.Validateable;
import org.tentackle.validate.ValidationScope;
import org.tentackle.validate.scope.ChangeableScope;
import org.tentackle.validate.scope.MandatoryScope;

/**
 * Base class for validateable generators.<br>
 * Holds common methods for {@link PdoGenerator} and {@link OperationGenerator}.
 */
public abstract class AbstractValidateableGenerator extends AbstractGenerator implements ScopeConfigurator, Validateable {

  /** the TT model. */
  private final Model model;

  /**
   * Creates a new generator.
   *
   * @param model the model
   */
  public AbstractValidateableGenerator(Model model) {
    this.model = model;
  }


  /**
   * Gets the model.
   *
   * @return the model
   */
  public Model getModel() {
    return model;
  }

  /**
   * Determines the default for remoting from the project's model defaults.
   *
   * @return true if there are no model defaults or they enable the {@code remote} option explicitly
   */
  public boolean isRemoteDefault() {
    return model.getModelDefaults() == null || Boolean.TRUE.equals(model.getModelDefaults().getRemote());
  }

  @Override
  @SuppressWarnings({ "unchecked", "rawtypes" })
  public Class<? extends ValidationScope>[] getDefaultScopes() {
    return new Class[] { MandatoryScope.class, ChangeableScope.class};
  }

}
