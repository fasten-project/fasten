# Docker images

We have three docker images for the components:
- Server
- Analyzer

## Build Docker images
Make sure there exists a correct `fasten.jar` file in each of the folders (`/server`, `/analyzer`, `pipeliner`).
```
docker build -t fasten_server server/.
docker build -t fasten_analyzer analyzer/.
```

## Run Docker images
```
docker run fasten_server # add arguments here
docker run fasten_analyzer # add arguments here
```

## Publish Docker images
Make sure you have built the latest Docker images.
