<#-- template to create the name of the runner script -->
<#if osName?upper_case?contains("WIN")>
daemon.cmd
<#else>
daemon.sh
</#if>