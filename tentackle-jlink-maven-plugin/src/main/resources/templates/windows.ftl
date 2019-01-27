
pushd %~dp0
set script_dir=%CD%
popd
cd %script_dir
cd ..

<#if mainModule != "">
<#-- modular application -->
  bin\java${(modulePath!="")?then(" -p " + modulePath, "")}${(classPath!="")?then(" -cp " + classPath, "")} -m ${mainModule}/${mainClass}
<#else>
<#-- classpath application -->
  bin\java -cp ${classPath} ${mainClass}
</#if>
