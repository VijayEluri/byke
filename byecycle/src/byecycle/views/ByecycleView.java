//Copyright (C) 2004 Klaus Wuestefeld and Rodrigo B de Oliveira.
//This is free software. See the license distributed along with this file.
package byecycle.views;

import java.util.Collection;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;

import byecycle.PackageDependencyAnalysis;
import byecycle.dependencygraph.Node;
import byecycle.views.layout.CartesianLayout;
import byecycle.views.layout.algorithm.AlgorithmCombination;
import byecycle.views.layout.algorithm.LayoutAlgorithm;
import byecycle.views.layout.ui.GraphCanvas;


public class ByecycleView extends ViewPart implements IByecycleView {

	private static final int ONE_MILLISECOND = 1000000;
	private static final int TEN_SECONDS = 10 * 1000000000;

	private IViewSite _site;

	private LayoutAlgorithm<IBinding> _algorithm;

	private Composite _parent;
	private GraphCanvas<IBinding> _canvas;

	private IPackageFragment _selectedPackage;
	private Collection<Node<IBinding>> _selectedPackageGraph;

	private final Object _graphChangeMonitor = new Object();

	private UIJob _layoutJob;
	private long _timeLastLayoutJobStarted;
	private final PackageLayoutMap _layoutCache = new PackageLayoutMap();

	private boolean _paused;
	private IJavaElement _deferredSelection;


	public void init(IViewSite site) throws PartInitException {
		super.init(site);
		_site = site;
		_site.getPage().addSelectionListener(this);
		_layoutJob = new UIJob("Package dependency layout") {

			private IPackageFragment _packageBeingDisplayed;


			@Override
			public IStatus runInUIThread(IProgressMonitor monitor) {
				if (monitor.isCanceled()) return Status.OK_STATUS;

				if (_paused) return Status.OK_STATUS;

				if (_parent == null || _parent.isDisposed()) return Status.OK_STATUS;
				checkForNewGraph();
				if (_canvas == null || _canvas.isDisposed()) return Status.OK_STATUS;

				_timeLastLayoutJobStarted = System.nanoTime();
				_canvas.animationStep();
				boolean improved = _algorithm.improveLayoutForAWhile();
				this.schedule(nanosecondsToSleep() / 1000000);

				if (improved) {
					CartesianLayout bestSoFar = _algorithm.layoutMemento();
					_canvas.useLayout(bestSoFar);
					_layoutCache.keep(_packageBeingDisplayed, bestSoFar);
				}

				return Status.OK_STATUS;
			}

			private void checkForNewGraph() {
				if (_selectedPackageGraph == null) return;

				Collection<Node<IBinding>> myGraph;
				synchronized (_graphChangeMonitor) {
					_packageBeingDisplayed = _selectedPackage;
					myGraph = _selectedPackageGraph;
					_selectedPackageGraph = null;
				}
				CartesianLayout bestSoFar = _layoutCache.getLayoutFor(_packageBeingDisplayed);
				if (bestSoFar == null) bestSoFar = CartesianLayout.random();

				newCanvas((Collection<Node<IBinding>>)myGraph, bestSoFar);
				newAlgorithm((Collection<Node<IBinding>>)myGraph, bestSoFar);
			}

		};
		_layoutJob.setSystem(true);
		_layoutJob.setPriority(Job.DECORATE); // Low priority;
	}

	private void newCanvas(Collection<Node<IBinding>> graph, CartesianLayout initialLayout) {
		if (_canvas != null) _canvas.dispose();

		_canvas = new GraphCanvas<IBinding>(_parent, graph, initialLayout, new GraphCanvas.Listener<IBinding>() {
			public void nodeSelected(Node<IBinding> node) {
				selectNode(node);
			}
		});
		_parent.layout();
	}

	private void newAlgorithm(Collection<Node<IBinding>> graph, CartesianLayout initialLayout) {
		_algorithm = new AlgorithmCombination<IBinding>(graph, initialLayout, _canvas);
	}

	@Override
	public void dispose() {
		_site.getPage().removeSelectionListener(this);
		super.dispose();
	}

	public void createPartControl(Composite parent) {
		_parent = parent;
	}

	public void selectionChanged(IWorkbenchPart ignored, ISelection selectionCandidate) { // FIXME: After the "Show Dependencies" popup menu action, this method is no longer called (Byecycle is no longer notified of selections changes and no longer changes the graph display). If focus is changed to another View and back, for example, everything comes back to normal. Is this an Eclipse bug?
		IJavaElement newSelection = validadeSelection(selectionCandidate);
		if (_paused) {
			_deferredSelection = newSelection;
			return;
		}
		showJavaDependencies(newSelection);
	}

	public void showDependencies(ISelection selectionCandidate) {
		showJavaDependencies(validadeSelection(selectionCandidate));
	}

	private void showJavaDependencies(IJavaElement javaElement) {

		IPackageFragment newPackage = getPackage(javaElement);

		if (newPackage == null) return;
		if (newPackage == _selectedPackage) return;
		try {
			// No empty view if select package without source 
			if (newPackage.getKind() == IPackageFragmentRoot.K_BINARY) return;
		} catch (JavaModelException e) {
			// FIXME Auto-generated catch block
			e.printStackTrace();
		} 
		synchronized (_graphChangeMonitor) {
			_selectedPackage = newPackage;
			_selectedPackageGraph = null;
		}

		generateGraph(newPackage);
	}

	private void generateGraph(final IPackageFragment packageBeingGenerated) {
		final ICompilationUnit[] compilationUnits;
		try {
			compilationUnits = packageBeingGenerated.getCompilationUnits();
		} catch (JavaModelException x) {
			x.printStackTrace();
			return;
		}

		(new Job("'" + packageBeingGenerated.getElementName() + "' analysis") {
			protected IStatus run(IProgressMonitor monitor) {
				try {
					Collection<Node<IBinding>> nextGraph = new PackageDependencyAnalysis(compilationUnits, monitor).dependencyGraph();

					synchronized (_graphChangeMonitor) {
						if (packageBeingGenerated != _selectedPackage) return Status.OK_STATUS;
						_selectedPackageGraph = nextGraph;
					}
					_layoutJob.schedule();
				} catch (Exception x) {
					UIJob.errorStatus(x);
				}
				return Status.OK_STATUS;
			}

		}).schedule();
	}

	private IPackageFragment getPackage(IJavaElement element) {
		if (element == null) return null;
		if (element instanceof IPackageFragment) return (IPackageFragment)element;
		return getPackage(element.getParent());
	}

	private void selectNode(Node<IBinding> selection) {
		if (null == selection) {
			drillUp();
		} else {
			drillDown(selection);
		}
	}

	private void drillDown(Node<IBinding> selection) {
		IBinding binding = selection.payload();
		IJavaElement element = binding.getJavaElement();
		if (null != element) {
			selectionChanged(null, new StructuredSelection(element));
		}
	}

	private void drillUp() { // FIXME: drillUp is apparently not being called.
		showJavaDependencies(_selectedPackage.getParent());
	}

	public void togglePaused(boolean pause) {
		assert pause != _paused;
		_paused = pause;
		if (!_paused) showJavaDependencies(_deferredSelection);
		_layoutJob.schedule();
	}

	private IJavaElement validadeSelection(ISelection candidate) {
		if (!(candidate instanceof IStructuredSelection)) return null;

		Object firstElement = ((IStructuredSelection)candidate).getFirstElement();
		if (!(firstElement instanceof IJavaElement)) return null;

		return (IJavaElement)firstElement;
	}

	private long nanosecondsToSleep() {
		long currentTime = System.nanoTime();

		long timeLastLayoutJobTook = currentTime - _timeLastLayoutJobStarted;
		if (timeLastLayoutJobTook < 0) timeLastLayoutJobTook = 0; // This can happen due to rounding from nanos to millis.

		long timeToSleep = timeLastLayoutJobTook * 4; // The more things run in parallel with byecycle, the less greedy byecycle will be. Byecycle is proud to be a very good citizen. :)
		if (timeToSleep > TEN_SECONDS) timeToSleep = TEN_SECONDS;
		if (timeToSleep < ONE_MILLISECOND) timeToSleep = ONE_MILLISECOND;

		_timeLastLayoutJobStarted = currentTime + timeToSleep;

		return timeToSleep;
	}

	public void setFocus() {
		_parent.setFocus();
	}

}