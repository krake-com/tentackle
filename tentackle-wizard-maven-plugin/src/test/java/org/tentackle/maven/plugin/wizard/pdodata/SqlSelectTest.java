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

package org.tentackle.maven.plugin.wizard.pdodata;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.common.Constants;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.ModelManager;
import org.tentackle.sql.Backend;
import org.tentackle.sql.BackendFactory;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

/**
 * Tests the SQL {@code SELECT} of a single data node with inheritance.
 */
public class SqlSelectTest {

  /** resource directory holding the test model. */
  private static final String MODEL_DIR = "inhmodel";

  private Model model;
  private Backend backend;


  @BeforeClass
  public void loadModel() throws ModelException, URISyntaxException {
    URL url = getClass().getResource(MODEL_DIR);
    assertNotNull(url, "model directory '" + MODEL_DIR + "' not found");
    model = ModelManager.getInstance().getModel("sql-select-test");
    model.clearModel();
    model.loadFromDirectory(new File(url.toURI()).getPath(), true);
    backend = BackendFactory.getInstance().getBackendByUrl("jdbc:postgresql://localhost/test");
  }


  /**
   * An attribute of an abstract entity in a single-table hierarchy is restricted to the
   * class IDs of its concrete sub entities, so that sibling rows don't match a {@code NULL}.
   */
  @Test
  public void testSingleAbstractAttribute() throws ModelException {
    assertEquals(SqlCodeFactory.createSelect(item("Pickup", "Truck", "payload", null), backend),
                 "SELECT v.classid,v.plate,v.payload,v.axles,v.covered,v.id,v.serial FROM vehicle v " +
                 "WHERE v.payload IS NULL AND v.classid IN(23,24);");
  }

  /**
   * An object of a single-table hierarchy.
   */
  @Test
  public void testSingleObject() throws ModelException {
    assertEquals(SqlCodeFactory.createSelect(object("Pickup"), backend),
                 "SELECT v.classid,v.plate,v.payload,v.covered,v.id,v.serial FROM vehicle v " +
                 "WHERE v.id=4711 AND v.classid=23;");
  }

  /**
   * An attribute of an abstract entity in a multi-table hierarchy joins all tables explicitly.
   */
  @Test
  public void testMultiAbstractAttribute() throws ModelException {
    assertEquals(SqlCodeFactory.createSelect(item("Port", "Hub", "capacity", 5), backend),
                 "SELECT n.*,h.*,d.*,p.* FROM node n JOIN hub h ON h.id=n.id " +
                 "LEFT JOIN depot d ON d.id=n.id LEFT JOIN port p ON p.id=n.id WHERE h.capacity=5;");
  }

  /**
   * An object of a multi-table hierarchy.
   */
  @Test
  public void testMultiObject() throws ModelException {
    assertEquals(SqlCodeFactory.createSelect(object("Port"), backend),
                 "SELECT n.*,h.*,p.* FROM node n JOIN hub h ON h.id=n.id JOIN port p ON p.id=n.id WHERE n.id=4711;");
  }

  /**
   * A null reference has no SQL condition.
   */
  @Test
  public void testNullReference() throws ModelException {
    DataObject reference = new DataObject(null, "contact", null, null, getEntity("Car"), null, null, false, null);
    assertEquals(SqlCodeFactory.createSelect(reference, backend), "-- no SQL condition for contact");
  }


  private DataItem item(String pdoEntityName, String attributeEntityName, String attributeName, Object value)
          throws ModelException {
    Entity pdoEntity = getEntity(pdoEntityName);
    Attribute attribute = getEntity(attributeEntityName).getAttributeByJavaName(attributeName, false);
    assertNotNull(attribute, "attribute " + attributeEntityName + "." + attributeName + " missing");
    return new DataItem("Integer", attributeName, value, null, pdoEntity, attribute, false, null,
                        new SqlCondition(4711, pdoEntity.getAttributeByJavaName(Constants.AN_ID, true)));
  }

  /**
   * Creates an object node as the {@link DataNodeFactory} does.
   *
   * @param entityName the entity of the PDO
   * @return the node
   */
  private DataObject object(String entityName) throws ModelException {
    Entity entity = getEntity(entityName);
    return new DataObject(null, null, null, null, entity, null, null, false,
                          new SqlCondition(4711, entity.getAttributeByJavaName(Constants.AN_ID, true)));
  }

  private Entity getEntity(String name) throws ModelException {
    Entity entity = model.getByEntityName(name);
    assertNotNull(entity, "entity " + name + " missing in model");
    return entity;
  }

}
