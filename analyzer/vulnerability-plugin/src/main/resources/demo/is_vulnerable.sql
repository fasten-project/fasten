CREATE OR REPLACE PROCEDURE is_vulnerable(
    pkg_name TEXT,
    pkg_version TEXT)
LANGUAGE plpgsql
AS $$
DECLARE
    vul_call_out callables%ROWTYPE;
    vul_call_in callables%ROWTYPE;
    vul_pkg package_versions%ROWTYPE;
    counter_zero integer := 0;
    counter_one integer := 0;
    counter_two integer := 0;
BEGIN
    -- Check the metadata of the package
    FOR vul_pkg IN
        SELECT pv.id, pv.package_id, pv.version, pv.cg_generator, pv.created_at, pv.metadata
        FROM package_versions AS pv
        JOIN packages ON packages.id = pv.package_id
        WHERE packages.package_name = pkg_name
        AND pv.version = pkg_version
        AND pv.metadata -> 'vulnerabilities' IS NOT NULL
    LOOP
        counter_zero := counter_zero + 1;
        RAISE INFO ' - PACKAGE INFO - ';
        RAISE INFO '! -- ALERT --!';
        RAISE INFO 'Found vulnerablitiy % in the package.', 
            vul_pkg.metadata -> 'vulnerabilities' -> 0 -> 'id';
    END LOOP;
    IF counter_zero = 0 THEN
            RAISE INFO '';
            RAISE INFO ' - INTERNAL CALLS INFO - ';
            RAISE INFO 'o -- SAFE -- o';
    END IF;

    -- Check the internal calls of the package
    FOR vul_call_in IN
        SELECT c.id, c.module_id, c.fasten_uri, c.is_internal_call, c.created_at, c.metadata
        FROM callables AS c
        JOIN modules ON c.module_id = modules.id
        JOIN package_versions AS pv ON pv.id = modules.package_version_id
        JOIN packages ON packages.id = pv.package_id
        WHERE packages.package_name = pkg_name
        AND pv.version = pkg_version
        AND c.metadata -> 'vulnerabilities' IS NOT NULL
    LOOP
        counter_one := counter_one + 1;
        IF counter_one = 1 THEN
            RAISE INFO '';
            RAISE INFO ' - INTERNAL CALLS INFO - ';
            RAISE INFO '! -- ALERT --!';
        END IF;
        RAISE INFO 'Found vulnerablitiy % in callable with ID: % and fasten_uri: %', 
            vul_call_in.metadata -> 'vulnerabilities' -> 0 -> 'id',
            vul_call_in.id,
            vul_call_in.fasten_uri;
    END LOOP;
    IF counter_one = 0 THEN
            RAISE INFO '';
            RAISE INFO ' - INTERNAL CALLS INFO - ';
            RAISE INFO 'o -- SAFE -- o';
    END IF;

    -- Check external calls made by the package
    FOR vul_call_out IN
        SELECT c3.id, c3.module_id, c3.fasten_uri, c3.is_internal_call, c3.created_at, c3.metadata
        FROM callables AS c1
        JOIN modules ON c1.module_id = modules.id
        JOIN package_versions ON package_versions.id = modules.package_version_id
        JOIN packages ON packages.id = package_versions.package_id
        JOIN edges ON edges.source_id = c1.id
        JOIN callables AS c2 ON c2.id = edges.target_id
        JOIN callables AS c3 ON c3.fasten_uri = c2.fasten_uri
        JOIN modules AS m2 ON m2.id = c3.module_id
        JOIN (
            SELECT modules.id
            FROM packages
            JOIN package_versions ON package_versions.package_id = packages.id
            JOIN (
                SELECT packages.package_name, dep.version_range
                FROM dependencies AS dep
                JOIN packages ON packages.id = dep.dependency_id
                WHERE package_version_id = (
                    SELECT package_versions.id
                    FROM package_versions
                    JOIN packages ON packages.id = package_versions.package_id
                    WHERE packages.package_name = pkg_name
                    AND package_versions.version = pkg_version
                    )
                ) dep_packages ON (dep_packages.package_name = packages.package_name AND package_versions.version = ANY(dep_packages.version_range))
                JOIN modules ON package_versions.id = modules.package_version_id
            ) dep_modules ON dep_modules.id = m2.id
        WHERE packages.package_name = pkg_name
        AND package_versions.version = pkg_version
        AND c1.is_internal_call = 't' 
        AND c2.is_internal_call = 'f'
        AND c3.is_internal_call = 't'
        AND c3.metadata -> 'vulnerabilities' IS NOT NULL
    LOOP
        counter_two := counter_two + 1;
        IF counter_two = 1 THEN
        RAISE INFO '';
        RAISE INFO ' - EXTERNAL CALLS INFO - ';
        RAISE INFO '! -- ALERT --!';
        END IF;
        -- Print information about the vulnerability
        -- TODO: Each metadata -> vulnerabilities might contain more than 1 vulnerability
        RAISE INFO 'Found vulnerablitiy % in callable with ID: % and fasten_uri: %', 
            vul_call_out.metadata -> 'vulnerabilities' -> 0 -> 'id',
            vul_call_out.id,
            vul_call_out.fasten_uri;
        END LOOP;
    -- Print information
    IF counter_two = 0 THEN
        RAISE INFO '';
        RAISE INFO ' - EXTERNAL CALLS INFO - ';
        RAISE INFO 'o -- SAFE -- o';
    END IF;
END;
$$;