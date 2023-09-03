import org.apache.commons.collections4.bag.SynchronizedSortedBag;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.*;
import spoon.reflect.path.CtRole;
import spoon.reflect.visitor.CtScanner;

import java.util.ArrayList;
import java.util.List;

public class calleeVisitor extends CtScanner {
    private List<CtMethod> calleesPool;
    private List<CtMethod> callees = new ArrayList<>();

    public List<CtMethod> getCallees() {
        return callees;
    }

    public calleeVisitor(List<CtMethod> pool) {
        calleesPool = pool;
        if(pool == null) {
            calleesPool = new ArrayList<>();
        }
    }

    @Override
    public void enter(CtElement element) {}

    @Override
    public void exit(CtElement element) {}

    @Override
    public <T> void visitCtInvocation(CtInvocation<T> invocation) {
        CtExecutableReference r_calling = invocation.getExecutable();

        if(r_calling != null){
            CtExecutable calling = r_calling.getDeclaration();

            if((calling != null) && (calling instanceof CtMethod)) {
                CtMethod targetMethod = (CtMethod) calling;

                if(calleesPool.contains(targetMethod) && !callees.contains(targetMethod)) {
                    callees.add(targetMethod);
                }
            }
        }
        super.visitCtInvocation(invocation);
    }
}
