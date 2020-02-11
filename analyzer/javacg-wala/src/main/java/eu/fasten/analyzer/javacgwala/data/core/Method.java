package eu.fasten.analyzer.javacgwala.data.core;

import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeName;
import eu.fasten.core.data.FastenJavaURI;
import eu.fasten.core.data.FastenURI;

import java.util.Objects;

public abstract class Method {

    String namespace;
    Selector symbol;

    /**
     * Construct a method given its reference.
     *
     */
    public Method(String namespace, Selector symbol) {
        this.namespace = namespace;
        this.symbol = symbol;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Selector getSymbol() {
        return symbol;
    }

    public void setSymbol(Selector symbol) {
        this.symbol = symbol;
    }

    /**
     * Convert {@link Method} to ID representation.
     *
     * @return Method ID
     */
    public abstract String toID();

    /**
     * Convert {@link FastenJavaURI} to {@link FastenURI}.
     *
     * @return {@link FastenURI}
     */
    public FastenURI toCanonicalSchemalessURI() {

        FastenJavaURI javaURI = FastenJavaURI.create(getMethodInfo());

        return FastenURI.createSchemeless(javaURI.getRawForge(), javaURI.getRawProduct(),
                javaURI.getRawVersion(),
                javaURI.getRawNamespace(), javaURI.getRawEntity());
    }

    /**
     * Creates a URI representation for method's namespace, typeName, functionName, arguments list,
     * and return type.
     *
     * @return URI representation of a method
     */
    private String getMethodInfo() {
        String namespace = getPackageName();
        String typeName = getClassName();
        String methodName = getMethodName();
        String parameters = getParameters();
        String returnType = FastenJavaURI.pctEncodeArg(getReturnType());
        return "/" + namespace + "/" + typeName + "." + methodName + "("
                + parameters + ")" + returnType;
    }

    /**
     * Get name of the package.
     *
     * @return Package name
     */
    private String getPackageName() {
        return namespace.substring(0, this.namespace.lastIndexOf("."));
    }

    /**
     * Get name of the class.
     *
     * @return Class name
     */
    private String getClassName() {
        return namespace.substring(namespace.lastIndexOf(".") + 1);
    }

    /**
     * Get name of the method. Resolve < init > and < clinit > cases.
     *
     * @return Method name
     */
    private String getMethodName() {
        if (symbol.getName().toString().equals("<init>")) {

            if (getClassName().contains("Lambda")) {
                return FastenJavaURI.pctEncodeArg(getClassName());
            } else {
                return getClassName();
            }

        } else if (symbol.getName().toString().equals("<clinit>")) {
            return FastenJavaURI.pctEncodeArg("<init>");
        } else {
            return symbol.getName().toString();
        }
    }

    /**
     * Get  String representation of parameters.
     *
     * @return - Parameters
     */
    private String getParameters() {
        TypeName[] args = this.symbol.getDescriptor().getParameters();
        String argTypes = "";
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                argTypes = i == args.length - 1 ? FastenJavaURI.pctEncodeArg(getType(args[i]))
                        : FastenJavaURI.pctEncodeArg(getType(args[i])) + ",";
            }
        }
        return argTypes;
    }

    /**
     * Get return type of a method.
     *
     * @return Return type
     */
    private String getReturnType() {
        var type = getType(symbol.getDescriptor().getReturnType());
        var elements = type.split("/");

        if (elements[2].equals("V")) {
            return "/java.lang/Void";
        }
        return type;
    }

    /**
     * Getter for the type of a method.
     *
     * @param type TypeName to extract name from
     * @return Method type
     */
    private static String getType(TypeName type) {
        if (type == null) {
            return "";
        }
        if (type.getClassName() == null) {
            return "";
        }
        var packageName = type.getPackage() == null ? "" : type.getPackage().toString()
                .replace("/", ".");
        var classname = type.getClassName().toString();

        if (type.isArrayType()) {
            classname = classname.concat(twoTimesPct("[]"));
        }

        return "/" + packageName + "/" + classname;
    }

    /**
     * Perform encoding 3 times.
     *
     * @param nonEncoded String to encode
     * @return Encoded string
     */
    private static String twoTimesPct(String nonEncoded) {
        return FastenJavaURI.pctEncodeArg(FastenJavaURI
                .pctEncodeArg(nonEncoded));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Method method = (Method) o;
        return Objects.equals(namespace, method.namespace) &&
                Objects.equals(symbol, method.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, symbol);
    }
}
