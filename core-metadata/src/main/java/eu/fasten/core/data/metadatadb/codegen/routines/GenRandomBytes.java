/*
 * This file is generated by jOOQ.
 */
package eu.fasten.core.data.metadatadb.codegen.routines;


import eu.fasten.core.data.metadatadb.codegen.Public;

import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.Parameter;
import org.jooq.impl.AbstractRoutine;
import org.jooq.impl.Internal;
import org.jooq.impl.SQLDataType;


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
public class GenRandomBytes extends AbstractRoutine<byte[]> {

    private static final long serialVersionUID = 1L;

    /**
     * The parameter <code>public.gen_random_bytes.RETURN_VALUE</code>.
     */
    public static final Parameter<byte[]> RETURN_VALUE = Internal.createParameter("RETURN_VALUE", SQLDataType.BLOB, false, false);

    /**
     * The parameter <code>public.gen_random_bytes._1</code>.
     */
    public static final Parameter<Integer> _1 = Internal.createParameter("_1", SQLDataType.INTEGER, false, true);

    /**
     * Create a new routine call instance
     */
    public GenRandomBytes() {
        super("gen_random_bytes", Public.PUBLIC, SQLDataType.BLOB);

        setReturnParameter(RETURN_VALUE);
        addInParameter(_1);
    }

    /**
     * Set the <code>_1</code> parameter IN value to the routine
     */
    public void set__1(Integer value) {
        setValue(_1, value);
    }

    /**
     * Set the <code>_1</code> parameter to the function to be used with a
     * {@link org.jooq.Select} statement
     */
    public void set__1(Field<Integer> field) {
        setField(_1, field);
    }
}
