package eu.fasten.server.utils;

import eu.fasten.analyzer.javacgopal.MavenCoordinate;
import eu.fasten.analyzer.javacgopal.MavenResolver;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;
import java.util.StringJoiner;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.checkerframework.checker.units.qual.A;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;

/**
 * This is a utility class for processing POMFiles and generating a table with the following fields:
 * - groupID
 * - artifactID
 * - version
 * - timestamp
 */

public class ProcessPOMFiles {

    private String pomFile;
    private String csvFileName;
    private String pomFilesPath;

    private static String[] csvHeaderNames = {"groupID", "artifactID", "version", "timestamp"};
    
    static class MavenProject{
        public String groupID;
        public String artifactID;
        public String version;
        public String timestamp;

        public MavenProject(String groupID, String artifactID, String version, String timestamp) {
            this.groupID = groupID;
            this.artifactID = artifactID;
            this.version = version;
            this.timestamp = timestamp;
        }

        // For debugging purpose
        public void printProjectInfo(){
            System.out.println("groupID: " + this.groupID + " | " + "artifactID: " + this.artifactID + " | " +
                    "version: " + this.version + " | " + "timestamp: " + this.timestamp);
        }
    }

    public ProcessPOMFiles(String pomFile, String csvFileName, String pomFilesPath) {
        this.pomFile = pomFile;
        this.csvFileName = csvFileName;
        this.pomFilesPath = pomFilesPath;
    }

    public ArrayList<String> pomFileToList() throws FileNotFoundException {
        Scanner s = new Scanner(new File(this.pomFile));
        ArrayList<String> pomFilesURLs = new ArrayList<>();
        while (s.hasNextLine()){
            pomFilesURLs.add(s.nextLine());
        }
        return pomFilesURLs;
    }

    public ArrayList<MavenProject> gatherMavenProjects(ArrayList<String> pomFilesList) throws IOException {

        ArrayList<MavenProject> listMavenProjects = new ArrayList<>();

        for(String pomFile : pomFilesList){
            String fileName = pomFile.substring(pomFile.lastIndexOf("/") + 1);
            String pomFilePath = new StringJoiner(File.separator).add(this.pomFilesPath).add(fileName).toString();

            // TODO: if downloaded before, no need to get it again.
            MavenResolver resolver = new MavenResolver();
            Date timestamp =  resolver.getFileAndTimeStamp(pomFile, this.pomFilesPath);

            MavenProject mvnProject = parsePOMFile(pomFilePath);
            mvnProject.timestamp = new SimpleDateFormat("yyyy-MM-dd hh:mm").format(timestamp);

            listMavenProjects.add(mvnProject);

            mvnProject.printProjectInfo();
        }

        return listMavenProjects;
    }

    /**
     * Extracts groupID, artifactID, and version from a POM file.
     * @param POMFilePath
     * @return
     * @throws IOException
     */
    public MavenProject parsePOMFile(String POMFilePath) throws IOException {
        Document XMLDoc = Jsoup.parse(new FileInputStream(new File(POMFilePath)), "UTF-8", "", Parser.xmlParser());

        String groupID = "";
        String artifactID = "";
        String version = "";

        for(Element e: XMLDoc.getElementsByTag("project"))
        {
            groupID = e.getElementsByTag("groupId").text();
            artifactID =  e.getElementsByTag("artifactId").text();
            version = e.getElementsByTag("version").text();
        }

        return new MavenProject(groupID, artifactID, version, "");
    }

    public void generateCSVFile(ArrayList<MavenProject> listMavenProjects) throws IOException {
        FileWriter csvFile = new FileWriter(this.csvFileName);
        CSVPrinter csv = new CSVPrinter(csvFile, CSVFormat.DEFAULT.withHeader(this.csvHeaderNames));

        for(MavenProject p : listMavenProjects){
            csv.printRecord(p.groupID, p.artifactID, p.version, p.timestamp);
        }

        csvFile.close();
    }

    public static void main(String[] args) throws IOException {
        ProcessPOMFiles POMFileProc = new ProcessPOMFiles("./tmp/pomFiles.txt", "./tmp/projects.csv", "./tmp/xml");
        ArrayList<MavenProject> projects = POMFileProc.gatherMavenProjects(POMFileProc.pomFileToList());
        POMFileProc.generateCSVFile(projects);
    }

}
