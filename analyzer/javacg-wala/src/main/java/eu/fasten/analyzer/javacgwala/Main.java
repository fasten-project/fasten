package eu.fasten.analyzer.javacgwala;

import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.util.WalaException;
import eu.fasten.analyzer.javacgwala.data.type.MavenCoordinate;
import eu.fasten.core.data.FastenURI;
import picocli.CommandLine;

@CommandLine.Command(name = "JavaCGWala")
public class Main implements Runnable {

    static class Dependent {
        @CommandLine.Option(names = {"-g", "--group"},
                paramLabel = "GROUP",
                description = "Maven group id",
                required = true)
        String group;

        @CommandLine.Option(names = {"-a", "--artifact"},
                paramLabel = "ARTIFACT",
                description = "Maven artifact id",
                required = true)
        String artifact;

        @CommandLine.Option(names = {"-v", "--version"},
                paramLabel = "VERSION",
                description = "Maven version id",
                required = true)
        String version;
    }

    @CommandLine.ArgGroup(exclusive = true)
    Exclusive exclusive;

    static class Exclusive {
        @CommandLine.ArgGroup(exclusive = false)
        Dependent mavencoords;

        @CommandLine.Option(names = {"-c", "--coord"},
                paramLabel = "COORD",
                description = "Maven coordinates string",
                required = true)
        String mavenCoordStr;
    }


    public void run() {
        MavenCoordinate mavenCoordinate = null;
        if (this.exclusive.mavenCoordStr != null) {
            mavenCoordinate = MavenCoordinate.of(this.exclusive.mavenCoordStr);
        } else {
            mavenCoordinate = new MavenCoordinate(this.exclusive.mavencoords.group,
                    this.exclusive.mavencoords.artifact,
                    this.exclusive.mavencoords.version);
        }

        System.out.println("Generating call graph...");
        var revisionCallGraph = WalaJavaCGGen.generateCallGraph(mavenCoordinate.getCanonicalForm());

        //TODO something with the calculated RevesionCallGraph.
        System.out.println(revisionCallGraph.toJSON());
    }

    /**
     * Generates RevisionCallGraphs using Wala for the specified artifact in the command line parameters.
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
