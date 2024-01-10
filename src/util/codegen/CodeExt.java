package util.codegen;

import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import util.semantics.StructExt;
import util.semantics.TabExt;

public class CodeExt extends Code {
    public static void load (Obj o) {
        switch (o.getKind()) {

            case Obj.Con:
                if (o.getType() == TabExt.nullType)
                    put(const_n + 0);
                else
                    loadConst(o.getAdr());
                break;

            case Obj.Var:
                if (o.getLevel()==0) { // global variable
                    put(getstatic); put2(o.getAdr());
                    break;
                }
                // local variable
                if (0 <= o.getAdr() && o.getAdr() <= 3)
                    put(load_n + o.getAdr());
                else {
                    put(load); put(o.getAdr());
                }
                break;

            case Obj.Fld:
                put(getfield); put2(o.getAdr());
                break;

            case Obj.Elem:
                if (o.getType().getKind() == StructExt.Char) put(baload);
                else put(aload);
                break;

            default:
                error("Greska: nelegalan operand u Code.load");
        }
    }

}