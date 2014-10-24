/**
 * Copyright (c) 2008 The Regents of the University of California.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *   1. Redistributions of source code must retain the above copyright
 *      notice, this list of conditions, and the following disclaimer.
 *   2. Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions, and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *   3. Redistributions must acknowledge that this software was
 *      originally developed by the UCSF Computer Graphics Laboratory
 *      under support by the NIH National Center for Research Resources,
 *      grant P41-RR01081.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package clusterMaker.algorithms.attributeClusterers;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 *  BaseMatrix is a basic numeric matrix.
 *  Independent of Cytoscape.
 */
public class BaseMatrix {
	protected int nRows;
	protected int nColumns;
	protected Double matrix[][];
	protected double colWeights[];
	protected double rowWeights[];
	protected double maxAttribute;
	protected String rowLabels[];
	protected String columnLabels[];
	
	protected boolean transpose;
	protected boolean symmetrical;
	
	/**
 	 * Make this array public so it can be reused
 	 */
	public static DistanceMetric[] distanceTypes = { DistanceMetric.EUCLIDEAN,
	                                                 DistanceMetric.CITYBLOCK,
	                                                 DistanceMetric.CORRELATION,
	                                                 DistanceMetric.ABS_CORRELATION,
	                                                 DistanceMetric.UNCENTERED_CORRELATION,
	                                                 DistanceMetric.ABS_UNCENTERED_CORRELATION,
	                                                 DistanceMetric.SPEARMANS_RANK,
	                                                 DistanceMetric.KENDALLS_TAU,
	                                                 DistanceMetric.VALUE_IS_CORRELATION };
	
	/**
	 * Package-private constructor to be implicitly called by Matrix.
	 */
	BaseMatrix() {}
	
	/**
	 * Constructor.
	 * @param rows number of rows
	 * @param cols number of columns
	 */
	public BaseMatrix(int rows, int cols) {
		init(rows, cols);
	}
	
	/**
	 * Construct BaseMatrix from existing data array. Elements are populated in row-major order.
	 * @param rows number of rows (set to 0 for auto, if {@code cols} is specified)
	 * @param cols number of columns (set to 0 for auto, if rows is specified)
	 * @param data data array
	 */
	public BaseMatrix(int rows, int cols, Double data[]) {
		// determine and check dimensions
		if (rows == 0) {
			rows = data.length / cols;
		}
		if (cols == 0) {
			cols = data.length / rows;
		}
		if (rows * cols != data.length) {
			throw new IllegalArgumentException("Data array length does not conform with specified matrix dimension");
		}
		
		init(rows, cols);
		
		// populate matrix in row-major order
		int k = 0;
		for (int i = 0; i < rows; ++i) {
			for (int j = 0; j < cols; ++j) {
				matrix[i][j] = data[k++];
			}
		}
	}
	
	private void init(int rows, int cols) {
		this.nRows = rows;
		this.nColumns = cols;
		this.matrix = new Double[rows][cols];
		this.colWeights = new double[cols];
		this.rowWeights = new double[rows];
		this.columnLabels = new String[cols];
		this.rowLabels = new String[rows];
		this.transpose = false;
		setUniformWeights();
	}
	
	public int nRows() { return this.nRows; }

	public int nColumns() { return this.nColumns; }

	public Double getValue(int row, int column) {
		return matrix[row][column];
	}
	
	public double doubleValue(int row, int column) {
		if (matrix[row][column] != null)
			return matrix[row][column].doubleValue();
		return Double.NaN;
	}
	
	public void setValue(int row, int column, double value) {
		matrix[row][column] = new Double(value);
	}

	public void setValue(int row, int column, Double value) {
		matrix[row][column] = value;
	}

	public boolean hasValue(int row, int column) {
		if (matrix[row][column] != null)
			return true;
		return false;
	}
	
	public void setUniformWeights() {
		if (colWeights == null || rowWeights == null) {
			colWeights = new double[nColumns];
			rowWeights = new double[nRows];
		}
		Arrays.fill(this.colWeights,1.0);
		Arrays.fill(this.rowWeights,1.0);
	}
	
	public double[] getRowWeights() {
		return this.rowWeights;
	}

	public double getRowWeight(int row) {
		return this.rowWeights[row];
	}

	public double[] getColWeights() {
		return this.colWeights;
	}

	public double getColWeight(int col) {
		return this.colWeights[col];
	}
	
	public double[] getWeights() {
		return colWeights;
	}

	public void setRowWeight(int row, double value) {
		if (rowWeights == null) {
			rowWeights = new double[nRows];
		}
		rowWeights[row] = value;
	}

	public void setColWeight(int col, double value) {
		if (colWeights == null) {
			colWeights = new double[nColumns];
		}
		colWeights[col] = value;
	}
	
	public String[] getColLabels() {
		return this.columnLabels;
	}

	public String getColLabel(int col) {
		return this.columnLabels[col];
	}

	public void setColLabel(int col, String label) {
		this.columnLabels[col] = label;
	}

	public String[] getRowLabels() {
		return this.rowLabels;
	}

	public String getRowLabel(int row) {
		return this.rowLabels[row];
	}

	public void setRowLabel(int row, String label) {
		this.rowLabels[row] = label;
	}
	
	protected void setRowLabels(List<String>labelList) {
		int index = 0;
		for (String label: labelList){
			this.rowLabels[index++] = label;
		}
	}

	protected void setColumnLabels(List<String>labelList) {
		int index = 0;
		for (String label: labelList){
			this.columnLabels[index++] = label;
		}
	}

	public double[] getRank(int row) {
		// Get the masked row
		double[] tData = new double[nColumns];
		int nVals = 0;
		for (int column = 0; column < nColumns; column++) {
			if (hasValue(row,column))
				tData[nVals++] = matrix[row][column].doubleValue();
		}

		if (nVals == 0)
			return null;

		// Sort the data
		Integer index[] = indexSort(tData,nVals);

		// Build a rank table
		double[] rank = new double[nVals];
		for (int i = 0; i < nVals; i++) rank[index[i].intValue()] = i;

		// Fix for equal ranks
		int i = 0;
		while (i < nVals) {
			int m = 0;
			double value = tData[index[i].intValue()];
			int j = i+1;
			while (j < nVals && tData[index[j].intValue()] == value) j++;
			m = j - i; // Number of equal ranks found
			value = rank[index[i].intValue()] + (m-1)/2.0;
			for (j = i; j < i+m; j++) rank[index[j].intValue()] = value;
			i += m;
		}
		return rank;
	}

	public double[][] getDistanceMatrix(DistanceMetric metric) {
		double[][] result = new double[this.nRows][this.nRows];
		for (int row = 0; row < this.nRows; row++) {
			for (int column = row; column < this.nRows; column++) {
				result[row][column] = 
				   metric.getMetric(this, this, this.getWeights(), row, column);
				if (row != column)
					result[column][row] = result[row][column];  // Assumes symmetrical distances
				//System.out.println("distanceMatrix["+row+"]["+column+"] = "+result[row][column]);
			}
		}
		return result;
	}

	public void printMatrix() {
		for (int col = 0; col < nColumns; col++)
			System.out.print("\t"+columnLabels[col]);
		System.out.println();

		for (int row = 0; row < nRows; row++) {
			System.out.print(rowLabels[row]+"\t");
			for (int col = 0; col < nColumns; col++) {
				if (matrix[row][col] != null)
					System.out.print(matrix[row][col]+"\t");
				else
					System.out.print("\t");
			}
			System.out.println();
		}
	}

	public Integer[] indexSort(double[] tData, int nVals) {
		Integer[] index = new Integer[nVals];
		for (int i = 0; i < nVals; i++) index[i] = new Integer(i);
		IndexComparator iCompare = new IndexComparator(tData);
		Arrays.sort(index, iCompare);
		return index;
	}

	public Integer[] indexSort(int[] tData, int nVals) {
		Integer[] index = new Integer[nVals];
		for (int i = 0; i < nVals; i++) index[i] = new Integer(i);
		IndexComparator iCompare = new IndexComparator(tData);
		Arrays.sort(index, iCompare);
		return index;
	}

	public boolean isTransposed() { return this.transpose; }

	public boolean isSymmetrical() { return this.symmetrical; }

	public void setMissingToZero() {
		for (int row = 0; row < this.nRows; row++) {
			for (int col = 0; col < this.nColumns; col++ ) {
				if (matrix[row][col] == null)
					matrix[row][col] = new Double(0.0);
			}
		}
	}
	
	public void adjustDiagonals() {
		for (int col = 0; col < nColumns; col++ ) {
			matrix[col][col] = new Double(maxAttribute);
		}
	}
	
	private class IndexComparator implements Comparator<Integer> {
		double[] data = null;
		int[] intData = null;

		public IndexComparator(double[] data) { this.data = data; }

		public IndexComparator(int[] data) { this.intData = data; }

		public int compare(Integer o1, Integer o2) {
			if (data != null) {
				if (data[o1.intValue()] < data[o2.intValue()]) return -1;
				if (data[o1.intValue()] > data[o2.intValue()]) return 1;
				return 0;
			} else if (intData != null) {
				if (intData[o1.intValue()] < intData[o2.intValue()]) return -1;
				if (intData[o1.intValue()] > intData[o2.intValue()]) return 1;
				return 0;
			}
			return 0;
		}
	}
	
}