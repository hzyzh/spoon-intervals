import java.util.*;

import fr.inria.controlflow.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;

import org.json.simple.JSONObject;


public class IIntervalNode {
    HashSet<IControlFlowNode> CFGNodes = new HashSet<IControlFlowNode>();
    int nodeID;
    IControlFlowNode buggyCFGNode;
    HashMap<IControlFlowNode, Integer> nodeIds;
    static private int maxLength = 0;

    public IIntervalNode(IControlFlowNode node) {
        nodeID = node.getId();
        CFGNodes.add(node);
    }
    public void addNode(IControlFlowNode node) {
        CFGNodes.add(node);
    }
    public void addNodes(HashSet<IControlFlowNode> nodes) {
        CFGNodes.addAll(nodes);
    }
    public HashSet<IControlFlowNode> getNode() {
        return CFGNodes;
    }
    public IControlFlowNode findhNode() {
        for (IControlFlowNode n : CFGNodes) {
            if (n.getId() == nodeID) {
                return n;
            }
        }
        return null;
    }

    public void updateBuggyCFGNode(IControlFlowNode n) {
        buggyCFGNode = n;
    }

    public IControlFlowNode findNodeByID(int id) {
        for (IControlFlowNode n : CFGNodes) {
            if (n.getId() == id) {
                return n;
            }
        }
        return null;
    }

    private List<IControlFlowNode> next(IControlFlowNode node) {
        ArrayList<IControlFlowNode> result = new ArrayList<IControlFlowNode>();
        for (IControlFlowNode n : node.next()) {
            if (findNodeByID(n.getId()) != null)
                result.add(n);
        }
        return result;
    }

    private List<IControlFlowNode> prev(IControlFlowNode node) {
        ArrayList<IControlFlowNode> result = new ArrayList<IControlFlowNode>();
        for (IControlFlowNode n : node.prev()) {
            if (findNodeByID(n.getId()) != null)
                result.add(n);
        }
        return result;
    }

    private List<IControlFlowEdge> edges() {
        return null;
    }

    public void printNode(int ind) {
        if (ind == 0) {
            ind = nodeID;
        }
        IControlFlowNode n = findNodeByID(ind);
        if (n.getStatement() != null)
            System.out.print(n.getStatement().toString());
        else
            System.out.print("empty statement\n");
    }

    public int getNodeID() {
        return nodeID;
    }

    public void constructIntervalCFG(){
        //returnJsonData(0, null);
        for (IControlFlowNode n : CFGNodes) {
            returnToken(n);
        }
    }

    private void initNodeMap() {
        int i = 0;
        if (nodeIds == null) {
            nodeIds = new HashMap<IControlFlowNode, Integer>();
            for (IControlFlowNode n : CFGNodes) {
                nodeIds.put(n, i);
                ++i;
            }
        }
    }

    private List<int[]> returnGraphRep() {
        // [[0, 1, 1], [1, 1, 2], ...]
        // 0->1 whose edge type is 1, 1->2 whose edge tpye is 2.
        List<int[]> graph = new ArrayList<int[]>();
        initNodeMap();

        for (IControlFlowNode n : CFGNodes) {
            for (IControlFlowNode nn : next(n)) {
                // the edge type should start at 1.
                int[] data = {nodeIds.get(n), 1, nodeIds.get(nn)};
                graph.add(data);
            }
        }
        if (graph.isEmpty()) {
            int[] data = {0, 1, 0};
            graph.add(data);
        }
        return graph;
    }

    private int[][] returnAdjMatrix() {
        int matN = CFGNodes.size();
        int [][] adjMatrix = new int[matN][matN];
        initNodeMap();

        for (IControlFlowNode n : CFGNodes) {
            for (IControlFlowNode nn : next(n)) {
                adjMatrix[nodeIds.get(n)][nodeIds.get(nn)] = 1;
            }
        }
        return adjMatrix;
    }

    public static tokenVisitor returnToken(IControlFlowNode n) {
        CtElement ctele = n.getStatement();
        if (ctele == null) {
            tokenVisitor visitor = new tokenVisitor();
            if (n.getKind() == BranchKind.BEGIN)
                visitor.updateVector(tokenIndex.CFGBegin, ctele);
            else if (n.getKind() == BranchKind.EXIT)
                visitor.updateVector(tokenIndex.CFGEnd, ctele);
            else
                visitor.updateVector(tokenIndex.Other, ctele);
            return visitor;
        }
        tokenVisitor visitor = new tokenVisitor();
        visitor.scan(ctele);

        /*

        int [] tokenSeq = visitor.getTokenSeq();
        int i = tokenSeq.length;
        if (i > maxLength)
        {
            System.out.println(i);
            System.out.println(ctele.toString());
        }
        if (i > 40) {
            tokenVisitor visitor2 = new tokenVisitor();
            visitor2.scan(ctele);
            for ( CtElement cte : ctele.asIterable()) {
                Class c = cte.getClass();
                System.out.println(cte.toString());
            }

        }
        */
        return visitor;

    }

    static int count;

    public JSONObject returnCallerJsonData(int intervalID, HashSet<IControlFlowNode> CFGNodesWithoutCallee, boolean [] intervalNodeMask, CtMethod target) {
        JSONObject graphJson = new JSONObject();
        List<int[]> graph = returnGraphRep();
        int[][] node_features = new int[nodeIds.size()][tokenIndex.Size];
        List<int[]> convRep = new ArrayList<>();
        int[] numOfFeatures = new int[nodeIds.entrySet().size()];
        int i = 0;
        boolean[] node_mask = new boolean[nodeIds.size()];
        boolean[] calling_mask = new boolean[nodeIds.size()];
        boolean intervalMask = false;

        for (Map.Entry<IControlFlowNode, Integer> item : nodeIds.entrySet()) {
            IControlFlowNode node = item.getKey();
            Integer id = item.getValue();
            tokenVisitor vis = returnToken(node);
            node_features[id] = vis.getVector();
            int [] tokenSeq = vis.getTokenSeq();
            numOfFeatures[i] = tokenSeq.length;
            convRep.add(tokenSeq);

            List<CtMethod> list = node.getCallees();
            if(list != null && list.contains(target)) 
                calling_mask[i] = true;
            else
                calling_mask[i] = false;
            i++;

            if (CFGNodesWithoutCallee == null || CFGNodesWithoutCallee.contains(node)) {
                node_mask[id] = true;
                intervalMask = true;
                count++;
            }
            else {
                node_mask[id] = false;
            }
        }
        intervalNodeMask[intervalID] = intervalMask;

        graphJson.put("insideinterval", 1);
        graphJson.put("intervalID", intervalID);
        graphJson.put("graph", common.ArrayToList(graph));
        graphJson.put("node_features", common.ArrayToList(node_features));
        graphJson.put("convRep", common.ArrayToList(convRep));
        graphJson.put("numOfFeatures", common.ArrayToList(numOfFeatures));
        graphJson.put("bugPos", common.returnBuggyNode(nodeIds, buggyCFGNode, node_mask));
        // node_mask is used to mask node not in the top-level function (i.e., ignore callees)
        graphJson.put("node_mask", common.ArrayToList(node_mask) );
        graphJson.put("calling_mask", common.ArrayToList(calling_mask) );

        return graphJson;
    }

    public JSONObject returnTargetJsonData(int intervalID, HashSet<IControlFlowNode> CFGNodesWithoutCallee, boolean [] intervalNodeMask, Map<CtMethod, Integer> calleeIdMap) {
        JSONObject graphJson = new JSONObject();
        List<int[]> graph = returnGraphRep();
        int[][] node_features = new int[nodeIds.size()][tokenIndex.Size];
        List<int[]> convRep = new ArrayList<>();
        int[] numOfFeatures = new int[nodeIds.entrySet().size()];
        int i = 0;
        boolean[] node_mask = new boolean[nodeIds.size()];
        boolean[][] calling_mask = new boolean[calleeIdMap.size()][nodeIds.size()];
        boolean intervalMask = false;

        for (Map.Entry<IControlFlowNode, Integer> item : nodeIds.entrySet()) {
            IControlFlowNode node = item.getKey();
            Integer id = item.getValue();
            tokenVisitor vis = returnToken(node);
            node_features[id] = vis.getVector();
            int [] tokenSeq = vis.getTokenSeq();
            numOfFeatures[i] = tokenSeq.length;
            convRep.add(tokenSeq);

            int j = 0;
            while(j < calleeIdMap.size()) {
                calling_mask[j++][i] = false;
            }
            List<CtMethod> list = node.getCallees();
            if(list != null)
                for(CtMethod callee : list)
                    calling_mask[calleeIdMap.get(callee)][i] = true;
            i++;

            if (CFGNodesWithoutCallee == null || CFGNodesWithoutCallee.contains(node)) {
                node_mask[id] = true;
                intervalMask = true;
                count++;
            }
            else {
                node_mask[id] = false;
            }
        }
        intervalNodeMask[intervalID] = intervalMask;

        graphJson.put("insideinterval", 1);
        graphJson.put("intervalID", intervalID);
        graphJson.put("graph", common.ArrayToList(graph));
        graphJson.put("node_features", common.ArrayToList(node_features));
        graphJson.put("convRep", common.ArrayToList(convRep));
        graphJson.put("numOfFeatures", common.ArrayToList(numOfFeatures));
        graphJson.put("bugPos", common.returnBuggyNode(nodeIds, buggyCFGNode, node_mask));
        // node_mask is used to mask node not in the top-level function (i.e., ignore callees)
        graphJson.put("node_mask", common.ArrayToList(node_mask) );
        graphJson.put("calling_mask", common.ArrayToList(calling_mask) );

        return graphJson;
    }

    public JSONObject returnCalleeJsonData(int intervalID, HashSet<IControlFlowNode> CFGNodesWithoutCallee, boolean [] intervalNodeMask) {
        JSONObject graphJson = new JSONObject();
        List<int[]> graph = returnGraphRep();
        int[][] node_features = new int[nodeIds.size()][tokenIndex.Size];
        List<int[]> convRep = new ArrayList<>();
        int[] numOfFeatures = new int[nodeIds.entrySet().size()];
        int i = 0;
        boolean[] node_mask = new boolean[nodeIds.size()];
        boolean intervalMask = false;

        for (Map.Entry<IControlFlowNode, Integer> item : nodeIds.entrySet()) {
            IControlFlowNode node = item.getKey();
            Integer id = item.getValue();
            tokenVisitor vis = returnToken(node);
            node_features[id] = vis.getVector();
            int [] tokenSeq = vis.getTokenSeq();
            numOfFeatures[i++] = tokenSeq.length;
            convRep.add(tokenSeq);

            if (CFGNodesWithoutCallee == null || CFGNodesWithoutCallee.contains(node)) {
                node_mask[id] = true;
                intervalMask = true;
                count++;
            }
            else {
                node_mask[id] = false;
            }
        }
        intervalNodeMask[intervalID] = intervalMask;

        graphJson.put("insideinterval", 1);
        graphJson.put("intervalID", intervalID);
        graphJson.put("graph", common.ArrayToList(graph));
        graphJson.put("node_features", common.ArrayToList(node_features));
        graphJson.put("convRep", common.ArrayToList(convRep));
        graphJson.put("numOfFeatures", common.ArrayToList(numOfFeatures));
        graphJson.put("bugPos", common.returnBuggyNode(nodeIds, buggyCFGNode, node_mask));
        // node_mask is used to mask node not in the top-level function (i.e., ignore callees)
        graphJson.put("node_mask", common.ArrayToList(node_mask) );

        return graphJson;
    }
}
