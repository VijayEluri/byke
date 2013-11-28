package byke.views.layout.algorithm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import byke.dependencygraph.Node;
import byke.views.layout.CartesianLayout;
import byke.views.layout.NodeSizeProvider;
import byke.views.layout.criteria.NodeElement;


public class LayeredLayoutAlgorithm implements LayoutAlgorithm {

	private static final int LAYER_HEIGHT = 50;
	private static final int LAYER_WIDTH = 100;

	protected final List<NodeElement> nodeElements;


	public LayeredLayoutAlgorithm(Iterable<Node<?>> graph, CartesianLayout initialLayout, NodeSizeProvider sizeProvider) {
		NodesByDepth nodesByDepth = NodesByDepth.layeringOf(graph);
		nodeElements = asGraphElements(nodesByDepth, sizeProvider);
		arrangeWith(initialLayout == null ? new CartesianLayout() : initialLayout);
	}

	
	@Override
	public boolean improveLayoutStep() {
		return false;
	}


	@Override
	public CartesianLayout layoutMemento() {
		CartesianLayout result = new CartesianLayout();
		for (NodeElement node : nodeElements)
			result.keep(node.name(), node.position());
		return result;
	}

	
	protected void arrangeWith(CartesianLayout initiallayoutIgnoredForNow) {
		Collections.sort(nodeElements, new Comparator<NodeElement>() { @Override public int compare(NodeElement o1, NodeElement o2) {
			return o1.y < o2.y ? 0 : 1;
		}});
		
		
		for (NodeElement parent : nodeElements) {
			List<NodeElement> children = children(parent);
			for (int i = 0; i < children.size(); i++) {
				double qty = 0;
				
				if (children.size() % 2 == 0 && children.size() / 2 <= i)
					qty = 0.5;
				if (children.size() % 2 == 0 && children.size() / 2 - 1 >= i)
					qty = 0.5;
				NodeElement child = children.get(i);
				int parentCenterX = parent.x + (parent.aura().width / 2);
				int childNewX = parentCenterX - (child.aura().width / 2) + (int)(LAYER_WIDTH * (i + qty - children.size() / 2));
				child.position(childNewX, child.y);
			}
		}
	}

	
	private List<NodeElement> children(NodeElement node) {
		List<NodeElement> ret = new ArrayList<NodeElement>();
		
		for(Node<?> n : node.node().providers())
			ret.add(asNodeElement(n));
		
		Collections.sort(ret, new Comparator<NodeElement>() { @Override public int compare(NodeElement o1, NodeElement o2) {
			return o1.name().compareTo(o2.name());
		}});
		
		return ret; 
	}
	
	
	private NodeElement asNodeElement(Node<?> n) {
		for (NodeElement node : nodeElements)
			if(node.node().equals(n))
				return node;

			return null;
	}

	
	private static <T> List<NodeElement> asGraphElements(NodesByDepth nodesByLayer, NodeSizeProvider sizeProvider) {
		List<NodeElement> ret = new ArrayList<NodeElement>();
		for (Node<?> node : nodesByLayer.graph())
			ret.add(asGraphElement(node, nodesByLayer, sizeProvider));
		return ret;
	}

	
	private static NodeElement asGraphElement(Node<?> node, NodesByDepth nodesByLayer, NodeSizeProvider sizeProvider) {
		NodeElement ret = new NodeElement(node, sizeProvider.sizeGiven(node));
		ret.position(ret.x, nodesByLayer.depthOf(node) * LAYER_HEIGHT);
		return ret;
	}

}