<html>
<head>
   <title>${normalized.getTitle()}</title>
   <link rel="stylesheet" href="style.css">
</head>

<body>
   <table>
      <tr>
         <th></th>
         <#list normalized.getWorkers() as worker>
            <th class="center">
               Worker ${worker}
            </th>
         </#list>
      </tr>

      <#list normalized.getProperties()?keys as key>
         <#assign difference = normalized.checkForDifference(normalized.getProperties()[key]?values) />
         <tr>
            <#if difference>
               <th class="difference left"> ${key} </th>
            <#else>
               <th class="left"> ${key} </th>
            </#if>
            <#list normalized.getProperties()[key]?values as value>
               <#if difference>
                  <td class="difference"> ${value} </td>
               <#else>
                  <td>${value}</td>
               </#if>
            </#list>
         </tr>
      </#list>
   </table>
</body>
</html>

