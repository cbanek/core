/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2020.                            (c) 2020.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
*  $Revision: 5 $
*
************************************************************************
*/

package ca.nrc.cadc.rest;

import ca.nrc.cadc.auth.NotAuthenticatedException;
import ca.nrc.cadc.io.ByteLimitExceededException;
import ca.nrc.cadc.log.WebServiceLogInfo;
import ca.nrc.cadc.net.HttpTransfer;
import ca.nrc.cadc.net.IncorrectContentChecksumException;
import ca.nrc.cadc.net.IncorrectContentLengthException;
import ca.nrc.cadc.net.ResourceAlreadyExistsException;
import ca.nrc.cadc.net.ResourceNotFoundException;
import ca.nrc.cadc.net.TransientException;
import java.io.IOException;
import java.io.OutputStream;
import java.security.AccessControlException;
import java.security.PrivilegedExceptionAction;
import java.security.cert.CertificateException;
import java.util.Map;
import org.apache.log4j.Logger;

/**
 * Base REST action class.
 * 
 * @author pdowler
 */
public abstract class RestAction implements PrivilegedExceptionAction<Object> {
    private static final Logger log = Logger.getLogger(RestAction.class);
    
    public static final String STATE_MODE_KEY = "-" + RestAction.class.getName() + ".state";
    public static final String STATE_OFFLINE = "Offline";
    public static final String STATE_OFFLINE_MSG = "System is offline for maintainence";
    public static final String STATE_READ_ONLY = "ReadOnly";
    public static final String STATE_READ_ONLY_MSG = "System is in read-only mode for maintainence";
    public static final String STATE_READ_WRITE = "ReadWrite";
    
    /**
     * Current readable state. A service is readable in READ_WRITE or READ_ONLY mode.
     * Subclasses that implement application logic must check the readable flag and
     * respond with an appropriate message when necessary.
     */
    protected boolean readable = true;
    
    /**
     * Current writable state. A service is writable in READ_WRITE mode. Subclasses that 
     * implement application logic must check the readable flag and respond with an 
     * appropriate message when necessary.
     */
    protected boolean writable = true;

    /**
     * The application name is a string unique to the application.  It
     * can be used to prefix log messages, JNDI key names, etc. that are common
     * to components of the application.
     */
    protected String appName;
    
    /**
     * The componentID is a string unique to a single instance of RestServlet. It
     * can be used to prefix log messages, JNDI key names, System properties, etc. 
     * It is not a path like one might get from SyncInput.getContextPath() and should
     * never be parsed or interpreted.
     */
    protected String componentID;
    
    /**
     * Map of init params from the web deployment descriptor. This map does not include
     * the init params that configure the http method action classes.
     */
    protected Map<String,String> initParams;
    
    /**
     * Wrapper around the HTTP request.
     */
    protected SyncInput syncInput;
    
    /**
     * Wrapper around the HTTP response.
     */
    protected SyncOutput syncOutput;
    
    protected WebServiceLogInfo logInfo;

    public static final String URLENCODED = "application/x-www-form-urlencoded";
    public static final String MULTIPART = "multipart/form-data";

    protected RestAction()
    {
        super();
    }

    /**
     * Initialise readable and writable state. This method will only downgrade 
     * the state to !readable and !writable and will never restore them to true.
     * In this implementation, the state is stored in system property named
     * with the appName + STATE_MODE_KEY so the state is shared by all endpoints in an
     * application. 
     * 
     * The design philosophy is that an application will set the state via a WebService 
     * implementation (see cadc-vosi library), which has access to the same appName. 
     * The VOSI AvailabilityServlet supports POST requests to change the state. So,
     * a service operator can POST to the /appName/availability resource, the AvailabilityServlet
     * will call setState(String) on the WebService implementation, and the WebService impl is
     * responsible for setting the appName + STATE_MODE_KEY system property to one of 
     * STATE_READ_WRITE, STATE_READ_ONLY, or STATE_OFFLINE, and then this method will check the
     * system property and set readable and writable flags. Finally, RestAction subclasses must check
     * the flags and allow or disallow the request.
     */
    protected void initState() {
        String key = appName + STATE_MODE_KEY;
        String val = System.getProperty(key);
        log.debug("initState: " + key + "=" + val);
        if (STATE_OFFLINE.equals(val)) {
            readable = false;
            writable = false;
        } else if (STATE_READ_ONLY.equals(val)) {
            writable = false;
        }
    }
    
    /**
     * Create inline content handler to process non-form data. Non-form data could 
     * be a document or part of a multi-part request). Null return value is allowed 
     * if the service never expects non-form data or wants to ignore non-form data.
     * 
     * @return configured InlineContentHandler
     */
    abstract protected InlineContentHandler getInlineContentHandler();

    /**
     * Implemented by subclass
     *
     * The following exceptions, when thrown by this function, are
     * automatically mapped into HTTP errors by RestAction class:
     *
     *  java.lang.IllegalArgumentException : 400
     *  java.security.AccessControlException : 403
     *  java.security.cert.CertificateException : 403
     *  ca.nrc.cadc.net.ResourceNotFoundException : 404
     *  ca.nrc.cadc.net.ResourceAlreadyExistsException : 409
     *  ca.nrc.cadc.net.IncorrectContentChecksumException : 412
     *  ca.nrc.cadc.net.IncorrectContentLengthException : 412
     *  ca.nrc.cadc.io.ByteLimitExceededException : 413
     *  ca.nrc.cadc.net.TransientException : 503
     *  java.lang.RuntimeException : 500
     *  java.lang.Error : 500
     * 
     * @throws Exception for standard application failure
     */
    public abstract void doAction() throws Exception;

    public void setLogInfo(WebServiceLogInfo logInfo)
    {
        this.logInfo = logInfo;
    }

    public void setAppName(String appName) {
        this.appName = appName;
        initState();
    }
            
    public void setComponentID(String componentID) {
        this.componentID = componentID;
    }

    public void setInitParams(Map<String, String> initParams) {
        this.initParams = initParams;
    }

    public void setSyncInput(SyncInput syncInput)
    {
        this.syncInput = syncInput;
    }

    public void setSyncOutput(SyncOutput syncOutput)
    {
        this.syncOutput = syncOutput;
    }

    // return Object ignored; method signature from PrivilegedExceptionAction
    @Override
    public Object run()
        throws Exception
    {
        boolean ioExceptionOnInput = true;
        try
        {
            logInfo.setSuccess(false);
            if (syncInput != null)
            {
                syncInput.init();
                ioExceptionOnInput = false;
            }
            doAction();

            logInfo.setSuccess(true);
        }
        catch(NotAuthenticatedException ex) {
            logInfo.setSuccess(true);
            handleException(ex, 401, ex.getMessage(), false, false);
        }
        catch(AccessControlException ex)
        {
            logInfo.setSuccess(true);
            handleException(ex, 403, ex.getMessage(), false, false);
        }
        catch(CertificateException ex)
        {
            handleException(ex, 403, "permission denied -- reason: invalid proxy certficate", false, true);
        }
        catch(IllegalArgumentException ex)
        {
            logInfo.setSuccess(true);
            handleException(ex, 400, ex.getMessage(), false, true);
        }
        catch(ResourceNotFoundException ex)
        {
            logInfo.setSuccess(true);
            handleException(ex, 404, ex.getMessage(), false, false);
        }
        catch(ResourceAlreadyExistsException ex)
        {
            logInfo.setSuccess(true);
            handleException(ex, 409, ex.getMessage(), false, false);
        }
        catch (IncorrectContentChecksumException | IncorrectContentLengthException ex)
        {
            logInfo.setSuccess(true);
            handleException(ex, 412, ex.getMessage(), false, false);
        }
        catch(ByteLimitExceededException ex)
        {
            logInfo.setSuccess(true);
            handleException(ex, 413, ex.getMessage(), false, false);
        }
        catch(UnsupportedOperationException ex)
        {
            logInfo.setSuccess(true);
            handleException(ex, 400, ex.getMessage(), false, false);
        }
        catch(TransientException ex)
        {
            logInfo.setSuccess(true);
            syncOutput.setHeader(HttpTransfer.SERVICE_RETRY, ex.getRetryDelay());
            handleException(ex, 503, "temporarily unavailable: " + syncInput.getPath(), true, false);
        }
        catch(IOException ex) {
            if (ioExceptionOnInput) {
                // failed to read input stream
                logInfo.setSuccess(false);
                handleException(ex, 500, "failed to read input -- reason: " + ex.getMessage(), false, true);
            } else {
                // same as Error below
                logInfo.setSuccess(false);
                handleException(ex, 500, "unexpected error: " + syncInput.getPath(), true, true);
            }
            
        }
        catch(RuntimeException unexpected)
        {
            logInfo.setSuccess(false);
            handleException(unexpected, 500, "unexpected failure: " + syncInput.getPath(), true, true);
        }
        catch(Error unexpected)
        {
            logInfo.setSuccess(false);
            handleException(unexpected, 500, "unexpected error: " + syncInput.getPath(), true, true);
        }

        return null;
    }

    /**
     * Add message to logInfo, print stack trace in debug level, and try to send a
     * response to output. If the output stream has not been opened already, set the
     * response code and write the message in text/plain. Optionally write a full
     * except5ion stack trace to output (showExceptions = true).
     *
     * @param ex exception to handle
     * @param code HTTP status code
     * @param message message body
     * @param showStackTrace  log stack trace
     * @param showExceptions  show exceptions in output
     * @throws IOException if write to output fails
     */
    protected void handleException(
        Throwable ex, int code, String message, boolean showStackTrace, boolean showExceptions)
            throws IOException
    {
        logInfo.setMessage(message);
        if (showStackTrace)
        {
            log.error(message, ex);
        }
        else
        {
            log.debug(message, ex);
        }
        if (!syncOutput.isOpen())
        {
            syncOutput.setCode(code);
            syncOutput.setHeader("Content-Type", "text/plain");

            OutputStream os = syncOutput.getOutputStream();
            StringBuilder sb = new StringBuilder();
            sb.append(message);

            if (showExceptions)
            {
                sb.append(ex.toString()).append("\n");
                Throwable cause = ex.getCause();
                while (cause != null)
                {
                    sb.append("cause: ");
                    sb.append(cause.toString()).append("\n");
                    cause = cause.getCause();
                }
            }

            os.write(sb.toString().getBytes());
        }
        else
            log.error("unexpected situation: SyncOutput is open", ex);
    }

}
