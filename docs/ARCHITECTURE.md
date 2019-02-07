## Architecture

This document is a draft of the FASTEN Knowledge base service. The knowledge base comprises of four components: 
`libfasten-core`, `libfasten-analysis`, `libfasten-processor`, and `libfasten-rest`. An overview of each component is
described below:

- `libfasten-core`: [webgraph](http://webgraph.di.unimi.it/) already provide functionality to store and 
   query graph data from billions of nodes and edges. Our goal is to extend [webgraph](http://webgraph.di.unimi.it/) 
   with call graph specific functionality:
    - _metadata storage:_ keeping _node_ (e.g., ref to Github, LOC, complexity, CVE, deprecation) and 
        _edge_ (e.g., version range) data using a conventional data store
    - _process updates:_ integrate and update metadata and graph stores
    - _dynamic version resolution:_ based on a query, resolve dependency constraints on-the-fly to construct
     a temporal graph
- `libfasten-analysis:` A set of analyzers with an interface to query the graph and metadata store of `libfasten-core`.
   The component is extensible but provides the following _batteries included_ analyzers:
   - `security`
   - `deprecation:` 
   - `change impact/semantic changes`: quantifies the impact of new releases or releases made.
- `libfasten-processor`: The _router_ of the knowledge base. The goal of this component is to listen for new events 
   from [Codefeedr](https://github.com/codefeedr/codefeedr), process them, and then generate call graphs depending 
   on the language or platform.
- `libfasten-rest:` REST API to interface the knowledge and its various components. 


### Data specifications 

