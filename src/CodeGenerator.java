import ast.*;
import rs.etf.pp1.symboltable.concepts.Obj;
import util.codegen.CodeExt;
import util.semantics.StructExt;
import util.semantics.TabExt;

import java.util.Stack;

public class CodeGenerator extends VisitorAdaptor {
    /* jump to else, should eventually be a stack of lists */
    private Stack<Integer> elsePatches = new Stack<>();
    /* patch at the end of if statement */
    private Stack<Integer> beyondPatches = new Stack<>();

    @Override
    public void visit(ProgramName programName) {
        /* init universe scope methods */
        CodeExt.init();
    }

    @Override
    public void visit(MethodName methodName) {
        methodName.obj.setAdr(CodeExt.pc);
        /* enter method */
        CodeExt.put(CodeExt.enter);
        CodeExt.put(methodName.obj.getFpPos());
        CodeExt.put(methodName.obj.getLocalSymbols().size());
    }

    @Override
    public void visit(MethodDecl methodDecl) {
        /* exit method */
        CodeExt.put(CodeExt.exit);
        CodeExt.put(CodeExt.return_);
        if(methodDecl.getMethodName().getName().equals("main") && methodDecl.getMethodName().obj.getType() == TabExt.noType) {
            CodeExt.mainPc = methodDecl.getMethodName().obj.getAdr();
        }
    }

    @Override
    public void visit(DesignatorStatementCall designatorStatementCall) {
        Obj method = designatorStatementCall.getDesignator().obj;
        CodeExt.put(CodeExt.call);
        CodeExt.put2(method.getAdr() - CodeExt.pc + 1);
        if(method.getType() != TabExt.noType) {
            CodeExt.put(CodeExt.pop);
        }
    }


    @Override
    public void visit(StatementPrintNumber statementPrintNumber) {
        CodeExt.loadConst(statementPrintNumber.getNumber());
        int op = statementPrintNumber.getExpr().struct.getKind() == StructExt.Char? CodeExt.bprint : CodeExt.print;
        CodeExt.put(op);
    }

    @Override
    public void visit(StatementPrint statementPrint) {
        int kind = statementPrint.getExpr().struct.getKind();
        int op = kind == StructExt.Char? CodeExt.bprint : CodeExt.print;
        int width = kind == StructExt.Char || kind == StructExt.Bool? 1 : 5;
        CodeExt.loadConst(width);
        CodeExt.put(op);
    }

    @Override
    public void visit(StatementRead statementRead) {
        int kind = statementRead.getDesignator().obj.getType().getKind();
        int op = kind == StructExt.Char? CodeExt.bread : CodeExt.read;
        CodeExt.put(op);
        CodeExt.store(statementRead.getDesignator().obj);
    }

    @Override
    public void visit(StatementIf statementIf) {
        CodeExt.fixup(elsePatches.pop());
    }

    @Override
    /* pc points at else clause, add beyond jump, patch the else jump */
    public void visit(ElseHeader elseHeader) {
        CodeExt.putJump(0);
        beyondPatches.push(CodeExt.pc - 2);
        CodeExt.fixup(elsePatches.pop());
    }

    @Override
    /* patch the beyond jump */
    public void visit(StatementIfElse statementIfElse) {
        CodeExt.fixup(beyondPatches.pop());
    }

    @Override
    public void visit(DesignatorStatementAssignExpr designatorStatementAssignExpr) {
        CodeExt.store(designatorStatementAssignExpr.getDesignator().obj);
    }

    @Override
    public void visit(DesignatorStatementIncrement designatorStatementIncrement) {
        CodeExt.loadConst(1);
        CodeExt.put(CodeExt.add);
        CodeExt.store(designatorStatementIncrement.getDesignator().obj);
    }

    @Override
    public void visit(DesignatorStatementDecrement designatorStatementDecrement) {
        CodeExt.loadConst(-1);
        CodeExt.put(CodeExt.add);
        CodeExt.store(designatorStatementDecrement.getDesignator().obj);
    }

    @Override
    public void visit(DesignatorBaseNamespace designatorBaseNamespace) {
        if(!(designatorBaseNamespace.getParent() instanceof DesignatorStatementAssignExpr)) {
            CodeExt.load(designatorBaseNamespace.obj);
        }
    }

    @Override
    /* only load if not doing store later */
    public void visit(DesignatorBaseIdent designatorBaseIdent) {
        if(!(designatorBaseIdent.getParent() instanceof DesignatorStatementAssignExpr) &&
            !(designatorBaseIdent.getParent() instanceof  FactorCall) &&
            !(designatorBaseIdent.getParent() instanceof  DesignatorStatementCall)) {
                CodeExt.load(designatorBaseIdent.obj);
        }
    }

    @Override
    public void visit(DesignatorSuffixArray designatorSuffixArray) {
        if((!(designatorSuffixArray.getParent() instanceof DesignatorStatementAssignExpr))) {
            CodeExt.load(designatorSuffixArray.obj);
        }
    }

    @Override
    public void visit(CondFactRelExpr condFactRelExpr) {
       /* put false jump to somewhere, no idea where, then patch it after the statement is done */
    }

    @Override
    public void visit(CondFactExprSingle condFactExprSingle) {
        CodeExt.loadConst(0);
        CodeExt.putFalseJump(CodeExt.ne, 0);
        elsePatches.push(CodeExt.pc - 2);
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
    public void visit(FactorNewArray factorNewArray) {
        int val = factorNewArray.getType().struct.getKind() == StructExt.Char? 0 : 1;
        CodeExt.put(CodeExt.newarray);
        CodeExt.put(val);
    }

    @Override
    public void visit(FactorCall factorCall) {
        Obj method = factorCall.getDesignator().obj;
        CodeExt.put(CodeExt.call);
        CodeExt.put2(method.getAdr() - CodeExt.pc + 1);
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
