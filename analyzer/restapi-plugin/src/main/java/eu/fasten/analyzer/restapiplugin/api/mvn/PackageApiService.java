package eu.fasten.analyzer.restapiplugin.api.mvn;

import javax.ws.rs.core.Response;

public interface PackageApiService {

    Response getPackageVersions(String package_name);
    Response getPackageVersion(String package_name, String package_version);
    Response getPackageMetadata(String package_name, String package_version);
    Response getPackageCallgraph(String package_name, String package_version);
}
