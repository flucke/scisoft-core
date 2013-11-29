/*-
 * Copyright 2013 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.analysis.fitting.functions;

import org.junit.Assert;
import org.junit.Test;

public class StraightLineTest {

	private static final double ABS_TOL = 1e-7;

	@Test
	public void testFunction() {
		IFunction f = new StraightLine();
		Assert.assertEquals(2, f.getNoOfParameters());
		f.setParameterValues(23., -10.);
		Assert.assertArrayEquals(new double[] {23., -10.}, f.getParameterValues(), ABS_TOL);
		Assert.assertEquals(23. - 10., f.val(1), ABS_TOL);
	}
}
