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

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNCommitClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author TMate Software Ltd.
 */
public class DeleteCommand extends SVNCommand {

    public void run(PrintStream out, PrintStream err) throws SVNException {
        if (getCommandLine().hasURLs()) {
            runRemote(out);
        } else {
            runLocally(out, err);
        }
    }

    private void runRemote(PrintStream out) throws SVNException {
        final String commitMessage = (String) getCommandLine().getArgumentValue(SVNArgument.MESSAGE);

        SVNCommitClient client = getClientManager().getCommitClient();
        Collection urls  = new ArrayList(getCommandLine().getURLCount());
        for(int i = 0; i < getCommandLine().getURLCount(); i++) {
            urls.add(getCommandLine().getURL(i));
        }
        String[] urlsArray = (String[]) urls.toArray(new String[urls.size()]);
        SVNURL[] svnUrls = new SVNURL[urlsArray.length];
        for (int i = 0; i < svnUrls.length; i++) {
            svnUrls[i] = SVNURL.parseURIEncoded(urlsArray[i]);
        }
        SVNCommitInfo info = client.doDelete(svnUrls, commitMessage);
        if (info != SVNCommitInfo.NULL) {
            out.println();
            out.println("Committed revision " + info.getNewRevision() + ".");
        }
    }

    private void runLocally(final PrintStream out, PrintStream err) {
        boolean force = getCommandLine().hasArgument(SVNArgument.FORCE);

        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));

        SVNWCClient client = getClientManager().getWCClient();
        boolean error = false;
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            final String absolutePath = getCommandLine().getPathAt(i);
            try {
                client.doDelete(new File(absolutePath), force, false);
            } catch (SVNException e) {
                err.println(e.getMessage());
                error = true;
            }
        }
        if (error) {
            System.exit(1);
        }
    }
}
