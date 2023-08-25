package cn.qaiu.util;

public class ArrayUtil {

    public static int[] parseIntArray(String[] arr) {
        int[] ints = new int[arr.length];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = Integer.parseInt(arr[i]);
        }
        return ints;
    }

    public static float[] parseFloatArray(String[] arr) {
        float[] ints = new float[arr.length];
        for (int i = 0; i < ints.length; i++) {
            ints[i] = Float.parseFloat(arr[i]);
        }
        return ints;
    }
}
