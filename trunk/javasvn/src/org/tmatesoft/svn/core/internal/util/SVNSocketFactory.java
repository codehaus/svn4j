/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.util;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.StringTokenizer;

import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNSSLManager;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * <code>SVNSocketFactory</code> is a utility class that represents a custom
 * socket factory which provides creating either a plain socket or a secure one
 * to encrypt data transmitted over network.
 * 
 * <p>
 * The created socket then used by the inner engine of <b><i>JavaSVN</i></b>
 * library to communicate with a Subversion repository.
 * 
 * @version 1.0
 * @author TMate Software Ltd.
 * 
 */
public class SVNSocketFactory {

    public static Socket createPlainSocket(String host, int port) throws SVNException {
        int attempts = 3;
        while (true) {
            try {
                InetAddress address = createAddres(host);
                return new Socket(address, port);
            } catch (ConnectException timeOut) {
                if (timeOut.getMessage().indexOf("time") >= 0) {
                    attempts--;
                    if (attempts <= 0) {
                        SVNErrorManager.error("svn: Connection timeout");
                    }
                    continue;
                }
                SVNErrorManager.error("svn: Connection failed: '" + timeOut.getMessage() + "'");
            } catch (IOException e) {
                String message = e.getMessage();
                if (e instanceof UnknownHostException) {
                    message = "svn: Unknown host '" + host + "'";
                }
                SVNErrorManager.error(message);
            }
        }
    }

    public static Socket createSSLSocket(ISVNSSLManager manager, String host, int port) throws SVNException {
        int attempts = 3;
        manager = manager == null ? DEFAULT_SSL_MANAGER : manager;
        while (true) {
            try {
                return manager.getSSLContext().getSocketFactory().createSocket(createAddres(host), port);
            } catch (ConnectException timeOut) {
                if (timeOut.getMessage().indexOf("time") >= 0) {
                    attempts--;
                    if (attempts <= 0) {
                        SVNErrorManager.error("svn: Connection timeout");
                    }
                    continue;
                }
                SVNErrorManager.error("svn: Connection failed: '" + timeOut.getMessage() + "'");
            } catch (IOException e) {
                String message = e.getMessage();
                if (e instanceof UnknownHostException) {
                    message = "svn: Unknown host '" + host + "'";
                }
                SVNErrorManager.error(message);
            }
        }
    }

    public static Socket createSSLSocket(ISVNSSLManager manager, String host, int port, Socket socket) throws SVNException {
        int attempts = 3;
        manager = manager == null ? DEFAULT_SSL_MANAGER : manager;
        while (true) {
            try {
                return manager.getSSLContext().getSocketFactory().createSocket(socket, host, port, true);
            } catch (ConnectException timeOut) {
                if (timeOut.getMessage().indexOf("time") >= 0) {
                    attempts--;
                    if (attempts <= 0) {
                        SVNErrorManager.error("svn: Connection timeout");
                    }
                    continue;
                }
                SVNErrorManager.error("svn: Connection failed: '" + timeOut.getMessage() + "'");
            } catch (IOException e) {
                String message = e.getMessage();
                if (e instanceof UnknownHostException) {
                    message = "svn: Unknown host '" + host + "'";
                }
                SVNErrorManager.error(message);
            }
        }
    }

    private static InetAddress createAddres(String hostName) throws UnknownHostException {
        byte[] bytes = new byte[4];
        int index = 0;
        for (StringTokenizer tokens = new StringTokenizer(hostName, "."); tokens
                .hasMoreTokens();) {
            String token = tokens.nextToken();
            try {
                byte b = (byte) Integer.parseInt(token);
                if (index < bytes.length) {
                    bytes[index] = b;
                    index++;
                } else {
                    bytes = null;
                    break;
                }
            } catch (NumberFormatException e) {
                bytes = null;
                break;
            }
        }
        if (bytes != null && index == 4) {
            return InetAddress.getByAddress(hostName, bytes);
        }
        return InetAddress.getByName(hostName);
    }
    
    private static final ISVNSSLManager DEFAULT_SSL_MANAGER = new ISVNSSLManager() {
        public SSLContext getSSLContext() throws IOException {
            SSLContext context = null;
            try {
                context = SSLContext.getInstance("SSL");
                context.init(new KeyManager[] {}, new TrustManager[] {new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                    public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    }
                    public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    }
                    
                }}, null);
            } catch (NoSuchAlgorithmException e) {
                throw new IOException(e.getMessage());                
            } catch (KeyManagementException e) {
                throw new IOException(e.getMessage());                
            }
            return context;
        }
        public void acknowledgeSSLContext(boolean accepted, String errorMessage) {
        }
    };
}
