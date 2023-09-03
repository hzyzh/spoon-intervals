import fr.inria.controlflow.*;

import java.io.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.*;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;

import java.util.*;

import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import spoon.reflect.CtModel;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.Filter;

import javax.naming.ldap.Control;


public class CFGInt {
    List<ControlFlowGraph> graphs = new ArrayList<>();
    List<IControlFlowGraph> igraphs = new ArrayList<>();
    List<IntervalDerivedGraph> ISGs = new ArrayList<IntervalDerivedGraph>();
    List<IIntervalDerivedGraph> IISGs = new ArrayList<>();
    Map<CtMethod, MutableTriple<ArrayList<IIntervalDerivedGraph>, IIntervalDerivedGraph, ArrayList<IIntervalDerivedGraph>>> ISGsMap = new HashMap<>();
    List<ProgGraph> progGraphs = new ArrayList<ProgGraph>();
    boolean ifPrint = false;
    boolean useDFG = false;
    boolean useEGINN = false;
    int cleanOutputCount = -1;
    CtModel model;
    public CFGInt(CtModel m) {
        model = m;
    }
    public CFGInt (CtModel m, boolean useEGINN) {
        model = m;
        this.useEGINN = useEGINN;
    }

    public enum CallingRole {
        CALLER,
        TARGET,
        CALLEE
    }

    private void addISG(CtMethod target, CallingRole role, IIntervalDerivedGraph isg) {
        MutableTriple<ArrayList<IIntervalDerivedGraph>, IIntervalDerivedGraph, ArrayList<IIntervalDerivedGraph>> triple;
        boolean isNew = !ISGsMap.containsKey(target);
        
        if(isNew) {
            triple = new MutableTriple<>(new ArrayList<IIntervalDerivedGraph>(), null, new ArrayList<IIntervalDerivedGraph>());
        } else {
            triple = ISGsMap.get(target);
        }

        if(role == CallingRole.CALLER) {
            triple.getLeft().add(isg);
        } else if(role == CallingRole.TARGET) {
            triple.setMiddle(isg);
        } else {
            triple.getRight().add(isg);
        }

        if(isNew) {
            ISGsMap.put(target, triple);
        }
    }

    private CtMethod returnCorBuggyMethod(Map<CtMethod, BuggyInfo> buggyInfoMap, CtMethod method) {
        SourcePosition position = method.getPosition();
		if(position.isValidPosition()) {
            for (Map.Entry<CtMethod, BuggyInfo> bi : buggyInfoMap.entrySet()) {
                if (position.getFile().toString().endsWith(bi.getValue().targetFile)) {
                    return bi.getKey();
                }
            }
        }
        return null;
    }

    private HashSet<ControlFlowNode> retCFGNodes(ControlFlowGraph g) {
        HashSet<ControlFlowNode> tmp = new HashSet<>();
        for (ControlFlowNode n : g.vertexSet()) {
            // we don't need to predict nodes without statements.
            CtElement ele = n.getStatement();
            if (ele == null)
                continue;
            tmp.add(n);
        }
        return tmp;
    }

    private HashSet<IControlFlowNode> retICFGNodes(IControlFlowGraph g) {
        HashSet<IControlFlowNode> tmp = new HashSet<>();
        for (IControlFlowNode n : g.vertexSet()) {
            // we don't need to predict nodes without statements.
            CtElement ele = n.getStatement();
            if (ele == null)
                continue;
            tmp.add(n);
        }
        return tmp;
    }

    private IntervalDerivedGraph retIntervals(CtMethod corBM, CtMethod element, int inlineLevel) {
        ControlFlowGraph graph = cfgConstruct(element);
        HashSet<ControlFlowNode> CFGNodesWithoutCallee = retCFGNodes(graph);

        List<CtMethod> callees = analyzeCallees(graph, inlineLevel);
        graphs.add(graph);
        IntervalDerivedGraph intervals = constructIntervals(graph, element);
        intervals.addCFGNodesWithoutCallee(CFGNodesWithoutCallee);
        intervals.addCorBuggyMethod(corBM);
        intervals.addCallees(callees);
        return intervals;
    }

    private IIntervalDerivedGraph retIIntervals(CtMethod corBM, CtMethod element, List<CtMethod> calleesPool) { 
        IControlFlowGraph graph = icfgConstruct(element, calleesPool);
        HashSet<IControlFlowNode> ICFGNodes = retICFGNodes(graph);
        
        // callees are previously analyzed
        igraphs.add(graph);
        IIntervalDerivedGraph intervals = constructIIntervals(graph, element);
        intervals.addCFGNodesWithoutCallee(ICFGNodes);
        intervals.addCorBuggyMethod(element);
        return intervals;
    }

    List<CtMethod> analyzeCallees(ControlFlowGraph bm, int inlineLevel) {
        List<CtMethod> callees = new ArrayList<>();
        List<ControlFlowNode> corNodes = new ArrayList<>();
        if (inlineLevel == 0)
            return callees;

        for ( ControlFlowNode node : bm.vertexSet()) {
            CtElement cte = node.getStatement();
            if (cte == null)
                continue;
            for ( CtElement ele : cte.asIterable()) {
                if (ele instanceof  CtExecutableReference) {

                    CtExecutableReference CI = (CtExecutableReference) ele;
                    CtExecutable t = CI.getDeclaration();
                    if (t instanceof  CtMethod) {
                        CtMethod method = (CtMethod) t;
                        if (method.getBody() == null)
                            continue;;
                        callees.add(method);
                        // it is ok since list.add adds a reference.
                        corNodes.add(node);
                    }
                }
            }
        }

        List<CtMethod> subCallees = new ArrayList<>();
        for ( int index = 0; index < callees.size(); index++) {
            CtMethod method = callees.get(index);
            ControlFlowNode node = corNodes.get(index);
            ControlFlowGraph graph = cfgConstruct(method);
            subCallees.addAll(analyzeCallees(graph, inlineLevel - 1));
            mergeTwoGraphs(bm, graph);
            ControlFlowNode calleeExit = graph.getExitNode();
            List<ControlFlowNode> calleeBegins = graph.findNodesOfKind(BranchKind.BEGIN);
            if (calleeBegins.size() != 1)
                System.out.println("ERROR: Callee Begin is larger than 1.");
            ControlFlowNode calleeBegin = calleeBegins.get(0);
            // Modify the kind since it is no longer a BEGIN node.
            calleeBegin.setKind(BranchKind.STATEMENT);
            calleeExit.setKind(BranchKind.STATEMENT);
            for (ControlFlowNode next : node.next()) {
                bm.removeEdge(node, next);
                bm.addEdge(node, calleeBegin);
                bm.addEdge(calleeExit, next);
            }
        }
        callees.addAll(subCallees);
        return callees;
    }

    public  void mergeTwoGraphs(ControlFlowGraph g1, ControlFlowGraph g2) {
        for (ControlFlowNode n : g2.vertexSet()) {
            g1.addVertex(n);
        }
        for (ControlFlowEdge e : g2.edgeSet()) {
            g1.addEdge(e.getSourceNode(), e.getTargetNode());
        }

    }

    public void addGraphs(CtMethod element, Map<CtMethod, BuggyInfo> buggyInfoMap, boolean isFix,
                          boolean outputClean, boolean outputCleanAll, int inlineLevel, boolean useDFG) {
        this.useDFG = useDFG;
        CtMethod corBM = returnCorBuggyMethod(buggyInfoMap, element);
        if (corBM == null)
            return;

        if (cleanOutputCount == -1) {
            cleanOutputCount = buggyInfoMap.size();
        }
        int buggyLine = 0;
        if (buggyInfoMap == null)
            buggyLine = -1;
        else {
            BuggyInfo b = buggyInfoMap.get(element);
            if (b != null)
                buggyLine = b.targetLine;
        }

        if (buggyLine != 0) {
            IntervalDerivedGraph intervals = retIntervals(corBM, element, inlineLevel);
            if (!isFix)
                intervals.addBuggyLine(buggyLine);
            ISGs.add(intervals);
        }
        else {
            assert(cleanOutputCount != -1);
            if ((outputClean && cleanOutputCount != 0) || outputCleanAll) {
                IntervalDerivedGraph intervals = retIntervals(corBM, element, inlineLevel);
                ISGs.add(intervals);
                cleanOutputCount--;
            }
        }

    }

    public void addIGraphs(CtMethod element, Map<CtMethod, BuggyInfo> buggyInfoMap, Set<CtMethod> cleanSet, Map<CtMethod, List<CtMethod>> callerTargetMap, Map<CtMethod, List<CtMethod>> calleeTargetMap, Map<CtMethod, List<CtMethod>> targetCalleeMap,
    boolean isFix, boolean outputClean, boolean outputCleanAll, int inlineLevel, boolean useDFG) {

        this.useDFG = useDFG;
        CtMethod corBM = returnCorBuggyMethod(buggyInfoMap, element);
        if (corBM == null || buggyInfoMap == null)
            return;

        if(callerTargetMap != null && callerTargetMap.containsKey(element)) {
            IIntervalDerivedGraph intervals = retIIntervals(corBM, element, callerTargetMap.get(element));
            List<CtMethod> list = callerTargetMap.get(element);
            assert list != null;
            for(CtMethod target : list) {
                addISG(target, CallingRole.CALLER, intervals);
            }
        }
        
        int buggyLine = 0;
        if (buggyInfoMap != null) {

            BuggyInfo b = buggyInfoMap.get(element);
            if (b != null) {

                buggyLine = b.targetLine;
                IIntervalDerivedGraph intervals = retIIntervals(corBM, element, targetCalleeMap.get(element));
                if (!isFix)
                    intervals.addBuggyLine(buggyLine);
                addISG(element, CallingRole.TARGET, intervals);
            }
        }
        if(cleanSet != null && cleanSet.contains(element)) {
            IIntervalDerivedGraph intervals = retIIntervals(corBM, element, targetCalleeMap.get(element));
            addISG(element, CallingRole.TARGET, intervals);
        }

        if(calleeTargetMap != null && calleeTargetMap.containsKey(element)) {
            IIntervalDerivedGraph intervals = retIIntervals(corBM, element, null);
            List<CtMethod> list = calleeTargetMap.get(element);
            assert list != null;
            for(CtMethod target : list) {
                addISG(target, CallingRole.CALLEE, intervals);
            }
        }
    }


    public ControlFlowGraph cfgConstruct(CtMethod element){
        ControlFlowBuilder builder = new ControlFlowBuilder();
        ControlFlowGraph graph = builder.build(element);
        graph.simplify();
        if(ifPrint) {
            System.out.println(element.getSimpleName()+"\n");
            System.out.println(graph.toGraphVisText());
        }

        return graph;
    }

    public IControlFlowGraph icfgConstruct(CtMethod element, List<CtMethod> calleesPool) {
        IControlFlowBuilder builder = new IControlFlowBuilder(calleesPool);
        IControlFlowGraph graph = builder.build(element);
        graph.simplify();

        return graph;
    }

    private HashSet<ControlFlowNode> analyzeINodes(intervalNode I) {
        HashSet<ControlFlowNode> nodes = I.getNode();
        HashSet<ControlFlowNode> sucessors = new HashSet<ControlFlowNode>();
        HashSet<ControlFlowNode> setOfn = new HashSet<ControlFlowNode>();
        for (ControlFlowNode node : nodes) {
            HashSet<ControlFlowNode> succs = new HashSet<ControlFlowNode>(node.next());
            sucessors.addAll(succs);
        }
        //sucessors  = ListUtils.subtract(sucessors, nodes);
        sucessors.removeAll(nodes);
        for (ControlFlowNode node : sucessors) {
            HashSet<ControlFlowNode> preds = new HashSet<ControlFlowNode>(node.prev());
            if(nodes.containsAll(preds)) {
                setOfn.add(node);
            }
        }
        return setOfn;
    }

    private HashSet<IControlFlowNode> analyzeIIntervalNodes(IIntervalNode I) {
        HashSet<IControlFlowNode> nodes = I.getNode();
        HashSet<IControlFlowNode> sucessors = new HashSet<IControlFlowNode>();
        HashSet<IControlFlowNode> setOfn = new HashSet<IControlFlowNode>();
        for (IControlFlowNode node : nodes) {
            HashSet<IControlFlowNode> succs = new HashSet<IControlFlowNode>(node.next());
            sucessors.addAll(succs);
        }
        //sucessors  = ListUtils.subtract(sucessors, nodes);
        sucessors.removeAll(nodes);
        for (IControlFlowNode node : sucessors) {
            HashSet<IControlFlowNode> preds = new HashSet<IControlFlowNode>(node.prev());
            if(nodes.containsAll(preds)) {
                setOfn.add(node);
            }
        }
        return setOfn;
    }

    private HashSet<ControlFlowNode> findNextHeaders(intervalNode I, ControlFlowGraph graph,
                                                     HashSet<ControlFlowNode> processed) {
        HashSet<ControlFlowNode> headers = new HashSet<ControlFlowNode>();
        HashSet<ControlFlowNode> INodes = I.getNode();

        for (ControlFlowNode node : graph.vertexSet()) {
            if (INodes.contains(node) || processed.contains(node))
                continue;
            HashSet<ControlFlowNode> preds = new HashSet<ControlFlowNode>(node.prev());
            for (ControlFlowNode m : preds) {
                if (INodes.contains(m)) {
                    headers.add(node);
                    break;
                }
            }

        }
        return headers;
    }

        private HashSet<IControlFlowNode> findNextIHeaders(IIntervalNode I, IControlFlowGraph graph, HashSet<IControlFlowNode> processed) {
        HashSet<IControlFlowNode> headers = new HashSet<IControlFlowNode>();
        HashSet<IControlFlowNode> INodes = I.getNode();

        for (IControlFlowNode node : graph.vertexSet()) {
            if (INodes.contains(node) || processed.contains(node))
                continue;
            HashSet<IControlFlowNode> preds = new HashSet<IControlFlowNode>(node.prev());
            for (IControlFlowNode m : preds) {
                if (INodes.contains(m)) {
                    headers.add(node);
                    break;
                }
            }

        }
        return headers;
    }

    public IntervalDerivedGraph constructIntervals(ControlFlowGraph graph, CtMethod method){
        IntervalDerivedGraph ISG = new IntervalDerivedGraph(useDFG);
        ISG.addEntryMethod(method);

        List<ControlFlowNode> entryNodes = ISG.ReturnEntryNodes(graph);
        HashSet<ControlFlowNode> H = new HashSet<>(entryNodes);
        if (H == null)
            return null;
        HashSet<ControlFlowNode> processed = new HashSet<ControlFlowNode>();
        while(!H.isEmpty()) {
            ControlFlowNode h = H.iterator().next();
            H.remove(h);
            intervalNode I = new intervalNode(h);
            HashSet<ControlFlowNode> setOfn;
            do {
                setOfn = analyzeINodes(I);
                I.addNodes(setOfn);
            } while (!setOfn.isEmpty());
            processed.addAll(I.getNode());
            H.addAll(findNextHeaders(I, graph, processed));
            //I.constructIntervalCFG();
            ISG.addVertex(I);
        }
        ISG.linkEdges();
        //ISG.returnAdjMatrix();
        if(ifPrint)
            ISG.print();
        return ISG;
    }

    public IIntervalDerivedGraph constructIIntervals(IControlFlowGraph graph, CtMethod method){
        IIntervalDerivedGraph ISG = new IIntervalDerivedGraph(useDFG);
        ISG.addEntryMethod(method);

        List<IControlFlowNode> entryNodes = ISG.ReturnEntryNodes(graph);
        HashSet<IControlFlowNode> H = new HashSet<>(entryNodes);
        if (H == null)
            return null;
        HashSet<IControlFlowNode> processed = new HashSet<IControlFlowNode>();
        while(!H.isEmpty()) {
            IControlFlowNode h = H.iterator().next();
            H.remove(h);
            IIntervalNode I = new IIntervalNode(h);
            HashSet<IControlFlowNode> setOfn;
            do {
                setOfn = analyzeIIntervalNodes(I);
                I.addNodes(setOfn);
            } while (!setOfn.isEmpty());
            processed.addAll(I.getNode());
            H.addAll(findNextIHeaders(I, graph, processed));
            //I.constructIntervalCFG();
            ISG.addVertex(I);
        }
        ISG.linkEdges();
        //ISG.returnAdjMatrix();
        if(ifPrint)
            ISG.print();
        return ISG;
    }


    public void dumpJson(String outputXML, boolean isDumpCFG, boolean isDumpAST, String projName) {
        List<JSONObject> intervalLists = new ArrayList<JSONObject>();
        for (IntervalDerivedGraph ISG : ISGs) {
            intervalLists.add(ISG.returnJsonData());
        }
        String outputJson = outputXML;
        writeJsonFileByText(intervalLists, outputJson, projName);

        if (isDumpCFG){
            intervalLists.clear();
            outputJson = outputJson.split("\\.(?=[^\\.]+$)")[0];

            outputJson += "-CFG.json";
            for (IntervalDerivedGraph ISG : ISGs) {
                intervalLists.add(ISG.returnCFGJsonData());
            }
            writeJsonFileByText(intervalLists, outputJson, projName);
            outputJson = outputXML;
        }
        if (isDumpAST) {
            intervalLists.clear();
            outputJson = outputJson.split("\\.(?=[^\\.]+$)")[0];
            outputJson += "-AST.json";
            for (IntervalDerivedGraph ISG : ISGs) {
                intervalLists.add(ISG.returnASTJsonData());
            }
            writeJsonFileByText(intervalLists, outputJson, projName);
            outputJson = outputXML;
        }
    }

    public void dumpJson2(String outputXML, boolean isDumpCFG, boolean isDumpAST, String projName) {
        List<JSONObject> intervalLists = new ArrayList<JSONObject>();

        for(Map.Entry<CtMethod, MutableTriple<ArrayList<IIntervalDerivedGraph>, IIntervalDerivedGraph, ArrayList<IIntervalDerivedGraph>>> entry : ISGsMap.entrySet()) {
            CtMethod target = entry.getKey();
            Triple<ArrayList<IIntervalDerivedGraph>, IIntervalDerivedGraph, ArrayList<IIntervalDerivedGraph>> triple = entry.getValue();
            List<JSONObject> callerJsonList = new ArrayList<>();
            JSONObject targetJson;
            List<JSONObject> calleeJsonList = new ArrayList<>();

            int i = 0;
            Map<CtMethod, Integer> calleeIdMap = new HashMap<>();
            for(IIntervalDerivedGraph graph : triple.getRight()) {
                calleeIdMap.put(graph.getEntryMethod(), i++);
            }

            for(IIntervalDerivedGraph caller : triple.getLeft()) {
                callerJsonList.add(caller.returnCallerJsonData(target));
            }
            targetJson = triple.getMiddle().returnTargetJsonData(calleeIdMap);
            for(IIntervalDerivedGraph callee : triple.getRight()) {
                calleeJsonList.add(callee.returnCalleeJsonData());
            }

            JSONObject object = new JSONObject();
            object.put("callers", common.ToJsonArray(callerJsonList));
            object.put("target", targetJson);
            object.put("callees", common.ToJsonArray(calleeJsonList));
            intervalLists.add(object);
        }
        String outputJson = outputXML;
        outputJson = outputJson.split("\\.(?=[^\\.]+$)")[0];
        outputJson += "-EGINN.json";
        writeJsonFileByText(intervalLists, outputJson, projName);

        if(isDumpCFG) {
            //TODO
        }
    }

    private void writeJsonFileByText(List<JSONObject> jsonFile, String outputFileName, String projName) {
        File outputFile = new File(outputFileName);
        StringBuilder contentBuilder = new StringBuilder();
        boolean addComma = true;
        String data = null;
        if (outputFile.exists()) {

            try (BufferedReader br = new BufferedReader(new FileReader(outputFileName)))
            {

                String sCurrentLine;
                while ((sCurrentLine = br.readLine()) != null)
                {
                    contentBuilder.append(sCurrentLine).append("\n");
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            data = contentBuilder.toString();
            if (data.length() < 4 ) {
                data = "[";
                addComma = false;
            }
            else
                data = data.substring(0, data.length()-2);
        }
        else {
            data = "[";
            addComma = false;
        }


        for (JSONObject array : jsonFile) {
            array.put("projName", projName);
            if (addComma) {
                data += ",";
            }
            else
                addComma = true;
            data += array.toJSONString();
        }
        data += "]";

        try (FileWriter file = new FileWriter(outputFile)) {
            file.write(data);
            file.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeJsonFile(List<JSONObject> jsonFile, String outputFileName, String projName) {
        JSONArray intervalLists = null;
        File outputFile = new File(outputFileName);
        if (outputFile.exists()) {
            try (FileReader f = new FileReader(outputFile)) {
                JSONParser parser = new JSONParser();
                intervalLists = (JSONArray) parser.parse(f);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        else {
            intervalLists = new JSONArray();
        }

        for (JSONObject array : jsonFile) {
            array.put("projName", projName);
            intervalLists.add(array);
        }

        try (FileWriter file = new FileWriter(outputFile)) {
            file.write(intervalLists.toJSONString());
            file.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
