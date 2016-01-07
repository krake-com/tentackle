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

package org.tentackle.wurblet;

import java.util.Arrays;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.wurbelizer.wurbel.WurbelException;


/**
 *
 * @author harald
 */
public class WurbletKeyParserTest {

  @Test
  public void testParser() {
    List<String> args1 = Arrays.asList(new String[]{
      "processed:=:null", "AND", "(", "user", "OR" , "modType", ")"
    });

    List<String> args2 = Arrays.asList(new String[]{
      "(", "processed:=:null", "AND", "(", "user", "OR" , "modType", ")", ")"
    });

    List<String> args3 = Arrays.asList(new String[]{
      "processed:=:null", "(", "user", "OR" , "modType", ")"
    });

    List<String> args4 = Arrays.asList(new String[]{
      "NOT(", "processed:=:null", "OR", "user)AND", "NOT(processed:=:null", "OR" , "modType", ") +id extra"
    });

    List<String> args5 = Arrays.asList(new String[]{
      "processed:=:null", "user", "modType"
    });

    List<String> args6 = Arrays.asList(new String[]{
      "NOT", "processed:=:5", "|", "upd1", "upd2"
    });


    try {
      WurbletParameterParser parser = new WurbletParameterParser(args1);
      WurbletParameterExpression expr = parser.getExpression();
      Assert.assertEquals(expr.getOperands().size(), 2);
      WurbletParameterOperand operand = expr.getOperands().get(1);
      Assert.assertTrue(operand instanceof WurbletParameterExpression);
      expr = (WurbletParameterExpression) operand;
      Assert.assertEquals(expr.getOperators().size(), 1);
      Assert.assertEquals(parser.getExpression().toString(), "processed:=:null AND (user OR modType)");
    }
    catch(WurbelException wx) {
      Assert.fail(wx.getMessage(), wx);
    }

    try {
      WurbletParameterParser parser = new WurbletParameterParser(args2);
      WurbletParameterExpression expr = parser.getExpression();
      Assert.assertEquals(expr.getOperands().size(), 1);
      WurbletParameterOperand operand = expr.getOperands().get(0);
      Assert.assertTrue(operand instanceof WurbletParameterExpression);
      expr = (WurbletParameterExpression) operand;
      Assert.assertEquals(expr.getOperands().size(), 2);
      operand = expr.getOperands().get(1);
      Assert.assertTrue(operand instanceof WurbletParameterExpression);
      expr = (WurbletParameterExpression) operand;
      Assert.assertEquals(expr.getOperators().size(), 1);
      Assert.assertEquals(parser.getExpression().toString(), "(processed:=:null AND (user OR modType))");
    }
    catch(WurbelException wx) {
      Assert.fail(wx.getMessage(), wx);
    }

    try {
      WurbletParameterParser parser = new WurbletParameterParser(args3);
      WurbletParameterExpression expr = parser.getExpression();
      Assert.assertEquals(expr.getOperands().size(), 2);
      WurbletParameterOperand operand = expr.getOperands().get(1);
      Assert.assertTrue(operand instanceof WurbletParameterExpression);
      expr = (WurbletParameterExpression) operand;
      Assert.assertEquals(expr.getOperators().size(), 1);
      Assert.assertEquals(parser.getExpression().toString(), "processed:=:null AND (user OR modType)");
    }
    catch(WurbelException wx) {
      Assert.fail(wx.getMessage(), wx);
    }

    try {
      WurbletParameterParser parser = new WurbletParameterParser(args4);
      WurbletParameterExpression expr = parser.getExpression();
      Assert.assertEquals(expr.getOperands().size(), 2);
      WurbletParameterOperand operand = expr.getOperands().get(0);
      Assert.assertTrue(operand instanceof WurbletParameterExpression);
      expr = (WurbletParameterExpression) operand;
      Assert.assertEquals(expr.getOperators().size(), 1);
      operand = parser.getExpression().getOperands().get(1);
      Assert.assertTrue(operand instanceof WurbletParameterExpression);
      expr = (WurbletParameterExpression) operand;
      Assert.assertEquals(expr.getOperands().size(), 2);
      Assert.assertEquals(parser.getExpression().toString(), "NOT (processed:=:null OR user) AND NOT (processed:=:null OR modType)");
      Assert.assertEquals(parser.getSortingParameters().size(), 1);
    }
    catch(WurbelException wx) {
      Assert.fail(wx.getMessage(), wx);
    }

    try {
      WurbletParameterParser parser = new WurbletParameterParser(args5);
      WurbletParameterExpression expr = parser.getExpression();
      Assert.assertEquals(expr.getOperands().size(), 3);
      Assert.assertEquals(parser.getExpression().toString(), "processed:=:null AND user AND modType");
    }
    catch(WurbelException wx) {
      Assert.fail(wx.getMessage(), wx);
    }

    try {
      WurbletParameterParser parser = new WurbletParameterParser(args6);
      Assert.assertEquals(parser.getExpression().toString(), "NOT processed:=:5");
      Assert.assertEquals(parser.getExtraParameters().size(), 2);
    }
    catch(WurbelException wx) {
      Assert.fail(wx.getMessage(), wx);
    }

  }

}
