# Architecture

This document is a draft of the FASTEN knowledge base service. The knowledge base comprises four components: `libfasten-core`, `libfasten-analyzer`, `libfasten-pipeliner`, and `libfasten-server`. An overview of each component is described below:

- `libfasten-core`: [webgraph](http://webgraph.di.unimi.it/) already provides functionality to store and 
   query graph data from billions of nodes and edges. Our goal is to extend [webgraph](http://webgraph.di.unimi.it/) 
   with call graph specific functionality:
    - __metadata storage:__ keeping _node_ (e.g., ref to Github, LOC, complexity, CVE, deprecation) and 
        _edge_ (e.g., version range) data using a conventional data store
    - __process updates:__ integrate and update metadata and graph stores
    - __dynamic version resolution:__ based on a query, resolve dependency constraints on-the-fly to construct
     a temporal graph
- `libfasten-analyzer:` A collection of analyzers with an interface to query the graph and metadata store of `libfasten-core`.
   The component is extensible but provides the following _batteries included_ analyzers:
   - __security:__ calculating the transitive closure of affected dependent packages
   - __API analytics:__ following trends on deprecation/removal/adaption of package APIs.   
   - __change impact/semantic changes:__ quantifies the impact of new releases or releases made.
- `libfasten-pipeliner`: 
   - Orchestration component for creating workflow pipelines using `libfasten-core` and `libfasten-analyzer`. Event feed data will come from [Codefeedr](https://github.com/codefeedr/codefeedr) such as CVE or licence data. The component will also create new queryable and push-based event streams such as a "the daily top-10 most imported library function". Package manager plugins will interface this component for push-based notifications.
- `libfasten-server:`
   -  REST API to interface the knowledge base and its various components. 


### Data specifications 

Here we enlist data sources that the knowledge base will interact with:

- Package releases including metadata and source code 
- Release events 
- Call graphs
- API requests from users 
- Metadata related to nodes and edges in the graph
- Security advisories, licensing, function body information (e.g., LOC, complexity)
