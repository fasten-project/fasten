# Fasten + Quartermaster

<!-- TODO Introduction -->

1. Launch the demo:
    ```bash
    mvn clean install exec:java -Dexec.args="--file dummyKafkaTopic.json"
    ```
   This demo simulates a Kafka topic consumption by reading the [`dummyKafkaTopic.json` file](dummyKafkaTopic.json).\
   Upon topic consumption, the `compliance-analyzer` launches Quartermaster that will build the specified repository. 

1. Wait for the building process to be over:
    ```bash
    kubectl logs --follow $(kubectl get pods --selector job-name=qmstr -o=name) qmstr-client
    ```

1. Forward two local ports to the following two ports on the DGraph Pod:
    ```bash
    kubectl port-forward $(kubectl get pods --selector job-name=qmstr -o=name) 8000:8000
    ```
    ```bash
    kubectl port-forward $(kubectl get pods --selector job-name=qmstr -o=name) 8080:8080
    ```

1. Open [localhost:8000/?latest](http://localhost:8000/?latest) in your browser.

1. Click on "Continue":
    <p align="center">
        <img src="img/dgraph_login.png" alt="DGraph login page" width="75%"/>
    </p>

1. Navigate to the "Console" page
    <!-- TODO -->
    <!-- <p align="center">
        <img src="doc/img/dgraph_console.png" alt="DGraph console page"/>
    </p> -->

1. You should now be able to query the database
    <!-- TODO -->
    <!-- ```graphql
    {
        ...
    }
    ``` -->
