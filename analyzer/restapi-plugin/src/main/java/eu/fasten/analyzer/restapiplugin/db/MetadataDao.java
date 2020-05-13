package eu.fasten.analyzer.restapiplugin.db;

import eu.fasten.core.data.metadatadb.codegen.tables.Files;
import eu.fasten.core.data.metadatadb.codegen.tables.Modules;
import eu.fasten.core.data.metadatadb.codegen.tables.PackageVersions;
import eu.fasten.core.data.metadatadb.codegen.tables.Packages;
import eu.fasten.core.data.metadatadb.codegen.tables.BinaryModules;
import eu.fasten.core.data.metadatadb.codegen.tables.Callables;
import eu.fasten.core.data.metadatadb.codegen.tables.Edges;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Record2;
import org.jooq.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class MetadataDao {

    private DSLContext context;
    private final Logger logger = LoggerFactory.getLogger(MetadataDao.class.getName());

    public MetadataDao(DSLContext context){
        this.context = context;
    }

    public DSLContext getContext() {
        return this.context;
    }

    public void setContext(DSLContext context) {
        this.context = context;
    }

    /**
     * Dummy query to test jOOQ.
     *
     * @param forge       Forge of the package
     * @param packageName Name of the package
     * @param version     Version of the package
     * @return metadata   All known metadata
     */
    public String dummyQuery(String forge, String packageName, String version) {
        Result<Record2<Long, String>> result = context.select(Packages.PACKAGES.ID, Packages.PACKAGES.PACKAGE_NAME).from(Packages.PACKAGES).fetch();

        for (Record r : result) {
            Long id = r.getValue(Packages.PACKAGES.ID);
            String pkgName = r.getValue(Packages.PACKAGES.PACKAGE_NAME);
            System.out.println("id: "+id+"name: "+pkgName);
        }
        return "dummyQuery OK!";
    }

    /**
     * Gets all known metadata given a forge, package name and its version
     *
     * @param forge       Forge of the package
     * @param packageName Name of the package
     * @param version     Version of the package
     * @return metadata   All known metadata
     */
    public String getAllMetadataForPkg(String forge, String packageName, String version) {

        // TODO: update this to logger.debug
        System.out.println("DEBUG: forge: "+forge+" / pkg name: "+packageName+" / version:"+version);

        Packages p = Packages.PACKAGES.as("p");
        PackageVersions pv = PackageVersions.PACKAGE_VERSIONS.as("pv");
        Modules m = Modules.MODULES.as("m");
        BinaryModules bm = BinaryModules.BINARY_MODULES.as("bm");
        Files f = Files.FILES.as("f");
        Callables c = Callables.CALLABLES.as("c");
        Edges e = Edges.EDGES.as("e");

        // TODO: the binary_modules wasn't added to the query because the table is empty
        // TODO: add error handlers for empty tables?
        Result<Record> queryResult =
                context
                        .select(p.FORGE, p.PACKAGE_NAME)
                        .select(pv.VERSION, pv.METADATA.as("pkg_version_metadata"))
                        .select(f.METADATA.as("file_metadata"))
                        .select(m.METADATA.as("module_metadata"))
                        .select(c.METADATA.as("callable_metadata"))
                        .select(e.METADATA.as("edge_metadata"))
                        .from(p)
                                .innerJoin(pv).on(p.ID.eq(pv.PACKAGE_ID))
                                .innerJoin(m).on(pv.ID.eq(m.PACKAGE_VERSION_ID))
                                .innerJoin(f).on(pv.ID.eq(f.PACKAGE_VERSION_ID))
                                .innerJoin(c).on(m.ID.eq(c.MODULE_ID))
                                .innerJoin(e).on(c.ID.eq(e.TARGET_ID))
                                .or(c.ID.eq(e.SOURCE_ID))
                        .where(p.FORGE.equalIgnoreCase(forge)
                                .and(p.PACKAGE_NAME.equalIgnoreCase(packageName)
                                        .and(pv.VERSION.equalIgnoreCase(version))
                                )
                        )
                        .fetch();

        // TODO: update this to logger.debug
        System.out.println("DEBUG: Total rows: "+queryResult.size());

        String result = queryResult.formatJSON();
        return result;
    }

}
