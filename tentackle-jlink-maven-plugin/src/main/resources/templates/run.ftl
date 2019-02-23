<#-- template to create the runner script -->
<#if osName?upper_case?contains("WIN")>
pushd %~dp0
set script_dir=%CD%
popd
cd %script_dir%
cd ..
<#else>
#!/bin/sh
cd $(dirname $(dirname $(readlink -f "$0")))
</#if>

<#if mainModule != "">
<#-- modular application -->
bin/java${(modulePath!="")?then(" -p " + modulePath, "")}${(classPath!="")?then(" -cp " + classPath, "")} -m ${mainModule}/${mainClass}
<#else>
<#-- classpath application -->
bin/java -cp ${classPath} ${mainClass}
</#if>
