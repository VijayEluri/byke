//Copyright (C) 2004 Klaus Wuestefeld and Rodrigo B de Oliveira.
//This is free software. See the license distributed along with this file.


package byecycle.views.layout;

import java.util.*;

import org.eclipse.draw2d.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;

import byecycle.dependencygraph.Node;


public class GraphCanvas extends Canvas implements StressMeter {

	public GraphCanvas(Composite parent) {
		super(parent, SWT.FILL | SWT.NO_BACKGROUND);
		new LightweightSystem(this).setContents(_graphFigure);
		_graphFigure.setLayoutManager(_contentsLayout);
	}

	
	private NodeFigure[] _nodeFigures;
	private List _graphElements;
	
	private final IFigure _graphFigure = new Figure();
	private final XYLayout _contentsLayout = new XYLayout();
	
	private final Random _random = new Random();
	private float _currentStress;
	private float _smallestStressEver;
	private final List _nodesInPursuit = new LinkedList();


	public void setGraph(Node[] nodeGraph) {
		initGraphFigure(nodeGraph);
		randomizeLayout();
		_smallestStressEver = Float.MAX_VALUE;
	}
	
	public void tryToImproveLayout() {
		if (_nodeFigures == null) return;

		int guiRelief = 0;

		do {
			seekBetterTargetStep();
			if (betterTargetFound())  //TODO Comment this line to see the animation.
				lockOnNewTarget();
		} while (_nodesInPursuit.isEmpty() && guiRelief++ < 50);
		
		pursueTargetStep();
	}

	private void lockOnNewTarget() {
		_nodesInPursuit.clear();
		
		for (int i = 0; i < _nodeFigures.length; i++) {
			NodeFigure node = _nodeFigures[i];
			node.lockOnTarget();
			if (!node.onTarget()) _nodesInPursuit.add(node);
		}
	}

	private void pursueTargetStep() {
		Iterator it = _nodesInPursuit.iterator();
		while (it.hasNext()) {
			NodeFigure node = (NodeFigure)it.next();
			node.pursueTarget(_contentsLayout);
	 		if (node.onTarget()) it.remove();
		}

		makeInvertedDependenciesRed();
		
		_graphFigure.revalidate();
		_graphFigure.repaint();
	}

	private void seekBetterTargetStep() {
		for (int i = 0; i < _graphElements.size(); i++) {
	        GraphElement element1 = (GraphElement)_graphElements.get(i);
	        
            for (int j = i + 1; j < _graphElements.size(); j++) {
            	GraphElement element2 = (GraphElement)_graphElements.get(j);

    	        element1.reactTo(element2);
            }
        }

		int moving = 0;		
		for (int i = 0; i < _nodeFigures.length; i++) {
            NodeFigure figure = _nodeFigures[i];
            figure.give();

            if (figure.isMoving()) moving++;
        }
		System.out.println(moving);
		if (moving == 0) {
			randomNodeFigure().nudgeNudge();
		}
	}

	private boolean betterTargetFound() {
		System.out.println(" " + _smallestStressEver +"  "+ _currentStress);
		boolean result = _currentStress < _smallestStressEver;
		if (result) _smallestStressEver = _currentStress;
		_currentStress = 0;
		return result;
	}

	private NodeFigure randomNodeFigure() {
		return _nodeFigures[_random.nextInt(_nodeFigures.length)];
	}


	private void makeInvertedDependenciesRed() {
		Iterator children = _graphFigure.getChildren().iterator();
		while (children.hasNext()) {
			IFigure child = (IFigure) children.next();

			if (child instanceof PolylineConnection) {   //TODO Optimize. Iterate only on the PolylineConnections. 
				PolylineConnection dependency = (PolylineConnection) child;
				Color redOrBlack = dependency.getStart().y > dependency.getEnd().y
					? ColorConstants.red
					: ColorConstants.black;
				dependency.setForegroundColor(redOrBlack);
			}
		}
	}
	
	private void initGraphFigure(Node[] nodeGraph) {
		clearGraphFigure();
		_graphElements = new ArrayList();

		Map nodeFiguresByNode = new HashMap();
		
		for (int i = 0; i < nodeGraph.length; i++) {
			Node node = nodeGraph[i];
			NodeFigure dependentFigure = produceNodeFigureFor(node, nodeFiguresByNode);
			Iterator providers = node.providers();
			while (providers.hasNext()) {
				Node provider = (Node)providers.next();
				NodeFigure providerFigure = produceNodeFigureFor(provider, nodeFiguresByNode);
				addDependencyFigure(dependentFigure, providerFigure);
			}
		}

		_nodeFigures = new NodeFigure[nodeFiguresByNode.size()];
		Iterator it = nodeFiguresByNode.values().iterator();
		int j = 0;
		while (it.hasNext()) {
			NodeFigure nodeFigure = (NodeFigure)it.next();
			_nodeFigures[j++] = nodeFigure;
			_graphElements.add(nodeFigure);
		}
	}

	private void clearGraphFigure() {
		Object[] children = _graphFigure.getChildren().toArray();
		for (int i = 0; i < children.length; i++) {
			IFigure figure = (IFigure) children[i];
			_graphFigure.remove(figure);
		}
	}

	private NodeFigure produceNodeFigureFor(Node node, Map nodeFiguresByNode) {
		NodeFigure result = (NodeFigure) nodeFiguresByNode.get(node);
		if (result != null)	return result;

		result = new NodeFigure(node, this);
		nodeFiguresByNode.put(node, result);
		_graphFigure.add(result.figure());
		return result;
	}

	private void addDependencyFigure(NodeFigure dependentFigure,
			NodeFigure providerFigure) {
		DependencyFigure dependency = new DependencyFigure(dependentFigure, providerFigure);
		_graphElements.add(dependency);
		_graphFigure.add(dependency.figure());
	}

	private void randomizeLayout() {
		for (int i = 0; i < _nodeFigures.length; i++) {
			NodeFigure nodeFigure = _nodeFigures[i];
			int x = 180 + _random.nextInt(41);
			int y = 180 + _random.nextInt(41);
			nodeFigure.position(x, y);
		}
    }

	public void accumulateStress(float stress) {
		_currentStress += Math.abs(stress);
	}

}
