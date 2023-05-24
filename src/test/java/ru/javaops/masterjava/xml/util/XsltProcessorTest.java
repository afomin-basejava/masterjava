package ru.javaops.masterjava.xml.util;

import com.google.common.io.Resources;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class XsltProcessorTest {
    @Test
    public void transform() throws Exception {
        try (InputStream xslInputStream = Resources.getResource("payload.xsl").openStream();
             InputStream xmlInputStream = Resources.getResource("payload.xml").openStream();
             BufferedWriter bw = Files.newBufferedWriter(Paths.get("payload.html"));) {

            XsltProcessor processor = new XsltProcessor(xslInputStream);
            processor.setParameter("project", "topjava");
            String transform = processor.transform(xmlInputStream);
            System.out.println(transform);

            bw.write(transform);
        }
    }
}
