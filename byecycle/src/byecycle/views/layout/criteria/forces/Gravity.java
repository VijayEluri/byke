package byecycle.views.layout.criteria.forces;

import byecycle.views.layout.criteria.GraphElement;
import byecycle.views.layout.criteria.NodeElement;


public class Gravity extends DistanceDefinedForce {

	@Override
	public float intensityGiven(float safeDistance) {
		return 1f / (float)(Math.pow(safeDistance, 2)); // TODO Play with this formula.
	}

	@Override
	public void applyTo(GraphElement element1, GraphElement element2) {
		if (element1 instanceof NodeElement && element2 instanceof NodeElement) {
			super.applyTo(element1, element2);
		}
	}

}
