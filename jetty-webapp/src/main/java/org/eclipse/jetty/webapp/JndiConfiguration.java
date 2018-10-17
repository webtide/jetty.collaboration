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

package org.eclipse.jetty.webapp;

import java.util.ServiceLoader;

import org.eclipse.jetty.util.Loader;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * <p>JNDI Configuration</p>
 * <p>This configuration configures the WebAppContext system/server classes to
 * be able to see the org.eclipse.jetty.jaas package.   
 * This class is defined in the webapp package, as it implements the {@link Configuration} interface,
 * which is unknown to the jndi package.  However, the corresponding {@link ServiceLoader}
 * resource is defined in the jndi package, so that this configuration only be 
 * loaded if the jetty-jndi jars are on the classpath.
 * </p>
 */
public class JndiConfiguration extends AbstractConfiguration
{
    private static final Logger LOG = Log.getLogger(JndiConfiguration.class);

    public JndiConfiguration()
    {
        addDependencies(WebXmlConfiguration.class, MetaInfConfiguration.class, WebInfConfiguration.class, FragmentConfiguration.class);
        addDependents(WebAppConfiguration.class);
        protectAndExpose("org.eclipse.jetty.jndi.");
    }

    @Override
    public boolean isAvailable()
    {
        try
        {
            return Loader.loadClass("org.eclipse.jetty.jndi.InitialContextFactory")!=null;
        }
        catch (Throwable e)
        {
            LOG.ignore(e);
            return false;
        }
    }
}
