<p align="center">
    <img src="https://user-images.githubusercontent.com/45048351/89221900-bdad6400-d5dc-11ea-8136-533875bbee8e.jpg">
</p>
<br/>
<p align="center">
    <a href="https://github.com/fasten-project/fasten/actions" alt="GitHub Workflow Status">
        <img src="https://img.shields.io/github/workflow/status/fasten-project/fasten/Java%20CI?logo=GitHub%20Actions&logoColor=white&style=for-the-badge" /></a>
    <a href="https://github.com/fasten-project/fasten/releases" alt="GitHub Workflow Status">
                <img src="https://img.shields.io/github/v/release/fasten-project/fasten?logo=GitHub&style=for-the-badge" /></a>
    <a href="https://gitter.im/fasten-public" alt="Gitter">
            <img src="https://img.shields.io/gitter/room/fasten-project/fasten?style=for-the-badge&logo=gitter" /></a>
</p>
<br/>

The FASTEN project is an intelligent software package management system that will enhance robustness and security in software ecosystems. The research and development activities include innovative ecosystem analysis techniques,  with C, Java and Python applications. Derived techniques will tackle the problems of security and risk evaluation, license compliance and change impact analysis. Moreover, a back-end service and a knowledge base will host the results of those analyses while the integration of those analyses will reach the developer’s workflow and Continuous Integration (CI) tool chains.

## Contributing
We welcome contributions from external collaborators. If you are interested in contributing code or otherwise, please have a look at our [contributing guidelines](https://github.com/fasten-project/fasten/blob/master/CONTRIBUTING.md). Have a look at the issue board if you are looking for some inspiration.

## Setting up your development environment
We support development on Linux, MacOS, and Windows. In order to contribute, you need to have the following dependencies installed:
- Java 11
- Maven 

### Creating a workspace with IntelliJ
- Install the [latest version of IntelliJ Community edition](https://www.jetbrains.com/idea/download/#section=mac) (>= 2018.3)
- First clone the project from GitHub:  
```
git clone https://github.com/fasten-project/fasten
```
- Do a test build on the command line:  
```
cd fasten && mvn install
``` 
Everything should work. Let us know if it doesn't.
- Open the project in IntelliJ:
    - In the open project screen, click "Import Project" and navigate to your FASTEN checkout
    - Select "Import from from external model" and then Maven.
    - Click Next, Next
    - Select JDK 11 when prompted. If it is not there, click the
    `+` button and navigate to the installation directory of your JDK 11.
- When the import is done, go to File -> Project Structure -> Project Settings
    - From the dropdown menu, select the Java 11 SDK. If it is not there, click on `New` to create it.
    - Set the project language level to Java 11
- If you encounter "PF4J annotation processor" error when building the project, follow the below steps:
    - Go to Preferences -> Build, Execution, Deployment -> Compiler -> Annotation Processors .
    - For the annotation profile of the module you are trying to build/run, select "Obtain processors from classpath".
    
You now can start contributing to the FASTEN project. We'll be happy to review any code you have submitted. Also feel free to check out a more [detailed guide](https://github.com/fasten-project/fasten/wiki/Development-Environment) on how to set up a development environment.

## Join the community

The FASTEN software package management efficiency relies on an open community contributing to open technologies. Related research projects, R&D engineers, early users and open source contributors are welcome to join the [FASTEN community](https://www.fasten-project.eu/view/Main/Community), to try the tools, to participate in physical and remote workshops and to share our efforts using the project [community page](https://www.fasten-project.eu/view/Main/Community) and the social media buttons below.  
<p>
    <a href="http://www.twitter.com/FastenProject" alt="Fasten Twitter">
        <img src="https://img.shields.io/badge/%20-Twitter-%231DA1F2?logo=Twitter&style=for-the-badge&logoColor=white" /></a>
    <a href="http://www.slideshare.net/FastenProject" alt="GitHub Workflow Status">
                <img src="https://img.shields.io/badge/%20-SlideShare-%230077B5?logo=slideshare&style=for-the-badge&logoColor=white" /></a>
    <a href="http://www.linkedin.com/groups?gid=12172959" alt="Gitter">
            <img src="https://img.shields.io/badge/%20-LinkedIn-%232867B2?logo=linkedin&style=for-the-badge&logoColor=white" /></a>
</p>
