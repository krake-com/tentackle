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

import org.tentackle.common.StringHelper;
import org.tentackle.model.Attribute;
import org.tentackle.model.Entity;
import org.tentackle.model.Index;
import org.tentackle.model.InheritanceType;
import org.tentackle.model.Relation;
import org.tentackle.model.SourceInfo;

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

    if (configuration.asComment()) {
      if (configuration.useVariables()) {
        buf.append("/*\n * @{\n");
        if (entity.getTableName() != null) {
          buf.append(" * tablename = ").append(entity.getTableName()).append('\n')
             .append(" * mapping = $model/$tablename.map\n");
        }
        else {
          buf.append(" * mapping = $model/$classname.map\n");
        }
        if (entity.getClassId() != 0) {
          buf.append(" * classid = ").append(entity.getClassId()).append('\n');
        }
        buf.append(" * @}\n */\n\n");
      }

      buf.append("/*\n");
      if (configuration.useVariables()) {
        buf.append(" * @> $mapping\n");
      }
      else {
        buf.append(" * @> ").append(entity.getTableName()).append(".map\n");
      }
    }
    else if (entity.getSourceInfo() != null) {
      // if used as model input: set the complete source info as if from a wurbelizer here-doc.
      // there is no such info if the model source was loaded from a jar or the classpath -> omit the line
      SourceInfo sourceInfo = entity.getSourceInfo();
      buf.append("#@").append(sourceInfo.getFileName());
      if (sourceInfo.getLineNumber() > 0) {
        // wurbelizer source element offsets start at 0
        buf.append(':').append(sourceInfo.getLineNumber() - 1);
      }
      buf.append('\n');
    }

    line("\n");
    if (!StringHelper.isAllWhitespace(entity.getOptions().getComment())) {
      line("# ").append(entity.getOptions().getComment()).append('\n');
    }
    line("name := ");
    if (configuration.useVariables()) {
      buf.append("$classname\n");
    }
    else {
      buf.append(entity.getName()).append('\n');
    }
    if (entity.getTableName() != null) {
      line("table := ");
      if (configuration.useVariables()) {
        buf.append("$tablename\n");
      }
      else {
        buf.append(entity.getTableName()).append('\n');
      }
    }
    if (entity.getClassId() != 0) {
      line("id := ");
      if (configuration.useVariables()) {
        buf.append("$classid\n");
      }
      else {
        buf.append(entity.getClassId()).append('\n');
      }
    }
    line("integrity := ");
    if (configuration.useVariables()) {
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
    if (configuration.asComment()) {
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
    if (configuration.asComment()) {
      buf.append(" * ");
    }
    buf.append(str);
    return buf;
  }

}
