package edu.nyu.jet.ice.uicomps;

import edu.nyu.jet.ice.models.DepPathMap;
import edu.nyu.jet.ice.models.IcePath;
import edu.nyu.jet.ice.utils.SwingProgressMonitor;
import edu.nyu.jet.ice.relation.Bootstrap;
import edu.nyu.jet.ice.views.swing.SwingRelationsPanel;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.Position;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Describe the code here
 *
 * @author yhe
 * @version 1.0
 */
public class RelationBuilderFrame extends JFrame {
    private Bootstrap bootstrap;
    public JScrollPane listPane;
    public JList rankedList;
    public DefaultListModel rankedListModel = new DefaultListModel();
    public JRadioButton yesButton;
    public JRadioButton noButton;
    public JRadioButton undecidedButton;
    public RelationBuilder relationBuilder;
    public SwingRelationsPanel swingRelationsPanel;

    public RelationBuilderFrame(String title, final RelationBuilder relationBuilder, final Bootstrap bootstrap,
                                final SwingRelationsPanel swingRelationsPanel) {
        super(title);
        this.bootstrap = bootstrap;
        this.relationBuilder = relationBuilder;
        this.swingRelationsPanel = swingRelationsPanel;
        JPanel entitySetPanel = new JPanel(new MigLayout());
        entitySetPanel.setSize(400, 700);

        JLabel rankedListLabel = new JLabel("Bootstrapped patterns");
        entitySetPanel.add(rankedListLabel, "wrap");

        rankedList = new JList(rankedListModel){
            @Override
            public int getNextMatch(String prefix, int startIndex, Position.Bias bias) {
                return -1;
            }
        };
        listPane = new JScrollPane(rankedList,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        listPane.setSize(new Dimension(350, 360));
        listPane.setPreferredSize(new Dimension(350, 360));
        listPane.setMinimumSize(new Dimension(350, 360));
        listPane.setMaximumSize(new Dimension(350, 360));
        JPanel decisionPanel = new JPanel(new MigLayout());
        TitledBorder border = new TitledBorder("Decision");
        decisionPanel.setBorder(border);
        decisionPanel.setSize(new Dimension(350, 100));
        decisionPanel.setPreferredSize(new Dimension(350, 100));
        decisionPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        yesButton = new JRadioButton("Yes");
        yesButton.setActionCommand("YES");
        noButton = new JRadioButton("No");
        noButton.setActionCommand("NO");
        undecidedButton = new JRadioButton("Undecided");
        undecidedButton.setActionCommand("UNDECIDED");

        ButtonGroup group = new ButtonGroup();
        group.add(yesButton);
        group.add(noButton);
        group.add(undecidedButton);
        decisionPanel.add(yesButton);
        decisionPanel.add(noButton);
        decisionPanel.add(undecidedButton);
        ActionListener decisionActionListener = new BootstrappingActionListener(this);
        yesButton.addActionListener(decisionActionListener);
        noButton.addActionListener(decisionActionListener);
        undecidedButton.addActionListener(decisionActionListener);

        entitySetPanel.add(listPane, "wrap");
        entitySetPanel.add(decisionPanel, "wrap");

        JPanel actionButtonsPanel = new JPanel(new MigLayout());
        JButton iterateButton = new JButton("Iterate");
        JButton saveButton = new JButton("Save");
        JButton exitButton = new JButton("Exit");
        actionButtonsPanel.add(iterateButton);
        actionButtonsPanel.add(saveButton);
        actionButtonsPanel.add(exitButton);
        entitySetPanel.add(actionButtonsPanel);
        this.add(entitySetPanel);

        // listeners...
        rankedList.addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent listSelectionEvent) {
                int idx = rankedList.getSelectedIndex();
                if (idx < 0) return;
                IcePath e = (IcePath) rankedListModel.getElementAt(idx);
                if (e.getChoice() == IcePath.IcePathChoice.YES) {
                    yesButton.setSelected(true);
                }
                if (e.getChoice() == IcePath.IcePathChoice.NO) {
                    noButton.setSelected(true);
                }
                if (e.getChoice() == IcePath.IcePathChoice.UNDECIDED) {
                    undecidedButton.setSelected(true);
                }
            }
        });

        iterateButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
		String corpusName = Ice.selectedCorpus.getName();
                java.util.List<IcePath> approvedPaths = new ArrayList<IcePath>();
                java.util.List<IcePath> rejectedPaths = new ArrayList<IcePath>();
                for (Object o : rankedListModel.toArray()) {
                    IcePath e = (IcePath) o;
                    if (e.getChoice() == IcePath.IcePathChoice.YES) {
                        approvedPaths.add(e);
                    }
                    else {
                        if (e.getChoice() == IcePath.IcePathChoice.NO) {
                            rejectedPaths.add(e);
                        }
                    }
                }

                bootstrap.setProgressMonitor(new SwingProgressMonitor(
                        RelationBuilderFrame.this, "Bootstrapping",
                        "Collecting seeds...",
                        0,
                        5
                ));

                BootstrapIterateThread thread = new BootstrapIterateThread(
		   corpusName,
		   bootstrap,
		   approvedPaths,
		   rejectedPaths,
		   RelationBuilderFrame.this
									   );
                thread.start();
            }
        });

        saveButton.addActionListener(new ActionListener() {
          public void actionPerformed(ActionEvent actionEvent) {
	    String corpusName = Ice.selectedCorpus.getName();
	    java.util.List<IcePath> approvedPaths = new ArrayList<IcePath>();
	    java.util.List<IcePath> rejectedPaths = new ArrayList<IcePath>();
	    for (Object o : rankedListModel.toArray()) {
		IcePath e = (IcePath) o;
		if (e.getChoice() == IcePath.IcePathChoice.YES) {
		    approvedPaths.add(e);
		}
		if (e.getChoice() == IcePath.IcePathChoice.NO) {
		    rejectedPaths.add(e);
		}
	    }

	    bootstrap.addPathsToSeedSet(approvedPaths, bootstrap.getSeedPaths());
	    bootstrap.addPathsToSeedSet(rejectedPaths, bootstrap.getRejects());
	    DepPathMap depPathMap = DepPathMap.getInstance(corpusName);
	    StringBuilder text = new StringBuilder();
	    Set<String> usedRepr = new HashSet<String>();
	    for (String path : bootstrap.getSeedPaths()) {
		StringBuilder t = new StringBuilder();
		t.append(bootstrap.getArg1Type())
		    .append(" -- ")
		    .append(path)
		    .append(" -- ")
		    .append(bootstrap.getArg2Type());
		String repr = depPathMap.findRepr(t.toString());
		if (repr != null && !usedRepr.contains(repr)) {
		    text.append(repr).append("\n");
		    usedRepr.add(repr);
		}
	    }
	    if (relationBuilder != null) {
		relationBuilder.textArea.setText(text.toString());
	    }
	    if (swingRelationsPanel != null) {
		String[] reprs = text.toString().trim().split("\n");
		java.util.List<String> paths = Arrays.asList(reprs);
		swingRelationsPanel.updateEntriesListModel(paths);
	    }
	    // swingRelationsPanel.negPaths.clear();
	    java.util.List<String> paths = new ArrayList<String>();
	    for (String negPath : bootstrap.getRejects()) {
		// swingRelationsPanel.negPaths
		paths.add(bootstrap.getArg1Type() + " -- " +
			  negPath + " -- " + bootstrap.getArg2Type());
	    }
	    swingRelationsPanel.negPaths.put(bootstrap.getRelationName(),
					     paths);
	    RelationBuilderFrame.this.dispose();
	  }
	    });

        // handle the click of [Exit]
        exitButton.addActionListener(new ActionListener() {
		public void actionPerformed(ActionEvent actionEvent) {
		    

		    RelationBuilderFrame.this.dispose();
		}
	    });

        // handle the click of [x]
        this.addWindowListener(new WindowAdapter() {
		public void windowClosing(WindowEvent e) {
		    RelationBuilderFrame.this.dispose();
		    
		}
	    });

        // adapters

        rankedList.addMouseMotionListener(new MouseMotionAdapter() {
		@Override
		    public void mouseMoved(MouseEvent e) {
		    JList l = (JList)e.getSource();
		    ListModel m = l.getModel();
		    int index = l.locationToIndex(e.getPoint());
		    if( index>-1 ) {
			l.setToolTipText(((IcePath)m.getElementAt(index)).getExample());
		    }
		}
	    });



        // Key bindings
        rankedList.getInputMap().put(KeyStroke.getKeyStroke("Y"), "YES");
        rankedList.getInputMap().put(KeyStroke.getKeyStroke("y"), "YES");
        rankedList.getInputMap().put(KeyStroke.getKeyStroke("N"), "NO");
        rankedList.getInputMap().put(KeyStroke.getKeyStroke("n"), "NO");
        rankedList.getInputMap().put(KeyStroke.getKeyStroke("U"), "UNDECIDED");
        rankedList.getInputMap().put(KeyStroke.getKeyStroke("u"), "UNDECIDED");
        rankedList.getActionMap().put("YES", new AbstractAction() {
		public void actionPerformed(ActionEvent actionEvent) {
		    yesButton.doClick();
		}
	    });
        rankedList.getActionMap().put("NO", new AbstractAction() {
		public void actionPerformed(ActionEvent actionEvent) {
		    noButton.doClick();
		}
	    });
        rankedList.getActionMap().put("UNDECIDED", new AbstractAction() {
		public void actionPerformed(ActionEvent actionEvent) {
		    undecidedButton.doClick();
		}
	    });
    }

//    private void saveEntitySetToAuxFile(String typeName) {
//        try {
//            Properties props = new Properties();
//            props.load(new FileReader("parseprops"));
//            String fileName = props.getProperty("Jet.dataPath")
//                    + "/"
//                    + props.getProperty("Ace.EDTtype.auxFileName");
//            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
//
//            for (Object o : rankedListModel.toArray()) {
//                RankChoiceEntity e = (RankChoiceEntity) o;
//                if (e.getDecision() == RankChoiceEntity.EntityDecision.YES) {
//                    pw.println(e.getText().trim() + " | " + typeName + ":" + typeName + " 1");
//                }
//            }
//            pw.close();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }

    public void updateList() {
        DefaultListModel newListModel = new DefaultListModel();
        for (IcePath s : bootstrap.foundPatterns) {
            newListModel.addElement(s);
        }
        rankedListModel = newListModel;
        rankedList.setModel(rankedListModel);
    }

}

class BootstrappingActionListener implements ActionListener {
    RelationBuilderFrame frame;

    BootstrappingActionListener(RelationBuilderFrame frame) {
        this.frame = frame;
    }

    public void actionPerformed(ActionEvent actionEvent) {
        int idx = frame.rankedList.getSelectedIndex();
        if (idx < 0) return;
        IcePath e = (IcePath) frame.rankedListModel.getElementAt(idx);
        e.setChoice(IcePath.IcePathChoice.valueOf(actionEvent.getActionCommand()));
        frame.rankedList.revalidate();
        frame.rankedList.repaint();
    }
}

class BootstrapIterateThread extends Thread {
    Bootstrap bootstrap;
    java.util.List<IcePath> approvedPaths;
    java.util.List<IcePath> rejectedPaths;
    RelationBuilderFrame frame;
    String corpusName;

    BootstrapIterateThread(String corpusName, Bootstrap bootstrap,
                           java.util.List<IcePath> approvedPaths,
                           java.util.List<IcePath> rejectedPaths,
                           RelationBuilderFrame frame) {
	this.corpusName = corpusName;
        this.bootstrap = bootstrap;
        this.approvedPaths = approvedPaths;
        this.rejectedPaths = rejectedPaths;
        this.frame = frame;
    }

    public void run() {
        bootstrap.iterate(corpusName, approvedPaths, rejectedPaths);
        frame.updateList();
        frame.listPane.validate();
        frame.listPane.repaint();
    }
}
