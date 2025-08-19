package cn.qaiu.util;

public class FileSizeConverter {

    public static long convertToBytes(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) {
            throw new IllegalArgumentException("Invalid file size string");
        }

        sizeStr = sizeStr.replace(",","").trim().toUpperCase();
        // 判断是2位单位还是1位单位
        // 判断单位是否为2位
        int unitIndex = sizeStr.length() - 1;
        char unit = sizeStr.charAt(unitIndex);
        if (Character.isLetter(sizeStr.charAt(unitIndex - 1))) {
            unit = sizeStr.charAt(unitIndex - 1);
            sizeStr = sizeStr.substring(0, unitIndex - 1);
        } else {
            sizeStr = sizeStr.substring(0, unitIndex);
        }
        double size = Double.parseDouble(sizeStr.substring(0, sizeStr.length() - 1));

        return switch (unit) {
            case 'B' -> (long) size;
            case 'K' -> (long) (size * 1024);
            case 'M' -> (long) (size * 1024 * 1024);
            case 'G' -> (long) (size * 1024 * 1024 * 1024);
            default -> throw new IllegalArgumentException("Unknown file size unit: " + unit);
        };
    }

    public static String convertToReadableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f K", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f M", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.1f G", bytes / (1024.0 * 1024 * 1024));
        }
    }

}
