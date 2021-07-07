/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.tables;


import eu.fasten.core.data.metadatadb.codegen.Indexes;
import eu.fasten.core.data.metadatadb.codegen.Keys;
import eu.fasten.core.data.metadatadb.codegen.Public;
import eu.fasten.core.data.metadatadb.codegen.tables.records.BinaryModuleContentsRecord;

import java.util.Arrays;
import java.util.List;

import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row2;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.12.3"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class BinaryModuleContents extends TableImpl<BinaryModuleContentsRecord> {

    private static final long serialVersionUID = 231689836;

    /**
     * The reference instance of <code>public.binary_module_contents</code>
     */
    public static final BinaryModuleContents BINARY_MODULE_CONTENTS = new BinaryModuleContents();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<BinaryModuleContentsRecord> getRecordType() {
        return BinaryModuleContentsRecord.class;
    }

    /**
     * The column <code>public.binary_module_contents.binary_module_id</code>.
     */
    public final TableField<BinaryModuleContentsRecord, Long> BINARY_MODULE_ID = createField(DSL.name("binary_module_id"), org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>public.binary_module_contents.file_id</code>.
     */
    public final TableField<BinaryModuleContentsRecord, Long> FILE_ID = createField(DSL.name("file_id"), org.jooq.impl.SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * Create a <code>public.binary_module_contents</code> table reference
     */
    public BinaryModuleContents() {
        this(DSL.name("binary_module_contents"), null);
    }

    /**
     * Create an aliased <code>public.binary_module_contents</code> table reference
     */
    public BinaryModuleContents(String alias) {
        this(DSL.name(alias), BINARY_MODULE_CONTENTS);
    }

    /**
     * Create an aliased <code>public.binary_module_contents</code> table reference
     */
    public BinaryModuleContents(Name alias) {
        this(alias, BINARY_MODULE_CONTENTS);
    }

    private BinaryModuleContents(Name alias, Table<BinaryModuleContentsRecord> aliased) {
        this(alias, aliased, null);
    }

    private BinaryModuleContents(Name alias, Table<BinaryModuleContentsRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> BinaryModuleContents(Table<O> child, ForeignKey<O, BinaryModuleContentsRecord> key) {
        super(child, key, BINARY_MODULE_CONTENTS);
    }

    @Override
    public Schema getSchema() {
        return Public.PUBLIC;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.UNIQUE_BINARY_MODULE_FILE);
    }

    @Override
    public List<UniqueKey<BinaryModuleContentsRecord>> getKeys() {
        return Arrays.<UniqueKey<BinaryModuleContentsRecord>>asList(Keys.UNIQUE_BINARY_MODULE_FILE);
    }

    @Override
    public List<ForeignKey<BinaryModuleContentsRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<BinaryModuleContentsRecord, ?>>asList(Keys.BINARY_MODULE_CONTENTS__BINARY_MODULE_CONTENTS_BINARY_MODULE_ID_FKEY, Keys.BINARY_MODULE_CONTENTS__BINARY_MODULE_CONTENTS_FILE_ID_FKEY);
    }

    public BinaryModules binaryModules() {
        return new BinaryModules(this, Keys.BINARY_MODULE_CONTENTS__BINARY_MODULE_CONTENTS_BINARY_MODULE_ID_FKEY);
    }

    public Files files() {
        return new Files(this, Keys.BINARY_MODULE_CONTENTS__BINARY_MODULE_CONTENTS_FILE_ID_FKEY);
    }

    @Override
    public BinaryModuleContents as(String alias) {
        return new BinaryModuleContents(DSL.name(alias), this);
    }

    @Override
    public BinaryModuleContents as(Name alias) {
        return new BinaryModuleContents(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public BinaryModuleContents rename(String name) {
        return new BinaryModuleContents(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public BinaryModuleContents rename(Name name) {
        return new BinaryModuleContents(name, null);
    }

    // -------------------------------------------------------------------------
    // Row2 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row2<Long, Long> fieldsRow() {
        return (Row2) super.fieldsRow();
    }
}
