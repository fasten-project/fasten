package eu.fasten.analyzer.data.type;

import com.ibm.wala.types.TypeReference;

public enum JavaPrimitive implements Namespace {
    BOOLEAN, BYTE, CHAR, DOUBLE,
    FLOAT, INT, LONG, SHORT,
    VOID, UNKNOWN;

    public static JavaPrimitive of(TypeReference tyref) {
        String s = tyref.getName().toString();
        assert (tyref.isPrimitiveType());
        assert (s.length() > 0);
        switch (s.charAt(0)) {
            case TypeReference.BooleanTypeCode:
                return BOOLEAN;
            case TypeReference.ByteTypeCode:
                return BYTE;
            case TypeReference.CharTypeCode:
                return CHAR;
            case TypeReference.DoubleTypeCode:
                return DOUBLE;
            case TypeReference.FloatTypeCode:
                return FLOAT;
            case TypeReference.IntTypeCode:
                return INT;
            case TypeReference.LongTypeCode:
                return LONG;
            case TypeReference.ShortTypeCode:
                return SHORT;
            case TypeReference.VoidTypeCode:
                return VOID;
            default:
                return UNKNOWN;
        }
    }

    @Override
    public String[] getSegments() {
        return new String[]{"java", "primitive", this.name().toLowerCase()};
    }

    @Override
    public String getNamespaceDelim() {
        return ".";
    }
}


