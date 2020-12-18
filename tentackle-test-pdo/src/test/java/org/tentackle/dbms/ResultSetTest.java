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

package org.tentackle.dbms;

import org.testng.Assert;
import org.testng.annotations.Test;

import org.tentackle.ns.pdo.NumberPool;
import org.tentackle.test.pdo.AbstractPdoTest;

import java.util.List;

/**
 * Number source tests.
 *
 * @author harald
 */
public class ResultSetTest extends AbstractPdoTest {

  private static class SetterDTO {
    private String name;
    private String realm;
    private Boolean online;

    public SetterDTO() {}

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getRealm() {
      return realm;
    }

    public void setRealm(String realm) {
      this.realm = realm;
    }

    public Boolean isPoolOnline() {
      return online;
    }

    public void setPoolOnline(Boolean online) {
      this.online = online;
    }
  }

  private static class BuilderDTO {
    private final String name;
    private final String realm;
    private final boolean online;   // primitive!

    public static Builder builder() {
      return new Builder();
    }

    private BuilderDTO(Builder builder) {
      this.name = builder.name;
      this.realm = builder.realm;
      this.online = builder.online;
    }

    public String getName() {
      return name;
    }

    public String getRealm() {
      return realm;
    }

    public boolean isPoolOnline() {
      return online;
    }

    private static class Builder {
      private String name;
      private String realm;
      private boolean online;

      public void name(String name) {
        this.name = name;
      }

      public void realm(String realm) {
        this.realm = realm;
      }

      public void poolOnline(boolean online) {
        this.online = online;
      }

      public BuilderDTO build() {
        return new BuilderDTO(this);
      }
    }
  }


  @Test(groups = "local")
  public void retrieveDTOs() {

    // create some pools
    NumberPool numberPool = on(NumberPool.class);
    numberPool.setName("pool1");
    numberPool.setRealm("realm1");
    numberPool.setOnline(true);
    numberPool.save();
    numberPool = on(NumberPool.class);
    numberPool.setName("pool2");
    numberPool.setRealm("realm2");
    numberPool.setOnline(false);
    numberPool.save();

    Db session = (Db) getSession();   // is a local session
    try (ResultSetWrapper rs = session.createStatement().executeQuery("select * from " + numberPool.getTableName())) {
      List<SetterDTO> dtos = rs.toDTOs(SetterDTO.class);
      Assert.assertEquals(dtos.size(), 2);
      SetterDTO dto = dtos.get(0);
      Assert.assertEquals(dto.getName(), "pool1");
      Assert.assertEquals(dto.getRealm(), "realm1");
      Assert.assertEquals(dto.isPoolOnline(), Boolean.TRUE);
      dto = dtos.get(1);
      Assert.assertEquals(dto.getName(), "pool2");
      Assert.assertEquals(dto.getRealm(), "realm2");
      Assert.assertEquals(dto.isPoolOnline(), Boolean.FALSE);
    }

    // same with builder
    try (ResultSetWrapper rs = session.createStatement().executeQuery("select * from " + numberPool.getTableName())) {
      List<BuilderDTO> dtos = rs.toDTOs(BuilderDTO.class);
      Assert.assertEquals(dtos.size(), 2);
      BuilderDTO dto = dtos.get(0);
      Assert.assertEquals(dto.getName(), "pool1");
      Assert.assertEquals(dto.getRealm(), "realm1");
      Assert.assertEquals(dto.isPoolOnline(), true);
      dto = dtos.get(1);
      Assert.assertEquals(dto.getName(), "pool2");
      Assert.assertEquals(dto.getRealm(), "realm2");
      Assert.assertEquals(dto.isPoolOnline(), false);
    }

  }

}
