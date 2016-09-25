package edu.nyu.jet.ice.models;

import edu.nyu.jet.aceJet.AnchoredPath;
import edu.nyu.jet.lex.Stemmer;
import edu.nyu.jet.parser.SyntacticRelation;
import edu.nyu.jet.parser.SyntacticRelationSet;
import edu.nyu.jet.tipster.Annotation;
import edu.nyu.jet.tipster.Document;
import edu.nyu.jet.tipster.Span;
import gnu.trove.set.hash.TIntHashSet;

import java.util.*;

/**
 * A structure to save a dependency path and its lexical representation
 *
 * @author yhe
 * @version 1.0
 */
public class DepPath {

    public static final boolean DEBUG = false;

    // To store user-defined String representation. If path is set, toString() will return path,
    // otherwise, the string representation will be computed on-the-fly
    String path = null;

    // the span of the first node on the path
    private Span arg1;

    // the span of the last node on the path
    private Span arg2;

    // dependency triples on the path
    private List<SyntacticRelation> relations = new ArrayList<SyntacticRelation>();

    // the position of the head of the first node on the path
    private int start;

    // the position of the head of the last node on the path
    private int end;

    static Stemmer stemmer = Stemmer.getDefaultStemmer();

    /**
     * Constructor that allows the user to set spans for both arguments
     * If arguments are set, the linearization process will avoid putting words
     * in arguments on the linearized path.
     *
     * @param fromPosn start position of the head of arg1
     * @param toPosn start poistion of the head of arg2
     * @param arg1 Full span of the first argument
     * @param arg2 Span of the second argument
     */
    public DepPath(int fromPosn, int toPosn, Span arg1, Span arg2) {
        super();
        this.start = fromPosn;
        this.end = toPosn;
        this.arg1 = arg1;
        this.arg2 = arg2;
    }

    /*
     *  Creates a copy of a DepPath with an empty list of relations.
     */

    public DepPath copy () {
        return new DepPath(start, end, arg1, arg2);
    }

    /**
     * Define the string representation of the DepPath.
     * Suppose a DepPath records a path ARG1:prep:of:pobj:ARG2, if we call toString() without first
     * setPath(), toString() will return ARG1:prep:of:pobj:ARG2.
     * However, we might want toString() to return the transformed path. In that case, we can first
     * setPath("ARG1:prep_of:ARG2"). toString() will return the transformed path thereafter.
     * @param path User-defined string representation of the path
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Transcribe some dependency labels into words or punctuation marks in the linearized version.
     * @param role The dependency label
     * @return Transcribed dependency label
     */
    public static String lexicalContent(String role) {
        if (role.equals("appos"))
            return ",";
        if (role.startsWith("poss"))
            return "'s";
        if (role.equals("infmod"))  {
            return "to";
        }
//        if (role.startsWith("prep_")) {
//            return role.substring(5);
//        }
        if (role.equals("conj")) {
            return "and";
        }
        if (role.equals("purpcl")) {
            return "to";
        }
        return "";
    }

    /**
     * Create a new instance of DepPath from the current instance,
     * attaching r to the tail of the new instance.
     *
     * @param r A dependency relation between two words
     * @return A new DepPath
     */
    public DepPath extend(SyntacticRelation r) {
        DepPath p = new DepPath(this.start, this.end, this.arg1, this.arg2);
        for (SyntacticRelation rel : this.relations) {
            p.append(rel);
        }
        p.append(r);
        return p;
    }

    public void append(SyntacticRelation r) {
        relations.add(r);
    }

    public void clear() {
        relations.clear();
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }
    
    public List<SyntacticRelation> getRelations() {
        return relations;
    }

    public int length() {
        return relations.size();
    }

    @Override
    /**
     * Given a DepPath, transcribe it to the string form i.e. label1:word1:label2:word2: ... :labelk
     */
    public String toString() {
        if (path == null) {
            StringBuilder sb = new StringBuilder();
            int len = 0;
            for (int i = 0; i < relations.size(); i++) {
                Span targetSpan = new Span(relations.get(i).targetPosn, relations.get(i).targetPosn+1);
                if (i == relations.size() - 1 ||
                        arg1 == null ||
                        arg2 == null ||
                        !targetSpan.within(arg1) && !targetSpan.within(arg2)) {
                    if (len > 0) {
                        sb.append(":");
                    }
                    sb.append(relations.get(i).type);
                    if (i < relations.size() - 1) {
                        sb.append(":").append(relations.get(i).targetWord.replaceAll(":", "_"));
                    }
                    len++;
                }
            }
            return AnchoredPath.lemmatizePath(sb.toString());
        }
        else {
            return AnchoredPath.lemmatizePath(path);
        }
    }

    /**
     * Linearize the DepPath, given the Jet Document, the SyntacticRelationSet, and
     * the types of the entities at both ends.
     * @param doc The Jet Document
     * @param relations Dependency relations extracted from the Jet Document
     * @param type1 Entity type of the first argument
     * @param type2 Entity type of the second argument
     * @return Linearized version of the dependency path
     */
    public String linearize(Document doc, SyntacticRelationSet relations,
                            String type1, String type2) {
        // PriorityQueue is maintained as a min-heap, so that the dependency relations
        // in the heap are sorted by the offset of the governed word
        PriorityQueue<SyntacticRelation> nodes = new PriorityQueue<SyntacticRelation>(
                relations.size(),
                new RelationTargetPosnComparator()
        );
        TIntHashSet visitedOffsets = new TIntHashSet();

        int count = 1;
        // Add a "start relation" so that the entity type of the first argument will
        // be printed out.
        SyntacticRelation startRel =
                new SyntacticRelation(-1 , "", "", "NAMETAG", this.start, type1, "");
        nodes.add(startRel);
        visitedOffsets.add(this.start);
        for (SyntacticRelation r : this.relations) {
            if (count == this.relations.size()) {
                // Remove target word (the governed word) from the last dependency relation:
                // the last target word won't be printed out; we will later add an endRel
                // node to print out the entity type of the last target word on the dependency
                // path instead.
                SyntacticRelation fixedR = new SyntacticRelation(r.sourcePosn,
                        r.sourceWord, r.sourcePos, r.type, r.targetPosn,
                        "", r.targetPos);
                nodes.add(fixedR);
                visitedOffsets.add(r.targetPosn);
            }
            else {
                nodes.add(r);
                visitedOffsets.add(r.targetPosn);
            }
            // Determine if the relation is "inversed"
            // This mainly helps to decide that if we want to add a word to the linearized path,
            // where we should put the added word. For A:conj:B, "and" should be put after A, but
            // for A:conj-1:B, "and" should be put after B.
            String nodeType = r.type;
            boolean inversed = false;
            if (nodeType.endsWith("-1")) {
                nodeType = nodeType.substring(0, nodeType.length() - 2);
                inversed = true;
            }
            if (nodeType.equals("poss")) {
                inversed = !inversed;
            }
            // If the label can be transcribed to the word, add the transcribed word we obtained
            // from the dependency label to the heap.
            // (all labels will be added to the linearized path later)
            String lexicalContent = lexicalContent(nodeType);
            if (lexicalContent.length() > 0){
                int offset = inversed ? r.targetPosn + 1 : r.sourcePosn + 1;
                SyntacticRelation prepRel =
                        new SyntacticRelation(-1 , "", "", "NODETYPE",
                                offset, lexicalContent, "");
                nodes.add(prepRel);
                visitedOffsets.add(offset);
            }
            count++;
        }
        // Add an "end relation" so that the type of the second argument will be printed out.
        SyntacticRelation endRel   =
                new SyntacticRelation(-1 , "", "", "NAMETAG", this.end, type2, "");
        nodes.add(endRel);
        visitedOffsets.add(this.end);

        List<SyntacticRelation> currentNodes = new ArrayList<SyntacticRelation>();
        currentNodes.addAll(nodes);
        for (SyntacticRelation r : currentNodes) {
            //System.err.println("[POS]\t" + r.targetWord + "/" + posAt(r.targetPosn, doc));
            addVerbDependents(r, relations, doc, nodes, visitedOffsets);
        }



        StringBuilder linearizedPath = new StringBuilder();
        String lastWord = "";
        // Add all target words from the heap to the linearized path. Note that
        // nodes is an ordered heap based on targetPosn
        while (!nodes.isEmpty()) {
            SyntacticRelation node = nodes.poll();
            String targetWord = wordOnPath(node);
            // Avoid "Tom and and Jerry", "Tom and or Jerry" etc.
            if (targetWord.equals("and") ||
                    targetWord.equals("or") ||
                    targetWord.equals(",")) {
                if (!(lastWord.equals(",") ||
                        lastWord.equals("or") ||
                        lastWord.equals("and") ||
                        lastWord.equals(""))) {

                        linearizedPath.append(targetWord).append(" ");
                        lastWord = targetWord.toLowerCase().trim();
                }
            }
            else {
                // add word on the heap to the linearized path
                if (!targetWord.toLowerCase().trim().equals(lastWord)
                        || targetWord.toUpperCase().equals(targetWord)) {
                    Span targetSpan = new Span(node.targetPosn, node.targetPosn+1);

                    if (targetWord.toUpperCase().equals(targetWord) ||
                            arg1 == null ||
                            arg2 == null ||
                            (!targetSpan.within(arg1) && !targetSpan.within(arg2))) {
                        linearizedPath.append(targetWord);
                        lastWord = targetWord.toLowerCase().trim();
                        if (targetWord.length() > 0) {
                            linearizedPath.append(" ");
                        }
                    }
                }
            }
        }
        return linearizedPath.toString().trim();
    }

    private String posAt(int pos, Document doc) {
        List<Annotation> taggerAnns = doc.annotationsAt(pos, "tagger");
        String tag = null;
        if (taggerAnns != null && taggerAnns.size() > 0) {
            tag = (String)taggerAnns.get(0).get("cat");
        }
        if (tag == null) {
            tag = "?";
        }
        return tag;
    }

    private void addVerbDependents(SyntacticRelation r,
                                   SyntacticRelationSet relations,
                                   Document doc,
                                   PriorityQueue<SyntacticRelation> heap,
                                   TIntHashSet visitedOffsets) {
        if (posAt(r.targetPosn, doc).startsWith("V")) {
            SyntacticRelationSet candidates = relations.getRelationsFrom(r.targetPosn);
            for (SyntacticRelation candidate : candidates) {
                if (candidate.virtual) {
                    if (DEBUG) {
                        System.err.println("Skipped virtual node: " + candidate);
                    }
                }
                if (!candidate.virtual && (
                        candidate.type.startsWith("dobj") ||
                        candidate.type.startsWith("nsubj") ||
                        candidate.type.startsWith("iobj"))) {
                    if (!visitedOffsets.contains(candidate.targetPosn)) {
                        candidate.targetWord = "STH";
                        heap.add(candidate);
                        visitedOffsets.add(candidate.targetPosn);
                    }
//                if (posAt(candidate.targetPosn, doc).startsWith("V")) {
//                    addVerbDependents(candidate, relations, doc, heap, visitedOffsets);
//                }
                }
            }
        }

    }

    private String wordOnPath(SyntacticRelation node) {
        if (node.type.equals("NAMETAG") || node.type.equals("NODETYPE")) {
            return node.targetWord.trim();
        }
        else if (node.type.equals("dobj-1") &&
                node.sourcePosn < node.targetPosn) {
            return node.targetWord.trim();
        }
        else {
            return stemmer.getStem(node.targetWord.toLowerCase().trim(),
                    node.targetPos).trim();
        }
    }

    /**
     * Compares SyntacticRelations based on their targetPosn
     */
    class RelationTargetPosnComparator implements Comparator<SyntacticRelation> {
        public int compare(SyntacticRelation syntacticRelation, SyntacticRelation t1) {
            return syntacticRelation.targetPosn - t1.targetPosn;
        }

        public boolean equals(Object o) {
            return false;
        }
    }

}
