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
package org.tmatesoft.svn.core.test;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;

import org.tigris.subversion.javahl.ClientException;
import org.tigris.subversion.javahl.Revision;
import org.tigris.subversion.javahl.SVNClient;
import org.tigris.subversion.javahl.SVNClientInterface;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.javahl.SVNClientImpl;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class Benchmark {

    public static void main(String[] args) throws ClientException, SVNException {
        File root = new File("c:/javasvn/benchmark-test");
        SVNFileUtil.deleteAll(root, true);
        root.mkdirs();
        
        long start = System.currentTimeMillis();
        System.out.println("JAVASVN");
        Mark[] javaSVN = doBenchmark(createJavaSVNClient(), "c:/javasvn/benchmark-test/javasvn");
        System.out.println("total time: " + (System.currentTimeMillis() - start));
        start = System.currentTimeMillis();
        System.out.println("JAVAHL");
        Mark[] javaHL = doBenchmark(createJavaHLClient(), "c:/javasvn/benchmark-test/javahl");
        System.out.println("total time: " + (System.currentTimeMillis() - start));
        publishResults(javaSVN, javaHL, "c:/javasvn/javasvn/result.html");
    }
    
    private static void publishResults(Mark[] javaSVN, Mark[] javaHL, String filePath) throws SVNException {
        File file = new File(filePath);
        
        OutputStream os = null;
        try {
            os = SVNFileUtil.openFileForWriting(file);
            os.write("<table width=\"100%\">".getBytes());
            String line = "<tr><th>Test</th><th>JavaSVN (ms)</th><th>JavaHL (ms)</th><th>Diff</th></tr>";
            os.write(line.getBytes());
            for (int i = 0; i < javaHL.length; i++) {
                Mark m1 = javaSVN[i];
                Mark m2 = javaHL[i];
                double diff = ((double) m1.getTime())/((double) m2.getTime());
                line = "<tr><td>" + m1.getName() + "</td><td align=right>" + m1.getTime() + "</td><td align=right>" + m2.getTime()+ "</td>" +
                        "<td align=\"right\" >" + diff + "</td></tr>";
                os.write(line.getBytes());
            }
            os.write("</table>".getBytes());
        } catch (IOException e) {
        } finally {
            SVNFileUtil.closeFile(os);
        }
    }

    private static Mark[] doBenchmark(SVNClientInterface client, String rootPath) throws ClientException {
        String srcURL = "http://localhost:8082/svn";
        String dstURL = "http://localhost:8082/svn";
        String srcPath = rootPath + "/src";
        String dstPath = rootPath + "/dst";
        String dstPath2 = rootPath + "/dst2";
        
        Mark mark;
        Collection marks = new ArrayList();
        // do checkout,
        mark = doCheckout(client, srcURL, srcPath);
        marks.add(mark);
        
        
        // then status
        mark = doLocalStatus(client, srcPath);
        marks.add(mark);
        mark = doRemoteStatus(client, srcPath);
        marks.add(mark);
        // then export (wc->wc)
        mark = doExport(client, srcPath, dstPath);
        marks.add(mark);
        // then import
        dstURL += "/import-" + System.currentTimeMillis();
        mark = doImport(client, dstURL, dstPath);
        marks.add(mark);
        
        // and then checkout, modification, status, commit
        mark = doCheckout(client, dstURL, dstPath2);
        marks.add(mark);
        doLocalModifications(client, dstPath2);
        mark = doStatus(client, dstPath2);
        marks.add(mark);
        mark = doCommit(client, dstPath2);
        marks.add(mark);
        
        // delete temporary location.
        client.remove(new String[] {dstURL}, "deleted", true);
        return (Mark[]) marks.toArray(new Mark[marks.size()]);
    }
    
    private static Mark doCheckout(SVNClientInterface client, String url, String path) throws ClientException {
        Mark mark = new Mark("Checkout");
        mark.start();
        client.checkout(url, path, Revision.HEAD, true);
        mark.finish();
        return mark;
    }

    private static Mark doLocalStatus(SVNClientInterface client, String path) throws ClientException {
        Mark mark = new Mark("Local status");
        mark.start();
        client.status(path, true, false, true, true, false);
        mark.finish();
        return mark;
    }

    private static Mark doRemoteStatus(SVNClientInterface client, String path) throws ClientException {
        Mark mark = new Mark("Remote status");
        mark.start();
        client.status(path, true, true, true, true, false);
        mark.finish();
        return mark;
    }
    
    private static Mark doExport(SVNClientInterface client, String from, String to) throws ClientException {
        Mark mark = new Mark("Local export");
        mark.start();
        client.doExport(from, to, Revision.WORKING, false);
        mark.finish();
        return mark;
    }
    
    private static Mark doImport(SVNClientInterface client, String url, String path) throws ClientException {
        Mark mark = new Mark("Import");
        mark.start();
        client.doImport(path, url, "import", true);
        mark.finish();
        return mark;
    }
    
    private static void doLocalModifications(SVNClientInterface client, String path) throws ClientException {
        // move dir (add and delete).
        String srcPath = path + "/subversion/clients";
        String dstPath = path + "/subversion/clients2";
        client.move(srcPath, dstPath, "", true);
        // modify files.
        File[] files = new File(path + "/www").listFiles();
        try {
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()) {
                    continue;
                }
                OutputStream os = SVNFileUtil.openFileForWriting(files[i], true);
                os.write("local modification".getBytes());
                SVNFileUtil.closeFile(os);
            }
        } catch (SVNException e) {
        } catch (IOException e) {
        }
    }

    private static Mark doCommit(SVNClientInterface client,  String path) throws ClientException {
        // make local modifications at path
        Mark mark = new Mark("Commit");
        mark.start();
        client.commit(new String[] {path}, "commit", true, false);
        mark.finish();
        return mark;
    }

    private static Mark doStatus(SVNClientInterface client, String path) throws ClientException {
        Mark mark = new Mark("Status (mods)");
        mark.start();
        client.status(path, true, false, true, true, false);
        mark.finish();
        return mark;
    }
    
    private static SVNClientInterface createJavaHLClient() {
        return new SVNClient();        
    }

    private static SVNClientInterface createJavaSVNClient() {
        SVNClientInterface client = SVNClientImpl.newInstance();
        return client;
    }
    
    private static class Mark {
        
        private String myName;
        private long myStartTime;
        private long myFinishTime;

        public Mark(String name) {
            myName = name;
        }
        
        public void start() {
            myStartTime = System.currentTimeMillis();
        }
        
        public void finish() {
            myFinishTime = System.currentTimeMillis();
        }
        
        public String getName() {
            return myName;
        }
        
        public long getTime() {
            if (myFinishTime > myStartTime && myStartTime > 0) {
                return myFinishTime - myStartTime;
            }
            return -1;
        }
        
        public String toString() {
            String time = "<not started>";
            if (myStartTime > 0) {
                time = "<not completed>";
                if (myFinishTime > 0) {
                    time = Long.toString(myFinishTime - myStartTime) + " ms.";
                }
            }
            return myName + ": " + time;
        }
    }
}
