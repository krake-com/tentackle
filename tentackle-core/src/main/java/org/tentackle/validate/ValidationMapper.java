/*
 * Tentackle - https://tentackle.org.
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
 *
 */

package org.tentackle.validate;

import java.util.Objects;
import org.tentackle.bind.Binder;
import org.tentackle.common.Compare;

/**
 * A mapper to translate validation paths to binding paths.
 *
 * @author harald
 */
public class ValidationMapper implements Comparable<ValidationMapper> {


  private final String validationPath;    // from validation path
  private final Binder binder;            // for binder
  private final String bindingPath;       // to binding path
  private final Binder nextBinder;        // optional: continue with binder

  /**
   * Creates a mapper.
   *
   * @param validationPath the start of a validation path
   * @param binder the binder this validation path should be applied to, null if all
   * @param bindingPath the binding path to replace with
   * @param nextBinder the next binder to apply the binding path to
   */
  public ValidationMapper(String validationPath, Binder binder, String bindingPath, Binder nextBinder) {
    this.validationPath = validationPath;
    this.binder         = binder;
    this.bindingPath    = bindingPath;
    this.nextBinder     = nextBinder;
  }

  /**
   * @return the start of an original validation path
   */
  public String getValidationPath() {
    return validationPath;
  }

  /**
   * @return the binder the validation path should be applied to, null if all
   */
  public Binder getBinder() {
    return binder;
  }

  /**
   * @return the binding path to replace with
   */
  public String getBindingPath() {
    return bindingPath;
  }

  /**
   * @return the next binder to apply the binding path to
   */
  public Binder getNextBinder() {
    return nextBinder;
  }

  @Override
  public int hashCode() {
    int hash = 5;
    hash = 37 * hash + Objects.hashCode(this.validationPath);
    hash = 37 * hash + Objects.hashCode(this.binder);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final ValidationMapper other = (ValidationMapper) obj;
    if (!Objects.equals(this.validationPath, other.validationPath)) {
      return false;
    }
    return Objects.equals(this.binder, other.binder);
  }

  @Override
  public int compareTo(ValidationMapper other) {
    if (other != null) {
      int rv = Compare.compare(validationPath, other.validationPath);
      if (rv == 0) {
        rv = Compare.compare(binder, other.binder);
      }
      return rv;
    }
    else {
      return Integer.MAX_VALUE;
    }
  }

}
