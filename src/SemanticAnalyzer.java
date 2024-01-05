import ast.*;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import org.apache.log4j.Logger;
import util.semantics.StructExt;
import util.semantics.TabExt;

import java.util.ArrayList;

public class SemanticAnalyzer extends VisitorAdaptor {
    private final Logger logger = Logger.getLogger(SemanticAnalyzer.class);
    private boolean error = false;
    private Obj currentType;
    private String currentNamespace = null;
    private Obj currentMethod = null;
    private Obj currentClass = null;
    private boolean returnFound = false;
    private int loopDepth = 0;
    private ArrayList<Struct> paramList = null;
    private ArrayList<Designator> dList = null;

    public boolean isError() {
        return error;
    }

    public void reportError(String message, SyntaxNode info) {
        error = true;
        StringBuilder msg = new StringBuilder(message);
        int line = (info == null) ? 0: info.getLine();
        if (line != 0)
            msg.append (" on line ").append(line);
        logger.error(msg.toString());
    }

    public void reportInfo(String message, SyntaxNode info) {
        StringBuilder msg = new StringBuilder(message);
        int line = (info == null) ? 0: info.getLine();
        if (line != 0)
            msg.append (" on line ").append(line);
        logger.info(msg.toString());
    }

    private boolean checkType(String name) {
        return TabExt.find(name).getKind() == Obj.Type ||
                (currentNamespace != null && TabExt.find(currentNamespace + "::" + name).getKind() == Obj.Type);
    }

    @Override
    public void visit(ProgramName programName) {
        programName.obj = TabExt.insert(Obj.Prog, programName.getName(), TabExt.noType);
        if(programName.obj.getKind() != Obj.Prog) {
            reportError("Program not declared??", programName);
        }
        TabExt.openScope();
    }

    @Override
    public void visit(Program program) {
        program.obj = program.getProgramName().obj;
        TabExt.chainLocalSymbols(program.obj);
        TabExt.closeScope();
    }

    @Override
    public void visit(TypeSingle typeSingle) {
        /* start declaration */
        String typeName = typeSingle.getTypename();
        Obj type = TabExt.find(typeName);
        if(type == TabExt.noObj || type.getKind() != Obj.Type) {
            type = TabExt.find(currentNamespace + "::" + typeName);
            if(type == TabExt.noObj || type.getKind() != Obj.Type){
                reportError("Type does not exist.", typeSingle);
                return;
            }
        }

        typeSingle.struct = type.getType();
        currentType = type;
    }

    @Override
    public void visit(TypeNamespace typeNamespace) {
        /* start declaration */
        String typeName = typeNamespace.getTypePrefix() + "::" + typeNamespace.getTypename();
        Obj type = TabExt.find(typeName);
        if(type == TabExt.noObj || type.getKind() != Obj.Type) {
            reportError("Type does not exist.", typeNamespace);
            return;
        }

        typeNamespace.struct = type.getType();
        currentType = type;
    }

    @Override
    public void visit(VarDeclSingle varDeclSingle) {
        if(currentType == null) return;

        boolean isVar = currentClass == null || currentMethod != null;
        Obj oldObj, newObj;

        if(checkType(varDeclSingle.getVar())){
            reportError("Variable declaration " + varDeclSingle.getVar() + " masks type name", varDeclSingle);
            return;
        }

        String name = (currentNamespace != null && currentClass == null && currentMethod == null)?
                       currentNamespace + "::" + varDeclSingle.getVar() : varDeclSingle.getVar();

        oldObj = TabExt.find(name);
        newObj = TabExt.insert(isVar? Obj.Var : Obj.Fld, name, varDeclSingle.getArrayDeclaration().struct);
        if(newObj.equals(oldObj)) {
            reportError("Variable declaration " + name + " masks another symbol name.", varDeclSingle);
            return;
        }

        reportInfo("Declared variable " + name, varDeclSingle);
    }

    @Override
    public void visit(VarType varType) {
        currentType = null;
    }

    @Override
    public void visit(ArrayDeclarationSingle arrayDeclarationSingle) {
        if(currentType == null) {
            return;
        }

        arrayDeclarationSingle.struct = new StructExt(Struct.Array, currentType.getType());
    }

    @Override
    public void visit(ArrayDeclarationEmpty arrayDeclarationEmpty) {
        if(currentType == null) {
            return;
        }

        arrayDeclarationEmpty.struct = currentType.getType();
    }

    @Override
    public void visit(NumberConst numberConst) {
        numberConst.obj = new Obj(Obj.Con, "$constval", TabExt.intType, numberConst.getNumVal(), 0);
    }

    @Override
    public void visit(BoolConst boolConst) {
        boolConst.obj = new Obj(Obj.Con, "$constval", TabExt.boolType, boolConst.getBoolVal()? 1 : 0, 0);
    }

    @Override
    public void visit(CharConst charConst) {
        charConst.obj = new Obj(Obj.Con, "$constval", TabExt.charType, charConst.getCharVal(), 0);
    }

    @Override
    public void visit(ConstDecl constDecl) {
        if(currentType == null) return;

        if(currentType.getType() != constDecl.getConstVals().obj.getType()) {
            reportError("Types not matching", constDecl);
            return;
        }

        String name = constDecl.getName();
        /* no need to check type specifically */
        if(TabExt.find(name) != TabExt.noObj ||
                currentNamespace!= null && TabExt.find(currentNamespace + "::" + name) != TabExt.noObj) {
            reportError("Constant declaration " + constDecl.getName() + " masks another name", constDecl);
            return;
        }

        name = currentNamespace == null? constDecl.getName() : currentNamespace + "::" + constDecl.getName();
        Obj con = TabExt.insert(Obj.Con, name, currentType.getType());
        con.setAdr(constDecl.getConstVals().obj.getAdr());
        reportInfo("Constant declared " + name, constDecl);
    }

    @Override
    public void visit(ConstType constType) {
        currentType = null;
    }

    @Override
    public void visit(ClassNameIdent classNameIdent) {
        Obj newObj;
        String name = classNameIdent.getName();

        if(TabExt.find(name) != TabExt.noObj ||
                currentNamespace!= null && TabExt.find(currentNamespace + "::" + name) != TabExt.noObj) {
            reportError("Class declaration " + classNameIdent.getName() + " masks another name", classNameIdent);
            return;
        }

        name = currentNamespace == null? name : currentNamespace + "::" + name;
        /* same as with constants */
        newObj = TabExt.insert(Obj.Type, name, new StructExt(Struct.Class));
        currentClass = classNameIdent.obj = newObj;
        TabExt.openScope();
        reportInfo("Class declared " + name, classNameIdent);
    }

    @Override
    public void visit(ClassNameExtends classNameExtends) {

    }

    @Override
    public void visit(ClassDecl classDecl) {
        if (currentClass == null) {
            return;
        }

        TabExt.chainLocalSymbols(classDecl.getClassName().obj);
        TabExt.closeScope();
        currentMethod = null;
        currentClass = null;
    }
    @Override
    public void visit(NamespaceInit namespaceInit) {
        currentNamespace = namespaceInit.getName();
        reportInfo("Opening namespace " + currentNamespace, namespaceInit);
    }

    @Override
    public void visit(Namespace namespace) {
        currentNamespace = null;
        reportInfo("Closing namespace " + namespace.getNamespaceInit().getName(), namespace);
    }

    @Override
    public void visit(MethodName methodName) {
        Obj oldObj, newObj;
        if(checkType(methodName.getName())) {
            reportError("Method declaration " + methodName.getName() + " masks type name", methodName);
            return;
        }

        String name = (currentNamespace != null && currentClass == null)? currentNamespace + "::" + methodName.getName() : methodName.getName();
        oldObj = TabExt.find(name);
        newObj = TabExt.insert(Obj.Meth, name, methodName.getMethType().struct);
        if(newObj.equals(oldObj)) {
            reportError("Symbol with name " + name + " already exists in this scope. Error", methodName);
            return;
        }

        currentMethod = methodName.obj = newObj;
        currentType = null;
        TabExt.openScope();
        reportInfo("Opening method " + name + " level " + currentMethod.getLevel(), methodName);
    }

    @Override
    public void visit(MethTypeType methTypeType) {
        methTypeType.struct = methTypeType.getType().struct;
    }

    @Override
    public void visit(MethTypeVoid methTypeVoid) {
        methTypeVoid.struct = TabExt.noType;
    }

    @Override
    public void visit(MethodDecl methodDecl) {
        if(currentMethod == null) {
            return;
        }

        if(!returnFound && methodDecl.getMethodName().getMethType().struct != TabExt.noType) {
            reportError("Return statetement not found in method " + methodDecl.getMethodName().getName() + " declared", methodDecl);
        }

        TabExt.chainLocalSymbols(currentMethod);
        TabExt.closeScope();
        currentMethod = null;
        returnFound = false;
        reportInfo("Closing method", methodDecl);
    }

    @Override
    public void visit(FormParamSingle formParamSingle) {
        if(currentMethod == null) return;

        Obj oldObj, newObj;

        if(checkType(formParamSingle.getParam())) {
            reportError("Method declaration " + formParamSingle.getParam() + " masks type name", formParamSingle);
            return;
        }

        String name = formParamSingle.getParam();
        oldObj = TabExt.find(name);
        newObj = TabExt.insert(Obj.Var, name, formParamSingle.getArrayDeclaration().struct);
        if(newObj.equals(oldObj)) {
            reportError("Parameter with name " + name + " already declared. Error", formParamSingle);
            return;
        }

        currentMethod.setLevel(currentMethod.getLevel() + 1);
        currentType = null;
        reportInfo("Parameter " + name + " declared.", formParamSingle);
    }

    @Override
    public void visit(StatementReturn statementReturn) {
        returnFound = true;
        if(currentMethod.getType() != TabExt.noType) {
            reportError("Invalid return statement.", statementReturn);
        }
    }

    @Override
    public void visit(StatementReturnExpr statementReturnExpr) {
        returnFound = true;
        /* todo:equals might be a bad choice here, assignable to would be better because of classes */
        if(!currentMethod.getType().equals(statementReturnExpr.getExpr().struct)) {
            reportError("Invalid return statement.", statementReturnExpr);
        }
    }

    @Override
    public void visit(StatementPrint statementPrint) {
        Expr expr = statementPrint.getExpr();
        if(expr.struct != TabExt.intType && expr.struct != TabExt.charType && expr.struct != TabExt.boolType) {
            reportInfo("Unsupported type for print statememnt.", statementPrint);
        }
    }

    @Override
    public void visit(StatementPrintNumber statementPrintNumber) {
        Expr expr = statementPrintNumber.getExpr();
        if(expr.struct != TabExt.intType && expr.struct != TabExt.charType && expr.struct != TabExt.boolType) {
            reportInfo("Unsupported type for print statememnt.", statementPrintNumber);
        }
    }

    @Override
    public void visit(StatementRead statementRead)  {
        Designator designator = statementRead.getDesignator();
        Struct type = designator.obj.getType();
        if(TabExt.isNotAssignable(designator.obj)) {
            reportError("Designator " + designator.obj.getName() + " must be assignable.", statementRead);
            return;
        }

        if(type != TabExt.intType && type != TabExt.charType && type != TabExt.boolType) {
            reportError("Unsupported type for read statement.", statementRead);
        }
    }

    @Override
    public void visit(StatementIf statementIf) {
        if(statementIf.getCondition().struct != TabExt.boolType) {
            reportError("Condition for if statement must be boolean.", statementIf);
        }
    }

    @Override
    public void visit(StatementIfElse statementIfElse) {
        if(statementIfElse.getCondition().struct != TabExt.boolType) {
            reportError("Condition for if statement must be boolean.", statementIfElse);
        }
    }

    @Override
    public void visit(ForInit forInit) {
        loopDepth++;
    }

    /* loop ends only when all statements are matched, not only the header */
    @Override
    public void visit(StatementFor statementFor) {
        loopDepth--;
    }

    @Override
    public void visit(ForHeader forHeader) {
        ForCondition forCondition = forHeader.getForCondition();
        if(forCondition instanceof ForConditionSingle && ((ForConditionSingle)forCondition).getCondFact().struct != TabExt.boolType)  {
            reportError("Condition must be of type bool", forHeader);
        }
    }

    @Override
    public void visit(StatementBreak statementBreak) {
        if(loopDepth == 0) {
            reportError("Break statement not in loop", statementBreak);
        }
    }

    @Override
    public void visit(StatementContinue statementContinue) {
        if(loopDepth == 0) {
            reportError("Continue statement not in loop", statementContinue);
        }
    }

    @Override
    public void visit(DesignatorBaseIdent designatorBaseIdent) {
        designatorBaseIdent.obj = TabExt.find(designatorBaseIdent.getVar());
        if((designatorBaseIdent.obj == TabExt.noObj)) {
            if(currentNamespace != null) {
                String namePrefix = currentNamespace + "::";
                designatorBaseIdent.obj = TabExt.find(namePrefix + designatorBaseIdent.getVar());
            }

            if(designatorBaseIdent.obj == TabExt.noObj) {
                reportError("Designator not found " + designatorBaseIdent.getVar(), designatorBaseIdent);
                return;
            }
        }

        reportInfo("Caught designator " + designatorBaseIdent.getVar(), designatorBaseIdent);
    }

    @Override
    public void visit(DesignatorBaseNamespace designatorBaseNamespace) {
        designatorBaseNamespace.obj = TabExt.find(designatorBaseNamespace.getNameSpace() + "::" + designatorBaseNamespace.getVar());
        if(designatorBaseNamespace.obj.equals(TabExt.noObj)) {
            reportError("Designator not found " + designatorBaseNamespace.getVar(), designatorBaseNamespace);
            return;
        }
        reportInfo("Caught designator " + designatorBaseNamespace.getVar(), designatorBaseNamespace);
    }

    @Override
    public void visit(DesignatorSuffixArray designatorSuffixArray) {
        Designator designator = designatorSuffixArray.getDesignator();
        Expr expr = designatorSuffixArray.getExpr();

        if(designator.obj.getType().getKind() != Struct.Array) {
            reportError("Designator " + designator.obj.getName() + " not an array.", designatorSuffixArray);
            designatorSuffixArray.obj = TabExt.noObj;
            return;
        }

        if(!expr.struct.equals(TabExt.intType)) {
            reportError("Expr " + designator.obj.getName() + " must be of type int.", designatorSuffixArray);
            designatorSuffixArray.obj = TabExt.noObj;
            return;
        }

        designatorSuffixArray.obj = new Obj(Obj.Elem, designator.obj.getName(), designatorSuffixArray.getDesignator().obj.getType().getElemType());
    }

    @Override
    public void visit(DesignatorStatementIncrement designatorStatementIncrement) {
        Designator designator = designatorStatementIncrement.getDesignator();
        if(designator.obj == TabExt.noObj || designator.obj.getType() != TabExt.intType) {
            reportError("Type must be int", designatorStatementIncrement);
            return;
        }

        reportInfo("Increment for variable " + designatorStatementIncrement.getDesignator().obj.getName(), designatorStatementIncrement);
    }

    @Override
    public void visit(DesignatorStatementDecrement designatorStatementDecrement) {
        Designator designator = designatorStatementDecrement.getDesignator();
        if(designator.obj == TabExt.noObj || designator.obj.getType() != TabExt.intType) {
            reportError("Type must be int", designatorStatementDecrement);
            return;
        }

        reportInfo("Decrement for variable " + designatorStatementDecrement.getDesignator().obj.getName(), designatorStatementDecrement);
    }

    @Override
    public void visit(DesignatorStatementAssignExpr designatorStatementAssignExpr) {
        Designator designator = designatorStatementAssignExpr.getDesignator();
        if(TabExt.isNotAssignable(designator.obj)) {
            reportError("Designator must be assignable ", designatorStatementAssignExpr);
            return;
        }

        if(!designator.obj.getType().compatibleWith(designatorStatementAssignExpr.getExpr().struct)) {
            reportError("Types not compatible", designatorStatementAssignExpr);
            return;
        }

        reportInfo("Assign found for " + designatorStatementAssignExpr.getDesignator().obj.getName(), designatorStatementAssignExpr);
    }

    @Override
    public void visit(DesignatorStatementCall designatorStatementCall) {
        Designator designator = designatorStatementCall.getDesignator();
        if(!TabExt.checkParams(designator.obj, paramList)) {
            reportError("Error calling method " + designator.obj.getName(), designatorStatementCall);
            paramList = null;
            return;
        }

        paramList = null;
        reportInfo("Calling method " + designator.obj.getName(), designatorStatementCall);
    }

    @Override
    public void visit(DesignatorStatementUnpack designatorStatementUnpack) {
        Struct designator1Type = designatorStatementUnpack.getDesignator().obj.getType();
        Struct designator2Type = designatorStatementUnpack.getDesignator1().obj.getType();

        if(designator1Type.getKind() != Struct.Array || designator2Type.getKind() != Struct.Array) {
            reportError("Two rightmost designators must be arrays", designatorStatementUnpack);
            dList = null;
            return;
        }

        if(!designator2Type.getElemType().assignableTo(designator1Type.getElemType())) {
            reportError("Two array types not assignable", designatorStatementUnpack);
            dList = null;
            return;
        }


        for(Designator d : dList) {
            if(d == null) {
                continue;
            }

            if(TabExt.isNotAssignable(d.obj)) {
                reportError("Designator " + d.obj.getName() + " must be assignable", designatorStatementUnpack);
                dList = null;
                return;
            }

            Struct dType = d.obj.getType();
            if(!designator2Type.getElemType().assignableTo(dType)) {
                reportError("Type mismatch for " + d.obj.getName(), designatorStatementUnpack);
                dList = null;
                return;
            }
        }

        dList = null;
        reportInfo("Unpack statement OK", designatorStatementUnpack);
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
    public void visit(DesignatorListEmpty designatorListEmpty) {
        dList = new ArrayList<>();
    }

    @Override
    public void visit(FactorConstVals factorConstVals) {
        factorConstVals.struct = factorConstVals.getConstVals().obj.getType();
    }

    @Override
    public void visit(FactorDesignator factorDesignator) {
        factorDesignator.struct = factorDesignator.getDesignator().obj.getType();
    }

    @Override
    public void visit(FactorExpr factorExpr) {
        factorExpr.struct = factorExpr.getExpr().struct;
    }

    @Override
    public void visit(FactorNewArray factorNewArray) {
        if(factorNewArray.getExpr().struct != TabExt.intType) {
            reportError("Expr must be of type int", factorNewArray);
            factorNewArray.struct = TabExt.noType;
            return;
        }

        reportInfo("current type is uhh " + currentType.getName(), factorNewArray);
        factorNewArray.struct = new StructExt(Struct.Array, currentType.getType());
        currentType = null;
    }

    @Override
    public void visit(FactorCall factorCall) {
        Designator designator = factorCall.getDesignator();
        if(!TabExt.checkParams(designator.obj, paramList)) {
            reportError("Error calling method " + designator.obj.getName(), factorCall);
            paramList = null;
            return;
        }

        paramList = null;
        factorCall.struct = designator.obj.getType();
        reportInfo("Calling method " + designator.obj.getName(), factorCall);
    }

    @Override
    public void visit(MethodCallNoPars methodCallNoPars) {
        paramList = new ArrayList<>();
    }

    @Override
    public void visit(ActualParsSingle actualParsSingle) {
        paramList = new ArrayList<>();
        paramList.add(actualParsSingle.getExpr().struct);
    }

    @Override
    public void visit(ActualParsList actualParsList) {
        paramList.add(actualParsList.getExpr().struct);
    }

    @Override
    public void visit(TermFactor termFactor) {
        termFactor.struct = termFactor.getFactor().struct;
    }

    @Override
    public void visit(TermMulOp termMulOp) {
        if(!termMulOp.getFactor().struct.equals(TabExt.intType) || !termMulOp.getTerm().struct.equals(TabExt.intType)) {
            reportError("Incompatible types ", termMulOp);
            termMulOp.struct = TabExt.noType;
            return;
        }

        reportInfo("Mul!!", termMulOp);
        termMulOp.struct = termMulOp.getFactor().struct;
    }

    @Override
    public void visit(ExprTerm exprTerm) {
        exprTerm.struct = exprTerm.getTerm().struct;
    }

    @Override
    public void visit(ExprAddop exprAddop) {
        if(!exprAddop.getTerm().struct.equals(TabExt.intType) || !exprAddop.getExpr().struct.equals(TabExt.intType)) {
            reportError("Incompatible types ", exprAddop);
            exprAddop.struct = TabExt.noType;
            return;
        }

        reportInfo("Add!!", exprAddop);
        exprAddop.struct = TabExt.intType;
    }

    @Override
    public void visit(ExprMinusTerm exprMinusTerm) {
       if(!exprMinusTerm.getTerm().struct.equals(TabExt.intType)) {
           reportError("Incompatible types ", exprMinusTerm);
           exprMinusTerm.struct = TabExt.noType;
           return;
       }

       exprMinusTerm.struct = TabExt.intType;
    }

    @Override
    public void visit(ExprAddopMinus exprAddopMinus) {
        if(!exprAddopMinus.getTerm().struct.equals(TabExt.intType) || !exprAddopMinus.getExpr().struct.equals(TabExt.intType)) {
            reportError("Incompatible types ", exprAddopMinus);
            exprAddopMinus.struct = TabExt.noType;
            return;
        }

        exprAddopMinus.struct = TabExt.intType;
    }

    @Override
    public void visit(ConditionSingle conditionSingle) {
        conditionSingle.struct = conditionSingle.getCondTerm().struct;
    }

    @Override
    public void visit(ConditionList conditionList) {
        if(conditionList.getCondition().struct != TabExt.boolType || conditionList.getCondTerm().struct != TabExt.boolType) {
            conditionList.struct = TabExt.noType;
            return;
        }

        conditionList.struct = TabExt.boolType;
    }

    @Override
    public void visit(CondTermSingle condTermSingle) {
        condTermSingle.struct = condTermSingle.getCondFact().struct;
    }

    @Override
    public void visit(CondTermList condTermList) {
        if(condTermList.getCondTerm().struct != TabExt.boolType || condTermList.getCondFact().struct != TabExt.boolType) {
            condTermList.struct = TabExt.noType;
            return;
        }

        condTermList.struct = TabExt.boolType;
    }

    @Override
    public void visit(CondFactExprSingle condFactExprSingle) {
        if(condFactExprSingle.getExpr().struct != TabExt.boolType) {
            reportError("Condition must be boolean", condFactExprSingle);
            condFactExprSingle.struct = TabExt.noType;
            return;
        }

        condFactExprSingle.struct = TabExt.boolType;
    }

    @Override
    public void visit(CondFactRelExpr condFactRelExpr) {
        Struct struct1 = condFactRelExpr.getExpr().struct;
        Struct struct2 = condFactRelExpr.getExpr1().struct;
        if(!struct1.compatibleWith(struct2)) {
            reportError("Types must be compatible", condFactRelExpr);
            condFactRelExpr.struct = TabExt.noType;
            return;
        }

        RelOp relOp = condFactRelExpr.getRelOp();
        if(struct1.isRefType()) {
            if((relOp instanceof Equal || relOp instanceof NotEqu)) {
                condFactRelExpr.struct = TabExt.boolType;
            } else {
                condFactRelExpr.struct = TabExt.noType;
            }
        } else {
            condFactRelExpr.struct = TabExt.boolType;
        }
    }
}
