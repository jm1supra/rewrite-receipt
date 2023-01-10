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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
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
    private static final String FOUND = "found";

    @Override
    public String getDisplayName() {
        return "Set methods static when not accessing instance data";
    }

    @Override
    public String getDescription() {
        return "Non-overridable methods (private or final) methods that don't access instance data should be static.";
    }

    @NonNull
    String fullyQualifiedClassName;

    @JsonCreator
    public SetMethodsStatic(@NonNull @JsonProperty("fullyQualifiedClassName") String fullyQualifiedClassName) {
        this.fullyQualifiedClassName = fullyQualifiedClassName;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration m = super.visitMethodDeclaration(method, ctx);

                //ignore if method is already static
                if (m.hasModifier(J.Modifier.Type.Static)) {
                    return m;
                }

                //ignore if it's non-private , non-final
                if(!(m.hasModifier(J.Modifier.Type.Private) || m.hasModifier(J.Modifier.Type.Final))) {
                    return m;
                }

                boolean nonStaticAccessFound = getCursor().getMessage(FOUND) == null;
                if(nonStaticAccessFound) {
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
                for(Statement s: classDecl.getBody().getStatements()) {
                    if(s instanceof J.VariableDeclarations) {
                        J.VariableDeclarations vd = (J.VariableDeclarations)s;
                        if(!vd.hasModifier(J.Modifier.Type.Static)) {
                            Set<String> set = c.getMessage(VAR, new HashSet<>());
                            set.addAll(vd
                                    .getVariables()
                                    .stream()
                                    .map(J.VariableDeclarations.NamedVariable::getSimpleName)
                                    .collect(Collectors.toList()));
                            c.putMessage(VAR, set);
                        }
                    }
                    else if(s instanceof J.MethodDeclaration) {
                        J.MethodDeclaration md = (J.MethodDeclaration)s;
                        if(!md.hasModifier(J.Modifier.Type.Static)) {
                            Set<JavaType.Method> set = c.getMessage(METHOD, new HashSet<>());
                            if(md.getMethodType() != null)
                                set.add(md.getMethodType());
                            c.putMessage(METHOD, set);
                        }
                    }
                }

                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext executionContext) {
                J.MethodInvocation m = super.visitMethodInvocation(method, executionContext);

                Cursor cdCursor = getCursor().dropParentUntil(parent -> parent instanceof J.ClassDeclaration);
                Cursor mdCursor = this.dropParentUntil(getCursor(), parent -> parent instanceof J.MethodDeclaration);
                if(mdCursor == null)
                    return m;

                Set<JavaType.Method> set = cdCursor.getMessage(METHOD);
                if(set != null && method.getMethodType() != null && set.contains(method.getMethodType())) {
                    mdCursor.putMessage(FOUND, true);
                }
                return m;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier i, ExecutionContext executionContext) {

                Cursor mc = this.dropParentUntil(getCursor(), parent -> parent instanceof J.MethodDeclaration);
                if(mc == null)
                    return i;

                Cursor c = getCursor().dropParentUntil(parent -> parent instanceof J.ClassDeclaration);
                Set<String> set = c.getMessage(VAR);
                if(set != null && set.contains(i.getSimpleName())) {
                    mc.putMessage(FOUND, true);
                }

                return i;
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
