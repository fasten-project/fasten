/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.enums;


import eu.fasten.core.data.metadatadb.codegen.Public;

import javax.annotation.processing.Generated;

import org.jooq.Catalog;
import org.jooq.EnumType;
import org.jooq.Schema;


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
public enum CallableType implements EnumType {

    internalBinary("internalBinary"),

    externalProduct("externalProduct"),

    externalStaticFunction("externalStaticFunction"),

    externalUndefined("externalUndefined"),

    internalStaticFunction("internalStaticFunction");

    private final String literal;

    private CallableType(String literal) {
        this.literal = literal;
    }

    @Override
    public Catalog getCatalog() {
        return getSchema() == null ? null : getSchema().getCatalog();
    }

    @Override
    public Schema getSchema() {
        return Public.PUBLIC;
    }

    @Override
    public String getName() {
        return "callable_type";
    }

    @Override
    public String getLiteral() {
        return literal;
    }
}
