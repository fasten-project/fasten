/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.tables.records;


import eu.fasten.core.data.metadatadb.codegen.tables.IngestedArtifacts;

import java.sql.Timestamp;

import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record4;
import org.jooq.Row4;
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
public class IngestedArtifactsRecord extends UpdatableRecordImpl<IngestedArtifactsRecord> implements Record4<Long, String, String, Timestamp> {

    private static final long serialVersionUID = 1002408049;

    /**
     * Setter for <code>public.ingested_artifacts.id</code>.
     */
    public void setId(Long value) {
        set(0, value);
    }

    /**
     * Getter for <code>public.ingested_artifacts.id</code>.
     */
    public Long getId() {
        return (Long) get(0);
    }

    /**
     * Setter for <code>public.ingested_artifacts.package_name</code>.
     */
    public void setPackageName(String value) {
        set(1, value);
    }

    /**
     * Getter for <code>public.ingested_artifacts.package_name</code>.
     */
    public String getPackageName() {
        return (String) get(1);
    }

    /**
     * Setter for <code>public.ingested_artifacts.version</code>.
     */
    public void setVersion(String value) {
        set(2, value);
    }

    /**
     * Getter for <code>public.ingested_artifacts.version</code>.
     */
    public String getVersion() {
        return (String) get(2);
    }

    /**
     * Setter for <code>public.ingested_artifacts.timestamp</code>.
     */
    public void setTimestamp(Timestamp value) {
        set(3, value);
    }

    /**
     * Getter for <code>public.ingested_artifacts.timestamp</code>.
     */
    public Timestamp getTimestamp() {
        return (Timestamp) get(3);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Long> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record4 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row4<Long, String, String, Timestamp> fieldsRow() {
        return (Row4) super.fieldsRow();
    }

    @Override
    public Row4<Long, String, String, Timestamp> valuesRow() {
        return (Row4) super.valuesRow();
    }

    @Override
    public Field<Long> field1() {
        return IngestedArtifacts.INGESTED_ARTIFACTS.ID;
    }

    @Override
    public Field<String> field2() {
        return IngestedArtifacts.INGESTED_ARTIFACTS.PACKAGE_NAME;
    }

    @Override
    public Field<String> field3() {
        return IngestedArtifacts.INGESTED_ARTIFACTS.VERSION;
    }

    @Override
    public Field<Timestamp> field4() {
        return IngestedArtifacts.INGESTED_ARTIFACTS.TIMESTAMP;
    }

    @Override
    public Long component1() {
        return getId();
    }

    @Override
    public String component2() {
        return getPackageName();
    }

    @Override
    public String component3() {
        return getVersion();
    }

    @Override
    public Timestamp component4() {
        return getTimestamp();
    }

    @Override
    public Long value1() {
        return getId();
    }

    @Override
    public String value2() {
        return getPackageName();
    }

    @Override
    public String value3() {
        return getVersion();
    }

    @Override
    public Timestamp value4() {
        return getTimestamp();
    }

    @Override
    public IngestedArtifactsRecord value1(Long value) {
        setId(value);
        return this;
    }

    @Override
    public IngestedArtifactsRecord value2(String value) {
        setPackageName(value);
        return this;
    }

    @Override
    public IngestedArtifactsRecord value3(String value) {
        setVersion(value);
        return this;
    }

    @Override
    public IngestedArtifactsRecord value4(Timestamp value) {
        setTimestamp(value);
        return this;
    }

    @Override
    public IngestedArtifactsRecord values(Long value1, String value2, String value3, Timestamp value4) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached IngestedArtifactsRecord
     */
    public IngestedArtifactsRecord() {
        super(IngestedArtifacts.INGESTED_ARTIFACTS);
    }

    /**
     * Create a detached, initialised IngestedArtifactsRecord
     */
    public IngestedArtifactsRecord(Long id, String packageName, String version, Timestamp timestamp) {
        super(IngestedArtifacts.INGESTED_ARTIFACTS);

        set(0, id);
        set(1, packageName);
        set(2, version);
        set(3, timestamp);
    }
}
