/*
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

package org.tentackle.model.print;

import org.tentackle.common.StringHelper;
import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.testng.Reporter;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.tentackle.model.impl.ModelTest.MODEL_FILENAME;
import static org.testng.Assert.*;

/**
 *
 * @author harald
 */
public class EntityPrinterTest {

   @Test
   public void printEntity() throws Exception {
     String model = StringHelper.readTextFromResource(MODEL_FILENAME);
     Entity entity = Model.getInstance().getEntityFactory().createEntity(model, null);
     PrintConfiguration configuration = new PrintConfiguration(true, true, null, 2);
     String text = new EntityPrinter(entity, configuration).print();
     System.out.println(text);
   }
}
