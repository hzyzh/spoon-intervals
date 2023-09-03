import org.apache.log4j.Level;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.code.CtInvocation;

import java.util.*;


public class calleeLocProcessor extends AbstractProcessor<CtInvocation> {
	private Map<CtMethod, BuggyInfo> buggyInfoMap = null;
    private Set<CtMethod> cleanSet = null;
    private Map<CtMethod, List<CtMethod>> calleeTargetMap = new HashMap<>();
	private Map<CtMethod, List<CtMethod>> targetCalleeMap = new HashMap<>();


	public Map<CtMethod, List<CtMethod>> getCalleeTargetMap() {
		return calleeTargetMap;
	}

    public Map<CtMethod, List<CtMethod>> getTargetCalleeMap() {
        return targetCalleeMap;
    }

    calleeLocProcessor(Map<CtMethod, BuggyInfo> buggyInfoMap, Set<CtMethod> cleanSet){
        this.buggyInfoMap = buggyInfoMap;
        this.cleanSet = cleanSet;
    }

	@Override
	public boolean isToBeProcessed(CtInvocation candidate) {
		return true;
	}

	@Override
	public void process(CtInvocation element) {
        CtExecutableReference r_calling = element.getExecutable();

        if(r_calling != null){
            CtExecutable calling = r_calling.getDeclaration();

            if((calling != null) && (calling instanceof CtMethod)) {
                CtMethod calleeMethod = (CtMethod) calling;
                CtMethod parent = element.getParent(CtMethod.class);

                if(calleeMethod != null && parent != null && ((buggyInfoMap != null && buggyInfoMap.containsKey(parent)) || (cleanSet != null && cleanSet.contains(parent)))) {

                    if(!targetCalleeMap.containsKey(parent)) {
                        List<CtMethod> l = new ArrayList<>();
                        l.add(calleeMethod);
                        targetCalleeMap.put(parent, l);
                    } else {
                        List<CtMethod> l = targetCalleeMap.get(parent);
                        if(!l.contains(calleeMethod))
                            l.add(calleeMethod);
                    }
                    if(!calleeTargetMap.containsKey(calleeMethod)) {
                        List<CtMethod> l = new ArrayList<>();
                        l.add(parent);
                        calleeTargetMap.put(calleeMethod, l);
                    } else {
                        List<CtMethod> l = calleeTargetMap.get(calleeMethod);
                        if(!l.contains(parent))
                            l.add(parent);
                    }
                }
            }
        }
	}
}