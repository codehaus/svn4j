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
 * The final class <code>SVNNodeKind</code> incapsulates the kind of a versioned node
 * stored in the Subversion repository. This can be:
 * <ul>
 * <li>a directory - the node is a directory
 * <li>a file      - the node is a file
 * <li>none        - the node is absent (does not exist)
 * <li>unknown     - the node kind can not be recognized
 * </ul>
 * <code>SVNNodeKind</code> items are used to describe directory
 * entry type.
 *  
 * @version 1.0
 * @author 	TMate Software Ltd.
 * @see 	SVNDirEntry
 */
public final class SVNNodeKind implements Comparable {
    /**
     * Defines the none node kind 
     */
    public static final SVNNodeKind NONE = new SVNNodeKind(2);
    /**
     * Defines the file node kind
     */
    public static final SVNNodeKind FILE = new SVNNodeKind(1);
    /**
     * Defines the directory node kind
     */
    public static final SVNNodeKind DIR = new SVNNodeKind(0);
    /**
     * Defines the unknown node kind
     */
    public static final SVNNodeKind UNKNOWN = new SVNNodeKind(3);

    private int myID;

    private SVNNodeKind(int id) {
        myID = id;
    }
    
    /**
     * Parses the passed string and finds out the node kind. For instance,
     * parseKind("dir") will return <code>SVNNodeKind.DIR</code>.
     * 
     * @param kind 		a node kind as a string
     * @return 			node kind as <code>SVNNodeKind</code>. If the exact node kind is 
     * 					not known <code>SVNNodeKind.UNKNOWN</code> is returned or if
     * 					the node is currently missing - <code>SVNNodeKind.NONE</code>.
     */
    public static SVNNodeKind parseKind(String kind) {
        if ("file".equals(kind)) {
            return FILE;
        } else if ("dir".equals(kind)) {
            return DIR;
        } else if ("none".equals(kind) || kind == null) {
            return NONE;
        }
        return UNKNOWN;
    }
    /**
     * Represents the current <code>SVNNodeKind</code> object as a string.
     * 
     * @return string representation of this object.
     */
    public String toString() {
        if (this == NONE) {
            return "none";
        } else if (this == FILE) {
            return "file";
        } else if (this == DIR) {
            return "dir";
        }
        return "unknown";
    }

    public int compareTo(Object o) {
        if (o == null || o.getClass() != SVNNodeKind.class) {
            return -1;
        }
        int otherID = ((SVNNodeKind) o).myID;
        return myID > otherID ? 1 : myID < otherID ? -1 : 0;
    }
}
