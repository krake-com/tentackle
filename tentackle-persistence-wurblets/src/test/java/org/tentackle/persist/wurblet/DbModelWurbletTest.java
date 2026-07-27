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

package org.tentackle.persist.wurblet;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.wurbelizer.wurbel.WurbelException;

import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;
import static org.tentackle.persist.wurblet.WurbletTestSupport.attribute;
import static org.tentackle.persist.wurblet.WurbletTestSupport.createEntity;
import static org.tentackle.persist.wurblet.WurbletTestSupport.createWurblet;
import static org.tentackle.persist.wurblet.WurbletTestSupport.parseArgument;

/**
 * Tests for {@link DbModelWurblet}, the base wurblet of the persistence layer.
 * <p>
 * Covered here are the model-driven helpers the {@code .wrbl} templates call: the comma-list and
 * argument-list utilities, the JDBC accessor naming, and above all
 * {@code createRelopCode}/{@code createRelopSql}, which translate a wurblet argument's relational
 * operator into the java (prepared-statement building) and the plain-SQL form. The two must stay
 * in lockstep, so every operator is checked in both.
 */
@SuppressWarnings({ "exports", "missing-explicit-ctor" })
public class DbModelWurbletTest {

  private Entity entity;
  private DbModelWurblet wurblet;

  @BeforeClass
  public void setUp() throws Exception {
    entity = createEntity();
    wurblet = createWurblet();
  }


  @Test
  public void alwaysYieldsAString() {
    assertEquals(wurblet.as(null), "");
    assertEquals(wurblet.as(""), "");
    assertEquals(wurblet.as("x"), "x");
  }

  /**
   * Used to append to an existing argument list, which must stay valid when the list is empty.
   */
  @Test
  public void appendsToAnArgumentList() {
    assertEquals(wurblet.aas("name, max"), ", name, max");
    assertEquals(wurblet.aas(""), "");
    assertEquals(wurblet.aas(null), "");
  }

  @Test
  public void buildsCommaSeparatedLists() {
    assertEquals(wurblet.acs("a", "b"), "a, b");
    assertEquals(wurblet.acs("", "b"), "b");
    assertEquals(wurblet.pcs("a", "b"), "b, a");
    assertEquals(wurblet.pcs("", "b"), "b");
  }


  /**
   * The generated code reads and writes columns through the wrappers' typed accessors, so the
   * method name has to be derived from the attribute's effective type.
   */
  @Test
  public void derivesTheJdbcAccessorNames() throws Exception {
    Attribute txId = attribute(entity, "txId");
    assertEquals(wurblet.createJdbcGetterName(txId), "getLong");
    assertEquals(wurblet.createJdbcSetterName(txId.getEffectiveDataType()), "setLong");

    Attribute user = attribute(entity, "user");
    assertEquals(wurblet.createJdbcGetterName(user), "getString");
    assertEquals(wurblet.createJdbcSetterName(user.getEffectiveDataType()), "setString");

    Attribute processed = attribute(entity, "processed");
    assertEquals(wurblet.createJdbcGetterName(processed), "getTimestamp");
  }

  /**
   * Column-name constants are what the generated SQL refers to; the suffix disambiguates the
   * columns of a multi-column type.
   */
  @Test
  public void derivesTheColumnNameConstants() throws Exception {
    Attribute user = attribute(entity, "user");
    assertEquals(wurblet.getColumnNameConstant(user, -1), "CN_USER");
    assertEquals(wurblet.getColumnNameConstant(user, 0), "CN_USER");

    Attribute amount = attribute(entity, "amount");
    assertEquals(wurblet.getColumnNameConstant(amount, 0), "CN_AMOUNT");
    assertEquals(wurblet.getColumnNameConstant(amount, 1), "CN_AMOUNT_2");
  }

  /**
   * A plain type needs no conversion between the model and the JDBC layer.
   */
  @Test
  public void passesPlainTypesThroughUnconverted() throws Exception {
    Attribute user = attribute(entity, "user");
    assertEquals(wurblet.getModelCode(user, "rs.getString(1)"), "rs.getString(1)");
    assertEquals(wurblet.getJdbcCode(user, "getUser()"), "getUser()");
  }


  /**
   * Every relational operator the argument syntax offers, in the java form appended to the
   * {@code StringBuilder} that assembles the prepared statement.
   *
   * @return argument text and expected code
   */
  @DataProvider(name = "relops")
  public Object[][] relops() {
    return new Object[][] {
      { "user",         ".append(Backend.SQL_EQUAL_PAR)" },            // no operator means "="
      { "user:=",       ".append(Backend.SQL_EQUAL_PAR)" },
      { "user:<>",      ".append(Backend.SQL_NOTEQUAL_PAR)" },
      { "user:!=",      ".append(Backend.SQL_NOTEQUAL_PAR)" },         // alias of <>
      { "txId:<",       ".append(Backend.SQL_LESS_PAR)" },
      { "txId:>",       ".append(Backend.SQL_GREATER_PAR)" },
      { "txId:<=",      ".append(Backend.SQL_LESSOREQUAL_PAR)" },
      { "txId:>=",      ".append(Backend.SQL_GREATEROREQUAL_PAR)" },
      { "user:LIKE",    ".append(Backend.SQL_LIKE_PAR)" },
      { "user:NOTLIKE", ".append(Backend.SQL_NOTLIKE_PAR)" },
    };
  }

  @Test(dataProvider = "relops")
  public void translatesRelationalOperatorsToCode(String argument, String expected) throws Exception {
    assertEquals(wurblet.createRelopCode(parseArgument(entity, argument)), expected);
  }

  /**
   * The SQL form of the same operators - used where the statement text is built directly instead
   * of through the {@code Backend} constants.
   *
   * @return argument text and expected SQL
   */
  @DataProvider(name = "relopSql")
  public Object[][] relopSql() {
    return new Object[][] {
      { "user",         "=?" },
      { "user:<>",      "<>?" },
      { "user:!=",      "<>?" },
      { "txId:<",       "<?" },
      { "txId:>",       ">?" },
      { "txId:<=",      "<=?" },
      { "txId:>=",      ">=?" },
      { "user:LIKE",    " LIKE ?" },
      { "user:NOTLIKE", " NOT LIKE ?" },
    };
  }

  @Test(dataProvider = "relopSql")
  public void translatesRelationalOperatorsToSql(String argument, String expected) throws Exception {
    assertEquals(wurblet.createRelopSql(parseArgument(entity, argument)), expected);
  }

  /**
   * A literal value is inlined into the statement instead of becoming a parameter placeholder,
   * so the operator loses its {@code _PAR} variant.
   */
  @Test
  public void inlinesALiteralValueInsteadOfAPlaceholder() throws Exception {
    assertEquals(wurblet.createRelopCode(parseArgument(entity, "user:=#\"admin\"")),
                 ".append(Backend.SQL_EQUAL).append(\"'\\\"admin\\\"'\")");
    assertEquals(wurblet.createRelopCode(parseArgument(entity, "txId:<#42")),
                 ".append(Backend.SQL_LESS).append(\"42\")");
  }

  /**
   * An operator the wurblet does not know is passed through to the backend verbatim - that is
   * how backend-specific operators such as PostgreSQL's regex match reach the statement.
   */
  @Test
  public void passesAnUnknownOperatorThrough() throws Exception {
    assertEquals(wurblet.createRelopCode(parseArgument(entity, "user:~")), ".append(\"~?\")");
    assertEquals(wurblet.createRelopSql(parseArgument(entity, "user:~")), "~?");
  }


  /**
   * Array operators expand to a single placeholder bound to a whole collection. They are not
   * standard SQL, so the wurblet refuses to generate them for a backend that cannot execute them.
   */
  @Test
  public void generatesArrayOperatorsSupportedByTheBackend() throws Exception {
    DbModelWurblet postgres = WurbletTestSupport.createWurblet("PostgreSQL");

    assertEquals(postgres.createRelopCode(parseArgument(entity, "user:ANY")),
                 ".append(Backend.SQL_EQUAL).append(Backend.SQL_ARRAY_ANY_PAR)");
    assertEquals(postgres.createRelopSql(parseArgument(entity, "user:ANY")), "=ANY(?)");

    assertEquals(postgres.createRelopCode(parseArgument(entity, "user:ALL")),
                 ".append(Backend.SQL_EQUAL).append(Backend.SQL_ARRAY_ALL_PAR)");
    assertEquals(postgres.createRelopSql(parseArgument(entity, "user:ALL")), "=ALL(?)");
  }

  /**
   * The build must fail rather than emit SQL the target database will choke on at runtime.
   */
  @Test
  public void refusesArrayOperatorsTheBackendCannotExecute() throws Exception {
    DbModelWurblet postgres = WurbletTestSupport.createWurblet("PostgreSQL");

    WurbelException ex = expectThrows(WurbelException.class,
                                      () -> postgres.createRelopCode(parseArgument(entity, "user:IN")));
    assertTrue(ex.getMessage().contains("array operator 'IN' not supported"), ex.getMessage());
    assertTrue(ex.getMessage().contains("PostgreSQL"), ex.getMessage());

    expectThrows(WurbelException.class, () -> postgres.createRelopCode(parseArgument(entity, "user:NOTIN")));
  }

  /**
   * Generating for several backends at once means the feature has to be supported by all of them.
   */
  @Test
  public void refusesArrayOperatorsUnsupportedByAnyTargetBackend() throws Exception {
    DbModelWurblet mixed = WurbletTestSupport.createWurblet("PostgreSQL,MySQL");
    WurbelException ex = expectThrows(WurbelException.class,
                                      () -> mixed.createRelopCode(parseArgument(entity, "user:ANY")));
    assertTrue(ex.getMessage().contains("MySQL"), ex.getMessage());
  }

}
