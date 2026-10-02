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

package org.tentackle.dbms;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.tentackle.sql.Backend;
import org.tentackle.sql.SqlType;

import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Tests the setter- and builder-pattern branches of {@link DbUtilities#resultSetToList}
 * (reached via {@link ResultSetWrapper#toList}), which {@code dsl/SelectQueryTest.java} does not
 * cover — its DTOs are all Java records, exercising only the record-pattern branch.
 *
 * @author harald
 */
@SuppressWarnings({ "missing-explicit-ctor", "exports" })
public class DbUtilitiesResultSetToListTest extends AbstractDbTest {

  private static final String TABLE_NAME = "resultsettolisttest";

  /** DTO exercising the setter pattern: public no-arg constructor + {@code setXxx} methods. */
  public static class SetterDto {

    private long id;
    private String name;

    public SetterDto() {
      // required by the setter pattern
    }

    public void setId(long id) {
      this.id = id;
    }

    public void setName(String name) {
      this.name = name;
    }

    public long getId() {
      return id;
    }

    public String getName() {
      return name;
    }
  }


  /** DTO exercising the builder pattern: a static {@code builder()} + a {@code build()} method. */
  public static final class BuilderDto {

    private final long id;
    private final String name;

    private BuilderDto(long id, String name) {
      this.id = id;
      this.name = name;
    }

    public long getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public static Builder builder() {
      return new Builder();
    }

    public static final class Builder {

      private long id;
      private String name;

      public void id(long id) {
        this.id = id;
      }

      public void name(String name) {
        this.name = name;
      }

      public BuilderDto build() {
        return new BuilderDto(id, name);
      }
    }
  }


  /** DTO exercising the record pattern: the canonical constructor. */
  public record RecordDto(long id, String name) { }


  /** Record DTO with additional constructors, which must not be mistaken for the canonical one. */
  public record MultiConstructorRecordDto(long id, String name) {

    public MultiConstructorRecordDto() {
      this(0, "none");
    }

    public MultiConstructorRecordDto(String name) {
      this(-1, name);
    }
  }


  private void executeUpdate(Db db, String sql) {
    // each StatementWrapper is single-use: it detaches the session once executeUpdate() returns
    try (StatementWrapper st = db.createStatement()) {
      st.executeUpdate(sql);
    }
  }

  @BeforeClass(dependsOnMethods = "beforeClass")
  public void createTestTable() {
    Db db = getSession();
    Backend backend = db.getBackend();
    StringBuilder ddl = new StringBuilder(backend.sqlCreateTableIntro(TABLE_NAME, null));
    ddl.append("    ").append(backend.sqlCreateColumn("id", null, SqlType.BIGINT, 0, 0, false, null, false, true));
    ddl.append("    ").append(backend.sqlCreateColumn("name", null, SqlType.VARCHAR, 64, 0, true, null, false, false));
    ddl.append(backend.sqlCreateTableClosing(TABLE_NAME, null));
    executeUpdate(db, ddl.toString());
    executeUpdate(db, "INSERT INTO " + TABLE_NAME + " (id, name) VALUES (1, 'first')");
    executeUpdate(db, "INSERT INTO " + TABLE_NAME + " (id, name) VALUES (2, NULL)");
  }

  private <T> List<T> select(Class<T> dtoClass) {
    Db db = getSession();
    try (PreparedStatementWrapper st = db.createPreparedStatement(
             _ -> "SELECT id, name FROM " + TABLE_NAME + " ORDER BY id");
         ResultSetWrapper rs = st.executeQuery()) {
      return rs.toList(dtoClass);
    }
  }


  @Test
  public void testSetterPatternDto() {
    List<SetterDto> dtos = select(SetterDto.class);
    assertEquals(dtos.size(), 2);
    assertEquals(dtos.get(0).getId(), 1L);
    assertEquals(dtos.get(0).getName(), "first");
    assertEquals(dtos.get(1).getId(), 2L);
    assertNull(dtos.get(1).getName(), "a null column value must be applied via the setter as null");
  }


  @Test
  public void testBuilderPatternDto() {
    List<BuilderDto> dtos = select(BuilderDto.class);
    assertEquals(dtos.size(), 2);
    assertEquals(dtos.get(0).getId(), 1L);
    assertEquals(dtos.get(0).getName(), "first");
    assertEquals(dtos.get(1).getId(), 2L);
    assertNull(dtos.get(1).getName());
  }


  @Test
  public void testRecordDto() {
    List<RecordDto> dtos = select(RecordDto.class);
    assertEquals(dtos, List.of(new RecordDto(1, "first"), new RecordDto(2, null)));
  }


  @Test
  public void testRecordDtoWithAdditionalConstructors() {
    List<MultiConstructorRecordDto> dtos = select(MultiConstructorRecordDto.class);
    assertEquals(dtos, List.of(new MultiConstructorRecordDto(1, "first"), new MultiConstructorRecordDto(2, null)));
  }

}
