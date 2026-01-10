package cn.qaiu.parser;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Value;

/**
 * GraalPy 简单测试
 */
public class GraalPyTest {
    
    public static void main(String[] args) {
        System.out.println("===== GraalPy 测试开始 =====");
        
        try {
            System.out.println("1. 检查可用语言...");
            try (Engine engine = Engine.create()) {
                System.out.println("   可用语言: " + engine.getLanguages().keySet());
                if (!engine.getLanguages().containsKey("python")) {
                    System.err.println("   ✗ Python 语言不可用！");
                    System.exit(1);
                }
                System.out.println("   ✓ Python 语言可用");
            }
            
            System.out.println("2. 尝试创建 Python Context...");
            try (Context context = Context.newBuilder("python")
                    .option("engine.WarnInterpreterOnly", "false")
                    .build()) {
                System.out.println("   ✓ Context 创建成功");
                
                System.out.println("3. 执行简单 Python 代码...");
                Value result = context.eval("python", "1 + 2");
                System.out.println("   ✓ 计算结果: 1 + 2 = " + result.asInt());
                
                System.out.println("4. 执行字符串操作...");
                Value strResult = context.eval("python", "'Hello' + ' ' + 'GraalPy'");
                System.out.println("   ✓ 字符串结果: " + strResult.asString());
                
                System.out.println("5. 执行多行代码...");
                String code = """
                    def greet(name):
                        return f"Hello, {name}!"
                    greet("World")
                    """;
                Value funcResult = context.eval("python", code);
                System.out.println("   ✓ 函数结果: " + funcResult.asString());
            }
            
            System.out.println("===== GraalPy 测试通过 =====");
        } catch (Exception e) {
            System.err.println("✗ GraalPy 测试失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
