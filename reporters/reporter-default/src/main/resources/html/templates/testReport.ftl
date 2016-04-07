<html xmlns="http://www.w3.org/1999/html">
<head>
    <title>${testReport.getTitle()}</title>
    <script></script>
    <link rel="stylesheet" href="style.css">
    <script src="script.js"></script>
    <#import "lib/library.ftl" as library />
</head>
<body>
   <h1>Test ${testReport.getTestName()}</h1>
   <#assign StatisticType=enums["org.radargun.reporting.html.ReportDocument$StatisticType"] />

   <#assign aggregationsList = testReport.getTestAggregations()>
   <#list  aggregationsList as aggregations>
      <#list aggregations.results()?keys as aggregationKey>
         <h2> ${aggregationKey} </h2>
         <table>
            <#assign results = aggregations.results()[aggregationKey]/>
            <#if (testReport.maxIterations > 1)>
                <tr>
                   <th colspan="2">&nbsp</th>
                   <#assign entry = (results?api.entrySet()?first)! />
                   <#list 0..(testReport.maxIterations -1) as iteration>
                      <#if entry?has_content>
                         <#assign testResult = entry.getValue()[iteration]/>
                         <#assign iterationName = (testResult.getIteration().test.iterationsName)!/>
                         <#if iterationName?has_content && testResult?has_content>
                            <#assign iterationValue = (iterationName + "=" + testResult.getIteration().getValue() )/>
                         <#else>
                            <#assign iterationValue = ("iteration " + iteration)/>
                         </#if>
                      <#else>
                         <#assign iterationValue = ("iteration " + iteration)/>
                      </#if>
                      <th>${iterationValue}</th>
                   </#list>
                </tr>
            </#if>
            <tr>
               <th colspan="2">Configuration</th>
               <#list 0..(testReport.maxIterations - 1) as iteration>
                  <th>Value</th>
               </#list>
            </tr>

            <#list results?keys as report>
               <#if results?api.get(report)?size == 0>
                  <#assign nodeCount = 0 />
               <#else>
                  <#assign nodeCount = results?api.get(report)?first.slaveResults?size />
               </#if>
               <tr>
                  <th class="onClick" onclick="${library.expandableRows(nodeCount, testReport.elementCounter)}">
                     ${report.getConfiguration().name}
                  </th>
                  <th>
                     ${report.getCluster()}
                  </th>
                  <#list results?api.get(report) as result>
                     <#assign rowClass = testReport.rowClass(result.suspicious) />
                     <td class="${rowClass}">
                        ${result.aggregatedValue}
                     </td>
                  </#list>
               </tr>
               <#if testReport.configuration.generateNodeStats>
                  <#list 0 .. (nodeCount - 1) as node>
                     <tr id="e${testReport.getElementCounter()}" class="collapse">
                        <th colspan="2"> node${node}</th>
                        ${testReport.incElementCounter()}
                        <#list results?api.get(report) as result>
                           <#assign slaveResult = (result.slaveResults?api.get(node))! />
                           <#if slaveResult?? && slaveResult.value??>
                              <#assign rowClass = testReport.rowClass(result.suspicious) />
                              <td class="${rowClass}">
                                 ${slaveResult.value}
                              </td>
                           <#else >
                              <td></td>
                           </#if>
                        </#list>
                     </tr>
                  </#list>
               </#if>
            </#list>
         </table>
      </#list>
   </#list>

   <#list testReport.getOperations() as operation>
   <h2>${operation}</h2>
      <#if (testReport.getMaxClusters() > 1 && testReport.separateClusterCharts())>
         <#list testReport.getClusterSizes() as clusterSize>
            <#if (clusterSize > 0)>
               <#assign suffix = "_" + clusterSize />
            <#else>
               <#assign suffix = "" />
            </#if>
            <@graphs operation=operation suffix=suffix/>
         </#list>
      <#else>
         <#assign suffix = "" />
         <@graphs operation=operation suffix=suffix/>
      </#if>

      <#assign aggregationsList = testReport.getTestAggregations()>
      <#assign i = 0 />
      <#list  aggregationsList as aggregations>
      <table>
         <#assign operationData = testReport.getOperationData(operation, aggregations.byReports())>
         <#assign numberOfColumns = testReport.numberOfColumns(operationData.getPresentedStatistics()) />

         <#if (testReport.getMaxIterations() > 1)>
            <tr>
               <th colspan="2">&nbsp</th>
               <#list operationData.getIterationValues() as iterationValue>
                  <th colspan=${numberOfColumns}> ${iterationValue}</th>
               </#list>
            </tr>
         </#if>

         <tr>
            <th colspan="2"> Configuration ${testReport.getSingleTestName(i)}</th>
            <#assign i = i + 1 />
            <#list 0 .. (testReport.getMaxIterations() - 1) as i >
               <th>requests</th>
               <th>errors</th>
               <th>mean</th>
               <th>std.dev</th>

               <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.OPERATION_THROUGHPUT) >
                  <th>gross operation throughput</th>
                  <th>net operation throughput</th>
               </#if>
               <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.DATA_THROUGHPUT) >
                  <th colspan="4">data throughput</th>
               </#if>

               <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.PERCENTILES) >
                  <#list testReport.configuration.percentiles as percentile>
                     <th>RTM at ${percentile} %</th>
                  </#list>
               </#if>

               <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.HISTOGRAM) >
                  <th>histograms</th>
               </#if>

            </#list>
         </tr>

         <#assign reportAggregationMap = aggregations.byReports() />
         <#list reportAggregationMap?keys as report>

            <#assign aggregs = reportAggregationMap?api.get(report) />
            <#assign nodeCount = aggregs?first.nodeStats?size!0 />
            <#assign expandableRowsCount = testReport.calculateExpandableRows(aggregs, nodeCount) />

            <tr>
               <th onClick="${library.expandableRows(expandableRowsCount, testReport.elementCounter)}" class="onClick">
                  ${report.getConfiguration().name}
               </th>
               <th>
                  ${report.getCluster()}
               </th>
               <#list aggregs as aggregation>
                  <@writeRepresentations statistics=aggregation.totalStats report=report aggregation=aggregation
                  node="total" operation=operation/>
               </#list>
            </tr

            <#if testReport.configuration.generateNodeStats>
               </tr>
               <#list 0..(nodeCount -1) as node>
                  <tr id="e${testReport.getElementCounter()}" class="collapse">
                     <th colspan="2">node${node}</th>
                     ${testReport.incElementCounter()}
                     <#list aggregs as aggregation>
                        <#assign statistics = testReport.getStatistics(aggregation, node)! />
                        <@writeRepresentations statistics=statistics report=report aggregation=aggregation
                        node= "node${node}" operation=operation/>
                     </#list>
                  </tr>
                  <#if testReport.configuration.generateThreadStats>
                     <#assign maxThreads = testReport.getMaxThreads(aggregs, node) />
                     <#list 0..(maxThreads -1) as thread>
                        <tr id="e${testReport.getElementCounter()}" class="collapse">
                           <th colspan="2">thread ${node}_${thread}</th>
                           ${testReport.incElementCounter()}
                           <#list aggregs as aggregation >
                              <#assign threadStats = (testReport.getThreadStatistics(aggregation, node, thread))! />
                              <@writeRepresentations statistics=threadStats report=report aggregation=aggregation
                              node="thread${node}_${thread}" operation=operation/>
                           </#list>
                        </tr>
                     </#list>
                  </#if>
               </#list>
            </#if>
         </#list>
         </tr>
      </table>
      </#list>
   </#list>
</body>
</html>

<#macro graphs operation suffix>

   <table class="graphTable">
      <#list testReport.getGeneratedCharts(operation) as chart>
         <#local img = testReport.generateImageName(operation, suffix, chart.name + ".png")/>
         <th>
            ${chart.title}<br/>
            <img src="${img}" alt="${operation}">
         </th>
      </#list>
   </table>

</#macro>

<#macro writeRepresentations statistics report aggregation node operation>

   <#if statistics?has_content>
      <#local period = testReport.period(statistics)!0 />
   </#if>

   <#local defaultOutcome = statistics.getRepresentation(operation, testReport.defaultOutcomeClass())! />
   <#local meanAndDev = statistics.getRepresentation(operation, testReport.meanAndDevClass())! />

   <#local rowClass = testReport.rowClass(aggregation.anySuspect(operation)) />
   <#if defaultOutcome?? && defaultOutcome?has_content>
      <td class="${rowClass} firstCellStyle">
         ${defaultOutcome.requests}
      </td>
      <td class="${rowClass} rowStyle">
         ${defaultOutcome.errors}
      </td>
   <#else >
      <td class="${rowClass} firstCellStyle"/>
      <td class="${rowClass} rowStyle"/>
   </#if>

   <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.MEAN_AND_DEV)>
      <#if meanAndDev?has_content>
         <td class="${rowClass} rowStyle">
            ${testReport.formatTime(meanAndDev.mean)}
         </td>
         <td class="${rowClass} rowStyle">
            ${testReport.formatTime(meanAndDev.dev)}
         </td>
      <#else >
         <td class="${rowClass} rowStyle"/>
         <td class="${rowClass} rowStyle"/>
      </#if>
   </#if>
   <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.OPERATION_THROUGHPUT)>

      <#local operationThroughput = statistics.getRepresentation(operation, testReport.operationThroughputClass(), period)! />

      <#if operationThroughput?has_content>
         <td class="${rowClass} rowStyle">
            ${testReport.formatOperationThroughput(operationThroughput.gross)}
         </td>
         <td class="${rowClass} rowStyle">
            ${testReport.formatOperationThroughput(operationThroughput.net)}
         </td>
      <#else >
         <td class="${rowClass} rowStyle"/>
         <td class="${rowClass} rowStyle"/>
      </#if>
   </#if>

   <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.DATA_THROUGHPUT)>
      <#local dataThroughput = statistics.getRepresentation(operation, testReport.dataThroughputClass())! />
      <#if dataThroughput?has_content>
         <td class="${rowClass} rowStyle">
            ${testReport.formatDataThroughput(dataThroughput.minThroughput)} - min
         </td>
         <td class="${rowClass} rowStyle">
            ${testReport.formatDataThroughput(dataThroughput.maxThroughput)} - max
         </td>
         <td class="${rowClass} rowStyle">
            ${testReport.formatDataThroughput(dataThroughput.meanThroughput)} - mean
         </td>
         <td class="${rowClass} rowStyle">
            ${testReport.formatDataThroughput(dataThroughput.deviation)} - std. dev
         </td>
      <#else >
         <td class="${rowClass} rowStyle"/>
         <td class="${rowClass} rowStyle"/>
         <td class="${rowClass} rowStyle"/>
         <td class="${rowClass} rowStyle"/>
      </#if>
   </#if>

   <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.PERCENTILES)>
      <#list testReport.configuration.percentiles as percentile >
         <#local p = (statistics.getRepresentation(operation, testReport.percentileClass(), percentile?double))! />

         <#if p?has_content>
            <td class="${rowClass} rowStyle">
               ${testReport.formatTime(p.responseTimeMax)}
            </td>
         <#else >
            <td class="${rowClass} rowStyle"/>
         </#if>
      </#list>
   </#if>

   <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.HISTOGRAM)>
      <#local histogram = testReport.getHistogramName(statistics, operation, report.getConfiguration().name,
         report.getCluster().getClusterIndex(), aggregation.iteration.id, node, operationData.getPresentedStatistics()) />

      <#local percentileGraph = testReport.getPercentileChartName(statistics, operation, report.getConfiguration().name,
         report.getCluster().getClusterIndex(), aggregation.iteration.id, node, operationData.getPresentedStatistics()) />

      <#if histogram?has_content && percentileGraph?has_content>
         <td class="${rowClass} rowStyle">
            <a href="${histogram}">histogram</a> <br>
            <a href="${percentileGraph}">percentiles</a>
         </td>
      <#else >
         <td class="${rowClass} rowStyle">
            none
         </td>
     </#if>
   </#if>
</#macro>
