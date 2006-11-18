/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.ISVNSSLManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.util.IMeasurable;
import org.tmatesoft.svn.core.internal.util.SVNBase64;
import org.tmatesoft.svn.core.internal.util.SVNSocketFactory;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.Version;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * @author TMate Software Ltd.
 *
 */
class HttpConnection {

    private OutputStream myOutputStream;
    private InputStream myInputStream;
    private Socket mySocket;

    private SVNURL mySVNRepositoryLocation;
    private SAXParser mySAXParser;

    private static SAXParserFactory ourSAXParserFactory;

    private Map myCredentialsChallenge;
    private ISVNAuthenticationManager myAuthManager;
    private SVNAuthentication myLastValidAuth;
    private ISVNProxyManager myProxyAuth;

    public HttpConnection(SVNURL location, SVNRepository repos) {
        mySVNRepositoryLocation = location;
        myAuthManager = repos.getAuthenticationManager();
    }

    private void connect() throws SVNException {
        if (mySocket == null || isStale()) {
            close();
            String host = mySVNRepositoryLocation.getHost();
            int port = mySVNRepositoryLocation.getPort();
            myProxyAuth = myAuthManager != null ? myAuthManager.getProxyManager(mySVNRepositoryLocation) : null;
            ISVNSSLManager sslManager = myAuthManager != null && isSecured() ? 
                    myAuthManager.getSSLManager(mySVNRepositoryLocation) : null;
            if (myProxyAuth != null && myProxyAuth.getProxyHost() != null) {
                mySocket = SVNSocketFactory.createPlainSocket(myProxyAuth.getProxyHost(), myProxyAuth.getProxyPort());
                if (isSecured()) {
                    Map props = new HashMap();
                    if (myProxyAuth.getProxyUserName() != null && myProxyAuth.getProxyPassword() != null) {
                        props.put("Proxy-Authorization", getProxyAuthString(myProxyAuth.getProxyUserName(), myProxyAuth.getProxyPassword()));
                    }
                    DAVStatus status = null;
                    try {
                        myOutputStream = SVNDebugLog.createLogStream(mySocket.getOutputStream());
                        sendHeader("CONNECT", mySVNRepositoryLocation.getHost() + ":" + mySVNRepositoryLocation.getPort(), props, null);
                        myOutputStream.flush();
                        status = readHeader(new HashMap());
                        if (status != null && status.getResponseCode() == 200) {
                            myInputStream = null;
                            myOutputStream = null;
                            mySocket = SVNSocketFactory.createSSLSocket(sslManager, host, port, mySocket);
                            return;
                        }
                    } catch (IOException e) {
                        SVNErrorManager.error("svn: Cannot establish connection to proxy server: '" + e.getMessage() + "'");
                    }
                    SVNErrorManager.error("svn: Cannot establish http tunnel for proxied secure connection: " + (status != null ? status.getErrorText() + "" : " for unknow reason"));
                }
            } else {
                mySocket = isSecured() ? SVNSocketFactory.createSSLSocket(sslManager, host, port) : SVNSocketFactory.createPlainSocket(host, port);
            }
        }
    }

    private boolean isSecured() {
        return "https".equals(mySVNRepositoryLocation.getProtocol());
    }

    private boolean isStale() throws SVNException {
        boolean isStale = true;
        if (mySocket != null) {
            isStale = false;
            try {
                if (mySocket.getInputStream().available() == 0) {
                    int timeout = mySocket.getSoTimeout();
                    try {
                        mySocket.setSoTimeout(1);
                        mySocket.getInputStream().mark(1);
                        int byteRead = mySocket.getInputStream().read();
                        if (byteRead == -1) {
                            isStale = true;
                        } else {
                            mySocket.getInputStream().reset();
                        }
                    } finally {
                        mySocket.setSoTimeout(timeout);
                    }
                }
            } catch (InterruptedIOException e) {
                if (!SocketTimeoutException.class.isInstance(e)) {
                    SVNErrorManager.error("svn: Connection timeout while connecting to '" + mySVNRepositoryLocation.toString() + "'");
                }
            } catch (IOException e) {
                isStale = true;
            }
        }
        return isStale;
    }

    public void close() {
        if (mySocket != null) {
            if (myInputStream != null) {
                try {
                    myInputStream.close();
                } catch (IOException e) {
                    //
                }
            }
            if (myOutputStream != null) {
                try {
                    myOutputStream.flush();
                } catch (IOException e) {
                    //
                }
            }
            if (myOutputStream != null) {
                try {
                    myOutputStream.close();
                } catch (IOException e) {
                    //
                }
            }
            try {
                mySocket.close();
            } catch (IOException e) {
                //
            }
            mySocket = null;
            myOutputStream = null;
            myInputStream = null;
        }
    }

    public DAVStatus request(String method, String path, Map header, InputStream body, DefaultHandler handler, int[] okCodes) throws SVNException {
        DAVStatus status = sendRequest(method, path, initHeader(0, null, header), body);
        // check okCodes, read to status if not ok.
        assertOk(method, path, status, okCodes);
        if (status != null && status.getResponseCode() == 204) {
            finishResponse(status.getResponseHeader());
        } else if (status != null) {
            readResponse(handler, status.getResponseHeader());
        }
        return status;
    }

    public DAVStatus request(String method, String path, Map header, StringBuffer reqBody, DefaultHandler handler, int[] okCodes) throws SVNException {
        DAVStatus status = sendRequest(method, path, initHeader(0, null, header), reqBody, okCodes);
        if (status != null && status.getResponseCode() == 204) {
            finishResponse(status.getResponseHeader());
        } else if (status != null) {
            readResponse(handler, status.getResponseHeader());
        }
        return status;
    }

    public DAVStatus request(String method, String path, int depth, String label, StringBuffer requestBody, OutputStream result, int[] okCodes) throws SVNException {
        DAVStatus status = sendRequest(method, path, initHeader(depth, label, null), requestBody, okCodes);
        if (status != null && status.getResponseCode() == 204) {
            finishResponse(status.getResponseHeader());
        } else if (status != null) {
            readResponse(result, status.getResponseHeader());
        }
        return status;
    }

    public DAVStatus request(String method, String path, int depth, String label, StringBuffer requestBody, DefaultHandler handler, int[] okCodes)
            throws SVNException {
        DAVStatus status = sendRequest(method, path, initHeader(depth, label, null), requestBody, okCodes);
        if (status != null && status.getResponseCode() == 204) {
            finishResponse(status.getResponseHeader());
        } else if (status != null) {
            readResponse(handler, status.getResponseHeader());
        }
        return status;
    }

    private DAVStatus sendRequest(String method, String path, Map header, StringBuffer requestBody, int[] okCodes) throws SVNException {
		byte[] request = null;
		if (requestBody != null) {
			try {
				request = requestBody.toString().getBytes("UTF-8");
			} catch (UnsupportedEncodingException e) {
				request = requestBody.toString().getBytes();
			}
		}
        DAVStatus status = sendRequest(method, path, header, request != null ? new ByteArrayInputStream(request) : null);
        assertOk(method, path, status, okCodes);
        return status;
    }

    private DAVStatus sendRequest(String method, String path, Map header, InputStream requestBody) throws SVNException {
        Map readHeader = new HashMap();
        if (myCredentialsChallenge != null) {
            myCredentialsChallenge.put("methodname", method);
            myCredentialsChallenge.put("uri", path);
        }
        String realm = null;
        SVNAuthentication auth = myLastValidAuth;
        while (true) {
            DAVStatus status;
            try {
                connect();
                if (auth != null && myCredentialsChallenge != null) {
                    header.put("Authorization", composeAuthResponce(auth, myCredentialsChallenge));
                }
                sendHeader(method, path, header, requestBody);
                SVNDebugLog.flushStream(myOutputStream);
                readHeader.clear();
                status = readHeader(readHeader);
            } catch (IOException e) {
                close();
                acknowledgeSSLContext(false);
                throw new SVNException(e);
            } 
            acknowledgeSSLContext(true);
            if (status != null
                    && (status.getResponseCode() == HttpURLConnection.HTTP_UNAUTHORIZED || status.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN)) {
                myLastValidAuth = null;
                try {
                    skipRequestBody(readHeader);
                } catch (IOException e1) {
                    //
                }
                close();
                myCredentialsChallenge = DAVUtil.parseAuthParameters((String) readHeader.get("WWW-Authenticate"));
                if (myCredentialsChallenge == null) {
                    if (status.getResponseCode() == HttpURLConnection.HTTP_FORBIDDEN) {
                        throw new SVNAuthenticationException("Access forbidden");
                    }
                    throw new SVNAuthenticationException("Authentication challenge is not supported:\n" + readHeader.get("WWW-Authenticate"));
                }
                myCredentialsChallenge.put("methodname", method);
                myCredentialsChallenge.put("uri", path);
                realm = (String) myCredentialsChallenge.get("realm");
                realm = realm == null ? "" : " " + realm;
                realm = "<" + mySVNRepositoryLocation.getProtocol() + "://" + mySVNRepositoryLocation.getHost() + ":" + mySVNRepositoryLocation.getPort() + ">" + realm;
                if (myAuthManager == null) {
                    throw new SVNAuthenticationException("No credentials defined");
                }
                if (auth == null) {
                    auth = myAuthManager.getFirstAuthentication(ISVNAuthenticationManager.PASSWORD, realm, mySVNRepositoryLocation);
                } else {
                    myAuthManager.acknowledgeAuthentication(false, ISVNAuthenticationManager.PASSWORD, realm, null, auth);
                    auth = myAuthManager.getNextAuthentication(ISVNAuthenticationManager.PASSWORD, realm, mySVNRepositoryLocation);
                }
                // reset stream!
                if (requestBody instanceof ByteArrayInputStream) {
                    try {
                        requestBody.reset();
                    } catch (IOException e) {
                        //
                    }
                } else if (requestBody != null) {
                    throw new SVNAuthenticationException("Authentication failed");
                }
            } else if (status != null &&
                    (status.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || status.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP)) {
                try {
                    skipRequestBody(readHeader);
                } catch (IOException e1) {
                    //
                }
                close();
                // reconnect
                String newLocation = (String) readHeader.get("Location");
                if (newLocation == null) {
                    throw new SVNException("can't connect: " + status.getMessage());
                }
                int hostIndex = newLocation.indexOf("://");
                if (hostIndex > 0) {
                    hostIndex += 3;
                    hostIndex = newLocation.indexOf("/", hostIndex);
                }
                if (hostIndex > 0 && hostIndex < newLocation.length()) {
                    String newPath = newLocation.substring(hostIndex);
                    if (newPath.endsWith("/") &&
                            !newPath.endsWith("//") && !path.endsWith("/") &&
                            newPath.substring(0, newPath.length() - 1).equals(path)) {
                        path += "//";
                        continue;
                    }
                }
                throw new SVNException("HTTP 301 MOVED PERMANENTLY: " + newLocation);
            } else if (status != null) {
                if (auth != null && myAuthManager != null && realm != null) {
                    myAuthManager.acknowledgeAuthentication(true, ISVNAuthenticationManager.PASSWORD, realm, null, auth);
                }
                myLastValidAuth = auth;
                status.setResponseHeader(readHeader);
                return status;
            } else if (auth != null) {
                close();
                SVNErrorManager.error("svn: Cannot connecto to host '" + mySVNRepositoryLocation.getHost() + "'");
            } else {
                close();
                if (status != null && status.getResponseCode() >= 300) {
                    throw new SVNException(status.getMessage());
                }
                throw new SVNCancelException("svn: Authentication cancelled");
            }
        }
    }

    private void readError(String url, DAVStatus status) throws SVNException {
        if (status.getErrorText() != null) {
            return;
        }
        StringBuffer text = new StringBuffer();
        InputStream stream = null;
        try {
			stream = createInputStream(status.getResponseHeader(), getInputStream());
            byte[] buffer = new byte[32*1024];
            while (true) {
                int count = stream.read(buffer);
                if (count <= 0) {
                    break;
                }
                text.append(new String(buffer, 0, count));
            }
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            close();
            if (status.getResponseCode() == 404) {
                status.setErrorText("svn: '" + url + "' path not found");
            } else {
                String errorMessage = text.toString();
                if (errorMessage.indexOf("<m:human-readable") >= 0) {
                    errorMessage = errorMessage.substring(errorMessage.indexOf("<m:human-readable") + "<m:human-readable".length());
                    if (errorMessage.indexOf('>') >= 0) {
                        errorMessage = errorMessage.substring(errorMessage.indexOf('>') + 1);
                    }
                    if (errorMessage.indexOf("</m:human-readable>") >= 0) {
                        errorMessage = errorMessage.substring(0, errorMessage.indexOf("</m:human-readable>"));
                        errorMessage = "svn: " + errorMessage.trim();
                    }
                }
                status.setErrorText(errorMessage);
            }
        }
    }

    private void readResponse(OutputStream result, Map responseHeader) throws SVNException {
        InputStream stream = null;
        try {
			stream = createInputStream(responseHeader, getInputStream());
            byte[] buffer = new byte[32*1024];
            while (true) {
                int count = stream.read(buffer);
                if (count <= 0) {
                    break;
                }
                if (result != null) {
                    result.write(buffer, 0, count);
                }
            }
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            finishResponse(responseHeader);
            SVNDebugLog.flushStream(stream);
        }
    }

    private void readResponse(DefaultHandler handler, Map responseHeader) throws SVNException {
        InputStream is = null;
        try {
			is = createInputStream(responseHeader, getInputStream());
            XMLInputStream xmlIs = new XMLInputStream(is);

            if (handler == null) {
                while (true) {
                    int r = is.read();
                    if (r < 0) {
                        break;
                    }
                }
            } else {
                if (mySAXParser == null) {
                    mySAXParser = getSAXParserFactory().newSAXParser();
                }
                while (!xmlIs.isClosed()) {
                    mySAXParser.parse(xmlIs, handler);
                }
            }
        } catch (SAXException e) {
            if (e instanceof SAXParseException) {
                return;
            }
            if (e.getCause() instanceof SVNException) {
                throw (SVNException) e.getCause();
            }
            throw new SVNException(e);
        } catch (ParserConfigurationException e) {
            throw new SVNException(e);
        } catch (EOFException e) {
            // skip it.
        } catch (IOException e) {
            throw new SVNException(e);
        } finally {
            finishResponse(responseHeader);
            SVNDebugLog.flushStream(is);
        }
    }

    private static final char[] CRLF = { '\r', '\n' };
    private static final byte[] CRLF_BYTES = { '\r', '\n' };

    private void sendHeader(String method, String path, Map header, InputStream requestBody) throws IOException {
        StringBuffer sb = new StringBuffer();
        sb.append(method);
        sb.append(' ');
        
        boolean isProxied = myProxyAuth != null; 
        if (isProxied && !isSecured()) {
            // prepend path with host name.
            sb.append("http://");
            sb.append(mySVNRepositoryLocation.getHost());
            sb.append(":");
            sb.append(mySVNRepositoryLocation.getPort());
        }
        DAVUtil.getCanonicalPath(path, sb);
        sb.append(' ');
        sb.append("HTTP/1.1");
        sb.append(HttpConnection.CRLF);
        sb.append("Host: ");
        sb.append(mySVNRepositoryLocation.getHost());
        sb.append(":");
        sb.append(mySVNRepositoryLocation.getPort());
        sb.append(HttpConnection.CRLF);
        sb.append("User-Agent: ");
        sb.append(Version.getVersionString());
        sb.append(HttpConnection.CRLF);
        sb.append("Keep-Alive:");
        sb.append(HttpConnection.CRLF);
        sb.append("Connection: TE, Keep-Alive");
        sb.append(HttpConnection.CRLF);
        sb.append("TE: trailers");
        sb.append(HttpConnection.CRLF);
        if (isProxied && !isSecured() && myProxyAuth != null) {
            sb.append("Proxy-Authorization: " + getProxyAuthString(myProxyAuth.getProxyUserName(), myProxyAuth.getProxyPassword()));
            sb.append(HttpConnection.CRLF);
        }
        boolean chunked = false;
        if (requestBody instanceof ByteArrayInputStream) {
            sb.append("Content-Length: ");
            sb.append(requestBody.available());
        } else if (requestBody instanceof IMeasurable) { 
            sb.append("Content-Length: ");
            sb.append(((IMeasurable) requestBody).getLength());
        } else if (requestBody != null) {
            sb.append("Transfer-Encoding: chunked");
            chunked = true;
        } else {
            sb.append("Content-Lenght: 0");
        }
        sb.append(HttpConnection.CRLF);
        sb.append("Accept-Encoding: gzip");
        sb.append(HttpConnection.CRLF);
        if (header != null) {
            if (!header.containsKey("Content-Type")) {
                sb.append("Content-Type: text/xml; charset=\"utf-8\"");
                sb.append(HttpConnection.CRLF);
            }
            for (Iterator keys = header.keySet().iterator(); keys.hasNext();) {
                Object key = keys.next();
                sb.append(key.toString());
                sb.append(": ");
                sb.append(header.get(key).toString());
                sb.append(HttpConnection.CRLF);
            }
        }
        getOutputStream().write(sb.toString().getBytes());
        getOutputStream().write(HttpConnection.CRLF_BYTES);
        if (requestBody != null) {
            byte[] buffer = new byte[1024*32];
            while (true) {
                int read = requestBody.read(buffer);
                if (chunked) {
                    if (read > 0) {
                        getOutputStream().write(Integer.toHexString(read).getBytes());
                        getOutputStream().write(HttpConnection.CRLF_BYTES);
                        getOutputStream().write(buffer, 0, read);
                        getOutputStream().write(HttpConnection.CRLF_BYTES);
                    } else {
                        getOutputStream().write('0');
                        getOutputStream().write(HttpConnection.CRLF_BYTES);
                        getOutputStream().write(HttpConnection.CRLF_BYTES);
                        break;
                    }
                } else {
                    if (read > 0) {
                        getOutputStream().write(buffer, 0, read);
                    } else {
                        break;
                    }
                }
            }
        }
        getOutputStream().flush();
    }

    private DAVStatus readHeader(Map headerProperties) throws IOException {
        return readHeader(headerProperties, false);
    }
    private DAVStatus readHeader(Map headerProperties, boolean firstLineOnly) throws IOException {
        DAVStatus responseCode = null;
        StringBuffer line = new StringBuffer();
        InputStream is = SVNDebugLog.createLogStream(getInputStream());
        
        boolean firstLine = true;
        try {
            while (true) {
                int read = is.read();
                if (read < 0) {
                    return responseCode;
                }
                if (read != '\n' && read != '\r') {
                    line.append((char) read);
                    continue;
                }
                // eol read.
                if (read == '\r') {
                    is.mark(1);
                    read = is.read();
                    if (read < 0) {
                        return responseCode;
                    }
                    if (read != '\n') {
                        is.reset();
                    }
                    if (firstLineOnly) {
                        return DAVStatus.parse(line.toString());
                    }
                }
                String lineStr = line.toString();
                if (lineStr.trim().length() == 0) {
                    if (firstLine) {
                        line = new StringBuffer();
                        firstLine = false;
                        continue;
                    }
                    // first empty line (+ eol) read.
                    break;
                }
                firstLine = false;
    
                int index = line.indexOf(":");
                if (index >= 0 && headerProperties != null) {
                    String name = line.substring(0, index);
                    String value = line.substring(index + 1);
                    headerProperties.put(name.trim(), value.trim());
                } else if (responseCode == null) {
                    responseCode = DAVStatus.parse(lineStr);
                }
    
                line.delete(0, line.length());
            }
        } finally {
            SVNDebugLog.flushStream(is);
        }
        return responseCode;
    }

    private void skipRequestBody(Map header) throws IOException {
        InputStream is = createInputStream(header, getInputStream());
        while (is.skip(2048) > 0) {}
    }

    private OutputStream getOutputStream() throws IOException {
        if (myOutputStream == null) {
        	if (mySocket == null) {
        		return null;
        	}
            myOutputStream = new BufferedOutputStream(mySocket.getOutputStream(), 2048);
            myOutputStream = SVNDebugLog.createLogStream(myOutputStream);
        }
        return myOutputStream;
    }

    private InputStream getInputStream() throws IOException {
        if (myInputStream == null) {
        	if (mySocket == null) {
        		return null;
        	}
            myInputStream = new BufferedInputStream(mySocket.getInputStream(), 2048);
        }
        return myInputStream;
    }

    private static InputStream createInputStream(Map readHeader, InputStream is) throws IOException {
        if (readHeader.get("Content-Length") != null) {
            is = new FixedSizeInputStream(is, Long.parseLong(readHeader.get("Content-Length").toString()));
        } else if ("chunked".equals(readHeader.get("Transfer-Encoding"))) {
            is = new ChunkedInputStream(is);
        }
        if ("gzip".equals(readHeader.get("Content-Encoding"))) {
            is = new GZIPInputStream(is);
        }
        return SVNDebugLog.createLogStream(is);
    }


    private static String composeAuthResponce(SVNAuthentication credentials, Map credentialsChallenge) throws SVNAuthenticationException {
        String method = (String) credentialsChallenge.get("");
        StringBuffer result = new StringBuffer();
        if ("basic".equalsIgnoreCase(method) && credentials instanceof SVNPasswordAuthentication) {
            SVNPasswordAuthentication auth = (SVNPasswordAuthentication) credentials;
            String authStr = auth.getUserName() + ":" + auth.getPassword();
            authStr = SVNBase64.byteArrayToBase64(authStr.getBytes());
            result.append("Basic ");
            result.append(authStr);
        } else if ("digest".equalsIgnoreCase(method) && credentials instanceof SVNPasswordAuthentication) {
            SVNPasswordAuthentication auth = (SVNPasswordAuthentication) credentials;
            result.append("Digest ");
            HttpDigestAuth digestAuth = new HttpDigestAuth(auth, credentialsChallenge);
            String response = digestAuth.authenticate();
            result.append(response);
        } else {
            throw new SVNAuthenticationException("Authentication method '" + method + "' is not supported");
        }

        return result.toString();
    }

    private static synchronized SAXParserFactory getSAXParserFactory() throws FactoryConfigurationError {
        if (ourSAXParserFactory == null) {
            ourSAXParserFactory = SAXParserFactory.newInstance();
            try {
                ourSAXParserFactory.setFeature("http://xml.org/sax/features/namespaces", true);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            try {
                ourSAXParserFactory.setFeature("http://xml.org/sax/features/validation", false);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            try {
                ourSAXParserFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (SAXNotRecognizedException e) {
            } catch (SAXNotSupportedException e) {
            } catch (ParserConfigurationException e) {
            }
            ourSAXParserFactory.setNamespaceAware(true);
            ourSAXParserFactory.setValidating(false);
        }
        return ourSAXParserFactory;
    }

    private void assertOk(String method, String url, DAVStatus status, int[] codes) throws SVNException {
        int code = status.getResponseCode();
        if (url == null || "".equals(url)) {
            url = "/";
        }
        if (codes == null) {
            // check that all are > 200.
            if (code >= 200 && code < 300) {
                return;
            }
            readError(url, status);
            String message = "svn: " + method + " request failed on '" + url + "'";
            if (status.getErrorText() != null && !"".equals(status.getErrorText())) {
                message += "\nsvn: " + status.getErrorText();
            }
            SVNErrorManager.error(message);
        }
        for (int i = 0; i < codes.length; i++) {
            if (code == codes[i]) {
                return;
            }
        }
        readError(url, status);
        String message = "svn: " + method + " request failed on '" + url + "'";
        if (status.getErrorText() != null && !"".equals(status.getErrorText())) {
            message += "\nsvn: " + status.getErrorText();
        }
        SVNErrorManager.error(message);
    }

    private static Map initHeader(int depth, String label, Map map) {
        map = map == null ? new HashMap() : map;
        if (label != null && !map.containsKey("Label")) {
            map.put("Label", label);
        }
        if (!map.containsKey("Depth")) {
            if (depth == 1 || depth == 0) {
                map.put("Depth", Integer.toString(depth));
            } else {
                map.put("Depth", "infinity");
            }
        }
        return map;
    }

    private void acknowledgeSSLContext(boolean accepted) throws SVNException {
        if (mySVNRepositoryLocation == null || !"https".equalsIgnoreCase(mySVNRepositoryLocation.getProtocol())) {
            return;
        }
        if (myAuthManager != null) {
            ISVNSSLManager sslManager = myAuthManager.getSSLManager(mySVNRepositoryLocation);
            if (sslManager != null) {
                sslManager.acknowledgeSSLContext(accepted, null);
            }
        }
    }

    private void finishResponse(Map readHeader) {
        if (myOutputStream != null) {
            try {
                myOutputStream.flush();
            } catch (IOException ex) {
                new SVNException(ex);
            }
        }
        if ("close".equals(readHeader.get("Connection")) ||
                "close".equals(readHeader.get("Proxy-Connection"))) {
            close();
        }
    }

    private String getProxyAuthString(String username, String password) {
        if (username != null && password != null) {
            String auth = username + ":" + password;
            return "Basic " + SVNBase64.byteArrayToBase64(auth.getBytes());
        }
        return null;
    }

    public SVNAuthentication getLastValidCredentials() {
        return myLastValidAuth;
    }
}
