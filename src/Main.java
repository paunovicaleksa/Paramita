import java.io.*;
import java.nio.file.Files;

import ast.Visitor;
import java_cup.runtime.Symbol;

import org.apache.log4j.Logger;
import org.apache.log4j.xml.DOMConfigurator;

import ast.Program;
import rs.etf.pp1.symboltable.concepts.Obj;
import rs.etf.pp1.symboltable.concepts.Struct;
import rs.etf.pp1.symboltable.visitors.DumpSymbolTableVisitor;
import util.Log4JUtils;
import rs.etf.pp1.symboltable.*;
import util.codegen.CodeExt;
import util.semantics.TabExt;

public class Main {

	static {
		DOMConfigurator.configure("config/log4j.xml");
		Log4JUtils.instance().prepareLogFile(Logger.getRootLogger());
	}

	private static void initTab() {
		TabExt.init();
	}
	
	public static void main(String[] args) throws Exception {
		
		Logger log = Logger.getLogger(Main.class);

		Reader br = null;
		try {
			if(args.length < 2) throw new IOException();

			File sourceCode = new File(args[0]);
			log.info("Compiling source file: " + sourceCode.getAbsolutePath());
			
			br = new BufferedReader(new FileReader(sourceCode));
			Yylex lexer = new Yylex(br);
			
			MJParser p = new MJParser(lexer);
	        Symbol s = p.parse();  //pocetak parsiranja

	        Program prog = (Program)(s.value);
			/* syntax analysis */
			log.info("\n" + prog.toString(""));
			log.info("===================================");
			if(p.getError()) {
				log.info("Syntax errors detected, aborting.");
				return;
			}
			/* semantic analysis */
			initTab();
			SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
			prog.traverseBottomUp(semanticAnalyzer);
			/* */
			DumpSymbolTableVisitor dumpSymbolTableVisitor = new DumpSymbolTableVisitor();
			TabExt.dump(dumpSymbolTableVisitor);
			if(semanticAnalyzer.isError()) {
				log.error("Semantic errors detected, aborting");
				return;
			}

			/* code generation */
			Visitor codeGenerator = new CodeGenerator();
			prog.traverseBottomUp(codeGenerator);
			File f = new File(args[1]);
			if(f.exists()) f.delete();
			CodeExt.write(Files.newOutputStream(f.toPath()));
		}
		finally {
			if (br != null) try { br.close(); } catch (IOException e1) { log.error(e1.getMessage(), e1); }
		}

	}
	
	
}
