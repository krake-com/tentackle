/*
 * ${name}.java
 *
 */

package ${package};

import org.tentackle.swing.FormTableEntry;


/**
 *
 * @author ${user}
 */
public class ${name} extends FormTableEntry {

  /**
   * @> .$classname/columns
   * # <COLUMN-INDEX> "Column Text"
   * @<
   *
   * @wurblet columnNames IntMap .$classname/columns --static
   */



  private SomeObject object;    // the data object


  /**
   * Creates an instance of ${name} from a data object.
   *
   * @param object the data object
   */
  public ${name}(SomeObject object)  {
    this.object = object;
  }


  /**
   * Creates a new ${name} from an object.
   *
   * @param object the data object
   * @return the form table entry
   */
  @Override
  public FormTableEntry newInstanceOf(Object object) {
    return new ${name}((SomeObject)object);
  }


  /**
   * Gets the current data object.
   *
   * @return the object
   */
  @Override
  public Object getObject() {
    return object;
  }

  /**
   * Describes the column name.
   * This may be a symbolic name if getDisplayedColumnName()
   * is overridden.
   *
   * @param mColumn the datamodel-column
   * @return the name of the column
   */
  @Override
  public String getColumnName(int mColumn) {
    return columnNames[mColumn];
  }

  /**
   * Determines the number of data-model columns.
   * Notice that not all columns may actually be visible.
   *
   * @return the number of columns
   */
  @Override
  public int getColumnCount() {
    return columnNames.length;
  }

  /**
   * Get the column-object for this entry in a given column.
   *
   * @param mColumn the datamodel-column
   * @return the column data object
   */
  @Override
  public Object getValueAt(int mColumn)  {
    try {
      switch (mColumn)  {
        case COL1:
      }
    }
    catch (Exception e) {}
    return null;
  }

}
