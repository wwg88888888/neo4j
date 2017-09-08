/*
 * Copyright (c) 2002-2017 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.index.lucene;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.neo4j.index.impl.lucene.explicit.LuceneIndexImplementation;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.extension.KernelExtensionFactory;
import org.neo4j.kernel.impl.index.IndexConfigStore;
import org.neo4j.kernel.impl.spi.KernelContext;
import org.neo4j.kernel.lifecycle.Lifecycle;
import org.neo4j.kernel.spi.explicitindex.IndexProviders;

/**
 * @deprecated removed in 4.0
 */
@Deprecated
public class LuceneKernelExtensionFactory extends KernelExtensionFactory<LuceneKernelExtensionFactory.Dependencies>
{
    /**
     * @deprecated removed in 4.0
     */
    @Deprecated
    public interface Dependencies
    {
        Config getConfig();

        org.neo4j.kernel.spi.legacyindex.IndexProviders getIndexProviders();

        IndexConfigStore getIndexStore();

        FileSystemAbstraction fileSystem();
    }

    /**
     * @deprecated removed in 4.0
     */
    @Deprecated
    public LuceneKernelExtensionFactory()
    {
        super( LuceneIndexImplementation.SERVICE_NAME );
    }

    @Override
    public Lifecycle newInstance( KernelContext context, Dependencies dependencies ) throws Throwable
    {
        IndexProviders indexProvider = mimicClassWith( IndexProviders.class, dependencies.getIndexProviders() );
        return new org.neo4j.kernel.api.impl.index.LuceneKernelExtension(
                context.storeDir(),
                dependencies.getConfig(),
                dependencies::getIndexStore,
                dependencies.fileSystem(),
                indexProvider,
                context.databaseInfo().operationalMode );
    }

    /**
     * Create a mimicking proxy since it's in the public API and can't be changed
     */
    @SuppressWarnings( "unchecked" )
    private static <T,F> T mimicClassWith( Class<T> clazz, F base )
    {
        return (T)Proxy.newProxyInstance( null, new Class<?>[]{clazz}, new MimicWrapper<>( base ) );
    }

    private static class MimicWrapper<F> implements InvocationHandler
    {
        private final F wrapped;

        MimicWrapper( F wrapped )
        {
            this.wrapped = wrapped;
        }

        @Override
        public Object invoke( Object proxy, Method method, Object[] args ) throws Throwable
        {
            Method match = wrapped.getClass().getMethod(method.getName(), method.getParameterTypes());
            return match.invoke( wrapped, args);
        }
    }
}
