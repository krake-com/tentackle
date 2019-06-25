<#-- template to create the name of the runner script -->
<#if osName?upper_case?contains("WIN")>
server.cmd
<#else>
server.sh
</#if>