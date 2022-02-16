/*
 * Copyright (C) 2017 by:
 *
 * Fr. Meyer's Sohn (GmbH & Co) KG
 * Grüner Deich 19
 * 20097 Hamburg
 * Germany
 * Tel. +49 40 2393 0
 * http://www.fms-logistics.com
 *
 * All Rights Reserved.
 */
package org.tentackle.model;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Test;

import java.util.Collection;

public class ModelTest {

  @Test
  public void loadFromClasspath() {
    Model model = Model.getInstance();
    model.clearModel();
    try {
      Collection<EntityInfo> entityInfos = model.loadFromResources(true);   // tentackle-test-pdo is in classpath!
      Assert.assertEquals(entityInfos.size(), 10);    // 10 tentackle entities
      for (EntityInfo entityInfo : entityInfos) {
        Reporter.log(entityInfo.getEntity() + " in " + entityInfo.getURL() + "<br>");
      }
    }
    catch (ModelException e) {
      e.printStackTrace();
    }
  }

}
