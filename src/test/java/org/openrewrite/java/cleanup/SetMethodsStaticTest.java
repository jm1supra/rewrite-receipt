package org.openrewrite.java.cleanup;

import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@SuppressWarnings("ALL")
class SetMethodsStaticTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new SetMethodsStatic());
    }

    @Test
    void Serializable() {
        rewriteRun(
            java(
                """
                    import java.io.*;
                    
                    class Utilities implements java.io.Serializable {
                        private static String magicWord = "magic";
                        private static int magicNumber = 2;
                        private String instantName;
                        
                        private static String getMagicWord() {
                            return magicWord;
                        }
                       
                        private void setName(String name) {
                            this.instantName = name;
                        }
                       
                        private static void setMagicWord(String value) {
                            magicWord = value;
                        }
                        
                        public void setMagicNumber(int value) {
                            magicNumber = value;
                        }
                        
                        private void writeObject(java.io.ObjectOutputStream out) throws IOException {
                        
                        }
                        
                        private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
                        
                        }
                        
                        private void readObjectNoData() throws ObjectStreamException {
                        
                        }
                    }
                    """
            )
        );
    }

    @Test
    void nochange() {
        rewriteRun(
            java(
                """
                    class Utilities {
                        private static String magicWord = "magic";
                        private static int magicNumber = 2;
                        private String instantName;
                        
                        private static String getMagicWord() {
                            return magicWord;
                        }
                       
                        private void setName(String name) {
                            this.instantName = name;
                        }
                       
                        private static void setMagicWord(String value) {
                            magicWord = value;
                        }
                        
                        public void setMagicNumber(int value) {
                            magicNumber = value;
                        }
                    }
                    """
            )
        );
    }

    @Test
    void privateMethodWithoutInstanceData() {
        rewriteRun(
            java(
                """
                    class Utilities {
                        private static String magicWord = "magic";
                                             
                        private void setMagicWord(String value) {
                            magicWord = value;
                        }
                    }
                    """,
                """
                    class Utilities {
                        private static String magicWord = "magic";
                                               
                        private static void setMagicWord(String value) {
                            magicWord = value;
                        }
                    }
                    """
            )
        );
    }

    @Test
    void finalMethodWithoutInstanceData() {
        rewriteRun(
            java(
                """
                    class Utilities {
                        private static String magicWord = "magic";
                        
                        public final String getMagicWord() {
                            return magicWord;
                        }
                    }
                    """,
                """
                    class Utilities {
                        private static String magicWord = "magic";
                        
                        public static String getMagicWord() {
                            return magicWord;
                        }
                    }
                    """
            )
        );
    }

    @Test
    void loop() {
        rewriteRun(
            java(
                """
                    class Utilities {
                        private static String name="john";
                        private String instantName;
                                                
                        private void test(String v) {
                            for (int i = 0; i < 10; i++) {
                                name += v;
                            }
                        }
                        
                        private void test2(String v) {
                            for (int i = 0; i < 10; i++) {
                                instantName += v;
                            }
                        }
                    }
                    """,
                """
                    class Utilities {
                        private static String name="john";
                        private String instantName;
                        
                        private static void test(String v) {
                            for (int i = 0; i < 10; i++) {
                                name += v;
                            }
                        }
                        
                        private void test2(String v) {
                            for (int i = 0; i < 10; i++) {
                                instantName += v;
                            }
                        }
                    }
                    """
            )
        );
    }

    @Test
    void methodOverloading() {
        rewriteRun(
            java(
                """
                    class Utilities {
                        private static String magicWord = "magic";
                        private String instantName;
                        
                        private String getMagicWord() {
                            return magicWord;
                        }
                        
                        private String getMagicWord(String s) {
                            return instantName + magicWord;
                        }
                                               
                        private void setMagicWord(String value) {
                            getMagicWord(value);
                        }
                    }
                    """,
                """
                    class Utilities {
                        private static String magicWord = "magic";
                        private String instantName;
                        
                        private static String getMagicWord() {
                            return magicWord;
                        }
                        
                        private String getMagicWord(String s) {
                            return instantName + magicWord;
                        }
                                               
                        private void setMagicWord(String value) {
                            getMagicWord(value);
                        }
                    }
                    """
            )
        );
    }

    @Test
    void declareMultipleInstanceVariables() {
        rewriteRun(
            java(
                """
                    class Utilities {
                        private static String magicWord = "magic";
                        private String firstName, lastName;
                        
                        private void setMagicWord(String s) {
                            firstName = lastName + magicWord;
                        }
                    }
                    """
            )
        );
    }

    @Test
    void accessVariablesinLambda() {
        rewriteRun(
            java(
                """
                    import java.util.List;
                    import java.util.stream.Collectors;
                     
                    class Utilities {
                        private static String magicWord = "magic";
                        private String firstName, lastName;
                     
                        private void setName(List<String> list) {
                            List newList = list.stream().map(s->s.concat(firstName)).map(s->s+lastName).collect(Collectors.toList());
                        }
                        
                        private void setMagivWord(List<String> list) {
                            List newList = list.stream().map(s -> s.concat(magicWord)).collect(Collectors.toList());
                        }
                    }
                    """,
                """
                    import java.util.List;
                    import java.util.stream.Collectors;
                     
                    class Utilities {
                        private static String magicWord = "magic";
                        private String firstName, lastName;
                     
                        private void setName(List<String> list) {
                            List newList = list.stream().map(s->s.concat(firstName)).map(s->s+lastName).collect(Collectors.toList());
                        }
                        
                        private static void setMagivWord(List<String> list) {
                            List newList = list.stream().map(s -> s.concat(magicWord)).collect(Collectors.toList());
                        }
                    }
                    """
            )
        );
    }

    @Test
    void methodsUpdateToStaticInTwoCycles() {
        rewriteRun(
            spec -> { spec.expectedCyclesThatMakeChanges(2); },
            java(
                """
                    class Utilities {
                        private static String magicWord = "magic";
                                               
                        private void setMagicWord(String value) {
                            magicWord = value;
                        }
                        
                        private void accessSetMagicWord() {
                            setMagicWord(magicWord);
                        }
                    }
                    """,
                """
                    class Utilities {
                        private static String magicWord = "magic";
                                               
                        private static void setMagicWord(String value) {
                            magicWord = value;
                        }
                        
                        private static void accessSetMagicWord() {
                            setMagicWord(magicWord);
                        }
                    }
                    """
            )
        );
    }

    //This is currently failing.
    //Looks like it is only running two cycles, even though 3 is specified.
    //by adding doAfterVisit(), this test will succeed.
    void methodsUpdateToStaticInMultipleCycles() {
        rewriteRun(
            spec -> { spec.expectedCyclesThatMakeChanges(3); },
            java(
                """
                    class Utilities {
                        private static String magicWord = "magic";
                        
                        private String getMagicWord() {
                            return magicWord;
                        }
                                               
                        private void setMagicWord(String value) {
                            magicWord = getMagicWord() + value;
                        }
                        
                        private void accessSetName() {
                            setMagicWord(magicWord);
                        }
                    }
                    """,
                """
                    class Utilities {
                        private static String magicWord = "magic";
                        
                        private static String getMagicWord() {
                            return magicWord;
                        }
                                               
                        private static void setMagicWord(String value) {
                            magicWord = getMagicWord() + value;
                        }
                        
                        private static void accessSetName() {
                            setMagicWord(magicWord);
                        }
                    }
                    """
            )
        );
    }

    @Test
    void methodAccessInnerStaticClassMethod() {
        rewriteRun(
            java(
                """
                    class Utilities {
                        private static String magicWord = "magic";
                                                                       
                        private void setMagicWord(String value) {
                            magicWord = value + Helper.getString();
                        }
                        
                        static class Helper {
                            public static String getString() {
                                return "string";
                            }
                        }
                    }
                    """,
                """
                    class Utilities {
                        private static String magicWord = "magic";
                                                                           
                        private static void setMagicWord(String value) {
                            magicWord = value + Helper.getString();
                        }
                        
                        static class Helper {
                            public static String getString() {
                                return "string";
                            }
                        }
                    }
                    """
            )
        );
    }

    @Test
    void methodAccessingLibraryStaticMethod() {
        rewriteRun(
            java(
                """
                    class Utilities {
                        private static String magicWord = "magic";
                        
                        private void setMagicWord(String value) {
                            int v = Integer.parseInt(value);
                            magicWord = value + v;
                        }
                    }
                    """,
                """
                    class Utilities {
                        private static String magicWord = "magic";
                                             
                        private static void setMagicWord(String value) {
                            int v = Integer.parseInt(value);
                            magicWord = value + v;
                        }
                    }
                    """
            )
        );
    }

    @Test
    void methodsWithComplexParams() {
        rewriteRun(
            java(
                """
                    import java.util.Comparator;
                    import java.util.List;
                    
                    class Utilities {
                        private String instantName;
                        
                        private <T> void sort(List<T> list, Comparator<? super T> c) {
                            instantName = list.size() + "";
                        }
             
                        private <T> void sort(List<T> list) {
                            int i = 0;
                        }
                    }
                    """,
                """
                    import java.util.Comparator;
                    import java.util.List;
                    
                    class Utilities {
                        private String instantName;
                        
                        private <T> void sort(List<T> list, Comparator<? super T> c) {
                            instantName = list.size() + "";
                        }
             
                        private static <T> void sort(List<T> list) {
                            int i = 0;
                        }
                    }
                    """
            )
        );
    }
}
