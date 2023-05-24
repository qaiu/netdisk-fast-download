package cn.qaiu.web.test;

import cn.qaiu.vx.core.util.CastUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

/**
 * @author <a href="https://qaiu.top">QAIU</a>
 * @date 2023/4/21 21:43
 */
@Slf4j
public class TestJsoup {
/*



2023-04-21 21:52:56.401 INFO  -> [           main] cn.qaiu.web.test.TestJsoup               :
{code=0000, message=success, tn=TN:19387A43A5564BB6B52B008071DD69B2,
data={payEnabled=false, payStatus=false, skuId=null, skuPrice=null,
guid=e4f41b51-b5da-4f60-9312-37aa10c0aad7, transferName=05-CGB-DB-MENU-V1.02,
transferMessage=null, uniqueUrl=e4f41b51b5da4f, needPassword=false,
expireAt=2099-12-31 23:59:59, validDays=-1, enableDownload=true, enablePreview=true, enableSaveto=true,
uploadState=1, deleted=false, tag=1, dataTag=1, status=0, fileAmount=1, folderAmount=0, size=962041, openId=1023860921943729188,
firstFile={id=23861191276513345, owner=1023860921943729188, recycle=false, need_pro=false, storage_class=standard, file_type=document, analysis_status=2, audit_status=2, repository_id=2004556995, created_at=1682081417000, created_by=1023860921943729188, updated_at=1682081416968, folder_id=2013607944, folder_name=, file_info={format=docx, size=962041, title=05-CGB-DB-MENU-V1.02, description=, preview={ext={"ratio":3}, height=2525, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/82ff6da4-67f9-4cdb-8495-24bcd97cd6ab69566.png?auth_key=1682099544-1f494837775a422d82f4e67006c720c1-0-0d1cc615dd3e5b7a900b75fc5f7edf21, width=1785}, colors=[], origin_url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/db32e132e69f490eb4a343b398990f4b.docx?auth_key=1682099544-d71aa67f5af843cba64a5dfff9ec3357-0-ec3e3325421d8f858c4a3cb33139553c, theme_color=, extend_previews=[{ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/312b7270-106a-480d-9deb-49df4c6539b069567.png?auth_key=1682099544-695f3060762c474d8312efc8205b85e3-0-a80564c120ff26c276cce769f091e9fa, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/6322a6a9-d1ea-4904-ada0-a0cfe504b32e69568.png?auth_key=1682099544-04e62f12cca94ad5a8f86c922d76b2f3-0-6efc5315f539691b378be42757a6d9f1, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/f6f37eb4-0be8-41e6-b362-07e2331237f369569.png?auth_key=1682099544-c28fd4178f9541938136ece965e8c6f0-0-71a0d0b9a7b7ea16be8d76294987ca48, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/f0f3d05d-7597-4235-b41d-2beb79fee1c869570.png?auth_key=1682099544-49825346ee864edfa855c05f65e11cdc-0-c511e3c9d18bb88a1044dc374f53b5e3, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/a85999d4-007e-4b68-882a-3f5ac593b6ce69571.png?auth_key=1682099544-898de7bce2a44413a297ec455a628f41-0-80f2b0bbcd4979e0c341ae629681825f, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/725f22ae-b7d3-4447-9408-df1da9a9c1bd69572.png?auth_key=1682099544-5b0b2f6b640a4e44936cacfbf1907d9c-0-8d0a7568bb13a93c0cb020f7f2498028, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/427cf5da-8d67-41b3-a429-37e8f3cc96b069573.png?auth_key=1682099544-fe5f7595fc0748868de59c19139dff3d-0-0078c6a395a89afdeef23df934636665, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/e2f489d3-7b28-4d9b-af0c-cbcc0e71fb3469574.png?auth_key=1682099544-85e4592f24e840f59ac0b5e51e39eb97-0-7211f254968e80f34680b530f06120d8, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/f885435e-2758-4dec-90e0-c7f6d55b8bff69575.png?auth_key=1682099544-96df86e45e5a4b02a1bd6197c292fe53-0-7d0af45b70b7e55ab383d0019a1dc5cd, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/f1570313-64ec-4547-9e9c-0af154cd8dc469576.png?auth_key=1682099544-4218db8e98f541a2ad8ad717958cc859-0-588626b81c407d1150319b4d76e2069b, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/7d9e7d47-a874-4c45-9bba-0687d2b4b36c69577.png?auth_key=1682099544-9322e74bc4694be386cc284ee1e4991a-0-2734f9d25bdee992255f0d410a57ad42, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/7a58264c-0b8d-4737-99e6-c336c59d5e9e69578.png?auth_key=1682099544-e80940a49de5408da1906bde7aeaadb9-0-0c471dc965e28fd7d8b12ce5a8d98317, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/fc452796-048e-4926-85cf-ec9fc62fd50369579.png?auth_key=1682099544-3fb431425b07484dae2479b6488ee1df-0-cfeecc56fb2cf5ca2a9148de3581056e, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/5aa0b694-b420-43b3-b0bd-616e34c7a84269580.png?auth_key=1682099544-00a8b57aeeb6436a82abcd44d027345c-0-9d7bc1319e4cbefa8017eaa7504f6ef7, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/e4d9be2e-00be-45f1-a6f2-e78e8aa9405469581.png?auth_key=1682099544-388d1436290e4cdca82a0852ba55c005-0-526df026b1359fe1d031b7ea2429d115, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/7878c6e6-8983-4ef4-9bc5-f0b6c40fbc3869582.png?auth_key=1682099544-a288c1b2f78945d3a2298d5b9a7806cd-0-2ee0cb7aeb8dac5a5dcd5a5e98a0dc4a, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/86b9fe4c-fd0b-4c66-9ca6-d7756573c7d769583.png?auth_key=1682099544-2d3022ff607840cd920c6ed6e8192718-0-d118921ce6d6332292836080105df7d4, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/1347bd4c-67ee-44e4-a8c7-2732422de33769584.png?auth_key=1682099544-0edbf55bab6e443a86c3f266942b2bb9-0-40ea5e075efa1d2bb9463ee3206945f3, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/2402bdcb-5cc3-4936-b433-780ee69d29d769585.png?auth_key=1682099544-6fbdd591672146efa58ca39f17cf4d90-0-c3317facaf27bb24990909797c8131bb, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/c66339b0-2896-4772-82e2-514c30b7007669586.png?auth_key=1682099544-13774a13171b41189464c5b08b51b230-0-0b592e6f0e921cbd6d3c8be98038fdc0, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/74611acd-fa00-4356-a526-6d5adccddb6169587.png?auth_key=1682099544-4f9edebf7ac449539d011e8f61d6b024-0-a7b60ddc5193b5efcc5da1760a1b5c6a, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/cf3462bd-fdbb-4e6d-8dd5-9ba4b0245a7869588.png?auth_key=1682099544-a970b0bbef744224898403bc4ea21025-0-8a0fa954736aa0018bf7f15f5eca8ff5, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/8b1bded3-7636-4b9a-aed4-27bdb5cac02969589.png?auth_key=1682099544-5510a830e0d54f1a869cee37f0120181-0-d387b63c2cefb5d6e234746c20d802a9, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/0f32ff8c-81a9-4a7f-8b47-7356b8f9708a69590.png?auth_key=1682099544-778e56b2660044879a64bd73cc3653d9-0-d09837511af37730045f240aace666f3, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/77da0e25-3155-4f5c-a382-ec066afacf8069591.png?auth_key=1682099544-f6753571d09d4f60b3bf52a6d1509471-0-890fc11cbff3f372847cde64cfce047e, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/b5ffa264-7a21-406e-9ba4-f306c252827669592.png?auth_key=1682099544-9e89ee064e77444dbc8efb8c58264ead-0-6e33b5e1628d3c76ce273ec1122943c8, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/90d39f4b-94d2-44fe-80f5-ff010ee11bd469593.png?auth_key=1682099544-99fc8ad1607d40449653aee22e899709-0-3e220bdfb3be8811d249a0d1ebc9c37c, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/affdc9b8-f3e6-4700-b5ff-bfbfa28c0f8f69594.png?auth_key=1682099544-fd9868d864864111b29a482ed1af913a-0-f72b6eb1de8f1a06a9978ae12ec530df, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/85a905c6-afe9-4079-85d6-ab5bb377f70869595.png?auth_key=1682099544-6ad4b40a3d2c4fb78a721d393174f584-0-a23d71ff3f03de11de77a0da9a8caa2b, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/6bf91741-bd1d-49ba-ba4b-4759831e364269596.png?auth_key=1682099544-7a1de1f120d74914a2620aeb099ba084-0-776b564c76ef2568ece044dfe2a192ff, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/d03f08dd-156c-45e7-8b94-afa13b8afcd469597.png?auth_key=1682099544-a4a53055c6aa4b4fb2f7d112a6429d77-0-a802f42a4671c2d7e13abe69233e9336, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/e80a0b67-122a-4276-a671-d9e5008d336e69598.png?auth_key=1682099544-8bfa620907534670834e40b9235baf69-0-500db05cf6cc42952dbb375cce9f361a, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/ba61d5c3-307e-4afd-95cd-d45412ae55da69599.png?auth_key=1682099544-f9bd3a4beede4723b9b3ac225e65c8c1-0-93f08328115bddbdf798f6df59381b3a, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/e56afec9-3c04-46c0-a1ed-ca5b8b20466d69600.png?auth_key=1682099544-397910a7945942f496800102b597b3a8-0-42dc09c2de91bef1c69e55a36163b65b, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/54d6b750-7b7a-4b91-950d-919d2449487469601.png?auth_key=1682099544-4eb6b7e6c6c443c6a1a7058179074300-0-6ac688110cd244102b19e41924717bae, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/15387dca-6ca9-422c-a775-4bcf9721fd4669602.png?auth_key=1682099544-436689971c234e67a806f9c7ebdc11c2-0-02dc01b03b941f378e5f708c599e6218, width=0}, {ext={"ratio":1}, height=0, url=https://download.cowcs.com/cowtransfer/cowtransfer/29188/90c577b1-7830-4365-a820-c83adaeaf00c69603.png?auth_key=1682099544-2323376ba3de435a84adb4a8b319aa5d-0-3d58a45a4e3d71599edda71ad1578b3d, width=0}], music_info=null, video_info=null}}, firstFolder=null, zipDownload=false}}
与目标 VM 断开连接, 地址为: ''127.0.0.1:57249'，传输: '套接字''
 */
    @Test
    public void test1() throws IOException {
        String baseUrl = "https://cowtransfer.com/core/api/transfer/share";
        String result = Jsoup
                .connect(baseUrl+"?uniqueUrl=e4f41b51b5da4").ignoreContentType(true)
                .get()
                .text();
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> map = objectMapper.readValue(result, new TypeReference<>() {});
        if ("success".equals(map.get("message")) && map.containsKey("data")) {
            Map<String, Object> data = CastUtil.cast(map.get("data"));
            String guid = data.get("guid").toString();
            Map<String, Object> firstFile = CastUtil.cast(data.get("firstFile"));
            String fileId = firstFile.get("id").toString();
            String result2 = Jsoup
                    .connect(baseUrl+"/download?transferGuid="+guid+"&fileId="+fileId)
                    .ignoreContentType(true)
                    .get()
                    .text();
            Map<String, Object> map2 = objectMapper.readValue(result2, new TypeReference<>() {});

            if ("success".equals(map2.get("message")) && map2.containsKey("data")) {
                Map<String, Object> data2 = CastUtil.cast(map2.get("data"));
                String downloadUrl = data2.get("downloadUrl").toString();
                if (StringUtils.isNotEmpty(downloadUrl)) {
                    log.info(downloadUrl);
                }
            }

        }
        log.info("OK------------->end");
    }
}
