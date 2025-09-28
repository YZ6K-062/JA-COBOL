# JA-COBOL 简易解释器

这是一个用 Java 实现的简化 COBOL 解释器，用于学习与小型测试。当前实现支持：

- DATA DIVISION (WORKING-STORAGE) 中简单 `PIC` 声明（`9`, `9(n)`, `X`, `X(n)`）并初始化变量
- MOVE / ADD / SUBTRACT / MULTIPLY / DIVIDE
- DISPLAY / ACCEPT
- IF ... ELSE ... END-IF
- PERFORM 段落调用与简单循环 (PERFORM ... UNTIL / END-PERFORM)
- COMPUTE / GOTO / EVALUATE (简化实现)
- STOP RUN

构建与运行

使用内置 Gradle wrapper 构建：

```powershell
g:\JA-COBOL\gradlew.bat build
```

运行内置示例：

```powershell
java -cp build/libs/JA-COBOL-1.0-SNAPSHOT.jar main
```

用示例 COBOL 文件运行（示例位于 `examples/hello.cob`）：

```powershell
java -cp build/libs/JA-COBOL-1.0-SNAPSHOT.jar main examples\\hello.cob
```

示例

`examples/hello.cob` 包含一个最小程序，展示 WORKING-STORAGE、MOVE、DISPLAY 与 PERFORM。

扩展与测试

- 我可以添加更多 COBOL 语句支持（STRING, UNSTRING, SEARCH, WRITE 等），或改进 DATA DIVISION 的完整解析（GROUP、REDEFINES、PIC 更多格式）。
- 建议添加 JUnit 测试来覆盖核心语义。 

如果你想让我创建测试或扩展某些语法，现在告诉我下一步。