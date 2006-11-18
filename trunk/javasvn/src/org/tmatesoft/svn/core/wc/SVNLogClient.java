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
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.TreeSet;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNAnnotationGenerator;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;

/**
 * The <b>SVNLogClient</b> class is intended for such purposes as getting
 * revisions history, browsing repository entries and annotating file contents.
 * 
 * <p>
 * Here's a list of the <b>SVNLogClient</b>'s methods 
 * matched against corresponing commands of the <b>SVN</b> command line 
 * client:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>JavaSVN</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doLog()</td><td>'svn log'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doList()</td><td>'svn list'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doAnnotate()</td><td>'svn blame'</td>
 * </tr>
 * </table>
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNLogClient extends SVNBasicClient {

    public SVNLogClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    protected SVNLogClient(ISVNRepositoryFactory repositoryFactory, ISVNOptions options) {
        super(repositoryFactory, options);
    }
    
    /**
     * Obtains annotation information for each file text line from a repository
     * (using a Working Copy path to get a corresponding URL) and passes it to a 
     * provided annotation handler. 
     * 
     * <p>
     * If <code>startRevision</code> is invalid (for example, 
     * <code>startRevision = </code>{@link SVNRevision#UNDEFINED UNDEFINED}) then
     * it's set to revision 1.
     * 
     * @param  path           a WC file item to be annotated
     * @param  pegRevision    a revision in which <code>path</code> is first looked up
     *                        in the repository
     * @param  startRevision  a revision for an operation to start from
     * @param  endRevision    a revision for an operation to stop at
     * @param  handler        a caller's handler to process annotation information
     * @throws SVNException   if <code>startRevision > endRevision</code>
     * @see                   #doAnnotate(SVNURL, SVNRevision, SVNRevision, SVNRevision, ISVNAnnotateHandler)
     */
    public void doAnnotate(File path, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler) throws SVNException {
        if (startRevision == null || !startRevision.isValid()) {
            startRevision = SVNRevision.create(1);
        }
        SVNRepository repos = createRepository(null, path, pegRevision, endRevision);
        long endRev = getRevisionNumber(endRevision, repos, path);
        long startRev = getRevisionNumber(startRevision, repos, path);
        if (endRev < startRev) {
            SVNErrorManager.error("svn: Start revision must precede end revision (" + startRev + ":" + endRev + ")");
        }
        File tmpFile = new File(path.getParentFile(), ".svn/tmp/text-base");
        if (!tmpFile.exists()) {
            tmpFile = new File(System.getProperty("user.home"), ".javasvn");
            tmpFile.mkdirs();
        }
        doAnnotate(path.getAbsolutePath(), startRev, tmpFile, repos, endRev, handler);
    }
    
    /**
     * Obtains annotation information for each file text line from a repository
     * and passes it to a provided annotation handler. 
     * 
     * <p>
     * If <code>startRevision</code> is invalid (for example, 
     * <code>startRevision = </code>{@link SVNRevision#UNDEFINED UNDEFINED}) then
     * it's set to revision 1.
     * 
     * 
     * @param  url            a URL of a text file that is to be annotated 
     * @param  pegRevision    a revision in which <code>path</code> is first looked up
     *                        in the repository
     * @param  startRevision  a revision for an operation to start from
     * @param  endRevision    a revision for an operation to stop at
     * @param  handler        a caller's handler to process annotation information
     * @throws SVNException   if <code>startRevision > endRevision</code>
     * @see                   #doAnnotate(File, SVNRevision, SVNRevision, SVNRevision, ISVNAnnotateHandler)
     */
    public void doAnnotate(SVNURL url, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler) throws SVNException {
        if (startRevision == null || !startRevision.isValid()) {
            startRevision = SVNRevision.create(1);
        }
        SVNRepository repos = createRepository(url, null, pegRevision, endRevision);
        long endRev = getRevisionNumber(endRevision, repos, null);
        long startRev = getRevisionNumber(startRevision, repos, null);
        if (endRev < startRev) {
            SVNErrorManager.error("svn: Start revision must precede end revision (" + startRev + ":" + endRev + ")");
        }
        File tmpFile = new File(System.getProperty("user.home"), ".javasvn");
        if (!tmpFile.exists()) {
            tmpFile.mkdirs();
        }
        doAnnotate(repos.getLocation().toString(), startRev, tmpFile, repos, endRev, handler);
    }

    private void doAnnotate(String path, long startRev, File tmpFile, SVNRepository repos, long endRev, ISVNAnnotateHandler handler) throws SVNException {
        SVNAnnotationGenerator generator = new SVNAnnotationGenerator(path, startRev, tmpFile, this);
        try {
            repos.getFileRevisions("", startRev, endRev, generator);
            generator.reportAnnotations(handler, null);
        } finally {
            generator.dispose();
            SVNFileUtil.deleteAll(tmpFile, false, null);
        }
    }
    
    /**
     * Gets commit log messages with other revision specific 
     * information from a repository (using Working Copy paths to get 
     * corresponding URLs) and passes them to a log entry handler for
     * processing. Useful for observing the history of affected paths,
     * author, date and log comments information per revision.
     * 
     * <p>
     * If <code>paths</code> is not empty then the result will be restricted
     * to only those revisions from the specified range [<code>startRevision</code>, <code>endRevision</code>], 
     * where <code>paths</code> were changed in the repository. To cover the
     * entire range set <code>paths</code> just to an empty array:
     * <pre class="javacode">
     *     logClient.doLog(<span class="javakeyword">new</span> File[]{<span class="javastring">""</span>},..);</pre><br />
     * <p>
     * If <code>startRevision</code> is valid but <code>endRevision</code> is
     * not (for example, <code>endRevision = </code>{@link SVNRevision#UNDEFINED UNDEFINED})
     * then <code>endRevision</code> is equated to <code>startRevision</code>.
     * 
     * <p>
     * If <code>startRevision</code> is invalid (for example, {@link SVNRevision#UNDEFINED UNDEFINED}) 
     * then it's equated to {@link SVNRevision#BASE BASE}. In this case if <code>endRevision</code> is
     * also invalid, then <code>endRevision</code> is set to revision 0.
     * 
     * @param  paths           an array of Working Copy paths,
     *                         should not be <span class="javakeyword">null</span>
     * @param  startRevision   a revision for an operation to start from (including
     *                         this revision)    
     * @param  endRevision     a revision for an operation to stop at (including
     *                         this revision)
     * @param  stopOnCopy      <span class="javakeyword">true</span> not to cross
     *                         copies while traversing history, otherwise copies history
     *                         will be also included into processing
     * @param  reportPaths     <span class="javakeyword">true</span> to report
     *                         of all changed paths for every revision being processed 
     *                         (those paths will be available by calling 
     *                         {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  limit           a maximum number of log entries to be processed 
     * @param  handler         a caller's log entry handler
     * @throws SVNException    if one of the following is true:
     *                         <ul>
     *                         <li>a path is not under version control
     *                         <li>can not obtain a URL of a WC path - there's no such
     *                         entry in the Working Copy
     *                         <li><code>paths</code> contain entries that belong to
     *                         different repositories
     *                         </ul>
     * @see                    #doLog(SVNURL, String[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, long, ISVNLogEntryHandler)                        
     */
    public void doLog(File[] paths, SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, boolean reportPaths, long limit, ISVNLogEntryHandler handler) throws SVNException {
        if (paths == null || paths.length == 0) {
            return;
        }
        if (startRevision.isValid() && !endRevision.isValid()) {
            endRevision = startRevision;
        } else if (!startRevision.isValid()) {
            startRevision = SVNRevision.BASE;
            if (!endRevision.isValid()) {
                endRevision = SVNRevision.create(0);
            }
        }
        String[] urls = new String[paths.length];
        for (int i = 0; i < paths.length; i++) {
            File path = paths[i];
            SVNWCAccess wcAccess = createWCAccess(path);
            SVNEntry entry = wcAccess.getTargetEntry();
            if (entry == null) {
                SVNErrorManager.error("svn: '" + path + "' is not under version control");
                return;
            }
            if (entry.getURL() == null) {
                SVNErrorManager.error("svn: '" + path + "' has no URL");
            }
            urls[i] = entry.getURL();
        }
        if (urls.length == 0) {
            return;
        }
        Collection targets = new TreeSet();
        String baseURL = SVNPathUtil.condenceURLs(urls, targets, true);
        if (baseURL == null || "".equals(baseURL)) {
            SVNErrorManager.error("svn: Entries belong to different repositories");
        }
        if (targets.isEmpty()) {
            targets.add("");
        }
        SVNRepository repos = createRepository(baseURL);
        String[] targetPaths = (String[]) targets.toArray(new String[targets.size()]);
        for (int i = 0; i < targetPaths.length; i++) {
            targetPaths[i] = SVNEncodingUtil.uriDecode(targetPaths[i]);
        }
        if (startRevision.isLocal() || endRevision.isLocal()) {
            for (int i = 0; i < paths.length; i++) {
                long startRev = getRevisionNumber(startRevision, repos, paths[i]);
                long endRev = getRevisionNumber(endRevision, repos, paths[i]);
                repos.log(targetPaths, startRev, endRev, reportPaths, stopOnCopy, limit, handler);
            }
        } else {
            long startRev = getRevisionNumber(startRevision, repos, null);
            long endRev = getRevisionNumber(endRevision, repos, null);
            repos.log(targetPaths, startRev, endRev, reportPaths, stopOnCopy, limit, handler);
        }
    }
    
    /**
     * Gets commit log messages with other revision specific 
     * information from a repository and passes them to a log entry 
     * handler for processing. Useful for observing the history of 
     * affected paths, author, date and log comments information per revision.
     * 
     * <p>
     * If <code>paths</code> is <span class="javakeyword">null</span> or empty
     * then <code>url</code> is the target path that is used to restrict the result
     * to only those revisions from the specified range [<code>startRevision</code>, <code>endRevision</code>], 
     * where <code>url</code> was changed in the repository. Otherwise if <code>paths</code> is
     * not empty then <code>url</code> is the root for all those paths (that are
     * used for restricting the result).
     * 
     * <p>
     * If <code>startRevision</code> is valid but <code>endRevision</code> is
     * not (for example, <code>endRevision = </code>{@link SVNRevision#UNDEFINED UNDEFINED})
     * then <code>endRevision</code> is equated to <code>startRevision</code>.
     * 
     * <p>
     * If <code>startRevision</code> is invalid (for example, {@link SVNRevision#UNDEFINED UNDEFINED}) 
     * then it's equated to {@link SVNRevision#HEAD HEAD}. In this case if <code>endRevision</code> is
     * also invalid, then <code>endRevision</code> is set to revision 0.
     * 
     * 
     * @param  url             a target URL            
     * @param  paths           an array of paths relative to the target 
     *                         <code>url</code>
     * @param  pegRevision     a revision in which <code>url</code> is first looked up
     * @param  startRevision   a revision for an operation to start from (including
     *                         this revision)    
     * @param  endRevision     a revision for an operation to stop at (including
     *                         this revision)
     * @param  stopOnCopy      <span class="javakeyword">true</span> not to cross
     *                         copies while traversing history, otherwise copies history
     *                         will be also included into processing
     * @param  reportPaths     <span class="javakeyword">true</span> to report
     *                         of all changed paths for every revision being processed 
     *                         (those paths will be available by calling 
     *                         {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  limit           a maximum number of log entries to be processed 
     * @param  handler         a caller's log entry handler
     * @throws SVNException
     * @see                    #doLog(File[], SVNRevision, SVNRevision, boolean, boolean, long, ISVNLogEntryHandler)
     */
    public void doLog(SVNURL url, String[] paths, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, boolean reportPaths, long limit, ISVNLogEntryHandler handler) throws SVNException {
        if (startRevision.isValid() && !endRevision.isValid()) {
            endRevision = startRevision;
        } else if (!startRevision.isValid()) {
            startRevision = SVNRevision.HEAD;
            if (!endRevision.isValid()) {
                endRevision = SVNRevision.create(0);
            }
        }
        paths = paths == null || paths.length == 0 ? new String[] {""} : paths;
        long targetRevNumber = startRevision.getNumber();
        if (endRevision.getNumber() > 0) {
            targetRevNumber = Math.max(targetRevNumber, startRevision.getNumber());
        }
        SVNRepository repos = targetRevNumber > 0 && pegRevision.isValid() && !pegRevision.isLocal() ?
                createRepository(url, null, pegRevision, SVNRevision.create(targetRevNumber)) :
                    createRepository(url);
        long startRev = getRevisionNumber(startRevision, repos, null);
        long endRev = getRevisionNumber(endRevision, repos, null);
        repos.log(paths, startRev, endRev, reportPaths, stopOnCopy, limit, handler);
    }
    
    /**
     * Browses directory entries from a repository (using Working 
     * Copy paths to get corresponding URLs) and uses the provided dir 
     * entry handler to process them.
     * 
     * <p>
     * On every entry that this method stops it gets some useful entry 
     * information which is packed into an {@link org.tmatesoft.svn.core.SVNDirEntry}
     * object and passed to the <code>handler</code>'s 
     * {@link org.tmatesoft.svn.core.ISVNDirEntryHandler#handleDirEntry(SVNDirEntry) handleDirEntry()} method.
     *  
     * @param  path           a WC item to get its repository location            
     * @param  pegRevision    a revision in which the item's URL is first looked up
     * @param  revision       a target revision
     * @param  recursive      <span class="javakeyword">true</span> to
     *                        descend recursively (relevant for directories)    
     * @param  handler        a caller's directory entry handler (to process
     *                        info on an entry)
     * @throws SVNException 
     * @see                   #doList(SVNURL, SVNRevision, SVNRevision, boolean, ISVNDirEntryHandler)  
     */
    public void doList(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.BASE;
        }
        SVNRepository repos = createRepository(null, path, pegRevision, revision);
        long rev = getRevisionNumber(revision, repos, path);
        doList(repos, rev, handler, recursive);
    }
    
    /**
     * Browses directory entries from a repository and uses the provided 
     * dir entry handler to process them. This method is 
     * especially useful when having no Working Copy. 
     * 
     * <p>
     * On every entry that this method stops it gets some useful entry 
     * information which is packed into an {@link org.tmatesoft.svn.core.SVNDirEntry}
     * object and passed to the <code>handler</code>'s 
     * {@link org.tmatesoft.svn.core.ISVNDirEntryHandler#handleDirEntry(SVNDirEntry) handleDirEntry()} method.
     * 
     * @param  url            a repository location to be "listed"
     * @param  pegRevision    a revision in which the item's URL is first looked up
     * @param  revision       a target revision
     * @param  recursive      <span class="javakeyword">true</span> to
     *                        descend recursively (relevant for directories)    
     * @param  handler        a caller's directory entry handler (to process
     *                        info on an entry)
     * @throws SVNException
     * @see                   #doList(File, SVNRevision, SVNRevision, boolean, ISVNDirEntryHandler)   
     */
    public void doList(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        if (revision == null || !revision.isValid()) {
            revision = SVNRevision.HEAD;
        }
        SVNRepository repos = createRepository(url, null, pegRevision, revision);
        long rev = getRevisionNumber(revision, repos, null);
        doList(repos, rev, handler, recursive);
    }

    private void doList(SVNRepository repos, long rev, ISVNDirEntryHandler handler, boolean recursive) throws SVNException {
        if (repos.checkPath("", rev) == SVNNodeKind.FILE) {
            SVNDirEntry entry = repos.info("", rev);
            String name = SVNPathUtil.tail(repos.getLocation().getPath());
            entry.setPath(name);
            handler.handleDirEntry(entry);
        } else {
            list(repos, "", rev, recursive, handler);
        }
    }

    private static void list(SVNRepository repository, String path, long rev, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        Collection entries = new TreeSet();
        entries = repository.getDir(path, rev, null, entries);

        for (Iterator iterator = entries.iterator(); iterator.hasNext();) {
            SVNDirEntry entry = (SVNDirEntry) iterator.next();
            String childPath = SVNPathUtil.append(path, entry.getName());
            entry.setPath(childPath);
            handler.handleDirEntry(entry);
            if (entry.getKind() == SVNNodeKind.DIR && entry.getDate() != null && recursive) {
                list(repository, childPath, rev, recursive, handler);
            }
        }
    }
}