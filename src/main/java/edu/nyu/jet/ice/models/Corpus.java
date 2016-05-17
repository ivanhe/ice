package edu.nyu.jet.ice.models;// -*- tab-width: 4 -*-
//Title:        JET-ICE
//Version:      1.72
//Copyright:    Copyright (c) 2014
//Author:       Ralph Grishman
//Description:  A Java-based Information Extraction Tool -- Customization Environment

import edu.nyu.jet.ice.uicomps.EntitySetBuilder;
import edu.nyu.jet.ice.uicomps.Ice;
import edu.nyu.jet.ice.uicomps.RelationBuilder;
import edu.nyu.jet.ice.uicomps.ListFilter;
import edu.nyu.jet.ice.uicomps.TermFilter;
import edu.nyu.jet.ice.uicomps.RelationFilter;
import edu.nyu.jet.ice.utils.FileNameSchema;
import edu.nyu.jet.ice.utils.IceUtils;
import edu.nyu.jet.ice.utils.ProgressMonitorI;
import edu.nyu.jet.ice.utils.Ratio;
import edu.nyu.jet.ice.utils.SwingProgressMonitor;
import edu.nyu.jet.ice.terminology.TermCounter;
import edu.nyu.jet.ice.terminology.TermRanker;

//import java.nio.file.Files;
//import java.nio.file.Paths;
import java.awt.*;
import java.util.*;
import java.io.*;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.event.*;
import java.util.List;

public class Corpus {

    // properties
    public String name;
    public String directory;
    public String filter;
    public int numberOfDocs;
    public String docListFileName;
    // for term finder
    public String wordCountFileName;
    public String backgroundCorpus;
    public String termFileName;
    // for relation finder
    public String relationTypesFileName;
    public String relationInstanceFileName;
    public RelationBuilder relationBuilder;

    public TermFilter termFilter = new TermFilter();
    static public RelationFilter relationFilter = new RelationFilter();
    public EntitySetBuilder entitySetBuilder = new EntitySetBuilder();

    // property methods
    public String getName() {
        return name;
    }

    public void setName(String s) {
        name = s;
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String s) {
        directory = s;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String s) {
        String corpusInfoDirectory = FileNameSchema.getCorpusInfoDirectory(name);
        //Files.createDirectory(Paths.get(corpusInfoDirectory));
        File dir = new File(corpusInfoDirectory);
        dir.mkdirs();
        filter = s;
    }

    public int getNumberOfDocs() {
        return numberOfDocs;
    }

    public void setNumberOfDocs(int n) {
        numberOfDocs = n;
    }

    public String getDocListFileName() {
        return docListFileName;
    }

    public void setDocListFileName(String s) {
        docListFileName = s;
    }

    public String getWordCountFileName() {
        return wordCountFileName;
    }

    public void setWordCountFileName(String s) {
        wordCountFileName = s;
    }

    public String getBackgroundCorpus() {
        return backgroundCorpus;
    }

    public void setBackgroundCorpus(String s) {
        backgroundCorpus = s;
    }

    public String getTermFileName() {
        return termFileName;
    }

    public void setTermFileName(String s) {
        termFileName = s;
    }

    public String getRelationTypesFileName() {
        return relationTypesFileName;
    }

	public void setRelationTypesFileName(String s) {
		this.relationTypesFileName = s;
	}

	public void setRelationInstanceFileName(String s) {
		this.relationInstanceFileName = s;
	}

    ProgressMonitorI wordProgressMonitor;

    public Corpus(String name) {
        this.name = name;
        directory = "?";
        filter = "?";
        relationBuilder = new RelationBuilder();

        try {
            String corpusInfoDirectory = FileNameSchema.getCorpusInfoDirectory(name);
            //Files.createDirectory(Paths.get(corpusInfoDirectory));
            File dir = new File(corpusInfoDirectory);
            dir.mkdirs();
        }
        catch(Exception e) {
            System.err.println("Unable to create info directory: " + e.getMessage());
        }
    }

    public Corpus() {
        this("?");
    }

    //List<JRadioButton> backgroundCorpusButtons = new ArrayList<JRadioButton>();
    JTextArea textArea;
    JTextArea relationTextArea;


    public void countWords(ProgressMonitorI monitor) {
        wordCountFileName = FileNameSchema.getWordCountFileName(name);
        //WordCounter counter = new WordCounter(
        //        docListFileName, directory, filter, wordCountFileName);

        //Words.progressMonitor = wordProgressMonitor;
        //counter.start();
        String[] docFileNames = null;
        try {
            docFileNames = IceUtils.readLines(FileNameSchema.getDocListFileName(name));
        }
        catch (IOException e) {
            e.printStackTrace(System.err);
            return;
        }
        TermCounter counter = TermCounter.prepareRun("onomaprops",
                Arrays.asList(docFileNames),
                directory,
                filter,
                wordCountFileName,
                monitor);
        counter.start();
    }

    public void findTerms(Corpus bgCorpus) {

        termFileName = FileNameSchema.getTermsFileName(name);
//                run("Terms", "utils/findTerms " + wordCountFileName + " " +
//                        Ice.corpora.get(backgroundCorpus).wordCountFileName + " " +
//                        termFileName);
        String ratioFileName = this.name + "-" + backgroundCorpus + "-" + "Ratio";
        try {
            //Ratio.main(new String[] {wordCountFileName, Ice.corpora.get(backgroundCorpus).wordCountFileName, ratioFileName});
            //IceUtils.numsort(ratioFileName, termFileName);
            TermRanker.rankTerms(wordCountFileName, bgCorpus.wordCountFileName, termFileName);
        }
        catch (IOException e) {
            e.printStackTrace(System.err);
            return;
        }
    }

    /**
     * box for term finder
     */

    public Box termBox() {

        Box box = Box.createVerticalBox();
        box.setMinimumSize(new Dimension(480, 400));
        TitledBorder border = new TitledBorder("Find Terms");
        border.setTitleColor(Color.RED);
        box.setBorder(border);

        Box countAndRatioBox = Box.createHorizontalBox();

        JButton countWordsButton = new JButton("count words");
        countAndRatioBox.add(countWordsButton);
        JButton findTermsButton = new JButton("find terms");
        // findTermsButton.setEnabled(wordCountFileName != null);
        countAndRatioBox.add(findTermsButton);
        box.add(countAndRatioBox);

        box.add(termFilter.makeBox());

        textArea = new JTextArea(5, 30);
        JScrollPane scrollPane = new JScrollPane(textArea);
        if (termFileName != null)
            displayTerms(termFileName, 100, textArea, termFilter);
        box.add(scrollPane);

        // listeners -----------

        countWordsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {

                wordProgressMonitor =
                        new SwingProgressMonitor(Ice.mainFrame, "Counting words",
                                "Initializing Jet", 0, numberOfDocs);
                ((SwingProgressMonitor)wordProgressMonitor).setMillisToDecideToPopup(5);
                countWords(wordProgressMonitor);
            }
        });

        findTermsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {

                if (backgroundCorpus == null || !Ice.corpora.containsKey(backgroundCorpus)) {
                    JOptionPane.showMessageDialog(Ice.mainFrame, "Please choose background corpus first!");
                    return;
                }

                if (wordCountFileName == null) {
                    JOptionPane.showMessageDialog(Ice.mainFrame, "Please count words first!");
                }

                findTerms(Ice.corpora.get(backgroundCorpus));
                displayTerms(termFileName, 100, textArea, termFilter);
            }
        });

        return box;
    }

    /**
     * box for term finder
     */

    public Box swingTermBox() {

        Box box = Box.createVerticalBox();
        box.setOpaque(false);
        box.setMinimumSize(new Dimension(480, 270));
        TitledBorder border = new TitledBorder("Find Entities");
        //border.setTitleColor(Color.RED);
        box.setBorder(border);

        Box countAndRatioBox = Box.createHorizontalBox();

        JButton countWordsButton = new JButton("Count words");
        countAndRatioBox.add(countWordsButton);
        JButton findTermsButton = new JButton("Find entities");
        // findTermsButton.setEnabled(wordCountFileName != null);
        countAndRatioBox.add(findTermsButton);
        box.add(countAndRatioBox);

        box.add(termFilter.makeBox());

        textArea = new JTextArea(8, 20);
        JScrollPane scrollPane = new JScrollPane(textArea);
        if (termFileName != null)
            displayTerms(termFileName, 100, textArea, termFilter);
        box.add(scrollPane);

        // listeners -----------

        countWordsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {

                wordProgressMonitor =
                        new SwingProgressMonitor(Ice.mainFrame, "Counting words",
                                "Initializing Jet", 0, numberOfDocs);
                countWords(wordProgressMonitor);
            }
        });

        findTermsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {

                if (backgroundCorpus == null || !Ice.corpora.containsKey(backgroundCorpus)) {
                    JOptionPane.showMessageDialog(Ice.mainFrame, "Please choose background corpus first!");
                    return;
                }

                if (wordCountFileName == null) {
                    JOptionPane.showMessageDialog(Ice.mainFrame, "Please count words first!");
                }

                findTerms(Ice.corpora.get(backgroundCorpus));
                displayTerms(termFileName, 100, textArea, termFilter);
            }
        });

        return box;
    }


    public void checkForAndFindRelations(ProgressMonitorI progressMonitor, RelationFilter relationFilter) {
        relationInstanceFileName = FileNameSchema.getRelationsFileName(this.name);//name + "Relations";
        relationTypesFileName = FileNameSchema.getRelationTypesFileName(this.name);//name + "Relationtypes";
        File file = new File(relationTypesFileName);
        if (file.exists() &&
                !file.isDirectory()) {
            int n = JOptionPane.showConfirmDialog(
                    Ice.mainFrame,
                    "Extracted patterns already exist. Show existing patterns without recomputation?",
                    "Patterns exist",
                    JOptionPane.YES_NO_OPTION);
            if (n == 0) {
                Corpus.displayTerms(relationTypesFileName,
                        40,
                        relationTextArea,
                        relationFilter);
                return;
            }
        }
        relationFilter.sententialPatternCheckBox.setSelected(false);
        relationFilter.onlySententialPatterns = false;
        findRelations(progressMonitor, "", relationTextArea);
    }

    public void findRelations(ProgressMonitorI progressMonitor, String docListPrefix, JTextArea publishToTextArea) {
        relationInstanceFileName = FileNameSchema.getRelationsFileName(name);
        relationTypesFileName = FileNameSchema.getRelationTypesFileName(name);
        docListFileName = FileNameSchema.getDocListFileName(name);


        RelationFinder finder = new RelationFinder(
                docListFileName, directory, filter, relationInstanceFileName,
                relationTypesFileName, publishToTextArea, numberOfDocs,
                progressMonitor);
        finder.start();
    }

    public void rankRelations() {
        String ratioFileName = FileNameSchema.getPatternRatioFileName(name, backgroundCorpus);
        rankRelations(this.name, backgroundCorpus, ratioFileName);
    }

    public static void rankRelations(String fgCorpus, String bgCorpus, String fileName) {
        try {
            String sortedRatioFileName = fileName + ".sorted";
            Ratio.main(new String[]{
                    FileNameSchema.getRelationTypesFileName(fgCorpus),
                    FileNameSchema.getRelationTypesFileName(bgCorpus),
                    fileName
            });
            IceUtils.numsort(fileName, sortedRatioFileName);

            // return getTerms(sortedRatioFileName, 40, relationFilter);
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(Ice.mainFrame,
                    "Error ranking patterns. Please make sure both fore and background patterns are counted.",
                    "Error ranking patterns",
                    JOptionPane.ERROR_MESSAGE);
            e.printStackTrace(System.err);
        }
        //return new ArrayList<String>();
    }

    /**
     * box for relation finder:  displays frequent patterns
     */

    public Box swingPatternBox() {

        Box box = Box.createVerticalBox();
        box.setOpaque(false);
        box.setMinimumSize(new Dimension(500, 300));
        TitledBorder border = new TitledBorder("Find Phrases");
        // border.setTitleColor(Color.RED);
        box.setBorder(border);

        Box buttonsBox = Box.createHorizontalBox();
        JButton findRelationsButton = new JButton("Find Phrases");
        JButton rankRelationsButton = new JButton("Rank Phrases");
        buttonsBox.add(findRelationsButton);
        buttonsBox.add(rankRelationsButton);
        box.add(buttonsBox);

        box.add(relationFilter.makeBox());

        relationTextArea = new JTextArea(10, 30);
        JScrollPane scrollPane = new JScrollPane(relationTextArea);
        relationFilter.setArea(relationTextArea);

        if (relationTypesFileName != null)
            //Corpus.displayTerms(relationTypesFileName, 40, relationTextArea, null);

            Corpus.displayTerms(relationTypesFileName, 40, relationTextArea, relationFilter);
        box.add(scrollPane);

        // listener -----

        findRelationsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                checkForAndFindRelations(new SwingProgressMonitor(Ice.mainFrame,
                        "Extracting relation phrases",
                        "Initializing Jet", 0, numberOfDocs), relationFilter);
            }
        });

        rankRelationsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                rankRelations();

                String ratioFileName = FileNameSchema.getPatternRatioFileName(name, backgroundCorpus);
                String sortedRatioFileName = ratioFileName + ".sorted";
                Corpus.displayTerms(sortedRatioFileName, 40, relationTextArea, relationFilter);
            }
        });

        return box;
    }


    /**
     * box for relation finder:  displays frequent patterns
     */

    public Box patternBox() {

        Box box = Box.createVerticalBox();
        TitledBorder border = new TitledBorder("Find Phrases");
        border.setTitleColor(Color.RED);
        box.setBorder(border);

        Box buttonsBox = Box.createHorizontalBox();
        JButton findRelationsButton = new JButton("Find Phrases");
        JButton rankRelationsButton = new JButton("Rank Phrases");
        buttonsBox.add(findRelationsButton);
        buttonsBox.add(rankRelationsButton);
        box.add(buttonsBox);

        box.add(relationFilter.makeBox());

        relationTextArea = new JTextArea(5, 30);
        JScrollPane scrollPane = new JScrollPane(relationTextArea);
        relationFilter.setArea(relationTextArea);

        if (relationTypesFileName != null)
            //Corpus.displayTerms(relationTypesFileName, 40, relationTextArea, null);

            Corpus.displayTerms(relationTypesFileName, 40, relationTextArea, relationFilter);
        box.add(scrollPane);

        // listener -----

        findRelationsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                checkForAndFindRelations(new SwingProgressMonitor(Ice.mainFrame, "Extracting relation phrases",
                                "Initializing Jet", 0, numberOfDocs), relationFilter);
            }
        });

        rankRelationsButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                rankRelations();

                String ratioFileName = FileNameSchema.getPatternRatioFileName(name, backgroundCorpus);
                String sortedRatioFileName = ratioFileName + ".sorted";
                Corpus.displayTerms(sortedRatioFileName, 40, relationTextArea, relationFilter);
            }
        });

        return box;
    }

    static void sort(String infile, String outfile) throws IOException{
        //run("Sort", "./numsort " + infile + " " + outfile);
        IceUtils.numsort(infile, outfile);
    }

    String[] buildDocumentList() {
        if (directory.equals("?") || filter.equals("?")) return null;
        //File dir = new File(directory);
        //String[] docs = dir.list(new Jet.IceModels.CorpusFileFilter(filter));
        String[] docs = findAllFiles(directory, filter).toArray(new String[0]);
        numberOfDocs = docs.length;
        System.out.println("Directory has " + numberOfDocs + " files.");
        return docs;
    }

    public static List<String> findAllFiles(String sDir, String filter) {
        return findAllFiles(sDir, sDir, filter);
    }

    private static List<String> findAllFiles(String sDir, String topDir, String filter) {
        List<String> allFiles = new ArrayList<String>();
        File[] faFiles = new File(sDir).listFiles();
        String topPath = new File(topDir).getAbsolutePath();
        for (File file : faFiles) {
            // Added basic support for * wildcard to select all files.
            if (file.isFile() &&
                    ("*".equals(filter.trim()) ||
                        file.getName().endsWith(String.format(".%s", filter))
                    )
            ) {
                allFiles.add(file.getAbsolutePath().substring(topPath.length() + 1));
            }
            if (file.isDirectory()) {
                allFiles.addAll(findAllFiles(file.getAbsolutePath(), topDir, filter));
            }
        }
        return allFiles;
    }


    public void writeDocumentList() {
        String[] docs = buildDocumentList();
        if (docs == null) return;
        try {
			docListFileName = FileNameSchema.getDocListFileName(name);
            File docListFile = new File(docListFileName);
            PrintWriter writer = new PrintWriter(new FileWriter(docListFile));
            for (String doc : docs) {

                if ("*".equals(filter.trim())) {
                    writer.println(doc);
                } else {
                    String nameWithoutSuffix =
                            doc.substring(0, doc.length() - filter.length() - 1);
                    writer.println(nameWithoutSuffix);
                }
            }
            writer.close();
        } catch (IOException e) {
            System.err.println("Error writing document List");
            e.printStackTrace();
        }
    }

    public List<String> getRelations(int limit) {
        relationTypesFileName = FileNameSchema.getRelationTypesFileName(name);
        return getTerms(relationTypesFileName, limit, Corpus.relationFilter);
    }

    public static List<String> getTerms(String termFile, int limit, ListFilter tf) {
		String corpusName = FileNameSchema.getCorpusNameFromTermsFile(termFile);
        List<String> topTerms = new ArrayList<String>();
        int k = 0;
        DepPathMap depPathMap = DepPathMap.getInstance(corpusName);
        boolean displayRelation = false;
        if (tf != null && tf instanceof RelationFilter) {
            // depPathMap.clear();
            displayRelation = true;
        }
        try {
            BufferedReader reader = new BufferedReader(new FileReader(termFile));
            // System.out.println("TERMFILE: " + termFile);
//            File exampleFile = new File(termFile + ".source.dict");
//            BufferedReader exampleReader = null;
//            if (exampleFile.exists() && !exampleFile.isDirectory()) {
//                exampleReader = new BufferedReader(new FileReader(exampleFile));
//            }
            boolean shouldReload = !depPathMap.load();
            if (shouldReload) {
                depPathMap.clear();
            }
            while (true) {
                String term = reader.readLine();
//                String example = "";
//                if (exampleReader != null) {
//                    example = exampleReader.readLine();
//                }
                if (term == null) break;
                if (tf != null && !tf.filter(term)) continue;
                if (displayRelation) {
                    String[] parts = term.split("\t");
//                    if (example.indexOf("|||") > -1) {
//                        example = example.split("\\|\\|\\|")[1].trim();
//                    }
//                    if (shouldReload) {
//                        if (parts.length == 2 &&
//                                depPathMap.addPath(parts[1], example) == DepPathMap.AddStatus.SUCCESSFUL) {
//                            topTerms.add(parts[0] + "\t" + depPathMap.findRepr(parts[1]));
//                        }
//                    }
//                    else {
                        String repr = depPathMap.findRepr(parts[1]);
                        if (repr != null) {
                            topTerms.add(parts[0] + "\t" + repr);
                        }
//                    }
                }
                else {
                    topTerms.add(term);
                    k++;
                }
                if (k >= limit) break;
            }
			reader.close();
            if (displayRelation && shouldReload) {
                depPathMap.persist();
                System.err.println("DepPathMap saved.");
            }
//            if (null != exampleReader) {
//    			exampleReader.close();
//            }
        } catch (IOException e) {
            System.out.println("IOException");
        }
        return topTerms;
    }

    /**
     * display the first (up to) 'limit' lines of file 'termFile' in
     * TextArea 'textArea'.
     */

    public static void displayTerms(String termFile, int limit, JTextArea textArea, ListFilter tf) {
        if (null == textArea) {
            System.err.println("displayTerms() returning, no text area to publish to.");
            return;
        }
        List<String> topTerms = getTerms(termFile, limit, tf);
        textArea.setText("");
        for (String term : topTerms)
            textArea.append(term + "\n");
    }
}

class CorpusFileFilter implements FilenameFilter {

    String suffix;

    CorpusFileFilter(String suffix) {
        this.suffix = suffix;
    }

    public boolean accept(File dir, String name) {
        return name.endsWith("." + suffix);
    }
}

/**
 * count all words in corpus.
 */

class WordCounter extends Thread {

    String[] args;

    WordCounter(String docListFileName, String directory, String filter,
                String wordCountFileName) {
        args = new String[5];
        args[0] = "onomaprops";
        args[1] = docListFileName;
        args[2] = directory;
        args[3] = filter;
        args[4] = wordCountFileName;
    }

    public void run() {
        try {
            Words.main(args);
        } catch (IOException e) {
            System.out.println("IOException in Words " + e);
        }
    }
}


