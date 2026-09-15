/*
 * Decompiled with CFR 0.152.
 */
package me.zetra.nickguard;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TotpUtil {
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final char[] SAFE_BASE32 = "ABCDEFGHJKMNPQRSTUVWXYZ234567".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtil() {
    }

    public static String generateSecret() {
        StringBuilder stringBuilder = new StringBuilder(32);
        for (int i = 0; i < 32; ++i) {
            stringBuilder.append(SAFE_BASE32[RANDOM.nextInt(SAFE_BASE32.length)]);
        }
        return stringBuilder.toString();
    }

    public static boolean verify(String string, String string2) {
        String string3;
        String string4 = string3 = string2 == null ? "" : string2.replace(" ", "").trim();
        if (!string3.matches("\\d{6}")) {
            return false;
        }
        long l = System.currentTimeMillis() / 1000L / 30L;
        for (long i = -1L; i <= 1L; ++i) {
            if (!TotpUtil.generateCode(string, l + i).equals(string3)) continue;
            return true;
        }
        return false;
    }

    public static String otpauthUrl(String string, String string2, String string3) {
        String string4 = TotpUtil.encode(string);
        String string5 = string4 + ":" + TotpUtil.encode(string2);
        return "otpauth://totp/" + string5 + "?secret=" + string3 + "&issuer=" + string4;
    }

    private static String generateCode(String string, long l) {
        try {
            byte[] byArray = TotpUtil.base32Decode(string);
            byte[] byArray2 = ByteBuffer.allocate(8).putLong(l).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(byArray, "HmacSHA1"));
            byte[] byArray3 = mac.doFinal(byArray2);
            int n = byArray3[byArray3.length - 1] & 0xF;
            int n2 = (byArray3[n] & 0x7F) << 24 | (byArray3[n + 1] & 0xFF) << 16 | (byArray3[n + 2] & 0xFF) << 8 | byArray3[n + 3] & 0xFF;
            return String.format(Locale.ROOT, "%06d", n2 % 1000000);
        }
        catch (Exception exception) {
            return "";
        }
    }

    private static String base32Encode(byte[] byArray) {
        StringBuilder stringBuilder = new StringBuilder();
        int n = 0;
        int n2 = 0;
        for (byte by : byArray) {
            n = n << 8 | by & 0xFF;
            n2 += 8;
            while (n2 >= 5) {
                stringBuilder.append(BASE32[n >> n2 - 5 & 0x1F]);
                n2 -= 5;
            }
        }
        if (n2 > 0) {
            stringBuilder.append(BASE32[n << 5 - n2 & 0x1F]);
        }
        return stringBuilder.toString();
    }

    private static byte[] base32Decode(String string) {
        String string2 = string.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer byteBuffer = ByteBuffer.allocate(string2.length() * 5 / 8 + 5);
        int n = 0;
        int n2 = 0;
        for (char c : string2.toCharArray()) {
            int n3 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(c);
            if (n3 < 0) continue;
            n = n << 5 | n3;
            if ((n2 += 5) < 8) continue;
            byteBuffer.put((byte)(n >> n2 - 8 & 0xFF));
            n2 -= 8;
        }
        byte[] byArray = new byte[byteBuffer.position()];
        byteBuffer.flip();
        byteBuffer.get(byArray);
        return byArray;
    }

    private static String encode(String string) {
        return URLEncoder.encode(string, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

