/* -*- mode: jde; c-basic-offset: 2; indent-tabs-mode: nil -*- */
/*
 * To compiler Arduino code to a hex file, almost all of the code 
 * is copied from Arduino's Source.
 */



/*
  PdePreprocessor - wrapper for default ANTLR-generated parser
  Part of the Wiring project - http://wiring.org.co

  Copyright (c) 2004-05 Hernando Barragan

  Processing version Copyright (c) 2004-05 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  ANTLR-generated parser and several supporting classes written
  by Dan Mosedale via funding from the Interaction Institute IVREA.

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.iitb.vpeub;



import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;


/**
 * Class to initiate conversion of Arduino's Processing code to pure C++
 */
public class PdePreprocessor {
	// stores number of built user-defined function prototypes
	public int prototypeCount = 0;

	// stores number of included library headers written
	// we always write one header: Arduino.h
	public int headerCount = 1;

	// the prototypes that are generated by the preprocessor
	List<String> prototypes;

	// these ones have the .* at the end, since a class name might be at the end
	// instead of .* which would make trouble other classes using this can lop
	// off the . and anything after it to produce a package name consistently.
	List<String> programImports;

	// imports just from the code folder, treated differently
	// than the others, since the imports are auto-generated.
	List<String> codeFolderImports;

	String indent;

	PrintStream stream;
	String program;
	String buildPath;
	// starts as sketch name, ends as main class name
	String name;
	
	Activity activity;
	File buildFolder;

	/**
	 * Setup a new preprocessor.
	 */
	public PdePreprocessor(Activity act) { 
		int tabSize = 4;
		char[] indentChars = new char[tabSize];
		Arrays.fill(indentChars, ' ');
		indent = new String(indentChars);
		activity = act;
		buildFolder =new File(activity.getFilesDir(),activity.getString(R.string.build_folder));
		
		buildPath = buildFolder.getAbsolutePath();
		
	}

	/**
	 * Writes out the head of the C++ code generated for a sketch. 
	 * Called from Compiler.java
	 * @param program the concatenated code from all tabs containing pde-files
	 * @param buildPath the path into which the processed pde-code is to be written
	 * @param name the name of the sketch 
	 * @param codeFolderPackages unused param (leftover from processing)
	 */
	public int writePrefix(String program,
			String sketchName, String codeFolderPackages[]) throws FileNotFoundException {
		this.name = sketchName;

		// if the program ends with no CR or LF an OutOfMemoryError will happen.
		// not gonna track down the bug now, so here's a hack for it:
		// http://dev.processing.org/bugs/show_bug.cgi?id=5
		program += "\n";


		//String importRegexp = "(?:^|\\s|;)(import\\s+)(\\S+)(\\s*;)";
		String importRegexp = "^\\s*#include\\s*[<\"](\\S+)[\">]";
		programImports = new ArrayList<String>();

		String[][] pieces = matchAll(program, importRegexp);

		if (pieces != null)
			for (int i = 0; i < pieces.length; i++)
				programImports.add(pieces[i][1]);  // the package name

		codeFolderImports = new ArrayList<String>();
		//    if (codeFolderPackages != null) {
		//      for (String item : codeFolderPackages) {
		//        codeFolderImports.add(item + ".*");
		//      }
		//    }

		prototypes = prototypes(program);

		// store # of prototypes so that line number reporting can be adjusted
		prototypeCount = prototypes.size();

		// do this after the program gets re-combobulated
		this.program = program;

		// output the code
		File streamFile = new File(buildPath, name + ".cpp");
		stream = new PrintStream(new FileOutputStream(streamFile));

		return headerCount + prototypeCount;
	}




	/**
	 * preprocesses a pde file and writes out a cpp file
	 * @return the classname of the exported cpp file
	 */
	//public String write(String program, String buildPath, String name,
	//                  String extraImports[]) throws java.lang.Exception {
	public String write() throws java.lang.Exception {
		writeProgram(stream, program, prototypes);
		stream.close();

		return name;
	}

	// Write the pde program to the cpp file
	protected void writeProgram(PrintStream out, String program, List<String> prototypes) {
		int prototypeInsertionPoint = firstStatement(program);

		out.print(program.substring(0, prototypeInsertionPoint));
		out.print("#include \"Arduino.h\"\n");    

		// print user defined prototypes
		for (int i = 0; i < prototypes.size(); i++) {
			out.print(prototypes.get(i) + "\n");
		}
		String[] lines = program.substring(0, prototypeInsertionPoint).split("\n", -1);
		out.println("#line " + (lines.length - 1));
		out.print(program.substring(prototypeInsertionPoint));
	}





	public List<String> getExtraImports() {
		return programImports;
	}





	/**
	 * Returns the index of the first character that's not whitespace, a comment
	 * or a pre-processor directive.
	 */
	public int firstStatement(String in) {
		// whitespace
		String p = "\\s+";

		// multi-line and single-line comment
		//p += "|" + "(//\\s*?$)|(/\\*\\s*?\\*/)";
		p += "|(/\\*[^*]*(?:\\*(?!/)[^*]*)*\\*/)|(//.*?$)";

		// pre-processor directive
		p += "|(#(?:\\\\\\n|.)*)";
		Pattern pattern = Pattern.compile(p, Pattern.MULTILINE);

		Matcher matcher = pattern.matcher(in);
		int i = 0;
		while (matcher.find()) {
			if (matcher.start()!=i)
				break;
			i = matcher.end();
		}

		return i;
	}

	/**
	 * Strips comments, pre-processor directives, single- and double-quoted
	 * strings from a string.
	 * @param in the String to strip
	 * @return the stripped String
	 */
	public String strip(String in) {
		// XXX: doesn't properly handle special single-quoted characters
		// single-quoted character
		String p = "('.')";

		// double-quoted string
		p += "|(\"(?:[^\"\\\\]|\\\\.)*\")";

		// single and multi-line comment
		//p += "|" + "(//\\s*?$)|(/\\*\\s*?\\*/)";
		p += "|(//.*?$)|(/\\*[^*]*(?:\\*(?!/)[^*]*)*\\*/)";

		// pre-processor directive
		p += "|" + "(^\\s*#.*?$)";

		Pattern pattern = Pattern.compile(p, Pattern.MULTILINE);
		Matcher matcher = pattern.matcher(in);
		return matcher.replaceAll(" ");
	}

	/**
	 * Removes the contents of all top-level curly brace pairs {}.
	 * @param in the String to collapse
	 * @return the collapsed String
	 */
	private String collapseBraces(String in) {
		StringBuffer buffer = new StringBuffer();
		int nesting = 0;
		int start = 0;

		// XXX: need to keep newlines inside braces so we can determine the line
		// number of a prototype
		for (int i = 0; i < in.length(); i++) {
			if (in.charAt(i) == '{') {
				if (nesting == 0) {
					buffer.append(in.substring(start, i + 1));  // include the '{'
				}
				nesting++;
			}
			if (in.charAt(i) == '}') {
				nesting--;
				if (nesting == 0) {
					start = i; // include the '}'
				}
			}
		}

		buffer.append(in.substring(start));

		return buffer.toString();
	}

	public ArrayList<String> prototypes(String in) {
		in = collapseBraces(strip(in));

		// XXX: doesn't handle ... varargs
		// XXX: doesn't handle function pointers
		Pattern prototypePattern = Pattern.compile("[\\w\\[\\]\\*]+\\s+[&\\[\\]\\*\\w\\s]+\\([&,\\[\\]\\*\\w\\s]*\\)(?=\\s*;)");
		Pattern functionPattern  = Pattern.compile("[\\w\\[\\]\\*]+\\s+[&\\[\\]\\*\\w\\s]+\\([&,\\[\\]\\*\\w\\s]*\\)(?=\\s*\\{)");

		// Find already declared prototypes
		ArrayList<String> prototypeMatches = new ArrayList<String>();
		Matcher prototypeMatcher = prototypePattern.matcher(in);
		while (prototypeMatcher.find())
			prototypeMatches.add(prototypeMatcher.group(0) + ";");

		// Find all functions and generate prototypes for them
		ArrayList<String> functionMatches = new ArrayList<String>();
		Matcher functionMatcher = functionPattern.matcher(in);
		while (functionMatcher.find())
			functionMatches.add(functionMatcher.group(0) + ";");

		// Remove generated prototypes that exactly match ones found in the source file
		for (int functionIndex=functionMatches.size() - 1; functionIndex >= 0; functionIndex--) {
			for (int prototypeIndex=0; prototypeIndex < prototypeMatches.size(); prototypeIndex++) {
				if ((functionMatches.get(functionIndex)).equals(prototypeMatches.get(prototypeIndex))) {
					functionMatches.remove(functionIndex);
					break;
				}
			}
		}

		return functionMatches;
	}

	/* 
	 * Copied from PApplet.java 
	 */
	private String[][] matchAll(String what, String regexp) {
		Pattern p = Pattern.compile(regexp, Pattern.MULTILINE | Pattern.DOTALL);
		Matcher m = p.matcher(what);
		ArrayList<String[]> results = new ArrayList<String[]>();
		int count = m.groupCount() + 1;
		while (m.find()) {
			String[] groups = new String[count];
			for (int i = 0; i < count; i++) {
				groups[i] = m.group(i);
			}
			results.add(groups);
		}
		if (results.isEmpty()) {
			return null;
		}
		String[][] matches = new String[results.size()][count];
		for (int i = 0; i < matches.length; i++) {
			matches[i] = (String[]) results.get(i);
		}
		return matches;
	}


}