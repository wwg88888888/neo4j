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

import java.io.File;
import java.util.function.Supplier;

import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.factory.OperationalMode;
import org.neo4j.kernel.impl.index.IndexConfigStore;
import org.neo4j.kernel.lifecycle.LifecycleAdapter;
import org.neo4j.kernel.spi.explicitindex.IndexProviders;

/**
 * @deprecated removed in 4.0
 */
@Deprecated
public class LuceneKernelExtension extends LifecycleAdapter
{
    private final org.neo4j.kernel.api.impl.index.LuceneKernelExtension delegate;

    /**
     * @deprecated removed in 4.0
     */
    @Deprecated
    public LuceneKernelExtension( File storeDir, Config config, Supplier<IndexConfigStore> indexStore,
            FileSystemAbstraction fileSystemAbstraction, IndexProviders indexProviders )
    {
        this( storeDir, config, indexStore, fileSystemAbstraction, indexProviders, OperationalMode.single );
    }

    /**
     * @deprecated removed in 4.0
     */
    @Deprecated
    public LuceneKernelExtension( File storeDir, Config config, Supplier<IndexConfigStore> indexStore,
            FileSystemAbstraction fileSystemAbstraction, IndexProviders indexProviders, OperationalMode operationalMode )
    {
        delegate = new org.neo4j.kernel.api.impl.index.LuceneKernelExtension( storeDir, config, indexStore,
                fileSystemAbstraction, indexProviders, operationalMode );
    }

    @Override
    public void init()
    {
        delegate.init();
    }

    @Override
    public void shutdown()
    {
        delegate.shutdown();
    }
}
