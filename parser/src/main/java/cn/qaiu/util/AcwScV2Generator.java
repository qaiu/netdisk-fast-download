package cn.qaiu.util;

import java.util.Arrays;

public class AcwScV2Generator {

    public static String acwScV2Simple(String arg1) {
        // 映射表
        int[] posList = {15,35,29,24,33,16,1,38,10,9,19,31,40,27,22,23,25,
                         13,6,11,39,18,20,8,14,21,32,26,2,30,7,4,17,5,3,
                         28,34,37,12,36};

        String mask = "3000176000856006061501533003690027800375";
        String[] outPutList = new String[40];
        Arrays.fill(outPutList, "");

        // 重排 arg1
        for (int i = 0; i < arg1.length(); i++) {
            char ch = arg1.charAt(i);
            for (int j = 0; j < posList.length; j++) {
                if (posList[j] == i + 1) {
                    outPutList[j] = String.valueOf(ch);
                }
            }
        }

        StringBuilder arg2 = new StringBuilder();
        for (String s : outPutList) {
            arg2.append(s);
        }

        // 按 mask 异或
        StringBuilder result = new StringBuilder();
        int length = Math.min(arg2.length(), mask.length());

        for (int i = 0; i < length; i += 2) {
            String strHex = arg2.substring(i, i + 2);
            String maskHex = mask.substring(i, i + 2);

            int strVal = Integer.parseInt(strHex, 16);
            int maskVal = Integer.parseInt(maskHex, 16);

            int xor = strVal ^ maskVal;

            // 补齐 2 位小写 16 进制
            result.append(String.format("%02x", xor));
        }

        return result.toString();
    }

}
