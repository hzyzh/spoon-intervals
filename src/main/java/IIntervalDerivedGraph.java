import fr.inria.controlflow.*;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtElement;

import java.util.*;

public class IIntervalDerivedGraph extends DefaultDirectedGraph<IIntervalNode, IIntervalEdge>{

    HashMap<IIntervalNode, Integer> nodeIds;
    CtMethod corBuggyMethod = null;
    IIntervalNode buggyNode = null;
    CtMethod baseMethod = null;
    int buggyLine = 0;
    boolean useDFG = false;
    IControlFlowNode buggyCFGNode = null;
    IControlFlowGraph CFG = null;
    HashSet<IControlFlowNode> CFGNodesWithoutCallee = null;
    HashMap<IControlFlowNode, Integer> CFGNodeIds = null;
    List<CtMethod> callees = null;

    public IIntervalDerivedGraph(Class<? extends IIntervalEdge> edgeClass) {
        super(edgeClass);
    }

    public IIntervalDerivedGraph() {
        super(IIntervalEdge.class);
    }

    public IIntervalDerivedGraph(boolean useDFG) {
        super(IIntervalEdge.class);
        this.useDFG = useDFG;
    }

    public void addCFGNodesWithoutCallee(HashSet<IControlFlowNode> n) {
        CFGNodesWithoutCallee = n;
    }

    List<IControlFlowNode> ReturnEntryNodes(IControlFlowGraph graph) {
        CFG = graph;
        return graph.findNodesOfKind(BranchKind.BEGIN);
    }

    public void addEntryMethod(CtMethod method) {
        baseMethod = method;
    }

    public CtMethod getEntryMethod() {
        return baseMethod;
    }

    public void addCorBuggyMethod(CtMethod method) { corBuggyMethod = method;}

    public void addCallees(List<CtMethod> methods) { callees = methods;}

    @Override
    public IIntervalEdge addEdge(IIntervalNode source, IIntervalNode target) {
        if (!containsVertex(source)) {
            addVertex(source);
        }
        if (!containsVertex(target)) {
            addVertex(target);
        }
        return super.addEdge(source, target);
    }
    public IIntervalNode findNodeById(int id) {
        for (IIntervalNode n : vertexSet()) {
            if (n.findNodeByID(id) != null) {
                return n;
            }
        }
        return null;
    }
    public void linkEdges(){
        // link edges between IIntervalNodes according
        // to the control flow graph
        for (IIntervalNode n : vertexSet()) {
            for (IControlFlowNode h : n.getNode()) {
                for (IControlFlowNode e : h.next()) {
                    IIntervalNode next = findNodeById(e.getId());
                    //if (n == null || next == null) continue;
                    if (next.getNodeID() != n.getNodeID()) {
                        addEdge(n, next);
                    }
                }
            }
        }
        initNode();
    }
    public void print(){
        StringBuilder sb = new StringBuilder("digraph ").append("null").append(" { \n");
        //sb.append("exit [shape=doublecircle];\n");
        sb.append("node [fontsize = 8];\n");


        int i = 0;
        for (IIntervalNode n : vertexSet()) {
            sb.append(n.getNodeID() + " [label=" + n.getNodeID() + "];\n");
        }

        for (IIntervalEdge e : edgeSet()) {
            sb.append(e.getSourceNode().getNodeID() + " -> " +
                    e.getTargetNode().getNodeID() + " ;\n");
        }

        sb.append("}\n");
        System.out.print(sb.toString());
    }

    public void addBuggyLine(int buggyLine) {
        int closest=0;
        IControlFlowNode tmph = null;
        IIntervalNode tmpn = null;
        for (IIntervalNode n : vertexSet()) {
            for (IControlFlowNode h : n.getNode()) {
                CtElement stmt = h.getStatement();
                if (stmt == null) {
                    continue;
                }
                SourcePosition source = stmt.getPosition();
                if (source.isValidPosition()) {
                    int curLine = source.getLine();
                    if (curLine == buggyLine) {
                        buggyNode = n;
                        buggyCFGNode = h;
                        this.buggyLine = buggyLine;
                        break;
                    }
                    else if (curLine > closest && curLine < buggyLine) {
                        closest = curLine;
                        tmph = h;
                        tmpn = n;
                    }
                }
            }
        }

        assert buggyNode != null : "BuggyLine must be found!";
        if (buggyNode != null) {
            // for line prediction, we should avoid confuse lines.
            // therefore, we should filter instances whose bugpos is all 0.
            buggyNode.updateBuggyCFGNode(buggyCFGNode);
        }

        if (buggyNode == null){
            if (tmpn != null) {
                buggyNode = tmpn;
                buggyCFGNode = tmph;
                System.out.println("using tmp node.");
                buggyNode.updateBuggyCFGNode(buggyCFGNode);
            }
            else {
                //TODO: ohhhhh my goooooood
                System.out.println("Still not found.");
            }
        }

    }

    private void initNode() {
        if (nodeIds == null) {
            int i = 0;
            nodeIds = new HashMap<IIntervalNode, Integer>();
            for (IIntervalNode n : vertexSet()) {
                nodeIds.put(n, i);
                ++i;
            }
        }
    }

    public IIntervalNode findIntervalNode(IControlFlowNode n) {
        for (IIntervalNode in : vertexSet()) {
            if (in.getNode().contains(n))
                return in;
        }
        return  null;
    }

    private List<int[]> returnGraphRep() {
        // [[0, 1, 1], [1, 1, 2], ...]
        // 0->1 whose edge type is 1, 1->2 whose edge tpye is 2.
        List<int[]> graph = new ArrayList<int[]>();

        for (IIntervalEdge e : edgeSet()) {
            // the edge type should start at 1.
            int[] data = {nodeIds.get(e.getSourceNode()), e.getEdgeType(), nodeIds.get(e.getTargetNode())};
            graph.add(data);
        }
        /*if (useDFG) {
            DataFlowGraph df = new DataFlowGraph(this);
            for (Map.Entry<IControlFlowNode, IControlFlowNode> VRNode : df.retVRNodeMap().entrySet()) {
                int[] data = {nodeIds.get(findIntervalNode(VRNode.getKey())), 2, nodeIds.get(findIntervalNode(VRNode.getValue()))};
                graph.add(data);
            }
        }*/

        if (graph.isEmpty()) {
            int[] data = {0, 1, 0};
            graph.add(data);
        }

        return graph;
    }

    public boolean[][] returnAdjMatrix() {
        int matN = vertexSet().size();
        boolean[][] adjMatrix = new boolean[matN][matN];
        for (IIntervalEdge e : edgeSet()) {
            adjMatrix[nodeIds.get(e.getSourceNode())][nodeIds.get(e.getTargetNode())] = true;
        }
        return adjMatrix;
    }

    public JSONObject returnCallerJsonData(CtMethod target) {
        JSONObject graphJson = new JSONObject();
        List<int[]> graph = returnGraphRep();
        IIntervalNode.count = 0;
        boolean[] node_mask = new boolean[nodeIds.size()];

        for(IIntervalNode n : vertexSet()) {
            graphJson.put(nodeIds.get(n), n.returnCallerJsonData(nodeIds.get(n), CFGNodesWithoutCallee, node_mask, target));
        }
        JSONArray a2 = common.returnTarget(nodeIds, buggyNode);
        JSONArray bugPos = common.returnBuggyNode(nodeIds, buggyNode, node_mask);

        IControlFlowNode entry = ReturnEntryNodes(CFG).get(0);
        IIntervalNode entryInterval = findIntervalNode(entry);
        assert entryInterval != null;
        int id1 = nodeIds.get(entryInterval);
        int id2 = entryInterval.nodeIds.get(entryInterval.findhNode());
        int[] id = new int[2];
        id[0] = id1;
        id[1] = id2;

        graphJson.put("insideinterval", 0);
        graphJson.put("targets", a2);
        graphJson.put("numOfNode", vertexSet().size());
        graphJson.put("graph", common.ArrayToList(graph));
        // incorrect bugpos, use intra-interval ones and
        // you can also construct it from intra-interval bugpos.
        graphJson.put("bugPos", bugPos);
        graphJson.put("fileHash", common.returnFileInd(corBuggyMethod));
        graphJson.put("funName", baseMethod.getSimpleName());
        graphJson.put("entryNode", commom.ArrayToList(id));

        return graphJson;
    }

    public JSONObject returnTargetJsonData(Map<CtMethod, Integer> calleeIdMap) {
        JSONObject graphJson = new JSONObject();
        List<int[]> graph = returnGraphRep();
        IIntervalNode.count = 0;
        boolean[] node_mask = new boolean[nodeIds.size()];

        for(IIntervalNode n : vertexSet()) {
            graphJson.put(nodeIds.get(n), n.returnTargetJsonData(nodeIds.get(n), CFGNodesWithoutCallee, node_mask, calleeIdMap));
        }
        JSONArray a2 = common.returnTarget(nodeIds, buggyNode);
        JSONArray bugPos = common.returnBuggyNode(nodeIds, buggyNode, node_mask);

        IControlFlowNode entry = ReturnEntryNodes(CFG).get(0);
        IIntervalNode entryInterval = findIntervalNode(entry);
        assert entryInterval != null;
        int id1 = nodeIds.get(entryInterval);
        int id2 = entryInterval.nodeIds.get(entryInterval.findhNode());
        int[] id = new int[2];
        id[0] = id1;
        id[1] = id2;

        graphJson.put("insideinterval", 0);
        graphJson.put("targets", a2);
        graphJson.put("numOfNode", vertexSet().size());
        graphJson.put("graph", common.ArrayToList(graph));
        // incorrect bugpos, use intra-interval ones and
        // you can also construct it from intra-interval bugpos.
        graphJson.put("bugPos", bugPos);
        graphJson.put("fileHash", common.returnFileInd(corBuggyMethod));
        graphJson.put("funName", baseMethod.getSimpleName());
        graphJson.put("entryNode", commom.ArrayToList(id));

        return graphJson;
    }

    public JSONObject returnCalleeJsonData() {
        JSONObject graphJson = new JSONObject();
        List<int[]> graph = returnGraphRep();
        IIntervalNode.count = 0;
        boolean[] node_mask = new boolean[nodeIds.size()];

        for(IIntervalNode n : vertexSet()) {
            graphJson.put(nodeIds.get(n), n.returnCalleeJsonData(nodeIds.get(n), CFGNodesWithoutCallee, node_mask));
        }
        JSONArray a2 = common.returnTarget(nodeIds, buggyNode);
        JSONArray bugPos = common.returnBuggyNode(nodeIds, buggyNode, node_mask);

        IControlFlowNode entry = ReturnEntryNodes(CFG).get(0);
        IIntervalNode entryInterval = findIntervalNode(entry);
        assert entryInterval != null;
        int id1 = nodeIds.get(entryInterval);
        int id2 = entryInterval.nodeIds.get(entryInterval.findhNode());
        int[] id = new int[2];
        id[0] = id1;
        id[1] = id2;

        graphJson.put("insideinterval", 0);
        graphJson.put("targets", a2);
        graphJson.put("numOfNode", vertexSet().size());
        graphJson.put("graph", common.ArrayToList(graph));
        // incorrect bugpos, use intra-interval ones and
        // you can also construct it from intra-interval bugpos.
        graphJson.put("bugPos", bugPos);
        graphJson.put("fileHash", common.returnFileInd(corBuggyMethod));
        graphJson.put("funName", baseMethod.getSimpleName());
        graphJson.put("entryNode", commom.ArrayToList(id));

        return graphJson;
    }

    private boolean [] filterNodeMaskByUninlinedFun() {
        initCFGNodeID();
        boolean[] node_mask = new boolean[CFGNodeIds.size()];
        int count = 0;
        for (Map.Entry<IControlFlowNode, Integer> item : CFGNodeIds.entrySet()) {
            IControlFlowNode node = item.getKey();
            Integer id = item.getValue();

            if (CFGNodesWithoutCallee.contains(node)) {
                node_mask[id] = true;
                count++;
            }
            else {
                node_mask[id] = false;
            }
        }
        //System.out.println(count);
        return node_mask;
    }

/*
    public JSONObject returnASTJsonData() {
        JSONObject graphJson = new JSONObject();

        ProgGraph astBasedProgGraph = new ProgGraph(baseMethod, buggyCFGNode, CFGNodeIds, callees, CFGNodesWithoutCallee);
        List<int[]> graph = astBasedProgGraph.graph;


        int[][] node_features = astBasedProgGraph.returnFeatures();
        boolean[] node_mask = astBasedProgGraph.returnNodeMask();

        JSONArray a2 = common.returnTarget(CFGNodeIds, buggyCFGNode);
        JSONArray bugPos = astBasedProgGraph.returnBugPos();

        // The bug pos of AST is the same to CFG, since building CFG is based on AST.
        graphJson.put("targets", a2);
        graphJson.put("node_features", common.ArrayToList(node_features) );
        graphJson.put("node_mask", common.ArrayToList(node_mask) );
        graphJson.put("graph", common.ArrayToList(graph));
        graphJson.put("bugPos", bugPos);
        graphJson.put("fileHash", common.returnFileInd(corBuggyMethod));
        graphJson.put("funName", baseMethod.getSimpleName());
        return graphJson;
    }*/

    private void initCFGNodeID() {
        if (CFGNodeIds != null)
            return;
        CFGNodeIds = new HashMap<>();
        int i = 0;
        for (IControlFlowNode n : CFG.vertexSet()) {
            CFGNodeIds.put(n, i);
            ++i;
        }
    }

    public JSONObject returnCFGJsonData() {
        JSONObject graphJson = new JSONObject();
        List<int[]> graph = new ArrayList<int[]>();

        initCFGNodeID();

        for (IControlFlowEdge e : CFG.edgeSet()) {
            // the edge type should start at 1.
            int[] data = {CFGNodeIds.get(e.getSourceNode()), 1, CFGNodeIds.get(e.getTargetNode())};
            graph.add(data);
        }
        if (CFG.vertexSet().isEmpty()) {
            int[] data = {0, 1, 0};
            graph.add(data);
        }

        int[][] node_features = new int[CFGNodeIds.size()][tokenIndex.Size];
        // for mask inlined cfg nodes.
        boolean [] node_mask = filterNodeMaskByUninlinedFun();


        for (Map.Entry<IControlFlowNode, Integer> item : CFGNodeIds.entrySet()) {
            IControlFlowNode node = item.getKey();
            Integer id = item.getValue();
            tokenVisitor vis = IIntervalNode.returnToken(node);
            node_features[id] = vis.getVector();
        }

        JSONArray a2 = common.returnTarget(CFGNodeIds, buggyCFGNode);
        JSONArray bugPos = common.returnBuggyNode(CFGNodeIds, buggyCFGNode, node_mask);

        graphJson.put("targets", a2);
        graphJson.put("node_mask", common.ArrayToList(node_mask) );
        graphJson.put("node_features", common.ArrayToList(node_features) );
        graphJson.put("graph", common.ArrayToList(graph));
        graphJson.put("bugPos", bugPos);
        graphJson.put("fileHash", common.returnFileInd(corBuggyMethod));
        graphJson.put("funName", baseMethod.getSimpleName());

        return graphJson;
    }
}
