/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.tables.records;


import eu.fasten.core.data.metadatadb.codegen.tables.Vulnerabilities;

import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Row2;
import org.jooq.impl.UpdatableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "https://www.jooq.org",
        "jOOQ version:3.13.6"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class VulnerabilitiesRecord extends UpdatableRecordImpl<VulnerabilitiesRecord> implements Record2<Long, JSONB> {

    private static final long serialVersionUID = -502893581;

    /**
     * Setter for <code>public.vulnerabilities.id</code>.
     */
    public void setId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>public.vulnerabilities.id</code>.
     */
    public Long getId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>public.vulnerabilities.statement</code>.
     */
    public void setStatement(JSONB value) {
        set(1, value);
    }

    /**
     * Getter for <code>public.vulnerabilities.statement</code>.
     */
    public JSONB getStatement() {
        return (JSONB) get(1);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Long> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record2 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row2<Long, JSONB> fieldsRow() {
        return (Row2) super.fieldsRow();
    }

    @Override
    public Row2<Long, JSONB> valuesRow() {
        return (Row2) super.valuesRow();
    }

    @Override
    public Field<Long> field1() {
        return Vulnerabilities.VULNERABILITIES.ID;
    }

    @Override
    public Field<JSONB> field2() {
        return Vulnerabilities.VULNERABILITIES.STATEMENT;
    }

    @Override
    public Long component1() {
        return getId();
    }

    @Override
    public JSONB component2() {
        return getStatement();
    }

    @Override
    public Long value1() {
        return getId();
    }

    @Override
    public JSONB value2() {
        return getStatement();
    }

    @Override
    public VulnerabilitiesRecord value1(Long value) {
        setId(value);
        return this;
    }

    @Override
    public VulnerabilitiesRecord value2(JSONB value) {
        setStatement(value);
        return this;
    }

    @Override
    public VulnerabilitiesRecord values(Long value1, JSONB value2) {
        value1(value1);
        value2(value2);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached VulnerabilitiesRecord
     */
    public VulnerabilitiesRecord() {
        super(Vulnerabilities.VULNERABILITIES);
    }

    /**
     * Create a detached, initialised VulnerabilitiesRecord
     */
    public VulnerabilitiesRecord(Long id, JSONB statement) {
        super(Vulnerabilities.VULNERABILITIES);

        set(0, id);
        set(1, statement);
    }
}
