/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen;


import javax.annotation.processing.Generated;

import org.jooq.Sequence;
import org.jooq.impl.Internal;


/**
 * Convenience access to all sequences in public
 */
@Generated(
    value = {
        "https://www.jooq.org",
        "jOOQ version:3.13.6"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Sequences {

    /**
     * The sequence <code>public.artifact_repositories_id_seq</code>
     */
    public static final Sequence<Long> ARTIFACT_REPOSITORIES_ID_SEQ = Internal.createSequence("artifact_repositories_id_seq", Public.PUBLIC, org.jooq.impl.SQLDataType.BIGINT.nullable(false), null, null, null, null, false, null);

    /**
     * The sequence <code>public.binary_modules_id_seq</code>
     */
    public static final Sequence<Long> BINARY_MODULES_ID_SEQ = Internal.createSequence("binary_modules_id_seq", Public.PUBLIC, org.jooq.impl.SQLDataType.BIGINT.nullable(false), null, null, null, null, false, null);

    /**
     * The sequence <code>public.callables_id_seq</code>
     */
    public static final Sequence<Long> CALLABLES_ID_SEQ = Internal.createSequence("callables_id_seq", Public.PUBLIC, org.jooq.impl.SQLDataType.BIGINT.nullable(false), null, null, null, null, false, null);

    /**
     * The sequence <code>public.files_id_seq</code>
     */
    public static final Sequence<Long> FILES_ID_SEQ = Internal.createSequence("files_id_seq", Public.PUBLIC, org.jooq.impl.SQLDataType.BIGINT.nullable(false), null, null, null, null, false, null);

    /**
     * The sequence <code>public.ingested_artifacts_id_seq</code>
     */
    public static final Sequence<Long> INGESTED_ARTIFACTS_ID_SEQ = Internal.createSequence("ingested_artifacts_id_seq", Public.PUBLIC, org.jooq.impl.SQLDataType.BIGINT.nullable(false), null, null, null, null, false, null);

    /**
     * The sequence <code>public.module_names_id_seq</code>
     */
    public static final Sequence<Long> MODULE_NAMES_ID_SEQ = Internal.createSequence("module_names_id_seq", Public.PUBLIC, org.jooq.impl.SQLDataType.BIGINT.nullable(false), null, null, null, null, false, null);

    /**
     * The sequence <code>public.modules_id_seq</code>
     */
    public static final Sequence<Long> MODULES_ID_SEQ = Internal.createSequence("modules_id_seq", Public.PUBLIC, org.jooq.impl.SQLDataType.BIGINT.nullable(false), null, null, null, null, false, null);

    /**
     * The sequence <code>public.package_versions_id_seq</code>
     */
    public static final Sequence<Long> PACKAGE_VERSIONS_ID_SEQ = Internal.createSequence("package_versions_id_seq", Public.PUBLIC, org.jooq.impl.SQLDataType.BIGINT.nullable(false), null, null, null, null, false, null);

    /**
     * The sequence <code>public.packages_id_seq</code>
     */
    public static final Sequence<Long> PACKAGES_ID_SEQ = Internal.createSequence("packages_id_seq", Public.PUBLIC, org.jooq.impl.SQLDataType.BIGINT.nullable(false), null, null, null, null, false, null);

    /**
     * The sequence <code>public.vulnerabilities_id_seq</code>
     */
    public static final Sequence<Long> VULNERABILITIES_ID_SEQ = Internal.createSequence("vulnerabilities_id_seq", Public.PUBLIC, org.jooq.impl.SQLDataType.BIGINT.nullable(false), null, null, null, null, false, null);
}
