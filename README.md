## Fasten-core 
[![Build Status](https://travis-ci.org/fasten-project/fasten.svg?branch=master)](https://travis-ci.org/fasten-project/fasten)

### Creating a workspace with IntelliJ

- Install the latest Maven for your system
    - Mac: `brew install maven`
- Install JDK 11 for your system
    - Mac: `brew cask install java`
- Export the `JAVA_HOME` environment variable to point to your newly installed JDK
    - Mac: `export JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-11.0.2.jdk/Contents/Home/`
- Install the [latest version of IntelliJ Community edition](https://www.jetbrains.com/idea/download/#section=mac) (>= 2018.3)
- Clone the project from GitHub: `git clone https://github.com/fasten-project/fasten`
- Do a test build on the command line: `cd fasten && mvn install`. Everything should work.
- Open the project in IntelliJ:
    - In the open project screen, click "Import Project" and navigate to your FASTEN checkout
    - Select "Import from from external model" and then Maven
    - Click Next, Next
    - In the SDK selection screen, select JDK 11. If it is not there, click the
    `+` button and navigate to the installation directory of your JDK 11.
    - Set the project name to **fasten**
- When the import is done, go to File -> Project Structure -> Project Settings
    - From the dropdown menu, select the Java 11 SDK. If it is not there, click on `New` to create it.
    - Set the project language level to Java 11
