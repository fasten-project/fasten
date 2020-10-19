package eu.fasten.analyzer.restapiplugin.api.mvn;

import javax.ws.rs.core.Response;

public interface PackageApiService {

    Response getPackageVersions(String package_name,
                                short offset,
                                short limit);

    Response getPackageVersion(String package_name,
                               String package_version,
                               short offset,
                               short limit);

    Response getPackageMetadata(String package_name,
                                String package_version,
                                short offset,
                                short limit);

    Response getPackageCallgraph(String package_name,
                                 String package_version,
                                 short offset,
                                 short limit);
}
