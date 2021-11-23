WARNING: All paths to files in this folder are hard-coded in tests, please do not rename anything.

Every folder is a separate Maven project. If you want to change or extend tests, you can import
the Maven project into the IDE of your choice, edit it, and execute a "mvn clean package" in this
parent folder. Do not forget to commit all files.

Please note: When the packaging is not done through this parent, but for projects individually,
it does not work unless you "mvn install" the dependencies.

