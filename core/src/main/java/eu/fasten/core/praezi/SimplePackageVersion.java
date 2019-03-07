package eu.fasten.core.praezi;


import java.io.Serializable;
import java.util.Objects;

public class SimplePackageVersion implements Serializable {

    public final String name;
    public final String version;


    public SimplePackageVersion(String name, String version) {
        this.name = name;
        this.version = version;
    }

    @Override
    public String toString() {
        return name + "," + version;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SimplePackageVersion) {
            SimplePackageVersion dc = (SimplePackageVersion) o;
            return Objects.equals(dc.name, this.name) && Objects.equals(dc.version, this.version);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.name, this.version);
    }

}


