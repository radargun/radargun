package org.cachebench.reportgenerators


import apache.commons.logging.LogFactory
import java.awt.Color
import jfree.chart.labels.CategoryItemLabelGenerator
import jfree.data.category.{CategoryDataset, DefaultCategoryDataset}
import java.io.{BufferedReader, FileReader, File}
import java.util.StringTokenizer
import java.text.NumberFormat
import jfree.chart.{ChartUtilities, JFreeChart, ChartFactory}
import jfree.chart.plot.PlotOrientation
import jfree.chart.renderer.category.BarRenderer3D

/**
 * Generates average and total throughput.  Used for parsing reports generated with a ClusterReportGenerator.
 *
 * @author Manik Surtani (<a href="mailto:manik@jboss.org">manik@jboss.org</a>)
 */
class PutGetChartGenerator extends AbstractChartGen {
   var putData: DefaultCategoryDataset = null
   var getData: DefaultCategoryDataset = null
   var memData: DefaultCategoryDataset = null
   val chartExtension = ".png"
   var putChartName = "PutChart"
   var getChartName = "GetChart"
   var memChartName = "MemoryFootprintChart"
   val log = LogFactory.getLog(this.getClass())
   var nPuts = 0
   var nGets = 0
   var numFilesScanned = 0

   def generateChart() {
      readData();

      def chartNameToUse(s: String) = {
         if (filenamePrefix == null) s
         else filenamePrefix + "_" + s
      }

      var chartFile = new File(chartNameToUse(putChartName) + chartExtension)
      if (chartFile.exists()) {
         chartFile renameTo new File(chartNameToUse(putChartName) + "." + System.currentTimeMillis() + chartExtension)
         chartFile = new File(chartNameToUse(putChartName) + chartExtension)
      }

      ChartUtilities.saveChartAsPNG(chartFile, createChart("Report: Comparing Cache PUT (WRITE) performance", putData, nPuts / numFilesScanned, "Average time (µ-seconds)", false), 800, 800)

      chartFile = new File(chartNameToUse(getChartName) + chartExtension)
      if (chartFile.exists()) {
         chartFile renameTo new File(chartNameToUse(getChartName) + "." + System.currentTimeMillis() + chartExtension)
         chartFile = new File(chartNameToUse(getChartName) + chartExtension)
      }

      ChartUtilities.saveChartAsPNG(chartFile, createChart("Report: Comparing Cache GET (READ) performance", getData, nGets / numFilesScanned, "Average time (µ-seconds)", false), 800, 800)

      chartFile = new File(chartNameToUse(memChartName) + chartExtension)
      if (chartFile.exists()) {
         chartFile renameTo new File(chartNameToUse(memChartName) + "." + System.currentTimeMillis() + chartExtension)
         chartFile = new File(chartNameToUse(memChartName) + chartExtension)
      }

      ChartUtilities.saveChartAsPNG(chartFile, createChart("Report: Comparing Cache memory footprint", memData, nGets + nPuts / numFilesScanned, "Final memory footprint (MiB)", true), 800, 800)

      log info "Charts saved as " + chartNameToUse(putChartName) + ", " + chartNameToUse(getChartName) + " and " + chartNameToUse(memChartName)
   }

   def createChart(title: String, data: DefaultCategoryDataset, numOperations: Int, yAxisLabel: String, isMemory: Boolean): JFreeChart = {
      val chart: JFreeChart = ChartFactory.createBarChart3D(title, "Cache operations performed (approx): " + NumberFormat.getIntegerInstance().format(numOperations), yAxisLabel, data, PlotOrientation.VERTICAL, true, true, false)
      val renderer: BarRenderer3D = chart.getCategoryPlot().getRenderer().asInstanceOf[BarRenderer3D]
      renderer setBaseItemLabelsVisible true

      renderer setBaseItemLabelGenerator new CustomLabelGen(isMemory);
      chart addSubtitle getSubtitle()
      chart setBorderVisible true
      chart setAntiAlias true
      chart setTextAntiAlias true
      chart setBackgroundPaint new Color(0x61, 0x9e, 0xa1)
      return chart
   }

   def readData() {
      val file = new File(reportDirectory)
      if (!file.exists() || !file.isDirectory())
         throw new IllegalArgumentException("Report directory " + reportDirectory + " does not exist or is not a directory!")
      putData = new DefaultCategoryDataset()
      getData = new DefaultCategoryDataset()
      memData = new DefaultCategoryDataset()

      for (f <- file.listFiles() if f.getName.toUpperCase().endsWith(".CSV")) readData(f)
   }

   def readData(f: File) {
      log debug "Processing file " + f
      var productName = f.getName()
      if (productName startsWith "data_") productName = productName substring 5

      if ((productName indexOf ".xml") > 0) productName = productName substring (0, productName.indexOf(".xml"))

      // now the contents of this file:
      val br = new BufferedReader(new FileReader(f))
      var line: String = br.readLine()

      var avgPut: Double = -1
      var avgGet: Double = -1
      var mem: Double = 0
      var s: Stats = null

      while (line != null && s == null)
         {
            s = getAveragePutAndGet(line)
            log debug "Read stats " + s
            if (s != null)
               {
                  avgPut = s.getAvgPut
                  avgGet = s.getAvgGet
                  nGets += s.getNumGets
                  nPuts += s.getNumPuts
                  mem = s.getMem
               }
            line = br.readLine()
         }

      br.close()

      putData addValue (avgPut, productName, "PUT")
      getData addValue (avgGet, productName, "GET")
      memData addValue (mem, productName, "MemoryFootprint")
      numFilesScanned += 1
   }

   def getAveragePutAndGet(line: String): Stats = {
      // To be a valid line, the line should be comma delimited
      val strTokenizer = new StringTokenizer(line, ",")
      if (strTokenizer.countTokens() < 7) return null

      // 8th token is avg put
      // 9th token is avg get
      for (i <- 0 to 6) strTokenizer.nextToken()

      var putStr = strTokenizer.nextToken()
      var getStr = strTokenizer.nextToken()

      // 20 and 21st tokens are the num puts and num gets performed.

      for (i <- 0 to 9) strTokenizer.nextToken()

      var nPutStr = strTokenizer.nextToken()
      var nGetStr = strTokenizer.nextToken()
      var mem = strTokenizer.nextToken()
      try {
         return new Stats(
            putStr.toDouble / 1000,
            getStr.toDouble / 1000,
            nPutStr.toInt, nGetStr.toInt,
            mem.toDouble / (1024 * 1024))
      } catch {
         case _: NumberFormatException => return null
      }
   }
}

class Stats(avgPut: Double, avgGet: Double, numPuts: Int, numGets: Int, mem: Double) {
   def getAvgPut = avgPut

   def getAvgGet = avgGet

   def getNumPuts = numPuts

   def getNumGets = numGets

   def getMem = mem
}

class CustomLabelGen(isMemory: Boolean) extends CategoryItemLabelGenerator {
   val fmt = NumberFormat.getNumberInstance()
   fmt setMaximumFractionDigits 2
   fmt setMinimumFractionDigits 2

   def generateRowLabel(categoryDataset: CategoryDataset, i: Int): String = {
      return null
   }

   def generateColumnLabel(categoryDataset: CategoryDataset, i: Int): String = {
      return null
   }

   def generateLabel(categoryDataset: CategoryDataset, product: Int, operation: Int): String = {
      if (isMemory)
         return fmt.format(categoryDataset.getValue(product, operation)) + " MiB"
      else
         return fmt.format(categoryDataset.getValue(product, operation)) + " µs"
   }
}
