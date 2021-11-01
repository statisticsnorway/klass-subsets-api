package no.ssb.subsetsservice;
import no.ssb.subsetsservice.entity.SQL;
import no.ssb.subsetsservice.service.ConnectionPool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@SpringBootTest
public class SubsetsServiceApplicationTests {

	private static final Logger LOG = LoggerFactory.getLogger(SubsetsServiceApplicationTests.class);

	@Test
	void logTest(){
		LOG.trace("TRACE");
		LOG.debug("DEBUG");
		LOG.info("INFO");
		LOG.warn("WARN");
		LOG.error("ERROR");
	}



}
