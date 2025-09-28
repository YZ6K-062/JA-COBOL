
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 一个简化版 COBOL 解释器，用于 Java
 * 支持：
 * - MOVE, ADD, SUBTRACT, MULTIPLY, DIVIDE
 * - COMPUTE
 * - DISPLAY, ACCEPT
 * - IF ... ELSE ... END-IF
 * - PERFORM ... UNTIL
 * - SECTION / PARAGRAPH 调用
 * - GOTO
 * - EVALUATE ... WHEN ... END-EVALUATE
 * - STOP RUN
 */
public class CobolInterpreter {
    private final Map<String, Object> variables = new HashMap<>();
    private final Map<String, VarSpec> varSpecs = new HashMap<>();
    private final Map<String, List<String>> paragraphs = new HashMap<>();
    private final List<String> output = new ArrayList<>();
    private boolean running = true;
    private Iterator<String> lineIterator;
    private final Scanner scanner = new Scanner(System.in);

    /** 从文件运行 COBOL 程序 */
    public List<String> runFile(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        return run(lines);
    }

    /** 从内存运行 COBOL 程序 */
    public List<String> run(List<String> lines) {
        variables.clear();
        varSpecs.clear();
        paragraphs.clear();
        output.clear();
        running = true;

        parseDataDivision(lines);
        preprocessParagraphs(lines);
        List<String> procedure = extractProcedure(lines);
        executeBlock(procedure);

        return new ArrayList<>(output);
    }

    /** DATA DIVISION -> 解析变量声明 (PIC) */
    private void parseDataDivision(List<String> lines) {
        boolean inData = false, inWorking = false;
        Pattern picPattern = Pattern.compile("PIC\\s+([9Xx][^\\s.]*)", Pattern.CASE_INSENSITIVE);

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("*")) continue;
            String up = line.toUpperCase();
            if (up.startsWith("DATA DIVISION")) { inData = true; continue; }
            if (!inData) continue;
            if (up.startsWith("PROCEDURE DIVISION")) break;
            if (up.contains("WORKING-STORAGE SECTION")) { inWorking = true; continue; }
            if (!inWorking) continue;

            Matcher m = picPattern.matcher(line);
            if (m.find()) {
                String picToken = m.group(1);
                String beforePic = line.substring(0, m.start()).trim();
                String[] tokens = beforePic.split("\\s+");
                if (tokens.length >= 2) {
                    String varName = tokens[tokens.length - 1].replaceAll("\\.", "").toUpperCase();
                    VarSpec spec = parsePicToken(picToken);
                    varSpecs.put(varName, spec);
                    variables.put(varName, spec.isNumeric ? 0 :
                            String.format("%1$" + spec.length + "s", ""));
                }
            }
        }
    }

    private VarSpec parsePicToken(String pic) {
        pic = pic.trim().toUpperCase();
        boolean isNumeric = pic.startsWith("9");
        int length = 0;
        Pattern p = Pattern.compile("([9X])(\\((\\d+)\\))?");
        Matcher m = p.matcher(pic);
        while (m.find()) {
            String num = m.group(3);
            length += (num != null) ? Integer.parseInt(num) : 1;
        }
        return new VarSpec(isNumeric, length);
    }

    private static class VarSpec {
        final boolean isNumeric;
        final int length;
        VarSpec(boolean isNumeric, int length) {
            this.isNumeric = isNumeric;
            this.length = length;
        }
    }

    /** PROCEDURE 段落预处理 */
    private void preprocessParagraphs(List<String> lines) {
        boolean inProcedure = false;
        String currentParagraph = null;
        List<String> buffer = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("*")) continue;
            if (line.toUpperCase().startsWith("PROCEDURE DIVISION")) { inProcedure = true; continue; }
            if (inProcedure) {
                if (line.endsWith(".")) {
                    String token = line.substring(0, line.length() - 1).trim();
                    if (token.matches("[A-Z0-9-]+")) {
                        if (currentParagraph != null) {
                            paragraphs.put(currentParagraph, new ArrayList<>(buffer));
                            buffer.clear();
                        }
                        currentParagraph = token.toUpperCase();
                        continue;
                    }
                }
                if (currentParagraph != null) buffer.add(line);
            }
        }
        if (currentParagraph != null && !buffer.isEmpty()) {
            paragraphs.put(currentParagraph, buffer);
        }
    }

    /** 提取 PROCEDURE DIVISION 主体 */
    private List<String> extractProcedure(List<String> lines) {
        boolean inProcedure = false;
        List<String> procedure = new ArrayList<>();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("*")) continue;
            if (line.toUpperCase().startsWith("PROCEDURE DIVISION")) { inProcedure = true; continue; }
            if (inProcedure) procedure.add(line);
        }
        return procedure;
    }

    /** 执行一个 block */
    private void executeBlock(List<String> block) {
        lineIterator = block.iterator();
        while (lineIterator.hasNext() && running) {
            String line = lineIterator.next().trim();
            if (!line.isEmpty()) execute(line);
        }
    }

    /** 执行一行 */
    private void execute(String line) {
        String upper = line.trim().toUpperCase();
        if (upper.startsWith("MOVE")) handleMove(line);
        else if (upper.startsWith("COMPUTE")) handleCompute(line);
        else if (upper.startsWith("GOTO")) handleGoto(line);
        else if (upper.startsWith("EVALUATE")) handleEvaluate(line);
        else if (upper.startsWith("ADD")) handleAdd(line);
        else if (upper.startsWith("SUBTRACT")) handleSubtract(line);
        else if (upper.startsWith("MULTIPLY")) handleMultiply(line);
        else if (upper.startsWith("DIVIDE")) handleDivide(line);
        else if (upper.startsWith("DISPLAY")) handleDisplay(line);
        else if (upper.startsWith("ACCEPT")) handleAccept(line);
        else if (upper.startsWith("IF")) handleIf(line);
        else if (upper.startsWith("PERFORM")) handlePerform(line);
        else if (upper.startsWith("STOP RUN")) running = false;
    }

    // === COMPUTE / GOTO / EVALUATE ===
    private void handleCompute(String line) {
        String cleaned = line.trim();
        if (cleaned.endsWith(".")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        int eq = cleaned.indexOf('=');
        if (eq < 0) return;
        String left = cleaned.substring(7, eq).trim().toUpperCase();
        String expr = cleaned.substring(eq + 1).trim();
        Integer val = evalExpression(expr);
        if (val == null) return;
        VarSpec vs = varSpecs.get(left);
        if (vs != null && !vs.isNumeric) {
            String s = String.valueOf(val);
            if (vs.length > 0) s = s.length() > vs.length ? s.substring(0, vs.length) :
                    String.format("%1$-" + vs.length + "s", s);
            variables.put(left, s);
        } else variables.put(left, val);
    }

    private void handleGoto(String line) {
        String cleaned = line.trim();
        if (cleaned.endsWith(".")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        String[] parts = cleaned.split("\\s+");
        if (parts.length < 2) return;
        String label = parts[1].toUpperCase();
        if (paragraphs.containsKey(label)) executeBlock(paragraphs.get(label));
    }

    private void handleEvaluate(String firstLine) {
        String expr = firstLine.substring(8).trim();
        List<WhenBlock> blocks = new ArrayList<>();
        WhenBlock current = null;
        while (lineIterator.hasNext()) {
            String next = lineIterator.next().trim();
            String up = next.toUpperCase();
            if (up.startsWith("WHEN ")) {
                if (current != null) blocks.add(current);
                current = new WhenBlock();
                current.when = next.substring(5).trim();
                current.lines = new ArrayList<>();
                continue;
            }
            if (up.startsWith("WHEN OTHER")) {
                if (current != null) blocks.add(current);
                current = new WhenBlock();
                current.when = "OTHER";
                current.lines = new ArrayList<>();
                continue;
            }
            if (up.startsWith("END-EVALUATE")) {
                if (current != null) blocks.add(current);
                break;
            }
            if (current != null) current.lines.add(next);
        }
        Integer val = evalExpression(expr);
        String sval = val == null ? expr : String.valueOf(val);
        for (WhenBlock wb : blocks) {
            if ("OTHER".equalsIgnoreCase(wb.when)) {
                executeBlock(wb.lines); break;
            }
            boolean matched = false;
            String whenTrim = wb.when.trim();
            if (whenTrim.matches("-?\\d+") && val != null)
                matched = Integer.valueOf(whenTrim).equals(val);
            else if (whenTrim.startsWith("'") && whenTrim.endsWith("'"))
                matched = whenTrim.substring(1, whenTrim.length() - 1).equals(sval);
            else matched = whenTrim.equals(sval) || whenTrim.equalsIgnoreCase(sval);

            if (matched) { executeBlock(wb.lines); break; }
        }
    }

    private static class WhenBlock {
        String when;
        List<String> lines;
    }

    // === 表达式解析 ===
    private Integer evalExpression(String expr) {
        try { return new ExprParser(expr).parseExpression(); }
        catch (Exception e) { return null; }
    }

    private class ExprParser {
        private final String s;
        private int idx = 0;
        ExprParser(String s) { this.s = s; }
        int parseExpression() {
            int v = parseTerm();
            while (true) {
                skipWhitespace();
                if (peek() == '+') { idx++; v += parseTerm(); }
                else if (peek() == '-') { idx++; v -= parseTerm(); }
                else break;
            }
            return v;
        }
        int parseTerm() {
            int v = parseFactor();
            while (true) {
                skipWhitespace();
                if (peek() == '*') { idx++; v *= parseFactor(); }
                else if (peek() == '/') { idx++; v /= parseFactor(); }
                else break;
            }
            return v;
        }
        int parseFactor() {
            skipWhitespace();
            char c = peek();
            if (c == '(') { idx++; int v = parseExpression(); skipWhitespace(); if (peek() == ')') idx++; return v; }
            if (c == '+' || c == '-' || Character.isDigit(c)) {
                int start = idx; if (c == '+' || c == '-') idx++;
                while (Character.isDigit(peek())) idx++;
                return Integer.valueOf(s.substring(start, idx).trim());
            }
            int start = idx;
            while (Character.isLetterOrDigit(peek()) || peek()=='-') idx++;
            String name = s.substring(start, idx).trim();
            Object v = variables.getOrDefault(name.toUpperCase(), variables.getOrDefault(name, 0));
            if (v instanceof Integer) return (int) v;
            try { return Integer.valueOf(String.valueOf(v).trim()); } catch (Exception e) { return 0; }
        }
        char peek() { return idx >= s.length() ? '\0' : s.charAt(idx); }
        void skipWhitespace() { while (Character.isWhitespace(peek())) idx++; }
    }

    // === 基础运算 ===
    private void handleMove(String line) {
        String cleaned = line.trim();
        if (cleaned.endsWith(".")) cleaned = cleaned.substring(0, cleaned.length() - 1);
        int toIdx = cleaned.toUpperCase().lastIndexOf(" TO ");
        if (toIdx < 0) return;
        String valuePart = cleaned.substring(4, toIdx).trim();
        String var = cleaned.substring(toIdx + 4).trim().toUpperCase().replaceAll("\\.$", "");
        Object toValue;
        if ((valuePart.startsWith("'") && valuePart.endsWith("'")) || (valuePart.startsWith("\"") && valuePart.endsWith("\"")))
            toValue = valuePart.substring(1, valuePart.length() - 1);
        else if (valuePart.matches("-?\\d+"))
            toValue = Integer.valueOf(valuePart);
        else toValue = variables.getOrDefault(valuePart.toUpperCase(), variables.getOrDefault(valuePart, 0));

        VarSpec targetSpec = varSpecs.get(var);
        if (targetSpec != null) {
            if (targetSpec.isNumeric) {
                try { variables.put(var, Integer.valueOf(String.valueOf(toValue).trim())); }
                catch (Exception e) { variables.put(var, 0); }
            } else {
                String s = String.valueOf(toValue == null ? "" : toValue);
                if (targetSpec.length > 0)
                    s = s.length() > targetSpec.length ? s.substring(0, targetSpec.length) :
                        String.format("%1$-" + targetSpec.length + "s", s);
                variables.put(var, s);
            }
        } else variables.put(var, toValue);
    }

    private void handleAdd(String line) {
        String[] parts = line.replace(".", "").trim().split("\\s+");
        if (parts.length < 4) return;
        int value = Integer.parseInt(parts[1]);
        String var = parts[3].toUpperCase();
        int old = (variables.getOrDefault(var, 0) instanceof Integer) ? (int) variables.get(var) : 0;
        variables.put(var, old + value);
    }

    private void handleSubtract(String line) {
        String[] parts = line.replace(".", "").trim().split("\\s+");
        if (parts.length < 4) return;
        int value = Integer.parseInt(parts[1]);
        String var = parts[3].toUpperCase();
        int old = (variables.getOrDefault(var, 0) instanceof Integer) ? (int) variables.get(var) : 0;
        variables.put(var, old - value);
    }

    private void handleMultiply(String line) {
        String[] parts = line.replace(".", "").trim().split("\\s+");
        if (parts.length < 4) return;
        int value = Integer.parseInt(parts[1]);
        String var = parts[3].toUpperCase();
        int old = (variables.getOrDefault(var, 0) instanceof Integer) ? (int) variables.get(var) : 0;
        variables.put(var, old * value);
    }

    private void handleDivide(String line) {
        String[] parts = line.replace(".", "").trim().split("\\s+");
        if (parts.length < 4) return;
        int value = Integer.parseInt(parts[1]);
        String var = parts[3].toUpperCase();
        int old = (variables.getOrDefault(var, 0) instanceof Integer) ? (int) variables.get(var) : 0;
        if (value != 0) variables.put(var, old / value);
        else output.add("ERROR: DIVIDE BY ZERO");
    }

    // === I/O ===
    private void handleDisplay(String line) {
        String cleaned = line.replace(".", "").trim();
        String after = cleaned.substring(7).trim();
        if ((after.startsWith("'") && after.endsWith("'")) || (after.startsWith("\"") && after.endsWith("\""))) {
            output.add(after.substring(1, after.length() - 1));
            return;
        }
        String arg = after.split("\\s+")[0].toUpperCase();
        Object value = variables.getOrDefault(arg, arg);
        VarSpec vs = varSpecs.get(arg);
        if (vs != null && vs.isNumeric) output.add(String.valueOf(variables.getOrDefault(arg, 0)));
        else output.add(String.valueOf(value));
    }

    private void handleAccept(String line) {
        String cleaned = line.replace(".", "").trim();
        String[] parts = cleaned.split("\\s+");
        if (parts.length < 2) return;
        String var = parts[1].toUpperCase();
        try {
            String input = (System.console() != null) ? System.console().readLine() :
                    (scanner.hasNextLine() ? scanner.nextLine() : "");
            if (input == null) return;
            if (input.matches("-?\\d+")) variables.put(var, Integer.valueOf(input));
            else {
                VarSpec vs = varSpecs.get(var);
                if (vs != null && !vs.isNumeric && vs.length > 0) {
                    if (input.length() > vs.length) input = input.substring(0, vs.length);
                    else if (input.length() < vs.length) input = String.format("%1$-" + vs.length + "s", input);
                }
                variables.put(var, input);
            }
        } catch (NoSuchElementException ignored) {}
    }

    // === 条件 ===
    private void handleIf(String firstLine) {
        String conditionLine = firstLine;
        List<String> trueBlock = new ArrayList<>(), falseBlock = new ArrayList<>(), current = trueBlock;
        while (lineIterator.hasNext()) {
            String next = lineIterator.next().trim();
            if (next.toUpperCase().startsWith("ELSE")) { current = falseBlock; continue; }
            if (next.toUpperCase().startsWith("END-IF")) break;
            current.add(next);
        }
        boolean cond = evalCondition(conditionLine.substring(2).trim());
        executeBlock(cond ? trueBlock : falseBlock);
    }

    private boolean evalCondition(String expr) {
        String[] parts = expr.split("\\s+");
        if (parts.length < 3) return false;
        String left = parts[0], op = parts[1], right = parts[2];
        Object l

    = variables.getOrDefault(left.toUpperCase(), left);
            Object r = right.matches("-?\\d+") ? Integer.valueOf(right) :
                    variables.getOrDefault(right.toUpperCase(), right);
            if (l instanceof Integer && r instanceof Integer) {
                int li = (int) l, ri = (int) r;
                return switch (op) {
                    case "=" -> li == ri;
                    case "<>" -> li != ri;
                    case ">" -> li > ri;
                    case "<" -> li < ri;
                    case ">=" -> li >= ri;
                    case "<=" -> li <= ri;
                    default -> false;
                };
            } else {
                String ls = String.valueOf(l), rs = String.valueOf(r);
                return switch (op) {
                    case "=" -> ls.equals(rs);
                    case "<>" -> !ls.equals(rs);
                    case ">" -> ls.compareTo(rs) > 0;
                    case "<" -> ls.compareTo(rs) < 0;
                    case ">=" -> ls.compareTo(rs) >= 0;
                    case "<=" -> ls.compareTo(rs) <= 0;
                    default -> false;
                };
            }
        }
    
        // === 循环 ===
        private void handlePerform(String line) {
            String cleaned = line.replace(".", "").trim();
            String[] parts = cleaned.split("\\s+");
            if (parts.length < 2) return;
            String label = parts[1].toUpperCase();
            if (!paragraphs.containsKey(label)) return;
    
            if (cleaned.contains("UNTIL")) {
                String condition = cleaned.substring(cleaned.indexOf("UNTIL") + 5).trim();
                do {
                    executeBlock(paragraphs.get(label));
                } while (!evalCondition(condition) && running);
            } else {
                executeBlock(paragraphs.get(label));
            }
        }
    }