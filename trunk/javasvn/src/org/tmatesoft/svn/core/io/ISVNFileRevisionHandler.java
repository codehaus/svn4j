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

package org.tmatesoft.svn.core.io;

import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;



/**
 * <code>ISVNFileRevisionHandler</code> is an interface of a handler that processes
 * file revisions (provided as <code>SVNFileRevision</code> objects).
 * 
 * <p>
 * The <code>ISVNFileRevisionHandler</code> public interface is used within the 
 * {@link SVNRepository#getFileRevisions(String, long, long, ISVNFileRevisionHandler)}
 * method (that is a user should provide an <code>ISVNFileRevisionHandler</code> 
 * instance when going to call 
 * {@link SVNRepository#getFileRevisions(String, long, long, ISVNFileRevisionHandler)}).
 * 
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	SVNRepository#getFileRevisions(String, long, long, ISVNFileRevisionHandler)
 */
public interface ISVNFileRevisionHandler {
    
    /**
     * Called within the method
     * {@link SVNRepository#getFileRevisions(String, long, long, ISVNFileRevisionHandler)} 
     * method to handle an <code>SVNFileRevision</code> passed.
     * 
     * @param  fileRevision 	a <code>SVNFileRevision</code> object representing file
     * 							revision information
     * @throws SVNException
     * @see 					SVNFileRevision
     */
	public void handleFileRevision(SVNFileRevision fileRevision) throws SVNException;
	
    public OutputStream handleDiffWindow(String token, SVNDiffWindow diffWindow) throws SVNException;
    
    public void handleDiffWindowClosed(String token) throws SVNException;

}

