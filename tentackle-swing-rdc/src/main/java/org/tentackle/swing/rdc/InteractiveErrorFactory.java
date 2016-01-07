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


package org.tentackle.swing.rdc;

import java.util.TreeSet;
import org.tentackle.bind.Binder;
import org.tentackle.common.ServiceFactory;
import org.tentackle.log.Logger;
import org.tentackle.validate.ValidationMapper;
import org.tentackle.validate.ValidationResult;


interface InteractiveErrorFactory$Singleton {
  static InteractiveErrorFactory INSTANCE = ServiceFactory.createService(InteractiveErrorFactory.class);
}

/**
 * A factory for interactive errors.
 *
 * @author harald
 */
public interface InteractiveErrorFactory {

  /**
   * The singleton.
   *
   * @return the singleton
   */
  static InteractiveErrorFactory getInstance() {
    return InteractiveErrorFactory$Singleton.INSTANCE;
  }

  /**
   * Creates an interactive error from a validation result.
   *
   * @param mappers optional mappers to map the validation path to a binding path
   * @param binder the binder
   * @param validationResult the validation result
   * @return the interactive error
   */
  InteractiveError createInteractiveError(TreeSet<ValidationMapper> mappers, Binder binder, ValidationResult validationResult);

  /**
   * Creates an interactive error explicitly.
   *
   * @param text the error text
   * @param level the severity (usually {@link Level#WARNING} or {@link Level#SEVERE})
   * @param errorCode the optional error code
   * @param uiComponent the optional UI component related to the error, null if none
   * @param validationResult the optional validation result
   */
  InteractiveError createInteractiveError(String text, Logger.Level level, String errorCode,
                                          ValidationResult validationResult, Object uiComponent);

}
