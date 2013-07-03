/**
 * Copyright (c) 2002-2013 "Neo Technology,"
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
package org.neo4j.kernel.impl.api;

import java.util.Iterator;

import org.neo4j.kernel.api.constraints.UniquenessConstraint;
import org.neo4j.kernel.api.exceptions.KernelException;
import org.neo4j.kernel.api.exceptions.schema.AddIndexFailureException;
import org.neo4j.kernel.api.exceptions.schema.AlreadyConstrainedException;
import org.neo4j.kernel.api.exceptions.schema.AlreadyIndexedException;
import org.neo4j.kernel.api.exceptions.schema.DropIndexFailureException;
import org.neo4j.kernel.api.exceptions.schema.IllegalTokenNameException;
import org.neo4j.kernel.api.exceptions.schema.IndexBelongsToConstraintException;
import org.neo4j.kernel.api.exceptions.schema.NoSuchIndexException;
import org.neo4j.kernel.api.exceptions.schema.SchemaKernelException;
import org.neo4j.kernel.api.operations.KeyWriteOperations;
import org.neo4j.kernel.api.operations.SchemaReadOperations;
import org.neo4j.kernel.api.operations.SchemaWriteOperations;
import org.neo4j.kernel.impl.api.index.IndexDescriptor;

import static org.neo4j.helpers.collection.IteratorUtil.loop;

public class DataIntegrityValidatingStatementContext implements
    KeyWriteOperations,
    SchemaWriteOperations
{
    private final KeyWriteOperations keyWriteDelegate;
    private final SchemaReadOperations schemaReadDelegate;
    private final SchemaWriteOperations schemaWriteDelegate;

    public DataIntegrityValidatingStatementContext(
            KeyWriteOperations keyWriteDelegate,
            SchemaReadOperations schemaReadDelegate,
            SchemaWriteOperations schemaWriteDelegate )
    {
        this.keyWriteDelegate = keyWriteDelegate;
        this.schemaReadDelegate = schemaReadDelegate;
        this.schemaWriteDelegate = schemaWriteDelegate;
    }
    
    @Override
    public long propertyKeyGetOrCreateForName( String propertyKey ) throws SchemaKernelException
    {
        // KISS - but refactor into a general purpose constraint checker later on
        return keyWriteDelegate.propertyKeyGetOrCreateForName( checkValidTokenName( propertyKey ) );
    }

    private String checkValidTokenName( String name ) throws IllegalTokenNameException
    {
        if ( name == null || name.isEmpty() )
        {
            throw new IllegalTokenNameException( name );
        }
        return name;
    }

    @Override
    public long labelGetOrCreateForName( String label ) throws SchemaKernelException
    {
        // KISS - but refactor into a general purpose constraint checker later on
        return keyWriteDelegate.labelGetOrCreateForName( checkValidTokenName( label ) );
    }

    @Override
    public IndexDescriptor indexCreate( long labelId, long propertyKey ) throws SchemaKernelException
    {
        try
        {
            checkIndexExistence( labelId, propertyKey );
        }
        catch ( KernelException e )
        {
            throw new AddIndexFailureException(labelId, propertyKey, e);
        }
        return schemaWriteDelegate.indexCreate( labelId, propertyKey );
    }

    @Override
    public IndexDescriptor uniqueIndexCreate( long labelId, long propertyKey ) throws SchemaKernelException
    {
        try
        {
            checkIndexExistence( labelId, propertyKey );
        }
        catch ( KernelException e )
        {
            throw new AddIndexFailureException(labelId, propertyKey, e);
        }
        return schemaWriteDelegate.uniqueIndexCreate( labelId, propertyKey );
    }

    private void checkIndexExistence( long labelId, long propertyKey ) throws SchemaKernelException
    {
        for ( IndexDescriptor descriptor : loop( schemaReadDelegate.indexesGetForLabel( labelId ) ) )
        {
            if ( descriptor.getPropertyKeyId() == propertyKey )
            {
                throw new AlreadyIndexedException( descriptor );
            }
        }
        for ( IndexDescriptor descriptor : loop( schemaReadDelegate.uniqueIndexesGetForLabel( labelId ) ) )
        {
            if ( descriptor.getPropertyKeyId() == propertyKey )
            {
                throw new AlreadyConstrainedException(
                        new UniquenessConstraint( descriptor.getLabelId(), descriptor.getPropertyKeyId() ) );
            }
        }
    }

    @Override
    public void indexDrop( IndexDescriptor descriptor ) throws DropIndexFailureException
    {
        try
        {
            assertIsNotUniqueIndex( descriptor, schemaReadDelegate.uniqueIndexesGetForLabel(
                    descriptor.getLabelId() ) );
            assertIndexExists( descriptor, schemaReadDelegate.indexesGetForLabel( descriptor.getLabelId() ) );
        }
        catch ( SchemaKernelException e )
        {
            throw new DropIndexFailureException( descriptor, e );
        }
        schemaWriteDelegate.indexDrop( descriptor );
    }

    private void assertIsNotUniqueIndex( IndexDescriptor descriptor, Iterator<IndexDescriptor> uniqueIndexes )
            throws IndexBelongsToConstraintException

    {
        while ( uniqueIndexes.hasNext() )
        {
            IndexDescriptor uniqueIndex = uniqueIndexes.next();
            if ( uniqueIndex.getPropertyKeyId() == descriptor.getPropertyKeyId() )
            {
                throw new IndexBelongsToConstraintException( descriptor );
            }
        }
    }

    @Override
    public void uniqueIndexDrop( IndexDescriptor descriptor ) throws DropIndexFailureException
    {
        try
        {
            assertIndexExists( descriptor, schemaReadDelegate.uniqueIndexesGetForLabel( descriptor.getLabelId() ) );
        }
        catch ( NoSuchIndexException e )
        {
            throw new DropIndexFailureException( descriptor, e );
        }
        schemaWriteDelegate.uniqueIndexDrop( descriptor );
    }

    @Override
    public UniquenessConstraint uniquenessConstraintCreate( long labelId, long propertyKey )
            throws SchemaKernelException
    {
        Iterator<UniquenessConstraint> constraints = schemaReadDelegate.constraintsGetForLabelAndPropertyKey(
                labelId, propertyKey );
        if ( constraints.hasNext() )
        {
            throw new AlreadyConstrainedException( constraints.next() );
        }
        return schemaWriteDelegate.uniquenessConstraintCreate( labelId, propertyKey );
    }

    private void assertIndexExists( IndexDescriptor descriptor, Iterator<IndexDescriptor> indexes )
            throws NoSuchIndexException
    {
        for ( IndexDescriptor existing : loop( indexes ) )
        {
            if ( existing.getPropertyKeyId() == descriptor.getPropertyKeyId() )
            {
                return;
            }
        }
        throw new NoSuchIndexException( descriptor );
    }
    
    // === TODO Below is unnecessary delegate methods

    @Override
    public void constraintDrop( UniquenessConstraint constraint )
    {
        schemaWriteDelegate.constraintDrop( constraint );
    }
}