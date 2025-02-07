package cn.qaiu.util;

public class FileSizeConverter {

    public static long convertToBytes(String sizeStr) {
        if (sizeStr == null || sizeStr.isEmpty()) {
            throw new IllegalArgumentException("Invalid file size string");
        }

        sizeStr = sizeStr.trim().toUpperCase();
        char unit = sizeStr.charAt(sizeStr.length() - 1);
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
