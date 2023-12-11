package cn.qaiu.util;

public class StringUtils {

    // 非贪婪截断匹配
    public static String StringCutNot(final String strtarget, final String strstart)
    {
        int startIdx = strtarget.indexOf(strstart);

        if (startIdx != -1) {
            startIdx += strstart.length();
            return strtarget.substring(startIdx);
        }

        return null;
    }

    // 非贪婪截断匹配
    public static String StringCutNot(final String strtarget, final String strstart, final String strend)
    {
        int startIdx = strtarget.indexOf(strstart);
        int endIdx   = -1;

        if (startIdx != -1) {
            startIdx += strstart.length();
            endIdx    = strtarget.indexOf(strend, startIdx);

            if (endIdx != -1) {
                return strtarget.substring(startIdx, endIdx);
            }
        }

        return null;
    }

}
