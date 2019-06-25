<#-- template to create the name of the runner script -->
<#if osName?upper_case?contains("WIN")>
client.cmd
<#else>
client.sh
</#if>