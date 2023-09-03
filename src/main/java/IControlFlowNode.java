import fr.inria.controlflow.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * A node of the control flow
 *
 * Created by marodrig on 13/10/2015.
 */
public class IControlFlowNode {

	public static int count = 0;

	private int id;

    private List<CtMethod> callees;

	/**
	 * control flow graph containing this node
	 */
	IControlFlowGraph parent;

	public BranchKind getKind() {
		return kind;
	}

	public void setKind(BranchKind kind) {
		this.kind = kind;
	}

	private BranchKind kind;

	/**
	 * Statement that is going to be pointed to by this node
	 */
	CtElement statement;

	List<Value> input;

	List<Value> output;

	//An object you can tag to the node
	Object tag;

	/**
	 * Visitor containing the transfer functions for each node
	 */
	TransferFunctionVisitor visitor;

	public IControlFlowNode(CtElement statement, IControlFlowGraph parent, BranchKind kind, List<CtMethod> callees) {
		this.kind = kind;
		this.parent = parent;
		this.statement = statement;
		++count;
		id = count;
		if(callees != null)
        	this.callees = callees;
		else
			this.callees = new ArrayList<>();
	}

	public IControlFlowNode(CtElement statement, IControlFlowGraph parent, BranchKind kind, CtMethod callee) {
		this.kind = kind;
		this.parent = parent;
		this.statement = statement;
		++count;
		id = count;
        this.callees = new ArrayList<>();
		this.callees.add(callee);
	}

	public IControlFlowNode(CtElement statement, IControlFlowGraph parent, BranchKind kind) {
		this.kind = kind;
		this.parent = parent;
		this.statement = statement;
		++count;
		id = count;
		this.callees = new ArrayList<>();
	}


	public IControlFlowNode(CtElement statement, IControlFlowGraph parent) {
		this.statement = statement;
		this.parent = parent;
		++count;
		id = count;
		this.callees = new ArrayList<>();
	}

	/**
	 * Performs the transfer using a given visitor
	 */
	public void transfer(TransferFunctionVisitor visitor) {
		this.visitor = visitor;
		transfer();
	}

	/**
	 * Perform the transfer function
	 */
	public void transfer() {
		if (statement != null && visitor != null) {
			output = visitor.transfer(statement);
		} else {
			throw new RuntimeException("Unable to perform the transfer function. Statement or visitor are null.");
		}

	}

	public int getId() {
		return id;
	}

    public List<CtMethod> getCallees(){
        return callees;
    }

	/**
	 * Obtains the siblings of a control node. Siblings are the nodes in parallel branches
	 */
	public List<IControlFlowNode> siblings() {
		ArrayList<IControlFlowNode> result = new ArrayList<IControlFlowNode>();
		for (IControlFlowNode n : prev()) {
			for (IControlFlowNode nn : n.next()) {
				if (!nn.equals(this)) {
					result.add(nn);
				}
			}
		}
		return result;
	}

	/**
	 * List of nodes that can be executed just after this one
	 */
	public List<IControlFlowNode> next() {
		ArrayList<IControlFlowNode> result = new ArrayList<IControlFlowNode>();
		for (IControlFlowEdge e : parent.outgoingEdgesOf(this)) {
			result.add(e.getTargetNode());
		}
		return result;
	}

	/**
	 * List of nodes that could be executed just before this one
	 */
	public List<IControlFlowNode> prev() {
		ArrayList<IControlFlowNode> result = new ArrayList<IControlFlowNode>();
		for (IControlFlowEdge e : parent.incomingEdgesOf(this)) {
			result.add(e.getSourceNode());
		}
		return result;
	}

	public List<Value> getOutput() {
		if (output == null)  {
			transfer();
		}
		return output;
	}

	public CtElement getStatement() {
		return statement;
	}

	public void setStatement(CtElement statement) {
		this.statement = statement;
	}

	public List<Value> getInput() {
		return input;
	}

	public void setInput(List<Value> input) {
		this.input = input;
	}

	public IControlFlowGraph getParent() {
		return parent;
	}

	public void setParent(IControlFlowGraph parent) {
		this.parent = parent;
	}

	public Object getTag() {
		return tag;
	}

	public void setTag(Object tag) {
		this.tag = tag;
	}

	@Override
	public String toString() {
		if (statement != null) {
			return id + " - " + statement.toString();
		} else {
			return kind + "_" + id;
		}
	}

}
