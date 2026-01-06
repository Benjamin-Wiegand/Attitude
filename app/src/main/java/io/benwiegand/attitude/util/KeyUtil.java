package io.benwiegand.attitude.util;

import android.util.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;

import io.benwiegand.attitude.exception.CorruptedKeystoreException;

public class KeyUtil {
    public static byte[] calculateCertificateFingerprint(Certificate cert) throws CorruptedKeystoreException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA256");
            return digest.digest(cert.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new CorruptedKeystoreException("certificate encoding is invalid", e);
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("missing JDK support for SHA256 hashing", e);
        }
    }


    private static final char[] HEX_DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
    private static final char[] HEX_DIGITS_UPPER = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
    public static String hexOf(byte[] input, String separator, boolean upper) {
        char[] digits = upper ? HEX_DIGITS_UPPER : HEX_DIGITS;

        StringBuilder sb = new StringBuilder(input.length * (2 + separator.length()) - separator.length());
        for (int i = 0; i < input.length; i++) {
            byte b = input[i];
            sb.append(digits[(0xF0 & b) >>> 4])
                    .append(digits[0x0F & b]);

            if (i != input.length - 1) sb.append(separator);
        }

        return sb.toString();
    }

}
