import java_cup.runtime.Symbol;
import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;
import util.Log4JUtils;

import java.io.*;

public class MainLexer {
    static {
        DOMConfigurator.configure("config/log4j.xml");
        Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
    }

    public static void main(String[] args) throws IOException {
        Logger log = Logger.getLogger(MainLexer.class);
        Reader br = null;
        try {

            File sourceCode = new File("test/program.mj");
            log.info("Compiling source file: " + sourceCode.getAbsolutePath());

            br = new BufferedReader(new FileReader(sourceCode));

            Yylex lexer = new Yylex(br);
            Symbol currToken = null;
            while ((currToken = lexer.next_token()).sym != sym.EOF) {
                if (currToken != null && currToken.value != null)
                    switch (currToken.sym) {
                        case sym.LOWEQU:
                            System.out.println("LOWEQU");
                            break;
                        case sym.LOGAND:
                            System.out.println("LOGAND");
                            break;
                        case sym.INCREMENT:
                            System.out.println("INCREMENT");
                            break;
                        case sym.CONST:
                            System.out.println("CONST");
                            break;
                        case sym.NAMESPACE:
                            System.out.println("NAMESPACE");
                            break;
                        case sym.NUMCONST:
                            System.out.println("NUMCONST");
                            break;
                        case sym.LOWER:
                            System.out.println("LOWER");
                            break;
                        case sym.LPAREN:
                            System.out.println("LPAREN");
                            break;
                        case sym.SEMI:
                            System.out.println("SEMI");
                            break;
                        case sym.GREATER:
                            System.out.println("GREATER");
                            break;
                        case sym.CONTINUE:
                            System.out.println("CONTINUE");
                            break;
                        case sym.FOR:
                            System.out.println("FOR");
                            break;
                        case sym.MINUS:
                            System.out.println("MINUS");
                            break;
                        case sym.DECREMENT:
                            System.out.println("DECREMENT");
                            break;
                        case sym.RPAREN:
                            System.out.println("RPAREN");
                            break;
                        case sym.STATIC:
                            System.out.println("STATIC");
                            break;
                        case sym.COMMA:
                            System.out.println("COMMA");
                            break;
                        case sym.CLASS:
                            System.out.println("CLASS");
                            break;
                        case sym.DIV:
                            System.out.println("DIV");
                            break;
                        case sym.PLUS:
                            System.out.println("PLUS");
                            break;
                        case sym.ASSIGN:
                            System.out.println("ASSIGN");
                            break;
                        case sym.IF:
                            System.out.println("IF");
                            break;
                        case sym.DOT:
                            System.out.println("DOT");
                            break;
                        case sym.EOF:
                            System.out.println("EOF");
                            break;
                        case sym.RETURN:
                            System.out.println("RETURN");
                            break;
                        case sym.EQUAL:
                            System.out.println("EQUAL");
                            break;
                        case sym.NEW:
                            System.out.println("NEW");
                            break;
                        case sym.error:
                            System.out.println("error");
                            break;
                        case sym.MUL:
                            System.out.println("MUL");
                            break;
                        case sym.MOD:
                            System.out.println("MOD");
                            break;
                        case sym.IDENT:
                            System.out.println("IDENT");
                            break;
                        case sym.BREAK:
                            System.out.println("BREAK");
                            break;
                        case sym.VOID:
                            System.out.println("VOID");
                            break;
                        case sym.ARROW:
                            System.out.println("ARROW");
                            break;
                        case sym.COLON:
                            System.out.println("COLON");
                            break;
                        case sym.LBRACE:
                            System.out.println("LBRACE");
                            break;
                        case sym.ELSE:
                            System.out.println("ELSE");
                            break;
                        case sym.LSQUARE:
                            System.out.println("LSQUARE");
                            break;
                        case sym.READ:
                            System.out.println("READ");
                            break;
                        case sym.CHARCONST:
                            System.out.println("CHARCONST");
                            break;
                        case sym.RSQUARE:
                            System.out.println("RSQUARE");
                            break;
                        case sym.LOGOR:
                            System.out.println("LOGOR");
                            break;
                        case sym.RBRACE:
                            System.out.println("RBRACE");
                            break;
                        case sym.NOTEQU:
                            System.out.println("NOTEQU");
                            break;
                        case sym.BOOLCONST:
                            System.out.println("BOOLCONST");
                            break;
                        case sym.EXTENDS:
                            System.out.println("EXTENDS");
                            break;
                        case sym.PROG:
                            System.out.println("PROG");
                            break;
                        case sym.GREQU:
                            System.out.println("GREQU");
                            break;
                        case sym.PRINT:
                            System.out.println("PRINT");
                            break;
                        default:
                            System.out.println("Unknown Token: " + currToken.toString());
                    }
            }
        }
        finally {
            if (br != null) try { br.close(); } catch (IOException e1) { log.error(e1.getMessage(), e1); }
        }
    }

}
