<#-- template to create the runner script -->
<#if osName?upper_case?contains("WIN")>
@echo off
pushd %~dp0
set script_dir=%CD%
popd
cd %script_dir%
cd ..
if not exist logs mkdir logs
  <#if mainModule != "">
    <#-- modular application -->
bin\java${(modulePath!="")?then(" -p " + modulePath, "")}${(classPath!="")?then(" -cp " + classPath, "")} ^
  --add-exports javafx.graphics/com.sun.javafx.css=org.tentackle.fx ^
  --add-exports javafx.graphics/com.sun.javafx.scene=org.tentackle.fx ^
  --add-exports javafx.graphics/com.sun.javafx.scene.traversal=org.tentackle.fx ^
  --add-exports javafx.fxml/com.sun.javafx.fxml=org.tentackle.fx ^
  --add-exports javafx.graphics/com.sun.javafx.application=org.tentackle.fx.rdc ^
  --add-opens javafx.controls/javafx.scene.control=org.tentackle.fx ^
  -m ${mainModule}/${mainClass}
  <#else>
    <#-- classpath application -->
bin\java -cp ${classPath} ${mainClass}
  </#if>
<#else>
#!/bin/sh
cd $(dirname $(dirname $(readlink -f "$0")))
mkdir logs 2>/dev/null
  <#if mainModule != "">
    <#-- modular application (using GTK2 since DnD doesnt work with GTK3) -->
bin/java -Djdk.gtk.version=2${(modulePath!="")?then(" -p " + modulePath, "")}${(classPath!="")?then(" -cp " + classPath, "")} \
  --add-exports javafx.graphics/com.sun.javafx.css=org.tentackle.fx \
  --add-exports javafx.graphics/com.sun.javafx.scene=org.tentackle.fx \
  --add-exports javafx.graphics/com.sun.javafx.scene.traversal=org.tentackle.fx \
  --add-exports javafx.fxml/com.sun.javafx.fxml=org.tentackle.fx \
  --add-exports javafx.graphics/com.sun.javafx.application=org.tentackle.fx.rdc \
  --add-opens javafx.controls/javafx.scene.control=org.tentackle.fx \
  -m ${mainModule}/${mainClass}
  <#else>
    <#-- classpath application -->
bin/java -cp ${classPath} ${mainClass}
  </#if>
</#if>