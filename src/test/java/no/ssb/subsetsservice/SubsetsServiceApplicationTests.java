package no.ssb.subsetsservice;

import net.minidev.json.parser.JSONParser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

@SpringBootTest
class SubsetsServiceApplicationTests {

	String ldsURL = SubsetsController.LDS_LOCAL;

	@Test
	void postToLDSLocal() {
		try {
			String filename = "subset1.json";
			String path = new File("").getAbsolutePath();
			File myObj = new File(path +"\\src\\test\\java\\no\\ssb\\subsetsservice\\"+filename);
			Scanner myReader = new Scanner(myObj);
			StringBuilder sb = new StringBuilder();
			while (myReader.hasNextLine()) {
				sb.append(myReader.nextLine());
			}
			myReader.close();
			String subsetJSON = sb.toString();
			System.out.println(subsetJSON);
			SubsetsController.putTo(ldsURL, "/1", subsetJSON);
		} catch (FileNotFoundException e) {
			System.out.println("An error occurred.");
			e.printStackTrace();
		}
	}

}
