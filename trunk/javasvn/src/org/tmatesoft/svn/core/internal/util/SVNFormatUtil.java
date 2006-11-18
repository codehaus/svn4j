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

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.StringTokenizer;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class SVNFormatUtil {
    
    private static final DateFormat HUMAN_DATE_FORMAT = 
        new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss' 'ZZZZ' ('E', 'dd' 'MMM' 'yyyy')'");
    private static final Date NULL_DATE = new Date(0);
    
    
    public static String formatDate(Date date) {
        return HUMAN_DATE_FORMAT.format(date != null ? date : NULL_DATE);
    }
    
    public static String formatString(String src, int width, boolean left) {
        if (src.length() > width) {
            return src.substring(0, width);
        }
        StringBuffer formatted = new StringBuffer();
        if (left) {
            formatted.append(src);
        }
        for (int i = 0; i < width - src.length(); i++) {
            formatted.append(' ');
        }
        if (!left) {
            formatted.append(src);
        }
        return formatted.toString();
    }
    
    // path relative to the program home directory, or absolute path
    public static String formatPath(File file) {
        String path;
        String rootPath;
        path = file.getAbsolutePath();
        rootPath = new File("").getAbsolutePath();
        path = path.replace(File.separatorChar, '/');
        rootPath = rootPath.replace(File.separatorChar, '/');
        if (path.equals(rootPath)) {
            path  = "";
        } else if (path.startsWith(rootPath + "/")) {
            path = path.substring(rootPath.length() + 1);
        }
        // remove all "./"
        path = condensePath(path);
        path = path.replace('/', File.separatorChar);
        if (path.trim().length() == 0) {
            path = ".";
        }
        return path;
    }

    private static String condensePath(String path) {
        StringBuffer result = new StringBuffer();
        for (StringTokenizer tokens = new StringTokenizer(path, "/", true); tokens
                .hasMoreTokens();) {
            String token = tokens.nextToken();
            if (".".equals(token)) {
                if (tokens.hasMoreTokens()) {
                    String nextToken = tokens.nextToken();
                    if (!nextToken.equals("/")) {
                        result.append(nextToken);
                    }
                }
                continue;
            }
            result.append(token);
        }
        return result.toString();
    }
}
