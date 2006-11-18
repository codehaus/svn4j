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

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.core.wc.SVNRevision;

/**
 * @author TMate Software Ltd.
 */
public class MoveCommand extends SVNCommand {

	public void run(PrintStream out, PrintStream err) throws SVNException {
    	if (getCommandLine().hasPaths() && getCommandLine().hasURLs()) {
    		err.println("only URL->URL or WC->WC copy is supported");
    		return;
    	}
		if (getCommandLine().hasURLs()) {
			runRemote(out, err);
		} else {
			runLocally(out, err);
		}
	}

	private void runRemote(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().getURLCount() != 2) {
            throw new SVNException("Please enter SRC and DST URL");
        }
        String srcURL = getCommandLine().getURL(0);
        SVNRevision srcRevision = SVNRevision.parse((String) getCommandLine().getArgumentValue(SVNArgument.REVISION));
        String dstURL = getCommandLine().getURL(1);

        if (matchTabsInURL(srcURL, err) || matchTabsInURL(dstURL, err)) {
            return;
        }

        String commitMessage = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNCopyClient updater = getClientManager().getCopyClient();
        SVNCommitInfo result = updater.doCopy(SVNURL.parseURIEncoded(srcURL), srcRevision, SVNURL.parseURIDecoded(dstURL), true, commitMessage);
        if (result != SVNCommitInfo.NULL) {
            out.println();
            out.println("Committed revision " + result.getNewRevision() + ".");
        }
	}

	private void runLocally(final PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().getPathCount() != 2) {
            throw new SVNException("Please enter SRC and DST path");
        }

        final String absoluteSrcPath = getCommandLine().getPathAt(0);
        final String absoluteDstPath = getCommandLine().getPathAt(1);
        if (matchTabsInPath(absoluteDstPath, err) || matchTabsInPath(absoluteSrcPath, err)) {
            return;
        }
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNCopyClient updater = getClientManager().getCopyClient();
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);
        updater.doCopy(new File(absoluteSrcPath), SVNRevision.WORKING, new File(absoluteDstPath), force, true);
	}
}
