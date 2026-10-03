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

package org.tentackle.fx.atlanta.translate;

import org.tentackle.fx.FxComponent;
import org.tentackle.fx.ValueTranslatorService;
import org.tentackle.fx.translate.IdentityTranslator;

import java.time.LocalDate;

/**
 * Identity LocalDate translator.<br>
 * Provided for the {@link atlantafx.base.controls.Calendar} control.
 *
 * @author harald
 */
@ValueTranslatorService(modelClass = LocalDate.class, viewClass = LocalDate.class)
public class LocalDateLocalDateTranslator extends IdentityTranslator<LocalDate> {

  /**
   * Creates an identity translator for {@link LocalDate}.
   *
   * @param component the component
   */
  public LocalDateLocalDateTranslator(FxComponent component) {
    super(component);
  }

}
