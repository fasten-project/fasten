# Docker images
Here is the instructions for building and running the docker image of the FASTEN server. Note that the FASTEN plug-ins are also included in the docker image of the server.

## Build Docker images
Make sure there exists JAR files in each of the folders (`/server`, `/plug-ins`). Also, note that the build command should be executed in the `/server` folder.

```
docker build -t fasten-server -f server/Dockerfile .
```

## Run Docker images
To start the FASTEN server and load the plug-ins, run the following command:

```
docker run --network="host" -it fasten-server -k localhost:30001
```

## Publish Docker images
Make sure you have built the latest Docker images.
