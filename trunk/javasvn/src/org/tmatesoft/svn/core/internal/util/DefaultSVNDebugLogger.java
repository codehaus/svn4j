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
package org.tmatesoft.svn.core.internal.util;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.tmatesoft.svn.util.SVNDebugLoggerAdapter;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DefaultSVNDebugLogger extends SVNDebugLoggerAdapter {

    private Logger myLogger;

    public void logInfo(String message) {
        getLogger().log(Level.FINE, message);
    }

    public void logError(String message) {
        getLogger().log(Level.SEVERE, message);
    }

    public void logInfo(Throwable th) {
        if (getLogger().isLoggable(Level.FINE)) {
            getLogger().log(Level.FINE, th != null ? th.getMessage() : "", th);
        }
    }

    public void logError(Throwable th) {
        if (getLogger().isLoggable(Level.SEVERE)) {
            getLogger().log(Level.SEVERE, th != null ? th.getMessage() : "", th);
        }
    }

    public void log(String message, byte[] data) {
        if (getLogger().isLoggable(Level.FINER)) {
            getLogger().log(Level.FINER, message + "\n" + new String(data));
        }
    }

    public InputStream createLogStream(InputStream is) {
        if (getLogger().isLoggable(Level.FINEST)) {
            return super.createLogStream(is);
        }
        return is;
    }

    public OutputStream createLogStream(OutputStream os) {
        if (getLogger().isLoggable(Level.FINEST)) {
            return super.createLogStream(os);
        }
        return os;
    }
    
    private Logger getLogger() {
        if (myLogger == null) {
            myLogger = Logger.getLogger("javasvn");
        }
        return myLogger;
    }
}