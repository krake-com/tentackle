
cd $(dirname $(dirname $(readlink -f "$0")))

<#if mainModule != "">
<#-- modular application -->
  bin/java${(modulePath!="")?then(" -p " + modulePath, "")}${(classPath!="")?then(" -cp " + classPath, "")} -m ${mainModule}/${mainClass}
<#else>
<#-- classpath application -->
  bin/java -cp ${classPath} ${mainClass}
</#if>
