import ast.*;
import util.codegen.CodeExt;

public class CodeGenerator extends VisitorAdaptor {
    @Override
    public void visit(DesignatorStatementIncrement designatorStatementIncrement) {
            CodeExt.load(designatorStatementIncrement.getDesignator().obj);
            CodeExt.loadConst(1);
            CodeExt.put(CodeExt.add);
    }

    @Override
    public void visit(DesignatorStatementDecrement designatorStatementDecrement) {
            CodeExt.load(designatorStatementDecrement.getDesignator().obj);
            CodeExt.loadConst(-1);
            CodeExt.put(CodeExt.add);
    }

    @Override
    public void visit(ExprMinusTerm exprMinusTerm) {
        CodeExt.put(CodeExt.neg);
    }

    @Override
    public void visit(FactorConstVals factorConstVals) {
        CodeExt.load(factorConstVals.getConstVals().obj);
    }

    @Override
    public void visit(ExprAddop exprAddop) {
        int operation = exprAddop.getAddOp() instanceof Plus? CodeExt.add : CodeExt.sub;
        CodeExt.put(operation);
    }

    @Override
    public void visit(TermMulOp termMulOp) {
        MulOp m = termMulOp.getMulOp();
        if(m instanceof Mul) {
            CodeExt.put(CodeExt.mul);
        } else if(m instanceof  Div) {
            CodeExt.put(CodeExt.div);
        } else {
            CodeExt.put(CodeExt.rem);
        }
    }
}
