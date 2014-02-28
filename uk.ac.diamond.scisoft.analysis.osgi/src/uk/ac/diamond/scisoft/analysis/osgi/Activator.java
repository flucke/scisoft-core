package uk.ac.diamond.scisoft.analysis.osgi;

import java.util.Hashtable;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import py4j.ClassLoaderService;
import uk.ac.diamond.scisoft.analysis.dataset.IDatasetMathsService;
import uk.ac.diamond.scisoft.analysis.dataset.IFittingAlgorithmService;
import uk.ac.diamond.scisoft.analysis.io.ILoaderFactoryExtensionService;
import uk.ac.diamond.scisoft.analysis.io.ILoaderService;

public class Activator implements BundleActivator {

	private BundleContext context=null;
	@Override
	public void start(BundleContext c) throws Exception {
		context = c;
		Hashtable<String, String> props = new Hashtable<String, String>(1);
		props.put("description", "A service used by the LoaderFactory to read extension points.");
		context.registerService(ILoaderFactoryExtensionService.class, new LoaderFactoryExtensionService(), props);

		props = new Hashtable<String, String>(1);
		props.put("description", "A service for loading any data that is supported ");
		context.registerService(ILoaderService.class, new LoaderServiceImpl(), props);

		props = new Hashtable<String, String>(1);
		props.put("description", "A service which replaces concrete classes in the scisoft.analysis plugin.");
		context.registerService(IDatasetMathsService.class, new DatasetMathsServiceImpl(), props);

		props = new Hashtable<String, String>(1);
		props.put("description", "A service for loading of split analysis packages");
		context.registerService(ClassLoaderService.class, new ClassLoaderServiceImpl(), props);
		
		props = new Hashtable<String, String>(1);
		props.put("description", "A service for creating fitters");
		context.registerService(IFittingAlgorithmService.class, new FittingAlgorithmServiceImpl(), props);

	}

	@Override
	public void stop(BundleContext c) throws Exception {
		context = null;
	}

}
