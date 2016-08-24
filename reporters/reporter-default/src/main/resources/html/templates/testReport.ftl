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
   
   <#assign hiddenCounter = 0>

   <#assign aggregationsList = testReport.getTestAggregations()>
   <#list  aggregationsList as aggregations>
      <#list aggregations.results()?keys as aggregationKey>
         <h2>${aggregationKey} </h2>
         <table>
            <#assign results = aggregations.results()[aggregationKey]/>
            <#if (testReport.maxIterations > 1)>
                <tr>
                   <th colspan="2">&nbsp</th>
                   <#assign entry = (results?api.entrySet()?first)! />
                   <#list 0..(testReport.maxIterations -1) as iteration>
                      <#if entry?has_content && entry.getValue()?size gt iteration>
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
      
      <br>

      <#assign aggregationsList = testReport.getTestAggregations()>
      <#assign i = 0 />
      <#list  aggregationsList as aggregations>
      <table>
         <#assign operationData = testReport.getOperationData(operation, aggregations.byReports())>
         <#assign numberOfColumns = testReport.numberOfColumns(operationData.getPresentedStatistics()) />
         
         <tr>
            <th colspan="4"> Configuration ${testReport.getSingleTestName(i)}</th>
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
         </tr>

         <#assign reportAggregationMap = aggregations.byReports() />
         <#list reportAggregationMap?keys as report>
         
         	<#assign hiddenCounter++>

            <#assign aggregs = reportAggregationMap?api.get(report) />
            <#assign nodeCount = aggregs?first.nodeStats?size!0 />
            <#assign expandableRowsCount = testReport.calculateExpandableRows(aggregs, nodeCount) />
            
            <#assign threadCount = 0/>
            
            <#if testReport.configuration.generateThreadStats>
            	<#list 0..nodeCount-1 as node>
            		<#assign threadCount = threadCount + testReport.getMaxThreads(aggregs, node)/>
            	</#list>
            </#if>  

             <#assign rowspan = aggregs?size />
             
                                
            <#if testReport.configuration.generateNodeStats>
            	<#assign rowspan = rowspan + aggregs?size*nodeCount />
            </#if>
            
            <#if testReport.configuration.generateThreadStats>
            	<#assign rowspan = rowspan + nodeCount*threadCount/>
            </#if>
            
            <tr>
              	<th rowspan= ${rowspan} onClick="switch_visibility_by_class('h_${hiddenCounter}')" class="onClick">
            	<img class="h_${hiddenCounter} visible" src="ic_arrow_drop_down_black_24dp.png">
            	<img class="h_${hiddenCounter} collapse" src="ic_arrow_drop_up_black_24dp.png">
            	</th>
            	<th rowspan= ${rowspan} >${report.getConfiguration().name}</th>
               	<th rowspan= ${rowspan} >${report.getCluster()}</th>            
            
			<#list aggregs as aggregation>

               <th>Itteration ${aggregation?counter-1}</th>
               
                  <@writeRepresentations statistics=aggregation.totalStats report=report aggregation=aggregation
                  node="total" operation=operation/>
             
             	<#if testReport.configuration.generateNodeStats>
             	<#list 0..nodeCount-1 as node>
             	<tr class="h_${hiddenCounter} collapse">
             	<th>Node ${node}</th>
             	<#assign statistics = testReport.getStatistics(aggregation, node)! />
                        <@writeRepresentations statistics=statistics report=report aggregation=aggregation
                        node= "node${node}" operation=operation/>
             	</tr>
             	<#if testReport.configuration.generateThreadStats>
                     <#assign maxThreads = testReport.getMaxThreads(aggregs, node) />
                     <#list 0..(maxThreads -1) as thread>
                        <tr class="h_${hiddenCounter} collapse">
                           <th>thread ${node}_${thread}</th>
                           ${testReport.incElementCounter()}
                           <#assign threadStats = (testReport.getThreadStatistics(aggregation, node, thread))! />
                           <@writeRepresentations statistics=threadStats report=report aggregation=aggregation
                           node="thread${node}_${thread}" operation=operation/>
                        </tr>
                     </#list>
                  </#if>
             	</#list>
             	</#if>   
            </tr>
            </#list>
         </#list>
         </tr>
      </table>
      </#list>
	</tr>
   </#list>
</body>
</html>

<#macro graphs operation suffix>

   <table class="graphTable">
      <#list testReport.getGeneratedCharts(operation) as chart>
         <#local img = testReport.generateImageName(operation, suffix, chart.name + ".png")/>
         <#if chart?counter%2==1>
         <tr>
         </#if>
         <th>
             <br/>
            ${chart.title}<br/>
            <img src="${img}" alt="${operation}">
         </th>
         <#if chart?counter%2==0>
         </tr>
         </#if>
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
   
   <#if rowClass=="highlight">
      <#local tooltip = "Node(s) significantly deviate from average result">
   <#else>
      <#local tooltip = "">
   </#if>
   
   <#if defaultOutcome?? && defaultOutcome?has_content>
      <td class="${rowClass} firstCellStyle" title="${tooltip}">
         ${defaultOutcome.requests}
      </td>
      <td class="${rowClass} rowStyle" title="${tooltip}">
         ${defaultOutcome.errors}
      </td>
   <#else >
      <td class="${rowClass} firstCellStyle" title="${tooltip}"/>
      <td class="${rowClass} rowStyle" title="${tooltip}"/>
   </#if>

   <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.MEAN_AND_DEV)>
      <#if meanAndDev?has_content>
         <td class="${rowClass} rowStyle" title="${tooltip}">
            ${testReport.formatTime(meanAndDev.mean)}
         </td>
         <td class="${rowClass} rowStyle" title="${tooltip}">
            ${testReport.formatTime(meanAndDev.dev)}
         </td>
      <#else >
         <td class="${rowClass} rowStyle" title="${tooltip}"/>
         <td class="${rowClass} rowStyle" title="${tooltip}"/>
      </#if>
   </#if>
   <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.OPERATION_THROUGHPUT)>

      <#local operationThroughput = statistics.getRepresentation(operation, testReport.operationThroughputClass(), period)! />

      <#if operationThroughput?has_content>
         <td class="${rowClass} rowStyle" title="${tooltip}">
            ${testReport.formatOperationThroughput(operationThroughput.gross)}
         </td>
         <td class="${rowClass} rowStyle" title="${tooltip}">
            ${testReport.formatOperationThroughput(operationThroughput.net)}
         </td>
      <#else >
         <td class="${rowClass} rowStyle" title="${tooltip}"/>
         <td class="${rowClass} rowStyle" title="${tooltip}"/>
      </#if>
   </#if>

   <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.DATA_THROUGHPUT)>
      <#local dataThroughput = statistics.getRepresentation(operation, testReport.dataThroughputClass())! />
      <#if dataThroughput?has_content>
         <td class="${rowClass} rowStyle" title="${tooltip}">
            ${testReport.formatDataThroughput(dataThroughput.minThroughput)} - min
         </td>
         <td class="${rowClass} rowStyle" title="${tooltip}">
            ${testReport.formatDataThroughput(dataThroughput.maxThroughput)} - max
         </td>
         <td class="${rowClass} rowStyle" title="${tooltip}">
            ${testReport.formatDataThroughput(dataThroughput.meanThroughput)} - mean
         </td>
         <td class="${rowClass} rowStyle" title="${tooltip}">
            ${testReport.formatDataThroughput(dataThroughput.deviation)} - std. dev
         </td>
      <#else >
         <td class="${rowClass} rowStyle" title="${tooltip}"/>
         <td class="${rowClass} rowStyle" title="${tooltip}"/>
         <td class="${rowClass} rowStyle" title="${tooltip}"/>
         <td class="${rowClass} rowStyle" title="${tooltip}"/>
      </#if>
   </#if>

   <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.PERCENTILES)>
      <#list testReport.configuration.percentiles as percentile >
         <#local p = (statistics.getRepresentation(operation, testReport.percentileClass(), percentile?double))! />

         <#if p?has_content>
            <td class="${rowClass} rowStyle" title="${tooltip}">
               ${testReport.formatTime(p.responseTimeMax)}
            </td>
         <#else >
            <td class="${rowClass} rowStyle" title="${tooltip}"/>
         </#if>
      </#list>
   </#if>

   <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.HISTOGRAM)>
      <#local histogram = testReport.getHistogramName(statistics, operation, report.getConfiguration().name,
         report.getCluster().getClusterIndex(), aggregation.iteration.id, node, operationData.getPresentedStatistics()) />

      <#local percentileGraph = testReport.getPercentileChartName(statistics, operation, report.getConfiguration().name,
         report.getCluster().getClusterIndex(), aggregation.iteration.id, node, operationData.getPresentedStatistics()) />

      <#if histogram?has_content && percentileGraph?has_content>
         <td class="${rowClass} rowStyle" title="${tooltip}">
            <a href="${histogram}">histogram</a> <br>
            <a href="${percentileGraph}">percentiles</a>
         </td>
      <#else >
         <td class="${rowClass} rowStyle" title="${tooltip}">
            none
         </td>
     </#if>
   </#if>
</#macro>
