/*
 * Copyright 2021 Sebastian Proksch
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package eu.fasten.analyzer.sourceanalyzer;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.visitor.VoidVisitorWithDefaults;
import java.util.*;
import java.util.stream.Collectors;

public class CommentPrintingVisitor extends VoidVisitorWithDefaults<Object> {

	private String currentClassName = null;
	private String currentMethodName = null;

	@Override
	public void defaultAction(Node n, Object arg) {
		for (Node c : n.getChildNodes()) {

			if (c.getComment().isPresent()) {
				putComment(c.getComment(), (Map<String, Set<String>>) arg);
			}

			c.accept(this, arg);
		}
	}

	private void print(String c) {
		System.out.printf("%s\n", c.trim());
	}

	@Override
	public void defaultAction(NodeList n, Object arg) {
		// ??
	}

	@Override
	public void visit(ClassOrInterfaceDeclaration n, Object arg) {
		String oldClassName = currentClassName;

		currentClassName = n.getNameAsString();
		
		defaultAction(n, arg);

		currentClassName = oldClassName;
	}

	@Override
	public void visit(MethodDeclaration n, Object arg) {

		String oldMethodName = currentMethodName;
		currentMethodName = currentClassName + "." + n.getName() +
			"("+splitParams(n.getParameters())+ ")";

		((Map<String, Set<String>>) arg).putIfAbsent(currentMethodName, new HashSet<>());

		defaultAction(n, arg);
		currentMethodName = oldMethodName;

	}

	@Override
	public void visit(ConstructorDeclaration n, Object arg){
		String oldMethodName = currentMethodName;
		currentMethodName = currentClassName + ".<init>("+splitParams(n.getParameters())+ ")";

		((Map<String, Set<String>>) arg).putIfAbsent(currentMethodName, new HashSet<>());

		defaultAction(n, arg);
		currentMethodName = oldMethodName;
	}

	private String splitParams(final NodeList<Parameter> parameters) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < parameters.size(); i++) {
			Parameter parameter = parameters.get(i);
			var type = parameter.getType().toString();
			if (parameter.getType().isPrimitiveType()) {
				type = parameter.getType().asPrimitiveType().getType().toBoxedType().getName()+"Type";
			}
			result.append(type);
			if (i != parameters.size()-1) {
				result.append(",");
			}
		}
		return result.toString();
	}

	@Override
	public void visit(LineComment n, Object arg) {
		if (n.getComment().isPresent()) {
			putComment(n.getComment(), (Map<String, Set<String>>) arg);
		}

	}

	private void putComment(Optional<Comment> n, Map<String, Set<String>> arg) {
		final var current = arg.getOrDefault(currentMethodName, new HashSet<>());
		current.addAll(Arrays.stream(n.get().getContent().split(",")).collect(Collectors.toList()));
		arg.put(currentMethodName, current);
	}

	@Override
	public void visit(LambdaExpr n, Object arg) {
		// do not traverse?!
	}

}