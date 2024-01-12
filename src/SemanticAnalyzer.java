import ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import org.apache.log4j.Logger;
import util.codegen.CodeExt;
import util.semantics.StructExt;
import util.semantics.TabExt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

public class SemanticAnalyzer extends VisitorAdaptor {
    private final Logger logger = Logger.getLogger(SemanticAnalyzer.class);
    private boolean error = false;
    private Obj currentType;
    private String currentNamespace = null;
    private Obj currentMethod = null;
    private Obj currentClass = null;
    private boolean returnFound = false;
    private boolean staticScope = false;
    private int loopDepth = 0;
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

    private String classDeclCheck(String name, SyntaxNode node) {
        if(TabExt.find(name) != TabExt.noObj ||
                currentNamespace!= null && TabExt.find(currentNamespace + "::" + name) != TabExt.noObj) {
            reportError("Class declaration " + name + " masks another name", node);
            return null;
        }

        name = currentNamespace == null? name : currentNamespace + "::" + name;
        return name;
    }

    private boolean addFormalParam(String param, Struct paramType, SyntaxNode node) {
        Obj oldObj, newObj;
        oldObj = TabExt.find(param);
        newObj = TabExt.insert(Obj.Var, param, paramType);
        if(newObj.equals(oldObj)) {
            reportError("Parameter with name " + param + " already declared. Error", node);
            return false;
        }

        if(currentClass == null || !((StructExt)currentClass.getType()).getInheritedMethods().contains(currentMethod)) {
            currentMethod.setFpPos(currentMethod.getFpPos() + 1);
        }

        return true;
    }

    private void addThis(Designator designator, ArrayList<Struct> paramList) {
        Struct prevType = TabExt.noType;
        if(designator instanceof DesignatorSuffixDot) {
            prevType = ((DesignatorSuffixDot) designator).getDesignator().obj.getType();
        }

        if(prevType.getKind() == StructExt.Class) {
            paramList.add(0, prevType);
        } else if(currentClass != null && currentMethod != null &&
                TabExt.currentScope.getOuter().findSymbol(designator.obj.getName()).equals(designator.obj)) {
            paramList.add(0, currentClass.getType());
        }
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
        Obj main = TabExt.find("main");
        if(main == TabExt.noObj || !main.getType().equals(TabExt.noType) || main.getKind() != Obj.Meth) {
            reportError("Main method not found", program);
        }

        /* cant set main pc here, since i dont know the main address! */
        CodeExt.dataSize = TabExt.currentScope.getnVars();
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

        Obj oldObj, newObj;
        String name;
        boolean isVar;

        if(checkType(varDeclSingle.getVar())){
            reportError("Variable declaration " + varDeclSingle.getVar() + " masks type name", varDeclSingle);
            return;
        }


        if(staticScope) {
            if(currentClass == null) {
                reportError("Cannot declare static variable outside of class scope", varDeclSingle);
                return;
            }

            name = currentClass.getName() + "." + varDeclSingle.getVar();
            isVar = true;
        } else {
            name = (currentNamespace != null && currentClass == null && currentMethod == null)?
                    currentNamespace + "::" + varDeclSingle.getVar() : varDeclSingle.getVar();
            isVar = currentClass == null || currentMethod != null;
        }

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

        String name = classDeclCheck(classNameIdent.getName(), classNameIdent);
        if(name == null) {
            return;
        }

        /* same as with constants */
        newObj = TabExt.insert(Obj.Type, name, new StructExt(Struct.Class));
        ((StructExt)newObj.getType()).setClassName(name);
        currentClass = classNameIdent.obj = newObj;
        reportInfo("Class declared " + name, classNameIdent);
    }

    @Override
    public void visit(ClassNameExtends classNameExtends) {
        Obj newObj;
        Struct parentClass;

        String name = classDeclCheck(classNameExtends.getName(), classNameExtends);
        if(name == null) {
            currentType = null;
            return;
        }

        /* checks for name in TYPE node, see if it works inside a namespace(it should) */
        if(currentType == null) {
            reportError("Parent class not found", classNameExtends);
            return;
        }

        parentClass = currentType.getType();
        if(parentClass.getKind() != Struct.Class) {
            reportError("Cannot inherit a non-class type", classNameExtends);
            currentType = null;
            return;
        }

        newObj = TabExt.insert(Obj.Type, name, new StructExt(Struct.Class));
        newObj.getType().setElementType(parentClass);
        ((StructExt)newObj.getType()).setClassName(name);
        currentClass = classNameExtends.obj = newObj;
        currentType = null;
        reportInfo("Class declared " + name, classNameExtends);
    }

    @Override
    public void visit(StaticOpen staticOpen) {
        staticScope = true;
    }

    @Override
    public void visit(ClassStatics classStatics) {
        reportInfo("Leaving class statics, whether empty or full!", classStatics);
        staticScope = false;
        TabExt.openScope();
        if(currentClass.getType().getElemType() != null && currentClass.getType().getElemType().getKind() == StructExt.Class) {
            Struct parentClass = currentClass.getType().getElemType();
            for(Obj o : parentClass.getMembers()) {
                /* better copy i guess, same with methods */
                Obj addObj = new Obj(o.getKind(), o.getName(), o.getType(), o.getAdr(), o.getLevel());
                TabExt.currentScope.addToLocals(addObj);
                /* copy everything. (DB slides) */
                if(addObj.getKind() == Obj.Meth) {
                    addObj.setFpPos(o.getFpPos());
                    TabExt.openScope();
                    for(Obj local : o.getLocalSymbols()) {
                        Obj addLocal = new Obj(local.getKind(), local.getName(),
                                local.getName().equals("this")? currentClass.getType() : local.getType(),
                                local.getAdr(), local.getLevel());
                        TabExt.currentScope.addToLocals(addLocal);
                    }
                    TabExt.chainLocalSymbols(addObj);
                    TabExt.closeScope();
                    ((StructExt)currentClass.getType()).getInheritedMethods().add(addObj);
                }
            }
        } else {
            TabExt.insert(Obj.Fld, "$tvfp$", TabExt.intType);
        }
    }

    @Override
    public void visit(ClassDecl classDecl) {
        if (currentClass == null) {
            return;
        }

        TabExt.chainLocalSymbols(currentClass.getType());
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
        /* override method, since it was found in current scope */
        if(currentClass != null && oldObj.getLevel() == 1 && oldObj.getKind() == Obj.Meth) {
            /* method already redefined */
            Set<Obj> inheritedMethods = ((StructExt)currentClass.getType()).getInheritedMethods();
            if(!inheritedMethods.contains(oldObj)) {
                reportError("Symbol with name " + name + " already exists in this scope. Error", methodName);
                return;
            }

            if(!oldObj.getType().equals(methodName.getMethType().struct)) {
                reportError("Return type not matching with parent class method " + methodName.getName(), methodName);
                return;
            }

            currentMethod = methodName.obj = oldObj;
        } else {
            /* insert new method */
            newObj = TabExt.insert(Obj.Meth, name, methodName.getMethType().struct);
            if(newObj.equals(oldObj)) {
                reportError("Symbol with name " + name + " already exists in this scope. Error", methodName);
                return;
            }

            currentMethod = methodName.obj = newObj;
            currentMethod.setFpPos(0);
        }

        currentType = null;
        TabExt.openScope();
        if(currentClass != null) addFormalParam("this", currentClass.getType(), methodName);
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
        if(currentClass != null) {
            ((StructExt)currentClass.getType()).getInheritedMethods().remove(currentMethod);
        }
        currentMethod = null;
        returnFound = false;
        reportInfo("Closing method", methodDecl);
    }

    @Override
    public void visit(FormParsEmpty formParsEmpty) {
        if(currentClass != null) {
            Set<Obj> inheritedMethods = ((StructExt)currentClass.getType()).getInheritedMethods();
            if(inheritedMethods.contains(currentMethod) && currentMethod.getFpPos() != 1) {
                reportError("Cannot match formal parameters for method override.", formParsEmpty);
            }
        }
    }

    @Override
    public void visit(FormParsList formParsList) {
        if(currentClass != null && ((StructExt)currentClass.getType()).getInheritedMethods().contains(currentMethod)) {
           /* check number and types of parameters */
            if(TabExt.currentScope().getnVars() != currentMethod.getFpPos()) {
                reportError("Cannot match formal parameters for method override.", formParsList);
            }

            for(Iterator<Obj> iteratorMeth = currentMethod.getLocalSymbols().iterator(),
                iteratorScope = TabExt.currentScope.getLocals().symbols().iterator();
                iteratorScope.hasNext() && iteratorMeth.hasNext();) {
                Obj methParam = iteratorMeth.next();
                Obj scopeParam = iteratorScope.next();
                if(!methParam.getType().equals(scopeParam.getType())) {
                    reportError("Cannot match formal parameters for method override.", formParsList);
                    return;
                }
            }
        }
    }

    @Override
    public void visit(FormParamSingle formParamSingle) {
        if(currentMethod == null) return;

        if(checkType(formParamSingle.getParam())) {
            reportError("Parameter declaration " + formParamSingle.getParam() + " masks type name", formParamSingle);
            return;
        }

        String name = formParamSingle.getParam();
        if(!addFormalParam(name, formParamSingle.getArrayDeclaration().struct, formParamSingle)) {
            return;
        }

        currentType = null;
        reportInfo("Parameter " + name + " declared. ", formParamSingle);
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
        if(currentMethod == null) return;
        returnFound = true;
        if(!statementReturnExpr.getExpr().struct.assignableTo(currentMethod.getType())) {
            reportError("Invalid return statement.", statementReturnExpr);
        }
    }

    @Override
    public void visit(StatementPrint statementPrint) {
        Expr expr = statementPrint.getExpr();
        if(expr.struct != TabExt.intType && expr.struct != TabExt.charType && expr.struct != TabExt.boolType) {
            reportError("Unsupported type for print statememnt.", statementPrint);
        }
    }

    @Override
    public void visit(StatementPrintNumber statementPrintNumber) {
        Expr expr = statementPrintNumber.getExpr();
        if(expr.struct != TabExt.intType && expr.struct != TabExt.charType && expr.struct != TabExt.boolType) {
            reportError("Unsupported type for print statememnt.", statementPrintNumber);
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
    /* todo:test this, and write tomorrow if needed */
    public void visit(DesignatorBaseIdent designatorBaseIdent) {
        designatorBaseIdent.obj = TabExt.find(designatorBaseIdent.getVar());

        /* see if a local var with the given name exists */
        if(designatorBaseIdent.obj.getLevel() == 1) {
            reportInfo("Caught designator " + designatorBaseIdent.obj.getName(), designatorBaseIdent);
            return;
        }

        /* see if a static var with the name exists */
        if(currentClass != null) {
            for(Struct currentType = currentClass.getType(); currentType != null; currentType = currentType.getElemType()) {
                String namePrefix =  ((StructExt)currentType).getClassName() + ".";
                designatorBaseIdent.obj = TabExt.find(namePrefix + designatorBaseIdent.getVar());
                if(designatorBaseIdent.obj != TabExt.noObj) {
                    reportInfo("Caught designator " + designatorBaseIdent.obj.getName(), designatorBaseIdent);
                    return;
                }
            }
        }

        /* find a global??? var */
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

        reportInfo("Caught designator " + designatorBaseIdent.obj.getName(), designatorBaseIdent);

    }

    @Override
    public void visit(DesignatorBaseNamespace designatorBaseNamespace) {
        /* should return class obj if we are accessing a static */
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
    /* i only need to grab the type and search for the field, since this only exists for classes */
    public void visit(DesignatorSuffixDot designatorSuffixDot) {
        Obj designatorObj = designatorSuffixDot.getDesignator().obj;
        if(designatorObj.getType().getKind() != StructExt.Class) {
            reportError("Left side designator must be a type.", designatorSuffixDot);
            designatorSuffixDot.obj = TabExt.noObj;
            return;
        }

        /* accessing a static variable, probably. */
        if(designatorObj.getKind() == Obj.Type) {
            for(Struct currentType = designatorObj.getType(); currentType != null; currentType = currentType.getElemType()) {
                String namePrefix = ((StructExt)currentType).getClassName() + ".";
                designatorSuffixDot.obj = TabExt.find(namePrefix + designatorSuffixDot.getVar());
                if(designatorSuffixDot.obj != TabExt.noObj) {
                    reportInfo("Caught designator " + designatorSuffixDot.getVar(), designatorSuffixDot);
                    return;
                }
            }
        }

        /* this can only be used in methods, so search outer scope for this. */
        if(currentClass != null && designatorObj.getType().equals(currentClass.getType())) {
            designatorSuffixDot.obj = TabExt.currentScope.getOuter().findSymbol(designatorSuffixDot.getVar());
            if(designatorSuffixDot.obj != null) {
                reportInfo("Caught designator " + designatorSuffixDot.getVar(), designatorSuffixDot);
                return;
            }
        }

        /* find the field */
        for(Obj field : designatorObj.getType().getMembers()) {
            if(field.getName().equals(designatorSuffixDot.getVar())) {
                designatorSuffixDot.obj = field;
                reportInfo("Caught designator " + designatorSuffixDot.getVar(), designatorSuffixDot);
                return;
            }
        }

        reportError("Could not find designator " + designatorSuffixDot.getVar(), designatorSuffixDot);
        designatorSuffixDot.obj = TabExt.noObj;
    }

    @Override
    public void visit(DesignatorStatementIncrement designatorStatementIncrement) {
        Designator designator = designatorStatementIncrement.getDesignator();
        if(TabExt.isNotAssignable(designator.obj)) {
            reportError("Designator not assignable ", designatorStatementIncrement);
            return;
        }

        if(designator.obj == TabExt.noObj || designator.obj.getType() != TabExt.intType) {
            reportError("Type must be int", designatorStatementIncrement);
            return;
        }

        reportInfo("Increment for variable " + designatorStatementIncrement.getDesignator().obj.getName(), designatorStatementIncrement);
    }

    @Override
    public void visit(DesignatorStatementDecrement designatorStatementDecrement) {
        Designator designator = designatorStatementDecrement.getDesignator();
        if(TabExt.isNotAssignable(designator.obj)) {
            reportError("Designator not assignable ", designatorStatementDecrement);
            return;
        }

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

        Struct designatorStruct = designator.obj.getType();
        Struct exprStruct = designatorStatementAssignExpr.getExpr().struct;

        if(!exprStruct.assignableTo(designatorStruct)){
            reportError("Types not compatible", designatorStatementAssignExpr);
            return;
        }

        reportInfo("Assign found for " + designatorStatementAssignExpr.getDesignator().obj.getName(), designatorStatementAssignExpr);
    }

    @Override
    public void visit(DesignatorStatementCall designatorStatementCall) {
        Designator designator = designatorStatementCall.getDesignator();
        addThis(designator, designatorStatementCall.getMethodCall().arraylist);

        if(!TabExt.checkParams(designator.obj, designatorStatementCall.getMethodCall().arraylist)) {
            reportError("Error calling method " + designator.obj.getName(), designatorStatementCall);
            return;
        }

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
    public void visit(FactorNewTypeNoPars factorNewTypeNoPars) {
        factorNewTypeNoPars.struct = factorNewTypeNoPars.getType().struct;
        currentType = null;
    }

    @Override
    public void visit(FactorCall factorCall) {
        Designator designator = factorCall.getDesignator();
        addThis(designator, factorCall.getMethodCall().arraylist);
        /* if current class is not null, and we are calling a class method. Or if we had a previous designator which is a class */
        if(!TabExt.checkParams(designator.obj, factorCall.getMethodCall().arraylist)) {
            reportError("Error calling method " + designator.obj.getName(), factorCall);
            factorCall.struct = TabExt.noType;
            return;
        }

        factorCall.struct = designator.obj.getType();
        reportInfo("Calling method " + designator.obj.getName(), factorCall);
    }

    @Override
    public void visit(MethodCallNoPars methodCallNoPars) {
        methodCallNoPars.arraylist = new ArrayList<Struct>();
    }

    @Override
    public void visit(MethodCallPars methodCallPars) {
        methodCallPars.arraylist = methodCallPars.getActualPars().arraylist;
    }

    @Override
    public void visit(ActualParsSingle actualParsSingle) {
        actualParsSingle.arraylist = new ArrayList<Struct>();
        actualParsSingle.arraylist.add(actualParsSingle.getExpr().struct);
    }

    @Override
    public void visit(ActualParsList actualParsList) {
        actualParsList.arraylist = actualParsList.getActualPars().arraylist;
        actualParsList.arraylist.add(actualParsList.getExpr().struct);
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
