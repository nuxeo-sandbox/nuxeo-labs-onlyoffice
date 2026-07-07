/*
 * (C) Copyright 2025 Hyland (http://hyland.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Damon Brown
 *     Thibaud Arguillere
 *       (Migration to LTS 2025 with the help of OpenCode / Claude Opus)
 */
package nuxeo.labs.onlyoffice.conversion;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestConversionCompatbility {

  private static List<ConversionCompatibility> MATRIX = null;

  @BeforeClass
  public static void loadConversionCompatibility() {
    ObjectMapper mapper = new ObjectMapper();
    try (InputStream in = TestConversionCompatbility.class.getResourceAsStream("/reference/conversion_matrix.json")) {
      MATRIX = mapper.readValue(in, new TypeReference<List<ConversionCompatibility>>() {
      });
    } catch (IOException iox) {

    }
  }

  boolean check(String srcType, String destType) {
    boolean compatConversion = false;
    for (ConversionCompatibility cc : MATRIX) {
      if (cc.accepts(srcType, destType)) {
        compatConversion = true;
        break;
      }
    }
    return compatConversion;
  }

  @Test
  public void typicalConversions() {
    Assert.assertTrue("doc-bmp", check("doc", "bmp"));
    Assert.assertTrue("xltm-ods", check("xltm", "ods"));
    Assert.assertTrue("pps-gif", check("pps", "gif"));
  }

  @Test
  public void failedConversions() {
    Assert.assertFalse("docx-docx", check("docx", "docx"));
    Assert.assertFalse("xps-rtf", check("xps", "rtf"));
    Assert.assertFalse("odp-odp", check("odp", "odp"));
  }
}
