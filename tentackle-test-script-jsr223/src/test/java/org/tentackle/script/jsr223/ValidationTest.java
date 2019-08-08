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


package org.tentackle.script.jsr223;

import java.util.List;

import org.tentackle.script.DefaultScriptFactory;
import org.tentackle.script.ScriptFactory;
import org.tentackle.validate.*;
import org.tentackle.validate.scope.AllScope;
import org.tentackle.validate.scope.ChangeableScope;
import org.tentackle.validate.scope.InteractiveScope;
import org.tentackle.validate.scope.MandatoryScope;
import org.tentackle.validate.scope.PersistenceScope;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

/**
 * Validation test.
 *
 * @author harald
 */
public class ValidationTest {

  @Test
  @SuppressWarnings("unchecked")
  public void testScopes() {

    System.setProperty("polyglot.js.nashorn-compat","true");
    ScriptFactory.getInstance().setDefaultLanguage("js");

    Class<AllScope> all = (Class<AllScope>) ValidationScopeFactory.getInstance().getValidationScope(AllScope.NAME);
    Class<ChangeableScope> changeable = (Class<ChangeableScope>) ValidationScopeFactory.getInstance().getValidationScope(ChangeableScope.NAME);
    Class<InteractiveScope> interactive = (Class<InteractiveScope>) ValidationScopeFactory.getInstance().getValidationScope(InteractiveScope.NAME);
    Class<MandatoryScope> mandatory = (Class<MandatoryScope>) ValidationScopeFactory.getInstance().getValidationScope(MandatoryScope.NAME);
    Class<PersistenceScope> persistence = (Class<PersistenceScope>) ValidationScopeFactory.getInstance().getValidationScope(PersistenceScope.NAME);

    Assert.assertTrue(ValidationScopeFactory.getInstance().getAllScope().appliesTo(changeable, interactive, mandatory, persistence));
    Assert.assertFalse(ValidationScopeFactory.getInstance().getInteractiveScope().appliesTo(changeable, mandatory, persistence));
    Assert.assertTrue(ValidationScopeFactory.getInstance().getPersistenceScope().appliesTo(changeable, interactive, mandatory, persistence));
    Assert.assertTrue(ValidationScopeFactory.getInstance().getPersistenceScope().appliesTo(all));
  }

  @Test
  public void testValidate() {

    System.setProperty("polyglot.js.nashorn-compat","true");
    ScriptFactory.getInstance().setDefaultLanguage("js");

    TestObject object = new TestObject();

    List<Validator> validators = ValidatorCache.getInstance().getFieldValidators(object.getClass());
    for (Validator validator: validators) {
      Reporter.log("1: " + validator + "<br/>");
    }
    Assert.assertEquals(validators.size(), 9);


    object.text = null;
    object.percent = 200;

    AllScope allScopeImpl = ValidationScopeFactory.getInstance().getAllScope();
    List<? extends ValidationResult> results = ValidationUtilities.getInstance().validateFields(validators, allScopeImpl, "object", object);
    for (ValidationResult result: results) {
      Reporter.log("2: " + result + "<br/>");
    }
    Assert.assertTrue(results.size() == 5);


    object.text = "blah";
    object.percent = 80;
    object.value = 250;

    results = ValidationUtilities.getInstance().validateFields(validators, null, "object", object);
    for (ValidationResult result: results) {
      Reporter.log("3: " + result + "<br/>");
    }
    Assert.assertTrue(results.isEmpty());

    object.percent = 87;

    results = ValidationUtilities.getInstance().validateFields(validators, null, "object", object);
    for (ValidationResult result: results) {
      Reporter.log("4: " + result + "<br/>");
    }

    Assert.assertTrue(results.size() == 1);


    object.percent = 50;
    validators = ValidatorCache.getInstance().getObjectValidators(object.getClass());
    Assert.assertTrue(validators.size() == 1);

    results = ValidationUtilities.getInstance().validateObject(validators, null, "object", null, object, object.getClass());
    for (ValidationResult result: results) {
      Reporter.log("5: " + result + "<br/>");
    }
    Assert.assertTrue(results.isEmpty());

    object.percent = 81;

    results = ValidationUtilities.getInstance().validateObject(validators, null, "object", null, object, object.getClass());
    for (ValidationResult result: results) {
      Reporter.log("6: " + result + "<br/>");
    }
    Assert.assertTrue(results.size() == 1);
  }

}
