import fr.inria.controlflow.*;

import org.jgrapht.graph.DefaultDirectedGraph;
import spoon.reflect.declaration.CtElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class IControlFlowGraph extends DefaultDirectedGraph<IControlFlowNode, IControlFlowEdge> {

	/**
	 * Description of the graph
	 */
	private String name;

	private IControlFlowNode exitNode;

	public IControlFlowGraph(Class<? extends IControlFlowEdge> edgeClass) {
		super(edgeClass);
	}

	public IControlFlowGraph() {
		super(IControlFlowEdge.class);
	}

	private int countNodes(BranchKind kind) {
		int result = 0;
		for (IControlFlowNode v : vertexSet()) {
			if (v.getKind().equals(kind)) {
				result++;
			}
		}
		return result;
	}

	/* public String toGraphVisText() {
		GraphVisPrettyPrinter p = new GraphVisPrettyPrinter(this);
		return p.print();
	} */

	/**
	 * Find the node holding and element
	 *
	 * @param e node to find
	 * @return
	 */
	public IControlFlowNode findNode(CtElement e) throws NotFoundException {
		if (e != null) {
			for (IControlFlowNode n : vertexSet()) {
				if (e == n.getStatement()) {
					return n;
				}
			}
		}
		throw new NotFoundException("Element's node not found ");
	}

	/**
	 * Find nodes by a given id
	 * @param id of the node to find
	 * @return
	 */
	public IControlFlowNode findNodeById(int id) {
		for (IControlFlowNode n : vertexSet()) {
			if (n.getId() == id) {
				return n;
			}
		}
		return null;
	}

	/**
	 * Find all nodes of a given kind
	 *
	 * @param kind of node to find
	 * @return list of nodes
	 */
	public List<IControlFlowNode> findNodesOfKind(BranchKind kind) {
		ArrayList<IControlFlowNode> result = new ArrayList<IControlFlowNode>();
		for (IControlFlowNode n : vertexSet()) {
			if (n.getKind().equals(kind)) {
				result.add(n);
			}
		}
		return result;
	}

	@Override
	public IControlFlowEdge addEdge(IControlFlowNode source, IControlFlowNode target) {
		if (!containsVertex(source)) {
			addVertex(source);
		}
		if (!containsVertex(target)) {
			addVertex(target);
		}
		return super.addEdge(source, target);
	}

	/**
	 * Returns all statements
	 */
	public List<IControlFlowNode> statements() {
		return findNodesOfKind(BranchKind.STATEMENT);
	}

	/**
	 * Returns all branches
	 */
	public List<IControlFlowNode> branches() {
		return findNodesOfKind(BranchKind.BRANCH);
	}

	private void simplify(BranchKind kind) {
		try {
			List<IControlFlowNode> convergence = findNodesOfKind(kind);
			for (IControlFlowNode n : convergence) {
				Set<IControlFlowEdge> incoming = incomingEdgesOf(n);
				Set<IControlFlowEdge> outgoing = outgoingEdgesOf(n);
				if (incoming != null && outgoing != null) {
					for (IControlFlowEdge in : incoming) {
						for (IControlFlowEdge out : outgoing) {
							IControlFlowEdge ed = addEdge(in.getSourceNode(), out.getTargetNode());
							if (ed != null) {
								ed.setBackEdge(out.isBackEdge() || in.isBackEdge());
							}
						}
					}
				}

				for (IControlFlowEdge e : edgesOf(n)) {
					removeEdge(e);
				}
				removeVertex(n);
			}
		} catch (Exception e) {
			//System.out.println(toGraphVisText());
			throw e;
		}
		//Clean the exit node
		exitNode = null;
	}

	/**
	 * Removes all blocks
	 */
	public void simplifyBlockNodes() {
		simplify(BranchKind.BLOCK_BEGIN);
		simplify(BranchKind.BLOCK_END);
	}

	/**
	 * Removes all non statements or branches
	 */
	public void simplify() {
		simplifyConvergenceNodes();
		simplifyBlockNodes();
	}

	/**
	 * Removes all convergence nodes
	 */
	public void simplifyConvergenceNodes() {
		simplify(BranchKind.CONVERGE);
	}

	//public void

	public int branchCount() {
		return countNodes(BranchKind.BRANCH);
	}

	public int statementCount() {
		return countNodes(BranchKind.STATEMENT);
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public IControlFlowNode getExitNode() {
		if (exitNode == null) {
			exitNode = findNodesOfKind(BranchKind.EXIT).get(0);
		}
		return exitNode;
	}
}
