package edu.nyu.jet.ice.views.cli;

import Jet.JetTest;
import edu.nyu.jet.ice.controllers.Nice;
import edu.nyu.jet.ice.entityset.EntitySetIndexer;
import edu.nyu.jet.ice.models.Corpus;
import edu.nyu.jet.ice.models.IcePreprocessor;
import edu.nyu.jet.ice.models.RelationFinder;
import edu.nyu.jet.ice.uicomps.Ice;
import edu.nyu.jet.ice.utils.FileNameSchema;
import edu.nyu.jet.ice.utils.IceUtils;
import edu.nyu.jet.ice.utils.ProcessFarm;
import edu.nyu.jet.ice.views.swing.SwingEntitiesPanel;
import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;


/**
 * Command line interface for running Ice processing tasks.
 *
 * @author yhe
 * @version 1.0
 */
public class IceCLI {

    // TODO: switch this to on unless cache is already linked
    public static final boolean SHOULD_LINK_CACHE = false;

    public static void main(String[] args) {
        // create Options object
        Options options = new Options();
        Option inputDir = OptionBuilder.withLongOpt("inputDir").hasArg().withArgName("inputDirName")
                .withDescription("Location of new corpus").create("i");
        Option background = OptionBuilder.withLongOpt("background").hasArg().withArgName("backgroundCorpusName")
                .withDescription("Name of the background corpus").create("b");
        Option filter = OptionBuilder.withLongOpt("filter").hasArg().withArgName("filterFileExtension")
                .withDescription("File extension to process: sgm, txt, etc.").create("f");
        Option entityIndexCutoff = OptionBuilder.withLongOpt("entityIndexCutoff").hasArg().withArgName("cutoff")
                .withDescription("Cutoff of entity index: 1.0-25.0").create("e");
        Option numOfProcessesOpt = OptionBuilder.withLongOpt("processes").hasArg().withArgName("numOfProcesses")
                .withDescription("Num of parallel processes when adding and preprocessing corpus").create("p");
        Option targetDir = OptionBuilder.withLongOpt("targetDir").hasArg().withArgName("targetDirForCreateFrom")
                .withDescription("Directory for the new corpus, when combining old corpora").create("t");
        Option fromCorporaOpt = OptionBuilder.withLongOpt("fromCorpora").hasArg().withArgName("fromCorpora")
                .withDescription("Names for the corpora to be merged").create("s");
        options.addOption(inputDir);
        options.addOption(background);
        options.addOption(filter);
        options.addOption(entityIndexCutoff);
        options.addOption(numOfProcessesOpt);
        options.addOption(targetDir);
        options.addOption(fromCorporaOpt);

        CommandLineParser parser = new GnuParser();
        try {
            CommandLine cmd = parser.parse(options, args);
            String[] arguments = cmd.getArgs();
            if (arguments.length != 2) {
                System.err.println("Must provide exactly 2 arguments: ACTION CORPUS");
                printHelp(options);
                System.exit(-1);
            }
            String action  = arguments[0];
            String corpusName  = arguments[1];

            if (action.equals("addCorpus")) {
                int numOfProcesses = 1;
                String inputDirName = cmd.getOptionValue("inputDir");
                if (inputDirName == null) {
                    System.err.println("--inputDir must be set for the addCorpus action.");
                    printHelp(options);
                    System.exit(-1);
                }
                String filterName = cmd.getOptionValue("filter");
                if (filterName == null) {
                    System.err.println("--filter must be set for the addCorpus action.");
                    printHelp(options);
                    System.exit(-1);
                }
                String numOfProcessesStr = cmd.getOptionValue("processes");
                if (numOfProcessesStr != null) {
                    try {
                        numOfProcesses = Integer.valueOf(numOfProcessesStr);
                        if (numOfProcesses < 1) {
                            throw new Exception();
                        }
                    }
                    catch (Exception e) {
                        System.err.println("--processes only accepts an integer (>=1) as parameter");
                        printHelp(options);
                        System.exit(-1);
                    }
                }
                File inputDirFile = new File(inputDirName);
                if (inputDirFile.exists() && inputDirFile.isDirectory()) {
                    init();
                    if (Ice.corpora.containsKey(corpusName)) {
                        System.err.println("Name of corpus already exists. Please choose another name.");
                        System.exit(-1);
                    }

                    String backgroundCorpusName = cmd.getOptionValue("background");
                    if (backgroundCorpusName != null) {
                        if (!Ice.corpora.containsKey(backgroundCorpusName)) {
                            System.err.println("Cannot find background corpus. Use one of the current corpora as background:");
                            printCorporaListExcept(corpusName);
                            System.exit(-1);
                        }
                    }

                    Corpus newCorpus = new Corpus(corpusName);
                    if (backgroundCorpusName != null) {
                        newCorpus.setBackgroundCorpus(backgroundCorpusName);
                    }
                    Ice.corpora.put(corpusName, newCorpus);
                    Ice.selectCorpus(corpusName);
                    Ice.selectedCorpus.setDirectory(inputDirName);
                    Ice.selectedCorpus.setFilter(filterName);
                    Ice.selectedCorpus.writeDocumentList();
                    if (Ice.selectedCorpus.docListFileName == null) {
                        System.err.println("Unable to find any file that satisfies the filter.");
                        System.exit(-1);
                    }
                    if (numOfProcesses == 1) {
                        preprocess(filterName, backgroundCorpusName);

                    }
                    else {
                        if (backgroundCorpusName == null) {
                            System.err.println("[WARNING]\tMultiprocess preprocessing will not handle background corpus.");
                        }
                        ProcessFarm processFarm = new ProcessFarm();
                        // split corpus
                        try {
                            String[] docList = IceUtils.readLines(Ice.selectedCorpus.docListFileName);
                            int splitCount = 1;
                            int start = 0;
                            int end   = 0;
                            int portion = docList.length / numOfProcesses;
                            if (portion > 0) {
                                for (int i = 0; i < numOfProcesses; i++) {
                                    end += portion;
                                    if (i == 0) {
                                        end += docList.length % numOfProcesses;
                                    }
                                    Corpus splitCorpus = new Corpus(splitCorpusName(corpusName, splitCount));
                                    splitCorpus.setDirectory(inputDirName);
                                    splitCorpus.setFilter(filterName);
                                    String docListFileName = FileNameSchema.getDocListFileName(
                                            splitCorpusName(corpusName, splitCount));
                                    IceUtils.writeLines(docListFileName, Arrays.copyOfRange(docList,
                                            start, end));
                                    splitCorpus.setDocListFileName(docListFileName);
                                    Ice.corpora.put(splitCorpusName(corpusName, splitCount), splitCorpus);
                                    processFarm.addTask(String.format("./icecli preprocess %s",
                                            splitCorpusName(corpusName, splitCount)));
                                    start = end;
                                    splitCount++;
                                }
                            }
                            saveStatus();
                        }
                        catch (Exception e) {
                            System.err.println("Error occured when preparing to submit processes.");
                            e.printStackTrace();
                        }
                        processFarm.submit();
                        boolean success = processFarm.waitFor();
                        if (!success) {
                            System.err.println("[WARNING] processFarm returned with error. Please check log.");
                        }
                        if (numOfProcesses > 1) {
                            System.err.println("Preprocessing finished. Merging splits...");
                            mergeSplit(corpusName, numOfProcesses);
                        }
                    }
                    System.err.println("Corpus added successfully.");
                }
                else {
                    System.err.println("--inputDir should specify a valid directory.");
                    printHelp(options);
                    System.exit(-1);
                }

            }
            else if (action.equals("preprocess")) {
                init();
                validateCorpus(corpusName);
                Ice.selectCorpus(corpusName);
                preprocess(Ice.selectedCorpus.filter, Ice.selectedCorpus.backgroundCorpus);
            }
            else if (action.equals("mergeSplit")) {
                int numOfProcesses = 1;
                init();
                validateCorpus(corpusName);
                Ice.selectCorpus(corpusName);
                String numOfProcessesStr = cmd.getOptionValue("processes");
                if (numOfProcessesStr != null) {
                    try {
                        numOfProcesses = Integer.valueOf(numOfProcessesStr);
                        if (numOfProcesses < 1) {
                            throw new Exception();
                        }
                    }
                    catch (Exception e) {
                        System.err.println("--processes only accepts an integer (>=1) as parameter");
                        printHelp(options);
                        System.exit(-1);
                    }
                }
                if (numOfProcesses == 1) {
                    System.err.println("No need to corpus that has 1 split.");
                    System.exit(0);
                }
                mergeSplit(corpusName, numOfProcesses);
            }
            else if (action.equals("mergeCorporaInto")) {
                String corpusDir  = cmd.getOptionValue("targetDir");
                String filterName = cmd.getOptionValue("filter");
                String fromCorporaStr = cmd.getOptionValue("fromCorpora");
                if (corpusDir == null || filterName == null || fromCorporaStr == null) {
                    System.err.println("--targetDir --filter must be set for the mergeCorporaInto action.");
                    printHelp(options);
                    System.exit(-1);
                }
                String[] fromCorpora = fromCorporaStr.split(",");

                init();
                // validateCorpus(corpusName);
                for (String fromCorpusName : fromCorpora) {
                    validateCorpus(fromCorpusName);
                }

                Ice.selectCorpus(corpusName);
                try {
                    // create directory first
                    File dir = new File(corpusDir);
                    dir.mkdirs();

                    // create cache directory
                    String targetCacheDir = FileNameSchema.getPreprocessCacheDir(corpusName);
                    File targetCacheDirFile = new File(targetCacheDir);
                    targetCacheDirFile.mkdirs();
                    if (copyApfDTD(targetCacheDir)) return;

                    // create docList file
                    String docListFileName = FileNameSchema.getDocListFileName(corpusName);
                    PrintWriter docListFileWriter = new PrintWriter(new FileWriter(docListFileName));

                    // create all links and write docListFile
                    for (String fromCorpusName : fromCorpora) {

                        String fromDir = Ice.corpora.get(fromCorpusName).directory;
                        String fromDocListFileName = Ice.corpora.get(fromCorpusName).docListFileName;
                        String sourceCacheDir = FileNameSchema.getPreprocessCacheDir(fromCorpusName);
                        BufferedReader br = new BufferedReader(new FileReader(fromDocListFileName));
                        String line = null;
                        while ((line = br.readLine()) != null) {
                            String targetSourceFileName = (fromDir + "/" + line).replaceAll("/", "_");
                            docListFileWriter.println(targetSourceFileName);
                            targetSourceFileName = targetSourceFileName + "." + filterName;
                            File targetSourceFile = (new File(corpusDir + "/" + targetSourceFileName)).getCanonicalFile();
//                            if (targetSourceFile.exists()) {
//                                targetSourceFile.delete();
//                            }
                            String sourceSourceFileName = line + "." + filterName;
                            // create link for source file
                            Path target = targetSourceFile.toPath();
                            Path source = (new File(fromDir + "/" + sourceSourceFileName)).getCanonicalFile().toPath();
                            Files.deleteIfExists(target);
                            try {
                                Files.createSymbolicLink(target, source);
                                // now create links to cache files
                                if (SHOULD_LINK_CACHE) {
                                    linkCache(sourceCacheDir, targetCacheDir,
                                            fromDir, corpusDir,
                                            sourceSourceFileName, targetSourceFileName);
                                }
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        br.close();;
                    }

                    docListFileWriter.close();
                    Corpus newCorpus = new Corpus(corpusName);

                    Ice.corpora.put(corpusName, newCorpus);
                    Ice.selectCorpus(corpusName);
                    Ice.selectedCorpus.setDirectory(corpusDir);
                    Ice.selectedCorpus.setFilter(filterName);
                    Ice.selectedCorpus.setDocListFileName(docListFileName);
                    saveStatus();
                }
                catch(Exception e) {
                    e.printStackTrace();
                    printHelp(options);
                    System.exit(-1);
                }
            }
            else if (action.equals("setBackgroundFor")) {
                init();
                validateCorpus(corpusName);
                String backgroundCorpusName = cmd.getOptionValue("background");
                validateBackgroundCorpus(corpusName, backgroundCorpusName);
                Ice.selectCorpus(corpusName);
                Ice.selectedCorpus.backgroundCorpus = backgroundCorpusName;
                saveStatus();
                System.err.println("Background corpus set successfully.");
            }
            else if (action.equals("findEntities")) {
                init();
                validateCorpus(corpusName);
                Ice.selectCorpus(corpusName);
                validateCurrentBackground();
                SwingEntitiesPanel entitiesPanel = new SwingEntitiesPanel();
                entitiesPanel.findTerms();
                Ice.selectedCorpus.setTermFileName(FileNameSchema.getTermsFileName(Ice.selectedCorpus.getName()));
                saveStatus();
                System.err.println("Entities extracted successfully.");
            }
            else if (action.equals("indexEntities")) {
                init();
                validateCorpus(corpusName);
                Ice.selectCorpus(corpusName);
                Corpus selectedCorpus = Ice.selectedCorpus;
                String termFileName = FileNameSchema.getTermsFileName(selectedCorpus.getName());
                File termFile = new File(termFileName);
                if (!termFile.exists() || ! termFile.isFile()) {
                    System.err.println("Entities file does not exist. Please use findEntities to generate entities file first.");
                    System.exit(-1);
                }
                String cutoff = "3.0";
                String userCutoff = cmd.getOptionValue("entityIndexCutoff");
                if (userCutoff != null) {
                    try {
                        double cutOffVal = Double.valueOf(userCutoff);
                        if (cutOffVal < 1.0 || cutOffVal > 25.0) {
                            System.err.println("Please specify an entityIndexCutoff value between 1.0 and 25.0.");
                            System.exit(-1);
                        }
                    }
                    catch (Exception e) {
                        System.err.println(userCutoff + " is not a valid value of entityIndexCutoff. Please use a number between 1.0 and 25.0.");
                        System.exit(-1);
                    }
                    cutoff = userCutoff;
                }
                else {
                    System.err.println("Using default cutoff: " + cutoff);
                }
                EntitySetIndexer.main(new String[]{FileNameSchema.getTermsFileName(selectedCorpus.getName()),
                        "nn",
                        String.valueOf(cutoff),
                        "onomaprops",
                        selectedCorpus.getDocListFileName(),
                        selectedCorpus.getDirectory(),
                        selectedCorpus.getFilter(),
                        FileNameSchema.getEntitySetIndexFileName(selectedCorpus.getName(), "nn")});
                System.err.println("Entities index successfully. Please use ICE GUI to build entity sets.");

            }
            else if (action.equals("findPhrases")) {
                init();
                validateCorpus(corpusName);
                Ice.selectCorpus(corpusName);
                // validateCurrentBackground();
                RelationFinder finder = new RelationFinder(
                        Ice.selectedCorpus.docListFileName, Ice.selectedCorpus.directory,
                        Ice.selectedCorpus.filter, FileNameSchema.getRelationsFileName(Ice.selectedCorpusName),
                        FileNameSchema.getRelationTypesFileName(Ice.selectedCorpusName), null,
                        Ice.selectedCorpus.numberOfDocs,
                        null);
                finder.run();
                Ice.selectedCorpus.relationTypesFileName =
                        FileNameSchema.getRelationTypesFileName(Ice.selectedCorpus.name);
                Ice.selectedCorpus.relationInstanceFileName =
                        FileNameSchema.getRelationsFileName(Ice.selectedCorpusName);
                if (Ice.selectedCorpus.backgroundCorpus != null) {
                    System.err.println("Generating path ratio file by comparing phrases in background corpus.");
                    Corpus.rankRelations(Ice.selectedCorpusName, Ice.selectedCorpus.backgroundCorpus,
                            FileNameSchema.getPatternRatioFileName(Ice.selectedCorpusName,
                                    Ice.selectedCorpus.backgroundCorpus));
                }
                else {
                    System.err.println("Background corpus is not selected, so pattern ratio file is not generated. " +
                            "Use setBackgroundFor to set the background corpus.");
                }
                saveStatus();
                System.err.println("Phrases extracted successfully.");
            }
            else {
                System.err.println("Invalid action: " + action);
                printHelp(options);
                System.exit(-1);
            }
        }
        catch (MissingOptionException e) {
            System.err.println(e.getMessage());
            printHelp(options);
            System.exit(-1);
        }
        catch (ParseException e) {
            System.err.println(e.getMessage());
            printHelp(options);
            System.exit(-1);
        }
    }

    public static boolean copyApfDTD(String targetCacheDir) throws IOException {
        Properties props = new Properties();
        props.load(new FileReader("parseprops"));
        try {
            FileUtils.copyFile(new File(props.getProperty("Jet.dataPath") + File.separator + "apf.v5.1.1.dtd"),
                    new File(targetCacheDir + File.separator + "apf.v5.1.1.dtd"));
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        }
        return false;
    }

    /**
     * Merge preprocessed splits for a corpus by creating hard links for each
     * cache file
     * @param corpusName name of the corpus
     * @param numOfProcesses number of splits
     */
    public static void mergeSplit(String corpusName, int numOfProcesses) {
        String origCorpusName = corpusName;
        String origPreprocessDir = FileNameSchema.getPreprocessCacheDir(origCorpusName);
        File origPreprocessDirFile = new File(origPreprocessDir);
        origPreprocessDirFile.mkdirs();
        try {
            copyApfDTD(origPreprocessDir);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        String suffixName = Ice.selectedCorpus.filter;

        for (int i = 1; i < numOfProcesses + 1; i++) {
            try {
                String currentCorpusName = splitCorpusName(corpusName, i);
                Ice.selectCorpus(currentCorpusName);
                String currentPreprocessDir = FileNameSchema.getPreprocessCacheDir(currentCorpusName);

                String[] docList = IceUtils.readLines(Ice.selectedCorpus.docListFileName);
                for (String docName : docList) {
                    docName = docName + "." + Ice.selectedCorpus.filter;
                    linkCache(origPreprocessDir, currentPreprocessDir,
                            Ice.selectedCorpus.directory, Ice.selectedCorpus.directory,
                            docName, docName);
                }
            }
            catch (Exception e) {
                System.err.println("Problem merging split " + i);
                e.printStackTrace();
            }
        }
    }

    public static void linkCache(String sourceCacheDir,
                                 String targetCacheDir,
                                 String sourceSourceDir,
                                 String targetSourceDir,
                                 String sourceDocName,
                                 String targetDocName) throws IOException {
        String sourceFileName = IcePreprocessor.getAceFileName(sourceCacheDir,
                sourceSourceDir,
                sourceDocName
        );
        String targetFileName = IcePreprocessor.getAceFileName(targetCacheDir,
                targetSourceDir,
                targetDocName);
        linkFile(sourceFileName, targetFileName);


        sourceFileName = IcePreprocessor.getDepFileName(sourceCacheDir,
                sourceSourceDir,
                sourceDocName
        );
        targetFileName = IcePreprocessor.getDepFileName(targetCacheDir,
                targetSourceDir,
                targetDocName);
        linkFile(sourceFileName, targetFileName);



        sourceFileName = IcePreprocessor.getJetExtentsFileName(sourceCacheDir,
                sourceSourceDir,
                sourceDocName
        );
        targetFileName = IcePreprocessor.getJetExtentsFileName(targetCacheDir,
                targetSourceDir,
                targetDocName);
        linkFile(sourceFileName, targetFileName);



        sourceFileName = IcePreprocessor.getNamesFileName(sourceCacheDir,
                sourceSourceDir,
                sourceDocName
        );
        targetFileName = IcePreprocessor.getNamesFileName(targetCacheDir,
                targetSourceDir,
                targetDocName);
        linkFile(sourceFileName, targetFileName);



        sourceFileName = IcePreprocessor.getNpsFileName(sourceCacheDir,
                sourceSourceDir,
                sourceDocName
        );
        targetFileName = IcePreprocessor.getNpsFileName(targetCacheDir,
                targetSourceDir,
                targetDocName);
        linkFile(sourceFileName, targetFileName);



        sourceFileName = IcePreprocessor.getPosFileName(sourceCacheDir,
                sourceSourceDir,
                sourceDocName
        );
        targetFileName = IcePreprocessor.getPosFileName(targetCacheDir,
                targetSourceDir,
                targetDocName);
        linkFile(sourceFileName, targetFileName);

    }

    private static void linkFile(String sourceFileName, String targetFileName) throws IOException {
        File targetFile = (new File(targetFileName)).getCanonicalFile();
        Path target = targetFile.toPath();
        Path source = (new File(sourceFileName)).getCanonicalFile().toPath();
        Files.deleteIfExists(target);
        Files.createSymbolicLink(target, source);
    }

    public static String splitCorpusName(String corpusName, int splitCount) {
        return "." + corpusName + "_split_" + splitCount;
    }

    public static void preprocess(String filterName, String backgroundCorpusName) {
        IcePreprocessor icePreprocessor = new IcePreprocessor(
                Ice.selectedCorpus.directory,
                Ice.iceProperties.getProperty("Ice.IcePreprocessor.parseprops"),
                Ice.selectedCorpus.docListFileName,
                filterName,
                FileNameSchema.getPreprocessCacheDir(Ice.selectedCorpusName)
        );
        icePreprocessor.run();
        saveStatus();
        if (backgroundCorpusName == null) {
            System.err.println("[WARNING]\tBackground corpus is not set.");
        }
    }

    private static void validateCurrentBackground() {
        if (Ice.selectedCorpus.backgroundCorpus == null) {
            System.err.println("Background corpus is not set yet. Please use setBackground to pick a background corpus.");
            System.exit(-1);
        }
    }

    private static void validateCorpus(String corpusName) {
        if (!Ice.corpora.containsKey(corpusName)) {
            System.err.println(String.format("corpusName:%s does not exist. Please pick a corpus from the list below:",
                corpusName));
            printCorporaList();
            System.exit(-1);
        }
    }

    private static void validateBackgroundCorpus(String corpusName, String backgroundCorpusName) {
        if (backgroundCorpusName != null) {
            if (!Ice.corpora.containsKey(backgroundCorpusName)) {
                System.err.println("Cannot find background corpus. Use one of the current corpora as background:");
                printCorporaListExcept(corpusName);
                System.exit(-1);
            }
            else if (corpusName.equals(backgroundCorpusName)) {
                System.err.println("Foreground and background corpus should not be the same.");
                System.exit(-1);
            }
        }
        else {
            System.err.println("--background must be set for the selected action.");
            System.exit(-1);
        }
    }

    private static void saveStatus() {
        try {
            Nice.saveStatus();
        }
        catch (Exception e) {
            System.err.println("Unable to save status. Please check if the ice config file is writable.");
            System.err.println(-1);
        }
    }

    private static void printCorporaList() {
        for (String key : Ice.corpora.keySet()) {
            System.err.println("\t" + key);
        }
    }

    private static void printCorporaListExcept(String corpusName) {
        for (String key : Ice.corpora.keySet()) {
            if (!key.equals(corpusName)) {
                System.err.println("\t" + key);
            }
        }
    }

    public static void init() {
        Nice.printCover();
        Properties iceProperties = Nice.loadIceProperties();
        Nice.initIce();
        //Nice.loadPathMatcher(iceProperties);
    }

    private static void printHelp(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("IceCLI ACTION CORPUS [OPTIONS]\n" +
                        "ACTION=addCorpus|mergeCorporaInto|setBackgroundFor|findEntities|indexEntities|findPhrases",
                options);
    }

}
