//Copyright (C) 2005 Klaus Wuestefeld and Rodrigo B de Oliveira.
//This is free software. See the license distributed along with this file.

package byecycle.views.layout;

import org.eclipse.draw2d.IFigure;


public abstract class GraphElement {

    private IFigure _figure;
	
	IFigure figure() {
		return _figure == null
			? _figure = produceFigure()
			: _figure;
	}

	abstract IFigure produceFigure();
	
}
