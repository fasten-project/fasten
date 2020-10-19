package eu.fasten.analyzer.restapiplugin.api.mvn;

import javax.ws.rs.core.Response;

public interface EdgeApiService {

    Response getPackageEdges(String package_name,
                             String package_version,
                             short offset,
                             short limit);
}
