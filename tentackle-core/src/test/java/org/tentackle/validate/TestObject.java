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


package org.tentackle.validate;

import org.tentackle.common.DMoney;
import org.tentackle.validate.validator.Greater;
import org.tentackle.validate.validator.GreaterOrEqual;
import org.tentackle.validate.validator.LessOrEqual;
import org.tentackle.validate.validator.NotNull;
import org.tentackle.validate.validator.NotZero;
import org.tentackle.validate.validator.Pattern;
import org.tentackle.validate.validator.True;

/**
 * Validation test object.
 *
 * @author harald
 */
@TestObjectValidator
public class TestObject {

  @NotZero
  public DMoney amount = new DMoney("1", 2);

  @NotNull
  @Pattern("$pattern")
  public String text;

  @LessOrEqual("100")
  @GreaterOrEqual("0")
  @True(condition="$textValid", value="$percentModulo10", message="not divideable by 10")
  // we cannot use scripts in core since nashorn was removed (see CompoundValue<Scriptinglanguage>Test)
  public int percent;

  @Greater("100")
  @Greater(value = "50", priority = 1)
  @NotZero
  @Greater("25")
  public int value;

  public String getPattern() {
    return "bl.*";
  }

  public boolean isTextValid() {
    return text != null;
  }

  public boolean isPercentModulo10() {
    return percent % 10 == 0;
  }
}
