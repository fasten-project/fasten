package eu.fasten.core.praezi;

import java.io.Serializable;
import java.util.Objects;


public class DependencyConstraint implements Serializable {

    public final String pkg;
    public final String version;
    public final String versionConstraint;

    public DependencyConstraint(String pkg, String version, String versionConstraint) {
        this.pkg = pkg;
        this.version = version;
        this.versionConstraint = versionConstraint;
    }

    @Override
    public String toString() {
        return pkg + "," + version + ":" + versionConstraint;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DependencyConstraint) {
            DependencyConstraint dc = (DependencyConstraint) o;
            return Objects.equals(dc.pkg,this.pkg) && Objects.equals(dc.version, this.version) && Objects.equals(dc.versionConstraint, this.versionConstraint);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.pkg,this.version, this.versionConstraint);
    }
}
