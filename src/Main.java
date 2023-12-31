import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import ast.Visitor;
import java_cup.runtime.Symbol;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import ast.Program;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import util.Log4JUtils;
import rs.etf.pp1.symboltable.*;

public class Main {

	static {
		DOMConfigurator.configure("config/log4j.xml");
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}

	public static final Struct boolType = new Struct(Struct.Bool);

	private static void initTab() {
		Tab.init();
		Tab.insert(Obj.Type, "bool", boolType);
	}
	
	public static void main(String[] args) throws Exception {
		
		Logger log = Logger.getLogger(Main.class);

		Reader br = null;
		try {
			File sourceCode = new File("test/program.mj");
			log.info("Compiling source file: " + sourceCode.getAbsolutePath());
			
			br = new BufferedReader(new FileReader(sourceCode));
			Yylex lexer = new Yylex(br);
			
			MJParser p = new MJParser(lexer);
	        Symbol s = p.parse();  //pocetak parsiranja

	        Program prog = (Program)(s.value);
			// ispis sintaksnog stabla
			log.info("\n" + prog.toString(""));
			log.info("===================================");
			if(p.getError()) {
				log.info("Syntax errors detected, aborting.");
				return;
			}
			initTab();
			Visitor semanticAnalyzer = new SemanticAnalyzer();
			prog.traverseBottomUp(semanticAnalyzer);
			// ispis prepoznatih programskih konstrukcija
		}
		finally {
			if (br != null) try { br.close(); } catch (IOException e1) { log.error(e1.getMessage(), e1); }
		}

	}
	
	
}
