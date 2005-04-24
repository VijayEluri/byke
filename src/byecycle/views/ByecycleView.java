//Copyright (C) 2004 Klaus Wuestefeld and Rodrigo B de Oliveira.
//This is free software. See the license distributed along with this file.

package byecycle.views;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.UIJob;

import byecycle.PackageDependencyAnalysis;
import byecycle.dependencygraph.Node;
import byecycle.views.layout.GraphCanvas;

public class ByecycleView extends ViewPart implements ISelectionListener, ISelectionProvider {

	private GraphCanvas _canvas;
	private IViewSite _site;
	private Set<ISelectionChangedListener> _selectionListeners = new HashSet<ISelectionChangedListener>();
	private ISelection _selection;
	
	/**
	 * The constructor.
	 */
	public ByecycleView() {
	}
	
	public void init(IViewSite site) throws PartInitException {
		super.init(site);
		_site = site;
		_site.getPage().addSelectionListener(this);
		_site.setSelectionProvider(this);
	}
	
	public void dispose() {
		_site.getPage().removeSelectionListener(this);
		super.dispose();
	}

	/**
	 * This is a callback that will allow us to create the viewer and initialize
	 * it.
	 */
	public void createPartControl(Composite parent) {
		_canvas = new GraphCanvas(parent, new GraphCanvas.Listener() {
			public void nodeSelected(Node node) {
				setSelection(node);
			}
		});
		scheduleImproveLayoutJob();
	}
	
	private void scheduleImproveLayoutJob() {
		UIJob job = new UIJob("package analysis display") {
			public IStatus runInUIThread(IProgressMonitor monitor) {
				try {
					if (!_canvas.isDisposed() && !monitor.isCanceled()) {
						_canvas.tryToImproveLayout();
						Thread.sleep(100);
						scheduleImproveLayoutJob();
					}
				} catch (Exception x) {
					x.printStackTrace();
				}
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		job.schedule();
	}
	
	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		//viewer.getControl().setFocus();
	}
	
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (!(selection instanceof IStructuredSelection)) {
			return;
		}		
		
		_selection = selection;
		IStructuredSelection structured = (IStructuredSelection) selection;
		Object selected = structured.getFirstElement();
		
		try {
			if (selected instanceof IPackageFragment) {
				IPackageFragment selectedPackage = (IPackageFragment) selected;
				analyze(selectedPackage.getElementName(), selectedPackage.getCompilationUnits());
			} else if (selected instanceof ICompilationUnit) {
				ICompilationUnit compilationUnit = (ICompilationUnit)selected;
				analyze(compilationUnit.getElementName(), new ICompilationUnit[] { compilationUnit });
			} else if (selected instanceof IType) {
				IType type = (IType)selected;
				analyze(type.getElementName(), new ICompilationUnit[] { type.getCompilationUnit() });
			}
		} catch (Exception x) {
			x.printStackTrace();
		}
	}
	
	private void analyze(final String elementName, final ICompilationUnit[] compilationUnits) {
		Job job = new Job("'" + elementName + "' analysis") {
			protected IStatus run(IProgressMonitor monitor) {
				try {
					final Collection<Node<IBinding> > nodes = new PackageDependencyAnalysis(compilationUnits, monitor).dependencyGraph();
					if (!monitor.isCanceled()) {
						//dumpGraph(graph);
						UIJob job = new UIJob("package analysis display") {
							public IStatus runInUIThread(IProgressMonitor monitor) {
								try {
									_canvas.setGraph((Collection<Node>)nodes);
								} catch (Exception x) {
									x.printStackTrace();
								}
								return Status.OK_STATUS;
							}
						};
						job.schedule();
					}
				} catch (Exception x) {
					x.printStackTrace();
				}
				return Status.OK_STATUS;
			}
		};
		job.schedule();
		
	}

	private void dumpGraph(Node[] graph) {
		System.out.println("*********");
		for (int i=0; i<graph.length; ++i) {
			System.out.println(graph[i].name());
		}
	}
	
	public void addSelectionChangedListener(ISelectionChangedListener listener) {
		_selectionListeners.add(listener);
	}

	public ISelection getSelection() {
		return _selection;
	}

	public void removeSelectionChangedListener(ISelectionChangedListener listener) {
		_selectionListeners.remove(listener);
	}
	
	void setSelection(Node selection) {
		if (null == selection) {
			// drill up
			IStructuredSelection structured = (IStructuredSelection)selection;
			IJavaElement element = (IJavaElement)structured.getFirstElement();
			setSelection(new StructuredSelection(element.getParent()));
		} else {
			// drill down
			Node<IBinding> typedNode = (Node<IBinding>)selection;
			IBinding binding = typedNode.payload();
			IJavaElement element = binding.getJavaElement();
			if (null != element) {
				setSelection(new StructuredSelection(element));
			}
		}
	}

	public void setSelection(ISelection selection) {
		if (selection == _selection) return;
		
		_selection = selection;
		fireSelectionChanged();
	}

	private void fireSelectionChanged() {
		SelectionChangedEvent event = new SelectionChangedEvent(this, _selection);
		for (ISelectionChangedListener listener : _selectionListeners) {
			listener.selectionChanged(event);
		}
	}

}