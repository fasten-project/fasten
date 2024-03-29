/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.tables;


import eu.fasten.core.data.metadatadb.codegen.Indexes;
import eu.fasten.core.data.metadatadb.codegen.Keys;
import eu.fasten.core.data.metadatadb.codegen.Public;
import eu.fasten.core.data.metadatadb.codegen.tables.records.VulnerabilitiesXCallablesRecord;

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
        "jOOQ version:3.16.21"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class VulnerabilitiesXCallables extends TableImpl<VulnerabilitiesXCallablesRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>public.vulnerabilities_x_callables</code>
     */
    public static final VulnerabilitiesXCallables VULNERABILITIES_X_CALLABLES = new VulnerabilitiesXCallables();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<VulnerabilitiesXCallablesRecord> getRecordType() {
        return VulnerabilitiesXCallablesRecord.class;
    }

    /**
     * The column
     * <code>public.vulnerabilities_x_callables.vulnerability_id</code>.
     */
    public final TableField<VulnerabilitiesXCallablesRecord, Long> VULNERABILITY_ID = createField(DSL.name("vulnerability_id"), SQLDataType.BIGINT.nullable(false), this, "");

    /**
     * The column <code>public.vulnerabilities_x_callables.callable_id</code>.
     */
    public final TableField<VulnerabilitiesXCallablesRecord, Long> CALLABLE_ID = createField(DSL.name("callable_id"), SQLDataType.BIGINT.nullable(false), this, "");

    private VulnerabilitiesXCallables(Name alias, Table<VulnerabilitiesXCallablesRecord> aliased) {
        this(alias, aliased, null);
    }

    private VulnerabilitiesXCallables(Name alias, Table<VulnerabilitiesXCallablesRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>public.vulnerabilities_x_callables</code> table
     * reference
     */
    public VulnerabilitiesXCallables(String alias) {
        this(DSL.name(alias), VULNERABILITIES_X_CALLABLES);
    }

    /**
     * Create an aliased <code>public.vulnerabilities_x_callables</code> table
     * reference
     */
    public VulnerabilitiesXCallables(Name alias) {
        this(alias, VULNERABILITIES_X_CALLABLES);
    }

    /**
     * Create a <code>public.vulnerabilities_x_callables</code> table reference
     */
    public VulnerabilitiesXCallables() {
        this(DSL.name("vulnerabilities_x_callables"), null);
    }

    public <O extends Record> VulnerabilitiesXCallables(Table<O> child, ForeignKey<O, VulnerabilitiesXCallablesRecord> key) {
        super(child, key, VULNERABILITIES_X_CALLABLES);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Public.PUBLIC;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.VULNERABILITIES_X_CALLABLES_CALLABLE_ID, Indexes.VULNERABILITIES_X_CALLABLES_VULNERABILITY_ID);
    }

    @Override
    public List<UniqueKey<VulnerabilitiesXCallablesRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.UNIQUE_VULN_X_CALLABLE);
    }

    @Override
    public List<ForeignKey<VulnerabilitiesXCallablesRecord, ?>> getReferences() {
        return Arrays.asList(Keys.VULNERABILITIES_X_CALLABLES__VULNERABILITIES_X_CALLABLES_VULNERABILITY_ID_FKEY, Keys.VULNERABILITIES_X_CALLABLES__VULNERABILITIES_X_CALLABLES_CALLABLE_ID_FKEY);
    }

    private transient Vulnerabilities _vulnerabilities;
    private transient Callables _callables;

    /**
     * Get the implicit join path to the <code>public.vulnerabilities</code>
     * table.
     */
    public Vulnerabilities vulnerabilities() {
        if (_vulnerabilities == null)
            _vulnerabilities = new Vulnerabilities(this, Keys.VULNERABILITIES_X_CALLABLES__VULNERABILITIES_X_CALLABLES_VULNERABILITY_ID_FKEY);

        return _vulnerabilities;
    }

    /**
     * Get the implicit join path to the <code>public.callables</code> table.
     */
    public Callables callables() {
        if (_callables == null)
            _callables = new Callables(this, Keys.VULNERABILITIES_X_CALLABLES__VULNERABILITIES_X_CALLABLES_CALLABLE_ID_FKEY);

        return _callables;
    }

    @Override
    public VulnerabilitiesXCallables as(String alias) {
        return new VulnerabilitiesXCallables(DSL.name(alias), this);
    }

    @Override
    public VulnerabilitiesXCallables as(Name alias) {
        return new VulnerabilitiesXCallables(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public VulnerabilitiesXCallables rename(String name) {
        return new VulnerabilitiesXCallables(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public VulnerabilitiesXCallables rename(Name name) {
        return new VulnerabilitiesXCallables(name, null);
    }

    // -------------------------------------------------------------------------
    // Row2 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row2<Long, Long> fieldsRow() {
        return (Row2) super.fieldsRow();
    }
}
