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

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.tentackle.common.StringHelper;
import org.tentackle.model.Entity;
import org.tentackle.model.MethodArgument;
import org.tentackle.model.Relation;
import org.tentackle.model.RelationType;

/**
 * Support methods for model comment.
 *
 * @author harald
 */
public class ModelCommentSupport {


  /**
   * Prints the attribute holding the link.
   *
   * @param relation the relation
   * @param out the output stream
   */
  public static void printVia(Relation relation, PrintStream out) {
    out.print(" via ");
    if (relation.getAttribute() != null) {
      out.print(relation.getAttribute());
    }
    else if (relation.getForeignEntity() != null && relation.getForeignAttribute() != null) {
      String entityName = relation.getForeignEntity().getName();
      if (relation.getForeignEntity().getTableAlias() != null) {
        entityName = relation.getForeignEntity().getTableAlias();   // is shorter and better to read
      }
      out.print(entityName);
      out.print('.');
      out.print(relation.getForeignAttribute());
    }
    else if (!relation.getMethodArgs().isEmpty()) {
      out.print(relation.getMethodArgs().get(0));
    }
    else {
      out.print("?");
    }
    if (relation.getMethodArgs().size() > 1) {
      boolean firstSkipped = false;
      for (MethodArgument arg: relation.getMethodArgs()) {
        if (firstSkipped) {
          out.print(" & ");
          out.print(arg);
        }
        firstSkipped = true;
      }
    }
  }

  /**
   * Prints the referencing relation.
   *
   * @param entity the referenced entity
   * @param relation the relation
   * @param indent the indent string
   * @param out the output stream
   */
  public static void printReferencedBy(Entity entity, Relation relation, String indent, PrintStream out) {
    Entity referencingEntity = relation.getEntity();
    Collection<Entity> roots = referencingEntity.getRootEntities();
    if (roots.size() != 1 || !roots.contains(entity)) {
      out.print(indent);
      out.print(referencingEntity);
      printRootEntities(referencingEntity, roots, out);
      if (relation.isDeepReference()) {
        out.print(" deeply");
      }

      Relation nmRel = relation.getNmRelation();
      Relation rel = relation;
      if (nmRel == null && relation.getForeignRelation() != null) {
        nmRel = relation.getForeignRelation().getNmRelation();
        if (nmRel != null) {
          rel = relation.getForeignRelation();
        }
      }

      if (nmRel != null && !entity.equals(relation.getForeignEntity())) {
        out.print(" via ");
        out.print(relation.getForeignEntity());
        out.print(" as ");
        out.print(relation.getName());
        out.println(" [N:M]");
      }
      else  {
        printVia(relation, out);
        if (!relation.getName().equalsIgnoreCase(referencingEntity.toString())) {
          out.print(" as " + StringHelper.firstToLower(relation.getMethodNameSuffix()));
        }
        if (relation.getRelationType() == RelationType.LIST) {
          out.println(" [1:N]");
        }
        else {
          out.println(" [1:1]");
        }
      }
    }
  }


  /**
   * Recursively prints the components and sub-entities of an entity.
   *
   * @param allRelations set to avoid recursion loops
   * @param compositeRelations the composite relations
   * @param subEntities the sub entities
   * @param indent the indent string
   * @param out the output stream
   */
  public static void printComponents(Set<Relation> allRelations,
                                     Collection<Relation> compositeRelations, Collection<Entity> subEntities,
                                     String indent, PrintStream out) {

    for (Relation relation: compositeRelations) {

      boolean relationNotAlreadyPrinted = allRelations.add(relation);
      Entity component = relation.getForeignEntity();

      if (relationNotAlreadyPrinted) {
        for (Entity sub: subEntities) {
          if (!sub.getComponents().isEmpty()) {
            out.print(indent);
            out.print("^ ");
            out.println(sub);
            List<Relation> subCompositeRelations = new ArrayList<>();
            for (Relation subRelation : sub.getRelationsIncludingInherited()) {
              if (subRelation.isComposite()) {
                subCompositeRelations.add(subRelation);
              }
            }
            printComponents(allRelations, subCompositeRelations, sub.getSubEntities(), indent + "    ", out);
          }
        }
      }

      out.print(indent);
      out.print("+ ");
      out.print(component);

      printVia(relation, out);

      if (!relation.getName().equalsIgnoreCase(component.toString())) {
        out.print(" as " + StringHelper.firstToLower(relation.getName()));
      }
      if (relation.getNmRelation() != null) {
        out.print(" [N:M] to ");
        Entity nmEntity = relation.getNmRelation().getForeignEntity();
        out.print(nmEntity);
        if (relation.getNmMethodName() != null &&
            !relation.getNmMethodName().equalsIgnoreCase(nmEntity.toString())) {
          out.print(" as " + StringHelper.firstToLower(relation.getNmMethodName()));
        }
      }
      else  {
        if (relation.getRelationType() == RelationType.LIST) {
          out.print(" [1:N]");
        }
        else {
          out.print(" [1:1]");
        }
      }

      List<Relation> componentCompositeRelations = new ArrayList<>();
      for (Relation subRelation : component.getRelationsIncludingInherited()) {
        if (subRelation.isComposite()) {
          componentCompositeRelations.add(subRelation);
        }
      }

      if (relationNotAlreadyPrinted) {
        out.println();
        printComponents(allRelations, componentCompositeRelations, component.getSubEntities(), indent + "    ", out);
      }
      else {
        if (relation.getForeignEntity().isComposite()) {
          out.println(" ...");
        }
        else {
          out.println();
        }
      }
    }
  }

  /**
   * Recursively prints the sub-entities of an entity.
   *
   * @param subEntities the sub entities
   * @param indent the indent string
   * @param out the output stream
   */
  public static void printSubEntities(Collection<Entity> subEntities, String indent, PrintStream out) {
    for (Entity sub: subEntities) {
      out.print(indent);
      out.print("^ ");
      out.println(sub);
      printSubEntities(sub.getSubEntities(), indent + "    ", out);
    }
  }


  /**
   * Prints outgoing non-composite relations from components.
   *
   * @param entity the root entity
   * @param relations the outgoing relations
   * @param rootsFromForeignEntity true if print roots from relation.foreignEntity else relation.entity
   * @param indent the indent string
   * @param out the output stream
   */
  public static void printNonCompositeRelations(Entity entity, List<Relation> relations,
                                                boolean rootsFromForeignEntity,
                                                String indent, PrintStream out) {

    List<Relation> sortedRelations = new ArrayList<>();

    for (Relation relation: relations) {
      if (!relation.isComposite() && !relation.getForeignEntity().equals(entity)) {
        sortedRelations.add(relation);
      }
    }

    Collections.sort(sortedRelations, (o1, o2) -> {
      return o1.getForeignEntity().getName().compareTo(o2.getForeignEntity().getName());
    });

    for (Relation relation: sortedRelations) {
      out.print(indent);
      out.print(relation.getForeignEntity());
      if (rootsFromForeignEntity) {
        printRootEntities(entity, relation.getForeignEntity().getRootEntities(), out);
      }
      if (!relation.getEntity().equals(entity)) {   // some sub entity
        out.print(" from ");
        out.print(relation.getEntity());
        if (!rootsFromForeignEntity) {
          printRootEntities(relation.getEntity(), relation.getEntity().getRootEntities(), out);
        }
      }
      printVia(relation, out);
      if (relation.isReversed()) {
        out.print(" reversed");
      }
      if (relation.isDeepReference()) {
        out.print(" deeply");
      }
      if (relation.getRelationType() == RelationType.LIST && !relation.isReversed()) {
        out.print(" [1:N]");
      }
      else {
        Relation nmRel = relation.getDefiningNmRelation();
        if (nmRel != null && entity.equals(nmRel.getEntity())) {
          out.print(" [N:M]");
        }
        else {
          out.print(" [1:1]");
        }
      }
      out.println();
    }
  }


  /**
   * Prints the root entities for a component or other entity.
   *
   * @param entity the entity to ignore in roots
   * @param rootEntities the root entities
   * @param out the output stream
   */
  private static void printRootEntities(Entity entity, Collection<Entity> rootEntities, PrintStream out) {
    List<Entity> roots = new ArrayList<>(rootEntities);
    if (roots.size() == 1 && entity.equals(roots.get(0))) {
      roots.clear();
    }

    if (!roots.isEmpty()) {

      Collections.sort(roots, (o1, o2) -> {
        return o1.getName().compareTo(o2.getName());
      });

      out.print(" (");
      boolean needComma = false;
      for (Entity rootEntity : roots) {
        if (needComma) {
          out.print(", ");
        }
        else  {
          needComma = true;
        }
        out.print(rootEntity);
      }
      out.print(")");
    }
  }


  private ModelCommentSupport() {}

}
