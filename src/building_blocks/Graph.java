package building_blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import core.App;
import entity.NodeEntity;
import lib_duke.ImageResource;
import lib_duke.LineMaker;
import lib_duke.Pixel;

// graph format
// https://www.dropbox.com/s/cpaidvxzisyic4d/2017-12-30%2021.54.47.jpg?dl=0

public class Graph {
	private Map<NodeEntity, NodeEntity> retrievableDataSet = new HashMap<NodeEntity, NodeEntity>();
	private int rawSize;
	private int edgeSizeNoMerge;
	private int edgeSizeAfterMerge;
	private int sizeProblem, containsProblem;
	private int weightUpdated;
	private ImageResource visual;
	private LineMaker line = new LineMaker(visual);
	private App app;
	private List<NodeEntity> playLeft = new ArrayList<NodeEntity>();
	private List<NodeEntity> playRight = new ArrayList<NodeEntity>();
	private List<NodeEntity> matchFound = new ArrayList<NodeEntity>();

	public Graph(App app) {
		this.app = app;
		if (App.DEVELOPMENT) {
			visual = new ImageResource(App.PIC_WIDTH_MAX_INDEX + 1, App.PIC_HEIGHT_MAX_INDEX + 1);
		}
	}

	/**
	 * 
	 * @param tile
	 */
	public void buildIn(Tile tile) {

		NodeEntity nodeEntityLeft;
		short weightLeft;
		short weightRight;

		for (NodeEntity nodeEntityRight : tile.getData()) {
			rawSize++;
			edgeSizeNoMerge += nodeEntityRight.getAdjacents().size();

			if (!retrievableDataSet.containsKey(nodeEntityRight)) {
				// NOT CONTAINS
				retrievableDataSet.put(nodeEntityRight, nodeEntityRight);
			} else {
				// DOES CONTAIN
				nodeEntityLeft = retrievableDataSet.get(nodeEntityRight);
				// check weight
				weightLeft = nodeEntityLeft.getWeight();
				weightRight = nodeEntityRight.getWeight();
				if (weightLeft != weightRight)
					weightUpdated++;
				nodeEntityLeft.setWeight((short) Math.max(weightLeft, weightRight));
			}
		} // for
	}

	/**
	 * 
	 */
	public void rebuildDataSet() {
		Set<NodeEntity> newAdj;
		NodeEntity definitelyFromLeft;
		for (NodeEntity current : retrievableDataSet.keySet()) {
			newAdj = new HashSet<NodeEntity>();
			for (NodeEntity currentAdj : current.getAdjacents()) {
				definitelyFromLeft = retrievableDataSet.get(currentAdj);
				if (definitelyFromLeft == null)
					throw new RuntimeException("null, inconsistency");
				newAdj.add(definitelyFromLeft);
			}
			current.setAdjacents(newAdj);
		}
	}

	public int countZeroAdjNodes() {
		int count = 0;
		for (NodeEntity node : retrievableDataSet.keySet()) {
			if (node.getAdjacents().size() == 0)
				count++;
		}
		return count;
	}

	public void computeEdgeSizeAfterMerge() {
		for (NodeEntity current : retrievableDataSet.keySet()) {
			edgeSizeAfterMerge += current.getAdjacents().size();
		}
	}

	/**
	 * @return the dataSet
	 */
	public Map<NodeEntity, NodeEntity> getRetrievableDataSet() {
		return retrievableDataSet;
	}

	/**
	 * @return original number of nodes read from DB if we call after all work
	 *         done.
	 */
	public int getRawSize() {
		return rawSize;
	}

	/**
	 * @return final number of nodes if we call after all work done.
	 */
	public int getMergedSize() {
		return retrievableDataSet.size();
	}

	/**
	 * @return if we call after all work done.
	 */
	public int getEdgeSizeNoMerge() {
		return edgeSizeNoMerge;
	}

	/**
	 * @return if we call after all work done.
	 */
	public int getEdgeSizeAfterMerge() {
		return edgeSizeAfterMerge;
	}

	/**
	 * @return the sizeProblem
	 */
	public int getSizeProblem() {
		return sizeProblem;
	}

	/**
	 * @return the containsProblem
	 */
	public int getContainsProblem() {
		return containsProblem;
	}

	/**
	 * 
	 * @param left
	 * @param right
	 */
	@SuppressWarnings("unused")
	private void printCompare(NodeEntity left, NodeEntity right) {
		System.out.println("=========================================================================");
		System.out.println("FROM my DATABASE has just arrived:");
		System.out.println(right);
		System.out.println("WHAT I ALREADY HAVE in my DATASET:");
		System.out.println(left);
		System.out.println("=========================================================================");
	}

	/**
	 * 
	 */
	public void printStats() {
		System.out.println("=========================================================================");
		System.out.println("RAW number of nodes: " + this.getRawSize());
		System.out.println("FINAL number of nodes: " + this.getMergedSize());
		System.out.println("EdgeSize before merge: " + this.getEdgeSizeNoMerge());
		System.out.println("EdgeSize after merge: " + this.getEdgeSizeAfterMerge());
		System.out.println("Weights updated: " + this.weightUpdated);
		System.out.println("=========================================================================");
	}

	/**
	 * 
	 * @param left
	 * @param right
	 */
	@SuppressWarnings("unused")
	private void testCompareLeftRightStore(NodeEntity left, NodeEntity right) {
		playLeft.add(left);
		playRight.add(right);
	}

	/**
	 * call when graph done
	 */
	public void testCompareLeftRightPlay() {
		app.computeBoundsOfExistingNodes(this);
		int size = playLeft.size();
		int[] reds = new int[] { 255, 0 };
		int[] blues = new int[] { 0, 255 };
		for (int i = 0; i < size; i++) {
			NodeEntity left = playLeft.get(i);
			NodeEntity right = playRight.get(i);
			NodeEntity[] entities = new NodeEntity[] { left, right };
			for (int j = 0; j < 2; j++) {
				int anchorX = app.convertLonToPixX(entities[j].getLon());
				int anchorY = app.convertLatToPixY(entities[j].getLat());
				for (NodeEntity adj : entities[j].getAdjacents()) {
					int adjX = app.convertLonToPixX(adj.getLon());
					int adjY = app.convertLatToPixY(adj.getLat());
					line.drawLine(anchorX, anchorY, adjX, adjY, reds[j], 0, blues[j]);
				}
				visual.draw();
				try {
					Thread.sleep(300);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * call when graph done
	 */
	public void testOverlap() {
		app.computeBoundsOfExistingNodes(this);
		for (NodeEntity entity : matchFound) {
			Pixel p = visual.getPixel(app.convertLonToPixX(entity.getLon()), app.convertLatToPixY(entity.getLat()));
			p.setRed(255);
			p.setBlue(255);
		}
		for (NodeEntity entity : retrievableDataSet.keySet()) {
			Pixel p = visual.getPixel(app.convertLonToPixX(entity.getLon()), app.convertLatToPixY(entity.getLat()));
			p.setGreen(200);
		}
		visual.draw();
	}
}
