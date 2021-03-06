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
package org.tmatesoft.svn.core.auth;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNAuthenticationStorage {
    
    public void putData(String kind, String realm, Object data);
    
    public Object getData(String kind, String realm);

}
