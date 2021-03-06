/*
 ************************************************************************
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 *
 * (c) 2016.                          (c) 2016.
 * National Research Council            Conseil national de recherches
 * Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
 * All rights reserved                  Tous droits reserves
 *
 * NRC disclaims any warranties         Le CNRC denie toute garantie
 * expressed, implied, or statu-        enoncee, implicite ou legale,
 * tory, of any kind with respect       de quelque nature que se soit,
 * to the software, including           concernant le logiciel, y com-
 * without limitation any war-          pris sans restriction toute
 * ranty of merchantability or          garantie de valeur marchande
 * fitness for a particular pur-        ou de pertinence pour un usage
 * pose.  NRC shall not be liable       particulier.  Le CNRC ne
 * in any event for any damages,        pourra en aucun cas etre tenu
 * whether direct or indirect,          responsable de tout dommage,
 * special or general, consequen-       direct ou indirect, particul-
 * tial or incidental, arising          ier ou general, accessoire ou
 * from the use of the software.        fortuit, resultant de l'utili-
 *                                      sation du logiciel.
 *
 *
 * @author goliaths
 * @version $ Revision: $
 *
 *
 ****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
 ************************************************************************
 */


package ca.nrc.cadc.exec;

import ca.nrc.cadc.util.StringUtil;
import org.apache.log4j.Level;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileReader;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import ca.nrc.cadc.util.Log4jInit;

/**
 *
 */
public class BuilderOutputGrabberTest
{
//    static
//    {
//        String[] args = {"-d", "-v"};
//        try
//        {
//            LogArgUtil.initialize(
//                    BuilderOutputGrabberTest.class.getPackage().getName(),
//                    args);
//        }
//        catch(Exception ex)
//        {
//            fail();
//        }
//    }

    @Before
    public void setUp()
    {
        Log4jInit.setLevel("ca.nrc.cadc.exec", Level.INFO);
    }

    @Test
    public void captureOutputSucceeds() throws Exception
    {
        BuilderOutputGrabber bog = new BuilderOutputGrabber();
        String cmd = "/bin/date";
        bog.captureOutput(cmd);
        assertEquals(0, bog.getExitValue());
        assertEquals("", bog.getErrorOutput());
        assertNotNull(bog.getOutput());
    }

    @Test
    public void captureOutputFails() throws Exception
    {
        BuilderOutputGrabber bog = new BuilderOutputGrabber();
        String cmd = "date -d \"2011-01-01 01:01:01\" +%s";
        bog.captureOutput(cmd);
        assertEquals(-1, bog.getExitValue());
        assertNotNull(bog.getErrorOutput());
        assertEquals("", bog.getOutput());
    }

    @Test
    public void captureOutputSucceedsArray() throws Exception
    {
        BuilderOutputGrabber bog = new BuilderOutputGrabber();
        String[] cmd = {"/bin/bash", "-c", "ls -al $RPS"};
        Map<String, String> env = new TreeMap<String, String>();
        env.put("RPS", "/usr/local");
        bog.captureOutput(cmd, env);
        assertEquals("", bog.getErrorOutput());
        assertTrue("Output should not be empty.",
                   StringUtil.hasText(bog.getOutput()));
    }

    @Test
    public void captureOutputSucceedsEnvironment() throws Exception
    {
        BuilderOutputGrabber bog = new BuilderOutputGrabber();
        String[] cmd = {"/bin/bash", "-c", "ls -al $CADC_ROOT"};
        Map<String, String> env = new TreeMap<String, String>();
        env.put("CADC_ROOT", "/usr/local");
        bog.captureOutput(cmd, env);
        assertEquals("", bog.getErrorOutput());
        assertNotNull(bog.getOutput());
    }

    @Test
    public void captureOutputSucceedsFile() throws Exception
    {
        File file = new File("build/tmp");
        BuilderOutputGrabber bog = new BuilderOutputGrabber();
        String[] cmd = {"/bin/bash", "-c", "ls -al $RPS >> ls_output"};
        Map<String, String> env = new TreeMap<String, String>();
        env.put("RPS", "/usr/local");
        bog.captureOutput(cmd, env, file);
        assertEquals("", bog.getErrorOutput());
        assertEquals("", bog.getOutput());
        File outputFile = new File(file, "ls_output");
        assertTrue(outputFile.exists());

        final char[] outputContent =
                new char[new Long(outputFile.length()).intValue()];
        new FileReader(outputFile).read(outputContent);
        assertTrue("File contents",
                   StringUtil.hasText(String.valueOf(outputContent)));
    }

}
