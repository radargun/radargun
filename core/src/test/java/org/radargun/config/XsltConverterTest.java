package org.radargun.config;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Test @{@link XsltConverter}
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public class XsltConverterTest {

   @Test
   public void testIsXsltAttribute() {
      Assert.assertTrue(new XsltConverter().isXsltAttribute("xslt(foo, bar)"));
      Assert.assertFalse(new XsltConverter().isXsltAttribute("xslt/foo.xml"));
      Assert.assertFalse(new XsltConverter().isXsltAttribute("foo/bar.xml"));
      Assert.assertFalse(new XsltConverter().isXsltAttribute(null));
   }

   @Test
   public void testTransform() throws IOException {
      String outputFile = new XsltConverter().convertToString("xslt(input.xml, transform.xslt)");
      assertOutput(XsltConverter.getFile("expected-output.xml"), new File(outputFile));
   }

   @Test
   public void testEvaluatorPath() throws URISyntaxException, IOException {
      File inputFile = new File(XsltConverter.class.getResource("/input.xml").toURI());
      File xsltFile = new File(XsltConverter.class.getResource("/transform.xslt").toURI());
      System.setProperty("INPUT_FILE_PATH", inputFile.getParent());
      System.setProperty("XSLT_FILE_PATH", xsltFile.getParent());
      String outputFile = new XsltConverter().convertToString("xslt(${INPUT_FILE_PATH}/input.xml, ${XSLT_FILE_PATH}/transform.xslt)");
      assertOutput(XsltConverter.getFile("expected-output.xml"), new File(outputFile));
   }

   @Test
   public void testCustomOutputDir() throws IOException {
      String tmpDir = File.createTempFile("test", "Xslt").getParentFile().getAbsolutePath();
      File outputFile = new File(new XsltConverter().convertToString("xslt(input.xml, transform.xslt, "+ tmpDir +")"));
      assertOutput(XsltConverter.getFile("expected-output.xml"), outputFile);
      Assert.assertEquals(tmpDir, outputFile.getParentFile().getAbsolutePath());
   }

   @Test
   public void testOutputDirDoesNotExists() throws IOException {
      String tmpDir = File.createTempFile("test", "Xslt").getParentFile().getAbsolutePath() + File.separatorChar + "foo" + File.separatorChar + "bar";
      File outputFile = new File(new XsltConverter().convertToString("xslt(input.xml, transform.xslt, "+ tmpDir +")"));
      assertOutput(XsltConverter.getFile("expected-output.xml"), outputFile);
      Assert.assertEquals(tmpDir, outputFile.getParentFile().getAbsolutePath());
   }

   private void assertOutput(File expectedOuputFile, File currentOutputFile) throws IOException {
      String expectedOutput = readContent(expectedOuputFile);
      String currentOutput = readContent(currentOutputFile);
      Assert.assertEquals(expectedOutput, currentOutput);
   }

   private String readContent(File file) throws IOException {
      return new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
   }
}
