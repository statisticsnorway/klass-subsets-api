package no.ssb.subsetsservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

    final String s0 = "";
    final String s1 = "a";
    final String s2 = "A";
    final String s3 = "!";
    final String s4 = ",1";
    final String s5 = "2-";
    final String s6 = "3,3";
    final String s7 =" 4.4";
    final String s8 = "5";
    final String s9 = "";

    final String YYYY_MM_DD_true_1 = "2020-01-01";
    final String YYYY_MM_DD_true_2 = "1990-12-31";
    final String YYYY_MM_DD_false_0 = "";
    final String YYYY_MM_DD_false_1 = "2020-00-01";
    final String YYYY_MM_DD_false_2 = "2020-13-01";
    final String YYYY_MM_DD_false_3 = "2020-01-00";
    final String YYYY_MM_DD_false_4 = "100-01-01";
    final String YYYY_MM_DD_false_5 = "10000-01-01";
    final String YYYY_MM_DD_false_6 = "2000-1-1";
    final String YYYY_MM_DD_false_7 = "2000-01-1";
    final String YYYY_MM_DD_false_8 = "2000-1-01";
    final String YYYY_MM_DD_false_9 = "0200-01-01";

    final String version_true_1 = "1";
    final String version_true_2 = "2.2";
    final String version_true_3 = "3.3.3";
    final String version_false_0 = "";
    final String version_false_1 = "v1";
    final String version_false_2 = "v2.2";
    final String version_false_3 = "v3.3.3";
    final String version_false_4 = "version 1";
    final String version_false_5 = "1,1";
    final String version_false_6 = "1,1";

    final String clean_true_1 = "aasdukrjiklmklv";
    final String clean_true_2 = "SDFGHJKKKKKMNBVCFGHJK";
    final String clean_true_3 = "084375887498234756";
    final String clean_true_4 = "SDFfgjhFGJHhlvbmk";
    final String clean_true_5 = "SDF946fgjh6794FGJHhlvb468mk";
    final String clean_true_6 = "TEST_test-94300";
    final String clean_true_7 = "-TEST_test-94300";
    final String clean_true_8 = "_TEST_test-94300";
    final String clean_false_1 = "test test";
    final String clean_false_2 = "test/";
    final String clean_false_3 = "test?";
    final String clean_false_4 = "test&";
    final String clean_false_5 = "test%";
    final String clean_false_6 = "test&";
    final String clean_false_7 = "Robert'); DROP TABLE Students;--";
    final String clean_false_8 = "test'";
    final String clean_false_9 = "test\"";
    final String clean_false_10 = "test;";
    final String clean_false_11 = "test:";
    final String clean_false_12 = "test{}[]<>^^¨¨~=¤#@!|§";

    @Test
    void isYearMonthDay() {
        assertTrue(Utils.isYearMonthDay(YYYY_MM_DD_true_1));
        assertTrue(Utils.isYearMonthDay(YYYY_MM_DD_true_2));

        assertFalse(Utils.isYearMonthDay(YYYY_MM_DD_false_0));
        assertFalse(Utils.isYearMonthDay(YYYY_MM_DD_false_1));
        assertFalse(Utils.isYearMonthDay(YYYY_MM_DD_false_2));
        assertFalse(Utils.isYearMonthDay(YYYY_MM_DD_false_3));
        assertFalse(Utils.isYearMonthDay(YYYY_MM_DD_false_4));
        assertFalse(Utils.isYearMonthDay(YYYY_MM_DD_false_5));
        assertFalse(Utils.isYearMonthDay(YYYY_MM_DD_false_6));
        assertFalse(Utils.isYearMonthDay(YYYY_MM_DD_false_7));
        assertFalse(Utils.isYearMonthDay(YYYY_MM_DD_false_8));
        assertFalse(Utils.isYearMonthDay(YYYY_MM_DD_false_9));
    }

    @Test
    void isVersion() {
        assertTrue(Utils.isVersion(version_true_1));
        assertTrue(Utils.isVersion(version_true_2));
        assertTrue(Utils.isVersion(version_true_3));

        assertFalse(Utils.isVersion(version_false_0));
        assertFalse(Utils.isVersion(version_false_1));
        assertFalse(Utils.isVersion(version_false_2));
        assertFalse(Utils.isVersion(version_false_3));
        assertFalse(Utils.isVersion(version_false_4));
        assertFalse(Utils.isVersion(version_false_5));
        assertFalse(Utils.isVersion(version_false_6));
    }

    @Test
    void isClean() {
        assertTrue(Utils.isClean(clean_true_1));
        assertTrue(Utils.isClean(clean_true_2));
        assertTrue(Utils.isClean(clean_true_3));
        assertTrue(Utils.isClean(clean_true_4));
        assertTrue(Utils.isClean(clean_true_5));
        assertTrue(Utils.isClean(clean_true_6));
        assertTrue(Utils.isClean(clean_true_7));
        assertTrue(Utils.isClean(clean_true_8));

        assertFalse(Utils.isClean(clean_false_1));
        assertFalse(Utils.isClean(clean_false_2));
        assertFalse(Utils.isClean(clean_false_3));
        assertFalse(Utils.isClean(clean_false_4));
        assertFalse(Utils.isClean(clean_false_5));
        assertFalse(Utils.isClean(clean_false_6));
        assertFalse(Utils.isClean(clean_false_7));
        assertFalse(Utils.isClean(clean_false_8));
        assertFalse(Utils.isClean(clean_false_9));
        assertFalse(Utils.isClean(clean_false_10));
        assertFalse(Utils.isClean(clean_false_11));
        assertFalse(Utils.isClean(clean_false_12));
    }

    @Test
    void isNumeric() {
        assertFalse(Utils.isNumeric(s0));
        assertFalse(Utils.isNumeric(s1));
        assertFalse(Utils.isNumeric(s2));
        assertFalse(Utils.isNumeric(s3));
        assertFalse(Utils.isNumeric(s4));
        assertFalse(Utils.isNumeric(s5));
        assertFalse(Utils.isNumeric(s6));
        assertTrue(Utils.isNumeric(s7));
        assertTrue(Utils.isNumeric(s8));
    }

    @Test
    void isInteger() {
        assertFalse(Utils.isInteger(s0));
        assertFalse(Utils.isInteger(s1));
        assertFalse(Utils.isInteger(s2));
        assertFalse(Utils.isInteger(s3));
        assertFalse(Utils.isInteger(s4));
        assertFalse(Utils.isInteger(s5));
        assertFalse(Utils.isInteger(s6));
        assertFalse(Utils.isInteger(s7));
        assertTrue(Utils.isInteger(s8));
    }

    @Test
    void getSelfLinkObject() {
    }

    @Test
    void cleanSubsetVersion() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode subset = mapper.createObjectNode();
        subset.put(Field.VERSION, version_true_1);
        assertEquals("1", Utils.cleanV1SubsetVersionField(subset).get(Field.VERSION).asText());
        subset.put(Field.VERSION, version_true_2);
        assertEquals("2", Utils.cleanV1SubsetVersionField(subset).get(Field.VERSION).asText());
        subset.put(Field.VERSION, version_true_3);
        assertEquals("3", Utils.cleanV1SubsetVersionField(subset).get(Field.VERSION).asText());
    }

    @Test
    void getLatestMajorVersion() {
    }

    @Test
    void sortByVersionValidFrom() {
    }

    @Test
    void versionComparator() {
    }
}
