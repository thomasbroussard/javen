package org.javen.integration;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.shared.invoker.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * Support class to build projects using maven
 */
public class Reactor {

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
    }

    public static final String FRONT_END_PLUGIN = "com.github.eirslett:frontend-maven-plugin:1.10.0";

    private static final List<String> failedCommands = new ArrayList<>();
    private static final List<String> successFullCommands = new ArrayList<>();

    /**
     * quiet option to pass to maven CLI
     */
    private static final String MVN_CLI_QUIET = "-q";

    /**
     * force update option for maven CLI
     */
    static final String MVN_CLI_UPDATE = "-U";

    /**
     * do not run unit testcases
     */
    static final String MVN_SKIP_TESTS = "-DskipTests=true";

    private static final Logger LOGGER = Logger.getLogger("Reactor");

    private static final Invoker invoker = new DefaultInvoker();


    private static String mvnCliParam(String parameter, String... value) {
        return "-D" + parameter + (value == null || value.length == 0 ? "" : "=" + value[0]);
    }

    static String mvnCliPomFile(String file) {
        return "-f " + file;
    }


    public static boolean createDirs(String path) {
        return new File(path).mkdirs();
    }


    /**
     * zips a folder
     *
     * @param origin the folder to zip
     * @param target the target name (should be a file)
     * @return true if the command succeeded
     */
    public static boolean zip(String origin, String target) {

        try (FileOutputStream fos = new FileOutputStream(target);) {
            ZipOutputStream zipOut = new ZipOutputStream(fos);
            File fileToZip = new File(origin);

            zipFile(fileToZip, fileToZip.getName(), zipOut, 0);
            zipOut.close();
            fos.close();
        } catch (IOException e) {
            LOGGER.throwing("Reactor", "zip", e);
            failedCommands.add("zip : " + origin + " --> " + target);
            return false;
        }
        return true;

    }

    //credits : https://www.baeldung.com/java-compress-and-uncompress
    private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut, int level) throws IOException {
        if (fileToZip.isHidden()) {
            return;
        }
        if (fileToZip.isDirectory()) {
            if (level != 0) {
                if (fileName.endsWith("/")) {
                    zipOut.putNextEntry(new ZipEntry(fileName));
                    zipOut.closeEntry();
                } else {
                    zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                    zipOut.closeEntry();
                }
                fileName = fileName + "/";
            } else {
                fileName = "";
            }
            File[] children = fileToZip.listFiles();
            for (File childFile : children) {
                zipFile(childFile, fileName + childFile.getName(), zipOut, ++level);
            }
            return;
        }
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileName);
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
    }

    /**
     * Move origin to target
     *
     * @param origin the file to be moved (can be a directory)
     * @param target the target file
     * @return true if operation succeedd
     */
    public static boolean move(String origin, String target) {
        try {
            Files.move(new File(origin).toPath(), new File(target).toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }


    private static void outputSection(String message) {
        System.out.println();
        System.out.println();
        System.out.println("##########################################################################################");
        System.out.println(message);
        System.out.println("##########################################################################################");
        System.out.println();
        System.out.println();
    }


    /**
     * copy origin to target
     * @param origin file (can be a file or a directory) to be copied
     * @param target file (can be a file or a directory)
     * @return true if the copy has been successful
     */
    public static boolean copy(String origin, String target) {
        return copy(origin, target, s -> true, s -> false);
    }

    /**
     * find some files matching file name criteria
     *
     * @param origin the base path from which to search for files
     * @param matchCriteria the criteria to retain files
     * @return a list of files matching the criteria given as parameters
     */
    public static List<File> find(String origin, Predicate<String> matchCriteria) {
        List<File> result = new ArrayList<>();
        File origingFile = new File(origin);
        if (origingFile.isFile()) {
            logInfo("find command run with a file, try with a folder exiting...");
            failedCommands.add("find : on " + origin + "(called with a file, not a folder)");
            return result;
        }
        try {
            result = Files.walk(origingFile.toPath())
                    .filter(p -> matchCriteria.test(p.getFileName().toString()))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logError("error while walking through directory structure", "find", e);
        }
        return result;


    }

    /**
     * Deletes a folder or a directory.
     * If the folder path is exactly same as the current execution path, then the operation is rejected
     * TODO improve
     *
     * @param path the path to be deleted
     * @return true if operation succeeded
     */
    public static boolean delete(String path) {
        String execHome = System.getProperty("user.dir");
        File target = new File(path);
        if (target.getAbsolutePath().equals(new File(execHome).getAbsolutePath())) {
            logInfo("rejecting the delete operation, trying to delete the whole project directory");
            return false;
        }
        if (target.isDirectory()) {
            return deleteDirectory(target);
        } else {
            return target.delete();
        }
    }

    private static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    /**
     * deletes files based on the given criteria
     * @param path the base path to trigger the delete operation
     * @param matchCriteria the matching criteria to retain files
     */
    public static void delete(String path, Predicate<String> matchCriteria) {
        final File fileDirectory = new File(path);
        List<File> result = new ArrayList<>();
        try {
            Files.walk(fileDirectory.toPath())
                    .filter(p -> matchCriteria.test(p.getFileName().toString()))
                    .map(Path::toFile)
                    .forEach(f -> Reactor.delete(f.getAbsolutePath()));
        } catch (final Exception e) {
            logError("error while walking through directory structure", "delete", e);
        }

    }


    /**
     * nested class to represent Maven Related Operations
     */
    public static class mvn {
        /**
         * runs a maven execution parameterized to execute the maven-dependency-plugin:copy-dependencies
         *
         * @param pomFilePath the target maven project file
         * @return true if operation succeeded
         */
        public static boolean copyDependencies(String pomFilePath) {
            logInfo("materializing dependencies for project : " + pomFilePath);
            return mvn.run("materializing dependencies for project : " + pomFilePath, mvnCliPomFile(pomFilePath), " org.apache.maven.plugins:maven-dependency-plugin:3.1.2:copy-dependencies");
        }

        /**
         * reads the current project version, target pom file is defined by pomFilePath
         *
         * @param pomFilePath project to read
         * @return a String containing the version
         */
        public static String projectversion(String pomFilePath) {
            String version = "";
            try {
                version = extractVersion(pomFilePath);

            } catch (Exception e) {
                logError("problem reading the version", "mvnProjectVersion", e);
            }
            return version;
        }

        /**
         * reads the current project version, target pom file is defined by pomFilePath
         *
         * @param pomFilePath project to read
         * @return a String containing the version
         */
        public static String projectArtifactId(String pomFilePath) {
            String version = "";
            try {
                version = extractArtifactId(pomFilePath);

            } catch (Exception e) {
                logError("problem reading the version", "mvnProjectVersion", e);
            }
            return version;
        }


        public static boolean run(String message, String... arguments) {
            outputSection(message);

            InvocationRequest request = new DefaultInvocationRequest();
            request.setJavaHome(new File(System.getProperty("java.home")));
            List<String> argumentsList = new ArrayList<String>(Arrays.asList(arguments));
            System.out.println(argumentsList);
            //argumentsList.add(0, "-q");
            request.setGoals(argumentsList);
            try {
                InvocationResult result = invoker.execute(request);
                if (result.getExecutionException() != null || result.getExitCode() != 0) {
                    failedCommands.add(message);
                    return false;
                } else {
                    successFullCommands.add(message);
                }
            } catch (MavenInvocationException e) {
                LOGGER.throwing(Reactor.class.getName(), "mvn()", e);
                failedCommands.add(message);
                return false;
            }

            return true;
        }

        /**
         * operate a javadoc-jar goal on the targetted project
         * @param pomFilePath the project file path
         * @param options supplemental options
         */
        public static void javadocJar(String pomFilePath, String... options) {
            mvn.run("building javadoc jar  for " + pomFilePath, "-f " + pomFilePath, processOptions(options), "generate-sources javadoc:jar");

        }

        /**
         * reads the current project properties, target pom file is defined by pomFilePath
         *
         * @param pomFilePath project to read
         * @return a map containing the project parameters
         */
        public static Map<String, String> mvnProjectProperties(String pomFilePath) {
            Map<String, String> resultMap = new LinkedHashMap<>();
            try {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFilePath);
                NodeList properties = doc.getElementsByTagName("properties").item(0).getChildNodes();
                for (int i = 0; i < properties.getLength(); i++) {
                    Node item = properties.item(0);
                    resultMap.put(item.getNodeName(), item.getTextContent().trim());

                }
            } catch (Exception e) {
                logError("problem reading the properties", "mvnProjectProperties", e);
            }
            return resultMap;
        }

        /**
         * creates a maven project representation from a directory
         * @param projectDir the directory containing a maven project
         * @return an instance of MvnProject
         */
        public static MvnProject project(String projectDir) {
            return new MvnProject(projectDir);
        }

        /**
         * triggers a "javadoc:javadoc" goal on the targeted project
         * @param pomFilePath the pom.xml file representing the maven project
         * @param options supplemental options
         */
        public static void javadoc(String pomFilePath, String... options) {

            mvn.run("building javadoc folder (apidoc) for " + pomFilePath, "-f " + pomFilePath, processOptions(options), "generate-sources javadoc:javadoc");

        }

        /**
         * triggers a "source:jar" goal on the targeted project
         * @param pomFilePath the pom.xml file representing the maven project
         * @param options supplemental options
         */
        public static void sourcesJar(String pomFilePath, String... options) {

            String optionAsString = processOptions(options);
            mvn.run("building sources jar for " + pomFilePath, "-f " + pomFilePath, optionAsString, "generate-sources source:jar");

        }

        private static String processOptions(String[] options) {
            if (options == null) {
                return "";
            }
            String optionAsString = "";
            for (String s : options) {
                optionAsString += " " + s;
            }
            return optionAsString;
        }

    }


    private static String extractVersion(String pomFilePath) throws SAXException, IOException, ParserConfigurationException {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFilePath);

        Node parent = null;
        String version = "";

        NodeList directChildren = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < directChildren.getLength(); i++) {
            Node item = directChildren.item(i);
            if (item.getNodeName().equals("version")) {
                version = item.getTextContent().trim();
            }
            if (item.getNodeName().equals("parent")) {
                parent = item;
            }
        }
        if (version == null && parent != null) {
            Element element = (Element) parent;
            version = element.getElementsByTagName("version").item(0).getTextContent().trim();
        }
        return version;
    }

    private static String extractArtifactId(String pomFilePath) throws SAXException, IOException, ParserConfigurationException {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomFilePath);

        String artifactId = "";

        NodeList directChildren = doc.getDocumentElement().getChildNodes();
        for (int i = 0; i < directChildren.getLength(); i++) {
            Node item = directChildren.item(i);
            if (item.getNodeName().equals("artifactId")) {
                artifactId = item.getTextContent().trim();
            }
        }

        return artifactId;
    }



    /**
     * Copy files from an origin to a target with inclusions and exclusions based on the file name
     * @param origin the path to copy from
     * @param target the path to copy to
     * @param includePredicate a lambda expression to keep files to copy
     * @param excludePredicate a lambda expression to exclude files from copy
     * @return true if the copy succeeded
     */
    public static boolean copy(String origin, String target, Predicate<String> includePredicate, Predicate<String> excludePredicate) {
        File originFile = new File(origin);
        File targetFile = new File(target);
        boolean inpredicate = includePredicate != null;
        boolean expredicate = excludePredicate != null;

        try {

            Path originPath = originFile.toPath();
            final Path targetPath = targetFile.toPath();
            if (originFile.isFile()) {
                Path fileTargetPath = targetPath;
                if (targetFile.isDirectory()) {
                    fileTargetPath = targetPath.resolve(originPath.getFileName());
                }
                Files.copy(originPath, fileTargetPath, StandardCopyOption.REPLACE_EXISTING);
            }
            if (originFile.isDirectory()) {
                try (Stream<Path> stream = Files.walk(originPath)) {
                    stream.forEach(source -> {
                        // System.out.println("copy :" + source);
                        try {
                            if ((inpredicate && includePredicate.test(source.toString())) ||
                                    (expredicate && !excludePredicate.test(source.toString()))) {
                                LOGGER.finer("copying : " + source.getFileName() + " to " + target);
                                if (targetFile.isDirectory()) {
                                    Path relativize = originPath.relativize(source); //fix: keep directory hierarchy
                                    Files.copy(source, targetPath.resolve(relativize), StandardCopyOption.REPLACE_EXISTING);
                                } else {
                                    Files.copy(originPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                                }

                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        } catch (IOException e) {
            LOGGER.throwing("Reactor", "copy", e);
            failedCommands.add("copy : " + origin + " --> " + target);
            return false;
        }

        return true;
    }


    /**
     * logs a message in info using the Reactor logger
     * @param message
     */
    public static void logInfo(String message) {
        LOGGER.info(message);
    }

    /**
     * Logs an error using the Reactor logger
     * @param message
     * @param method
     * @param throwable
     */
    public static void logError(String message, String method, Throwable throwable) {
        LOGGER.severe(message + " : " + throwable.getMessage());
        LOGGER.throwing(message, method, throwable);
    }


    /**
     * gets the Reactor Logger
     * @return an instance of JUL Logger
     */
    public static Logger getLogger() {
        return LOGGER;
    }


    /** Utility method to avoid using Arrays to get a list of element
     *
     * @param elements elements to convert to a list
     * @param <T> the type of an element of that list
     * @return a java.util.List of elements
     */
    public static <T> List<T> listOf(T... elements) {
        return new ArrayList<>(Arrays.asList(elements));

    }


    /**
     * prints the summary of the integration operation
     */
    public static void printSummary() {
        outputSection("Build Summary");
        System.out.println("Successful commands :");
        for (String s : successFullCommands) {
            System.out.println("OK -> " + s);
        }
        System.out.println();
        System.out.println("Failed  commands :");
        for (String s : failedCommands) {
            System.out.println("KO -> " + s);
        }
    }


    /**
     * represents a maven project
     */
    public static class MvnProject {
        String dependenciesDir = "";
        String artifactId = "";
        String version = "";
        String pomFileName = "pom.xml";
        String targetDir = "";
        String javadocDir = "";
        String projectDir = "";
        public Map<String, String> properties;

        public MvnProject(String projectDir) {
            this.projectDir = projectDir;
            this.targetDir = projectDir + "/target";
            this.javadocDir = this.targetDir + "/site/apidocs";
            this.dependenciesDir = this.targetDir + "/dependency";
            this.version = mvn.projectversion(getPomFilePath());
            this.artifactId = mvn.projectArtifactId(getPomFilePath());
        }

        public MvnExecutor executor() {
            MvnExecutor executor = new MvnExecutor();
            executor.projectPomFile(getPomFilePath());
            executor.project = this;
            return executor;
        }

        public String getPomFilePath() {
            return this.projectDir + "/" + this.pomFileName;
        }

        public String getJavadocFilePath() {
            return this.projectDir + " / " + this.targetDir + "/" + this.javadocDir;
        }

        public MvnExecutor prepareCompoDoc() {
            return executor()
                    .userProperty("frontend.npx.arguments", "\"@compodoc/compodoc -p tsconfig.json  --language 'fr-FR' --theme 'postmark'\"")
                    .goals(FRONT_END_PLUGIN + ":npx");
        }

        public MvnExecutor prepareCleanInstall() {
            return executor().goals("clean install");
        }
        
        public MvnExecutor prepareCleanDeploy() {
        	return executor().goals("clean deploy");
        }

        public MvnExecutor prepareCopyDependencies() {
            return executor().goals("org.apache.maven.plugins:maven-dependency-plugin:3.1.2:copy-dependencies");
        }

        public MvnExecutor prepareJavadoc() {
            return executor().goals("generate-sources javadoc:javadoc");
        }

        public MvnExecutor prepareJavadocJar() {
            return executor().goals("generate-sources javadoc:jar");
        }

        public MvnExecutor prepareSourcesJar() {
            return executor().goals("generate-sources source:jar");
        }

        public String target() {
            return this.projectDir + " / " + this.targetDir;
        }
    }

    /**
     * functionnal interface to represent an operation without result
     * @param <T>
     */
    public interface Operation<T> {
        void process();
    }

    /**
     * represents an execution for a maven project.
     */
    public static class MvnExecutor {
        MvnProject project;
        StringBuilder firstArguments = new StringBuilder();
        StringBuilder arguments = new StringBuilder();
        String goals = "";
        Operation<MvnExecutor> failOperation = () -> logInfo("command status : error executing goal");
        Operation<MvnExecutor> successOperation = () -> logInfo("command status : success");

        public MvnExecutor forceUpdate() {
            firstArguments.append(" " + MVN_CLI_UPDATE + " ");
            return this;
        }

        public MvnExecutor skipTests() {
            firstArguments.append(" " + MVN_SKIP_TESTS + " ");
            return this;
        }

        public MvnExecutor quiet() {
            firstArguments.append(" " + MVN_CLI_QUIET + " ");
            return this;
        }

        public MvnExecutor goals(String goals) {
            this.goals = goals;
            return this;
        }

        public MvnExecutor onFail(Operation<MvnExecutor> op) {
            this.failOperation = op;
            return this;
        }

        public MvnExecutor onSuccess(Operation<MvnExecutor> op) {
            this.successOperation = op;
            return this;
        }


        public MvnExecutor userProperty(String key, String value) {
            arguments.append(" " + Reactor.mvnCliParam(key, value));
            return this;
        }

        public MvnExecutor projectPomFile(String pomFilePath) {
            firstArguments.append(" -f " + pomFilePath + " ");
            return this;
        }

        public MvnExecutor execute(String executionMessage) {
            if (mvn.run(this.project.artifactId + " : " + executionMessage, firstArguments.toString(), arguments.toString(), goals)) {
                successOperation.process();
            } else {
                failOperation.process();
            }
            return this;
        }


    }

}
