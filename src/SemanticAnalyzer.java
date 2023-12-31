import ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import org.apache.log4j.Logger;

public class SemanticAnalyzer extends VisitorAdaptor {
    private final Logger logger = Logger.getLogger(SemanticAnalyzer.class);
    private boolean error = false;
    private Obj currentType;
    private String currentNamespace = null;
    private Obj currentMethod = null;
    private Obj currentClass = null;
    private boolean returnFound = false;

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

    @Override
    public void visit(ProgramName programName) {
        programName.obj = Tab.insert(Obj.Prog, programName.getName(), Tab.noType);
        Tab.openScope();
    }

    @Override
    public void visit(Program program) {
        Tab.chainLocalSymbols(program.getProgramName().obj);
        Tab.closeScope();
    }

    @Override
    public void visit(TypeSingle typeSingle) {
        /* start declaration */
        String typeName = typeSingle.getTypename();
        Obj type = Tab.find(typeName);
        if(type == Tab.noObj || type.getKind() != Obj.Type) {
            reportError("Type does not exist.", typeSingle);
            return;
        }
        currentType = type;
    }

    @Override
    public void visit(TypeNamespace typeNamespace) {
        /* start declaration */
        String typeName = typeNamespace.getTypePrefix() + "::" + typeNamespace.getTypename();
        Obj type = Tab.find(typeName);
        if(type == Tab.noObj || type.getKind() != Obj.Type) {
            reportError("Type does not exist.", typeNamespace);
            return;
        }
        currentType = type;
    }

    /* TODO:Local var support */
    @Override
    public void visit(VarDeclSingle varDeclSingle) {
        if(currentType == null) return;

        String namePrefix = currentNamespace !=null? currentNamespace + "::" : "";
        String addName = namePrefix + varDeclSingle.getVar();
        /* searches in all scopes, i only need to find local scope same name? */
        Obj oldObj = Tab.find(addName);
        Obj newObj = Tab.insert(Obj.Var, addName, currentType.getType());
        if(newObj.equals(oldObj)) {
            reportError("Error declaring variable " + varDeclSingle.getVar(), varDeclSingle);
            return;
        }

        reportInfo("Variable declared: " + newObj.getName(), varDeclSingle);
    }

    @Override
    public void visit(VarType varType) {
        currentType = null;
    }

    @Override
    public void visit(ArrayDeclarationSingle arrayDeclarationSingle) {
       /* do i need this? */
    }

    @Override
    public void visit(NumberConst numberConst) {
        numberConst.obj = new Obj(Obj.Con, "constval", Tab.intType, numberConst.getNumVal(), 0);
    }

    @Override
    public void visit(BoolConst boolConst) {
        boolConst.obj = new Obj(Obj.Con, "constval", Main.boolType, boolConst.getBoolVal()? 1 : 0, 0);
    }

    @Override
    public void visit(CharConst charConst) {
        charConst.obj = new Obj(Obj.Con, "constval", Tab.charType, charConst.getCharVal(), 0);
    }

    /* TODO:Local var support */
    @Override
    public void visit(ConstDecl constDecl) {
        if(currentType == null) return;

        if(currentType.getType() != constDecl.getConstVals().obj.getType()) {
            reportError("Types not matching", constDecl);
            return;
        }

        String namePrefix = currentNamespace !=null? currentNamespace + "::" : "";
        String addName = namePrefix + constDecl.getName();
        /* searches in all scopes, i only need to find local scope same name? */
        Obj oldObj = Tab.find(addName);
        Obj newObj = Tab.insert(Obj.Con, addName, currentType.getType());
        if(newObj.equals(oldObj)) {
            reportError("Error declaring variable " + addName, constDecl);
            return;
        }

        newObj.setAdr(constDecl.getConstVals().obj.getAdr());
        reportInfo("Constant declared " + newObj.getName(), constDecl);
    }

    @Override
    public void visit(ConstType constType) {
        currentType = null;
    }

    /* TODO:Local var support */
    @Override
    public void visit(ClassNameIdent classNameIdent) {
        String namePrefix = currentNamespace !=null? currentNamespace + "::" : "";
        String addName = namePrefix + classNameIdent.getName();
        /* searches in all scopes, i only need to find local scope same name? */
        Obj oldObj = Tab.find(addName);
        Obj newObj = Tab.insert(Obj.Type, addName, new Struct(Struct.Class));
        if(newObj.equals(oldObj)) {
            reportError("Error declaring class " + addName, classNameIdent);
            return;
        }

        currentClass = classNameIdent.obj = newObj;
        Tab.openScope();
        reportInfo("Class declared " + addName, classNameIdent);
    }

    @Override
    public void visit(ClassNameExtends classNameExtends) {

    }

    @Override
    public void visit(ClassDecl classDecl) {
        if (currentClass == null) {
            return;
        }

        Tab.chainLocalSymbols(classDecl.getClassName().obj);
        Tab.closeScope();
        currentMethod = null;

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
    /* TODO:Local var support */
    public void visit(MethodName methodName) {
        String namePrefix = currentNamespace != null? currentNamespace + "::" : "";
        String name = namePrefix + methodName.getName();
        Obj oldObj = Tab.find(name);
        Obj newObj = Tab.insert(Obj.Meth, name, methodName.getMethType().struct);
        if(newObj.equals(oldObj)) {
            reportError("Symbol with name " + name + " already declared. Error", methodName);
            return;
        }
        currentMethod = methodName.obj = newObj;
        Tab.openScope();
        reportInfo("Opening method " + name, methodName);
    }

    @Override
    public void visit(MethTypeType methTypeType) {
        methTypeType.struct = methTypeType.getType().struct;
    }

    @Override
    public void visit(MethTypeVoid methTypeVoid) {
        methTypeVoid.struct = Tab.noType;
    }

    @Override
    public void visit(MethodDecl methodDecl) {
        if(currentMethod == null) {
            return;
        }

        if(!returnFound && methodDecl.getMethodName().getMethType().struct != Tab.noType) {
            reportError("Return statetement not found in method " + methodDecl.getMethodName().getName() + " declared", methodDecl);
            return;
        }

        Tab.chainLocalSymbols(methodDecl.getMethodName().obj);
        Tab.closeScope();
        currentMethod = null;
        returnFound = false;
        reportInfo("Closing method", methodDecl);
    }

    @Override
    public void visit(StatementReturn statementReturn) {
        returnFound = true;
    }

    @Override
    public void visit(StatementReturnExpr statementReturnExpr) {
        returnFound = true;
    }

    @Override
    public void visit(DesignatorBaseIdent designatorBaseIdent) {
    }

    @Override
    public void visit(DesignatorBaseNamespace designatorBaseNamespace) {

    }
}
