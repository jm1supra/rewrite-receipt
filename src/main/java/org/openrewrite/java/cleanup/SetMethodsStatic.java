/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.cleanup;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Value
@EqualsAndHashCode(callSuper = false)
public class SetMethodsStatic extends Recipe {
    private static final String VAR = "var";
    private static final String METHOD = "method";
    private static final String INSTANCE_ACCESS_FOUND = "found";
    private static final String IS_SERIALIZABLE = "isSerializable";

    @Override
    public String getDisplayName() {
        return "Set methods static when not accessing instance data";
    }

    @Override
    public String getDescription() {
        return "Non-overridable methods (private or final) methods that don't access instance data should be static.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDeclaration, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(methodDeclaration, ctx);

                //ignore if method is already static
                if (m.hasModifier(J.Modifier.Type.Static)) {
                    return m;
                }

                //ignore if it's non-private , non-final
                if(!(m.hasModifier(J.Modifier.Type.Private) || m.hasModifier(J.Modifier.Type.Final))) {
                    return m;
                }

                Cursor cdCursor = getCursor().dropParentUntil(parent -> parent instanceof J.ClassDeclaration);

                /*
                ignore following methods if the class implements Serializable
                todo: instead of using simpleName, it would be more accurate to compare method objects
                 */
                if (cdCursor.getMessage(IS_SERIALIZABLE) != null) {
                    switch (m.getSimpleName()) {
                        case "writeObject":
                        case "readObject":
                        case "readObjectNoData":
                            return m;
                        default:
                    }
                }

                //there was no instance data access found
                if(getCursor().getMessage(INSTANCE_ACCESS_FOUND) == null) {
                    //no need to keep the final modifier, since this will create another code smell.
                    List<J.Modifier> list = m.getModifiers().stream()
                            .filter(modifier->!(modifier.getType().equals(J.Modifier.Type.Final)))
                            .collect(Collectors.toList());

                    m = autoFormat(
                            m.withModifiers(
                                    ListUtils.concat(
                                            list,
                                            new J.Modifier(
                                                    Tree.randomId(),
                                                    Space.EMPTY,
                                                    Markers.EMPTY,
                                                    J.Modifier.Type.Static,
                                                    Collections.emptyList()
                                            )
                                    )
                            ), ctx
                    );

                }
                //doAfterVisit(this);
                return m;
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                Cursor c = getCursor();

                //check if the class implements Serializable
                if(classDecl.getImplements() != null && !classDecl.getImplements().isEmpty()) {
                    boolean isSerializable = classDecl.getImplements().stream().filter(fa -> {
                        J.FieldAccess fs = (J.FieldAccess) fa;
                        return fs.getName().getSimpleName().equals("Serializable");
                    }).count() != 0;

                    if(isSerializable)
                        c.putMessage(IS_SERIALIZABLE, true);
                }

                for(Statement s: classDecl.getBody().getStatements()) {
                    if(s instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations)s;
                        if(vd.hasModifier(J.Modifier.Type.Static))
                            continue;

                        Set<String> varSet = c.getMessage(VAR, new HashSet<>());
                        varSet.addAll(vd
                                .getVariables()
                                .stream()
                                .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                                .collect(Collectors.toList()));
                        c.putMessage(VAR, varSet);
                    }
                    else if(s instanceof J.MethodDeclaration) {
                        J.MethodDeclaration md = (J.MethodDeclaration)s;
                        if(md.hasModifier(J.Modifier.Type.Static))
                            continue;

                        Set<JavaType.Method> methodSet = c.getMessage(METHOD, new HashSet<>());
                        if(md.getMethodType() != null)
                            methodSet.add(md.getMethodType());
                        c.putMessage(METHOD, methodSet);
                    }
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(methodInvocation, executionContext);

                Cursor mdCursor = this.dropParentUntil(getCursor(), parent -> parent instanceof J.MethodDeclaration);
                if(mdCursor == null)
                    return m;

                Cursor cdCursor = getCursor().dropParentUntil(parent -> parent instanceof J.ClassDeclaration);

                //check if the method is the instance member of the class
                Set<JavaType.Method> set = cdCursor.getMessage(METHOD);
                if(set != null && m.getMethodType() != null && set.contains(m.getMethodType())) {
                    mdCursor.putMessage(INSTANCE_ACCESS_FOUND, true);
                }
                return m;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext executionContext) {

                Cursor mdCursor = this.dropParentUntil(getCursor(), parent -> parent instanceof J.MethodDeclaration);
                if(mdCursor == null)
                    return identifier;

                //check if the identifier name matches the instance variable of the class
                Cursor cdCursor = getCursor().dropParentUntil(parent -> parent instanceof J.ClassDeclaration);
                Set<String> set = cdCursor.getMessage(VAR);
                if(set != null && set.contains(identifier.getSimpleName())) {
                    mdCursor.putMessage(INSTANCE_ACCESS_FOUND, true);
                }

                return identifier;
            }

            @Nullable
            public Cursor dropParentUntil(Cursor c, Predicate<Object> valuePredicate) {
                Cursor cursor = c;
                while (cursor != null && !valuePredicate.test(cursor.getValue())) {
                    cursor = cursor.getParent();
                }
                return cursor;
            }
        };
    }
}
