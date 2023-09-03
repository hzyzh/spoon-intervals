import fr.inria.controlflow.*;

import org.jgrapht.graph.DefaultEdge;

/**
 * Created by marodrig on 13/10/2015.
 */
public class IControlFlowEdge extends DefaultEdge {

	/**
	 * Indicates if this loop is the looping edge of a loop (from the las statement to the first of a loop).
	 */
	boolean isBackEdge = false;

	public boolean isBackEdge() {
		return isBackEdge;
	}

	public void setBackEdge(boolean isLooopingEdge) {
		this.isBackEdge = isLooopingEdge;
	}

	public IControlFlowNode getTargetNode() {
		return (IControlFlowNode) getTarget();
	}

	public IControlFlowNode getSourceNode() {
		return (IControlFlowNode) getSource();
	}

}
