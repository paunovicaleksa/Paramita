import ast.*;
import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;

import org.apache.log4j.Logger;

public class SemanticAnalyzer extends VisitorAdaptor {
    private final Logger logger = Logger.getLogger(SemanticAnalyzer.class);
    private boolean error = false;
    private Obj currentType;

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
        /* close scope? */
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

    @Override
    public void visit(VarDeclSingle varDeclSingle) {
        if(currentType == null) return;
        String varName = varDeclSingle.getVar();
        /* searches in all scopes, i only need to find local scope same name? */
        Obj oldSym = Tab.find(varName);
        Obj newSym = Tab.insert(Obj.Var, varName, currentType.getType());
        /* should i use equals? */
        if(newSym.equals(oldSym)) {
            reportError("Name already declared", varDeclSingle);
            return;
        }

        reportInfo("Variable declared", varDeclSingle);
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

    @Override
    public void visit(ConstDecl constDecl) {
        if(currentType == null) return;

        if(currentType.getType() != constDecl.getConstVals().obj.getType()) {
            reportError("Types not matching", constDecl);
            return;
        }
        /* check if name exists already? */
        Obj oldSym = Tab.find(constDecl.getName());
        Obj newSym = Tab.insert(Obj.Con, constDecl.getName(), currentType.getType());
        /* should i use equals? */
        if(newSym.equals(oldSym)) {
            reportError("Name already declared", constDecl);
            return;
        }

        reportInfo("Constant declared", constDecl);
    }

    @Override
    public void visit(ConstType constType) {
        currentType = null;
    }
}
