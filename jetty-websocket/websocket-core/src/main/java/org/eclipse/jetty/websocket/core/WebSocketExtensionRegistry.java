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

package org.eclipse.jetty.websocket.core;

import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.server.ClassLoaderDump;
import org.eclipse.jetty.util.DecoratedObjectFactory;
import org.eclipse.jetty.util.StringUtil;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

public class WebSocketExtensionRegistry implements Iterable<Class<? extends Extension>>
{
    private Map<String, Class<? extends Extension>> availableExtensions;

    public WebSocketExtensionRegistry()
    {
        // Load extensions from container loader
        ServiceLoader<Extension> extensionLoader = ServiceLoader.load(Extension.class,this.getClass().getClassLoader());
        availableExtensions = new HashMap<>();
        for (Extension ext : extensionLoader)
        {
            if (ext != null)
            {
                availableExtensions.put(ext.getName(),ext.getClass());
            }
        }
    }

    public Map<String, Class<? extends Extension>> getAvailableExtensions()
    {
        return availableExtensions;
    }

    public Class<? extends Extension> getExtension(String name)
    {
        return availableExtensions.get(name);
    }

    public Set<String> getAvailableExtensionNames()
    {
        return availableExtensions.keySet();
    }

    public boolean isAvailable(String name)
    {
        return availableExtensions.containsKey(name);
    }

    @Override
    public Iterator<Class<? extends Extension>> iterator()
    {
        return availableExtensions.values().iterator();
    }

    public Extension newInstance(DecoratedObjectFactory objectFactory, ByteBufferPool bufferPool, ExtensionConfig config)
    {
        if (config == null)
        {
            return null;
        }

        String name = config.getName();
        if (StringUtil.isBlank(name))
        {
            return null;
        }

        Class<? extends Extension> extClass = getExtension(name);
        if (extClass == null)
        {
            return null;
        }

        try
        {
            Extension ext = objectFactory.createInstance(extClass);
            ext.init(config, bufferPool);

            return ext;
        }
        catch (Throwable t)
        {
            throw new WebSocketException("Cannot instantiate extension: " + extClass,t);
        }
    }

    public void register(String name, Class<? extends Extension> extension)
    {
        availableExtensions.put(name,extension);
    }

    public void unregister(String name)
    {
        availableExtensions.remove(name);
    }
}
