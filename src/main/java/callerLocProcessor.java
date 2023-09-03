import org.apache.log4j.Level;
import spoon.processing.AbstractProcessor;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtStatement;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtTypedElement;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.code.CtInvocation;

import java.util.*;

/**
 * Reports warnings when empty catch blocks are found.
 */
public class callerLocProcessor extends AbstractProcessor<CtInvocation> {
	private Map<CtMethod, BuggyInfo> buggyInfoMap = null;
	private Set<CtMethod> cleanSet = null;
	private Map<CtMethod, List<CtMethod>> callerTargetMap = new HashMap<>();


	public Map<CtMethod, List<CtMethod>> getCallerTargetMap() {
		return callerTargetMap;
	}

    callerLocProcessor(Map<CtMethod, BuggyInfo> buggyInfoMap, Set<CtMethod> cleanSet){
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
                CtMethod targetMethod = (CtMethod) calling;

                if((buggyInfoMap != null && buggyInfoMap.containsKey(targetMethod)) || (cleanSet != null && cleanSet.contains(targetMethod))) {
                    CtMethod parent = element.getParent(CtMethod.class);

                    if(parent != null) {
                        //System.out.println(parent.toString());
						if(!callerTargetMap.containsKey(parent)) {
                            List<CtMethod> l = new ArrayList<>();
							l.add(targetMethod);
							callerTargetMap.put(parent, l);
						} else {
							List<CtMethod> l = callerTargetMap.get(parent);
							if(!l.contains(targetMethod))
								l.add(targetMethod);
						}
                    }
                }
            }
        }
	}
	/*
	@Override
	public void process(CtClass element) {
		SourcePosition position = element.getPosition();
		if(position.isValidPosition()) {
			if (! position.getFile().toString().endsWith(targetFile)) {
			    return;
			}
			Set<CtMethod> methodSet = element.getAllMethods();
			for (CtMethod method : methodSet){
				SourcePosition methodPosition = method.getPosition();
				if(!methodPosition.isValidPosition()) continue;
				int methodLine = methodPosition.getLine();
				if (methodLine > curLine && methodLine < targetLine) {
					curLine = methodLine;
					this.method = method;
				}
			}
		}
	}
	*/
}