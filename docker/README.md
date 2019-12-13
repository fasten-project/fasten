# Docker images

We have three docker images for the components:
- Server
- Analyzer
- Pipeliner

## Build Docker images
Make sure there exists a correct `fasten.jar` file in this folder.
```
docker build -t fasten_server server/.
docker build -t fasten_analyzer analyzer/.
docker build -t fasten_pipeliner pipeliner/.
```

## Publish Docker images
Make sure you have built the latest Docker images.
