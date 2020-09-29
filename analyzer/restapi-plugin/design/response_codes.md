# API response codes for Maven

## Packages and package versions

| Resource | Method | Req. body | Query params | Status | Description |
|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/versions` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| `/mvn/packages/{pkg}/{pkg_ver}` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| `/mvn/packages/{pkg}/{pkg_ver}/metadata` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| `/mvn/packages/{pkg}/{pkg_ver}/vulnerabilities` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| `/mvn/packages/{pkg}/{pkg_ver}/callgraph` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| `/mvn/packages/{pkg}/{pkg_ver}/callgraph` | `GET` | — | `resolve`: `dateTime`, `full`: `boolean` |  |  |
| | | | | 400 | Bad request: invalid timestamp |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |

## Dependencies

| Resource | Method | Req. body | Query params | Status | Description |
|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/{pkg_ver}/deps` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| `/mvn/packages/{pkg}/{pkg_ver}/deps` | `GET` | — | `resolve`: `dateTime` | 200 | OK |
| | | | | 400 | Bad request: invalid timestamp |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |

## Modules

| Resource | Method | Req. body | Query params | Status | Description |
|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/{pkg_ver}/modules` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| `/mvn/packages/{pkg}/{pkg_ver}/modules/{namespace}/metadata` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| | | | | 404 | Module namespace not found |
| `/mvn/packages/{pkg}/{pkg_ver}/modules/{namespace}/files` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| | | | | 404 | Module namespace not found |

## Binary Modules

| Resource | Method | Req. body | Query params | Status | Description |
|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/{pkg_ver}/binary-modules` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| `/mvn/packages/{pkg}/{pkg_ver}/binary-modules/{binary}/metadata` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| | | | | 404 | Binary name not found |
| `/mvn/packages/{pkg}/{pkg_ver}/binary-modules/{binary}/files` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| | | | | 404 | Binary name not found |

## Callables

| Resource | Method | Req. body | Query params | Status | Description |
|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/{pkg_ver}/callables` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| `/mvn/packages/{pkg}/{pkg_ver}/{fasten_uri}/vulnerabilities` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| | | | | 404 | FASTEN URI not found |
| `/mvn/packages/{pkg}/{pkg_ver}/{fasten_uri}/metadata` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| | | | | 404 | FASTEN URI not found |
| `/mvn/packages/{pkg}/{pkg_ver}/{fasten_uri}/reach` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
| | | | | 404 | FASTEN URI not found |

## Edges

| Resource | Method | Req. body | Query params | Status | Description |
|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/{pkg_ver}/edges` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |

## Files

| Resource | Method | Req. body | Query params | Status | Description |
|-|-|-|-|-|-|
| `/mvn/packages/{pkg}/{pkg_ver}/files` | `GET` | — | — | 200 | OK |
| | | | | 404 | Package not found |
| | | | | 404 | Package version not found |
