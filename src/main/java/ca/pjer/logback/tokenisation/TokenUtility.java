package ca.pjer.logback.tokenisation;

import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenUtility {
    public static String replaceTokens(String input, Map<String, Supplier<String>> tokenSuppliers) {
        Pattern pattern = Pattern.compile("%\\{(.+?)}");
        Matcher matcher = pattern.matcher(input);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            Supplier<String> tokenSupplier = tokenSuppliers.get(matcher.group(1));
            String replacement = "";

            if (tokenSupplier != null) {
                replacement = tokenSupplier.get();
            }
            matcher.appendReplacement(buffer, "");
            buffer.append(replacement);
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }
}
