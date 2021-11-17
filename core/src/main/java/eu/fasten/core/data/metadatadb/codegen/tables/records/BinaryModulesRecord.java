/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.tables.records;


import eu.fasten.core.data.metadatadb.codegen.tables.BinaryModules;

import java.sql.Timestamp;

import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record1;
import org.jooq.Record5;
import org.jooq.Row5;
import org.jooq.impl.UpdatableRecordImpl;


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
public class BinaryModulesRecord extends UpdatableRecordImpl<BinaryModulesRecord> implements Record5<Long, Long, String, Timestamp, JSONB> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>public.binary_modules.id</code>.
     */
    public void setId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>public.binary_modules.id</code>.
     */
    public Long getId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>public.binary_modules.package_version_id</code>.
     */
    public void setPackageVersionId(Long value) {
        set(1, value);
    }

    /**
     * Getter for <code>public.binary_modules.package_version_id</code>.
     */
    public Long getPackageVersionId() {
        return (Long) get(1);
    }

    /**
     * Setter for <code>public.binary_modules.name</code>.
     */
    public void setName(String value) {
        set(2, value);
    }

    /**
     * Getter for <code>public.binary_modules.name</code>.
     */
    public String getName() {
        return (String) get(2);
    }

    /**
     * Setter for <code>public.binary_modules.created_at</code>.
     */
    public void setCreatedAt(Timestamp value) {
        set(3, value);
    }

    /**
     * Getter for <code>public.binary_modules.created_at</code>.
     */
    public Timestamp getCreatedAt() {
        return (Timestamp) get(3);
    }

    /**
     * Setter for <code>public.binary_modules.metadata</code>.
     */
    public void setMetadata(JSONB value) {
        set(4, value);
    }

    /**
     * Getter for <code>public.binary_modules.metadata</code>.
     */
    public JSONB getMetadata() {
        return (JSONB) get(4);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Long> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record5 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row5<Long, Long, String, Timestamp, JSONB> fieldsRow() {
        return (Row5) super.fieldsRow();
    }

    @Override
    public Row5<Long, Long, String, Timestamp, JSONB> valuesRow() {
        return (Row5) super.valuesRow();
    }

    @Override
    public Field<Long> field1() {
        return BinaryModules.BINARY_MODULES.ID;
    }

    @Override
    public Field<Long> field2() {
        return BinaryModules.BINARY_MODULES.PACKAGE_VERSION_ID;
    }

    @Override
    public Field<String> field3() {
        return BinaryModules.BINARY_MODULES.NAME;
    }

    @Override
    public Field<Timestamp> field4() {
        return BinaryModules.BINARY_MODULES.CREATED_AT;
    }

    @Override
    public Field<JSONB> field5() {
        return BinaryModules.BINARY_MODULES.METADATA;
    }

    @Override
    public Long component1() {
        return getId();
    }

    @Override
    public Long component2() {
        return getPackageVersionId();
    }

    @Override
    public String component3() {
        return getName();
    }

    @Override
    public Timestamp component4() {
        return getCreatedAt();
    }

    @Override
    public JSONB component5() {
        return getMetadata();
    }

    @Override
    public Long value1() {
        return getId();
    }

    @Override
    public Long value2() {
        return getPackageVersionId();
    }

    @Override
    public String value3() {
        return getName();
    }

    @Override
    public Timestamp value4() {
        return getCreatedAt();
    }

    @Override
    public JSONB value5() {
        return getMetadata();
    }

    @Override
    public BinaryModulesRecord value1(Long value) {
        setId(value);
        return this;
    }

    @Override
    public BinaryModulesRecord value2(Long value) {
        setPackageVersionId(value);
        return this;
    }

    @Override
    public BinaryModulesRecord value3(String value) {
        setName(value);
        return this;
    }

    @Override
    public BinaryModulesRecord value4(Timestamp value) {
        setCreatedAt(value);
        return this;
    }

    @Override
    public BinaryModulesRecord value5(JSONB value) {
        setMetadata(value);
        return this;
    }

    @Override
    public BinaryModulesRecord values(Long value1, Long value2, String value3, Timestamp value4, JSONB value5) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached BinaryModulesRecord
     */
    public BinaryModulesRecord() {
        super(BinaryModules.BINARY_MODULES);
    }

    /**
     * Create a detached, initialised BinaryModulesRecord
     */
    public BinaryModulesRecord(Long id, Long packageVersionId, String name, Timestamp createdAt, JSONB metadata) {
        super(BinaryModules.BINARY_MODULES);

        setId(id);
        setPackageVersionId(packageVersionId);
        setName(name);
        setCreatedAt(createdAt);
        setMetadata(metadata);
    }
}
