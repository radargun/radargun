<html xmlns="http://www.w3.org/1999/html">
<head>
    <title>${testReport.getTitle()}</title>
    <script></script>
    <link rel="stylesheet" href="style.css">
    <#import "lib/library.ftl" as library />
</head>
<body>
  <h1>Test ${testReport.getTestName()}</h1>
  <#assign StatisticType=enums["org.radargun.reporting.html.ReportDocument$StatisticType"] />

  <#-- counter from creating unique classes for hidden rows -->
  <#assign hiddenCounter = 0>

  <#list  testReport.getTestAggregations() as aggregations>
    <#list aggregations.results()?keys as aggregationKey>
      <h2>Aggregation: ${aggregationKey}</h2>

      <table>
      <#assign results = aggregations.results()[aggregationKey]/>
        <tr>
          <th colspan="3">Configuration</th>
          <#assign entry = (results?api.entrySet()?first)! />
          <#list 0..(testReport.maxIterations -1) as iteration>

            <#if entry?has_content && entry.getValue()?size gt iteration>
              <#assign testResult = entry.getValue()[iteration]/>
              <#assign iterationName = (testResult.getIteration().test.iterationsName)!/>
              <#if iterationName?has_content && testResult?has_content>
                <#assign iterationValue = (iterationName + "=" + testResult.getIteration().getValue() )/>
              <#else>
                <#assign iterationValue = ("Iteration " + iteration)/>
              </#if>
            <#else>
              <#assign iterationValue = ("Iteration " + iteration)/>
            </#if>
            <th>${iterationValue}</th>

          </#list>
        </tr>

        <#list results?keys as report>

          <#assign hiddenCounter++>

          <#if results?api.get(report)?size == 0>
            <#assign nodeCount = 0 />
          <#else>
            <#assign nodeCount = results?api.get(report)?first.slaveResults?size />
          </#if>

          <tr>
            <th rowspan= ${nodeCount+1} onClick="switch_class_by_class('h_${hiddenCounter}','expanded','collapsed')" class="onClick">
              <img class="h_${hiddenCounter} expanded" src="ic_arrow_drop_down_black_24dp.png">
              <img class="h_${hiddenCounter} collapsed" src="ic_arrow_drop_up_black_24dp.png">
            </th>
            <th>${report.getConfiguration().name}</th>
            <th>${report.getCluster()}</th>

            <#assign dataCount = 0>

            <#list results?api.get(report) as result>
              <#assign rowClass = testReport.rowClass(result.suspicious) />
              <td class="${rowClass}">
                ${result.aggregatedValue}
              </td>
              <#assign dataCount = dataCount + 1 >
            </#list>

            <#-- Fill remaining cells because CSS -->
            <#if dataCount!=(testReport.maxIterations)>
              <#list dataCount..(testReport.maxIterations - 1)  as colNum>
                <td/>
              </#list>
            </#if>

          </tr>
          <#if testReport.configuration.generateNodeStats>
            <#list 0 .. (nodeCount - 1) as node>
              <tr class="h_${hiddenCounter} collapsed">
                <th/>
                <th> node${node}</th>

                <#assign dataCount = 0>

                ${testReport.incElementCounter()}
                <#list results?api.get(report) as result>
                  <#assign slaveResult = (result.slaveResults?api.get(node))! />
                  <#if slaveResult?? && slaveResult.value??>
                    <#assign rowClass = testReport.rowClass(result.suspicious) />
                    <td class="${rowClass}">
                      ${slaveResult.value}
                    </td>
                  <#else >
                    <td/>
                  </#if>

                  <#assign dataCount = dataCount + 1>
                </#list>

                <#-- Fill remaining cells because CSS -->
                <#if dataCount!=(testReport.maxIterations)>
                  <#list dataCount..(testReport.maxIterations - 1)  as colNum>
                    <td/>
                  </#list>
                </#if>
              </tr>
            </#list>
          </#if>
        </#list>
      </table>
    </#list>
  </#list>
  <#list testReport.getOperationGroups() as operation>
    <h2>Operation: ${operation}</h2>

    <#-- place graphs -->
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

    <#assign i = 0 />
    <#list  testReport.getTestAggregations() as aggregations>
      <table>
      <#assign operationData = testReport.getOperationData(operation, aggregations.byReports())>
      <#assign numberOfColumns = testReport.numberOfColumns(operationData.getPresentedStatistics()) />

        <col/>
        <col/>
        <col/>
        <col/>

        <col/>
        <col/>
        <col/>
        <col/>

        <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.OPERATION_THROUGHPUT) >
          <col/>
          <col class="tPut_with_errors collapsed"/>
        </#if>

        <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.DATA_THROUGHPUT) >
          <col id="data throughput">
        </#if>

        <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.PERCENTILES) >
          <#list testReport.configuration.percentiles as percentile>
            <col id="RTM at ${percentile} %">
          </#list>
        </#if>

        <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.HISTOGRAM) >
          <col id="histograms">
        </#if>

        <tr>
          <th colspan="4"> Configuration ${testReport.getSingleTestName(i)}</th>
          <th>requests</th>
          <th>errors</th>
          <th>mean</th>
          <th>std.dev</th>

          <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.OPERATION_THROUGHPUT) >
            <th>throughput</th>
            <th>throughput w/ errors</th>
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
          <#assign nodeCount = report.getCluster().getSize() />
          <#assign threadCount = 0/>

          <#if testReport.configuration.generateThreadStats>
            <#list 0..nodeCount-1 as node>
              <#assign threadCount = threadCount + testReport.getMaxThreads(aggregs, node)/>
            </#list>
          </#if>

          <#assign rowspan = aggregations.getMaxIterations() />

          <#if testReport.configuration.generateNodeStats>
            <#assign rowspan = rowspan + aggregations.getMaxIterations()*nodeCount />
          </#if>

          <#if testReport.configuration.generateThreadStats>
            <#assign rowspan = rowspan + aggregations.getMaxIterations()*threadCount/>
          </#if>

          <tr>
            <th rowspan= ${rowspan} onClick="switch_class_by_class('h_${hiddenCounter}','expanded','collapsed')" class="onClick">
              <img class="h_${hiddenCounter} expanded" src="ic_arrow_drop_down_black_24dp.png">
              <img class="h_${hiddenCounter} collapsed" src="ic_arrow_drop_up_black_24dp.png">
            </th>
            <th rowspan= ${rowspan} >${report.getConfiguration().name}</th>
            <th rowspan= ${rowspan} >${report.getCluster()}</th>

            <#-- list all possible itteration ids -->
            <#list 0..(aggregations.getMaxIterations()-1) as iteration>
              <#assign aggregation = "" >

              <#-- fine if there is iteration matching the id -->
              <#list aggregs as agg>
                <#if agg.iteration.id==iteration>
                  <#assign aggregation = agg >
                </#if>
              </#list>

              <th>Iteration ${iteration}</th>

              <#-- write iteration totals -->
              <#if aggregation?? && aggregation != "">
                <@writeRepresentations statistics=aggregation.totalStats report=report aggregation=aggregation
                 node="total" operation=operation/>
              <#else>
                <#-- Fill cells because CSS -->
                <#list 1..numberOfColumns as colNum>
                  <td/>
                </#list>
              </#if>

              <#-- write node totals -->
              <#if testReport.configuration.generateNodeStats>
                <#list 0..nodeCount-1 as node>
                  <tr class="h_${hiddenCounter} collapsed">
                  <th>Node ${node}</th>
                  <#if aggregation?? && aggregation != "">
                    <#assign statistics = testReport.getStatistics(aggregation, node)! />
                    <@writeRepresentations statistics=statistics report=report aggregation=aggregation
                                           node= "node${node}" operation=operation/>
                  <#else>
                    <#-- Fill cells because CSS -->
                    <#list 1..numberOfColumns as colNum>
                      <td/>
                    </#list>
                  </#if>

                  <#-- write thread totals -->
                  <#if testReport.configuration.generateThreadStats>
                    <#assign maxThreads = testReport.getMaxThreads(aggregs, node) />
                    <#list 0..(maxThreads -1) as thread>
                      <tr class="h_${hiddenCounter} collapsed">
                        <th>thread ${node}_${thread}</th>
                        ${testReport.incElementCounter()}
                        <#if aggregation?? && aggregation != "">
                          <#assign threadStats = (testReport.getThreadStatistics(aggregation, node, thread))! />
                          <@writeRepresentations statistics=threadStats report=report aggregation=aggregation
                                                 node="thread${node}_${thread}" operation=operation/>
                        <#else>
                          <#-- Fill cells because CSS -->
                          <#list 1..numberOfColumns as colNum>
                            <td/>
                          </#list>
                        </#if>
                      </tr>
                    </#list>
                  </#if>
                </#list>
              </#if>
            </#list> <!-- iteration -->
          </tr>
        </#list> <!-- report -->
      </table>
    </#list> <!-- aggregations -->
  </#list> <!-- operation -->

  <#list testReport.getOperations() as operation>
    <h2>Operation: ${operation}</h2>

    <#-- place graphs -->
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

    <#assign i = 0 />
    <#list  testReport.getTestAggregations() as aggregations>
      <table>
      <#assign operationData = testReport.getOperationData(operation, aggregations.byReports())>
      <#assign numberOfColumns = testReport.numberOfColumns(operationData.getPresentedStatistics()) />

        <col/>
        <col/>
        <col/>
        <col/>

        <col/>
        <col/>
        <col/>
        <col/>

        <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.OPERATION_THROUGHPUT) >
          <col/>
          <col class="tPut_with_errors collapsed"/>
        </#if>

        <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.DATA_THROUGHPUT) >
          <col id="data throughput">
        </#if>

        <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.PERCENTILES) >
          <#list testReport.configuration.percentiles as percentile>
            <col id="RTM at ${percentile} %">
          </#list>
        </#if>

        <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.HISTOGRAM) >
          <col id="histograms">
        </#if>

        <tr>
          <th colspan="4"> Configuration ${testReport.getSingleTestName(i)}</th>
          <th>requests</th>
          <th>errors</th>
          <th>mean</th>
          <th>std.dev</th>

          <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.OPERATION_THROUGHPUT) >
            <th>throughput</th>
            <th>throughput w/ errors</th>
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
          <#assign nodeCount = report.getCluster().getSize() />
          <#assign threadCount = 0/>

          <#if testReport.configuration.generateThreadStats>
            <#list 0..nodeCount-1 as node>
              <#assign threadCount = threadCount + testReport.getMaxThreads(aggregs, node)/>
            </#list>
          </#if>

          <#assign rowspan = aggregations.getMaxIterations() />

          <#if testReport.configuration.generateNodeStats>
            <#assign rowspan = rowspan + aggregations.getMaxIterations()*nodeCount />
          </#if>

          <#if testReport.configuration.generateThreadStats>
            <#assign rowspan = rowspan + aggregations.getMaxIterations()*threadCount/>
          </#if>

          <tr>
            <th rowspan= ${rowspan} onClick="switch_class_by_class('h_${hiddenCounter}','expanded','collapsed')" class="onClick">
              <img class="h_${hiddenCounter} expanded" src="ic_arrow_drop_down_black_24dp.png">
              <img class="h_${hiddenCounter} collapsed" src="ic_arrow_drop_up_black_24dp.png">
            </th>
            <th rowspan= ${rowspan} >${report.getConfiguration().name}</th>
            <th rowspan= ${rowspan} >${report.getCluster()}</th>

            <#-- list all possible itteration ids -->
            <#list 0..(aggregations.getMaxIterations()-1) as iteration>

              <#if iteration != 0>
                </tr><tr>
              </#if>

              <#assign aggregation = "" >

              <#-- fine if there is iteration matching the id -->
              <#list aggregs as agg>
                <#if agg.iteration.id==iteration>
                  <#assign aggregation = agg >
                </#if>
              </#list>

              <th>Iteration ${iteration}</th>

              <#-- write iteration totals -->
              <#if aggregation?? && aggregation != "">
                <@writeRepresentations statistics=aggregation.totalStats report=report aggregation=aggregation
                                       node="total" operation=operation/>
              <#else>
                <#-- Fill cells because CSS -->
                <#list 1..numberOfColumns as colNum>
                  <td/>
                </#list>
              </#if>


              <#-- write node totals -->
              <#if testReport.configuration.generateNodeStats>
                <#list 0..nodeCount-1 as node>
                  </tr><tr class="h_${hiddenCounter} collapsed">
                    <th>Node ${node}</th>
                    <#if aggregation?? && aggregation != "">
                      <#assign statistics = testReport.getStatistics(aggregation, node)! />
                      <@writeRepresentations statistics=statistics report=report aggregation=aggregation
                                             node= "node${node}" operation=operation/>
                    <#else>
                      <#-- Fill cells because CSS -->
                      <#list 1..numberOfColumns as colNum>
                        <td/>
                      </#list>
                    </#if>

                    <#-- write thread totals -->
                    <#if testReport.configuration.generateThreadStats>
                      <#assign maxThreads = testReport.getMaxThreads(aggregs, node) />
                      <#list 0..(maxThreads -1) as thread>
                        </tr><tr class="h_${hiddenCounter} collapsed">
                          <th>thread ${node}_${thread}</th>
                            ${testReport.incElementCounter()}
                            <#if aggregation?? && aggregation != "">
                              <#assign threadStats = (testReport.getThreadStatistics(aggregation, node, thread))! />
                              <@writeRepresentations statistics=threadStats report=report aggregation=aggregation
                                                     node="thread${node}_${thread}" operation=operation/>
                            <#else>
                              <#-- Fill cells because CSS -->
                              <#list 1..numberOfColumns as colNum>
                                <td/>
                              </#list>
                            </#if>
                      </#list>
                    </#if> <!-- generate thread stats -->
                </#list> <!-- node -->
              </#if> <!-- generate node stats -->
            </#list> <!-- iteration -->
          </tr>
        </#list> <!-- report -->
      </table>
    </#list> <!-- aggregations -->
  </#list><!-- operation -->
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
  <#if !statistics?has_content>
    <#return>
  </#if>

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
    <td class="${rowClass} rowStyle errorData" title="${tooltip}">
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
        ${testReport.formatOperationThroughput(operationThroughput.net)}
      </td>
      <td class="${rowClass} rowStyle" title="${tooltip}">
        ${testReport.formatOperationThroughput(operationThroughput.gross)}
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
      <td class="${rowClass} rowStyle" title="${tooltip}">none</td>
    </#if>
  </#if>
</#macro>

<#macro writeTotalRepresentations statistics report aggregation node operation>

  <#if statistics?has_content>
    <#local period = testReport.period(statistics)!0 />
  </#if>

  <#local defaultOutcome = statistics.getRepresentation(operation, testReport.defaultOutcomeClass())! />


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

  <#if operationData.getPresentedStatistics()?seq_contains(StatisticType.OPERATION_THROUGHPUT)>

    <#if operationStats?has_content>
      <#local operationThroughput = statistics.getRepresentation(operation, testReport.operationThroughputClass(), period)! />
    </#if>

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
</#macro>

<script src="script.js"></script>
