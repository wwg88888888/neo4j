/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
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
package org.neo4j.commandline.dbms;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.neo4j.io.IOUtils;
import org.neo4j.io.fs.DefaultFileSystemAbstraction;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.kernel.internal.locker.DatabaseLocker;
import org.neo4j.kernel.internal.locker.FileLockException;
import org.neo4j.kernel.internal.locker.Locker;

public class DatabaseLockChecker implements Closeable
{

    private final FileSystemAbstraction fileSystem;
    private final Locker locker;

    private DatabaseLockChecker( FileSystemAbstraction fileSystem, DatabaseLayout databaseLayout )
    {
        this.fileSystem = fileSystem;
        this.locker = new DatabaseLocker( fileSystem, databaseLayout );
    }

    /**
     * Create database lock checker with lock on a provided database layout if its exist and writable
     *
     * @param databaseLayout database layout to check
     * @return lock checker or empty closeable in case if path does not exists or is not writable
     * @see Locker
     * @see Files
     */
    public static Closeable check( DatabaseLayout databaseLayout ) throws CannotWriteException
    {
        Path lockFile = databaseLayout.databaseLockFile().toPath();
        if ( Files.isWritable( databaseLayout.databaseDirectory().toPath() ) )
        {
            DatabaseLockChecker locker = new DatabaseLockChecker( new DefaultFileSystemAbstraction(), databaseLayout );
            try
            {
                locker.checkLock();
                return locker;
            }
            catch ( FileLockException le )
            {
                try
                {
                    locker.close();
                }
                catch ( IOException e )
                {
                    le.addSuppressed( e );
                }
                throw le;
            }
        }
        else
        {
            throw new CannotWriteException( lockFile );
        }
    }

    private void checkLock()
    {
        locker.checkLock();
    }

    @Override
    public void close() throws IOException
    {
        IOUtils.closeAll( locker, fileSystem );
    }
}
