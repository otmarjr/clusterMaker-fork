package clusterMaker.algorithms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Cytoscape imports
import cytoscape.CyEdge;
import cytoscape.CyNetwork;
import cytoscape.CyNode;
import cytoscape.Cytoscape;
import cytoscape.data.CyAttributes;
import cytoscape.logger.CyLogger;

import cern.colt.function.IntIntDoubleFunction;
import cern.colt.matrix.DoubleFactory2D;
import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;

import clusterMaker.algorithms.edgeConverters.EdgeAttributeHandler;
import clusterMaker.algorithms.edgeConverters.EdgeWeightConverter;

public class DistanceMatrix {
	private String edgeAttributeName = null;
	private double minWeight = Double.MAX_VALUE;
	private double maxWeight = Double.MIN_VALUE;
	private double minAttribute = Double.MAX_VALUE;
	private double maxAttribute = Double.MIN_VALUE;
	private double edgeCutOff = 0.0;
	private boolean unDirectedEdges = false;
	private boolean distanceValues = false;
	private boolean logValues = false;
	private List<CyNode> nodes = null;
	private List<CyEdge> edges = null;
	private DoubleMatrix2D matrix = null;
	private EdgeWeightConverter converter = null;

	private double[] edgeWeights = null;

	public DistanceMatrix(String edgeAttributeName, boolean selectedOnly, EdgeWeightConverter converter) {

		this.edgeAttributeName = edgeAttributeName;
		this.converter = converter;

		CyNetwork network = Cytoscape.getCurrentNetwork();
		String networkID = network.getIdentifier();
		if (!selectedOnly) {
			nodes = (List<CyNode>)network.nodesList();
			edges = (List<CyEdge>)network.edgesList();
		} else {
			nodes = new ArrayList<CyNode>();
			nodes.addAll(network.getSelectedNodes());
			edges = network.getConnectingEdges(nodes);
		}

		CyAttributes edgeAttributes = Cytoscape.getEdgeAttributes();

		edgeWeights = new double[edges.size()];

		// We do a fair amount of massaging the data, so let's just do it once
		for(int edgeIndex = 0; edgeIndex < edges.size(); edgeIndex++) {
			CyEdge edge = edges.get(edgeIndex);
			String id = edge.getIdentifier();

			edgeWeights[edgeIndex] = Double.MIN_VALUE;

			if(!edgeAttributes.hasAttribute(id,edgeAttributeName)) {
				// Special-case for "None" attribute.  We just assign an edge value of 1.
				if (edgeAttributeName.equals(EdgeAttributeHandler.NONEATTRIBUTE))
					edgeWeights[edgeIndex] = 1.0;
				continue;
			}

			double edgeWeight = 0.0;
			if(edgeAttributes.getType(edgeAttributeName) == edgeAttributes.TYPE_FLOATING)
				edgeWeight = edgeAttributes.getDoubleAttribute(id,edgeAttributeName).doubleValue();
			else if(edgeAttributes.getType(edgeAttributeName) == edgeAttributes.TYPE_INTEGER)
				edgeWeight = edgeAttributes.getIntegerAttribute(id,edgeAttributeName).doubleValue();
			else {
				// System.out.println("Attribute "+edgeAttributeName+" is not a number");
				continue;
			}

			minAttribute = Math.min(minAttribute, edgeWeight);
			maxAttribute = Math.max(maxAttribute, edgeWeight);
			edgeWeights[edgeIndex] = edgeWeight;
		}

		// We now have two lists, one with the edges, one with the weights, now massage the edgeWeights data as requested
		// Note that we need to go through this again to handle some of the edge cases
		List<Integer> edgeCase = new ArrayList();
		for(int edgeIndex = 0; edgeIndex < edges.size(); edgeIndex++) {
			double edgeWeight = edgeWeights[edgeIndex];
			if (edgeWeight == Double.MIN_VALUE) continue;

			edgeWeight = converter.convert(edgeWeight, minAttribute, maxAttribute);
			
			if (edgeWeight == Double.MIN_VALUE)
					edgeCase.add(edgeIndex);

			edgeWeights[edgeIndex] = edgeWeight;
			if (edgeWeight != Double.MIN_VALUE) {
				minWeight = Math.min(minWeight, edgeWeight);
				maxWeight = Math.max(maxWeight, edgeWeight);
			}
		}

		// OK, now we have our two arrays with the exception of the edge cases -- we can fix those, now
		for (Integer index: edgeCase) {
			edgeWeights[index] = maxWeight+maxWeight/10.0;
		}
	}

	public double[] getEdgeValues() {
		return edgeWeights;
	}

	public double getEdgeValueFromMatrix(int row, int column) {
		if (matrix == null)
			getDistanceMatrix();

		return matrix.get(row, column);
	}

	public double scaleValue(double value) {
			if (distanceValues) {
				if (value != 0.0)
					value = 1/value;
				else {
					value = Double.MAX_VALUE;
				}
			}

			if (logValues) {
				if (minAttribute < 0.0) 
					value += Math.abs(minAttribute);

				if(value != 0.0 && value != Double.MAX_VALUE)
					value = -Math.log10(value);
				else
					value = 500; // Assume 1e-500 as a reasonble upper bound
			}
			return value;
	}

	public boolean hasDistanceValues() { return this.distanceValues; }
	public boolean hasLogValues() { return this.logValues; }

	public double getNormalizedValue(double value) {
		double span = maxWeight - minWeight;
		return ((value-minWeight)/span);
	}

	public void normalizeMatrix(double factor) {
		if (matrix == null)
			getDistanceMatrix();

		matrix.forEachNonZero(new Normalize(minWeight, maxWeight, factor));
	}

	public Map<Integer, List<CyNode>> findConnectedComponents() {
		if (matrix == null)
			getDistanceMatrix();

		Map<Integer, List<CyNode>> cmap = new HashMap<Integer, List<CyNode>>();
		matrix.forEachNonZero(new FindComponents(cmap));
		return cmap;
	}

	public DoubleMatrix2D getDistanceMatrix(Double edgeCutOff, boolean undirectedEdges) {
		setEdgeCutOff(edgeCutOff);
		setUndirectedEdges(undirectedEdges);
		matrix = null;
		return getDistanceMatrix();
	}

	// TODO: relax symmetrical constraint?
	public DoubleMatrix2D getDistanceMatrix() {
		if (matrix != null)
			return matrix;

		matrix = DoubleFactory2D.sparse.make(nodes.size(),nodes.size());
		int sourceIndex;
		int targetIndex;

		for(int edgeIndex = 0; edgeIndex < edges.size(); edgeIndex++) {
			CyEdge edge = edges.get(edgeIndex);

			// Is this weight above the cutoff?
			if (edgeWeights[edgeIndex] < edgeCutOff) {
				// System.out.println("Skipping edge "+edge.getIdentifier()+" weight "+edgeWeights[edgeIndex]+" < "+edgeCutOff);
				continue; // Nope, don't add it
			}

			/*Add edge to matrix*/
			sourceIndex = nodes.indexOf(edge.getSource());
			targetIndex = nodes.indexOf(edge.getTarget());

			matrix.set(targetIndex,sourceIndex,edgeWeights[edgeIndex]);
			if(unDirectedEdges)
				matrix.set(sourceIndex,targetIndex,edgeWeights[edgeIndex]);
		}

		return matrix;
	}

	public double getMaxAttribute() {return maxAttribute;}
	public double getMaxWeight() {return maxWeight;}
	public double getMinAttribute() {return minAttribute;}
	public double getMinWeight() {return minWeight;}
	public List<CyNode> getNodes() {return nodes;}
	public List<CyEdge> getEdges() {return edges;}

	public void setEdgeCutOff(Double edgeCutOff) { 
		matrix = null;
		this.edgeCutOff = edgeCutOff.doubleValue(); 
	}

	public void	setUndirectedEdges(boolean undirectedEdges) { 
		matrix = null;
		this.unDirectedEdges = undirectedEdges; 
	}

	/**
   * This method handles the loop adjustment, if desired by the user.
   * The basic approach is to go through the diagonal and set the value of the
   * diagonal to the maximum value of the column.  In the von Dongen code, this
   * is handled in separate steps (zero the diagonal, (maybe) preinflate, set diagonal
   * to max).
   *
   * @param matrix the (sparse) data matrix we're going to adjust
   */
	public void adjustLoops()
	{
		if (matrix == null)
			getDistanceMatrix();

		double [] max = new double[matrix.columns()];
		// Calculate the max value for each column
		matrix.forEachNonZero(new MatrixFindMax(max));

		// Set it in the diagonal
		for (int col = 0; col < matrix.columns(); col++) {
			if (max[col] != 0.0)
				matrix.set(col,col,max[col]);
			else
				matrix.set(col,col,1.0);
		}
		
	}

	/**
	 * Debugging routine to print out information about a matrix
	 *
	 * @param matrix the matrix we're going to print out information about
	 */
	public void printMatrixInfo(CyLogger logger, DoubleMatrix2D m) {
		logger.debug("Matrix("+m.rows()+", "+m.columns()+")");
		if (m instanceof SparseDoubleMatrix2D)
			logger.debug(" matrix is sparse");
		else
			logger.debug(" matrix is dense");
		logger.debug(" cardinality is "+m.cardinality());
	}

	/**
	 * Debugging routine to print out information about a matrix
	 *
	 * @param matrix the matrix we're going to print out information about
	 */
	public void printMatrix(CyLogger logger, DoubleMatrix2D m) {
		String s = "";
		for (int row = 0; row < m.rows(); row++) {
			s += nodes.get(row).getIdentifier()+":\t";
			for (int col = 0; col < m.columns(); col++) {
				s += ""+m.get(row,col)+"\t";
			}
			logger.debug(s);
		}
	}

	/**
 	 * MatrixFindMax simply records the maximum value in a column
 	 */
	private class MatrixFindMax implements IntIntDoubleFunction {
		double [] colMax;

		public MatrixFindMax(double[] colMax) {
			this.colMax = colMax;
		}

		public double apply(int row, int column, double value) {
			if (value > colMax[column])
				colMax[column] = value;
			return value;
		}
	}

	/**
 	 * Normalize normalizes a cell in the matrix
 	 */
	private class Normalize implements IntIntDoubleFunction {
		private double maxValue;
		private double minValue;
		private double span;
		private double factor;

		public Normalize(double minValue, double maxValue, double factor) {
			this.maxValue = maxValue;
			this.minValue = minValue;
			this.factor = factor;
			span = maxValue - minValue;
		}

		public double apply(int row, int column, double value) {
			return ((value-minWeight)/span)*factor;
		}
	}

	/**
 	 * Find the connected components in a matrix
 	 */
  private class FindComponents implements IntIntDoubleFunction {
    Map<CyNode,Integer> nodeToCluster;
    Map<Integer, List<CyNode>> clusterMap;
    int clusterNumber = 0;

    public FindComponents(Map<Integer, List<CyNode>> cMap) {
      clusterMap = cMap;
      nodeToCluster = new HashMap<CyNode, Integer>();
    }

    public double apply(int row, int column, double value) {
			// For the purposes of determining connected components, we can
			// safely ignore self-edges
			if (row == column) 
				return value;
      CyNode node1 = nodes.get(row);
      CyNode node2 = nodes.get(column);
      if (nodeToCluster.containsKey(node1)) {
        if(!nodeToCluster.containsKey(node2)) {
          addNodeToCluster(nodeToCluster.get(node1), node2);
        } else {
          combineClusters(nodeToCluster.get(node1), nodeToCluster.get(node2));
				}
      } else {
        if (nodeToCluster.containsKey(node2)) {
          addNodeToCluster(nodeToCluster.get(node2), node1);
        } else {
          createCluster(node1, node2);
				}
      }
      return value;
    }

    private void addNodeToCluster(Integer cluster, CyNode node) {
			// System.out.println("Adding "+node+" to cluster "+cluster);
      List<CyNode> nodeList = clusterMap.get(cluster);
      nodeList.add(node);
      nodeToCluster.put(node, cluster);
    }

    private void createCluster(CyNode node1, CyNode node2) {
			// System.out.println("Creating cluster "+clusterNumber+" with "+node1+" and "+node2);
      List<CyNode> nodeList = new ArrayList<CyNode>();
      clusterMap.put(clusterNumber, nodeList);
      addNodeToCluster(clusterNumber, node1);
      addNodeToCluster(clusterNumber, node2);
      clusterNumber++;
    }

    private void combineClusters(Integer cluster1, Integer cluster2) {
      if (cluster1.intValue() == cluster2.intValue())
          return;
			// System.out.println("Combining cluster "+cluster1+" and "+cluster2);
      List<CyNode> list1 = clusterMap.get(cluster1);
      List<CyNode> list2 = clusterMap.get(cluster2);
      clusterMap.remove(cluster2);
      for (CyNode node: list2) {
        nodeToCluster.put(node, cluster1);
      }
      list1.addAll(list2);
    }
  }

}
