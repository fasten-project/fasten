## Instructions

This document outlines instructions to run the `vulnerability_plugin` in the demo. After running `mvn install`, the `jar` file of the plugin will be found in the `/docker/plugins` folder with the other plugins. At this point the `jar` can be run with 3 args.

-db : URL of the database to inject data in

-dbu : User of the database to use

-json_path : Path to the JSON file with the vulnerability information to inject. *The file can be found in the `vulnerablity_plugin` directory under `/resources/demo/data.json`*