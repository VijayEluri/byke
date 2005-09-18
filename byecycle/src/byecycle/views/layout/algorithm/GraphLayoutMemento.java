package byecycle.views.layout.algorithm;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class GraphLayoutMemento implements Serializable {
	
	private static final long serialVersionUID = 1L;

	static private final Random _random = new Random();

	private final Map<String, Coordinates> _coordinatesByName = new HashMap<String, Coordinates>();

	public void remember(String name, Coordinates coordinates) {
		_coordinatesByName.put(name, coordinates);
	}

	public Coordinates getCoordinatesFor(String name) {
		Coordinates result = _coordinatesByName.get(name);
		return result == null ? randomCoordinates() : result;
	}
	
	private Coordinates randomCoordinates() {
		float x = _random.nextFloat();
		float y = _random.nextFloat();
		return new Coordinates(x, y);
	}

	public static GraphLayoutMemento random() {
		return new GraphLayoutMemento();
	}


}
