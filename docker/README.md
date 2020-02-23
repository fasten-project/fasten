# Docker images
Here is the instructions for building and running the docker image of the FASTEN server. Note that the FASTEN plug-ins are also included in the docker image of the server.

## Build Docker images
Make sure there exists JAR files in each of the folders (`/server`, `/plugins`). Also, note that the build command should be executed in the `/server` folder.

```
docker build -t fasten-server-dev -f server/Dockerfile .
```

## Run Docker images
To start the FASTEN server and load the plug-ins, run the following command:

```
docker run --user $(id -u):$(id -g) --network="host" --log-opt max-size=1g -d fasten-server-dev -k localhost:30001
```
The above command runs the Docker container of the FASTEN server in background as non-root user.

### local
To start the FASTEN server locally on a Mac system, run the following command:
```
docker run --log-opt max-size=1g -d fasten-server-dev -k host.docker.internal:9092
```

## Publish Docker images
To push the Docker image of FASTEN server to Docker Hub, tag the image first:
```
docker tag $IMG_TAG yourhubusername/fasten-server-dev:v$ver_number
```
Where `$IMG_TAG` is the tag of the Docker image and `$ver_number` is the version of the new release.

Finally, run the following command to push the image to Docker Hub:
```
docker push yourhubusername/fasten-server-dev
```

# Kubernetes
To deploy the FASTEN server on Kubernetes clusters, run the following command:
```
kubectl apply -f k8s_deploy.yaml
```
The above commands deploys a number of FASTEN server applications depending on the value of `replicas`.

To check the status of deployed pods, execute below command:
```
kubectl get pods -n fasten
```
