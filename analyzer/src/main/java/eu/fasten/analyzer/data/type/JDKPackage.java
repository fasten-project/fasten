package eu.fasten.analyzer.data.type;

import java.io.Serializable;
import java.util.Objects;

public final class JDKPackage implements Serializable, Namespace {

    public final String version = Runtime.class.getPackage().getImplementationVersion();
    public final String vendor = Runtime.class.getPackage().getImplementationVendor();

    private JDKPackage() {
    }

    public static JDKPackage getInstance() {
        return SingletonHelper.INSTANCE;
    }

    @Override
    public boolean equals(Object o) {
        // self check
        if (this == o)
            return true;
        // null check
        if (o == null)
            return false;
        // type check and cast
        if (getClass() != o.getClass())
            return false;
        JDKPackage jdk = (JDKPackage) o;
        return
                Objects.equals(this.vendor, jdk.vendor) &&
                        Objects.equals(this.version, jdk.version);
    }

    @Override
    public String[] getSegments() {
        return new String[]{this.version, this.vendor};
    }

    @Override
    public String getNamespaceDelim() {
        return ".";
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.version, this.vendor);
    }

    protected Object readResolve() {
        return SingletonHelper.INSTANCE;
    }

    private static class SingletonHelper {
        private static final JDKPackage INSTANCE = new JDKPackage();
    }
}
