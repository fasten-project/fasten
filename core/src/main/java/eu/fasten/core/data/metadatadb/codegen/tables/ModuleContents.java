/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.tables;


import eu.fasten.core.data.metadatadb.codegen.Indexes;
import eu.fasten.core.data.metadatadb.codegen.Keys;
import eu.fasten.core.data.metadatadb.codegen.Public;
import eu.fasten.core.data.metadatadb.codegen.tables.records.ModuleContentsRecord;

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
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "https://www.jooq.org",
        "jOOQ version:3.14.15"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class ModuleContents extends TableImpl<ModuleContentsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>public.module_contents</code>
     */
    public static final ModuleContents MODULE_CONTENTS = new ModuleContents();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<ModuleContentsRecord> getRecordType() {
        return ModuleContentsRecord.class;
    }

    /**
     * The column <code>public.module_contents.module_id</code>.
     */
    public final TableField<ModuleContentsRecord, Long> MODULE_ID = createField(DSL.name("module_id"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>public.module_contents.file_id</code>.
     */
    public final TableField<ModuleContentsRecord, Long> FILE_ID = createField(DSL.name("file_id"), SQLDataType.BIGINT.nullable(false), this, "");

    private ModuleContents(Name alias, Table<ModuleContentsRecord> aliased) {
        this(alias, aliased, null);
    }

    private ModuleContents(Name alias, Table<ModuleContentsRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>public.module_contents</code> table reference
     */
    public ModuleContents(String alias) {
        this(DSL.name(alias), MODULE_CONTENTS);
    }

    /**
     * Create an aliased <code>public.module_contents</code> table reference
     */
    public ModuleContents(Name alias) {
        this(alias, MODULE_CONTENTS);
    }

    /**
     * Create a <code>public.module_contents</code> table reference
     */
    public ModuleContents() {
        this(DSL.name("module_contents"), null);
    }

    public <O extends Record> ModuleContents(Table<O> child, ForeignKey<O, ModuleContentsRecord> key) {
        super(child, key, MODULE_CONTENTS);
    }

    @Override
    public Schema getSchema() {
        return Public.PUBLIC;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.MODULE_CONTENTS_FILE_ID, Indexes.MODULE_CONTENTS_MODULE_ID);
    }

    @Override
    public List<UniqueKey<ModuleContentsRecord>> getKeys() {
        return Arrays.<UniqueKey<ModuleContentsRecord>>asList(Keys.UNIQUE_MODULE_FILE);
    }

    @Override
    public List<ForeignKey<ModuleContentsRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<ModuleContentsRecord, ?>>asList(Keys.MODULE_CONTENTS__MODULE_CONTENTS_MODULE_ID_FKEY, Keys.MODULE_CONTENTS__MODULE_CONTENTS_FILE_ID_FKEY);
    }

    private transient Modules _modules;
    private transient Files _files;

    public Modules modules() {
        if (_modules == null)
            _modules = new Modules(this, Keys.MODULE_CONTENTS__MODULE_CONTENTS_MODULE_ID_FKEY);

        return _modules;
    }

    public Files files() {
        if (_files == null)
            _files = new Files(this, Keys.MODULE_CONTENTS__MODULE_CONTENTS_FILE_ID_FKEY);

        return _files;
    }

    @Override
    public ModuleContents as(String alias) {
        return new ModuleContents(DSL.name(alias), this);
    }

    @Override
    public ModuleContents as(Name alias) {
        return new ModuleContents(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public ModuleContents rename(String name) {
        return new ModuleContents(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public ModuleContents rename(Name name) {
        return new ModuleContents(name, null);
    }

    // -------------------------------------------------------------------------
    // Row2 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row2<Long, Long> fieldsRow() {
        return (Row2) super.fieldsRow();
    }
}
