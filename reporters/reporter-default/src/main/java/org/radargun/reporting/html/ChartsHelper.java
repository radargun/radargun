package org.radargun.reporting.html;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.List;

import org.jfree.chart.axis.Tick;
import org.jfree.text.TextUtilities;
import org.jfree.ui.RectangleInsets;
import org.jfree.ui.TextAnchor;

/**
 * Helper for charts, axes etc..
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ChartsHelper {

   public static double getMaximumTickLabelHeight(List<Tick> ticks, Graphics2D g2, RectangleInsets insets, Font font, double tickLabelAngle) {
      g2.setFont(font);
      double maxHeight = 0.0;
      for (Tick tick : ticks) {
         Rectangle2D labelBounds = null;
         if (tick.getText() != null) {
            labelBounds = TextUtilities.calculateRotatedStringBounds(tick.getText(), g2, 0, 0, TextAnchor.TOP_LEFT, tickLabelAngle, TextAnchor.TOP_LEFT).getBounds2D();
         }
         if (labelBounds != null && labelBounds.getWidth()
               + insets.getTop() + insets.getBottom() > maxHeight) {
            maxHeight = labelBounds.getWidth()
                  + insets.getTop() + insets.getBottom();
         }
      }
      return maxHeight;
   }

   public static double getMaximumTickLabelWidth(List<Tick> ticks, Graphics2D g2, RectangleInsets insets, Font font, double tickLabelAngle) {
      g2.setFont(font);
      double maxWidth = 0.0;
      for (Tick tick : ticks) {
         Rectangle2D labelBounds = null;
         if (tick.getText() != null) {
            labelBounds = TextUtilities.calculateRotatedStringBounds(tick.getText(), g2, 0, 0, TextAnchor.TOP_LEFT, tickLabelAngle, TextAnchor.TOP_LEFT).getBounds2D();
         }
         if (labelBounds != null
               && labelBounds.getWidth() + insets.getLeft()
               + insets.getRight() > maxWidth) {
            maxWidth = labelBounds.getWidth()
                  + insets.getLeft() + insets.getRight();
         }
      }
      return maxWidth;
   }
}
