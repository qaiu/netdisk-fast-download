package cn.qaiu.parser.impl;

import org.junit.Test;

import static org.junit.Assert.*;

public class LzToolAjaxExtractTest {

    @Test
    public void testExtractWpSignAjaxfile() {
        String html = """
                <script>
                var wp_sign = 'BWMGOF5v';
                var ajaxdata = 'MrBR';
                var kdns =1;
                $.ajax({
                    type : 'post',
                    url : '/ajaxfile.php?file=150233466',
                    data : { 'action':'downprocess','websignkey':ajaxdata,'signs':ajaxdata,'sign':wp_sign,'websign':'','kd':kdns,'ves':1 }
                });
                </script>
                """;
        LzTool.AjaxCall call = LzTool.extractAjaxFromHtml(html, null);
        assertNotNull(call);
        assertEquals("/ajaxfile.php?file=150233466", call.path());
        assertEquals("BWMGOF5v", call.form().get("sign"));
        assertEquals("MrBR", call.form().get("websignkey"));
        assertEquals("downprocess", call.form().get("action"));
        assertEquals("1", call.form().get("kd"));
    }

    @Test
    public void testExtractPasswordSign() {
        String html = """
                <input id="pwd">
                <script>
                function down_p(){
                    $.ajax({
                        type : 'post',
                        url : '/ajaxm.php?file=1',
                        data : { 'action':'downprocess','sign':'ABC123','p':'x' }
                    });
                }
                </script>
                """;
        LzTool.AjaxCall call = LzTool.extractAjaxFromHtml(html, "e4k4");
        assertNotNull(call);
        assertEquals("/ajaxm.php?file=1", call.path());
        assertEquals("ABC123", call.form().get("sign"));
        assertEquals("e4k4", call.form().get("p"));
    }
}
