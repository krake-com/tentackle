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

package org.tentackle.model.print;

import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.Relation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prints the model of an entity.
 *
 * @author harald
 */
public class EntityPrinter {

  private final Entity entity;
  private final PrintConfiguration configuration;
  private final StringBuilder buf;

  /**
   * Creates an entity printer.
   *
   * @param entity the entity to print
   * @param configuration the printing configuration
   */
  public EntityPrinter(Entity entity, PrintConfiguration configuration) {
    this.entity = entity;
    this.configuration = configuration;
    buf = new StringBuilder();
  }

  /**
   * Creates a pretty print of the entity model.
   *
   * @return the printed entity model
   */
  public String print() {
    buf.setLength(0);

    if (configuration.isPrintingAsComment()) {
      buf.append("/*\n");
      if (configuration.isUsingVariables()) {
        buf.append(" * @> $mapping\n");
      }
      else {
        buf.append(" * @> ").append(entity.getTableName()).append(".map\n");
      }
    }

    line("\n");
    line("# ").append(entity.getOptions().getComment()).append('\n');
    line("name := ");
    if (configuration.isUsingVariables()) {
      buf.append("$classname\n");
    }
    else {
      buf.append(entity.getName()).append('\n');
    }
    if (entity.getTableName() != null) {
      line("table := ");
      if (configuration.isUsingVariables()) {
        buf.append("$tablename\n");
      }
      else {
        buf.append(entity.getTableName()).append('\n');
      }
    }
    if (entity.getClassId() != 0) {
      line("id := ");
      if (configuration.isUsingVariables()) {
        buf.append("$classid\n");
      }
      else {
        buf.append(entity.getClassId()).append('\n');
      }
    }
    line("integrity := ");
    if (configuration.isUsingVariables()) {
      buf.append("$integrity\n");
    }
    else {
      buf.append(entity.getIntegrity().toString().toLowerCase(Locale.ROOT)).append('\n');
    }
    if (entity.getInheritanceType() != InheritanceType.NONE) {
      line("inheritance := ").append(entity.getInheritanceType().toString().toLowerCase(Locale.ROOT)).append('\n');
    }
    if (entity.getSuperEntityName() != null) {
      line("extends := ").append(entity.getSuperEntityName()).append('\n');
    }
    if (entity.getDefinedTableAlias() != null) {
      line("alias := ").append(entity.getDefinedTableAlias()).append('\n');
    }

    line("\n");
    line("## attributes\n");
    String globalOptions = new GlobalOptionsPrinter(entity, configuration).print();
    if (!"[]".equals(globalOptions)) {
      line(globalOptions).append('\n');
    }
    buf.append(new AttributeSectionPrinter(entity, configuration).print());

    line("\n");
    line("## indexes\n");
    for (Index index: entity.getIndexes()) {
      buf.append(new IndexPrinter(index, configuration).print());
    }

    line("\n");
    line("## relations\n");
    boolean needBlankLine = false;
    for (Relation relation: entity.getRelations()) {
      if (needBlankLine) {
        line("\n");
      }
      else {
        needBlankLine = true;
      }
      buf.append(new RelationPrinter(relation, configuration).print());
    }

    line("\n");
    line("## annotations\n");
    needBlankLine = false;
    for (Attribute attribute: entity.getAttributes()) {
      List<String> annotations = new ArrayList<>();
      for (String anno: attribute.getOptions().getAnnotations()) {
        if (!configuration.isOptionAnnotation(anno)) {
          annotations.add(anno);
        }
      }
      if (!annotations.isEmpty()) {
        if (needBlankLine) {
          line("\n");
        }
        else {
          needBlankLine = true;
        }
        line(attribute.getName()).append(":\n");
        int size = annotations.size();
        int ndx = 0;
        for (String annotation: annotations) {
          line("    ").append(new AnnotationPrinter(annotation).print());
          if (++ndx < size) {
            buf.append(',');
          }
          buf.append('\n');
        }
      }
    }

    line("\n");
    if (configuration.isPrintingAsComment()) {
      buf.append(" * @<\n */\n");
    }

    return buf.toString();
  }


  /**
   * Begin a new line.
   *
   * @param str the leading part of the line
   */
  private StringBuilder line(String str) {
    if (configuration.isPrintingAsComment()) {
      buf.append(" * ");
    }
    buf.append(str);
    return buf;
  }

}
