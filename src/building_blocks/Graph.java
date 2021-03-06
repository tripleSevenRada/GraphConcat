package building_blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import core.App;
import entity.NodeEntity;
import entity.VisitedStatus;
import lib_duke.ImageResource;
import lib_duke.LineMaker;
import lib_duke.Pixel;
import utils.geospatial.Bearing;
import utils.geospatial.Haversine;

// graph format
// https://www.dropbox.com/s/r4ixnibea713d9p/2018-01-24%2008.45.44.jpg?dl=0

public class Graph {
	private Map<NodeEntity, NodeEntity> retrievableDataSet = new HashMap<NodeEntity, NodeEntity>();
	private int rawSize;
	private int edgeSizeNoMerge;
	private int edgeSizeAfterMerge;
	private int mergedSize;
	private int edgeSizeAfterPrune;
	private int edgeSizeAfterCut;
	private int sizeProblem, containsProblem;
	private int weightUpdated;
	private int prunedOut;
	private int cutOut;
	private ImageResource visual;
	private LineMaker line = new LineMaker(visual);
	private App app;
	private List<NodeEntity> playLeft = new ArrayList<NodeEntity>();
	private List<NodeEntity> playRight = new ArrayList<NodeEntity>();
	private List<NodeEntity> matchFound = new ArrayList<NodeEntity>();
	private static final int PRUNE_THRESHOLD = 200;
	private static final double CONSIDER_ALLIGNED_NODES_CUT_UP_TO_DIST = 180.0;//metres
	private static final double DIFF_ANGLE_BEARING_TO_NODES_CUT = 20.0;//degrees

	public Graph(App app) {
		this.app = app;
		if (App.DEVELOPMENT) {
			visual = new ImageResource(App.PIC_WIDTH_MAX_INDEX + 1, App.PIC_HEIGHT_MAX_INDEX + 1);
		}
	}

	/*
	 * 
	 */
	public void buildIn(Tile tile) {

		NodeEntity nodeEntityLeft;
		short weightLeft;
		short weightRight;

		for (NodeEntity nodeEntityRight : tile.getData()) {
			rawSize++;
			edgeSizeNoMerge += nodeEntityRight.getAdjacents().size();

			if (!retrievableDataSet.containsKey(nodeEntityRight)) {
				// DOES NOT CONTAIN
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

	/*
	 * 
	 */
	public void rebuildDataSet() {
		Set<NodeEntity> newAdj;
		NodeEntity definitelyFromLeft;
		int nullFromLeft = 0;
		int compareSetsFalse = 0;
		for (NodeEntity current : retrievableDataSet.keySet()) {
			newAdj = new HashSet<NodeEntity>();
			for (NodeEntity currentAdj : current.getAdjacents()) {
				definitelyFromLeft = retrievableDataSet.get(currentAdj);
				if (definitelyFromLeft == null) {
					nullFromLeft++;
				} else
					newAdj.add(definitelyFromLeft);
			}
			if (compareSets(newAdj, current.getAdjacents()) == false) {
				compareSetsFalse++;
			}
			current.setAdjacents(newAdj);
		}
		
		System.out.println("\n\n=========================================================================");
		System.out.println("REBUILD DATASET: From left came Null in adjacents matching: " + nullFromLeft);
		System.out.println("REBUILD DATASET: Compare sets false: " + compareSetsFalse);
		System.out.println("=========================================================================\n");

		mergedSize = this.getDatasetSize();
	}

	/**
	 * if you see me, I need to see you too
	 * https://www.dropbox.com/s/mqijzl4vwzg0zjj/2018-01-11%2010.15.46.jpg?dl=0
	 * 
	 */
	public int fixMutualVisibility() {
		Set<NodeEntity> leftAdj;
		Set<NodeEntity> rightAdj;
		int refSelf = 0;

		int newEdges = 0;
		for (NodeEntity left : retrievableDataSet.keySet()) {
			leftAdj = left.getAdjacents();
			if (leftAdj.contains(left)) {
				leftAdj.remove(left);
				refSelf++;
			}
			for (NodeEntity right : leftAdj) {
				rightAdj = right.getAdjacents();
				if (!rightAdj.contains(left)) {
					right.addToAdj(left);
					newEdges++;
				}
			}
		}
		System.out.println("FIX MUTUAL VISIBILITY: Reference to itself: " + refSelf);
		return newEdges;
	}
	
	/*
	 * remove nodes that survived prune and are part of net structure
	 */
	public void removeNodeEntity(NodeEntity ne){
		assert(retrievableDataSet.containsKey(ne));
		for(NodeEntity adjNe : ne.getAdjacents()) {
			assert(adjNe.getAdjacents().contains(ne));
			//assertion may be switched off
			if(adjNe.getAdjacents().contains(ne)) {
				adjNe.getAdjacents().remove(ne);
			}
		}
		retrievableDataSet.remove(ne);
	}

	/**
	 *
	 */
	public void prune() {

		System.out.println("\n\nPRUNE STARTS " + System.currentTimeMillis());
		
		Set<NodeEntity> tempSet = null;
		Set<NodeEntity> toRemove = new HashSet<NodeEntity>();
		Queue<NodeEntity> bfsQueue = null;
		Set<NodeEntity> adj = null;
		int pruned = 0;
		int survived = 0;
		// iter
		for (NodeEntity node : retrievableDataSet.keySet()) {
			if (node.visitedStatus == VisitedStatus.SURVIVED || node.visitedStatus == VisitedStatus.PRUNED) {
				continue;
			} else {
				tempSet = new HashSet<NodeEntity>();
				bfsQueue = new LinkedList<NodeEntity>();
				// run BFS from node into tempSet
				bfsQueue.add(node);
				while (bfsQueue.isEmpty() == false) {
					NodeEntity curr = bfsQueue.remove();
					if (tempSet.contains(curr) == false) {
						tempSet.add(curr);
					}
					adj = curr.getAdjacents();
					for (NodeEntity adjNode : adj) {
						if (tempSet.contains(adjNode) == false) {
							bfsQueue.add(adjNode);
							tempSet.add(adjNode);
						}
					}
				}
			}

			// if tempSet.size < THRESHOLD mark all tempSet as pruned
			// else mark all tempSet as survived
			if (tempSet.size() < PRUNE_THRESHOLD) {
				markBunchOfNodesWithStatus(tempSet, VisitedStatus.PRUNED);
				pruned += tempSet.size();
				for (NodeEntity r : tempSet){
					assert (toRemove.contains(r) == false);
					toRemove.add(r);
				}
			} else {
				markBunchOfNodesWithStatus(tempSet, VisitedStatus.SURVIVED);
				survived += tempSet.size();
			}
		} // for
		
		assert(toRemove.size() == pruned);
		for(NodeEntity r : toRemove){
			retrievableDataSet.remove(r);
		}
		System.out.println("PRUNE FINISHES " + System.currentTimeMillis());
		System.out.println("PRUNE results: survived: " + survived + " pruned: " + pruned);
		this.prunedOut = pruned;
	}
	
	List <NodeEntity> starts = new LinkedList<NodeEntity>();
	List <NodeEntity> middles = new LinkedList<NodeEntity>();
	List <NodeEntity> ends = new LinkedList<NodeEntity>();
	
	//https://www.dropbox.com/s/le91saby0n0d7oj/2018-02-10%2016.42.56.jpg?dl=0
	public void cutUnnecesarryAlignedNodes() {
	
		assert(starts.size() == 0);
		assert(middles.size() == 0);
		assert(ends.size() == 0);
		
		int cutThisRound = 0;
		
		for (NodeEntity ne : retrievableDataSet.keySet()) {
			if(
					ne.getAdjacents().size() == 2 &&
					ne.isAvailableForCutConsideration() &&
					allAdjacentsAreAvailableForCutConsideration(ne)
															){
				NodeEntity[] adj = ne.getAdjacents().toArray(new NodeEntity[2]);
				NodeEntity start = adj[0];
				NodeEntity middle = ne;
				NodeEntity end = adj[1];
				double distStMid = Haversine.haversineInM(start.getLat(), start.getLon(),
						middle.getLat(), middle.getLon());
				double distMidEnd = Haversine.haversineInM(middle.getLat(), middle.getLon(),
						end.getLat(), end.getLon());
				if((distStMid + distMidEnd) < CONSIDER_ALLIGNED_NODES_CUT_UP_TO_DIST) {
					double bearingMidSt = Bearing.getBearing(middle.getLat(), middle.getLon(),
							start.getLat(), start.getLon());
					double bearingMidEnd = Bearing.getBearing(middle.getLat(), middle.getLon(),
							end.getLat(), end.getLon());
					double pointer = bearingMidSt;
					double pointerOponnent = (pointer + 180d) % 360d;
					double marginLow = pointerOponnent - (DIFF_ANGLE_BEARING_TO_NODES_CUT / 2.0);
					double marginUp = pointerOponnent + (DIFF_ANGLE_BEARING_TO_NODES_CUT / 2.0);
					//piecut
					double marginLowClipped = Bearing.clipDegrees(marginLow);
					double marginUpClipped = Bearing.clipDegrees(marginUp);
					if(
							(bearingMidEnd > marginLowClipped && bearingMidEnd < marginUpClipped) ||
							
							pointerOpponentBelongsToPiecutIncludingNorth(
									pointerOponnent,
									marginLowClipped,
									marginUpClipped)
							
							) {
						cutOut ++;
						cutThisRound ++;
						starts.add(start);
						middles.add(middle);
						ends.add(end);
						start.setAvailableForCutConsideration(false);
						middle.setAvailableForCutConsideration(false);
						end.setAvailableForCutConsideration(false);
					}
				}
			}
		}
		System.out.println("CUT PROCEDURE: this round cut: " + cutThisRound);
		assert(starts.size() == middles.size() && middles.size() == ends.size());
		for(int i = 0; i < starts.size(); i ++) {
			performCut(starts.get(i), middles.get(i), ends.get(i));
		}
	}
	
	private boolean allAdjacentsAreAvailableForCutConsideration(NodeEntity ne) {
		for(NodeEntity adj : ne.getAdjacents()) {
			if(adj.isAvailableForCutConsideration() == false) return false;
		}
		return true;
	}
	
	//TODO?
	//this could be omitted if we give up clipping
	//and allow angles greater than 360 degrees
	//https://www.dropbox.com/s/flt67kez10r9xxx/2018-02-12%2018.58.47.jpg?dl=0
	private boolean pointerOpponentBelongsToPiecutIncludingNorth(
			
			double pointerOpponent,
			double marginLowClipped,
			double marginUpClipped) {
		
		//do my pie includes north?
		if(marginUpClipped < marginLowClipped) {
			double marginUpUnclipped = 360.0 + marginUpClipped;
			double pointerOpponentUnclipped;
			if(pointerOpponent < DIFF_ANGLE_BEARING_TO_NODES_CUT) {
				pointerOpponentUnclipped  = 360.0 + pointerOpponent;
			} else {
				pointerOpponentUnclipped = pointerOpponent;
			}
			if((pointerOpponentUnclipped > marginLowClipped) &&
					(pointerOpponentUnclipped <	marginUpUnclipped)) {
				return true;
			}
		}
		return false;
	}
	
	private void performCut(NodeEntity start, NodeEntity middle, NodeEntity end) {
		assert(middle.getAdjacents().size() == 2);
		assert(start.getAdjacents().contains(middle));
		assert(end.getAdjacents().contains(middle));
		
		if(!start.getAdjacents().contains(end))start.getAdjacents().add(end);
		if(!end.getAdjacents().contains(start))end.getAdjacents().add(start);
		
		removeNodeEntity(middle);
	}
	
	public void resetCutAvailability() {
		for (NodeEntity ne : retrievableDataSet.keySet()) {
			ne.setAvailableForCutConsideration(true);
		}
		starts.clear();
		middles.clear();
		ends.clear();
	}

	private void markBunchOfNodesWithStatus(Iterable<NodeEntity> iterable, VisitedStatus status) {
		Iterator<NodeEntity> i = iterable.iterator();
		while (i.hasNext()) {
			NodeEntity n = i.next();
			n.visitedStatus = status;
		}
	}

	public void computeEdgeSizeAfterMerge() {
		this.edgeSizeAfterMerge = countAdjacents();
	}

	public void computeEdgeSizeAfterPrune() {
		this.edgeSizeAfterPrune = countAdjacents();
	}
	
	public void computeEdgeSizeAfterCut() {
		this.edgeSizeAfterCut = countAdjacents();
	}

	private int countAdjacents() {
		int count = 0;
		for (NodeEntity current : retrievableDataSet.keySet()) {
			count += current.getAdjacents().size();
		}
		return count;
	}

	private boolean compareSets(Set<NodeEntity> old, Set<NodeEntity> young) {
		return old.containsAll(young) && young.containsAll(old);
	}

	public Map<NodeEntity, NodeEntity> getRetrievableDataSet() {
		return retrievableDataSet;
	}

	public int getRawSize() {
		return rawSize;
	}

	public int getDatasetSize() {
		return retrievableDataSet.size();
	}

	public int getEdgeSizeNoMerge() {
		return edgeSizeNoMerge;
	}

	public int getEdgeSizeAfterMerge() {
		return edgeSizeAfterMerge;
	}

	public int getEdgeSizeAfterPrune() {
		return edgeSizeAfterPrune;
	}
	
	public int getEdgeSizeAfterCut() {
		return edgeSizeAfterCut;
	}

	public int getSizeProblem() {
		return sizeProblem;
	}

	public int getContainsProblem() {
		return containsProblem;
	}

	@SuppressWarnings("unused")
	private void printCompare(NodeEntity left, NodeEntity right) {
		System.out.println("\n\n=========================================================================");
		System.out.println("FROM my DATABASE has just arrived:");
		System.out.println(right);
		System.out.println("WHAT I ALREADY HAVE in my DATASET:");
		System.out.println(left);
		System.out.println("=========================================================================\n");
	}

	public void printStats() {
		System.out.println("\n\n=========================================================================");
		System.out.println("Raw number of nodes: " + this.getRawSize());
		System.out.println("Merged number of nodes: " + this.mergedSize);
		System.out.println("EdgeSize before merge: " + this.getEdgeSizeNoMerge());
		System.out.println("EdgeSize after merge: " + this.getEdgeSizeAfterMerge());
		System.out.println("--");
		System.out.println("Nodes removed by prune procedure: " + this.prunedOut);
		System.out.println("EdgeSize after prune: " + this.getEdgeSizeAfterPrune());
		System.out.println("--");
		System.out.println("Nodes removed by cut procedure: " + this.cutOut);
		System.out.println("EdgeSize after cut: " + this.getEdgeSizeAfterCut());
		System.out.println("--");
		System.out.println("Weights updated: " + this.weightUpdated);
		System.out.println("Final number of nodes: " + this.getDatasetSize());
		System.out.println("=========================================================================\n");
	}

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
