import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class main {
    public static void main(String[] args) {

        if (args.length < 1) {
            System.out.println("⚠️ 没有传入 COBOL 文件路径，使用内置示例代码运行...\n");

            List<String> demoProgram = List.of(
                    "IDENTIFICATION DIVISION.",
                    "PROGRAM-ID. HELLO.",
                    "DATA DIVISION.",
                    "WORKING-STORAGE SECTION.",
                    "01 X PIC 9(3).",
                    "PROCEDURE DIVISION.",
                    "    MOVE 10 TO X.",
                    "    ADD 5 TO X.",
                    "    SUBTRACT 2 FROM X.",
                    "    DISPLAY X.",
                    "    MOVE 'HELLO' TO MSG.",
                    "    DISPLAY 'Message:'",
                    "    DISPLAY MSG.",
                    "    STOP RUN."
            );

            CobolInterpreter interp = new CobolInterpreter();
            List<String> out = interp.run(demoProgram);
            out.forEach(System.out::println);
            return;
        }
        String filePath = args[0];
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            CobolInterpreter interp = new CobolInterpreter();
            List<String> out = interp.run(lines);
            out.forEach(System.out::println);
        } catch (IOException e) {
            System.err.println("❌ 读取文件失败: " + e.getMessage());
        }
    }
}
