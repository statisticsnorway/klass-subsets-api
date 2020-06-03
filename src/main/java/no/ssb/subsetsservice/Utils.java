package no.ssb.subsetsservice;

public class Utils {

    public static boolean isYearMonthDay(String date){
        return date.matches("([12]\\d{3}-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01]))");
    }

    public static boolean isVersion(String version){
        return version.matches("(\\d\\.\\d\\.\\d)");
    }

    public static boolean isClean(String str){
        return str.matches("^[a-zA-Z0-9-_]+$");
    }
}
