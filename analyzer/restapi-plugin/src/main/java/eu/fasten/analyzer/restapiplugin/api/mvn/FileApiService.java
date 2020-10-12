package eu.fasten.analyzer.restapiplugin.api.mvn;

import javax.ws.rs.core.Response;

public interface FileApiService {

    Response getPackageFiles(String package_name, String package_version);
}
