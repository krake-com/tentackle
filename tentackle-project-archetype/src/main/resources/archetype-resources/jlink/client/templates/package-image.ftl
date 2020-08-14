<#-- template to create the options file for the jpackage tool to create the application image -->
<#if osName?upper_case?contains("WIN")>

<#elseif osName?upper_case?contains("MAC")>

<#else>

</#if>
--java-options "--add-exports javafx.graphics/com.sun.javafx.css=org.tentackle.fx"
--java-options "--add-exports javafx.graphics/com.sun.javafx.scene=org.tentackle.fx"
--java-options "--add-exports javafx.graphics/com.sun.javafx.scene.traversal=org.tentackle.fx"
--java-options "--add-exports javafx.fxml/com.sun.javafx.fxml=org.tentackle.fx"
--java-options "--add-exports javafx.graphics/com.sun.javafx.application=org.tentackle.fx.rdc"
--java-options "--add-opens javafx.controls/javafx.scene.control=org.tentackle.fx"