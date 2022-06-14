package eu.fasten.analyzer.qualityanalyzer;

import org.junit.jupiter.api.Test;

import static eu.fasten.analyzer.qualityanalyzer.MetadataUtils.normalizeCallableName;
import static org.junit.jupiter.api.Assertions.*;

class MetadataUtilsTest {

    @Test
    void normalizeCallableNameJavaTest() {
        assertEquals("",
                normalizeCallableName(""));

        assertEquals("NDC.clear",
                normalizeCallableName("NDC::clear"));

        assertEquals("SMTPAppender.%3Cinit%3E",
                normalizeCallableName("SMTPAppender::SMTPAppender"));

        assertEquals("LoggingReceiver$Slurper.run",
                normalizeCallableName("LoggingReceiver::Slurper::run"));

        assertEquals("LoggingReceiver$Slurper.%3Cinit%3E",
                normalizeCallableName("LoggingReceiver::Slurper::Slurper"));

        assertEquals("PatternParser$NamedPatternConverter.%3Cinit%3E",
                normalizeCallableName("PatternParser::NamedPatternConverter::NamedPatternConverter"));
    }

    @Test
    void normalizeCallableNamePythonTest() {
        assertEquals("get_filing_list",
                normalizeCallableName("get_filing_list"));

        assertEquals("_get_daily_listing_url",
                normalizeCallableName("_get_daily_listing_url"));
    }

    @Test
    void normalizeCallableNameCTest() {
        assertEquals("drop_excludes",
                normalizeCallableName("drop_excludes"));

        assertEquals("gda_xslt_getxmlvalue_function",
                normalizeCallableName("gda_xslt_getxmlvalue_function"));
    }
}