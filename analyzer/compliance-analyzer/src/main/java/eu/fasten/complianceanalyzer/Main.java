package eu.fasten.analyzer.complianceanalyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;


  @CommandLine.Command(name = "ComplianceAnalyzer")
 public class Main implements Runnable{

      private static Logger logger = LoggerFactory.getLogger(Main.class);

      @CommandLine.Option(names = {"-d", "--directory"},
             paramLabel = "Dir",
             description = "Directory to the project's source code")
     String dir;

      public static void main(String[] args) {
        new CommandLine(new Main()).execute(args);
     }

      @Override
     public void run() {

     }
 }