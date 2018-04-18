/*
 * Copyright (c) 2018 Villu Ruusmann
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

import java.util.ArrayList;
import java.util.List;

import org.dmg.pmml.DataType;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.converter.CategoricalFeature;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.ValueUtil;

public class RPartConverter extends TreeModelConverter<RGenericVector> {

	public RPartConverter(RGenericVector rpart){
		super(rpart);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector rpart = getObject();

		RGenericVector frame = (RGenericVector)rpart.getValue("frame");
		RExp terms = rpart.getValue("terms");

		RGenericVector xlevels = (RGenericVector)rpart.getAttributeValue("xlevels", true);
		RStringVector ylevels = (RStringVector)rpart.getAttributeValue("ylevels", true);

		RIntegerVector var = (RIntegerVector)frame.getValue("var");

		FormulaContext context = new XLevelsFormulaContext(xlevels);

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		SchemaUtil.setLabel(formula, terms, ylevels, encoder);

		List<String> names = SchemaUtil.removeSpecialSymbol(RExpUtil.getFactorLevels(var), "<leaf>", 0);

		SchemaUtil.addFeatures(formula, names, false, encoder);
	}

	@Override
	public TreeModel encodeModel(Schema schema){
		RGenericVector rpart = getObject();

		RGenericVector frame = (RGenericVector)rpart.getValue("frame");
		RStringVector method = (RStringVector)rpart.getValue("method");
		RNumberVector<?> splits = (RNumberVector<?>)rpart.getValue("splits");
		RIntegerVector csplit = (RIntegerVector)rpart.getValue("csplit", true);

		RIntegerVector var = (RIntegerVector)frame.getValue("var");
		RIntegerVector n = (RIntegerVector)frame.getValue("n");
		RIntegerVector ncompete = (RIntegerVector)frame.getValue("ncompete");
		RIntegerVector nsurrogate = (RIntegerVector)frame.getValue("nsurrogate");

		RIntegerVector rowNames = (RIntegerVector)frame.getAttributeValue("row.names");

		if((rowNames.getValues()).indexOf(Integer.MIN_VALUE) > -1){
			throw new IllegalArgumentException();
		}

		int[] splitOffsets = new int[1 + rowNames.size()];

		for(int offset = 0; offset < rowNames.size(); offset++){
			splitOffsets[offset + 1] = splitOffsets[offset] + ncompete.getValue(offset) + nsurrogate.getValue(offset) + (var.getValue(offset) != 1 ? 1 : 0);
		}

		switch(method.asScalar()){
			case "anova":
				return encodeRegression(frame, rowNames, var, n, splitOffsets, splits, csplit, schema);
			case "class":
				return encodeClassification(frame, rowNames, var, n, splitOffsets, splits, csplit, schema);
			default:
				throw new IllegalArgumentException();
		}
	}

	private TreeModel encodeRegression(RGenericVector frame, RIntegerVector rowNames, RIntegerVector var, RIntegerVector n, int[] splitOffsets, RNumberVector<?> splits, RIntegerVector csplit, Schema schema){
		RNumberVector<?> yval = (RNumberVector<?>)frame.getValue("yval");

		ScoreEncoder scoreEncoder = new ScoreEncoder(){

			@Override
			public void encode(Node node, int offset){
				Number score = yval.getValue(offset);
				Number recordCount = n.getValue(offset);

				node
					.setScore(ValueUtil.formatValue(score))
					.setRecordCount(recordCount.doubleValue());
			}
		};

		Node root = new Node()
			.setPredicate(new True());

		encodeNode(root, 1, rowNames, var, n, splitOffsets, splits, csplit, scoreEncoder, schema);

		TreeModel treeModel = new TreeModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema.getLabel()), root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT)
			.setNoTrueChildStrategy(TreeModel.NoTrueChildStrategy.RETURN_LAST_PREDICTION);

		return treeModel;
	}

	private TreeModel encodeClassification(RGenericVector frame, RIntegerVector rowNames, RIntegerVector var, RIntegerVector n, int[] splitOffsets, RNumberVector<?> splits, RIntegerVector csplit, Schema schema){
		RDoubleVector yval2 = (RDoubleVector)frame.getValue("yval2");

		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		List<String> categories = categoricalLabel.getValues();

		ScoreEncoder scoreEncoder = new ScoreEncoder(){

			private List<Integer> classes = null;

			private List<List<? extends Number>> recordCounts = new ArrayList<>();


			{
				int rows = rowNames.size();
				int columns = 1 + (2 * categories.size()) + 1;

				List<Integer> classes = ValueUtil.asIntegers(FortranMatrixUtil.getColumn(yval2.getValues(), rows, columns, 0));

				this.classes = new ArrayList<>(classes);

				for(int i = 0; i < categories.size(); i++){
					List<? extends Number> recordCounts = FortranMatrixUtil.getColumn(yval2.getValues(), rows, columns, 1 + i);

					this.recordCounts.add(new ArrayList<>(recordCounts));
				}
			}

			@Override
			public void encode(Node node, int offset){
				String score = categories.get(this.classes.get(offset) - 1);
				Integer recordCount = n.getValue(offset);

				node
					.setScore(score)
					.setRecordCount(recordCount.doubleValue());

				for(int i = 0; i < categories.size(); i++){
					List<? extends Number> recordCounts = this.recordCounts.get(i);

					ScoreDistribution scoreDistribution = new ScoreDistribution()
						.setValue(categories.get(i))
						.setRecordCount(recordCounts.get(offset).doubleValue());

					node.addScoreDistributions(scoreDistribution);
				}
			}
		};

		Node root = new Node()
			.setPredicate(new True());

		encodeNode(root, 1, rowNames, var, n, splitOffsets, splits, csplit, scoreEncoder, schema);

		TreeModel treeModel = new TreeModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(schema.getLabel()), root)
			.setSplitCharacteristic(TreeModel.SplitCharacteristic.BINARY_SPLIT)
			.setNoTrueChildStrategy(TreeModel.NoTrueChildStrategy.RETURN_LAST_PREDICTION)
			.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));

		return treeModel;
	}

	private void encodeNode(Node node, int rowName, RIntegerVector rowNames, RIntegerVector var, RIntegerVector n, int[] splitOffsets, RNumberVector<?> splits, RIntegerVector csplit, ScoreEncoder scoreEncoder, Schema schema){
		int offset = rowNames.indexOf(rowName);
		if(offset < 0){
			throw new IllegalArgumentException();
		}

		node.setId(String.valueOf(rowName));

		scoreEncoder.encode(node, offset);

		int splitVar = var.getValue(offset) - 1;
		if(splitVar == 0){
			return;
		}

		Feature feature = schema.getFeature(splitVar - 1);

		RIntegerVector splitsDim = splits.dim();

		int splitRows = splitsDim.getValue(0);
		int splitColumns = splitsDim.getValue(1);

		List<? extends Number> ncat = FortranMatrixUtil.getColumn(splits.getValues(), splitRows, splitColumns, 1);
		List<? extends Number> index = FortranMatrixUtil.getColumn(splits.getValues(), splitRows, splitColumns, 3);

		int splitOffset = splitOffsets[offset];

		Node leftChild = new Node();
		Node rightChild = new Node();

		encodeNode(leftChild, rowName * 2, rowNames, var, n, splitOffsets, splits, csplit, scoreEncoder, schema);
		encodeNode(rightChild, (rowName * 2) + 1, rowNames, var, n, splitOffsets, splits, csplit, scoreEncoder, schema);

		boolean leftIsMajority = (leftChild.getRecordCount() > rightChild.getRecordCount());

		Predicate leftPredicate;
		Predicate rightPredicate;

		Number splitValue = index.get(splitOffset);

		int splitType = ValueUtil.asInt(ncat.get(splitOffset));
		if(splitType == -1){
			String value = ValueUtil.formatValue(splitValue);

			leftPredicate = createSimplePredicate(feature, SimplePredicate.Operator.LESS_THAN, value);
			rightPredicate = createSimplePredicate(feature, SimplePredicate.Operator.GREATER_OR_EQUAL, value);
		} else

		if(splitType == 1){
			String value = ValueUtil.formatValue(splitValue);

			leftPredicate = createSimplePredicate(feature, SimplePredicate.Operator.GREATER_OR_EQUAL, value);
			rightPredicate = createSimplePredicate(feature, SimplePredicate.Operator.LESS_THAN, value);
		} else

		{
			RIntegerVector csplitDim = csplit.dim();

			int csplitRows = csplitDim.getValue(0);
			int csplitColumns = csplitDim.getValue(1);

			List<Integer> csplitRow = FortranMatrixUtil.getRow(csplit.getValues(), csplitRows, csplitColumns, ValueUtil.asInt(splitValue) - 1);

			CategoricalFeature categoricalFeature = (CategoricalFeature)feature;

			List<String> values = categoricalFeature.getValues();

			leftPredicate = createSimpleSetPredicate(categoricalFeature, selectValues(values, csplitRow, 1));
			rightPredicate = createSimpleSetPredicate(categoricalFeature, selectValues(values, csplitRow, 3));
		}

		leftChild.setPredicate(leftPredicate);
		rightChild.setPredicate(rightPredicate);

		node.addNodes(leftChild, rightChild);
	}

	static
	private List<String> selectValues(List<String> values, List<Integer> valueFlags, int flag){
		List<String> result = new ArrayList<>(values.size());

		for(int i = 0; i < values.size(); i++){
			String value = values.get(i);
			Integer valueFlag = valueFlags.get(i);

			if(valueFlag == flag){
				result.add(value);
			}
		}

		return result;
	}

	static
	private interface ScoreEncoder {

		void encode(Node node, int offset);
	}
}