package eu.fasten.analyzer.data.type;

public final class JavaPackage implements Namespace {

    public final String[] path;

    public JavaPackage(String... path) {
        this.path = path;
    }

    @Override
    public String[] getSegments() {
        return this.path;
    }

    @Override
    public String getNamespaceDelim() {
        return ".";
    }

}
