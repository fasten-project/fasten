# API response codes for Maven

## Packages and package versions

| Resource | Method | Req. body | Query params | Response codes | Response body | Notes |
|-|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/versions` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: Package versions returned</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li></ul> | `(package_versions:* ∪ callables:metadata)` | — |
| `/mvn/packages/{pkg}/{pkg_ver}` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li></ul> | `package_versions:*` | — |
| `/mvn/packages/{pkg}/{pkg_ver}/metadata` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li></ul> | `package_versions:metadata` | — |
| `/mvn/packages/{pkg}/{pkg_ver}/callgraph` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li></ul> | `edges:(source_id, target_id)` | Could be retrieved directly from graph DB |
<!--
| `/mvn/packages/{pkg}/{pkg_ver}/callgraph` | `GET` | — | `limit`: `integer`, `offset`: `integer`, `resolve`: `dateTime`, `full`: `boolean` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`400`: Invalid timestamp</li><li>`404`: Package not found</li><li>`404`: Package version not found</li></ul> | ? | Graph stichting is a WIP. Will be retrievable through a simple `mvn` call. |
| `/mvn/packages/{pkg}/{pkg_ver}/vulnerabilities` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li></ul> | `callables:metadata` | — |
-->

## Dependencies

| Resource | Method | Req. body | Query params | Response codes | Response body | Notes |
|-|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/{pkg_ver}/deps` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li></ul> | `dependencies:*` | — |
<!--
| `/mvn/packages/{pkg}/{pkg_ver}/deps` | `GET` | — | `limit`: `integer`, `offset`: `integer`, `resolve`: `dateTime` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`400`: Invalid timestamp</li><li>`404`: Package not found</li><li>`404`: Package version not found</li></ul> | ? | Depends on `/mvn/packages/{pkg}/{pkg_ver}/callgraph`. |
-->

## Modules

| Resource | Method | Req. body | Query params | Response codes | Response body | Notes |
|-|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/{pkg_ver}/modules` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li></ul> | `modules:*` | — |
| `/mvn/packages/{pkg}/{pkg_ver}/modules/{namespace}/metadata` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li><li>`404`: Module namespace not found</li></ul> | `modules:metadata` | — |
| `/mvn/packages/{pkg}/{pkg_ver}/modules/{namespace}/files` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li><li>`404`: Module namespace not found</li></ul> | `files:*` | — |

## Binary Modules

| Resource | Method | Req. body | Query params | Response codes | Response body | Notes |
|-|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/{pkg_ver}/binary-modules` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li></ul> | `binary_modules:*` | — |
| `/mvn/packages/{pkg}/{pkg_ver}/binary-modules/{binary}/metadata` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li><li>`404`: Binary name not found</li></ul> | `binary_modules:metadata` | — |
| `/mvn/packages/{pkg}/{pkg_ver}/binary-modules/{binary}/files` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li><li>`404`: Binary name not found</li></ul> | `files:*` | — |

## Callables

Callable ID = module ID (package version) + FASTEN URI + other

| Resource | Method | Req. body | Query params | Response codes | Response body | Notes |
|-|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/{pkg_ver}/callables` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li></ul> | `callables:*` | Retrievably directly from graph DB, one-line query. |
| `/mvn/packages/{pkg}/{pkg_ver}/{fasten_uri}/metadata` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li><li>`404`: FASTEN URI not found</li></ul> | `callables:metadata` | All metadata. |
<!--
| `/mvn/packages/{pkg}/{pkg_ver}/{fasten_uri}/reach` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li><li>`404`: FASTEN URI not found</li></ul> | `dependencies:dependency_id` | There should be a query for this already. |
| `/mvn/packages/{pkg}/{pkg_ver}/{fasten_uri}/vulnerabilities` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li><li>`404`: FASTEN URI not found</li></ul> | `callables:metadata` | Vulnerabilities only. |
-->

## Edges

| Resource | Method | Req. body | Query params | Response codes | Response body | Notes |
|-|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/{pkg_ver}/edges` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li></ul> | `edges:*` | Edge IDs not stored in graph DB, hence SQL query is needed. |

## Files

| Resource | Method | Req. body | Query params | Response codes | Response body | Notes |
|-|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/{pkg_ver}/files` | `GET` | — | `limit`: `integer`, `offset`: `integer` | <ul><li>`200`: OK</li><li>`400`: Invalid limit value</li><li>`400`: Invalid offset value</li><li>`404`: Package not found</li><li>`404`: Package version not found</li></ul> | `files:*` | — |
