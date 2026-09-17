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

package org.tentackle.wurblet;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wurbelizer.wurbel.WurbelException;

import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.SortType;
import org.tentackle.model.parse.Document;

import java.util.Arrays;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;


/**
 * Tests wurblet expressions.
 *
 * @author harald
 */
public class WurbletArgumentParserTest {

  private Entity entity;

  @BeforeClass
  public void createEntity() {
    try {
      entity = Model.getInstance().getEntityFactory().createEntity(new Document(
                "name := Blah\n" +
                "id := 1\n" +
                "table := blah\n" +
                "long       txId      txid\n" +
                "char       modType   modtype\n" +
                "String(32) user      moduser\n" +
                "Timestamp  processed processed\n" +
                "JSON       payload   payload"),
                null);
    }
    catch (ModelException mx) {
      fail("creating test entity failed", mx);
    }
  }

  @Test
  public void testParser() {
    List<String> args1 = Arrays.asList("user:=#\"hinz kunz\"", "AND", "(", "user", "OR", "modType", ")");
    List<String> args2 = Arrays.asList("(", "processed:=:null", "AND", "(", "user", "OR", "modType", ")", ")");
    List<String> args3 = Arrays.asList("processed:=:null", "(", "user", "OR", "modType", ")");
    List<String> args4 = Arrays.asList("NOT(", "processed:=:null", "OR", "user)AND", "NOT(processed:=:null",
                                       "OR", "modType", ")",  "|",  "+id", "txId");
    List<String> args5 = Arrays.asList("processed:=:null", "user", "modType");
    List<String> args6 = Arrays.asList("NOT", "processed:<=:\"1980-01-01 00:00:00\"", "|", "processed", "modType");

    try {
      WurbletArgumentParser parser = new WurbletArgumentParser(entity, false, args1);
      WurbletArgumentExpression expr = parser.getExpression();
      assertEquals(expr.getOperands().size(), 2);
      assertTrue(expr.getOperands().get(0) instanceof WurbletArgument);
      WurbletArgument argument = (WurbletArgument) expr.getOperands().get(0);
      assertEquals(argument.getText(), "user:=#\"hinz kunz\"");
      assertEquals(argument.getValue(), "'\"hinz kunz\"'");
      assertEquals(argument.getRelop(), "=");
      WurbletArgumentOperand operand = expr.getOperands().get(1);
      assertTrue(operand instanceof WurbletArgumentExpression);
      expr = (WurbletArgumentExpression) operand;
      assertEquals(expr.getOperators().size(), 1);
      assertEquals(parser.getExpression().toString(), "user:=#\"hinz kunz\" AND (user OR modType)");
    }
    catch(WurbelException wx) {
      fail(wx.getMessage(), wx);
    }

    try {
      WurbletArgumentParser parser = new WurbletArgumentParser(entity, false, args2);
      WurbletArgumentExpression expr = parser.getExpression();
      assertEquals(expr.getOperands().size(), 1);
      WurbletArgumentOperand operand = expr.getOperands().get(0);
      assertTrue(operand instanceof WurbletArgumentExpression);
      expr = (WurbletArgumentExpression) operand;
      assertEquals(expr.getOperands().size(), 2);
      operand = expr.getOperands().get(1);
      assertTrue(operand instanceof WurbletArgumentExpression);
      expr = (WurbletArgumentExpression) operand;
      assertEquals(expr.getOperators().size(), 1);
      assertEquals(parser.getExpression().toString(), "(processed:=:null AND (user OR modType))");
    }
    catch(WurbelException wx) {
      fail(wx.getMessage(), wx);
    }

    try {
      WurbletArgumentParser parser = new WurbletArgumentParser(entity, false, args3);
      WurbletArgumentExpression expr = parser.getExpression();
      assertEquals(expr.getOperands().size(), 2);
      WurbletArgumentOperand operand = expr.getOperands().get(1);
      assertTrue(operand instanceof WurbletArgumentExpression);
      expr = (WurbletArgumentExpression) operand;
      assertEquals(expr.getOperators().size(), 1);
      assertEquals(parser.getExpression().toString(), "processed:=:null AND (user OR modType)");
    }
    catch(WurbelException wx) {
      fail(wx.getMessage(), wx);
    }

    try {
      WurbletArgumentParser parser = new WurbletArgumentParser(entity, false, args4);
      WurbletArgumentExpression expr = parser.getExpression();
      assertEquals(expr.getOperands().size(), 2);
      WurbletArgumentOperand operand = expr.getOperands().get(0);
      assertTrue(operand instanceof WurbletArgumentExpression);
      expr = (WurbletArgumentExpression) operand;
      assertEquals(expr.getOperators().size(), 1);
      operand = parser.getExpression().getOperands().get(1);
      assertTrue(operand instanceof WurbletArgumentExpression);
      expr = (WurbletArgumentExpression) operand;
      assertEquals(expr.getOperands().size(), 2);
      assertEquals(parser.getExpression().toString(), "NOT (processed:=:null OR user) AND NOT (processed:=:null OR modType)");
      assertEquals(parser.getSortingArguments().size(), 2);
      WurbletArgument arg = parser.getSortingArguments().get(1);
      assertEquals(arg.getAttribute().getName(), "txId");
      assertEquals(arg.getSortType(), SortType.ASC);
    }
    catch(WurbelException wx) {
      fail(wx.getMessage(), wx);
    }

    try {
      WurbletArgumentParser parser = new WurbletArgumentParser(entity, false, args5);
      WurbletArgumentExpression expr = parser.getExpression();
      assertEquals(expr.getOperands().size(), 3);
      assertEquals(parser.getExpression().toString(), "processed:=:null AND user AND modType");
    }
    catch(WurbelException wx) {
      fail(wx.getMessage(), wx);
    }

    try {
      WurbletArgumentParser parser = new WurbletArgumentParser(entity, true, args6);
      assertEquals(parser.getExpression().toString(), "NOT processed:<=:\"1980-01-01 00:00:00\"");
      assertEquals(parser.getExtraArguments().size(), 2);
    }
    catch(WurbelException wx) {
      fail(wx.getMessage(), wx);
    }

  }


  /**
   * Tests the optional conditions, i.e. arguments with a "?boolean" suffix.
   */
  @Test
  public void testOptionalArguments() {
    try {
      WurbletArgumentParser parser = new WurbletArgumentParser(entity, false,
              Arrays.asList("user?withUser", "AND", "NOT", "(", "processed?withProcessed:=:null", "OR", "modType", ")"));

      assertTrue(parser.isWithOptionalArguments());
      List<WurbletArgument> args = parser.getExpressionArguments();
      assertEquals(args.size(), 3);

      // the boolean is separate from the value argument
      WurbletArgument user = args.get(0);
      assertTrue(user.isOptional());
      assertEquals(user.getOptionalName(), "withUser");
      assertEquals(user.getMethodArgumentName(), "user");
      assertEquals(user.getAttribute().getName(), "user");

      // an argument with a fixed value can be optional as well
      WurbletArgument processed = args.get(1);
      assertTrue(processed.isOptional());
      assertEquals(processed.getOptionalName(), "withProcessed");
      assertEquals(processed.getValue(), "null");

      // mandatory arguments are unaffected
      assertFalse(args.get(2).isOptional());
      assertNull(args.get(2).getOptionalName());
    }
    catch(WurbelException wx) {
      fail(wx.getMessage(), wx);
    }

    try {
      // more than one argument may refer to the same boolean
      WurbletArgumentParser parser = new WurbletArgumentParser(entity, false,
              Arrays.asList("user?withText", "OR", "user[user2]?withText"));
      assertEquals(parser.getExpressionArguments().get(0).getOptionalName(), "withText");
      assertEquals(parser.getExpressionArguments().get(1).getOptionalName(), "withText");
      assertEquals(parser.getExpressionArguments().get(1).getMethodArgumentName(), "user2");
    }
    catch(WurbelException wx) {
      fail(wx.getMessage(), wx);
    }

    // the boolean must not collide with another method argument
    assertThrows(WurbelException.class, () ->
            new WurbletArgumentParser(entity, false, Arrays.asList("user?user")));

    // the boolean must be a valid java identifier
    assertThrows(WurbelException.class, () ->
            new WurbletArgumentParser(entity, false, Arrays.asList("user?with user")));
    assertThrows(WurbelException.class, () ->
            new WurbletArgumentParser(entity, false, Arrays.asList("user?")));

    // the marker must precede the relop, not end up in the value
    assertThrows(WurbelException.class, () ->
            new WurbletArgumentParser(entity, false, Arrays.asList("processed:=:null?withProcessed")));

    // only conditions can be optional
    assertThrows(WurbelException.class, () ->
            new WurbletArgumentParser(entity, false, Arrays.asList("+txId?withTxId")));
    assertThrows(WurbelException.class, () ->
            new WurbletArgumentParser(entity, true, Arrays.asList("user", "|", "modType?withModType")));
  }

  @Test
  public void testNonComparableAttributeRejectedAsConditionOrSort() {
    // a JSON column has no database comparator, so it cannot be used as a condition or sort argument
    assertThrows(WurbelException.class, () ->
            new WurbletArgumentParser(entity, false, Arrays.asList("payload:=:\"{}\"")));
    assertThrows(WurbelException.class, () ->
            new WurbletArgumentParser(entity, false, Arrays.asList("+payload")));

    // but it can still be used as a plain update value (EXTRA), which needs no comparison
    try {
      new WurbletArgumentParser(entity, true, Arrays.asList("txId", "|", "payload"));
    }
    catch (WurbelException wx) {
      fail(wx.getMessage(), wx);
    }
  }

  @Test
  public void testNonComparableAttributeAllowedForIsNullCheck() {
    // "IS NULL"/"IS NOT NULL" need no comparator, so they're fine even for a non-comparable datatype
    try {
      new WurbletArgumentParser(entity, false, Arrays.asList("payload:null"));
      new WurbletArgumentParser(entity, false, Arrays.asList("payload:notnull"));
    }
    catch (WurbelException wx) {
      fail(wx.getMessage(), wx);
    }
  }

}
