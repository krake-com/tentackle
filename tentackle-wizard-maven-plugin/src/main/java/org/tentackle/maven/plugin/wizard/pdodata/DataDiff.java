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

package org.tentackle.maven.plugin.wizard.pdodata;

import org.tentackle.bind.Bindable;
import org.tentackle.model.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Data node to be displayed in a tree table.<br>
 * The node can be used in 2 modes:
 * <ol>
 *   <li>to display the PDO tree</li>
 *   <li>to compare two PDO trees</li>
 * </ol>
 */
public class DataDiff implements DataNode {

  private final DataNode left;           // the left data, null if no such data in left object
  private final DataNode right;          // the right data, null if no such data in right object
  private final boolean different;       // true if this node or a sub node differs
  private final List<DataDiff> nodes;    // the sub nodes

  /**
   * Creates a DataDiff.
   *
   * @param left the left data, null if no such data in left object
   * @param right the right data, null if no such data in right object
   * @param different true if this node or a sub node differs
   * @param nodes the sub nodes
   */
  public DataDiff(DataNode left, DataNode right, boolean different, List<DataDiff> nodes) {
    this.left = left;
    this.right = right;
    this.different = different;
    this.nodes = nodes;
  }

  /**
   * Gets the left data, null if no such data in left object.
   *
   * @return the left data, null if no such data in left object
   */
  public DataNode getLeft() {
    return left;
  }

  /**
   * Gets the right data, null if no such data in right object.
   *
   * @return the right data, null if no such data in right object
   */
  public DataNode getRight() {
    return right;
  }

  /**
   * Returns whether this node or a sub node differs.
   *
   * @return true if this node or a sub node differs
   */
  public boolean isDifferent() {
    return different;
  }

  /**
   * Gets the sub nodes.
   *
   * @return the sub nodes
   */
  @Override
  public List<DataDiff> getNodes() {
    return nodes;
  }

  @Override
  public String getComment() {
    return null;
  }

  @Override
  public boolean isHidden() {
    return false;
  }

  @Override
  @Bindable(ordinal = 2)
  public String getType() {
    if (left != null) {
      return left.getType();
    }
    return right.getType();
  }

  @Override
  public String getName() {
    if (left != null) {
      return left.getName();
    }
    return right.getName();
  }

  @Override
  public String getValue() {
    if (left != null) {
      return left.getValue();
    }
    return right.getValue();
  }

  @Override
  public String toString() {
    if (left != null) {
      return left.toString();
    }
    return right.toString();
  }

  @Override
  public String getNodeType() {
    if (left != null) {
      return left.getNodeType();
    }
    return right.getNodeType();
  }

  /**
   * Gets the node to display.
   *
   * @return the left node, or the right node if there is no left node
   */
  @Bindable(ordinal = 1)
  public DataNode getNode() {
    if (left != null) {
      return left;
    }
    return right;
  }

  /**
   * Gets the left value.
   *
   * @return null if none
   */
  @Bindable(ordinal = 3)
  public String getLeftValue() {
    return left == null ? null : left.getValue();
  }

  /**
   * Gets the right value.
   *
   * @return null if none
   */
  @Bindable(ordinal = 4)
  public String getRightValue() {
    return right == null ? null : right.getValue();
  }


  /**
   * Returns whether the left node is present.
   *
   * @return true if left != null
   */
  public boolean isLeftPresent() {
    return left != null;
  }

  /**
   * Returns whether the right node is present.
   *
   * @return true if right != null
   */
  public boolean isRightPresent() {
    return right != null;
  }

  @Override
  public void setConfigurationPath(String configurationPath) {
    throw new UnsupportedOperationException("not applicable to diffs");
  }

  @Override
  public String getConfigurationPath() {
    return null;
  }

  @Override
  public SqlCondition getSqlCondition() {
    return left != null ? left.getSqlCondition() : right.getSqlCondition();
  }

  @Override
  public Entity getEntity() {
    return left != null ? left.getEntity() : right.getEntity();
  }

  /**
   * Compares the data of a PDO between two locations.
   *
   * @param left the data of the left location, must not be null
   * @param right the data of the right location, null to only show the data without comparing
   * @return the comparison
   */
  public static DataDiff compare(DataObject left, DataObject right) {
    boolean rootIdDiffers = left != null && right != null && left.getId() != right.getId();
    return compareImpl(Objects.requireNonNull(left), right, right == null, rootIdDiffers);
  }



  // --- all static private below ---

  @SuppressWarnings("unchecked")
  private static DataDiff compareImpl(DataNode left, DataNode right, boolean rightMissing, boolean rootIdDiffers) {

    if (left != null && right != null && left.getClass() != right.getClass()) {
      throw new IllegalArgumentException("cannot compare " + left.getClass() + " with " + right.getClass());
    }

    List<DataDiff> diffNodes;
    boolean different;

    if (left == null && right == null) {
      diffNodes = null;
      different = false;
    }
    else if (left instanceof DataItem || right instanceof DataItem) {
      diffNodes = null;
      if (left != null && right != null) {
        DataItem leftItem = (DataItem) left;
        DataItem rightItem = (DataItem) right;
        if (leftItem.isIdOfCompositeRelation() && rightItem.isIdOfCompositeRelation() ||
            leftItem.getAttribute() != null && leftItem.getAttribute().isImplicit() &&
            rightItem.getAttribute() != null && rightItem.getAttribute().isImplicit()) {
          different = false;
        }
        else {
          different = !Objects.equals(left.getValue(), right.getValue());
        }
      }
      else {
        different = false;
      }
    }
    else {    // DataObject or DataList
      diffNodes = new ArrayList<>();
      List<? extends DataNode> leftNodes = left == null ? null : left.getNodes();
      List<? extends DataNode> rightNodes = right == null ? null : right.getNodes();
      int leftSize = leftNodes == null ? 0 : leftNodes.size();
      int rightSize = rightNodes == null ? 0 : rightNodes.size();

      if (left instanceof DataObject || right instanceof DataObject) {
        DataObject leftObject = (DataObject) left;
        DataObject rightObject = (DataObject) right;
        long leftSerial;
        long rightSerial;
        long leftId;
        long rightId;

        if (leftObject == null) {
          leftId = 0;
          leftSerial = 0;
        }
        else {
          NonCompositeRelation nmRelation = leftObject.getNmRelation();
          if (nmRelation != null) {
            // compare the PDO the N:M-link points to
            leftId = nmRelation.getRelatedPdo().getId();
            leftSerial = nmRelation.getRelatedPdo().getSerial();
          }
          else {
            leftId = leftObject.getId();
            leftSerial = leftObject.getSerial();
          }
        }

        if (rightObject == null) {
          rightId = 0;
          rightSerial = 0;
        }
        else {
          NonCompositeRelation nmRelation = rightObject.getNmRelation();
          if (nmRelation != null) {
            // compare the PDO the N:M-link points to
            rightId = nmRelation.getRelatedPdo().getId();
            rightSerial = nmRelation.getRelatedPdo().getSerial();
          }
          else {
            rightId = rightObject.getId();
            rightSerial = rightObject.getSerial();
          }
        }

        different = !rightMissing && (leftSerial != rightSerial || leftId != rightId);
        int size = Math.max(leftSize, rightSize);
        for (int i=0; i < size; i++) {
          DataNode leftNode = i < leftSize ? leftNodes.get(i) : null;
          DataNode rightNode = i < rightSize ? rightNodes.get(i) : null;
          DataDiff diffNode = compareImpl(leftNode, rightNode, rightMissing, rootIdDiffers);
          diffNodes.add(diffNode);
          different |= !rightMissing && diffNode.different;
        }
      }
      else {    // DataList
        different = false;
        // merge both lists in the order of compareElements
        List<DataObject> leftObjects = sortForMerge((List<DataObject>) leftNodes, rightMissing, rootIdDiffers);
        List<DataObject> rightObjects = sortForMerge((List<DataObject>) rightNodes, rightMissing, rootIdDiffers);
        int leftIndex = 0;
        int rightIndex = 0;
        DataObject nextLeft = null;
        DataObject nextRight = null;
        for (;;) {
          if (nextLeft == null && leftIndex < leftSize) {
            nextLeft = leftObjects.get(leftIndex++);
          }
          if (nextRight == null && rightIndex < rightSize) {
            nextRight = rightObjects.get(rightIndex++);
          }

          DataDiff diffNode;

          if (nextLeft == null && nextRight == null) {
            break;
          }
          else if (nextLeft == null) {
            diffNode = compareImpl(null, nextRight, rightMissing, rootIdDiffers);
            nextRight = null;
          }
          else if (nextRight == null) {
            diffNode = compareImpl(nextLeft, null, rightMissing, rootIdDiffers);
            nextLeft = null;
          }
          else {
            int cmp = compareElements(nextLeft, nextRight, rootIdDiffers);
            if (cmp < 0) {
              // gap in right list
              diffNode = compareImpl(nextLeft, null, rightMissing, false);
              nextLeft = null;
            }
            else if (cmp > 0) {
              // gap in left list
              diffNode = compareImpl(null, nextRight, rightMissing, false);
              nextRight = null;
            }
            else {
              // same ID, same N:M-related PDO, or root ID differs
              diffNode = compareImpl(nextLeft, nextRight, rightMissing, rootIdDiffers);
              nextLeft = null;
              nextRight = null;
            }
          }

          diffNodes.add(diffNode);
          different |= !rightMissing && diffNode.different;
        }
      }
    }

    if (diffNodes != null && diffNodes.isEmpty() &&
        (left == null || left instanceof DataObject) &&
        (right == null || right instanceof DataObject)) {
      diffNodes = null;   // no sub nodes: make it a leaf (data lists keep their empty list)
    }

    return new DataDiff(left, right, different, diffNodes);
  }

  /**
   * Compares two list elements for merging.<br>
   * Elements of N:M relations are compared by their related PDOs, all others by their IDs.
   * If the IDs of the root objects differ, the elements are compared by position.
   *
   * @param left the left element
   * @param right the right element
   * @param rootIdDiffers true if the IDs of the root objects differ
   * @return &lt; 0 if gap in right list, &gt; 0 if gap in left list, 0 if elements match
   */
  static int compareElements(DataObject left, DataObject right, boolean rootIdDiffers) {
    if (left.getNmRelation() != null && right.getNmRelation() != null) {
      return left.getNmRelation().toString().compareTo(right.getNmRelation().toString());
    }
    return rootIdDiffers ? 0 : Long.compare(left.getId(), right.getId());
  }

  /**
   * Sorts the elements of a list to be merged by {@link #compareElements}.<br>
   * N:M lists are already sorted by the {@link DataNodeFactory}, but the elements of 1:N lists
   * are in the order of the select, which is not necessarily by ID.
   *
   * @param objects the list elements, null if none
   * @param rightMissing true if just show the data and don't compare
   * @param rootIdDiffers true if the IDs of the root objects differ
   * @return the sorted elements, empty if none
   */
  private static List<DataObject> sortForMerge(List<DataObject> objects, boolean rightMissing, boolean rootIdDiffers) {
    if (objects == null) {
      return List.of();
    }
    if (rightMissing || rootIdDiffers || objects.stream().anyMatch(o -> o.getNmRelation() != null)) {
      return objects;   // keep the order
    }
    List<DataObject> sorted = new ArrayList<>(objects);
    sorted.sort(Comparator.comparingLong(DataObject::getId));
    return sorted;
  }

}
