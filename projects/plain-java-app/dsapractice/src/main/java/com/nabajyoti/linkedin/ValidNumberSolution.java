package com.nabajyoti.linkedin;

import java.util.ArrayList;
import java.util.List;

public class ValidNumberSolution {
    public boolean isNumber(String s) {
        boolean seenDigit = false, seenDot = false, seenExp = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c >= '0' && c <= '9') {
                seenDigit = true;
            } else if (c == '+' || c == '-') {
                // sign only at the start, or right after e/E
                if (i > 0 && s.charAt(i - 1) != 'e' && s.charAt(i - 1) != 'E') {
                    return false;
                }
            } else if (c == '.') {
                // no second dot, and no dot in the exponent part
                if (seenDot || seenExp) return false;
                seenDot = true;
            } else if (c == 'e' || c == 'E') {
                // need digits before e, and only one e
                if (seenExp || !seenDigit) return false;
                seenExp = true;
                seenDigit = false; // must see digits after e
            } else {
                return false;
            }
        }
        return seenDigit;
    }
}


