
package org.radargun.reporting.html;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.jfree.chart.axis.AxisState;
import org.jfree.chart.axis.NumberTick;
import org.jfree.chart.axis.NumberTickUnitSource;
import org.jfree.chart.axis.TickType;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.data.Range;
import org.jfree.ui.RectangleEdge;
import org.jfree.ui.TextAnchor;

/**
 * Inverse logarithmic axis 0 .. limit < 100
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
class PercentileLogAxis extends ValueAxis {

   protected static final double TICK_LABEL_ANGLE = Math.PI / 4;
   private final double limit;
   private final double totalTicks;

   PercentileLogAxis(String label, double limit) {
      super(label, new NumberTickUnitSource());
      this.limit = limit;
      totalTicks = computeTicks(limit);
   }

   @Override
   public void configure() {
   }

   @Override
   public AxisState draw(Graphics2D g2, double cursor, Rectangle2D plotArea, Rectangle2D dataArea, RectangleEdge edge, PlotRenderingInfo plotState) {
      AxisState state;
      // if the axis is not visible, don't draw it...
      if (!isVisible()) {
         state = new AxisState(cursor);
         // even though the axis is not visible, we need ticks for the
         // gridlines...
         java.util.List ticks = refreshTicks(g2, state, dataArea, edge);
         state.setTicks(ticks);
         return state;
      }
      state = drawTickMarksAndLabels(g2, cursor, plotArea, dataArea, edge);
      if (getAttributedLabel() != null) {
         state = drawAttributedLabel(getAttributedLabel(), g2, plotArea,
               dataArea, edge, state);

      } else {
         state = drawLabel(getLabel(), g2, plotArea, dataArea, edge, state);
      }
      createAndAddEntity(cursor, state, dataArea, edge, plotState);
      return state;
   }

   @Override
   public java.util.List refreshTicks(Graphics2D g2, AxisState state, Rectangle2D dataArea, RectangleEdge edge) {
      if (RectangleEdge.isTopOrBottom(edge)) {
         return refreshTicksHorizontal(g2, dataArea, edge);
      } else throw new UnsupportedOperationException();
   }

   protected java.util.List refreshTicksHorizontal(Graphics2D g2, Rectangle2D dataArea, RectangleEdge edge) {
      Range range = getRange();
      java.util.List ticks = new ArrayList();
      Font tickLabelFont = getTickLabelFont();
      g2.setFont(tickLabelFont);
      TextAnchor textAnchor;
      if (edge == RectangleEdge.TOP) {
         textAnchor = TextAnchor.BOTTOM_RIGHT;
      } else {
         textAnchor = TextAnchor.TOP_LEFT;
      }

      DecimalFormat formatter = new DecimalFormat("##.#%");
      int tick = 1;
      ticks.add(new NumberTick(TickType.MINOR, 0.5, "50%", textAnchor, TextAnchor.TOP_LEFT, TICK_LABEL_ANGLE));
      for (;;) {
         formatter.setMinimumFractionDigits(tick - 2);
         formatter.setMaximumFractionDigits(tick - 1);;
         double current = computeInverseTicks(tick);
         if (current > limit) break;
         if (range.contains(current)) {
            ticks.add(new NumberTick(TickType.MAJOR, current, formatter.format(current), textAnchor, textAnchor, TICK_LABEL_ANGLE));
         }
         current = current + (1 - current) / 2;
         if (current > limit) break;
         ticks.add(new NumberTick(TickType.MINOR, current, formatter.format(current), textAnchor, TextAnchor.TOP_LEFT, TICK_LABEL_ANGLE));
         ++tick;
      }
      return ticks;
   }

   protected double findMaximumTickLabelHeight(List ticks, Graphics2D g2, Rectangle2D drawArea, boolean vertical) {
      return ChartsHelper.getMaximumTickLabelHeight(ticks, g2, getTickLabelInsets(), getTickLabelFont(), TICK_LABEL_ANGLE);
   }

   protected double findMaximumTickLabelWidth(List ticks, Graphics2D g2, Rectangle2D drawArea, boolean vertical) {
      return ChartsHelper.getMaximumTickLabelWidth(ticks, g2, getTickLabelInsets(), getTickLabelFont(), TICK_LABEL_ANGLE);
   }

   @Override
   public double valueToJava2D(double value, Rectangle2D area, RectangleEdge edge) {
      double valueTicks = computeTicks(value);

      double min = 0.0, max = 0.0;
      if (RectangleEdge.isTopOrBottom(edge)) {
         min = area.getX();
         max = area.getMaxX();
      } else if (RectangleEdge.isLeftOrRight(edge)) {
         max = area.getMinY();
         min = area.getMaxY();
      }
      return min + (valueTicks / totalTicks) * (max - min);
   }

   @Override
   public double java2DToValue(double java2DValue, Rectangle2D area, RectangleEdge edge) {
      double min = 0.0, max = 0.0;

      if (RectangleEdge.isTopOrBottom(edge)) {
         min = area.getX();
         max = area.getMaxX();
      } else if (RectangleEdge.isLeftOrRight(edge)) {
         min = area.getMaxY();
         max = area.getY();
      }

      double valueTicks = totalTicks * (java2DValue - min) / (max - min);
      return computeInverseTicks(valueTicks);
   }

   private double computeTicks(double value) {
      return -Math.log10(1 - value);
   }

   private double computeInverseTicks(double valueTicks) {
      return 1 - Math.pow(10d, -valueTicks);
   }

   @Override
   protected void autoAdjustRange() {
      // nothing to do, the range is fixed to 0 .. limit
   }
}
