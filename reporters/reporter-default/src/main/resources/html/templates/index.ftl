<html>
<head>
   <title>${indexDocument.getTitle()}</title>
   <script src="script.js"></script>
   <link rel="stylesheet" href="style.css">
   <#import "lib/library.ftl" as library />
</head>
<body>
   <h1>RadarGun benchmark report</h1>
   <h2>Tests</h2>
   <#assign elementCounter = 0 />
   <ul>
      <#list reporter.allTests as testName>
         <li>
            <a href="test_${testName}.html">${testName}</a>
         </li>
      </#list>
   </ul>
   <h2>Configurations</h2>
      The benchmark was executed on following configurations and cluster sizes: <br/>
   <ul>
      <#list reporter.reports as report>
         <li>
            <b>${report.configuration.name}</b> on cluster with ${report.cluster.size} slaves: ${report.cluster}. <br/>
               Setups: <br/>
            <ul>
               <#list report.configuration.setups as setup>
                  <li>
                     Group: ${setup.group}
                     <ul>
                        <li>Plugin: ${setup.plugin}</li>
                        <li>Service: ${setup.service}</li>
                        <#if (indexDocument.getConfigs(report))?size == 0>
                           <#assign fileName = (setup.properties["file"])!" no file" />
                              <li>Configuration file: ${fileName}</li>

                        <#else>
                           <li>
                              Configuration files:
                              <ul>
                                 <#list (indexDocument.getConfigs(report)) as config>
                                    <li>
                                       <a href="${getConfigFileName(setup.configuration.name, setup.group, report.cluster.clusterIndex, config.filename)}"> ${config.filename}</a>
                                    </li>
                                 </#list>
                              </ul>
                           </li>
                           <li>
                              Normalized configurations:
                               <ul>
                                  <#list indexDocument.getNormalized() as config>
                                    <li>
                                       <a href="${indexDocument.getFilename(report.getConfiguration().name, setup.group, report.getCluster(), config)}">
                                             ${config}
                                       </a>
                                    </li>
                                  </#list>
                               </ul>
                           </li>
                        </#if>
                     </ul>
                     <#if setup.properties??>
                        <li>
                           Properties:
                            <ul>
                               <#list setup.properties?keys as property>
                                 <@writeProperty name=property definition=(setup.properties)[property] />
                              </#list>
                            </ul>
                        </li>
                     </#if>
                  </li>
               </#list>
            </ul>
         </li>
      </#list>
   </ul>
   <h2>Scenario</h2>
      Note that some properties may have not been resolved correctly as these depend on local properties. <br/>
      These stages have been used for the benchmark: <br/>
   <ul>
      <#if reporter.reports?? && reporter.reports?has_content>
         <#list reporter.reports[0].stages as stage>
            <li>
               <span class="onClick" onclick="${library.expandableRowsInList(stage.getProperties()?size, elementCounter)}">
                     ${stage.name}
               </span>
               <ul>
                  <#list stage.properties as property>
                     <#assign propertyCounter = elementCounter />
                     <#assign elementCounter = elementCounter + 1 />
                     <#if property.definition??>
                        <li>
                           <b>${property.name} = ${property.value}</b>
                           <small class="onClick" id="showdef${propertyCounter}"
                                 onclick="switch_visibility('showdef${propertyCounter}'); switch_visibility('def${propertyCounter}');">show definition</small>

                            <span id="def${propertyCounter}" class="collapse"><small class="onClick"
                               onClick="switch_visibility('showdef${propertyCounter}'); switch_visibility('def${propertyCounter}');">hide definition:</small>
                               ${property.getDefinition()}
                            </span>
                        </li>
                     <#else>
                        <li id="e${propertyCounter}" class="none">
                              ${property.name} = ${property.value}
                        </li>
                     </#if>
                  </#list>
               </ul>
            </li>
         </#list>
      </#if>
   </ul>
   <h2>Timelines</h2>
   <ul>
      <#list reporter.reports as report>
         <li>
            <a href="timeline_${report.configuration.name}_${report.cluster.clusterIndex}.html">${report.configuration.name} on ${report.cluster}</a>
         </li>
      </#list>
   </ul>
   <hr/>
   Generated on ${.now} by RadarGun
   JDK: ${reporter.getSystemProperty("java.vm.name")} ${reporter.getSystemProperty("java.vm.version")}, ${reporter.getSystemProperty("java.vm.vendor")}
   OS: ${reporter.getSystemProperty("os.name")} ${reporter.getSystemProperty("os.version")}, ${reporter.getSystemProperty("os.arch")}
</body>
</html>

<#macro writeProperty name definition>
<#if definition.getClass().getSimpleName() == "SimpleDefinition">
<li>
   ${name} : ${definition}
</li>
<#elseif definition.getClass().getSimpleName() == "ComplexDefinition">
<li>
    <ul>
       <#list definition.attributes?keys as attributeKey>
            <@writeProperty name=attributeKey definition=(definition.attributes)[attributeKey]/>
         </#list>
    </ul>
</li>
</#if>
</#macro>

<#function getConfigFileName configurationName group clusterIndex filename>
   <#local result = "original_" + configurationName + "_" + group + "_" + clusterIndex + "_" + filename />
   <#local result = indexDocument.removeFileSeparator(result)/>
   <#return result />
</#function>