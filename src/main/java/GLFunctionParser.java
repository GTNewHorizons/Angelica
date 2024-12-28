import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.visitor.VoidVisitorWithDefaults;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used to generate gl_functions.csv. Which contains a mapping of OpenGL functions provided by LWJGL
 * corresponding to the earliest GL version they appear in.
 */
public class GLFunctionParser {
    final static String glPath = "util/gl"; // Path to LWJGL OpenGL bindings source code

    public static void main(String[] args) throws FileNotFoundException {
        HashMap<String, Set<String>> byFunction = new HashMap<>();
        File dir = new File(glPath);
        for (File file : dir.listFiles()) {
            final String filename = file.getName();
            if (filename.endsWith(".java") && filename.startsWith("GL") && Character.isDigit(filename.charAt(2))) {
                Path p = Paths.get(filename);

                String parsedFile = removeFileExtension(p.getFileName().toString());

                CompilationUnit cu = StaticJavaParser.parse(file);
                cu.accept(new VoidVisitorWithDefaults<>() {

                    @Override
                    public void defaultAction(Node n, Object arg) {
                        for( Node child : n.getChildNodes()) {
                            if(child instanceof ClassOrInterfaceDeclaration cd) {
                                for (MethodDeclaration md : cd.getMethods()) {
                                    String methodName = md.getNameAsString() + "(";
                                    for (Parameter p : md.getParameters()) {
                                        methodName = methodName + p.getTypeAsString();
                                    }
                                    methodName = methodName + ")";
                                    if(methodName.startsWith("n")) continue;

                                    Set<String> functionSet = byFunction.computeIfAbsent(methodName, k -> new HashSet<>());
                                    functionSet.add(parsedFile);
                                }
                            }
                        }
                    }
                }, null);

            }
        }

        Map<String, String> functionVersionMap = new HashMap<>();

        for(String function : byFunction.keySet()) {

            Set<String> versionsForFunction = byFunction.get(function);
            int lowestInt = 46;
            String lowestString = "GL46";
            for(String version : versionsForFunction) {
                int i = Integer.parseInt(version.substring(2, 4));
                if (i < lowestInt) {
                    lowestInt = i;
                    lowestString = version;
                }
            }
            functionVersionMap.put(function, lowestString);
        }

        List<String[]> outputLines = new ArrayList<>();

        for (Map.Entry<String, String> entry : functionVersionMap.entrySet()) {
            outputLines.add(new String[] {entry.getKey(), entry.getValue()});
        }

        File output = new File("output.csv");
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
