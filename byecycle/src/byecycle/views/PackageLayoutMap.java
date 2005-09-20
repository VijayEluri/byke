package byecycle.views;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;

import byecycle.views.layout.CartesianLayout;


public class PackageLayoutMap {

	private static final String FILE_EXTENSION = ".serialized";

	private final WorkspaceJob _saveJob = createSaveJob();
	private Map<IPackageFragment, CartesianLayout> _scheduledSaves;
	private final Object _scheduledSavesMonitor = new Object();


	public CartesianLayout getLayoutFor(IPackageFragment aPackage) {
		CartesianLayout newest = mementoToBeWrittenFor(aPackage);
		if (newest != null) return newest;

		return read(aPackage);
		// TODO: Optimize: Use an LRU cache. This is not so urgent because Eclipse apparently does a lot of caching of the workspace files.
	}

	private CartesianLayout mementoToBeWrittenFor(IPackageFragment aPackage) {
		synchronized (_scheduledSavesMonitor) {
			if (_scheduledSaves == null) return null;
			return _scheduledSaves.get(aPackage);
		}
	}

	private CartesianLayout read(IPackageFragment aPackage) {
		try {
			IFile file = fileForReading(aPackage);
			if (file == null) return null;

			InputStream contents = file.getContents();
			try {
				return (CartesianLayout)new ObjectInputStream(contents).readObject();
			} catch (ClassNotFoundException expectedForOldCacheFiles) {
				return null;
			} finally {
				contents.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public void keep(IPackageFragment aPackage, CartesianLayout memento) {
		scheduleSave(aPackage, memento);
	}

	private void scheduleSave(IPackageFragment aPackage, CartesianLayout memento) {
		synchronized (_scheduledSavesMonitor) {
			scheduledSaves().put(aPackage, memento);
		}
		_saveJob.schedule(1000 * 10);
	}

	private void save(IPackageFragment aPackage, CartesianLayout memento) {
		try {
			IFile file = createTimestampedFileToAvoidScmMergeConflicts(aPackage);

			ByteArrayOutputStream serialization = new ByteArrayOutputStream();
			new ObjectOutputStream(serialization).writeObject(memento); // TODO: Use readable format (properties file) instead of serialization.
			file.create(new ByteArrayInputStream(serialization.toByteArray()), false, null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void performScheduledSaves() {
		Map<IPackageFragment, CartesianLayout> mySaves;
		synchronized (_scheduledSavesMonitor) {
			mySaves = scheduledSaves();
			_scheduledSaves = null;
		}

		for (IPackageFragment aPackage : mySaves.keySet())
			save(aPackage, mySaves.get(aPackage));
	}

	static private IFile fileForReading(IPackageFragment aPackage) throws CoreException, JavaModelException {
		IFolder cacheFolder = produceCacheFolder(aPackage);
		final String baseName = baseNameFor(aPackage);

		return matchingFile(cacheFolder, baseName);
	}

	private static IFile matchingFile(IFolder cacheFolder, String baseName) throws CoreException {
		for (IResource candidate : cacheFolder.members())
			if (candidate.getName().startsWith(baseName)) return (IFile)candidate;
		return null;
	}

	static private IFile createTimestampedFileToAvoidScmMergeConflicts(IPackageFragment aPackage) throws CoreException, JavaModelException {
		IFolder cacheFolder = produceCacheFolder(aPackage);
		String baseName = baseNameFor(aPackage);

		deleteOldFiles(cacheFolder, baseName);

		String newName = baseName + System.currentTimeMillis() + FILE_EXTENSION;
		return cacheFolder.getFile(newName);
	}

	private static void deleteOldFiles(IFolder cacheFolder, String baseName) throws CoreException {
		while (true) {
			IFile oldFile = matchingFile(cacheFolder, baseName);
			if (oldFile == null) return;
			oldFile.delete(false, false, null);
		}
	}

	static private String baseNameFor(IPackageFragment aPackage) throws JavaModelException {
		IPackageFragmentRoot root = getPackageFragmentRoot(aPackage);
		if (root == null) return "";

		IResource correspondingResource;
		try {
			correspondingResource = root.getCorrespondingResource();
		} catch (JavaModelException ignored) {
			return "";
		}
		if (correspondingResource == null) return "";

		String rootNameIncludingSlashes = correspondingResource.getProjectRelativePath().toString();
		String validRootName = rootNameIncludingSlashes.replaceAll("/", "__");

		String packageName = aPackage.isDefaultPackage() ? "(default package)" : aPackage.getElementName();

		return validRootName + "__" + packageName + "__timestamp";
	}

	static private IFolder produceCacheFolder(IPackageFragment aPackage) throws CoreException {
		IProject project = aPackage.getJavaProject().getProject();

		IFolder byecycleFolder = project.getFolder(".byecycle");
		if (!byecycleFolder.exists()) byecycleFolder.create(false, false, null);

		IFolder result = byecycleFolder.getFolder("layoutcache");
		if (!result.exists()) result.create(false, false, null);

		return result;
	}

	/**
	 * @return a IPackageFragmentRoot representing a source folder, jar file, zip file or null if the package is directly in the root of an Eclipse project.
	 */
	static private IPackageFragmentRoot getPackageFragmentRoot(IJavaElement element) {
		if (element == null) return null;
		return element instanceof IPackageFragmentRoot ? (IPackageFragmentRoot)element : getPackageFragmentRoot(element.getParent());
	}

	private WorkspaceJob createSaveJob() {
		WorkspaceJob job = new WorkspaceJob("Writing Byecycle layout cache") {
			public IStatus runInWorkspace(IProgressMonitor monitor) {
				performScheduledSaves();
				return Status.OK_STATUS;
			}
		};
		job.setSystem(true);
		job.setPriority(Job.DECORATE); // Low priority.
		return job;
	}

	private Map<IPackageFragment, CartesianLayout> scheduledSaves() {
		synchronized (_scheduledSavesMonitor) {
			if (_scheduledSaves == null) _scheduledSaves = new HashMap<IPackageFragment, CartesianLayout>();
			return _scheduledSaves;
		}
	}

}
