/*
 * Copyright 2019 Telefonaktiebolaget LM Ericsson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ericsson.bss.cassandra.ecaudit.entry.suppressor;

import java.nio.ByteBuffer;
import java.util.Optional;

import org.apache.cassandra.cql3.CQL3Type;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.CollectionType;
import org.apache.cassandra.db.marshal.ListType;
import org.apache.cassandra.db.marshal.MapType;
import org.apache.cassandra.db.marshal.SetType;
import org.apache.cassandra.db.marshal.TupleType;

public class SuppressBlobs extends AbstractSuppressor
{
    @Override
    public Optional<String> suppress(ColumnSpecification column, ByteBuffer value)
    {
        return containsBlob(column.type)
               ? Optional.of(suppressWithType(column))
               : Optional.empty();
    }

    private boolean containsBlob(AbstractType<?> type)
    {
        if (type.asCQL3Type() instanceof CQL3Type.Native)
        {
            return type.asCQL3Type().equals(CQL3Type.Native.BLOB);
        }
        if (type instanceof CollectionType)
        {
            return collectionContainsBlob((CollectionType) type);
        }
        if (type instanceof TupleType) // UDT is subtype of TupleTyp
        {
            return tupleContainsBlob((TupleType) type);
        }
        return true;
    }

    private boolean collectionContainsBlob(CollectionType type)
    {
        switch(type.kind)
        {
            case LIST:
                return listContainsBlob((ListType) type);
            case SET:
                return setContainsBlob((SetType) type);
            case MAP:
                return mapContainsBlob((MapType) type);
        }
        throw new IllegalArgumentException("Invalid collection type: " + type.kind);
    }

    private boolean listContainsBlob(ListType type)
    {
        return containsBlob(type.getElementsType());
    }

    @SuppressWarnings("PMD.LinguisticNaming")
    private boolean setContainsBlob(SetType type)
    {
        return containsBlob(type.getElementsType());
    }

    private boolean mapContainsBlob(MapType type)
    {
        AbstractType keysType = type.getKeysType();
        AbstractType valuesType = type.getValuesType();
        return containsBlob(keysType) || containsBlob(valuesType);
    }

    private boolean tupleContainsBlob(TupleType type)
    {
        return type.allTypes()
                   .stream()
                   .anyMatch(this::containsBlob);
    }
}
