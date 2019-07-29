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
package org.neo4j.dbms.archive;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Random;

import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.io.layout.DatabaseLayout;
import org.neo4j.test.extension.DefaultFileSystemExtension;
import org.neo4j.test.extension.Inject;
import org.neo4j.test.extension.TestDirectoryExtension;
import org.neo4j.test.rule.TestDirectory;

import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.neo4j.dbms.archive.TestUtils.withPermissions;

@ExtendWith( {DefaultFileSystemExtension.class, TestDirectoryExtension.class} )
class LoaderTest
{
    @Inject
    private TestDirectory testDirectory;
    @Inject
    private FileSystemAbstraction fileSystem;

    @Test
    void shouldGiveAClearErrorMessageIfTheArchiveDoesntExist() throws IOException
    {
        Path archive = testDirectory.file( "the-archive.dump" ).toPath();
        DatabaseLayout databaseLayout = testDirectory.databaseLayout();
        deleteLayoutFolders( databaseLayout );

        NoSuchFileException exception = assertThrows( NoSuchFileException.class, () -> new Loader().load( archive, databaseLayout ) );
        assertEquals( archive.toString(), exception.getMessage() );
    }

    @Test
    void shouldGiveAClearErrorMessageIfTheArchiveIsNotInGzipFormat() throws IOException
    {
        Path archive = testDirectory.file( "the-archive.dump" ).toPath();
        Files.write( archive, singletonList( "some incorrectly formatted data" ) );
        DatabaseLayout databaseLayout = testDirectory.databaseLayout();
        deleteLayoutFolders( databaseLayout );

        IncorrectFormat incorrectFormat = assertThrows( IncorrectFormat.class, () -> new Loader().load( archive, databaseLayout ) );
        assertEquals( archive.toString(), incorrectFormat.getMessage() );
    }

    @Test
    void shouldGiveAClearErrorMessageIfTheArchiveIsNotInTarFormat() throws IOException
    {
        Path archive = testDirectory.file( "the-archive.dump" ).toPath();
        try ( GzipCompressorOutputStream compressor =
                      new GzipCompressorOutputStream( Files.newOutputStream( archive ) ) )
        {
            byte[] bytes = new byte[1000];
            new Random().nextBytes( bytes );
            compressor.write( bytes );
        }

        DatabaseLayout databaseLayout = testDirectory.databaseLayout();
        deleteLayoutFolders( databaseLayout );

        IncorrectFormat incorrectFormat = assertThrows( IncorrectFormat.class, () -> new Loader().load( archive, databaseLayout ) );
        assertEquals( archive.toString(), incorrectFormat.getMessage() );
    }

    @Test
    void shouldGiveAClearErrorIfTheDestinationTxLogAlreadyExists()
    {
        Path archive = testDirectory.file( "the-archive.dump" ).toPath();
        DatabaseLayout databaseLayout = testDirectory.databaseLayout();

        assertTrue( databaseLayout.databaseDirectory().delete() );
        assertTrue( databaseLayout.getTransactionLogsDirectory().exists() );

        FileAlreadyExistsException exception = assertThrows( FileAlreadyExistsException.class, () -> new Loader().load( archive, databaseLayout ) );
        assertEquals( databaseLayout.getTransactionLogsDirectory().toString(), exception.getMessage() );
    }

    @Test
    void shouldGiveAClearErrorMessageIfTheDestinationsParentDirectoryDoesntExist()
    {
        Path archive = testDirectory.file( "the-archive.dump" ).toPath();
        Path destination = Paths.get( testDirectory.absolutePath().getAbsolutePath(), "subdir", "the-destination" );
        DatabaseLayout databaseLayout = DatabaseLayout.of( destination.toFile() );

        NoSuchFileException noSuchFileException = assertThrows( NoSuchFileException.class, () -> new Loader().load( archive, databaseLayout ) );
        assertEquals( destination.getParent().toString(), noSuchFileException.getMessage() );
    }

    @Test
    void shouldGiveAClearErrorMessageIfTheTxLogsParentDirectoryDoesntExist() throws IOException
    {
        Path archive = testDirectory.file( "the-archive.dump" ).toPath();
        Path txLogsDestination = Paths.get( testDirectory.absolutePath().getAbsolutePath(), "subdir", "txLogs" );
        DatabaseLayout databaseLayout = DatabaseLayout.of( testDirectory.file("destination"), () -> Optional.of( txLogsDestination.toFile() ) );

        NoSuchFileException noSuchFileException = assertThrows( NoSuchFileException.class, () -> new Loader().load( archive, databaseLayout ) );
        assertEquals( txLogsDestination.toString(), noSuchFileException.getMessage() );
    }

    @Test
    void shouldGiveAClearErrorMessageIfTheDestinationsParentDirectoryIsAFile()
            throws IOException
    {
        Path archive = testDirectory.file( "the-archive.dump" ).toPath();
        Path destination = Paths.get( testDirectory.absolutePath().getAbsolutePath(), "subdir", "the-destination" );
        Files.write( destination.getParent(), new byte[0] );
        DatabaseLayout databaseLayout = DatabaseLayout.of( destination.toFile() );

        FileSystemException exception = assertThrows( FileSystemException.class, () -> new Loader().load( archive, databaseLayout ) );
        assertEquals( destination.getParent().toString() + ": Not a directory", exception.getMessage() );
    }

    @Test
    @DisabledOnOs( OS.WINDOWS )
    void shouldGiveAClearErrorMessageIfTheDestinationsParentDirectoryIsNotWritable()
            throws IOException
    {
        Path archive = testDirectory.file( "the-archive.dump" ).toPath();
        File destination = testDirectory.directory( "subdir/the-destination" );
        DatabaseLayout databaseLayout = DatabaseLayout.of( destination );

        Path parentPath = databaseLayout.databaseDirectory().getParentFile().toPath();
        try ( Closeable ignored = withPermissions( parentPath, emptySet() ) )
        {
            AccessDeniedException exception = assertThrows( AccessDeniedException.class, () -> new Loader().load( archive, databaseLayout ) );
            assertEquals( parentPath.toString(), exception.getMessage() );
        }
    }

    @Test
    @DisabledOnOs( OS.WINDOWS )
    void shouldGiveAClearErrorMessageIfTheTxLogsParentDirectoryIsNotWritable()
            throws IOException
    {
        Path archive = testDirectory.file( "the-archive.dump" ).toPath();
        File txLogsDirectory = testDirectory.directory( "subdir/txLogs" );
        DatabaseLayout databaseLayout = DatabaseLayout.of( testDirectory.file( "destination" ) , () -> Optional.of( txLogsDirectory ) );

        Path txLogsRoot = databaseLayout.getTransactionLogsDirectory().getParentFile().toPath();
        try ( Closeable ignored = withPermissions( txLogsRoot, emptySet() ) )
        {
            AccessDeniedException exception = assertThrows( AccessDeniedException.class, () -> new Loader().load( archive, databaseLayout ) );
            assertEquals( txLogsRoot.toString(), exception.getMessage() );
        }
    }

    private void deleteLayoutFolders( DatabaseLayout databaseLayout ) throws IOException
    {
        fileSystem.deleteRecursively( databaseLayout.databaseDirectory() );
        fileSystem.deleteRecursively( databaseLayout.getTransactionLogsDirectory() );
    }
}
