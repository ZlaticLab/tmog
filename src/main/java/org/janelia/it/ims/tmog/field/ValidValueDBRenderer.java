package org.janelia.it.ims.tmog.field;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * This class supports the rendering of a valid value cell
 * within the file table.
 *
 * @author Andy Stoychev
 */
public class ValidValueDBRenderer extends DefaultTableCellRenderer {

    public Component getTableCellRendererComponent(JTable table,
                                                   Object value,
                                                   boolean isSelected,
                                                   boolean hasFocus,
                                                   int row,
                                                   int column) {

        Component cellRenderer;
        if (value instanceof ValidValueDBModel) {
            cellRenderer = super.getTableCellRendererComponent(table,
                                                               value.toString(),
                                                               isSelected,
                                                               hasFocus,
                                                               row,
                                                               column);
            if (hasFocus) {
              table.editCellAt(row, column);
            }
        } else {
            cellRenderer = super.getTableCellRendererComponent(table,
                                                               value,
                                                               isSelected,
                                                               hasFocus,
                                                               row,
                                                               column);
        }

        return cellRenderer;
    }
}
