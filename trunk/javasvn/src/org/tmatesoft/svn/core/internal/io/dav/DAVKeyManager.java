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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import org.tmatesoft.svn.util.SVNDebugLog;

class DAVKeyManager {
    
    private static final String CERTIFICATE_FILE = "javasvn.ssl.client-cert-file";
    private static final String CERTIFICATE_PASSPHRASE = "javasvn.ssl.client-cert-password";
    
    private static KeyManager[] ourKeyManagers;
    private static boolean ourIsInitialized;
    
    public static KeyManager[] getKeyManagers() {
        if (ourIsInitialized) {
            return ourKeyManagers;
        }
        ourIsInitialized = true;
        String certFileName = System.getProperty(CERTIFICATE_FILE);
        if (certFileName == null) {
            return null;
        }
        char[] passphrase = null;
        if (System.getProperty(CERTIFICATE_PASSPHRASE) != null) {
            passphrase = System.getProperty(CERTIFICATE_PASSPHRASE).toCharArray();
        }
        KeyStore keyStore = null;            
        InputStream is = null;
        try {
            keyStore = KeyStore.getInstance("PKCS12");
            if (keyStore != null) {
                is = new FileInputStream(certFileName);
                keyStore.load(is, passphrase);                    
            }
        } catch (Throwable th) {
            SVNDebugLog.logInfo(th);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
        KeyManagerFactory kmf = null;
        if (keyStore != null) {
            try {
                kmf = KeyManagerFactory.getInstance("SunX509");
                if (kmf != null) {
                    kmf.init(keyStore, passphrase);
                    ourKeyManagers = kmf.getKeyManagers();
                }
            } catch (Throwable e) {
                SVNDebugLog.logInfo(e);
            } 
        }
        return ourKeyManagers; 
    }

}
