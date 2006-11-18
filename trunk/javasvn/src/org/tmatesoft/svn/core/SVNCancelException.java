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

package org.tmatesoft.svn.core;

/**
 * An exception class that is used to signal about a denial of
 * the Repository Access Layer to send a user's request to a repository
 * server (over an <code>http(s)</code> connection) because of missing user's 
 * credentials.
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see		SVNException
 *
 */
public class SVNCancelException extends SVNException {

    public SVNCancelException(String message) {
        super(message);
    }
}
