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

import org.wurbelizer.wurbel.WurbelException;

import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.MethodArgument;
import org.tentackle.model.ModelException;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;
import org.tentackle.wurblet.CodeGenerator;
import org.tentackle.wurblet.ComponentInfo;
import org.tentackle.wurblet.WurbletArgument;
import org.tentackle.wurblet.WurbletArgumentExpression;
import org.tentackle.wurblet.WurbletArgumentOperator;

import java.util.List;
import java.util.Set;

/**
 * Generates the Java code to create the SQL expressions for the WHERE clause.
 */
public class WhereClauseGenerator implements CodeGenerator<Object> {

  private final DbModelWurblet wurblet;

  /**
   * Creates the generator.
   *
   * @param wurblet the model wurblet
   */
  public WhereClauseGenerator(DbModelWurblet wurblet) {
    this.wurblet = wurblet;
  }

  @Override
  public String generate(Object t) throws WurbelException {

    if (t instanceof WurbletArgumentOperator operator) {
      return "        sql.append(Backend." + operator.getSql() + ");\n";
    }

    if (t instanceof WurbletArgumentExpression expression) {
      return "        sql.append(Backend.SQL_LEFT_PARENTHESIS);\n" +
             expression.toCode(this) +
             "        sql.append(Backend.SQL_RIGHT_PARENTHESIS);\n";
    }

    if (t instanceof WurbletArgument argument) {
      StringBuilder buf = new StringBuilder();
      StringBuilder existsBuf = new StringBuilder();    // the EXISTS clause introducing the relations
      Attribute attribute = argument.getAttribute();
      if (argument.isPath()) {
        Set<Relation> existsRelations = argument.getExistsRelations();
        if (existsRelations != null) {
          buf.append("        sql.append(Backend.SQL_EXISTS)\n");
          // (FROM) table1 AS alias1, table2 AS alias 2, ...
          String rightClass;
          String comma = "";
          for (Entity component: argument.getExistsComponents()) {
            ComponentInfo ci = wurblet.createComponentInfo(component);
            rightClass = ci.getRootIdClassName();
            buf.append("           .append(").append(comma).append(rightClass)
               .append(".CLASSVARIABLES.getTableName())\n")
               .append("           .append(getBackend().sqlAsBeforeTableAlias())\n")
               .append("           .append(").append(rightClass)
               .append(".CLASSVARIABLES.getTableAlias())\n");
            comma = "Backend.SQL_COMMA).append(";
            if (ci.isExtraJoinNecessary()) {
              buf.append("           .append(").append(comma).append(ci.getExtraClassName())
                 .append(".CLASSVARIABLES.getTableName())\n")
                 .append("           .append(getBackend().sqlAsBeforeTableAlias())\n")
                 .append("           .append(").append(ci.getExtraClassName())
                 .append(".CLASSVARIABLES.getTableAlias())\n");
            }
          }

          for (Relation relation: existsRelations) {
            rightClass = wurblet.deriveClassNameForEntity(relation.getForeignEntity());
            buf.append("           .append(").append(comma).append(rightClass)
               .append(".CLASSVARIABLES.getTableName())\n")
               .append("           .append(getBackend().sqlAsBeforeTableAlias())\n")
               .append("           .append(").append(rightClass)
               .append(".CLASSVARIABLES.getTableAlias())\n");
            comma = "Backend.SQL_COMMA).append(";
          }

          buf.append("           .append(Backend.SQL_WHERE)\n");

          for (Entity component: argument.getExistsComponents()) {
            ComponentInfo componentInfo = wurblet.createComponentInfo(component);
            rightClass = componentInfo.getRootIdClassName();
            buf.append("           .append(getColumnName(CN_ID)).append(Backend.SQL_EQUAL)\n")
               .append("           .append(").append(rightClass).append(".CLASSVARIABLES.getColumnName(")
               .append(componentInfo.getRootIdColumnName()).append("))\n")
               .append("           .append(Backend.SQL_AND)\n");
            if (componentInfo.isExtraJoinNecessary()) {
              buf.append("           .append(").append(componentInfo.getRootIdClassName())
                 .append(".CLASSVARIABLES.getColumnName(CN_ID)).append(Backend.SQL_EQUAL)\n")
                 .append("           .append(").append(componentInfo.getExtraClassName())
                 .append(".CLASSVARIABLES.getColumnName(CN_ID))\n")
                 .append("           .append(Backend.SQL_AND)\n");
            }
          }

          for (Relation relation: existsRelations) {
            String leftAttribute;
            String rightAttribute;
            boolean isEmbeddingRelation = false;
            if (relation.getRelationType() == RelationType.OBJECT) {
              if (relation.isEmbedded()) {
                isEmbeddingRelation = true;
                leftAttribute = '"' + argument.getEmbeddingColumnPrefix() + relation.getMethodArgs().getFirst().getAttribute().getColumnName() + '"';
              }
              else {
                leftAttribute = wurblet.getColumnNameConstant(relation.getAttribute(), 0);
              }
              rightAttribute = "CN_ID";
            }
            else {
              leftAttribute = "CN_ID";
              rightAttribute = wurblet.getColumnNameConstant(relation.getForeignAttribute(), 0);
            }
            String leftClass = relation.getEntity().equals(wurblet.getEntity()) ? "" :
                                 (wurblet.deriveClassNameForEntity(relation.getEntity()) + ".");
            String leftClassVariables = leftClass.isEmpty() || isEmbeddingRelation ? "" : (leftClass + "CLASSVARIABLES.");
            rightClass = relation.getForeignEntity().equals(wurblet.getEntity()) ? "" :
                           (wurblet.deriveClassNameForEntity(relation.getForeignEntity()) + ".");
            buf.append("           .append(").append(leftClassVariables).append("getColumnName(")
               .append(leftAttribute).append(")).append(Backend.SQL_EQUAL)\n")
               .append("           .append(").append(rightClass).append("CLASSVARIABLES.getColumnName(")
               .append(rightClass).append(rightAttribute).append("))\n")
               .append("           .append(Backend.SQL_AND)\n");

            List<MethodArgument> methodArguments = relation.getMethodArgs();
            for (MethodArgument methodArgument: methodArguments.subList(1, methodArguments.size())) {
              Attribute methodAttribute = methodArgument.getForeignAttribute();
              buf.append("           .append(").append(rightClass).append("CLASSVARIABLES.getColumnName(")
                 .append(rightClass).append(wurblet.getColumnNameConstant(methodAttribute, 0)).append("))\n");
              if (methodArgument.isValue()) {
                buf.append("           .append(Backend.SQL_EQUAL_PAR)\n");
                for (int columnIndex = 1; columnIndex < wurblet.getEffectiveDataType(attribute).getColumnCount(null); columnIndex++) {
                  buf.append("           .append(Backend.SQL_AND)\n")
                     .append("           .append(").append(rightClass).append("CLASSVARIABLES.getColumnName(")
                     .append(rightClass).append(wurblet.getColumnNameConstant(attribute, columnIndex)).append("))\n")
                     .append("           .append(Backend.SQL_EQUAL_PAR)\n");
                }
              }
              else {
                buf.append("           .append(Backend.SQL_EQUAL).append(")
                   .append(leftClassVariables).append("getColumnName(").append(leftClassVariables)
                   .append(wurblet.getColumnNameConstant(methodArgument.getAttribute(), 0)).append("))\n");
              }
              buf.append("           .append(Backend.SQL_AND)\n");
            }
          }
          buf.deleteCharAt(buf.length() - 1).append(";\n");
          // the EXISTS clause is shared by all arguments of the clause and gets its own condition
          existsBuf.append(buf);
          buf.setLength(0);
        }

        String compKeyClass = wurblet.deriveClassNameForEntity(attribute.getEmbeddingPath() == null ?
                                                                 attribute.getEntity() :
                                                                 attribute.getEmbeddingPath().getFirst());

        for (int columnIndex = 0; columnIndex < argument.getDataType().getColumnCount(null); columnIndex++) {
          if (columnIndex > 0 && argument.getColumnIndex() == null) {
            buf.append("        sql.append(Backend.SQL_AND);\n");
          }
          if (argument.getColumnIndex() == null || columnIndex == argument.getColumnIndex()) {
            buf.append("        sql.append(").append(compKeyClass).append(".CLASSVARIABLES.getColumnName(");
            if (attribute.getEmbeddingPath() != null) {
              try {
                buf.append('"').append(attribute.getColumnName(null, columnIndex)).append("\"))\n");
              }
              catch(ModelException mx) {
                throw new WurbelException("cannot determine embedded column name", mx);
              }
            }
            else {
              buf.append(compKeyClass).append('.').append(wurblet.getColumnNameConstant(attribute, columnIndex)).append("))\n");
            }
            buf.append("           ").append(wurblet.createRelopCode(argument)).append(";\n");
          }
        }
      }
      else {
        for (int columnIndex = 0; columnIndex < argument.getDataType().getColumnCount(null); columnIndex++) {
          if (columnIndex > 0 && argument.getColumnIndex() == null) {
            buf.append("        sql.append(Backend.SQL_AND);\n");
          }
          if (argument.getColumnIndex() == null || columnIndex == argument.getColumnIndex()) {
            if (wurblet.isPathAllowed()) {
              if (wurblet.getEntity().getHierarchyInheritanceType() == InheritanceType.MULTI) {
                buf.append("        sql.append(")
                   .append(wurblet.deriveClassNameForEntity(attribute.getEntity()))
                   .append(".CLASSVARIABLES.getColumnName(")
                   .append(wurblet.getColumnNameConstant(attribute, columnIndex)).append("));\n");
              }
              else  {
                if (wurblet.isPdo()) {
                  buf.append("        sql.append(getColumnName(");
                  if (attribute.isEmbedded()) {
                    buf.append('"').append(attribute.getColumnName()).append('"');
                  }
                  else {
                    buf.append(wurblet.getColumnNameConstant(attribute, columnIndex));
                  }
                  buf.append("));\n");
                }
                else  {
                  buf.append("        sql.append(").append(wurblet.getColumnNameConstant(attribute, columnIndex)).append(");\n");
                }
              }
            }
            else  {
              buf.append("        sql.append(");
              if (attribute.isEmbedded()) {
                buf.append("getColumnPrefix() + ");
              }
              buf.append(wurblet.getColumnNameConstant(attribute, columnIndex)).append(");\n");
            }
            buf.append("        sql").append(wurblet.createRelopCode(argument)).append(";\n");
          }
        }
      }

      // the condition itself is generated only if its boolean method argument is true.
      // the EXISTS clause is generated if at least one of its conditions is applicable.
      String clauseCondition = argument.getExistsArguments() == null ? null :
                                 wurblet.createOptionalCondition(argument.getExistsArguments());
      StringBuilder code = new StringBuilder();
      code.append(wrapOptional(existsBuf.toString(), clauseCondition))
          .append(wrapOptional(buf.toString(), argument.isOptional() ? argument.getOptionalName() : null));
      if (argument.isEndOfExistsClause()) {
        code.append(wrapOptional("        sql.append(Backend.SQL_RIGHT_PARENTHESIS);\n", clauseCondition));
      }
      return code.toString();
    }

    return "        sql.append(" + t + ");\n";
  }


  /**
   * Encloses the generated code in an if-statement, if the code is optional.
   *
   * @param code the generated code, may be empty
   * @param condition the java boolean expression, null if the code is not optional
   * @return the code
   */
  private String wrapOptional(String code, String condition) {
    if (condition == null || code.isEmpty()) {
      return code;
    }
    StringBuilder buf = new StringBuilder("        if (").append(condition).append(") {\n");
    for (String line: code.split("\n")) {
      buf.append("  ").append(line).append('\n');
    }
    buf.append("        }\n");
    return buf.toString();
  }

}
