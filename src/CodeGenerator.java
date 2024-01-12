import ast.*;
import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import util.codegen.CodeExt;
import util.semantics.StructExt;
import util.semantics.TabExt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class CodeGenerator extends VisitorAdaptor {
    enum ConditionType {
        ForLoop,
        IfStmnt
    }
    ConditionType conditionType = ConditionType.IfStmnt;
    /* jump to else, should eventually be a stack of lists */
    private Stack<ArrayList<Integer>> elsePatches = new Stack<>();
    /* patch at the end of if statement */
    private Stack<ArrayList<Integer>> statementPatches = new Stack<>();
    private Stack<Integer> beyondPatches = new Stack<>();
    /* for loop stuff */
    private Stack<Integer> conditionJumps = new Stack<>();
    private Stack<Integer> statementJumps = new Stack();
    private Stack<Integer> updationJumps = new Stack<>();
    private Stack<ArrayList<Integer>> beyondJumps = new Stack<>();
    /* unpack statement stuff */
    private ArrayList<Designator> dList = null;
    private final Map<Class, Integer> relOps = new HashMap<>();


    /* i have to patch every && here pretty much, so everything in the list */
    private void OrCondition(SyntaxNode node) {
        /* condition || CondTerm, we need a jump here */
        if(node.getParent() instanceof ConditionList) {
            /* already checked condition earlier, if we are here, we should jump to the StatementHeader */
            CodeExt.putJump(0);
            /* patch every && here, they should not jump to ELSE */
            for(int fixUp : elsePatches.pop()) {
                CodeExt.fixup(fixUp);
            }
            elsePatches.push(new ArrayList<>());
            /* for future patching */
            statementPatches.peek().add(CodeExt.pc - 2);
        }
    }

    @Override
    public void visit(ProgramName programName) {
        /* init universe scope methods */
        CodeExt.init();
        /* init some internal structures of the visitor */
        relOps.put(Equal.class, CodeExt.eq);
        relOps.put(NotEqu.class, CodeExt.ne);
        relOps.put(Greater.class, CodeExt.gt);
        relOps.put(GrEqu.class, CodeExt.ge);
        relOps.put(Lower.class, CodeExt.lt);
        relOps.put(LowEqu.class, CodeExt.le);
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
        if(methodDecl.getMethodName().obj.getType() == TabExt.noType) {
            CodeExt.put(CodeExt.exit);
            CodeExt.put(CodeExt.return_);
        }

        CodeExt.put(CodeExt.trap);
        CodeExt.put(1);

        if(methodDecl.getMethodName().getName().equals("main") && methodDecl.getMethodName().obj.getType() == TabExt.noType) {
            CodeExt.mainPc = methodDecl.getMethodName().obj.getAdr();
        }
    }

    private boolean wasNew(Expr expr) {
        return (expr instanceof  ExprTerm) && (((ExprTerm)expr).getTerm() instanceof TermFactor) &&
                ((TermFactor)((ExprTerm)expr).getTerm()).getFactor() instanceof FactorNewTypeNoPars;
    }

    @Override
    public void visit(StatementReturn statementReturn) {
        CodeExt.put(CodeExt.exit);
        CodeExt.put(CodeExt.return_);
    }

    @Override
    public void visit(StatementReturnExpr statementReturnExpr) {
        CodeExt.put(CodeExt.exit);
        CodeExt.put(CodeExt.return_);
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
        for(int fixUp : elsePatches.pop()) {
            CodeExt.fixup(fixUp);
        }
    }

    @Override
    /* fixup all jumps to the end*/
    public void visit(StatementFor statementFor) {
        /* jump to updation part, end of statement, so pop. */
        CodeExt.putJump(updationJumps.pop());

        for(int fixUp : beyondJumps.pop()) {
            CodeExt.fixup(fixUp);
        }
        conditionJumps.pop();
    }

    @Override
    public void visit(StatementBreak statementBreak) {
        CodeExt.putJump(0);
        beyondJumps.peek().add(CodeExt.pc - 2);
    }

    @Override
    public void visit(StatementContinue statementContinue) {
        CodeExt.putJump(updationJumps.peek());
    }

    @Override
    /* jump to condition check here? */
    public void visit(ForHeader forHeader) {
        CodeExt.putJump(conditionJumps.peek());
        CodeExt.fixup(statementJumps.pop());
    }

    @Override
    public void visit(ForInit forInit) {
        /* used for CondFact, patched at the end? */
        conditionType = ConditionType.ForLoop;
        beyondJumps.push(new ArrayList<>());
    }

    @Override
    public void visit(ForConditionBegin forConditionBegin) {
        conditionJumps.push(CodeExt.pc);
    }

    @Override
    /* jump to statement? skip the i++ stuff etc. */
    public void visit(ForConditionSingle forConditionSingle) {
        CodeExt.putJump(0);
        statementJumps.push(CodeExt.pc - 2);
        /* jump address */
        updationJumps.push(CodeExt.pc);
    }

    @Override
    public void visit(ForConditionEmpty forConditionEmpty) {
        CodeExt.putJump(0);
        statementJumps.push(CodeExt.pc - 2);
        /* jump address */
        updationJumps.push(CodeExt.pc);
    }

    @Override
    public void visit(IfHeader ifHeader) {
        elsePatches.push(new ArrayList<>());
        statementPatches.push(new ArrayList<>());
        conditionType = ConditionType.IfStmnt;
    }

    @Override
    public void visit(StatementHeader statementHeader) {
        /* patch every statement thing now */
        for(int fixUp : statementPatches.pop()) {
            CodeExt.fixup(fixUp);
        }
    }

    @Override
    /* pc points at else clause, add beyond jump, patch the else jump */
    public void visit(ElseHeader elseHeader) {
        CodeExt.putJump(0);
        beyondPatches.push(CodeExt.pc - 2);
        for(int fixUp : elsePatches.pop()) {
            CodeExt.fixup(fixUp);
        }
    }

    @Override
    /* patch the beyond jump */
    public void visit(StatementIfElse statementIfElse) {
        CodeExt.fixup(beyondPatches.pop());
    }

    @Override
    public void visit(DesignatorStatementAssignExpr designatorStatementAssignExpr) {
        CodeExt.store(designatorStatementAssignExpr.getDesignator().obj);

        /* fix this lol? */
        if(wasNew(designatorStatementAssignExpr.getExpr())) {
            Code.load(designatorStatementAssignExpr.getDesignator().obj);
            Code.loadConst(((StructExt)designatorStatementAssignExpr.getDesignator().obj.getType()).getTvfp());
            CodeExt.put(CodeExt.putfield);
            CodeExt.put2(0);
        }
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
    public void visit(DesignatorStatementCall designatorStatementCall) {
        Obj method = designatorStatementCall.getDesignator().obj;
        CodeExt.put(CodeExt.call);
        CodeExt.put2(method.getAdr() - CodeExt.pc + 1);
        if(method.getType() != TabExt.noType) {
            CodeExt.put(CodeExt.pop);
        }
    }

    @Override
    /* todo: fix for class field access */
    public void visit(DesignatorStatementUnpack designatorStatementUnpack) {
        Obj arrDest = designatorStatementUnpack.getDesignator().obj;
        Obj arrSrc = designatorStatementUnpack.getDesignator1().obj;
        /* first compare lengths */
        CodeExt.load(arrDest);
        CodeExt.put(CodeExt.arraylength);
        CodeExt.loadConst(dList.size());
        CodeExt.put(CodeExt.add);
        CodeExt.load(arrSrc);
        CodeExt.put(CodeExt.arraylength);
        CodeExt.putFalseJump(CodeExt.gt, CodeExt.pc + 5);
        CodeExt.put(CodeExt.trap);
        CodeExt.put(2);

        /* store into designator list designators, i is array index also */
        int i;
        for(i = dList.size() - 1; i >= 0; i--) {
            if(dList.get(i) == null) continue;

            CodeExt.load(arrSrc);
            CodeExt.loadConst(i);
            CodeExt.load(new Obj(Obj.Elem, "$elem", arrSrc.getType().getElemType()));

            CodeExt.store(dList.get(i).obj);
        }
        int beyondFix;
        int conditionPc;
        int statementFix;
        int updationJump;
        i = dList.size();

        /* loop until arraylength of designator 1*/
        /* initial part */
        CodeExt.loadConst(0);
        CodeExt.loadConst(i);
        CodeExt.loadConst(0);
        /* condition */
        conditionPc = CodeExt.pc;
        CodeExt.load(arrDest);
        CodeExt.put(CodeExt.arraylength);
        CodeExt.putFalseJump(CodeExt.lt, 0); /* fixup later i guess*/
        beyondFix = CodeExt.pc - 2;
        CodeExt.putJump(0);
        statementFix = CodeExt.pc - 2;
        /* updation */
        updationJump = CodeExt.pc;
        CodeExt.loadConst(1);
        CodeExt.put(CodeExt.add);
        CodeExt.put(CodeExt.dup_x1);
        CodeExt.put(CodeExt.pop);
        CodeExt.loadConst(1);
        CodeExt.put(CodeExt.add);
        CodeExt.put(CodeExt.dup_x1);
        CodeExt.putJump(conditionPc);
        CodeExt.fixup(statementFix);
        /* statement */
        CodeExt.put(CodeExt.dup2);
        CodeExt.load(arrSrc);
        CodeExt.put(CodeExt.dup_x1);
        CodeExt.put(CodeExt.pop);
        CodeExt.load(new Obj(Obj.Elem, "$elem", arrSrc.getType().getElemType()));
        CodeExt.load(arrDest);
        CodeExt.put(CodeExt.dup_x2);
        CodeExt.put(CodeExt.pop);
        CodeExt.store(new Obj(Obj.Elem, "$elem", arrDest.getType().getElemType()));;
        CodeExt.putJump(updationJump);
        CodeExt.fixup(beyondFix);
        /* after loop */
        CodeExt.put(CodeExt.pop);
        CodeExt.put(CodeExt.pop);
    }

    @Override
    public void visit(DesignatorListEmpty designatorListEmpty) {
        dList = new ArrayList<>();
    }

    @Override
    public void visit(DesignatorListComma designatorListComma) {
        dList.add(null);
    }

    @Override
    public void visit(DesignatorListList designatorListList) {
        dList.add(designatorListList.getDesignator());
    }

    @Override
    public void visit(DesignatorBaseNamespace designatorBaseNamespace) {
        if(!(designatorBaseNamespace.getParent() instanceof DesignatorStatementAssignExpr) &&
                !(designatorBaseNamespace.getParent() instanceof  FactorCall) &&
                !(designatorBaseNamespace.getParent() instanceof  DesignatorStatementCall) &&
                !(designatorBaseNamespace.getParent() instanceof  DesignatorList) &&
                !(designatorBaseNamespace.getParent() instanceof  DesignatorStatementUnpack)) {
                    CodeExt.load(designatorBaseNamespace.obj);
        }
    }

    @Override
    /* only load if not doing store later */
    public void visit(DesignatorBaseIdent designatorBaseIdent) {
        if(!(designatorBaseIdent.getParent() instanceof DesignatorStatementAssignExpr) &&
            !(designatorBaseIdent.getParent() instanceof  FactorCall) &&
            !(designatorBaseIdent.getParent() instanceof  DesignatorStatementCall) &&
            !(designatorBaseIdent.getParent() instanceof  DesignatorList) &&
            !(designatorBaseIdent.getParent() instanceof  DesignatorStatementUnpack) &&
            !(designatorBaseIdent.getParent() instanceof  StatementRead)) {
                CodeExt.load(designatorBaseIdent.obj);
        }
    }

    @Override
    public void visit(DesignatorSuffixArray designatorSuffixArray) {
        if(!(designatorSuffixArray.getParent() instanceof DesignatorStatementAssignExpr) &&
                !(designatorSuffixArray.getParent() instanceof  FactorCall) &&
                !(designatorSuffixArray.getParent() instanceof  DesignatorStatementCall) &&
                !(designatorSuffixArray.getParent() instanceof  DesignatorList) &&
                !(designatorSuffixArray.getParent() instanceof  DesignatorStatementUnpack) &&
                !(designatorSuffixArray.getParent() instanceof  StatementRead)) {
                    CodeExt.load(designatorSuffixArray.obj);
        }
    }

    @Override
    public void visit(DesignatorSuffixDot designatorSuffixDot) {
        /* should eventually be changed */
        if(!(designatorSuffixDot.getParent() instanceof DesignatorStatementAssignExpr) &&
                !(designatorSuffixDot.getParent() instanceof  FactorCall) &&
                !(designatorSuffixDot.getParent() instanceof  DesignatorStatementCall) &&
                !(designatorSuffixDot.getParent() instanceof  DesignatorList) &&
                !(designatorSuffixDot.getParent() instanceof  DesignatorStatementUnpack) &&
                !(designatorSuffixDot.getParent() instanceof  StatementRead)) {
            CodeExt.load(designatorSuffixDot.obj);
        }
        /* but what if its a method call, get tvfp probably, will see how that works out ?? */
    }

    @Override
    public void visit(ConditionSingle conditionSingle) {
        OrCondition(conditionSingle);
    }

    @Override
    public void visit(ConditionList conditionList) {
        OrCondition(conditionList);
    }

    @Override
    public void visit(CondFactRelExpr condFactRelExpr) {
       /* put false jump to somewhere, no idea where, then patch it after the statement is done */
        int op = relOps.get(condFactRelExpr.getRelOp().getClass());
        CodeExt.putFalseJump(op, 0);
        switch(conditionType) {
            case ForLoop:
                beyondJumps.peek().add(CodeExt.pc - 2);
                break;
            case IfStmnt:
                elsePatches.peek().add(CodeExt.pc - 2);
                break;
            default:
        }
    }

    @Override
    public void visit(CondFactExprSingle condFactExprSingle) {
        CodeExt.loadConst(0);
        CodeExt.putFalseJump(CodeExt.ne, 0);
        switch(conditionType) {
            case ForLoop:
                beyondJumps.peek().add(CodeExt.pc - 2);
                break;
            case IfStmnt:
                elsePatches.peek().add(CodeExt.pc - 2);
                break;
        }
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
    public void visit(FactorNewTypeNoPars factorNewTypeNoPars) {
        CodeExt.put(CodeExt.new_);
        CodeExt.put2(factorNewTypeNoPars.struct.getNumberOfFields() * 4);
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
