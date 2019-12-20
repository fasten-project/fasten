# Docker images
Here is the instructions for building and running the docker image of the FASTEN server. Note that the FASTEN plug-ins are also included in the docker image of the server.

## Build Docker images
Make sure there exists JAR files in each of the folders (`/server`, `/plug-ins`). Also, note that the build command should be executed in the `/server` folder.

```
docker build -t fasten-server -f server/Dockerfile .
```

## Run Docker images
```
docker run -it fasten-server -k host.docker.internal:9092
```

## Publish Docker images
Make sure you have built the latest Docker images.
