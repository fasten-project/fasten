/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.tables.records;


import eu.fasten.core.data.metadatadb.codegen.tables.VirtualImplementations;

import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Row2;
import org.jooq.impl.TableRecordImpl;


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
public class VirtualImplementationsRecord extends TableRecordImpl<VirtualImplementationsRecord> implements Record2<Long, Long> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for
     * <code>public.virtual_implementations.virtual_package_version_id</code>.
     */
    public void setVirtualPackageVersionId(Long value) {
        set(0, value);
    }

    /**
     * Getter for
     * <code>public.virtual_implementations.virtual_package_version_id</code>.
     */
    public Long getVirtualPackageVersionId() {
        return (Long) get(0);
    }

    /**
     * Setter for
     * <code>public.virtual_implementations.package_version_id</code>.
     */
    public void setPackageVersionId(Long value) {
        set(1, value);
    }

    /**
     * Getter for
     * <code>public.virtual_implementations.package_version_id</code>.
     */
    public Long getPackageVersionId() {
        return (Long) get(1);
    }

    // -------------------------------------------------------------------------
    // Record2 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row2<Long, Long> fieldsRow() {
        return (Row2) super.fieldsRow();
    }

    @Override
    public Row2<Long, Long> valuesRow() {
        return (Row2) super.valuesRow();
    }

    @Override
    public Field<Long> field1() {
        return VirtualImplementations.VIRTUAL_IMPLEMENTATIONS.VIRTUAL_PACKAGE_VERSION_ID;
    }

    @Override
    public Field<Long> field2() {
        return VirtualImplementations.VIRTUAL_IMPLEMENTATIONS.PACKAGE_VERSION_ID;
    }

    @Override
    public Long component1() {
        return getVirtualPackageVersionId();
    }

    @Override
    public Long component2() {
        return getPackageVersionId();
    }

    @Override
    public Long value1() {
        return getVirtualPackageVersionId();
    }

    @Override
    public Long value2() {
        return getPackageVersionId();
    }

    @Override
    public VirtualImplementationsRecord value1(Long value) {
        setVirtualPackageVersionId(value);
        return this;
    }

    @Override
    public VirtualImplementationsRecord value2(Long value) {
        setPackageVersionId(value);
        return this;
    }

    @Override
    public VirtualImplementationsRecord values(Long value1, Long value2) {
        value1(value1);
        value2(value2);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached VirtualImplementationsRecord
     */
    public VirtualImplementationsRecord() {
        super(VirtualImplementations.VIRTUAL_IMPLEMENTATIONS);
    }

    /**
     * Create a detached, initialised VirtualImplementationsRecord
     */
    public VirtualImplementationsRecord(Long virtualPackageVersionId, Long packageVersionId) {
        super(VirtualImplementations.VIRTUAL_IMPLEMENTATIONS);

        setVirtualPackageVersionId(virtualPackageVersionId);
        setPackageVersionId(packageVersionId);
    }
}
