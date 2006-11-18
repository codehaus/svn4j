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
package org.tmatesoft.svn.examples.wc;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNEventAction;

/*
 * This class is an implementation of ISVNEventHandler intended to reflect the 
 * status of an update operation. Think of an SVN*Client's operation (for example, 
 * updating a working copy with invoking SVNUpdateClient.doUpdate(..)) as of a 
 * number of actions it performs, - for example, for updating, a current action can 
 * be deleting a directory or updating a file. And there can be several of them. So, 
 * the SVN*Client notifies an ISVNEventHandler provided by an implementor of each 
 * action it's running. Information on each action is incapsulated by SVNEvent and is
 * reported by ISVNEventHandler.svnEvent(SVNEvent event, double progress).    
 */
public class UpdateEventHandler implements ISVNEventHandler {
    /*
     * progress - will be the current percentage stage of an operation (how many of
     * operation has passed) 
     */
    public void handleEvent(SVNEvent event, double progress) {
        /*
         * Gets the current action. An action is represented by SVNEventAction.
         * In case of an update a current action can be determined via 
         * SVNEvent.getAction() and  SVNEventAction.UPDATE_-like constants. 
         */
        SVNEventAction action = event.getAction();
        String pathChangeType = " ";
        if (action == SVNEventAction.UPDATE_ADD) {
            /*
             * the item was added
             */
            pathChangeType = "A";
        } else if (action == SVNEventAction.UPDATE_DELETE) {
            /*
             * the item was deleted
             */
            pathChangeType = "D";
        } else if (action == SVNEventAction.UPDATE_UPDATE) {
            /*
             * Find out in details what state the item is (after having
             * been updated).
             */
            /*
             * Gets the status of file/directory item contents. It is 
             * SVNStatusType who contains information on the state of an item.
             */
            SVNStatusType contentsStatus = event.getContentsStatus();
            if (contentsStatus == SVNStatusType.CHANGED) {
                /*
                 * the local item was updated - got the changes
                 * from the repository
                 */
                pathChangeType = "U";
            }else if (contentsStatus == SVNStatusType.CONFLICTED) {
                /*
                 * The file item is in a state of Conflict. That is, changes
                 * received from the server during an update overlap with local
                 * changes the user has in his working copy.
                 */
                pathChangeType = "C";
            } else if (contentsStatus == SVNStatusType.MERGED) {
                /*
                 * The file item was merGed (changes that came from the
                 * repository did not overlap local changes and were merged into
                 * the file).
                 */
                pathChangeType = "G";
            }
        } else if (action == SVNEventAction.UPDATE_EXTERNAL) {//for externals definitions
            System.out.println("Fetching external item into '"
                    + event.getFile().getAbsolutePath() + "'");
            System.out.println("External at revision " + event.getRevision());
            return;
        } else if (action == SVNEventAction.UPDATE_COMPLETED) {
            /*
             * The working copy update is completed.
             */
            System.out.println("At revision " + event.getRevision());
            return;
        } else if (action == SVNEventAction.ADD){
            System.out.println("A     " + event.getPath());
            return;
        } else if (action == SVNEventAction.DELETE){
            System.out.println("D     " + event.getPath());
            return;
        } else if (action == SVNEventAction.LOCKED){
            System.out.println("L     " + event.getPath());
            return;
        } else if (action == SVNEventAction.LOCK_FAILED){
            System.out.println("failed to lock    " + event.getPath());
            return;
        }

        /*
         * Now getting the status of properties of an item. SVNStatusType also
         * contains information on the properties state.
         */
        SVNStatusType propertiesStatus = event.getPropertiesStatus();
        /*
         * Default - properties are normal (unchanged).
         */
        String propertiesChangeType = " ";
        if (propertiesStatus == SVNStatusType.CHANGED) {
            /*
             * Properties were updated.
             */
            propertiesChangeType = "U";
        } else if (propertiesStatus == SVNStatusType.CONFLICTED) {
            /*
             * Properties are in conflict with the repository.
             */
            propertiesChangeType = "C";
        } else if (propertiesStatus == SVNStatusType.MERGED) {
            /*
             * Properties that came from the repository were merged with the
             * local ones.
             */
            propertiesChangeType = "G";
        }

        /*
         * Gets the status of the lock.
         */
        String lockLabel = " ";
        SVNStatusType lockType = event.getLockStatus();
        
        if (lockType == SVNStatusType.LOCK_UNLOCKED) {
            /*
             * The lock is broken by someone.
             */
            lockLabel = "B";
        }
        
        System.out.println(pathChangeType
                + propertiesChangeType
                + lockLabel
                + "       "
                + event.getPath());
    }

    /*
     * Should be implemented to react on a user's cancel of the operation.
     * If the operation was cancelled this method should throw an 
     * SVNCancelException. 
     */
    public void checkCancelled() throws SVNCancelException {
    }

}