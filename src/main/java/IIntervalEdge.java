import org.jgrapht.graph.DefaultEdge;

public class IIntervalEdge extends DefaultEdge {
    int edgeType;
    public IIntervalEdge() {
        super();
        edgeType = 1;
    }
    public int getEdgeType() {return edgeType;}
    public void setEdgeType(int et) {edgeType = et;}
    public IIntervalNode getTargetNode() {
        return (IIntervalNode) getTarget();
    }

    public IIntervalNode getSourceNode() {
        return (IIntervalNode) getSource();
    }
}


