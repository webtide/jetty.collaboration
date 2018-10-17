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

package org.eclipse.jetty.websocket.server;

import org.eclipse.jetty.webapp.AbstractConfiguration;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.MetaInfConfiguration;
import org.eclipse.jetty.webapp.WebAppConfiguration;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;

import java.util.ServiceLoader;

import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * <p>Websocket Configuration</p>
 * <p>This configuration configures the WebAppContext server/system classes to
 * be able to see the org.eclipse.jetty.websocket package.   
 * This class is defined in the webapp package, as it implements the {@link Configuration} interface,
 * which is unknown to the websocket package.  However, the corresponding {@link ServiceLoader}
 * resource is defined in the websocket package, so that this configuration only be 
 * loaded if the jetty-websocket jars are on the classpath.
 * </p>
 */
public class JettyWebSocketConfiguration extends AbstractConfiguration
{
    private static final Logger LOG = Log.getLogger(JettyWebSocketConfiguration.class);
    public JettyWebSocketConfiguration()
    {
        addDependencies(WebXmlConfiguration.class, MetaInfConfiguration.class, WebInfConfiguration.class, FragmentConfiguration.class);
        addDependents("org.eclipse.jetty.annotations.AnnotationConfiguration", WebAppConfiguration.class.getName());
        protectAndExpose(
            "org.eclipse.jetty.websocket.api.",
            "org.eclipse.jetty.websocket.common.",
            "org.eclipse.jetty.websocket.client.",
            "org.eclipse.jetty.websocket.server.");
    }

    @Override
    public boolean isAvailable()
    {
        try
        {
            return Loader.loadClass("org.eclipse.jetty.websocket.common.JettyWebSocketFrame")!=null;
        }
        catch (Throwable e)
        {
            LOG.ignore(e);
            return false;
        }
    }
}
