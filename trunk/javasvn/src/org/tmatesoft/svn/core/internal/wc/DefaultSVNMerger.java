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
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNMerger;
import org.tmatesoft.svn.core.wc.SVNStatusType;

import de.regnis.q.sequence.line.QSequenceLineRAData;
import de.regnis.q.sequence.line.QSequenceLineRAFileData;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class DefaultSVNMerger implements ISVNMerger {
    
    private byte[] myStart;
    private byte[] mySeparator;
    private byte[] myEnd;
    private byte[] myEOL;

    public DefaultSVNMerger(byte[] start, byte[] sep, byte[] end, byte[] eol) {
        myStart = start;
        mySeparator = sep;
        myEnd = end;
        myEOL = eol;
    }

    public SVNStatusType mergeText(File baseFile, File localFile, File latestFile, boolean dryRun, OutputStream result) throws SVNException {
        FSMergerBySequence merger = new FSMergerBySequence(myStart, mySeparator, myEnd, myEOL);
        int mergeResult = 0;
        RandomAccessFile localIS = null;
        RandomAccessFile latestIS = null;
        RandomAccessFile baseIS = null;
        try {
            localIS = new RandomAccessFile(localFile, "r");
            latestIS = new RandomAccessFile(latestFile, "r");
            baseIS = new RandomAccessFile(baseFile, "r");

            QSequenceLineRAData baseData = new QSequenceLineRAFileData(baseIS);
            QSequenceLineRAData localData = new QSequenceLineRAFileData(localIS);
            QSequenceLineRAData latestData = new QSequenceLineRAFileData(latestIS);
            mergeResult = merger.merge(baseData, localData, latestData, result);
        } catch (IOException e) {
            SVNErrorManager.error("svn: I/O error while merge: '" + e.getMessage() + "'");
        } finally {
            if (localIS != null) {
                try {
                    localIS.close();
                } catch (IOException e) {
                    //
                }
            }
            if (baseIS != null) {
                try {
                    baseIS.close();
                } catch (IOException e) {
                    //
                }
            }
            if (latestIS != null) {
                try {
                    latestIS.close();
                } catch (IOException e) {
                    //
                }
            }
        }
        SVNStatusType status = SVNStatusType.UNCHANGED;
        if (mergeResult == FSMergerBySequence.CONFLICTED) {
            status = SVNStatusType.CONFLICTED;
        } else if (mergeResult == FSMergerBySequence.MERGED) {
            status = SVNStatusType.MERGED;
        }
        
        return status;
    }

}
