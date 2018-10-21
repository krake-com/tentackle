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

import java.util.Arrays;
import java.util.List;
import org.tentackle.model.Entity;
import org.tentackle.model.Model;
import org.tentackle.model.ModelException;
import org.tentackle.model.SortType;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wurbelizer.wurbel.WurbelException;

import static org.testng.Assert.*;


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
      entity = Model.getInstance().getEntityFactory().createEntity(
                "name := Blah\n" +
                "id := 1\n" +
                "table := blah\n" +
                "long       txId      txid\n" +
                "char       modType   modtype\n" +
                "String(32) user      moduser\n" +
                "Timestamp  processed processed",
                null);
    }
    catch (ModelException mx) {
      fail("creating test entity failed", mx);
    }
  }

  @Test
  public void testParser() {
    List<String> args1 = Arrays.asList("processed:=:null", "AND", "(", "user", "OR", "modType", ")");
    List<String> args2 = Arrays.asList("(", "processed:=:null", "AND", "(", "user", "OR", "modType", ")", ")");
    List<String> args3 = Arrays.asList("processed:=:null", "(", "user", "OR", "modType", ")");
    List<String> args4 = Arrays.asList("NOT(", "processed:=:null", "OR", "user)AND", "NOT(processed:=:null",
                                       "OR", "modType", ") | +id txId");
    List<String> args5 = Arrays.asList("processed:=:null", "user", "modType");
    List<String> args6 = Arrays.asList("NOT", "processed:<=:\"1980-01-01\"", "|", "processed", "modType");

    try {
      WurbletArgumentParser parser = new WurbletArgumentParser(entity, false, args1);
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
      assertEquals(parser.getExpression().toString(), "NOT processed:<=:\"1980-01-01\"");
      assertEquals(parser.getExtraArguments().size(), 2);
    }
    catch(WurbelException wx) {
      fail(wx.getMessage(), wx);
    }

  }

}
