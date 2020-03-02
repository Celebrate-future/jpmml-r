/*
 * Copyright (c) 2015 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp;

import org.jpmml.evaluator.PMMLEquivalence;
import org.junit.Test;

public class GBMConverterTest extends ConverterTest {

	@Test
	public void evaluateFitAdaBoostAuditNA() throws Exception {
		evaluate("GBMAdaBoost", "AuditNA");
	}

	@Test
	public void evaluateWrappedAdaBoostAuditNA() throws Exception {
		evaluate("WrappedGBMAdaBoost", "AuditNA", new PMMLEquivalence(5e-13, 5e-13));
	}

	@Test
	public void evaluateFitBernoulliAuditNA() throws Exception {
		evaluate("GBMBernoulli", "AuditNA");
	}

	@Test
	public void evaluateFormulaIris() throws Exception {
		evaluate("GBMFormula", "Iris");
	}

	@Test
	public void evaluateFitIris() throws Exception {
		evaluate("GBM", "Iris");
	}

	@Test
	public void evaluateCaretFormulaIris() throws Exception {
		evaluate("TrainGBMFormula", "Iris");
	}

	@Test
	public void evaluateFormulaAutoNA() throws Exception {
		evaluate("GBMFormula", "AutoNA");
	}

	@Test
	public void evaluateFitAutoNA() throws Exception {
		evaluate("GBM", "AutoNA");
	}

	@Test
	public void evaluateCaretFormulaAutoNA() throws Exception {
		evaluate("TrainGBMFormula", "AutoNA");
	}
}