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

import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.tmatesoft.svn.util.ISVNDebugLogger;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNLogOutputStream extends FilterOutputStream {

    private ISVNDebugLogger myLogger;
    private ByteArrayOutputStream myBuffer;

    public SVNLogOutputStream(OutputStream out, ISVNDebugLogger logger) {
        super(out);
        myLogger = logger;
        myBuffer = new ByteArrayOutputStream(2048);
    }

    public void close() throws IOException {
        super.close();
        flushBuffer(true);
    }

    public void flush() throws IOException {
        super.flush();
        flushBuffer(true);
    }

    public void write(int b) throws IOException {
        super.write(b);
        if (myBuffer != null) {
            myBuffer.write(b);
        }
        flushBuffer(false);
    }
    
    public void flushBuffer(boolean force) {
        if (!force && myBuffer.size() < 1024) {
            return;
        }
        if (myLogger != null && myBuffer.size() > 0) {
            myLogger.log("SENT", myBuffer.toByteArray());
        }
        myBuffer.reset();
    }


}
