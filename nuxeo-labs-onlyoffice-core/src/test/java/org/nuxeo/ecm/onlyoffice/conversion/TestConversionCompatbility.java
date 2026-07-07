package org.nuxeo.ecm.onlyoffice.conversion;

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
