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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationProvider;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationStorage;
import org.tmatesoft.svn.core.auth.ISVNProxyManager;
import org.tmatesoft.svn.core.auth.ISVNSSLManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.auth.SVNSSHAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DefaultSVNAuthenticationManager implements ISVNAuthenticationManager {

    private boolean myIsStoreAuth;
    private ISVNAuthenticationProvider[] myProviders;
    private File myConfigDirectory;
    
    private SVNAuthentication myPreviousAuthentication;
    private String myPreviousErrorMessage;
    private SVNConfigFile myServersFile;
    private ISVNAuthenticationStorage myRuntimeAuthStorage;
    private int myLastProviderIndex;
    private SVNConfigFile myConfigFile;

    public DefaultSVNAuthenticationManager(File configDirectory, boolean storeAuth, String userName, String password) {
        password = password == null ? "" : password;

        myIsStoreAuth = storeAuth;
        myConfigDirectory = configDirectory;
        if (myConfigDirectory == null) {
            myConfigDirectory = SVNWCUtil.getDefaultConfigurationDirectory();
        }   
        
        myProviders = new ISVNAuthenticationProvider[4];
        myProviders[0] = new DumbAuthenticationProvider(userName, password, myIsStoreAuth);
        myProviders[1] = new CacheAuthenticationProvider();
        myProviders[2] = new PersistentAuthenticationProvider(new File(myConfigDirectory, "auth"));
    }

    public void setAuthenticationProvider(ISVNAuthenticationProvider provider) {
        // add provider to list
        myProviders[3] = provider; 
    }

    public ISVNProxyManager getProxyManager(SVNURL url) throws SVNException {
        String host = url.getHost();
        
        Map properties = getHostProperties(host);
        String proxyHost = (String) properties.get("http-proxy-host");
        if (proxyHost == null || "".equals(proxyHost.trim())) {
            return null;
        }
        String proxyExceptions = (String) properties.get("http-proxy-exceptions");
        if (proxyExceptions != null) {
          for(StringTokenizer exceptions = new StringTokenizer(proxyExceptions, ","); exceptions.hasMoreTokens();) {
              String exception = exceptions.nextToken().trim();
              if (DefaultSVNOptions.matches(exception, host)) {
                  return null;
              }
          }
        }
        String proxyPort = (String) properties.get("http-proxy-port");
        String proxyUser = (String) properties.get("http-proxy-username");
        String proxyPassword = (String) properties.get("http-proxy-password");
        return new SimpleProxyManager(proxyHost, proxyPort, proxyUser, proxyPassword);
    }

    public ISVNSSLManager getSSLManager(SVNURL url) throws SVNException {
        String host = url.getHost();
        
        Map properties = getHostProperties(host);
        boolean trustAll = !"no".equalsIgnoreCase((String) properties.get("ssl-trust-default-ca")); // jdk keystore
        String sslAuthorityFiles = (String) properties.get("ssl-authority-files"); // "pem" files
        String sslClientCert = (String) properties.get("ssl-client-cert-file"); // PKCS#12
        String sslClientCertPassword = (String) properties.get("ssl-client-cert-password");
        
        File clientCertFile = sslClientCert != null ? new File(sslClientCert) : null;
        Collection trustStorages = new ArrayList();
        if (sslAuthorityFiles != null) {
            for(StringTokenizer files = new StringTokenizer(sslAuthorityFiles, ","); files.hasMoreTokens();) {
                String fileName = files.nextToken();
                if (fileName != null && !"".equals(fileName.trim())) {
                    trustStorages.add(new File(fileName));
                }
            }
        }
        File[] serverCertFiles = (File[]) trustStorages.toArray(new File[trustStorages.size()]);
        File authDir = new File(myConfigDirectory, "auth/svn.ssl.server");
        return new DefaultSVNSSLManager(authDir, url, serverCertFiles, trustAll, clientCertFile, sslClientCertPassword, this);
    }

    private Map getHostProperties(String host) {
        Map globalProps = getServersFile().getProperties("global");
        String groupName = getGroupName(getServersFile().getProperties("groups"), host);
        if (groupName != null) {
            Map hostProps = getServersFile().getProperties(groupName);
            globalProps.putAll(hostProps);
        }
        return globalProps;
    }

    public SVNAuthentication getFirstAuthentication(String kind, String realm, SVNURL url) throws SVNException {
        myPreviousAuthentication = null;
        myPreviousErrorMessage = null;
        myLastProviderIndex = 0;
        // iterate over providers and ask for auth till it is found.
        for (int i = 0; i < myProviders.length; i++) {
            if (myProviders[i] == null) {
                continue;
            }
            SVNAuthentication auth = myProviders[i].requestClientAuthentication(kind, null, realm, null, null, myIsStoreAuth);
            if (auth != null) {
                myPreviousAuthentication = auth;
                myLastProviderIndex = i;
                return auth;
            }
            if (i == 3) {
                throw new SVNCancelException("svn: Authentication cancelled");
            }
        }
        throw new SVNAuthenticationException("svn: Authentication required for '" + realm + "'");
    }

    public SVNAuthentication getNextAuthentication(String kind, String realm, SVNURL url) throws SVNException {
        int index = Math.min(myLastProviderIndex + 1, 3);
        for(int i = index; i < myProviders.length; i++) {
            if (myProviders[i] == null) {
                continue;
            }
            SVNAuthentication auth = myProviders[i].requestClientAuthentication(kind, null, realm, myPreviousErrorMessage, myPreviousAuthentication, myIsStoreAuth);
            if (auth != null) {
                myPreviousAuthentication = auth;
                myLastProviderIndex = i;
                return auth;
            }
            if (i == 3) {
                throw new SVNCancelException("svn: Authentication cancelled");
            }
        }
        throw new SVNAuthenticationException("svn: Authentication required for '" + realm + "'");
    }

    public void acknowledgeAuthentication(boolean accepted, String kind, String realm, String errorMessage, SVNAuthentication authentication) {
        if (!accepted) {
            myPreviousErrorMessage = errorMessage;
            myPreviousAuthentication = authentication;
            return;
        }
        if (myIsStoreAuth && authentication.isStorageAllowed() && myProviders[2] != null) {
            ((PersistentAuthenticationProvider) myProviders[2]).saveAuthentication(authentication, kind, realm);
        }
        ((CacheAuthenticationProvider) myProviders[1]).saveAuthentication(authentication, realm);
    }
    
    protected SVNConfigFile getServersFile() {
        if (myServersFile == null) {
            SVNConfigFile.createDefaultConfiguration(myConfigDirectory);
            myServersFile = new SVNConfigFile(new File(myConfigDirectory, "servers"));
        }
        return myServersFile;
    }

    protected SVNConfigFile getConfigFile() {
        if (myConfigFile == null) {
            SVNConfigFile.createDefaultConfiguration(myConfigDirectory);
            myConfigFile = new SVNConfigFile(new File(myConfigDirectory, "config"));
        }
        return myConfigFile;
    }

    public void setRuntimeStorage(ISVNAuthenticationStorage storage) {
        myRuntimeAuthStorage = storage;
    }
    
    protected ISVNAuthenticationStorage getRuntimeAuthStorage() {
        if (myRuntimeAuthStorage == null) {
            myRuntimeAuthStorage = new ISVNAuthenticationStorage() {
                private Map myData = new HashMap(); 

                public void putData(String kind, String realm, Object data) {
                    myData.put(kind + "$" + realm, data);
                }
                public Object getData(String kind, String realm) {
                    return myData.get(kind + "$" + realm);
                }
            };
        }
        return myRuntimeAuthStorage;
    }
    
    protected boolean isAuthStorageEnabled() {
        return myIsStoreAuth;
    }
    
    protected ISVNAuthenticationProvider getAuthenticationProvider() {
        return myProviders[3];
    }
    
    protected int getDefaultSSHPortNumber() {
        Map tunnels = getConfigFile().getProperties("tunnels");
        if (tunnels == null || !tunnels.containsKey("ssh")) {
            return -1;
        }
        String sshProgram = (String) tunnels.get("ssh");
        if (sshProgram == null) {
            return -1;
        }
        String port = getOptionValue(sshProgram, sshProgram.toLowerCase().trim().startsWith("plink") ? "-p" : "-P");
        port = port == null ? System.getProperty("javasvn.ssh2.port") : port;
        if (port != null) {
            return Integer.parseInt(port);
        }
        return -1;
    }
    
    protected SVNSSHAuthentication getDefaultSSHAuthentication() {
        Map tunnels = getConfigFile().getProperties("tunnels");
        if (tunnels == null || !tunnels.containsKey("ssh")) {
            return null;
        }
        
        String sshProgram = (String) tunnels.get("ssh");
        String userName = getOptionValue(sshProgram, "-l");
        String password = getOptionValue(sshProgram, "-pw");
        String keyFile = getOptionValue(sshProgram, "-i");
        String port = getOptionValue(sshProgram, sshProgram != null && sshProgram.toLowerCase().trim().startsWith("plink") ? "-P" : "-p");
        String passphrase = null; 
        // fallback to system properties.
        userName = userName == null ? System.getProperty("javasvn.ssh2.username") : userName;
        keyFile = keyFile == null ? System.getProperty("javasvn.ssh2.key") : keyFile;
        passphrase = passphrase == null ? System.getProperty("javasvn.ssh2.passphrase") : passphrase;
        password = password == null ? System.getProperty("javasvn.ssh2.password") : password;
        port = port == null ? System.getProperty("javasvn.ssh2.port") : port;

        if (userName == null) {
            userName = System.getProperty("user.name");
        }
        int portNumber = -1;
        if (port != null) {
            portNumber = Integer.parseInt(port);
        }
        
        if (userName != null && password != null) {
            return new SVNSSHAuthentication(userName, password, portNumber, isAuthStorageEnabled());
        } else if (userName != null && keyFile != null) {
            return new SVNSSHAuthentication(userName, new File(keyFile), passphrase, portNumber, isAuthStorageEnabled());
        }
        return null;
    }
    
    private static String getOptionValue(String commandLine, String optionName) {
        if (commandLine == null || optionName == null) {
            return null;
        }
        for(StringTokenizer options = new StringTokenizer(commandLine, " \r\n\t"); options.hasMoreTokens();) {
            String option = options.nextToken().trim();
            if (optionName.equals(option) && options.hasMoreTokens()) {
                return options.nextToken();
            } else if (option.startsWith(optionName)) {
                return option.substring(optionName.length());
            }
        }
        return null;
    }
    
    
    private class DumbAuthenticationProvider implements ISVNAuthenticationProvider {
        
        private String myUserName;
        private String myPassword;
        private boolean myIsStore;
        
        public DumbAuthenticationProvider(String userName, String password, boolean store) {
            myUserName = userName;
            myPassword = password;
            myIsStore = store;
        }
        public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, String errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
            if (previousAuth == null) {
                if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    SVNSSHAuthentication sshAuth = getDefaultSSHAuthentication();
                    if (myUserName == null || "".equals(myUserName.trim())) {
                        return sshAuth;
                    }
                    return new SVNSSHAuthentication(myUserName, myPassword, sshAuth != null ? sshAuth.getPortNumber() : -1, myIsStore);
                } else if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                    if (myUserName == null || "".equals(myUserName.trim())) {
                        return null;
                    }
                    return new SVNPasswordAuthentication(myUserName, myPassword, myIsStore);
                }
            }
            return null;
        }
        public int acceptServerAuthentication(SVNURL url, String r, Object serverAuth, boolean resultMayBeStored) {
            return ACCEPTED;
        }
    }

    private static String getGroupName(Map groups, String host) {
        for (Iterator names = groups.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            String pattern = (String) groups.get(name);
            if (DefaultSVNOptions.matches(pattern, host)) {
                return name;
            }
        }
        return null;
    }

    private class CacheAuthenticationProvider implements ISVNAuthenticationProvider {        

        public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, String errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
            // get next after prev for select kind and realm.
            if (previousAuth != null) {
                return null;
            }
            return (SVNAuthentication) getRuntimeAuthStorage().getData(kind, realm);
        }
        
        public void saveAuthentication(SVNAuthentication auth, String realm) {
            if (auth == null || realm == null) {
                return;
            }
            String kind = auth instanceof SVNSSHAuthentication ? ISVNAuthenticationManager.SSH : ISVNAuthenticationManager.PASSWORD;
            getRuntimeAuthStorage().putData(kind, realm, auth);
        }
        
        public int acceptServerAuthentication(SVNURL url, String r, Object serverAuth, boolean resultMayBeStored) {
            return ACCEPTED;
        }
    }
    
    private class PersistentAuthenticationProvider implements ISVNAuthenticationProvider {
        
        private File myDirectory;

        public PersistentAuthenticationProvider(File directory) {
            myDirectory = directory;
        }

        public SVNAuthentication requestClientAuthentication(String kind, SVNURL url, String realm, String errorMessage, SVNAuthentication previousAuth, boolean authMayBeStored) {
            File dir = new File(myDirectory, kind);
            if (!dir.isDirectory()) {
                return null;
            }
            String fileName = SVNFileUtil.computeChecksum(realm);
            File authFile = new File(dir, fileName);
            if (authFile.exists()) {
                SVNProperties props = new SVNProperties(authFile, "");
                try {
                    String storedRealm = props.getPropertyValue("svn:realmstring");
                    if (storedRealm == null || !storedRealm.equals(realm)) {
                        return null;
                    }
                    String userName = props.getPropertyValue("username");
                    if (userName == null || "".equals(userName.trim())) {
                        return null;
                    }
                    if ("wincrypt".equals(props.getPropertyValue("passtype"))) {
                        return null;
                    }
                    String password = props.getPropertyValue("password");
                    String path = props.getPropertyValue("key");
                    String passphrase = props.getPropertyValue("passphrase");
                    if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                        return new SVNPasswordAuthentication(userName, password, authMayBeStored);
                    } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
                        // get port from config file or system property?
                        int portNumber = getDefaultSSHPortNumber();
                        if (path != null) {
                            return new SVNSSHAuthentication(userName, new File(path), passphrase, portNumber, authMayBeStored);
                        } else if (password != null) {
                            return new SVNSSHAuthentication(userName, password, portNumber, authMayBeStored);
                        }                    
                    }
                } catch (SVNException e) {
                    //
                }
            }
            return null;
        }
        
        public void saveAuthentication(SVNAuthentication auth, String kind, String realm) {
            File dir = new File(myDirectory, kind);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            if (!dir.isDirectory()) {
                return;
            }
            // get file name for auth and store password.
            String fileName = SVNFileUtil.computeChecksum(realm);
            File authFile = new File(dir, fileName);

            SVNProperties props = new SVNProperties(authFile, "");
            props.delete();
            
            try {
                props.setPropertyValue("svn:realmstring", realm);
                props.setPropertyValue("username", auth.getUserName());
                if (ISVNAuthenticationManager.PASSWORD.equals(kind)) {
                    SVNPasswordAuthentication passwordAuth = (SVNPasswordAuthentication) auth;
                    props.setPropertyValue("password", passwordAuth.getPassword());
                } else if (ISVNAuthenticationManager.SSH.equals(kind)) {
                    SVNSSHAuthentication sshAuth = (SVNSSHAuthentication) auth;
                    props.setPropertyValue("password", sshAuth.getPassword());
                    if (sshAuth.getPrivateKeyFile() != null) { 
                        String path = SVNPathUtil.validateFilePath(sshAuth.getPrivateKeyFile().getAbsolutePath());
                        props.setPropertyValue("passphrase", sshAuth.getPassphrase());
                        props.setPropertyValue("key", path);
                    }
                }
                SVNFileUtil.setReadonly(props.getFile(), false);
            } catch (SVNException e) {
                props.getFile().delete();
            }
        }
        

        public int acceptServerAuthentication(SVNURL url, String r, Object serverAuth, boolean resultMayBeStored) {
            return ACCEPTED;
        }
        
    }
    
    private static final class SimpleProxyManager implements ISVNProxyManager {

        private String myProxyHost;
        private String myProxyPort;
        private String myProxyUser;
        private String myProxyPassword;

        public SimpleProxyManager(String host, String port, String user, String password) {
            myProxyHost = host;
            myProxyPort = port == null ? "80" : port;
            myProxyUser = user;
            myProxyPassword = password;
        }
        
        public String getProxyHost() {
            return myProxyHost;
        }

        public int getProxyPort() {
            try {
                return Integer.parseInt(myProxyPort);
            } catch (NumberFormatException nfe) {
                //
            }
            return 80;
        }

        public String getProxyUserName() {
            return myProxyUser;
        }

        public String getProxyPassword() {
            return myProxyPassword;
        }

        public void acknowledgeProxyContext(boolean accepted, String errorMessage) {
        }
    }
}
