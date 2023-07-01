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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Datanode to be displayed in a tree table.<br>
 * The node can be used in 2 modes:
 * <ol>
 *   <li>to display the pdo tree</li>
 *   <li>to compare two pdo trees</li>
 * </ol>
 */
public class DataDiff implements DataNode {

  private final DataNode left;           // the left data, null if no such data in right object
  private final DataNode right;          // the right data, null if no such data in right object
  private final boolean different;       // true if this node or a sub node differs
  private final List<DataDiff> nodes;    // the sub nodes

  /**
   * Creates a DataDiff.
   *
   * @param left the left data, null if no such data in right object
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
   * Gets true if this node or a sub node differs.
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
    throw new UnsupportedOperationException("no applicable to diffs");
  }

  @Override
  public String getConfigurationPath() {
    return null;
  }

  /**
   * Compares the data of a PDO between two locations.
   *
   * @param left the data of the left location, must not be null
   * @param right the data of the right location, null if just show the data and don't compare
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

    List<DataDiff> nodes;
    boolean different;

    if (left == null && right == null) {
      nodes = null;
      different = false;
    }
    else if (left instanceof DataItem || right instanceof DataItem) {
      nodes = null;
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
      nodes = new ArrayList<>();
      List<? extends DataNode> leftNodes = left == null ? null : left.getNodes();
      List<? extends DataNode> rightNodes = right == null ? null : right.getNodes();
      int leftSize = leftNodes == null ? 0 : leftNodes.size();
      int rightSize = rightNodes == null ? 0 : rightNodes.size();

      if (left instanceof DataObject || right instanceof DataObject) {
        DataObject leftObject = (DataObject) left;
        DataObject rightObject = (DataObject) right;
        long leftSerial = leftObject == null ? 0 : leftObject.getSerial();
        long rightSerial = rightObject == null ? 0 : rightObject.getSerial();
        different = !rightMissing && leftSerial != rightSerial;
        int size = Math.max(leftSize, rightSize);
        for (int i=0; i < size; i++) {
          DataNode leftNode = i < leftSize ? leftNodes.get(i) : null;
          DataNode rightNode = i < rightSize ? rightNodes.get(i) : null;
          DataDiff node = compareImpl(leftNode, rightNode, rightMissing, rootIdDiffers);
          nodes.add(node);
          different |= !rightMissing && node.different;
        }
      }
      else {    // DataList
        different = false;
        // we must sort by ID because collections may be returned in any order
        List<DataObject> leftObjects = (List<DataObject>) leftNodes;
        List<DataObject> rightObjects = (List<DataObject>) rightNodes;
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

          DataDiff node;

          if (nextLeft == null && nextRight == null) {
            break;
          }
          else if (nextLeft == null) {
            node = compareImpl(null, nextRight, rightMissing, rootIdDiffers);
            nextRight = null;
          }
          else if (nextRight == null) {
            node = compareImpl(nextLeft, null, rightMissing, rootIdDiffers);
            nextLeft = null;
          }
          else {
            if (!rootIdDiffers && nextLeft.getId() < nextRight.getId()) {
              // gap in right list
              node = compareImpl(nextLeft, null, rightMissing, false);
              nextLeft = null;
            }
            else if (!rootIdDiffers && nextLeft.getId() > nextRight.getId()) {
              // gap in left list
              node = compareImpl(null, nextRight, rightMissing, false);
              nextRight = null;
            }
            else {
              // same ID or root ID differs
              node = compareImpl(nextLeft, nextRight, rightMissing, rootIdDiffers);
              nextLeft = null;
              nextRight = null;
            }
          }

          nodes.add(node);
          different |= !rightMissing && node.different;
        }
      }
    }

    if (nodes != null && nodes.isEmpty() &&
        (left == null || left instanceof DataObject) &&
        (right == null || right instanceof DataObject)) {
      nodes = null;   // both missing
    }

    return new DataDiff(left, right, different, nodes);
  }

}
