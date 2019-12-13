# Docker images

We have three docker images for the components:
- Server
- Analyzer
- Pipeliner

## Build Docker images
Make sure there exists a correct `fasten.jar` file in each of the folders (`/server`, `/analyzer`, `pipeliner`).
```
docker build -t fasten_server server/.
docker build -t fasten_analyzer analyzer/.
docker build -t fasten_pipeliner pipeliner/.
```

## Run Docker images
```
docker run fasten_server
docker run fasten_analyzer
docker run fasten_pipeliner
```

## Publish Docker images
Make sure you have built the latest Docker images.
