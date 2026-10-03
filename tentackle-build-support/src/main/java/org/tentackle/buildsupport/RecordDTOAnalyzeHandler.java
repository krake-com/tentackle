/*
 * Tentackle - https://tentackle.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */


package org.tentackle.buildsupport;

import org.tentackle.common.Settings;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;


/**
 * Handler for the {@code @RecordDTO} annotation.
 * <p>
 * Writes a {@link RecordDTOInfo} for each annotated record to {@code <record>/record.info}
 * in the analyze directory.
 *
 * @author harald
 */
public class RecordDTOAnalyzeHandler extends AbstractAnalyzeHandler {

  private AnalyzeProcessor processor;     // the annotation processor

  @Override
  public void setProcessor(AnalyzeProcessor processor) {
    this.processor = processor;
  }

  @Override
  public AnalyzeProcessor getProcessor() {
    return processor;
  }

  @Override
  public void processAnnotation(TypeElement annotationType, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
      if (element.getKind().equals(ElementKind.ANNOTATION_TYPE)) {
        continue;   // ignore annotated annotations (such as in tentackle-core)
      }
      if (element.getKind().equals(ElementKind.RECORD)) {
        TypeElement recordElement = (TypeElement) element;
        String recordName = recordElement.toString();
        if (getProcessor().isDebugLogging()) {
          print(Kind.NOTE, "found annotated record " + recordName, element);
        }
        try {
          // create info object
          RecordDTOInfo info = new RecordDTOInfo(getProcessor().getProcessingEnvironment(), recordElement);
          File outputDir = getDirectory(getProcessor().getAnalyzeDir(), recordName);
          outputDir.mkdirs();
          File outputFile = new File(outputDir, RecordDTOInfo.INFO_FILE_NAME);
          try (PrintWriter pw = new PrintWriter(
              new BufferedWriter(
                  new OutputStreamWriter(
                      new FileOutputStream(outputFile), Settings.getEncodingCharset())))) {
            info.write(pw);
          }
          if (getProcessor().isDebugLogging()) {
            print(Kind.NOTE, "created " + outputFile, element);
          }
        }
        catch (IOException ex) {
          print(Kind.ERROR, "extracting RecordDTOInfo failed: " + ex, element);
        }
      }
      else {
        print(Kind.WARNING, "annotated element '" + element + "' is not a record -> ignored", element);
      }
    }
  }

}
