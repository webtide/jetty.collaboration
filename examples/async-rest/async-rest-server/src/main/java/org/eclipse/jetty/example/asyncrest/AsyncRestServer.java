//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.example.asyncrest;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.ClassMatcher;
import org.eclipse.jetty.webapp.WebAppContext;

import javax.servlet.http.HttpServlet;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class AsyncRestServer
{
    public static void main(String[] args)
        throws Exception
    {
        // Find the async-reset webapp based on common IDE working directories
        // TODO import webapp as maven artifact
        Path home = FileSystems.getDefault().getPath(System.getProperty("jetty.home",".")).toAbsolutePath();
        Path war = home.resolve("../async-rest-webapp/target/async-rest/");
        if (!Files.exists(war))
            war = home.resolve("examples/async-rest/async-rest-webapp/target/async-rest/");
        if (!Files.exists(war))
            throw new IllegalArgumentException("Cannot find async-rest webapp");

        // Build a demo server
        Server server = new Server(Integer.getInteger("jetty.http.port",8080).intValue());
        WebAppContext webapp = new WebAppContext();
        webapp.setContextPath("/");
        webapp.setWar(war.toAbsolutePath().toString());
        server.setHandler(webapp);

        server.start();
        server.join();
    }
}
