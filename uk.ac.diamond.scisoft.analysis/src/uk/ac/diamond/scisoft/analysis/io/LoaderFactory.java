/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.io.IDataAnalysisObject;
import org.eclipse.dawnsci.analysis.api.io.IDataHolder;
import org.eclipse.dawnsci.analysis.api.io.IFileLoader;
import org.eclipse.dawnsci.analysis.api.io.ILoaderFactoryExtensionService;
import org.eclipse.dawnsci.analysis.api.io.ScanFileHolderException;
import org.eclipse.dawnsci.analysis.api.metadata.IMetaLoader;
import org.eclipse.dawnsci.analysis.api.metadata.IMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.dataset.impl.LazyDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.utils.FileUtils;
// TODO Not sure if org.eclipse.core could break GDA server.
// Been told verbally that the GDA server now can resolve core and resources.

/**
 * A class which gives a single point of entry to loading data files
 * into the system.
 * 
 * In order to work with the factory a loader must have:
 * 1. a no argument constructor
 * 2. a setFile(...) method with a string path argument
 * 
 * *OR*
 * 
 * A constructor with a string argument.
 * 
 * In order to work well the loader should implement:
 * 
 * 1. IMetaLoader - this interface marks it possible to extract dataset names and other meta
 *    data without reading all the file data into memory.
 *    
 * 2. IDataSetLoader to load a single data set without loading the rest of the file.
 * 
 * see LoaderFactoryExtensions which boots up the extensions from reading the extension points.
 * 
 * This class is going to be @Deprecated please use ILoaderService where possible.
 * <code>
 * 
  	final ILoaderService service = (ILoaderService)ServiceManager.getService(ILoaderService.class);
  	// ServiceManager may need to be configured using Spring on GDA server
  	// service can now be used as if it was a 'LoaderFactory' instance.
    
 */
public class LoaderFactory {

	// Not for public use: only used by OSGI
	public LoaderFactory() {
		
	}

	/**
	 * Injected by OSGI
	 * @param lf
	 */
	public static void setLoaderFactoryService(ILoaderFactoryExtensionService lf) {
		try {
			/**
			 * Tell the extension points to load in.
			 */
			if (lf != null)
				lf.registerExtensionPoints();
		} catch (Throwable t) {
			logger.error("Problem getting extension service");
		}
	}

	/**
	 * A caching mechanism using soft references. Soft references attempt to keep things
	 * in memory until the system is short on memory. Hashtable used because it is synchronized
	 * which should reduce chances of getting the wrong data for the key.
	 */
	private static final Map<LoaderKey, Reference<IDataAnalysisObject>> SOFT_CACHE = new Hashtable<LoaderKey, Reference<IDataAnalysisObject>>(89);
	
	/**
	 * This method may be called to ensure that the soft reference cache of data is
	 * empty. It is required from the unit tests which attempt to measure memory
	 * leaks, which otherwise would measure the "leak" of the soft reference cache.
	 */
	public static void clear() {
		SOFT_CACHE.clear();
	}

	private static final Logger logger = LoggerFactory.getLogger(LoaderFactory.class);

	private static final Map<String, List<Class<? extends IFileLoader>>> LOADERS = new HashMap<String, List<Class<? extends IFileLoader>>>(19);
	private static final Map<String, Class<? extends InputStream>> UNZIPPERS = new HashMap<String, Class<? extends InputStream>>(3);

	/**
	 * 
	 * Loaders can be registered at run time using registerLoader(...)
	 * 
	 * There is no need for an extension point now and no dependency on eclipse.
	 * Instead an OSGI service contributing the loaders is looked for.
	 * 
	 * To change a loader programmatically (not advised)
	 * 
	 * 1. LoaderFactory.getSupportedExtensions();
	 * 2. LoaderFactory.clearLoader("h5");
	 * 3. LoaderFactory.registerLoader("h5", MyH5ClassThatIsBetter.class);
	 * 
	 */
	static {
		try {
		    registerLoader("npy",  NumPyFileLoader.class);
		    registerLoader("img",  ADSCImageLoader.class);
		    registerLoader("osc",  RAxisImageLoader.class);
		    registerLoader("cbf",  CBFLoader.class);
		    registerLoader("img",  CrysalisLoader.class);
			registerLoader("tif",  PixiumLoader.class);
		    registerLoader("jpeg", JPEGLoader.class);
		    registerLoader("jpg",  JPEGLoader.class);
		    registerLoader("mccd", MARLoader.class);
		    registerLoader("mar3450", MAR345Loader.class);
		    registerLoader("pck3450", MAR345Loader.class);
		    registerLoader("mrc", MRCImageStackLoader.class);

		    // There is some disagreement about the proper nexus/hdf5 
		    // file extension at different facilities
		    registerLoader("nxs",  NexusHDF5Loader.class);
		    registerLoader("nexus",NexusHDF5Loader.class);
		    registerLoader("h5",   HDF5Loader.class);
		    registerLoader("hdf",  HDF5Loader.class);
		    registerLoader("hdf5", HDF5Loader.class);
		    registerLoader("hd5",  HDF5Loader.class);
		    registerLoader("mat",  HDF5Loader.class);
		    
		    registerLoader("tif",  PilatusTiffLoader.class);
		    registerLoader("png",  PNGLoader.class);
		    registerLoader("raw",  RawBinaryLoader.class);
		    registerLoader("srs",  ExtendedSRSLoader.class);
		    registerLoader("srs",  SRSLoader.class);
		    registerLoader("dat",  DatLoader.class);
		    registerLoader("xy",  DatLoader.class);
		    registerLoader("dat",  ExtendedSRSLoader.class);
		    registerLoader("dat",  SRSLoader.class);
		    registerLoader("csv",  CSVLoader.class);
		    registerLoader("txt",  DatLoader.class);
		    registerLoader("txt",  SRSLoader.class);
		    registerLoader("txt",  RawTextLoader.class);
		    registerLoader("mca",  DatLoader.class);
		    registerLoader("mca",  SRSLoader.class);
		    registerLoader("mca",  RawTextLoader.class);
		    registerLoader("tif",  TIFFImageLoader.class);		    
		    registerLoader("tiff", TIFFImageLoader.class);		    
		    registerLoader("zip",  XMapLoader.class);
		    registerLoader("edf",  PilatusEdfLoader.class);
		    registerLoader("pgm",  PgmLoader.class);
		    registerLoader("f2d",  Fit2DLoader.class);
		    registerLoader("msk",  Fit2DMaskLoader.class);
		    registerLoader("mib", MerlinLoader.class);
		    registerLoader("bmp", BitmapLoader.class);

		    registerUnzip("gz",  GZIPInputStream.class);
		    registerUnzip("zip", ZipInputStream.class);
		    registerUnzip("bz2", CBZip2InputStream.class);

		} catch (Exception ne) {
			logger.error("Cannot register loader - ALL loader registration aborted!", ne);
		}
	}

	/**
	 * This method is used to define the supported extensions that the LoaderFactory
	 * already knows about.
	 * 
	 * NOTE that is can be called from Jython. It is probably not used in the GDA/SDA 
	 * code based that much but external code like Jython can 
	 * 
	 * @return collection of extensions.
	 */
	public static Collection<String> getSupportedExtensions() {
		return LOADERS.keySet();
	}


	/**
	 * Call to load any file type into memory. By default loads all data sets, therefore
	 * could take a **long time**.
	 * 
	 * In addition to find out if a given file loads with a particular loader - it actually 
	 * LOADS it. 
	 * 
	 * Therefore it can take a while to run depending on how quickly the loader
	 * fails. Also if there are many loaders called in turn, much memory could be consumed and
	 * discarded. For this reason the registration process requires a file extension and tries 
	 * all the loaders for a given extension if the extension is registered already. 
	 * Otherwise it tries all loaders - in no particular order.
	 * 
	 * @param path
	 * @return DataHolder
	 * @throws Exception
	 */
	public static IDataHolder getData(final String path) throws Exception {
		return getData(path, true, new IMonitor.Stub() {
			
			@Override
			public void worked(int amount) {
				// Deliberately empty
			}
			
			@Override
			public boolean isCancelled() {
				return false;
			}
		});
	}
	
	/**
	 * Call to load any file type into memory. By default loads all data sets, therefore
	 * could take a **long time**.
	 * 
	 * In addition to find out if a given file loads with a particular loader - it actually 
	 * LOADS it. 
	 * 
	 * Therefore it can take a while to run depending on how quickly the loader
	 * fails. Also if there are many loaders called in turn, much memory could be consumed and
	 * discarded. For this reason the registration process requires a file extension and tries 
	 * all the loaders for a given extension if the extension is registered already. 
	 * Otherwise it tries all loaders - in no particular order.
	 * 
	 * @param path
	 * @param mon
	 * @return DataHolder
	 * @throws Exception
	 */
	public static IDataHolder getData(final String path, final IMonitor mon) throws Exception {
		return getData(path, true, mon);
	}

	/**
	 * Call to load any file type into memory. By default loads all data sets, therefore
	 * could take a **long time**.
	 * 
	 * In addition to find out if a given file loads with a particular loader - it actually 
	 * LOADS it. 
	 * 
	 * Therefore it can take a while to run depending on how quickly the loader
	 * fails. Also if there are many loaders called in turn, much memory could be consumed and
	 * discarded. For this reason the registration process requires a file extension and tries 
	 * all the loaders for a given extension if the extension is registered already. 
	 * Otherwise it tries all loaders - in no particular order.
	 * 
	 * @param path to file
	 * @param willLoadMetadata dictates whether metadata is not loaded (if possible)
	 * @param mon
	 * @return DataHolder
	 * @throws Exception
	 */
	public static IDataHolder getData(final String path, final boolean willLoadMetadata, final IMonitor mon) throws Exception {
		return getData(path, willLoadMetadata, false, false, mon);
	}

	/**
	 * Call to load any file type into memory. By default loads all data sets, therefore
	 * could take a **long time**.
	 * 
	 * In addition to find out if a given file loads with a particular loader - it actually 
	 * LOADS it. 
	 * 
	 * Therefore it can take a while to run depending on how quickly the loader
	 * fails. Also if there are many loaders called in turn, much memory could be consumed and
	 * discarded. For this reason the registration process requires a file extension and tries 
	 * all the loaders for a given extension if the extension is registered already. 
	 * Otherwise it tries all loaders - in no particular order.
	 * 
	 *   *synchronized* is REQUIRED because multiple threads load data simultaneously and without
	 *   a synchronized you can get data loaded twice which is SLOW.
	 * 
	 * @param path to file
	 * @param willLoadMetadata dictates whether metadata is not loaded (if possible)
	 * @param loadImageStacks if true, find and load images in the same directory as a stack
	 * @param mon
	 * @return DataHolder
	 * @throws Exception
	 */
	public static /*THIS IS REQUIRED:*/ synchronized  IDataHolder getData(final String   path, 
												  final boolean  willLoadMetadata, 
												  final boolean  loadImageStacks, 
											      final IMonitor mon) throws Exception {
		return getData(path, willLoadMetadata, loadImageStacks, false, mon);
	}

	/**
	 * Call to load any file type into memory. By default loads all data sets, therefore
	 * could take a **long time**.
	 * 
	 * In addition to find out if a given file loads with a particular loader - it actually 
	 * LOADS it. 
	 * 
	 * Therefore it can take a while to run depending on how quickly the loader
	 * fails. Also if there are many loaders called in turn, much memory could be consumed and
	 * discarded. For this reason the registration process requires a file extension and tries 
	 * all the loaders for a given extension if the extension is registered already. 
	 * Otherwise it tries all loaders - in no particular order.
	 * 
	 *   *synchronized* is REQUIRED because multiple threads load data simultaneously and without
	 *   a synchronized you can get data loaded twice which is SLOW.
	 * 
	 * @param path to file
	 * @param willLoadMetadata dictates whether metadata is not loaded (if possible)
	 * @param loadImageStacks if true, find and load images in the same directory as a stack
	 * @param lazily if true, <b>all</b> datasets in the data holder will be lazy otherwise the holder
	 * may contain non-lazy datasets
	 * @param mon
	 * @return DataHolder
	 * @throws Exception
	 */
	public static /*THIS IS REQUIRED:*/ synchronized  IDataHolder getData(final String   path,
												final boolean willLoadMetadata, 
												final boolean loadImageStacks, 
												final boolean lazily, 
												final IMonitor mon) throws Exception {

		if (!(new File(path)).exists()) throw new FileNotFoundException(path);

		// IMPORTANT: DO NOT USE loadImageStacks in Key. 
		// Instead when loadImageStacks=true, we add the stack to the already
		// cached data. So reducing the cache size.
		final LoaderKey key = new LoaderKey();
		key.setFilePath(path);
		key.setMetadata(willLoadMetadata);
		// END IMPORTANT

		final Object cachedObject = getSoftReference(key);
		IDataHolder holder = null;
		if (cachedObject!=null && cachedObject instanceof IDataHolder) holder = (IDataHolder)cachedObject;

		if (holder==null) { // try and load it
			final Iterator<Class<? extends IFileLoader>> it = getIterator(path);
			if (it == null) return null;
	
			// Currently this method simply cycles through all loaders.
			// When it finds one which does not give an exception on loading it
			// returns the data from this loader.
			while (it.hasNext()) {
				final Class<? extends IFileLoader> clazz = it.next();
				final IFileLoader loader = getLoader(clazz, path);
				loader.setLoadMetadata(willLoadMetadata);
				loader.setLoadAllLazily(lazily);
				try {
					// NOTE Assumes loader fails quickly and nicely
					// if given the wrong file. If a loader does not
					// do this it should not be registered with LoaderFactory
					holder = loader.loadFile(mon);
					holder.setLoaderClass(clazz);
					holder.setFilePath(path);

					if (!lazily) {
						key.setMetadata(holder.getMetadata()!=null);
						boolean cached = recordSoftReference(key, holder);
						if (!cached) System.err.println("Loader factory failed to cache "+path);
					}
					break;
					
				} catch (OutOfMemoryError ome) {
					logger.error("There was not enough memory to load {}", path);
					throw new ScanFileHolderException("Out of memory in loader factory", ome);
				} catch (Throwable ne) {
					logger.trace("Loader {} caused {}", loader, ne);
					continue;
				}
			}
		}
		
		// For images, we can put another item in the data holder
		// which represents the stack of other images in the same directory.
		try {
			if (loadImageStacks && holder!=null) {

				if (holder.size()==1 && holder.getLazyDataset(0).getRank()==2 && !isH5(path)) {
					final Map<String,ILazyDataset> stack = getImageStack(path, holder, mon);
					if (stack!=null) {
						for (String name : stack.keySet()) {
							holder.addDataset(name, stack.get(name));
						}
					}
				}

			}
		} catch (Throwable ne) { // It is not a fatal error to fail to load an image stack.
			logger.error("Cannot load image stack!", ne);
		}
		return holder;
	}
	
	/**
	 * Call to load file into memory with specific loader class
	 * 
	 *   *synchronized* is REQUIRED because multiple threads load data simultaneously and without
	 *   a synchronized you can get data loaded twice which is SLOW.
     *
	 * @param clazz loader class
	 * @param path to file
	 * @param willLoadMetadata dictates whether metadata is not loaded (if possible)
	 * @param mon
	 * @return data holder (can be null)
	 * @throws ScanFileHolderException
	 */
	public static /*THIS IS REQUIRED:*/ synchronized IDataHolder getData(Class<? extends IFileLoader> clazz, 
						                         String path, 
			                                     boolean willLoadMetadata, 
			                                     IMonitor mon) throws Exception {
		
		if (!(new File(path)).exists()) throw new FileNotFoundException(path);

		// IMPORTANT: DO NOT USE loadImageStacks in Key. 
		// Instead when loadImageStacks=true, we add the stack to the already
		// cached data. So reducing the cache size.
		final LoaderKey key = new LoaderKey();
		key.setFilePath(path);
		key.setMetadata(willLoadMetadata);
		// END IMPORTANT

		final Object cachedObject = getSoftReference(key);
		IDataHolder holder = null;
		if (cachedObject!=null && cachedObject instanceof IDataHolder) holder = (IDataHolder)cachedObject;
        if (holder!=null) return holder;
		
		IFileLoader loader;
		try {
			loader = getLoader(clazz, path);
		} catch (Exception e) {
			logger.error("Cannot create loader", e);
			throw new ScanFileHolderException("Cannot create loader", e);
		}
		if (loader == null) {
			logger.error("Cannot create loader");
			throw new ScanFileHolderException("Cannot create loader");
		}

		loader.setLoadMetadata(willLoadMetadata);
		try {
			holder = loader.loadFile(mon);
			holder.setLoaderClass(clazz);
			holder.setFilePath(path);
			
			key.setMetadata(holder.getMetadata()!=null);
			boolean cached = recordSoftReference(key, holder);
			if (!cached) System.err.println("Loader factory failed to cache "+path);
			return holder;
			
		} catch (OutOfMemoryError ome) {
			logger.error("There was not enough memory to load {}", path);
			throw new ScanFileHolderException("Out of memory in loader factory", ome);
		} catch (Throwable ne) {
			logger.trace("Loader {} caused {}", loader, ne);
			throw new ScanFileHolderException("Loader error", ne);
		}
	}

	/**
	 * Store data into cache
	 * 
	 *   *synchronized* is REQUIRED because multiple threads load data simultaneously and without
	 *   a synchronized you can get data loaded twice which is SLOW.
     *
	 * @param holder
	 */
	public static /*THIS IS REQUIRED:*/ synchronized void cacheData(IDataHolder holder) {
		cacheData(holder, 0);
	}

	/**
	 * Store data into cache
	 * 
	 *   *synchronized* is REQUIRED because multiple threads load data simultaneously and without
	 *   a synchronized you can get data loaded twice which is SLOW.
     *
	 * @param holder
	 * @param imageNumber
	 */
	public static /*THIS IS REQUIRED:*/ synchronized void cacheData(IDataHolder holder, int imageNumber) {
		final LoaderKey key = new LoaderKey();
		key.setFilePath(holder.getFilePath());
		key.setMetadata(holder.getMetadata() != null);
		key.setImageNumber(imageNumber);

		if (!recordSoftReference(key, holder))
			System.err.println("Loader factory failed to cache "+holder.getFilePath());
	}

	/**
	 * Fetch data from cache
	 * 
	 *   *synchronized* is REQUIRED because multiple threads load data simultaneously and without
	 *   a synchronized you can get data loaded twice which is SLOW.
     *
	 * @param path
	 * @param willLoadMetadata dictates whether metadata is not loaded (if possible)
	 * @return data or null if not in cache
	 */
	public static /*THIS IS REQUIRED:*/ synchronized IDataHolder fetchData(String path, boolean willLoadMetadata) {
		return fetchData(path, willLoadMetadata, 0);
	}

	/**
	 * Fetch data from cache
	 * 
	 *   *synchronized* is REQUIRED because multiple threads load data simultaneously and without
	 *   a synchronized you can get data loaded twice which is SLOW.
     *
	 * @param path
	 * @param willLoadMetadata dictates whether metadata is not loaded (if possible)
	 * @param imageNumber
	 * @return data or null if not in cache
	 */
	public static /*THIS IS REQUIRED:*/ synchronized IDataHolder fetchData(String path, boolean willLoadMetadata, int imageNumber) {
		final LoaderKey key = new LoaderKey();
		key.setFilePath(path);
		key.setMetadata(willLoadMetadata);
		key.setImageNumber(imageNumber);

		final Object cachedObject = getSoftReference(key);
		return cachedObject instanceof IDataHolder ? (IDataHolder) cachedObject : null;
	}

	/**
	 * This method can be used to load an image stack of other images in the same directory.
	 * 
	 * @param filePath - to one of the images in the stack.
	 * @param holder
	 * @param mon
	 * @return and image stack for 
	 * @throws Exception
	 */
	public static final Map<String,ILazyDataset> getImageStack(final String filePath, IDataHolder holder, IMonitor mon) throws Exception {
		
		if (filePath==null) return null;
		
		final Map<String, List<String>> imageFilenames = new TreeMap<String, List<String>>();
		imageFilenames.put("Image Stack", new ArrayList<String>(31));
		
		final File   file  = new File(filePath);
		final String ext  = FileUtils.getFileExtension(file.getName());
		final File   par = file.getParentFile();
		
		Pattern pattern = Pattern.compile("(.+)_(\\d+)."+ext);
		if (par.isDirectory()) {
			for (String fName : par.list()) {
				if (fName.endsWith(ext)) {
					
					final File f = new File(par,fName);
					String name  = "Image Stack";
					
					// Name will be something like 35873_M3S15_1_0001.cbf
					// A string '35873_M3S15_1_' followed by a 4-digit number, followed by the file extension.
					Matcher matcher = pattern.matcher(fName);
					if (matcher.matches()) {
						name = matcher.group(1);
						if (!imageFilenames.containsKey(name)) {
							imageFilenames.put(name, new ArrayList<String>(31));
						}
					} 
					
					imageFilenames.get(name).add(f.getAbsolutePath());
				}
			}
		}
		
		if (imageFilenames.size() > 0) {
			
			Map<String,ILazyDataset> ret = new TreeMap<String,ILazyDataset>();
			for (String name : imageFilenames.keySet()) {
				final List<String> files = imageFilenames.get(name);
				
				if (files==null || files.size()<2) continue;
	 			ImageStackLoader loader = new ImageStackLoader(files, holder, mon);
				LazyDataset lazyDataset = new LazyDataset(name, loader.getDtype(), loader.getShape(), loader);
				ret.put(name, lazyDataset);
			}
			
			if (ret.size()>0) return ret;
		}
		return null;
	}

	private final static Object LOCK = new Object();

	/**
	 * May be null
	 * @param key
	 * @return the object referenced or null if it got garbaged or was not cached yet
	 */
	private static Object getSoftReference(LoaderKey key) {
		Object o = getReference(key);
		if (o != null) {
			return o;
		}
		if (key.hasMetadata()) { // wanted metadata but none there
			return null;
		}
		key.setMetadata(true); // try with unwanted metadata
		return getReference(key);
	}

	/**
	 * May be null
	 * @param key
	 * @return the object referenced or null if it got garbaged or was not cached yet
	 */
	private static Object getSoftReferenceWithMetadata(LoaderKey key) {
		Object o = getReference(key);
		if (o != null) return o;

		LoaderKey k = findKeyWithMetadata(key);
		return k == null ? null : getReference(k);
	}


	private static final String NO_CACHING = "uk.ac.diamond.scisoft.analysis.io.nocaching";

	/**
	 * May be null
	 * @param key
	 * @return the object referenced or null if it got garbaged or was not cached yet
	 */
	private static IDataAnalysisObject getReference(LoaderKey key) {
		if (Boolean.getBoolean(NO_CACHING)) return null;
		synchronized (LOCK) {
			try {
		        final Reference<IDataAnalysisObject> ref = SOFT_CACHE.get(key);
		        if (ref == null) return null;
		        IDataAnalysisObject got = ref.get();
		        return got;
			} catch (Throwable ne) {
				return null;
			}
		}
	}

	private static LoaderKey findKeyWithMetadata(LoaderKey key) {
		if (Boolean.getBoolean(NO_CACHING)) return null;
		synchronized (LOCK) {
			for (LoaderKey k : SOFT_CACHE.keySet()) {
				if (k.isSameFile(key) && k.hasMetadata()) {
					return k;
				}
			}
			return null;
		}

	}

	/**
	 * 
	 * @param key
	 * @param value
	 * @return true if another value has been replaced.
	 */
	private static boolean recordSoftReference(LoaderKey key, IDataAnalysisObject value) {
		
		if (Boolean.getBoolean(NO_CACHING)) return false;
		synchronized (LOCK) {
			try {
				Reference<IDataAnalysisObject> ref = Boolean.getBoolean("uk.ac.diamond.scisoft.analysis.io.weakcaching")
						                           ? new WeakReference<IDataAnalysisObject>(value)
						                           : new SoftReference<IDataAnalysisObject>(value);
				return SOFT_CACHE.put(key, ref)!=null;
			} catch (Throwable ne) {
				return false;
			}
		}
	}

	/**
	 * Call to load any file type into memory. If a loader implements IMetaLoader will
	 * use this fast method to avoid loading the entire file into memory. If the loader
	 * does not implement IMetaLoader it will return null. Then you should use getData(...) 
	 * to load the entire file.
	 * 
	 * 
	 * @param path
	 * @param mon
	 * @return IMetadata
	 * @throws Exception
	 */
	public static IMetadata getMetadata(final String path, final IMonitor mon) throws Exception {

		
		if (!(new File(path)).exists()) throw new FileNotFoundException(path);
		final LoaderKey key = new LoaderKey();
		key.setFilePath(path);
		key.setMetadata(true);
		
		Object cachedObject = getSoftReferenceWithMetadata(key);
		if (cachedObject!=null) {
			if (cachedObject instanceof DataHolder) {
				IMetadata meta = ((DataHolder) cachedObject).getMetadata();
			    if (meta!=null) return meta;
			}
			if (cachedObject instanceof IMetadata)
				return (IMetadata)cachedObject;
			logger.warn("Cached object is not a metadata object or contain one");
		}

		final Iterator<Class<? extends IFileLoader>> it = getIterator(path);
		if (it == null) return null;

		// Currently this method simply cycles through all loaders.
		// When it finds one which does not give an exception on loading, it
		// returns the data from this loader.
		while (it.hasNext()) {
			final Class<? extends IFileLoader> clazz = it.next();
			final IFileLoader loader = getLoader(clazz, path);
			if (!IMetaLoader.class.isInstance(loader)) continue;

			try {
				// NOTE Assumes loader fails quickly and nicely
				// if given the wrong file. If a loader does not
				// do this, it should not be registered with LoaderFactory
				((IMetaLoader) loader).loadMetadata(mon);
				IMetadata meta = ((IMetaLoader) loader).getMetadata();
				recordSoftReference(key, meta);
				return meta;
			} catch (Throwable ne) {
				//logger.trace("Cannot load nexus meta data", ne);
				logger.trace("Loader {} caused {}", loader, ne);
				continue;
			}
		}

		return null;
	}

	/**
	 * Loads a single dataset by loading the whole data holder and asking for the dataset
	 * by name. Loaders should load things properly to ILazyDatasets and then this method
	 * will take from the data holder the set by name. This uses caching of the data holder
	 * if the data has been previously loaded into a DataHolder.
	 * 
	 * NOTE LazyDatasets will be loaded into memory by this method. To avoid this use:
	 * <code>
	 * IDataHolder holder = LoaderFactory.getData(...)
	 * ILazyDataset lz    = holder.getLazyDataset(...)
	 * <code>
	 * 
	 * Now the ILazyDataset is available rather than loading all into memory.
	 * If you use this method all the data of the dataset will be loaded to memory.
	 * 
	 * @param path
	 * @param mon
	 * @return IDataset
	 * @throws Exception
	 */
	public static IDataset getDataSet(final String path, final String name, final IMonitor mon) throws Exception {

		// Makes the cache the DataHolder
        final IDataHolder holder = getData(path, true, mon);
        if (holder == null) return null;
        try {
            return holder.getDataset(name);
        } catch (Exception ne) { // We try to load the data from the ILazyDataset
        	final ILazyDataset lz = holder.getLazyDataset(name);
        	IDataset loaded = lz.getSlice(); // Loads all data
        	holder.addDataset(name, loaded); // We just loaded the data, cache it
        	return loaded;
        }
	}

	/**
	 * Returns true if a given file is an IMetadata and able to load metadata without the data
	 * 
	 * @param path
	 * @return true if can load metadata without all data being loaded.
	 */
	public boolean isMetaLoader(final String path) throws Exception {

		return isInstanceSupported(path, IMetaLoader.class);
	}

	private boolean isInstanceSupported(String path, Class<?> interfaceClass) throws Exception {

		final String extension = FileUtils.getFileExtension(path).toLowerCase();

		if (LOADERS.containsKey(extension)) {
			final Collection<Class<? extends IFileLoader>> loaders = LOADERS.get(extension);

			for (Class<? extends IFileLoader> clazz : loaders) {
				final IFileLoader loader = getLoader(clazz, path);
				if (interfaceClass.isInstance(loader))
					return true;
			}
		}
		return false;
	}

	/**
	 * Gets an AbstractFileLoader for the given class and file path.
	 * 
	 * @param clazz
	 * @param path
	 * @return AbstractFileLoader
	 * @throws Exception
	 */
	public static IFileLoader getLoader(Class<? extends IFileLoader> clazz, final String path) throws Exception {

		IFileLoader loader;
		try {
			final Constructor<?> singleString = clazz.getConstructor(String.class);
			loader = (AbstractFileLoader) singleString.newInstance(path);
		} catch (NoSuchMethodException e) {
			loader = clazz.newInstance();

			final Method setFile = loader.getClass().getMethod("setFile", String.class);
			setFile.invoke(loader, path);
		} catch (NoClassDefFoundError ne) { // CBF Loader does this on win64
			loader = null;
		} catch (UnsatisfiedLinkError ule) {// CBF Loader does this on win64, the first time
			loader = null;
		}

		return loader;
	}

	/**
	 * Get class that can load files of given extension
	 * 
	 * @param extension
	 * @return loader class
	 */
	public static Class<? extends IFileLoader> getLoaderClass(String extension) {
		List<Class<? extends IFileLoader>> loader = LOADERS.get(extension); 
		return (loader!=null) ? loader.get(0) : null;
	}

	private static Iterator<Class<? extends IFileLoader>> getIterator(String path) throws IllegalAccessException {

		if ((new File(path).isDirectory()))
			throw new IllegalAccessException("Cannot load directories with LoaderFactory!");

		final String extension = FileUtils.getFileExtension(path).toLowerCase();
		Iterator<Class<? extends IFileLoader>> it = null;

		if (LOADERS.containsKey(extension)) {
			it = LOADERS.get(extension).iterator();
		} else {
			// We may have a zipped file type that we support
			final File file = new File(path);
			final String regEx = ".+\\." + getLoaderExpression() + "\\." + getZipExpression();

			final Matcher m = Pattern.compile(regEx).matcher(file.getName());
			if (m.matches()) {
				final String realExt = m.group(1);
				if (LOADERS.keySet().contains(realExt)) {
					final Collection<Class<? extends IFileLoader>> ret = new ArrayList<Class<? extends IFileLoader>>(1);
					ret.add(CompressedLoader.class);
					return ret.iterator();
				}
			}

			if (!searchingAllowed)
				return null;

			final Set<Class<? extends IFileLoader>> all = new HashSet<Class<? extends IFileLoader>>();
			for (String ext : LOADERS.keySet())
				all.addAll(LOADERS.get(ext));
			it = all.iterator();
		}
		return it;
	}

	public static void registerUnzip(final String extension, final Class<? extends InputStream> input) {
		UNZIPPERS.put(extension, input);
	}

	/**
	 * Could cache this but it will be fast
	 */
	protected static String getZipExpression() {
		return getExpression(UNZIPPERS.keySet().iterator());
	}

	/**
	 * Could cache this but it will be fast
	 */
	protected static String getLoaderExpression() {
		return getExpression(LOADERS.keySet().iterator());
	}

	/**
	 * Could cache this but it will be fast
	 */
	private static String getExpression(final Iterator<String> it) {
		final StringBuilder buf = new StringBuilder();
		buf.append("(");
		while (it.hasNext()) {

			buf.append(it.next());
			if (it.hasNext())
				buf.append("|");
		}
		buf.append(")");
		return buf.toString();
	}

	/**
	 * Throws an exception if the loader is not ready to be used with LoaderFactory.
	 * Otherwise adds the class to the list of loaders.
	 * 
	 * NOTE that duplicates are allowed and the LoaderFactory simply tries loaders until
	 * one works. If loaders do not fail fast on invalid files then this approach does not work.
	 * 
	 * This has been tested by adding a test for each file type using the loader factory. This
	 * coverage could be extended by adding more example files and attempting to load them
	 * with the factory. However as long as each file type is passed through LoaderFactory and
	 * checks are made in the test to ensure that the loader is working, there is a good chance
	 * that it will find the right loader.
	 * 
	 * @param extension - lower case string
	 * @param loader
	 * @throws Exception
	 */
	public static void registerLoader(final String extension, final Class<? extends IFileLoader> loader) throws Exception {

		List<Class<? extends IFileLoader>> list = prepareRegistration(extension, loader);

		// Since not using set of loaders anymore must use contains to ensure
		// that a memory leak does not occur.
		if (!list.contains(loader)) list.add(loader);
	}

	/**
	 * Throws an exception if the loader is not ready to be used with LoaderFactory.
	 * Otherwise adds the class to the list of loaders at the position specified.
	 * 
	 * NOTE that duplicates are allowed and the LoaderFactory simply tries loaders until
	 * one works. If loaders do not fail fast on invalid files then this approach does not work.
	 * 
	 * This has been tested by adding a test for each file type using the loader factory. This
	 * coverage could be extended by adding more example files and attempting to load them
	 * with the factory. However as long as each file type is passed through LoaderFactory and
	 * checks are made in the test to ensure that the loader is working, there is a good chance
	 * that it will find the right loader.
	 * 
	 * @param extension - lower case string
	 * @param loader
	 * @throws Exception
	 */
	public static void registerLoader(final String extension, final Class<? extends IFileLoader> loader, final int position) throws Exception {

		List<Class<? extends IFileLoader>> list = prepareRegistration(extension, loader);
		// Since not using set of loaders anymore must use contains to ensure
		// that a memory leak does not occur.
		if (!list.contains(loader)) list.add(position, loader);
	}

	private static List<Class<? extends IFileLoader>> prepareRegistration(String extension, Class<? extends IFileLoader> loader) throws Exception {
		try {
			loader.getConstructor(String.class);
		} catch (NoSuchMethodException e) {
			// TODO These messages are not quite correct
			if (loader.getMethod("setFile", String.class) == null)
				throw new Exception("Loaders must have method setFile(String path)");
			if (loader.getConstructor() == null)
				throw new Exception("Loaders must have a no argument constructor!");
		}

		List<Class<? extends IFileLoader>> list = LOADERS.get(extension);
		if (list == null) {
			list = new ArrayList<Class<? extends IFileLoader>>();
			LOADERS.put(extension, list);
		}
		return list;
	}

	/**
	 * Call to clear all the loaders registered for a given extension.
	 * 
	 * @param extension
	 * @return the old loader list, now removed, if any.
	 */
	public static List<Class<? extends IFileLoader>> clearLoader(final String extension) {
		return LOADERS.remove(extension);
	}

	private static boolean searchingAllowed = false;

	public static void setLoaderSearching(final boolean sa) {
		searchingAllowed = sa;
	}

	protected static Class<? extends InputStream> getZipStream(final String extension) {
		return UNZIPPERS.get(extension);
	}

	
	private static IMetadata lockedMetaData;

	/**
	 * DO NOT MAKE Public. Use ILoaderService instead.
	 * @return metaData
	 */
	static IMetadata getLockedMetaData() {
		return lockedMetaData;
	}

	/**
	 * @Internal do not use. Use ILoaderService.getLockedDiffractionMetaData()
	 */
	public static void setLockedMetaData(IMetadata lockedMetaData) {
		LoaderFactory.lockedMetaData = lockedMetaData;
		if (lockedMetaData==null) clear();
	}

	private final static List<String> HDF5_EXT;
	static {
		List<String> tmp = new ArrayList<String>(7);
		tmp.add("h5");
		tmp.add("nxs");
		tmp.add("hd5");
		tmp.add("hdf5");
		tmp.add("hdf");
		tmp.add("nexus");
		HDF5_EXT = Collections.unmodifiableList(tmp);
	}	

	private static boolean isH5(final String filePath) {
		if (filePath == null) { return false; }
		final String ext = FileUtils.getFileExtension(filePath);
		if (ext == null) { return false; }
		return HDF5_EXT.contains(ext.toLowerCase());
	}
}
