package util.codegen;

import rs.etf.pp1.mj.runtime.Code;
import rs.etf.pp1.symboltable.concepts.Obj;
import util.semantics.StructExt;
import util.semantics.TabExt;

import java.util.ArrayList;

public class CodeExt extends Code {
    private static final ArrayList<StructExt> classes = new ArrayList<>();
    private static final ArrayList<Obj> staticInitList = new ArrayList<>();

    public static void addClass(StructExt c) {
        classes.add(c);
        c.setTvfp(dataSize);
        /* change dataSize here!!!*/
        for(Obj m : c.getMembers()) {
            if(m.getKind() != Obj.Meth) continue;
            /* name + -1 + adr */
            dataSize += m.getName().length() + 2;
        }
        /* -2 */
        dataSize++;
    }

    public static void addInitializer(Obj init) {
        staticInitList.add(init);
    }

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
                System.out.println(o.getName());
        }
    }

    public static void init() {
        /* init chr */
        Obj chr = TabExt.find("chr");
        chr.setAdr(pc);
        put(enter);
        put(1);
        put(1);
        put(load_n);
        put(exit);
        put(return_);

        /* init ord */
        Obj ord = TabExt.find("ord");
        ord.setAdr(pc);
        put(enter);
        put(1);
        put(1);
        put(load_n);
        put(exit);
        put(return_);

        /* init len */
        Obj len = TabExt.find("len");
        len.setAdr(pc);
        put(enter);
        put(1);
        put(1);
        put(load_n);
        put(arraylength);
        put(exit);
        put(return_);
    }

    /* write tvfs etc. */
    public static void initClasses() {
       for(StructExt cl : classes) {
           int writePtr = cl.getTvfp();
           for(Obj m : cl.getMembers()) {
               if(m.getKind() != Obj.Meth) continue;

               for(char c : m.getName().toCharArray()) {
                    loadConst(c);
                    put(putstatic);
                    put2(writePtr++);
               }
               loadConst(-1);
               put(putstatic);
               put2(writePtr++);
               loadConst(m.getAdr());
               put(putstatic);
               put2(writePtr++);
           }
           loadConst(-2);
           put(putstatic);
           put2(writePtr);
       }
    }

    public static void initScopes() {
        for(Obj initializer : staticInitList) {
            /* calling void method with no args */
            CodeExt.put(CodeExt.call);
            CodeExt.put2(initializer.getAdr() - CodeExt.pc + 1);
        }
    }
}
