package util.semantics;

import rs.etf.pp1.symboltable.Tab;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Scope;
import rs.etf.pp1.symboltable.concepts.Struct;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

/* https://rti.etf.bg.ac.rs/rti/ir4ps/predavanja/Projektni%20uzorci/02%20Unikat.pdf */
public class TabExt extends Tab {
    private static final Queue<Obj> globalVars = new LinkedList<>();

    public static Obj arrSrc;
    public static Obj arrDst;
    public static final StructExt noType = new StructExt(Struct.None);
    public static final StructExt intType = new StructExt(Struct.Int);
    public static final StructExt charType = new StructExt(Struct.Char);
    /* todo:problem with code generation, there is a check for Tab.nullType, maybe i dont need this? */
    public static final StructExt nullType = new StructExt(Struct.Class);
    public  static final StructExt boolType = new StructExt(Struct.Bool);
    public static final Obj noObj = new Obj(Obj.Var, "noObj", noType);

    public static Obj getNextGlobal() {
        return globalVars.remove();
    }

    public static void addGlobal(Obj globalVar) {
        globalVars.add(globalVar);
    }

    /* same for now */
    public static void init() {
        currentScope = new Scope(null);
        closeScope();

        Scope universe = currentScope = new Scope(null);

        universe.addToLocals(new Obj(Obj.Type, "int", intType));
        universe.addToLocals(new Obj(Obj.Type, "char", charType));
        universe.addToLocals(new Obj(Obj.Type, "bool", boolType));
        universe.addToLocals(new Obj(Obj.Con, "eol", charType, 10, 0));
        universe.addToLocals(new Obj(Obj.Con, "null", nullType, 0, 0));

        universe.addToLocals(chrObj = new Obj(Obj.Meth, "chr", charType, 0, 0));
        {
            openScope();
            currentScope.addToLocals(new Obj(Obj.Var, "i", intType, 0, 1));
            chrObj.setLocals(currentScope.getLocals());
            chrObj.setFpPos(1);
            closeScope();
        }

        universe.addToLocals(ordObj = new Obj(Obj.Meth, "ord", intType, 0, 0));
        {
            openScope();
            currentScope.addToLocals(new Obj(Obj.Var, "ch", charType, 0, 1));
            ordObj.setLocals(currentScope.getLocals());
            ordObj.setFpPos(1);
            closeScope();
        }


        universe.addToLocals(lenObj = new Obj(Obj.Meth, "len", intType, 0, 0));
        {
            openScope();
            currentScope.addToLocals(new Obj(Obj.Var, "arr", new StructExt(Struct.Array, noType), 0, 1));
            lenObj.setLocals(currentScope.getLocals());
            lenObj.setFpPos(1);
            closeScope();
        }
    }

    public static Obj find(String name) {
        Obj ret = Tab.find(name);
        return  ret == Tab.noObj? TabExt.noObj : ret;
    }

    public static Obj insert(int kind, String name, Struct type) {
        Obj ret = Tab.insert(kind, name, type);
        return  ret == Tab.noObj? TabExt.noObj : ret;
    }

    public static boolean isNotAssignable(Obj obj) {
        return obj.getKind() != Obj.Var && obj.getKind() != Obj.Fld && obj.getKind() != Obj.Elem;
    }

    public static boolean checkParams(Obj meth, ArrayList<Struct> paramList) {
        if(meth.getKind() != Obj.Meth || meth.getFpPos() != paramList.size()) {
            return false;
        }

        for(Obj obj : meth.getLocalSymbols()) {
            if(obj.getAdr() >= paramList.size()) break;

            if(!paramList.get(obj.getAdr()).assignableTo(obj.getType())) {
                return false;
            }
        }

        return true;
    }
}
