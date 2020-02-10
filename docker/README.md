# Docker images
Here is the instructions for building and running the docker image of the FASTEN server. Note that the FASTEN plug-ins are also included in the docker image of the server.

## Build Docker images
Make sure there exists JAR files in each of the folders (`/server`, `/plugins`). Also, note that the build command should be executed in the `/server` folder.

```
docker build -t fasten-server -f server/Dockerfile .
```

## Run Docker images
To start the FASTEN server and load the plug-ins, run the following command:

```
docker run --user $(id -u):$(id -g) --network="host" -d fasten-server -k localhost:30001
```
The above command runs the Docker container of the FASTEN server in background as non-root user.

### local
To start the FASTEN server locally on a Mac system, run the following command:
```
docker run -d fasten-server -k host.docker.internal:9092
```

## Publish Docker images
Make sure you have built the latest Docker images.
