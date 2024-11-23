import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.visitor.VoidVisitorWithDefaults;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used to compare OpenGL functions from gl_functions.csv to current GLStateManager
 * source code to determine what functions have an implementation in GLSM, and what ones don't.
 * Only considers functions which appear in GL 2.1 and lower
 */
public class GLSMFunctionComparison {

    /**
     * If set to true, then the outputted CSV will show functions which do have an implementation in GLSM
     */
    final static boolean INCLUDE_TRUE = false;

    final static String glFunctionsCsvPath = "gl_functions.csv";
    final static String glsmPath = "src/main/java/com/gtnewhorizons/angelica/glsm/GLStateManager.java";

    public static Map<String, String> parseGLFunctions() {
        Map<String, String> map = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(glFunctionsCsvPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                if (values.length == 2) {
                    String key = values[0].trim();
                    String value = values[1].trim();
                    int versionInt = Integer.parseInt(value.substring(2, 4));
                    if (versionInt < 30) map.put(key, value);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return map;
    }

    public static void main(String[] args) throws FileNotFoundException {
        //GLSM will fail to parse without increasing language level
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);

        Map<String, String> functionMap = parseGLFunctions();
        Set<String> glsmFunctions = new HashSet<>();

        File glsmFile = new File(glsmPath);
        CompilationUnit cu = StaticJavaParser.parse(glsmFile);
        cu.accept(new VoidVisitorWithDefaults<>() {

            @Override
            public void defaultAction(Node n, Object arg) {
                for (Node child : n.getChildNodes()) {
                    if (child instanceof ClassOrInterfaceDeclaration cd) {
                        for (MethodDeclaration md : cd.getMethods()) {
                            String methodName = md.getNameAsString() + "(";
                            for (Parameter p : md.getParameters()) {
                                methodName = methodName + p.getTypeAsString();
                            }
                            methodName = methodName + ")";
                            glsmFunctions.add(methodName);
                        }
                    }
                }
            }
        }, null);

        Map<String, String> functionGLSMMap = new HashMap<>();

        for(String function : functionMap.keySet()) {
            if (!glsmFunctions.contains(function)) {
                functionGLSMMap.put(function, "false");
            } else if (INCLUDE_TRUE) {
                functionGLSMMap.put(function, "true");
            }
        }

        List<String[]> outputLines = new ArrayList<>();

        for (Map.Entry<String, String> entry : functionGLSMMap.entrySet()) {
            outputLines.add(new String[] {entry.getKey(), functionMap.get(entry.getKey()), entry.getValue()});
        }

        File output = new File("glsm_function_comparison.csv");
        try (PrintWriter writer = new PrintWriter(output)) {
            outputLines.stream().map(GLSMFunctionComparison::convertToCSV).forEach(writer::println);
        }
    }

    public static String convertToCSV(String[] data) {
        return Stream.of(data).map(GLSMFunctionComparison::escapeSpecialCharacters).collect(Collectors.joining(","));
    }

    public static String escapeSpecialCharacters(String data) {
        if (data == null) {
            throw new IllegalArgumentException("Input data cannot be null");
        }
        String escapedData = data.replaceAll("\\R", " ");
        if (data.contains(",") || data.contains("\"") || data.contains("'")) {
            data = data.replace("\"", "\"\"");
            escapedData = "\"" + data + "\"";
        }
        return escapedData;
    }

    public static String removeFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return filename;
        }

        String extPattern = "(?<!^)[.]" + "[^.]*$";
        return filename.replaceAll(extPattern, "");
    }

}
