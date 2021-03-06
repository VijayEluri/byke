package byke.views.layout.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.draw2d.SWTEventDispatcher;
import org.eclipse.gef4.layout.LayoutAlgorithm;
import org.eclipse.gef4.zest.core.widgets.GraphConnection;
import org.eclipse.gef4.zest.core.widgets.GraphItem;
import org.eclipse.gef4.zest.core.widgets.GraphNode;
import org.eclipse.gef4.zest.core.widgets.GraphWidget;
import org.eclipse.gef4.zest.core.widgets.ZestStyles;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;

import byke.dependencygraph.SubGraph;
import byke.views.BykeView;
import byke.views.cache.NodeFigure;
import byke.views.layout.algorithm.WuestefeldTomaziniLayoutAlgorithm;

public class NonMovableGraph extends GraphWidget {

	private class NonMovableGraphMouseListener implements MouseListener {

		@Override
		public void mouseUp(MouseEvent arg0) {}

		@Override
		public void mouseDown(MouseEvent arg0) {}

		
		@Override
		public void mouseDoubleClick(MouseEvent e) {
			newGraph(((NonMovableGraph)e.getSource()).getSelection());
		}
	}
	
	private class NonMovableGraphSelectionListener implements SelectionListener {
		
		@Override
		public void widgetSelected(SelectionEvent arg0) {
			highlightConnections();
		}

		@Override
		public void widgetDefaultSelected(SelectionEvent arg0) {}
	}
	
	private Map<NodeFigure, NonMovableNode> _nodeFiguresByNode = new HashMap<NodeFigure, NonMovableNode>();
	
	protected Collection<NodeFigure> _parent = new HashSet<NodeFigure>();
	private Collection<NodeFigure> _graph;
	
	public NonMovableGraph(Composite parent, final Collection<NodeFigure> graph) {
		super(parent, ZestStyles.NONE);
		setAnimationEnabled(true);
		setBounds(parent.getBounds());
		_graph = graph;
		
		lockNodeMoves();

		LayoutAlgorithm algorithm = new WuestefeldTomaziniLayoutAlgorithm();
  	setLayoutAlgorithm(algorithm, true);
		initGraphFigures(clusterCycles(graph));
		
		addMouseListener(new NonMovableGraphMouseListener());
		addSelectionListener(new NonMovableGraphSelectionListener());
	}


	protected Collection<? extends NodeFigure> clusterCycles(final Collection<NodeFigure> graph) {
		DependencyProcessor processor = new DependencyProcessor();
		Collection<SubGraph> newGraph = processor.clusterCycles(graph);
		return newGraph;
	}

	
	private void lockNodeMoves() {
		getLightweightSystem().setEventDispatcher(new SWTEventDispatcher() {
      @Override
      public void dispatchMouseMoved(org.eclipse.swt.events.MouseEvent me) {}
    });
	}
	

	private void initGraphFigures(Collection<? extends NodeFigure> nodeGraph) {
		for(NodeFigure node : nodeGraph)
			createNodeFigures(node);
		MnemonicColors.colorFor(_nodeFiguresByNode.values());
	}


	private void createNodeFigures(NodeFigure node) {
		GraphNode dependentFigure = produceNodeFigureFor(node);
			
		for (NodeFigure provider : node.providers()) {
			GraphNode providerFigure = produceNodeFigureFor(provider);
			if (dependentFigure.equals(providerFigure)) continue;

			if (providerFigure != null)
				new GraphConnection(this, ZestStyles.CONNECTIONS_DIRECTED, dependentFigure, providerFigure);
		}
	}
	
	
	private NonMovableNode produceNodeFigureFor(NodeFigure subGraph) {
		NonMovableNode result = _nodeFiguresByNode.get(subGraph);
		if (result != null) return result;
		
		result = new NonMovableNode(this, SWT.NONE, subGraph, subGraph.name());
		_nodeFiguresByNode.put(subGraph, result);
		return result;
	}
	
	
	protected void newGraph(List<GraphItem> selection) {
		if (selection.isEmpty())
			return;

		Set<NodeFigure> nodesForNewGraph = nodesForNewGraph(selection.get(0));
		
		if (!canCreateNewGraph(nodesForNewGraph))
			return;

		new NonMovableSubGraph(composite(), nodesForNewGraph, _graph);
		dispose();
	}

	
	private boolean canCreateNewGraph(Set<NodeFigure> nodesForNewGraph) {
		return nodesForNewGraph.size() > 1;
	}


	private Set<NodeFigure> nodesForNewGraph(GraphItem graphItem) {
		try {
			return ((NonMovableNode)graphItem).subGraph().subGraph();
		} catch (Exception e) {
			return Collections.EMPTY_SET;
		}
	}


	protected Composite composite() {
		Composite composite;
		try {
			BykeView bikeView = (BykeView)PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().showView("byke.views.BykeView");
			composite = bikeView._parent;
		} catch (Throwable ex) {
			composite = getShell();
		}
		return composite;
	}

	private void highlightConnections() {
		List<GraphItem> connectionsToSelect = connectionsToSelect();

		List<GraphItem> newSelection = new ArrayList<GraphItem>(getSelection());
		newSelection.addAll(connectionsToSelect);
		NonMovableGraph.this.setSelection((GraphItem[])newSelection.toArray(new GraphItem[newSelection.size()]));
	}


	private List<GraphItem> connectionsToSelect() {
		List<GraphItem> connectionsToSelect = new ArrayList<GraphItem>();
		
		for(GraphItem item : getSelection()) {
			if(!(item instanceof NonMovableNode))
				continue;
			
			List<GraphConnection> sourceConnections = ((NonMovableNode)item).getSourceConnections();
			connectionsToSelect.addAll(sourceConnections);
			connectionsToSelect.addAll(cyclicConnections(item, sourceConnections));
		}
		
		return connectionsToSelect;
	}
	
	private List<GraphItem> cyclicConnections(GraphItem item, List<GraphConnection> sourceConnections) {
		List<GraphItem> connectionsToSelect = new ArrayList<GraphItem>();
		
		for(GraphConnection connection : sourceConnections)
			for(GraphConnection c : connection.getDestination().getSourceConnections())
				if(c.getDestination().equals(item))
					connectionsToSelect.add(c);
		
		return connectionsToSelect;
	}

}
