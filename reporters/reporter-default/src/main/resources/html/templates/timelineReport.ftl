<html>
<head>
    <title>${timelineDocument.getTitle()}</title>
    <link rel="stylesheet" href="style.css">
    <script src="script.js"></script>
</head>

<body>
   <h1> ${timelineDocument.title} Timeline</h1>
   <table class="graphTable floatLeft">
      <#list timelineDocument.getValueCategoriesOfType(categoryType)?keys as key>
         <#assign valueCategory = key />
         <#assign valueCategoryId = timelineDocument.getValueCategoriesOfType(categoryType)?api.get(key) />

         <#assign relativeDomainFile = "domain_${timelineDocument.getConfigName()}_relative.png" />
         <#assign absoluteDomainFile = "domain_${timelineDocument.getConfigName()}_absolute.png" />

         <tr>
            <th colspan="2"> ${valueCategory.getName()} </th>
         </tr>
         <#assign rangeFile = timelineDocument.range(valueCategory, valueCategoryId) />
         <tr>
            <td>
               <img src="${rangeFile}">
            </td>
            <td>
               <div class="graphDiv"
                    style="width: ${timelineDocument.getConfiguration().width}; height: ${timelineDocument.getConfiguration().height}; ">
                  <#list timelineDocument.getTimelines() as timeline>
                     <#assign valueChartFile = timelineDocument.getValueChartFile(valueCategoryId, timeline.slaveIndex) />
                     <img class="topLeft" id="layer_${valueCategoryId}_${timeline.slaveIndex}" src="${valueChartFile}">

                     <#list timeline.getEventCategories() as eventCategory>
                        <#assign events = timeline.getEvents(eventCategory)!false />
                        <#if !events?is_boolean>
                           <#assign eventCategoryId = timelineDocument.getEventCategories()[eventCategory] />
                           <#assign eventChartFile = timelineDocument.generateEventChartFile(eventCategoryId, timeline.slaveIndex) />
                           <img class="topLeft"
                                id="layer_${valueCategoryId}_${timelineDocument.eventCategories[eventCategory]}_${timeline.slaveIndex}"
                                src="${eventChartFile}">
                        </#if>
                     </#list>
                  </#list>
               </div>
            </td>
          </tr>
         <tr>
            <td></td>
            <td>
               <img src="${relativeDomainFile}">
            </td>
         </tr>
         <tr>
            <td> </td>
            <td>
               <img src="${absoluteDomainFile}">
            </td>
         </tr>
      </#list>
   </table>

   <#-- Checkboxes -->
   <div class="floatLeft">
      <#list timelineDocument.getEventCategories()?keys as key>
         <#assign value = timelineDocument.getEventCategories()[key] />
          <input id="cat_${value}" type="checkbox" checked="checked"
                 onclick="${resetDisplay(value)}">
          <strong>${key}</strong>
          <br/>
      </#list>
      <br/><br/>
      <#assign groups = timelineDocument.getCluster().getGroups() />
      <#list timelineDocument.getTimelines() as timeline>
         <span style="background-color: ${timelineDocument.getCheckboxColor(timeline)}">&nbsp</span>
         <input type="checkbox" checked="checked" id="slave_${timeline.slaveIndex}"
                onclick="${resetDisplayTimeline(timeline)}">
         <#if (timeline.slaveIndex >= 0)>
            <strong>
               Slave
               <#if (groups?size > 1)>
                  ${timeline.slaveIndex} ${timelineDocument.getCluster().getGroup(timeline.slaveIndex).name}
               <#else>
                  ${timeline.slaveIndex}
               </#if>
            </strong> <br/>
         <#else>
            <strong>Master</strong><br>
         </#if>
      </#list>
      <#if (groups?has_content) >
         <#list 0..(groups?size -1) as groupID>
            <span>&nbsp;</span>
            <input type="checkbox" checked="checked" id="group_${groupID}"
               onclick="${resetDisplayGroup(groups, groupID)}">
             <strong>Group ${groups?api.get(groupID).name} </strong><br>
         </#list>
      </#if>
   </div>
</body>
</html>

<#function resetDisplay value >
   <#local result = "" />
   <#list timelineDocument.timelines as timeline>
      <#list timelineDocument.valueCategories?values as valuesId>
      <#local result = result + (String.format("reset_display('layer_%d_%d_%d', this.checked && is_checked('slave_%d'), 'block');",
         valuesId, value, timeline.slaveIndex, timeline.slaveIndex)) />
      </#list>
   </#list>
   <#return result/>
</#function>

<#function resetDisplayTimeline timeline>
   <#local result = "" />
   <#list timelineDocument.valueCategories?values as valuesId>
      <#local result = result + (String.format("reset_display('layer_%d_%d', this.checked, 'block');",
         valuesId, timeline.slaveIndex)) />
      <#list timelineDocument.eventCategories?values as eventsId>
         <#local result = result + (String.format("reset_display('layer_%d_%d_%d', this.checked && is_checked('cat_%d'), 'block');",
            valuesId, eventsId, timeline.slaveIndex, eventsId)) />
      </#list>
   </#list>
   <#return result/>
</#function>

<#function resetDisplayGroup groups groupId>
   <#local result = "" />
   <#list timelineDocument.cluster.getSlaves(groups?api.get(groupId).name) as slaveIndex>
      <#list timelineDocument.valueCategories?values as valuesId>
         <#local result = result + (String.format("document.getElementById('slave_%d').checked = this.checked;", slaveIndex)) />
         <#local result = result + String.format("reset_display('layer_%d_%d', this.checked, 'block');",
            valuesId, slaveIndex) />
      </#list>
   </#list>
   <#return result/>
</#function>
