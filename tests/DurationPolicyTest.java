package dev.lance.focusallowlist;

public final class DurationPolicyTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int value : new int[]{1, 5, 15, 19, 20, 180, 181, 240, 720, 1440, 9999, 10000, DurationPolicy.MAX})
            check(DurationPolicy.parse(String.valueOf(value)) == value, "whole minutes " + value);
        for (String value : new String[]{null, "", "0", "00000", "-100", "-1", "1.5", " 5", "5 ",
                "+5", "１２", "99999", "35792", "2147483647", "999999999999", "a5", "\n5", "000001"})
            check(DurationPolicy.parse(value) == 0, "invalid input " + value);
        check(DurationPolicy.parse("00005") == 5, "leading zeros normalize before native persistence");
        check(!DurationPolicy.valid(-100), "trial sentinel is handled separately in native start hook");
        check(!DurationPolicy.valid(0), "zero is not a formal session");
        check(!DurationPolicy.valid(Integer.MAX_VALUE), "oversized persisted preset cannot start");
        check((long) DurationPolicy.MAX * 60000 <= Integer.MAX_VALUE, "conservative millisecond boundary");
        check((long) (DurationPolicy.MAX + 1) * 60000 > Integer.MAX_VALUE, "next minute exceeds budget");
        System.out.println("PASS " + checks + " duration input and boundary checks");
    }
}
