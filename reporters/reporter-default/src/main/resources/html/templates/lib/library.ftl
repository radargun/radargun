<#function expandableRowsInList rowCount elementCounter>
   <#local result = "" />
   <#list 0 .. (rowCount - 1) as count>
      <#local result = result + "switch_li_display('e" + (elementCounter + count) + "'); " />
   </#list>
   <#return result />
</#function>

<#function expandableRows rowCount elementCounter>
   <#local result = "" />
   <#list 0 .. (rowCount - 1) as count>
      <#local result = result + "switch_visibility('e" + (elementCounter + count) + "'); " />
   </#list>
   <#return result />
</#function>
