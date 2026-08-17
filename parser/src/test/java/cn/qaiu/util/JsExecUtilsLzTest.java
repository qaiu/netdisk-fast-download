package cn.qaiu.util;

import org.junit.Test;
import org.openjdk.nashorn.api.scripting.ScriptObjectMirror;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * 新版蓝奏页面会调用更多 document / jQuery API，沙箱必须能跑完并抓住 $.ajax。
 */
public class JsExecUtilsLzTest {

    @Test
    public void testNewIframeAjaxWithDomApis() throws Exception {
        String js = """
                var lanosso = '';
                var down_1 = '';
                var wsk_sign = 'c20230908';
                var wp_sign = 'SIGN_ABC';
                var ajaxdata = 'MrBR';
                var kdns = 1;
                if (typeof(killdns)=='undefined'){
                    var kdns = 0;
                }
                document.cookie = 'x=1';
                document.location.reload();
                document.querySelector('#tourl');
                document.createElement('div');
                $.ajax({
                    type : 'post',
                    url : '/ajaxfile.php?file=150233466',
                    data : { 'action':'downprocess','websignkey':ajaxdata,'signs':ajaxdata,'sign':wp_sign,'websign':'','kd':kdns,'ves':1 },
                    dataType : 'json',
                    success:function(msg){
                        $("#tourl").html("ok");
                        $("#outime").css("display","block");
                    }
                });
                """;
        ScriptObjectMirror sign = JsExecUtils.executeDynamicJs(js, null);
        assertNotNull(sign);
        assertEquals("/ajaxfile.php?file=150233466", String.valueOf(sign.get("url")));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) sign.get("data");
        assertEquals("downprocess", String.valueOf(data.get("action")));
        assertEquals("SIGN_ABC", String.valueOf(data.get("sign")));
        assertEquals("MrBR", String.valueOf(data.get("websignkey")));
        assertEquals("1", String.valueOf(data.get("kd")));
    }

    @Test
    public void testPwdViaJqueryValAndDocument() throws Exception {
        String js = """
                function down_p(){
                    var pwd = $('#pwd').val();
                    var pwd2 = document.getElementById('pwd').value;
                    var pwd3 = document.querySelector('#pwd').value;
                    $(".passwdinput").focus();
                    $.ajax({
                        type : 'post',
                        url : '/ajaxm.php',
                        data : { 'action':'downprocess','sign':'S1','p':pwd,'p2':pwd2,'p3':pwd3 }
                    });
                }
                """;
        ScriptObjectMirror sign = JsExecUtils.executeDynamicJs(js, "down_p", "e4k4");
        assertNotNull(sign);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) sign.get("data");
        assertEquals("e4k4", String.valueOf(data.get("p")));
        assertEquals("e4k4", String.valueOf(data.get("p2")));
        assertEquals("e4k4", String.valueOf(data.get("p3")));
    }

    @Test
    public void testReadyAndSuccessCallbackDoNotDropAjax() throws Exception {
        String js = """
                $(function(){
                    document.getElementById('rpt').style.display = 'none';
                    $.ajax({ url: '/ajaxm.php?file=1', data: { a: 1 } });
                    $("#tourl").html("x");
                });
                """;
        ScriptObjectMirror sign = JsExecUtils.executeDynamicJs(js, null);
        assertNotNull(sign);
        assertTrue(String.valueOf(sign.get("url")).contains("ajaxm.php"));
    }
}
