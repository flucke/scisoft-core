/*
 * Copyright (c) 2012 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.analysis.rpc.flattening;

import java.net.URL;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import uk.ac.diamond.scisoft.analysis.PythonHelper;
import uk.ac.diamond.scisoft.analysis.PythonHelper.PythonRunInfo;
import uk.ac.diamond.scisoft.analysis.rpc.internal.AnalysisRpcDoubleParser;

abstract public class FlatteningViaXmlRpcToPythonTestAbstract extends ExplicitFlatteningTestAbstract {
	private static PythonRunInfo pythonRunInfo;
	protected static XmlRpcClient client;

	@BeforeClass
	public static void start() throws Exception {
		pythonRunInfo = PythonHelper
				.runPythonFileBackground("src/uk/ac/diamond/scisoft/analysis/rpc/flattening/loopbackxmlrpc.py");

		Thread.sleep(SERVER_WAIT_TIME); // wait for server to start

		XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
		config.setServerURL(new URL("http://127.0.0.1:8713/xmlrpc"));
		client = new XmlRpcClient();
		client.setConfig(config);
	}

	@AfterClass
	public static void stop() {
		if (pythonRunInfo != null) {
			pythonRunInfo.terminate();
			pythonRunInfo.getStdout(true);
		}
		pythonRunInfo = null;
		client = null;
	}

	protected void checkPythonState() {
		if (pythonRunInfo != null && pythonRunInfo.hasTerminated()) {
			pythonRunInfo.getStdout(true);
			throw new RuntimeException("Python script unexpectedly terminated");
		}
	}

	/**
	 * This test fails because the loopback XML-RPC Client and Server don't have the type managers written to deal with
	 * the special values. The "special" XML-RPC client and server used by AnalysisRpc solves the issue, hence the test
	 * is enabled with that server is on.
	 * 
	 * @see AnalysisRpcDoubleParser
	 */
	@Override
	@Test
	public void testDoubleSpecialValues() {
		// test does not apply
	}
}
